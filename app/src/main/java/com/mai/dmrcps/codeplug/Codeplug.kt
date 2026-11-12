package com.mai.dmrcps.codeplug

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * 码盘里各段的地址和长度。
 *
 * 全部来自 OpenGD77 CPS 的 `CPFormatOGD77.cs`（地址常数）＋ 固件 `codeplug.h`（结构体），
 * 两边对过，跟真机读出来的 128 KB 镜像一一对应。
 */
object CodeplugLayout {
    const val IMAGE_SIZE = 0x20000

    /** 用户呼号（8 字节）和 DMR ID（后面 4 字节 BCD）。 */
    const val USER_CALLSIGN = 0x00E0
    const val USER_DMRID = 0x00E8

    /** 开机画面：0x01 = 显示文字、0x00 = 图片；文字两行各 16 字节。 */
    const val BOOT_INTRO_SCREEN = 0x7518
    const val BOOT_LINE1 = 0x7540
    const val BOOT_LINE2 = 0x7550

    /** 信道：先是 16 字节头（条数），再是每条 56 字节。 */
    const val CHANNEL_HEADER = 0x3780
    const val CHANNEL_DATA = 0x3790
    const val CHANNEL_SIZE = 56

    /** 联系人：每条 24 字节（名字 16 + 号码 4 + 呼叫类型 1 + 铃声 1 + 铃声样式 1 + 时隙覆盖 1）。 */
    const val CONTACTS_START = 0x17620
    const val CONTACT_SIZE = 24
    const val CONTACTS_MAX = 1024

    /** 接收组（通话组列表）：每条 80 字节（名字 16 + 32 个联系人索引各 2 字节）。 */
    const val RX_GROUP_START = 0x1D6A0
    const val RX_GROUP_SIZE = 80
    const val RX_GROUP_MAX = 76

    const val NAME_SIZE = 16

    /** DTMF 联系人：每条 32 字节（名字 16 + 号码 16）。 */
    const val DTMF_START = 0x2F88
    const val DTMF_SIZE = 32
    const val DTMF_MAX = 63

    /** 总体设置：40 字节，从 0x00E0 开始。 */
    const val GENERAL_SET = 0x00E0

    /** 快捷短信：32 条，长度表 32 字节 + 保留 32 + 正文池 4608（每条 144）。 */
    const val TEXT_MSG = 0x0128
    const val TEXT_MSG_COUNT = 32
    const val TEXT_MSG_SLOT = 144

    /** 扫描表（扩展区）：主信道两组各 64 条、指定信道 64 条、扫描列表 2048 条，都是 u16、1 起算。 */
    const val SCAN_PRI1 = 44816
    const val SCAN_PRI2 = 44944
    const val SCAN_SPECIFY = 45072
    const val SCAN_LIST = 45200
    const val SCAN_PRI_COUNT = 64
    const val SCAN_LIST_COUNT = 2048

    /** APRS 条目：8 条，每条 64 字节。 */
    const val APRS_START = 0x1588
    const val APRS_SIZE = 64

    /** 侧键：8 字节（紧急短按时长、长按时长、SK1 短/长、SK2 短/长、TK 短/长）。 */
    const val SIDE_KEY = 0x0108

    /** VFO：A 和 B 两条，都是和信道一样的 56 字节结构。 */
    const val VFO_A = 0x7590
    const val VFO_SIZE = 56

    /** 区域（扩展区）：0x8010 起 32 字节是「哪些区域在用」的位图，0x8030 起每个区域 176 字节。 */
    const val EX_ZONE_INUSE = 0x8010
    const val EX_ZONE_LIST = 0x8030
    const val ZONE_SIZE = 176
    const val ZONES_MAX = 68
}

/** 一条信道。字段顺序照 CPS 里的 `codeplugChannel`（56 字节）。 */
data class Channel(
    val index: Int,
    val name: String,
    val rxHz: Long,
    val txHz: Long,
    val digital: Boolean,
    val colorCode: Int,
    val timeslot: Int,
    val power: Int,
    val bandwidth: Int,
    val tot: Int,
    val rxOnly: Boolean,
    val zoneSkip: Boolean,
    val allSkip: Boolean,
    val rxTone: Int,
    val txTone: Int,
    /** 1 开始；0 = 没设。 */
    val contactIndex: Int,
    /** 1 开始；0 = 没设。 */
    val rxGroupList: Int,
    /** 这条信道上单独指定的 DMR ID，0 = 用全局的。 */
    val dmrId: Int,
    /** 这条信道带的坐标（CPS 里叫 location）。0,0 表示没设。 */
    val lat: Double,
    val lon: Double,
    /** 挂在这条信道上的 APRS 配置：0 = 没挂，1..8 = APRS-1..APRS-8。 */
    val aprsConfig: Int = 0,
) {
    val rxMhz: Double get() = rxHz / 1_000_000.0
    val txMhz: Double get() = txHz / 1_000_000.0
    val modeLabel: String get() = if (digital) "数字" else "模拟"
    val tsLabel: String get() = if (digital) "TS${timeslot + 1}" else ""
}

/** 联系人 / 通话组。callType 0 = 个呼，1 = 组呼。 */
data class Contact(
    val index: Int,
    val name: String,
    val number: Long,
    val callType: Int,
) {
    val isGroup: Boolean get() = callType == 1
    val kindLabel: String get() = if (isGroup) "组呼" else "个呼"
}

/** 接收组（通话组）：一个名字 + 一至 32 个联系人序号（1 起算）。 */
data class RxGroup(
    val index: Int,
    val name: String,
    val members: List<Int> = emptyList(),
) {
    val memberCount: Int get() = members.size
}

/**
 * 一条 APRS 设置。字段顺序照新版源码 `APRSForm` 里那个结构：
 * 名字 8 + 发送 SSID + 坐标 3+3 + 路径1 6+SSID + 路径2 6+SSID + 图标 + 备注 24 + 频率 4 + 标志。
 */
data class AprsEntry(
    val index: Int,
    val name: String,
    val ssid: Int,
    val lat: Double,
    val lon: Double,
    val via1: String,
    val via1Ssid: Int,
    val via2: String,
    val via2Ssid: Int,
    val comment: String,
    /** 频率，赫兹。 */
    val txHz: Long,
    /** APRS 图标表：0 = 主表（/），1 = 副表（\）。 */
    val iconTable: Int = 0,
    /** 图标在表里的序号（0..93），实际字符 = 33 + 序号。 */
    val iconIndex: Int = 0,
    /** 0 = 1200 baud，1 = 300 baud。 */
    val baudRate: Int = 0,
    /** 用机器自己的 GPS 定位。 */
    val usePosition: Boolean = false,
    /** 信标里带 QSY 信息。 */
    val transmitQsy: Boolean = false,
    /** 静默发送信标。 */
    val beaconingSilent: Boolean = false,
    /** 位置掩蔽档位 0..7。 */
    val positionMasking: Int = 0,
)

