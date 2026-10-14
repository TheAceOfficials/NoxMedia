package com.tgserver.streamer.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import com.tgserver.streamer.ui.components.*

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

    // Tabs: 0: In-App Direct Login, 1: Session String & Colab
    var selectedAuthMode by remember { mutableIntStateOf(0) }

    // Inputs
    var phoneInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var password2FAInput by remember { mutableStateOf("") }
    var sessionInput by remember { mutableStateOf("") }

    // Guide state
    var selectedGuideTab by remember { mutableIntStateOf(0) } // 0: Colab, 1: PC
    var isGuideExpanded by remember { mutableStateOf(false) }

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

    AdaptiveScreenContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // TOP BRANDING BADGE & STEP INDICATOR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NothingDotBadge(
                    text = "NOXMEDIA",
                    dotColor = AccentApple,
                    backgroundColor = Color(0x1FFFFFFF),
                    textColor = TextPrimary
                )

                NothingDotBadge(
                    text = "STEP 02/02",
                    dotColor = AccentSilver,
                    backgroundColor = Color(0x14FFFFFF),
                    textColor = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // HEADLINE & DESCRIPTION (Apple SF Pro clean hierarchy)
            Text(
                text = "Connect Account",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Authenticate your Telegram account to allow Stremio to stream catalog media directly from your channels.",
                color = TextSecondary,
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.2).sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // API CREDENTIALS SUMMARY SQUIRCLE CARD
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SurfaceCard,
                borderColor = BorderSubtle,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "API CREDENTIALS",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        NothingDotBadge(
                            text = "ACTIVE",
                            dotColor = StatusGreen,
                            textColor = StatusGreen
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .background(SurfaceCardElevated)
                            .clickable { viewModel.clearApiCredentials() }
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Credentials",
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Edit",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCardElevated.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ID: $apiId",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "•",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "HASH: ${apiHash.take(6)}•••${apiHash.takeLast(4)}",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // MODERN SEGMENTED PILL TAB SWITCHER (Nothing OS Minimal Touch)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(SurfaceCardElevated)
                    .border(1.dp, BorderSubtle, CircleShape)
                    .padding(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // TAB 0: IN-APP DIRECT
                    val tab0Active = selectedAuthMode == 0
                    val tab0Bg by animateColorAsState(
                        targetValue = if (tab0Active) Color(0xFF26262E) else Color.Transparent,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "tab0_bg"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(CircleShape)
                            .background(tab0Bg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedAuthMode = 0 }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        if (tab0Active) AccentApple else TextTertiary,
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "In-App Login",
                                color = if (tab0Active) TextPrimary else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (tab0Active) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }

                    // TAB 1: SESSION STRING / COLAB
                    val tab1Active = selectedAuthMode == 1
                    val tab1Bg by animateColorAsState(
                        targetValue = if (tab1Active) Color(0xFF26262E) else Color.Transparent,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "tab1_bg"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(CircleShape)
                            .background(tab1Bg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedAuthMode = 1 }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        if (tab1Active) AccentApple else TextTertiary,
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "Session String",
                                color = if (tab1Active) TextPrimary else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (tab1Active) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CONTENT BASED ON SELECTED TAB
            if (selectedAuthMode == 0) {
                // ==================== MODE 0: IN-APP DIRECT PHONE LOGIN ====================
                if (!codeSentSuccess) {
                    // STEP 1: PHONE INPUT
                    NothingTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = "PHONE NUMBER",
                        placeholder = "+91 98765 43210",
                        isMonospace = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        trailingIcon = {
                            if (phoneInput.trim().length >= 8) {
                                Icon(Icons.Default.CheckCircle, null, tint = AccentSilver, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    NothingPillButton(
                        text = "Send Official Telegram Code →",
                        onClick = { viewModel.sendAuthCode(phoneInput) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSendingCode && phoneInput.trim().length >= 8,
                        isLoading = isSendingCode,
                        variant = NothingButtonVariant.Primary,
                        height = 54.dp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // PRIVACY NOTE (FROSTED OBSIDIAN CARD)
                    NothingCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = SurfaceCard,
                        borderColor = BorderSubtle,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentSilver, CircleShape)
                            )
                            Text(
                                text = "Runs 100% inside your device via embedded Chaquopy engine. No external server receives your credentials.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }

                } else if (!is2FARequired) {
                    // STEP 2: ENTER OTP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NothingDotBadge(
                            text = "CODE SENT TO TELEGRAM",
                            dotColor = StatusGreen,
                            textColor = StatusGreen
                        )

                        Text(
                            text = "Change phone",
                            color = AccentSilver,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.cancelPhoneAuth() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    NothingTextField(
                        value = otpInput,
                        onValueChange = { otpInput = it },
                        label = "CONFIRMATION CODE",
                        badgeText = "5 DIGITS",
                        placeholder = "•••••",
                        isMonospace = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            if (otpInput.trim().length >= 4) {
                                Icon(Icons.Default.CheckCircle, null, tint = AccentSilver, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    NothingPillButton(
                        text = "Verify & Connect →",
                        onClick = { viewModel.verifyAuthCode(otpInput) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isVerifyingCode && otpInput.trim().length >= 4,
                        isLoading = isVerifyingCode,
                        variant = NothingButtonVariant.Primary,
                        height = 54.dp
                    )

                } else {
                    // STEP 3: 2FA PASSWORD
                    NothingDotBadge(
                        text = "TWO-STEP VERIFICATION",
                        dotColor = AccentYellow,
                        textColor = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    NothingTextField(
                        value = password2FAInput,
                        onValueChange = { password2FAInput = it },
                        label = "CLOUD PASSWORD",
                        badgeText = "2FA",
                        placeholder = "Enter your 2FA password",
                        isMonospace = false,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    NothingPillButton(
                        text = "Unlock & Connect →",
                        onClick = { viewModel.verify2FAPassword(password2FAInput) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isVerifying2FA && password2FAInput.isNotBlank(),
                        isLoading = isVerifying2FA,
                        variant = NothingButtonVariant.Primary,
                        height = 54.dp
                    )
                }

            } else {
                // ==================== MODE 1: SESSION STRING / COLAB ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(AccentSilver, CircleShape)
                        )
                        Text(
                            text = "SESSION STRING",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sessionInput.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .border(1.dp, BorderSubtle, CircleShape)
                                    .background(SurfaceCardElevated)
                                    .clickable { sessionInput = "" }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = StatusRed,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Clear",
                                    color = StatusRed,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(1.dp, BorderSilver, CircleShape)
                                .background(SurfaceCardElevated)
                                .clickable {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        sessionInput = clipText.trim()
                                        Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = TextPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Paste",
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // MULTILINE TOKEN CONTAINER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            1.dp,
                            if (isFormatLikelyValid) StatusGreen.copy(alpha = 0.5f) else BorderSubtle,
                            RoundedCornerShape(20.dp)
                        )
                        .background(SurfaceCardElevated)
                        .padding(18.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 150.dp)) {
                        if (sessionInput.isEmpty()) {
                            Text(
                                text = "Paste Pyrogram or Telethon session string here...\n(e.g., 1BVtsOG...)",
                                color = TextTertiary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        BasicTextField(
                            value = sessionInput,
                            onValueChange = { sessionInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            cursorBrush = SolidColor(TextPrimary),
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                                letterSpacing = 0.3.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    DotDivider(color = BorderSubtle, dotsCount = 16)

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFormatLikelyValid) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(5.dp).background(StatusGreen, CircleShape))
                                Text("Ready to connect", color = StatusGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            Text("Session String required", color = TextTertiary, fontSize = 11.5.sp)
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
                }

                // Error text if any
                AnimatedVisibility(
                    visible = sessionFormatError != null || sessionImportError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = sessionFormatError ?: sessionImportError ?: "",
                        color = StatusRed,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // CONNECT BUTTON
                NothingPillButton(
                    text = "Connect & Start Server →",
                    onClick = {
                        if (trimmedSession.length < 20) {
                            Toast.makeText(context, "Please enter a valid session string", Toast.LENGTH_SHORT).show()
                            return@NothingPillButton
                        }
                        viewModel.importSessionString(trimmedSession)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isImporting && trimmedSession.length >= 20,
                    isLoading = isImporting,
                    variant = NothingButtonVariant.Primary,
                    height = 54.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // GOOGLE COLAB ACTION CARD
                NothingCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val url = "https://colab.research.google.com/github/TheAceOfficials/NoxMedia/blob/main/string_session.ipynb"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                    backgroundColor = SurfaceCardElevated,
                    borderColor = BorderSubtle,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1AFFFFFF))
                                    .border(1.dp, BorderSubtle, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = AccentSilver,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Generate via Google Colab",
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.2).sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Official cloud sandbox • 100% private",
                                    color = TextTertiary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in browser",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // EXPANDABLE GUIDE SECTION (Nothing Card)
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SurfaceCard,
                    borderColor = BorderSubtle,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isGuideExpanded = !isGuideExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentSilver, CircleShape)
                            )
                            Text(
                                text = "How to get Session String in 1 min?",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Icon(
                            imageVector = if (isGuideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isGuideExpanded,
                        enter = fadeIn(tween(200)) + expandVertically(spring(stiffness = Spring.StiffnessLow)),
                        exit = fadeOut(tween(150)) + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            // Mini Guide Tab Switcher
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(SurfaceCardElevated)
                                    .padding(3.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedGuideTab == 0) Color(0xFF26262E) else Color.Transparent)
                                        .clickable { selectedGuideTab = 0 }
                                ) {
                                    Text(
                                        text = "Google Colab (Safe)",
                                        color = if (selectedGuideTab == 0) TextPrimary else TextTertiary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedGuideTab == 1) Color(0xFF26262E) else Color.Transparent)
                                        .clickable { selectedGuideTab = 1 }
                                ) {
                                    Text(
                                        text = "PC Terminal",
                                        color = if (selectedGuideTab == 1) TextPrimary else TextTertiary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (selectedGuideTab == 0) {
                                GuideStepItem(
                                    step = "01",
                                    title = "Open Google Colab Sandbox",
                                    desc = "Tap 'Generate via Google Colab' button above to open the verified notebook in browser."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "02",
                                    title = "Run the Code Cell",
                                    desc = "Tap ▶ Play button. It runs in Google's cloud sandbox, 100% private."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "03",
                                    title = "Enter API Keys & OTP",
                                    desc = "Enter your API ID ($apiId), Hash, and official Telegram OTP."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "04",
                                    title = "Copy String to App",
                                    desc = "The string is sent straight to your Telegram Saved Messages chat. Paste it above."
                                )
                            } else {
                                GuideStepItem(
                                    step = "01",
                                    title = "Run in Terminal",
                                    desc = "In your PC terminal inside NoxMedia: venv/bin/python generate_string.py"
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "02",
                                    title = "Enter Credentials",
                                    desc = "Enter your API ID and phone number to generate session string."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                GuideStepItem(
                                    step = "03",
                                    title = "Copy & Paste",
                                    desc = "Copy the output from terminal or Telegram Saved Messages and paste above."
                                )
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
        Text(
            text = step,
            color = AccentSilver,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                color = TextTertiary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }
    }
}
