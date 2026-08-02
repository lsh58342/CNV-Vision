package com.example.cnv.report.excel

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal XLSX (OOXML) workbook writer (STEP 19-1).
 * Writes sheets with shared strings — no Apache POI dependency.
 */
class XlsxWorkbookWriter {

    private val sheets = ArrayList<SheetData>()

    fun addSheet(name: String, rows: List<List<Any?>>): XlsxWorkbookWriter {
        sheets += SheetData(sanitizeSheetName(name), rows)
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
                writeEntry(
                    zip,
                    "xl/worksheets/sheet${index + 1}.xml",
                    sheetXml(sheet, shared.indexOf),
                )
            }
        }
    }

    private data class SheetData(val name: String, val rows: List<List<Any?>>)

    private data class SharedIndex(
        val strings: List<String>,
        val indexOf: Map<String, Int>,
    )

    private fun buildSharedStrings(): SharedIndex {
        val ordered = LinkedHashMap<String, Int>()
        for (sheet in sheets) {
            for (row in sheet.rows) {
                for (cell in row) {
                    if (cell == null || cell is Number || cell is Boolean) continue
                    val s = cell.toString()
                    if (!ordered.containsKey(s)) {
                        ordered[s] = ordered.size
                    }
                }
            }
        }
        return SharedIndex(ordered.keys.toList(), ordered)
    }

    private fun sheetXml(sheet: SheetData, sharedIndex: Map<String, Int>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append(
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""",
        )
        append("<sheetData>")
        sheet.rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            append("""<row r="$rowNum">""")
            row.forEachIndexed { cIdx, value ->
                val ref = cellRef(cIdx, rowNum)
                when (value) {
                    null -> Unit
                    is Number -> append("""<c r="$ref"><v>${value}</v></c>""")
                    is Boolean -> append("""<c r="$ref" t="b"><v>${if (value) 1 else 0}</v></c>""")
                    else -> {
                        val idx = sharedIndex[value.toString()] ?: return@forEachIndexed
                        append("""<c r="$ref" t="s"><v>$idx</v></c>""")
                    }
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun sharedStringsXml(strings: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append(
            """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
                """count="${strings.size}" uniqueCount="${strings.size}">""",
        )
        for (s in strings) {
            append("<si><t>${xmlEscape(s)}</t></si>")
        }
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
            """<Relationship Id="rId$stylesId" """ +
                """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" """ +
                """Target="styles.xml"/>""",
        )
        append(
            """<Relationship Id="rId$sharedId" """ +
                """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" """ +
                """Target="sharedStrings.xml"/>""",
        )
        append("</Relationships>")
    }

    private fun contentTypesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append(
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""",
        )
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append(
            """<Override PartName="/xl/workbook.xml" """ +
                """ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""",
        )
        sheets.forEachIndexed { i, _ ->
            append(
                """<Override PartName="/xl/worksheets/sheet${i + 1}.xml" """ +
                    """ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""",
            )
        }
        append(
            """<Override PartName="/xl/styles.xml" """ +
                """ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""",
        )
        append(
            """<Override PartName="/xl/sharedStrings.xml" """ +
                """ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""",
        )
        append("</Types>")
    }

    private fun rootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" """ +
            """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" """ +
            """Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun stylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="1"><fill><patternFill patternType="none"/></fill></fills>""" +
            """<borders count="1"><border/></borders>""" +
            """<cellStyleXfs count="1"><xf/></cellStyleXfs>""" +
            """<cellXfs count="1"><xf xfId="0"/></cellXfs>""" +
            """</styleSheet>"""

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun cellRef(col: Int, row: Int): String {
        var c = col
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A'.code + (c % 26)).toChar())
            c = c / 26 - 1
        } while (c >= 0)
        return "$sb$row"
    }

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
