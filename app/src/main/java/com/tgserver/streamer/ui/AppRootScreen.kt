package com.tgserver.streamer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.screens.ApiCredentialsScreen
import com.tgserver.streamer.ui.screens.ChannelsScreen
import com.tgserver.streamer.ui.screens.HomeScreen
import com.tgserver.streamer.ui.screens.LibraryScreen
import com.tgserver.streamer.ui.screens.SettingsScreen
import com.tgserver.streamer.ui.screens.TelegramLoginScreen

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val initialIdx = NAV_TABS.indexOfFirst { it.tab == initialState }
                        val targetIdx = NAV_TABS.indexOfFirst { it.tab == targetState }
                        if (targetIdx >= initialIdx) {
                            (slideInHorizontally(tween(240)) { width -> width / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(180)) { width -> -width / 4 } + fadeOut(tween(180)))
                        } else {
                            (slideInHorizontally(tween(240)) { width -> -width / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(180)) { width -> width / 4 } + fadeOut(tween(180)))
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
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Glassmorphism Capsule Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color(0x4D0D2626),
                    ambientColor = Color(0x66000000)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEB1A1D28),
                            Color(0xF50F1118)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x40FFFFFF),
                                Color(0x18FFFFFF),
                                Color(0x0DFFFFFF)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                // Hold and drag/scrub across the bar to switch pages seamlessly
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val fraction = (offset.x / barWidthPx).coerceIn(0f, 0.999f)
                            val targetIndex = (fraction * NAV_TABS.size).toInt().coerceIn(0, NAV_TABS.size - 1)
                            val targetTab = NAV_TABS[targetIndex].tab
                            if (targetTab != currentTab) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(targetTab)
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / barWidthPx).coerceIn(0f, 0.999f)
                            val targetIndex = (fraction * NAV_TABS.size).toInt().coerceIn(0, NAV_TABS.size - 1)
                            val targetTab = NAV_TABS[targetIndex].tab
                            if (targetTab != currentTab) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(targetTab)
                            }
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NAV_TABS.forEach { item ->
                    val isSelected = currentTab == item.tab
                    EnhancedNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTabSelected(item.tab)
                        },
                        onHold = {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnhancedNavItem(
    item: NavItemData,
    isSelected: Boolean,
    onClick: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_scale"
    )

    val translateY by animateDpAsState(
        targetValue = if (isSelected) (-2).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_translate_y"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x28FFFFFF),
                                    Color(0x14FFFFFF)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Color(0x2EFFFFFF),
                            RoundedCornerShape(24.dp)
                        )
                } else Modifier
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onHold
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale)
                .offset(y = translateY)
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint = if (isSelected) Color(0xFFFFFFFF) else Color(0x80FFFFFF),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.label,
                color = if (isSelected) Color(0xFFFFFFFF) else Color(0x80FFFFFF),
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
