package com.tgserver.streamer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
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
import com.tgserver.streamer.R
import com.tgserver.streamer.data.model.ChannelInfo
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.ScanProgressState
import com.tgserver.streamer.data.model.UnmatchedItem
import com.tgserver.streamer.metadata.EnrichedMetadata
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Pixel Dot-Matrix Font (Exact match with HomeScreen / LibraryScreen design)
private val NdotFont = FontFamily(Font(R.font.ndot))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(viewModel: MainAppViewModel) {
    val channels by viewModel.channels.collectAsState()
    val mediaItems by viewModel.media.collectAsState()
    val isAddingChannel by viewModel.isAddingChannel.collectAsState()
    val scanningChannelIds by viewModel.scanningChannelIds.collectAsState()
    val isScanningAll by viewModel.isScanningAll.collectAsState()
    val unmatchedMedia by viewModel.unmatchedMedia.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val config by viewModel.config.collectAsState()
    val haptic = LocalHapticFeedback.current

    var channelInput by remember { mutableStateOf("") }
    var isAnimeChannel by remember { mutableStateOf(false) }
    var showAnimeInfo by remember { mutableStateOf(false) }
    var showUnmatchedSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 100.dp)
    ) {
        // 1. TOP HEADER (Channels / NoxMedia • Telegram)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Channels",
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "NoxMedia • Telegram",
                    color = Color(0xFF6B7280),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.2.sp
                )
            }
        }

        // 2. CONNECT TELEGRAM CHANNEL CARD
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF22232A), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Connect Telegram Channel",
                        color = Color(0xFFE5E7EB),
                        fontFamily = NdotFont,
                        fontSize = 12.5.sp,
                        letterSpacing = 0.3.sp
                    )

                    // Inset dark text field
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C0D10))
                            .border(1.dp, Color(0xFF1E2028), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (channelInput.isEmpty()) {
                                    Text(
                                        text = "channel username or id (-100******",
                                        color = Color(0xFF4B5563),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                BasicTextField(
                                    value = channelInput,
                                    onValueChange = { channelInput = it },
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color.White),
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (channelInput.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { channelInput = "" }
                                )
                            }
                        }
                    }

                    // Options & Action Row (Mark as Anime Channel radio dot + info icon + Add pill button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Radio dot toggle + Info icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isAnimeChannel = !isAnimeChannel
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clip(CircleShape)
                                        .background(if (isAnimeChannel) Color(0xFF10B981) else Color.Transparent)
                                        .border(
                                            1.5.dp,
                                            if (isAnimeChannel) Color(0xFF10B981) else Color(0xFF4B5563),
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = "Mark as Anime Channel",
                                    color = if (isAnimeChannel) Color.White else Color(0xFF9CA3AF),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Anime Parser Details",
                                tint = if (showAnimeInfo) Color.White else Color(0xFF6B7280),
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(CircleShape)
                                    .clickable { showAnimeInfo = !showAnimeInfo }
                            )
                        }

                        // Add pill button
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (channelInput.isNotBlank() && !isAddingChannel)
                                        Color(0xFF252730)
                                    else
                                        Color(0xFF191A20)
                                )
                                .border(
                                    1.dp,
                                    if (channelInput.isNotBlank() && !isAddingChannel)
                                        Color(0xFF424555)
                                    else
                                        Color(0xFF2A2C38),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable(enabled = channelInput.isNotBlank() && !isAddingChannel) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.addChannel(channelInput.trim(), isAnimeChannel) { success, _ ->
                                        if (success) {
                                            channelInput = ""
                                            isAnimeChannel = false
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isAddingChannel) {
                                    CircularProgressIndicator(
                                        strokeWidth = 1.5.dp,
                                        color = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Adding..",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "Add",
                                        color = if (channelInput.isNotBlank()) Color.White else Color(0xFF6B7280),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Expandable Info Box (same design language as Settings)
                    AnimatedVisibility(
                        visible = showAnimeInfo,
                        enter = fadeIn(tween(150)) + expandVertically(),
                        exit = fadeOut(tween(120)) + shrinkVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF161820))
                                .border(1.dp, Color(0xFF242732), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Anime channels use the Kitsu database sorted by absolute episode numbering (e.g. One Piece 1131) instead of standard Season/Episode format.",
                                color = Color(0xFFA0A3B0),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. YOUR CONNECTS CONTAINER CARD
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF22232A), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header row: Your Connects (count) + Sync All action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Connects (${channels.size})",
                            color = Color(0xFFE5E7EB),
                            fontFamily = NdotFont,
                            fontSize = 12.5.sp,
                            letterSpacing = 0.3.sp
                        )

                        if (channels.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isScanningAll) Color(0xFF1F2920) else Color(0xFF1B1C22))
                                    .border(
                                        1.dp,
                                        if (isScanningAll) Color(0xFF10B981) else Color(0xFF2E303D),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = !isScanningAll && scanningChannelIds.isEmpty()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.syncAllChannels()
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isScanningAll) {
                                        CircularProgressIndicator(
                                            strokeWidth = 1.5.dp,
                                            color = Color(0xFF10B981),
                                            modifier = Modifier.size(9.dp)
                                        )
                                        Text(
                                            text = "Syncing..",
                                            color = Color(0xFF10B981),
                                            fontFamily = NdotFont,
                                            fontSize = 8.5.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = Color(0xFF9CA3AF),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Sync All",
                                            color = Color(0xFFD1D5DB),
                                            fontFamily = NdotFont,
                                            fontSize = 8.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (channels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0C0D10))
                                .border(1.dp, Color(0xFF1E2028), RoundedCornerShape(12.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No channels connected yet. Add one above to begin indexing.",
                                color = Color(0xFF6B7280),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            channels.forEach { channel ->
                                val isScanning = channel.id in scanningChannelIds
                                ChannelItemCard(
                                    channel = channel,
                                    mediaItems = mediaItems,
                                    isScanning = isScanning,
                                    progress = scanProgress[channel.id],
                                    viewModel = viewModel,
                                    onScan = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.syncChannel(channel.id)
                                    },
                                    onStopScan = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.stopScan(channel.id)
                                    },
                                    onRescanFromScratch = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.rescanFromScratch(channel.id)
                                    },
                                    onToggleAnime = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleAnimeChannel(channel.id)
                                    },
                                    onDelete = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.removeChannel(channel.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. UNSUPPORTED CONTENT CONTAINER CARD
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF22232A), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header Row: Unsupported Content (count) + View button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unsupported Content (${unmatchedMedia.size})",
                            color = Color(0xFFE5E7EB),
                            fontFamily = NdotFont,
                            fontSize = 12.5.sp,
                            letterSpacing = 0.3.sp
                        )

                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1B1C22))
                                .border(1.dp, Color(0xFF2E303D), RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showUnmatchedSheet = true
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "View",
                                color = Color(0xFFE5E7EB),
                                fontFamily = NdotFont,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Inner list preview card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C0D10))
                            .border(1.dp, Color(0xFF1E2028), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (unmatchedMedia.isEmpty()) {
                            Text(
                                text = "All content indexed successfully. No unmatched items.",
                                color = Color(0xFF6B7280),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                unmatchedMedia.take(3).forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.fileName,
                                            color = Color(0xFFD1D5DB),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Size badge
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF17181F))
                                                    .border(1.dp, Color(0xFF282A35), RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = item.formattedSize,
                                                    color = Color(0xFF9CA3AF),
                                                    fontSize = 8.5.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }

                                            // Red No Match badge
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF1E1114))
                                                    .border(
                                                        1.dp,
                                                        Color(0xFFEF4444).copy(alpha = 0.8f),
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "No Match",
                                                    color = Color(0xFFEF4444),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Medium
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
        }
    }

    if (showUnmatchedSheet) {
        UnmatchedContentBottomSheet(
            viewModel = viewModel,
            unmatchedItems = unmatchedMedia,
            port = config.port,
            onDismiss = { showUnmatchedSheet = false }
        )
    }
}

