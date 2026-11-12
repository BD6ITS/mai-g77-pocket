package com.mai.dmrcps.protocol

import android.util.Log
import com.mai.dmrcps.usb.UsbCps

class CpsProtocolError(message: String) : Exception(message)

/** 机型枚举，数值照 OpenGD77 CPS 的 RadioType。MD-390 刷 OpenGD77 后报 MD380 = 7。 */
enum class RadioModel(val code: Int, val label: String) {
    GD77(0, "GD-77"),
    GD77S(1, "GD-77S"),
    DM1801(2, "DM-1801"),
    RD5R(3, "RD-5R"),
    DM1801A(4, "DM-1801A"),
    MD9600(5, "MD-9600"),
    MDUV380(6, "MD-UV380"),
    MD380(7, "MD-380 / MD-390"),
    DM1701_BGR(8, "DM-1701 (BGR)"),
    MD2017(9, "MD-2017"),
    DM1701_RGB(10, "DM-1701 (RGB)");

    val isStm32: Boolean
        get() = this in setOf(MD9600, MDUV380, MD380, DM1701_BGR, MD2017, DM1701_RGB)

    companion object {
        fun of(code: Int): RadioModel? = entries.firstOrNull { it.code == code }
    }
}

object MemType {
    const val FLASH = 1
    const val EEPROM = 2
    const val RADIO_INFO = 9
}

data class RadioInfo(
    val structVersion: Long,
    val model: RadioModel?,
    val rawModel: Int,
    val gitRevision: String,
    val buildDateTime: String,
    val flashId: Long,
) {
    val modelLabel: String get() = model?.label ?: "未知型号 ($rawModel)"
}

data class MemoryPart(val memType: Int, val radioAddr: Long, val size: Int)

/** 写入用的一段：镜像里的偏移、机器的地址、长度。 */
data class WritePart(val fileOffset: Int, val radioAddr: Long, val length: Int)

/**
 * OpenGD77 的 Codeplug 传输层。帧格式全部照 CPS 的 `SerialComms.cs`：
 *
 *   读：`R` + 类型 + 地址(4B 大端) + 长度(2B 大端) → 长度(2B) + 数据
 *   写：命令 + 操作 + 地址(4B) + 长度(2B) + 数据 → 回显前两字节算确认
 *
 * **STM32 机型（MD380/MDUV380/MD9600…）用 'X' 当写命令**，而且 FLASH 地址要 +0x20000
 * ——这是模拟 EEPROM 的机器，代码段从 0x20000 开始。缓冲 1024（固件日期 > 20211002）。
 */
class Cps(private val usb: UsbCps) {

    var channelBuffer = 32
        private set
    private var writeCommand = 'W'.code.toByte()
    private var emulatedEeprom = false
    private val MEMTYPE_SCREENGRAB = 6
    private val SCREENGRAB_SIZE = 40960
    private val MEMTYPE_MCU_ROM = 5
    private val MCU_ROM_SIZE = 1048576

    /** 读到机器信息以后调一次，把缓冲、写命令、地址偏移这些按机型配好。 */
    fun configure(info: RadioInfo) {
        val build = info.buildDateTime.take(8).toIntOrNull()
        channelBuffer = if (build != null && build > 20211002) 1024 else 32
        emulatedEeprom = info.model?.isStm32 == true
        writeCommand = if (emulatedEeprom) 'X'.code.toByte() else 'W'.code.toByte()
    }

    private fun radioAddress(memType: Int, addr: Long): Long =
        if (memType == MemType.FLASH && emulatedEeprom) addr + 0x20000 else addr

    // ---------------- 控制命令 ----------------

