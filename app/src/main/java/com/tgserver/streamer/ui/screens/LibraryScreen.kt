package com.tgserver.streamer.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tgserver.streamer.R
import com.tgserver.streamer.data.model.*
import com.tgserver.streamer.metadata.EnrichedMetadata
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

// Authentic Nothing Ndot Dot-Matrix Font
private val NdotFont = FontFamily(Font(R.font.ndot))

// Smart poster URL optimizer: seamlessly upgrades lower resolution URLs from TMDB/TVDB/Kitsu
// so existing and incoming library items load crystal clear HD posters without manual database re-scans.
fun optimizePosterUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    var optimized = url.trim()

    // 1. TMDB: Upgrade lower resolutions (w92, w154, w185, w342, w500) to w780.
    // w780 is the optimal HD sweet spot: crystal sharp on mobile/tablet/TV displays while loading almost instantly (~100-150KB WebP/JPEG).
    if (optimized.contains("image.tmdb.org/t/p/")) {
        optimized = optimized.replace(Regex("/t/p/(?:w92|w154|w185|w342|w500)/"), "/t/p/w780/")
    }

    // 2. TheTVDB: Upgrade thumbnail suffixes (_t.jpg or _cache) to full resolution artwork
    if (optimized.contains("artworks.thetvdb.com")) {
        if (optimized.endsWith("_t.jpg")) {
            optimized = optimized.replace(Regex("_t\\.jpg$"), ".jpg")
        } else if (optimized.contains("/_cache/")) {
            optimized = optimized.replace("/_cache/", "/")
        }
    }

    // 3. Kitsu Anime: Upgrade medium/small/tiny thumbnails to large for crisp anime art
    if (optimized.contains("kitsu.io")) {
        optimized = optimized
            .replace("/medium.", "/large.")
            .replace("/small.", "/large.")
            .replace("/tiny.", "/large.")
    }

    // 4. Cinemeta / Metahub: Upgrade small to medium
    if (optimized.contains("images.metahub.space/poster/small/")) {
        optimized = optimized.replace("/poster/small/", "/poster/medium/")
    }

    return optimized
}

// In-memory poster and backdrop cache with safe heap sizing (avoids OOM, caches up to 64MB of decoded bitmaps)
object ImageMemoryCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt() // KB
    private val cacheSizeKb = (maxMemory / 8).coerceIn(32 * 1024, 64 * 1024)

    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return try {
                (value.asAndroidBitmap().allocationByteCount / 1024).coerceAtLeast(1)
            } catch (_: Exception) {
                1024
            }
        }
    }

    fun get(url: String): ImageBitmap? = cache.get(url)
    fun put(url: String, bmp: ImageBitmap) {
        cache.put(url, bmp)
    }
    fun clear() {
        cache.evictAll()
    }
}

private var diskCachedHttpClient: OkHttpClient? = null

fun getImageHttpClient(context: Context): OkHttpClient {
    return diskCachedHttpClient ?: synchronized(ImageMemoryCache) {
        diskCachedHttpClient ?: run {
            val cacheDir = File(context.cacheDir, "nox_poster_disk_cache")
            val cache = okhttp3.Cache(cacheDir, 200L * 1024 * 1024) // 200MB disk cache for instant poster reuse
            val client = OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            diskCachedHttpClient = client
            client
        }
    }
}

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    isSeries: Boolean = false
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) { getImageHttpClient(context) }
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        val targetUrl = optimizePosterUrl(url) ?: url

        // Check memory cache: either optimized URL or original URL
        val cached = ImageMemoryCache.get(targetUrl) ?: ImageMemoryCache.get(url)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }

        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                var loadedBmp: ImageBitmap? = null
                // Attempt high-quality optimized URL first; fall back to original url if necessary
                val candidates = if (targetUrl != url) listOf(targetUrl, url) else listOf(targetUrl)
                for (candUrl in candidates) {
                    try {
                        val req = Request.Builder()
                            .url(candUrl)
                            .header("User-Agent", "NoxMedia/1.0 (Android)")
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val bytes = resp.body?.bytes()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    // Use ARGB_8888 for full 32-bit color fidelity (eliminates 16-bit color banding/blur)
                                    val options = BitmapFactory.Options().apply {
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                    }
                                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                    if (decoded != null) {
                                        loadedBmp = decoded.asImageBitmap()
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    if (loadedBmp != null) break
                }

                if (loadedBmp != null) {
                    val finalBmp = loadedBmp!!
                    ImageMemoryCache.put(targetUrl, finalBmp)
                    if (targetUrl != url) {
                        ImageMemoryCache.put(url, finalBmp)
                    }
                    withContext(Dispatchers.Main) {
                        bitmap = finalBmp
                    }
                }
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1B1C22), Color(0xFF101115))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFFA855F7),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isSeries) Icons.Default.Tv else Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color(0xFF4A4D5A),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// User-configurable library filters and sort criteria
data class ActiveFilters(
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENT,
    val resolutions: Set<String> = emptySet(),
    val editions: Set<String> = emptySet(),
    val platforms: Set<String> = emptySet(),
    val audioLanguages: Set<String> = emptySet()
) {
    val totalFilterCount: Int
        get() = resolutions.size + editions.size + platforms.size + audioLanguages.size + (if (sortOrder != LibrarySortOrder.RECENT) 1 else 0)

    val hasActiveFilters: Boolean
        get() = totalFilterCount > 0

    fun clear(): ActiveFilters = ActiveFilters(sortOrder = LibrarySortOrder.RECENT)
}

data class FilterSheetOptions(
    val resolutions: List<Pair<String, Int>> = emptyList(),
    val editions: List<Pair<String, Int>> = emptyList(),
    val platforms: List<Pair<String, Int>> = emptyList(),
    val audios: List<Pair<String, Int>> = emptyList()
)

private fun matchesGroupResolution(group: MediaGroup, res: String): Boolean {
    return when (res) {
        "4K" -> group.has4K
        "1080p (FHD)" -> group.has1080p
        "720p (HD)" -> group.has720p
        "SD" -> group.hasSD
        else -> false
    }
}

private fun matchesGroupEdition(group: MediaGroup, ed: String): Boolean {
    return when (ed) {
        "IMAX" -> group.hasIMAX
        "BluRay" -> group.hasBluRay
        "WEB-DL" -> group.hasWebDl
        "HDR / DV" -> group.hasHdrDv
        "10-Bit" -> group.has10Bit
        "Remux" -> group.hasRemux
        else -> false
    }
}

private fun matchesGroupPlatform(group: MediaGroup, plat: String): Boolean {
    return when (plat) {
        "Netflix" -> group.hasNetflix
        "Crunchyroll" -> group.hasCrunchyroll
        "Prime Video" -> group.hasPrimeVideo
        "Disney+ / Hotstar" -> group.hasDisney
        "Apple TV" -> group.hasAppleTv
        "Zee5" -> group.hasZee5
        else -> false
    }
}