/** 区域：名字 + 一串信道号（1 起算）。机器上的旋钮就是在这些之间转。 */
data class Zone(
    val index: Int,
    val name: String,
    val channelNumbers: List<Int>,
)

/** DTMF 联系人：一个名字 + 一串号码。 */
data class DtmfContact(val index: Int, val name: String, val code: String)

/**
 * 总体设置。字段顺序照 CPS 里的 `GeneralSet`（40 字节，Pack=1）。
 * 前 16 字节是呼号 + DMR ID，后面是各种时长和开关。
 */
data class GeneralSettings(
    val callsign: String,
    val dmrId: Long,
    val bootLine1: String,
    val bootLine2: String,
    val bootTextMode: Boolean,
    val arsInitDly: Int,
    val txPreambleDur: Int,
    val monitorType: Int,
    val voxSense: Int,
    val rxLowBatt: Int,
    val callAlertDur: Int,
    val respTmr: Int,
    val reminderTmr: Int,
    val grpHang: Int,
    val privateHang: Int,
    val progPwd: String,
    /** 发射退出提示音（结束时那声「哔」），在 flag4 的 bit3。 */
    val exitTone: Boolean = false,
    /** 通话许可提示音：0 无、1 数字、2 模拟、3 两者。在 flag2 的低两位。 */
    val talkPermitTone: Int = 0,
    val disableAllTones: Boolean = false,
    val crescendoTone: Boolean = false,
    val chFreeTone: Boolean = false,
    val selfTestTone: Boolean = false,
    val resetTone: Boolean = false,
    val unfamiliarTone: Boolean = false,
)

class CodeplugImage(private val bytes: ByteArray) {

    private val gbk: Charset = Charset.forName("GBK")

    init {
        require(bytes.size >= CodeplugLayout.IMAGE_SIZE) {
            "码盘应该正好 128 KB（0x20000），这份是 ${bytes.size}"
        }
    }

    // ---------------- 信道 ----------------

    val channelCount: Int get() = le32(CodeplugLayout.CHANNEL_HEADER).toInt()

    fun channels(): List<Channel> {
        val count = channelCount.coerceIn(0, 1024)
        return (0 until count).mapNotNull { i ->
            val base = CodeplugLayout.CHANNEL_DATA + i * CodeplugLayout.CHANNEL_SIZE
            if (base + CodeplugLayout.CHANNEL_SIZE > bytes.size) return@mapNotNull null
            // 头部记的 count 是「用过的最大序号」，里面混着 CPS 自动生成的占位信道
            // （名字就叫 Channel7、Channel8…），机器上不显示它们，这边也别显示。
            val name = readNameAt(base)
            if (isPlaceholderChannelName(name)) return@mapNotNull null
            runCatching { parseChannel(i, base) }.getOrNull()
        }
    }

    /** 名字像 CPS 自动生成的占位名（Channel7…），或者是残留的乱码，就当这个槽没东西。 */
    private fun isPlaceholderChannelName(name: String): Boolean {
        if (name.isEmpty()) return true
        if (Regex("^Channel\\d+$", RegexOption.IGNORE_CASE).matches(name.trim())) return true
        // 名字里有不可打印字符 → 这个槽里是垃圾数据，不是人写的
        if (name.any { it.code < 32 || it.code > 126 }) return true
        return false
    }

    private fun parseChannel(index: Int, base: Int): Channel {
        val mode = bytes[base + 24].toInt() and 0xFF
        val flag2 = bytes[base + 49].toInt() and 0xFF
        val flag4 = bytes[base + 51].toInt() and 0xFF
        // 38..41 这四个字节在 R2025 版里是「接收信令 / ART 间隔 / 加密 / 备用」，
        // 旧版源码里被当成 DMR ID 覆盖，别再按那个读。
        val dmrId = 0
        return Channel(
            index = index,
            name = readNameAt(base),
            rxHz = readBcdFreq(base + 16),
            txHz = readBcdFreq(base + 20),
            digital = mode == 1,
            power = bytes[base + 25].toInt() and 0xFF,
            tot = bytes[base + 27].toInt() and 0xFF,
            rxTone = le16(base + 32),
            txTone = le16(base + 34),
            rxGroupList = bytes[base + 43].toInt() and 0xFF,
            colorCode = bytes[base + 44].toInt() and 0xFF,
            contactIndex = le16(base + 46),
            // flag2 第 6 位：0 = TS1，1 = TS2
            timeslot = (flag2 shr 6) and 1,
            // flag4：0x02 带宽、0x04 只收、0x10 全部跳过、0x20 区域跳过
            bandwidth = (flag4 shr 1) and 1,
            rxOnly = flag4 and 0x04 != 0,
            allSkip = flag4 and 0x10 != 0,
            zoneSkip = flag4 and 0x20 != 0,
            dmrId = dmrId,
            lat = decodeCoord(
                bytes[base + 26].toInt() and 0xFF,
                bytes[base + 28].toInt() and 0xFF,
                bytes[base + 29].toInt() and 0xFF,
            ),
            lon = decodeCoord(
                bytes[base + 30].toInt() and 0xFF,
                bytes[base + 31].toInt() and 0xFF,
                bytes[base + 36].toInt() and 0xFF,
            ),
            // 第 45 字节在 CPS 里叫 emgSystem，语言文件里它显示成「APRS」：
            // 0 = 不挂，1..8 = 挂哪套 APRS 配置。手册说信道不挂就不发信标。
            aprsConfig = bytes[base + 45].toInt() and 0xFF,
        )
    }

    /** 给某条信道挂上 / 摘掉 APRS 配置（0 = 摘掉）。 */
    fun setChannelAprs(index: Int, config: Int) {
        val base = CodeplugLayout.CHANNEL_DATA + index * CodeplugLayout.CHANNEL_SIZE
        if (base + CodeplugLayout.CHANNEL_SIZE > bytes.size) return
        bytes[base + 45] = config.coerceIn(0, 8).toByte()
    }

