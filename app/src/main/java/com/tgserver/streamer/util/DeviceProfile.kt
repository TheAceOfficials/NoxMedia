package com.tgserver.streamer.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build

/**
 * Device profile & hardware detection utility.
 * Optimizes image decoding, heap allocation, and transition rendering
 * for 32-bit and low-RAM Android devices.
 */
object DeviceProfile {
    @Volatile
    private var cachedIsLowEnd: Boolean? = null

    @Volatile
    var userForcedOptimization: Boolean? = null

    val is32BitDevice: Boolean
        get() = Build.SUPPORTED_64_BIT_ABIS.isEmpty()

    fun getMaxHeapMb(): Int {
        return (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
    }

    fun getTotalMemoryMb(context: Context): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1024L)
        } catch (_: Throwable) {
            4096L
        }
    }

    fun isLowEnd(context: Context? = null): Boolean {
        userForcedOptimization?.let { return it }
        cachedIsLowEnd?.let { return it }

        val is32Bit = is32BitDevice
        val maxMemoryMb = getMaxHeapMb()
        val isLowRam = if (context != null) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                val totalMb = memInfo.totalMem / (1024L * 1024L)
                am?.isLowRamDevice == true || totalMb <= 3500L
            } catch (_: Throwable) {
                false
            }
        } else false

        // Low end if 32-bit architecture, or JVM heap <= 256MB, or low-RAM hardware
        val result = is32Bit || maxMemoryMb <= 256 || isLowRam
        cachedIsLowEnd = result
        return result
    }

    /**
     * Calculates safe, memory-efficient inSampleSize and Config for poster images.
     * Prevents OutOfMemoryError crashes on 32-bit/low-RAM devices.
     */
    fun getSafePosterDecodeOptions(
        bytes: ByteArray,
        isLowEnd: Boolean
    ): BitmapFactory.Options {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val targetWidth = if (isLowEnd) 320 else 500
        val targetHeight = if (isLowEnd) 480 else 750

        var inSample = 1
        val halfW = boundsOptions.outWidth / 2
        val halfH = boundsOptions.outHeight / 2
        while ((halfW / inSample) >= targetWidth && (halfH / inSample) >= targetHeight) {
            inSample *= 2
        }

        return BitmapFactory.Options().apply {
            inSampleSize = inSample.coerceAtLeast(1)
            // RGB_565 uses 2 bytes/pixel (50% memory savings), perfect for opaque posters
            inPreferredConfig = if (isLowEnd) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            inDither = true
        }
    }
}
