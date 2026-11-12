package com.mai.dmrcps.firmware

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.util.zip.CRC32

/** 一份要刷进机器的固件。 */
data class FirmwareFile(
    val name: String,
    val bytes: ByteArray,
) {
    val size: Int get() = bytes.size
    val crc: Long
        get() {
            val c = CRC32()
            c.update(bytes)
            return c.value
        }

    /**
     * 做个最基本的体检查。
     * OpenGD77 的固件是给 STM32F405 用的，正常在几百 KB 量级；
     * 太小可能是下载没下全，太大多半不是这份固件。
     */
    fun sanity(): String? = when {
        size < 32 * 1024 -> "文件只有 ${size / 1024} KB，不像完整固件"
        size > 1024 * 1024 -> "文件有 ${size / 1024} KB，比这台机器的闪存还大"
        else -> null
    }
}

/**
 * DFU 设备探测。
 *
 * 固件升级走的是 ST 的 DFU 协议（电脑版 CPS 调的是 `STDFU_*.dll`），
 * 机器要先进 bootloader 才会以 DFU 设备的身份出现：
 * 接口类 0xFE、子类 0x01。这时候它已经**不是**那个能读写码盘的串口设备了。
 */
object DfuProbe {

    const val CLASS_DFU = 0xFE
    const val SUBCLASS_DFU = 0x01

    fun isDfu(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == CLASS_DFU && intf.interfaceSubclass == SUBCLASS_DFU) return true
        }
        return false
    }

    fun devices(context: Context): List<UsbDevice> {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return manager.deviceList.values.filter { isDfu(it) }
    }

    fun describe(device: UsbDevice): String =
        "${device.productName ?: device.deviceName}  VID %04X PID %04X"
            .format(device.vendorId, device.productId)
}