    /**
     * 坐标解码，照 CPS 的算法：三字节拼 24 位，
     * 高的部分除以 10000 是小数、上面的位是度数，最高位是负号（南纬/西经）。
     */
    private fun decodeCoord(b0: Int, b1: Int, b2: Int): Double {
        val v = ((b2 and 0x7F) shl 16) or (b1 shl 8) or b0
        var value = (v and 0x7FFF) / 10000.0 + (v shr 15)
        if (b2 and 0x80 != 0) value = -value
        return value
    }

    fun setChannelTot(index: Int, tot: Int) {
        val base = CodeplugLayout.CHANNEL_DATA + index * CodeplugLayout.CHANNEL_SIZE
        if (base + CodeplugLayout.CHANNEL_SIZE > bytes.size) return
        bytes[base + 27] = tot.toByte()
    }

    /** 批量只改一个字段，不动别的。field: cc / ts / power / bw / rxonly。 */
    fun patchChannels(indices: List<Int>, field: String, value: Int) {
        for (i in indices) {
            val base = CodeplugLayout.CHANNEL_DATA + i * CodeplugLayout.CHANNEL_SIZE
            if (base + CodeplugLayout.CHANNEL_SIZE > bytes.size) continue
            when (field) {
                "cc" -> bytes[base + 44] = value.toByte()
                "ts" -> {
                    var f2 = bytes[base + 49].toInt() and 0xFF
                    f2 = if (value and 1 == 1) f2 or 0x40 else f2 and 0x40.inv()
                    bytes[base + 49] = f2.toByte()
                }
                "power" -> bytes[base + 25] = value.toByte()
                "bw" -> {
                    var f4 = bytes[base + 51].toInt() and 0xFF
                    f4 = if (value and 1 == 1) f4 or 0x02 else f4 and 0x02.inv()
                    bytes[base + 51] = f4.toByte()
                }
                "rxonly" -> {
                    var f4 = bytes[base + 51].toInt() and 0xFF
                    f4 = if (value == 1) f4 or 0x04 else f4 and 0x04.inv()
                    bytes[base + 51] = f4.toByte()
                }
            }
        }
    }

    /**
     * 复制一条信道到末尾（复制模板传 null 就是新建一条空的）。
     * 条数在 0x3780，最多 1024 条。
     */
    fun appendChannel(templateIndex: Int?): Int {
        val count = channelCount.coerceIn(0, 1023)
        val srcBase = if (templateIndex != null) {
            CodeplugLayout.CHANNEL_DATA + templateIndex * CodeplugLayout.CHANNEL_SIZE
        } else -1
        val dstBase = CodeplugLayout.CHANNEL_DATA + count * CodeplugLayout.CHANNEL_SIZE
        if (dstBase + CodeplugLayout.CHANNEL_SIZE > bytes.size) return -1

        if (srcBase >= 0) {
            System.arraycopy(bytes, srcBase, bytes, dstBase, CodeplugLayout.CHANNEL_SIZE)
        } else {
            for (i in 0 until CodeplugLayout.CHANNEL_SIZE) bytes[dstBase + i] = 0
            writeName(dstBase, "新信道")
            writeBcdFreq(dstBase + 16, 438_500_000)
            writeBcdFreq(dstBase + 20, 438_500_000)
        }
        setChannelCount(count + 1)
        return count
    }

    /** 删一条，后面的往前挪。 */
    fun deleteChannel(index: Int): Boolean {
        val count = channelCount.coerceIn(0, 1024)
        if (index !in 0 until count) return false
        val base = CodeplugLayout.CHANNEL_DATA + index * CodeplugLayout.CHANNEL_SIZE
        val next = base + CodeplugLayout.CHANNEL_SIZE
        val tail = CodeplugLayout.CHANNEL_DATA + count * CodeplugLayout.CHANNEL_SIZE
        if (next < tail) System.arraycopy(bytes, next, bytes, base, tail - next)
        // 末尾清干净
        for (i in 0 until CodeplugLayout.CHANNEL_SIZE) bytes[tail - CodeplugLayout.CHANNEL_SIZE + i] = 0xFF.toByte()
        setChannelCount(count - 1)
        return true
    }

    private fun setChannelCount(n: Int) {
        val v = n.toLong()
        bytes[CodeplugLayout.CHANNEL_HEADER] = (v and 0xFF).toByte()
        bytes[CodeplugLayout.CHANNEL_HEADER + 1] = ((v shr 8) and 0xFF).toByte()
        bytes[CodeplugLayout.CHANNEL_HEADER + 2] = ((v shr 16) and 0xFF).toByte()
        bytes[CodeplugLayout.CHANNEL_HEADER + 3] = ((v shr 24) and 0xFF).toByte()
    }

    fun updateChannel(
        index: Int,
        name: String,
        rxHz: Long,
        txHz: Long,
        digital: Boolean,
        colorCode: Int,
        timeslot: Int,
        power: Int,
        bandwidth: Int,
        rxOnly: Boolean,
        contactIndex: Int,
        rxGroupList: Int,
    ) {
        val base = CodeplugLayout.CHANNEL_DATA + index * CodeplugLayout.CHANNEL_SIZE
        if (base + CodeplugLayout.CHANNEL_SIZE > bytes.size) return

        writeName(base, name)
        writeBcdFreq(base + 16, rxHz)
        writeBcdFreq(base + 20, txHz)
        bytes[base + 24] = (if (digital) 1 else 0).toByte()
        bytes[base + 25] = power.toByte()
        bytes[base + 43] = (rxGroupList and 0xFF).toByte()
        bytes[base + 44] = colorCode.toByte()
        bytes[base + 46] = (contactIndex and 0xFF).toByte()
        bytes[base + 47] = ((contactIndex shr 8) and 0xFF).toByte()

        // 这两个 flag 里还塞着别的设置，只改自己那几位
        var flag2 = bytes[base + 49].toInt() and 0xFF
        flag2 = if (timeslot and 1 == 1) flag2 or 0x40 else flag2 and 0x40.inv()
        bytes[base + 49] = flag2.toByte()

        var flag4 = bytes[base + 51].toInt() and 0xFF
        flag4 = if (bandwidth and 1 == 1) flag4 or 0x02 else flag4 and 0x02.inv()
        flag4 = if (rxOnly) flag4 or 0x04 else flag4 and 0x04.inv()
        bytes[base + 51] = flag4.toByte()
    }

    // ---------------- 联系人 / 通话组 ----------------

