package com.mai.dmrcps

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.mai.dmrcps.codeplug.AprsEntry
import com.mai.dmrcps.codeplug.BootImage
import com.mai.dmrcps.codeplug.ChannelCsv
import com.mai.dmrcps.codeplug.CustomData
import com.mai.dmrcps.firmware.Dfu
import com.mai.dmrcps.firmware.DfuProbe
import com.mai.dmrcps.firmware.FirmwareFile
import com.mai.dmrcps.codeplug.Channel
import com.mai.dmrcps.codeplug.CodeplugImage
import com.mai.dmrcps.codeplug.Contact
import com.mai.dmrcps.codeplug.DtmfContact
import com.mai.dmrcps.codeplug.GeneralSettings
import com.mai.dmrcps.codeplug.RxGroup
import com.mai.dmrcps.codeplug.Zone
import com.mai.dmrcps.protocol.Cps
import com.mai.dmrcps.protocol.MemType
import com.mai.dmrcps.protocol.CpsProtocolError
import com.mai.dmrcps.protocol.RadioInfo
import com.mai.dmrcps.usb.UsbCps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import com.mai.dmrcps.satellite.Keps
import com.mai.dmrcps.satellite.Observer
import com.mai.dmrcps.satellite.Orbit
import com.mai.dmrcps.satellite.Pass
import com.mai.dmrcps.satellite.TleSat
import com.mai.dmrcps.satellite.parseTleText

private const val ACTION_USB_PERMISSION = "com.mai.dmrcps.USB_PERMISSION"

private enum class Tab(val label: String) {
    CHANNELS("信道"), ZONES("区域"), CONTACTS("联系人"), SETTINGS("设置"), TRANSFER("读写"), TOOLS("工具")
}

class AppState {
    val log = mutableStateListOf<String>()
    var deviceName by mutableStateOf<String?>(null)
    var info by mutableStateOf<RadioInfo?>(null)
    var progress by mutableStateOf<Float?>(null)
    var busy by mutableStateOf(false)
    var lastImage by mutableStateOf<File?>(null)
    var sourceName by mutableStateOf<String?>(null)
    var channels by mutableStateOf<List<Channel>>(emptyList())
    var contacts by mutableStateOf<List<Contact>>(emptyList())
    var rxGroups by mutableStateOf<List<RxGroup>>(emptyList())
    var zones by mutableStateOf<List<Zone>>(emptyList())
    var dtmf by mutableStateOf<List<DtmfContact>>(emptyList())
    var messages by mutableStateOf<List<String>>(emptyList())
    var tles by mutableStateOf<List<TleSat>>(emptyList())
    var vfoA by mutableStateOf<Channel?>(null)
    var vfoB by mutableStateOf<Channel?>(null)
    var sideKeys by mutableStateOf<IntArray>(IntArray(8))
    var aprs by mutableStateOf<List<AprsEntry>>(emptyList())
    var firmware by mutableStateOf<FirmwareFile?>(null)
    var themeDay by mutableStateOf<List<Int>>(emptyList())
    var themeNight by mutableStateOf<List<Int>>(emptyList())
    var melodyLen by mutableStateOf(0)
    var melodyText by mutableStateOf("")
    var bootBitmap by mutableStateOf<Bitmap?>(null)
    var kepsSize by mutableStateOf(0)
    var scanList by mutableStateOf<List<Int>>(emptyList())
    var dfuDevices by mutableStateOf<List<String>>(emptyList())
    /** 下载下来的呼号库（全量），筛选在界面上做。 */
    var dmrIdAll by mutableStateOf<List<DmrIdDb.Entry>>(emptyList())
    var dmrIdPrefix by mutableStateOf("46")
    var dmrIdQuery by mutableStateOf("")
    var dmrIdSource by mutableStateOf(DmrIdDb.SOURCES.first().second)
    var dmrIdBusy by mutableStateOf(false)
    var dmrIdMessage by mutableStateOf<String?>(null)
    /** 刚从机器取回来的屏幕画面，预览用。 */
    var screengrab by mutableStateOf<android.graphics.Bitmap?>(null)
    /** 从机器读回来的校准数据（4096 字节，头部 A0 0F 开头才算正常）。 */
    var calibration by mutableStateOf<ByteArray?>(null)
    /** 校准区实际所在的地址和内存类型，写回时用同一处。 */
    var calibrationAddr by mutableStateOf<Long?>(null)
    var calibrationMemType by mutableStateOf(0)
    var lastPass by mutableStateOf<Pass?>(null)
    var general by mutableStateOf<GeneralSettings?>(null)
    var image by mutableStateOf<CodeplugImage?>(null)
    var dirty by mutableStateOf(false)

    fun say(line: String) {
        log.add(line)
        if (log.size > 200) log.removeAt(0)
    }
}

/** 编辑框里攒出来的一条信道。 */
data class ChannelEdit(
    val index: Int,
    val name: String,
    val rxHz: Long,
    val txHz: Long,
    val digital: Boolean,
    val colorCode: Int,
    val timeslot: Int,
    val power: Int,
    val bandwidth: Int,
    val rxOnly: Boolean,
    val contactIndex: Int,
    val rxGroupList: Int,
    /** 挂哪套 APRS 配置：0 = 不挂。 */
    val aprsConfig: Int = 0,
)

class MainActivity : ComponentActivity() {

    private var pending: ((Boolean) -> Unit)? = null
    private var usb: UsbCps? = null
    private var currentDevice: UsbDevice? = null
    private val state = AppState()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            pending?.invoke(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            pending = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(permissionReceiver, filter)
        }

        // 从命令行/别的程序打开 .g77：am start -d file:///...
        handleOpenIntent(intent)

