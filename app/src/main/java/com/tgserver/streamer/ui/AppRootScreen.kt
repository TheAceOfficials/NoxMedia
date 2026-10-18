package com.tgserver.streamer.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.screens.ApiCredentialsScreen
import com.tgserver.streamer.ui.screens.ChannelsScreen
import com.tgserver.streamer.ui.screens.HomeScreen
import com.tgserver.streamer.ui.screens.LibraryScreen
import com.tgserver.streamer.ui.screens.SettingsScreen
import com.tgserver.streamer.ui.screens.TelegramLoginScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private data class NavItemData(
    val tab: AppTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NAV_TABS = listOf(
    NavItemData(AppTab.HOME, Icons.Filled.Home, Icons.Outlined.Home),
    NavItemData(AppTab.LIBRARY, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    NavItemData(AppTab.CHANNELS, Icons.Filled.Hub, Icons.Outlined.Hub),
    NavItemData(AppTab.SETTINGS, Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun AppRootScreen(viewModel: MainAppViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val apiCreds by viewModel.apiCredentials.collectAsState()
    val session by viewModel.session.collectAsState()
    val haptic = LocalHapticFeedback.current

    val hasCredentials = apiCreds.first > 0 && apiCreds.second.isNotBlank()

    if (!hasCredentials) {
        ApiCredentialsScreen(viewModel = viewModel)
    } else if (!session.isLoggedIn) {
        TelegramLoginScreen(viewModel = viewModel)
    } else {
        var dragAccumulator by remember { mutableFloatStateOf(0f) }

        // Root Edge-to-Edge Container (No Scaffold bottomBar = No black box strip!)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundOled)
        ) {
            // Full-screen content layer (scrolls seamlessly behind the floating dock)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Horizontal swipe between tabs
                    .pointerInput(selectedTab) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount
                            },
                            onDragEnd = {
                                val currentIdx = NAV_TABS.indexOfFirst { it.tab == selectedTab }
                                if (dragAccumulator < -90f && currentIdx < NAV_TABS.size - 1) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.selectTab(NAV_TABS[currentIdx + 1].tab)
                                } else if (dragAccumulator > 90f && currentIdx > 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.selectTab(NAV_TABS[currentIdx - 1].tab)
                                }
                                dragAccumulator = 0f
                            }
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val initialIdx = NAV_TABS.indexOfFirst { it.tab == initialState }
                        val targetIdx = NAV_TABS.indexOfFirst { it.tab == targetState }
                        if (targetIdx >= initialIdx) {
                            (slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = 420f)) { width -> width / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(spring(dampingRatio = 0.82f, stiffness = 420f)) { width -> -width / 3 } + fadeOut(tween(180)))
                        } else {
                            (slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = 420f)) { width -> -width / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(spring(dampingRatio = 0.82f, stiffness = 420f)) { width -> width / 3 } + fadeOut(tween(180)))
                        }
                    },
                    label = "tab_content_switch"
                ) { tab ->
                    when (tab) {
                        AppTab.HOME -> HomeScreen(viewModel = viewModel)
                        AppTab.LIBRARY -> LibraryScreen(viewModel = viewModel)
                        AppTab.CHANNELS -> ChannelsScreen(viewModel = viewModel)
                        AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }

            // Minimalist Floating Glassmorphic Dock (True Floating Island, Zero Side Box)
            MinimalFloatingNavDock(
                currentTab = selectedTab,
                avatarUrl = session.avatarUrl,
                userInitials = session.initials,
                onTabSelected = { viewModel.selectTab(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )
        }
    }
}