private fun matchesGroupAudio(group: MediaGroup, lang: String): Boolean {
    return when (lang) {
        "Hindi" -> group.hasHindi
        "English" -> group.hasEnglish
        "Japanese" -> group.hasJapanese
        "Dual Audio" -> group.hasDualAudio
        "Multi Audio" -> group.hasMultiAudio
        else -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(viewModel: MainAppViewModel) {
    val allMedia by viewModel.media.collectAsState()
    val mediaGroups by viewModel.mediaGroups.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val navGroupKey by viewModel.selectedMediaGroupKey.collectAsState()
    val haptic = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()

    // Dynamic animated search placeholders from user library sample & popular titles (Zero memory waste)
    val searchPlaceholders = remember(mediaGroups) {
        val libraryTitles = mediaGroups.take(20).map { it.title.trim() }.filter { it.isNotBlank() }
        val defaultExamples = listOf(
            "Naruto",
            "One Piece",
            "Spider-Man: Brand New Day",
            "Loki",
            "Peacemaker",
            "Demon Slayer",
            "Breaking Bad",
            "Jujutsu Kaisen"
        )
        if (libraryTitles.size >= 3) {
            libraryTitles.shuffled()
        } else {
            (libraryTitles + defaultExamples).distinct()
        }
    }

    var placeholderIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(searchPlaceholders) {
        if (searchPlaceholders.isNotEmpty()) {
            while (true) {
                delay(3200)
                placeholderIndex = (placeholderIndex + 1) % searchPlaceholders.size
            }
        }
    }

    val currentPlaceholderTitle = searchPlaceholders.getOrElse(placeholderIndex) { "Naruto" }

    var selectedGroupKey by remember(navGroupKey) { mutableStateOf(navGroupKey) }
    val selectedGroup = mediaGroups.find { it.key == selectedGroupKey }

    var activeFilters by remember { mutableStateOf(ActiveFilters()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var quickActionGroup by remember { mutableStateOf<MediaGroup?>(null) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedGroupKeys = remember { mutableStateListOf<String>() }

    // Intercept hardware / gesture back navigation when inside details view, multi-select, or modal
    BackHandler(enabled = selectedGroup != null || isMultiSelectMode || quickActionGroup != null || showFilterSheet) {
        when {
            selectedGroup != null -> {
                selectedGroupKey = null
                viewModel.clearSelectedMediaGroup()
            }
            quickActionGroup != null -> {
                quickActionGroup = null
            }
            showFilterSheet -> {
                showFilterSheet = false
            }
            isMultiSelectMode -> {
                isMultiSelectMode = false
                selectedGroupKeys.clear()
            }
        }
    }

    // Dynamic Category Tabs: Single-pass iteration offloading CPU strain
    var mCount = 0
    var sCount = 0
    var aCount = 0
    var hCount = 0
    var unhiddenCount = 0
    for (group in mediaGroups) {
        if (group.isHidden) {
            hCount++
        } else {
            unhiddenCount++
            when (group.type) {
                "movie" -> mCount++
                "series" -> sCount++
                "anime" -> aCount++
            }
        }
    }
    val movieCount = mCount
    val seriesCount = sCount
    val animeCount = aCount
    val hiddenCount = hCount

    // Precomputed filter chip options and counts computed off-thread (0ms Main UI looper latency)
    var availableFilterOptions by remember { mutableStateOf(FilterSheetOptions()) }
    LaunchedEffect(mediaGroups) {
        availableFilterOptions = withContext(Dispatchers.Default) {
            val allResolutions = listOf("4K", "1080p (FHD)", "720p (HD)", "SD")
            val allEditions = listOf("IMAX", "BluRay", "WEB-DL", "HDR / DV", "10-Bit", "Remux")
            val allPlatforms = listOf("Netflix", "Crunchyroll", "Prime Video", "Disney+ / Hotstar", "Apple TV", "Zee5")
            val allAudios = listOf("Hindi", "English", "Japanese", "Dual Audio", "Multi Audio")

            val res = allResolutions.map { r -> r to mediaGroups.count { matchesGroupResolution(it, r) } }.filter { it.second > 0 }
            val ed = allEditions.map { e -> e to mediaGroups.count { matchesGroupEdition(it, e) } }.filter { it.second > 0 }
            val plat = allPlatforms.map { p -> p to mediaGroups.count { matchesGroupPlatform(it, p) } }.filter { it.second > 0 }
            val aud = allAudios.map { a -> a to mediaGroups.count { matchesGroupAudio(it, a) } }.filter { it.second > 0 }

            FilterSheetOptions(
                resolutions = res,
                editions = ed,
                platforms = plat,
                audios = aud
            )
        }
    }

    val categories = remember(unhiddenCount, movieCount, seriesCount, animeCount, hiddenCount) {
        buildList {
            add("all" to "ALL")
            if (movieCount > 0) add("movies" to "MOVIES")
            if (seriesCount > 0) add("series" to "SERIES")
            if (animeCount > 0) add("anime" to "ANIME")
            if (hiddenCount > 0) add("hide" to "HIDE")
        }
    }

    LaunchedEffect(categories) {
        if (categories.none { it.first == selectedCategory }) {
            viewModel.setSelectedCategory("all")
        }
    }

    // Filter media groups by category, search & multi-filters, then sort (Asynchronously on Dispatchers.Default)
    var filteredGroups by remember { mutableStateOf<List<MediaGroup>>(emptyList()) }
    var isFiltering by remember { mutableStateOf(false) }

    LaunchedEffect(mediaGroups, searchQuery, selectedCategory, activeFilters) {
        if (searchQuery.isNotBlank()) {
            delay(160L) // Debounce search keystrokes so typing is butter-smooth
        }
        isFiltering = true
        val result = withContext(Dispatchers.Default) {
            val filtered = mediaGroups.filter { group ->
                val matchesCategory = when (selectedCategory) {
                    "all" -> !group.isHidden
                    "movies" -> !group.isHidden && group.type == "movie"
                    "series" -> !group.isHidden && group.type == "series"
                    "anime" -> !group.isHidden && group.type == "anime"
                    "hide" -> group.isHidden
                    else -> !group.isHidden
                }
                if (!matchesCategory) return@filter false

                val matchesQuery = searchQuery.isBlank() ||
                        group.title.contains(searchQuery, ignoreCase = true) ||
                        (group.year?.contains(searchQuery, ignoreCase = true) == true) ||
                        (group.imdbId?.contains(searchQuery, ignoreCase = true) == true) ||
                        group.items.any { it.fileName.contains(searchQuery, ignoreCase = true) || it.episodeTitle?.contains(searchQuery, ignoreCase = true) == true }
                if (!matchesQuery) return@filter false

                // Resolutions: OR match within selected resolutions
                if (activeFilters.resolutions.isNotEmpty()) {
                    val match = activeFilters.resolutions.any { matchesGroupResolution(group, it) }
                    if (!match) return@filter false
                }

                // Editions & Formats: OR match within selected editions
                if (activeFilters.editions.isNotEmpty()) {
                    val match = activeFilters.editions.any { matchesGroupEdition(group, it) }
                    if (!match) return@filter false
                }

                // Platform / OTT: OR match within selected platforms
                if (activeFilters.platforms.isNotEmpty()) {
                    val match = activeFilters.platforms.any { matchesGroupPlatform(group, it) }
                    if (!match) return@filter false
                }

                // Audio & Languages: OR match within selected languages
                if (activeFilters.audioLanguages.isNotEmpty()) {
                    val match = activeFilters.audioLanguages.any { matchesGroupAudio(group, it) }
                    if (!match) return@filter false
                }

                true
            }

            when (activeFilters.sortOrder) {
                LibrarySortOrder.RECENT -> filtered.sortedByDescending { group ->
                    val date = group.dateAdded.takeIf { it > 0L }
                    date ?: (group.items.maxOfOrNull { it.messageId }?.toLong() ?: 0L)
                }
                LibrarySortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase().trim() }
                LibrarySortOrder.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase().trim() }
                LibrarySortOrder.YEAR_DESC -> filtered.sortedWith(
                    compareByDescending<MediaGroup> { it.year?.toIntOrNull() ?: 0 }
                        .thenBy { it.title.lowercase() }
                )
                LibrarySortOrder.YEAR_ASC -> filtered.sortedWith(
                    compareBy<MediaGroup> { it.year?.toIntOrNull() ?: 9999 }
                        .thenBy { it.title.lowercase() }
                )
                LibrarySortOrder.SIZE_DESC -> {
                    val sizeMap = filtered.associateWith { g -> g.items.sumOf { it.sizeBytes } }
                    filtered.sortedByDescending { sizeMap[it] ?: 0L }
                }
                LibrarySortOrder.SIZE_ASC -> {
                    val sizeMap = filtered.associateWith { g -> g.items.sumOf { it.sizeBytes } }
                    filtered.sortedBy { sizeMap[it] ?: 0L }
                }
                LibrarySortOrder.RATING_DESC -> filtered.sortedWith(
                    compareByDescending<MediaGroup> { it.rating }
                        .thenBy { it.title.lowercase() }
                )
            }
        }
        filteredGroups = result
        isFiltering = false
    }

    // Netflix & Apple TV style Windowing / Pagination:
    // Render initial 24 items immediately (<4ms render time), append 24 on scroll
    val pageSize = 24
    var visibleItemCount by remember { mutableIntStateOf(pageSize) }

    LaunchedEffect(searchQuery, selectedCategory, activeFilters) {
        visibleItemCount = pageSize
    }

    val displayedGroups = remember(filteredGroups, visibleItemCount) {
        filteredGroups.take(visibleItemCount)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = displayedGroups.size
            lastVisible >= total - 6 && total < filteredGroups.size
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleItemCount = (visibleItemCount + pageSize).coerceAtMost(filteredGroups.size)
        }
    }

    // Scroll to top only when user switches category or types a new search query
    LaunchedEffect(selectedCategory, searchQuery) {
        if (filteredGroups.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    val streamBaseUrl = "http://${telemetry.localIp}:${telemetry.port}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
    ) {
        AnimatedContent(
            targetState = selectedGroupKey,
            transitionSpec = {
                if (targetState != null) {
                    (fadeIn(tween(260)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(260))) togetherWith
                            (fadeOut(tween(200)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200)))
                } else {
                    (fadeIn(tween(260)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(260))) togetherWith
                            (fadeOut(tween(200)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)))
                }
            },
            label = "library_screen_transition"
        ) { currentGroupKey ->
            val currentGroup = mediaGroups.find { it.key == currentGroupKey }
            if (currentGroup != null) {
                // FULL SCREEN CONTENT DETAILS VIEW
                ContentDetailsScreen(
                    group = currentGroup,
                    allGroups = mediaGroups,
                    viewModel = viewModel,
                    streamBaseUrl = streamBaseUrl,
                    onBack = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedGroupKey = null
                        viewModel.clearSelectedMediaGroup()
                    },
                    onCopyStream = { url ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.copyToClipboard(url, "Stream link copied")
                    },
                    onRefreshMetadata = {
                        viewModel.refreshContentGroup(currentGroup.items)
                    }
                )
            } else {
                // MAIN POSTER GRID LIBRARY VIEW (Edge-to-Edge with Status Bar Clearance)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Library",
                                color = Color.White,
                                fontSize = 26.sp,
                                fontFamily = NdotFont,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "${mediaGroups.size} titles · ${allMedia.size} streams indexed",
                                color = Color(0xFF8E909A),
                                fontSize = 11.5.sp,
                                fontFamily = NdotFont,
                                letterSpacing = 0.5.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.syncFromCloud(silent = false)
                                viewModel.refreshAllLibraryMetadata()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF16171E))
                                .border(1.dp, Color(0xFF282A34), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Enrich Metadata",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // DYNAMIC ANIMATED SEARCH BAR (Sleek Capsule Pill with Rotating Library Titles)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF14151B))
                            .border(
                                BorderStroke(1.dp, if (searchQuery.isNotBlank()) Color(0xFFA855F7) else Color(0xFF25262F)),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (searchQuery.isNotBlank()) Color(0xFFA855F7) else Color(0xFF888B98),
                                modifier = Modifier.size(16.dp)
                            )
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    AnimatedContent(
                                        targetState = currentPlaceholderTitle,
                                        transitionSpec = {
                                            (slideInVertically(animationSpec = tween(360)) { height -> height / 2 } + fadeIn(tween(360))) togetherWith
                                                    (slideOutVertically(animationSpec = tween(280)) { height -> -height / 2 } + fadeOut(tween(280)))
                                        },
                                        label = "search_placeholder_anim"
                                    ) { title ->
                                        Text(
                                            text = "Search \"$title\"...",
                                            color = Color(0xFF6B6E7B),
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Default,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Default
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFA855F7)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotBlank()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF888B98),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.setSearchQuery("") }
                                )
                            }
                        }
                    }

                    // IOS GLASS SEGMENTED CONTROL TABS (Dynamic tabs with spring sliding thumb)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF13141B))
                            .border(
                                BorderStroke(1.dp, Color(0xFF242632)),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(4.dp)
                    ) {
                        val tabWidth = maxWidth / categories.size
                        val selectedIndex = categories.indexOfFirst { it.first == selectedCategory }.coerceAtLeast(0)
                        val indicatorOffset by animateDpAsState(
                            targetValue = tabWidth * selectedIndex,
                            animationSpec = spring(
                                dampingRatio = 0.82f,
                                stiffness = 380f
                            ),
                            label = "ios_tab_thumb_offset"
                        )

                        // Sliding Thumb Indicator (.cc-ios-tabs__thumb)
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFFA855F7))
                                    )
                                )
                                .border(
                                    BorderStroke(1.dp, Color(0x44FFFFFF)),
                                    shape = RoundedCornerShape(999.dp)
                                )
                        )

                        // Tab Item Labels (.cc-ios-tabs__item)
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            categories.forEach { (id, label) ->
                                val isSelected = selectedCategory == id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setSelectedCategory(id)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0xFF888B98),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = NdotFont,
                                        letterSpacing = 0.4.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // FILTERS & ACTIONS TOOLBAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredGroups.size} ${if (filteredGroups.size == 1) "title" else "titles"}",
                            color = Color(0xFF7E8088),
                            fontFamily = NdotFont,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Multi-Select Toggle Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isMultiSelectMode) Color(0xFF2E1A47) else Color(0xFF1B1D25))
                                    .border(
                                        1.dp,
                                        if (isMultiSelectMode) Color(0xFFA855F7) else Color(0xFF2C2F3A),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMultiSelectMode = !isMultiSelectMode
                                        if (!isMultiSelectMode) selectedGroupKeys.clear()
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = "Select multiple",
                                        tint = if (isMultiSelectMode) Color(0xFFC084FC) else Color(0xFFCBD5E1),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isMultiSelectMode) "Done" else "Select",
                                        color = if (isMultiSelectMode) Color(0xFFC084FC) else Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = NdotFont,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            // Filters & Sort Button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (activeFilters.hasActiveFilters) Color(0xFF2E1A47) else Color(0xFF1B1D25))
                                    .border(
                                        1.dp,
                                        if (activeFilters.hasActiveFilters) Color(0xFFA855F7) else Color(0xFF2C2F3A),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showFilterSheet = true
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Filters",
                                    tint = if (activeFilters.hasActiveFilters) Color(0xFFC084FC) else Color(0xFFA855F7),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Filters",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = NdotFont,
                                    letterSpacing = 0.5.sp
                                )
                                if (activeFilters.totalFilterCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0xFFA855F7))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${activeFilters.totalFilterCount}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = NdotFont
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFFA855F7),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    // POSTER GRID (Edge-to-Edge with Inset Padding)
                    if (filteredGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFiltering && mediaGroups.isNotEmpty()) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color(0xFFA855F7),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No Matching Media" else "Library is Empty",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontFamily = NdotFont,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "Try another keyword or filter." else "Media files will appear here once monitored channels are indexed.",
                                        color = Color(0xFF717482),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                state = gridState,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 100.dp)
                            ) {
                                items(displayedGroups, key = { it.key }) { group ->
                                    val isSelected = group.key in selectedGroupKeys
                                    PosterCard(
                                        group = group,
                                        isMultiSelectMode = isMultiSelectMode,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (isSelected) selectedGroupKeys.remove(group.key)
                                                else selectedGroupKeys.add(group.key)
                                            } else {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                selectedGroupKey = group.key
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!isMultiSelectMode) {
                                                quickActionGroup = group
                                            }
                                        }
                                    )
                                }

                                if (visibleItemCount < filteredGroups.size) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color(0xFFA855F7),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }
                            }

                            // Smooth Top Fader Effect (Soft Gradient Fade-out for Posters on Scroll)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                BackgroundOled,
                                                BackgroundOled.copy(alpha = 0.65f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }

        // MULTI-SELECT FLOATING ACTION BAR (Emil Kowalski style spring physics)
        AnimatedVisibility(
            visible = isMultiSelectMode,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 86.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF14151E),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.7f)),
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = {
                            isMultiSelectMode = false
                            selectedGroupKeys.clear()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "${selectedGroupKeys.size} selected",
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 12.sp
                    )

                    // Select All / Deselect All
                    Text(
                        text = if (selectedGroupKeys.size == filteredGroups.size && filteredGroups.isNotEmpty()) "Deselect" else "Select All",
                        color = Color(0xFFC084FC),
                        fontFamily = NdotFont,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (selectedGroupKeys.size == filteredGroups.size) {
                                    selectedGroupKeys.clear()
                                } else {
                                    selectedGroupKeys.clear()
                                    selectedGroupKeys.addAll(filteredGroups.map { it.key })
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    val isHideTab = selectedCategory == "hide"
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val groupsToUpdate = mediaGroups.filter { it.key in selectedGroupKeys }
                            viewModel.setMultipleGroupsHidden(groupsToUpdate, hide = !isHideTab)
                            isMultiSelectMode = false
                            selectedGroupKeys.clear()
                        },
                        enabled = selectedGroupKeys.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHideTab) Color(0xFF059669) else Color(0xFFDC2626),
                            disabledContainerColor = Color(0xFF262837)
                        ),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isHideTab) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isHideTab) "Unhide (${selectedGroupKeys.size})" else "Hide (${selectedGroupKeys.size})",
                            color = Color.White,
                            fontFamily = NdotFont,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        // DYNAMIC FILTER BOTTOM SHEET
        if (showFilterSheet) {
            FilterBottomSheet(
                options = availableFilterOptions,
                activeFilters = activeFilters,
                onApply = { updated ->
                    activeFilters = updated
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false }
            )
        }

        // QUICK ACTION MODAL (Emil Kowalski style spring pop-out on poster long-press)
        if (quickActionGroup != null) {
            QuickActionModal(
                group = quickActionGroup!!,
                onDismiss = { quickActionGroup = null },
                onToggleHide = {
                    val g = quickActionGroup!!
                    quickActionGroup = null
                    viewModel.setGroupHidden(g, hide = !g.isHidden)
                },
                onStartMultiSelect = {
                    val g = quickActionGroup!!
                    quickActionGroup = null
                    isMultiSelectMode = true
                    selectedGroupKeys.clear()
                    selectedGroupKeys.add(g.key)
                }
            )
        }
    }
}

