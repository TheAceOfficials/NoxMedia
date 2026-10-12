package com.tgserver.streamer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import com.tgserver.streamer.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainAppViewModel) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val config by viewModel.config.collectAsState()

    // Form inputs state synced with config
    var tmdbKeyInput by remember(config.tmdbApiKey) { mutableStateOf(config.tmdbApiKey) }
    var tvdbKeyInput by remember(config.tvdbApiKey) { mutableStateOf(config.tvdbApiKey) }
    var posterUrlInput by remember(config.customPosterUrl) { mutableStateOf(config.customPosterUrl) }
    var portInput by remember(config.port) { mutableStateOf(config.port.toString()) }
    var remoteUrlInput by remember(config.remotePublicUrl) { mutableStateOf(config.remotePublicUrl) }
    var selectedBufferKb by remember(config.bufferChunkSizeKb) { mutableIntStateOf(config.bufferChunkSizeKb) }
    var selectedQuality by remember(config.preferredQuality) { mutableStateOf(config.preferredQuality) }
    var hideCamTs by remember(config.hideCamTs) { mutableStateOf(config.hideCamTs) }
    var startOnBoot by remember(config.startOnBoot) { mutableStateOf(config.startOnBoot) }
    var autoScanNewContent by remember(config.autoScanNewContent) { mutableStateOf(config.autoScanNewContent) }
    var replaceSameQuality by remember(config.replaceSameQuality) { mutableStateOf(config.replaceSameQuality) }
    var skipExactDuplicates by remember(config.skipExactDuplicates) { mutableStateOf(config.skipExactDuplicates) }
    var autoBackupEnabled by remember(config.autoBackupEnabled) { mutableStateOf(config.autoBackupEnabled) }
    var autoBackupHours by remember(config.autoBackupIntervalHours) { mutableIntStateOf(config.autoBackupIntervalHours) }

    // Library scan state
    var duplicateScanCount by remember { mutableStateOf<Int?>(null) }
    var deadFilesScanCount by remember { mutableStateOf<Int?>(null) }

    // Dialogs state
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }

    AdaptiveScreenContainer(applyInsets = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ==================== TOP BADGE & TITLE ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NothingDotBadge(
                    text = "TG-SERVER",
                    dotColor = AccentApple,
                    backgroundColor = Color(0x1FFFFFFF),
                    textColor = TextPrimary
                )

                NothingDotBadge(
                    text = "ENGINE CONFIG",
                    dotColor = AccentSilver,
                    backgroundColor = Color(0x14FFFFFF),
                    textColor = TextSecondary
                )
            }

            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "Settings",
                    color = TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configure metadata pipelines, streaming quality, network access, and channel automation.",
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )
            }

            // ==================== 1. PROFILE HEADER CARD ====================
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SurfaceCard,
                borderColor = BorderSubtle,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar Squircle
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceCardElevated)
                                .border(1.dp, BorderSilver, RoundedCornerShape(16.dp))
                        ) {
                            Text(
                                text = session.initials,
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = session.firstName.ifBlank { "Telegram User" },
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                NothingDotBadge(
                                    text = if (session.isLoggedIn) "ACTIVE" else "OFFLINE",
                                    dotColor = if (session.isLoggedIn) StatusGreen else StatusRed,
                                    textColor = if (session.isLoggedIn) StatusGreen else StatusRed
                                )
                            }

                            Text(
                                text = if (session.username.isNotBlank()) "@${session.username}" else "Telegram Account",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )

                            if (session.userId > 0) {
                                Text(
                                    text = "USER ID: ${session.userId}",
                                    color = TextTertiary,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    NothingPillButton(
                        text = "Logout",
                        onClick = { showLogoutDialog = true },
                        variant = NothingButtonVariant.Ghost,
                        height = 34.dp,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }

            // ==================== 2. METADATA PROVIDERS ====================
            SettingsSectionCard(
                icon = Icons.Default.Extension,
                title = "METADATA PROVIDERS",
                description = "Enrich movie & show catalogs with plot overviews, posters, and IMDb ratings."
            ) {
                NothingTextField(
                    value = tmdbKeyInput,
                    onValueChange = { tmdbKeyInput = it },
                    label = "TMDB API KEY (V3)",
                    badgeText = "MOVIES & SHOWS",
                    placeholder = "Paste your TMDB API Key...",
                    isMonospace = true,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                NothingTextField(
                    value = tvdbKeyInput,
                    onValueChange = { tvdbKeyInput = it },
                    label = "TVDB API KEY (V4)",
                    badgeText = "SERIES & ANIME",
                    placeholder = "Paste your TVDB API Key...",
                    isMonospace = true,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Cascade Pipeline Diagram Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCardElevated.copy(alpha = 0.5f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CASCADE FALLBACK ENGINE",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        )

                        NothingDotBadge(
                            text = "AUTO-SWAP",
                            dotColor = StatusGreen,
                            textColor = StatusGreen
                        )
                    }

                    FallbackPipelineRow(
                        category = "Movies",
                        steps = listOf("TMDB", "Cinemeta")
                    )

                    FallbackPipelineRow(
                        category = "Series",
                        steps = listOf("TVDB", "TMDB", "Cinemeta")
                    )

                    FallbackPipelineRow(
                        category = "Anime",
                        steps = listOf("Kitsu", "TVDB", "TMDB", "Cinemeta")
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "• If any provider hits rate limits or is missing a key, the engine seamlessly auto-switches to the next fallback.",
                        color = TextTertiary,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                NothingPillButton(
                    text = "Save Provider Keys →",
                    onClick = {
                        viewModel.updateMetadataProviders(tmdbKeyInput, tvdbKeyInput)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = NothingButtonVariant.Secondary,
                    height = 46.dp
                )
            }

            // ==================== 3. POSTERS & ARTWORKS ====================
            SettingsSectionCard(
                icon = Icons.Default.Image,
                title = "POSTERS & ARTWORKS",
                description = "Customize poster templates used for rendering catalog cards in Stremio."
            ) {
                NothingTextField(
                    value = posterUrlInput,
                    onValueChange = { posterUrlInput = it },
                    label = "CUSTOM POSTER TEMPLATE URL",
                    placeholder = "https://custom-posters.domain/template/{id}",
                    isMonospace = true,
                    singleLine = true,
                    trailingIcon = if (posterUrlInput.isNotBlank()) {
                        {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextTertiary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { posterUrlInput = "" }
                            )
                        }
                    } else null
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Leave empty to use official high-res TMDB & Cinemeta posters.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                NothingPillButton(
                    text = "Save Poster Template →",
                    onClick = { viewModel.updatePosterSettings(posterUrlInput) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = NothingButtonVariant.Secondary,
                    height = 46.dp
                )
            }

            // ==================== 4. STREAMING PREFERENCES ====================
            SettingsSectionCard(
                icon = Icons.Default.PlayCircle,
                title = "STREAMING PREFERENCES",
                description = "Configure resolution prioritization and video stream filters."
            ) {
                Text(
                    text = "PREFERRED QUALITY PRIORITY",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quality Chips Row
                val qualities = listOf("Any", "4K", "2K", "1080p", "720p", "480p")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    qualities.forEach { q ->
                        val isSelected = selectedQuality == q
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFFFFFFFF) else SurfaceCardElevated)
                                .border(1.dp, if (isSelected) Color.Transparent else BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedQuality = q
                                    viewModel.updateStreamingSettings(q, hideCamTs)
                                }
                        ) {
                            Text(
                                text = q,
                                color = if (isSelected) Color(0xFF070709) else TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Selected quality will be sorted to the very top in Stremio's stream selector.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                DotDivider(dotsCount = 20)
                Spacer(modifier = Modifier.height(16.dp))

                // Hide Cam/TS Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Hide CAM & TeleSync Streams",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Filters out low-quality theatre recordings (CAM, HDCAM, TS) from catalog.",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    NothingSwitch(
                        checked = hideCamTs,
                        onCheckedChange = {
                            hideCamTs = it
                            viewModel.updateStreamingSettings(selectedQuality, it)
                        }
                    )
                }
            }

            // ==================== 5. SERVER & NETWORK ====================
            SettingsSectionCard(
                icon = Icons.Default.Dns,
                title = "SERVER & NETWORK",
                description = "Configure local port, streaming buffer chunks, and remote tunnel URLs."
            ) {
                // Aligned Local Port & Buffer Chunk side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        NothingTextField(
                            value = portInput,
                            onValueChange = { portInput = it.filter { c -> c.isDigit() } },
                            label = "LOCAL PORT",
                            placeholder = "7788",
                            isMonospace = true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        // Harmonized Label Row matching NothingTextField
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(TextTertiary, CircleShape)
                            )
                            Text(
                                text = "BUFFER CHUNK",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.2.sp
                            )
                        }

                        // Aligned segmented button row matching field height
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                                .background(SurfaceCardElevated)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(512, 1024, 2048).forEach { kb ->
                                val isSelected = selectedBufferKb == kb
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) Color(0xFFFFFFFF) else Color.Transparent)
                                        .clickable { selectedBufferKb = kb }
                                ) {
                                    Text(
                                        text = if (kb >= 1024) "${kb / 1024}M" else "${kb}K",
                                        color = if (isSelected) Color(0xFF070709) else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                NothingTextField(
                    value = remoteUrlInput,
                    onValueChange = { remoteUrlInput = it },
                    label = "REMOTE PUBLIC IP / TUNNEL URL",
                    badgeText = "OPTIONAL",
                    placeholder = "https://tg-server.my-domain.ts.net",
                    isMonospace = true,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Paste Tailscale, Cloudflare Tunnel, or Ngrok public URL to stream Stremio away from home.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                DotDivider(dotsCount = 20)
                Spacer(modifier = Modifier.height(16.dp))

                // Start on Boot Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Start Server on Boot",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Auto-wakes streaming engine when phone power is turned on.",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    NothingSwitch(
                        checked = startOnBoot,
                        onCheckedChange = { startOnBoot = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                NothingPillButton(
                    text = "Save Server Settings →",
                    onClick = {
                        val parsedPort = portInput.toIntOrNull() ?: 7788
                        viewModel.updateServerNetwork(parsedPort, selectedBufferKb, remoteUrlInput, startOnBoot)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = NothingButtonVariant.Secondary,
                    height = 46.dp
                )
            }

            // ==================== 6. LIBRARY TOOLS ====================
            SettingsSectionCard(
                icon = Icons.Default.Build,
                title = "LIBRARY TOOLS",
                description = "Inspect media catalog consistency and scan for duplicate or broken entries."
            ) {
                // Duplicate Scan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Check Duplicate Files",
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (duplicateScanCount != null) {
                                NothingDotBadge(
                                    text = if (duplicateScanCount == 0) "0 CLEAN" else "$duplicateScanCount DUPES",
                                    dotColor = if (duplicateScanCount == 0) StatusGreen else StatusAmber,
                                    textColor = if (duplicateScanCount == 0) StatusGreen else StatusAmber
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (duplicateScanCount == null) "Scans for repeated media titles in your catalog."
                            else if (duplicateScanCount == 0) "All media entries in catalog are unique."
                            else "$duplicateScanCount duplicate media entries detected in database.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    NothingPillButton(
                        text = "Scan",
                        onClick = {
                            val res = viewModel.scanDuplicates()
                            duplicateScanCount = res
                            Toast.makeText(context, if (res == 0) "✓ Catalog clean: 0 duplicates" else "⚠ Found $res duplicate entries", Toast.LENGTH_SHORT).show()
                        },
                        variant = NothingButtonVariant.Secondary,
                        height = 34.dp,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                DotDivider(dotsCount = 20)
                Spacer(modifier = Modifier.height(14.dp))

                // Dead Files Scan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Check Dead Files",
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (deadFilesScanCount != null) {
                                NothingDotBadge(
                                    text = if (deadFilesScanCount == 0) "0 DEAD" else "$deadFilesScanCount DEAD",
                                    dotColor = if (deadFilesScanCount == 0) StatusGreen else StatusRed,
                                    textColor = if (deadFilesScanCount == 0) StatusGreen else StatusRed
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (deadFilesScanCount == null) "Checks if channel video messages were deleted."
                            else if (deadFilesScanCount == 0) "All indexed media files are online and valid."
                            else "$deadFilesScanCount inaccessible files detected.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    NothingPillButton(
                        text = "Scan",
                        onClick = {
                            val res = viewModel.scanDeadFiles()
                            deadFilesScanCount = res
                            Toast.makeText(context, if (res == 0) "✓ All indexed media files accessible" else "⚠ Detected $res inaccessible files", Toast.LENGTH_SHORT).show()
                        },
                        variant = NothingButtonVariant.Secondary,
                        height = 34.dp,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }

            // ==================== 7. CHANNEL AUTOMATION ====================
            SettingsSectionCard(
                icon = Icons.Default.Sync,
                title = "CHANNEL AUTOMATION",
                description = "Define rules for monitoring and importing newly posted content from channels."
            ) {
                // Rule 1: Auto Scan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Scan New Content Automatically",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Instantly indexes new video messages posted to monitored channels.",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    NothingSwitch(
                        checked = autoScanNewContent,
                        onCheckedChange = {
                            autoScanNewContent = it
                            viewModel.updateChannelAutomation(it, replaceSameQuality, skipExactDuplicates)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                DotDivider(dotsCount = 20)
                Spacer(modifier = Modifier.height(14.dp))

                // Rule 2: Replace Same Quality
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Replace Same Quality Content",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Replaces old copies when a newer file of identical resolution arrives.",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    NothingSwitch(
                        checked = replaceSameQuality,
                        onCheckedChange = {
                            replaceSameQuality = it
                            viewModel.updateChannelAutomation(autoScanNewContent, it, skipExactDuplicates)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                DotDivider(dotsCount = 20)
                Spacer(modifier = Modifier.height(14.dp))

                // Rule 3: Skip Exact Duplicates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Skip Exact Duplicate Files",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Skips re-indexing if exact file name, size, and hash already exist.",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    NothingSwitch(
                        checked = skipExactDuplicates,
                        onCheckedChange = {
                            skipExactDuplicates = it
                            viewModel.updateChannelAutomation(autoScanNewContent, replaceSameQuality, it)
                        }
                    )
                }
            }

            // ==================== 8. SERVER BACKUP & SYNC ====================
            SettingsSectionCard(
                icon = Icons.Default.Backup,
                title = "SERVER BACKUP & SYNC",
                description = "Snapshot server configurations and channel libraries to local storage."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Automatic Scheduled Backup",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Periodically saves snapshots of your settings and channel catalog.",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    NothingSwitch(
                        checked = autoBackupEnabled,
                        onCheckedChange = {
                            autoBackupEnabled = it
                            viewModel.updateBackupSettings(it, autoBackupHours)
                        }
                    )
                }

                AnimatedVisibility(
                    visible = autoBackupEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        Text(
                            text = "BACKUP FREQUENCY INTERVAL",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val intervals = listOf(1, 2, 6, 12, 24)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            intervals.forEach { h ->
                                val isSelected = autoBackupHours == h
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFFFFFFFF) else SurfaceCardElevated)
                                        .border(1.dp, if (isSelected) Color.Transparent else BorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable {
                                            autoBackupHours = h
                                            viewModel.updateBackupSettings(autoBackupEnabled, h)
                                        }
                                ) {
                                    Text(
                                        text = "${h}h",
                                        color = if (isSelected) Color(0xFF070709) else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                DotDivider(dotsCount = 20)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NothingPillButton(
                        text = "Backup Now",
                        onClick = {
                            val json = viewModel.exportBackupJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("TGServerBackup", json))
                            Toast.makeText(context, "✓ Backup snapshot copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        variant = NothingButtonVariant.Secondary,
                        height = 44.dp,
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.Download, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        }
                    )

                    NothingPillButton(
                        text = "Restore",
                        onClick = {
                            restoreJsonInput = ""
                            showRestoreDialog = true
                        },
                        variant = NothingButtonVariant.Ghost,
                        height = 44.dp,
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.Upload, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ==================== LOGOUT CONFIRMATION DIALOG ====================
    if (showLogoutDialog) {
        BasicAlertDialog(
            onDismissRequest = { showLogoutDialog = false }
        ) {
            NothingCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = SurfaceCard,
                borderColor = BorderSilver,
                contentPadding = PaddingValues(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StatusRed.copy(alpha = 0.15f))
                            .border(1.dp, StatusRed.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = StatusRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Logout Telegram?",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "This will disconnect your account session.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NothingPillButton(
                        text = "Cancel",
                        onClick = { showLogoutDialog = false },
                        variant = NothingButtonVariant.Ghost,
                        height = 40.dp,
                        modifier = Modifier.weight(1f)
                    )

                    NothingPillButton(
                        text = "Logout",
                        onClick = {
                            showLogoutDialog = false
                            viewModel.logout()
                        },
                        variant = NothingButtonVariant.Primary,
                        height = 40.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // ==================== RESTORE BACKUP DIALOG ====================
    if (showRestoreDialog) {
        BasicAlertDialog(
            onDismissRequest = { showRestoreDialog = false }
        ) {
            NothingCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = SurfaceCard,
                borderColor = BorderSilver,
                contentPadding = PaddingValues(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentApple.copy(alpha = 0.15f))
                            .border(1.dp, AccentApple.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = AccentApple,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Restore Configuration",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Paste your exported backup JSON below.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                NothingTextField(
                    value = restoreJsonInput,
                    onValueChange = { restoreJsonInput = it },
                    placeholder = "{\n  \"serverConfig\": { ... }\n}",
                    singleLine = false,
                    isMonospace = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NothingPillButton(
                        text = "Cancel",
                        onClick = { showRestoreDialog = false },
                        variant = NothingButtonVariant.Ghost,
                        height = 42.dp,
                        modifier = Modifier.weight(1f)
                    )

                    NothingPillButton(
                        text = "Restore Now",
                        onClick = {
                            if (restoreJsonInput.isNotBlank()) {
                                val success = viewModel.importBackupJson(restoreJsonInput.trim())
                                if (success) {
                                    showRestoreDialog = false
                                }
                            } else {
                                Toast.makeText(context, "Please paste JSON backup content", Toast.LENGTH_SHORT).show()
                            }
                        },
                        variant = NothingButtonVariant.Primary,
                        height = 42.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    NothingCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = SurfaceCard,
        borderColor = BorderSubtle,
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentSilver,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            color = TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        content()
    }
}

@Composable
private fun FallbackPipelineRow(
    category: String,
    steps: List<String>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                color = TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${steps.size} Stages",
                color = TextTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            steps.forEachIndexed { index, step ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index == 0) SurfaceCardActive else SurfaceCardElevated)
                        .border(
                            0.5.dp,
                            if (index == 0) BorderSilver else BorderSubtle,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = step,
                        color = if (index == 0) TextPrimary else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                if (index < steps.size - 1) {
                    Text(
                        text = "→",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
