package com.tgserver.streamer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.theme.*

/**
 * Button style variants reflecting Nothing OS & Apple aesthetics.
 */
enum class NothingButtonVariant {
    Primary,    // Pure White background, Deep Black text
    Secondary,  // Smoked Obsidian Glass background, Crisp Silver text, subtle border
    Accent,     // Tactile Yellow background (as seen in Nothing OS reference), Deep Black text
    Ghost       // Transparent background with subtle frosted border
}

/**
 * Tactile Pill Button with spring scale feedback and embedded NothingDotLoader.
 */
@Composable
fun NothingPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    variant: NothingButtonVariant = NothingButtonVariant.Primary,
    height: Dp = 54.dp,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pill_button_scale"
    )

    val containerColor = when (variant) {
        NothingButtonVariant.Primary -> if (enabled) Color(0xFFFFFFFF) else Color(0xFF323238)
        NothingButtonVariant.Secondary -> if (enabled) SurfaceCardElevated else Color(0xFF141418)
        NothingButtonVariant.Accent -> if (enabled) AccentYellow else Color(0xFF3D350A)
        NothingButtonVariant.Ghost -> Color.Transparent
    }

    val contentColor = when (variant) {
        NothingButtonVariant.Primary -> if (enabled) Color(0xFF070709) else Color(0xFF71717A)
        NothingButtonVariant.Secondary -> if (enabled) Color(0xFFFFFFFF) else Color(0xFF52525B)
        NothingButtonVariant.Accent -> if (enabled) Color(0xFF070709) else Color(0xFF71717A)
        NothingButtonVariant.Ghost -> if (enabled) Color(0xFFFFFFFF) else Color(0xFF52525B)
    }

    val borderColor = when (variant) {
        NothingButtonVariant.Secondary -> if (enabled) BorderSubtle else Color(0xFF1F1F24)
        NothingButtonVariant.Ghost -> if (enabled) BorderSilver else BorderSubtle
        else -> Color.Transparent
    }

    val effectivePadding = contentPadding ?: if (height <= 38.dp) {
        PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    } else {
        PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    }

    val effectiveFontSize = if (height <= 38.dp) 12.sp else 14.sp

    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .clip(shape)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, shape)
                } else Modifier
            )
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(effectivePadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            NothingDotLoader(
                dotColor = contentColor,
                dotSize = 6.dp,
                spacing = 5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.wrapContentSize()
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = text,
                    color = contentColor,
                    fontSize = effectiveFontSize,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    softWrap = false
                )

                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * Frosted Obsidian Squircle Card with 1dp subtle metallic border.
 */
@Composable
fun NothingCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = SurfaceCard,
    borderColor: Color = BorderSubtle,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(backgroundColor)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Nothing OS & Apple inspired Minimal Text Field with smooth focus animation.
 */
@Composable
fun NothingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    badgeText: String? = null,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    isMonospace: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isError -> StatusRed
            isFocused -> BorderBright
            else -> BorderSubtle
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "field_border_color"
    )

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isFocused) SurfaceCardActive else SurfaceCardElevated,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "field_container_color"
    )

    Column(modifier = modifier) {
        if (label != null || badgeText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (label != null) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(
                                    if (isFocused) AccentSilver else TextTertiary,
                                    CircleShape
                                )
                        )
                        Text(
                            text = label,
                            color = if (isFocused) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (badgeText != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    NothingDotBadge(
                        text = badgeText,
                        dotColor = if (isFocused) AccentSilver else TextTertiary
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, animatedBorderColor, RoundedCornerShape(18.dp))
                .background(animatedContainerColor)
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = TextTertiary,
                            fontSize = 15.sp,
                            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                            letterSpacing = (-0.2).sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = singleLine,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = visualTransformation,
                        cursorBrush = SolidColor(TextPrimary),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                            fontWeight = if (isMonospace) FontWeight.Normal else FontWeight.Medium,
                            letterSpacing = if (isMonospace) 0.5.sp else (-0.2).sp
                        )
                    )
                }

                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    trailingIcon()
                }
            }
        }

        AnimatedVisibility(
            visible = isError && !errorMessage.isNullOrBlank(),
            enter = fadeIn(tween(200)) + expandVertically(spring(stiffness = Spring.StiffnessLow)),
            exit = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(StatusRed, CircleShape)
                )
                Text(
                    text = errorMessage ?: "",
                    color = StatusRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Nothing OS Pill Badge with micro glowing dot and tracked monospace typography.
 */
@Composable
fun NothingDotBadge(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color = AccentSilver,
    backgroundColor: Color = Color(0x1AFFFFFF),
    textColor: Color = TextSecondary
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .border(0.5.dp, BorderSubtle, CircleShape)
            .background(backgroundColor)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.5.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = text,
            color = textColor,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Liquid Pulsing Dot-Wave Loader (Signature Nothing OS style).
 */
@Composable
fun NothingDotLoader(
    modifier: Modifier = Modifier,
    dotColor: Color = TextPrimary,
    dotSize: Dp = 6.dp,
    spacing: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_loader_infinite")

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1_scale"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2_scale"
    )

    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3_scale"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(dot1Scale)
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(dot2Scale)
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(dot3Scale)
                .background(dotColor, CircleShape)
        )
    }
}

/**
 * Subtle Dot Divider (Nothing OS signature pattern).
 */
@Composable
fun DotDivider(
    modifier: Modifier = Modifier,
    color: Color = BorderSubtle,
    dotsCount: Int = 24
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotsCount) {
            Box(
                modifier = Modifier
                    .size(2.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * Tablet & Foldable Adaptive Container:
 * Keeps layout cleanly centered with a max width constraint across large screens.
 */
@Composable
fun AdaptiveScreenContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 500.dp,
    applyInsets: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .then(
                if (applyInsets) {
                    Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                } else Modifier
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = maxWidth),
            content = content
        )
    }
}

/**
 * Sleek Pill Toggle Switch (Inspired by Uiverse.io SelfMadeSystem design).
 * Elongated aspect ratio (212.5 / 84.5 ~ 2.51), smooth 0.5s ease-out transitions,
 * outer halo border, and clean sliding white thumb with haptic feedback.
 */
@Composable
fun NothingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = StatusGreen,
    inactiveColor: Color = Color(0xFF262833)
) {
    val haptic = LocalHapticFeedback.current
    val trackHeight = 22.dp
    val trackWidth = 55.dp
    val thumbSize = 18.dp
    val padding = 2.dp
    val maxTravel = trackWidth - thumbSize - padding

    val interactionSource = remember { MutableInteractionSource() }

    // 0.5s ease-out transition matching --a: 0.5s ease-out
    val animSpecColor = tween<Color>(durationMillis = 500, easing = FastOutSlowInEasing)
    val animSpecDp = tween<Dp>(durationMillis = 500, easing = FastOutSlowInEasing)

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) maxTravel else padding,
        animationSpec = animSpecDp,
        label = "switch_thumb_offset"
    )

    val trackBgColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF16171E)
            checked -> activeColor
            else -> inactiveColor
        },
        animationSpec = animSpecColor,
        label = "switch_track_bg"
    )

    val haloBorderColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF22242D)
            checked -> activeColor.copy(alpha = 0.35f)
            else -> Color(0xFF383B4A)
        },
        animationSpec = animSpecColor,
        label = "switch_halo_border"
    )

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(CircleShape)
            .border(1.5.dp, haloBorderColor, CircleShape)
            .background(trackBgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Clean tactile white thumb (SelfMadeSystem slider)
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun PixelCyberSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = StatusGreen,
    inactiveColor: Color = Color(0xFF232530)
) {
    NothingSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor
    )
}