    private fun sendCmd(command: Int, payload: ByteArray = ByteArray(0)) {
        val frame = ByteArray(2 + payload.size)
        frame[0] = 'C'.code.toByte()
        frame[1] = command.toByte()
        System.arraycopy(payload, 0, frame, 2, payload.size)
        usb.write(frame)
        // 必须从接收线程的缓冲里拿，不能自己去 bulkTransfer——否则两边抢同一包数据
        val ack = usb.take(1, 1500)
        if (ack.size != 1 || ack[0] != '-'.code.toByte()) {
            throw CpsProtocolError("命令 $command 没被确认（收到 ${ack.size} 字节）")
        }
    }

    /**
     * 进编程模式：CPS 在读任何东西之前先发 `C 0`，机器回 `-` 才算接上。
     * 不发这一句，机器根本不理你的 R/W 帧。
     */
    fun connectToRadio() {
        // 照 CPS 的 readOpenGD77RadioInfo：C0 → C1 → 四条屏幕文字 → C3 → C6 4
        var ok = false
        for (attempt in 1..2) {
            try {
                sendCmd(0)
                ok = true
                break
            } catch (e: CpsProtocolError) {
                Log.w("dmrcps", "C0 第 $attempt 次没被确认，等一下再来")
                Thread.sleep(400)
            }
        }
        if (!ok) throw CpsProtocolError("C0 握手没回应（机器可能已卡死，拔电重启再试）")
        sendCmd(1)
        sendCmd(2, byteArrayOf(0, 0x00, 0x03, 0x01, 0x00) + "CPS ".toByteArray(Charsets.US_ASCII))
        sendCmd(2, byteArrayOf(0, 0x10, 0x03, 0x01, 0x00) + "Read".toByteArray(Charsets.US_ASCII))
        sendCmd(2, byteArrayOf(0, 0x20, 0x03, 0x01, 0x00) + "Radi".toByteArray(Charsets.US_ASCII))
        sendCmd(2, byteArrayOf(0, 0x30, 0x03, 0x01, 0x00) + "Info".toByteArray(Charsets.US_ASCII))
        sendCmd(3)
        sendCmd(6, byteArrayOf(4))
        Log.i("dmrcps", "开场命令发完（C0/C1/C2×4/C3/C6）")
    }

    /** 收尾：CPS 在读完以后发 C5、C7 退出。 */
    fun leaveRadio() {
        runCatching { sendCmd(5) }
        runCatching { sendCmd(7) }
    }

    fun showText(line: String) {
        val text = line.toByteArray(Charsets.US_ASCII).copyOf(16)
        sendCmd(2, byteArrayOf(0x00, 0x20, 0x03, 0x01, 0x00) + text)
    }

    fun flashGreen() = sendCmd(6, byteArrayOf(3))
    fun flashRed() = sendCmd(6, byteArrayOf(4))
    fun save() = sendCmd(6, byteArrayOf(2))

    // ---------------- 读 ----------------

    fun readRadioInfo(stealth: Boolean = false): RadioInfo {
        // 静默模式只握手、不写屏幕文字，机器不会切到「读取」画面。
        // CPS 取屏幕截图时就是这么干的（readOpenGD77RadioInfo(port, stealth: true)）。
        if (stealth) {
            runCatching { sendCmd(0) }
            runCatching { sendCmd(6, byteArrayOf(4)) }
        } else {
            connectToRadio()
        }
        val header = try {
            requestRead(MemType.RADIO_INFO, 0, 0)
        } catch (e: CpsProtocolError) {
            if (!stealth) throw e
            // 静默拿不到就退回完整开场，屏幕上会出现一瞬间的读取画面
            connectToRadio()
            requestRead(MemType.RADIO_INFO, 0, 0)
        }
        val raw = header
        // 机器回的是小端：03 00 00 00 = 结构版本 3，06 00 00 00 = 机型 6
        fun u32(at: Int): Long {
            var v = 0L
            for (i in 3 downTo 0) v = (v shl 8) or (raw[at + i].toLong() and 0xFF)
            return v
        }
        fun cstr(at: Int, size: Int): String {
            val bytes = raw.copyOfRange(at, at + size)
            val end = bytes.indexOf(0).let { if (it < 0) size else it }
            return String(bytes, 0, end, Charsets.US_ASCII).trim()
        }
        val info = RadioInfo(
            structVersion = u32(0),
            rawModel = u32(4).toInt(),
            model = RadioModel.of(u32(4).toInt()),
            gitRevision = cstr(8, 16),
            buildDateTime = cstr(24, 16),
            flashId = u32(40),
        )
        configure(info)
        return info
    }

