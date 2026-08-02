package com.example.cnv.report.excel

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * XLSX writer with cell styles + optional bar charts (STEP 19-1 / 19-2).
 */
class XlsxWorkbookWriter {

    enum class CellStyle {
        NORMAL,
        HEADER,
        CRITICAL,
        HIGH,
        WARNING,
        GOOD,
    }

    data class Cell(
        val value: Any?,
        val style: CellStyle = CellStyle.NORMAL,
    )

    private val sheets = ArrayList<SheetData>()
    private val charts = ArrayList<ChartSpec>()

    fun addSheet(name: String, rows: List<List<Any?>>): XlsxWorkbookWriter {
        val styled = rows.map { row -> row.map { Cell(it) } }
        sheets += SheetData(sanitizeSheetName(name), styled)
        return this
    }

    fun addStyledSheet(name: String, rows: List<List<Cell>>): XlsxWorkbookWriter {
        sheets += SheetData(sanitizeSheetName(name), rows)
        return this
    }

    /**
     * Bar chart against a sheet's category column + value columns.
     * [sheetName] must already be added.
     */
    fun addBarChart(
        title: String,
        sheetName: String,
        categoryCol: Int,
        valueCols: List<Pair<String, Int>>,
        dataStartRow: Int,
        dataEndRow: Int,
    ): XlsxWorkbookWriter {
        charts += ChartSpec(
            title,
            sanitizeSheetName(sheetName),
            categoryCol,
            valueCols,
            dataStartRow,
            dataEndRow,
        )
        return this
    }

