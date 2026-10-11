package com.tgserver.streamer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel

@Composable
fun TelegramLoginScreen(viewModel: MainAppViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Session String state
    val isImporting by viewModel.isImportingSession.collectAsState()
    val sessionImportError by viewModel.sessionImportError.collectAsState()

    // Phone Auth state (Embedded Chaquopy)
    val isSendingCode by viewModel.isSendingCode.collectAsState()
    val isVerifyingCode by viewModel.isVerifyingCode.collectAsState()
    val codeSentSuccess by viewModel.codeSentSuccess.collectAsState()
    val is2FARequired by viewModel.is2FARequired.collectAsState()
    val isVerifying2FA by viewModel.isVerifying2FA.collectAsState()

    val (apiId, apiHash) = viewModel.apiCredentials.collectAsState().value

    // Tabs: 0: In-App Phone Login, 1: Session String & Colab
    var selectedAuthMode by remember { mutableIntStateOf(0) }

    // Inputs
    var phoneInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var password2FAInput by remember { mutableStateOf("") }
    var sessionInput by remember { mutableStateOf("") }

    // Guide state
    var selectedGuideTab by remember { mutableIntStateOf(0) } // 0: Mobile, 1: PC
    var isGuideExpanded by remember { mutableStateOf(false) }

    val amberColor = Color(0xFFF3A43B)
    val inputBackground = Color(0xFF131418)
    val cardBackground = Color(0xFF16171C)
    val cardBorder = Color(0xFF26282F)
    val validGreen = Color(0xFF34C759)
    val telegramBlue = Color(0xFF2AABEE)

    // Validation for session string
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
                        text = "EMBEDDED MTPROTO",
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
                text = "Connect your account to stream high-speed video directly to Stremio. Choose your preferred login method below.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

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

            Spacer(modifier = Modifier.height(20.dp))

            // DUAL-MODE TAB SWITCHER (In-App Phone vs Session String)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1014), RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedAuthMode == 0) cardBackground else Color.Transparent)
                        .clickable { selectedAuthMode = 0 }
                        .padding(vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = if (selectedAuthMode == 0) amberColor else TextTertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "In-App Direct Login",
                            color = if (selectedAuthMode == 0) TextPrimary else TextTertiary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedAuthMode == 1) cardBackground else Color.Transparent)
                        .clickable { selectedAuthMode = 1 }
                        .padding(vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = if (selectedAuthMode == 1) amberColor else TextTertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Session String / Colab",
                            color = if (selectedAuthMode == 1) TextPrimary else TextTertiary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedAuthMode == 0) {
                // ==================== MODE 0: IN-APP DIRECT PHONE LOGIN ====================
                if (!codeSentSuccess) {
                    // STEP 1: ENTER PHONE NUMBER
                    Text(
                        text = "PHONE NUMBER (WITH COUNTRY CODE)",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        placeholder = {
                            Text(
                                text = "+919876543210 (Include country code)",
                                color = TextTertiary.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = amberColor,
                            unfocusedBorderColor = cardBorder,
                            focusedContainerColor = inputBackground,
                            unfocusedContainerColor = inputBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { viewModel.sendAuthCode(phoneInput) },
                        enabled = !isSendingCode && phoneInput.trim().length >= 8,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = amberColor,
                            contentColor = Color(0xFF1E1402)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isSendingCode) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF1E1402),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connecting via Pyrogram Engine...", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Send Official Telegram Code", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "🔒 Runs 100% inside your phone using embedded Chaquopy engine. No external server receives your phone number.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                } else if (!is2FARequired) {
                    // STEP 2: ENTER OTP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ENTER CONFIRMATION CODE",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        TextButton(
                            onClick = { viewModel.cancelPhoneAuth() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Change phone", color = amberColor, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { otpInput = it },
                        placeholder = {
                            Text("5-digit code sent to Telegram app", color = TextTertiary.copy(alpha = 0.6f), fontSize = 13.sp)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = validGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedContainerColor = inputBackground,
                            unfocusedContainerColor = inputBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { viewModel.verifyAuthCode(otpInput) },
                        enabled = !isVerifyingCode && otpInput.trim().length >= 4,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = validGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isVerifyingCode) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Authorizing Session...", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Verify & Connect", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else {
                    // STEP 3: 2FA PASSWORD
                    Text(
                        text = "TWO-STEP VERIFICATION PASSWORD",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password2FAInput,
                        onValueChange = { password2FAInput = it },
                        placeholder = {
                            Text("Enter your 2FA password", color = TextTertiary.copy(alpha = 0.6f), fontSize = 13.sp)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = amberColor,
                            unfocusedBorderColor = cardBorder,
                            focusedContainerColor = inputBackground,
                            unfocusedContainerColor = inputBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { viewModel.verify2FAPassword(password2FAInput) },
                        enabled = !isVerifying2FA && password2FAInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = amberColor,
                            contentColor = Color(0xFF1E1402)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isVerifying2FA) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF1E1402), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Verifying 2FA...", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Unlock & Connect", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

            } else {
                // ==================== MODE 1: SESSION STRING / COLAB ====================
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

                        // PASTE BUTTON
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

                Spacer(modifier = Modifier.height(20.dp))

                // CONNECT STRING BUTTON
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
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF1E1402),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Connecting Account...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Connect & Start Server", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // GOOGLE COLAB 1-CLICK BUTTON
                OutlinedButton(
                    onClick = {
                        val url = "https://colab.research.google.com/github/TheAceOfficials/TG-Server/blob/main/string_session.ipynb"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26282F)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Cloud, null, tint = amberColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate String via Google Colab (1-Click)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                                            text = "☁️ Google Colab (Safe)",
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
                                            text = "💻 PC Terminal",
                                            color = if (selectedGuideTab == 1) amberColor else TextTertiary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                if (selectedGuideTab == 0) {
                                    GuideStepItem(
                                        step = "1",
                                        title = "Open Google Colab Sandbox",
                                        desc = "Tap 'Generate via Google Colab' button above to open the official verified script in your browser."
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    GuideStepItem(
                                        step = "2",
                                        title = "Run the Code Cell",
                                        desc = "Tap ▶ Play button. It runs in Google's cloud sandbox, 100% private to you."
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    GuideStepItem(
                                        step = "3",
                                        title = "Enter API Keys & OTP",
                                        desc = "Enter your API ID ($apiId), Hash, and official Telegram OTP."
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    GuideStepItem(
                                        step = "4",
                                        title = "Copy String to App",
                                        desc = "The string is sent straight to your Telegram Saved Messages chat. Copy and paste it here!"
                                    )
                                } else {
                                    GuideStepItem(
                                        step = "1",
                                        title = "Run in Terminal",
                                        desc = "In your PC terminal inside TG-Server: venv/bin/python generate_string.py"
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    GuideStepItem(
                                        step = "2",
                                        title = "Enter Credentials",
                                        desc = "Enter your API ID and phone number to generate session string."
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    GuideStepItem(
                                        step = "3",
                                        title = "Copy & Paste",
                                        desc = "Copy the output from terminal or Telegram Saved Messages and paste above."
                                    )
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
