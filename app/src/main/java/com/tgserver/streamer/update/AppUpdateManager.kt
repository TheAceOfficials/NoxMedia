package com.tgserver.streamer.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GitHubReleaseAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("browser_download_url") val downloadUrl: String = ""
)

data class GitHubReleaseResponse(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("published_at") val publishedAt: String = "",
    @SerializedName("assets") val assets: List<GitHubReleaseAsset> = emptyList()
)

data class AppReleaseInfo(
    val versionTag: String,
    val releaseTitle: String,
    val changelog: String,
    val publishedAt: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val matchedAbi: String,
    val isNewer: Boolean
) {
    val formattedSize: String
        get() {
            if (fileSizeBytes <= 0L) return "~35 MB"
            val mb = fileSizeBytes.toDouble() / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.1f MB", mb)
        }
}

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class Available(val info: AppReleaseInfo) : UpdateUiState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val fileName: String
    ) : UpdateUiState()
    data class ReadyToInstall(val apkFile: File, val info: AppReleaseInfo) : UpdateUiState()
    data class UpToDate(val currentVersion: String) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

/**
 * Intelligent In-App Auto-Updater for NoxMedia.
 * Features:
 * - Checks GitHub Releases API (TheAceOfficials/NoxMedia)
 * - Auto-detects device CPU architecture (64-bit arm64-v8a vs 32-bit armeabi-v7a vs Universal)
 * - Downloads the exact matching APK with real-time byte & percentage progress
 * - Seamlessly launches Android Package Installer via FileProvider
 */
