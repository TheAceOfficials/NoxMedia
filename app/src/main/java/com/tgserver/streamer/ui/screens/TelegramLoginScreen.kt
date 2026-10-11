package com.tgserver.streamer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel

@Composable
fun TelegramLoginScreen(viewModel: MainAppViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isImporting by viewModel.isImportingSession.collectAsState()
    val sessionImportError by viewModel.sessionImportError.collectAsState()
    val (apiId, apiHash) = viewModel.apiCredentials.collectAsState().value

    var sessionInput by remember { mutableStateOf("") }
    var selectedGuideTab by remember { mutableIntStateOf(0) } // 0: Mobile, 1: PC
    var isGuideExpanded by remember { mutableStateOf(false) }

    val amberColor = Color(0xFFF3A43B)
    val inputBackground = Color(0xFF131418)
    val cardBackground = Color(0xFF16171C)
    val cardBorder = Color(0xFF26282F)
    val validGreen = Color(0xFF34C759)
    val telegramBlue = Color(0xFF2AABEE)

    // Real-time session string format validation
    val trimmedSession = sessionInput.trim()
    val isFormatLikelyValid = remember(trimmedSession) {
        trimmedSession.length >= 50 && !trimmedSession.contains(" ")
    }
    val sessionFormatError: String? = remember(trimmedSession) {
        when {
            trimmedSession.isEmpty() -> null
            trimmedSession.length < 20 -> "Session string is too short. Please paste the complete token."
            trimmedSession.contains(" ") -> "Session string should not contain spaces."
            else -> null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // BRANDING HEADER
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(amberColor, CircleShape)
                )
                Text(
                    text = "TG-Server",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .background(amberColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "CORE ENGINE",
                        color = amberColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connect Telegram",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Paste your Telegram Session String to connect your account and start streaming channels without any computer required.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // API CREDENTIALS BADGE / SUMMARY CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBackground, RoundedCornerShape(12.dp))
                    .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .background(validGreen.copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = validGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "API Credentials Configured",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "ID: $apiId • Hash: ${apiHash.take(6)}...${apiHash.takeLast(4)}",
                                color = TextTertiary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    TextButton(
                        onClick = { viewModel.clearApiCredentials() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Edit",
                            color = amberColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SESSION STRING INPUT SECTION
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SESSION STRING",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessionInput.isNotBlank()) {
                        TextButton(
                            onClick = { sessionInput = "" },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Clear", color = StatusRed, fontSize = 11.sp)
                        }
                    }

                    // PASTE FROM CLIPBOARD BUTTON
                    FilledTonalButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                sessionInput = clipText.trim()
                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF22242C),
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = sessionInput,
                onValueChange = { sessionInput = it },
                placeholder = {
                    Text(
                        text = "Paste Pyrogram or Telethon session string here...\n(e.g., 1BVtsOG...)",
                        color = TextTertiary.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                },
                minLines = 4,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                isError = sessionFormatError != null || sessionImportError != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = if (isFormatLikelyValid) validGreen else amberColor,
                    unfocusedBorderColor = if (isFormatLikelyValid) validGreen.copy(alpha = 0.6f) else cardBorder,
                    focusedContainerColor = inputBackground,
                    unfocusedContainerColor = inputBackground,
                    errorBorderColor = StatusRed,
                    errorContainerColor = inputBackground
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Status feedback below input
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sessionFormatError != null) {
                    Text(text = sessionFormatError, color = StatusRed, fontSize = 11.sp)
                } else if (sessionImportError != null) {
                    Text(text = sessionImportError ?: "", color = StatusRed, fontSize = 11.sp)
                } else if (isFormatLikelyValid) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = validGreen, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ready to connect", color = validGreen, fontSize = 11.sp)
                    }
                } else {
                    Text("Pyrogram / Telethon string supported", color = TextTertiary, fontSize = 11.sp)
                }

                if (trimmedSession.isNotEmpty()) {
                    Text(
                        text = "${trimmedSession.length} chars",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CONNECT BUTTON
            Button(
                onClick = {
                    if (trimmedSession.length < 20) {
                        Toast.makeText(context, "Please enter a valid session string", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.importSessionString(trimmedSession)
                },
                enabled = !isImporting && trimmedSession.length >= 20,
                colors = ButtonDefaults.buttonColors(
                    containerColor = amberColor,
                    contentColor = Color(0xFF1E1402),
                    disabledContainerColor = amberColor.copy(alpha = 0.3f),
                    disabledContentColor = Color(0xFF1E1402).copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF1E1402),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Connecting Account...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Connect & Start Server",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // EXPANDABLE GUIDE SECTION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isGuideExpanded = !isGuideExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = telegramBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "How to get Session String in 1 minute?",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Icon(
                            imageVector = if (isGuideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextTertiary
                        )
                    }

                    AnimatedVisibility(visible = isGuideExpanded) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            // TABS: MOBILE vs PC
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F1014), RoundedCornerShape(8.dp))
                                    .padding(3.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selectedGuideTab == 0) cardBackground else Color.Transparent)
                                        .clickable { selectedGuideTab = 0 }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "📱 On Mobile (No PC)",
                                        color = if (selectedGuideTab == 0) amberColor else TextTertiary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selectedGuideTab == 1) cardBackground else Color.Transparent)
                                        .clickable { selectedGuideTab = 1 }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "💻 On PC (Terminal)",
                                        color = if (selectedGuideTab == 1) amberColor else TextTertiary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (selectedGuideTab == 0) {
                                // MOBILE GUIDE
                                GuideStepItem(
                                    step = "1",
                                    title = "Open Telegram Generator Bot",
                                    desc = "In your Telegram app, search for @StringSessionRobot or @SessionStringGeneratorBot and tap START."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "2",
                                    title = "Choose Pyrogram",
                                    desc = "Select 'Pyrogram' when prompted for library choice."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "3",
                                    title = "Enter Credentials & Phone",
                                    desc = "Enter your API ID ($apiId), API HASH, and your phone number with country code."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "4",
                                    title = "Receive Session String",
                                    desc = "Enter the OTP code received in your Telegram app. The bot sends your Session String directly to your 'Saved Messages'!"
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "5",
                                    title = "Copy & Paste Here",
                                    desc = "Copy the string token from Saved Messages, return to TG-Server, tap 'Paste', and connect!"
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Bot username", "@StringSessionRobot"))
                                        Toast.makeText(context, "Copied @StringSessionRobot to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = telegramBlue),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26282F)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy @StringSessionRobot Username", fontSize = 12.sp)
                                }
                            } else {
                                // PC GUIDE
                                GuideStepItem(
                                    step = "1",
                                    title = "Install Pyrogram on PC",
                                    desc = "Run in your Windows PowerShell or Linux terminal: pip install pyrogram tgcrypto"
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "2",
                                    title = "Run 1-Line Generator",
                                    desc = "Run: python -m pyrogram\nLog in with your phone and get the string printed on screen."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "3",
                                    title = "Send to Phone & Paste",
                                    desc = "Send the string to your Telegram Saved Messages, open it on your phone, and paste it here."
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("PC Command", "pip install pyrogram tgcrypto && python -m pyrogram"))
                                        Toast.makeText(context, "Command copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = amberColor),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26282F)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy PC Terminal Command", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GuideStepItem(
    step: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .background(Color(0xFF26282F), CircleShape)
        ) {
            Text(
                text = step,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                color = TextTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