/**
 * Ultra-minimalist, 60fps+ fluid glassmorphic floating navigation island.
 * Features:
 * - Only icons (pure minimal aesthetic, zero text clutter).
 * - Symmetrical, perfectly centered icon alignment.
 * - Fluid hold-to-swipe drag scrub gesture with squishy liquid springs.
 * - Profile avatar integrated for Settings tab with active white ring.
 * - Zero background cut-off: completely transparent on the sides.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalFloatingNavDock(
    currentTab: AppTab,
    avatarUrl: String?,
    userInitials: String,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val horizontalPaddingPx = remember(density) { with(density) { 5.dp.toPx() } }

    val targetIndex = remember(currentTab) {
        NAV_TABS.indexOfFirst { it.tab == currentTab }.coerceAtLeast(0).toFloat()
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(targetIndex) }
    var lastHapticIndex by remember { mutableIntStateOf(targetIndex.toInt()) }

    LaunchedEffect(targetIndex) {
        if (!isDragging) {
            dragPosition = targetIndex
            lastHapticIndex = targetIndex.toInt()
        }
    }

    // 120 FPS buttery smooth spring interpolation
    val animatedPosition by animateFloatAsState(
        targetValue = if (isDragging) dragPosition else targetIndex,
        animationSpec = if (isDragging) spring(dampingRatio = 0.98f, stiffness = 1600f)
                        else spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "dock_pill_pos"
    )

    // Liquid squishy stretch physics based on animation speed
    var prevPos by remember { mutableFloatStateOf(targetIndex) }
    val velocity = animatedPosition - prevPos
    SideEffect {
        prevPos = animatedPosition
    }

    val dynamicStretch by animateFloatAsState(
        targetValue = (abs(velocity) * 1.8f).coerceIn(0f, 0.28f),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 600f),
        label = "dock_pill_stretch"
    )

    val pillScaleX = 1.0f + dynamicStretch
    val pillScaleY = (1.0f - dynamicStretch * 0.35f).coerceAtLeast(0.75f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Floating Island Capsule (Compact, Centered, Symmetrical)
        Box(
            modifier = Modifier
                .width(276.dp)
                .height(54.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(27.dp),
                    spotColor = Color(0x99000000),
                    ambientColor = Color(0x55000000)
                )
                .clip(RoundedCornerShape(27.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xDD15161F), // Frosted dark obsidian
                            Color(0xF00D0E13)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x40FFFFFF), // Subtle top rim metallic highlight
                                Color(0x14FFFFFF),
                                Color(0x06FFFFFF)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(27.dp)
                )
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                // Hold-to-Swipe Gesture Scrubbing across the dock
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val usableWidth = (barWidthPx - 2 * horizontalPaddingPx).coerceAtLeast(1f)
                            val relativeX = (offset.x - horizontalPaddingPx).coerceIn(0f, usableWidth)
                            val frac = ((relativeX / usableWidth) * NAV_TABS.size - 0.5f)
                                .coerceIn(0f, (NAV_TABS.size - 1).toFloat())
                            dragPosition = frac
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val usableWidth = (barWidthPx - 2 * horizontalPaddingPx).coerceAtLeast(1f)
                            val relativeX = (change.position.x - horizontalPaddingPx).coerceIn(0f, usableWidth)
                            val frac = ((relativeX / usableWidth) * NAV_TABS.size - 0.5f)
                                .coerceIn(0f, (NAV_TABS.size - 1).toFloat())
                            dragPosition = frac

                            // Haptic tick on crossing tab boundary
                            val closest = frac.roundToInt().coerceIn(0, NAV_TABS.size - 1)
                            if (closest != lastHapticIndex) {
                                lastHapticIndex = closest
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val closest = dragPosition.roundToInt().coerceIn(0, NAV_TABS.size - 1)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTabSelected(NAV_TABS[closest].tab)
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        ) {
            val usableWidthPx = (barWidthPx - 2 * horizontalPaddingPx).coerceAtLeast(1f)
            val slotWidthPx = usableWidthPx / NAV_TABS.size
            val slotWidthDp = with(density) { slotWidthPx.toDp() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                // Sliding Elevated Frosted Glass Highlight Pill
                Box(
                    modifier = Modifier
                        .width(slotWidthDp)
                        .fillMaxHeight()
                        .offset {
                            IntOffset(
                                x = (animatedPosition * slotWidthPx).roundToInt(),
                                y = 0
                            )
                        }
                        .graphicsLayer {
                            scaleX = pillScaleX
                            scaleY = pillScaleY
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                        .clip(RoundedCornerShape(23.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x35FFFFFF), // Elevated frosted highlight
                                    Color(0x18FFFFFF)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                Color(0x3CFFFFFF)
                            ),
                            shape = RoundedCornerShape(23.dp)
                        )
                )

                // Row of symmetrical, icon-only navigation slots
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NAV_TABS.forEachIndexed { index, item ->
                        val distance = abs(animatedPosition - index)
                        val closeness = (1f - distance).coerceIn(0f, 1f)
                        val isSelected = currentTab == item.tab

                        MinimalNavItem(
                            item = item,
                            isSelected = isSelected,
                            closeness = closeness,
                            avatarUrl = if (item.tab == AppTab.SETTINGS) avatarUrl else null,
                            userInitials = if (item.tab == AppTab.SETTINGS) userInitials else "",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(item.tab)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual navigation item (pure icon, perfectly centered, no text).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalNavItem(
    item: NavItemData,
    isSelected: Boolean,
    closeness: Float,
    avatarUrl: String?,
    userInitials: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSettings = item.tab == AppTab.SETTINGS

    // Micro bounce scale on selection
    val bounceScale by animateFloatAsState(
        targetValue = 1.0f + (closeness * 0.08f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_item_bounce"
    )

    // Load local or cached avatar bitmap synchronously, fallback to remote fetch
    var avatarBitmap by remember(avatarUrl) {
        val path = avatarUrl?.removePrefix("file://")
        val initialBmp = if (!path.isNullOrBlank()) {
            val f = File(path)
            if (f.exists() && f.length() > 0) {
                try {
                    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            } else null
        } else null
        mutableStateOf(initialBmp)
    }

    LaunchedEffect(avatarUrl) {
        if (avatarBitmap == null && !avatarUrl.isNullOrBlank() && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))) {
            withContext(Dispatchers.IO) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url(avatarUrl)
                        .header("User-Agent", "NoxMedia/1.0 (Android)")
                        .build()
                    val client = okhttp3.OkHttpClient()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                if (bmp != null) {
                                    withContext(Dispatchers.Main) {
                                        avatarBitmap = bmp.asImageBitmap()
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(23.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.scale(bounceScale),
            contentAlignment = Alignment.Center
        ) {
            if (isSettings) {
                // Profile Avatar Tab (Circular Crop with Active White Glowing Ring)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2028))
                        .border(
                            width = if (closeness > 0.5f) 1.8.dp else 1.dp,
                            color = if (closeness > 0.5f) Color.White else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap!!,
                            contentDescription = "Profile Avatar",
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.65f + closeness * 0.35f),
                            contentScale = ContentScale.Crop
                        )
                    } else if (userInitials.isNotBlank()) {
                        Text(
                            text = userInitials,
                            color = Color.White.copy(alpha = 0.70f + closeness * 0.30f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = if (closeness > 0.5f) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color.White.copy(alpha = 0.65f + closeness * 0.35f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            } else {
                // Standard Tabs (Home, Library, Channels)
                // Color smoothly interpolates from muted slate-silver to crisp pure white
                val iconColor = lerp(
                    Color(0xFF7E8088),
                    Color.White,
                    closeness
                )

                Icon(
                    imageVector = if (closeness > 0.5f) item.selectedIcon else item.unselectedIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(if (closeness > 0.5f) 23.dp else 22.dp)
                )
            }
        }
    }
}
