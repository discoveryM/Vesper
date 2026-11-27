package com.example.overlay

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.theme.VesperTheme

/**
 * Translucent single-instance bottom-sheet host launched over any active app when triggered
 * by VesperAccessibilityService (Volume Down 750ms hold or Volume Up+Down 3s hold).
 */
class AssistantOverlayActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    setContent {
      VesperTheme {
        AssistantOverlaySheet(
          onDismiss = {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
          },
        )
      }
    }
  }
}