    fun write(output: OutputStream) {
        require(sheets.isNotEmpty()) { "Workbook needs at least one sheet" }
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeEntry(zip, "_rels/.rels", rootRelsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml())
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            writeEntry(zip, "xl/styles.xml", stylesXml())
            val shared = buildSharedStrings()
            writeEntry(zip, "xl/sharedStrings.xml", sharedStringsXml(shared.strings))
            sheets.forEachIndexed { index, sheet ->
                val sheetCharts = charts.mapIndexedNotNull { ci, c ->
                    if (c.sheetName == sheet.name) ci to c else null
                }
                writeEntry(
                    zip,
                    "xl/worksheets/sheet${index + 1}.xml",
                    sheetXml(sheet, shared.indexOf, sheetCharts.map { it.first }),
                )
                if (sheetCharts.isNotEmpty()) {
                    writeEntry(
                        zip,
                        "xl/worksheets/_rels/sheet${index + 1}.xml.rels",
                        sheetRelsXml(sheetCharts.map { it.first }),
                    )
                }
            }
            charts.forEachIndexed { i, chart ->
                writeEntry(zip, "xl/charts/chart${i + 1}.xml", chartXml(chart))
                writeEntry(zip, "xl/drawings/drawing${i + 1}.xml", drawingXml(i + 1))
                writeEntry(
                    zip,
                    "xl/drawings/_rels/drawing${i + 1}.xml.rels",
                    drawingRelsXml(i + 1),
                )
            }
        }
    }

    private data class SheetData(val name: String, val rows: List<List<Cell>>)
    private data class ChartSpec(
        val title: String,
        val sheetName: String,
        val categoryCol: Int,
        val valueCols: List<Pair<String, Int>>,
        val dataStartRow: Int,
        val dataEndRow: Int,
    )

    private data class SharedIndex(val strings: List<String>, val indexOf: Map<String, Int>)

    private fun buildSharedStrings(): SharedIndex {
        val ordered = LinkedHashMap<String, Int>()
        for (sheet in sheets) {
            for (row in sheet.rows) {
                for (cell in row) {
                    val v = cell.value
                    if (v == null || v is Number || v is Boolean) continue
                    val s = v.toString()
                    if (!ordered.containsKey(s)) ordered[s] = ordered.size
                }
            }
        }
        return SharedIndex(ordered.keys.toList(), ordered)
    }

    private fun styleIndex(style: CellStyle): Int = when (style) {
        CellStyle.NORMAL -> 0
        CellStyle.HEADER -> 1
        CellStyle.CRITICAL -> 2
        CellStyle.HIGH -> 3
        CellStyle.WARNING -> 4
        CellStyle.GOOD -> 5
    }

    private fun sheetXml(
        sheet: SheetData,
        sharedIndex: Map<String, Int>,
        chartIndices: List<Int>,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append(
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
                """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""",
        )
        append("<sheetData>")
        sheet.rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            append("""<row r="$rowNum">""")
            row.forEachIndexed { cIdx, cell ->
                val value = cell.value ?: return@forEachIndexed
                val ref = cellRef(cIdx, rowNum)
                val s = styleIndex(cell.style)
                when (value) {
                    is Number -> append("""<c r="$ref" s="$s"><v>$value</v></c>""")
                    is Boolean -> append(
                        """<c r="$ref" s="$s" t="b"><v>${if (value) 1 else 0}</v></c>""",
                    )
                    else -> {
                        val idx = sharedIndex[value.toString()] ?: return@forEachIndexed
                        append("""<c r="$ref" s="$s" t="s"><v>$idx</v></c>""")
                    }
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        // Conditional formatting rules for Severity text columns (best-effort).
        append(conditionalFormattingXml(sheet))
        chartIndices.forEachIndexed { i, _ ->
            append("""<drawing r:id="rId${i + 1}"/>""")
        }
        append("</worksheet>")
    }

    private fun conditionalFormattingXml(sheet: SheetData): String {
        // Apply to a reasonable range if sheet has severity-like headers.
        val header = sheet.rows.firstOrNull()?.map { it.value?.toString().orEmpty() }.orEmpty()
        val sevCol = header.indexOfFirst {
            it.contains("Severity", ignoreCase = true) || it == "Highest Severity"
        }
        if (sevCol < 0 || sheet.rows.size < 2) return ""
        val col = cellRef(sevCol, 1).replace(Regex("\\d+"), "")
        val end = sheet.rows.size
        val range = "$col" + "2:$col$end"
        return """
            <conditionalFormatting sqref="$range">
              <cfRule type="containsText" operator="containsText" text="CRITICAL" priority="1">
                <formula>NOT(ISERROR(SEARCH("CRITICAL",$col""" + """2)))</formula>
                <dxf><fill><patternFill patternType="solid"><fgColor rgb="FFFF5252"/></patternFill></fill></dxf>
              </cfRule>
              <cfRule type="containsText" operator="containsText" text="HIGH" priority="2">
                <formula>NOT(ISERROR(SEARCH("HIGH",$col""" + """2)))</formula>
                <dxf><fill><patternFill patternType="solid"><fgColor rgb="FFFF9800"/></patternFill></fill></dxf>
              </cfRule>
            </conditionalFormatting>
        """.trimIndent().replace("\n", "")
    }

    private fun sheetRelsXml(chartIndices: List<Int>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append(
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""",
        )
        chartIndices.forEachIndexed { i, chartIndex ->
            append(
                """<Relationship Id="rId${i + 1}" """ +
                    """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" """ +
                    """Target="../drawings/drawing${chartIndex + 1}.xml"/>""",
            )
        }
        append("</Relationships>")
    }

    private fun drawingXml(chartNum: Int): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" """ +
            """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<xdr:twoCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:colOff>0</xdr:colOff>""" +
            """<xdr:row>12</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>""" +
            """<xdr:to><xdr:col>8</xdr:col><xdr:colOff>0</xdr:colOff>""" +
            """<xdr:row>28</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>""" +
            """<xdr:graphicFrame><xdr:nvGraphicFramePr><xdr:cNvPr id="2" name="Chart $chartNum"/>""" +
            """<xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr><xdr:xfrm/>""" +
            """<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart">""" +
            """<c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rId1"/>""" +
            """</a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>"""

    private fun drawingRelsXml(chartNum: Int): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" """ +
            """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" """ +
            """Target="../charts/chart$chartNum.xml"/></Relationships>"""

    private fun chartXml(chart: ChartSpec): String {
        val sheet = quoteSheet(chart.sheetName)
        val catRef = "'$sheet'!$${colLetter(chart.categoryCol)}$${chart.dataStartRow}:" +
            "$${colLetter(chart.categoryCol)}$${chart.dataEndRow}"
        val seriesXml = chart.valueCols.mapIndexed { i, (name, col) ->
            val valRef = "'$sheet'!$${colLetter(col)}$${chart.dataStartRow}:" +
                "$${colLetter(col)}$${chart.dataEndRow}"
            """
            <c:ser>
              <c:idx val="$i"/><c:order val="$i"/>
              <c:tx><c:v>${xmlEscape(name)}</c:v></c:tx>
              <c:cat><c:strRef><c:f>$catRef</c:f></c:strRef></c:cat>
              <c:val><c:numRef><c:f>$valRef</c:f></c:numRef></c:val>
            </c:ser>
            """.trimIndent().replace("\n", "")
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" """ +
            """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""" +
            """<c:chart><c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>${xmlEscape(chart.title)}</a:t></a:r></a:p></c:rich></c:tx></c:title>""" +
            """<c:plotArea><c:layout/><c:barChart><c:barDir val="col"/><c:grouping val="clustered"/>""" +
            seriesXml +
            """<c:axId val="1"/><c:axId val="2"/></c:barChart>""" +
            """<c:catAx><c:axId val="1"/><c:scaling><c:orientation val="minMax"/></c:scaling>""" +
            """<c:axPos val="b"/><c:crossAx val="2"/></c:catAx>""" +
            """<c:valAx><c:axId val="2"/><c:scaling><c:orientation val="minMax"/></c:scaling>""" +
            """<c:axPos val="l"/><c:crossAx val="1"/></c:valAx></c:plotArea></c:chart></c:chartSpace>"""
    }

    private fun sharedStringsXml(strings: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append(
            """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
                """count="${strings.size}" uniqueCount="${strings.size}">""",
        )
        for (s in strings) append("<si><t>${xmlEscape(s)}</t></si>")
        append("</sst>")
    }

    private fun workbookXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append(
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
                """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""",
        )
        append("<sheets>")
        sheets.forEachIndexed { i, sheet ->
            append(
                """<sheet name="${xmlEscape(sheet.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""",
            )
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelsXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append(
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""",
        )
        sheets.forEachIndexed { i, _ ->
            append(
                """<Relationship Id="rId${i + 1}" """ +
                    """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" """ +
                    """Target="worksheets/sheet${i + 1}.xml"/>""",
            )
        }
        val stylesId = sheets.size + 1
        val sharedId = sheets.size + 2
        append(
            """<Relationship Id="rId$stylesId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""",
        )
        append(
            """<Relationship Id="rId$sharedId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""",
        )
        append("</Relationships>")
    }

    private fun contentTypesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append(
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""",
        )
        sheets.forEachIndexed { i, _ ->
            append(
                """<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""",
            )
        }
        charts.forEachIndexed { i, _ ->
            append(
                """<Override PartName="/xl/charts/chart${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>""",
            )
            append(
                """<Override PartName="/xl/drawings/drawing${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""",
            )
        }
        append(
            """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""",
        )
        append(
            """<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""",
        )
        append("</Types>")
    }

    private fun rootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun stylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="6">""" +
            """<fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FFFF5252"/></patternFill></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FFFF9800"/></patternFill></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FFFFEE58"/></patternFill></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FF81C784"/></patternFill></fill>""" +
            """</fills><borders count="1"><border/></borders>""" +
            """<cellStyleXfs count="1"><xf/></cellStyleXfs>""" +
            """<cellXfs count="6">""" +
            """<xf xfId="0"/>""" +
            """<xf xfId="0" fontId="1" applyFont="1"/>""" +
            """<xf xfId="0" fillId="2" applyFill="1"/>""" +
            """<xf xfId="0" fillId="3" applyFill="1"/>""" +
            """<xf xfId="0" fillId="4" applyFill="1"/>""" +
            """<xf xfId="0" fillId="5" applyFill="1"/>""" +
            """</cellXfs></styleSheet>"""

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun cellRef(col: Int, row: Int): String = "${colLetter(col)}$row"

    private fun colLetter(col: Int): String {
        var c = col
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A'.code + (c % 26)).toChar())
            c = c / 26 - 1
        } while (c >= 0)
        return sb.toString()
    }

    private fun quoteSheet(name: String): String = name.replace("'", "''")

    private fun sanitizeSheetName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/?*\\[\\]]"), "_").take(31)
        return cleaned.ifBlank { "Sheet" }
    }

    private fun xmlEscape(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                in '\u0000'..'\u0008', '\u000B', '\u000C', in '\u000E'..'\u001F' -> Unit
                else -> append(ch)
            }
        }
    }
}
