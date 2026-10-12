package com.tgserver.streamer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.AppTab
import com.tgserver.streamer.ui.MainAppViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: MainAppViewModel) {
    val telemetry by viewModel.telemetry.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val media by viewModel.media.collectAsState()
    val session by viewModel.session.collectAsState()
    val haptic = LocalHapticFeedback.current

    var copiedRecently by remember { mutableStateOf(false) }
    var isAwakening by remember { mutableStateOf(false) }
    var awakeningDots by remember { mutableStateOf(".") }

    // Reset copy indicator after 2.5s
    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(2500)
            copiedRecently = false
        }
    }

    // Awakening dot-dot animation loop
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

    // Turn off awakening state once telemetry confirms running
    LaunchedEffect(telemetry.isRunning) {
        if (telemetry.isRunning) {
            isAwakening = false
        }
    }

    // Breathing pulse for standby / live LED indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_pulse"
    )

    // Library statistics
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // TOP APP BAR (Nothing OS / Dot-Matrix Minimalist)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "TG-Server",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.8.sp
                )

                val displayName = if (session.firstName.isNotBlank()) session.firstName else "Name"
                val usernameDisplay = if (session.username.isNotBlank()) "@${session.username}" else "@USERNAME"
                Text(
                    text = "$displayName . $usernameDisplay",
                    color = Color(0xFF8E909F),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )
            }

            // Concentric Rings Avatar / Emblem
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(1.5.dp, Color(0x3DFFFFFF), CircleShape)
                    .padding(4.dp)
                    .border(1.2.dp, Color(0x66FFFFFF), CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF14151D)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = session.initials,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // HERO CARD: SERVER AWAKENING & LIVE TELEMETRY
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(
                    BorderStroke(
                        1.dp,
                        if (telemetry.isRunning) Color(0xFF2E313D) else Color(0xFF22232B)
                    ),
                    shape = RoundedCornerShape(22.dp)
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
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF181920),
                                Color(0xFF0F1014)
                            )
                        )
                    )
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
                        // SLEEP MODE VIEW (Centered standby state)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 46.dp, horizontal = 20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAwakening) Color(0xFF38BDF8).copy(alpha = alphaPulse)
                                            else Color(0xFFF59E0B).copy(alpha = alphaPulse)
                                        )
                                )
                                Text(
                                    text = if (isAwakening) "Server Awakening$awakeningDots" else "In Sleep mode",
                                    color = if (isAwakening) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.2.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (isAwakening) "starting streamer daemon..." else "tap to awaken",
                                color = Color(0xFF555968),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.6.sp
                            )
                        }
                    } else {
                        // ACTIVE TELEMETRY VIEW (Live stats + Copyable URL)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp, start = 18.dp, end = 18.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
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
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF34D399).copy(alpha = alphaPulse))
                                    )
                                    Text(
                                        text = "Server Active",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.sp
                                    )
                                }

                                // Quick Sleep action button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x18FFFFFF))
                                        .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleServer()
                                        }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "SLEEP",
                                        color = Color(0xFFA1A1AA),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            // 3 Telemetry Metrics (uptime, ram usage, cpu usage)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ServerMetricItem(
                                    value = telemetry.uptimeFormatted.ifBlank { "1m" },
                                    label = "uptime",
                                    modifier = Modifier.weight(1f)
                                )
                                ServerMetricItem(
                                    value = "${telemetry.memoryUsedMb}mb",
                                    label = "ram usage",
                                    modifier = Modifier.weight(1f)
                                )
                                ServerMetricItem(
                                    value = "${String.format(Locale.US, "%.1f", telemetry.cpuPercent)}%",
                                    label = "cpu usage",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Bottom Sub-bar: Stream URL (One-tap Copy)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF090A0D))
                                    .border(1.dp, Color(0xFF1E2028), RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.copyToClipboard(serverUrl, "Stream URL copied")
                                        copiedRecently = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "URL: $serverUrl",
                                        color = Color(0xFFD4D4D8),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (copiedRecently) "COPIED" else "COPY",
                                        color = if (copiedRecently) Color(0xFF34D399) else Color(0xFF888894),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // LIBRARY CARD (Cosmic Purple Glass Minimalist)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(
                    BorderStroke(1.dp, Color(0xFF451778)),
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.selectTab(AppTab.LIBRARY)
                },
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1B0733),
                                Color(0xFF110220)
                            )
                        )
                    )
                    .padding(top = 18.dp, start = 18.dp, end = 18.dp, bottom = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Title
                    Text(
                        text = "Library",
                        color = Color(0xFFD8B4FE),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )

                    // 4 Counts in a Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // Bottom Total Server Size bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33000000))
                            .border(1.dp, Color(0x3D9333EA), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "Total Server Size: $totalServerSizeFormatted",
                            color = Color(0xFFC084FC),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Bottom Spacer for Floating Liquid Bottom Bar clearance
        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun ServerMetricItem(
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
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color(0xFF9094A6),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            letterSpacing = 0.5.sp
        )
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
            fontFamily = FontFamily.Monospace,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color(0xFFA78BFA),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 0.4.sp
        )
    }
}
