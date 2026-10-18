package com.tgserver.streamer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.R
import com.tgserver.streamer.data.model.MediaGroup
import com.tgserver.streamer.data.model.toMediaGroups
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.AppTab
import com.tgserver.streamer.ui.MainAppViewModel
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.tgserver.streamer.ui.components.AppUpdateBanner
import com.tgserver.streamer.ui.components.AppUpdateDialog
import com.tgserver.streamer.ui.components.TopBarUpdateBadge
import com.tgserver.streamer.update.AppReleaseInfo
import com.tgserver.streamer.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// Pixel Dot-Matrix Font (Exact match with Nothing / Canva design)
private val NdotFont = FontFamily(Font(R.font.ndot))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: MainAppViewModel) {
    val telemetry by viewModel.telemetry.collectAsState()
    val media by viewModel.media.collectAsState()
    val session by viewModel.session.collectAsState()
    val haptic = LocalHapticFeedback.current

    var copiedRecently by remember { mutableStateOf(false) }
    var isAwakening by remember { mutableStateOf(false) }
    var awakeningDots by remember { mutableStateOf(".") }

    // Reset copied flag after 2.5s
    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(2500)
            copiedRecently = false
        }
    }

    // Refresh user profile & avatar when logged in
    LaunchedEffect(session.isLoggedIn) {
        if (session.isLoggedIn) {
            viewModel.refreshUserProfile()
        }
    }

    // Awakening dot animation loop
    LaunchedEffect(isAwakening) {
        if (isAwakening) {
            var dotCount = 1
            while (isAwakening) {
                delay(350)
                dotCount = (dotCount % 3) + 1
                awakeningDots = ".".repeat(dotCount)
            }
        }
    }

    // Clear awakening state once server confirms running
    LaunchedEffect(telemetry.isRunning) {
        if (telemetry.isRunning) {
            isAwakening = false
        }
    }

    // In-App Updater state
    val updateState by viewModel.updateState.collectAsState()
    val isUpdateDismissed by viewModel.isUpdateDismissed.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Library counts
    val moviesCount = remember(media) {
        media.count { it.type.equals("movie", ignoreCase = true) }
    }
    val showsCount = remember(media) {
        media.filter { it.type.equals("series", ignoreCase = true) }
            .map { it.title.trim() }
            .distinct()
            .size
    }
    val episodesCount = remember(media) {
        media.count { it.type.equals("series", ignoreCase = true) }
    }
    val contentFilesCount = remember(media) {
        media.sumOf { if (it.isSplit && it.parts.isNotEmpty()) it.parts.size else 1 }
    }
    val totalServerSizeBytes = remember(media) {
        media.sumOf { it.sizeBytes }
    }
    val totalServerSizeFormatted = remember(totalServerSizeBytes) {
        if (totalServerSizeBytes <= 0L) "0.0 GB"
        else {
            val tb = totalServerSizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0 * 1024.0)
            if (tb >= 1.0) {
                String.format(Locale.US, "%.1fTB", tb)
            } else {
                val gb = totalServerSizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                String.format(Locale.US, "%.1fGB", gb)
            }
        }
    }

    // Recently Added media groups (newest 15)
    val recentlyAdded = remember(media) {
        media.toMediaGroups()
            .sortedByDescending { group ->
                val date = group.dateAdded.takeIf { it > 0L }
                date ?: (group.items.maxOfOrNull { it.messageId }?.toLong() ?: 0L)
            }
            .take(15)
    }

    // Stream URL
    val serverUrl = remember(telemetry) {
        if (telemetry.localIp.isNotBlank() && telemetry.port > 0) {
            "http://${telemetry.localIp}:${telemetry.port}"
        } else if (telemetry.manifestUrl.isNotBlank()) {
            telemetry.manifestUrl.removeSuffix("/manifest.json")
        } else {
            "http://127.0.0.1:7788"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // TOP APP BAR (Nothing OS / Dot-Matrix Minimalist with Professional Status Bar Clearance)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sleek, proportional NoxMedia logo icon (26dp)
                Image(
                    painter = painterResource(id = R.drawable.noxmedia_icon_trans),
                    contentDescription = "NoxMedia Logo",
                    modifier = Modifier.size(26.dp),
                    contentScale = ContentScale.Fit
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "NoxMedia",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = NdotFont,
                        letterSpacing = 1.sp
                    )

                    val displayName = if (session.firstName.isNotBlank()) session.firstName else "Name"
                    val usernameDisplay = if (session.username.isNotBlank()) "@${session.username}" else "@USERNAME"
                    Text(
                        text = "$displayName . $usernameDisplay",
                        color = Color(0xFF7E8088),
                        fontSize = 11.5.sp,
                        fontFamily = NdotFont,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Top Bar Right Action: Update Badge only when update is available (avatar removed as requested)
            val updateInfo = when (val s = updateState) {
                is UpdateUiState.Available -> s.info
                is UpdateUiState.Downloading -> AppReleaseInfo(s.fileName, "", "", "", "", s.fileName, s.totalBytes, "", true)
                is UpdateUiState.ReadyToInstall -> s.info
                else -> null
            }

            if (updateInfo != null) {
                TopBarUpdateBadge(
                    versionTag = updateInfo.versionTag.ifBlank { "NEW" },
                    onClick = { showUpdateDialog = true }
                )
            }
        }

        // IN-APP UPDATE PROMPT BANNER
        val activeUpdate = when (val s = updateState) {
            is UpdateUiState.Available -> s.info
            is UpdateUiState.Downloading -> AppReleaseInfo("NEW", "Update", "", "", "", s.fileName, s.totalBytes, "Compatible", true)
            is UpdateUiState.ReadyToInstall -> s.info
            else -> null
        }

        if (activeUpdate != null && !isUpdateDismissed) {
            AppUpdateBanner(
                info = activeUpdate,
                state = updateState,
                onActionClick = {
                    if (updateState is UpdateUiState.ReadyToInstall) {
                        viewModel.installDownloadedApk((updateState as UpdateUiState.ReadyToInstall).apkFile)
                    } else if (updateState is UpdateUiState.Available) {
                        viewModel.startDownloadUpdate((updateState as UpdateUiState.Available).info)
                    } else {
                        showUpdateDialog = true
                    }
                },
                onDismiss = { viewModel.dismissUpdateBanner() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }

        // HERO CARD: SERVER STATE (Sleep / Awakening / Awaked)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(1.dp, Color(0xFF282A31)),
                    shape = RoundedCornerShape(20.dp)
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!telemetry.isRunning && !isAwakening) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isAwakening = true
                            viewModel.toggleServer()
                        }
                    },
                    onLongClick = {
                        if (telemetry.isRunning) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleServer()
                        }
                    }
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F24))
        ) {
            AnimatedContent(
                targetState = telemetry.isRunning,
                transitionSpec = {
                    (fadeIn(tween(300)) + expandVertically(tween(350))) togetherWith
                            (fadeOut(tween(250)) + shrinkVertically(tween(300)))
                },
                label = "server_state_animation"
            ) { isRunning ->
                if (!isRunning) {
                    // SLEEP MODE VIEW (Pure, sleek, centered)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(156.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isAwakening) "Server Awakening$awakeningDots" else "In Sleep mode",
                            color = if (isAwakening) Color(0xFFE2E8F0) else Color(0xFFA0A2AC),
                            fontFamily = NdotFont,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    // SERVER AWAKED VIEW (Stats + Integrated Lower Shelf)
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Upper Section: Title + 3 Stats
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 18.dp)
                        ) {
                            // Top Row: "Server Awaked" + subtle sleep button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Server Awaked",
                                    color = Color(0xFFD4D4D8),
                                    fontFamily = NdotFont,
                                    fontSize = 13.5.sp
                                )

                                Text(
                                    text = "sleep",
                                    color = Color(0xFF6B6E7B),
                                    fontFamily = NdotFont,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleServer()
                                        }
                                        .padding(4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 2 Large Prominent Metrics: Uptime & Speed
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ServerLargeMetricItem(
                                    value = telemetry.uptimeFormatted,
                                    label = "uptime",
                                    modifier = Modifier.weight(1f)
                                )
                                ServerLargeMetricItem(
                                    value = telemetry.liveSpeedFormatted,
                                    label = "speed",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Secondary Traffic Details Shelf: Session & All-Time
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .background(Color(0xFF131418), shape = RoundedCornerShape(10.dp))
                                    .border(BorderStroke(0.8.dp, Color(0xFF26272E)), shape = RoundedCornerShape(10.dp))
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "session",
                                        color = Color(0xFF71747F),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = telemetry.sessionDataFormatted,
                                        color = Color(0xFFD4D4D8),
                                        fontFamily = NdotFont,
                                        fontSize = 11.5.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .background(Color(0xFF3F414D), shape = CircleShape)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "all-time",
                                        color = Color(0xFF71747F),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = telemetry.allTimeDataFormatted,
                                        color = Color(0xFFD4D4D8),
                                        fontFamily = NdotFont,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }

                        // Lower Shelf: Stream URL
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF131418))
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF26272E),
                                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.copyToClipboard(serverUrl, "URL copied")
                                    copiedRecently = true
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "URL: $serverUrl",
                                    color = Color(0xFFA6A8B2),
                                    fontFamily = NdotFont,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (copiedRecently) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "copied",
                                        color = Color(0xFF34D399),
                                        fontFamily = NdotFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // LIBRARY CARD (Deep Cosmic Purple with Lower Shelf)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(1.dp, Color(0xFF3C1460)),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.selectTab(AppTab.LIBRARY)
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18072B))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Upper Section: "Library" + 4 Stats
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 18.dp)
                ) {
                    Text(
                        text = "Library",
                        color = Color(0xFFC084FC),
                        fontFamily = NdotFont,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4 Stats Columns in a single balanced row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LibraryStatItem(
                            value = "$moviesCount",
                            label = "Movies",
                            modifier = Modifier.weight(1f)
                        )
                        LibraryStatItem(
                            value = "$showsCount",
                            label = "Shows",
                            modifier = Modifier.weight(1f)
                        )
                        LibraryStatItem(
                            value = "$episodesCount",
                            label = "Episodes",
                            modifier = Modifier.weight(1.05f)
                        )
                        LibraryStatItem(
                            value = "$contentFilesCount",
                            label = "Content Files",
                            modifier = Modifier.weight(1.35f)
                        )
                    }
                }

                // Lower Shelf: Total Server Size
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F031C))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF2C0D48),
                            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Total Server Size: $totalServerSizeFormatted",
                        color = Color(0xFFA855F7),
                        fontFamily = NdotFont,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // RECENTLY ADDED SECTION (Edge-to-Edge Full Width Horizontal Row of Newest Media)
        if (recentlyAdded.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recently Added",
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 14.sp,
                        letterSpacing = 0.8.sp
                    )

                    Text(
                        text = "view all →",
                        color = Color(0xFFA855F7),
                        fontFamily = NdotFont,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.selectTab(AppTab.LIBRARY)
                            }
                            .padding(4.dp)
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    items(recentlyAdded, key = { it.key }) { group ->
                        RecentMediaCard(
                            group = group,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openMediaDetails(group.key)
                            }
                        )
                    }
                }
            }
        }

        // Bottom clearance for floating liquid navbar
        Spacer(modifier = Modifier.height(72.dp))
    }

    // App Update Dialog with Changelog & Download progress
    val dialogInfo = when (val s = updateState) {
        is UpdateUiState.Available -> s.info
        is UpdateUiState.ReadyToInstall -> s.info
        else -> null
    }

    if (showUpdateDialog && dialogInfo != null) {
        AppUpdateDialog(
            info = dialogInfo,
            state = updateState,
            currentVersion = viewModel.getAppVersion(),
            onDownloadClick = { viewModel.startDownloadUpdate(dialogInfo) },
            onInstallClick = { file -> viewModel.installDownloadedApk(file) },
            onDismiss = { showUpdateDialog = false }
        )
    }
}