    fun contacts(): List<Contact> {
        val out = ArrayList<Contact>()
        for (i in 0 until CodeplugLayout.CONTACTS_MAX) {
            val base = CodeplugLayout.CONTACTS_START + i * CodeplugLayout.CONTACT_SIZE
            if (base + CodeplugLayout.CONTACT_SIZE > bytes.size) break
            val name = readNameAt(base)
            // 空槽的名字是空的，号码却被固件写成 0x91000000 之类的占位——
            // 以前光看号码判断，结果这些空槽全被当成联系人塞进选择列表了。
            if (name.isEmpty()) break
            out.add(
                Contact(
                    i + 1,
                    name,
                    readBcdNumber(base + 16),
                    bytes[base + 20].toInt() and 0xFF,
                )
            )
        }
        return out
    }

    /**
     * 联系人号码：4 字节 BCD，**按存的顺序**两位一组。
     * 例：`04 61 20 79` → 4612079（BD6JZH 的 DMR ID）；`00 00 09 00` → 900（通话组）。
     */
    private fun readBcdNumber(offset: Int): Long {
        var value = 0L
        for (i in 0 until 4) {
            val b = bytes[offset + i].toInt() and 0xFF
            val hi = (b shr 4) and 0xF
            val lo = b and 0xF
            if (hi > 9 || lo > 9) return 0
            value = value * 100 + hi * 10 + lo
        }
        return value
    }

    fun rxGroups(): List<RxGroup> {
        val out = ArrayList<RxGroup>()
        for (i in 0 until CodeplugLayout.RX_GROUP_MAX) {
            val base = CodeplugLayout.RX_GROUP_START + i * CodeplugLayout.RX_GROUP_SIZE
            if (base + CodeplugLayout.RX_GROUP_SIZE > bytes.size) break
            val name = readNameAt(base)
            if (name.isEmpty()) continue
            val members = ArrayList<Int>()
            for (k in 0 until 32) {
                val idx = le16(base + 16 + k * 2)
                if (idx != 0xFFFF && idx != 0) members.add(idx)
            }
            out.add(RxGroup(i + 1, name, members))
        }
        return out
    }

    /** 第一个空着的通话组槽位（1 起算），满了返回 0。 */
    fun firstFreeRxGroup(): Int {
        for (i in 0 until CodeplugLayout.RX_GROUP_MAX) {
            val base = CodeplugLayout.RX_GROUP_START + i * CodeplugLayout.RX_GROUP_SIZE
            if (base + CodeplugLayout.RX_GROUP_SIZE > bytes.size) break
            if (readNameAt(base).isEmpty()) return i + 1
        }
        return 0
    }

    /** 写一个通话组：名字 16 字节，后面 32 个联系人序号，空位填 0xFFFF。 */
    fun writeRxGroup(index1Based: Int, name: String, members: List<Int>): Boolean {
        if (index1Based !in 1..CodeplugLayout.RX_GROUP_MAX) return false
        val base = CodeplugLayout.RX_GROUP_START + (index1Based - 1) * CodeplugLayout.RX_GROUP_SIZE
        if (base + CodeplugLayout.RX_GROUP_SIZE > bytes.size) return false
        writeName(base, name)
        for (k in 0 until 32) {
            val v = if (k < members.size) members[k] else 0xFFFF
            bytes[base + 16 + k * 2] = (v and 0xFF).toByte()
            bytes[base + 17 + k * 2] = ((v shr 8) and 0xFF).toByte()
        }
        return true
    }

    /** 清掉一个通话组（整块填 0xFF）。 */
    fun clearRxGroup(index1Based: Int) {
        if (index1Based !in 1..CodeplugLayout.RX_GROUP_MAX) return
        val base = CodeplugLayout.RX_GROUP_START + (index1Based - 1) * CodeplugLayout.RX_GROUP_SIZE
        if (base + CodeplugLayout.RX_GROUP_SIZE > bytes.size) return
        for (k in 0 until CodeplugLayout.RX_GROUP_SIZE) bytes[base + k] = 0xFF.toByte()
    }

    /** VFO A(0) / B(1)。没设过就是默认值。 */
    fun vfo(index: Int): Channel {
        val base = CodeplugLayout.VFO_A + index * CodeplugLayout.VFO_SIZE
        return parseChannel(-1 - index, base)
    }

    fun updateVfo(index: Int, rxHz: Long, txHz: Long, digital: Boolean, colorCode: Int) {
        val base = CodeplugLayout.VFO_A + index * CodeplugLayout.VFO_SIZE
        if (base + CodeplugLayout.VFO_SIZE > bytes.size) return
        writeBcdFreq(base + 16, rxHz)
        writeBcdFreq(base + 20, txHz)
        bytes[base + 24] = (if (digital) 1 else 0).toByte()
        bytes[base + 44] = colorCode.toByte()
    }

    /** 侧键的 8 个字节。动作码的含义在 CPS 的语言 XML 里，源码没带，所以按原值显示。 */
    fun sideKeys(): IntArray = IntArray(8) { bytes[CodeplugLayout.SIDE_KEY + it].toInt() and 0xFF }

    fun setSideKeys(values: IntArray) {
        for (i in 0 until minOf(8, values.size)) {
            bytes[CodeplugLayout.SIDE_KEY + i] = values[i].toByte()
        }
    }

    /** 改联系人名字。号码的编码还没核准（BCD 解出来对不上号），先只改名字。 */
    fun renameContact(index1Based: Int, name: String) {
        val base = CodeplugLayout.CONTACTS_START + (index1Based - 1) * CodeplugLayout.CONTACT_SIZE
        if (base < CodeplugLayout.CONTACTS_START || base + CodeplugLayout.CONTACT_SIZE > bytes.size) return
        writeName(base, name)
    }

    /** 联系人现在是几条（顺序排到第一个空名字为止）。 */
    fun contactCount(): Int {
        for (i in 0 until CodeplugLayout.CONTACTS_MAX) {
            val base = CodeplugLayout.CONTACTS_START + i * CodeplugLayout.CONTACT_SIZE
            if (base + CodeplugLayout.CONTACT_SIZE > bytes.size) return i
            if (readNameAt(base).isEmpty()) return i
        }
        return CodeplugLayout.CONTACTS_MAX
    }

