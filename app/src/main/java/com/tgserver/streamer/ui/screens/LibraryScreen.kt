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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material.icons.filled.Image
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
    val gridState = rememberLazyGridState()

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
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 84.dp)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Emil Kowalski Principle: Pressable media cards must have active scale feedback (0.965f)
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.962f else 1f,
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
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
    viewModel: MainAppViewModel,
    streamBaseUrl: String,
    onBack: () -> Unit,
    onCopyStream: (String) -> Unit,
    onRefreshMetadata: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isSeries = group.type == "series" || group.type == "anime"
    var showRescanDialog by remember { mutableStateOf(false) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
            .verticalScroll(rememberScrollState())
    ) {
        // ==================== 1. HERO BACKDROP & FLOATING GLASS BAR ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            // Immersive Backdrop Image
            val heroImageUrl = group.backdropUrl?.takeIf { it.isNotBlank() } ?: group.posterUrl
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

            // Top vignette for clear status & action buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
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
                                Color(0xFF070709).copy(alpha = 0.2f),
                                Color(0xFF070709).copy(alpha = 0.78f),
                                Color(0xFF070709)
                            )
                        )
                    )
            )

            // Floating Frosted Glass Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
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

            // Hero Center & Bottom: Logo / Title + Metadata Line
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
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
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ==================== 2. PRIMARY ACTION BUTTON: FIX METADATA MATCH ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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

        // ==================== 6. ABOUT & SERVER SPECIFICATIONS ====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "About",
                color = Color.White,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF101117),
                border = BorderStroke(0.5.dp, Color(0xFF22242F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: External IDs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("IMDb ID", color = Color(0xFF717482), fontSize = 11.sp)
                            Text(
                                text = group.imdbId ?: "N/A",
                                color = Color(0xFFFBBF24),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TMDb ID", color = Color(0xFF717482), fontSize = 11.sp)
                            Text(
                                text = group.tmdbId?.toString() ?: "N/A",
                                color = Color(0xFF60A5FA),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1F212A), thickness = 0.5.dp)

                    // Row 2: Media Specs & Total Storage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Audio / Languages", color = Color(0xFF717482), fontSize = 11.sp)
                            Text(
                                text = visualSpecs.audioLanguages.joinToString(", ").ifBlank { "Original" },
                                color = Color(0xFFE4E4E7),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Storage", color = Color(0xFF717482), fontSize = 11.sp)
                            Text(
                                text = visualSpecs.totalSize,
                                color = Color(0xFFC084FC),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1F212A), thickness = 0.5.dp)

                    // Row 3: Server Indexing Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${group.items.size} video files indexed",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "Type: ${group.type.uppercase()}",
                            color = Color(0xFFA855F7),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Bottom space for floating bottom bar clearance
        Spacer(modifier = Modifier.height(100.dp))
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
    var searchType by remember { mutableStateOf(group.type) } // "movie" or "series"
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
                    text = "Search TMDB / Cinemeta or enter IMDb ID (e.g. tt14173636) to re-sync correct title, poster, and stream mappings.",
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

                // Type Toggle: Switch between Movie and Series!
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
                        listOf("movie" to "🎬 Movie", "series" to "📺 Series").forEach { (typeKey, label) ->
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
                            text = "No matches found for \"$searchQuery\" in ${searchType.uppercase()}.\nTry entering an IMDb ID (e.g. tt1234567)",
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
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0x1FFFFFFF),
                    border = BorderStroke(0.5.dp, Color(0x33FFFFFF))
                ) {
                    Text(
                        text = "${versions.size}",
                        color = Color(0xFFD1D5DB),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
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
                            val is4k = res == "4K"
                            NetflixAppleBadge(
                                text = res,
                                textColor = if (is4k) Color.White else Color(0xFFE4E4E7),
                                borderColor = if (is4k) Color(0x55FFFFFF) else Color(0x35FFFFFF),
                                bgColor = if (is4k) Color(0x28FFFFFF) else Color(0x18FFFFFF)
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
