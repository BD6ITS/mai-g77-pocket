package com.mai.dmrcps.codeplug

import java.io.Reader
import java.io.Writer

/**
 * 频道的 CSV 导入导出。
 *
 * 表头照电脑版 CPS 的 `CSVEML.cs`（`Channels.csv`）取的名字，这样导出以后
 * 用 Excel 打开、甚至再导回电脑版都认得。导入时按表头名字找列，列的顺序无所谓。
 */
object ChannelCsv {

    private val HEADER = listOf(
        "Channel Number",
        "Channel Name",
        "Channel Type",
        "Rx Frequency",
        "Tx Frequency",
        "Bandwidth (kHz)",
        "Colour Code",
        "Timeslot",
        "Contact",
        "TG List",
        "Power",
        "Rx Only",
        "TOT",
        "Latitude",
        "Longitude",
    )

    fun write(image: CodeplugImage, channels: List<Channel>, out: Writer) {
        out.write(HEADER.joinToString(","))
        out.write("\r\n")
        for (ch in channels) {
            val row = listOf(
                (ch.index + 1).toString(),
                ch.name,
                if (ch.digital) "Digital" else "Analog",
                "%.4f".format(ch.rxMhz),
                "%.4f".format(ch.txMhz),
                if (ch.bandwidth == 1) "12.5" else "25",
                ch.colorCode.toString(),
                (ch.timeslot + 1).toString(),
                ch.contactIndex.toString(),
                ch.rxGroupList.toString(),
                ch.power.toString(),
                if (ch.rxOnly) "1" else "0",
                ch.tot.toString(),
                if (ch.lat == 0.0) "" else "%.4f".format(ch.lat),
                if (ch.lon == 0.0) "" else "%.4f".format(ch.lon),
            )
            out.write(row.joinToString(",") { escape(it) })
            out.write("\r\n")
        }
    }

    /** 一行导入的数据。缺的字段是 null，表示这一列没给，别动。 */
    data class Row(
        val number: Int?,
        val name: String?,
        val digital: Boolean?,
        val rxHz: Long?,
        val txHz: Long?,
        val bandwidth: Int?,
        val colorCode: Int?,
        val timeslot: Int?,
        val contact: Int?,
        val group: Int?,
        val power: Int?,
        val rxOnly: Boolean?,
        val tot: Int?,
    )

    fun read(input: Reader): List<Row> {
        val lines = input.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val head = split(lines.first()).mapIndexed { i, v -> v.trim() to i }.toMap()
        fun idx(vararg names: String): Int? = names.firstNotNullOfOrNull { head[it] }

        val iNum = idx("Channel Number")
        val iName = idx("Channel Name")
        val iType = idx("Channel Type")
        val iRx = idx("Rx Frequency")
        val iTx = idx("Tx Frequency")
        val iBw = idx("Bandwidth (kHz)", "Bandwidth")
        val iCc = idx("Colour Code", "Color Code")
        val iTs = idx("Timeslot")
        val iContact = idx("Contact")
        val iGroup = idx("TG List", "Rx Group List")
        val iPower = idx("Power")
        val iRxOnly = idx("Rx Only")
        val iTot = idx("TOT")

        val out = ArrayList<Row>()
        for (line in lines.drop(1)) {
            val cells = split(line)
            fun cell(i: Int?): String? = i?.let { cells.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            fun mhz(i: Int?): Long? = cell(i)?.toDoubleOrNull()?.let { (it * 1_000_000).toLong() }
            val type = cell(iType)?.lowercase()
            out.add(
                Row(
                    number = cell(iNum)?.toIntOrNull(),
                    name = cell(iName),
                    digital = when {
                        type == null -> null
                        type.startsWith("d") -> true
                        type.startsWith("a") -> false
                        else -> null
                    },
                    rxHz = mhz(iRx),
                    txHz = mhz(iTx),
                    bandwidth = cell(iBw)?.toDoubleOrNull()?.let { if (it < 20) 1 else 0 },
                    colorCode = cell(iCc)?.toIntOrNull(),
                    timeslot = cell(iTs)?.toIntOrNull()?.minus(1)?.coerceIn(0, 1),
                    contact = cell(iContact)?.toIntOrNull(),
                    group = cell(iGroup)?.toIntOrNull(),
                    power = cell(iPower)?.toIntOrNull(),
                    rxOnly = cell(iRxOnly)?.let { it == "1" || it.equals("true", true) },
                    tot = cell(iTot)?.toIntOrNull(),
                )
            )
        }
        return out
    }

    /** 把一行导入数据套到某条信道上，只改给了的字段。 */
    fun apply(image: CodeplugImage, index: Int, row: Row, current: Channel) {
        image.updateChannel(
            index = index,
            name = row.name ?: current.name,
            rxHz = row.rxHz ?: current.rxHz,
            txHz = row.txHz ?: current.txHz,
            digital = row.digital ?: current.digital,
            colorCode = row.colorCode ?: current.colorCode,
            timeslot = row.timeslot ?: current.timeslot,
            power = row.power ?: current.power,
            bandwidth = row.bandwidth ?: current.bandwidth,
            rxOnly = row.rxOnly ?: current.rxOnly,
            contactIndex = row.contact ?: current.contactIndex,
            rxGroupList = row.group ?: current.rxGroupList,
        )
        if (row.tot != null) image.setChannelTot(index, row.tot)
    }

    private fun escape(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v

    /** 最小 CSV 切分：认双引号包起来的字段，认 "" 转义。 */
    private fun split(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    out.add(sb.toString()); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