/**
 * Minimalist Poster Grid Item with Multi-Select & Long-Press Support
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(
    group: MediaGroup,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Emil Kowalski Principle: Pressable media cards must have active scale feedback (0.955f)
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.955f else if (isSelected) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "poster_card_press"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Clean Poster art (Overlay quality pills removed as requested to avoid messy look)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) Color(0xFFA855F7) else Color(0xFF2A1C3C)
                    ),
                    RoundedCornerShape(14.dp)
                )
        ) {
            RemoteImage(
                url = group.posterUrl,
                contentDescription = group.title,
                isSeries = group.type == "series" || group.type == "anime",
                modifier = Modifier.fillMaxSize()
            )

            // Multi-Select Checkbox overlay
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFFA855F7) else Color(0x99000000))
                        .border(
                            BorderStroke(
                                1.5.dp,
                                if (isSelected) Color.White else Color(0x99FFFFFF)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Hidden status indicator pill
            if (group.isHidden) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFDC2626).copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(9.dp)
                        )
                        Text(
                            text = "HIDDEN",
                            color = Color.White,
                            fontFamily = NdotFont,
                            fontSize = 8.5.sp
                        )
                    }
                }
            }

            // Clean Episode Count badge for Series/Anime (Bottom Left)
            if (group.type == "series" || group.type == "anime") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF6B21A8).copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${group.distinctEpisodeCount} EPS",
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Title in NORMAL FONT (SemiBold, clear and legible)
        Text(
            text = group.title,
            color = Color.White,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Year · Type · Total Aggregated Size
        val yearText = group.year?.takeIf { it.isNotBlank() } ?: "2026"
        val subtitle = if (group.formattedTotalSize.isNotBlank()) {
            "$yearText · ${group.type.uppercase()} · ${group.formattedTotalSize}"
        } else {
            "$yearText · ${group.type.uppercase()}"
        }
        Text(
            text = subtitle,
            color = Color(0xFFA855F7),
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Long-Press Quick Action Pop-out Modal (Emil Kowalski micro-interaction)
 */
@Composable
private fun QuickActionModal(
    group: MediaGroup,
    onDismiss: () -> Unit,
    onToggleHide: () -> Unit,
    onStartMultiSelect: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.88f,
            animationSpec = spring(
                dampingRatio = 0.76f,
                stiffness = 380f
            ),
            label = "modal_scale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(180),
            label = "modal_alpha"
        )

        // Floating Title Hover Effect
        val infiniteTransition = rememberInfiniteTransition(label = "title_hover")
        val hoverOffsetY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hover_anim"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .padding(24.dp)
                    .widthIn(max = 330.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF14151E))
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFA855F7).copy(alpha = 0.6f),
                                    Color(0xFF2E1A47)
                                )
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Scaled Pop-out Poster
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            BorderStroke(1.5.dp, Color(0xFFA855F7).copy(alpha = 0.8f)),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    RemoteImage(
                        url = group.posterUrl,
                        contentDescription = group.title,
                        isSeries = group.type == "series" || group.type == "anime",
                        modifier = Modifier.fillMaxSize()
                    )

                    if (group.type == "series" || group.type == "anime") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF6B21A8).copy(alpha = 0.9f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${group.distinctEpisodeCount} EPS",
                                color = Color.White,
                                fontFamily = NdotFont,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                // Title with subtle floating hover effect
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.graphicsLayer { translationY = hoverOffsetY }
                ) {
                    Text(
                        text = group.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${group.year ?: "2026"} · ${group.type.uppercase()} · ${group.formattedTotalSize}",
                        color = Color(0xFF9CA3AF),
                        fontFamily = NdotFont,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                HorizontalDivider(color = Color(0xFF262837), thickness = 1.dp)

                // Actions: Hide/Unhide
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (group.isHidden) Color(0xFF064E3B) else Color(0xFF450A0A))
                        .border(
                            1.dp,
                            if (group.isHidden) Color(0xFF10B981) else Color(0xFFEF4444),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(onClick = onToggleHide)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (group.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (group.isHidden) Color(0xFF34D399) else Color(0xFFFCA5A5),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (group.isHidden) "Unhide Title" else "Hide & Blacklist",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = NdotFont
                        )
                        Text(
                            text = if (group.isHidden) "Restore to Library and Stremio addon" else "Remove from Library & Stremio streams",
                            color = if (group.isHidden) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                            fontSize = 10.sp
                        )
                    }
                }

                // Action: Select Multiple
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1F212D))
                        .border(1.dp, Color(0xFF323648), RoundedCornerShape(14.dp))
                        .clickable(onClick = onStartMultiSelect)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = null,
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Select Multiple",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = NdotFont
                        )
                        Text(
                            text = "Batch hide or unhide multiple titles",
                            color = Color(0xFF9CA3AF),
                            fontSize = 10.sp
                        )
                    }
                }

                // Close Button
                Text(
                    text = "Close",
                    color = Color(0xFF717482),
                    fontSize = 12.sp,
                    fontFamily = NdotFont,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Modern Filter & Sort Bottom Sheet with Dynamic Multi-Select Chips
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterBottomSheet(
    options: FilterSheetOptions,
    activeFilters: ActiveFilters,
    onApply: (ActiveFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var tempFilters by remember { mutableStateOf(activeFilters) }
    val haptic = LocalHapticFeedback.current

    val availableResolutions = options.resolutions
    val availableEditions = options.editions
    val availablePlatforms = options.platforms
    val availableAudios = options.audios

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12131A),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3F414D))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filters & Sorting",
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 16.sp,
                    letterSpacing = 0.8.sp
                )

                if (tempFilters.hasActiveFilters) {
                    Text(
                        text = "Reset All",
                        color = Color(0xFFC084FC),
                        fontFamily = NdotFont,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                tempFilters = ActiveFilters()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 1. Sort Section
            FilterSection(title = "SORT BY") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibrarySortOrder.values().forEach { order ->
                        val isSelected = tempFilters.sortOrder == order
                        FilterChipItem(
                            label = order.label,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                tempFilters = tempFilters.copy(sortOrder = order)
                            }
                        )
                    }
                }
            }

            // 2. Resolution Section
            if (availableResolutions.isNotEmpty()) {
                FilterSection(title = "RESOLUTION") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableResolutions.forEach { (res, count) ->
                            val isSelected = res in tempFilters.resolutions
                            FilterChipItem(
                                label = "$res ($count)",
                                isSelected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val updated = if (isSelected) tempFilters.resolutions - res else tempFilters.resolutions + res
                                    tempFilters = tempFilters.copy(resolutions = updated)
                                }
                            )
                        }
                    }
                }
            }

            // 3. Edition & Quality Section
            if (availableEditions.isNotEmpty()) {
                FilterSection(title = "EDITION & QUALITY") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableEditions.forEach { (ed, count) ->
                            val isSelected = ed in tempFilters.editions
                            FilterChipItem(
                                label = "$ed ($count)",
                                isSelected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val updated = if (isSelected) tempFilters.editions - ed else tempFilters.editions + ed
                                    tempFilters = tempFilters.copy(editions = updated)
                                }
                            )
                        }
                    }
                }
            }

            // 4. Platform & Source Section
            if (availablePlatforms.isNotEmpty()) {
                FilterSection(title = "PLATFORM & SOURCE") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availablePlatforms.forEach { (plat, count) ->
                            val isSelected = plat in tempFilters.platforms
                            FilterChipItem(
                                label = "$plat ($count)",
                                isSelected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val updated = if (isSelected) tempFilters.platforms - plat else tempFilters.platforms + plat
                                    tempFilters = tempFilters.copy(platforms = updated)
                                }
                            )
                        }
                    }
                }
            }

            // 5. Audio & Languages Section
            if (availableAudios.isNotEmpty()) {
                FilterSection(title = "AUDIO & LANGUAGES") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableAudios.forEach { (aud, count) ->
                            val isSelected = aud in tempFilters.audioLanguages
                            FilterChipItem(
                                label = "$aud ($count)",
                                isSelected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val updated = if (isSelected) tempFilters.audioLanguages - aud else tempFilters.audioLanguages + aud
                                    tempFilters = tempFilters.copy(audioLanguages = updated)
                                }
                            )
                        }
                    }
                }
            }

            // Apply Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApply(tempFilters)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFA855F7)
                )
            ) {
                Text(
                    text = "Apply Filters",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NdotFont,
                    fontSize = 13.sp,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = Color(0xFF717482),
            fontFamily = NdotFont,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp
        )
        content()
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (isSelected) Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFA855F7)))
                else Brush.horizontalGradient(listOf(Color(0xFF1B1D25), Color(0xFF1B1D25)))
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isSelected) Color(0x66FFFFFF) else Color(0xFF2C2F3A)
                ),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = label,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/**
 * Apple TV Style ClearLogo or Bold Typography with Automatic Fallback
 */