/**
 * Minimal, polished Channel Card matching user mockup
 */
@Composable
private fun ChannelItemCard(
    channel: ChannelInfo,
    mediaItems: List<MediaItem>,
    isScanning: Boolean,
    progress: ScanProgressState? = null,
    viewModel: MainAppViewModel,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onRescanFromScratch: () -> Unit,
    onToggleAnime: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Media stats calculation: Movies | Shows - Episodes
    val channelMedia = remember(mediaItems, channel.id) {
        mediaItems.filter { it.channelId == channel.id }
    }
    val movieCount = remember(channelMedia) {
        channelMedia.count { it.type == "movie" }
    }
    val seriesItems = remember(channelMedia) {
        channelMedia.filter { it.type == "series" }
    }
    val showCount = remember(seriesItems) {
        seriesItems.map { it.title.lowercase().trim() }.distinct().size
    }
    val episodeCount = seriesItems.size

    val statsText = remember(movieCount, showCount, episodeCount, channel.videoCount) {
        when {
            movieCount > 0 && showCount > 0 -> {
                val movieStr = if (movieCount == 1) "1 Movie" else "$movieCount Movies"
                val showStr = if (showCount == 1) "1 Show" else "$showCount Shows"
                val epStr = if (episodeCount == 1) "1 Episode" else "$episodeCount Episodes"
                "$movieStr  •  $showStr  •  $epStr"
            }
            movieCount > 0 -> if (movieCount == 1) "1 Movie" else "$movieCount Movies"
            showCount > 0 -> {
                val showStr = if (showCount == 1) "1 Show" else "$showCount Shows"
                val epStr = if (episodeCount == 1) "1 Episode" else "$episodeCount Episodes"
                "$showStr  •  $epStr"
            }
            channel.videoCount > 0 -> "${channel.videoCount} Media Files"
            else -> "0 Media Files"
        }
    }

    // Relative scan time
    val relativeScanTime = remember(channel.lastSyncTime) {
        formatRelativeScanTime(channel.lastSyncTime)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1B1C22))
            .border(
                1.dp,
                if (isScanning) Color(0xFF10B981).copy(alpha = 0.7f) else Color(0xFF282A34),
                RoundedCornerShape(14.dp)
            )
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // TOP ROW: Avatar + Channel Info + Badges + Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channel Avatar (Fetched dynamically or loaded from local cache)
                        ChannelAvatar(
                            channel = channel,
                            viewModel = viewModel,
                            isScanning = isScanning
                        )

                        // Title & Handle Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = channel.title,
                                    color = Color.White,
                                    fontFamily = NdotFont,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (channel.isAnime) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF242630))
                                            .border(1.dp, Color(0xFF404455), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "Anime",
                                            color = Color(0xFFE5E7EB),
                                            fontFamily = NdotFont,
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }

                            val handle = channel.username ?: channel.id.toString()
                            val displayHandle = when {
                                handle.startsWith("-100") -> "Channel Id: $handle"
                                handle.startsWith("-") -> "Channel Id: $handle"
                                handle.all { it.isDigit() } -> "Channel Id: -100$handle"
                                else -> "@$handle"
                            }

                            Text(
                                text = displayHandle,
                                color = Color(0xFF6B7280),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // 3-Dots Menu
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier
                                .background(Color(0xFF1E2028))
                                .border(1.dp, Color(0xFF323647), RoundedCornerShape(12.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Rescan from scratch",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFFC084FC),
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRescanFromScratch()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (channel.isAnime) "Disable anime parser" else "Mark as anime channel",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleAnime()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Refresh channel avatar",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.refreshChannelAvatar(channel.id)
                                    Toast.makeText(context, "Fetching channel photo...", Toast.LENGTH_SHORT).show()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Copy channel ID",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.copyToClipboard(channel.id.toString(), "Channel ID copied")
                                }
                            )

                            HorizontalDivider(
                                color = Color(0xFF2C2F3D),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Remove Channel",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Default
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                // STATS ROW: Movies • Shows • Episodes (Full width, never truncated)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statsText,
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                // BOTTOM ACTION ROW: Synced status on Left, Scan Button on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(if (isScanning) Color(0xFF10B981) else Color(0xFF6B7280), CircleShape)
                        )
                        Text(
                            text = if (isScanning) "Scanning library..." else if (relativeScanTime == "Never") "Never synced" else "Synced $relativeScanTime",
                            color = if (isScanning) Color(0xFF10B981) else Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // Scan / Scanning Button
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isScanning) Color(0xFF15261C) else Color(0xFF22242D))
                            .border(
                                1.dp,
                                if (isScanning) Color(0xFF10B981) else Color(0xFF353846),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (isScanning) onStopScan() else onScan()
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScanning) {
                            Text(
                                text = "Scanning..",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Scan",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Glowing Green Scanning Progress Bar at bottom of card
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
                    color = Color(0xFF22C55E),
                    trackColor = Color(0xFF15261C)
                )
            }
        }
    }
}

