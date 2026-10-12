package com.tgserver.streamer.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.metadata.EnrichedMetadata
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import kotlinx.coroutines.Dispatchers
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
private object ImageMemoryCache {
    private val cache = LruCache<String, ImageBitmap>(120)
    fun get(url: String): ImageBitmap? = cache.get(url)
    fun put(url: String, bmp: ImageBitmap) {
        cache.put(url, bmp)
    }
}

private val imageHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    isSeries: Boolean = false
) {
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
                    .header("User-Agent", "TG-Server/1.0 (Android)")
                    .build()
                imageHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { stream ->
                            val decoded = BitmapFactory.decodeStream(stream)
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

/**
 * Shorten full language names to clean 3-letter codes for minimal, sleek UI display.
 */
private fun formatShortBadge(badge: String): String {
    if (badge.isBlank()) return badge
    return badge
        .replace("English", "Eng", ignoreCase = true)
        .replace("Hindi", "Hin", ignoreCase = true)
        .replace("Kannada", "Kan", ignoreCase = true)
        .replace("Malayalam", "Mal", ignoreCase = true)
        .replace("Tamil", "Tam", ignoreCase = true)
        .replace("Telugu", "Tel", ignoreCase = true)
        .replace("Japanese", "Jap", ignoreCase = true)
        .replace("Korean", "Kor", ignoreCase = true)
        .replace("Spanish", "Spa", ignoreCase = true)
        .replace("French", "Fre", ignoreCase = true)
        .replace("German", "Ger", ignoreCase = true)
        .replace("Italian", "Ita", ignoreCase = true)
        .replace("Russian", "Rus", ignoreCase = true)
        .replace("Chinese", "Chi", ignoreCase = true)
        .replace("Multi Audio", "Multi", ignoreCase = true)
        .replace("Dual Audio", "Dual", ignoreCase = true)
}

/**
 * Grouped media entity representing a single Movie or TV Show title
 */
data class MediaGroup(
    val key: String,
    val title: String,
    val type: String, // "movie", "series", "anime"
    val year: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val imdbId: String?,
    val tmdbId: Int?,
    val qualityBadge: String,
    val items: List<MediaItem>
) {
    val distinctEpisodeCount: Int
        get() = items.map { Pair(it.season ?: 1, it.episode ?: 1) }.distinct().size
}

@Composable
fun LibraryScreen(viewModel: MainAppViewModel) {
    val allMedia by viewModel.media.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Group raw files into distinct titles (Movies and Series entities)
    val mediaGroups = remember(allMedia) {
        allMedia.groupBy { item ->
            val cleanTitle = item.title.trim().lowercase()
            when {
                item.tmdbId != null && item.tmdbId!! > 0 -> "tmdb_${item.type}_${item.tmdbId}"
                !item.imdbId.isNullOrBlank() -> "imdb_${item.imdbId}"
                else -> "title_${item.type}_$cleanTitle"
            }
        }.map { (key, items) ->
            val primary = items.first()
            val bestPoster = items.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl ?: primary.posterUrl
            val bestBackdrop = items.firstOrNull { !it.backdropUrl.isNullOrBlank() }?.backdropUrl ?: primary.backdropUrl
            val bestOverview = items.firstOrNull { !it.overview.isNullOrBlank() }?.overview ?: primary.overview
            val bestYear = items.firstOrNull { !it.year.isNullOrBlank() }?.year ?: primary.year
            val bestQuality = items.maxByOrNull {
                when {
                    it.quality.contains("4K", ignoreCase = true) || it.quality.contains("2160", ignoreCase = true) -> 4
                    it.quality.contains("1080", ignoreCase = true) -> 3
                    it.quality.contains("720", ignoreCase = true) -> 2
                    else -> 1
                }
            }?.qualityBadge ?: primary.qualityBadge

            MediaGroup(
                key = key,
                title = primary.title,
                type = primary.type,
                year = bestYear,
                posterUrl = bestPoster,
                backdropUrl = bestBackdrop,
                overview = bestOverview,
                imdbId = primary.imdbId,
                tmdbId = primary.tmdbId,
                qualityBadge = formatShortBadge(bestQuality),
                items = items.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 1 }))
            )
        }.sortedBy { it.title.lowercase() }
    }

    var selectedGroupKey by remember { mutableStateOf<String?>(null) }
    val selectedGroup = mediaGroups.find { it.key == selectedGroupKey }

    // Intercept hardware / gesture back navigation when inside details view
    BackHandler(enabled = selectedGroup != null) {
        selectedGroupKey = null
    }

    // Filter media groups by category & search
    val filteredGroups = remember(mediaGroups, searchQuery, selectedCategory) {
        mediaGroups.filter { group ->
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
                    },
                    onCopyStream = { url ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.copyToClipboard(url, "Stream link copied")
                    },
                    onRefreshMetadata = {
                        val firstItem = currentGroup.items.firstOrNull()
                        if (firstItem != null) {
                            viewModel.refreshMediaMetadata(firstItem)
                            viewModel.syncChannel(firstItem.channelId)
                        }
                    }
                )
            } else {
                // MAIN POSTER GRID LIBRARY VIEW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
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

                    // MINIMAL & COMPACT SEARCH BAR (42dp height, sleek border)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF14151B))
                            .border(1.dp, Color(0xFF25262F), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
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
                                tint = Color(0xFF888B98),
                                modifier = Modifier.size(16.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search title, IMDb, resolution...",
                                        color = Color(0xFF6B6E7B),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Default
                                    )
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

                    // CATEGORY CHIPS
                    val categories = listOf(
                        "all" to "ALL",
                        "movies" to "MOVIES",
                        "series" to "SERIES",
                        "anime" to "ANIME",
                        "4k" to "4K UHD"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { (id, label) ->
                            val isSelected = selectedCategory == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFFA855F7) else Color(0xFF15161D))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFFA855F7) else Color(0xFF262832),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setSelectedCategory(id)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFF8E909A),
                                    fontSize = 11.sp,
                                    fontFamily = NdotFont,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // POSTER GRID
                    if (filteredGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
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
                            contentPadding = PaddingValues(bottom = 84.dp)
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

        // Year · Type
        val yearText = group.year?.takeIf { it.isNotBlank() } ?: "2026"
        Text(
            text = "$yearText · ${group.type.uppercase()}",
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
        // TOP BACKDROP WITH APP BAR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
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

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 12.dp, end = 12.dp),
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

                // Meta row: Year · Quality · Rating
                val year = group.year?.takeIf { it.isNotBlank() } ?: "2026"
                Text(
                    text = "$year · ${formatShortBadge(group.qualityBadge)} · ★ 8.2",
                    color = Color(0xFFA1A1AA),
                    fontFamily = NdotFont,
                    fontSize = 11.5.sp
                )

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
