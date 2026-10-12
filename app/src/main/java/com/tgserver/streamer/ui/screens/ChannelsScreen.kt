package com.tgserver.streamer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.data.model.ChannelInfo
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel

private val AccentAmber = Color(0xFFEAA23E)

@Composable
fun ChannelsScreen(viewModel: MainAppViewModel) {
    val channels by viewModel.channels.collectAsState()
    val isAddingChannel by viewModel.isAddingChannel.collectAsState()
    val scanningChannelIds by viewModel.scanningChannelIds.collectAsState()
    val isScanningAll by viewModel.isScanningAll.collectAsState()

    var channelInput by remember { mutableStateOf("") }
    var isAnimeChannel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Channels",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Scan once; new posts are indexed automatically while the server runs.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (channels.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = { viewModel.syncAllChannels() },
                    enabled = !isScanningAll && scanningChannelIds.isEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceCard,
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (isScanningAll) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scanning...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Scan all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ADD CHANNEL CARD (Inline container matching screenshots)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Channel Text Input
                OutlinedTextField(
                    value = channelInput,
                    onValueChange = { channelInput = it },
                    label = { Text("Channel", color = TextSecondary, fontSize = 13.sp) },
                    placeholder = {
                        Text(
                            text = "@name, t.me link or -100... id",
                            color = TextTertiary,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentAmber,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceCardElevated,
                        unfocusedContainerColor = SurfaceCardElevated,
                        focusedLabelColor = AccentAmber,
                        unfocusedLabelColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Anime Checkbox + Add Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isAnimeChannel = !isAnimeChannel }
                            .padding(end = 8.dp)
                    ) {
                        Checkbox(
                            checked = isAnimeChannel,
                            onCheckedChange = { isAnimeChannel = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentAmber,
                                checkmarkColor = BackgroundOled,
                                uncheckedColor = TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Anime channel",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            if (channelInput.isNotBlank()) {
                                viewModel.addChannel(channelInput.trim(), isAnimeChannel) { success, _ ->
                                    if (success) {
                                        channelInput = ""
                                        isAnimeChannel = false
                                    }
                                }
                            }
                        },
                        enabled = channelInput.isNotBlank() && !isAddingChannel,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmber,
                            disabledContainerColor = AccentAmber.copy(alpha = 0.4f),
                            contentColor = BackgroundOled
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        if (isAddingChannel) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = BackgroundOled,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "Add",
                                color = BackgroundOled,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Helper Subtext
                Text(
                    text = "Anime channels use Kitsu first and understand absolute episode numbers (One Piece 1123).",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // SECTION: YOUR CHANNELS
        Text(
            text = "YOUR CHANNELS",
            color = TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        // CHANNELS LIST OR EMPTY STATE
        if (channels.isEmpty()) {
            Text(
                text = "No channels yet.",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    val isScanning = channel.id in scanningChannelIds
                    ChannelItemCard(
                        channel = channel,
                        isScanning = isScanning,
                        onScan = { viewModel.syncChannel(channel.id) },
                        onRescanFromScratch = { viewModel.rescanFromScratch(channel.id) },
                        onToggleAnime = { viewModel.toggleAnimeChannel(channel.id) },
                        onDelete = { viewModel.removeChannel(channel.id) }
                    )
                }
            }
        }
    }
}

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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: Channel Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = channel.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (channel.isAnime) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentAmber.copy(alpha = 0.15f))
                                .border(1.dp, AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ANIME",
                                color = AccentAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val handle = channel.username ?: "channel"
                    Text(
                        text = if (handle.startsWith("-100")) handle else "@$handle",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(text = "•", color = TextTertiary, fontSize = 10.sp)
                    Text(
                        text = channel.scanStatusText,
                        color = if (channel.videoCount > 0) StatusGreen else TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }

            // RIGHT: Scan Button & 3-Dots Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scan Button
                OutlinedButton(
                    onClick = onScan,
                    enabled = !isScanning,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceCardElevated,
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = AccentAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Scanning", fontSize = 12.sp, color = AccentAmber)
                    } else {
                        Text(
                            text = "Scan",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 3-dots Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(SurfaceCardElevated)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Rescan from scratch",
                                    color = TextPrimary,
                                    fontSize = 13.sp
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
                                    text = if (channel.isAnime) "Unmark anime channel" else "Mark as anime channel",
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleAnime()
                            }
                        )

                        HorizontalDivider(
                            color = BorderSubtle,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Remove",
                                    color = StatusRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = StatusRed,
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
        }
    }
}
