package com.tgserver.streamer.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel
import com.tgserver.streamer.ui.components.*

@Composable
fun ApiCredentialsScreen(viewModel: MainAppViewModel) {
    val context = LocalContext.current
    val (savedApiId, savedApiHash) = viewModel.apiCredentials.collectAsState().value
    val isValidatingApi by viewModel.isValidatingApi.collectAsState()

    var apiIdInput by remember(savedApiId) {
        mutableStateOf(if (savedApiId > 0) savedApiId.toString() else "")
    }
    var apiHashInput by remember(savedApiHash) {
        mutableStateOf(savedApiHash)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Real-time format validation
    val apiIdError: String? = remember(apiIdInput) {
        when {
            apiIdInput.isBlank() -> null
            apiIdInput.toIntOrNull() == null -> "API ID must contain digits only"
            (apiIdInput.toIntOrNull() ?: 0) <= 0 -> "API ID must be positive"
            else -> null
        }
    }
    val apiHashError: String? = remember(apiHashInput) {
        when {
            apiHashInput.isBlank() -> null
            apiHashInput.length < 20 -> "Hash is too short (32 hex characters expected)"
            !apiHashInput.matches(Regex("[a-fA-F0-9]+")) -> "Hash must contain only hex characters (0-9, a-f)"
            else -> null
        }
    }

    val isFormValid = apiIdInput.isNotBlank() && apiHashInput.isNotBlank()
            && apiIdError == null && apiHashError == null

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
                    text = "TG-SERVER",
                    dotColor = AccentApple,
                    backgroundColor = Color(0x1FFFFFFF),
                    textColor = TextPrimary
                )

                NothingDotBadge(
                    text = "STEP 01/02",
                    dotColor = AccentSilver,
                    backgroundColor = Color(0x14FFFFFF),
                    textColor = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // HEADLINE & DESCRIPTION (Apple SF Pro clean hierarchy)
            Text(
                text = "Telegram API",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connect your Telegram API ID & Hash to establish the local media bridge and stream directly to Stremio.",
                color = TextSecondary,
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.2).sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            DotDivider(color = BorderSubtle, dotsCount = 20)

            Spacer(modifier = Modifier.height(20.dp))

            // INFO CARD: HOW TO RETRIEVE API CREDENTIALS (Nothing Squircle Card)
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SurfaceCard,
                borderColor = BorderSubtle,
                contentPadding = PaddingValues(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GET YOUR CREDENTIALS",
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    NothingDotBadge(
                        text = "OFFICIAL",
                        dotColor = StatusGreen,
                        textColor = StatusGreen
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuideStepRow(
                        step = "01",
                        text = "Log in with your phone on my.telegram.org"
                    )
                    GuideStepRow(
                        step = "02",
                        text = "Select 'API development tools'"
                    )
                    GuideStepRow(
                        step = "03",
                        text = "Create app, then copy your API ID & API Hash"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1-Click Link Button (Smoked secondary pill)
                NothingPillButton(
                    text = "Open my.telegram.org/apps ↗",
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org/apps"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Open my.telegram.org/apps in your browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = NothingButtonVariant.Secondary,
                    height = 44.dp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // INPUT 1: API ID
            NothingTextField(
                value = apiIdInput,
                onValueChange = {
                    apiIdInput = it.filter { c -> c.isDigit() }
                    errorMessage = null
                },
                label = "TELEGRAM API ID",
                badgeText = "NUMERIC",
                placeholder = "e.g. 12345678",
                isMonospace = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = apiIdError != null && apiIdInput.isNotBlank(),
                errorMessage = apiIdError,
                trailingIcon = {
                    when {
                        apiIdInput.isNotBlank() && apiIdError == null ->
                            Icon(Icons.Default.CheckCircle, null, tint = AccentSilver, modifier = Modifier.size(18.dp))
                        apiIdInput.isNotBlank() && apiIdError != null ->
                            Icon(Icons.Default.Warning, null, tint = StatusRed, modifier = Modifier.size(18.dp))
                    }
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // INPUT 2: API HASH
            NothingTextField(
                value = apiHashInput,
                onValueChange = {
                    apiHashInput = it.trim()
                    errorMessage = null
                },
                label = "TELEGRAM API HASH",
                badgeText = "32-HEX",
                placeholder = "e.g. a3b4c5d6e7f8091...",
                isMonospace = true,
                singleLine = true,
                isError = apiHashError != null && apiHashInput.isNotBlank(),
                errorMessage = apiHashError,
                trailingIcon = {
                    when {
                        apiHashInput.isNotBlank() && apiHashError == null ->
                            Icon(Icons.Default.CheckCircle, null, tint = AccentSilver, modifier = Modifier.size(18.dp))
                        apiHashInput.isNotBlank() && apiHashError != null ->
                            Icon(Icons.Default.Warning, null, tint = StatusRed, modifier = Modifier.size(18.dp))
                    }
                }
            )

            // Server-side validation error message banner
            AnimatedVisibility(
                visible = !errorMessage.isNullOrBlank(),
                enter = fadeIn(tween(200)) + expandVertically(spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF1B1113),
                    borderColor = StatusRed.copy(alpha = 0.4f),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(StatusRed, CircleShape)
                        )
                        Text(
                            text = errorMessage ?: "",
                            color = StatusRed,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // PRIMARY SUBMIT BUTTON (Solid White Pill with Spring Click)
            NothingPillButton(
                text = "Continue to Login →",
                onClick = {
                    val idParsed = apiIdInput.trim().toIntOrNull()
                    val hashTrimmed = apiHashInput.trim()

                    if (idParsed == null || idParsed <= 0) {
                        errorMessage = "Please enter a valid numeric Telegram API ID"
                        return@NothingPillButton
                    }
                    if (hashTrimmed.isBlank()) {
                        errorMessage = "Please enter your Telegram API hash"
                        return@NothingPillButton
                    }
                    if (apiIdError != null || apiHashError != null) {
                        errorMessage = apiIdError ?: apiHashError
                        return@NothingPillButton
                    }

                    viewModel.validateAndSaveApiCredentials(idParsed, hashTrimmed) { success, msg ->
                        if (!success) {
                            errorMessage = msg
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isFormValid && !isValidatingApi,
                isLoading = isValidatingApi,
                variant = NothingButtonVariant.Primary,
                height = 54.dp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // PRIVACY & LOCAL STORAGE GUARANTEE FOOTER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(TextTertiary, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Stored locally on device • Never shared externally",
                    color = TextTertiary,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.1.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideStepRow(
    step: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = step,
            color = AccentSilver,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
    }
}
