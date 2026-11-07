package com.tgserver.streamer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import com.tgserver.streamer.ui.components.*
import kotlinx.coroutines.launch

data class ParsedLogItem(
    val raw: String,
    val timestamp: String = "",
    val level: String = "I",
    val tag: String = "",
    val message: String = "",
    val isStreamLog: Boolean = false,
    val isDropOrTimeout: Boolean = false,
    val isSpeedMetric: Boolean = false
)

private val LOGCAT_REGEX = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^:(]+)(?:\(\s*\d+\))?:\s*(.*)$""")

private fun parseLogItem(line: String): ParsedLogItem {
    val trimmed = line.trim()
    val match = LOGCAT_REGEX.matchEntire(trimmed)
    val (time, level, tag, msg) = if (match != null) {
        val (t, l, tg, m) = match.destructured
        Quad(t, l.uppercase(), tg.trim(), m)
    } else {
        val lvl = when {
            trimmed.contains(" E/") || trimmed.startsWith("E/") || trimmed.contains("Exception") || trimmed.contains("Error") -> "E"
            trimmed.contains(" W/") || trimmed.startsWith("W/") || trimmed.contains("Warning") || trimmed.contains("⚠️") -> "W"
            trimmed.contains(" D/") || trimmed.startsWith("D/") -> "D"
            trimmed.contains(" V/") || trimmed.startsWith("V/") -> "V"
            else -> "I"
        }
        Quad("", lvl, "", trimmed)
    }

    val lowerRaw = line.lowercase()
    val isStream = lowerRaw.contains("stream") || lowerRaw.contains("chunk") || lowerRaw.contains("http") ||
            lowerRaw.contains("range") || lowerRaw.contains("tg_bridge") || lowerRaw.contains("tgmediainputstream") ||
            lowerRaw.contains("exoplayer") || lowerRaw.contains("dc_id") || lowerRaw.contains("dc ")

    val isDrop = lowerRaw.contains("packet loss") || lowerRaw.contains("timed out") || lowerRaw.contains("timeout") ||
            lowerRaw.contains("underrun") || lowerRaw.contains("retry") || lowerRaw.contains("stalled") ||
            lowerRaw.contains("resetting socket") || lowerRaw.contains("exhausted retries")

    val isSpeed = lowerRaw.contains("mb/s") || lowerRaw.contains("kb/s") || lowerRaw.contains("prefetched task") ||
            lowerRaw.contains("delivered to player") || lowerRaw.contains("progress:")

    return ParsedLogItem(
        raw = line,
        timestamp = time,
        level = level,
        tag = tag,
        message = msg,
        isStreamLog = isStream,
        isDropOrTimeout = isDrop,
        isSpeedMetric = isSpeed
    )
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: MainAppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val rawLogs by viewModel.appLogs.collectAsState()
    val isRefreshing by viewModel.isRefreshingLogs.collectAsState()
    val isExportingLogs by viewModel.isExportingLogs.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val config by viewModel.config.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableStateOf("ALL") }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-refresh logs on entrance
    LaunchedEffect(Unit) {
        viewModel.refreshAppLogs()
    }

    // Parse logs
    val parsedLogs = remember(rawLogs) {
        rawLogs.map { parseLogItem(it) }
    }

    // Filter logs based on active tab and search
    val filteredLogs = remember(parsedLogs, searchQuery, selectedFilterTab) {
        parsedLogs.filter { item ->
            val matchesTab = when (selectedFilterTab) {
                "ALL" -> true
                "STREAM" -> item.isStreamLog
                "DROPS" -> item.isDropOrTimeout || item.level == "E" || item.level == "W"
                "SPEED" -> item.isSpeedMetric
                "ERRORS" -> item.level == "E" || item.level == "F"
                else -> true
            }

            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                item.raw.contains(searchQuery, ignoreCase = true)
            }

            matchesTab && matchesSearch
        }
    }

    // Diagnostic Metrics calculations
    val dropsCount = remember(parsedLogs) {
        parsedLogs.count { it.isDropOrTimeout }
    }
    val streamEventsCount = remember(parsedLogs) {
        parsedLogs.count { it.isStreamLog }
    }
    val errorsCount = remember(parsedLogs) {
        parsedLogs.count { it.level == "E" || it.level == "F" }
    }

    // Auto-scroll to bottom when new logs arrive (if auto-scroll is enabled)
    LaunchedEffect(filteredLogs.size, autoScrollEnabled) {
        if (autoScrollEnabled && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    // Refresh animation spin
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                }
            }
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp)
    ) {
        // ==================== 1. TOP APP BAR ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Back Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14151B))
                        .border(1.dp, Color(0xFF262833), CircleShape)
                        .clickable { onBack() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Stream Diagnostics",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                        NothingDotBadge(
                            text = "${filteredLogs.size}",
                            dotColor = if (dropsCount > 0) StatusAmber else StatusGreen,
                            backgroundColor = Color(0x18FFFFFF),
                            textColor = TextSecondary
                        )
                    }
                    Text(
                        text = "MTProto Striping • ExoPlayer Pipeline • Socket Telemetry",
                        color = TextTertiary,
                        fontSize = 10.5.sp
                    )
                }
            }

            // Quick Actions: Refresh, Clear
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Refresh Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14151B))
                        .border(1.dp, Color(0xFF262833), CircleShape)
                        .clickable { viewModel.refreshAppLogs() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isRefreshing) AccentApple else TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (isRefreshing) spinAngle else 0f)
                    )
                }

                // Clear Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1418))
                        .border(1.dp, StatusRed.copy(alpha = 0.35f), CircleShape)
                        .clickable { showClearDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Logs",
                        tint = StatusRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ==================== 2. TELEGRAM EXPORT & ACTION BAR ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Export to Telegram (Saved Messages)" Button
            NothingPillButton(
                text = if (isExportingLogs) "Exporting to Telegram..." else "Send to Saved Messages",
                onClick = {
                    viewModel.exportLogsToTelegram { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !isExportingLogs,
                variant = NothingButtonVariant.Primary,
                height = 38.dp,
                fontSize = 11.5.sp,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.weight(1.3f),
                leadingIcon = {
                    if (isExportingLogs) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            )

            // Copy All Button
            NothingPillButton(
                text = "Copy All",
                onClick = {
                    if (filteredLogs.isEmpty()) {
                        Toast.makeText(context, "No logs to copy", Toast.LENGTH_SHORT).show()
                    } else {
                        val fullText = filteredLogs.joinToString("\n") { it.raw }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("NoxMediaLogs", fullText))
                        Toast.makeText(context, "✓ Copied ${filteredLogs.size} lines to clipboard", Toast.LENGTH_SHORT).show()
                    }
                },
                variant = NothingButtonVariant.Secondary,
                height = 38.dp,
                fontSize = 11.5.sp,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(0.85f),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = TextPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            )

            // Share Button
            NothingPillButton(
                text = "Share",
                onClick = {
                    if (filteredLogs.isEmpty()) {
                        Toast.makeText(context, "No logs to share", Toast.LENGTH_SHORT).show()
                    } else {
                        val fullText = filteredLogs.joinToString("\n") { it.raw }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, fullText)
                            putExtra(Intent.EXTRA_TITLE, "NoxMedia Diagnostic Logs")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share NoxMedia Logs")
                        context.startActivity(shareIntent)
                    }
                },
                variant = NothingButtonVariant.Ghost,
                height = 38.dp,
                fontSize = 11.5.sp,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(0.75f),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            )
        }

        // ==================== 3. DIAGNOSTICS HUD CARDS ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Metric 1: Stream Engine Status
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101218))
                    .border(1.dp, Color(0xFF1F222D), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Column {
                    Text(
                        text = "BUFFER PIPE",
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (telemetry.isRunning) StatusGreen else StatusAmber, CircleShape)
                        )
                        Text(
                            text = "${config.bufferPipelineMb} MB",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Metric 2: Stream Activity
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101218))
                    .border(1.dp, Color(0xFF1F222D), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Column {
                    Text(
                        text = "STREAM EVENTS",
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$streamEventsCount",
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Metric 3: Packet Drops / Timeouts
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (dropsCount > 0) Color(0x22FF5252) else Color(0xFF101218))
                    .border(
                        1.dp,
                        if (dropsCount > 0) StatusRed.copy(alpha = 0.5f) else Color(0xFF1F222D),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Column {
                    Text(
                        text = "PACKET DROPS",
                        color = if (dropsCount > 0) Color(0xFFFF8A80) else TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$dropsCount",
                        color = if (dropsCount > 0) StatusRed else StatusGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // ==================== 4. SEARCH BAR ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF12141A))
                .border(1.dp, Color(0xFF22242D), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(15.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Filter logs (e.g. chunk, packet loss, timeout, DC2, underrun)...",
                            color = TextTertiary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        cursorBrush = SolidColor(TextPrimary),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22242D))
                            .clickable { searchQuery = "" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==================== 5. SMART FILTER TABS ====================
        val filterTabs = listOf(
            Triple("ALL", "All", TextSecondary),
            Triple("STREAM", "Streaming", Color(0xFF00E5FF)),
            Triple("DROPS", "Drops & Retries", StatusRed),
            Triple("SPEED", "Speed & MB/s", Color(0xFF69F0AE)),
            Triple("ERRORS", "Errors", StatusAmber)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            filterTabs.forEach { (code, label, dotColor) ->
                val isSelected = selectedFilterTab == code
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(29.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF242734) else Color(0xFF101217))
                        .border(
                            1.dp,
                            if (isSelected) AccentApple.copy(alpha = 0.5f) else Color(0xFF1B1E26),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedFilterTab = code }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (code != "ALL") {
                            Box(
                                modifier = Modifier
                                    .size(4.5.dp)
                                    .background(dotColor, CircleShape)
                            )
                        }
                        Text(
                            text = label,
                            color = if (isSelected) TextPrimary else TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==================== 6. LOGS CONSOLE LIST ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF090A0E))
                .border(1.dp, Color(0xFF1A1C24), RoundedCornerShape(12.dp))
        ) {
            if (filteredLogs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        tint = TextTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedFilterTab != "ALL") "No matching logs found" else "Log buffer is clean",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try clearing search filter" else "Streaming and MTProto activity will appear here in real time",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filteredLogs) { _, item ->
                        LogLineCard(item = item, onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LogLine", item.raw))
                            Toast.makeText(context, "✓ Copied log line", Toast.LENGTH_SHORT).show()
                        })
                    }
                }

                // Floating Action Bar inside Console (Auto-scroll toggle + Jump to bottom)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Auto-Scroll Toggle Pill
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (autoScrollEnabled) Color(0xEE1E2230) else Color(0xEE14161E))
                            .border(
                                1.dp,
                                if (autoScrollEnabled) StatusGreen.copy(alpha = 0.4f) else BorderSilver,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { autoScrollEnabled = !autoScrollEnabled }
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(if (autoScrollEnabled) StatusGreen else TextTertiary, CircleShape)
                            )
                            Text(
                                text = if (autoScrollEnabled) "LIVE AUTO-SCROLL" else "SCROLL PAUSED",
                                color = if (autoScrollEnabled) TextPrimary else TextTertiary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Jump to Bottom Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xEE1E202A))
                            .border(1.dp, BorderSilver, CircleShape)
                            .clickable {
                                coroutineScope.launch {
                                    if (filteredLogs.isNotEmpty()) {
                                        listState.animateScrollToItem(filteredLogs.size - 1)
                                    }
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ==================== 7. FOOTER ====================
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap log line to copy • Logs clear automatically on app close",
                color = TextTertiary,
                fontSize = 9.5.sp
            )
            Text(
                text = "${filteredLogs.size}/${parsedLogs.size} lines",
                color = TextTertiary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    // ==================== CLEAR LOGS DIALOG ====================
    if (showClearDialog) {
        BasicAlertDialog(
            onDismissRequest = { showClearDialog = false }
        ) {
            NothingCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = SurfaceCard,
                borderColor = BorderSilver,
                contentPadding = PaddingValues(18.dp)
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
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = StatusRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Clear Diagnostic Logs?",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Flushes the in-memory buffer and persistent stream log files.",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NothingPillButton(
                        text = "Cancel",
                        onClick = { showClearDialog = false },
                        variant = NothingButtonVariant.Ghost,
                        height = 36.dp,
                        modifier = Modifier.weight(1f)
                    )

                    NothingPillButton(
                        text = "Clear",
                        onClick = {
                            showClearDialog = false
                            val ok = viewModel.clearAppLogs()
                            Toast.makeText(context, if (ok) "✓ Logs cleared" else "Failed to clear logs", Toast.LENGTH_SHORT).show()
                        },
                        variant = NothingButtonVariant.Primary,
                        height = 36.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LogLineCard(
    item: ParsedLogItem,
    onCopy: () -> Unit
) {
    val levelColor = when {
        item.isDropOrTimeout -> Color(0xFFFF5252)
        item.level == "E" || item.level == "F" -> Color(0xFFFF3366)
        item.level == "W" -> Color(0xFFFFB300)
        item.isSpeedMetric -> Color(0xFF69F0AE)
        item.isStreamLog -> Color(0xFF00E5FF)
        item.level == "D" -> Color(0xFF81C784)
        else -> Color(0xFF64B5F6)
    }

    val cardBg = when {
        item.isDropOrTimeout -> Color(0x20FF5252)
        item.level == "E" || item.level == "F" -> Color(0x18FF3366)
        item.level == "W" -> Color(0x12FFB300)
        item.isSpeedMetric -> Color(0x0C69F0AE)
        else -> Color(0xFF0D0F14)
    }

    val borderColor = when {
        item.isDropOrTimeout -> Color(0x55FF5252)
        item.level == "E" || item.level == "F" -> Color(0x45FF3366)
        item.level == "W" -> Color(0x35FFB300)
        else -> Color(0xFF171922)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(cardBg)
            .border(0.5.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onCopy() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Level Badge, Tag, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Level Badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(levelColor.copy(alpha = 0.18f))
                            .border(0.5.dp, levelColor.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = if (item.isDropOrTimeout) "DROP" else item.level,
                            color = levelColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Tag
                    if (item.tag.isNotBlank()) {
                        Text(
                            text = item.tag,
                            color = if (item.isStreamLog) Color(0xFF80D8FF) else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Timestamp
                if (item.timestamp.isNotBlank()) {
                    Text(
                        text = item.timestamp,
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Message text with highlighted keywords
            Text(
                text = formatHighlightedLog(item.message),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.5.sp
            )
        }
    }
}

/**
 * Highlights crucial streaming diagnostic keywords in logcat text
 */
private fun formatHighlightedLog(text: String) = buildAnnotatedString {
    val dropRegex = Regex("""(?i)(PACKET LOSS|TIMEOUT|BUFFER UNDERRUN|SOCKET ERROR|timed out|retry \d+/\d+)""")
    val speedRegex = Regex("""(\d+\.?\d*\s*MB/s|\d+\.?\d*\s*KB/s)""")
    val dcRegex = Regex("""(DC\d+|sess\d+|range=\d+-\d+)""")

    var cursor = 0
    val matches = mutableListOf<Pair<IntRange, Color>>()

    dropRegex.findAll(text).forEach { matches.add(it.range to Color(0xFFFF5252)) }
    speedRegex.findAll(text).forEach { matches.add(it.range to Color(0xFF69F0AE)) }
    dcRegex.findAll(text).forEach { matches.add(it.range to Color(0xFF00E5FF)) }

    matches.sortBy { it.first.first }

    var lastIdx = 0
    for ((range, color) in matches) {
        if (range.first >= lastIdx) {
            append(text.substring(lastIdx, range.first))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(text.substring(range.first, minOf(text.length, range.last + 1)))
            }
            lastIdx = range.last + 1
        }
    }
    if (lastIdx < text.length) {
        append(text.substring(lastIdx))
    }
}
