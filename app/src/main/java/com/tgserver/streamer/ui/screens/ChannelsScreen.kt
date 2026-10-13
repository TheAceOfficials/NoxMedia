package com.tgserver.streamer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.R
import com.tgserver.streamer.data.model.ChannelInfo
import com.tgserver.streamer.data.model.ScanProgressState
import com.tgserver.streamer.data.model.UnmatchedItem
import com.tgserver.streamer.metadata.EnrichedMetadata
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Pixel Dot-Matrix Font (Exact match with HomeScreen / LibraryScreen design)
private val NdotFont = FontFamily(Font(R.font.ndot))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(viewModel: MainAppViewModel) {
    val channels by viewModel.channels.collectAsState()
    val isAddingChannel by viewModel.isAddingChannel.collectAsState()
    val scanningChannelIds by viewModel.scanningChannelIds.collectAsState()
    val isScanningAll by viewModel.isScanningAll.collectAsState()
    val unmatchedMedia by viewModel.unmatchedMedia.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val config by viewModel.config.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var channelInput by remember { mutableStateOf("") }
    var isAnimeChannel by remember { mutableStateOf(false) }
    var showUnmatchedSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        // 1. TOP HEADER & SCAN ALL ACTION
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CHANNELS",
                            color = Color.White,
                            fontFamily = NdotFont,
                            fontSize = 20.sp,
                            letterSpacing = 0.5.sp
                        )

                        // Channel Count Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF28103F))
                                .border(1.dp, Color(0xFF4C1D7C), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${channels.size} CONNECTED",
                                color = Color(0xFFC084FC),
                                fontFamily = NdotFont,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Text(
                        text = "Live Telegram streams indexed in real-time for Stremio",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Default,
                        lineHeight = 16.sp
                    )
                }

                if (channels.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isScanningAll) Color(0xFF2E1C0A) else Color(0xFF1E1729))
                            .border(
                                1.dp,
                                if (isScanningAll) Color(0xFFF59E0B) else Color(0xFF5B21B6),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = !isScanningAll && scanningChannelIds.isEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.syncAllChannels()
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isScanningAll) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFBBF24),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "SCANNING",
                                    color = Color(0xFFFBBF24),
                                    fontFamily = NdotFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Scan all",
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "SCAN ALL",
                                    color = Color(0xFFC084FC),
                                    fontFamily = NdotFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. CONNECT CHANNEL CARD (Minimal, Sleek Glass/OLED Container)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF13141B))
                    .border(1.dp, Color(0xFF262832), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header Tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "CONNECT TELEGRAM CHANNEL",
                                color = Color(0xFFC084FC),
                                fontFamily = NdotFont,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = "MTProto",
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }

                    // Sleek Minimal Input Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1C25))
                            .border(1.dp, Color(0xFF2D3142), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                if (channelInput.isEmpty()) {
                                    Text(
                                        text = "@username, t.me link, or channel ID (-100...)",
                                        color = Color(0xFF64748B),
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                }
                                BasicTextField(
                                    value = channelInput,
                                    onValueChange = { channelInput = it },
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color(0xFFC084FC)),
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (channelInput.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { channelInput = "" }
                                )
                            }
                        }
                    }

                    // Options & Action Row: Anime Toggle Chip + Connect Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Interactive Anime Parser Chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAnimeChannel) Color(0xFF2E1C0A) else Color(0xFF181A22))
                                .border(
                                    1.dp,
                                    if (isAnimeChannel) Color(0xFFF59E0B) else Color(0xFF2D3142),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isAnimeChannel = !isAnimeChannel
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAnimeChannel) Icons.Default.Check else Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = if (isAnimeChannel) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "ANIME PARSER (KITSU)",
                                    color = if (isAnimeChannel) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                    fontFamily = NdotFont,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Connect Button
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (channelInput.isNotBlank() && !isAddingChannel)
                                        Color(0xFF7C3AED)
                                    else
                                        Color(0xFF2A233A)
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
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isAddingChannel) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "CONNECTING...",
                                        color = Color.White,
                                        fontFamily = NdotFont,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                } else {
                                    Text(
                                        text = "+ CONNECT",
                                        color = if (channelInput.isNotBlank()) Color.White else Color(0xFF7E7A8A),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Helpful Hint Footer
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Supports public @channels, invite links, or IDs (-100...). For private channels, make sure your account is already joined.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 3. SECTION TITLE: CONNECTED CHANNELS
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE SOURCES (${channels.size})",
                    color = Color(0xFF94A3B8),
                    fontFamily = NdotFont,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )

                if (channels.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            text = "LIVE SYNC READY",
                            color = Color(0xFF10B981),
                            fontFamily = NdotFont,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // 4. CHANNELS LIST OR EMPTY STATE
        if (channels.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF13141B))
                        .border(1.dp, Color(0xFF262832), RoundedCornerShape(16.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1F222F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "No channels connected yet",
                            color = Color.White,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )

                        Text(
                            text = "Add a Telegram channel above using username or channel ID to start indexing movies and series for Stremio.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(channels, key = { it.id }) { channel ->
                val isScanning = channel.id in scanningChannelIds
                ChannelItemCard(
                    channel = channel,
                    isScanning = isScanning,
                    progress = scanProgress[channel.id],
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

        // 5. UNMATCHED & SKIPPED CONTENT MINIMAL SECTION
        if (unmatchedMedia.isNotEmpty()) {
            item {
                UnmatchedContentPreviewCard(
                    unmatchedItems = unmatchedMedia,
                    onViewAll = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showUnmatchedSheet = true
                    }
                )
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
 * Beautiful, state-of-the-art Channel Card with dark OLED glass aesthetics
 */
@Composable
private fun ChannelItemCard(
    channel: ChannelInfo,
    isScanning: Boolean,
    progress: ScanProgressState? = null,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onRescanFromScratch: () -> Unit,
    onToggleAnime: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13141B))
            .border(
                1.dp,
                if (isScanning) Color(0xFF7C3AED) else Color(0xFF262832),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    // Circular Avatar with Gradient
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        if (channel.isAnime) Color(0xFFD97706) else Color(0xFF6D28D9),
                                        Color(0xFF1E1729)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                if (channel.isAnime) Color(0xFFF59E0B) else Color(0xFF8B5CF6),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.title.take(1).uppercase(),
                            color = Color.White,
                            fontFamily = NdotFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Title & Username/ID Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = channel.title,
                                color = Color.White,
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (channel.isAnime) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF2E1C0A))
                                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                ) {
                                    Text(
                                        text = "ANIME",
                                        color = Color(0xFFFBBF24),
                                        fontFamily = NdotFont,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val handle = channel.username ?: channel.id.toString()
                        val displayHandle = when {
                            handle.startsWith("-100") -> "ID: $handle"
                            handle.startsWith("-") -> "ID: $handle"
                            handle.all { it.isDigit() } -> "ID: -100$handle"
                            else -> "@$handle"
                        }

                        Text(
                            text = displayHandle,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp
                        )
                    }
                }

                // 3-Dots Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(Color(0xFF1E202B))
                            .border(1.dp, Color(0xFF323647), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Rescan from scratch",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Default
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(16.dp)
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
                                    text = if (channel.isAnime) "Turn off anime parser" else "Mark as anime channel",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Default
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleAnime()
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Default
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
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

            HorizontalDivider(color = Color(0xFF20222D), thickness = 1.dp)

            // BOTTOM ROW: Stats Chips + Scan Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Count and Scan Status Chips
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (channel.videoCount > 0) Color(0xFF0F291E) else Color(0xFF1E212E))
                            .border(
                                1.dp,
                                if (channel.videoCount > 0) Color(0xFF059669) else Color(0xFF33384B),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "${channel.videoCount} MEDIA",
                            color = if (channel.videoCount > 0) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontFamily = NdotFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    val statusText = if (isScanning) {
                        if (progress != null) {
                            if (progress.isCooldown) {
                                "⚠️ Rate limit: wait ${progress.cooldownSeconds}s"
                            } else {
                                "Batch #${progress.currentBatch} • ${progress.indexedMedia} vids • ${progress.scannedMessages} msgs"
                            }
                        } else "syncing..."
                    } else channel.scanStatusText

                    Text(
                        text = statusText,
                        color = if (isScanning) Color(0xFFFBBF24) else Color(0xFF64748B),
                        fontFamily = FontFamily.Default,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action Buttons Row: SCAN or SCANNING + STOP
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2E1C0A))
                                .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFBBF24),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "SCANNING",
                                    color = Color(0xFFFBBF24),
                                    fontFamily = NdotFont,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Stop Button
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF3B1219))
                                .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                .clickable { onStopScan() }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Stop",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "STOP",
                                    color = Color(0xFFEF4444),
                                    fontFamily = NdotFont,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1729))
                                .border(1.dp, Color(0xFF5B21B6), RoundedCornerShape(8.dp))
                                .clickable { onScan() }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "SCAN",
                                    color = Color(0xFFC084FC),
                                    fontFamily = NdotFont,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
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
 * Minimal sleek preview card on ChannelsScreen showing unmatched & skipped media count + 2-3 sample items
 */
