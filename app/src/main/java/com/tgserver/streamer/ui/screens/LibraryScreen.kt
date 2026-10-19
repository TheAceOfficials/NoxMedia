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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// In-memory poster and backdrop cache
object ImageMemoryCache {
    private val cache = LruCache<String, ImageBitmap>(250)
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
            val cache = okhttp3.Cache(cacheDir, 120L * 1024 * 1024)
            val client = OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
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
        val cached = ImageMemoryCache.get(url)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NoxMedia/1.0 (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { stream ->
                            val options = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.RGB_565
                            }
                            val decoded = BitmapFactory.decodeStream(stream, null, options)
                            if (decoded != null) {
                                val imgBmp = decoded.asImageBitmap()
                                ImageMemoryCache.put(url, imgBmp)
                                withContext(Dispatchers.Main) {
                                    bitmap = imgBmp
                                }
                            }
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: MainAppViewModel) {
    val allMedia by viewModel.media.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val navGroupKey by viewModel.selectedMediaGroupKey.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Group raw files into distinct titles (Movies and Series entities)
    val mediaGroups = remember(allMedia) {
        allMedia.toMediaGroups()
    }

    // Dynamic animated search placeholders from user library & popular titles
    val searchPlaceholders = remember(mediaGroups) {
        val libraryTitles = mediaGroups.map { it.title.trim() }.filter { it.isNotBlank() }.distinct()
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

    var sortOrder by remember { mutableStateOf(LibrarySortOrder.RECENT) }
    var showSortSheet by remember { mutableStateOf(false) }

    // Intercept hardware / gesture back navigation when inside details view
    BackHandler(enabled = selectedGroup != null) {
        selectedGroupKey = null
        viewModel.clearSelectedMediaGroup()
    }

    // Filter media groups by category & search, then sort by selected criteria
    val filteredGroups = remember(mediaGroups, searchQuery, selectedCategory, sortOrder) {
        val filtered = mediaGroups.filter { group ->
            val matchesCategory = when (selectedCategory) {
                "all" -> true
                "movies" -> group.type == "movie"
                "series" -> group.type == "series"
                "anime" -> group.type == "anime"
                "4k" -> group.qualityBadge.contains("4K", ignoreCase = true) || group.qualityBadge.contains("2160", ignoreCase = true)
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                    group.title.contains(searchQuery, ignoreCase = true) ||
                    (group.year?.contains(searchQuery, ignoreCase = true) == true) ||
                    (group.imdbId?.contains(searchQuery, ignoreCase = true) == true) ||
                    group.items.any { it.fileName.contains(searchQuery, ignoreCase = true) || it.episodeTitle?.contains(searchQuery, ignoreCase = true) == true }

            matchesCategory && matchesQuery
        }

        when (sortOrder) {
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
            LibrarySortOrder.SIZE_DESC -> filtered.sortedByDescending { it.items.sumOf { item -> item.sizeBytes } }
            LibrarySortOrder.SIZE_ASC -> filtered.sortedBy { it.items.sumOf { item -> item.sizeBytes } }
            LibrarySortOrder.RATING_DESC -> filtered.sortedWith(
                compareByDescending<MediaGroup> { it.rating }
                    .thenBy { it.title.lowercase() }
            )
        }
    }

    val streamBaseUrl = "http://${telemetry.localIp}:${telemetry.port}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
    ) {
        AnimatedContent(
            targetState = selectedGroup,
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
        ) { currentGroup ->
            if (currentGroup != null) {
                // FULL SCREEN CONTENT DETAILS VIEW
                ContentDetailsScreen(
                    group = currentGroup,
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

                    // IOS GLASS SEGMENTED CONTROL TABS (Inspired by cc-ios-tabs)
                    val categories = listOf(
                        "all" to "ALL",
                        "movies" to "MOVIES",
                        "series" to "SERIES",
                        "anime" to "ANIME",
                        "4k" to "4K"
                    )

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

                    // SORT & SUMMARY TOOLBAR
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
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B1D25))
                                .border(1.dp, Color(0xFF2C2F3A), RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showSortSheet = true
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Sort: ${sortOrder.label}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = NdotFont,
                                letterSpacing = 0.5.sp
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select sort order",
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(14.dp)
                            )
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
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 84.dp)
                        ) {
                            items(filteredGroups, key = { it.key }) { group ->
                                PosterCard(
                                    group = group,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedGroupKey = group.key
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                containerColor = Color(0xFF14151B),
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
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Sort Library",
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 15.sp,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LibrarySortOrder.values().forEach { option ->
                        val isSelected = sortOrder == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF28103F) else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    sortOrder = option
                                    showSortSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                color = if (isSelected) Color(0xFFC084FC) else Color(0xFFD4D4D8),
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontFamily = FontFamily.Default
                            )

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFA855F7))
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
 * Minimalist Poster Grid Item (Clean Poster without messy overlay badges, normal title font)
 */
@Composable
private fun PosterCard(
    group: MediaGroup,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Clean Poster art (Overlay quality pills removed as requested to avoid messy look)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(1.dp, Color(0xFF2A1C3C)), RoundedCornerShape(14.dp))
        ) {
            RemoteImage(
                url = group.posterUrl,
                contentDescription = group.title,
                isSeries = group.type == "series" || group.type == "anime",
                modifier = Modifier.fillMaxSize()
            )

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
 * Full-Screen Content Details View (Adapted from user's attached screenshots)
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ContentDetailsScreen(
    group: MediaGroup,
    viewModel: MainAppViewModel,
    streamBaseUrl: String,
    onBack: () -> Unit,
    onCopyStream: (String) -> Unit,
    onRefreshMetadata: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isSeries = group.type == "series" || group.type == "anime"
    var showRescanDialog by remember { mutableStateOf(false) }

    val visualSpecs = remember(group) {
        extractMediaVisualSpecs(group)
    }

    val detectedVersions = remember(group.items) {
        detectMediaVersions(group.items)
    }

    // Group episodes by Season for series
    val seasonMap = remember(group.items) {
        if (isSeries) {
            group.items.groupBy { it.season ?: 1 }
        } else {
            emptyMap()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // TOP BACKDROP WITH APP BAR (Edge-to-edge behind status bar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            if (!group.backdropUrl.isNullOrBlank()) {
                RemoteImage(
                    url = group.backdropUrl,
                    contentDescription = "Backdrop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (!group.posterUrl.isNullOrBlank()) {
                RemoteImage(
                    url = group.posterUrl,
                    contentDescription = "Backdrop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1F0D36), Color(0xFF090310))
                            )
                        )
                )
            }

            // Dark gradient overlay towards bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.85f),
                                BackgroundOled
                            )
                        )
                    )
            )

