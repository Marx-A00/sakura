package com.sakura.features.loading

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sakura.R

private val CardCream1 = Color(0xFFFFFAF3)
private val CardCream2 = Color(0xFFFFF7EE)

private const val STRIP_COUNT = 60
private const val STRIP_DELAY_MS = 40
private const val STRIP_FLY_DURATION_MS = 250
private const val HOLD_DURATION_MS = 1200
private const val TOTAL_CYCLE_MS =
    STRIP_COUNT * STRIP_DELAY_MS + STRIP_FLY_DURATION_MS + HOLD_DURATION_MS

/**
 * Sakura loading animation — horizontal card strips peel off
 * top-to-bottom to reveal a cherry blossom tree underneath.
 *
 * This is a self-contained composable. Delete the whole
 * `features/loading/` directory to remove it.
 */
@Composable
fun SakuraLoadingAnimation(
    modifier: Modifier = Modifier,
    treeWidth: Int = 200,
    treeHeight: Int = 200,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sakura-loading")

    val clock by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(TOTAL_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "clock",
    )

    val timeMs = clock * TOTAL_CYCLE_MS

    Box(
        modifier = modifier
            .size(treeWidth.dp, treeHeight.dp)
            .clip(RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Layer 1: The watercolor sakura tree
        Image(
            painter = painterResource(R.drawable.sakura_tree),
            contentDescription = "Sakura tree",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Layer 2: Horizontal strips covering the tree
        val stripHeight = treeHeight.toFloat() / STRIP_COUNT
        for (i in 0 until STRIP_COUNT) {
            val stripStartMs = i * STRIP_DELAY_MS
            val progress = ((timeMs - stripStartMs) / STRIP_FLY_DURATION_MS).coerceIn(0f, 1f)

            val eased = FastOutSlowInEasing.transform(progress)

            val direction = if (i % 2 == 0) 1f else -1f
            val translateX = eased * treeWidth * 1.5f * direction
            val rotation = eased * 25f * direction
            val alpha = 1f - eased

            if (alpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(stripHeight.dp)
                        .offset(y = (i * stripHeight).dp)
                        .graphicsLayer {
                            translationX = translateX * density
                            rotationZ = rotation
                            this.alpha = alpha
                        }
                        .background(
                            color = if (i % 2 == 0) CardCream1 else CardCream2,
                            shape = RoundedCornerShape(2.dp),
                        )
                        .border(
                            width = 0.5.dp,
                            color = Color(0x30000000),
                            shape = RoundedCornerShape(2.dp),
                        )
                )
            }
        }
    }
}