@Composable
private fun UnmatchedContentPreviewCard(
    unmatchedItems: List<UnmatchedItem>,
    onViewAll: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13141B))
            .border(1.dp, Color(0xFF2E2438), RoundedCornerShape(16.dp))
            .clickable { onViewAll() }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E1C0A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Text(
                        text = "UNMATCHED CONTENT",
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E1C0A))
                            .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${unmatchedItems.size} SKIPPED",
                            color = Color(0xFFFBBF24),
                            fontFamily = NdotFont,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "VIEW ALL",
                        color = Color(0xFFC084FC),
                        fontFamily = NdotFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Text(
                text = "Files not found on TMDB/TVDB/Kitsu or skipped due to unsupported formats. Tap to review, search, or stream directly.",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )

            HorizontalDivider(color = Color(0xFF222430), thickness = 1.dp)

            // Preview of latest 2-3 items
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                unmatchedItems.take(3).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF181A24))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (item.isVideo) Color(0xFFC084FC) else Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )

                            Text(
                                text = item.fileName,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Default,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF222533))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.formattedSize,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (item.isVideo) Color(0xFF2A1C14) else Color(0xFF222533))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (item.isVideo) "No Match" else "Skipped",
                                    color = if (item.isVideo) Color(0xFFF59E0B) else Color(0xFF64748B),
                                    fontFamily = NdotFont,
                                    fontSize = 8.sp,
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

