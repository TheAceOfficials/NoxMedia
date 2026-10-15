package com.tgserver.streamer.server

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object StreamTrafficStats {
    private val sessionBytes = AtomicLong(0L)
    private val windowBytes = AtomicLong(0L)
    @Volatile
    private var lastSpeedCalcTime = System.currentTimeMillis()
    @Volatile
    private var currentSpeedBps = 0L
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("tg_server_traffic", Context.MODE_PRIVATE)
        }
    }

    fun recordBytes(bytes: Long) {
        if (bytes <= 0) return
        sessionBytes.addAndGet(bytes)
        windowBytes.addAndGet(bytes)

        // Increment lifetime traffic
        prefs?.let { p ->
            synchronized(this) {
                val current = p.getLong("lifetime_traffic_bytes", 0L)
                p.edit().putLong("lifetime_traffic_bytes", current + bytes).apply()
            }
        }
    }

    fun getSessionBytes(): Long = sessionBytes.get()

    fun getLifetimeBytes(): Long {
        return prefs?.getLong("lifetime_traffic_bytes", 0L) ?: 0L
    }

    fun resetSession() {
        sessionBytes.set(0L)
        windowBytes.set(0L)
        currentSpeedBps = 0L
        lastSpeedCalcTime = System.currentTimeMillis()
    }

    fun calculateLiveSpeed(): Long {
        val now = System.currentTimeMillis()
        val elapsed = now - lastSpeedCalcTime
        if (elapsed >= 950) {
            val bytesInWindow = windowBytes.getAndSet(0L)
            currentSpeedBps = if (elapsed > 0) (bytesInWindow * 1000L) / elapsed else 0L
            lastSpeedCalcTime = now
        }
        return currentSpeedBps
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec <= 0L -> "0 B/s"
            bytesPerSec < 1024L -> "$bytesPerSec B/s"
            bytesPerSec < 1024L * 1024L -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
            bytesPerSec < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB/s", bytesPerSec / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes <= 0L -> "0.0 MB"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
