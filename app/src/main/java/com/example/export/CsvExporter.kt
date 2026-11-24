package com.example.export

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object CsvExporter {
    fun generateCsvFile(
        context: Context,
        fileNamePrefix: String,
        headers: List<String>,
        rows: List<List<String>>
    ): File {
        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel opens cleanly
        sb.append('\uFEFF')
        sb.append(headers.joinToString(",") { escapeCsv(it) }).append("\n")
        rows.forEach { row ->
            sb.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
        }
        val safeName = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_]"), "_").take(36)
        val file = File(context.cacheDir, "${safeName}_${System.currentTimeMillis()}.csv")
        FileOutputStream(file).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        return file
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
