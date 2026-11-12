package com.mai.dmrcps

import java.net.HttpURLConnection
import java.net.URL

/**
 * DMR ID 呼号库：从 RadioID 的公开镜像拉 CSV，按地区筛选。
 *
 * 数据源就是电脑 CPS「下载呼号库」用的那个仓库（ContactLists/NORMAL-ContactLists），
 * 全球版 GD77_WW_default.csv 一万七千多条，跟 CPS 界面上显示的数量一致。
 *
 * 格式：ID,呼号,姓名,城市,省,国家,备注 —— 每行一条。
 */
object DmrIdDb {

    private const val BASE = "https://raw.githubusercontent.com/ContactLists/NORMAL-ContactLists/main/RADIODDITY/GD77/"

    /** 可选的数据源。全球版最大，欧洲版小一些。 */
    val SOURCES = listOf(
        "全球" to "GD77_WW_default.csv",
        "欧洲" to "GD77_EU_default.csv",
        "德国/奥地利/瑞士" to "GD77_DACH_default.csv",
        "法国" to "GD77_FR_default.csv",
        "意大利" to "GD77_IT_default.csv",
        "德国" to "GD77_DE_default.csv",
    )

    data class Entry(
        val id: Long,
        val callsign: String,
        val name: String,
        val city: String,
        val state: String,
        val country: String,
    ) {
        /** 「呼号 姓名 · 城市」这样一行摘要。 */
        val summary: String
            get() = listOf(callsign, name, city, state)
                .filter { it.isNotBlank() }
                .joinToString("　")
    }

    /** 下载并解析。onProgress 回调已读字节数（不知道总长时 total 传 0）。 */
    fun download(fileName: String, onProgress: (Int) -> Unit = {}): List<Entry> {
        val conn = (URL(BASE + fileName).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("User-Agent", "mai-cps")
        }
        try {
            conn.inputStream.bufferedReader().use { reader ->
                val out = ArrayList<Entry>(20000)
                var lineNo = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    lineNo++
                    if (line.isBlank()) continue
                    // 第一行可能是表头
                    if (lineNo == 1 && line.firstOrNull()?.isDigit() != true) continue
                    val f = line.split(',')
                    if (f.size < 3) continue
                    val id = f[0].trim().trim('"').toLongOrNull() ?: continue
                    out.add(
                        Entry(
                            id = id,
                            callsign = f.getOrNull(1)?.trim()?.trim('"') ?: "",
                            name = f.getOrNull(2)?.trim()?.trim('"') ?: "",
                            city = f.getOrNull(3)?.trim()?.trim('"') ?: "",
                            state = f.getOrNull(4)?.trim()?.trim('"') ?: "",
                            country = f.getOrNull(5)?.trim()?.trim('"') ?: "",
                        )
                    )
                    if (out.size % 2000 == 0) onProgress(out.size)
                }
                return out
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 按 ID 前缀筛（46 = 中国），空前缀就是全部。 */
    fun filterPrefix(all: List<Entry>, prefix: String): List<Entry> {
        val p = prefix.trim()
        if (p.isEmpty()) return all
        return all.filter { it.id.toString().startsWith(p) }
    }

    /**
     * 固件用的 64 字符表：每个字符压成 6 bit。
     * 顺序就是 CPS 里 DECOMPRESS_LUT 的顺序。
     */
    private const val ALPHABET = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz."

    private fun lut(c: Char): Int {
        val i = ALPHABET.indexOf(c)
        return if (i >= 0) i else 0
    }

    /** 6 bit 一个字符，每 4 个字符压成 3 字节，和 CPS 的 compress 一模一样。 */
    private fun compress(text: String): ByteArray {
        val s = text
        val size = s.length / 4 * 3 + s.length % 4
        val out = ByteArray(size)
        var o = 0
        var i = 0
        while (i < s.length) {
            val c0 = lut(s[i])
            out[o] = (c0 shl 2).toByte()
            i++
            if (i >= s.length) break
            val c1 = lut(s[i])
            out[o] = (out[o].toInt() or (c1 shr 4)).toByte()
            out[o + 1] = (c1 shl 4).toByte()
            i++
            if (i >= s.length) break
            val c2 = lut(s[i])
            out[o + 1] = (out[o + 1].toInt() or (c2 shr 2)).toByte()
            out[o + 2] = (c2 shl 6).toByte()
            i++
            if (i >= s.length) break
            val c3 = lut(s[i])
            out[o + 2] = (out[o + 2].toInt() or c3).toByte()
            i++
            o += 3
        }
        return out
    }

    /** 每条记录多少字节：3 字节 DMR ID + 压缩后的文字。 */
    fun recordLength(stringLength: Int): Int = stringLength / 4 * 3 + stringLength % 4 + 3

    /**
     * 生成写进电台的二进制块，格式照 CPS 的 GenerateUploadData：
     * 12 字节头（"Id" + N + 记录长 + "001\0" + 记录数小端）+ 每条记录。
     * 每条记录 = 3 字节 ID（小端）+ Callsign 空格 Name 的 6-bit 压缩。
     */
    fun buildBinary(entries: List<Entry>, stringLength: Int = 16): ByteArray {
        val recLen = recordLength(stringLength)
        val sorted = entries.sortedBy { it.id }
        val out = ByteArray(12 + sorted.size * recLen)
        out[0] = 73          // 'I'
        out[1] = 100         // 'd'
        out[2] = 78          // 'N'：ID 用 3 字节
        out[3] = (74 + recLen).toByte()
        out[4] = 48          // '0'
        out[5] = 48          // '0'
        out[6] = 49          // '1'
        out[7] = 0
        val n = sorted.size
        out[8] = (n and 0xFF).toByte()
        out[9] = ((n shr 8) and 0xFF).toByte()
        out[10] = ((n shr 16) and 0xFF).toByte()
        out[11] = ((n shr 24) and 0xFF).toByte()

        var off = 12
        for (e in sorted) {
            out[off] = (e.id and 0xFF).toByte()
            out[off + 1] = ((e.id shr 8) and 0xFF).toByte()
            out[off + 2] = ((e.id shr 16) and 0xFF).toByte()
            val text = (e.callsign + " " + e.name).take(stringLength)
            val packed = compress(text)
            System.arraycopy(packed, 0, out, off + 3, minOf(packed.size, recLen - 3))
            off += recLen
        }
        return out
    }
    /** 导出成 CPS 能吃的 CSV（ID,呼号,姓名,城市,省,国家,备注）。 */
    fun toCsv(rows: List<Entry>): String = buildString {
        append("ID,Callsign,Name,City,State,Country,Remarks\n")
        rows.forEach { e ->
            append(e.id).append(',')
            append(e.callsign).append(',')
            append(e.name).append(',')
            append(e.city).append(',')
            append(e.state).append(',')
            append(e.country).append(',')
            append('\n')
        }
    }
}
