package com.tgserver.streamer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.screens.ApiCredentialsScreen
import com.tgserver.streamer.ui.screens.ChannelsScreen
import com.tgserver.streamer.ui.screens.HomeScreen
import com.tgserver.streamer.ui.screens.LibraryScreen
import com.tgserver.streamer.ui.screens.SettingsScreen

@Composable
fun AppRootScreen(viewModel: MainAppViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val apiCreds by viewModel.apiCredentials.collectAsState()

    val hasCredentials = apiCreds.first > 0 && apiCreds.second.isNotBlank()

    if (!hasCredentials) {
        ApiCredentialsScreen(viewModel = viewModel)
    } else {
        Scaffold(
            containerColor = BackgroundOled,
            bottomBar = {
                MinimalBottomNav(
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
                when (selectedTab) {
                    AppTab.HOME -> HomeScreen(viewModel = viewModel)
                    AppTab.CHANNELS -> ChannelsScreen(viewModel = viewModel)
                    AppTab.LIBRARY -> LibraryScreen(viewModel = viewModel)
                    AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun MinimalBottomNav(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundOled)
            .border(
                width = 1.dp,
                color = BorderSubtle,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentTab == AppTab.HOME,
                onClick = { onTabSelected(AppTab.HOME) }
            )
            NavItem(
                icon = Icons.Default.Share,
                label = "Channels",
                isSelected = currentTab == AppTab.CHANNELS,
                onClick = { onTabSelected(AppTab.CHANNELS) }
            )
            NavItem(
                icon = Icons.Default.PlayArrow,
                label = "Library",
                isSelected = currentTab == AppTab.LIBRARY,
                onClick = { onTabSelected(AppTab.LIBRARY) }
            )
            NavItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = currentTab == AppTab.SETTINGS,
                onClick = { onTabSelected(AppTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) AccentApple else TextTertiary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (isSelected) AccentApple else TextTertiary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
