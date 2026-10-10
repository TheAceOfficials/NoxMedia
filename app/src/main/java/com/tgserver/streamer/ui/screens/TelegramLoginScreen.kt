package com.tgserver.streamer.ui.screens

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel

@Composable
fun TelegramLoginScreen(viewModel: MainAppViewModel) {
    val context = LocalContext.current
    val isSendingCode by viewModel.isSendingCode.collectAsState()
    val isVerifyingCode by viewModel.isVerifyingCode.collectAsState()
    val codeSentSuccess by viewModel.codeSentSuccess.collectAsState()

    var phoneInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var sessionStringInput by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf("PHONE") } // "PHONE", "OTP", "SESSION_STRING", "QR_WEB"
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val amberButtonColor = Color(0xFFF3A43B)
    val inputBackground = Color(0xFF18181C)
    val buttonDarkBackground = Color(0xFF1A1A1E)

    // Sync authMode when code is sent
    LaunchedEffect(codeSentSuccess) {
        if (codeSentSuccess) {
            authMode = "OTP"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (authMode == "QR_WEB") {
            // WEBVIEW QR / TELEGRAM WEB AUTH SHEET
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
                        Text("Close", color = TextSecondary, fontSize = 12.sp)
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
                                        // Detect if user logged in inside Telegram Web K
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
                        viewModel.loginWithWebSession("Telegram User", "tg_user", System.currentTimeMillis() % 100000000L, "web_auth_confirmed")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Complete Login", color = BackgroundOled, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // BRANDING HEADER (TG-Server)
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

                when (authMode) {
                    "PHONE" -> {
                        // PHONE INPUT VIEW
                        Text(
                            text = "Phone number",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Include the country code, e.g. +91...",
                            color = TextTertiary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = {
                                phoneInput = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text("Phone", color = TextTertiary.copy(alpha = 0.6f), fontSize = 15.sp)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = amberButtonColor,
                                unfocusedBorderColor = Color(0xFF2C2C32),
                                focusedContainerColor = inputBackground,
                                unfocusedContainerColor = inputBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        )

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = errorMessage!!, color = StatusRed, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // ACTION 1: SEND CODE
                        Button(
                            onClick = {
                                val cleanPhone = phoneInput.trim()
                                if (cleanPhone.length < 8) {
                                    errorMessage = "Please enter a valid phone number with country code (e.g. +91...)"
                                    return@Button
                                }
                                viewModel.sendAuthCode(cleanPhone)
                            },
                            enabled = !isSendingCode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = amberButtonColor,
                                contentColor = Color(0xFF1E1402)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isSendingCode) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF1E1402), strokeWidth = 2.dp)
                            } else {
                                Text(text = "Send code", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ACTION 2: USE A QR CODE INSTEAD / WEB AUTH
                        OutlinedButton(
                            onClick = { authMode = "QR_WEB" },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C32)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = buttonDarkBackground,
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(text = "Use a QR code instead", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ACTION 3: CHANGE API CREDENTIALS
                        OutlinedButton(
                            onClick = {
                                viewModel.saveApiCredentials(0, "")
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C32)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = buttonDarkBackground,
                                contentColor = TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(text = "Change API credentials", fontSize = 14.sp, fontWeight = FontWeight.Normal)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ACTION 4: SESSION STRING TOGGLE
                        TextButton(
                            onClick = { authMode = "SESSION_STRING" },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Or login with Session String Token", color = Color(0xFF64B5F6), fontSize = 13.sp)
                        }
                    }

                    "OTP" -> {
                        // OTP VERIFICATION VIEW
                        Text(
                            text = "Verification code",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Enter the code sent to your Telegram app for $phoneInput",
                            color = TextTertiary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = {
                                otpInput = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text("Code from Telegram app", color = TextTertiary.copy(alpha = 0.6f), fontSize = 15.sp)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = StatusGreen,
                                unfocusedBorderColor = Color(0xFF2C2C32),
                                focusedContainerColor = inputBackground,
                                unfocusedContainerColor = inputBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        )

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = errorMessage!!, color = StatusRed, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (otpInput.trim().length < 4) {
                                    errorMessage = "Please enter the full code"
                                    return@Button
                                }
                                viewModel.verifyAuthCode(otpInput)
                            },
                            enabled = !isVerifyingCode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusGreen,
                                contentColor = BackgroundOled
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isVerifyingCode) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BackgroundOled, strokeWidth = 2.dp)
                            } else {
                                Text(text = "Verify & Login", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { authMode = "PHONE" },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C32)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = buttonDarkBackground,
                                contentColor = TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(text = "Back to phone number", fontSize = 14.sp)
                        }
                    }

                    "SESSION_STRING" -> {
                        // SESSION STRING VIEW
                        Text(
                            text = "Session String",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Paste your Pyrogram, Telethon, or StringSession token below",
                            color = TextTertiary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = sessionStringInput,
                            onValueChange = {
                                sessionStringInput = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text("1BJW9b... (Session String)", color = TextTertiary.copy(alpha = 0.6f), fontSize = 13.sp)
                            },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = amberButtonColor,
                                unfocusedBorderColor = Color(0xFF2C2C32),
                                focusedContainerColor = inputBackground,
                                unfocusedContainerColor = inputBackground
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = errorMessage!!, color = StatusRed, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (sessionStringInput.trim().length < 10) {
                                    errorMessage = "Please enter a valid session string"
                                    return@Button
                                }
                                viewModel.importSessionString(sessionStringInput)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = amberButtonColor,
                                contentColor = Color(0xFF1E1402)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(text = "Import & Login", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { authMode = "PHONE" },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C32)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = buttonDarkBackground,
                                contentColor = TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(text = "Use Phone Number instead", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
