package com.tgserver.streamer.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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

    var apiIdInput by remember(savedApiId) { 
        mutableStateOf(if (savedApiId > 0) savedApiId.toString() else "") 
    }
    var apiHashInput by remember(savedApiHash) { 
        mutableStateOf(savedApiHash) 
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val amberButtonColor = Color(0xFFF3A43B)
    val inputBackground = Color(0xFF18181C)

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
            Text(
                text = "TeleStremio",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your Telegram channels as a Stremio catalog, served from this device.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Open my.telegram.org in browser", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(
                    text = "From ",
                    color = TextTertiary,
                    fontSize = 13.sp
                )
                Text(
                    text = "my.telegram.org",
                    color = Color(0xFF64B5F6),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = " \u2192 API development tools.",
                    color = TextTertiary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // INPUT 1: API ID
            OutlinedTextField(
                value = apiIdInput,
                onValueChange = { 
                    apiIdInput = it
                    errorMessage = null 
                },
                placeholder = { 
                    Text("API ID", color = TextTertiary.copy(alpha = 0.6f), fontSize = 15.sp) 
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

            Spacer(modifier = Modifier.height(16.dp))

            // INPUT 2: API HASH
            OutlinedTextField(
                value = apiHashInput,
                onValueChange = { 
                    apiHashInput = it
                    errorMessage = null 
                },
                placeholder = { 
                    Text("API hash", color = TextTertiary.copy(alpha = 0.6f), fontSize = 15.sp) 
                },
                singleLine = true,
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
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = StatusRed,
                    fontSize = 12.sp
                )
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

                    viewModel.saveApiCredentials(idParsed, hashTrimmed)
                    Toast.makeText(context, "API Credentials Saved!", Toast.LENGTH_SHORT).show()
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
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
