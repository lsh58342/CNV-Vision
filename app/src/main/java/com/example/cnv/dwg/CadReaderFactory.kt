package com.example.cnv.dwg

/**
 * Selects [DWGReader] by source path extension.
 * DXF → [DxfReader]; DWG / unknown → [StubDWGReader].
 */
object CadReaderFactory {

    fun create(sourcePath: String): DWGReader {
        val name = sourcePath
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBefore('?')
            .lowercase()
        return if (name.endsWith(".dxf")) {
            DxfReader()
        } else {
            StubDWGReader()
        }
    }
}
