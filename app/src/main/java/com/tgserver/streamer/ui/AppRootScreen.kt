package com.tgserver.streamer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.screens.ApiCredentialsScreen
import com.tgserver.streamer.ui.screens.ChannelsScreen
import com.tgserver.streamer.ui.screens.HomeScreen
import com.tgserver.streamer.ui.screens.LibraryScreen
import com.tgserver.streamer.ui.screens.SettingsScreen
import com.tgserver.streamer.ui.screens.TelegramLoginScreen
import kotlin.math.abs
import kotlin.math.roundToInt

private data class NavItemData(
    val tab: AppTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NAV_TABS = listOf(
    NavItemData(AppTab.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavItemData(AppTab.LIBRARY, "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    NavItemData(AppTab.CHANNELS, "Channels", Icons.Filled.Hub, Icons.Outlined.Hub),
    NavItemData(AppTab.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
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
        Scaffold(
            containerColor = BackgroundOled,
            bottomBar = {
                EnhancedGlassBottomNav(
                    currentTab = selectedTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            }
        ) { innerPadding ->
            var dragAccumulator by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Full-screen horizontal swipe switch between tabs
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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnhancedGlassBottomNav(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val horizontalPaddingPx = remember(density) { with(density) { 6.dp.toPx() } }

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

    val animatedPosition by animateFloatAsState(
        targetValue = if (isDragging) dragPosition else targetIndex,
        animationSpec = if (isDragging) spring(dampingRatio = 0.90f, stiffness = 950f)
                        else spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "pill_position"
    )

    // Squishy physics: compute velocity and dynamic stretch factor
    var prevPosition by remember { mutableFloatStateOf(targetIndex) }
    val instantaneousVelocity = animatedPosition - prevPosition
    SideEffect {
        prevPosition = animatedPosition
    }

    val dynamicStretch by animateFloatAsState(
        targetValue = (abs(instantaneousVelocity) * 2.6f).coerceIn(0f, 0.38f),
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "pill_stretch"
    )

    val pillScaleX = 1.0f + dynamicStretch
    val pillScaleY = (1.0f - dynamicStretch * 0.42f).coerceAtLeast(0.72f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Transparent Liquid Glass Capsule Bar
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x1CFFFFFF),
                            Color(0x0AFFFFFF)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x59FFFFFF),
                                Color(0x1FFFFFFF),
                                Color(0x0DFFFFFF)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                // Interactive horizontal swipe / drag scrub across the dock
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val usableWidth = (barWidthPx - 2 * horizontalPaddingPx).coerceAtLeast(1f)
                            val relativeX = (offset.x - horizontalPaddingPx).coerceIn(0f, usableWidth)
                            val frac = (relativeX / usableWidth * NAV_TABS.size - 0.5f).coerceIn(0f, (NAV_TABS.size - 1).toFloat())
                            dragPosition = frac
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val usableWidth = (barWidthPx - 2 * horizontalPaddingPx).coerceAtLeast(1f)
                            val relativeX = (change.position.x - horizontalPaddingPx).coerceIn(0f, usableWidth)
                            val frac = (relativeX / usableWidth * NAV_TABS.size - 0.5f).coerceIn(0f, (NAV_TABS.size - 1).toFloat())
                            dragPosition = frac

                            val closest = frac.roundToInt().coerceIn(0, NAV_TABS.size - 1)
                            if (closest != lastHapticIndex) {
                                lastHapticIndex = closest
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(NAV_TABS[closest].tab)
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

            // Continuous Reactive Squishy Liquid Glass Pill Indicator
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
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
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x44FFFFFF),
                                    Color(0x20FFFFFF)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x8CFFFFFF),
                                        Color(0x38FFFFFF),
                                        Color(0x18FFFFFF)
                                    )
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                )

                // Row of icons layered above the squishy pill
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NAV_TABS.forEachIndexed { index, item ->
                        val distance = abs(animatedPosition - index)
                        val closeness = (1f - distance).coerceIn(0f, 1f)
                        val isSelected = currentTab == item.tab

                        EnhancedNavItem(
                            item = item,
                            isSelected = isSelected,
                            closeness = closeness,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnhancedNavItem(
    item: NavItemData,
    isSelected: Boolean,
    closeness: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bounceScale by animateFloatAsState(
        targetValue = 1.0f + (closeness * 0.16f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_bounce"
    )

    val iconAlpha = 0.55f + (closeness * 0.45f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = Color(0xFFFFFFFF).copy(alpha = iconAlpha),
            modifier = Modifier
                .size(24.dp)
                .scale(bounceScale)
        )
    }
}
