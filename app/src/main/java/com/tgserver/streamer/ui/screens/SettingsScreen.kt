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
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(StatusGreen, RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = "Connected",
                                color = StatusGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "@${session.username}",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = session.phoneNumber.ifBlank { "Session active" },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Log Out", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // LOGGED OUT STATE
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Sign in with your Telegram account (secondary account recommended) to access your channels and stream video files.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    // Phone input
                    Text(
                        text = "PHONE NUMBER (WITH COUNTRY CODE)",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            placeholder = { Text("+91 9876543210", color = TextTertiary, fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = AccentApple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = SurfaceCardElevated,
                                unfocusedContainerColor = SurfaceCardElevated
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = { viewModel.sendAuthCode(phoneInput) },
                            enabled = !isSendingCode && phoneInput.length >= 8,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentApple),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text(
                                text = if (isSendingCode) "Sending..." else "Send OTP",
                                color = BackgroundOled,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // OTP verification field (when code sent)
                    if (codeSentSuccess) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "ENTER VERIFICATION CODE",
                                color = TextTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = otpInput,
                                    onValueChange = { otpInput = it },
                                    placeholder = { Text("Code from Telegram app", color = TextTertiary, fontSize = 13.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = StatusGreen,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedContainerColor = SurfaceCardElevated,
                                        unfocusedContainerColor = SurfaceCardElevated
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = { viewModel.verifyAuthCode(otpInput) },
                                    enabled = !isVerifyingCode && otpInput.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                    modifier = Modifier.height(54.dp)
                                ) {
                                    Text(
                                        text = if (isVerifyingCode) "Verifying..." else "Verify",
                                        color = BackgroundOled,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Advanced: String Session Import
                    TextButton(
                        onClick = { showSessionImport = !showSessionImport },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (showSessionImport) "Hide Session String Import" else "Advanced: Import StringSession",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    if (showSessionImport) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sessionStringInput,
                                onValueChange = { sessionStringInput = it },
                                placeholder = { Text("Paste Pyrogram / Telethon / GramJS session string", color = TextTertiary, fontSize = 12.sp) },
                                maxLines = 3,
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
                                onClick = { viewModel.importSessionString(sessionStringInput) },
                                enabled = sessionStringInput.length > 10,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardElevated)
                            ) {
                                Text("Import Session", color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
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
