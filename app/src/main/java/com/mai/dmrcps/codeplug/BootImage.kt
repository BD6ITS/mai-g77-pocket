package com.mai.dmrcps.codeplug

import android.graphics.Bitmap

/**
 * 开机图片：128×64 单色，存在自定义数据块里，一共 1024 字节。
 *
 * 排法照 CPS 的 `loadImageFromCodeplug`：
 * 第 i 行（0..7）的第 j 个字节管第 j 列、第 i*8..i*8+7 这 8 个像素，
 * 字节的第 k 位就是第 i*8+k 行那一点，1 = 黑。
 */
object BootImage {

    const val WIDTH = 128
    const val HEIGHT = 64
    const val SIZE = WIDTH * HEIGHT / 8   // 1024

    /** 从块里解出点阵：true = 黑。 */
    fun decode(data: ByteArray): Array<BooleanArray> {
        val px = Array(HEIGHT) { BooleanArray(WIDTH) }
        if (data.size < SIZE) return px
        for (i in 0 until 8) {
            for (j in 0 until WIDTH) {
                val b = data[i * WIDTH + j].toInt() and 0xFF
                for (k in 0 until 8) {
                    if ((b shr k) and 1 == 1) px[i * 8 + k][j] = true
                }
            }
        }
        return px
    }

    /** 点阵写回 1024 字节。 */
    fun encode(px: Array<BooleanArray>): ByteArray {
        val out = ByteArray(SIZE)
        for (i in 0 until 8) {
            for (j in 0 until WIDTH) {
                var b = 0
                for (k in 0 until 8) {
                    if (px[i * 8 + k][j]) b = b or (1 shl k)
                }
                out[i * WIDTH + j] = b.toByte()
            }
        }
        return out
    }

    /**
     * 把任意图片缩成 128×64，按亮度切黑白。
     * 阈值取平均亮度，偏黑就留黑点——单色屏只能这么办，没有灰阶。
     */
    fun fromBitmap(src: Bitmap): ByteArray {
        val small = Bitmap.createScaledBitmap(src, WIDTH, HEIGHT, true)
        var sum = 0L
        val grey = IntArray(WIDTH * HEIGHT)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val c = small.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val v = (r * 299 + g * 587 + b * 114) / 1000
                grey[y * WIDTH + x] = v
                sum += v
            }
        }
        val avg = (sum / (WIDTH * HEIGHT)).toInt()
        val px = Array(HEIGHT) { BooleanArray(WIDTH) }
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                px[y][x] = grey[y * WIDTH + x] < avg
            }
        }
        return encode(px)
    }

    /** 写进码盘的自定义数据块（长度必须是 1024）。 */
    fun writeInto(image: ByteArray, bits: ByteArray): Boolean {
        // 没有这块就现建一块，机器上原来没设过图也能写
        val offset = CustomData.ensureBlock(image, CustomData.TYPE_IMAGE, SIZE) ?: return false
        System.arraycopy(bits, 0, image, CustomData.START + offset, SIZE)
        return true
    }
}