    /**
     * 发一帧读请求，把回帧的数据部分交回去。
     *
     * 收法跟串口驱动一样：USB 那边有个线程一直在收，我们只从缓冲里按长度取。
     * 取不满或首字节不对就丢掉重来，最多 4 次。
     */
    private fun requestRead(memType: Int, addr: Long, requested: Int): ByteArray {
        val len = if (requested == 0) 0 else requested.coerceAtMost(channelBuffer)
        var lastError = ""
        for (attempt in 1..4) {
            val frame = ByteArray(8)
            frame[0] = 'R'.code.toByte()
            frame[1] = memType.toByte()
            frame[2] = (addr shr 24).toByte()
            frame[3] = (addr shr 16).toByte()
            frame[4] = (addr shr 8).toByte()
            frame[5] = addr.toByte()
            frame[6] = (len shr 8).toByte()
            frame[7] = len.toByte()
            usb.write(frame)

            val header = usb.take(3, 3000)
            if (header.size < 3) {
                lastError = "帧头只回来 ${header.size} 字节"
            } else if (header[0] != 'R'.code.toByte()) {
                lastError = "首字节 0x%02X 不是 R".format(header[0].toInt() and 0xFF)
            } else {
                val gotLen = ((header[1].toInt() and 0xFF) shl 8) or (header[2].toInt() and 0xFF)
                if (gotLen == 0) return ByteArray(0)
                val data = usb.take(gotLen, 5000)
                if (data.size >= gotLen) return data.copyOf(gotLen)
                lastError = "数据只收到 ${data.size}/$gotLen"
            }
            Log.w("dmrcps", "第 $attempt 次失败（0x%05X）：$lastError，排空重来".format(addr))
            usb.drainInput()
        }
        throw CpsProtocolError("读 0x%05X 四次都没成：%s".format(addr, lastError))
    }

