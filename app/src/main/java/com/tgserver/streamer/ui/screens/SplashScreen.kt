package com.tgserver.streamer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val NdotFont = FontFamily(Font(R.font.ndot))

/**
 * Premium OLED Splash Screen with live moving white-silver ambient gradient,
 * high-res transparent NoxMedia PNG logo, Nothing OS dot-matrix branding,
 * custom Uiverse Pinwheel loading spinner, and smooth cinematic dissolve outro.
 */
@Composable
fun SplashScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    splashDurationMillis: Long = 1900L
) {
    val coroutineScope = rememberCoroutineScope()

    // Outro animation states
    val outroAlpha = remember { Animatable(1f) }
    val outroScale = remember { Animatable(1f) }

    // Entrance animation states
    val entranceAlpha = remember { Animatable(0f) }
    val entranceScale = remember { Animatable(0.86f) }

    // Infinite transition for live background gradient & breathing logo
    val infiniteTransition = rememberInfiniteTransition(label = "splash_ambient_transition")

    // Slow ambient pulsation phase (0 .. 2pi)
    val ambientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_phase"
    )

    // Secondary diagonal sheen sweep progress (0 .. 1)
    val sheenProgress by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sheen_progress"
    )

    // Gentle logo breathing scale
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_breathing"
    )

    // Launch entrance and schedule outro
    LaunchedEffect(Unit) {
        // Entrance animation
        launch {
            entranceAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        launch {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 550,
                    easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                )
            )
        }

        // Wait for splash duration
        delay(splashDurationMillis)

        // Smooth cinematic outro animation (scale up + fade out)
        launch {
            outroScale.animateTo(
                targetValue = 1.08f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
                )
            )
        }
        outroAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 420,
                easing = FastOutSlowInEasing
            )
        )

        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(outroAlpha.value)
            .scale(outroScale.value),
        contentAlignment = Alignment.Center
    ) {
        // 1. LIVE ANIMATED BACKGROUND (Moving White-Silver Ambient Gradient)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Calculate moving glow center using smooth sinusoidal oscillation
            val glowCenterX = w * (0.5f + 0.08f * sin(ambientPhase))
            val glowCenterY = h * (0.42f + 0.05f * cos(ambientPhase))
            val pulseRadius = w * (0.65f + 0.10f * sin(ambientPhase * 0.8f))

            // Layer A: Soft Silver-White Radial Ambient Glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x1CE5E7EB), // Silver luminous center
                        Color(0x0BD1D5DB), // Subtle mid aura
                        Color(0x039CA3AF), // Tapering edge
                        Color.Transparent
                    ),
                    center = Offset(glowCenterX, glowCenterY),
                    radius = pulseRadius
                )
            )

            // Layer B: Ambient Sweeping Silver Light Sheen
            val sheenOffset = sheenProgress * (w + h)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x06FFFFFF),
                        Color(0x14E5E7EB), // Soft silver metallic streak
                        Color(0x06FFFFFF),
                        Color.Transparent
                    ),
                    start = Offset(sheenOffset - w * 0.5f, 0f),
                    end = Offset(sheenOffset + w * 0.5f, h)
                )
            )
        }

        // 2. MAIN CENTER CONTENT (Logo + NoxMedia Branding)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(entranceAlpha.value)
                .scale(entranceScale.value * breathingScale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // High-Resolution Transparent PNG Logo
            Image(
                painter = painterResource(id = R.drawable.noxmedia_icon_trans),
                contentDescription = "NoxMedia Logo",
                modifier = Modifier
                    .size(108.dp)
                    .padding(4.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // NoxMedia Title in Nothing OS Dot-Matrix Typography
            Text(
                text = "NoxMedia",
                color = Color.White,
                fontSize = 30.sp,
                fontFamily = NdotFont,
                letterSpacing = 2.sp
            )
        }

        // 3. BOTTOM LOADING SPINNER & INITIALIZING STATUS
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 54.dp)
                .alpha(entranceAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Custom Uiverse Pinwheel Spinner adapted to Silver-White OLED aesthetic
            PinwheelSpinner(
                size = 40.dp,
                lineWeight = 3.5.dp,
                color = Color(0xFFE5E7EB),
                speedMillis = 850
            )

            Text(
                text = "INITIALIZING",
                color = Color(0xFF6B7280),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * Precise Compose port of Uiverse Pinwheel spinner by dovatgabriel.
 * Features 6 rotating rounded lines fanning out with ease-in-out easing
 * and staggered phase offsets, rendered on a high-performance hardware-accelerated Canvas.
 */
@Composable
fun PinwheelSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    lineWeight: Dp = 3.5.dp,
    color: Color = Color(0xFFE5E7EB),
    speedMillis: Int = 850
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pinwheel_anim")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(speedMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pinwheel_progress"
    )

    // Staggered opacities and delays from the Uiverse CSS specification
    val opacities = remember { floatArrayOf(1.0f, 0.80f, 0.60f, 0.40f, 0.22f, 0.12f) }
    val delays = remember { floatArrayOf(0f, 0.075f, 0.150f, 0.225f, 0.300f, 0.375f) }

    // CSS ease-in-out equivalent cubic bezier curve
    val easeInOutCurve = remember { CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f) }

    Canvas(modifier = modifier.size(size)) {
        val strokePx = lineWeight.toPx()
        val diameterPx = this.size.width
        val radiusPx = diameterPx / 2f

        for (i in 0 until 6) {
            // Calculate line phase with delay
            var p = progress - delays[i]
            while (p < 0f) p += 1f
            p %= 1f

            val eased = easeInOutCurve.transform(p)
            val angle = eased * 180f

            rotate(degrees = angle, pivot = center) {
                drawLine(
                    color = color.copy(alpha = opacities[i]),
                    start = Offset(x = strokePx / 2f, y = radiusPx),
                    end = Offset(x = diameterPx - strokePx / 2f, y = radiusPx),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