            // Top Bar Overlay with Status Bar clearance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 14.dp, end = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Top bar Title in NORMAL FONT (SemiBold, clear and legible)
                Text(
                    text = group.title,
                    color = Color.White,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                // Top bar Action: Refresh (Rescan button is already present below next to type badge)
                IconButton(
                    onClick = onRefreshMetadata,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // CONTENT INFO HEADER (Poster + Title + Badges)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Poster thumbnail
            RemoteImage(
                url = group.posterUrl,
                contentDescription = group.title,
                isSeries = isSeries,
                modifier = Modifier
                    .width(96.dp)
                    .height(142.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF38155E), RoundedCornerShape(12.dp))
            )

            // Title & Meta Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header Title in NORMAL FONT (Bold, clear and prominent)
                Text(
                    text = group.title,
                    color = Color.White,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 1. Netflix & Apple TV+ Style Badges: Year, Rating, Quality, Visuals
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Year Badge
                    NetflixAppleBadge(
                        text = visualSpecs.year,
                        textColor = Color(0xFFE4E4E7),
                        borderColor = Color(0x35FFFFFF),
                        bgColor = Color(0x18FFFFFF)
                    )

                    // Rating Badge (Gold Star)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x20FBBF24))
                            .border(BorderStroke(0.8.dp, Color(0x66FBBF24)), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "★",
                                color = Color(0xFFFBBF24),
                                fontSize = 9.sp
                            )
                            Text(
                                text = visualSpecs.rating,
                                color = Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Default
                            )
                        }
                    }

                    // Quality Badge (e.g. 720p -> HD, 1080p -> 1080p FHD, 1440p -> 2K, 2160p -> 4K)
                    NetflixAppleBadge(
                        text = visualSpecs.resolutionBadge,
                        textColor = Color.White,
                        borderColor = Color(0x55FFFFFF),
                        bgColor = Color(0x28FFFFFF)
                    )

