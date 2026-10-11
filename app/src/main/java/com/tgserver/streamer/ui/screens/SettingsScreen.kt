package com.tgserver.streamer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel

@Composable
fun SettingsScreen(viewModel: MainAppViewModel) {
    val session by viewModel.session.collectAsState()
    val config by viewModel.config.collectAsState()
    val isSendingCode by viewModel.isSendingCode.collectAsState()
    val isVerifyingCode by viewModel.isVerifyingCode.collectAsState()
    val codeSentSuccess by viewModel.codeSentSuccess.collectAsState()

    var phoneInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var sessionStringInput by remember { mutableStateOf("") }
    var showSessionImport by remember { mutableStateOf(false) }

    var portInput by remember(config.port) { mutableStateOf(config.port.toString()) }
    var posterUrlInput by remember(config.customPosterUrl) { mutableStateOf(config.customPosterUrl) }
    var posterKeyInput by remember(config.customPosterApiKey) { mutableStateOf(config.customPosterApiKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // HEADER
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "Settings",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Telegram account, local engine, and poster integration",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // 1. TELEGRAM AUTHENTICATION (REAL, NO FAKE MOCKS)
        SettingsCard(title = "Telegram Account") {
            if (session.isLoggedIn) {
                // LOGGED IN STATE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFF3A43B).copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                .border(1.dp, Color(0xFFF3A43B).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                        ) {
                            Text(
                                text = session.initials,
                                color = Color(0xFFF3A43B),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = session.firstName.ifBlank { "Telegram User" },
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(StatusGreen, RoundedCornerShape(4.dp))
                                )
                            }
                            Text(
                                text = "@${session.username.ifBlank { "tg_user" }}",
                                color = Color(0xFF64B5F6),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = session.phoneNumber.ifBlank { "Session Connected" },
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Log Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // LOGGED OUT STATE
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Sign in with your Telegram Session String to access your monitored channels and stream movies locally to Stremio.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Text(
                        text = "TELEGRAM SESSION STRING",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    OutlinedTextField(
                        value = sessionStringInput,
                        onValueChange = { sessionStringInput = it },
                        placeholder = { Text("Paste Pyrogram or Telethon session string", color = TextTertiary, fontSize = 12.sp) },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Color(0xFFF3A43B),
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = SurfaceCardElevated,
                            unfocusedContainerColor = SurfaceCardElevated
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { viewModel.importSessionString(sessionStringInput) },
                        enabled = sessionStringInput.trim().length >= 20,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF3A43B),
                            contentColor = Color(0xFF1E1402)
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Connect Session String", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1.5 TELEGRAM API CREDENTIALS
        val (apiId, apiHash) = viewModel.apiCredentials.collectAsState().value
        var editApiId by remember(apiId) { mutableStateOf(if (apiId > 0) apiId.toString() else "") }
        var editApiHash by remember(apiHash) { mutableStateOf(apiHash) }

        SettingsCard(title = "Telegram API Credentials") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "API ID",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = editApiId,
                    onValueChange = { editApiId = it },
                    placeholder = { Text("API ID", color = TextTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(0xFFF3A43B),
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceCardElevated,
                        unfocusedContainerColor = SurfaceCardElevated
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "API HASH",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = editApiHash,
                    onValueChange = { editApiHash = it },
                    placeholder = { Text("API Hash", color = TextTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(0xFFF3A43B),
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceCardElevated,
                        unfocusedContainerColor = SurfaceCardElevated
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val parsedId = editApiId.trim().toIntOrNull() ?: 0
                        viewModel.saveApiCredentials(parsedId, editApiHash.trim())
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3A43B)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Credentials", color = Color(0xFF1E1402), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. LOCAL SERVER CONFIGURATION (PORT 7788)
        SettingsCard(title = "Local Streaming Engine") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "SERVER PORT",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentApple,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceCardElevated,
                        unfocusedContainerColor = SurfaceCardElevated
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Default port 7788 prevents collisions with web servers and internal proxies.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // 3. SPATIALPOSTERS INTEGRATION
        SettingsCard(title = "SpatialPosters Integration") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "CUSTOM POSTER ENDPOINT URL",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = posterUrlInput,
                    onValueChange = { posterUrlInput = it },
                    placeholder = { Text("http://192.168.1.100:3000", color = TextTertiary, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentApple,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceCardElevated,
                        unfocusedContainerColor = SurfaceCardElevated
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "API KEY (OPTIONAL)",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = posterKeyInput,
                    onValueChange = { posterKeyInput = it },
                    placeholder = { Text("Enter API key if required", color = TextTertiary, fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentApple,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceCardElevated,
                        unfocusedContainerColor = SurfaceCardElevated
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val parsedPort = portInput.toIntOrNull() ?: 7788
                        viewModel.updateConfig(parsedPort, posterUrlInput.trim(), posterKeyInput.trim())
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentApple),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Save Settings", color = BackgroundOled, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 4. SYSTEM INFO
        SettingsCard(title = "System Info") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow(label = "Application", value = "TG-Server Mobile Streamer")
                InfoRow(label = "Version", value = "1.0.0 (Native ART Engine)")
                InfoRow(label = "Target Architecture", value = "Android 8.0+ (ARM64 / ARMv7)")
                InfoRow(label = "Default Streaming Port", value = "7788")
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Text(text = value, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
