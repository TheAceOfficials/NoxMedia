package com.tgserver.streamer.ui.screens

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TelegramLoginScreen(viewModel: MainAppViewModel) {
    val isSendingCode by viewModel.isSendingCode.collectAsState()
    val isVerifyingCode by viewModel.isVerifyingCode.collectAsState()
    val codeSentSuccess by viewModel.codeSentSuccess.collectAsState()

    var phoneInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var sessionStringInput by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf("PHONE") } // "PHONE", "OTP", "SESSION_STRING", "QR_WEB"
    var phoneError by remember { mutableStateOf<String?>(null) }
    var otpError by remember { mutableStateOf<String?>(null) }
    var sessionError by remember { mutableStateOf<String?>(null) }

    // OTP resend cooldown timer
    val scope = rememberCoroutineScope()
    var resendCooldown by remember { mutableIntStateOf(0) }
    val canResend = resendCooldown <= 0

    val amberButtonColor = Color(0xFFF3A43B)
    val inputBackground = Color(0xFF18181C)
    val buttonDarkBackground = Color(0xFF1A1A1E)
    val errorRed = Color(0xFFFF5252)

    // When code is sent, move to OTP screen and start resend cooldown
    LaunchedEffect(codeSentSuccess) {
        if (codeSentSuccess) {
            authMode = "OTP"
            // Start 60s cooldown for resend
            resendCooldown = 60
            scope.launch {
                while (resendCooldown > 0) {
                    delay(1000)
                    resendCooldown--
                }
            }
        }
    }

    // Phone number real-time validation
    val phoneFormatError: String? = remember(phoneInput) {
        if (phoneInput.isBlank()) null
        else viewModel.validatePhoneNumber(phoneInput)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (authMode == "QR_WEB") {
            // WEBVIEW QR / TELEGRAM WEB AUTH
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Telegram Web Auth",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = { authMode = "PHONE" },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C32)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("← Back", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF2C2C32), RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { webContext ->
                            WebView(webContext).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(
                                            "(function() { return localStorage.getItem('user_auth') || localStorage.getItem('account_id'); })();"
                                        ) { value ->
                                            if (!value.isNullOrBlank() && value != "null") {
                                                viewModel.loginWithWebSession("Telegram Web User", "web_user", System.currentTimeMillis() % 100000000L, value)
                                            }
                                        }
                                    }
                                }

                                loadUrl("https://web.telegram.org/k/")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.loginWithWebSession(
                            "Telegram User", "tg_user",
                            System.currentTimeMillis() % 100000000L, "web_auth_confirmed"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✓ Complete Login", color = BackgroundOled, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            AnimatedContent(
                targetState = authMode,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                },
                label = "authModeTransition"
            ) { currentMode ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // BRANDING HEADER
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(AccentApple, RoundedCornerShape(5.dp))
                        )
                        Text(
                            text = "TG-Server",
                            color = TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Your Telegram channels as a Stremio catalog, served from this device.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(44.dp))

                    when (currentMode) {
                        "PHONE" -> PhoneInputSection(
                            phoneInput = phoneInput,
                            phoneFormatError = phoneFormatError,
                            phoneError = phoneError,
                            isSendingCode = isSendingCode,
                            amberButtonColor = amberButtonColor,
                            inputBackground = inputBackground,
                            buttonDarkBackground = buttonDarkBackground,
                            errorRed = errorRed,
                            onPhoneChange = { phoneInput = it; phoneError = null },
                            onSendCode = {
                                val validationErr = viewModel.validatePhoneNumber(phoneInput.trim())
                                if (validationErr != null) {
                                    phoneError = validationErr
                                    return@PhoneInputSection
                                }
                                viewModel.sendAuthCode(phoneInput.trim())
                            },
                            onQrWeb = { authMode = "QR_WEB" },
                            onChangeApi = { viewModel.clearApiCredentials() },
                            onSessionString = { authMode = "SESSION_STRING" }
                        )

                        "OTP" -> OtpInputSection(
                            phoneInput = phoneInput,
                            otpInput = otpInput,
                            otpError = otpError,
                            isVerifyingCode = isVerifyingCode,
                            isSendingCode = isSendingCode,
                            canResend = canResend,
                            resendCooldown = resendCooldown,
                            amberButtonColor = amberButtonColor,
                            inputBackground = inputBackground,
                            buttonDarkBackground = buttonDarkBackground,
                            errorRed = errorRed,
                            onOtpChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { otpInput = it; otpError = null } },
                            onVerify = {
                                if (otpInput.trim().isEmpty()) {
                                    otpError = "Please enter the verification code"
                                    return@OtpInputSection
                                }
                                if (otpInput.trim().length < 4) {
                                    otpError = "Code is too short. Telegram sends 5-digit codes."
                                    return@OtpInputSection
                                }
                                viewModel.verifyAuthCode(otpInput.trim())
                            },
                            onResend = {
                                otpInput = ""
                                otpError = null
                                viewModel.resendAuthCode(phoneInput.trim())
                            },
                            onBack = {
                                authMode = "PHONE"
                                otpInput = ""
                                otpError = null
                            }
                        )

                        "SESSION_STRING" -> SessionStringSection(
                            sessionStringInput = sessionStringInput,
                            sessionError = sessionError,
                            amberButtonColor = amberButtonColor,
                            inputBackground = inputBackground,
                            buttonDarkBackground = buttonDarkBackground,
                            errorRed = errorRed,
                            onSessionChange = { sessionStringInput = it; sessionError = null },
                            onImport = {
                                if (sessionStringInput.trim().length < 10) {
                                    sessionError = "Please enter a valid session string"
                                    return@SessionStringSection
                                }
                                viewModel.importSessionString(sessionStringInput.trim())
                            },
                            onBack = { authMode = "PHONE" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PhoneInputSection(
    phoneInput: String,
    phoneFormatError: String?,
    phoneError: String?,
    isSendingCode: Boolean,
    amberButtonColor: Color,
    inputBackground: Color,
    buttonDarkBackground: Color,
    errorRed: Color,
    onPhoneChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onQrWeb: () -> Unit,
    onChangeApi: () -> Unit,
    onSessionString: () -> Unit
) {
    Text("Phone number", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "Include country code, e.g. +91 9876543210",
        color = TextTertiary,
        fontSize = 13.sp
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Phone input
    val displayError = phoneError ?: (if (phoneInput.isNotBlank()) phoneFormatError else null)
    val hasError = displayError != null

    OutlinedTextField(
        value = phoneInput,
        onValueChange = { onPhoneChange(it) },
        placeholder = {
            Text("+91 XXXXXXXXXX", color = TextTertiary.copy(alpha = 0.6f), fontSize = 15.sp)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = RoundedCornerShape(12.dp),
        isError = hasError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = if (hasError) errorRed else amberButtonColor,
            unfocusedBorderColor = if (hasError) errorRed.copy(alpha = 0.7f) else Color(0xFF2C2C32),
            focusedContainerColor = inputBackground,
            unfocusedContainerColor = inputBackground,
            errorBorderColor = errorRed,
            errorContainerColor = inputBackground
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    )

    AnimatedVisibility(visible = hasError, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = errorRed, modifier = Modifier.size(13.dp))
            Text(text = displayError ?: "", color = errorRed, fontSize = 11.sp)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // SEND CODE BUTTON
    Button(
        onClick = onSendCode,
        enabled = !isSendingCode && phoneInput.isNotBlank(),
        colors = ButtonDefaults.buttonColors(
            containerColor = amberButtonColor,
            contentColor = Color(0xFF1E1402),
            disabledContainerColor = amberButtonColor.copy(alpha = 0.4f),
            disabledContentColor = Color(0xFF1E1402).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        if (isSendingCode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF1E1402), strokeWidth = 2.dp)
                Text("Sending code...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Send verification code", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onQrWeb,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C32)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = buttonDarkBackground, contentColor = TextPrimary),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("Use QR code instead", fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onChangeApi,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C32)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = buttonDarkBackground, contentColor = TextSecondary),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("Change API credentials", fontSize = 14.sp)
    }

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(
        onClick = onSessionString,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
        Text("Login with Session String Token", color = Color(0xFF64B5F6), fontSize = 13.sp)
    }
}

@Composable
private fun ColumnScope.OtpInputSection(
    phoneInput: String,
    otpInput: String,
    otpError: String?,
    isVerifyingCode: Boolean,
    isSendingCode: Boolean,
    canResend: Boolean,
    resendCooldown: Int,
    amberButtonColor: Color,
    inputBackground: Color,
    buttonDarkBackground: Color,
    errorRed: Color,
    onOtpChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    Text("Verification code", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)

    Spacer(modifier = Modifier.height(6.dp))

    // Code sent confirmation banner
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A3A1A), RoundedCornerShape(10.dp))
            .border(1.dp, StatusGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                "✓ Code sent to Telegram",
                color = StatusGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Check your Telegram app for a message from Telegram with the code. Phone: $phoneInput",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // OTP Input - large digits
    OutlinedTextField(
        value = otpInput,
        onValueChange = onOtpChange,
        placeholder = {
            Text("Enter 5-digit code", color = TextTertiary.copy(alpha = 0.6f), fontSize = 15.sp)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        isError = otpError != null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = if (otpError != null) errorRed else StatusGreen,
            unfocusedBorderColor = if (otpError != null) errorRed.copy(alpha = 0.7f) else Color(0xFF2C2C32),
            focusedContainerColor = inputBackground,
            unfocusedContainerColor = inputBackground,
            errorBorderColor = errorRed,
            errorContainerColor = inputBackground
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    )

    AnimatedVisibility(visible = otpError != null, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = errorRed, modifier = Modifier.size(13.dp))
            Text(text = otpError ?: "", color = errorRed, fontSize = 11.sp)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // VERIFY BUTTON
    Button(
        onClick = onVerify,
        enabled = !isVerifyingCode && otpInput.length >= 4,
        colors = ButtonDefaults.buttonColors(
            containerColor = StatusGreen,
            contentColor = BackgroundOled,
            disabledContainerColor = StatusGreen.copy(alpha = 0.4f),
            disabledContentColor = BackgroundOled.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        if (isVerifyingCode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BackgroundOled, strokeWidth = 2.dp)
                Text("Verifying...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Verify & Login", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // RESEND CODE BUTTON
    if (isSendingCode) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = TextTertiary, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Resending code...", color = TextTertiary, fontSize = 13.sp)
        }
    } else {
        TextButton(
            onClick = onResend,
            enabled = canResend,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            if (canResend) {
                Text("Didn't get code? Resend", color = Color(0xFF64B5F6), fontSize = 13.sp)
            } else {
                Text("Resend in ${resendCooldown}s", color = TextTertiary, fontSize = 13.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onBack,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C32)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = buttonDarkBackground, contentColor = TextSecondary),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("← Change phone number", fontSize = 14.sp)
    }
}

@Composable
private fun ColumnScope.SessionStringSection(
    sessionStringInput: String,
    sessionError: String?,
    amberButtonColor: Color,
    inputBackground: Color,
    buttonDarkBackground: Color,
    errorRed: Color,
    onSessionChange: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    Text("Session String", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "Paste your Pyrogram, Telethon, or StringSession token below",
        color = TextTertiary,
        fontSize = 13.sp
    )

    Spacer(modifier = Modifier.height(20.dp))

    OutlinedTextField(
        value = sessionStringInput,
        onValueChange = onSessionChange,
        placeholder = {
            Text("1BJW9b... (Session String)", color = TextTertiary.copy(alpha = 0.6f), fontSize = 13.sp)
        },
        minLines = 3,
        maxLines = 5,
        shape = RoundedCornerShape(12.dp),
        isError = sessionError != null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = if (sessionError != null) errorRed else amberButtonColor,
            unfocusedBorderColor = if (sessionError != null) errorRed.copy(alpha = 0.7f) else Color(0xFF2C2C32),
            focusedContainerColor = inputBackground,
            unfocusedContainerColor = inputBackground,
            errorBorderColor = errorRed,
            errorContainerColor = inputBackground
        ),
        modifier = Modifier.fillMaxWidth()
    )

    AnimatedVisibility(visible = sessionError != null, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = errorRed, modifier = Modifier.size(13.dp))
            Text(text = sessionError ?: "", color = errorRed, fontSize = 11.sp)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onImport,
        enabled = sessionStringInput.isNotBlank(),
        colors = ButtonDefaults.buttonColors(
            containerColor = amberButtonColor,
            contentColor = Color(0xFF1E1402),
            disabledContainerColor = amberButtonColor.copy(alpha = 0.4f),
            disabledContentColor = Color(0xFF1E1402).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("Import & Login", fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onBack,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C32)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = buttonDarkBackground, contentColor = TextSecondary),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("← Use Phone Number instead", fontSize = 14.sp)
    }
}
