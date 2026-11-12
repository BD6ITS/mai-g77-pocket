package com.mai.dmrcps.satellite

import java.time.Instant
import kotlin.math.*

/** 一条 TLE 记录。 */
data class TleSat(
    val name: String,
    val line1: String,
    val line2: String,
) {
    val noradId: Int = line1.substring(2, 7).trim().toIntOrNull() ?: 0
    val epochYear: Int = line1.substring(18, 20).trim().toIntOrNull() ?: 0
    val epochDay: Double = line1.substring(20, 32).trim().toDoubleOrNull() ?: 0.0
    val inclination: Double = line2.substring(8, 16).trim().toDoubleOrNull() ?: 0.0
    val raan: Double = line2.substring(17, 25).trim().toDoubleOrNull() ?: 0.0
    val eccentricity: Double = ("0." + line2.substring(26, 33).trim()).toDoubleOrNull() ?: 0.0
    val argPerigee: Double = line2.substring(34, 42).trim().toDoubleOrNull() ?: 0.0
    val meanAnomaly: Double = line2.substring(43, 51).trim().toDoubleOrNull() ?: 0.0
    val meanMotion: Double = line2.substring(52, 63).trim().toDoubleOrNull() ?: 0.0

    /** TLE 时刻（UTC）。 */
    val epoch: Instant
        get() {
            val year = if (epochYear < 57) 2000 + epochYear else 1900 + epochYear
            val jan1 = Instant.parse("%04d-01-01T00:00:00Z".format(year))
            return jan1.plusSeconds(((epochDay - 1.0) * 86400.0).roundToLong())
        }
}

/** 把 CelesTrak 的文本切成一条条 TLE（三行一组：名字、line1、line2）。 */
fun parseTleText(text: String): List<TleSat> {
    val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    val out = ArrayList<TleSat>()
    var i = 0
    while (i + 2 < lines.size + 1) {
        if (i + 1 >= lines.size) break
        val a = lines[i]
        if (a.startsWith("1 ")) {
            // 没有名字行的情况
            if (i + 1 < lines.size && lines[i + 1].startsWith("2 ")) {
                out.add(TleSat("NORAD ${a.substring(2, 7).trim()}", a, lines[i + 1]))
                i += 2
                continue
            }
        }
        if (i + 2 < lines.size && lines[i + 1].startsWith("1 ") && lines[i + 2].startsWith("2 ")) {
            out.add(TleSat(a.trim(), lines[i + 1], lines[i + 2]))
            i += 3
        } else {
            i += 1
        }
    }
    return out
}

/** 观察者在地球上的位置。 */
data class Observer(val latDeg: Double, val lonDeg: Double)

/** 一次过境：开始、最高点、结束。 */
data class Pass(
    val satName: String,
    val aos: Instant,
    val los: Instant,
    val maxElevationDeg: Double,
    /** 过境中点那一刻的多普勒修正后的收发频率。 */
    val downlinkAtTcaHz: Double,
    val uplinkAtTcaHz: Double,
)

/**
 * 简化轨道推算。
 *
 * 用的是「两体 + 地球自转」，没上 SGP4 全套。对 FM 卫星够用：
 * 位置误差带来的多普勒偏差在几十赫兹量级，而 FM 卫星的频率容差是几百赫兹。
 * 想要更准以后再把 SGP4 的摄动项补上。
 */
object Orbit {

    private const val MU = 398600.4418e9       // m^3/s^2
    private const val EARTH_R = 6378.137e3     // m
    private const val EARTH_ROT = 7.2921150e-5 // rad/s
    private const val C = 299792458.0

    private fun deg2rad(d: Double) = d * PI / 180.0

    /** 某一时刻卫星在地心惯性系里的位置（米）。 */
    fun eciPosition(sat: TleSat, at: Instant): DoubleArray {
        val a = (MU / (sat.meanMotion * 2 * PI / 86400.0).pow(2)).pow(1.0 / 3.0)
        val ecc = sat.eccentricity
        val n = sat.meanMotion * 2 * PI / 86400.0

        val dt = (at.epochSecond - sat.epoch.epochSecond).toDouble()
        var m = deg2rad(sat.meanAnomaly) + n * dt
        m = m.mod(2 * PI)

        // 解开普勒方程
        var e = m
        repeat(12) {
            e -= (e - ecc * sin(e) - m) / (1 - ecc * cos(e))
        }

        val trueAnomaly = 2 * atan2(
            sqrt(1 + ecc) * sin(e / 2),
            sqrt(1 - ecc) * cos(e / 2),
        )
        val r = a * (1 - ecc * cos(e))

        // 轨道平面 → 惯性系
        val u = trueAnomaly + deg2rad(sat.argPerigee)
        val i = deg2rad(sat.inclination)
        val om = deg2rad(sat.raan)

        val x = r * (cos(om) * cos(u) - sin(om) * sin(u) * cos(i))
        val y = r * (sin(om) * cos(u) + cos(om) * sin(u) * cos(i))
        val z = r * (sin(u) * sin(i))
        return doubleArrayOf(x, y, z)
    }