    /** 新建一个联系人，接在最后。返回 1 起算的序号，满了返回 -1。 */
    fun appendContact(name: String, number: Long, group: Boolean): Int {
        val i = contactCount()
        val base = CodeplugLayout.CONTACTS_START + i * CodeplugLayout.CONTACT_SIZE
        if (base + CodeplugLayout.CONTACT_SIZE > bytes.size) return -1
        for (k in 0 until CodeplugLayout.CONTACT_SIZE) bytes[base + k] = 0
        writeName(base, name)
        writeBcdNumberBe(base + 16, number)
        bytes[base + 20] = if (group) 1 else 0
        return i + 1
    }

    /** 删一个联系人，后面的往前挪。 */
    fun deleteContact(index1Based: Int): Boolean {
        val count = contactCount()
        if (index1Based !in 1..count) return false
        val base = CodeplugLayout.CONTACTS_START + (index1Based - 1) * CodeplugLayout.CONTACT_SIZE
        val next = base + CodeplugLayout.CONTACT_SIZE
        val tail = CodeplugLayout.CONTACTS_START + count * CodeplugLayout.CONTACT_SIZE
        if (next < tail) System.arraycopy(bytes, next, bytes, base, tail - next)
        for (k in 0 until CodeplugLayout.CONTACT_SIZE) bytes[tail - CodeplugLayout.CONTACT_SIZE + k] = 0xFF.toByte()
        return true
    }

    /**
     * 改 DMR ID。CPS 里是 4 字节，十进制数字两位塞一个字节（BCD），
     * 和联系人号码同一套写法，这里直接复用。
     */
    fun setDmrId(id: Long) {
        val base = CodeplugLayout.GENERAL_SET + 8
        if (base + 4 > bytes.size) return
        writeBcdNumberBe(base, id)
    }
    /** 改号码（BCD，按存的顺序两位一组）。 */
    fun setContactNumber(index1Based: Int, number: Long) {
        val base = CodeplugLayout.CONTACTS_START + (index1Based - 1) * CodeplugLayout.CONTACT_SIZE
        if (base < CodeplugLayout.CONTACTS_START || base + CodeplugLayout.CONTACT_SIZE > bytes.size) return
        writeBcdNumberBe(base + 16, number)
    }

    /** 号码写回去：8 位十进制，两位一字节，高位在前。900 → 00 00 09 00。 */
    private fun writeBcdNumberBe(offset: Int, value: Long) {
        var v = value
        val digits = IntArray(8)
        for (i in 7 downTo 0) {
            digits[i] = (v % 10).toInt()
            v /= 10
        }
        for (i in 0 until 4) {
            bytes[offset + i] = (((digits[i * 2] shl 4) or digits[i * 2 + 1]) and 0xFF).toByte()
        }
    }

    /** 改区域名字（只动名字，不碰信道列表）。 */
    fun renameZone(index1Based: Int, name: String) {
        val base = CodeplugLayout.EX_ZONE_LIST + (index1Based - 1) * CodeplugLayout.ZONE_SIZE
        if (base < CodeplugLayout.EX_ZONE_LIST || base + CodeplugLayout.ZONE_SIZE > bytes.size) return
        writeName(base, name)
    }

    /**
     * APRS 那几条：0x1588 起 8 条，每条 64 字节，布局照 CPS 里 `APRS_One`（Pack=1）：
     * 名字 8、发送 SSID、坐标 3+3、经由1 6+SSID、经由2 6+SSID、图标表、图标序号、
     * 描述 24、频率 4（10 Hz）、标志位 1、末两字节是魔数 0x4153（"SA"）。
     * 名字不是 "APRS" 开头就当没有了。
     */
    fun aprsEntries(): List<AprsEntry> {
        val out = ArrayList<AprsEntry>()
        for (i in 0 until 8) {
            val base = CodeplugLayout.APRS_START + i * CodeplugLayout.APRS_SIZE
            if (base + CodeplugLayout.APRS_SIZE > bytes.size) break
            val tag = String(bytes, base, 4, Charsets.US_ASCII)
            if (tag != "APRS") break
            fun str(at: Int, len: Int): String {
                val raw = bytes.copyOfRange(base + at, base + at + len)
                var end = raw.size
                for (k in raw.indices) {
                    val b = raw[k].toInt() and 0xFF
                    if (b == 0 || b == 0xFF) { end = k; break }
                }
                return String(raw, 0, end, Charsets.US_ASCII).trim()
            }
            // 第 59 字节是标志位：bit0 波特率、bit1 用 GPS 定位、bit2 发 QSY、bit3 静默、bit5..7 位置掩蔽。
            val flags = bytes[base + 59].toInt() and 0xFF
            out.add(
                AprsEntry(
                    index = i + 1,
                    name = str(0, 8),
                    ssid = bytes[base + 8].toInt() and 0xFF,
                    lat = decodeCoord(
                        bytes[base + 9].toInt() and 0xFF,
                        bytes[base + 10].toInt() and 0xFF,
                        bytes[base + 11].toInt() and 0xFF,
                    ),
                    lon = decodeCoord(
                        bytes[base + 12].toInt() and 0xFF,
                        bytes[base + 13].toInt() and 0xFF,
                        bytes[base + 14].toInt() and 0xFF,
                    ),
                    via1 = str(15, 6),
                    via1Ssid = bytes[base + 21].toInt() and 0xFF,
                    via2 = str(22, 6),
                    via2Ssid = bytes[base + 28].toInt() and 0xFF,
                    comment = str(31, 24),
                    // 频率按 10 Hz 存的
                    txHz = (((bytes[base + 55].toLong() and 0xFF)) or
                        ((bytes[base + 56].toLong() and 0xFF) shl 8) or
                        ((bytes[base + 57].toLong() and 0xFF) shl 16) or
                        ((bytes[base + 58].toLong() and 0xFF) shl 24)) * 10,
                    iconTable = bytes[base + 29].toInt() and 0xFF,
                    iconIndex = bytes[base + 30].toInt() and 0xFF,
                    baudRate = flags and 1,
                    usePosition = flags and 2 != 0,
                    transmitQsy = flags and 4 != 0,
                    beaconingSilent = flags and 8 != 0,
                    positionMasking = (flags shr 5) and 7,
                )
            )
        }
        return out
    }

    /** decodeCoord 的反向：度 + 四位小数拼成 24 位，南纬/西经置最高位。 */
    private fun encodeCoord(value: Double, out: ByteArray, at: Int) {
        val abs = kotlin.math.abs(value)
        val deg = abs.toInt().coerceIn(0, 180)
        val frac = Math.round((abs - deg) * 10000).toInt().coerceIn(0, 9999)
        var v = (deg shl 15) or frac
        if (value < 0) v = v or 0x800000
        out[at] = (v and 0xFF).toByte()
        out[at + 1] = ((v shr 8) and 0xFF).toByte()
        out[at + 2] = ((v shr 16) and 0xFF).toByte()
    }

