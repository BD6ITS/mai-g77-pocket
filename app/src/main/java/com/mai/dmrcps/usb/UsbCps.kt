package com.mai.dmrcps.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.Closeable

/**
 * 对讲机的 USB 传输层。
 *
 * OpenGD77 的机器是「直驱」：线里没有转换芯片，对讲机自己的 USB 口枚举成一个
 * 设备（STM32 的 CDC-ACM 虚拟串口）。安卓这边不用驱动，claim 数据接口，
 * 用 bulk 端点收发就行。
 *
 * 帧格式照 OpenGD77 CPS 的 SerialComms.cs：
 *   'C'(0x43) + 命令 + 参数        → 回 '-' (0x2D) 表示收到
 *   'R'(0x52) + mode + 地址(4B 大端) + 长度(2B 大端)
 *                                  → 回 c(1B) + 实际长度(2B 大端) + 数据
 *   'W'(0x57) + 同上                （写）
 */
class UsbCps private constructor(
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint,
    val deviceName: String,
) : Closeable {

    // ---- 接收线程：像串口驱动那样，一直把 IN 端点的数据收进缓冲 ----
    private val rxLock = Object()
    private val rxBuf = java.io.ByteArrayOutputStream(8192)
    @Volatile private var rxRunning = true
    private var rxThread: Thread? = null

    private fun startReader() {
        if (rxThread != null) return
        rxRunning = true
        rxThread = Thread {
            val buf = ByteArray(4096)
            while (rxRunning) {
                try {
                    val n = connection.bulkTransfer(epIn, buf, buf.size, 50)
                    if (n > 0) {
                        synchronized(rxLock) {
                            rxBuf.write(buf, 0, n)
                            rxLock.notifyAll()
                        }
                    }
                } catch (e: Exception) {
                    if (!rxRunning) break
                    Thread.sleep(20)
                }
            }
        }.apply { isDaemon = true; name = "usb-rx"; start() }
    }

    /** 从接收缓冲里取满 n 字节；超时返回已有的。 */
    fun take(n: Int, timeoutMs: Int = 3000): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(rxLock) {
            while (rxBuf.size() < n) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                rxLock.wait(left.coerceAtMost(100))
            }
            val all = rxBuf.toByteArray()
            val take = minOf(n, all.size)
            rxBuf.reset()
            if (take < all.size) rxBuf.write(all, take, all.size - take)
            val out = all.copyOf(take)
            if (out.isNotEmpty()) {
                val head = out.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
                Log.i(TAG, "← ${out.size} 字节: $head" + if (out.size > 16) " …" else "")
            }
            return out
        }
    }

    /** 把缓冲里现有的字节全丢掉。 */
    fun drainInput(): Int = synchronized(rxLock) {
        val n = rxBuf.size()
        rxBuf.reset()
        n
    }

    /** 原样发一段字节，返回实际发出的长度。 */
    fun write(bytes: ByteArray, timeoutMs: Int = 2000): Int {
        val n = connection.bulkTransfer(epOut, bytes, bytes.size, timeoutMs)
        Log.i(TAG, "→ ${hex(bytes)}  (写了 $n 字节, 端点 ${epOut.address.toString(16)})")
        return n
    }

    /** 把还挂着的输入全读掉（开串口后先清一次，免得被开头的垃圾带偏）。 */
    fun flushInput() {
        val junk = ArrayList<Byte>()
        repeat(16) {
            val buf = ByteArray(64)
            val n = connection.bulkTransfer(epIn, buf, buf.size, 30)
            if (n <= 0) return@repeat
            for (i in 0 until n) junk.add(buf[i])
        }
        if (junk.isNotEmpty()) {
            Log.i(TAG, "清掉残留 ${junk.size} 字节: ${hex(junk.toByteArray())}")
        }
    }

    /** 读 len 个字节（最多等 timeoutMs）。读不满就返回读到的那些。 */
    fun read(len: Int, timeoutMs: Int = 3000): ByteArray {
        val out = ByteArray(len)
        var got = 0
        while (got < len) {
            // bulkTransfer 只能从 buffer[0] 开始填，所以每段先读进临时数组再拷
            val chunk = ByteArray(len - got)
            val n = connection.bulkTransfer(epIn, chunk, chunk.size, timeoutMs)
            if (n <= 0) {
                Log.w(TAG, "← 读超时/空（要 $len 字节，已收 $got）")
                break
            }
            Log.i(TAG, "← ${hex(chunk.copyOf(n))}")
            System.arraycopy(chunk, 0, out, got, n)
            got += n
        }
        return if (got == len) out else out.copyOf(got)
    }

    /**
     * 照 CPS 的收法：隔 5 ms 问一次「有没有数据」，最多问 attempts 次，
     * 一有数据就把当时能拿的全拿走（不是死等固定字节数）。
     */
    fun readAvailable(max: Int = 1032, pollMs: Int = 5, attempts: Int = 100): ByteArray {
        val buf = ByteArray(max)
        repeat(attempts) {
            val n = connection.bulkTransfer(epIn, buf, max, pollMs)
            if (n > 0) {
                val got = buf.copyOf(n)
                Log.i(TAG, "← ${hex(got)}")
                return got
            }
        }
        Log.w(TAG, "← 500 ms 内没收到任何字节")
        return ByteArray(0)
    }

    /** 精确收满 len 个字节；超时收不满就返回已经收到的。 */
    fun readExact(len: Int, timeoutMs: Int = 2000): ByteArray {
        val out = ByteArray(len)
        var got = 0
        val deadline = System.currentTimeMillis() + timeoutMs
        while (got < len) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) break
            val chunk = ByteArray(len - got)
            val n = connection.bulkTransfer(epIn, chunk, chunk.size, left.coerceAtMost(200L).toInt())
            if (n > 0) {
                System.arraycopy(chunk, 0, out, got, n)
                got += n
            }
        }
        return if (got == len) out else out.copyOf(got)
    }

    /** 已经收到的这些之外，继续补齐到 len。 */
    fun readMore(already: ByteArray, want: Int): ByteArray {
        val out = ArrayList<Byte>(want)
        already.forEach { out.add(it) }
        var guard = 0
        while (out.size < want && guard++ < 200) {
            val n = connection.bulkTransfer(epIn, ByteArray(want - out.size), want - out.size, 20)
            if (n <= 0) continue
        }
        return already
    }

    override fun close() {
        runCatching { connection.releaseInterface(iface) }
        runCatching { connection.close() }
    }

    companion object {
        const val TAG = "dmrcps"

        /**
         * 找一台像对讲机的 USB 设备：接口里带 bulk 端点对就行。
         * 现在先不按 VID/PID 卡死——型号太多，等真机插上把 VID/PID 记下来再加白名单。
         */
        fun pickDevice(context: Context): UsbDevice? {
            val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
            return manager.deviceList.values.firstOrNull { hasBulkPair(it) }
        }

        fun hasBulkPair(device: UsbDevice): Boolean {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
                }
                if (inEp != null && outEp != null) return true
            }
            return false
        }

        /** 打开设备（调用前要确保已经拿到 USB 权限）。 */
        fun open(context: Context, device: UsbDevice, timeoutMs: Int = 3000): UsbCps? {
            val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
            val connection = manager.openDevice(device) ?: return null

            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
                }
                if (inEp == null || outEp == null) continue
                if (!connection.claimInterface(intf, true)) continue
                Log.i(TAG, "claim 接口 ${intf.id}（类 ${intf.interfaceClass}/${intf.interfaceSubclass}）" +
                    " OUT=0x${outEp.address.toString(16)} IN=0x${inEp.address.toString(16)}" +
                    " maxPacket=${outEp.maxPacketSize}")
                runCatching { configureCdc(connection, device, timeoutMs) }
                val link = UsbCps(connection, intf, inEp, outEp, device.deviceName)
                // 先把设备话说完再开口
                Thread.sleep(150)
                link.flushInput()
                link.startReader()
                Thread.sleep(150)
                return link
            }
            connection.close()
            return null
        }

        /**
         * CDC-ACM 通常要先把线路参数写进去（9600 8N1）它才肯好好传数据。
         * 对讲机不一定吃这一套，出错就算了，不影响后面的 bulk 读写。
         */
        private fun hex(b: ByteArray): String =
            b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

        private fun configureCdc(connection: UsbDeviceConnection, device: UsbDevice, timeoutMs: Int) {
            // 真 CPS 建的是 SerialPort(端口, 115200, None, 8, One)，不是 9600
            val baud = 115200
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass != UsbConstants.USB_CLASS_COMM) continue
                // SET_LINE_CODING: bmRequestType=0x21, bRequest=0x20, wValue=0, wIndex=接口号
                val payload = byteArrayOf(
                    (baud and 0xFF).toByte(),
                    ((baud shr 8) and 0xFF).toByte(),
                    ((baud shr 16) and 0xFF).toByte(),
                    ((baud shr 24) and 0xFF).toByte(),
                    0x00, // 1 停止位
                    0x00, // 无校验
                    0x08, // 8 数据位
                )
                val r1 = connection.controlTransfer(0x21, 0x20, 0, intf.id, payload, payload.size, timeoutMs)
                // SET_CONTROL_LINE_STATE: DTR=1 RTS=1，很多固件要看到 DTR 才肯把数据往串口那头递
                val r2 = connection.controlTransfer(0x21, 0x22, 0x03, intf.id, null, 0, timeoutMs)
                Log.i(TAG, "CDC 设置：接口 ${intf.id} 波特率 $baud → lineCoding=$r1 controlLineState=$r2")
            }
        }
    }
}