class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val GITHUB_OWNER = "TheAceOfficials"
        private const val GITHUB_REPO = "NoxMedia"
        private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val ALL_RELEASES_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _isDismissed = MutableStateFlow(false)
    val isDismissed: StateFlow<Boolean> = _isDismissed.asStateFlow()

    fun dismissBanner() {
        _isDismissed.value = true
    }

    fun getAppCurrentVersion(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    fun getDeviceArchitecture(): String {
        val abis = Build.SUPPORTED_ABIS
        if (!abis.isNullOrEmpty()) {
            for (abi in abis) {
                val lower = abi.lowercase()
                if (lower.contains("arm64") || lower.contains("aarch64")) return "arm64-v8a (64-bit)"
                if (lower.contains("v7a") || lower.contains("armeabi")) return "armeabi-v7a (32-bit)"
            }
            return "${abis[0]} (${if (abis[0].contains("64")) "64-bit" else "32-bit"})"
        }
        @Suppress("DEPRECATION")
        return Build.CPU_ABI ?: "universal"
    }

    /**
     * Checks for new release on GitHub.
     */
    suspend fun checkForUpdate(manual: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!manual && _updateState.value is UpdateUiState.Downloading) {
            return@withContext false
        }

        _updateState.value = UpdateUiState.Checking

        try {
            // 1. Fetch latest release
            var release = fetchRelease(RELEASES_API_URL)
            if (release == null) {
                // Fallback to all releases list to get the first one if "latest" isn't tagged as default
                release = fetchFirstReleaseFromList(ALL_RELEASES_URL)
            }

            if (release == null || release.assets.isEmpty()) {
                val curVer = getAppCurrentVersion()
                _updateState.value = if (manual) UpdateUiState.UpToDate(curVer) else UpdateUiState.Idle
                return@withContext false
            }

            // 2. Determine device preferred architecture and select best APK asset
            val (matchedAsset, abiLabel) = pickBestAsset(release.assets)
            if (matchedAsset == null) {
                val curVer = getAppCurrentVersion()
                _updateState.value = if (manual) UpdateUiState.UpToDate(curVer) else UpdateUiState.Idle
                return@withContext false
            }

            // 3. Compare versions
            val currentVersion = getAppCurrentVersion()
            val isNewer = isRemoteNewer(release, currentVersion)

            if (isNewer) {
                val releaseInfo = AppReleaseInfo(
                    versionTag = release.tagName.ifBlank { "Latest" },
                    releaseTitle = release.name.ifBlank { "NoxMedia Update" },
                    changelog = release.body.ifBlank { "Bug fixes and performance improvements." },
                    publishedAt = formatReleaseDate(release.publishedAt),
                    downloadUrl = matchedAsset.downloadUrl,
                    fileName = matchedAsset.name,
                    fileSizeBytes = matchedAsset.size,
                    matchedAbi = abiLabel,
                    isNewer = true
                )
                _isDismissed.value = false
                _updateState.value = UpdateUiState.Available(releaseInfo)
                return@withContext true
            } else {
                _updateState.value = if (manual) UpdateUiState.UpToDate(currentVersion) else UpdateUiState.Idle
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            _updateState.value = if (manual) UpdateUiState.Error(e.localizedMessage ?: "Failed to check for updates") else UpdateUiState.Idle
            return@withContext false
        }
    }

    private fun fetchRelease(url: String): GitHubReleaseResponse? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NoxMedia-Android-App")
            .header("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    gson.fromJson(body, GitHubReleaseResponse::class.java)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchFirstReleaseFromList(url: String): GitHubReleaseResponse? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NoxMedia-Android-App")
            .header("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val list = gson.fromJson(body, Array<GitHubReleaseResponse>::class.java)
                    list.firstOrNull()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Smart ABI matching:
     * 1. 64-bit devices -> NoxMedia-arm64-v8a.apk
     * 2. 32-bit devices -> NoxMedia-armeabi-v7a.apk
     * 3. Fallback -> NoxMedia-universal.apk
     */
    private fun pickBestAsset(assets: List<GitHubReleaseAsset>): Pair<GitHubReleaseAsset?, String> {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()

        for (abi in abis) {
            val lower = abi.lowercase()
            if (lower.contains("arm64") || lower.contains("aarch64")) {
                val match = assets.find { it.name.contains("arm64", ignoreCase = true) }
                if (match != null) return Pair(match, "arm64-v8a (64-bit)")
            } else if (lower.contains("v7a") || lower.contains("armeabi")) {
                val match = assets.find {
                    it.name.contains("armeabi-v7a", ignoreCase = true) ||
                    it.name.contains("armv7", ignoreCase = true) ||
                    it.name.contains("v7a", ignoreCase = true)
                }
                if (match != null) return Pair(match, "armeabi-v7a (32-bit)")
            } else if (lower.contains("x86_64")) {
                val match = assets.find { it.name.contains("x86_64", ignoreCase = true) }
                if (match != null) return Pair(match, "x86_64 (64-bit)")
            }
        }

        // Universal fallback
        val universal = assets.find { it.name.contains("universal", ignoreCase = true) }
        if (universal != null) return Pair(universal, "Universal Build")

        // First available apk
        val firstApk = assets.find { it.name.endsWith(".apk", ignoreCase = true) }
        return Pair(firstApk, "Android Package")
    }

    /**
     * Compares remote release version against current installed app.
     */
    private fun isRemoteNewer(release: GitHubReleaseResponse, currentVersion: String): Boolean {
        val rawTag = release.tagName.removePrefix("v").removePrefix("V").trim()
        val rawCurrent = currentVersion.removePrefix("v").removePrefix("V").trim()

        if (rawTag.equals(rawCurrent, ignoreCase = true)) return false

        // Check semantic numbers if both are versions like 1.1 vs 1.0
        val remoteParts = rawTag.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = rawCurrent.split(".").mapNotNull { it.toIntOrNull() }

        if (remoteParts.isNotEmpty() && currentParts.isNotEmpty()) {
            for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        // If tag is "latest" (from continuous master/main CI builds), compare release date vs install date
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val appUpdateTime = pInfo.lastUpdateTime
            val releaseTime = parseIsoToMillis(release.publishedAt)
            // If release was published more than 3 minutes after this APK was installed
            releaseTime > (appUpdateTime + 180_000L)
        } catch (e: Exception) {
            false
        }
    }

    private fun parseIsoToMillis(isoString: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(isoString)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatReleaseDate(isoString: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(isoString) ?: return ""
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Downloads APK file and triggers installation.
     */
    suspend fun downloadAndInstall(info: AppReleaseInfo, onReady: (File) -> Unit = {}) = withContext(Dispatchers.IO) {
        val targetDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(targetDir, info.fileName)

        _updateState.value = UpdateUiState.Downloading(
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = info.fileSizeBytes,
            fileName = info.fileName
        )

        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", "NoxMedia-Android-App")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _updateState.value = UpdateUiState.Error("Download failed with HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body ?: run {
                    _updateState.value = UpdateUiState.Error("Empty response body")
                    return@withContext
                }

                val totalBytes = if (body.contentLength() > 0) body.contentLength() else info.fileSizeBytes
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        var lastProgressEmit = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressEmit > 120L || downloadedBytes == totalBytes) {
                                lastProgressEmit = now
                                val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
                                _updateState.value = UpdateUiState.Downloading(
                                    progress = progress.coerceIn(0f, 1f),
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    fileName = info.fileName
                                )
                            }
                        }
                        output.flush()
                    }
                }

                _updateState.value = UpdateUiState.ReadyToInstall(targetFile, info)
                withContext(Dispatchers.Main) {
                    onReady(targetFile)
                    installApk(context, targetFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            _updateState.value = UpdateUiState.Error("Download error: ${e.localizedMessage}")
        }
    }

    /**
     * Prompts the system package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show()
            return
        }

        // On Android 8.0+ (Oreo, API 26+), verify UNKNOWN_APP_SOURCES permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Please allow NoxMedia to install updates", Toast.LENGTH_LONG).show()
                return
            }
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(context, "Install failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
