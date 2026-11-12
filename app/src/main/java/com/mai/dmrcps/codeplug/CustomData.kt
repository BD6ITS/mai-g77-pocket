package com.mai.dmrcps.codeplug

/**
 * 码盘末尾那块「自定义数据」：开机图片、开机旋律、卫星星历、日间/夜间主题。
 *
 * 布局（照反编译出来的 `OpenGD77Form.FindCustomDataBlock`）：
 *
 *   0x1EE60 起：`OpenGD77` 八个字节 + 4 字节版本
 *   之后每块：  [类型 1 字节][3 字节保留][长度 u32 小端][数据…]
 *   类型 255 表示列表结束
 *
 * 类型：1=开机图片（128×64 单色）、2=开机旋律、3=卫星星历、4=日间主题、5=夜间主题。
 */
object CustomData {

    const val START = 0x1EE60
    const val END = 0x20000
    const val HEADER_SIZE = 12 // "OpenGD77".length + 4

    const val TYPE_IMAGE = 1
    const val TYPE_MELODY = 2
    const val TYPE_KEPS = 3
    const val TYPE_THEME_DAY = 4
    const val TYPE_THEME_NIGHT = 5
    const val TYPE_END = 255

    const val THEME_SIZE = 64
    const val THEME_COLOURS = 32

    data class Block(val type: Int, val offset: Int, val length: Int)

    /** 把整块自定义数据区扫一遍，返回找到的块（offset 是相对 0x1EE60 的）。 */
    fun blocks(bytes: ByteArray): List<Block> {
        if (bytes.size < END) return emptyList()
        val area = bytes.copyOfRange(START, END)
        if (!String(area, 0, 8, Charsets.US_ASCII).startsWith("OpenGD77")) return emptyList()

        val out = ArrayList<Block>()
        var pos = HEADER_SIZE
        var guard = 0
        while (pos + 8 <= area.size - 8 && guard++ < 64) {
            val type = area[pos].toInt() and 0xFF
            if (type == TYPE_END) break
            val len = le32(area, pos + 4)
            if (len < 0 || pos + 8 + len > area.size) break
            out.add(Block(type, pos + 8, len))
            pos += 8 + len
        }
        return out
    }

    fun blockOf(bytes: ByteArray, type: Int): Block? = blocks(bytes).firstOrNull { it.type == type }

    /** 主题：32 个颜色（块长 64 字节），RGB565、高字节在前。 */
    fun themeColours(bytes: ByteArray, type: Int): List<Int> {
        val block = blockOf(bytes, type) ?: return emptyList()
        val n = minOf(THEME_COLOURS, block.length / 2)
        val out = ArrayList<Int>(n)
        for (i in 0 until n) {
            val hi = bytes[START + block.offset + i * 2].toInt() and 0xFF
            val lo = bytes[START + block.offset + i * 2 + 1].toInt() and 0xFF
            out.add((hi shl 8) or lo)
        }
        return out
    }

    /** 改一个颜色：写回 RGB565（高字节在前）。 */
    fun setThemeColour(bytes: ByteArray, type: Int, index: Int, rgb565: Int) {
        val block = blockOf(bytes, type) ?: return
        if (index < 0 || (index + 1) * 2 > block.length) return
        val at = START + block.offset + index * 2
        bytes[at] = ((rgb565 shr 8) and 0xFF).toByte()
        bytes[at + 1] = (rgb565 and 0xFF).toByte()
    }

    /** 0xRRGGBB → RGB565。 */
    fun rgbTo565(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return ((r * 31 / 255) shl 11) or ((g * 63 / 255) shl 5) or (b * 31 / 255)
    }

    /** RGB565 → 0xRRGGBB，方便在界面上画。 */
    fun rgb565ToRgb(value: Int): Int {
        val r = (value shr 11) and 0x1F
        val g = (value shr 5) and 0x3F
        val b = value and 0x1F
        val r8 = (r * 255 + 15) / 31
        val g8 = (g * 255 + 31) / 63
        val b8 = (b * 255 + 15) / 31
        return (r8 shl 16) or (g8 shl 8) or b8
    }

