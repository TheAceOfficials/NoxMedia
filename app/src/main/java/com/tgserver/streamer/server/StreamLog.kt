package com.tgserver.streamer.server

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated persistent logger for streaming events.
 * Writes formatted log lines to android.util.Log and File(context.filesDir, "logs/stream.log").
 * Guarantees streaming diagnostics are 100% visible on Android OEM builds (ColorOS/Oplus)
 * where system logcat buffers are filtered or truncated.
 */
object StreamLog {
    private const val TAG = "StreamLog"
    private const val MAX_LOG_FILE_BYTES = 1024 * 1024L // 1 MB
    private const val TRUNCATE_TARGET_BYTES = 512 * 1024L // 512 KB

    @Volatile
    private var logDir: File? = null
    private val lock = Any()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (logDir == null) {
            synchronized(lock) {
                if (logDir == null) {
                    val dir = File(context.applicationContext.filesDir, "logs")
                    if (!dir.exists()) dir.mkdirs()
                    logDir = dir
                    i(TAG, "StreamLog initialized at ${dir.absolutePath}/stream.log")
                }
            }
        }
    }

    fun getLogFilePath(): String {
        val dir = logDir ?: return ""
        val file = File(dir, "stream.log")
        return file.absolutePath
    }

    fun getLogFile(): File? {
        val dir = logDir ?: return null
        return File(dir, "stream.log")
    }

    fun i(tag: String, message: String) {
        log("I", tag, message)
    }

    fun d(tag: String, message: String) {
        log("D", tag, message)
    }

    fun w(tag: String, message: String) {
        log("W", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        log("E", tag, fullMsg)
    }

    private fun log(level: String, tag: String, message: String) {
        // 1. Android Logcat
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "D" -> Log.d(tag, message)
            else -> Log.i(tag, message)
        }

        // 2. Persistent file logging
        val dir = logDir ?: return
        val timeStr = synchronized(dateFormat) { dateFormat.format(Date()) }
        val formattedLine = "$timeStr $level/$tag: $message\n"

        synchronized(lock) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val logFile = File(dir, "stream.log")
                if (logFile.exists() && logFile.length() > MAX_LOG_FILE_BYTES) {
                    trimLogFile(logFile)
                }
                FileWriter(logFile, true).use { writer ->
                    writer.write(formattedLine)
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Error writing to stream.log: ${ex.message}")
            }
        }
    }

    private fun trimLogFile(file: File) {
        try {
            val content = file.readText()
            if (content.length > TRUNCATE_TARGET_BYTES) {
                val trimmed = content.substring((content.length - TRUNCATE_TARGET_BYTES).toInt())
                val cleanStart = trimmed.indexOf('\n')
                val finalContent = if (cleanStart != -1) trimmed.substring(cleanStart + 1) else trimmed
                file.writeText("[... older stream logs truncated ...]\n$finalContent")
            }
        } catch (_: Exception) {}
    }

    fun clear() {
        synchronized(lock) {
            try {
                logDir?.let { dir ->
                    val file = File(dir, "stream.log")
                    if (file.exists()) file.delete()
                }
            } catch (_: Exception) {}
        }
    }
}