    /**
     * 切到校准区。CPS 在读校准数据之前会先发 `C 02 00 20 03 01 00 "Calibration"`，
     * 不发这一条，按码盘地址读到的是空区。
     */
    /**
     * 备份 MCU ROM（固件本体，STM32 机型 1 MB）。CPS 里是 mode=5、地址 0、整片读，
     * 读之前走 C1 → 三条屏幕字 → C3 → C6 3，读完 C5、C7 收尾。纯只读。
     */
    fun readMcuRom(onBytes: ((Int) -> Unit)? = null): ByteArray {
        sendCmd(1)
        sendCmd(2, byteArrayOf(0, 0, 3, 1, 0) + "CPS ".toByteArray(Charsets.US_ASCII))
        sendCmd(2, byteArrayOf(0, 16, 3, 1, 0) + "Backup".toByteArray(Charsets.US_ASCII))
        sendCmd(2, byteArrayOf(0, 32, 3, 1, 0) + "MCU ROM".toByteArray(Charsets.US_ASCII))
        sendCmd(3)
        sendCmd(6, byteArrayOf(3))
        val data = readMemory(MEMTYPE_MCU_ROM, 0L, MCU_ROM_SIZE, onBytes)
        runCatching { sendCmd(5) }
        runCatching { sendCmd(7) }
        return data
    }
    /**
     * 取一张屏幕截图。CPS 里是 mode=6（ReadScreenGrab）、地址 0、长度 40960，
     * 160×128 每像素 2 字节 RGB565，读完发 `C 7` 收尾。
     */
    fun readScreengrab(): ByteArray {
        val data = readMemory(MEMTYPE_SCREENGRAB, 0L, SCREENGRAB_SIZE)
        runCatching { sendCmd(7) }
        return data
    }
    fun enterCalibrationMode() {
        sendCmd(2, byteArrayOf(0, 32, 3, 1, 0) + "Calibration".toByteArray(Charsets.US_ASCII))
    }
    fun readMemory(
        memType: Int,
        addr: Long,
        length: Int,
        onBytes: ((Int) -> Unit)? = null,
    ): ByteArray {
        val base = radioAddress(memType, addr)
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            // 块长照 CPS：getUSBReadBufferSize()
            val want = (length - offset).coerceAtMost(channelBuffer)
            val data = requestRead(memType, base + offset, want)
            if (data.isEmpty()) {
                throw CpsProtocolError("读 0x%05X 时机器没给数据".format(base + offset))
            }
            val n = minOf(data.size, want)
            System.arraycopy(data, 0, out, offset, n)
            offset += n
            onBytes?.invoke(offset)
            if (n < want) break
        }
        return out
    }

    // ---------------- 写 ----------------

    private fun writeFrame(frame: ByteArray) {
        usb.write(frame)
        // 确认 = 回显前两个字节
        val echo = usb.take(2, 3000)
        if (echo.size != 2 || echo[0] != frame[0] || echo[1] != frame[1]) {
            throw CpsProtocolError(
                "写操作没被确认（想要 ${frame[0].toInt()}/${frame[1].toInt()}，收到 ${echo.size} 字节）"
            )
        }
    }

    private fun uploadChunks(op: Int, baseAddr: Long, data: ByteArray, onBytes: ((Int) -> Unit)?) {
        var written = 0
        while (written < data.size) {
            val n = (data.size - written).coerceAtMost(channelBuffer - 8)
            val addr = baseAddr + written
            val frame = ByteArray(8 + n)
            frame[0] = writeCommand
            frame[1] = op.toByte()
            frame[2] = (addr shr 24).toByte()
            frame[3] = (addr shr 16).toByte()
            frame[4] = (addr shr 8).toByte()
            frame[5] = addr.toByte()
            frame[6] = (n shr 8).toByte()
            frame[7] = n.toByte()
            System.arraycopy(data, written, frame, 8, n)
            writeFrame(frame)
            written += n
            onBytes?.invoke(written)
        }
    }

    /**
     * 写一段内存。帧格式照 CPS 的 flashWritePrepareSector / flashSendData：
     *
     *   准备扇区：`X 01 <扇区号3字节>`，发完等 50 ms，再收回显（X 01）
     *   写数据：  `X 02 <地址4> <长度2> <数据>`，收回显（X 02）
     *
     * 一块不能跨扇区，跨了就换扇区重新准备。
     */
    fun writeMemory(
        memType: Int,
        addr: Long,
        data: ByteArray,
        onBytes: ((Int) -> Unit)? = null,
    ) {
        val base = radioAddress(memType, addr)
        var off = 0
        var lastSector = -1
        while (off < data.size) {
            val abs = base + off
            val sector = (abs / SECTOR_SIZE).toInt()
            if (sector != lastSector) {
                prepareSector(sector)
                lastSector = sector
            }
            val toSectorEnd = (SECTOR_SIZE - (abs % SECTOR_SIZE)).toInt()
            val len = minOf(WRITE_CHUNK, toSectorEnd, data.size - off)
            sendDataFrame(abs, data, off, len)
            off += len
            onBytes?.invoke(off)
        }
    }

    private fun prepareSector(sector: Int) {
        // CPS 的做法：写 5 字节 → 等 50 ms → 读当时缓冲里有的（不一定正好 2 字节）→ 比前两字节
        usb.drainInput()
        val frame = byteArrayOf(
            writeCommand,
            1,
            ((sector shr 16) and 0xFF).toByte(),
            ((sector shr 8) and 0xFF).toByte(),
            (sector and 0xFF).toByte(),
        )
        usb.write(frame)
        Thread.sleep(50)
        // CPS 只等 100 ms，但 0.39.0 那次回显 850 ms 才到，先放宽到 1500
        val echo = usb.take(2, 1500)
        Log.i("dmrcps", "扇区 $sector 准备回显：${echo.size} 字节 " + echo.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
        if (echo.size < 2) {
            throw CpsProtocolError("扇区 $sector 准备失败（只回 ${echo.size} 字节）")
        }
        if (echo[0] != frame[0] || echo[1] != frame[1]) {
            throw CpsProtocolError(
                "扇区 $sector 准备回显不对：%02X %02X（期望 %02X %02X）".format(
                    echo[0].toInt() and 0xFF, echo[1].toInt() and 0xFF,
                    frame[0].toInt() and 0xFF, frame[1].toInt() and 0xFF,
                )
            )
        }
    }

    /**
     * 一个扇区写完要提交：`X 03`（CPS 的 flashWriteSector）。
     * 少这一步机器就一直在等提交，下一个 `X 01` 只会回一个 `-`。
     */
    private fun commitSector() {
        usb.drainInput()
        val frame = byteArrayOf(writeCommand, 3)
        usb.write(frame)
        Thread.sleep(100)
        val echo = usb.take(2, 1500)
        Log.i("dmrcps", "扇区提交回显：${echo.size} 字节 " + echo.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
        if (echo.size < 2 || echo[0] != frame[0] || echo[1] != frame[1]) {
            throw CpsProtocolError("扇区提交没被确认（回显 ${echo.size} 字节）")
        }
    }

    private fun sendDataFrame(address: Long, data: ByteArray, offset: Int, len: Int) {
        val frame = ByteArray(8 + len)
        frame[0] = writeCommand
        frame[1] = 2
        frame[2] = (address shr 24).toByte()
        frame[3] = (address shr 16).toByte()
        frame[4] = (address shr 8).toByte()
        frame[5] = address.toByte()
        frame[6] = (len shr 8).toByte()
        frame[7] = len.toByte()
        System.arraycopy(data, offset, frame, 8, len)
        usb.write(frame)
        // CPS 的 flashSendData 在这里停 20 ms 再收，补上
        Thread.sleep(20)
        val echo = usb.take(2, 1500)
        if (echo.size < 2 || echo[0] != frame[0] || echo[1] != frame[1]) {
            throw CpsProtocolError("写 0x%05X 没被确认（回显 ${echo.size} 字节）".format(address))
        }
    }

    /** 写之前照 CPS 再发一遍：C1、三条屏幕文字、C3、C6 4、C6 2。 */
    fun beginWrite() {
        sendCmd(1)
        sendCmd(2, byteArrayOf(0, 0x00, 0x03, 0x01, 0x00) + "CPS ".toByteArray(Charsets.US_ASCII))
        sendCmd(2, byteArrayOf(0, 0x10, 0x03, 0x01, 0x00) + "Writ".toByteArray(Charsets.US_ASCII))
        sendCmd(2, byteArrayOf(0, 0x20, 0x03, 0x01, 0x00) + "Code".toByteArray(Charsets.US_ASCII))
        sendCmd(3)
        sendCmd(6, byteArrayOf(4))
        sendCmd(6, byteArrayOf(2))
        Log.i("dmrcps", "写入模式已进（C1/C2×3/C3/C6 4/C6 2）")
    }

    /** 写完的收尾：CPS 只发一条 C6（不带参数）。 */
    fun endWrite() {
        runCatching { sendCmd(6) }
    }

    /** 把整份 128 KB 镜像写回去。分段照 CPS 的 WRITE_CODEPLUG（地址是机器地址，不要再套偏移）。 */
    fun writeCodeplug(image: ByteArray, onProgress: ((Int, Int) -> Unit)? = null) {
        if (image.size < CODEPLUG_SIZE) throw CpsProtocolError("镜像尺寸不对")
        for (part in WRITE_PARTS) {
            val slice = image.copyOfRange(part.fileOffset, part.fileOffset + part.length)
            writeRaw(part.radioAddr, slice) { n ->
                onProgress?.invoke(part.fileOffset + n, CODEPLUG_SIZE)
            }
        }
    }

    /** 写到指定的机器地址（不做任何偏移）。校准区也用这个写。 */
    fun writeRaw(radioAddr: Long, data: ByteArray, onBytes: ((Int) -> Unit)? = null) {
        var off = 0
        var lastSector = -1
        while (off < data.size) {
            val abs = radioAddr + off
            val sector = (abs / SECTOR_SIZE).toInt()
            if (sector != lastSector) {
                prepareSector(sector)
                lastSector = sector
            }
            val toSectorEnd = (SECTOR_SIZE - (abs % SECTOR_SIZE)).toInt()
            val len = minOf(WRITE_CHUNK, toSectorEnd, data.size - off)
            sendDataFrame(abs, data, off, len)
            off += len
            onBytes?.invoke(off)
            // 扇区写满就提交（CPS 的 flashWriteSector），最后一块也要提交
            val nextSector = ((radioAddr + off) / SECTOR_SIZE).toInt()
            if (nextSector != sector || off >= data.size) {
                commitSector()
                lastSector = -1
            }
        }
    }

    // ---------------- 码盘 ----------------

    fun readCodeplug(onProgress: ((Int, Int) -> Unit)? = null): ByteArray {
        val image = ByteArray(CODEPLUG_SIZE)
        var fileOffset = 0
        for (part in CODEPLUG_PARTS) {
            val start = fileOffset
            val data = readMemory(part.memType, part.radioAddr, part.size) { n ->
                onProgress?.invoke(start + n, CODEPLUG_SIZE)
            }
            System.arraycopy(data, 0, image, fileOffset, part.size)
            fileOffset += part.size
        }
        return image
    }

    companion object {
        const val CODEPLUG_SIZE = 0x20000

        /** 一个闪存扇区 4096 字节，写之前要逐扇区准备。 */
        private const val SECTOR_SIZE = 4096L

        /** 一帧最多写 1024 字节。 */
        private const val WRITE_CHUNK = 1024

        private const val OP_FLASH_PREPARE = 1
        private const val OP_FLASH = 2
        private const val OP_FLASH_COMMIT = 3
        private const val OP_EEPROM = 4

        /**
         * 写入用的三段（照 CPS 的 WRITE_CODEPLUG，地址已是机器地址）。
         *
         * 三段必须**首尾相接、覆盖到 0x20000**：
         *   file 0x0080-0x0B000 → radio 0x0080-0x0B000
         *   file 0x0B000-0x1EE60 → radio 0x9B000-0xB3E60（+0x20000 偏移）
         *   file 0x1EE60-0x20000 → radio 0x20000-0x211A0（自定义数据块）
         *
         * 0.41.2 之前第二段的 file 偏移写成 0x6000、第三段长度写成 0x111A0，
         * 结果一是整段错位 0x5000，二是越界崩在 toIndex 0x30000。
         */
        val WRITE_PARTS = listOf(
            WritePart(fileOffset = 0x00080, radioAddr = 0x000080, length = 0x0AF80),
            WritePart(fileOffset = 0x0B000, radioAddr = 0x9B000, length = 0x13E60),
            WritePart(fileOffset = 0x1EE60, radioAddr = 0x20000, length = 0x011A0),
        )

        /** 读的时候用的三段：EEPROM 0xB000，FLASH 0x7B000 起 0x13E60，FLASH 0x0 起 0x11A0。 */
        val CODEPLUG_PARTS = listOf(
            MemoryPart(MemType.EEPROM, 0x00000, 0xB000),
            MemoryPart(MemType.FLASH, 0x7B000, 0x13E60),
            MemoryPart(MemType.FLASH, 0x00000, 0x011A0),
        )
    }
}
