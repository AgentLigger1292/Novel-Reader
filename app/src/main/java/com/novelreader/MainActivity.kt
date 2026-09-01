package com.novelreader

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.novelreader.ui.AppNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // notification permission for download/tracker updates (API 33+)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as NovelApp
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppNav(app)
            }
        }
    }
}
