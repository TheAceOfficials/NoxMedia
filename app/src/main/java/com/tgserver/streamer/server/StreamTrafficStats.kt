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
    @Volatile
    var lastActivityTime = System.currentTimeMillis()
        private set
    private var prefs: SharedPreferences? = null

    private val pendingLifetimeBytes = AtomicLong(0L)

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("tg_server_traffic", Context.MODE_PRIVATE)
        }
    }

    fun notifyActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    fun recordBytes(bytes: Long) {
        if (bytes <= 0) return
        sessionBytes.addAndGet(bytes)
        windowBytes.addAndGet(bytes)
        pendingLifetimeBytes.addAndGet(bytes)
        lastActivityTime = System.currentTimeMillis()
    }

    fun getSessionBytes(): Long = sessionBytes.get()

    fun getLifetimeBytes(): Long {
        val base = prefs?.getLong("lifetime_traffic_bytes", 0L) ?: 0L
        return base + pendingLifetimeBytes.get()
    }

    fun flushLifetimeBytes() {
        val toPersist = pendingLifetimeBytes.getAndSet(0L)
        if (toPersist > 0L) {
            prefs?.let { p ->
                val current = p.getLong("lifetime_traffic_bytes", 0L)
                p.edit().putLong("lifetime_traffic_bytes", current + toPersist).apply()
            }
        }
    }

    fun resetSession() {
        flushLifetimeBytes()
        sessionBytes.set(0L)
        windowBytes.set(0L)
        currentSpeedBps = 0L
        lastSpeedCalcTime = System.currentTimeMillis()
        lastActivityTime = System.currentTimeMillis()
    }

    fun calculateLiveSpeed(): Long {
        val now = System.currentTimeMillis()
        val elapsed = now - lastSpeedCalcTime
        if (elapsed >= 950) {
            val bytesInWindow = windowBytes.getAndSet(0L)
            currentSpeedBps = if (elapsed > 0) (bytesInWindow * 1000L) / elapsed else 0L
            lastSpeedCalcTime = now

            // Batch-flush lifetime traffic to SharedPreferences once per second (1000x less disk I/O)
            flushLifetimeBytes()
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
