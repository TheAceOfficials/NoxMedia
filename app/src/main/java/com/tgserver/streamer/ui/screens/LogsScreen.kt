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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val message: String = ""
)

private val LOGCAT_REGEX = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^:(]+)(?:\(\s*\d+\))?:\s*(.*)$""")

private fun parseLogItem(line: String): ParsedLogItem {
    val trimmed = line.trim()
    val match = LOGCAT_REGEX.matchEntire(trimmed)
    return if (match != null) {
        val (time, level, tag, msg) = match.destructured
        ParsedLogItem(
            raw = line,
            timestamp = time,
            level = level.uppercase(),
            tag = tag.trim(),
            message = msg
        )
    } else {
        val level = when {
            trimmed.contains(" E/") || trimmed.startsWith("E/") || trimmed.contains("Exception") || trimmed.contains("Error") -> "E"
            trimmed.contains(" W/") || trimmed.startsWith("W/") || trimmed.contains("Warning") || trimmed.contains("miss for") -> "W"
            trimmed.contains(" D/") || trimmed.startsWith("D/") -> "D"
            trimmed.contains(" V/") || trimmed.startsWith("V/") -> "V"
            else -> "I"
        }
        ParsedLogItem(
            raw = line,
            timestamp = "",
            level = level,
            tag = "",
            message = trimmed
        )
    }
}

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

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevelFilter by remember { mutableStateOf("ALL") }
    var showClearDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-refresh logs when screen opens
    LaunchedEffect(Unit) {
        viewModel.refreshAppLogs()
    }

    // Parse logs into structured entries
    val parsedLogs = remember(rawLogs) {
        rawLogs.map { parseLogItem(it) }
    }

    // Filter logs based on search query and selected level
    val filteredLogs = remember(parsedLogs, searchQuery, selectedLevelFilter) {
        parsedLogs.filter { item ->
            val matchesLevel = when (selectedLevelFilter) {
                "ALL" -> true
                else -> item.level.equals(selectedLevelFilter, ignoreCase = true)
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                item.raw.contains(searchQuery, ignoreCase = true)
            }
            matchesLevel && matchesSearch
        }
    }

    // Auto-scroll to bottom on first load if logs present
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    // Refresh animation rotation
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
            // Consume horizontal drags so parent tab pager doesn't switch tabs while inspecting logs
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ==================== TOP NAVIGATION BAR ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
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
                        .background(Color(0xFF161820))
                        .border(1.dp, Color(0xFF262936), CircleShape)
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
                            text = "Diagnostic Logs",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                        NothingDotBadge(
                            text = if (filteredLogs.size == parsedLogs.size) "${parsedLogs.size}" else "${filteredLogs.size}/${parsedLogs.size}",
                            dotColor = if (parsedLogs.isEmpty()) AccentSilver else StatusGreen,
                            backgroundColor = Color(0x18FFFFFF),
                            textColor = TextSecondary
                        )
                    }
                    Text(
                        text = "Real-time engine & resolver activity",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            // Quick Actions: Refresh, Copy All, Share, Clear
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Refresh Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161820))
                        .border(1.dp, Color(0xFF242732), CircleShape)
                        .clickable { viewModel.refreshAppLogs() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Logs",
                        tint = if (isRefreshing) AccentApple else TextSecondary,
                        modifier = Modifier
                            .size(17.dp)
                            .rotate(if (isRefreshing) spinAngle else 0f)
                    )
                }

                // 2. Copy All Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161820))
                        .border(1.dp, Color(0xFF242732), CircleShape)
                        .clickable {
                            if (filteredLogs.isEmpty()) {
                                Toast.makeText(context, "No logs to copy", Toast.LENGTH_SHORT).show()
                            } else {
                                val fullText = filteredLogs.joinToString("\n") { it.raw }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("NoxMediaLogs", fullText))
                                Toast.makeText(context, "✓ Copied ${filteredLogs.size} log lines to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy All Logs",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 3. Share Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161820))
                        .border(1.dp, Color(0xFF242732), CircleShape)
                        .clickable {
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
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Logs",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 4. Clear Button
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
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // ==================== SEARCH BAR ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF12141A))
                .border(1.dp, Color(0xFF22242D), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search logs (e.g. Resolvers, Indexer, Money Heist)...",
                            color = TextTertiary,
                            fontSize = 12.5.sp,
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
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22242D))
                            .clickable { searchQuery = "" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==================== LEVEL FILTER CHIPS ====================
        val filterOptions = listOf(
            Triple("ALL", "All", TextSecondary),
            Triple("E", "Error", StatusRed),
            Triple("W", "Warn", StatusAmber),
            Triple("I", "Info", Color(0xFF64B5F6)),
            Triple("D", "Debug", StatusGreen)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filterOptions.forEach { (code, label, dotColor) ->
                val isSelected = selectedLevelFilter == code
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF262936) else Color(0xFF12141A))
                        .border(
                            1.dp,
                            if (isSelected) AccentApple.copy(alpha = 0.5f) else Color(0xFF1E202A),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedLevelFilter = code }
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
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==================== LOGS CONSOLE LIST ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0C0D12))
                .border(1.dp, Color(0xFF1E202A), RoundedCornerShape(14.dp))
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
                        imageVector = Icons.Default.Notes,
                        contentDescription = null,
                        tint = TextTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedLevelFilter != "ALL") "No matching logs found" else "Log buffer is clean",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try adjusting your search query or filter" else "System logs will appear here as the streamer runs",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(filteredLogs) { _, item ->
                        LogLineCard(item = item, onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LogLine", item.raw))
                            Toast.makeText(context, "✓ Copied log line", Toast.LENGTH_SHORT).show()
                        })
                    }
                }

                // Scroll to Bottom Quick Button
                val isAtBottom by remember {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= filteredLogs.size - 2
                    }
                }

                if (!isAtBottom && filteredLogs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xEE1E202A))
                                .border(1.dp, BorderSilver, CircleShape)
                                .clickable {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(filteredLogs.size - 1)
                                    }
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom status footer
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap any entry to copy line",
                color = TextTertiary,
                fontSize = 10.sp
            )
            Text(
                text = "Logcat Buffer • ${filteredLogs.size} items",
                color = TextTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    // ==================== CLEAR LOGS CONFIRMATION DIALOG ====================
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
                            text = "Clear All Logs?",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Flushes the runtime logcat buffer and log files.",
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
    val levelColor = when (item.level) {
        "E", "F" -> Color(0xFFFF5252)
        "W" -> Color(0xFFFFB74D)
        "I" -> Color(0xFF64B5F6)
        "D" -> Color(0xFF81C784)
        "V" -> Color(0xFFBA68C8)
        else -> Color(0xFFA0A3B0)
    }

    val cardBg = when (item.level) {
        "E", "F" -> Color(0x18FF5252)
        "W" -> Color(0x10FFB74D)
        else -> Color(0xFF101218)
    }

    val borderColor = when (item.level) {
        "E", "F" -> Color(0x40FF5252)
        "W" -> Color(0x30FFB74D)
        else -> Color(0xFF1B1D26)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onCopy() }
            .padding(horizontal = 9.dp, vertical = 7.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Level Pill
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(levelColor.copy(alpha = 0.18f))
                            .border(0.5.dp, levelColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = item.level,
                            color = levelColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Tag
                    if (item.tag.isNotBlank()) {
                        Text(
                            text = item.tag,
                            color = TextPrimary,
                            fontSize = 11.sp,
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
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Log Message Body
            Text(
                text = item.message,
                color = if (item.level == "E" || item.level == "F") Color(0xFFFFCDD2) else Color(0xFFD6D9E0),
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )
        }
    }
}
