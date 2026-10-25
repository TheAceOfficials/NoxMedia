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
import androidx.compose.material.icons.automirrored.filled.*
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
import com.tgserver.streamer.update.ChangeCategory
import com.tgserver.streamer.update.UpdateUiState
import com.tgserver.streamer.update.WhatsNewRepository
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
    val isBackingUpToCloud by viewModel.isBackingUpToCloud.collectAsState()
    val isRestoringFromCloud by viewModel.isRestoringFromCloud.collectAsState()

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
    var backupDestinationInput by remember(config.backupDestination) { mutableStateOf(config.backupDestination) }
    var cloudSyncLibrary by remember(config.cloudSyncLibrary) { mutableStateOf(config.cloudSyncLibrary) }
    var autoSleepEnabled by remember(config.autoSleepEnabled) { mutableStateOf(config.autoSleepEnabled) }
    var autoSleepMinutes by remember(config.autoSleepTimeoutMinutes) { mutableIntStateOf(config.autoSleepTimeoutMinutes) }
    var alexaVoiceControlEnabled by remember(config.alexaVoiceControlEnabled) { mutableStateOf(config.alexaVoiceControlEnabled) }
    var alexaDeviceNameInput by remember(config.alexaDeviceName) { mutableStateOf(config.alexaDeviceName) }
    var showAlexaInfo by remember { mutableStateOf(false) }

    // Key verification state
    var isVerifyingKeys by remember { mutableStateOf(false) }
    var isEditingTmdb by remember { mutableStateOf(false) }
    var isEditingTvdb by remember { mutableStateOf(false) }
    var tmdbKeyError by remember { mutableStateOf<String?>(null) }
    var tvdbKeyError by remember { mutableStateOf<String?>(null) }

    // Library scan & storage state (persisted across background & screen changes)
    val isScanningDuplicates by viewModel.isScanningDuplicates.collectAsState()
    val duplicateScanCount by viewModel.duplicateScanCount.collectAsState()
    val isScanningDeadFiles by viewModel.isScanningDeadFiles.collectAsState()
    val deadFilesScanCount by viewModel.deadFilesScanCount.collectAsState()
    var cacheSizeBytes by remember { mutableStateOf(viewModel.getAppCacheSizeBytes()) }

    // Card Expansion states (clean minimal collapsed by default)
    var expandedAccount by remember { mutableStateOf(false) }
    var expandedPlayback by remember { mutableStateOf(false) }
    var expandedMetadata by remember { mutableStateOf(false) }
    var expandedArtworks by remember { mutableStateOf(false) }
    var expandedServer by remember { mutableStateOf(false) }
    var expandedStorage by remember { mutableStateOf(false) }
    var expandedPerformance by remember { mutableStateOf(false) }
    var expandedAutomation by remember { mutableStateOf(false) }
    var expandedBackup by remember { mutableStateOf(false) }
    var expandedUpdates by remember { mutableStateOf(false) }
    var expandedWhatsNew by remember { mutableStateOf(false) }
    var showArtworkInfo by remember { mutableStateOf(false) }
    var showServerEngineInfo by remember { mutableStateOf(false) }
    var showRemoteAccessInfo by remember { mutableStateOf(false) }
    var showAnimeWorkflowInfo by remember { mutableStateOf(false) }

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
                                    text = "Account Information",
                                    color = Color(0xFF8E909B),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }

                        val chevronRotation by animateFloatAsState(
                            targetValue = if (expandedAccount) 0f else -90f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "apple_profile_chevron"
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (session.isLoggedIn) Color(0xFF34C759) else Color(0xFFFF453A))
                                        )
                                        Text(
                                            text = if (session.isLoggedIn) "Connected" else "Offline",
                                            color = if (session.isLoggedIn) Color(0xFF34C759) else Color(0xFFFF453A),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
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
                        placeholder = "Paste your TMDB API Key",
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
                        placeholder = "Paste your TVDB API Key",
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

                // Anime Indexing Preference Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161820))
                        .border(1.dp, Color(0xFF242732), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showAnimeWorkflowInfo = !showAnimeWorkflowInfo }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "ANIME NUMBERING & SOURCE",
                                color = Color(0xFFA0A3B0),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Anime Workflow Details",
                                tint = if (showAnimeWorkflowInfo) Color.White else TextTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Pill indicating active mode
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (config.animeMetadataPreference == "kitsu") Color(0x22F59E0B) else Color(0x22A855F7),
                            border = BorderStroke(0.5.dp, if (config.animeMetadataPreference == "kitsu") Color(0x40F59E0B) else Color(0x40A855F7))
                        ) {
                            Text(
                                text = if (config.animeMetadataPreference == "kitsu") "ABSOLUTE" else "SEASONAL",
                                color = if (config.animeMetadataPreference == "kitsu") Color(0xFFFBBF24) else Color(0xFFC084FC),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Expandable Info Workflow Explainer
                    AnimatedVisibility(
                        visible = showAnimeWorkflowInfo,
                        enter = fadeIn(tween(150)) + expandVertically(),
                        exit = fadeOut(tween(120)) + shrinkVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF101217))
                                .border(1.dp, Color(0xFF1F222B), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Anime Indexing Workflow:",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• Seasonal (TVDB / TMDB) — Recommended:\nOrganizes multi-season anime into S1, S2, etc. (Western TV standard). Matches Nuvio and Stremio Cinemeta catalogs so Season 2 onwards streams play without conflict.\n\n• Absolute Numbering (Kitsu):\nIndexes episodes continuously (Episode 1 to 1000+). Select this ONLY if you exclusively browse anime using the Stremio Kitsu Addon.",
                                    color = Color(0xFFA0A3B0),
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    // Selector Chips - Balanced, equal height & sleek Nothing/OLED aesthetic
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isSeasonal = config.animeMetadataPreference != "kitsu"

                        // Seasonal Chip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSeasonal) Color(0x22A855F7) else Color(0xFF101217)
                                )
                                .border(
                                    width = if (isSeasonal) 1.5.dp else 1.dp,
                                    color = if (isSeasonal) Color(0xFFA855F7) else Color(0xFF222530),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (!isSeasonal) viewModel.updateAnimeMetadataPreference("seasonal")
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Seasonal",
                                        color = if (isSeasonal) Color.White else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                if (isSeasonal) Color(0xFFC084FC) else Color(0xFF2E3240),
                                                CircleShape
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "TVDB / TMDB",
                                    color = if (isSeasonal) Color(0xFFC084FC) else Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Best for Nuvio & S2+ play",
                                    color = if (isSeasonal) Color(0xFFD8B4FE) else Color(0xFF64748B),
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        // Absolute Chip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (!isSeasonal) Color(0x22F59E0B) else Color(0xFF101217)
                                )
                                .border(
                                    width = if (!isSeasonal) 1.5.dp else 1.dp,
                                    color = if (!isSeasonal) Color(0xFFF59E0B) else Color(0xFF222530),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (isSeasonal) viewModel.updateAnimeMetadataPreference("kitsu")
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Absolute",
                                        color = if (!isSeasonal) Color.White else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                if (!isSeasonal) Color(0xFFFBBF24) else Color(0xFF2E3240),
                                                CircleShape
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Kitsu Catalog",
                                    color = if (!isSeasonal) Color(0xFFFBBF24) else Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Ep 1..1000 continuous",
                                    color = if (!isSeasonal) Color(0xFFFDE68A) else Color(0xFF64748B),
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                    FallbackPipelineRow(
                        category = "Anime",
                        steps = if (config.animeMetadataPreference == "kitsu") {
                            listOf("Kitsu", "TVDB", "TMDB", "Cinemeta")
                        } else {
                            listOf("TVDB", "TMDB", "Kitsu", "Cinemeta")
                        }
                    )
                    Text(
                        text = "• Missing keys fallback automatically to next provider.",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                NothingPillButton(
                    text = if (isVerifyingKeys) "Verifying" else if (isEditingTmdb || isEditingTvdb || config.tmdbApiKey.isBlank() || config.tvdbApiKey.isBlank()) "Save & Verify Provider Keys →" else "Re-verify Provider Keys →",
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
                subtitle = "SpatialPosters and Custom URL",
                isExpanded = expandedArtworks,
                onToggleExpand = { expandedArtworks = !expandedArtworks }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showArtworkInfo = !showArtworkInfo }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Poster Template",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Details",
                            tint = if (showArtworkInfo) Color.White else TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showArtworkInfo,
                    enter = fadeIn(tween(150)) + expandVertically(),
                    exit = fadeOut(tween(120)) + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161820))
                            .border(1.dp, Color(0xFF242732), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Custom poster URL template for movie and series cards. Use {id} as the placeholder for IMDb or TMDB ID. Leave empty to automatically use official TMDB & Cinemeta high-resolution posters.",
                            color = Color(0xFFA0A3B0),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                NothingTextField(
                    value = posterUrlInput,
                    onValueChange = { posterUrlInput = it },
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
                subtitle = "Port $portInput • Cache ${cacheMbInput}MB",
                isExpanded = expandedServer,
                onToggleExpand = { expandedServer = !expandedServer }
            ) {
                // Engine Pipeline Info Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showServerEngineInfo = !showServerEngineInfo }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Engine Pipeline",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Details",
                            tint = if (showServerEngineInfo) Color.White else TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showServerEngineInfo,
                    enter = fadeIn(tween(150)) + expandVertically(),
                    exit = fadeOut(tween(120)) + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161820))
                            .border(1.dp, Color(0xFF242732), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Cache MB: -1 for unlimited disk cache, 0 to disable. Buffer MB: In-memory prefetch buffer for smooth video playback. Port: Local streaming port (default 7788).",
                            color = Color(0xFFA0A3B0),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

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

                // Remote Access URL Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showRemoteAccessInfo = !showRemoteAccessInfo }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Remote Access URL",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Details",
                            tint = if (showRemoteAccessInfo) Color.White else TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    NothingDotBadge(
                        text = "OPTIONAL",
                        dotColor = TextTertiary,
                        textColor = TextTertiary
                    )
                }

                AnimatedVisibility(
                    visible = showRemoteAccessInfo,
                    enter = fadeIn(tween(150)) + expandVertically(),
                    exit = fadeOut(tween(120)) + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161820))
                            .border(1.dp, Color(0xFF242732), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Public domain, reverse proxy, or Tailscale address used by external players to reach your TG-Server remotely outside your home Wi-Fi network.",
                            color = Color(0xFFA0A3B0),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Remote Public IP / Tunnel URL
                NothingTextField(
                    value = remoteUrlInput,
                    onValueChange = { remoteUrlInput = it },
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

                SettingActionRow(
                    title = "Screen-Off Mode",
                    description = if (isIgnoringBattery) "Battery optimization ignored. Background streaming will not disconnect even when device screen is turned off."
                                  else "Android Doze can terminate background streaming when the screen is locked. Tap Unrestrict to permit uninterrupted background streaming.",
                    badge = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(top = 1.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(if (isIgnoringBattery) Color(0xFF00E676) else AccentYellow, CircleShape)
                            )
                            Text(
                                text = if (isIgnoringBattery) "Active (Unrestricted)" else "Optimized (Restricted)",
                                color = if (isIgnoringBattery) Color(0xFF00E676) else AccentYellow,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    },
                    action = {
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
                )

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
                SettingActionRow(
                    title = "Duplicate Media",
                    description = "Scans library for duplicate entries with identical file size and hash, and lets you safely delete redundant records.",
                    badge = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(top = 1.dp)
                        ) {
                            val dotColor = when {
                                isScanningDuplicates -> AccentYellow
                                duplicateScanCount == null -> TextTertiary
                                duplicateScanCount == 0 -> Color(0xFF00E676)
                                else -> StatusAmber
                            }
                            val statusText = when {
                                isScanningDuplicates -> "Scanning duplicates..."
                                duplicateScanCount == null -> "Not scanned"
                                duplicateScanCount == 0 -> "Clean (0 duplicates)"
                                duplicateScanCount == 1 -> "1 duplicate found"
                                else -> "$duplicateScanCount duplicates found"
                            }
                            val textColor = when {
                                isScanningDuplicates -> AccentYellow
                                duplicateScanCount == null -> TextTertiary
                                duplicateScanCount == 0 -> Color(0xFF00E676)
                                else -> StatusAmber
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Text(
                                text = statusText,
                                color = textColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    },
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            NothingPillButton(
                                text = if (isScanningDuplicates) "Scanning" else "Scan",
                                isLoading = isScanningDuplicates,
                                enabled = !isScanningDuplicates,
                                onClick = { viewModel.startBackgroundDuplicateScan() },
                                variant = NothingButtonVariant.Ghost,
                                height = 32.dp,
                                modifier = Modifier.wrapContentWidth()
                            )

                            NothingPillButton(
                                text = "Delete",
                                enabled = !isScanningDuplicates && duplicateScanCount != null && duplicateScanCount!! > 0,
                                onClick = {
                                    val res = viewModel.deleteDuplicates()
                                    Toast.makeText(context, if (res > 0) "✓ Deleted $res duplicate items" else "✓ Catalog clean: 0 duplicates", Toast.LENGTH_SHORT).show()
                                },
                                variant = if (duplicateScanCount != null && duplicateScanCount!! > 0) NothingButtonVariant.Primary else NothingButtonVariant.Secondary,
                                height = 32.dp,
                                modifier = Modifier.wrapContentWidth()
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                // 2. Dead Entries
                SettingActionRow(
                    title = "Dead Entries",
                    description = "Scans library for broken or deleted messages in Telegram channels, and clears inaccessible files.",
                    badge = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(top = 1.dp)
                        ) {
                            val dotColor = when {
                                isScanningDeadFiles -> AccentYellow
                                deadFilesScanCount == null -> TextTertiary
                                deadFilesScanCount == 0 -> Color(0xFF00E676)
                                else -> StatusRed
                            }
                            val statusText = when {
                                isScanningDeadFiles -> "Scanning channels..."
                                deadFilesScanCount == null -> "Not scanned"
                                deadFilesScanCount == 0 -> "Clean (0 dead entries)"
                                deadFilesScanCount == 1 -> "1 dead entry found"
                                else -> "$deadFilesScanCount dead entries found"
                            }
                            val textColor = when {
                                isScanningDeadFiles -> AccentYellow
                                deadFilesScanCount == null -> TextTertiary
                                deadFilesScanCount == 0 -> Color(0xFF00E676)
                                else -> StatusRed
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Text(
                                text = statusText,
                                color = textColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    },
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            NothingPillButton(
                                text = if (isScanningDeadFiles) "Scanning" else "Scan",
                                isLoading = isScanningDeadFiles,
                                enabled = !isScanningDeadFiles,
                                onClick = { viewModel.startBackgroundDeadFilesScan() },
                                variant = NothingButtonVariant.Ghost,
                                height = 32.dp,
                                modifier = Modifier.wrapContentWidth()
                            )

                            NothingPillButton(
                                text = "Clear",
                                enabled = !isScanningDeadFiles && deadFilesScanCount != null && deadFilesScanCount!! > 0,
                                onClick = {
                                    val res = viewModel.purgeDeadFiles()
                                    Toast.makeText(context, if (res > 0) "✓ Cleared $res dead entries" else "✓ Catalog clean: 0 dead files", Toast.LENGTH_SHORT).show()
                                },
                                variant = if (deadFilesScanCount != null && deadFilesScanCount!! > 0) NothingButtonVariant.Primary else NothingButtonVariant.Secondary,
                                height = 32.dp,
                                modifier = Modifier.wrapContentWidth()
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                // 3. Clear Cache
                SettingActionRow(
                    title = "App Cache",
                    description = "Image cache and temporary streaming chunks (${formatBytes(cacheSizeBytes)}). Clearing cache frees storage without affecting your saved media catalog.",
                    action = {
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
                )

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                // 4. Diagnostic Logs Screen
                SettingActionRow(
                    title = "Diagnostic Logs",
                    description = "Inspect, search, copy, share, and clear real-time system logs and engine streaming activity.",
                    action = {
                        NothingPillButton(
                            text = "View Logs →",
                            onClick = { showLogsScreen = true },
                            variant = NothingButtonVariant.Secondary,
                            height = 32.dp,
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                )
            }

            // ==================== 7. PERFORMANCE & SMOOTHNESS ====================
            val is32Bit = viewModel.is32BitDevice
            val totalRam = viewModel.totalMemoryMb
            val maxHeap = viewModel.maxHeapMb
            val isBoostActive = config.lowEndOptimization

            MinimalSettingsCard(
                icon = Icons.Default.Speed,
                title = "Performance & Smoothness",
                subtitle = "${if (is32Bit) "32-bit Architecture" else "64-bit Architecture"} • ${if (isBoostActive) "Low-End Boost Active" else "Standard HD Mode"}",
                isExpanded = expandedPerformance,
                onToggleExpand = { expandedPerformance = !expandedPerformance }
            ) {
                // Hardware Diagnostic Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141620))
                        .border(0.5.dp, Color(0xFF25293A), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HARDWARE PROFILE",
                            color = TextTertiary,
                            fontSize = 9.5.sp,
                            fontFamily = NdotFont,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${if (is32Bit) "32-bit CPU" else "64-bit CPU"} • $totalRam MB RAM • ${maxHeap}MB Heap",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isBoostActive) Color(0xFF1B381E) else Color(0xFF1B2638))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isBoostActive) "BOOST ON" else "HD READY",
                            color = if (isBoostActive) Color(0xFF00E676) else Color(0xFF64B5F6),
                            fontSize = 10.sp,
                            fontFamily = NdotFont,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                SettingToggleRow(
                    title = "Low-End Device Boost",
                    description = "Downsamples poster artwork, decodes bitmaps in 16-bit RGB_565 (saving 50-80% RAM), bounds image caches, and streamlines tab transitions to zero-overhead crossfades. Recommended for 32-bit and budget devices.",
                    checked = isBoostActive,
                    onCheckedChange = {
                        viewModel.toggleLowEndOptimization()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                // Diagnostic / Explanatory Note
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F1118))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Text(
                        text = if (is32Bit)
                            "Your device is running 32-bit Android with strict memory limits (~${maxHeap}MB). Low-End Boost prevents OutOfMemoryError crashes and maintains 60 FPS smooth scrolling."
                        else
                            "On 64-bit hardware with abundant RAM, you can leave this off for full 32-bit color fidelity (ARGB_8888) and animated screen scaling, or enable it for maximum battery and memory savings.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // ==================== 8. AUTOMATION ====================
            MinimalSettingsCard(
                icon = Icons.Default.Sync,
                title = "Automation",
                subtitle = null,
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

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(12.dp))

                // Alexa Voice Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showAlexaInfo = !showAlexaInfo }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "Alexa Voice Control",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Alexa Setup Guide",
                                tint = if (showAlexaInfo) Color.White else TextTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (alexaVoiceControlEnabled)
                                "Zero-cloud local WeMo emulation. Turn server on or off via Echo."
                            else
                                "Turn streaming server on and off with Echo voice commands over local Wi-Fi.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    NothingSwitch(
                        checked = alexaVoiceControlEnabled,
                        onCheckedChange = {
                            alexaVoiceControlEnabled = it
                            viewModel.updateAlexaVoiceControl(it, alexaDeviceNameInput)
                        }
                    )
                }

                // Expandable info box for Alexa
                AnimatedVisibility(
                    visible = showAlexaInfo,
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
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "HOW TO CONNECT WITH AMAZON ECHO",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "1. Connect this phone and your Amazon Echo to the same Wi-Fi network.\n" +
                                       "2. Turn on the Alexa toggle above.\n" +
                                       "3. Say \"Alexa, discover devices\" (or tap Discover in the Alexa app).\n" +
                                       "4. Echo discovers NoxMedia as a local smart plug. No Amazon skill, no AWS account, and zero cloud required!",
                                color = Color(0xFFA0A3B0),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = alexaVoiceControlEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Device Name input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Echo Device Name",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            NothingDotBadge(
                                text = "ECHO READY",
                                dotColor = StatusGreen,
                                textColor = TextPrimary
                            )
                        }

                        NothingTextField(
                            value = alexaDeviceNameInput,
                            onValueChange = {
                                alexaDeviceNameInput = it
                                viewModel.updateAlexaVoiceControl(alexaVoiceControlEnabled, it)
                            },
                            placeholder = "NoxMedia (or Server)",
                            singleLine = true,
                            trailingIcon = if (alexaDeviceNameInput.isNotBlank()) {
                                {
                                    IconButton(
                                        onClick = {
                                            alexaDeviceNameInput = ""
                                            viewModel.updateAlexaVoiceControl(alexaVoiceControlEnabled, "")
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = TextTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else null
                        )

                        // Voice commands cheat sheet
                        val spokenName = alexaDeviceNameInput.trim().ifBlank { "NoxMedia" }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF101217))
                                .border(1.dp, Color(0xFF1F222B), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "SPOKEN COMMANDS",
                                    color = TextTertiary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "• \"Alexa, turn on $spokenName\"\n• \"Alexa, turn off $spokenName\"\n• \"Alexa, is $spokenName on?\"",
                                    color = Color(0xFF86EFAC),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // ==================== 8. BACKUP ====================
            MinimalSettingsCard(
                icon = Icons.Default.Backup,
                title = "True Cloud Sync",
                subtitle = if (autoBackupEnabled) "Live sync active (${autoBackupHours}h / auto-push)" else "Manual & Cloud backup",
                isExpanded = expandedBackup,
                onToggleExpand = { expandedBackup = !expandedBackup }
            ) {
                SettingToggleRow(
                    title = "Auto-Backup & True Sync",
                    description = "Pushes compressed Gzip snapshots to Telegram when new videos are scanned or metadata matches are fixed.",
                    checked = autoBackupEnabled,
                    onCheckedChange = {
                        autoBackupEnabled = it
                        viewModel.updateBackupSettings(it, autoBackupHours, backupDestinationInput, cloudSyncLibrary)
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
                                            viewModel.updateBackupSettings(autoBackupEnabled, h, backupDestinationInput, cloudSyncLibrary)
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

                // Full Library & Metadata Match Sync Toggle
                SettingToggleRow(
                    title = "Full Library & Metadata Match Sync",
                    description = "Includes all indexed movies, series, posters, and your manual rematch fixes in cloud snapshots. Instant multi-device sync without re-scanning.",
                    checked = cloudSyncLibrary,
                    onCheckedChange = {
                        cloudSyncLibrary = it
                        viewModel.updateBackupSettings(autoBackupEnabled, autoBackupHours, backupDestinationInput, it)
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                // Backup Destination Chat Input
                Text(
                    text = "Backup Destination Chat (Optional)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Leave empty to use Telegram Saved Messages, or enter a dedicated Channel ID (-100...) or @username.",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        NothingTextField(
                            value = backupDestinationInput,
                            onValueChange = { backupDestinationInput = it },
                            placeholder = "Saved Messages (default)",
                            isMonospace = true,
                            singleLine = true,
                            trailingIcon = if (backupDestinationInput.isNotBlank()) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextTertiary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                backupDestinationInput = ""
                                                viewModel.updateBackupSettings(
                                                    autoBackupEnabled,
                                                    autoBackupHours,
                                                    "",
                                                    cloudSyncLibrary
                                                )
                                            }
                                    )
                                }
                            } else null
                        )
                    }

                    NothingPillButton(
                        text = "Save",
                        onClick = {
                            viewModel.updateBackupSettings(
                                autoBackupEnabled,
                                autoBackupHours,
                                backupDestinationInput.trim(),
                                cloudSyncLibrary
                            )
                        },
                        variant = NothingButtonVariant.Secondary,
                        height = 48.dp,
                        modifier = Modifier.width(76.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                // Primary Cloud Row
                Text(
                    text = "Telegram Cloud Actions",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NothingPillButton(
                        text = if (isBackingUpToCloud) "Uploading..." else "Push Cloud Backup",
                        onClick = { viewModel.backupToTelegramCloud() },
                        enabled = !isBackingUpToCloud && !isRestoringFromCloud,
                        variant = NothingButtonVariant.Primary,
                        height = 42.dp,
                        fontSize = 11.5.sp,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.CloudUpload, null, tint = Color.Black, modifier = Modifier.size(15.dp))
                        }
                    )

                    NothingPillButton(
                        text = if (isRestoringFromCloud) "Syncing..." else "Pull & Sync",
                        onClick = { viewModel.syncFromCloud(silent = false) },
                        enabled = !isBackingUpToCloud && !isRestoringFromCloud,
                        variant = NothingButtonVariant.Secondary,
                        height = 42.dp,
                        fontSize = 11.5.sp,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.CloudDownload, null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Offline Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NothingPillButton(
                        text = "Copy JSON",
                        onClick = {
                            val json = viewModel.exportBackupJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("NoxMediaBackup", json))
                            Toast.makeText(context, "✓ Backup snapshot copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        variant = NothingButtonVariant.Ghost,
                        height = 38.dp,
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    )

                    NothingPillButton(
                        text = "Paste JSON",
                        onClick = {
                            restoreJsonInput = ""
                            showRestoreDialog = true
                        },
                        variant = NothingButtonVariant.Ghost,
                        height = 38.dp,
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.Upload, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Backup Scope Info Note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF14161E))
                        .border(1.dp, Color(0xFF22242F), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AccentApple,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bi-Directional Multi-Device True Sync",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All your phones, Android TVs, and tablets automatically stay in true sync. When you scan channels or fix metadata matches on any device, snapshots are compressed (~90% smaller) and synced across all your connected screens without re-scanning.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // ==================== 9. APP UPDATES ====================
            val updateSubtitle = when (val s = updateState) {
                is UpdateUiState.Available -> "Update available: ${s.info.versionTag}"
                is UpdateUiState.Downloading -> "Downloading update"
                is UpdateUiState.ReadyToInstall -> "Downloaded • Ready to install"
                is UpdateUiState.UpToDate -> "Up to date (v${s.currentVersion})"
                is UpdateUiState.Checking -> "Checking for updates"
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0x1F22C55E),
                                        border = BorderStroke(0.5.dp, Color(0x4022C55E))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(Color(0xFF22C55E), CircleShape)
                                            )
                                            Text(
                                                text = "Update",
                                                color = Color(0xFF4ADE80),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.2.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = state.info.versionTag,
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
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
                                val cleanChangelog = remember(state.info.changelog) {
                                    state.info.changelog
                                        .replace("📱", "")
                                        .replace("📦", "")
                                        .replace("- ", "• ")
                                        .trim()
                                }
                                Text(
                                    text = cleanChangelog.take(220),
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
                                text = if (updateState is UpdateUiState.Checking) "Checking" else "Check Now",
                                onClick = { viewModel.checkForAppUpdate(manual = true) },
                                variant = NothingButtonVariant.Secondary,
                                height = 34.dp,
                                enabled = updateState !is UpdateUiState.Checking,
                                modifier = Modifier.wrapContentWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Beta Channel Enrollment Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Early Beta Access",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (config.receiveBetaUpdates) {
                                Spacer(modifier = Modifier.width(7.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0x24A855F7),
                                    border = BorderStroke(0.7.dp, Color(0x66C084FC))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(Color(0xFFC084FC), CircleShape)
                                        )
                                        Text(
                                            text = "BETA ACTIVE",
                                            color = Color(0xFFE9D5FF),
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Receive experimental features and pre-release updates before official rollout.",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }

                    Switch(
                        checked = config.receiveBetaUpdates,
                        onCheckedChange = { viewModel.updateReceiveBetaUpdates(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFA855F7),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF1E1F27)
                        )
                    )
                }

                // ==================== WHAT'S NEW SECTION ====================
                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF22242D)))
                Spacer(modifier = Modifier.height(14.dp))

                val whatsNewCategories = remember { WhatsNewRepository.getItemsByCategory() }
                val newFeaturesCount = whatsNewCategories[ChangeCategory.NEW]?.size ?: 0

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Bar (Clickable to toggle expand)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF14151B))
                            .border(BorderStroke(0.5.dp, Color(0xFF22242D)), RoundedCornerShape(10.dp))
                            .clickable { expandedWhatsNew = !expandedWhatsNew }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FBBF24)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Text(
                                text = "What's New!",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (newFeaturesCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0x1F22C55E),
                                    border = BorderStroke(0.5.dp, Color(0x4022C55E))
                                ) {
                                    Text(
                                        text = "$newFeaturesCount New",
                                        color = Color(0xFF4ADE80),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (expandedWhatsNew) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expandedWhatsNew) "Collapse" else "Expand",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Expanded What's New items
                    AnimatedVisibility(
                        visible = expandedWhatsNew,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            whatsNewCategories.forEach { (category, items) ->
                                if (items.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Category Pill (Premium Clean Dot without emojis)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(category.hexColor).copy(alpha = 0.12f),
                                            border = BorderStroke(0.5.dp, Color(category.hexColor).copy(alpha = 0.35f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .background(Color(category.hexColor), CircleShape)
                                                )
                                                Text(
                                                    text = "${category.label.uppercase()} (${items.size})",
                                                    color = Color(category.hexColor),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.4.sp
                                                )
                                            }
                                        }

                                        // Category items
                                        items.forEach { item ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF101217))
                                                    .border(BorderStroke(0.5.dp, Color(0xFF1F2128)), RoundedCornerShape(8.dp))
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.Top,
                                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(top = 5.dp)
                                                            .size(5.dp)
                                                            .background(Color(category.hexColor), CircleShape)
                                                    )
                                                    Text(
                                                        text = item.title,
                                                        color = TextPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        lineHeight = 16.sp
                                                    )
                                                }

                                                if (!item.description.isNullOrBlank()) {
                                                    Text(
                                                        text = item.description,
                                                        color = TextSecondary,
                                                        fontSize = 11.sp,
                                                        lineHeight = 15.sp,
                                                        modifier = Modifier.padding(start = 12.dp)
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

            // ==================== FOOTER ====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Designed by The Ace",
                    color = Color(0xFF6B7280),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
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
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
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

/**
 * Clean setting action row with title, optional badge, expandable info capsule on tap, and action controls.
 */
@Composable
private fun SettingActionRow(
    title: String,
    description: String,
    badge: @Composable () -> Unit = {},
    action: @Composable () -> Unit = {}
) {
    var showInfo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
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
                        modifier = Modifier.size(14.dp)
                    )
                }
                badge()
            }

            Spacer(modifier = Modifier.width(10.dp))
            action()
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

