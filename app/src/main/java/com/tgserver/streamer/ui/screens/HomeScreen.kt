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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.R
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.AppTab
import com.tgserver.streamer.ui.MainAppViewModel
import kotlinx.coroutines.delay
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
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // TOP APP BAR (Nothing OS / Dot-Matrix Minimalist)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "TG-Server",
                    color = Color.White,
                    fontSize = 25.sp,
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

            // Concentric Ring Lens Glyph
            ConcentricRingEmblem(
                modifier = Modifier.size(46.dp)
            )
        }

        // HERO CARD: SERVER STATE (Sleep / Awakening / Awaked)
        Card(
            modifier = Modifier
                .fillMaxWidth()
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

                            // 3 Metrics Columns
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ServerMetricItem(
                                    value = telemetry.uptimeFormatted.ifBlank { "1hr" },
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

        // Bottom clearance for floating liquid navbar
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
            fontFamily = NdotFont,
            fontSize = 20.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color(0xFF8E909A),
            fontFamily = NdotFont,
            fontSize = 10.5.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
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
