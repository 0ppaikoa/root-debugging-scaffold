package app.aoki.example.debug

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Journal {

    private const val DIR_NAME = "journal"
    private const val FILE_NAME = "journal.txt"

    @Synchronized
    fun append(context: Context, line: String) {
        val file = journalFile(context)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        file.appendText(stamp + " " + line + "\n")
    }

    fun readAll(context: Context): String {
        val file = journalFile(context)
        return if (file.exists()) file.readText() else ""
    }

    private fun journalFile(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }
}
