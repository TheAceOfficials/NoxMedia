package com.tgserver.streamer.util

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global Performance & Hardware Optimization Manager.
 *
 * Automatically detects 32-bit CPU architectures, Low-RAM environments (<= 3.5GB RAM),
 * and provides dynamic scaling for Bitmap decoding (RGB_565 vs ARGB_8888),
 * sample sizes, lightweight crossfade transitions, and memory-safe image caching.
 */
object DevicePerformanceManager {
    private var isInitialized = false
    private val _isPerformanceMode = MutableStateFlow(false)
    val isPerformanceMode: StateFlow<Boolean> = _isPerformanceMode.asStateFlow()

    var is32BitDevice: Boolean = false
        private set
    var isLowRamHardware: Boolean = false
        private set
    var totalMemoryMb: Long = 4096L
        private set

    val isLowRamDevice: Boolean
        get() = is32BitDevice || isLowRamHardware || totalMemoryMb <= 3500L

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        if (am != null) {
            try {
                am.getMemoryInfo(memInfo)
            } catch (_: Exception) {}
        }

        is32BitDevice = !Process.is64Bit()
        isLowRamHardware = am?.isLowRamDevice == true
        totalMemoryMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1024L)

        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val userPref = prefs.getString("performance_profile", "auto") ?: "auto"

        _isPerformanceMode.value = resolvePerformanceMode(userPref)
    }

    fun setPerformanceProfile(context: Context, profile: String) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("performance_profile", profile).apply()
        _isPerformanceMode.value = resolvePerformanceMode(profile)
    }

    fun getPerformanceProfile(context: Context): String {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getString("performance_profile", "auto") ?: "auto"
    }

    private fun resolvePerformanceMode(profile: String): Boolean {
        return when (profile) {
            "enabled" -> true
            "disabled" -> false
            else -> is32BitDevice || isLowRamHardware || totalMemoryMb <= 3500L
        }
    }
}
