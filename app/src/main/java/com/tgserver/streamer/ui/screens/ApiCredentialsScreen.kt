package com.tgserver.streamer.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*
import com.tgserver.streamer.ui.MainAppViewModel

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
            apiIdInput.isBlank() -> null // no error while empty
            apiIdInput.toIntOrNull() == null -> "API ID must be a number"
            (apiIdInput.toIntOrNull() ?: 0) <= 0 -> "API ID must be positive"
            else -> null
        }
    }
    val apiHashError: String? = remember(apiHashInput) {
        when {
            apiHashInput.isBlank() -> null
            apiHashInput.length < 20 -> "Hash is too short (should be 32 chars)"
            !apiHashInput.matches(Regex("[a-fA-F0-9]+")) -> "Hash should only contain hex chars (0-9, a-f)"
            else -> null
        }
    }

    val amberButtonColor = Color(0xFFF3A43B)
    val inputBackground = Color(0xFF18181C)
    val validGreen = Color(0xFF4CAF50)
    val errorRed = Color(0xFFFF5252)

    val isFormValid = apiIdInput.isNotBlank() && apiHashInput.isNotBlank()
        && apiIdError == null && apiHashError == null

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
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // APP BRANDING HEADER
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

            // SECTION TITLE
            Text(
                text = "API credentials",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org/apps"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Open my.telegram.org/apps in your browser", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(text = "From ", color = TextTertiary, fontSize = 13.sp)
                Text(
                    text = "my.telegram.org/apps",
                    color = Color(0xFF64B5F6),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(text = " → API development tools.", color = TextTertiary, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // HOW TO GET CREDENTIALS INFO BOX
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1E), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF2C2C32), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("How to get API credentials:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("1. Go to my.telegram.org and login with your phone", color = TextTertiary, fontSize = 12.sp)
                    Text("2. Click 'API development tools'", color = TextTertiary, fontSize = 12.sp)
                    Text("3. Create a new app and copy the API ID & Hash", color = TextTertiary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // INPUT 1: API ID
            Column {
                OutlinedTextField(
                    value = apiIdInput,
                    onValueChange = {
                        apiIdInput = it.filter { c -> c.isDigit() }
                        errorMessage = null
                    },
                    label = { Text("API ID", fontSize = 12.sp) },
                    placeholder = {
                        Text("e.g. 12345678", color = TextTertiary.copy(alpha = 0.6f), fontSize = 15.sp)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    isError = apiIdError != null && apiIdInput.isNotBlank(),
                    trailingIcon = {
                        when {
                            apiIdInput.isNotBlank() && apiIdError == null ->
                                Icon(Icons.Default.CheckCircle, null, tint = validGreen, modifier = Modifier.size(18.dp))
                            apiIdInput.isNotBlank() && apiIdError != null ->
                                Icon(Icons.Default.Warning, null, tint = errorRed, modifier = Modifier.size(18.dp))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = if (apiIdError != null && apiIdInput.isNotBlank()) errorRed else amberButtonColor,
                        unfocusedBorderColor = if (apiIdError != null && apiIdInput.isNotBlank()) errorRed.copy(alpha = 0.7f) else Color(0xFF2C2C32),
                        focusedContainerColor = inputBackground,
                        unfocusedContainerColor = inputBackground,
                        focusedLabelColor = amberButtonColor,
                        unfocusedLabelColor = TextTertiary,
                        errorBorderColor = errorRed,
                        errorContainerColor = inputBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                )
                AnimatedVisibility(
                    visible = apiIdError != null && apiIdInput.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = apiIdError ?: "",
                        color = errorRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // INPUT 2: API HASH
            Column {
                OutlinedTextField(
                    value = apiHashInput,
                    onValueChange = {
                        apiHashInput = it.trim()
                        errorMessage = null
                    },
                    label = { Text("API Hash", fontSize = 12.sp) },
                    placeholder = {
                        Text("e.g. a3b4c5d6... (32 hex chars)", color = TextTertiary.copy(alpha = 0.6f), fontSize = 13.sp)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = apiHashError != null && apiHashInput.isNotBlank(),
                    trailingIcon = {
                        when {
                            apiHashInput.isNotBlank() && apiHashError == null ->
                                Icon(Icons.Default.CheckCircle, null, tint = validGreen, modifier = Modifier.size(18.dp))
                            apiHashInput.isNotBlank() && apiHashError != null ->
                                Icon(Icons.Default.Warning, null, tint = errorRed, modifier = Modifier.size(18.dp))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = if (apiHashError != null && apiHashInput.isNotBlank()) errorRed else amberButtonColor,
                        unfocusedBorderColor = if (apiHashError != null && apiHashInput.isNotBlank()) errorRed.copy(alpha = 0.7f) else Color(0xFF2C2C32),
                        focusedContainerColor = inputBackground,
                        unfocusedContainerColor = inputBackground,
                        focusedLabelColor = amberButtonColor,
                        unfocusedLabelColor = TextTertiary,
                        errorBorderColor = errorRed,
                        errorContainerColor = inputBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                AnimatedVisibility(
                    visible = apiHashError != null && apiHashInput.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = apiHashError ?: "",
                        color = errorRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }

            // Server-side error message (from validation attempt)
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(errorRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .border(1.dp, errorRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = errorRed,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Text(text = errorMessage!!, color = errorRed, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // CONTINUE BUTTON
            Button(
                onClick = {
                    val idParsed = apiIdInput.trim().toIntOrNull()
                    val hashTrimmed = apiHashInput.trim()

                    if (idParsed == null || idParsed <= 0) {
                        errorMessage = "Please enter a valid numeric Telegram API ID"
                        return@Button
                    }
                    if (hashTrimmed.isBlank()) {
                        errorMessage = "Please enter your Telegram API hash"
                        return@Button
                    }
                    if (apiIdError != null || apiHashError != null) {
                        errorMessage = apiIdError ?: apiHashError
                        return@Button
                    }

                    viewModel.validateAndSaveApiCredentials(idParsed, hashTrimmed) { success, msg ->
                        if (!success) {
                            errorMessage = msg
                        }
                    }
                },
                enabled = isFormValid && !isValidatingApi,
                colors = ButtonDefaults.buttonColors(
                    containerColor = amberButtonColor,
                    contentColor = Color(0xFF1E1402),
                    disabledContainerColor = amberButtonColor.copy(alpha = 0.4f),
                    disabledContentColor = Color(0xFF1E1402).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isValidatingApi) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF1E1402),
                            strokeWidth = 2.dp
                        )
                        Text(text = "Verifying credentials...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(text = "Continue →", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your API credentials are stored locally on this device only.",
                color = TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
