package com.tgserver.streamer.ui.screens

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
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel

// Pixel Dot-Matrix Font (Exact match with HomeScreen / LibraryScreen design)
private val NdotFont = FontFamily(Font(R.font.ndot))

@Composable
fun ChannelsScreen(viewModel: MainAppViewModel) {
    val channels by viewModel.channels.collectAsState()
    val isAddingChannel by viewModel.isAddingChannel.collectAsState()
    val scanningChannelIds by viewModel.scanningChannelIds.collectAsState()
    val isScanningAll by viewModel.isScanningAll.collectAsState()
    val haptic = LocalHapticFeedback.current

    var channelInput by remember { mutableStateOf("") }
    var isAnimeChannel by remember { mutableStateOf(false) }

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
                    onScan = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.syncChannel(channel.id)
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

/**
 * Beautiful, state-of-the-art Channel Card with dark OLED glass aesthetics
 */
@Composable
private fun ChannelItemCard(
    channel: ChannelInfo,
    isScanning: Boolean,
    onScan: () -> Unit,
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

                    Text(
                        text = if (isScanning) "syncing..." else channel.scanStatusText,
                        color = if (isScanning) Color(0xFFFBBF24) else Color(0xFF64748B),
                        fontFamily = FontFamily.Default,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // SCAN BUTTON (Crisp, Fixed Height, Single-Line)
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isScanning) Color(0xFF2E1C0A) else Color(0xFF1E1729))
                        .border(
                            1.dp,
                            if (isScanning) Color(0xFFF59E0B) else Color(0xFF5B21B6),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = !isScanning) { onScan() }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isScanning) {
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
                        } else {
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