@Composable
private fun RemoteLogoOrTitle(
    logoUrl: String?,
    fallbackTitle: String,
    modifier: Modifier = Modifier
) {
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    var bitmap by remember(logoUrl) { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current.applicationContext

    LaunchedEffect(logoUrl) {
        if (logoUrl.isNullOrBlank()) {
            logoFailed = true
            return@LaunchedEffect
        }
        val cached = ImageMemoryCache.get(logoUrl)
        if (cached != null) {
            bitmap = cached
            logoLoaded = true
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val client = getImageHttpClient(context)
                val req = Request.Builder()
                    .url(logoUrl)
                    .header("User-Agent", "NoxMedia/1.0 (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { stream ->
                            val options = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            val decoded = BitmapFactory.decodeStream(stream, null, options)
                            if (decoded != null) {
                                val imgBmp = decoded.asImageBitmap()
                                ImageMemoryCache.put(logoUrl, imgBmp)
                                withContext(Dispatchers.Main) {
                                    bitmap = imgBmp
                                    logoLoaded = true
                                }
                            } else {
                                withContext(Dispatchers.Main) { logoFailed = true }
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) { logoFailed = true }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { logoFailed = true }
            }
        }
    }

    if (logoLoaded && bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = fallbackTitle,
            modifier = modifier
                .heightIn(max = 66.dp)
                .fillMaxWidth(0.72f),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
    } else {
        Text(
            text = fallbackTitle,
            color = Color.White,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth(0.9f)
        )
    }
}

/**
 * Full-Screen Content Details View (Apple TV+ Inspired Ultra-Premium Design)
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ContentDetailsScreen(
    group: MediaGroup,
    allGroups: List<MediaGroup> = emptyList(),
    viewModel: MainAppViewModel,
    streamBaseUrl: String,
    onBack: () -> Unit,
    onCopyStream: (String) -> Unit,
    onRefreshMetadata: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isSeries = group.type == "series" || group.type == "anime"
    var showRescanDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showPosterZoomDialog by remember { mutableStateOf(false) }
    var isOverviewExpanded by remember { mutableStateOf(false) }

    val visualSpecs = remember(group) {
        extractMediaVisualSpecs(group)
    }

    val detectedVersions = remember(group.items) {
        detectMediaVersions(group.items)
    }

    // Group episodes by Season for series
    val seasonMap = remember(group.items) {
        if (isSeries) {
            group.items.groupBy { it.season ?: 1 }.toSortedMap()
        } else {
            sortedMapOf()
        }
    }

    var selectedSeason by remember(group.key) {
        mutableStateOf(seasonMap.keys.firstOrNull() ?: 1)
    }

    // ClearLogo URL candidate from Metahub
    val effectiveLogoUrl = remember(group.imdbId) {
        group.imdbId?.takeIf { it.startsWith("tt") }?.let { id ->
            "https://images.metahub.space/logo/medium/$id/img"
        }
    }

    if (showRescanDialog) {
        RescanMetadataDialog(
            group = group,
            viewModel = viewModel,
            onDismiss = { showRescanDialog = false },
            onSynced = {
                showRescanDialog = false
            }
        )
    }

    if (showMergeDialog) {
        MergeTitlesDialog(
            currentGroup = group,
            allGroups = allGroups,
            onDismiss = { showMergeDialog = false },
            onMerge = { target ->
                showMergeDialog = false
                viewModel.mergeMediaGroups(sourceGroup = group, targetGroup = target)
            }
        )
    }

    // Full-Screen High-Res Poster Zoom Popup Window
    if (showPosterZoomDialog) {
        Dialog(
            onDismissRequest = { showPosterZoomDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { showPosterZoomDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .padding(horizontal = 22.dp, vertical = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Avoid closing when tapping directly on image area */ }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.title,
                            color = Color.White,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 10.dp)
                        )

                        IconButton(
                            onClick = { showPosterZoomDialog = false },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    RemoteImage(
                        url = group.posterUrl,
                        contentDescription = group.title,
                        isSeries = isSeries,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
    ) {
        // ==================== 1. PARALLAX HERO BACKDROP (3D Motion Depth) ====================
        val heroImageUrl = group.backdropUrl?.takeIf { it.isNotBlank() } ?: group.posterUrl
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(370.dp)
                .graphicsLayer {
                    // Smooth parallax: moves at ~0.45x scroll speed
                    translationY = -scrollState.value * 0.45f
                    // 3D scale and gentle depth
                    val scaleProgress = (scrollState.value / 750f).coerceIn(0f, 1f)
                    scaleX = 1f + (scaleProgress * 0.08f)
                    scaleY = 1f + (scaleProgress * 0.08f)
                    // OLED black fade
                    alpha = (1f - (scrollState.value / 520f)).coerceIn(0.06f, 1f)
                }
        ) {
            if (!heroImageUrl.isNullOrBlank()) {
                RemoteImage(
                    url = heroImageUrl,
                    contentDescription = group.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1E102F), Color(0xFF070709))
                            )
                        )
                )
            }

            // Top vignette for clear status bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Apple TV Signature Bottom Gradient (Seamless OLED Black fade)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF070709).copy(alpha = 0.25f),
                                Color(0xFF070709).copy(alpha = 0.85f),
                                Color(0xFF070709)
                            )
                        )
                    )
            )
        }

        // ==================== SCROLLABLE CONTENT COLUMN ====================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Spacer allowing the hero backdrop to be prominently visible
            Spacer(modifier = Modifier.height(175.dp))

            // Hero Center & Bottom: Logo / Title + Metadata Line (with foreground parallax)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -scrollState.value * 0.14f
                        alpha = (1f - (scrollState.value / 420f)).coerceIn(0f, 1f)
                    }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Text Logo / Graphic Artwork (Fallback: Bold Typography)
                RemoteLogoOrTitle(
                    logoUrl = effectiveLogoUrl,
                    fallbackTitle = group.title
                )

                // Metadata Row (Apple TV style: Year • Type • Rating • Quality • Audio)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = visualSpecs.year,
                        color = Color(0xFFD1D5DB),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(text = "•", color = Color(0xFF6B7280), fontSize = 11.sp)

                    Text(
                        text = group.type.uppercase(),
                        color = Color(0xFFA855F7),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isSeries) {
                        val seasonCount = seasonMap.size
                        val seasonTxt = if (seasonCount <= 1) "1 Season" else "$seasonCount Seasons"
                        Text(text = "•", color = Color(0xFF6B7280), fontSize = 11.sp)
                        Text(
                            text = seasonTxt,
                            color = Color(0xFFD1D5DB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(text = "•", color = Color(0xFF6B7280), fontSize = 11.sp)

                    // Gold Star Rating Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = "★", color = Color(0xFFFBBF24), fontSize = 10.sp)
                        Text(
                            text = visualSpecs.rating,
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Quality Badge (e.g. 4K / FHD)
                    NetflixAppleBadge(
                        text = visualSpecs.resolutionBadge,
                        textColor = Color.White,
                        borderColor = Color(0x55FFFFFF),
                        bgColor = Color(0x24FFFFFF)
                    )

                    // Top audio badge
                    if (visualSpecs.visualBadges.contains("5.1") || visualSpecs.visualBadges.contains("ATMOS")) {
                        NetflixAppleBadge(
                            text = if (visualSpecs.visualBadges.contains("ATMOS")) "ATMOS" else "5.1",
                            textColor = Color(0xFFE4E4E7),
                            borderColor = Color(0x35FFFFFF),
                            bgColor = Color(0x18FFFFFF)
                        )
                    }
                }

                // Audio Languages Row
                if (visualSpecs.audioLanguages.isNotEmpty()) {
                    Text(
                        text = "Available in: " + visualSpecs.audioLanguages.joinToString(", "),
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

        // ==================== 2. PRIMARY ACTION BUTTONS: FIX MATCH, MERGE, POSTER ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clear Labeled Primary Button: Fix Metadata Match
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showRescanDialog = true
                },
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Fix Match",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Fix Metadata Match",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }

            // Merge with Another Title in Library Button
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMergeDialog = true
                },
                shape = CircleShape,
                color = Color(0xFF191B24),
                border = BorderStroke(0.8.dp, Color(0x33FFFFFF)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Merge with another title",
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Right Action: View High-Res Poster
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showPosterZoomDialog = true
                },
                shape = CircleShape,
                color = Color(0xFF191B24),
                border = BorderStroke(0.8.dp, Color(0x33FFFFFF)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "View Poster",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // Library Storage Pill Indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0x14FFFFFF),
                border = BorderStroke(0.5.dp, Color(0x28FFFFFF))
            ) {
                Text(
                    text = "${visualSpecs.totalSize} in Library • ${group.items.size} files",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==================== 3. SYNOPSIS / OVERVIEW ====================
        if (!group.overview.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val overviewText = group.overview.trim()
                val isLong = overviewText.length > 180

                Text(
                    text = if (isLong && !isOverviewExpanded) overviewText.take(180) + "..." else overviewText,
                    color = Color(0xFFD4D4D8),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )

                if (isLong) {
                    Text(
                        text = if (isOverviewExpanded) "Less" else "More",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { isOverviewExpanded = !isOverviewExpanded }
                            .padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // ==================== 4. VERSIONS IN LIBRARY (Detected OTT) ====================
        if (detectedVersions.isNotEmpty()) {
            MediaVersionsSection(
                versions = detectedVersions,
                isSeries = isSeries
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ==================== 5. SEASONS & EPISODES / MOVIE STREAMS ====================
        if (!isSeries) {
            // MOVIE STREAMS LIST
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MOVIE STREAMS (${group.items.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = group.formattedTotalSize,
                        color = Color(0xFF8E909A),
                        fontSize = 11.5.sp
                    )
                }

                group.items.forEach { streamItem ->
                    MovieStreamItemRow(
                        item = streamItem,
                        backdropUrl = group.backdropUrl ?: group.posterUrl,
                        onToggleHide = {
                            viewModel.toggleHideMedia(streamItem.id)
                        },
                        onCopy = {
                            val encodedName = try {
                                URLEncoder.encode(streamItem.fileName.ifBlank { "movie.mp4" }, "UTF-8").replace("+", "%20")
                            } catch (_: Exception) {
                                "movie.mp4"
                            }
                            val streamUrl = "$streamBaseUrl/stream/video/${streamItem.channelId}/${streamItem.messageId}/$encodedName"
                            onCopyStream(streamUrl)
                        }
                    )
                }
            }
        } else {
            // SERIES SEASONS & 16:9 EPISODES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Multi-Season Selector Pills
                if (seasonMap.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(seasonMap.keys.toList()) { seasonNum ->
                            val isSelected = seasonNum == selectedSeason
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) Color.White else Color(0xFF14151C),
                                border = BorderStroke(0.5.dp, if (isSelected) Color.White else Color(0x33FFFFFF)),
                                modifier = Modifier.clickable { selectedSeason = seasonNum }
                            ) {
                                Text(
                                    text = "Season $seasonNum",
                                    color = if (isSelected) Color.Black else Color(0xFFD1D5DB),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                val currentSeasonItems = seasonMap[selectedSeason] ?: group.items
                val distinctEps = currentSeasonItems.map { it.episode ?: 1 }.distinct()
                val seasonTotalSize = currentSeasonItems.sumOf { it.sizeBytes }.let { bytes ->
                    if (bytes <= 0L) "0 MB"
                    else {
                        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                        if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
                        else String.format(java.util.Locale.US, "%.0f MB", bytes.toDouble() / (1024.0 * 1024.0))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Season $selectedSeason",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${distinctEps.size} Episodes • $seasonTotalSize",
                        color = Color(0xFF8E909A),
                        fontSize = 12.sp
                    )
                }

                // Group files by Episode number
                val episodesGrouped = remember(currentSeasonItems) {
                    currentSeasonItems.groupBy { it.episode ?: 1 }.toSortedMap()
                }

                episodesGrouped.forEach { (epNum, epItems) ->
                    EpisodeGroupAccordionRow(
                        epNum = epNum,
                        epItems = epItems,
                        showTitle = group.title,
                        groupImdbId = group.imdbId,
                        groupBackdropUrl = group.backdropUrl,
                        groupPosterUrl = group.posterUrl,
                        onToggleHide = { item ->
                            viewModel.toggleHideMedia(item.id)
                        },
                        onCopyStream = { streamUrl ->
                            onCopyStream(streamUrl)
                        },
                        streamBaseUrl = streamBaseUrl
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 6. ABOUT & SPECIFICATIONS (Redesigned) ====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA855F7))
                )
                Text(
                    text = "ABOUT & SPECIFICATIONS",
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF101117),
                border = BorderStroke(1.dp, Color(0xFF1F222E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Block 1: Identifiers / Databases (IMDb, TMDb, Kitsu)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "DATABASE IDENTIFIERS",
                            color = Color(0xFF717482),
                            fontFamily = NdotFont,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.5.sp
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // IMDb ID pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1B1914),
                                border = BorderStroke(1.dp, Color(0x66FBBF24))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text("IMDb", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    Text(
                                        text = group.imdbId ?: "N/A",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // TMDb ID pill
                            if (group.tmdbId != null && group.tmdbId > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF121924),
                                    border = BorderStroke(1.dp, Color(0x6660A5FA))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Text("TMDB", color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        Text(
                                            text = group.tmdbId.toString(),
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Kitsu Anime ID pill (if present on anime item!)
                            val kitsuId = group.items.firstOrNull { (it.kitsuId ?: 0) > 0 }?.kitsuId
                            if (kitsuId != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF221612),
                                    border = BorderStroke(1.dp, Color(0x66FF7A00))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Text("KITSU", color = Color(0xFFFF7A00), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        Text(
                                            text = "#$kitsuId",
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1C1E28), thickness = 0.5.dp)

                    // Block 2: 2-Column Responsive Specs Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("TOTAL STORAGE", color = Color(0xFF717482), fontFamily = NdotFont, fontSize = 9.5.sp)
                                Text(
                                    text = visualSpecs.totalSize,
                                    color = Color(0xFFC084FC),
                                    fontFamily = NdotFont,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("CONTENT TYPE", color = Color(0xFF717482), fontFamily = NdotFont, fontSize = 9.5.sp)
                                Text(
                                    text = group.type.uppercase(),
                                    color = Color(0xFFA855F7),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Right Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("INDEXED FILES", color = Color(0xFF717482), fontFamily = NdotFont, fontSize = 9.5.sp)
                                Text(
                                    text = "${group.items.size} media files",
                                    color = Color.White,
                                    fontFamily = NdotFont,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("STREAM QUALITY", color = Color(0xFF717482), fontFamily = NdotFont, fontSize = 9.5.sp)
                                Text(
                                    text = visualSpecs.resolutionBadge + if (visualSpecs.visualBadges.isNotEmpty()) " (${visualSpecs.visualBadges.joinToString(", ")})" else "",
                                    color = Color(0xFFD4D4D8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Block 3: Audio / Languages (Dedicated full-width row with clean tags)
                    if (visualSpecs.audioLanguages.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFF1C1E28), thickness = 0.5.dp)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "AVAILABLE AUDIO LANGUAGES",
                                color = Color(0xFF717482),
                                fontFamily = NdotFont,
                                fontSize = 9.5.sp
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                visualSpecs.audioLanguages.forEach { lang ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF181A24),
                                        border = BorderStroke(0.5.dp, Color(0x33FFFFFF))
                                    ) {
                                        Text(
                                            text = lang,
                                            color = Color(0xFFE4E4E7),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom space for floating bottom bar clearance
        Spacer(modifier = Modifier.height(110.dp))
    }

    // ==================== 7. FLOATING FROSTED GLASS ACTION BAR (Layer 3) ====================
    val navAlpha = (scrollState.value / 180f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .background(Color(0xFF070709).copy(alpha = navAlpha * 0.90f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = navAlpha * 0.12f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp, bottom = 10.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button with tactile response
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Title snippet smoothly fading in as user scrolls past hero banner
            if (navAlpha > 0.5f) {
                Text(
                    text = group.title,
                    color = Color.White.copy(alpha = ((navAlpha - 0.5f) / 0.5f).coerceIn(0f, 1f)),
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rescan Metadata Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showRescanDialog = true
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Rescan",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Refresh Metadata Button
                IconButton(
                    onClick = onRefreshMetadata,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
}

/**
 * Grouped Episode Row (Apple TV 16:9 Thumbnail Card + Accordion Details)
 * Displays: 16:9 Episode Thumbnail with overlay number, Title, Qualities, and expandable Telegram file details
 */
@Composable
private fun EpisodeGroupAccordionRow(
    epNum: Int,
    epItems: List<MediaItem>,
    showTitle: String,
    groupImdbId: String?,
    groupBackdropUrl: String?,
    groupPosterUrl: String?,
    streamBaseUrl: String,
    onToggleHide: (MediaItem) -> Unit,
    onCopyStream: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val primary = epItems.first()
    val candidateTitle = epItems.firstOrNull { !it.episodeTitle.isNullOrBlank() }?.episodeTitle?.trim()
    val epTitle = if (!candidateTitle.isNullOrBlank() && !isGarbageEpisodeTitle(candidateTitle, showTitle)) {
        candidateTitle
    } else {
        extractCleanEpisodeTitle(primary.fileName, epNum, showTitle)
    }

    val allHidden = epItems.all { it.isHidden }
    val anyHidden = epItems.any { it.isHidden }

    // Resolve 16:9 Episode Thumbnail URL
    val epThumbUrl: String? = remember(epNum, primary.season, groupImdbId, groupBackdropUrl, groupPosterUrl) {
        val directThumb = primary.episodeThumbnailUrl
        if (!directThumb.isNullOrBlank()) {
            directThumb
        } else if (!groupImdbId.isNullOrBlank() && groupImdbId.startsWith("tt")) {
            "https://episodes.metahub.space/$groupImdbId/${primary.season ?: 1}/$epNum/w780.jpg"
        } else if (!primary.backdropUrl.isNullOrBlank()) {
            primary.backdropUrl
        } else if (!groupBackdropUrl.isNullOrBlank()) {
            groupBackdropUrl
        } else {
            groupPosterUrl
        }
    }

    val totalEpBytes = remember(epItems) { epItems.sumOf { it.sizeBytes } }
    val formattedEpSize = remember(totalEpBytes) {
        if (totalEpBytes <= 0L) "0 MB"
        else {
            val gb = totalEpBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
            else String.format(java.util.Locale.US, "%.0f MB", totalEpBytes.toDouble() / (1024.0 * 1024.0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isExpanded) Color(0xFF14161F) else Color(0xFF0F1015))
            .border(
                BorderStroke(0.8.dp, if (isExpanded) Color(0x55A855F7) else Color(0xFF1E202B)),
                RoundedCornerShape(14.dp)
            )
    ) {
        // TOP 16:9 EPISODE CARD ROW (Clickable to expand/collapse)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 16:9 Episode Thumbnail Box
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF191B24))
                    .border(BorderStroke(0.5.dp, Color(0x28FFFFFF)), RoundedCornerShape(8.dp))
            ) {
                RemoteImage(
                    url = epThumbUrl,
                    contentDescription = epTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    isSeries = true
                )

                // Episode Number Pill Overlay (Top-Left)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .padding(5.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = "$epNum",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }

                // If hidden, show red overlay
                if (allHidden) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Hidden",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Episode Info & Badges
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = epTitle,
                    color = if (allHidden) Color(0xFF6B7280) else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Available Qualities in Apple TV badge style
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val distinctResolutions = remember(epItems) {
                        epItems.map { detectItemResolution(it) }.distinct()
                    }
                    distinctResolutions.forEach { res ->
                        NetflixAppleBadge(
                            text = res,
                            textColor = if (res == "4K") Color.White else Color(0xFFD1D5DB),
                            borderColor = if (res == "4K") Color(0x66FFFFFF) else Color(0x33FFFFFF),
                            bgColor = if (res == "4K") Color(0x28FFFFFF) else Color(0x14FFFFFF)
                        )
                    }

                    val hasDv = epItems.any { (it.qualityBadge + " " + it.fileName).contains("dv", ignoreCase = true) }
                    if (hasDv) {
                        NetflixAppleBadge(
                            text = "DV",
                            textColor = Color(0xFFFBBF24),
                            borderColor = Color(0x66FBBF24),
                            bgColor = Color(0x20FBBF24)
                        )
                    }

                    Text(
                        text = "• $formattedEpSize",
                        color = Color(0xFF8E909A),
                        fontSize = 11.sp
                    )
                }
            }

            // Expand Chevron
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
        }

        // EXPANDED ACCORDION DETAILS (Full Telegram filename, specs, size, Copy, Hide/Unhide)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = Color(0xFF222432), thickness = 0.8.dp)

                epItems.forEach { item ->
                    val isHidden = item.isHidden
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isHidden) Color(0xFF131014) else Color(0xFF1A1C25))
                            .border(
                                1.dp,
                                if (isHidden) Color(0xFF4A1924) else Color(0xFF2A2D3C),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            // 1. Full Telegram File Name
                            Text(
                                text = item.fileName.ifBlank { item.title },
                                color = if (isHidden) Color(0xFF94A3B8).copy(alpha = 0.6f) else Color(0xFFE2E8F0),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.5.sp
                            )

                            // 2. Details - Quality specs pill
                            val formattedBadge = formatShortBadge(item.qualityBadge.ifBlank { item.quality })
                            if (formattedBadge.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF2E1C0A))
                                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = formattedBadge,
                                        color = Color(0xFFFBBF24),
                                        fontSize = 9.5.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                            }

                            // 3. Size on Left, Copy & Hide/Unhide Buttons on Right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.formattedSize,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Copy Stream Link Icon
                                    val safeName = try {
                                        URLEncoder.encode(item.fileName.ifBlank { "video.mp4" }, "UTF-8").replace("+", "%20")
                                    } catch (_: Exception) { "video.mp4" }
                                    val streamUrl = "$streamBaseUrl/stream/video/${item.channelId}/${item.messageId}/$safeName"

                                    IconButton(
                                        onClick = { onCopyStream(streamUrl) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Stream",
                                            tint = Color(0xFF888B98),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }

                                    // HIDE / UNHIDE BUTTON
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(if (isHidden) Color(0xFF3F1318) else Color(0xFF232532))
                                            .border(
                                                1.dp,
                                                if (isHidden) Color(0xFFDC2626) else Color(0xFF3E4358),
                                                RoundedCornerShape(7.dp)
                                            )
                                            .clickable { onToggleHide(item) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (isHidden) "Unhide" else "Hide",
                                                tint = if (isHidden) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = if (isHidden) "UNHIDE" else "HIDE",
                                                color = if (isHidden) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Status Indicator
                            if (isHidden) {
                                Text(
                                    text = "Hidden: excluded from Stremio addon stream list",
                                    color = Color(0xFFEF4444),
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Default
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stream item row for Movies with Apple TV card styling, 16:9 thumbnail, Hide/Unhide toggle and full filename
 */
@Composable
private fun MovieStreamItemRow(
    item: MediaItem,
    backdropUrl: String?,
    onToggleHide: (MediaItem) -> Unit,
    onCopy: () -> Unit
) {
    val isHidden = item.isHidden
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isHidden) Color(0xFF131014) else Color(0xFF0F1015))
            .border(
                BorderStroke(0.8.dp, if (isHidden) Color(0xFF4A1924) else Color(0xFF1E202B)),
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 16:9 Movie File Thumbnail
                Box(
                    modifier = Modifier
                        .width(108.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF191B24))
                        .border(BorderStroke(0.5.dp, Color(0x28FFFFFF)), RoundedCornerShape(8.dp))
                ) {
                    RemoteImage(
                        url = backdropUrl,
                        contentDescription = item.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        isSeries = false
                    )

                    val res = detectItemResolution(item)
                    NetflixAppleBadge(
                        text = res,
                        textColor = Color.White,
                        borderColor = Color(0x66FFFFFF),
                        bgColor = Color(0x33000000),
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopStart)
                    )
                }

                // File Details & Name
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.fileName.ifBlank { item.title },
                        color = if (isHidden) Color(0xFF94A3B8).copy(alpha = 0.6f) else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    val formattedBadge = formatShortBadge(item.qualityBadge.ifBlank { item.quality })
                    if (formattedBadge.isNotBlank()) {
                        Text(
                            text = formattedBadge,
                            color = Color(0xFFFBBF24),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Size on Left, Copy & Hide/Unhide Buttons on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.formattedSize,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF888B98),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Hide / Unhide Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isHidden) Color(0xFF3F1318) else Color(0xFF232532))
                            .border(
                                1.dp,
                                if (isHidden) Color(0xFFDC2626) else Color(0xFF3E4358),
                                RoundedCornerShape(7.dp)
                            )
                            .clickable { onToggleHide(item) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isHidden) "Unhide" else "Hide",
                                tint = if (isHidden) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isHidden) "UNHIDE" else "HIDE",
                                color = if (isHidden) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (isHidden) {
                Text(
                    text = "Hidden: excluded from Stremio addon stream list",
                    color = Color(0xFFEF4444),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Default
                )
            }
        }
    }
}

/**
 * Interactive Rescan & Metadata Re-sync Modal Dialog
 * Allows searching Cinemeta / IMDb ID manually and re-linking correct title/posters
 */
@Composable
private fun RescanMetadataDialog(
    group: MediaGroup,
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit,
    onSynced: (EnrichedMetadata) -> Unit
) {
    var searchQuery by remember { mutableStateOf(group.title) }
    var searchType by remember {
        mutableStateOf(
            if (group.items.any { (it.kitsuId ?: 0) > 0 }) "anime" else group.type
        )
    } // "movie", "series", or "anime"
    var isLoading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<EnrichedMetadata>>(emptyList()) }
    var searchAttempted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Live Instant Search with 350ms debounce (no waiting 5-6s, snappy as user types)
    LaunchedEffect(searchQuery, searchType) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        delay(350)
        isLoading = true
        searchAttempted = true
        results = viewModel.searchMetadataCandidates(q, searchType)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF13141B),
        titleContentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fix Metadata Match",
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 17.sp,
                    letterSpacing = 0.5.sp
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF888B98),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Search by title, IMDb ID (e.g. tt14173636), TMDB ID (e.g. 109445), or Kitsu Anime ID to match metadata.",
                    color = Color(0xFF8E909A),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                // Search field + Search indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1C25))
                        .border(1.dp, Color(0xFF2E3242), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Default
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFA855F7)),
                            modifier = Modifier.weight(1f)
                        )
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color(0xFFA855F7),
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF717482),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                // Type Toggle: Switch between Movie, Series, and Anime!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Content Type:",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("movie" to "Movie", "series" to "Series", "anime" to "Anime").forEach { (typeKey, label) ->
                            val isSel = searchType == typeKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFFA855F7) else Color(0xFF1E202B))
                                    .border(
                                        1.dp,
                                        if (isSel) Color(0xFFC084FC) else Color(0xFF2C2F3C),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { searchType = typeKey }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Results list
                if (isLoading && results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFA855F7),
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else if (results.isEmpty() && searchAttempted && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matches found for \"$searchQuery\" in ${searchType.uppercase()}.\nTry entering an IMDb ID, TMDB ID, or Kitsu ID",
                            color = Color(0xFF717482),
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { candidate ->
                            CandidateCard(
                                candidate = candidate,
                                onSelect = {
                                    viewModel.applyManualMetadata(group.items, candidate, targetType = searchType)
                                    onSynced(candidate)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun CandidateCard(
    candidate: EnrichedMetadata,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181A22))
            .border(1.dp, Color(0xFF282A36), RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            RemoteImage(
                url = candidate.posterUrl,
                contentDescription = candidate.title,
                modifier = Modifier
                    .width(42.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = candidate.title ?: "Unknown",
                    color = Color.White,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!candidate.year.isNullOrBlank()) {
                        Text(
                            text = candidate.year,
                            color = Color(0xFFA855F7),
                            fontFamily = NdotFont,
                            fontSize = 10.sp
                        )
                    }
                    if (candidate.kitsuId != null && candidate.kitsuId > 0) {
                        Text(
                            text = "KITSU #${candidate.kitsuId}",
                            color = Color(0xFFFF7A00),
                            fontFamily = NdotFont,
                            fontSize = 10.sp
                        )
                    }
                    if (!candidate.imdbId.isNullOrBlank()) {
                        Text(
                            text = candidate.imdbId,
                            color = Color(0xFFFBBF24),
                            fontFamily = NdotFont,
                            fontSize = 10.sp
                        )
                    }
                }

                if (!candidate.overview.isNullOrBlank()) {
                    Text(
                        text = candidate.overview,
                        color = Color(0xFF888A96),
                        fontSize = 10.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2E1547))
                    .border(1.dp, Color(0xFFA855F7), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "SYNC",
                    color = Color(0xFFD8B4FE),
                    fontFamily = NdotFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Netflix / Apple TV+ style badge pill
 */
@Composable
private fun NetflixAppleBadge(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    borderColor: Color = Color(0x35FFFFFF),
    bgColor: Color = Color(0x18FFFFFF),
    isBold: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(BorderStroke(0.8.dp, borderColor), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = FontFamily.Default,
            letterSpacing = 0.4.sp,
            maxLines = 1
        )
    }
}

/**
 * Helper to accurately detect resolution of an item using canonical quality + word boundary regex
 */
private fun detectItemResolution(item: MediaItem): String {
    val q = item.quality.lowercase()
    if (q.contains("2160") || q.contains("4k") || q.contains("uhd")) return "4K"
    if (q.contains("1440") || q.contains("2k") || q.contains("qhd")) return "2K"
    if (q.contains("1080") || q.contains("fhd")) return "FHD"
    if (q.contains("720") || q.contains("hd")) return "HD"
    if (q.contains("480") || q.contains("sd")) return "SD"

    val qb = item.qualityBadge.lowercase()
    if (Regex("""\b(2160p?|4k|uhd)\b""").containsMatchIn(qb)) return "4K"
    if (Regex("""\b(1440p?|2k|qhd)\b""").containsMatchIn(qb)) return "2K"
    if (Regex("""\b(1080p?|fhd)\b""").containsMatchIn(qb)) return "FHD"
    if (Regex("""\b(720p?|hd)\b""").containsMatchIn(qb)) return "HD"

    val fn = item.fileName.lowercase()
    if (Regex("""\b(2160p?|4k|uhd)\b""").containsMatchIn(fn)) return "4K"
    if (Regex("""\b(1440p?|2k|qhd)\b""").containsMatchIn(fn)) return "2K"
    if (Regex("""\b(1080p?|fhd)\b""").containsMatchIn(fn)) return "FHD"
    if (Regex("""\b(720p?|hd)\b""").containsMatchIn(fn)) return "HD"

    return "FHD"
}

/**
 * Token-based audio track detector without false positives (avoids substring matching issues)
 */
private fun detectAudioTracks(textSources: List<String>): List<String> {
    val languages = mutableSetOf<String>()
    val combinedLower = textSources.joinToString(" ").lowercase()
    val tokens = combinedLower.split('.', '_', '-', ' ', '[', ']', '(', ')', '+', '/', ':', ',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

    // Explicit language tokens
    if (tokens.contains("hindi") || tokens.contains("hin")) languages.add("Hindi")
    if (tokens.contains("english") || tokens.contains("eng")) languages.add("English")
    if (tokens.contains("tamil") || tokens.contains("tam")) languages.add("Tamil")
    if (tokens.contains("telugu") || tokens.contains("tel")) languages.add("Telugu")
    if (tokens.contains("malayalam") || tokens.contains("mal")) languages.add("Malayalam")
    if (tokens.contains("kannada") || tokens.contains("kan")) languages.add("Kannada")
    if (tokens.contains("bengali") || tokens.contains("ben")) languages.add("Bengali")
    if (tokens.contains("marathi") || tokens.contains("mar")) languages.add("Marathi")
    if (tokens.contains("japanese") || tokens.contains("jap") || tokens.contains("jpn")) languages.add("Japanese")
    if (tokens.contains("korean") || tokens.contains("kor")) languages.add("Korean")
    if (tokens.contains("spanish") || tokens.contains("spa") || tokens.contains("espanol")) languages.add("Spanish")
    if (tokens.contains("french") || tokens.contains("fre") || tokens.contains("fra")) languages.add("French")
    if (tokens.contains("german") || tokens.contains("ger") || tokens.contains("deu")) languages.add("German")
    if (tokens.contains("italian") || tokens.contains("ita")) languages.add("Italian")
    if (tokens.contains("russian") || tokens.contains("rus")) languages.add("Russian")
    if (tokens.contains("chinese") || tokens.contains("chi") || tokens.contains("zho")) languages.add("Chinese")

    if (languages.isNotEmpty()) {
        return languages.toList()
    }

    // Check Multi / Dual
    if (tokens.contains("multi") || tokens.contains("multiaudio") || combinedLower.contains("multi audio") || combinedLower.contains("multi dub")) {
        return listOf("Multi Dub")
    }
    if (tokens.contains("dual") || tokens.contains("dualaudio") || combinedLower.contains("dual audio")) {
        return listOf("Dual Audio")
    }

    return listOf("Unknown")
}

private val EPISODE_GARBAGE_TOKENS = setOf(
    "japanese", "japan", "jpn", "jap", "hindi", "hin", "english", "eng", "tamil", "tam",
    "telugu", "tel", "korean", "kor", "chinese", "chi", "french", "fre", "fra",
    "german", "ger", "deu", "spanish", "spa", "esp", "italian", "ita", "russian", "rus",
    "malayalam", "mal", "kannada", "kan", "bengali", "ben", "marathi", "mar", "punjabi", "pun",
    "gujarati", "urdu", "thai", "vietnamese", "indonesian", "arabic", "ara", "portuguese", "por",
    "multi", "multiaudio", "dual", "dualaudio", "dub", "dubbed", "sub", "subbed", "subs",
    "esub", "esubs", "softsub", "hardsub", "raw", "vostfr", "clean", "audio",
    "1080p", "720p", "2160p", "4k", "uhd", "fhd", "hd", "sd", "480p", "360p",
    "hevc", "x264", "x265", "h264", "h265", "avc", "av1", "10bit", "8bit",
    "hdr", "hdr10", "hdr10+", "dv", "dovi", "dolby", "vision", "atmos",
    "aac", "aac2", "aac20", "ac3", "eac3", "ddp", "ddp51", "dts", "flac", "opus",
    "mp4", "mkv", "avi", "remux", "proper", "repack", "unrated", "extended",
    "web", "webdl", "web-dl", "webrip", "bluray", "blu-ray", "bdrip", "brrip", "dvdrip", "hdrip", "hdtv",
    "nf", "netflix", "amzn", "prime", "cr", "crunchyroll", "dsnp", "disney", "hotstar",
    "atvp", "apple", "max", "hbo", "zee5", "sonyliv", "jiocinema", "hulu", "peacock"
)

private fun isGarbageEpisodeTitle(candidate: String, showTitle: String? = null): Boolean {
    val trimmed = candidate.trim()
    if (trimmed.isBlank()) return true
    if (trimmed.length < 3) return true
    if (trimmed.all { it.isDigit() || it == '.' || it == '-' || it == '_' || it == ' ' }) return true

    // Check if it's identical or redundant with show title
    if (!showTitle.isNullOrBlank()) {
        val normShow = showTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
        val normCandidate = trimmed.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (normCandidate == normShow ||
            (normShow.length >= 4 && normCandidate.contains(normShow)) ||
            (normCandidate.length >= 4 && normShow.contains(normCandidate))
        ) {
            return true
        }
    }

    // Check if regex matches pure episode notation like "Episode 1", "Ep 01", "S01E01", "E01"
    if (trimmed.matches(Regex("""(?i)^(?:s\d{1,2})?\s*(?:ep?|episode)?\s*\.?\s*\d{1,4}$"""))) {
        return true
    }

    // Tokenize candidate by non-alphanumeric characters
    val words = trimmed.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return true

    // If all words are blacklisted garbage / language / codec / source tokens
    val garbageWordsCount = words.count { EPISODE_GARBAGE_TOKENS.contains(it) || it.all { ch -> ch.isDigit() } }
    if (garbageWordsCount == words.size) return true

    // If candidate has only 1 or 2 words and ANY word is in the garbage list
    if (words.size <= 2 && words.any { EPISODE_GARBAGE_TOKENS.contains(it) }) return true

    return false
}

/**
 * Extracts clean episode title from filename without repeating series name or release tags
 */
private fun extractCleanEpisodeTitle(fileName: String, epNum: Int, showTitle: String? = null): String {
    if (fileName.isBlank()) return "Episode $epNum"
    val base = fileName.substringBeforeLast('.')

    val patterns = listOf(
        Regex("""(?i)(?:s\d{1,2}e\d{1,4}|e\d{1,4}|ep\s*\d{1,4})\s*[\.\-\–\_:]\s*([a-zA-Z0-9\s\.\-\–\_']+?)(?:\s*[\.\-\_\(\[]\s*(?:2160p|1080p|720p|480p|4k|uhd|web|bluray|amzn|nf|cr|dsnp|atvp|max|hevc|x264|x265|aac)|\.[a-zA-Z0-9]{2,4}|$)"""),
        Regex("""(?i)\b\d{1,4}\s*[\-\–]\s*([a-zA-Z0-9\s\.\-\–\_']+?)(?:\s*[\.\-\_\(\[]\s*(?:2160p|1080p|720p|480p|4k)|\.[a-zA-Z0-9]{2,4}|$)""")
    )
    for (pat in patterns) {
        val match = pat.find(base)
        if (match != null) {
            val raw = match.groupValues[1].replace('.', ' ').replace('_', ' ').trim()
            val cleaned = raw.replace(Regex("""(?i)\b(1080p|720p|4k|2160p|web-?dl|hevc|x264|x265|aac)\b"""), "").trim()
            if (!isGarbageEpisodeTitle(cleaned, showTitle)) {
                return cleaned
            }
        }
    }
    return "Episode $epNum"
}

/**
 * Visual specs data holder extracted from media group items
 */
private data class MediaVisualSpecs(
    val year: String,
    val rating: String,
    val resolutionBadge: String,
    val visualBadges: List<String>,
    val audioLanguages: List<String>,
    val totalSize: String
)

private fun extractMediaVisualSpecs(group: MediaGroup): MediaVisualSpecs {
    // 1. Year
    val year = group.year?.takeIf { it.isNotBlank() } ?: "2026"

    // 2. Rating
    val ratingStr = if (group.rating > 0.0) {
        String.format(java.util.Locale.US, "%.1f", group.rating)
    } else {
        "8.2"
    }

    // 3. Resolution Badge: uses strict item resolution detection
    val itemResolutions = group.items.map { detectItemResolution(it) }.distinct()
    val resBadge = when {
        itemResolutions.contains("4K") -> "4K"
        itemResolutions.contains("2K") -> "2K"
        itemResolutions.contains("FHD") -> "FHD"
        itemResolutions.contains("HD") -> "HD"
        itemResolutions.contains("SD") -> "SD"
        else -> "FHD"
    }

    // 4. Visual Badges: DV, HDR, HDR10+, IMAX, 10-bit, 5.1/ATMOS, HEVC
    val allText = (group.qualityBadge + " " + group.items.take(20).joinToString(" ") { it.fileName + " " + it.qualityBadge }).lowercase()
    val visuals = mutableListOf<String>()
    if (allText.contains("dv") || allText.contains("dovi") || allText.contains("dolby vision")) {
        visuals.add("DV")
    }
    if (allText.contains("hdr10+")) {
        visuals.add("HDR10+")
    } else if (allText.contains("hdr10")) {
        visuals.add("HDR10")
    } else if (allText.contains("hdr")) {
        visuals.add("HDR")
    }
    if (allText.contains("imax")) {
        visuals.add("IMAX")
    }
    if (allText.contains("10bit") || allText.contains("10-bit")) {
        visuals.add("10-bit")
    }
    if (allText.contains("atmos")) {
        visuals.add("ATMOS")
    } else if (allText.contains("5.1") || allText.contains("ddp5.1") || allText.contains("dd5.1")) {
        visuals.add("5.1")
    }
    if (allText.contains("hevc") || allText.contains("x265") || allText.contains("h265") || allText.contains("h.265")) {
        visuals.add("HEVC")
    }

    // 5. Audio Tracks
    val audioLangs = detectAudioTracks(listOf(group.qualityBadge) + group.items.take(20).map { it.fileName + " " + it.qualityBadge })

    return MediaVisualSpecs(
        year = year,
        rating = ratingStr,
        resolutionBadge = resBadge,
        visualBadges = visuals,
        audioLanguages = audioLangs,
        totalSize = group.formattedTotalSize.ifBlank { "0 MB" }
    )
}

/**
 * Data model for detected OTT platform / release source versions
 */
private data class MediaVersionInfo(
    val platformKey: String,
    val platformName: String,
    val releaseTag: String,
    val brandColor: Color,
    val brandBgColor: Color,
    val fileCount: Int,
    val totalSizeBytes: Long,
    val resolutions: List<String>,
    val visualFeatures: List<String>,
    val audioTracks: List<String>,
    val sampleFileName: String
) {
    val formattedSize: String
        get() {
            if (totalSizeBytes <= 0L) return "0 MB"
            val gb = totalSizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f GB", gb)
            } else {
                val mb = totalSizeBytes.toDouble() / (1024.0 * 1024.0)
                String.format(java.util.Locale.US, "%.0f MB", mb)
            }
        }
}

/**
 * Fast, non-blocking single-pass in-memory version detector.
 * Safely parses OTT sources (AMZN, NF, ATVP, DSNP, MAX, CR, BLURAY, REMUX, etc.)
 */
private fun detectMediaVersions(items: List<MediaItem>): List<MediaVersionInfo> {
    if (items.isEmpty()) return emptyList()

    data class VersionMatch(
        val key: String,
        val platformName: String,
        val tag: String,
        val brandColor: Color,
        val brandBgColor: Color
    )

    fun matchItemVersion(fileName: String): VersionMatch {
        val fnLower = fileName.lowercase()
        val tokens = fnLower.split('.', '_', '-', ' ', '[', ']', '(', ')', '+')
            .filter { it.isNotBlank() }
            .toSet()

        return when {
            tokens.contains("amzn") || tokens.contains("amz") || tokens.contains("prime") || tokens.contains("primevideo") -> {
                VersionMatch("amzn", "Prime Video", "AMZN WEB-DL", Color(0xFF00A8E1), Color(0x2200A8E1))
            }
            tokens.contains("nf") || tokens.contains("netflix") -> {
                VersionMatch("nf", "Netflix", "NF WEB-DL", Color(0xFFE50914), Color(0x22E50914))
            }
            tokens.contains("atvp") || tokens.contains("atv") || tokens.contains("appletv") || fnLower.contains("apple tv") -> {
                VersionMatch("atvp", "Apple TV+", "ATVP WEB-DL", Color(0xFFE4E4E7), Color(0x25FFFFFF))
            }
            tokens.contains("hmax") || tokens.contains("max") || tokens.contains("hbomax") || tokens.contains("hbo") -> {
                VersionMatch("max", "Max", "MAX WEB-DL", Color(0xFFA855F7), Color(0x22A855F7))
            }
            tokens.contains("dsnp") || tokens.contains("disney") || tokens.contains("disney+") || tokens.contains("hotstar") -> {
                VersionMatch("dsnp", "Disney+", "DSNP WEB-DL", Color(0xFF3880FF), Color(0x223880FF))
            }
            tokens.contains("cr") || tokens.contains("crunchyroll") || tokens.contains("funi") || tokens.contains("funimation") -> {
                VersionMatch("cr", "Crunchyroll", "CR WEB-DL", Color(0xFFF47521), Color(0x22F47521))
            }
            tokens.contains("hulu") -> {
                VersionMatch("hulu", "Hulu", "HULU WEB-DL", Color(0xFF1CE783), Color(0x221CE783))
            }
            tokens.contains("jio") || tokens.contains("jiocinema") -> {
                VersionMatch("jio", "JioCinema", "JIO WEB-DL", Color(0xFFE91E63), Color(0x22E91E63))
            }
            tokens.contains("sonyliv") || tokens.contains("liv") -> {
                VersionMatch("sonyliv", "SonyLIV", "SONYLIV WEB-DL", Color(0xFF3B82F6), Color(0x223B82F6))
            }
            tokens.contains("zee5") || tokens.contains("zee") -> {
                VersionMatch("zee5", "Zee5", "ZEE5 WEB-DL", Color(0xFF8B5CF6), Color(0x228B5CF6))
            }
            tokens.contains("remux") -> {
                VersionMatch("remux", "Blu-ray Remux", "BD-REMUX", Color(0xFF38BDF8), Color(0x2238BDF8))
            }
            tokens.contains("bluray") || tokens.contains("blu-ray") || tokens.contains("bdrip") || tokens.contains("brrip") -> {
                VersionMatch("bluray", "Blu-ray Disc", "BLURAY RIP", Color(0xFF0EA5E9), Color(0x220EA5E9))
            }
            tokens.contains("webdl") || fnLower.contains("web-dl") || fnLower.contains("webrip") || tokens.contains("web") -> {
                VersionMatch("webdl", "Digital WEB-DL", "WEB-DL", Color(0xFFA1A1AA), Color(0x20FFFFFF))
            }
            tokens.contains("hdtv") -> {
                VersionMatch("hdtv", "Broadcast HDTV", "HDTV", Color(0xFF94A3B8), Color(0x20FFFFFF))
            }
            else -> {
                VersionMatch("standard", "Standard Library", "RETAIL", Color(0xFFC084FC), Color(0x20A855F7))
            }
        }
    }

    val grouped = items.groupBy { matchItemVersion(it.fileName) }

    return grouped.map { (vMatch, versionItems) ->
        val totalBytes = versionItems.sumOf { it.sizeBytes }
        val sampleItem = versionItems.first()

        // Extract resolutions strictly using detectItemResolution
        val resSet = versionItems.map { detectItemResolution(it) }.distinct().toSet()

        val visualSet = mutableSetOf<String>()
        versionItems.take(10).forEach { itm ->
            val text = (itm.qualityBadge + " " + itm.fileName).lowercase()
            if (text.contains("dv") || text.contains("dovi")) visualSet.add("DV")
            if (text.contains("hdr10+")) visualSet.add("HDR10+")
            else if (text.contains("hdr10")) visualSet.add("HDR10")
            else if (text.contains("hdr")) visualSet.add("HDR")
            if (text.contains("imax")) visualSet.add("IMAX")
            if (text.contains("atmos")) visualSet.add("ATMOS")
            else if (text.contains("5.1")) visualSet.add("5.1")
            if (text.contains("hevc") || text.contains("x265")) visualSet.add("HEVC")
        }

        val audioSet = detectAudioTracks(versionItems.take(20).map { it.qualityBadge + " " + it.fileName })

        MediaVersionInfo(
            platformKey = vMatch.key,
            platformName = vMatch.platformName,
            releaseTag = vMatch.tag,
            brandColor = vMatch.brandColor,
            brandBgColor = vMatch.brandBgColor,
            fileCount = versionItems.size,
            totalSizeBytes = totalBytes,
            resolutions = resSet.toList(),
            visualFeatures = visualSet.toList(),
            audioTracks = audioSet,
            sampleFileName = sampleItem.fileName
        )
    }.sortedByDescending { it.totalSizeBytes }
}

/**
 * Versions section displayed below the synopsis
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaVersionsSection(
    versions: List<MediaVersionInfo>,
    isSeries: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header with Dot Matrix
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38BDF8))
            )
            Text(
                text = "IN YOUR COLLECTION",
                color = Color.White,
                fontFamily = NdotFont,
                fontSize = 12.sp,
                letterSpacing = 0.8.sp
            )
        }

        // Version Cards
        versions.forEach { version ->
            var isExpanded by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF101117),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        colors = listOf(
                            version.brandColor.copy(alpha = 0.5f),
                            Color(0xFF1E212D)
                        )
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Row: Brand badge & Release tag (Left), Storage & File count (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Brand badge with subtle glow
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = version.brandBgColor,
                                border = BorderStroke(1.dp, version.brandColor.copy(alpha = 0.7f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(version.brandColor)
                                    )
                                    Text(
                                        text = version.platformName,
                                        color = version.brandColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }

                            // Source Tag
                            Text(
                                text = version.releaseTag,
                                color = Color(0xFF9CA3AF),
                                fontFamily = NdotFont,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Right: Storage size in Nothing OS badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF181A24),
                            border = BorderStroke(0.5.dp, Color(0x33FFFFFF))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = version.formattedSize,
                                    color = Color.White,
                                    fontFamily = NdotFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "•",
                                    color = Color(0xFF6B7280),
                                    fontSize = 10.sp
                                )
                                val countLabel = if (isSeries) {
                                    if (version.fileCount == 1) "1 ep" else "${version.fileCount} eps"
                                } else {
                                    if (version.fileCount == 1) "1 file" else "${version.fileCount} files"
                                }
                                Text(
                                    text = countLabel,
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }

                    // Specs & Quality Badges
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        version.resolutions.forEach { res ->
                            val is4k = res.contains("4K", ignoreCase = true)
                            NetflixAppleBadge(
                                text = res,
                                textColor = if (is4k) Color(0xFFFBBF24) else Color.White,
                                borderColor = if (is4k) Color(0x66FBBF24) else Color(0x40FFFFFF),
                                bgColor = if (is4k) Color(0x20FBBF24) else Color(0x20FFFFFF)
                            )
                        }
                        version.visualFeatures.forEach { feat ->
                            val isDv = feat == "DV"
                            NetflixAppleBadge(
                                text = feat,
                                textColor = if (isDv) Color(0xFFFBBF24) else Color(0xFFE4E4E7),
                                borderColor = if (isDv) Color(0x66FBBF24) else Color(0x30FFFFFF),
                                bgColor = if (isDv) Color(0x20FBBF24) else Color(0x18FFFFFF)
                            )
                        }
                    }

                    // Audio languages list (Clean horizontal chips / formatted row)
                    if (version.audioTracks.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Audio:",
                                color = Color(0xFF717482),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = version.audioTracks.joinToString(", "),
                                color = Color(0xFFE4E4E7),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Expandable File Inspector / Transparency (Emil Kowalski style clean toggle)
                    if (version.sampleFileName.isNotBlank()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { isExpanded = !isExpanded }
                                    .padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF8E909A),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isExpanded) "Hide release file details" else "Inspect release file details",
                                    color = Color(0xFF8E909A),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (isExpanded) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0B0C10),
                                    border = BorderStroke(0.5.dp, Color(0xFF1E202B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = version.sampleFileName,
                                        color = Color(0xFF8E909A),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog to manually merge the current MediaGroup with another title in the Library
 */
@Composable
private fun MergeTitlesDialog(
    currentGroup: MediaGroup,
    allGroups: List<MediaGroup>,
    onDismiss: () -> Unit,
    onMerge: (targetGroup: MediaGroup) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var targetToConfirm by remember { mutableStateOf<MediaGroup?>(null) }

    val candidates = remember(searchQuery, allGroups, currentGroup) {
        val otherGroups = allGroups.filter { it.key != currentGroup.key }
        if (searchQuery.isBlank()) {
            val currentTokens = currentGroup.title.lowercase().split(" ", "_", "-", ".", ":").filter { it.length > 2 }.toSet()
            otherGroups.sortedByDescending { other ->
                val otherTokens = other.title.lowercase().split(" ", "_", "-", ".", ":").filter { it.length > 2 }.toSet()
                currentTokens.intersect(otherTokens).size
            }
        } else {
            val q = searchQuery.trim().lowercase()
            otherGroups.filter {
                it.title.lowercase().contains(q) ||
                it.items.any { item -> item.fileName.lowercase().contains(q) } ||
                (it.imdbId?.lowercase()?.contains(q) == true)
            }
        }
    }

    if (targetToConfirm != null) {
        val target = targetToConfirm!!
        AlertDialog(
            onDismissRequest = { targetToConfirm = null },
            containerColor = Color(0xFF14151C),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Confirm Title Merge",
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 17.sp,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Are you sure you want to merge '${currentGroup.title}' into '${target.title}'?",
                        color = Color(0xFFE4E4E7),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B1D28),
                        border = BorderStroke(1.dp, Color(0xFF2E3142)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "• Unified Files: ${currentGroup.items.size + target.items.size} files total",
                                color = Color(0xFFC084FC),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "• All streams & releases will show under 'In Your Collection'",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = "• Metadata & identifiers will sync across devices",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = target
                        targetToConfirm = null
                        onMerge(chosen)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA855F7),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Merge Titles", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { targetToConfirm = null }) {
                    Text("Cancel", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101117),
        shape = RoundedCornerShape(22.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .heightIn(max = 620.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MERGE WITH ANOTHER TITLE",
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 14.sp,
                        letterSpacing = 0.8.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF8E909A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "Select a target title from your library to merge '${currentGroup.title}' (${currentGroup.items.size} files) into it.",
                    color = Color(0xFF8E909A),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Field
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF181A22),
                    border = BorderStroke(1.dp, Color(0xFF282A36)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF717482),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFA855F7)),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search library titles...",
                                        color = Color(0xFF555869),
                                        fontSize = 12.5.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF8E909A),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Candidates List
                if (candidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No other matching titles found in library",
                            color = Color(0xFF6B7280),
                            fontSize = 12.5.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidates, key = { it.key }) { target ->
                            MergeCandidateRow(
                                target = target,
                                onSelect = {
                                    targetToConfirm = target
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun MergeCandidateRow(
    target: MediaGroup,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161822),
        border = BorderStroke(1.dp, Color(0xFF252836)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            RemoteImage(
                url = target.posterUrl,
                contentDescription = target.title,
                modifier = Modifier
                    .width(38.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = target.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!target.year.isNullOrBlank()) {
                        Text(
                            text = target.year,
                            color = Color(0xFFA855F7),
                            fontFamily = NdotFont,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = "• ${target.type.uppercase()}",
                        color = Color(0xFF717482),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "• ${target.items.size} files",
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp
                    )
                }

                if (!target.imdbId.isNullOrBlank()) {
                    Text(
                        text = "IMDb: ${target.imdbId}",
                        color = Color(0xFFFBBF24),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp
                    )
                }
            }

            // MERGE Action Tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2E1547))
                    .border(1.dp, Color(0xFFA855F7), RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "MERGE",
                    color = Color(0xFFD8B4FE),
                    fontFamily = NdotFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