/**
 * Full Interactive BottomSheet for Unmatched & Skipped Content
 * Includes real-time search, filters, details (actual telegram file name, scan time, size),
 * and actions (manual TMDB matching, direct streaming, copy stream URL, delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var matchTargetItem by remember { mutableStateOf<UnmatchedItem?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

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
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "UNMATCHED CONTENT",
                            color = Color.White,
                            fontFamily = NdotFont,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2E1C0A))
                                .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${unmatchedItems.size} ITEMS",
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
                        fontSize = 11.5.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (unmatchedItems.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.size(32.dp)
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
                        modifier = Modifier.size(32.dp)
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

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF181A24))
                    .border(1.dp, Color(0xFF282B3A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
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
                    contentPadding = PaddingValues(bottom = 30.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        UnmatchedItemCard(
                            item = item,
                            port = port,
                            onMatch = { matchTargetItem = item },
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
        }
    }

    // Clear Confirmation Dialog
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

    // Manual Candidate Match Dialog
    if (matchTargetItem != null) {
        ManualMatchCandidateDialog(
            unmatched = matchTargetItem!!,
            viewModel = viewModel,
            onDismiss = { matchTargetItem = null }
        )
    }
}

/**
 * Detailed Unmatched Card showing raw file name, scan timestamp, size, reason, and interactive actions.
 */
@Composable
private fun UnmatchedItemCard(
    item: UnmatchedItem,
    port: Int,
    onMatch: () -> Unit,
    onPlay: () -> Unit,
    onCopyLink: () -> Unit,
    onAddAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF151720))
            .border(1.dp, Color(0xFF262938), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Filename and Dismiss Button
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
                    Icon(
                        imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (item.isVideo) Color(0xFFC084FC) else Color(0xFF94A3B8),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp)
                    )

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

            // Action Buttons Row
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
        }
    }
}

/**
 * Interactive Candidate Search Dialog to manually match an unmatched item with TMDB / Kitsu
 */
@Composable
private fun ManualMatchCandidateDialog(
    unmatched: UnmatchedItem,
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var searchTitle by remember { mutableStateOf(unmatched.fileName.replace(Regex("(?i)\\.(mkv|mp4|avi|ts)$"), "")) }
    var searchType by remember { mutableStateOf("movie") } // "movie" or "series"
    var isSearching by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<EnrichedMetadata>>(emptyList()) }

    fun doSearch() {
        if (searchTitle.isBlank()) return
        isSearching = true
        coroutineScope.launch {
            candidates = viewModel.searchMetadataCandidates(searchTitle.trim(), searchType)
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        doSearch()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Match Media Metadata",
                color = Color.White,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Input Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF14151D))
                        .border(1.dp, Color(0xFF282B3A), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = searchTitle,
                        onValueChange = { searchTitle = it },
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFFC084FC)),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Type toggles + Search Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("movie" to "Movie", "series" to "Series").forEach { (typeKey, label) ->
                            val isSel = searchType == typeKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) Color(0xFF7C3AED) else Color(0xFF1A1C26))
                                    .clickable {
                                        searchType = typeKey
                                        doSearch()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontFamily = NdotFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF7C3AED))
                            .clickable { doSearch() }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SEARCH",
                            color = Color.White,
                            fontFamily = NdotFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF252837), thickness = 1.dp)

                // Candidates List
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color(0xFFC084FC),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else if (candidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No candidates found. Try adjusting title query.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidates) { cand ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF181A24))
                                    .border(1.dp, Color(0xFF282B3A), RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.matchUnmatchedItem(unmatched.id, cand) { success ->
                                            if (success) onDismiss()
                                        }
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Poster Thumbnail
                                RemoteImage(
                                    url = cand.posterUrl,
                                    contentDescription = cand.title,
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 52.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = cand.title ?: "Unknown",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!cand.year.isNullOrBlank()) {
                                        Text(
                                            text = cand.year,
                                            color = Color(0xFFC084FC),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }

                                    if (!cand.overview.isNullOrBlank()) {
                                        Text(
                                            text = cand.overview,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Select",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF1E202B)
    )
}