    /** CPS 存名字用的是 0xFF 填充（不是 0），照它来。 */
    private fun writeStr(value: String, out: ByteArray, at: Int, len: Int) {
        for (k in 0 until len) out[at + k] = 0xFF.toByte()
        val raw = value.toByteArray(Charsets.US_ASCII)
        for (k in 0 until minOf(len, raw.size)) out[at + k] = raw[k]
    }

    /** 把一条 APRS 配置写回镜像。位置、图标、描述、频率都按 CPS 的布局。 */
    fun writeAprsEntry(e: AprsEntry) {
        val base = CodeplugLayout.APRS_START + (e.index - 1) * CodeplugLayout.APRS_SIZE
        if (base < 0 || base + CodeplugLayout.APRS_SIZE > bytes.size) return
        writeStr(e.name, bytes, base, 8)
        bytes[base + 8] = e.ssid.coerceIn(0, 255).toByte()
        encodeCoord(e.lat, bytes, base + 9)
        encodeCoord(e.lon, bytes, base + 12)
        writeStr(e.via1, bytes, base + 15, 6)
        bytes[base + 21] = e.via1Ssid.coerceIn(0, 255).toByte()
        writeStr(e.via2, bytes, base + 22, 6)
        bytes[base + 28] = e.via2Ssid.coerceIn(0, 255).toByte()
        bytes[base + 29] = e.iconTable.coerceIn(0, 255).toByte()
        bytes[base + 30] = e.iconIndex.coerceIn(0, 255).toByte()
        // 描述是 24 字节，CPS 这边用 0 填（不是 FF）
        for (k in 0 until 24) bytes[base + 31 + k] = 0
        val c = e.comment.toByteArray(Charsets.US_ASCII)
        for (k in 0 until minOf(24, c.size)) bytes[base + 31 + k] = c[k]
        val f = e.txHz / 10
        bytes[base + 55] = (f and 0xFF).toByte()
        bytes[base + 56] = ((f shr 8) and 0xFF).toByte()
        bytes[base + 57] = ((f shr 16) and 0xFF).toByte()
        bytes[base + 58] = ((f shr 24) and 0xFF).toByte()
        var flags = e.baudRate and 1
        if (e.usePosition) flags = flags or 2
        if (e.transmitQsy) flags = flags or 4
        if (e.beaconingSilent) flags = flags or 8
        flags = flags or ((e.positionMasking and 7) shl 5)
        bytes[base + 59] = flags.toByte()
    }

    /** 扫描表里的信道号（1 起算），空表示这条没设。 */
    fun scanList(): List<Int> {
        val out = ArrayList<Int>()
        for (i in 0 until CodeplugLayout.SCAN_LIST_COUNT) {
            val at = CodeplugLayout.SCAN_LIST + i * 2
            if (at + 2 > bytes.size) break
            val v = le16(at)
            if (v == 0 || v == 0xFFFF) continue
            out.add(v)
        }
        return out
    }

    fun scanPriority(): Pair<List<Int>, List<Int>> {
        fun read(start: Int): List<Int> {
            val out = ArrayList<Int>()
            for (i in 0 until CodeplugLayout.SCAN_PRI_COUNT) {
                val v = le16(start + i * 2)
                if (v != 0 && v != 0xFFFF) out.add(v)
            }
            return out
        }
        return read(CodeplugLayout.SCAN_PRI1) to read(CodeplugLayout.SCAN_PRI2)
    }