    /** 观察者在地固系里的位置（米）。 */
    fun observerEcef(obs: Observer): DoubleArray {
        val lat = deg2rad(obs.latDeg)
        val lon = deg2rad(obs.lonDeg)
        return doubleArrayOf(
            EARTH_R * cos(lat) * cos(lon),
            EARTH_R * cos(lat) * sin(lon),
            EARTH_R * sin(lat),
        )
    }

    /** 从观察者看卫星的仰角（度），以及斜距（米）。 */
    fun lookAngle(sat: TleSat, obs: Observer, at: Instant): Pair<Double, Double> {
        val eci = eciPosition(sat, at)
        val gmst = gmst(at)
        // 惯性系 → 地固系
        val x = cos(gmst) * eci[0] + sin(gmst) * eci[1]
        val y = -sin(gmst) * eci[0] + cos(gmst) * eci[1]
        val z = eci[2]
        val o = observerEcef(obs)
        val dx = x - o[0]
        val dy = y - o[1]
        val dz = z - o[2]
        val range = sqrt(dx * dx + dy * dy + dz * dz)

        // 观察者的「上方」方向
        val lat = deg2rad(obs.latDeg)
        val lon = deg2rad(obs.lonDeg)
        val up = doubleArrayOf(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
        val dot = (dx * up[0] + dy * up[1] + dz * up[2]) / range
        val elev = asin(dot.coerceIn(-1.0, 1.0)) * 180.0 / PI
        return elev to range
    }

    private fun gmst(at: Instant): Double {
        // 简化：从 J2000 起算的平均恒星时
        val days = (at.epochSecond - 946728000L) / 86400.0
        return (280.46061837 + 360.98564736629 * days).mod(360.0) * PI / 180.0
    }

    /** 多普勒修正后的频率：卫星靠近时收到的高一些。 */
    fun dopplerHz(sat: TleSat, obs: Observer, at: Instant, freqHz: Double): Double {
        val dt = 1.0
        val (_, r1) = lookAngle(sat, obs, at.minusSeconds(dt.toLong()))
        val (_, r2) = lookAngle(sat, obs, at.plusSeconds(dt.toLong()))
        val rangeRate = (r2 - r1) / (2 * dt)
        return -rangeRate / C * freqHz
    }

    /**
     * 从现在起找下一次过境（仰角 >= minElev 度）。
     * 用 30 秒粗扫 + 二分细化的办法，够快也够准。
     */
    fun nextPass(
        sat: TleSat,
        obs: Observer,
        from: Instant = Instant.now(),
        minElevDeg: Double = 0.0,
        horizonHours: Long = 24,
        downlinkHz: Double = 145_900_000.0,
        uplinkHz: Double = 145_900_000.0,
    ): Pass? {
        val step = 30L
        var t = from
        val end = from.plusSeconds(horizonHours * 3600)
        var prevElev = lookAngle(sat, obs, t).first
        var aos: Instant? = null

        while (t.isBefore(end)) {
            val next = t.plusSeconds(step)
            val elev = lookAngle(sat, obs, next).first
            if (aos == null && prevElev < minElevDeg && elev >= minElevDeg) {
                aos = refine(sat, obs, t, next, minElevDeg)
            } else if (aos != null && prevElev >= minElevDeg && elev < minElevDeg) {
                val los = refine(sat, obs, t, next, minElevDeg)
                // 在 AOS..LOS 之间找最高点
                var best = aos
                var bestElev = -90.0
                var q = aos
                while (q!!.isBefore(los)) {
                    val e = lookAngle(sat, obs, q).first
                    if (e > bestElev) { bestElev = e; best = q }
                    q = q.plusSeconds(10)
                }
                val tca = best!!
                return Pass(
                    satName = sat.name,
                    aos = aos,
                    los = los,
                    maxElevationDeg = bestElev,
                    downlinkAtTcaHz = downlinkHz + dopplerHz(sat, obs, tca, downlinkHz),
                    uplinkAtTcaHz = uplinkHz - dopplerHz(sat, obs, tca, uplinkHz),
                )
            }
            prevElev = elev
            t = next
        }
        return null
    }

    private fun refine(sat: TleSat, obs: Observer, a: Instant, b: Instant, target: Double): Instant {
        var lo = a.epochSecond
        var hi = b.epochSecond
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            val e = lookAngle(sat, obs, Instant.ofEpochSecond(mid)).first
            if (e >= target) hi = mid else lo = mid
        }
        return Instant.ofEpochSecond(hi)
    }
}