                    // Visual Badges: DV, HDR, HDR10+, IMAX, 5.1/ATMOS, HEVC
                    visualSpecs.visualBadges.forEach { badge ->
                        val isDv = badge == "DV"
                        val isImax = badge == "IMAX"
                        val textColor = when {
                            isDv -> Color(0xFFFBBF24)
                            isImax -> Color(0xFF60A5FA)
                            else -> Color(0xFFE4E4E7)
                        }
                        val borderColor = when {
                            isDv -> Color(0x66FBBF24)
                            isImax -> Color(0x6660A5FA)
                            else -> Color(0x35FFFFFF)
                        }
                        val bgColor = when {
                            isDv -> Color(0x20FBBF24)
                            isImax -> Color(0x203B82F6)
                            else -> Color(0x18FFFFFF)
                        }
                        NetflixAppleBadge(
                            text = badge,
                            textColor = textColor,
                            borderColor = borderColor,
                            bgColor = bgColor
                        )
                    }
                }

                // 2. Audio Tracks Row: Available in: Hindi, English, Tamil
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Available in:",
                        color = Color(0xFF8E909A),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = visualSpecs.audioLanguages.joinToString(", ").ifBlank { "English" },
                        color = Color(0xFFE4E4E7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Default,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 3. Total Size Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Total Size:",
                        color = Color(0xFF8E909A),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = visualSpecs.totalSize,
                        color = Color(0xFFC084FC),
                        fontSize = 11.sp,
                        fontFamily = NdotFont,
                        letterSpacing = 0.4.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF28103F))
                            .border(1.dp, Color(0xFF4C1D7C), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = group.type.uppercase(),
                            color = Color(0xFFC084FC),
                            fontFamily = NdotFont,
                            fontSize = 10.sp
                        )
                    }

                    // Rescan Metadata pill button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E1729))
                            .border(1.dp, Color(0xFF5B21B6), RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showRescanDialog = true
                            }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "RESCAN",
                                color = Color(0xFFC084FC),
                                fontFamily = NdotFont,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                // External IDs (IMDb / TMDb)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!group.imdbId.isNullOrBlank()) {
                        Text(
                            text = group.imdbId,
                            color = Color(0xFFFBBF24),
                            fontFamily = NdotFont,
                            fontSize = 10.sp
                        )
                    }
                    if (group.tmdbId != null && group.tmdbId > 0) {
                        Text(
                            text = "tmdb ${group.tmdbId}",
                            color = Color(0xFF60A5FA),
                            fontFamily = NdotFont,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // OVERVIEW SYNOPSIS
        if (!group.overview.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = group.overview,
                    color = Color(0xFFD4D4D8),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // VERSIONS IN LIBRARY (Detected OTT platforms & release editions)
        if (detectedVersions.isNotEmpty()) {
            MediaVersionsSection(
                versions = detectedVersions,
                isSeries = isSeries
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // STREAMS & EPISODES SECTION
        if (!isSeries) {
            // MOVIE STREAMS (Multi-format listing with Hide/Unhide toggle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "STREAMS (${group.items.size})",
                    color = Color(0xFF8E909A),
                    fontFamily = NdotFont,
                    fontSize = 11.5.sp
                )

                group.items.forEach { streamItem ->
                    MovieStreamItemRow(
                        item = streamItem,
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
            // SERIES SEASONS & EPISODES (Multi-quality grouping per episode: E01 | Title | 1080p | 4K)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val seasonCount = seasonMap.size
                val seasonLabel = if (seasonCount == 1) "1 SEASON" else "$seasonCount SEASONS"
                val epCount = group.distinctEpisodeCount
                val epLabel = if (epCount == 1) "1 EPISODE" else "$epCount EPISODES"
                Text(
                    text = "$seasonLabel · $epLabel",
                    color = Color(0xFF8E909A),
                    fontFamily = NdotFont,
                    fontSize = 11.5.sp
                )

                seasonMap.forEach { (seasonNum, seasonItems) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF14151B))
                            .border(1.dp, Color(0xFF262832), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Season Header in NORMAL FONT (SemiBold, clear and legible)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Season $seasonNum",
                                color = Color.White,
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            val distinctEpCount = seasonItems.map { it.episode ?: 1 }.distinct().size
                            Text(
                                text = "$distinctEpCount eps",
                                color = Color(0xFF8E909A),
                                fontFamily = NdotFont,
                                fontSize = 11.sp
                            )
                        }

                        HorizontalDivider(color = Color(0xFF22242D), thickness = 1.dp)

                        // Group files by Episode number so multi-qualities don't duplicate the row
                        val episodesGrouped = remember(seasonItems) {
                            seasonItems.groupBy { it.episode ?: 1 }.toSortedMap()
                        }

                        episodesGrouped.forEach { (epNum, epItems) ->
                            EpisodeGroupAccordionRow(
                                epNum = epNum,
                                epItems = epItems,
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
            }
        }

        // Bottom space for floating bottom bar clearance
        Spacer(modifier = Modifier.height(90.dp))
    }
}

/**
 * Grouped Episode Row with multi-quality badges and expandable dropdown details
 * Displays: "E01 | A Whole New Whirled | 1080p | 4K"
 * Clicking expands dropdown to show full Telegram filename, specs, size, and Hide/Unhide toggle
 */
@Composable
private fun EpisodeGroupAccordionRow(
    epNum: Int,
    epItems: List<MediaItem>,
    streamBaseUrl: String,
    onToggleHide: (MediaItem) -> Unit,
    onCopyStream: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val primary = epItems.first()
    val epTitle = epItems.firstOrNull { !it.episodeTitle.isNullOrBlank() }?.episodeTitle
        ?.ifBlank { primary.title } ?: primary.title

    // Distinct available qualities: e.g. ["1080p", "4K 2160p"]
    val qualities = remember(epItems) {
        epItems.map { it.quality.ifBlank { "1080p" } }.distinct()
    }
    val allHidden = epItems.all { it.isHidden }
    val anyHidden = epItems.any { it.isHidden }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isExpanded) Color(0xFF181A22) else Color(0xFF13141B))
            .border(
                1.dp,
                if (isExpanded) Color(0xFF3B235E) else Color(0xFF22242E),
                RoundedCornerShape(12.dp)
            )
    ) {
        // SUMMARY HEADER ROW (Clickable to expand/collapse)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Episode Number (e.g. E01)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2E230A))
                        .border(1.dp, Color(0xFFD97706), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "E%02d".format(epNum),
                        color = Color(0xFFFBBF24),
                        fontFamily = NdotFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Episode Title and Qualities stacked vertically so title has full row width and never truncates
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = epTitle,
                        color = if (allHidden) Color(0xFF6B7280) else Color.White,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Available Qualities Pills (e.g. 1080p | 4K)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        qualities.forEach { q ->
                            val is4k = q.contains("4K", ignoreCase = true) || q.contains("2160", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (is4k) Color(0xFF38155E) else Color(0xFF1E212E))
                                    .border(1.dp, if (is4k) Color(0xFF7C3AED) else Color(0xFF3B3E52), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = q,
                                    color = if (is4k) Color(0xFFE9D5FF) else Color(0xFFCBD5E1),
                                    fontSize = 9.sp,
                                    fontFamily = NdotFont,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (anyHidden) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3F1318))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (allHidden) "HIDDEN" else "PARTIAL",
                            color = Color(0xFFF87171),
                            fontSize = 8.sp,
                            fontFamily = NdotFont
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // EXPANDED ACCORDION DETAILS (Full Telegram filename on top, specs/size/hide underneath)
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
                HorizontalDivider(color = Color(0xFF252733), thickness = 1.dp)

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
                            // 1. UPPER: Full Telegram File Name (Pura title, upper)
                            Text(
                                text = item.fileName.ifBlank { item.title },
                                color = if (isHidden) Color(0xFF94A3B8).copy(alpha = 0.6f) else Color(0xFFE2E8F0),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.5.sp
                            )

                            // 2. NICHE: Details - Quality specs pill
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
                                        fontFamily = NdotFont,
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
                                    fontFamily = NdotFont,
                                    fontSize = 10.5.sp,
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
                                        modifier = Modifier.size(26.dp)
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
                                                fontFamily = NdotFont,
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
 * Stream item row for Movies with Hide/Unhide toggle and full filename
 */
@Composable
private fun MovieStreamItemRow(
    item: MediaItem,
    onToggleHide: (MediaItem) -> Unit,
    onCopy: () -> Unit
) {
    val isHidden = item.isHidden
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isHidden) Color(0xFF131014) else Color(0xFF14151B))
            .border(
                1.dp,
                if (isHidden) Color(0xFF4A1924) else Color(0xFF262832),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 1. UPPER: Full Telegram File Name (Pura title, upper)
            Text(
                text = item.fileName.ifBlank { item.title },
                color = if (isHidden) Color(0xFF94A3B8).copy(alpha = 0.6f) else Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.5.sp
            )

            // 2. NICHE: Details - Quality specs pill
            val formattedBadge = formatShortBadge(item.qualityBadge.ifBlank { item.quality })
            if (formattedBadge.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2E1C0A))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = formattedBadge,
                        color = Color(0xFFFBBF24),
                        fontFamily = NdotFont,
                        fontSize = 10.sp,
                        lineHeight = 13.5.sp
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
                    fontFamily = NdotFont,
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isHidden) Color(0xFF3F1318) else Color(0xFF232532))
                            .border(
                                1.dp,
                                if (isHidden) Color(0xFFDC2626) else Color(0xFF3E4358),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleHide(item) }
                            .padding(horizontal = 9.dp, vertical = 4.5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isHidden) "Unhide" else "Hide",
                                tint = if (isHidden) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isHidden) "UNHIDE" else "HIDE",
                                color = if (isHidden) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                                fontFamily = NdotFont,
                                fontSize = 9.5.sp,
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
    var isLoading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<EnrichedMetadata>>(emptyList()) }
    var searchAttempted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        searchAttempted = true
        results = viewModel.searchMetadataCandidates(searchQuery, group.type)
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
                    text = "Rescan Metadata",
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
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Search Cinemeta or enter IMDb ID (e.g. tt39395275) to re-sync correct title, poster, and stream mappings.",
                    color = Color(0xFF8E909A),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                // Search field + Search button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1C25))
                            .border(1.dp, Color(0xFF2E3242), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
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

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                searchAttempted = true
                                results = viewModel.searchMetadataCandidates(searchQuery, group.type)
                                isLoading = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            text = "SEARCH",
                            color = Color.White,
                            fontFamily = NdotFont,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Results list
                if (isLoading) {
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
                } else if (results.isEmpty() && searchAttempted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matches found. Try entering an IMDb ID (e.g. tt1234567)",
                            color = Color(0xFF717482),
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
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
                                    viewModel.applyManualMetadata(group.items, candidate)
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
    val allText = buildString {
        append(group.qualityBadge)
        append(" ")
        group.items.take(20).forEach { item ->
            append(item.fileName)
            append(" ")
            append(item.quality)
            append(" ")
            append(item.qualityBadge)
            append(" ")
        }
    }.lowercase()

    // 1. Year
    val year = group.year?.takeIf { it.isNotBlank() } ?: "2026"

    // 2. Rating
    val ratingStr = if (group.rating > 0.0) {
        String.format(java.util.Locale.US, "%.1f", group.rating)
    } else {
        "8.2"
    }

    // 3. Resolution Badge: 720p -> HD, 1080p -> 1080p FHD, 1440p -> 2K, 2160p -> 4K
    val resBadge = when {
        allText.contains("2160") || allText.contains("4k") || allText.contains("uhd") -> "4K"
        allText.contains("1440") || allText.contains("2k") || allText.contains("qhd") -> "2K"
        allText.contains("1080") || allText.contains("fhd") -> "1080p FHD"
        allText.contains("720") || allText.contains("hd") -> "HD"
        allText.contains("480") || allText.contains("sd") -> "SD"
        else -> "HD"
    }

    // 4. Visual Badges: DV, HDR, HDR10+, IMAX, 10-bit, 5.1/ATMOS, HEVC
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

    // 5. Audio Tracks / Languages
    val languages = mutableSetOf<String>()
    if (allText.contains("hin") || allText.contains("hindi")) languages.add("Hindi")
    if (allText.contains("eng") || allText.contains("english")) languages.add("English")
    if (allText.contains("tam") || allText.contains("tamil")) languages.add("Tamil")
    if (allText.contains("tel") || allText.contains("telugu")) languages.add("Telugu")
    if (allText.contains("mal") || allText.contains("malayalam")) languages.add("Malayalam")
    if (allText.contains("kan") || allText.contains("kannada")) languages.add("Kannada")
    if (allText.contains("ben") || allText.contains("bengali")) languages.add("Bengali")
    if (allText.contains("mar") || allText.contains("marathi")) languages.add("Marathi")
    if (allText.contains("jap") || allText.contains("japanese")) languages.add("Japanese")
    if (allText.contains("kor") || allText.contains("korean")) languages.add("Korean")
    if (allText.contains("spa") || allText.contains("spanish")) languages.add("Spanish")
    if (allText.contains("fre") || allText.contains("french")) languages.add("French")
    if (allText.contains("ger") || allText.contains("german")) languages.add("German")
    if (allText.contains("ita") || allText.contains("italian")) languages.add("Italian")
    if (allText.contains("rus") || allText.contains("russian")) languages.add("Russian")
    if (allText.contains("chi") || allText.contains("chinese")) languages.add("Chinese")

    if (languages.isEmpty()) {
        if (allText.contains("multi")) languages.add("Multi Audio")
        else if (allText.contains("dual")) languages.add("Dual Audio")
        else languages.add("English")
    }

    return MediaVisualSpecs(
        year = year,
        rating = ratingStr,
        resolutionBadge = resBadge,
        visualBadges = visuals,
        audioLanguages = languages.toList(),
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
 * with zero UI stutter, even on 1000+ episode shows like One Piece.
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
            // Prime Video
            tokens.contains("amzn") || tokens.contains("amz") || tokens.contains("prime") || tokens.contains("primevideo") -> {
                VersionMatch("amzn", "Prime Video", "AMZN WEB-DL", Color(0xFF00A8E1), Color(0x2200A8E1))
            }
            // Netflix
            tokens.contains("nf") || tokens.contains("netflix") -> {
                VersionMatch("nf", "Netflix", "NF WEB-DL", Color(0xFFE50914), Color(0x22E50914))
            }
            // Apple TV+
            tokens.contains("atvp") || tokens.contains("atv") || tokens.contains("appletv") || fnLower.contains("apple tv") -> {
                VersionMatch("atvp", "Apple TV+", "ATVP WEB-DL", Color(0xFFE4E4E7), Color(0x25FFFFFF))
            }
            // Max / HBO
            tokens.contains("hmax") || tokens.contains("max") || tokens.contains("hbomax") || tokens.contains("hbo") -> {
                VersionMatch("max", "Max", "MAX WEB-DL", Color(0xFFA855F7), Color(0x22A855F7))
            }
            // Disney+ / Hotstar
            tokens.contains("dsnp") || tokens.contains("disney") || tokens.contains("disney+") || tokens.contains("hotstar") -> {
                VersionMatch("dsnp", "Disney+", "DSNP WEB-DL", Color(0xFF3880FF), Color(0x223880FF))
            }
            // Crunchyroll / Funimation
            tokens.contains("cr") || tokens.contains("crunchyroll") || tokens.contains("funi") || tokens.contains("funimation") -> {
                VersionMatch("cr", "Crunchyroll", "CR WEB-DL", Color(0xFFF47521), Color(0x22F47521))
            }
            // Hulu
            tokens.contains("hulu") -> {
                VersionMatch("hulu", "Hulu", "HULU WEB-DL", Color(0xFF1CE783), Color(0x221CE783))
            }
            // JioCinema / SonyLIV / Zee5
            tokens.contains("jio") || tokens.contains("jiocinema") -> {
                VersionMatch("jio", "JioCinema", "JIO WEB-DL", Color(0xFFE91E63), Color(0x22E91E63))
            }
            tokens.contains("sonyliv") || tokens.contains("liv") -> {
                VersionMatch("sonyliv", "SonyLIV", "SONYLIV WEB-DL", Color(0xFF3B82F6), Color(0x223B82F6))
            }
            tokens.contains("zee5") || tokens.contains("zee") -> {
                VersionMatch("zee5", "Zee5", "ZEE5 WEB-DL", Color(0xFF8B5CF6), Color(0x228B5CF6))
            }
            // Blu-ray / Remux
            tokens.contains("remux") -> {
                VersionMatch("remux", "Blu-ray Remux", "BD-REMUX", Color(0xFF38BDF8), Color(0x2238BDF8))
            }
            tokens.contains("bluray") || tokens.contains("blu-ray") || tokens.contains("bdrip") || tokens.contains("brrip") -> {
                VersionMatch("bluray", "Blu-ray Disc", "BLURAY RIP", Color(0xFF0EA5E9), Color(0x220EA5E9))
            }
            // General WEB-DL / HDTV
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

        val resSet = mutableSetOf<String>()
        versionItems.forEach { itm ->
            val text = (itm.quality + " " + itm.qualityBadge + " " + itm.fileName).lowercase()
            when {
                text.contains("2160") || text.contains("4k") -> resSet.add("4K")
                text.contains("1440") || text.contains("2k") -> resSet.add("2K")
                text.contains("1080") -> resSet.add("1080p FHD")
                text.contains("720") -> resSet.add("HD")
            }
        }
        if (resSet.isEmpty()) resSet.add("HD")

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

        val audioSet = mutableSetOf<String>()
        versionItems.take(10).forEach { itm ->
            val text = (itm.qualityBadge + " " + itm.fileName).lowercase()
            if (text.contains("hin") || text.contains("hindi")) audioSet.add("Hindi")
            if (text.contains("eng") || text.contains("english")) audioSet.add("English")
            if (text.contains("tam") || text.contains("tamil")) audioSet.add("Tamil")
            if (text.contains("tel") || text.contains("telugu")) audioSet.add("Telugu")
            if (text.contains("jap") || text.contains("japanese")) audioSet.add("Japanese")
        }

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
            audioTracks = audioSet.toList(),
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "VERSIONS IN LIBRARY",
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF28103F))
                        .border(1.dp, Color(0xFF4C1D7C), RoundedCornerShape(999.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "${versions.size}",
                        color = Color(0xFFC084FC),
                        fontFamily = NdotFont,
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                text = "Detected OTT & Sources",
                color = Color(0xFF717482),
                fontFamily = FontFamily.Default,
                fontSize = 11.sp
            )
        }

        // Version Cards
        versions.forEach { version ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF14151C))
                    .border(
                        BorderStroke(1.dp, version.brandColor.copy(alpha = 0.28f)),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top row: Brand Badge + Tag + Total Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // OTT Brand Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(version.brandBgColor)
                                .border(
                                    BorderStroke(1.dp, version.brandColor.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = version.platformName,
                                color = version.brandColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Default,
                                letterSpacing = 0.3.sp
                            )
                        }

                        // Release Tag
                        Text(
                            text = version.releaseTag,
                            color = Color(0xFFA1A1AA),
                            fontFamily = NdotFont,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Total Size
                    Text(
                        text = version.formattedSize,
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Middle row: Specs Badges (4K, DV, HDR, etc.) + File count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        version.resolutions.forEach { res ->
                            NetflixAppleBadge(
                                text = res,
                                textColor = Color.White,
                                borderColor = Color(0x44FFFFFF),
                                bgColor = Color(0x22FFFFFF)
                            )
                        }
                        version.visualFeatures.forEach { feat ->
                            val isDv = feat == "DV"
                            NetflixAppleBadge(
                                text = feat,
                                textColor = if (isDv) Color(0xFFFBBF24) else Color(0xFFE4E4E7),
                                borderColor = if (isDv) Color(0x66FBBF24) else Color(0x35FFFFFF),
                                bgColor = if (isDv) Color(0x20FBBF24) else Color(0x18FFFFFF)
                            )
                        }
                    }

                    val countLabel = if (isSeries) {
                        if (version.fileCount == 1) "1 ep" else "${version.fileCount} eps"
                    } else {
                        if (version.fileCount == 1) "1 file" else "${version.fileCount} files"
                    }
                    Text(
                        text = countLabel,
                        color = Color(0xFF8E909A),
                        fontFamily = NdotFont,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Audio row (if detected for this version)
                if (version.audioTracks.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Audio:",
                            color = Color(0xFF717482),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Default
                        )
                        Text(
                            text = version.audioTracks.joinToString(", "),
                            color = Color(0xFFD4D4D8),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Default
                        )
                    }
                }

                // Sample filename snippet (gives release transparency)
                if (version.sampleFileName.isNotBlank()) {
                    Text(
                        text = version.sampleFileName,
                        color = Color(0xFF555765),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
