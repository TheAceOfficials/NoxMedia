package com.tgserver.streamer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.R
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import com.tgserver.streamer.ui.components.*
import com.tgserver.streamer.update.UpdateUiState
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val NdotFont = FontFamily(Font(R.font.ndot))


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainAppViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val session by viewModel.session.collectAsState()
    val config by viewModel.config.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    // Form inputs state synced with config
    var tmdbKeyInput by remember(config.tmdbApiKey) { mutableStateOf(config.tmdbApiKey) }
    var tvdbKeyInput by remember(config.tvdbApiKey) { mutableStateOf(config.tvdbApiKey) }
    var posterUrlInput by remember(config.customPosterUrl) { mutableStateOf(config.customPosterUrl) }
    var portInput by remember(config.port) { mutableStateOf(config.port.toString()) }
    var cacheMbInput by remember(config.diskCacheMb) { mutableStateOf(config.diskCacheMb.toString()) }
    var bufferMbInput by remember(config.bufferPipelineMb) { mutableStateOf(config.bufferPipelineMb.toString()) }
    var tailscaleIp by remember { mutableStateOf(viewModel.detectTailscaleIp()) }
    var remoteUrlInput by remember(config.remotePublicUrl) { mutableStateOf(config.remotePublicUrl) }
    var selectedQuality by remember(config.preferredQuality) { mutableStateOf(config.preferredQuality) }
    var hideCamTs by remember(config.hideCamTs) { mutableStateOf(config.hideCamTs) }
    var startOnBoot by remember(config.startOnBoot) { mutableStateOf(config.startOnBoot) }
    var autoScanNewContent by remember(config.autoScanNewContent) { mutableStateOf(config.autoScanNewContent) }
    var replaceSameQuality by remember(config.replaceSameQuality) { mutableStateOf(config.replaceSameQuality) }
    var skipExactDuplicates by remember(config.skipExactDuplicates) { mutableStateOf(config.skipExactDuplicates) }
    var autoBackupEnabled by remember(config.autoBackupEnabled) { mutableStateOf(config.autoBackupEnabled) }
    var autoBackupHours by remember(config.autoBackupIntervalHours) { mutableIntStateOf(config.autoBackupIntervalHours) }
    var autoSleepEnabled by remember(config.autoSleepEnabled) { mutableStateOf(config.autoSleepEnabled) }
    var autoSleepMinutes by remember(config.autoSleepTimeoutMinutes) { mutableIntStateOf(config.autoSleepTimeoutMinutes) }

    // Key verification state
    var isVerifyingKeys by remember { mutableStateOf(false) }
    var isEditingTmdb by remember { mutableStateOf(false) }
    var isEditingTvdb by remember { mutableStateOf(false) }
    var tmdbKeyError by remember { mutableStateOf<String?>(null) }
    var tvdbKeyError by remember { mutableStateOf<String?>(null) }

    // Library scan & storage state
    var duplicateScanCount by remember { mutableStateOf<Int?>(null) }
    var deadFilesScanCount by remember { mutableStateOf<Int?>(null) }
    var isScanningDeadFiles by remember { mutableStateOf(false) }
    var cacheSizeBytes by remember { mutableStateOf(viewModel.getAppCacheSizeBytes()) }

    // Card Expansion states (clean minimal collapsed by default)
    var expandedAccount by remember { mutableStateOf(false) }
    var expandedPlayback by remember { mutableStateOf(false) }
    var expandedMetadata by remember { mutableStateOf(false) }
    var expandedArtworks by remember { mutableStateOf(false) }
    var expandedServer by remember { mutableStateOf(false) }
    var expandedStorage by remember { mutableStateOf(false) }
    var expandedAutomation by remember { mutableStateOf(false) }
    var expandedBackup by remember { mutableStateOf(false) }
    var expandedUpdates by remember { mutableStateOf(false) }

    // Dialogs state
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var showLogsScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = showLogsScreen) {
        showLogsScreen = false
    }

    AnimatedContent(
        targetState = showLogsScreen,
        transitionSpec = {
            if (targetState) {
                slideInHorizontally(tween(220)) { it } + fadeIn(tween(180)) togetherWith
                slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(150))
            } else {
                slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(180)) togetherWith
                slideOutHorizontally(tween(200)) { it } + fadeOut(tween(150))
            }
        },
        label = "settings_logs_transition"
    ) { isLogs ->
        if (isLogs) {
            LogsScreen(
                viewModel = viewModel,
                onBack = { showLogsScreen = false }
            )
        } else {
            AdaptiveScreenContainer(applyInsets = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==================== HEADER ====================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontFamily = NdotFont,
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "NoxMedia • Preferences",
                    color = Color(0xFF6B7280),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.2.sp
                )
            }

            // ==================== 1. ACCOUNT (Apple Inset Profile Card) ====================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(BorderStroke(1.dp, Color(0xFF22242D)), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14151B))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedAccount = !expandedAccount }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Avatar Circle
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B1D25))
                                    .border(1.dp, Color(0xFF2E313D), CircleShape)
                            ) {
                                val avatarBitmap = remember(session.avatarUrl) {
                                    val path = session.avatarUrl?.removePrefix("file://")
                                    if (!path.isNullOrBlank()) {
                                        val f = File(path)
                                        if (f.exists() && f.length() > 0) {
                                            try {
                                                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                                            } catch (_: Exception) {
                                                null
                                            }
                                        } else null
                                    } else null
                                }

                                if (avatarBitmap != null) {
                                    Image(
                                        bitmap = avatarBitmap,
                                        contentDescription = "Profile Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = session.initials,
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = session.firstName.ifBlank { "User Account" },
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.3).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (session.username.isNotBlank()) "@${session.username} • Account info & session"
                                           else "Account info, session and settings",
                                    color = Color(0xFF8E909B),
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val chevronRotation by animateFloatAsState(
                            targetValue = if (expandedAccount) 90f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "apple_profile_chevron"
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = if (expandedAccount) "Collapse" else "Expand",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = expandedAccount,
                        enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut(tween(140)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                        ) {
                            HorizontalDivider(
                                color = Color(0xFF22242D),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Apple-style item rows
                            AccountDetailRow(
                                label = "Name",
                                value = session.firstName.ifBlank { "—" }
                            )
                            AccountDetailRow(
                                label = "Username",
                                value = if (session.username.isNotBlank()) "@${session.username}" else "—"
                            )
                            AccountDetailRow(
                                label = "Telegram ID",
                                value = if (session.userId > 0) "${session.userId}" else "—",
                                onCopy = if (session.userId > 0) {
                                    {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Telegram ID", "${session.userId}"))
                                        Toast.makeText(context, "Copied ID", Toast.LENGTH_SHORT).show()
                                    }
                                } else null
                            )
                            AccountDetailRow(
                                label = "Phone",
                                value = session.phoneNumber.ifBlank { "—" }
                            )
                            AccountDetailRow(
                                label = "Status",
                                customValue = {
                                    NothingDotBadge(
                                        text = if (session.isLoggedIn) "CONNECTED" else "OFFLINE",
                                        dotColor = if (session.isLoggedIn) StatusGreen else StatusRed,
                                        backgroundColor = if (session.isLoggedIn) Color(0x1F34C759) else Color(0x1FFF453A),
                                        textColor = if (session.isLoggedIn) StatusGreen else StatusRed
                                    )
                                }
                            )

                            HorizontalDivider(
                                color = Color(0xFF22242D),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )

                            // Clean Log Out Action
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showLogoutDialog = true }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Log Out",
                                    tint = StatusRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Log Out of Telegram Account",
                                    color = StatusRed,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ==================== 2. PLAYBACK ====================
            MinimalSettingsCard(
                icon = Icons.Default.PlayCircle,
                title = "Playback",
                subtitle = "Priority: $selectedQuality • ${if (hideCamTs) "Clean" else "All"}",
                isExpanded = expandedPlayback,
                onToggleExpand = { expandedPlayback = !expandedPlayback }
            ) {
                Text(
                    text = "Preferred Quality Priority",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Selected resolution is sorted to the top in Stremio stream results.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Quality Chips Row
                val qualities = listOf("Any", "4K", "2K", "1080p", "720p", "480p")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    qualities.forEach { q ->
                        val isSelected = selectedQuality == q
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) Color(0xFFFFFFFF) else Color(0xFF1B1D25))
                                .border(1.dp, if (isSelected) Color.Transparent else Color(0xFF2C2F3A), RoundedCornerShape(9.dp))
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

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                SettingToggleRow(
                    title = "Filter CAM & TS",
                    description = "Filters low-quality theatre recordings (CAM, TS) from Stremio stream listings.",
                    checked = hideCamTs,
                    onCheckedChange = {
                        hideCamTs = it
                        viewModel.updateStreamingSettings(selectedQuality, it)
                    }
                )
            }

            // ==================== 3. METADATA ====================
            MinimalSettingsCard(
                icon = Icons.Default.Extension,
                title = "Metadata",
                subtitle = "TMDB & TVDB enrichment",
                isExpanded = expandedMetadata,
                onToggleExpand = { expandedMetadata = !expandedMetadata }
            ) {
                Text(
                    text = "Enriches movies, series, and anime catalogs with synopsis, posters, and IMDb ratings.",
                    color = TextTertiary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (config.tmdbApiKey.isNotBlank() && !isEditingTmdb) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF161820))
                            .border(1.dp, Color(0xFF242732), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "TMDB API KEY",
                                    color = Color(0xFFA0A3B0),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                                NothingDotBadge(
                                    text = "VERIFIED ✓",
                                    dotColor = StatusGreen,
                                    textColor = StatusGreen,
                                    backgroundColor = Color(0x1F34C759)
                                )
                            }

                            NothingPillButton(
                                text = "Change",
                                onClick = {
                                    isEditingTmdb = true
                                    tmdbKeyInput = config.tmdbApiKey
                                },
                                variant = NothingButtonVariant.Ghost,
                                height = 28.dp
                            )
                        }

                        val maskedTmdb = "••••••••••••••••" + if (config.tmdbApiKey.length >= 4) config.tmdbApiKey.takeLast(4) else "••••"
                        Text(
                            text = maskedTmdb,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    NothingTextField(
                        value = tmdbKeyInput,
                        onValueChange = {
                            tmdbKeyInput = it
                            tmdbKeyError = null
                        },
                        label = "TMDB KEY (V3)",
                        badgeText = "MOVIES & SHOWS",
                        placeholder = "Paste your TMDB API Key...",
                        isMonospace = true,
                        singleLine = true,
                        isError = tmdbKeyError != null,
                        errorMessage = tmdbKeyError,
                        trailingIcon = if (isEditingTmdb) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Edit",
                                    tint = TextTertiary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            isEditingTmdb = false
                                            tmdbKeyInput = config.tmdbApiKey
                                            tmdbKeyError = null
                                        }
                                )
                            }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (config.tvdbApiKey.isNotBlank() && !isEditingTvdb) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF161820))
                            .border(1.dp, Color(0xFF242732), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "TVDB API KEY",
                                    color = Color(0xFFA0A3B0),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                                NothingDotBadge(
                                    text = "VERIFIED ✓",
                                    dotColor = StatusGreen,
                                    textColor = StatusGreen,
                                    backgroundColor = Color(0x1F34C759)
                                )
                            }

                            NothingPillButton(
                                text = "Change",
                                onClick = {
                                    isEditingTvdb = true
                                    tvdbKeyInput = config.tvdbApiKey
                                },
                                variant = NothingButtonVariant.Ghost,
                                height = 28.dp
                            )
                        }

                        val maskedTvdb = "••••••••••••••••" + if (config.tvdbApiKey.length >= 4) config.tvdbApiKey.takeLast(4) else "••••"
                        Text(
                            text = maskedTvdb,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    NothingTextField(
                        value = tvdbKeyInput,
                        onValueChange = {
                            tvdbKeyInput = it
                            tvdbKeyError = null
                        },
                        label = "TVDB KEY (V4)",
                        badgeText = "SERIES & ANIME",
                        placeholder = "Paste your TVDB API Key...",
                        isMonospace = true,
                        singleLine = true,
                        isError = tvdbKeyError != null,
                        errorMessage = tvdbKeyError,
                        trailingIcon = if (isEditingTvdb) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Edit",
                                    tint = TextTertiary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            isEditingTvdb = false
                                            tvdbKeyInput = config.tvdbApiKey
                                            tvdbKeyError = null
                                        }
                                )
                            }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cascade Fallback Engine Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161820))
                        .border(1.dp, Color(0xFF242732), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "FALLBACK CHAIN",
                        color = Color(0xFFA0A3B0),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    FallbackPipelineRow(category = "Movies", steps = listOf("TMDB", "Cinemeta"))
                    FallbackPipelineRow(category = "Series", steps = listOf("TVDB", "TMDB", "Cinemeta"))
                    FallbackPipelineRow(category = "Anime", steps = listOf("Kitsu", "TVDB", "TMDB", "Cinemeta"))
                    Text(
                        text = "• Missing keys fallback automatically to next provider.",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                NothingPillButton(
                    text = if (isVerifyingKeys) "Verifying..." else if (isEditingTmdb || isEditingTvdb || config.tmdbApiKey.isBlank() || config.tvdbApiKey.isBlank()) "Save & Verify Provider Keys →" else "Re-verify Provider Keys →",
                    isLoading = isVerifyingKeys,
                    onClick = {
                        coroutineScope.launch {
                            isVerifyingKeys = true
                            tmdbKeyError = null
                            tvdbKeyError = null
                            val (success, msg) = viewModel.verifyAndSaveMetadataKeys(tmdbKeyInput, tvdbKeyInput)
                            isVerifyingKeys = false
                            if (success) {
                                isEditingTmdb = false
                                isEditingTvdb = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                if (msg.contains("TMDB", ignoreCase = true)) {
                                    tmdbKeyError = msg
                                } else if (msg.contains("TVDB", ignoreCase = true)) {
                                    tvdbKeyError = msg
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = NothingButtonVariant.Secondary,
                    height = 42.dp
                )
            }

            // ==================== 4. ARTWORKS ====================
            MinimalSettingsCard(
                icon = Icons.Default.Image,
                title = "Artworks",
                subtitle = if (posterUrlInput.isNotBlank()) "Custom template active" else "Default TMDB / Cinemeta",
                isExpanded = expandedArtworks,
                onToggleExpand = { expandedArtworks = !expandedArtworks }
            ) {
                NothingTextField(
                    value = posterUrlInput,
                    onValueChange = { posterUrlInput = it },
                    label = "POSTER TEMPLATE URL",
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

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Leave empty to use official high-res TMDB & Cinemeta artwork.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                NothingPillButton(
                    text = "Save Template →",
                    onClick = { viewModel.updatePosterSettings(posterUrlInput) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = NothingButtonVariant.Secondary,
                    height = 42.dp
                )
            }

            // ==================== 5. SERVER ====================
            MinimalSettingsCard(
                icon = Icons.Default.Dns,
                title = "Server",
                subtitle = "Port $portInput • Cache ${cacheMbInput}MB" + (if (autoSleepEnabled) " • Sleep ${if (autoSleepMinutes >= 60) "${autoSleepMinutes / 60}h" else "${autoSleepMinutes}m"}" else ""),
                isExpanded = expandedServer,
                onToggleExpand = { expandedServer = !expandedServer }
            ) {
                // Aligned Cache MB, Buffer MB, and Local Port Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        NothingTextField(
                            value = cacheMbInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(Regex("""^-?\d*$"""))) {
                                    cacheMbInput = input
                                }
                            },
                            label = "CACHE MB",
                            placeholder = "2048",
                            isMonospace = true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NothingTextField(
                            value = bufferMbInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(Regex("""^\d*$"""))) {
                                    bufferMbInput = input
                                }
                            },
                            label = "BUFFER MB",
                            placeholder = "20",
                            isMonospace = true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NothingTextField(
                            value = portInput,
                            onValueChange = { portInput = it.filter { c -> c.isDigit() } },
                            label = "PORT",
                            placeholder = "7788",
                            isMonospace = true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "-1 = unlimited cache, 0 = disabled. Smaller values save local storage.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons Row: Save & Restore Defaults
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NothingPillButton(
                        text = "Save Settings →",
                        onClick = {
                            val parsedPort = portInput.toIntOrNull() ?: 7788
                            val parsedCache = cacheMbInput.toIntOrNull() ?: 2048
                            val parsedBuf = bufferMbInput.toIntOrNull() ?: 20
                            viewModel.updateServerNetwork(parsedPort, parsedCache, parsedBuf, remoteUrlInput, startOnBoot)
                        },
                        modifier = Modifier.weight(1.3f),
                        variant = NothingButtonVariant.Primary,
                        height = 42.dp
                    )

                    NothingPillButton(
                        text = "↺ Defaults",
                        onClick = {
                            val restored = viewModel.restoreServerDefaults()
                            portInput = restored.port.toString()
                            cacheMbInput = restored.diskCacheMb.toString()
                            bufferMbInput = restored.bufferPipelineMb.toString()
                            autoSleepEnabled = restored.autoSleepEnabled
                            autoSleepMinutes = restored.autoSleepTimeoutMinutes
                        },
                        modifier = Modifier.weight(1f),
                        variant = NothingButtonVariant.Secondary,
                        height = 42.dp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                // Remote Public IP / Tunnel URL
                NothingTextField(
                    value = remoteUrlInput,
                    onValueChange = { remoteUrlInput = it },
                    label = "REMOTE ACCESS URL",
                    badgeText = "OPTIONAL",
                    placeholder = "https://noxmedia.my-domain.ts.net",
                    isMonospace = true,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tailscale Detection Row
                if (tailscaleIp != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x0DFFFFFF))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Tailscale Detected",
                                color = StatusGreen,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "IP: $tailscaleIp",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        NothingPillButton(
                            text = "Use IP",
                            onClick = {
                                val effectivePort = portInput.ifBlank { "7788" }
                                remoteUrlInput = "http://$tailscaleIp:$effectivePort"
                            },
                            variant = NothingButtonVariant.Ghost,
                            height = 28.dp,
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(TextTertiary)
                        )
                        Text(
                            text = "Tailscale not detected on device",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                // Start on Boot
                SettingToggleRow(
                    title = "Start on Boot",
                    description = "Automatically launches the streaming server in the background when your device reboots.",
                    checked = startOnBoot,
                    onCheckedChange = { startOnBoot = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                // Screen-Off Streaming Mode Row
                val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
                var isIgnoringBattery by remember {
                    mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Screen-Off Mode",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            NothingDotBadge(
                                text = if (isIgnoringBattery) "ACTIVE" else "OPTIMIZED",
                                dotColor = if (isIgnoringBattery) StatusGreen else AccentYellow,
                                textColor = if (isIgnoringBattery) StatusGreen else AccentYellow
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isIgnoringBattery) "Battery optimization ignored. Background streaming will not disconnect."
                            else "Android Doze can cut Wi-Fi when screen is off.",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    if (!isIgnoringBattery) {
                        NothingPillButton(
                            text = "Unrestrict",
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            variant = NothingButtonVariant.Primary,
                            height = 32.dp,
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                // Auto Sleep Server
                SettingToggleRow(
                    title = "Auto Sleep",
                    description = "Automatically stops the server when idle with no active video streaming to conserve battery and resources.",
                    checked = autoSleepEnabled,
                    onCheckedChange = {
                        autoSleepEnabled = it
                        viewModel.updateAutoSleep(it, autoSleepMinutes)
                    }
                )

                AnimatedVisibility(
                    visible = autoSleepEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        val autoSleepOptions = listOf(15, 30, 60, 120, 360)
                        val autoSleepLabels = listOf("15m", "30m", "1h", "2h", "6h")
                        val currentIdx = autoSleepOptions.indexOf(autoSleepMinutes).let { if (it >= 0) it else 1 }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Idle Timeout",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            val badgeLabel = when (autoSleepMinutes) {
                                15 -> "15 MIN"
                                30 -> "30 MIN"
                                60 -> "1 HOUR"
                                120 -> "2 HOURS"
                                360 -> "6 HOURS"
                                else -> "${autoSleepMinutes} MIN"
                            }
                            NothingDotBadge(
                                text = badgeLabel,
                                dotColor = StatusAmber,
                                textColor = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Slider(
                            value = currentIdx.toFloat(),
                            onValueChange = { newIdx ->
                                val rounded = (newIdx + 0.5f).toInt().coerceIn(0, autoSleepOptions.lastIndex)
                                val selectedTimeout = autoSleepOptions[rounded]
                                if (selectedTimeout != autoSleepMinutes) {
                                    autoSleepMinutes = selectedTimeout
                                    viewModel.updateAutoSleep(autoSleepEnabled, selectedTimeout)
                                }
                            },
                            valueRange = 0f..4f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentApple,
                                activeTrackColor = AccentApple,
                                inactiveTrackColor = Color(0xFF2C2F3A),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Labels row for the 5 slider positions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            autoSleepOptions.forEachIndexed { index, minutes ->
                                val isSelected = autoSleepMinutes == minutes
                                Text(
                                    text = autoSleepLabels[index],
                                    color = if (isSelected) TextPrimary else TextTertiary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            autoSleepMinutes = minutes
                                            viewModel.updateAutoSleep(autoSleepEnabled, minutes)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        val durationText = when (autoSleepMinutes) {
                            15 -> "15 minutes"
                            30 -> "30 minutes"
                            60 -> "1 hour"
                            120 -> "2 hours"
                            360 -> "6 hours"
                            else -> "$autoSleepMinutes minutes"
                        }
                        Text(
                            text = "Server will sleep after $durationText of inactivity. Live countdown is displayed in the notification.",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // ==================== 6. STORAGE ====================
            MinimalSettingsCard(
                icon = Icons.Default.FolderOpen,
                title = "Storage",
                subtitle = "Cache: ${formatBytes(cacheSizeBytes)}",
                isExpanded = expandedStorage,
                onToggleExpand = { expandedStorage = !expandedStorage }
            ) {
                // 1. Duplicate Media Files
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "Duplicate Media",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (duplicateScanCount != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            NothingDotBadge(
                                text = if (duplicateScanCount == 0) "0 CLEAN" else "$duplicateScanCount DUPES",
                                dotColor = if (duplicateScanCount == 0) StatusGreen else StatusAmber,
                                textColor = if (duplicateScanCount == 0) StatusGreen else StatusAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (duplicateScanCount == null) "Scan catalog for duplicate entries."
                            else if (duplicateScanCount == 0) "Catalog is clean. No duplicates found."
                            else "$duplicateScanCount duplicates detected.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        NothingPillButton(
                            text = "Scan",
                            onClick = {
                                val res = viewModel.scanDuplicates()
                                duplicateScanCount = res
                                Toast.makeText(context, if (res == 0) "✓ Catalog clean: 0 duplicates" else "⚠ Found $res duplicate entries", Toast.LENGTH_SHORT).show()
                            },
                            variant = NothingButtonVariant.Ghost,
                            height = 32.dp,
                            modifier = Modifier.wrapContentWidth()
                        )

                        NothingPillButton(
                            text = "Delete",
                            onClick = {
                                val res = viewModel.deleteDuplicates()
                                duplicateScanCount = 0
                                Toast.makeText(context, if (res > 0) "✓ Deleted $res duplicate items" else "✓ Catalog clean: 0 duplicates", Toast.LENGTH_SHORT).show()
                            },
                            variant = if (duplicateScanCount != null && duplicateScanCount!! > 0) NothingButtonVariant.Primary else NothingButtonVariant.Secondary,
                            height = 32.dp,
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                // 2. Dead Entries
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "Dead Entries",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (deadFilesScanCount != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            NothingDotBadge(
                                text = if (deadFilesScanCount == 0) "0 DEAD" else "$deadFilesScanCount DEAD",
                                dotColor = if (deadFilesScanCount == 0) StatusGreen else StatusRed,
                                textColor = if (deadFilesScanCount == 0) StatusGreen else StatusRed
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (deadFilesScanCount == null) "Scan library for broken or deleted messages."
                            else if (deadFilesScanCount == 0) "All indexed files are online and valid."
                            else "$deadFilesScanCount broken files detected.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        NothingPillButton(
                            text = if (isScanningDeadFiles) "Scanning..." else "Scan",
                            onClick = {
                                if (isScanningDeadFiles) return@NothingPillButton
                                coroutineScope.launch {
                                    isScanningDeadFiles = true
                                    val res = viewModel.scanDeadFilesRemote()
                                    deadFilesScanCount = res
                                    isScanningDeadFiles = false
                                    Toast.makeText(
                                        context,
                                        if (res == 0) "✓ All indexed media files verified & online" else "⚠ Detected $res deleted or broken files",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            variant = NothingButtonVariant.Ghost,
                            height = 32.dp,
                            modifier = Modifier.wrapContentWidth()
                        )

                        NothingPillButton(
                            text = "Purge",
                            onClick = {
                                val res = viewModel.purgeDeadFiles()
                                deadFilesScanCount = 0
                                Toast.makeText(context, if (res > 0) "✓ Purged $res dead entries" else "✓ Catalog clean: 0 dead files", Toast.LENGTH_SHORT).show()
                            },
                            variant = if (deadFilesScanCount != null && deadFilesScanCount!! > 0) NothingButtonVariant.Primary else NothingButtonVariant.Secondary,
                            height = 32.dp,
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                // 3. Clear Cache
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "App Cache",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Freed image & temporary stream data (${formatBytes(cacheSizeBytes)}).",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    NothingPillButton(
                        text = "Clear Cache",
                        onClick = {
                            val freed = viewModel.clearAppCache()
                            cacheSizeBytes = viewModel.getAppCacheSizeBytes()
                            Toast.makeText(context, "✓ Cache cleared (${formatBytes(freed)} freed)", Toast.LENGTH_SHORT).show()
                        },
                        variant = NothingButtonVariant.Secondary,
                        height = 32.dp,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                // 4. Diagnostic Logs Screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showLogsScreen = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Diagnostic Logs",
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            NothingDotBadge(
                                text = "LOGCAT",
                                dotColor = AccentApple,
                                textColor = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Inspect, search, copy, share, and clear real-time system logs.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    NothingPillButton(
                        text = "View Logs →",
                        onClick = { showLogsScreen = true },
                        variant = NothingButtonVariant.Secondary,
                        height = 32.dp,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }

            // ==================== 7. AUTOMATION ====================
            MinimalSettingsCard(
                icon = Icons.Default.Sync,
                title = "Automation",
                subtitle = "Channel monitoring & duplicate rules",
                isExpanded = expandedAutomation,
                onToggleExpand = { expandedAutomation = !expandedAutomation }
            ) {
                SettingToggleRow(
                    title = "Auto-Scan Channels",
                    description = "Automatically indexes newly posted media files in monitored channels every 30 seconds.",
                    checked = autoScanNewContent,
                    onCheckedChange = {
                        autoScanNewContent = it
                        viewModel.updateChannelAutomation(it, replaceSameQuality, skipExactDuplicates)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                SettingToggleRow(
                    title = "Replace Same Quality",
                    description = "Upgrades or replaces older copies in your library when an identical or better resolution arrives.",
                    checked = replaceSameQuality,
                    onCheckedChange = {
                        replaceSameQuality = it
                        viewModel.updateChannelAutomation(autoScanNewContent, it, skipExactDuplicates)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                SettingToggleRow(
                    title = "Skip Exact Duplicates",
                    description = "Skips re-indexing if the exact same file size and hash already exist in the catalog.",
                    checked = skipExactDuplicates,
                    onCheckedChange = {
                        skipExactDuplicates = it
                        viewModel.updateChannelAutomation(autoScanNewContent, replaceSameQuality, it)
                    }
                )
            }

            // ==================== 8. BACKUP ====================
            MinimalSettingsCard(
                icon = Icons.Default.Backup,
                title = "Backup",
                subtitle = if (autoBackupEnabled) "Scheduled: ${autoBackupHours}h" else "Manual snapshots",
                isExpanded = expandedBackup,
                onToggleExpand = { expandedBackup = !expandedBackup }
            ) {
                SettingToggleRow(
                    title = "Scheduled Backup",
                    description = "Periodically saves local backup snapshots of all server settings and channels.",
                    checked = autoBackupEnabled,
                    onCheckedChange = {
                        autoBackupEnabled = it
                        viewModel.updateBackupSettings(it, autoBackupHours)
                    }
                )

                AnimatedVisibility(
                    visible = autoBackupEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Backup Frequency",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFFFFFFFF) else Color(0xFF1B1D25))
                                        .border(1.dp, if (isSelected) Color.Transparent else Color(0xFF2C2F3A), RoundedCornerShape(8.dp))
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

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NothingPillButton(
                        text = "Backup Now",
                        onClick = {
                            val json = viewModel.exportBackupJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("NoxMediaBackup", json))
                            Toast.makeText(context, "✓ Backup snapshot copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        variant = NothingButtonVariant.Secondary,
                        height = 42.dp,
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
                        height = 42.dp,
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.Upload, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            // ==================== 9. APP UPDATES ====================
            val updateSubtitle = when (val s = updateState) {
                is UpdateUiState.Available -> "Update available: ${s.info.versionTag}"
                is UpdateUiState.Downloading -> "Downloading update..."
                is UpdateUiState.ReadyToInstall -> "Downloaded • Ready to install"
                is UpdateUiState.UpToDate -> "Up to date (v${s.currentVersion})"
                is UpdateUiState.Checking -> "Checking for updates..."
                is UpdateUiState.Error -> "Check failed"
                else -> "v${viewModel.getAppVersion()} • ${viewModel.getDeviceArchitecture()}"
            }

            MinimalSettingsCard(
                icon = Icons.Default.SystemUpdate,
                title = "App & Updates",
                subtitle = updateSubtitle,
                isExpanded = expandedUpdates,
                onToggleExpand = { expandedUpdates = !expandedUpdates }
            ) {
                // Version & Architecture details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Installed Version",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "v${viewModel.getAppVersion()}",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Architecture",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = viewModel.getDeviceArchitecture(),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                // Status view & Actions
                when (val state = updateState) {
                    is UpdateUiState.Available -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    NothingDotBadge(text = "NEW BUILD", dotColor = StatusGreen, textColor = StatusGreen)
                                    Text(
                                        text = state.info.versionTag,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = state.info.formattedSize,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            if (state.info.changelog.isNotBlank()) {
                                Text(
                                    text = state.info.changelog.take(200),
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            NothingPillButton(
                                text = "Download & Install Update",
                                onClick = { viewModel.startDownloadUpdate(state.info) },
                                variant = NothingButtonVariant.Primary,
                                height = 40.dp,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(Icons.Default.Download, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = StatusGreen,
                                trackColor = Color(0xFF26262E)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val downloadedMb = state.downloadedBytes.toDouble() / (1024.0 * 1024.0)
                                val totalMb = state.totalBytes.toDouble() / (1024.0 * 1024.0)
                                Text(
                                    text = String.format(Locale.US, "%.1f / %.1f MB", downloadedMb, totalMb),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    color = StatusGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "✓ APK downloaded successfully. Ready to install.",
                                color = StatusGreen,
                                fontSize = 12.sp
                            )
                            NothingPillButton(
                                text = "Install Now",
                                onClick = { viewModel.installDownloadedApk(state.apkFile) },
                                variant = NothingButtonVariant.Primary,
                                height = 40.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (updateState is UpdateUiState.UpToDate) "You are on the latest version ✓"
                                       else if (updateState is UpdateUiState.Error) "Could not connect to GitHub"
                                       else "Check GitHub Releases for new APK",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )

                            NothingPillButton(
                                text = if (updateState is UpdateUiState.Checking) "Checking..." else "Check Now",
                                onClick = { viewModel.checkForAppUpdate(manual = true) },
                                variant = NothingButtonVariant.Secondary,
                                height = 34.dp,
                                enabled = updateState !is UpdateUiState.Checking,
                                modifier = Modifier.wrapContentWidth()
                            )
                        }
                    }
                }
            }

            // ==================== FOOTER ====================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.instagram.com/theaceofficials")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not open Instagram link", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Designed by",
                        color = Color(0xFF6B7280),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.2.sp
                    )
                    Text(
                        text = "The Ace",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NdotFont,
                        letterSpacing = 0.5.sp
                    )
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Instagram link",
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp)
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

/**
 * Reusable Minimal Settings Card with expandable chevron arrow & detail container.
 */
@Composable
private fun MinimalSettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    actionContent: (@Composable () -> Unit)? = null,
    expandedContent: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chevron_rot"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(BorderStroke(1.dp, Color(0xFF22242D)), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12141A))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFF1B1D25))
                            .border(1.dp, Color(0xFF2A2D38), RoundedCornerShape(11.dp))
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                color = Color(0xFF8E909B),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (actionContent != null) {
                        actionContent()
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B1D25))
                            .rotate(rotation)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color(0xFFA0A3B0),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(140)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0xFF22242D))
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    expandedContent()
                }
            }
        }
    }
}

/**
 * Clean setting toggle row with expandable information capsule on tap.
 */
@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { showInfo = !showInfo }
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Details",
                    tint = if (showInfo) Color.White else TextTertiary,
                    modifier = Modifier.size(15.dp)
                )
            }

            NothingSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }

        AnimatedVisibility(
            visible = showInfo,
            enter = fadeIn(tween(150)) + expandVertically(),
            exit = fadeOut(tween(120)) + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161820))
                    .border(1.dp, Color(0xFF242732), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = description,
                    color = Color(0xFFA0A3B0),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun FallbackPipelineRow(
    category: String,
    steps: List<String>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = category,
            color = TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(46.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            steps.forEachIndexed { index, step ->
                Text(
                    text = step,
                    color = if (index == 0) TextPrimary else TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                )
                if (index < steps.size - 1) {
                    Text(
                        text = "→",
                        color = Color(0xFF4A4A52),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
private fun AccountDetailRow(
    label: String,
    value: String = "",
    onCopy: (() -> Unit)? = null,
    customValue: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF8E909B),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
        if (customValue != null) {
            customValue()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = if (onCopy != null) Modifier.clickable { onCopy() } else Modifier
            ) {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (onCopy != null) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