/**
 * Channel Avatar with dynamic file loading, caching, and auto-fetch
 */
@Composable
private fun ChannelAvatar(
    channel: ChannelInfo,
    viewModel: MainAppViewModel,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(channel.avatarUrl, channel.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(channel.id, channel.avatarUrl) {
        withContext(Dispatchers.IO) {
            val localFile = File(context.filesDir, "channel_${channel.id}.jpg")
            val targetPath = channel.avatarUrl?.removePrefix("file://")
            val effectiveFile = when {
                !targetPath.isNullOrBlank() && File(targetPath).exists() && File(targetPath).length() > 0 -> File(targetPath)
                localFile.exists() && localFile.length() > 0 -> localFile
                else -> null
            }

            if (effectiveFile != null) {
                try {
                    val bmp = BitmapFactory.decodeFile(effectiveFile.absolutePath)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            bitmap = bmp.asImageBitmap()
                        }
                        return@withContext
                    }
                } catch (_: Exception) {}
            }

            // If no local avatar found yet, trigger background fetch
            viewModel.refreshChannelAvatar(channel.id)
        }
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF262832))
            .border(
                1.dp,
                if (isScanning) Color(0xFF10B981) else Color(0xFF383A46),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = channel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = channel.title.take(1).uppercase(),
                color = Color(0xFFD1D5DB),
                fontFamily = NdotFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

private fun formatRelativeScanTime(lastSyncTime: Long): String {
    if (lastSyncTime <= 0L) return "Never"
    val diffSec = (System.currentTimeMillis() - lastSyncTime) / 1000
    return when {
        diffSec < 60 -> "just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 7200 -> "1h ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        diffSec < 172800 -> "yesterday"
        else -> "${diffSec / 86400}d ago"
    }
}

/**
 * Full Interactive BottomSheet for Unmatched & Skipped Content
 * Includes real-time search, filters, details (actual telegram file name, scan time, size),
 * and actions (manual TMDB matching, direct streaming, copy stream URL, delete).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun UnmatchedContentBottomSheet(
    viewModel: MainAppViewModel,
    unmatchedItems: List<UnmatchedItem>,
    port: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "NO_MATCH", "SKIPPED"
    var matchTargetItems by remember { mutableStateOf<List<UnmatchedItem>>(emptyList()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<String>() }

    val filteredItems = remember(unmatchedItems, searchQuery, selectedFilter) {
        unmatchedItems.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "NO_MATCH" -> item.isVideo
                "SKIPPED" -> !item.isVideo
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                    item.fileName.contains(searchQuery, ignoreCase = true) ||
                    item.rawFileName.contains(searchQuery, ignoreCase = true) ||
                    item.channelTitle.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val totalBytes = remember(unmatchedItems) { unmatchedItems.sumOf { it.sizeBytes } }
    val totalSizeStr = remember(totalBytes) {
        val gb = totalBytes.toDouble() / (1024 * 1024 * 1024)
        if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.1f MB", totalBytes.toDouble() / (1024 * 1024))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101117),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        windowInsets = WindowInsets.navigationBars,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF333647))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedItemIds.clear()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit Selection",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "${selectedItemIds.size} SELECTED",
                            color = Color.White,
                            fontFamily = NdotFont,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val allFilteredSelected = filteredItems.isNotEmpty() && filteredItems.all { it.id in selectedItemIds }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E212E))
                                .border(1.dp, Color(0xFF33384B), RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (allFilteredSelected) {
                                        filteredItems.forEach { selectedItemIds.remove(it.id) }
                                    } else {
                                        filteredItems.forEach {
                                            if (it.id !in selectedItemIds) selectedItemIds.add(it.id)
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (allFilteredSelected) "DESELECT ALL" else "SELECT ALL",
                                color = Color(0xFFC084FC),
                                fontFamily = NdotFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "UNMATCHED CONTENT",
                                color = Color.White,
                                fontFamily = NdotFont,
                                fontSize = 15.sp,
                                letterSpacing = 0.5.sp,
                                maxLines = 1
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2E1C0A))
                                    .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${unmatchedItems.size}",
                                    color = Color(0xFFFBBF24),
                                    fontFamily = NdotFont,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Total volume: $totalSizeStr • Items skipped or lacking metadata",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (unmatchedItems.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E212E))
                                    .border(1.dp, Color(0xFF33384B), RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isSelectionMode = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = "Select",
                                        tint = Color(0xFFC084FC),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "SELECT",
                                        color = Color(0xFFC084FC),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear All",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF181A24))
                    .border(1.dp, Color(0xFF282B3A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search telegram filename or channel...",
                                color = Color(0xFF64748B),
                                fontSize = 12.5.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            cursorBrush = SolidColor(Color(0xFFC084FC)),
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Default
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

            // Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chips = listOf(
                    "ALL" to "All (${unmatchedItems.size})",
                    "NO_MATCH" to "No TMDB (${unmatchedItems.count { it.isVideo }})",
                    "SKIPPED" to "Skipped (${unmatchedItems.count { !it.isVideo }})"
                )

                chips.forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF7C3AED) else Color(0xFF181A24))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFFA855F7) else Color(0xFF282B3A),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedFilter = key }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = NdotFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // List of Unmatched Items
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No files match '$searchQuery'" else "No unmatched items found!",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = if (isSelectionMode) 8.dp else 30.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isSelected = item.id in selectedItemIds
                        UnmatchedItemCard(
                            item = item,
                            port = port,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isSelected) {
                                    selectedItemIds.remove(item.id)
                                } else {
                                    if (!isSelectionMode) isSelectionMode = true
                                    selectedItemIds.add(item.id)
                                }
                            },
                            onMatch = { matchTargetItems = listOf(item) },
                            onPlay = {
                                val safeFileName = try {
                                    java.net.URLEncoder.encode(item.fileName, "UTF-8")
                                } catch (_: Exception) { "video.mp4" }
                                val streamUrl = "http://127.0.0.1:$port/stream/video/${item.channelId}/${item.messageId}/$safeFileName"
                                val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(streamUrl), "video/*")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(playIntent, "Play video with"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No video player installed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onCopyLink = {
                                val safeFileName = try {
                                    java.net.URLEncoder.encode(item.fileName, "UTF-8")
                                } catch (_: Exception) { "video.mp4" }
                                val streamUrl = "http://127.0.0.1:$port/stream/video/${item.channelId}/${item.messageId}/$safeFileName"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Stream Link", streamUrl))
                                Toast.makeText(context, "✓ Stream URL copied", Toast.LENGTH_SHORT).show()
                            },
                            onAddAnyway = {
                                viewModel.addUnmatchedToLibraryAnyway(item.id)
                            },
                            onDismiss = {
                                viewModel.removeUnmatched(item.id)
                            }
                        )
                    }
                }
            }

            // Multi-Select Floating Action Bar
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF181A24),
                    border = BorderStroke(1.dp, Color(0xFF2E3244)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Match Metadata
                        Button(
                            onClick = {
                                val selectedList = unmatchedItems.filter { it.id in selectedItemIds }
                                if (selectedList.isNotEmpty()) {
                                    matchTargetItems = selectedList
                                }
                            },
                            enabled = selectedItemIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7C3AED),
                                disabledContainerColor = Color(0xFF28103F)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (selectedItemIds.isNotEmpty()) Color.White else Color(0xFF8860B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "MATCH (${selectedItemIds.size})",
                                    fontFamily = NdotFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedItemIds.isNotEmpty()) Color.White else Color(0xFF8860B8)
                                )
                            }
                        }

                        // 2. Add Anyway to Library
                        Button(
                            onClick = {
                                val ids = selectedItemIds.toList()
                                viewModel.addMultipleUnmatchedToLibraryAnyway(ids)
                                isSelectionMode = false
                                selectedItemIds.clear()
                            },
                            enabled = selectedItemIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F291E),
                                disabledContainerColor = Color(0xFF131A17)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (selectedItemIds.isNotEmpty()) Color(0xFF059669) else Color(0xFF1A3326)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (selectedItemIds.isNotEmpty()) Color(0xFF34D399) else Color(0xFF4B6E5B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "ADD (${selectedItemIds.size})",
                                    fontFamily = NdotFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedItemIds.isNotEmpty()) Color(0xFF34D399) else Color(0xFF4B6E5B)
                                )
                            }
                        }

                        // 3. Delete / Dismiss
                        Button(
                            onClick = {
                                showDeleteSelectedConfirm = true
                            },
                            enabled = selectedItemIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E1515),
                                disabledContainerColor = Color(0xFF1F1212)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (selectedItemIds.isNotEmpty()) Color(0xFFDC2626) else Color(0xFF3B1F1F)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = if (selectedItemIds.isNotEmpty()) Color(0xFFEF4444) else Color(0xFF7A3E3E),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "DELETE (${selectedItemIds.size})",
                                    fontFamily = NdotFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedItemIds.isNotEmpty()) Color(0xFFEF4444) else Color(0xFF7A3E3E)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear All Confirmation Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = {
                Text(
                    text = "Clear Unmatched List?",
                    color = Color.White,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "This will remove all skipped and unmatched file records from the list. It will not delete anything from Telegram.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearAllUnmatched()
                    }
                ) {
                    Text("Clear All", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E202B)
        )
    }

    // Delete Selected Confirmation Dialog
    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = {
                Text(
                    text = "Delete ${selectedItemIds.size} Selected Items?",
                    color = Color.White,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "This will remove ${selectedItemIds.size} items from the unmatched list. It will not delete anything from Telegram.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedItemIds.toList()
                        showDeleteSelectedConfirm = false
                        isSelectionMode = false
                        selectedItemIds.clear()
                        viewModel.removeMultipleUnmatched(ids)
                    }
                ) {
                    Text("Delete Selected", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E202B)
        )
    }

    // Manual Candidate Match Dialog (supports both single & multi-match)
    if (matchTargetItems.isNotEmpty()) {
        ManualMatchCandidateDialog(
            unmatchedItems = matchTargetItems,
            viewModel = viewModel,
            onDismiss = {
                matchTargetItems = emptyList()
                if (isSelectionMode) {
                    isSelectionMode = false
                    selectedItemIds.clear()
                }
            }
        )
    }
}

/**
 * Detailed Unmatched Card showing raw file name, scan timestamp, size, reason, and interactive actions.
 * Supports multi-selection mode with visual checkmarks and long-press selection trigger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnmatchedItemCard(
    item: UnmatchedItem,
    port: Int,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onMatch: () -> Unit,
    onPlay: () -> Unit,
    onCopyLink: () -> Unit,
    onAddAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    val cardBorderColor = when {
        isSelected -> Color(0xFFA855F7)
        isSelectionMode -> Color(0xFF2E3244)
        else -> Color(0xFF262938)
    }
    val cardBgColor = when {
        isSelected -> Color(0xFF1C182B)
        else -> Color(0xFF151720)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBgColor)
            .border(if (isSelected) 1.5.dp else 1.dp, cardBorderColor, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect()
                    }
                },
                onLongClick = {
                    onToggleSelect()
                }
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Filename and Dismiss / Selection Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFFA855F7) else Color(0xFF1E212E))
                                .border(
                                    1.5.dp,
                                    if (isSelected) Color(0xFFC084FC) else Color(0xFF474C65),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (item.isVideo) Color(0xFFC084FC) else Color(0xFF94A3B8),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.fileName,
                            color = Color.White,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (item.rawFileName.isNotBlank() && item.rawFileName != item.fileName) {
                            Text(
                                text = "Raw: ${item.rawFileName}",
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (!isSelectionMode) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Metadata Badges Row: Size, Timestamp, MsgId, Channel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Size Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1E212E))
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = item.formattedSize,
                        color = Color(0xFFC084FC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Scan Timestamp
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1E212E))
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = item.formattedDate,
                        color = Color(0xFF94A3B8),
                        fontSize = 9.5.sp
                    )
                }

                // Message ID
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1E212E))
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = "#${item.messageId}",
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp
                    )
                }

                if (item.channelTitle.isNotBlank()) {
                    Text(
                        text = item.channelTitle,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Reason Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = if (item.isVideo) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = item.reason,
                    color = if (item.isVideo) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            HorizontalDivider(color = Color(0xFF202330), thickness = 1.dp)

            // Action Buttons Row (shown in normal mode)
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isVideo) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Search & Match Button
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF28103F))
                                    .border(1.dp, Color(0xFF7C3AED), RoundedCornerShape(6.dp))
                                    .clickable { onMatch() }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
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
                                        text = "MATCH METADATA",
                                        color = Color(0xFFC084FC),
                                        fontFamily = NdotFont,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Play Button
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0F291E))
                                    .border(1.dp, Color(0xFF059669), RoundedCornerShape(6.dp))
                                    .clickable { onPlay() }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "PLAY",
                                        color = Color(0xFF34D399),
                                        fontFamily = NdotFont,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Copy Stream Link
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E212E))
                                    .border(1.dp, Color(0xFF33384B), RoundedCornerShape(6.dp))
                                    .clickable { onCopyLink() }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "LINK",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = NdotFont,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Add anyway
                        Text(
                            text = "+ Add to library",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            modifier = Modifier.clickable { onAddAnyway() }
                        )
                    } else {
                        Text(
                            text = "Non-video document file skipped",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )

                        Text(
                            text = "Dismiss",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { onDismiss() }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSelected) "✓ Selected" else "Tap card to select",
                        color = if (isSelected) Color(0xFFC084FC) else Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )

                    if (item.isVideo && isSelected) {
                        Text(
                            text = "Ready to match/delete",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Candidate Search Dialog to manually match one or multiple unmatched items
 */
@Composable
private fun ManualMatchCandidateDialog(
    unmatchedItems: List<UnmatchedItem>,
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val firstUnmatched = unmatchedItems.firstOrNull() ?: return
    val initialTitle = remember(unmatchedItems) {
        val parsed = firstUnmatched.parsedMediaItem?.title?.trim()
        if (!parsed.isNullOrBlank() && !parsed.contains(".mkv", ignoreCase = true) && !parsed.contains(".mp4", ignoreCase = true)) {
            parsed
        } else {
            firstUnmatched.fileName.replace(Regex("(?i)\\.(mkv|mp4|avi|ts|webm)$"), "")
        }
    }
    val initialType = remember(unmatchedItems) {
        val parsed = firstUnmatched.parsedMediaItem
        val ch = viewModel.channels.value.find { it.id == firstUnmatched.channelId }
        when {
            ch?.isAnime == true -> "anime"
            parsed?.kitsuId != null -> "anime"
            parsed?.type == "series" -> "series"
            parsed?.season != null || parsed?.episode != null -> "series"
            unmatchedItems.size > 1 -> "series"
            else -> "movie"
        }
    }
    var searchTitle by remember { mutableStateOf(initialTitle) }
    var searchType by remember { mutableStateOf(initialType) }
    var isLoading by remember { mutableStateOf(false) }
    var matchingCandidateKey by remember { mutableStateOf<String?>(null) }
    val isAnyMatching = matchingCandidateKey != null
    var results by remember { mutableStateOf<List<EnrichedMetadata>>(emptyList()) }
    var searchAttempted by remember { mutableStateOf(false) }

    // Live Instant Search with 350ms debounce (matches Fix Metadata Match snappiness)
    LaunchedEffect(searchTitle, searchType) {
        val q = searchTitle.trim()
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
        onDismissRequest = { if (!isAnyMatching) onDismiss() },
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
                    text = if (unmatchedItems.size > 1) "Match ${unmatchedItems.size} Items" else "Match Media Metadata",
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 17.sp,
                    letterSpacing = 0.5.sp
                )
                if (!isAnyMatching) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF888B98),
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                    text = if (unmatchedItems.size > 1) {
                        "Search show or movie. The chosen metadata & episode guide will be applied to all ${unmatchedItems.size} selected items."
                    } else {
                        "Search by title, IMDb ID (e.g. tt14173636), TMDB ID (e.g. 109445), or Kitsu Anime ID to match metadata."
                    },
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
                            value = searchTitle,
                            onValueChange = { searchTitle = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Default
                            ),
                            cursorBrush = SolidColor(Color(0xFFA855F7)),
                            modifier = Modifier.weight(1f)
                        )
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color(0xFFA855F7),
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (searchTitle.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF717482),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchTitle = "" }
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
                            text = "No matches found for \"$searchTitle\" in ${searchType.uppercase()}.\nTry entering an IMDb ID, TMDB ID, or Kitsu ID",
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
                        items(results) { cand ->
                            val candKey = "${cand.tmdbId}_${cand.imdbId}_${cand.kitsuId}_${cand.title}"
                            val isThisMatching = matchingCandidateKey == candKey
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF181A22))
                                    .border(1.dp, Color(0xFF282A36), RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isAnyMatching) {
                                        matchingCandidateKey = candKey
                                        if (unmatchedItems.size == 1) {
                                            viewModel.matchUnmatchedItem(firstUnmatched.id, cand, targetType = searchType) { success ->
                                                matchingCandidateKey = null
                                                if (success) onDismiss()
                                            }
                                        } else {
                                            val ids = unmatchedItems.map { it.id }
                                            viewModel.matchMultipleUnmatchedItems(ids, cand, targetType = searchType) { count ->
                                                matchingCandidateKey = null
                                                if (count > 0) onDismiss()
                                            }
                                        }
                                    }
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Poster thumbnail
                                    RemoteImage(
                                        url = cand.posterUrl,
                                        contentDescription = cand.title,
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
                                            text = cand.title ?: "Unknown",
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
                                            if (!cand.year.isNullOrBlank()) {
                                                Text(
                                                    text = cand.year,
                                                    color = Color(0xFFA855F7),
                                                    fontFamily = NdotFont,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            if (cand.kitsuId != null && cand.kitsuId > 0) {
                                                Text(
                                                    text = "KITSU #${cand.kitsuId}",
                                                    color = Color(0xFFFF7A00),
                                                    fontFamily = NdotFont,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            if (!cand.imdbId.isNullOrBlank()) {
                                                Text(
                                                    text = cand.imdbId,
                                                    color = Color(0xFFFBBF24),
                                                    fontFamily = NdotFont,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        if (!cand.overview.isNullOrBlank()) {
                                            Text(
                                                text = cand.overview,
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
                                        if (isThisMatching) {
                                            CircularProgressIndicator(
                                                color = Color(0xFFD8B4FE),
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp
                                            )
                                        } else {
                                            Text(
                                                text = if (unmatchedItems.size > 1) "APPLY ALL" else "MATCH",
                                                color = if (isAnyMatching) Color(0xFF6B7280) else Color(0xFFD8B4FE),
                                                fontFamily = NdotFont,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!isAnyMatching) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        }
    )
}
