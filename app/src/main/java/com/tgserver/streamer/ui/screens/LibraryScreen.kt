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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
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
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import kotlinx.coroutines.Dispatchers
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
)

@Composable
fun LibraryScreen(viewModel: MainAppViewModel) {
    val allMedia by viewModel.media.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val haptic = LocalHapticFeedback.current

    var selectedGroup by remember { mutableStateOf<MediaGroup?>(null) }

    // Intercept hardware / gesture back navigation when inside details view
    BackHandler(enabled = selectedGroup != null) {
        selectedGroup = null
    }

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
                qualityBadge = bestQuality,
                items = items.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 1 }))
            )
        }.sortedBy { it.title.lowercase() }
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
                    streamBaseUrl = streamBaseUrl,
                    onBack = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedGroup = null
                    },
                    onCopyStream = { url ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.copyToClipboard(url, "Stream link copied")
                    },
                    onRefreshMetadata = {
                        val firstItem = currentGroup.items.firstOrNull()
                        if (firstItem != null) {
                            viewModel.refreshMediaMetadata(firstItem)
                        }
                    }
                )
            } else {
                // MAIN POSTER GRID LIBRARY VIEW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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

                    // SEARCH INPUT
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(
                                text = "Search title, IMDb, resolution...",
                                color = Color(0xFF6B6E7B),
                                fontSize = 12.5.sp,
                                fontFamily = NdotFont
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF888B98),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF888B98),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFA855F7),
                            unfocusedBorderColor = Color(0xFF25262F),
                            focusedContainerColor = Color(0xFF14151B),
                            unfocusedContainerColor = Color(0xFF14151B)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

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
                                        selectedGroup = group
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
 * Minimalist Poster Grid Item (Aspect 2:3 with Title & Year/Type)
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
        // Poster with badges
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

            // Quality Pill (Top Right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = group.qualityBadge,
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 9.sp
                )
            }

            // Episode Count if Series (Bottom Left)
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
                        text = "${group.items.size} EPS",
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Title
        Text(
            text = group.title,
            color = Color.White,
            fontFamily = NdotFont,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Year · Type
        val yearText = group.year?.takeIf { it.isNotBlank() } ?: "2026"
        Text(
            text = "$yearText · ${group.type.uppercase()}",
            color = Color(0xFFA855F7),
            fontFamily = NdotFont,
            fontSize = 10.5.sp,
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
    streamBaseUrl: String,
    onBack: () -> Unit,
    onCopyStream: (String) -> Unit,
    onRefreshMetadata: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isSeries = group.type == "series" || group.type == "anime"

    // Group episodes by Season for series
    val seasonMap = remember(group.items) {
        if (isSeries) {
            group.items.groupBy { it.season ?: 1 }
        } else {
            emptyMap()
        }
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

                Text(
                    text = group.title,
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

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
                Text(
                    text = group.title,
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Meta row: Year · Duration / Quality
                val year = group.year?.takeIf { it.isNotBlank() } ?: "2026"
                Text(
                    text = "$year · ${group.qualityBadge} · ★ 8.2",
                    color = Color(0xFFA1A1AA),
                    fontFamily = NdotFont,
                    fontSize = 11.5.sp
                )

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
            // MOVIE STREAMS
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
                    val encodedName = try {
                        URLEncoder.encode(streamItem.fileName.ifBlank { "movie.mp4" }, "UTF-8").replace("+", "%20")
                    } catch (_: Exception) {
                        "movie.mp4"
                    }
                    val streamUrl = "$streamBaseUrl/stream/video/${streamItem.channelId}/${streamItem.messageId}/$encodedName"

                    StreamItemRow(
                        item = streamItem,
                        streamUrl = streamUrl,
                        onCopy = { onCopyStream(streamUrl) },
                        onPlay = {
                            playInExternalPlayer(context, streamUrl)
                        }
                    )
                }
            }
        } else {
            // SERIES SEASONS & EPISODES (Only existing episodes from user library)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${seasonMap.size} SEASON · ${group.items.size} EPISODES",
                    color = Color(0xFF8E909A),
                    fontFamily = NdotFont,
                    fontSize = 11.5.sp
                )

                seasonMap.forEach { (seasonNum, episodes) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF14151B))
                            .border(1.dp, Color(0xFF262832), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Season Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Season $seasonNum",
                                color = Color.White,
                                fontFamily = NdotFont,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${episodes.size} eps",
                                color = Color(0xFF8E909A),
                                fontFamily = NdotFont,
                                fontSize = 11.sp
                            )
                        }

                        HorizontalDivider(color = Color(0xFF22242D), thickness = 1.dp)

                        // Episodes in library
                        episodes.forEach { ep ->
                            val encodedName = try {
                                URLEncoder.encode(ep.fileName.ifBlank { "episode.mp4" }, "UTF-8").replace("+", "%20")
                            } catch (_: Exception) {
                                "episode.mp4"
                            }
                            val streamUrl = "$streamBaseUrl/stream/video/${ep.channelId}/${ep.messageId}/$encodedName"

                            EpisodeItemRow(
                                episode = ep,
                                streamUrl = streamUrl,
                                onCopy = { onCopyStream(streamUrl) },
                                onPlay = {
                                    playInExternalPlayer(context, streamUrl)
                                }
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
 * Stream item row for Movies
 */
@Composable
private fun StreamItemRow(
    item: MediaItem,
    streamUrl: String,
    onCopy: () -> Unit,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF14151B))
            .border(1.dp, Color(0xFF262832), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Resolution Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2E1C0A))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.qualityBadge,
                        color = Color(0xFFFBBF24),
                        fontFamily = NdotFont,
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = item.fileName.ifBlank { item.title },
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.formattedSize} ${if (item.isSplit) "· split" else ""}",
                    color = Color(0xFF888A96),
                    fontFamily = NdotFont,
                    fontSize = 10.5.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Copy URL
                    Button(
                        onClick = onCopy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2129)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "COPY",
                                color = Color.White,
                                fontFamily = NdotFont,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    // Play Stream
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "PLAY",
                                color = Color.White,
                                fontFamily = NdotFont,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Episode item row for Series
 */
@Composable
private fun EpisodeItemRow(
    episode: MediaItem,
    streamUrl: String,
    onCopy: () -> Unit,
    onPlay: () -> Unit
) {
    val epNumber = episode.episode ?: 1
    val epTitle = episode.episodeTitle?.takeIf { it.isNotBlank() } ?: episode.title

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "E%02d".format(epNumber),
                    color = Color(0xFFFBBF24),
                    fontFamily = NdotFont,
                    fontSize = 13.sp
                )

                Column {
                    Text(
                        text = epTitle,
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "1 stream · ${episode.qualityBadge} · ${episode.formattedSize}",
                        color = Color(0xFF888A96),
                        fontFamily = NdotFont,
                        fontSize = 10.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy URL",
                        tint = Color(0xFFC4C6D0),
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2D1645))
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFFD8B4FE),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Launch external video player (VLC, MX Player, Just Player, etc.) via Android Intent
 */
private fun playInExternalPlayer(context: Context, streamUrl: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(streamUrl), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Play stream with..."))
    } catch (_: Exception) {
    }
}