@Composable
private fun RecentMediaCard(
    group: MediaGroup,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(114.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(114.dp)
                .height(171.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(1.dp, Color(0xFF282A31)), RoundedCornerShape(14.dp))
        ) {
            RemoteImage(
                url = group.posterUrl,
                contentDescription = group.title,
                isSeries = group.type == "series" || group.type == "anime",
                modifier = Modifier.fillMaxSize()
            )

            // Episode count badge if series/anime
            if (group.type == "series" || group.type == "anime") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF6B21A8).copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${group.distinctEpisodeCount} EPS",
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 8.5.sp
                    )
                }
            }
        }

        // Title
        Text(
            text = group.title,
            color = Color.White,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Year · Size
        val yearText = group.year?.takeIf { it.isNotBlank() } ?: "2026"
        Text(
            text = "$yearText · ${group.formattedTotalSize}",
            color = Color(0xFFA855F7),
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ServerLargeMetricItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            color = Color.White,
            fontFamily = NdotFont,
            fontSize = 24.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color(0xFF8E909A),
            fontFamily = NdotFont,
            fontSize = 10.5.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProfileAvatar(
    photoPath: String?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(photoPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoPath) {
        if (photoPath.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val clean = photoPath.removePrefix("file://")
                val file = File(clean)
                if (file.exists() && file.length() > 0) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            bitmap = bmp.asImageBitmap()
                        }
                        return@withContext
                    }
                }
                if (photoPath.startsWith("http://") || photoPath.startsWith("https://")) {
                    val req = okhttp3.Request.Builder()
                        .url(photoPath)
                        .header("User-Agent", "NoxMedia/1.0 (Android)")
                        .build()
                    val client = okhttp3.OkHttpClient()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                if (bmp != null) {
                                    withContext(Dispatchers.Main) {
                                        bitmap = bmp.asImageBitmap()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    if (bitmap != null) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .border(1.5.dp, Color(0x66FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "Profile Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        ConcentricRingEmblem(modifier = modifier)
    }
}

@Composable
private fun LibraryStatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            color = Color.White,
            fontFamily = NdotFont,
            fontSize = 20.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color(0xFFC084FC),
            fontFamily = NdotFont,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConcentricRingEmblem(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.5.dp, Color(0x40FFFFFF), CircleShape)
            .padding(4.dp)
            .border(1.5.dp, Color(0x99FFFFFF), CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(Color(0xFF0D0E12)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val radius = size.minDimension / 2f
            drawCircle(
                color = Color(0x35FFFFFF),
                radius = radius * 0.75f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0x70FFFFFF),
                radius = radius * 0.4f,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFFFFFFF),
                radius = 1.8.dp.toPx()
            )
        }
    }
}
