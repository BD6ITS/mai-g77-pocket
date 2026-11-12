package com.mai.dmrcps.satellite

import com.mai.dmrcps.codeplug.CustomData
import kotlin.math.roundToInt

/**
 * 把 TLE 按电脑版 CPS 的格式压进码盘的卫星星历块。
 *
 * 格式（`OpenGD77Form.importKeps`）：整块 2520 字节，每条卫星 100 字节、最多 25 条：
 *
 *   [0..7]    名字，8 字节 ASCII
 *   [8..19]   第一行星历，12 字节（压缩）
 *   [20..47]  第二行星历，28 字节（压缩）
 *   [48..51]  接收频率 Hz（u32 小端）
 *   [52..55]  发射频率 Hz
 *   [56..57]  发射亚音 ×10
 *   [58..59]  唤醒亚音 ×10
 *   [60..63]  第二组接收/发射…
 *   [76..99]  预留，APRS 呼号之类
 *
 * 压缩算法（`compressTLEText`）：字符表 `0123456789. +-*`，
 * 一个字节装两个字符，高四位是第一个、低四位是第二个。
 */
object Keps {

    const val BLOCK_SIZE = 2520

    /** 默认写进机器的那几颗。主人的机器上用的就是这几颗。 */
    val DEFAULT_SATS = listOf(
        "AO-123", "AO-91", "ISS", "LilacSat",
        "PO-101", "SO-50", "SONATE-2", "UmKA-1",
    )

    /** 从一堆 TLE 里挑出默认那几颗。匹配不上就返回空，不拿别的卫星凑数。 */
    fun pickDefault(tles: List<TleSat>): List<TleSat> {
        val wanted = DEFAULT_SATS.map { it.uppercase() }
        return tles.filter { t ->
            val name = t.name.trim().uppercase()
            wanted.any { w -> name == w || name.startsWith(w + " ") || name.contains(w) }
        }
    }
    const val PER_SAT = 100
    const val MAX_SATS = 25
    private const val TABLE = "0123456789. +-*"

    /** satellites.txt 里的一行。 */
    data class SatFreq(
        val catalogue: String,
        val name: String,
        val rx1: Double,
        val tx1: Double,
        val ctcss1: Double,
        val armCtcss1: Double,
        val rx2: Double,
        val tx2: Double,
        val rx3: Double,
        val tx3: Double,
        val reserved: String,
    )

    /** 解析 CPS 的 satellites.txt（表头一行 + 每颗一行，逗号分隔）。 */
    fun parseTable(text: String): List<SatFreq> {
        val out = ArrayList<SatFreq>()
        for (raw in text.lines().drop(1)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val f = line.split(',')
            if (f.size < 10) continue
            fun d(i: Int) = f.getOrNull(i)?.trim()?.toDoubleOrNull() ?: 0.0
            out.add(
                SatFreq(
                    catalogue = f[0].trim(),
                    name = f[1].trim(),
                    rx1 = d(2), tx1 = d(3), ctcss1 = d(4), armCtcss1 = d(5),
                    rx2 = d(6), tx2 = d(7), rx3 = d(8), tx3 = d(9),
                    reserved = f.getOrNull(10)?.trim().orEmpty(),
                )
            )
        }
        return out
    }

    /**
     * 按星历和频率表拼出整块 2520 字节。
     * TLE 和表按 NORAD 编号对上；对不上的星历会被跳过。
     */
    fun build(tles: List<TleSat>, table: List<SatFreq>): ByteArray {
        val block = ByteArray(BLOCK_SIZE)
        var pos = 0
        var added = 0
        for (tle in tles) {
            if (added >= MAX_SATS) break
            val id = tle.line1.substring(2, 7).trim()
            val freq = table.firstOrNull { it.catalogue.trim() == id } ?: continue
            if (pos + PER_SAT > BLOCK_SIZE) break

            // 名字最多 8 字节
            val name = freq.name.take(8).toByteArray(Charsets.US_ASCII)
            System.arraycopy(name, 0, block, pos, minOf(name.size, 8))

            val l1 = tle.line1.padEnd(70)
            val l2 = tle.line2.padEnd(70)
            val s1 = l1.substring(18, 20) + l1.substring(20, 32) + l1.substring(33, 43)
            val s2 = l2.substring(8, 16) + l2.substring(17, 25) + l2.substring(26, 33) +
                l2.substring(34, 42) + l2.substring(43, 51) + l2.substring(52, 63) +
                l2.substring(63, 68) + " "

            val c1 = compress(s1)
            val c2 = compress(s2)
            System.arraycopy(c1, 0, block, pos + 8, minOf(c1.size, 12))
            System.arraycopy(c2, 0, block, pos + 20, minOf(c2.size, 28))

            putU32(block, pos + 48, hz(freq.rx1))
            putU32(block, pos + 52, hz(freq.tx1))
            putU16(block, pos + 56, (freq.ctcss1 * 10).roundToInt())
            putU16(block, pos + 58, (freq.armCtcss1 * 10).roundToInt())
            putU32(block, pos + 60, hz(freq.rx2))
            putU32(block, pos + 64, hz(freq.tx2))
            putU32(block, pos + 68, hz(freq.rx3))
            putU32(block, pos + 72, hz(freq.tx3))

            val res = freq.reserved.take(24).toByteArray(Charsets.US_ASCII)
            System.arraycopy(res, 0, block, pos + 76, minOf(res.size, 24))

            pos += PER_SAT
            added++
        }
        return block
    }

    /** 把新星历写进码盘的那块自定义数据（长度固定 2520，原地覆盖）。 */
    fun writeInto(image: ByteArray, block: ByteArray): Boolean {
        val found = CustomData.blockOf(image, CustomData.TYPE_KEPS) ?: return false
        if (found.length != BLOCK_SIZE) return false
        val at = CustomData.START + found.offset
        System.arraycopy(block, 0, image, at, BLOCK_SIZE)
        return true
    }

    private fun hz(mhz: Double): Int = (mhz * 1_000_000).roundToInt()

    private fun compress(s: String): ByteArray {
        val out = ByteArray((s.length + 1) / 2)
        var i = 0
        while (i < s.length) {
            val hi = TABLE.indexOf(s[i]).let { if (it < 0) 0 else it }
            val lo = if (i + 1 < s.length) TABLE.indexOf(s[i + 1]).let { if (it < 0) 0 else it } else 0
            out[i / 2] = ((hi shl 4) or lo).toByte()
            i += 2
        }
        return out
    }

    private fun putU32(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = ((v shr 8) and 0xFF).toByte()
        b[at + 2] = ((v shr 16) and 0xFF).toByte()
        b[at + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun putU16(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