    /** 改扫描列表（就地覆盖，不新建）。[numbers] 是 1 起算的信道号。 */
    fun setScanList(numbers: List<Int>): Boolean {
        if (blockOfScanMissing()) return false
        for (i in 0 until CodeplugLayout.SCAN_LIST_COUNT) {
            val v = numbers.getOrNull(i) ?: 0
            val at = CodeplugLayout.SCAN_LIST + i * 2
            bytes[at] = (v and 0xFF).toByte()
            bytes[at + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return true
    }

    private fun blockOfScanMissing(): Boolean =
        CodeplugLayout.SCAN_LIST + CodeplugLayout.SCAN_LIST_COUNT * 2 > bytes.size

    /** 第一个空着的区域槽（1 起算），满了返回 0。 */
    fun firstFreeZone(): Int {
        for (z in 0 until CodeplugLayout.ZONES_MAX) {
            val at = CodeplugLayout.EX_ZONE_INUSE + (z / 8)
            if (at >= bytes.size) break
            val inUse = (bytes[at].toInt() shr (z % 8)) and 1 == 1
            if (!inUse) return z + 1
        }
        return 0
    }

    /**
     * 写一个区域：名字 16 字节 + 最多 80 个信道号（空位填 0），并把它标记为在用。
     * 编号从 1 起算，和机器上看到的一致。
     */
    fun writeZone(index1Based: Int, name: String, channels: List<Int>): Boolean {
        if (index1Based !in 1..CodeplugLayout.ZONES_MAX) return false
        val z = index1Based - 1
        val base = CodeplugLayout.EX_ZONE_LIST + z * CodeplugLayout.ZONE_SIZE
        if (base + CodeplugLayout.ZONE_SIZE > bytes.size) return false
        writeName(base, name)
        for (c in 0 until 80) {
            val v = if (c < channels.size) channels[c].coerceIn(0, 0xFFFE) else 0
            bytes[base + 16 + c * 2] = (v and 0xFF).toByte()
            bytes[base + 17 + c * 2] = ((v shr 8) and 0xFF).toByte()
        }
        val at = CodeplugLayout.EX_ZONE_INUSE + (z / 8)
        bytes[at] = (bytes[at].toInt() or (1 shl (z % 8))).toByte()
        return true
    }

    /** 删掉一个区域：清掉在用标记，数据填 0xFF。 */
    fun clearZone(index1Based: Int) {
        if (index1Based !in 1..CodeplugLayout.ZONES_MAX) return
        val z = index1Based - 1
        val at = CodeplugLayout.EX_ZONE_INUSE + (z / 8)
        if (at < bytes.size) {
            bytes[at] = (bytes[at].toInt() and (1 shl (z % 8)).inv()).toByte()
        }
        val base = CodeplugLayout.EX_ZONE_LIST + z * CodeplugLayout.ZONE_SIZE
        if (base + CodeplugLayout.ZONE_SIZE <= bytes.size) {
            for (k in 0 until CodeplugLayout.ZONE_SIZE) bytes[base + k] = 0xFF.toByte()
        }
    }
    fun zones(): List<Zone> {
        val out = ArrayList<Zone>()
        for (z in 0 until CodeplugLayout.ZONES_MAX) {
            val inUse = (bytes[CodeplugLayout.EX_ZONE_INUSE + (z / 8)].toInt() shr (z % 8)) and 1 == 1
            val base = CodeplugLayout.EX_ZONE_LIST + z * CodeplugLayout.ZONE_SIZE
            if (base + CodeplugLayout.ZONE_SIZE > bytes.size) break
            if (!inUse) continue
            val name = readNameAt(base)
            val nums = ArrayList<Int>()
            for (c in 0 until 80) {
                val n = le16(base + 16 + c * 2)
                if (n > 0 && n < 0xFFFF) nums.add(n)
            }
            out.add(Zone(z + 1, name, nums))
        }
        return out
    }

    // ---------------- 全局设置 ----------------
    fun generalSettings(): GeneralSettings {
        var callsign = ""
        runCatching {
            var end = 0
            val base = CodeplugLayout.USER_CALLSIGN
            while (end < 8) {
                val b = bytes[base + end].toInt() and 0xFF
                if (b == 0 || b == 0xFF) break
                end++
            }
            callsign = String(bytes, base, end, Charsets.US_ASCII).filter { it.code in 32..126 }.trim()
        }
        val g = CodeplugLayout.GENERAL_SET
        fun byteAt(i: Int) = bytes[g + i].toInt() and 0xFF
        var pwd = ""
        runCatching {
            val raw = bytes.copyOfRange(g + 32, g + 40)
            val end = raw.indexOf(0).let { if (it < 0) 8 else it }
            pwd = String(raw, 0, end, Charsets.US_ASCII).trim()
        }
        return GeneralSettings(
            callsign = callsign,
            dmrId = readBcdNumber(CodeplugLayout.USER_DMRID),
            bootLine1 = readNameAt(CodeplugLayout.BOOT_LINE1),
            bootLine2 = readNameAt(CodeplugLayout.BOOT_LINE2),
            bootTextMode = (bytes[CodeplugLayout.BOOT_INTRO_SCREEN].toInt() and 1) == 1,
            arsInitDly = byteAt(16),
            txPreambleDur = byteAt(17),
            monitorType = byteAt(18),
            voxSense = byteAt(19),
            rxLowBatt = byteAt(20),
            callAlertDur = byteAt(21),
            respTmr = byteAt(22),
            reminderTmr = byteAt(23),
            grpHang = byteAt(24),
            privateHang = byteAt(25),
            progPwd = pwd,
            // 提示音这组都是位：26=flag1、27=flag2、29=flag4
            exitTone = byteAt(29) and 8 != 0,
            talkPermitTone = byteAt(27) and 3,
            disableAllTones = byteAt(27) and 0x20 != 0,
            crescendoTone = byteAt(27) and 0x10 != 0,
            chFreeTone = byteAt(27) and 8 != 0,
            selfTestTone = byteAt(27) and 4 != 0,
            resetTone = byteAt(26) and 4 != 0,
            unfamiliarTone = byteAt(26) and 8 != 0,
        )
    }

    /**
     * 改提示音这组开关。只动对应的位，旁边那些（ART、按键音、扫描模式等）原样保留。
     */
    fun setTones(
        exitTone: Boolean,
        talkPermitTone: Int,
        disableAllTones: Boolean,
        crescendoTone: Boolean,
        chFreeTone: Boolean,
        selfTestTone: Boolean,
        resetTone: Boolean,
        unfamiliarTone: Boolean,
    ) {
        val g = CodeplugLayout.GENERAL_SET
        fun patch(at: Int, mask: Int, on: Boolean) {
            val cur = bytes[g + at].toInt() and 0xFF
            bytes[g + at] = (if (on) cur or mask else cur and mask.inv()).toByte()
        }
        patch(29, 8, exitTone)
        patch(26, 4, resetTone)
        patch(26, 8, unfamiliarTone)
        patch(27, 0x20, disableAllTones)
        patch(27, 0x10, crescendoTone)
        patch(27, 8, chFreeTone)
        patch(27, 4, selfTestTone)
        val cur = bytes[g + 27].toInt() and 0xFF
        bytes[g + 27] = ((cur and 0xFC) or (talkPermitTone and 3)).toByte()
    }

    fun setCallsign(value: String) {
        val raw = value.toByteArray(Charsets.US_ASCII)
        for (i in 0 until 8) {
            bytes[CodeplugLayout.USER_CALLSIGN + i] = if (i < raw.size) raw[i] else 0
        }
    }

    fun setGeneralNumbers(
        arsInitDly: Int, txPreambleDur: Int, monitorType: Int, voxSense: Int,
        rxLowBatt: Int, callAlertDur: Int, respTmr: Int, reminderTmr: Int,
        grpHang: Int, privateHang: Int,
    ) {
        val g = CodeplugLayout.GENERAL_SET
        fun put(i: Int, v: Int) { bytes[g + i] = v.toByte() }
        put(16, arsInitDly); put(17, txPreambleDur); put(18, monitorType); put(19, voxSense)
        put(20, rxLowBatt); put(21, callAlertDur); put(22, respTmr); put(23, reminderTmr)
        put(24, grpHang); put(25, privateHang)
    }

    /** DTMF 号码用的字符表：每个字符存成它的下标（0..15），空位 0xFF。 */
    private val DTMF_ALPHABET = "0123456789ABCD*#"

    /** 第一个空着的 DTMF 槽位（1 起算），满了返回 0。 */
    fun firstFreeDtmf(): Int {
        for (i in 0 until CodeplugLayout.DTMF_MAX) {
            val base = CodeplugLayout.DTMF_START + i * CodeplugLayout.DTMF_SIZE
            if (base + CodeplugLayout.DTMF_SIZE > bytes.size) break
            if (readNameAt(base).isEmpty()) return i + 1
        }
        return 0
    }

    /** 写一条 DTMF 联系人：名字 16 字节，号码 16 字节（按字符表存下标）。 */
    fun writeDtmfContact(index1Based: Int, name: String, code: String): Boolean {
        if (index1Based !in 1..CodeplugLayout.DTMF_MAX) return false
        val base = CodeplugLayout.DTMF_START + (index1Based - 1) * CodeplugLayout.DTMF_SIZE
        if (base + CodeplugLayout.DTMF_SIZE > bytes.size) return false
        writeName(base, name)
        for (i in 0 until 16) {
            val ch = code.getOrNull(i)
            bytes[base + 16 + i] = if (ch == null) {
                0xFF.toByte()
            } else {
                val idx = DTMF_ALPHABET.indexOf(ch.uppercaseChar())
                (if (idx < 0) 0xFF else idx).toByte()
            }
        }
        return true
    }

    /** 删掉一条 DTMF 联系人（整条填 0xFF）。 */
    fun clearDtmfContact(index1Based: Int) {
        if (index1Based !in 1..CodeplugLayout.DTMF_MAX) return
        val base = CodeplugLayout.DTMF_START + (index1Based - 1) * CodeplugLayout.DTMF_SIZE
        if (base + CodeplugLayout.DTMF_SIZE > bytes.size) return
        for (k in 0 until CodeplugLayout.DTMF_SIZE) bytes[base + k] = 0xFF.toByte()
    }
    fun dtmfContacts(): List<DtmfContact> {
        val out = ArrayList<DtmfContact>()
        for (i in 0 until CodeplugLayout.DTMF_MAX) {
            val base = CodeplugLayout.DTMF_START + i * CodeplugLayout.DTMF_SIZE
            if (base + CodeplugLayout.DTMF_SIZE > bytes.size) break
            val name = readNameAt(base)
            // 空槽整条是 0xFF；名字空就当到头了，不然会把 128 个空槽全读成「联系人」
            if (name.isEmpty()) break
            var end = 0
            while (end < 16) {
                val b = bytes[base + 16 + end].toInt() and 0xFF
                if (b == 0 || b == 0xFF) break
                end++
            }
            val code = String(bytes, base + 16, end, Charsets.US_ASCII)
                .filter { it.code in 32..126 }.trim()
            out.add(DtmfContact(i + 1, name, code))
        }
        return out
    }

    /** 32 条快捷短信。空串就是没设。 */
    fun textMessages(): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until CodeplugLayout.TEXT_MSG_COUNT) {
            val len = bytes[CodeplugLayout.TEXT_MSG + 8 + i].toInt() and 0xFF
            val base = CodeplugLayout.TEXT_MSG + 72 + i * CodeplugLayout.TEXT_MSG_SLOT
            if (base + CodeplugLayout.TEXT_MSG_SLOT > bytes.size) break
            val n = len.coerceIn(0, CodeplugLayout.TEXT_MSG_SLOT)
            val raw = bytes.copyOfRange(base, base + n)
            val text = String(raw, gbk).trim()
            out.add(text)
        }
        return out
    }

