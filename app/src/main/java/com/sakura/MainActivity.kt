package com.sakura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.sakura.ui.theme.SakuraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SakuraTheme {
                // Minimal shell — onboarding navigation added in Plan 03
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Sakura")
                }
            }
        }
    }
}