    /** 开机旋律：一串 16 位小端数值，0,0 表示结束。 */
    fun melody(bytes: ByteArray): List<Int> {
        val block = blockOf(bytes, TYPE_MELODY) ?: return emptyList()
        val out = ArrayList<Int>()
        var i = 0
        while (i + 1 < block.length && i < 512) {
            val v = le16(bytes, START + block.offset + i)
            if (v == 0 && le16(bytes, START + block.offset + i + 2) == 0) break
            out.add(v)
            i += 2
        }
        return out
    }

    /**
     * 没有这块就新加一块：接在最后一块后面，把结束标记往后挪。
     * 位置不够就返回 null。
     */
    fun ensureBlock(bytes: ByteArray, type: Int, length: Int): Int? {
        blockOf(bytes, type)?.let { return it.offset }
        if (bytes.size < END) return null
        var pos = HEADER_SIZE
        var guard = 0
        while (pos + 8 <= END - START - 8 && guard++ < 64) {
            val t = bytes[START + pos].toInt() and 0xFF
            if (t == TYPE_END) break
            val len = le32(bytes, START + pos + 4)
            if (len < 0) return null
            pos += 8 + len
        }
        if (pos + 8 + length > END - START - 8) return null
        val at = START + pos
        bytes[at] = type.toByte()
        bytes[at + 1] = 0
        bytes[at + 2] = 0
        bytes[at + 3] = 0
        bytes[at + 4] = (length and 0xFF).toByte()
        bytes[at + 5] = ((length shr 8) and 0xFF).toByte()
        bytes[at + 6] = ((length shr 16) and 0xFF).toByte()
        bytes[at + 7] = ((length shr 24) and 0xFF).toByte()
        for (i in 0 until length) bytes[at + 8 + i] = 0
        val endAt = at + 8 + length
        bytes[endAt] = TYPE_END.toByte()
        for (i in 1 until 8) bytes[endAt + i] = 0xFF.toByte()
        return pos + 8
    }

    /**
     * 开机旋律拆成一个个音：(音高半音, 时长)，一直到 0,0 结束。
     * CPS 里文本框写的就是「音高,时长,音高,时长…」，音高是相对 A 的半音数，可以是负数。
     */
    fun melodyPairs(bytes: ByteArray): List<Pair<Int, Int>> {
        val block = blockOf(bytes, TYPE_MELODY) ?: return emptyList()
        val out = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i + 1 < block.length) {
            val note = bytes[START + block.offset + i].toInt()
            val dur = bytes[START + block.offset + i + 1].toInt() and 0xFF
            if (note == 0 && dur == 0) break
            out.add(note to dur)
            i += 2
        }
        return out
    }

    /** 把旋律写回去，末尾补 0,0，剩下的清掉。 */
    fun setMelody(bytes: ByteArray, pairs: List<Pair<Int, Int>>) {
        val block = blockOf(bytes, TYPE_MELODY) ?: return
        val base = START + block.offset
        var i = 0
        for ((note, dur) in pairs.take(block.length / 2 - 1)) {
            bytes[base + i] = note.toByte()
            bytes[base + i + 1] = dur.coerceIn(0, 255).toByte()
            i += 2
        }
        while (i < block.length) {
            bytes[base + i] = 0
            i++
        }
    }

    /** 音高半音数 → 频率（Hz），照 CPS 的 98*2^(n/12)。 */
    fun noteToHz(note: Int): Double = 98.0 * Math.pow(2.0, note / 12.0)

    /** 卫星星历块的原始字节（TLE 就存在这儿）。 */
    fun keps(bytes: ByteArray): ByteArray? {
        val block = blockOf(bytes, TYPE_KEPS) ?: return null
        return bytes.copyOfRange(START + block.offset, START + block.offset + block.length)
    }

    private fun le16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)
}