    /** 第一条空着的快捷短信槽位（0 起），满了返回 -1。 */
    fun firstFreeTextMessage(): Int {
        for (i in 0 until CodeplugLayout.TEXT_MSG_COUNT) {
            val len = bytes[CodeplugLayout.TEXT_MSG + 8 + i].toInt() and 0xFF
            if (len == 0) return i
        }
        return -1
    }
    fun setTextMessage(index: Int, text: String) {
        if (index !in 0 until CodeplugLayout.TEXT_MSG_COUNT) return
        val raw = text.toByteArray(gbk)
        val n = raw.size.coerceAtMost(CodeplugLayout.TEXT_MSG_SLOT)
        bytes[CodeplugLayout.TEXT_MSG + 8 + index] = n.toByte()
        val base = CodeplugLayout.TEXT_MSG + 72 + index * CodeplugLayout.TEXT_MSG_SLOT
        for (i in 0 until CodeplugLayout.TEXT_MSG_SLOT) {
            bytes[base + i] = if (i < n) raw[i] else 0
        }
    }

    fun setBootLines(line1: String, line2: String, textMode: Boolean) {
        writeName(CodeplugLayout.BOOT_LINE1, line1)
        writeName(CodeplugLayout.BOOT_LINE2, line2)
        val at = CodeplugLayout.BOOT_INTRO_SCREEN
        val cur = bytes[at].toInt() and 0xFF
        bytes[at] = (if (textMode) cur or 1 else cur and 0xFE).toByte()
    }

    // ---------------- 底层 ----------------

    fun toByteArray(): ByteArray = bytes.copyOf()

    /** 整份换掉（尺寸必须一样）。改自定义数据块那种「整块搬」的操作用。 */
    fun replaceBytes(newBytes: ByteArray) {
        require(newBytes.size == bytes.size) { "尺寸不对：${newBytes.size} != ${bytes.size}" }
        System.arraycopy(newBytes, 0, bytes, 0, bytes.size)
    }

    private fun readNameAt(base: Int): String {
        var end = base
        val limit = base + CodeplugLayout.NAME_SIZE
        while (end < limit && bytes[end] != 0.toByte() && bytes[end] != 0xFF.toByte()) end++
        return String(bytes, base, end - base, gbk).trim()
    }

    private fun writeName(base: Int, name: String) {
        val raw = name.toByteArray(gbk)
        for (i in 0 until CodeplugLayout.NAME_SIZE) {
            bytes[base + i] = if (i < raw.size) raw[i] else 0xFF.toByte()
        }
    }

    /**
     * 频率按 BCD 存：四个字节读成 32 位值，把它的**十六进制数字**当成十进制数字，
     * 再乘 10 Hz。CPS 里就是 `String.Format("{0:x}0", rxFreq)`。
     */
    private fun readBcdFreq(offset: Int): Long {
        val v = le32(offset)
        var value = 0L
        var shift = 28
        while (shift >= 0) {
            val nib = ((v ushr shift) and 0xF).toInt()
            if (nib > 9) return 0
            value = value * 10 + nib
            shift -= 4
        }
        return value * 10
    }

    private fun writeBcdFreq(offset: Int, hz: Long) {
        val v = hz / 10
        val digits = v.toString().padStart(8, '0').takeLast(8)
        var packed = 0L
        for (c in digits) {
            if (c !in '0'..'9') return
            packed = (packed shl 4) or (c - '0').toLong()
        }
        bytes[offset] = (packed and 0xFF).toByte()
        bytes[offset + 1] = ((packed shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((packed shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((packed shr 24) and 0xFF).toByte()
    }

    private fun le16(offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
}
