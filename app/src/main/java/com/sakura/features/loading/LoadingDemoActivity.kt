package com.sakura.features.loading

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standalone activity to preview the loading animation.
 * Launch it directly from the manifest to test, then delete
 * the whole `features/loading/` directory when done.
 */
class LoadingDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LoadingDemoScreen()
            }
        }
    }
}

@Composable
private fun LoadingDemoScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SakuraLoadingAnimation(
            treeWidth = 250,
            treeHeight = 250,
        )

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedLoadingText()
    }
}

@Composable
private fun AnimatedLoadingText() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dot-count",
    )

    val dots = ".".repeat(dotCount.toInt())
    Text(
        text = "Loading$dots",
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )
}
