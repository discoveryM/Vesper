package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.UserProfile
import com.example.ui.auth.UserSessionStore
import com.example.ui.home.HomeScreen
import com.example.ui.theme.VesperTheme
import com.example.ui.theme.VesperTokens
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    setContent {
      VesperTheme {
        VesperAppRoot()
      }
    }
  }
}

@Composable
private fun VesperAppRoot() {
  val context = LocalContext.current
  val sessionStore = remember(context) { UserSessionStore(context.applicationContext) }
  val userProfile by
    sessionStore.userProfileFlow.collectAsStateWithLifecycle(initialValue = null)
  val coroutineScope = rememberCoroutineScope()
  val tokens = VesperTokens.colors

  Box(
    modifier = Modifier.fillMaxSize().background(tokens.canvasBackground),
  ) {
    AnimatedContent(
      targetState = userProfile,
      transitionSpec = {
        fadeIn(tween(320, easing = FastOutSlowInEasing))
          .togetherWith(fadeOut(tween(220, easing = FastOutSlowInEasing)))
      },
      label = "vesper_auth_navigation",
    ) { activeProfile: UserProfile? ->
      if (activeProfile == null) {
        LoginScreen(
          onSignInSuccess = {
            // Session saved to DataStore; userProfileFlow automatically switches to HomeScreen.
          },
        )
      } else {
        HomeScreen(
          userProfile = activeProfile,
          onSignOut = {
            coroutineScope.launch {
              sessionStore.clearSession()
            }
          },
        )
      }
    }
  }
}
