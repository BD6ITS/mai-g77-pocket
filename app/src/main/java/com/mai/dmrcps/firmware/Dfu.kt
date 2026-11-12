package com.mai.dmrcps.firmware

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager

class DfuError(message: String) : Exception(message)

/**
 * STM32 的 DFU 刷写（DfuSe 那套）。
 *
 * 电脑版 CPS 用的是 ST 自家的 `STDFU.dll`，安卓没有，只能照 DfuSe 规范自己发控制传输：
 *
 *   DFU_DNLOAD(1) —— 往下写一块
 *   DFU_UPLOAD(2) —— 读回来
 *   DFU_GETSTATUS(3) —— 问状态，后面跟着要等多久
 *   DFU_CLRSTATUS(4) —— 清错误
 *   DFU_ABORT(6)
 *
 * DfuSe 在普通块之外还借 DNLOAD 的高字节下达命令：
 *   0x21 = 设置地址指针、0x41 = 擦除、0x41 不带地址 = 整片擦除。
 *
 * **这是整个 App 里唯一会动到底层、刷坏就只能靠电脑救的操作。**
 * 所以调用方必须先整片备份，写完再回读比对。
 */
class Dfu(private val manager: UsbManager, val device: UsbDevice) {

    private var conn: UsbDeviceConnection? = null
    private var epOut: UsbEndpoint? = null
    private var epIn: UsbEndpoint? = null
    var blockSize = 1024
        private set

    companion object {
        const val REQ_DETACH = 0
        const val REQ_DNLOAD = 1
        const val REQ_UPLOAD = 2
        const val REQ_GETSTATUS = 3
        const val REQ_CLRSTATUS = 4
        const val REQ_GETSTATE = 5
        const val REQ_ABORT = 6

        const val CMD_SET_ADDRESS = 0x21
        const val CMD_ERASE = 0x41

        /** STM32 的闪存起始地址。 */
        const val FLASH_BASE = 0x08000000L
    }

    fun open(): Boolean {
        val intf = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { it.interfaceClass == 0xFE && it.interfaceSubclass == 0x01 }
            ?: throw DfuError("这不是 DFU 设备")
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            when {
                ep.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT -> epOut = ep
                ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN -> epIn = ep
            }
        }
        if (epOut == null) throw DfuError("DFU 设备没有输出端点")
        val c = manager.openDevice(device) ?: throw DfuError("打不开设备（可能没给权限）")
        if (!c.claimInterface(intf, true)) {
            c.close()
            throw DfuError("接口被别的程序占着")
        }
        conn = c
        blockSize = epOut!!.maxPacketSize.coerceIn(64, 2048)
        return true
    }

    fun close() {
        runCatching { conn?.close() }
        conn = null
    }

    /** 发一条控制传输，返回读到的字节。 */
    private fun control(
        requestType: Int,
        request: Int,
        value: Int,
        data: ByteArray?,
        length: Int,
        timeout: Int = 5000,
    ): ByteArray {
        val c = conn ?: throw DfuError("还没连上")
        val buf = ByteArray(if (length > 0) length else 1)
        val n = c.controlTransfer(requestType, request, value, 0, data ?: buf, length, timeout)
        if (n < 0) throw DfuError("控制传输失败（request=$request value=$value）")
        return if (length > 0) buf.copyOf(n) else buf
    }

    /** 问状态：返回 (bState, 建议等待毫秒)。 */
    fun status(): Pair<Int, Int> {
        val r = control(0xA1, REQ_GETSTATUS, 0, null, 6)
        if (r.size < 6) throw DfuError("状态读不全")
        val wait = (r[1].toInt() and 0xFF) or
            ((r[2].toInt() and 0xFF) shl 8) or
            ((r[3].toInt() and 0xFF) shl 16)
        val state = r[4].toInt() and 0xFF
        if (state == 10) throw DfuError("设备报错了，先 DFU_CLEAR") // dfuERROR
        return state to wait
    }

    private fun clearStatus() {
        runCatching { control(0x21, REQ_CLRSTATUS, 0, null, 0) }
    }

    /** 整片擦除。这一步之后机器上原来的固件就没了。 */
    fun massErase() {
        clearStatus()
        control(0x21, REQ_DNLOAD, 0, byteArrayOf(CMD_ERASE.toByte()), 1)
        status()
        var waited = 0
        while (waited < 120_000) {
            val (state, wait) = status()
            if (state == 2) return   // dfuIDLE
            val sleep = wait.coerceIn(10, 2000)
            Thread.sleep(sleep.toLong())
            waited += sleep
        }
        throw DfuError("擦除超时")
    }

    private fun setAddress(addr: Long) {
        val data = ByteArray(5)
        data[0] = CMD_SET_ADDRESS.toByte()
        for (i in 0 until 4) data[4 - i] = ((addr shr (8 * i)) and 0xFF).toByte()
        control(0x21, REQ_DNLOAD, 0, data, data.size)
        status()
    }

    private fun erasePage(addr: Long) {
        val data = ByteArray(5)
        data[0] = CMD_ERASE.toByte()
        for (i in 0 until 4) data[4 - i] = ((addr shr (8 * i)) and 0xFF).toByte()
        control(0x21, REQ_DNLOAD, 0, data, data.size)
        status()
    }

    /**
     * 把固件写进闪存。[onProgress] 收 0..1。
     * 先整片擦、再设地址、再一块块写；写完回读比对由调用方做。
     */
    fun download(firmware: ByteArray, onProgress: ((Float) -> Unit)? = null) {
        massErase()
        setAddress(FLASH_BASE)
        erasePage(FLASH_BASE)

        var off = 0
        var block = 0
        while (off < firmware.size) {
            val n = minOf(blockSize, firmware.size - off)
            val chunk = firmware.copyOfRange(off, off + n)
            control(0x21, REQ_DNLOAD, block, chunk, chunk.size, timeout = 10000)
            status()
            off += n
            block++
            onProgress?.invoke(off.toFloat() / firmware.size)
        }
        // 收尾：发一块空的
        control(0x21, REQ_DNLOAD, block, null, 0)
        status()
    }

    /** 从闪存回读一段，用来比对。 */
    fun upload(address: Long, length: Int): ByteArray {
        clearStatus()
        setAddress(address)
        val out = ByteArray(length)
        var off = 0
        var block = 0
        while (off < length) {
            val n = minOf(blockSize, length - off)
            control(0xA1, REQ_UPLOAD, block, null, n)
            val got = control(0xA1, REQ_UPLOAD, block, null, n)
            System.arraycopy(got, 0, out, off, minOf(got.size, n))
            off += n
            block++
        }
        return out
    }

    /** 让设备离开 DFU，跳回去跑新固件。 */
    fun leave() {
        runCatching { control(0x21, REQ_DNLOAD, 0, null, 0) }
        runCatching { control(0x21, REQ_ABORT, 0, null, 0) }
    }
}