        setContent {
            AppTheme {
                AppScreen(
                    state = state,
                    onConnect = { onConnectClick() },
                    onReadInfo = { onReadInfoClick() },
                    onReadCodeplug = { onReadCodeplugClick() },
                    onDisconnect = { disconnect() },
                    onFilePicked = { uri -> loadFromUri(uri) },
                    onExport = { uri -> exportTo(uri) },
                    onChannelSaved = { edit -> saveChannel(edit) },
                    onSettingsSaved = { c, l1, l2, t, nums -> saveSettings(c, l1, l2, t, nums) },
                    onWriteConfirmed = { writeBack() },
                    onMessageSaved = { i, t -> saveMessage(i, t) },
                    onDownloadDmrId = { downloadDmrId() },
                    onExportDmrId = { uri -> exportDmrId(uri) },
                    onWriteDmrId = { writeDmrIdDb() },
                    onPreviewScreen = { previewScreengrab() },
                    onSaveScreengrab = { uri -> saveScreengrab(uri) },
                    onBackupMcuRom = { uri -> backupMcuRom(uri) },
                    onVerify = { verifyAgainstRadio() },
                    onFetchTle = { fetchTle() },
                    onComputePass = { i, lat, lon, d, u -> computePass(i, lat, lon, d, u) },
                    onWritePass = { i -> writePassToChannel(i) },
                    onVfoSaved = { aRx, aTx, bRx, bTx -> saveVfo(aRx, aTx, bRx, bTx) },
                    onSideKeysSaved = { v -> saveSideKeys(v) },
                    onDmrIdSaved = { id -> saveDmrId(id) },
                    onTonesSaved = { e, t, d, c1, f, s, r, u -> saveTones(e, t, d, c1, f, s, r, u) },
                    onAprsSaved = { e -> saveAprs(e) },
                    onChannelAprsSaved = { i, c -> saveChannelAprs(i, c) },
                    onAddChannel = { copyFrom -> addChannel(copyFrom) },
                    onDeleteChannel = { index -> deleteChannel(index) },
                    onRenameContact = { c, n -> renameContact(c, n) },
                    onRenameZone = { z, n -> renameZone(z, n) },
                    onSaveZone = { i, n, c -> saveZone(i, n, c) },
                    onDeleteZone = { z -> deleteZone(z) },
                    onAddContact = { n, num, g -> addContact(n, num, g) },
                    onSaveGroup = { i, n, m -> saveRxGroup(i, n, m) },
                    onSaveDtmf = { i, n, cd -> saveDtmf(i, n, cd) },
                    onDeleteDtmf = { d -> deleteDtmf(d) },
                    onDeleteGroup = { g -> deleteRxGroup(g) },
                    onDeleteContact = { c -> deleteContact(c) },
                    onContactNumber = { c, num -> setContactNumber(c, num) },
                    onBatchPatch = { idx, field, v -> batchPatch(idx, field, v) },
                    onFirmwarePicked = { uri -> loadFirmware(uri) },
                    onCsvExport = { uri -> exportCsv(uri) },
                    onCsvImport = { uri -> importCsv(uri) },
                    onThemeColour = { type, idx, rgbHex -> setThemeColour(type, idx, rgbHex) },
                    onWriteKeps = { writeKeps() },
                    onFlash = { flashFirmware() },
                    onMelodySaved = { text -> saveMelody(text) },
                    onBootImagePicked = { uri -> loadBootImage(uri) },
                    onProbeDfu = { probeDfu() },
                    onReadCalibration = { readCalibration() },
                    onExportCalibration = { uri -> exportCalibration(uri) },
                    onWriteCalibration = { writeCalibration() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (state.image != null && intent.getBooleanExtra("already_loaded", false)) return
        loadFromUri(uri)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(permissionReceiver) }
        disconnect()
        super.onDestroy()
    }

    @Composable
    private fun AppTheme(content: @Composable () -> Unit) {
        MaiTheme(content = content)
    }

    // ---- 文件 ----

    private fun loadFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("打不开这个文件")
                }
                applyImage(bytes, uri.lastPathSegment ?: "文件")
            } catch (e: Exception) {
                state.say("读文件失败：${e.message}")
            }
        }
    }

    private fun applyImage(bytes: ByteArray, label: String) {
        try {
            val image = CodeplugImage(bytes)
            state.image = image
            state.channels = image.channels()
            state.contacts = image.contacts()
            state.rxGroups = image.rxGroups()
            state.zones = image.zones()
            state.dtmf = image.dtmfContacts()
            state.messages = image.textMessages()
            state.vfoA = runCatching { image.vfo(0) }.getOrNull()
            state.vfoB = runCatching { image.vfo(1) }.getOrNull()
            state.sideKeys = image.sideKeys()
            state.aprs = image.aprsEntries()
            val raw = image.toByteArray()
            state.themeDay = CustomData.themeColours(raw, CustomData.TYPE_THEME_DAY)
            state.themeNight = CustomData.themeColours(raw, CustomData.TYPE_THEME_NIGHT)
            val pairs = CustomData.melodyPairs(raw)
            state.melodyLen = pairs.size
            state.bootBitmap = runCatching {
                CustomData.blockOf(raw, CustomData.TYPE_IMAGE)?.let { blk ->
                    val bits = raw.copyOfRange(CustomData.START + blk.offset, CustomData.START + blk.offset + BootImage.SIZE)
                    val px = BootImage.decode(bits)
                    val bmp = Bitmap.createBitmap(BootImage.WIDTH, BootImage.HEIGHT, Bitmap.Config.ARGB_8888)
                    for (y in 0 until BootImage.HEIGHT) {
                        for (x in 0 until BootImage.WIDTH) {
                            bmp.setPixel(x, y, if (px[y][x]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                        }
                    }
                    bmp
                }
            }.getOrNull()
            state.melodyText = pairs.joinToString(",") { "${it.first},${it.second}" }
            state.kepsSize = CustomData.keps(raw)?.size ?: 0
            state.scanList = runCatching { image.scanList() }.getOrDefault(emptyList())
            state.general = image.generalSettings()
            state.sourceName = label
            state.dirty = false
            state.say("$label：${bytes.size} 字节，${state.channels.size} 条信道，${state.contacts.size} 个联系人")
        } catch (e: Exception) {
            state.say("解析失败：${e.message}")
        }
    }

    private fun exportTo(uri: Uri) {
        val image = state.image ?: run { state.say("还没有数据"); return }
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(image.toByteArray()) }
                        ?: throw IllegalStateException("写不进这个位置")
                }
                state.dirty = false
                state.say("已导出：${uri.lastPathSegment}")
            } catch (e: Exception) {
                state.say("导出失败：${e.message}")
            }
        }
    }

    private fun saveChannel(v: ChannelEdit) {
        val image = state.image ?: return
        image.updateChannel(
            v.index, v.name, v.rxHz, v.txHz, v.digital, v.colorCode,
            v.timeslot, v.power, v.bandwidth, v.rxOnly, v.contactIndex, v.rxGroupList,
        )
        image.setChannelAprs(v.index, v.aprsConfig)
        state.channels = image.channels()
        state.dirty = true
        state.say("改了第 ${v.index + 1} 条：${v.name}")
    }

    private fun fetchTle() {
        if (state.busy) return
        state.busy = true
        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val conn = URL("https://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=tle")
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 25000
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    parseTleText(text)
                }
                state.tles = list
                state.say("TLE 更新：${list.size} 颗业余卫星")
            } catch (e: Exception) {
                state.say("拉 TLE 失败：${e.message}")
            } finally {
                state.busy = false
            }
        }
    }

    private fun computePass(satIndex: Int, lat: Double, lon: Double, downMhz: Double, upMhz: Double) {
        val sat = state.tles.getOrNull(satIndex) ?: run { state.say("先更新 TLE"); return }
        if (state.busy) return
        state.busy = true
        lifecycleScope.launch {
            try {
                val pass = withContext(Dispatchers.IO) {
                    Orbit.nextPass(
                        sat, Observer(lat, lon),
                        downlinkHz = downMhz * 1_000_000,
                        uplinkHz = upMhz * 1_000_000,
                    )
                }
                state.lastPass = pass
                val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
                if (pass == null) {
                    state.say("${sat.name}：24 小时内没有过境")
                } else {
                    state.say(
                        "${pass.satName}：${fmt.format(Date(pass.aos.toEpochMilli()))} → " +
                            "${fmt.format(Date(pass.los.toEpochMilli()))}，" +
                            "最高 ${"%.0f".format(pass.maxElevationDeg)}°"
                    )
                    state.say(
                        "过境中点：下行 ${"%.4f".format(pass.downlinkAtTcaHz / 1e6)} / " +
                            "上行 ${"%.4f".format(pass.uplinkAtTcaHz / 1e6)} MHz"
                    )
                }
            } catch (e: Exception) {
                state.say("算过境失败：${e.message}")
            } finally {
                state.busy = false
            }
        }
    }

    private fun writePassToChannel(index: Int) {
        val pass = state.lastPass ?: run { state.say("先算一次过境"); return }
        val image = state.image ?: run { state.say("还没有数据"); return }
        val ch = state.channels.firstOrNull { it.index == index }
            ?: run { state.say("没有第 ${index + 1} 条信道"); return }
        image.updateChannel(
            index,
            ch.name.ifBlank { pass.satName },
            pass.downlinkAtTcaHz.roundToLong(),
            pass.uplinkAtTcaHz.roundToLong(),
            ch.digital, ch.colorCode, ch.timeslot, ch.power, ch.bandwidth, ch.rxOnly,
            ch.contactIndex, ch.rxGroupList,
        )
        state.channels = image.channels()
        state.dirty = true
        state.say("把 ${pass.satName} 的中点多普勒频率写进第 ${index + 1} 条")
    }

    /** 校准数据在 FLASH 的 0x7C00 起 4096 字节（STM32 机型实际落到 +0x20000）。 */
    private val CAL_ADDR = 0x7C00L
    private val CAL_SIZE = 0x1000
    private val CAL_BIAS = 0x20000L
    /** DMR ID 库在数据区的偏移，CPS 用 196608（0x30000）。 */
    private val DMR_ID_ADDR = 0x30000L
    /** CPS 注释里写的绝对地址 0x80000，换成数据区偏移就是 0x60000。 */
    private val CAL_ADDR_HINT = 0x60000L

    /**
     * 校准区在哪个地址，CPS 的注释和实际对不上（写着 0x80000，用的却是 0x7C00）。
     * 所以先探几个候选，哪个头部是 A0 0F 就认定是它，再把 4096 字节读回来。
     */
    /**
     * 校准区的地址一直对不上：CPS 里常量写 0x7C00，旁边的注释却写 0x80000。
     * 数据区里 0x7C00 是空的、整段 0x00000-0x80000 也扫不到 A0 0F 头，
     * 而 0x60000（绝对 0x80000）那块确实有内容，就先按它读，读出来看看再定。
     */
    private fun readCalibration() {
        runTask {
            val cps = Cps(reopenUsb())
            cps.readRadioInfo()
            state.say("正在读 0x60000（绝对 0x80000）…")
            val data = runCatching {
                cps.readMemory(MemType.FLASH, CAL_ADDR_HINT, CAL_SIZE)
            }.getOrElse {
                state.say("读 0x%05X 失败：%s".format(CAL_ADDR_HINT, it.message))
                return@runTask
            }
            state.calibration = data
            state.calibrationAddr = CAL_ADDR_HINT
            state.calibrationMemType = MemType.FLASH
            val empty = data.all { b -> b == 0xFF.toByte() || b == 0.toByte() }
            val hex = data.take(8).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            state.say(
                if (empty) "这块也是空的（$hex）"
                else "读到 ${data.size} 字节，开头 $hex，存成文件发我看看"
            )
        }
    }
    /** 把手上这份校准数据写回上次读到的那个地址。写错会影响收发。 */
    private fun writeCalibration() {
        val data = state.calibration ?: run { state.say("先读一次校准数据"); return }
        val addr = state.calibrationAddr ?: run { state.say("还不知道校准区在哪，先读一次"); return }
        val type = state.calibrationMemType
        runTask {
            val cps = Cps(reopenUsb())
            cps.readRadioInfo()
            cps.beginWrite()
            try {
                // FLASH 在 STM32 机型上要加 0x20000，和读的时候保持同一套换算
                val target = if (type == MemType.FLASH) addr + CAL_BIAS else addr
                cps.writeRaw(target, data) { done -> state.progress = done.toFloat() / data.size }
                cps.endWrite()
                state.say("校准数据写回去了，${data.size} 字节")
            } catch (e: Exception) {
                runCatching { cps.endWrite() }
                throw e
            } finally {
                state.progress = null
            }
        }
    }
    /** 把读回来的校准数据存成文件。 */
    /** 备份 MCU ROM（固件本体）。纯读，1 MB，要一两分钟。 */
    private fun backupMcuRom(uri: Uri) {
        runTask {
            val cps = Cps(reopenUsb())
            cps.readRadioInfo()
            state.say("正在备份 MCU ROM（1 MB），要一两分钟…")
            var last = 0
            val data = cps.readMcuRom { done ->
                val p = done * 100 / 1048576
                if (p >= last + 10) {
                    last = p
                    state.say("MCU ROM 备份中：$p%")
                }
            }
            contentResolver.openOutputStream(uri)?.use { it.write(data) }
            state.say("MCU ROM 已备份：${data.size} 字节")
        }
    }
    /** 取一张对讲机屏幕画面放到界面上预览。160×128、RGB565，只读。 */
    private fun previewScreengrab() {
        runTask {
            val cps = Cps(reopenUsb())
            cps.readRadioInfo(stealth = true)
            state.say("正在取屏幕画面…")
            val raw = cps.readScreengrab()
            val w = 160
            val h = 128
            if (raw.size < w * h * 2) {
                state.say("画面数据不够：${raw.size} 字节")
                return@runTask
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            var i = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val v = ((raw[i].toInt() and 0xFF) shl 8) or (raw[i + 1].toInt() and 0xFF)
                    val r = (v and 0xF800) shr 8
                    val g = (v and 0x07E0) shr 3
                    val b = (v and 0x001F) shl 3
                    bmp.setPixel(x, y, android.graphics.Color.rgb(r, g, b))
                    i += 2
                }
            }
            state.screengrab = bmp
            state.say("画面取回来了，可以点「下载」存成 PNG")
        }
    }

    /** 把预览里那张画面存成 PNG。 */
    private fun saveScreengrab(uri: Uri) {
        val bmp = state.screengrab ?: run { state.say("先点「预览」取一张画面"); return }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                state.say("画面已保存")
            } catch (e: Exception) {
                state.say("保存失败：${e.message}")
            }
        }
    }

    /** 下载呼号库。文件十几 MB，走后台线程，界面上只显示进度文字。 */
    /**
     * 把当前筛选结果写进电台的 DMR ID 库。
     * 位置照 CPS：数据区 0x30000，STM32 机型实际落到 0x50000；先写头再写记录。
     */
    private fun writeDmrIdDb() {
        val rows = DmrIdDb.filterPrefix(state.dmrIdAll, state.dmrIdPrefix)
        if (rows.isEmpty()) { state.say("先下载并筛选出要写入的条目"); return }
        runTask {
            val data = DmrIdDb.buildBinary(rows, 16)
            val cps = Cps(reopenUsb())
            cps.readRadioInfo()
            state.say("开始写入 ${rows.size} 条呼号，共 ${data.size} 字节…")
            cps.beginWrite()
            try {
                cps.writeRaw(DMR_ID_ADDR + CAL_BIAS, data) { done ->
                    state.progress = done.toFloat() / data.size
                }
                cps.endWrite()
                state.dmrIdMessage = "已写入 ${rows.size} 条"
                state.say("呼号库写完了：${rows.size} 条")
            } catch (e: Exception) {
                runCatching { cps.endWrite() }
                throw e
            } finally {
                state.progress = null
            }
        }
    }
    /** 把当前筛选结果写成 CSV，交给系统的保存对话框。 */
    private fun exportDmrId(uri: Uri) {
        val rows = DmrIdDb.filterPrefix(state.dmrIdAll, state.dmrIdPrefix)
        if (rows.isEmpty()) { state.say("还没有可导出的数据"); return }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val text = DmrIdDb.toCsv(rows)
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                state.dmrIdMessage = "已导出 ${rows.size} 条"
            } catch (e: Exception) {
                state.dmrIdMessage = "导出失败：${e.message}"
            }
        }
    }
    private fun downloadDmrId() {
        if (state.dmrIdBusy) return
        state.dmrIdBusy = true
        state.dmrIdMessage = "开始下载…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val list = DmrIdDb.download(state.dmrIdSource) { got ->
                    state.dmrIdMessage = "已解析 $got 条…"
                }
                state.dmrIdAll = list
                state.dmrIdMessage = "下载完成：${list.size} 条"
            } catch (e: Exception) {
                state.dmrIdMessage = "下载失败：${e.message}"
            } finally {
                state.dmrIdBusy = false
            }
        }
    }
    private fun exportCalibration(uri: Uri) {
        val data = state.calibration ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(data) }
                state.say("校准数据已保存：${data.size} 字节")
            } catch (e: Exception) {
                state.say("保存校准失败：${e.message}")
            }
        }
    }
    private fun verifyAgainstRadio() {
        val link = usb ?: run { state.say("先点连接"); return }
        val image = state.image ?: run { state.say("还没有数据"); return }
        val mine = image.toByteArray()
        runTask {
            // 每次读之前彻底重来一遍：关掉旧连接、重新 claim、清空缓冲，
            // 避免把上一轮卡住的状态带进来（对讲机被噎住就是这种情况）
            val fresh = reopenUsb()
            val cps = Cps(fresh)
            val info = cps.readRadioInfo()
            state.say("校验：读取对讲机里的数据（${info.modelLabel}）")
            val theirs = cps.readCodeplug { done, total -> state.progress = done.toFloat() / total }
            val diffs = ArrayList<String>()
            var count = 0
            for (i in mine.indices) {
                if (mine[i] != theirs[i]) {
                    count++
                    if (diffs.size < 12) {
                        diffs.add(
                            "0x%05x  本地 %02x  机器 %02x".format(
                                i, mine[i].toInt() and 0xFF, theirs[i].toInt() and 0xFF,
                            )
                        )
                    }
                }
            }
            if (count == 0) {
                state.say("完全一致，${mine.size} 字节全对上")
            } else {
                state.say("有 $count 个字节不一样，前几处：")
                diffs.forEach { state.say("  $it") }
            }
        }
    }

    private fun writeBack() {
        val image = state.image ?: run { state.say("还没有数据"); return }
        val bytes = image.toByteArray()
        runTask {
            val cps = Cps(reopenUsb())
            cps.readRadioInfo()
            cps.beginWrite()
            state.say("开始写，128 KB（逐扇区准备 + 分块写）")
            cps.writeCodeplug(bytes) { done, total -> state.progress = done.toFloat() / total }
            cps.endWrite()
            // 写完机器会自己重启（USB 重新枚举、节点都换），不在 App 里等它重连：
            // 想核对就等它开机后点「连接」再点「读码盘」。
            state.say("写完了。机器会自己重启，开机后点「连接」再点「读取」即可核对")
        }
    }

    private fun saveMessage(index: Int, text: String) {
        val image = state.image ?: return
        val slot = if (index >= 0) index else image.firstFreeTextMessage()
        if (slot < 0) { state.say("快捷短信已经 32 条，满了"); return }
        image.setTextMessage(slot, text)
        state.messages = image.textMessages()
        state.dirty = true
        state.say(if (index >= 0) "第 ${slot + 1} 条改了" else "已新建一条快捷短信")
    }

    private fun saveVfo(aRx: Long, aTx: Long, bRx: Long, bTx: Long) {
        val image = state.image ?: return
        image.updateVfo(0, aRx, aTx, false, state.vfoA?.colorCode ?: 0)
        image.updateVfo(1, bRx, bTx, false, state.vfoB?.colorCode ?: 0)
        state.vfoA = image.vfo(0)
        state.vfoB = image.vfo(1)
        state.dirty = true
        state.say("VFO 改了：A %.4f / B %.4f MHz".format(aRx / 1e6, bRx / 1e6))
    }

    private fun saveSideKeys(values: IntArray) {
        val image = state.image ?: return
        image.setSideKeys(values)
        state.sideKeys = image.sideKeys()
        state.dirty = true
        state.say("侧键改了：${values.joinToString(" ")}")
    }

    private fun saveDtmf(index: Int, name: String, code: String) {
        val image = state.image ?: return
        val slot = if (index > 0) index else image.firstFreeDtmf()
        if (slot == 0) { state.say("DTMF 联系人已满 63 个"); return }
        if (!image.writeDtmfContact(slot, name, code)) { state.say("写入失败"); return }
        state.dtmf = image.dtmfContacts()
        state.dirty = true
        state.say(if (index > 0) "DTMF「$name」已修改" else "已新建 DTMF「$name」")
    }

    private fun deleteDtmf(d: DtmfContact) {
        val image = state.image ?: return
        image.clearDtmfContact(d.index)
        state.dtmf = image.dtmfContacts()
        state.dirty = true
        state.say("已删除 DTMF：${d.name}")
    }
    private fun saveTones(
        exitTone: Boolean, talkPermit: Int, disableAll: Boolean, crescendo: Boolean,
        chFree: Boolean, selfTest: Boolean, reset: Boolean, unfamiliar: Boolean,
    ) {
        val image = state.image ?: return
        image.setTones(exitTone, talkPermit, disableAll, crescendo, chFree, selfTest, reset, unfamiliar)
        state.general = image.generalSettings()
        state.dirty = true
        state.say("提示音设置已更新")
    }
    private fun saveZone(index: Int, name: String, channels: List<Int>) {
        val image = state.image ?: return
        val slot = if (index > 0) index else image.firstFreeZone()
        if (slot == 0) { state.say("区域已满 68 个"); return }
        if (!image.writeZone(slot, name, channels)) { state.say("写入失败"); return }
        state.zones = image.zones()
        state.dirty = true
        state.say(
            if (index > 0) "区域「$name」已修改，含 ${channels.size} 条信道"
            else "已新建区域「$name」，含 ${channels.size} 条信道"
        )
    }

    private fun deleteZone(z: Zone) {
        val image = state.image ?: return
        image.clearZone(z.index)
        state.zones = image.zones()
        state.dirty = true
        state.say("已删除区域：${z.name}")
    }
    private fun saveDmrId(id: Long) {
        val image = state.image ?: return
        image.setDmrId(id)
        state.general = image.generalSettings()
        state.dirty = true
        state.say("DMR ID 改为 $id")
    }
    private fun saveAprs(entry: AprsEntry) {
        val image = state.image ?: return
        image.writeAprsEntry(entry)
        state.aprs = image.aprsEntries()
        state.dirty = true
        state.say("APRS-${entry.index} 改了：${entry.name}")
    }

    private fun saveRxGroup(index: Int, name: String, members: List<Int>) {
        val image = state.image ?: return
        val slot = if (index > 0) index else image.firstFreeRxGroup()
        if (slot == 0) { state.say("通话组已满 76 个"); return }
        if (!image.writeRxGroup(slot, name, members)) { state.say("写入失败"); return }
        state.rxGroups = image.rxGroups()
        state.dirty = true
        state.say(
            if (index > 0) "通话组「$name」已修改，含 ${members.size} 个联系人"
            else "已新建通话组「$name」，含 ${members.size} 个联系人"
        )
    }

    private fun deleteRxGroup(g: RxGroup) {
        val image = state.image ?: return
        image.clearRxGroup(g.index)
        state.rxGroups = image.rxGroups()
        state.dirty = true
        state.say("已删除通话组：${g.name}")
    }
    /** 把某条信道挂到 / 摘离一套 APRS 配置。手册：信道不挂就不发信标。 */
    private fun saveChannelAprs(index: Int, config: Int) {
        val image = state.image ?: return
        image.setChannelAprs(index, config)
        state.channels = image.channels()
        state.dirty = true
        state.say(
            if (config == 0) "第 ${index + 1} 条不挂 APRS 了"
            else "第 ${index + 1} 条挂上 APRS-$config"
        )
    }

    private fun addChannel(copyFrom: Int?) {
        val image = state.image ?: return
        val at = image.appendChannel(copyFrom)
        if (at < 0) { state.say("加不进去了"); return }
        state.channels = image.channels()
        state.dirty = true
        state.say(if (copyFrom == null) "新建了第 ${at + 1} 条" else "复制成第 ${at + 1} 条")
    }

    private fun deleteChannel(index: Int) {
        val image = state.image ?: return
        if (image.deleteChannel(index)) {
            state.channels = image.channels()
            state.dirty = true
            state.say("删掉了第 ${index + 1} 条")
        }
    }

    private fun renameContact(c: Contact, name: String) {
        val image = state.image ?: return
        image.renameContact(c.index, name)
        state.contacts = image.contacts()
        state.dirty = true
        state.say("联系人改名：$name")
    }

    private fun renameZone(z: Zone, name: String) {
        val image = state.image ?: return
        image.renameZone(z.index, name)
        state.zones = image.zones()
        state.dirty = true
        state.say("区域改名：$name")
    }

    private fun addContact(name: String, number: Long, group: Boolean) {
        val image = state.image ?: return
        val at = image.appendContact(name, number, group)
        if (at < 0) { state.say("联系人满了"); return }
        state.contacts = image.contacts()
        state.dirty = true
        state.say("新建联系人：$name / $number / ${if (group) "组呼" else "个呼"}")
    }

    private fun deleteContact(c: Contact) {
        val image = state.image ?: return
        if (image.deleteContact(c.index)) {
            state.contacts = image.contacts()
            state.dirty = true
            state.say("删掉联系人：${c.name}")
        }
    }

    private fun setContactNumber(c: Contact, number: Long) {
        val image = state.image ?: return
        image.setContactNumber(c.index, number)
        state.contacts = image.contacts()
        state.dirty = true
        state.say("${c.name} 的号码改成 $number")
    }

    private fun batchPatch(indices: List<Int>, field: String, value: Int) {
        val image = state.image ?: return
        image.patchChannels(indices, field, value)
        state.channels = image.channels()
        state.dirty = true
        state.say("批量改了 ${indices.size} 条：$field = $value")
    }

    private fun setThemeColour(type: Int, index: Int, rgbHex: Int) {
        val image = state.image ?: return
        val raw = image.toByteArray()
        CustomData.setThemeColour(raw, type, index, CustomData.rgbTo565(rgbHex))
        runCatching { image.replaceBytes(raw) }
        state.themeDay = CustomData.themeColours(raw, CustomData.TYPE_THEME_DAY)
        state.themeNight = CustomData.themeColours(raw, CustomData.TYPE_THEME_NIGHT)
        state.dirty = true
        state.say("主题颜色 ${index + 1} 改成 #%06X".format(rgbHex))
    }

    private fun saveMelody(text: String) {
        val image = state.image ?: return
        val parts = text.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size % 2 != 0) { state.say("要成对给：音高,时长,音高,时长…"); return }
        val pairs = ArrayList<Pair<Int, Int>>()
        for (i in parts.indices step 2) {
            val note = parts[i].toIntOrNull()
            val dur = parts[i + 1].toIntOrNull()
            if (note == null || dur == null) { state.say("第 ${i / 2 + 1} 个音念不出来"); return }
            if (note !in -128..127 || dur !in 0..255) { state.say("音高 -128..127、时长 0..255"); return }
            pairs.add(note to dur)
        }
        val raw = image.toByteArray()
        CustomData.setMelody(raw, pairs)
        image.replaceBytes(raw)
        state.melodyText = pairs.joinToString(",") { "${it.first},${it.second}" }
        state.melodyLen = pairs.size
        state.dirty = true
        state.say("开机旋律改成 ${pairs.size} 个音")
    }

    /**
     * 8 颗星的 NORAD 目录号，按机器里的顺序排。
     *
     * 不按名字匹配：CelesTrak 的 amateur 分组里根本没有 AO-91 和 PO-101，
     * 而 "SWISS" 里含 "ISS"，按名字会把 SWISSCUBE 当成 ISS。按编号最稳。
     */
    private val SAT_NORAD = listOf(
        61781, // AO-123 (ASRTU-1)
        43017, // AO-91 (FOX-1B)
        25544, // ISS (ZARYA)
        40908, // LilacSat-2
        43678, // PO-101 (DIWATA-2B)
        27607, // SO-50 (SAUDISAT 1C)
        59112, // SONATE-2
        57172, // UmKA-1 (RS40S)
    )

    private fun writeKeps() {
        val image = state.image ?: run { state.say("先打开一个文件"); return }
        if (state.busy) return
        state.busy = true
        lifecycleScope.launch {
            try {
                state.say("正在拉最新 TLE（${SAT_NORAD.size} 颗）…")
                val list = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    for (id in SAT_NORAD) {
                        val one = runCatching {
                            val conn = URL("https://celestrak.org/NORAD/elements/gp.php?CATNR=$id&FORMAT=tle")
                                .openConnection() as HttpURLConnection
                            conn.connectTimeout = 15000
                            conn.readTimeout = 25000
                            conn.inputStream.bufferedReader().use { it.readText() }
                        }.getOrNull() ?: continue
                        if (one.contains("No GP data found", ignoreCase = true)) continue
                        sb.append(one)
                        if (!one.endsWith("\n")) sb.append('\n')
                    }
                    if (sb.isEmpty()) throw IllegalStateException("一颗都没拉下来，检查网络")
                    parseTleText(sb.toString())
                }
                state.tles = list
                val byId = list.associateBy { it.noradId }
                val chosen = SAT_NORAD.mapNotNull { byId[it] }
                if (chosen.isEmpty()) {
                    state.say("TLE 拉下来了，但对不上这 8 个编号")
                    return@launch
                }
                val table = withContext(Dispatchers.IO) {
                    assets.open("satellites.txt").bufferedReader().use { Keps.parseTable(it.readText()) }
                }
                val block = Keps.build(chosen, table)
                val raw = image.toByteArray()
                if (!Keps.writeInto(raw, block)) {
                    state.say("文件里没有 2520 字节的星历块，请先用电脑 CPS 写过一次")
                    return@launch
                }
                image.replaceBytes(raw)
                state.kepsSize = Keps.BLOCK_SIZE
                state.say("要落到机器上，去「读写」页点「写入」。那一步是整份覆盖，先确认手上这份是刚从对讲机读出来的。")
                state.dirty = true
                state.say("星历已写入（${chosen.size}/${SAT_NORAD.size} 颗）：" +
                    chosen.joinToString("、") { it.name })
            } catch (e: Exception) {
                state.say("写星历失败：${e.message}")
            } finally {
                state.busy = false
            }
        }
    }

    private fun loadBootImage(uri: Uri) {
        val image = state.image ?: run { state.say("先打开一个文件"); return }
        lifecycleScope.launch {
            try {
                val bits = withContext(Dispatchers.IO) {
                    val src = contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                        ?: throw IllegalStateException("这张图打不开")
                    BootImage.fromBitmap(src)
                }
                val raw = image.toByteArray()
                if (!BootImage.writeInto(raw, bits)) {
                    state.say("写不进开机图片块")
                    return@launch
                }
                image.replaceBytes(raw)
                val px = BootImage.decode(bits)
                val bmp = android.graphics.Bitmap.createBitmap(
                    BootImage.WIDTH, BootImage.HEIGHT, android.graphics.Bitmap.Config.ARGB_8888,
                )
                for (y in 0 until BootImage.HEIGHT) {
                    for (x in 0 until BootImage.WIDTH) {
                        bmp.setPixel(x, y, if (px[y][x]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                    }
                }
                state.bootBitmap = bmp
                state.dirty = true
                state.say("开机图片换了：128×64 单色")
            } catch (e: Exception) {
                state.say("换开机图片失败：${e.message}")
            }
        }
    }

    private fun loadFirmware(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("打不开这个文件")
                }
                val fw = FirmwareFile(uri.lastPathSegment ?: "固件", bytes)
                state.firmware = fw
                fw.sanity()?.let { state.say("注意：$it") }
                state.say("固件：${fw.name}，${fw.size / 1024} KB，CRC ${"%08X".format(fw.crc)}")
            } catch (e: Exception) {
                state.say("读固件失败：${e.message}")
            }
        }
    }

    private fun probeDfu() {
        val list = DfuProbe.devices(this)
        state.dfuDevices = list.map { DfuProbe.describe(it) }
        if (list.isEmpty()) state.say("没找到 DFU 设备：机器要先进 bootloader")
        else { state.say("找到 DFU 设备 ${list.size} 个"); state.dfuDevices.forEach { state.say("  $it") } }
    }

    private fun flashFirmware() {
        state.say("固件刷写还没接完")
    }

    private fun exportCsv(uri: Uri) {
        val image = state.image ?: run { state.say("还没有数据"); return }
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                        ChannelCsv.write(image, state.channels, w)
                    } ?: throw IllegalStateException("写不进这个位置")
                }
                state.say("导出 CSV：${state.channels.size} 条")
            } catch (e: Exception) {
                state.say("导出 CSV 失败：${e.message}")
            }
        }
    }

    private fun importCsv(uri: Uri) {
        val image = state.image ?: run { state.say("先打开一个文件"); return }
        lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { ChannelCsv.read(it) }
                        ?: throw IllegalStateException("打不开这个文件")
                }
                var changed = 0
                var added = 0
                for (row in rows) {
                    val idx = row.number?.minus(1)
                    val current = idx?.let { i -> state.channels.firstOrNull { it.index == i } }
                    if (current != null) {
                        ChannelCsv.apply(image, current.index, row, current)
                        changed++
                    } else {
                        val at = image.appendChannel(null)
                        if (at < 0) break
                        val fresh = image.channels().firstOrNull { it.index == at }
                        if (fresh != null) {
                            ChannelCsv.apply(image, at, row, fresh)
                            added++
                        }
                    }
                }
                state.channels = image.channels()
                state.dirty = true
                state.say("导入 CSV：改了 $changed 条，新增 $added 条")
            } catch (e: Exception) {
                state.say("导入 CSV 失败：${e.message}")
            }
        }
    }

    private fun saveSettings(
        callsign: String, line1: String, line2: String, textMode: Boolean, nums: IntArray,
    ) {
        val image = state.image ?: return
        image.setCallsign(callsign)
        image.setBootLines(line1, line2, textMode)
        if (nums.size >= 10) {
            image.setGeneralNumbers(
                nums[0], nums[1], nums[2], nums[3], nums[4],
                nums[5], nums[6], nums[7], nums[8], nums[9],
            )
        }
        state.general = image.generalSettings()
        state.dirty = true
        state.say("设置改了：呼号 $callsign")
    }

    // ---- USB ----

    private fun onConnectClick() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val device = UsbCps.pickDevice(this)
        if (device == null) {
            state.say("没找到设备：把写频线插上，机器开机")
            return
        }
        val name = "${device.productName ?: device.deviceName} (VID %04X PID %04X)"
            .format(device.vendorId, device.productId)
        if (manager.hasPermission(device)) {
            open(device, name)
        } else {
            state.say("等系统弹窗里点「允许」…")
            pending = { granted -> if (granted) open(device, name) else state.say("没给权限，连不上") }
            manager.requestPermission(
                device,
                PendingIntent.getBroadcast(
                    this, 0,
                    Intent(ACTION_USB_PERMISSION).setPackage(packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    private fun open(device: UsbDevice, name: String) {
        disconnect()
        val opened = UsbCps.open(this, device)
        if (opened == null) {
            state.say("打开了但拿不到接口，可能被别的程序占着")
            return
        }
        usb = opened
        currentDevice = device
        state.deviceName = name
        state.say("已连接：$name")
        // 连上就直接认机型（开场命令也跟着发），省得再点一次
        onReadInfoClick()
    }

    /** 关掉旧连接、重新打开并 claim，得到一个干净的状态。 */
    private fun reopenUsb(): UsbCps {
        val device = currentDevice ?: throw IllegalStateException("还没选设备")
        runCatching { usb?.close() }
        usb = null
        val opened = UsbCps.open(this, device)
            ?: throw IllegalStateException("重新打开设备失败")
        usb = opened
        return opened
    }

    private fun onReadInfoClick() {
        val link = usb ?: run { state.say("先点连接"); return }
        runTask {
            val info = Cps(link).readRadioInfo()
            state.info = info
            state.say("型号：${info.modelLabel}（code ${info.rawModel}）")
            state.say("固件：${info.gitRevision} / ${info.buildDateTime}")
        }
    }

    private fun onReadCodeplugClick() {
        val link = usb ?: run { state.say("先点连接"); return }
        runTask {
            // 先认机型再读：缓冲大小和 FLASH 地址偏移都取决于它，不认就读出垃圾
            // 每次读之前彻底重来一遍：关掉旧连接、重新 claim、清空缓冲，
            // 避免把上一轮卡住的状态带进来（对讲机被噎住就是这种情况）
            val fresh = reopenUsb()
            val cps = Cps(fresh)
            val info = cps.readRadioInfo()
            state.info = info
            state.say("机型 ${info.modelLabel}，缓冲 ${cps.channelBuffer}")
            state.say("开始读取，共 128 KB，需要一两分钟")
            val image = cps.readCodeplug { done, total -> state.progress = done.toFloat() / total }
            val file = File(filesDir, "codeplug-${System.currentTimeMillis()}.g77")
            file.writeBytes(image)
            state.lastImage = file
            withContext(Dispatchers.Main) { applyImage(image, "机器") }
        }
    }

    /** 让机器退出「读取中」状态：发 C5、C7，再断开。失败也要发。 */
    private fun endSession() {
        val link = usb ?: return
        runCatching { Cps(link).leaveRadio() }
    }

    private fun runTask(block: suspend () -> Unit) {
        if (state.busy) return
        state.busy = true
        state.progress = 0f
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: CpsProtocolError) {
                state.say("协议出错：${e.message}")
            } catch (e: Exception) {
                state.say("出错：${e.message ?: e::class.java.simpleName}")
            } finally {
                runCatching { endSession() }
                state.busy = false
                state.progress = null
            }
        }
    }

    private fun disconnect() {
        runCatching { usb?.let { Cps(it).leaveRadio() } }
        runCatching { usb?.close() }
        usb = null
        state.deviceName = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    state: AppState,
    onConnect: () -> Unit,
    onReadInfo: () -> Unit,
    onReadCodeplug: () -> Unit,
    onDisconnect: () -> Unit,
    onFilePicked: (Uri) -> Unit,
    onExport: (Uri) -> Unit,
    onChannelSaved: (ChannelEdit) -> Unit,
    onSettingsSaved: (String, String, String, Boolean, IntArray) -> Unit,
    onWriteConfirmed: () -> Unit,
    onMessageSaved: (Int, String) -> Unit,
    onDownloadDmrId: () -> Unit,
    onExportDmrId: (Uri) -> Unit,
    onWriteDmrId: () -> Unit,
    onPreviewScreen: () -> Unit,
    onSaveScreengrab: (Uri) -> Unit,
    onBackupMcuRom: (Uri) -> Unit,
    onVerify: () -> Unit,
    onFetchTle: () -> Unit,
    onComputePass: (Int, Double, Double, Double, Double) -> Unit,
    onWritePass: (Int) -> Unit,
    onVfoSaved: (Long, Long, Long, Long) -> Unit,
    onSideKeysSaved: (IntArray) -> Unit,
    onDmrIdSaved: (Long) -> Unit,
    onTonesSaved: (Boolean, Int, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onAprsSaved: (AprsEntry) -> Unit,
    onChannelAprsSaved: (Int, Int) -> Unit,
    onAddChannel: (Int?) -> Unit,
    onDeleteChannel: (Int) -> Unit,
    onRenameContact: (Contact, String) -> Unit,
    onRenameZone: (Zone, String) -> Unit,
    onSaveZone: (Int, String, List<Int>) -> Unit,
    onDeleteZone: (Zone) -> Unit,
    onAddContact: (String, Long, Boolean) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onContactNumber: (Contact, Long) -> Unit,
    onSaveGroup: (Int, String, List<Int>) -> Unit,
    onSaveDtmf: (Int, String, String) -> Unit,
    onDeleteDtmf: (DtmfContact) -> Unit,
    onDeleteGroup: (RxGroup) -> Unit,
    onBatchPatch: (List<Int>, String, Int) -> Unit,
    onFirmwarePicked: (Uri) -> Unit,
    onCsvExport: (Uri) -> Unit,
    onCsvImport: (Uri) -> Unit,
    onThemeColour: (Int, Int, Int) -> Unit,
    onWriteKeps: () -> Unit,
    onFlash: () -> Unit,
    onMelodySaved: (String) -> Unit,
    onBootImagePicked: (Uri) -> Unit,
    onProbeDfu: () -> Unit,
    onReadCalibration: () -> Unit,
    onExportCalibration: (Uri) -> Unit,
    onWriteCalibration: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.CHANNELS) }
    var editing by remember { mutableStateOf<Channel?>(null) }
    var showWriteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Channel?>(null) }

    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onFilePicked(uri)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) onExport(uri)
    }
    val firmwarePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onFirmwarePicked(uri)
    }
    val csvExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) onCsvExport(uri)
    }
    val bootImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onBootImagePicked(uri)
    }
    val csvImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onCsvImport(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(tab.label)
                    Text(
                        state.deviceName ?: state.sourceName ?: "还没读取",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.CHANNELS -> Icons.Filled.Podcasts
                                    Tab.ZONES -> Icons.Filled.Folder
                                    Tab.CONTACTS -> Icons.Filled.Groups
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                    Tab.TRANSFER -> Icons.Filled.Usb
                                    Tab.TOOLS -> Icons.Filled.Build
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.CHANNELS -> ChannelScreen(
                state, onOpenFile = { openPicker.launch(arrayOf("*/*")) },
                onExport = { exportPicker.launch("codeplug-改过.g77") },
                onEdit = { editing = it },
                onBatch = { idx, field, v -> onBatchPatch(idx, field, v) },
                onAdd = { onAddChannel(null) },
                onCopy = { onAddChannel(it.index) },
                onDelete = { onDeleteChannel(it.index) },
                modifier = Modifier.padding(padding),
            )
            Tab.ZONES -> ZoneScreen(
                state,
                onRename = onRenameZone,
                onSaveZone = onSaveZone,
                onDeleteZone = onDeleteZone,
                modifier = Modifier.padding(padding),
            )
            Tab.CONTACTS -> ContactScreen(
                state,
                onRename = onRenameContact,
                onAdd = onAddContact,
                onDelete = onDeleteContact,
                onNumber = onContactNumber,
                onSaveGroup = onSaveGroup,
                onSaveDtmf = onSaveDtmf,
                onDeleteDtmf = onDeleteDtmf,
                onDeleteGroup = onDeleteGroup,
                modifier = Modifier.padding(padding),
            )
            Tab.SETTINGS -> SettingsScreen(
                state,
                onSave = onSettingsSaved,
                onMessageSaved = onMessageSaved,
                onVfoSaved = onVfoSaved,
                onSideKeysSaved = onSideKeysSaved,
                onDmrIdSaved = onDmrIdSaved,
                onTonesSaved = onTonesSaved,
                onAprsSaved = onAprsSaved,
                onExport = { exportPicker.launch("codeplug-改过.g77") },
                modifier = Modifier.padding(padding),
            )
            Tab.TOOLS -> ToolScreen(
                state,
                onBackup = { exportPicker.launch("codeplug-备份.g77") },
                onRestore = { openPicker.launch(arrayOf("*/*")) },
                onVerify = onVerify,
                onFetchTle = onFetchTle,
                onComputePass = onComputePass,
                onWritePass = onWritePass,
                onPickFirmware = { firmwarePicker.launch(arrayOf("*/*")) },
                onCsvExport = { csvExportPicker.launch("channels.csv") },
                onCsvImport = { csvImportPicker.launch(arrayOf("*/*")) },
                onThemeColour = onThemeColour,
                onWriteKeps = onWriteKeps,
                onFlash = onFlash,
                onMelodySaved = onMelodySaved,
                onBootImagePicked = onBootImagePicked,
                onPickBootImage = { bootImagePicker.launch(arrayOf("image/*")) },
                onProbeDfu = onProbeDfu,
                onReadCalibration = onReadCalibration,
                onExportCalibration = onExportCalibration,
                onWriteCalibration = onWriteCalibration,
                onMessageSaved = onMessageSaved,
                onDownloadDmrId = onDownloadDmrId,
                onExportDmrId = onExportDmrId,
                onWriteDmrId = onWriteDmrId,
                onPreviewScreen = onPreviewScreen,
                onSaveScreengrab = onSaveScreengrab,
                onBackupMcuRom = onBackupMcuRom,
                modifier = Modifier.padding(padding),
            )
            Tab.TRANSFER -> TransferScreen(
                state, onConnect, onReadInfo, onReadCodeplug, onDisconnect,
                onWriteBack = { showWriteConfirm = true },
                Modifier.padding(padding),
            )
        }
    }

    if (showWriteConfirm) {
        AlertDialog(
            onDismissRequest = { showWriteConfirm = false },
            title = { Text("写回机器？") },
            text = {
                Text(
                    "会把当前数据整份写进对讲机。写的过程中别拔线、别让屏幕熄灭，" +
                        "大概一两分钟。写完会再读一遍，逐字节比对。"
                )
            },
            confirmButton = {
                Button(onClick = { showWriteConfirm = false; onWriteConfirmed() }) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("写入")
                }
            },
            dismissButton = { TextButton(onClick = { showWriteConfirm = false }) { Text("取消") } },
        )
    }

    deleting?.let { ch ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删掉第 ${ch.index + 1} 条？") },
            text = { Text("「${ch.name.ifBlank { "(无名)" }}」会被删掉，后面的信道往前挪一位。") },
            confirmButton = {
                Button(
                    onClick = { onDeleteChannel(ch.index); deleting = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("删掉")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }

    editing?.let { ch ->
        ChannelEditDialog(
            channel = ch,
            contacts = state.contacts,
            rxGroups = state.rxGroups,
            aprs = state.aprs,
            onDismiss = { editing = null },
            onSave = { edit ->
                onChannelSaved(edit)
                editing = null
            },
        )
    }
}

@Composable
private fun ChannelScreen(
    state: AppState,
    onOpenFile: () -> Unit,
    onExport: () -> Unit,
    onEdit: (Channel) -> Unit,
    onBatch: (List<Int>, String, Int) -> Unit,
    onAdd: () -> Unit,
    onCopy: (Channel) -> Unit,
    onDelete: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(0) }   // 0 原始 1 名字 2 频率
    var batchOpen by remember { mutableStateOf(false) }

    val shown = remember(state.channels, query, sortMode) {
        val filtered = if (query.isBlank()) state.channels else state.channels.filter { ch ->
            ch.name.contains(query, ignoreCase = true) ||
                "%.4f".format(ch.rxMhz).contains(query) ||
                "%.4f".format(ch.txMhz).contains(query)
        }
        when (sortMode) {
            1 -> filtered.sortedBy { it.name.lowercase() }
            2 -> filtered.sortedBy { it.rxHz }
            else -> filtered
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜名字或频率") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (query.isBlank()) "${state.channels.size} 条" else "${shown.size} / ${state.channels.size} 条",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.dirty) {
                    TextButton(onClick = onExport) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("导出")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpenFile, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("打开")
                }
                if (state.image != null) {
                    OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("新建") }
                    OutlinedButton(
                        onClick = { batchOpen = true },
                        enabled = shown.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("批量") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sortMode == 0, onClick = { sortMode = 0 }, label = { Text("原序") })
                FilterChip(selected = sortMode == 1, onClick = { sortMode = 1 }, label = { Text("按名字") })
                FilterChip(selected = sortMode == 2, onClick = { sortMode = 2 }, label = { Text("按频率") })
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown) { ch ->
                ChannelCard(
                    ch,
                    onClick = { onEdit(ch) },
                    onCopy = { onCopy(ch) },
                    onDelete = { onDelete(ch) },
                )
            }
        }
    }

    if (batchOpen) {
        var field by remember { mutableStateOf("cc") }
        var value by remember { mutableStateOf("1") }
        val labels = listOf(
            "cc" to "色码", "ts" to "时隙", "power" to "功率",
            "bw" to "带宽", "rxonly" to "只收",
        )
        AlertDialog(
            onDismissRequest = { batchOpen = false },
            title = { Text("批量改这 ${shown.size} 条") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (query.isBlank()) "当前全部信道" else "当前筛选出来的",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.take(3).forEach { (k, label) ->
                            FilterChip(selected = field == k, onClick = { field = k }, label = { Text(label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.drop(3).forEach { (k, label) ->
                            FilterChip(selected = field == k, onClick = { field = k }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.filter { c -> c.isDigit() }.take(2) },
                        label = {
                            Text(
                                when (field) {
                                    "cc" -> "色码 0-15"
                                    "ts" -> "时隙 1 或 2"
                                    "power" -> "功率 0/1/2"
                                    "bw" -> "带宽 0=25k 1=12.5k"
                                    else -> "只收 0/1"
                                }
                            )
                        },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = when (field) {
                        "ts" -> ((value.toIntOrNull() ?: 1) - 1).coerceIn(0, 1)
                        else -> (value.toIntOrNull() ?: 0).coerceIn(0, 15)
                    }
                    onBatch(shown.map { it.index }, field, v)
                    batchOpen = false
                }) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = { batchOpen = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ChannelCard(
    ch: Channel,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ch.index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    ch.name.ifBlank { "(无名)" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    buildString {
                        append(ch.modeLabel)
                        if (ch.digital) append(" · ${ch.tsLabel} · CC${ch.colorCode}")
                        if (ch.rxOnly) append(" · 只收")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "%.4f / %.4f MHz".format(ch.rxMhz, ch.txMhz),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun ZoneScreen(
    state: AppState,
    onRename: (Zone, String) -> Unit,
    onSaveZone: (Int, String, List<Int>) -> Unit,
    onDeleteZone: (Zone) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Zone?>(null) }
    var deleting by remember { mutableStateOf<Zone?>(null) }

    if (state.zones.isEmpty() && state.channels.isEmpty()) {
        Placeholder("区域", "先打开一个 .g77 文件，或从对讲机读取。", modifier)
        return
    }

    val byNumber = state.channels.associateBy { it.index + 1 }
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("区域", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${state.zones.size} 个",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { editing = Zone(0, "", emptyList()) },
                        enabled = state.image != null,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("新建")
                    }
                }
            }
            items(state.zones) { z ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().clickable { editing = z },
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                z.name.ifBlank { "(无名区域)" },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${z.channelNumbers.size} 条　" + z.channelNumbers.take(6)
                                    .mapNotNull { n -> byNumber[n]?.name?.ifBlank { "#$n" } ?: "#$n" }
                                    .joinToString(" · ") + if (z.channelNumbers.size > 6) " …" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { deleting = z }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "删除 ${z.name}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        editing?.let { z ->
            ZoneEditDialog(
                zone = z,
                channels = state.channels,
                onDismiss = { editing = null },
                onSave = { name, numbers ->
                    onSaveZone(z.index, name, numbers)
                    editing = null
                },
            )
        }

        deleting?.let { z ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("删除区域「${z.name}」？") },
                text = { Text("只删这一组，区域里的信道本身不会被删。") },
                confirmButton = {
                    Button(
                        onClick = { onDeleteZone(z); deleting = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("删除")
                    }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
            )
        }
    }
}

/**
 * 区域编辑：名称 + 从信道里挑成员（最多 80 条）。
 * 文件里每个区域 176 字节：名称 16 字节，后跟 80 个信道号，空位为 0。
 */
@Composable
private fun ZoneEditDialog(
    zone: Zone,
    channels: List<Channel>,
    onDismiss: () -> Unit,
    onSave: (String, List<Int>) -> Unit,
) {
    var name by remember { mutableStateOf(zone.name) }
    val chosen = remember { mutableStateListOf<Int>().also { it.addAll(zone.channelNumbers) } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    if (zone.index == 0) "新建区域" else "区域「${zone.name}」",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "区域是一组信道，机器上切换区域时按这里的顺序走，最多 80 条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    label = { Text("名称") },
                    supportingText = { Text("最多 16 个字符", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("信道（已选 ${chosen.size} / 80）", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(channels) { ch ->
                        val number = ch.index + 1
                        val picked = chosen.contains(number)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (picked) chosen.remove(number)
                                    else if (chosen.size < 80) chosen.add(number)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = picked, onCheckedChange = null)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "${number}. ${ch.name.ifBlank { "(无名)" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "%.4f".format(ch.rxMhz),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = { onSave(name.trim(), chosen.toList()) },
                        enabled = name.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactScreen(
    state: AppState,
    onRename: (Contact, String) -> Unit,
    onAdd: (String, Long, Boolean) -> Unit,
    onDelete: (Contact) -> Unit,
    onNumber: (Contact, Long) -> Unit,
    onSaveGroup: (Int, String, List<Int>) -> Unit,
    onSaveDtmf: (Int, String, String) -> Unit,
    onDeleteDtmf: (DtmfContact) -> Unit,
    onDeleteGroup: (RxGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<Contact?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Contact?>(null) }
    var editingGroup by remember { mutableStateOf<RxGroup?>(null) }
    var editingDtmf by remember { mutableStateOf<DtmfContact?>(null) }
    var deletingDtmf by remember { mutableStateOf<DtmfContact?>(null) }
    var deletingGroup by remember { mutableStateOf<RxGroup?>(null) }
    if (state.contacts.isEmpty() && state.rxGroups.isEmpty()) {
        Placeholder("联系人", "先打开一个 .g77 文件，或从对讲机读取。", modifier)
        return
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "联系人",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.contacts.size} 个",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { adding = true }, enabled = state.image != null) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("新建")
                    }
                }
            }
            items(state.contacts) { c ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().clickable { renaming = c },
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.name.ifBlank { "(无名)" },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${c.number}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    c.kindLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        IconButton(onClick = { deleting = c }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "删掉 ${c.name}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("DTMF 联系人", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${state.dtmf.size} 个",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { editingDtmf = DtmfContact(0, "", "") },
                        enabled = state.image != null,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("新建")
                    }
                }
            }
            items(state.dtmf) { d ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().clickable { editingDtmf = d },
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name.ifBlank { "(无名)" }, style = MaterialTheme.typography.titleSmall)
                            Text(
                                d.code.ifBlank { "（空号码）" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { deletingDtmf = d }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "删除 ${d.name}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("通话组", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${state.rxGroups.size} 个",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { editingGroup = RxGroup(0, "", emptyList()) },
                        enabled = state.image != null,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("新建")
                    }
                }
            }
            items(state.rxGroups) { g ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().clickable { editingGroup = g },
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "含 ${g.memberCount} 个联系人",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { deletingGroup = g }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "删除 ${g.name}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        if (adding) {
            var name by remember { mutableStateOf("") }
            var num by remember { mutableStateOf("") }
            var group by remember { mutableStateOf(true) }
            AlertDialog(
                onDismissRequest = { adding = false },
                title = { Text("新建联系人") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(name, { name = it.take(8) }, label = { Text("名字（最多 8）") }, singleLine = true)
                        OutlinedTextField(
                            num,
                            { v -> num = v.filter { it.isDigit() }.take(8) },
                            label = { Text("号码（通话组号或 DMR ID）") },
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = group, onClick = { group = true }, label = { Text("组呼") })
                            FilterChip(selected = !group, onClick = { group = false }, label = { Text("个呼") })
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val n = num.toLongOrNull()
                        if (name.isBlank() || n == null) return@Button
                        onAdd(name, n, group)
                        adding = false
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("创建")
                    }
                },
                dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } },
            )
        }

        deleting?.let { c ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("删掉联系人 ${c.name}？") },
                text = { Text("后面的联系人会往前挪一位，信道里引用的编号可能跟着变——删之前先想一下。") },
                confirmButton = {
                    Button(
                        onClick = { onDelete(c); deleting = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("删掉")
                    }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
            )
        }

        renaming?.let { c ->
            var draft by remember(c) { mutableStateOf(c.name) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("改联系人名字") },
                text = {
                    Column {
                        OutlinedTextField(draft, { draft = it.take(8) }, label = { Text("名字") }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        var numDraft by remember(c) { mutableStateOf(c.number.toString()) }
                        OutlinedTextField(
                            numDraft,
                            { v -> numDraft = v.filter { it.isDigit() }.take(8) },
                            label = { Text("号码") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = {
                            numDraft.toLongOrNull()?.let { onNumber(c, it) }
                        }) { Text("只改号码") }
                    }
                },
                confirmButton = {
                    Button(onClick = { onRename(c, draft); renaming = null }) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("保存")
                    }
                },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
            )
        }

        editingGroup?.let { g ->
            RxGroupEditDialog(
                group = g,
                contacts = state.contacts,
                onDismiss = { editingGroup = null },
                onSave = { name, members ->
                    onSaveGroup(g.index, name, members)
                    editingGroup = null
                },
            )
        }

        deletingGroup?.let { g ->
            AlertDialog(
                onDismissRequest = { deletingGroup = null },
                title = { Text("删除通话组「${g.name}」？") },
                text = { Text("引用这个通话组的信道会失去接收组，需要重新指定。") },
                confirmButton = {
                    Button(
                        onClick = { onDeleteGroup(g); deletingGroup = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("删除")
                    }
                },
                dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text("取消") } },
            )
        }
        editingDtmf?.let { d ->
            DtmfEditDialog(
                contact = d,
                onDismiss = { editingDtmf = null },
                onSave = { dtName, dtCode ->
                    onSaveDtmf(d.index, dtName, dtCode)
                    editingDtmf = null
                },
            )
        }

        deletingDtmf?.let { d ->
            AlertDialog(
                onDismissRequest = { deletingDtmf = null },
                title = { Text("删除 DTMF 联系人「${d.name}」？") },
                text = { Text("只删这一条，其他联系人不受影响。") },
                confirmButton = {
                    Button(
                        onClick = { onDeleteDtmf(d); deletingDtmf = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("删除")
                    }
                },
                dismissButton = { TextButton(onClick = { deletingDtmf = null }) { Text("取消") } },
            )
        }
    }
}

/**
 * 通话组编辑：名称 + 从联系人里挑成员（最多 32 个）。
 * 文件里每组 80 字节：名称 16 字节，后跟 32 个联系人序号，空位为 0xFFFF。
 */
@Composable
private fun RxGroupEditDialog(
    group: RxGroup,
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onSave: (String, List<Int>) -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    val chosen = remember { mutableStateListOf<Int>().also { it.addAll(group.members) } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    if (group.index == 0) "新建通话组" else "通话组「${group.name}」",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "通话组是一组可同时接收的联系人，供信道指定为接收组使用，最多 32 个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    label = { Text("名称") },
                    supportingText = { Text("最多 16 个字符", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "成员（已选 ${chosen.size} / 32）",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(contacts) { c ->
                        val picked = chosen.contains(c.index)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (picked) chosen.remove(c.index)
                                    else if (chosen.size < 32) chosen.add(c.index)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = picked, onCheckedChange = null)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                c.name.ifBlank { "(无名)" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${c.number}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = { onSave(name.trim(), chosen.toList()) },
                        enabled = name.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("保存")
                    }
                }
            }
        }
    }
}

/**
 * DTMF 联系人编辑：名称 + 号码（0-9、A-D、*、#，最多 16 位）。
 * 文件里每条 32 字节：名称 16 字节，号码 16 字节，号码按字符表存下标。
 */
@Composable
private fun DtmfEditDialog(
    contact: DtmfContact,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(contact.name) }
    var code by remember { mutableStateOf(contact.code) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (contact.index == 0) "新建 DTMF 联系人" else "DTMF「${contact.name}」"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    label = { Text("名称") },
                    supportingText = { Text("最多 16 个字符", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = v.uppercase().filter { c -> c in "0123456789ABCD*#" }.take(16)
                    },
                    label = { Text("号码") },
                    supportingText = {
                        Text(
                            "0-9、A-D、*、#，最多 16 位",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), code) },
                enabled = name.isNotBlank() && code.isNotBlank(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
@Composable
private fun SettingsScreen(
    state: AppState,
    onSave: (String, String, String, Boolean, IntArray) -> Unit,
    onMessageSaved: (Int, String) -> Unit,
    onVfoSaved: (Long, Long, Long, Long) -> Unit,
    onSideKeysSaved: (IntArray) -> Unit,
    onDmrIdSaved: (Long) -> Unit,
    onTonesSaved: (Boolean, Int, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onAprsSaved: (AprsEntry) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = state.general
    var callsign by remember(g) { mutableStateOf(g?.callsign ?: "") }
    var line1 by remember(g) { mutableStateOf(g?.bootLine1 ?: "") }
    var line2 by remember(g) { mutableStateOf(g?.bootLine2 ?: "") }
    var textMode by remember(g) { mutableStateOf(g?.bootTextMode ?: true) }
    fun initOf(v: Int?) = (v ?: 0).toString()
    var arsDly by remember(g) { mutableStateOf(initOf(g?.arsInitDly)) }
    var preamble by remember(g) { mutableStateOf(initOf(g?.txPreambleDur)) }
    var monitor by remember(g) { mutableStateOf((g?.monitorType ?: 0) == 1) }
    var vox by remember(g) { mutableStateOf(initOf(g?.voxSense)) }
    var lowBatt by remember(g) { mutableStateOf(initOf(g?.rxLowBatt)) }
    var callAlert by remember(g) { mutableStateOf(initOf(g?.callAlertDur)) }
    var respTmr by remember(g) { mutableStateOf(initOf(g?.respTmr)) }
    var reminderTmr by remember(g) { mutableStateOf(initOf(g?.reminderTmr)) }
    var grpHang by remember(g) { mutableStateOf(initOf(g?.grpHang)) }
    var privHang by remember(g) { mutableStateOf(initOf(g?.privateHang)) }
    var exitTone by remember(g) { mutableStateOf(g?.exitTone ?: false) }
    var talkPermit by remember(g) { mutableStateOf(g?.talkPermitTone ?: 0) }
    var disableAllTones by remember(g) { mutableStateOf(g?.disableAllTones ?: false) }
    var crescendoTone by remember(g) { mutableStateOf(g?.crescendoTone ?: false) }
    var chFreeTone by remember(g) { mutableStateOf(g?.chFreeTone ?: false) }
    var selfTestTone by remember(g) { mutableStateOf(g?.selfTestTone ?: false) }
    var resetTone by remember(g) { mutableStateOf(g?.resetTone ?: false) }
    var unfamiliarTone by remember(g) { mutableStateOf(g?.unfamiliarTone ?: false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("呼号与 DMR ID", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = callsign,
                    onValueChange = { callsign = it.take(8) },
                    label = { Text("呼号（最多 8 位）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                var dmrDraft by remember(g) { mutableStateOf((g?.dmrId ?: 0L).toString()) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = dmrDraft,
                        onValueChange = { v -> dmrDraft = v.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("DMR ID") },
                        supportingText = { Text("最多 8 位数字", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        dmrDraft.toLongOrNull()?.let { onDmrIdSaved(it) }
                    }) { Text("保存") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("开机画面", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("显示文字", modifier = Modifier.weight(1f))
                    Switch(checked = textMode, onCheckedChange = { textMode = it })
                }
                OutlinedTextField(
                    value = line1,
                    onValueChange = { line1 = it.take(16) },
                    label = { Text("第一行") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = line2,
                    onValueChange = { line2 = it.take(16) },
                    label = { Text("第二行") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("总体设置", style = MaterialTheme.typography.titleSmall)
                Text(
                    "数值与单位照 CPS 界面显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("监听方式", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (monitor) "静音：按监听键只是打开接收，不出声" else "常开静噪：按监听键直接放开静噪，能听到弱信号和噪声",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = monitor, onCheckedChange = { monitor = it })
                }
                SettingNumber(
                    "ARS 初始化延迟", "分钟",
                    "开机后等这么久才去试着找中继，0 到 240（每档 30 分钟）",
                    arsDly, 30, 0..8,
                ) { arsDly = it }
                SettingNumber(
                    "发射前导码时长", "毫秒",
                    "每次按下发射时，先空发这么久的载波再送语音，0 到 8640（每档 60）",
                    preamble, 60, 0..144,
                ) { preamble = it }
                SettingNumber(
                    "VOX 声控灵敏度", "级",
                    "声控发射的灵敏度，1 最不灵敏、10 最容易触发。不用声控就无所谓",
                    vox, 1, 1..10,
                ) { vox = it }
                SettingNumber(
                    "低电提示间隔", "秒",
                    "电池快没电时，每隔这么久提醒一次，0 到 635（每档 5 秒）",
                    lowBatt, 5, 0..127,
                ) { lowBatt = it }
                SettingNumber(
                    "呼叫提示音时长", "秒",
                    "收到呼叫时响提示音的时间，0 到 1200（每档 5 秒）",
                    callAlert, 5, 0..240,
                ) { callAlert = it }
                SettingNumber(
                    "单独工作响应计时", "分钟",
                    "单独工作模式下，多久没响应就报警，1 到 255 分钟",
                    respTmr, 1, 1..255,
                ) { respTmr = it }
                SettingNumber(
                    "单独工作提醒计时", "秒",
                    "单独工作模式下，隔多久提醒你按一下确认键，1 到 255 秒",
                    reminderTmr, 1, 1..255,
                ) { reminderTmr = it }
                SettingNumber(
                    "组呼挂起时间", "毫秒",
                    "一次组呼说完后，还占着信道等回话的时间，0 到 7000（每档 500）",
                    grpHang, 500, 0..14,
                ) { grpHang = it }
                SettingNumber(
                    "个呼挂起时间", "毫秒",
                    "一次个呼说完后，还占着信道等回话的时间，0 到 7000（每档 500）",
                    privHang, 500, 0..14,
                ) { privHang = it }
            }
        }



        Spacer(Modifier.height(12.dp))

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("提示音", style = MaterialTheme.typography.titleSmall)
                Text(
                    "机器自身的提示音开关，与信道设置无关。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToneSwitch("发射退出提示音", "松开 PTT、结束发射时的那一声", exitTone) { exitTone = it }
                ToneSwitch("禁用全部提示音", "一次关掉机器上的所有提示音", disableAllTones) { disableAllTones = it }
                ToneSwitch("渐强提示音", "提示音由轻到响", crescendoTone) { crescendoTone = it }
                ToneSwitch("信道空闲提示音", "信道空闲下来时提示一声", chFreeTone) { chFreeTone = it }
                ToneSwitch("自检通过提示音", "开机自检通过时响一声", selfTestTone) { selfTestTone = it }
                ToneSwitch("复位提示音", "复位操作完成时的提示音", resetTone) { resetTone = it }
                ToneSwitch("陌生号码提示音", "收到陌生号码呼叫时提示", unfamiliarTone) { unfamiliarTone = it }
                Column {
                    Text("通话许可提示音", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "按下 PTT、可以开口说话时的那一声",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("无", "数字", "模拟", "两者").forEachIndexed { i, label ->
                            FilterChip(
                                selected = talkPermit == i,
                                onClick = { talkPermit = i },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                OutlinedButton(onClick = {
                    onTonesSaved(
                        exitTone, talkPermit, disableAllTones, crescendoTone,
                        chFreeTone, selfTestTone, resetTone, unfamiliarTone,
                    )
                }) { Text("保存提示音") }
            }
        }

        if (state.aprs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            var editingAprs by remember { mutableStateOf<AprsEntry?>(null) }
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("APRS 信标配置", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "写入机器后还须关联到信道，并在机器的 APRS 菜单里启用信标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.aprs.forEach { a ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().clickable { editingAprs = a },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(a.name.ifBlank { "(无名)" }, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "呼号后缀 -${a.ssid}　${"%.4f".format(a.txHz / 1e6)} MHz　" +
                                        "经由 ${a.via1}-${a.via1Ssid} / ${a.via2}-${a.via2Ssid}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "坐标 ${"%.4f".format(a.lat)}, ${"%.4f".format(a.lon)}　描述 ${a.comment.ifBlank { "（空）" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            editingAprs?.let { a ->
                AprsEditDialog(
                    entry = a,
                    onDismiss = { editingAprs = null },
                    onSave = { updated -> onAprsSaved(updated); editingAprs = null },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            var aRx by remember(state.vfoA) { mutableStateOf("%.4f".format(state.vfoA?.rxMhz ?: 0.0)) }
            var aTx by remember(state.vfoA) { mutableStateOf("%.4f".format(state.vfoA?.txMhz ?: 0.0)) }
            var bRx by remember(state.vfoB) { mutableStateOf("%.4f".format(state.vfoB?.rxMhz ?: 0.0)) }
            var bTx by remember(state.vfoB) { mutableStateOf("%.4f".format(state.vfoB?.txMhz ?: 0.0)) }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("VFO A / B", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(aRx, { aRx = it }, label = { Text("A 收") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(aTx, { aTx = it }, label = { Text("A 发") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(bRx, { bRx = it }, label = { Text("B 收") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(bTx, { bTx = it }, label = { Text("B 发") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedButton(onClick = {
                    fun hz(t: String) = ((t.toDoubleOrNull() ?: 0.0) * 1_000_000).toLong()
                    onVfoSaved(hz(aRx), hz(aTx), hz(bRx), hz(bTx))
                }) { Text("保存 VFO") }
            }
        }


        Spacer(Modifier.height(12.dp))

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val keys = remember(state.sideKeys) { state.sideKeys.map { it.toString() }.toMutableStateList() }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("侧键", style = MaterialTheme.typography.titleSmall)
                Text(
                    "前两项是紧急键的短按、长按时长，后六项是各键的功能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingNumber(
                    "紧急键短按时长", "毫秒", "50 到 750（每档 50）",
                    keys[0], 50, 1..15,
                ) { keys[0] = it }
                SettingNumber(
                    "紧急键长按时长", "毫秒", "1000 到 3750（每档 250）",
                    keys[1], 250, 4..15,
                ) { keys[1] = it }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SideActionPicker("侧键 1 · 短按", keys[2]) { keys[2] = it }
                SideActionPicker("侧键 1 · 长按", keys[3]) { keys[3] = it }
                SideActionPicker("侧键 2 · 短按", keys[4]) { keys[4] = it }
                SideActionPicker("侧键 2 · 长按", keys[5]) { keys[5] = it }
                SideActionPicker("顶部键 · 短按", keys[6]) { keys[6] = it }
                SideActionPicker("顶部键 · 长按", keys[7]) { keys[7] = it }
                OutlinedButton(onClick = {
                    onSideKeysSaved(IntArray(8) { i -> keys[i].toIntOrNull()?.coerceIn(0, 255) ?: 0 })
                }) { Text("保存侧键") }
            }
        }


        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                onSave(
                    callsign, line1, line2, textMode,
                    intArrayOf(
                        arsDly.toIntOrNull() ?: 0, preamble.toIntOrNull() ?: 0,
                        if (monitor) 1 else 0, vox.toIntOrNull() ?: 0,
                        lowBatt.toIntOrNull() ?: 0, callAlert.toIntOrNull() ?: 0,
                        respTmr.toIntOrNull() ?: 0, reminderTmr.toIntOrNull() ?: 0,
                        grpHang.toIntOrNull() ?: 0, privHang.toIntOrNull() ?: 0,
                    ),
                )
            }) { Text("保存") }
            OutlinedButton(onClick = onExport) { Text("导出 .g77") }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "保存只改软件内的数据，写入对讲机请到「读写」页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 一个按物理量显示的设置项。码盘里存的是原始字节，这里显示「字节 × factor + 单位」，
 * 系数和范围全部照 CPS 的 GeneralSetForm / ButtonForm，不是我估的。
 * 输入时立刻按范围夹紧并换算回字节。
 */
@Composable
private fun SettingNumber(
    label: String,
    unit: String,
    hint: String,
    raw: String,
    factor: Int,
    byteRange: IntRange,
    onRaw: (String) -> Unit,
) {
    val minValue = byteRange.first * factor
    val maxValue = byteRange.last * factor
    var draft by remember { mutableStateOf(((raw.toIntOrNull() ?: byteRange.first) * factor).toString()) }
    OutlinedTextField(
        value = draft,
        onValueChange = { t ->
            val digits = t.filter { it.isDigit() }.take(6)
            draft = digits
            val n = (digits.toIntOrNull() ?: 0).coerceIn(minValue, maxValue)
            val b = n / factor
            onRaw(b.coerceIn(byteRange.first, byteRange.last).toString())
        },
        label = { Text("$label（$unit）") },
        supportingText = { Text(hint, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 一条 APRS 配置的编辑面板。字段与电脑端 CPS 的「APRS 详情」一致，
 * 每项都标注单位与取值含义；标志位按 CPS 里 `APRS_One` 的位定义写回。
 */
@Composable
private fun AprsEditDialog(
    entry: AprsEntry,
    onDismiss: () -> Unit,
    onSave: (AprsEntry) -> Unit,
) {
    var name by remember { mutableStateOf(entry.name) }
    var ssid by remember { mutableStateOf(entry.ssid.toString()) }
    var lat by remember { mutableStateOf("%.4f".format(entry.lat)) }
    var lon by remember { mutableStateOf("%.4f".format(entry.lon)) }
    var usePosition by remember { mutableStateOf(entry.usePosition) }
    var positionMasking by remember { mutableStateOf(entry.positionMasking) }
    var via1 by remember { mutableStateOf(entry.via1) }
    var via1Ssid by remember { mutableStateOf(entry.via1Ssid.toString()) }
    var via2 by remember { mutableStateOf(entry.via2) }
    var via2Ssid by remember { mutableStateOf(entry.via2Ssid.toString()) }
    var iconTable by remember { mutableStateOf(entry.iconTable) }
    var iconIndex by remember { mutableStateOf(entry.iconIndex.toString()) }
    var comment by remember { mutableStateOf(entry.comment) }
    var freq by remember { mutableStateOf("%.4f".format(entry.txHz / 1e6)) }
    var baudRate by remember { mutableStateOf(entry.baudRate) }
    var transmitQsy by remember { mutableStateOf(entry.transmitQsy) }
    var beaconingSilent by remember { mutableStateOf(entry.beaconingSilent) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("APRS-${entry.index} 详情", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "以下参数保存在文件中，修改后需保存并写回机器才会生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ApField("名称", name, "最多 8 个字符，用于区分多套配置") { name = it.take(8) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            ApField("发射 SSID", ssid, "跟在呼号后的编号，0 至 15") {
                                ssid = it.filter { c -> c.isDigit() }.take(2)
                            }
                        }
                        Box(Modifier.weight(1.3f)) {
                            ApField("发射频率（MHz）", freq, "信标使用的频率") {
                                freq = it.filter { c -> c.isDigit() || c == '.' }.take(10)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("使用定位", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "开启后使用机器自身的 GPS 位置；关闭时使用下面手工填写的经纬度。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = usePosition, onCheckedChange = { usePosition = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            ApField("纬度", lat, "十进制度，南纬为负") {
                                lat = it.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(10)
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ApField("经度", lon, "十进制度，西经为负") {
                                lon = it.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(10)
                            }
                        }
                    }

                    ApPicker(
                        "位置掩蔽",
                        "对外公布的位置按此精度取整，用于保护精确位置",
                        APRS_MASKING_NAMES.getOrElse(positionMasking) { APRS_MASKING_NAMES[0] },
                        APRS_MASKING_NAMES,
                    ) { positionMasking = it }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            ApField("经由 1", via1, "中继路径第一跳，通常 WIDE1") { via1 = it.take(6) }
                        }
                        Box(Modifier.weight(0.6f)) {
                            ApField("SSID", via1Ssid, "0 至 15") {
                                via1Ssid = it.filter { c -> c.isDigit() }.take(2)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            ApField("经由 2", via2, "中继路径第二跳，通常 WIDE2") { via2 = it.take(6) }
                        }
                        Box(Modifier.weight(0.6f)) {
                            ApField("SSID", via2Ssid, "0 至 15") {
                                via2Ssid = it.filter { c -> c.isDigit() }.take(2)
                            }
                        }
                    }

                    ApPicker(
                        "图标表",
                        "APRS 图标分为主表和副表两套",
                        if (iconTable == 0) "主表（/）" else "副表（\\）",
                        listOf("主表（/）", "副表（\\）"),
                    ) { iconTable = it }

                    ApField(
                        "图标序号",
                        iconIndex,
                        "0 至 93，对应字符 “${(33 + (iconIndex.toIntOrNull() ?: 0)).toChar()}”",
                    ) { iconIndex = it.filter { c -> c.isDigit() }.take(2) }

                    ApField("描述", comment, "随信标一起发出，最多 24 个字符") { comment = it.take(24) }

                    ApPicker(
                        "波特率",
                        "VHF/UHF 通常使用 1200",
                        if (baudRate == 0) "1200 baud（VHF/UHF）" else "300 baud（HF）",
                        listOf("1200 baud（VHF/UHF）", "300 baud（HF）"),
                    ) { baudRate = it }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("发送 QSY 信息", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "在信标中加入 “频率 + 偏移” 提示，接收方可据此转到语音频率。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = transmitQsy, onCheckedChange = { transmitQsy = it })
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("静默信标", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "发射信标时不发出提示音。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = beaconingSilent, onCheckedChange = { beaconingSilent = it })
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = {
                        onSave(
                            entry.copy(
                                name = name,
                                ssid = ssid.toIntOrNull()?.coerceIn(0, 15) ?: entry.ssid,
                                lat = lat.toDoubleOrNull() ?: entry.lat,
                                lon = lon.toDoubleOrNull() ?: entry.lon,
                                usePosition = usePosition,
                                positionMasking = positionMasking,
                                via1 = via1,
                                via1Ssid = via1Ssid.toIntOrNull()?.coerceIn(0, 15) ?: entry.via1Ssid,
                                via2 = via2,
                                via2Ssid = via2Ssid.toIntOrNull()?.coerceIn(0, 15) ?: entry.via2Ssid,
                                iconTable = iconTable,
                                iconIndex = (iconIndex.toIntOrNull() ?: entry.iconIndex).coerceIn(0, 93),
                                comment = comment,
                                txHz = (((freq.toDoubleOrNull() ?: (entry.txHz / 1_000_000.0)) * 1_000_000).toLong()),
                                baudRate = baudRate,
                                transmitQsy = transmitQsy,
                                beaconingSilent = beaconingSilent,
                            )
                        )
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("保存")
                    }
                }
            }
        }
    }
}

/** APRS 位置掩蔽档位，顺序照 CPS 的下拉框。 */
private val APRS_MASKING_NAMES = listOf(
    "不掩蔽", "0.0005 度", "0.001 度", "0.005 度", "0.01 度", "0.05 度", "0.1 度", "0.5 度",
)

@Composable
private fun ApField(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(hint, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ApPicker(
    label: String,
    hint: String,
    current: String,
    options: List<String>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(current, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { i, s ->
                    DropdownMenuItem(
                        text = { Text(s) },
                        onClick = { onPick(i); open = false },
                    )
                }
            }
        }
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
        )
    }
}

/** 侧键动作选择：码盘里存的是 [SIDE_ACTION_NAMES] 的下标。 */@Composable
private fun SideActionPicker(label: String, raw: String, onChange: (String) -> Unit) {
    val idx = raw.toIntOrNull() ?: 0
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    SIDE_ACTION_NAMES.getOrElse(idx) { "未定义（$idx）" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SIDE_ACTION_NAMES.forEachIndexed { i, name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onChange(i.toString()); open = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelEditDialog(
    channel: Channel,
    contacts: List<Contact>,
    rxGroups: List<RxGroup>,
    aprs: List<AprsEntry>,
    onDismiss: () -> Unit,
    onSave: (ChannelEdit) -> Unit,
) {
    var name by remember { mutableStateOf(channel.name) }
    var rx by remember { mutableStateOf("%.4f".format(channel.rxMhz)) }
    var tx by remember { mutableStateOf("%.4f".format(channel.txMhz)) }
    var digital by remember { mutableStateOf(channel.digital) }
    var cc by remember { mutableStateOf(channel.colorCode.toString()) }
    var ts by remember { mutableStateOf(channel.timeslot) }
    var power by remember { mutableStateOf(channel.power) }
    var narrow by remember { mutableStateOf(channel.bandwidth == 1) }
    var rxOnly by remember { mutableStateOf(channel.rxOnly) }
    var contactIndex by remember { mutableStateOf(channel.contactIndex) }
    var groupIndex by remember { mutableStateOf(channel.rxGroupList) }
    var showContacts by remember { mutableStateOf(false) }
    var showGroups by remember { mutableStateOf(false) }
    var showAprs by remember { mutableStateOf(false) }
    var aprsConfig by remember { mutableStateOf(channel.aprsConfig) }
    var error by remember { mutableStateOf<String?>(null) }

    val contact = contacts.firstOrNull { it.index == contactIndex }
    val group = rxGroups.firstOrNull { it.index == groupIndex }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("第 ${channel.index + 1} 条") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名字") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rx, onValueChange = { rx = it },
                    label = { Text("接收频率 (MHz)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tx, onValueChange = { tx = it },
                    label = { Text("发射频率 (MHz)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("数字模式", modifier = Modifier.weight(1f))
                    Switch(checked = digital, onCheckedChange = { digital = it })
                }

                if (digital) {
                    OutlinedTextField(
                        value = cc, onValueChange = { cc = it },
                        label = { Text("色码 0-15") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = ts == 0, onClick = { ts = 0 }, label = { Text("TS1") })
                        FilterChip(selected = ts == 1, onClick = { ts = 1 }, label = { Text("TS2") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("低", "中", "高").forEachIndexed { i, label ->
                            FilterChip(selected = power == i, onClick = { power = i }, label = { Text(label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = narrow, onClick = { narrow = true }, label = { Text("12.5 kHz") })
                        FilterChip(selected = !narrow, onClick = { narrow = false }, label = { Text("25 kHz") })
                    }

                    Box {
                        OutlinedButton(onClick = { showContacts = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("呼叫对象：" + (contact?.let { "${it.name} · ${it.kindLabel}" } ?: "未设"))
                        }
                        DropdownMenu(expanded = showContacts, onDismissRequest = { showContacts = false }) {
                            DropdownMenuItem(
                                text = { Text("（不设）") },
                                onClick = { contactIndex = 0; showContacts = false },
                            )
                            contacts.take(150).forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.name}  ${c.number}  ${c.kindLabel}") },
                                    onClick = { contactIndex = c.index; showContacts = false },
                                )
                            }
                        }
                    }

                    Box {
                        OutlinedButton(onClick = { showGroups = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("接收组：" + (group?.let { "${it.name}（${it.memberCount}）" } ?: "未设"))
                        }
                        DropdownMenu(expanded = showGroups, onDismissRequest = { showGroups = false }) {
                            DropdownMenuItem(
                                text = { Text("（不设）") },
                                onClick = { groupIndex = 0; showGroups = false },
                            )
                            rxGroups.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text("${g.name}（${g.memberCount} 个成员）") },
                                    onClick = { groupIndex = g.index; showGroups = false },
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("只收不发", modifier = Modifier.weight(1f))
                    Switch(checked = rxOnly, onCheckedChange = { rxOnly = it })
                }

                Box {
                    OutlinedButton(onClick = { showAprs = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "APRS 配置：" + when (aprsConfig) {
                                0 -> "不挂"
                                else -> aprs.firstOrNull { it.index == aprsConfig }
                                    ?.let { "${it.name}（-${it.ssid}）" }
                                    ?: "APRS-$aprsConfig"
                            }
                        )
                    }
                    DropdownMenu(expanded = showAprs, onDismissRequest = { showAprs = false }) {
                        DropdownMenuItem(
                            text = { Text("（不挂，这条信道不发信标）") },
                            onClick = { aprsConfig = 0; showAprs = false },
                        )
                        aprs.forEach { a ->
                            DropdownMenuItem(
                                text = { Text("APRS-${a.index}　${a.name}（-${a.ssid}）") },
                                onClick = { aprsConfig = a.index; showAprs = false },
                            )
                        }
                    }
                }
                Text(
                    "仅在信道关联了 APRS 配置、且机器的 APRS 模式已启用时，才会发送信标。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rxHz = (rx.toDoubleOrNull()?.times(1_000_000))?.toLong()
                val txHz = (tx.toDoubleOrNull()?.times(1_000_000))?.toLong()
                val ccValue = cc.toIntOrNull()
                if (rxHz == null || txHz == null || rxHz <= 0 || txHz <= 0) {
                    error = "频率要是正数，比如 438.5000"
                } else if (digital && (ccValue == null || ccValue !in 0..15)) {
                    error = "色码是 0 到 15"
                } else {
                    onSave(
                        ChannelEdit(
                            index = channel.index,
                            name = name,
                            rxHz = rxHz,
                            txHz = txHz,
                            digital = digital,
                            colorCode = ccValue ?: 0,
                            timeslot = ts,
                            power = power,
                            bandwidth = if (narrow) 1 else 0,
                            rxOnly = rxOnly,
                            contactIndex = if (digital) contactIndex else 0,
                            rxGroupList = if (digital) groupIndex else 0,
                            aprsConfig = aprsConfig,
                        )
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}



/**
 * 侧键动作，顺序照 CPS 语言文件里的 `ButtonItem` 那串（码盘里存的就是这里的下标）。
 * 名字直接取自 `Language\CPS\Chinese.xml`，不换说法。
 */
private val SIDE_ACTION_NAMES = listOf(
    "无", "所有提示音开关", "紧急模式开启", "紧急模式关闭", "高/低功率选择", "监听",
    "噪声信道临时删除", "一键通1", "一键通2", "一键通3", "一键通4", "一键通5",
    "一键通6", "中继/脱网", "扫描开/关", "严格/正常静噪", "加密开/关", "声控发射开/关",
    "区域切换", "电池电量指示", "单独工作开/关", "永久监听", "退出电话",
    "信道递增", "信道递减", "信道信息",
)

/**
 * 32 个主题色的名字，顺序和含义照固件 `OpenMDUV390_10W_PLUS_Chinese R250326.bin`
 * 里 0x49514 起的那串英文原名逐条译过来（Text Default / Background / … / BeiDou spot）。
 * 固件里没写英文的那几项按 CPS 中文语言文件补，不自己编。
 */
private val THEME_COLOUR_NAMES = listOf(
    "默认文本", "背景", "装饰", "输入文本",
    "开机前景", "开机背景", "通知文本", "警告通知",
    "错误通知", "通知背景", "菜单名称", "菜单名称背景",
    "菜单项", "菜单高亮", "选项数值", "页眉文本",
    "页眉背景", "信号强度条", "信号强度条 S9+", "信道名称",
    "联系人", "联系人信息", "区域名称", "接收频率",
    "发射频率", "CSS/SQL 值", "发射计时", "极坐标",
    "卫星点位", "GPS 编号", "GPS 点位", "北斗点位",
)

@Composable
private fun ThemeSection(
    day: List<Int>,
    night: List<Int>,
    onPick: (Int, Int, Int) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    val colours = if (tab == 0) day else night
    val type = if (tab == 0) CustomData.TYPE_THEME_DAY else CustomData.TYPE_THEME_NIGHT
    var editing by remember(tab) { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) {
                FilledTonalButton(onClick = { tab = 0 }) { Text("日间（${day.size}）") }
                OutlinedButton(onClick = { tab = 1 }) { Text("夜间") }
            } else {
                OutlinedButton(onClick = { tab = 0 }) { Text("日间") }
                FilledTonalButton(onClick = { tab = 1 }) { Text("夜间（${night.size}）") }
            }
        }
        colours.forEachIndexed { idx, c ->
            val rgb = CustomData.rgb565ToRgb(c)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editing = idx }
                    .padding(vertical = 6.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF000000.toInt() or rgb)),
                    modifier = Modifier.size(30.dp).height(24.dp),
                ) {}
                Spacer(Modifier.size(12.dp))
                Text(
                    THEME_COLOUR_NAMES.getOrElse(idx) { "第 ${idx + 1} 色" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("%06X".format(rgb), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }

    editing?.let { idx ->
        val rgb0 = CustomData.rgb565ToRgb(colours[idx])
        var hex by remember(idx) { mutableStateOf("%06X".format(rgb0)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(THEME_COLOUR_NAMES.getOrElse(idx) { "第 ${idx + 1} 色" }) },
            text = {
                Column {
                    Text(
                        (if (tab == 0) "日间主题" else "夜间主题") + " · 第 ${idx + 1} 色",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { v -> hex = v.filter { it.isLetterOrDigit() }.take(6).uppercase() },
                        label = { Text("RRGGBB") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    hex.toIntOrNull(16)?.let { preview ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF000000.toInt() or preview),
                            ),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {}
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    hex.toIntOrNull(16)?.let { onPick(type, idx, it) }
                    editing = null
                }) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ThemeEditorPage(
    day: List<Int>,
    night: List<Int>,
    onPick: (Int, Int, Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onClose) { Text("返回") }
            Text("主题", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ThemeSection(day, night, onPick)
        }
    }
}

@Composable
private fun ToolScreen(
    state: AppState,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onVerify: () -> Unit,
    onFetchTle: () -> Unit,
    onComputePass: (Int, Double, Double, Double, Double) -> Unit,
    onWritePass: (Int) -> Unit,
    onPickFirmware: () -> Unit,
    onCsvExport: () -> Unit,
    onCsvImport: () -> Unit,
    onThemeColour: (Int, Int, Int) -> Unit,
    onWriteKeps: () -> Unit,
    onFlash: () -> Unit,
    onMelodySaved: (String) -> Unit,
    onBootImagePicked: (Uri) -> Unit,
    onPickBootImage: () -> Unit,
    onProbeDfu: () -> Unit,
    onReadCalibration: () -> Unit,
    onExportCalibration: (Uri) -> Unit,
    onWriteCalibration: () -> Unit,
    onMessageSaved: (Int, String) -> Unit,
    onDownloadDmrId: () -> Unit,
    onExportDmrId: (Uri) -> Unit,
    onWriteDmrId: () -> Unit,
    onPreviewScreen: () -> Unit,
    onSaveScreengrab: (Uri) -> Unit,
    onBackupMcuRom: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var themeOpen by remember { mutableStateOf(false) }
    var editingDmrId by remember { mutableStateOf<DmrIdDb.Entry?>(null) }
    val calExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) onExportCalibration(uri)
    }
    val dmrIdExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onExportDmrId(uri)
    }
    val screenExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) onSaveScreengrab(uri)
    }
    val mcuRomExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) onBackupMcuRom(uri)
    }
    editingDmrId?.let { e ->
        var idText by remember(e) { mutableStateOf(e.id.toString()) }
        var callsign by remember(e) { mutableStateOf(e.callsign) }
        var name by remember(e) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { editingDmrId = null },
            title = { Text("编辑 ${e.id}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = idText,
                        onValueChange = { idText = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("DMR ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = callsign,
                        onValueChange = { callsign = it.trim().take(10) },
                        label = { Text("呼号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        label = { Text("姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newId = idText.toLongOrNull() ?: e.id
                    state.dmrIdAll = state.dmrIdAll.map {
                        if (it.id == e.id) it.copy(id = newId, callsign = callsign, name = name) else it
                    }
                    editingDmrId = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingDmrId = null }) { Text("取消") } },
        )
    }
    if (themeOpen) {
        ThemeEditorPage(
            day = state.themeDay,
            night = state.themeNight,
            onPick = onThemeColour,
            onClose = { themeOpen = false },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "常用",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        // ---- 校验 ----
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("校验", style = MaterialTheme.typography.titleSmall)
                Text(
                    "把对讲机里的数据读出来，和手上这份逐字节比。改完东西、写完机器都该点一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onVerify, enabled = state.deviceName != null && !state.busy) {
                    Text("和机器比对")
                }
            }
        }

        // ---- 卫星 ----
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("卫星", style = MaterialTheme.typography.titleSmall)
                Text(
                    "现拉最新 TLE，只写这 ${Keps.DEFAULT_SATS.size} 颗；过境和跟星由机器自己算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onWriteKeps,
                    enabled = state.image != null && !state.busy,
                ) { Text("写入卫星星历") }
            }
        }

        // ---- 主题 ----
        if (state.themeDay.isNotEmpty() || state.themeNight.isNotEmpty()) {
            Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("主题", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "日间 ${state.themeDay.size} 色、夜间 ${state.themeNight.size} 色。" +
                            "开机旋律 ${state.melodyLen} 个音，卫星星历 ${state.kepsSize} 字节。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { themeOpen = true },
                        enabled = state.themeDay.isNotEmpty() || state.themeNight.isNotEmpty(),
                    ) { Text("进入主题编辑") }
                }
            }
        }

        // ---- 开机旋律 ----
        if (state.melodyText.isNotEmpty() || state.melodyLen > 0) {
            Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
                var draft by remember(state.melodyText) { mutableStateOf(state.melodyText) }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("开机旋律", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "成对的数字：音高,时长,音高,时长… 音高是相对 A 的半音数（0 = 98Hz，12 高八度），" +
                            "可以是负数；时长越大越长。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("旋律") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onMelodySaved(draft) }) { Text("保存") }
                        OutlinedButton(onClick = { draft = state.melodyText }) { Text("还原") }
                    }
                }
            }
        }

        // ---- 开机图片 ----
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("开机图片", style = MaterialTheme.typography.titleSmall)
                Text(
                    "128×64 单色。选一张图，按平均亮度切成黑白——单色屏没有灰阶，" +
                        "线条清楚的效果最好。文件里没有这一块的话会自动建一块。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.bootBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "开机图片",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(onClick = onPickBootImage, enabled = state.image != null) { Text("换一张") }
            }
        }



        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MCU ROM", style = MaterialTheme.typography.titleSmall)
                Text(
                    "把机器里的固件本体读出来存成 .bin，1 MB，读一次约一两分钟。" +
                        "刷固件前存一份，万一刷坏可以拿它回滚。这一项只读，不动机器上的数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { mcuRomExport.launch("mcu-rom.bin") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("备份固件")
                }
            }
        }


        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("对讲机屏幕", style = MaterialTheme.typography.titleSmall)
                Text(
                    "取机器当前的屏幕画面，160×128。只读，不改机器上的数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.screengrab?.let { bmp ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "对讲机屏幕",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPreviewScreen) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("预览")
                    }
                    OutlinedButton(
                        onClick = {
                            if (state.screengrab != null) screenExport.launch("radio-screen.png")
                        },
                        enabled = state.screengrab != null,
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("下载")
                    }
                }
            }
        }


        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("呼号库", style = MaterialTheme.typography.titleSmall)
                Text(
                    "从 RadioID 的全球库下载 DMR ID 与呼号的对照表，按地区前缀筛选，可直接写入电台，" +
                        "也可以编辑某一条再写。写错只影响呼号显示，不影响收发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = state.dmrIdPrefix,
                    onValueChange = { state.dmrIdPrefix = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("地区 ID 前缀") },
                    supportingText = {
                        Text("留空表示不限地区", style = MaterialTheme.typography.bodySmall)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onDownloadDmrId, enabled = !state.dmrIdBusy) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (state.dmrIdBusy) "下载中…" else "下载")
                    }
                    OutlinedButton(
                        onClick = { dmrIdExport.launch("dmrid.csv") },
                        enabled = state.dmrIdAll.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("导出 CSV")
                    }
                }

                state.dmrIdMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.dmrIdAll.isNotEmpty()) {
                    val q = state.dmrIdQuery.trim()
                    val filtered = DmrIdDb.filterPrefix(state.dmrIdAll, state.dmrIdPrefix).filter { e ->
                        q.isEmpty() || e.callsign.contains(q, true) || e.name.contains(q, true) ||
                            e.id.toString().contains(q)
                    }
                    TextField(
                        value = state.dmrIdQuery,
                        onValueChange = { state.dmrIdQuery = it },
                        placeholder = { Text("搜索呼号、姓名或 ID") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.dmrIdQuery.isNotEmpty()) {
                                IconButton(onClick = { state.dmrIdQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "清空")
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "筛选结果：${filtered.size} 条（全库 ${state.dmrIdAll.size} 条，点一条可编辑）",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (filtered.isEmpty()) {
                        Text(
                            "这个前缀下没有条目",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            items(filtered) { e ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { editingDmrId = e }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${e.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(78.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        e.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }

                var confirmDmrWrite by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { confirmDmrWrite = true },
                    enabled = state.dmrIdAll.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("写入电台")
                }
                if (confirmDmrWrite) {
                    AlertDialog(
                        onDismissRequest = { confirmDmrWrite = false },
                        title = { Text("把呼号库写入电台？") },
                        text = {
                            Text(
                                "会覆盖电台里现有的 DMR ID 库（当前筛选结果 " +
                                    DmrIdDb.filterPrefix(state.dmrIdAll, state.dmrIdPrefix).size +
                                    " 条）。写错只影响呼号显示，不影响收发。"
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { confirmDmrWrite = false; onWriteDmrId() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) { Text("确认写入") }
                        },
                        dismissButton = { TextButton(onClick = { confirmDmrWrite = false }) { Text("取消") } },
                    )
                }
            }
        }

        // ---- CSV ----
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CSV 导入导出", style = MaterialTheme.typography.titleSmall)
                Text(
                    "列名照电脑版 CPS 的 Channels.csv。导出以后可以拿 Excel 批量改，" +
                        "改完导入回来；导入按「Channel Number」对号，超出条数的按新信道加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCsvExport, enabled = state.image != null) { Text("导出 CSV") }
                    OutlinedButton(onClick = onCsvImport, enabled = state.image != null) { Text("导入 CSV") }
                }
            }
        }
        // ---- 备份与恢复 ----
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("备份与恢复", style = MaterialTheme.typography.titleSmall)
                Text(
                    "备份 = 把当前这份 128 KB 镜像存成 .g77；恢复 = 从文件读回来。" +
                        "写回机器之前先备份一份，出事能回到原样。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onBackup, enabled = state.image != null) { Text("备份到文件") }
                    OutlinedButton(onClick = onRestore) { Text("从文件恢复") }
                }
            }
        }


        var showMessages by remember { mutableStateOf(false) }
        var editingMsg by remember { mutableStateOf<Int?>(null) }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("快捷短信", style = MaterialTheme.typography.titleSmall)
                Text(
                    "机器上预置的 ${state.messages.size} 条，发消息时直接选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showMessages = true }) { Text("打开列表") }
            }
        }

        if (showMessages) {
            AlertDialog(
                onDismissRequest = { showMessages = false },
                title = { Text("快捷短信") },
                text = {
                    LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.messages.size) { i ->
                            val text = state.messages.getOrNull(i).orEmpty()
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clickable { editingMsg = i },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 10.dp),
                                    )
                                    Text(text.ifBlank { "（空）" }, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = { showMessages = false; editingMsg = -1 }) { Text("新建") }
                        TextButton(onClick = { showMessages = false }) { Text("关掉") }
                    }
                },
            )
        }

        editingMsg?.let { idx ->
            var draft by remember(idx) { mutableStateOf(if (idx < 0) "" else state.messages.getOrNull(idx).orEmpty()) }
            AlertDialog(
                onDismissRequest = { editingMsg = null },
                title = { Text(if (idx < 0) "新建短信" else "第 ${idx + 1} 条短信") },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(140) },
                        label = { Text("内容（最多 144 字节）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onMessageSaved(idx, draft)
                        editingMsg = null
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { editingMsg = null }) { Text("取消") } },
            )
        }


    }
}

@Composable
private fun Placeholder(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TransferScreen(
    state: AppState,
    onConnect: () -> Unit,
    onReadInfo: () -> Unit,
    onReadCodeplug: () -> Unit,
    onDisconnect: () -> Unit,
    onWriteBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Usb, contentDescription = null)
                    Spacer(Modifier.fillMaxWidth(0.04f))
                    Text(state.deviceName ?: "未连接", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                state.info?.let { info ->
                    Text("型号：${info.modelLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text("固件：${info.gitRevision}", style = MaterialTheme.typography.bodySmall)
                    Text("编译：${info.buildDateTime}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.deviceName == null) {
                        androidx.compose.material3.Button(
                            onClick = onConnect,
                            enabled = !state.busy,
                        ) { Text("连接") }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = onDisconnect,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text("断开") }
                    }
                    OutlinedButton(onClick = onReadCodeplug, enabled = !state.busy) {
                        Text("读取")
                    }
                    OutlinedButton(
                        onClick = onWriteBack,
                        enabled = !state.busy && state.image != null,
                    ) { Text("写入") }
                }
            }
        }

        state.progress?.let { p ->
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("日志", style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.log.asReversed()) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** 一个开关行：左边标题+说明，右边开关。提示音那组共用。 */
@Composable
private fun ToneSwitch(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
