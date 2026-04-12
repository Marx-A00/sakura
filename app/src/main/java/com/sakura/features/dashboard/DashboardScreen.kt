package com.sakura.features.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sakura.ui.theme.CherryBlossomPink

/**
 * Home/Dashboard screen — stub for Task 2 compilation.
 * Full implementation in Task 3.
 */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Home")
    }
}
