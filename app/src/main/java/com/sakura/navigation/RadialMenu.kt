package com.sakura.navigation

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.R
import com.sakura.ui.theme.CherryBlossomPink
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class RadialAction {
    ADD_FOOD, ADD_EXERCISE, LOG_WEIGHT
}

private data class RadialOption(
    val action: RadialAction,
    val label: String,
    val icon: ImageVector,
    val angleDeg: Float // angle from 12 o'clock, clockwise positive
)

private val RADIAL_OPTIONS = listOf(
    RadialOption(RadialAction.ADD_FOOD, "Add Food", Icons.Filled.Restaurant, -40f),
    RadialOption(RadialAction.ADD_EXERCISE, "Add Exercise", Icons.Filled.FitnessCenter, 0f),
    RadialOption(RadialAction.LOG_WEIGHT, "Log Weight", Icons.Filled.MonitorWeight, 40f),
)

private val OptionBg = Color(0xFF2B2930)

/**
 * Renders [content] in a MATCH_PARENT ComposeView added directly to the Activity's
 * content view. Android equivalent of React's createPortal.
 */
@Composable
private fun FullScreenPortal(content: @Composable () -> Unit) {
    val parentComposition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val activity = LocalContext.current as Activity

    DisposableEffect(Unit) {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
        val overlayView = ComposeView(activity).apply {
            setParentCompositionContext(parentComposition)
            setContent { currentContent() }
        }
        contentView.addView(
            overlayView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        onDispose {
            contentView.removeView(overlayView)
        }
    }
}

/**
 * The raised center Home button with radial menu on long-press.
 *
 * Normal tap → [onHomeTap].
 * Long-press + drag → radial menu fans out, release triggers [onRadialAction].
 */
@Composable
fun CenterHomeButton(
    onHomeTap: () -> Unit,
    onRadialAction: (RadialAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    // Actual button center in window coordinates — used to position radial items
    var buttonCenterPx by remember { mutableStateOf(Offset.Zero) }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val minDragPx = with(density) { 40.dp.toPx() }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                buttonCenterPx = Offset(
                    pos.x + size.width / 2f,
                    pos.y + size.height / 2f
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onHomeTap() })
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        menuOpen = true
                        dragOffset = Offset.Zero
                        highlightedIndex = -1
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                        val dist = sqrt(
                            dragOffset.x * dragOffset.x + dragOffset.y * dragOffset.y
                        )
                        highlightedIndex = if (dist < minDragPx) {
                            -1
                        } else {
                            findClosestOption(dragOffset)
                        }
                    },
                    onDragEnd = {
                        if (highlightedIndex in RADIAL_OPTIONS.indices) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRadialAction(RADIAL_OPTIONS[highlightedIndex].action)
                        }
                        menuOpen = false
                        dragOffset = Offset.Zero
                        highlightedIndex = -1
                    },
                    onDragCancel = {
                        menuOpen = false
                        dragOffset = Offset.Zero
                        highlightedIndex = -1
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sakura_branch),
            contentDescription = "Home",
            tint = CherryBlossomPink,
            modifier = Modifier
                .offset(y = 20.dp)
                .size(width = 48.dp, height = 150.dp)
        )
    }

    // Full-screen overlay via portal — items positioned using actual button coordinates
    if (menuOpen) {
        FullScreenPortal {
            RadialMenuOverlay(
                anchorCenterPx = buttonCenterPx,
                highlightedIndex = highlightedIndex,
                onDismiss = {
                    menuOpen = false
                    dragOffset = Offset.Zero
                    highlightedIndex = -1
                }
            )
        }
    }
}

/**
 * Full-screen overlay showing the radial option bubbles.
 * Items are positioned using absolute window coordinates from [anchorCenterPx].
 */
@Composable
private fun RadialMenuOverlay(
    anchorCenterPx: Offset,
    highlightedIndex: Int,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { 100.dp.toPx() }
    val optionSizePx = with(density) { 48.dp.toPx() }
    val optionSizeDp = 48.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                indication = null,
                interactionSource = null
            ) { onDismiss() }
    ) {
        RADIAL_OPTIONS.forEachIndexed { index, option ->
            val angleRad = option.angleDeg * PI.toFloat() / 180f
            // Position each item at absolute coordinates relative to the anchor
            val itemCenterX = anchorCenterPx.x + radiusPx * sin(angleRad)
            val itemCenterY = anchorCenterPx.y - radiusPx * cos(angleRad)

            val isHighlighted = index == highlightedIndex

            val scale = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }

            Column(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (itemCenterX - optionSizePx / 2).roundToInt(),
                            (itemCenterY - optionSizePx / 2).roundToInt()
                        )
                    }
                    .size(optionSizeDp)
                    .wrapContentSize(unbounded = true, align = Alignment.TopCenter)
                    .scale(scale.value * if (isHighlighted) 1.15f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(optionSizeDp)
                        .shadow(
                            elevation = if (isHighlighted) 12.dp else 4.dp,
                            shape = CircleShape,
                            ambientColor = CherryBlossomPink.copy(alpha = 0.3f),
                            spotColor = CherryBlossomPink.copy(alpha = 0.3f)
                        )
                        .background(OptionBg, CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isHighlighted) {
                                    listOf(
                                        CherryBlossomPink.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                } else {
                                    listOf(Color.Transparent, Color.Transparent)
                                }
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.label,
                        tint = if (isHighlighted) CherryBlossomPink else CherryBlossomPink.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = option.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isHighlighted) Color.White else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.offset(y = 4.dp)
                )
            }
        }
    }
}

/**
 * Find the closest radial option index based on drag offset from center.
 * Uses angle from 12 o'clock (straight up), clockwise positive.
 */
private fun findClosestOption(dragOffset: Offset): Int {
    val dragAngleRad = atan2(dragOffset.x, -dragOffset.y)
    val dragAngleDeg = dragAngleRad * 180f / PI.toFloat()

    var closestIndex = -1
    var closestDist = Float.MAX_VALUE
    RADIAL_OPTIONS.forEachIndexed { index, option ->
        var diff = dragAngleDeg - option.angleDeg
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        val absDiff = kotlin.math.abs(diff)
        if (absDiff < closestDist) {
            closestDist = absDiff
            closestIndex = index
        }
    }
    return closestIndex
}
