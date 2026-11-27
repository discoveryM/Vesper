package com.example.hardware

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.automation.VesperAutomationEngine
import com.example.overlay.AssistantOverlayActivity
import com.example.overlay.AssistantOverlaySheet
import com.example.ui.theme.VesperTheme

/**
 * 1. Power Button Long-Press Digital Assistant API (VoiceInteractionService).
 */
class VesperVoiceInteractionService : VoiceInteractionService()

class VesperVoiceInteractionSessionService : VoiceInteractionSessionService() {
  override fun onNewSession(args: Bundle?): VoiceInteractionSession {
    return VesperVoiceInteractionSession(this)
  }
}

class VesperRecognitionService : RecognitionService() {
  override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) = Unit
  override fun onCancel(listener: Callback?) = Unit
  override fun onStopListening(listener: Callback?) = Unit
}

class VesperVoiceInteractionSession(context: Context) :
  VoiceInteractionSession(context),
  LifecycleOwner,
  ViewModelStoreOwner,
  SavedStateRegistryOwner {

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val savedStateController = SavedStateRegistryController.create(this)
  private val store = ViewModelStore()

  override val lifecycle: Lifecycle get() = lifecycleRegistry
  override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
  override val viewModelStore: ViewModelStore get() = store

  override fun onCreate() {
    super.onCreate()
    savedStateController.performRestore(null)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
  }

  override fun onCreateContentView(): View {
    val composeView =
      ComposeView(context).apply {
        setViewTreeLifecycleOwner(this@VesperVoiceInteractionSession)
        setViewTreeSavedStateRegistryOwner(this@VesperVoiceInteractionSession)
        setViewTreeViewModelStoreOwner(this@VesperVoiceInteractionSession)
        setContent {
          VesperTheme {
            AssistantOverlaySheet(onDismiss = { finish() })
          }
        }
      }
    window?.window?.decorView?.let { decor ->
      decor.setViewTreeLifecycleOwner(this)
      decor.setViewTreeSavedStateRegistryOwner(this)
      decor.setViewTreeViewModelStoreOwner(this)
    }
    return composeView
  }

  override fun onShow(args: Bundle?, showFlags: Int) {
    super.onShow(args, showFlags)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  override fun onHide() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    super.onHide()
  }

  override fun onDestroy() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    store.clear()
    super.onDestroy()
  }
}

/**
 * 2. Accessibility Key Interceptor & Multi-Step UI Automation Service (VesperHardwareService):
 *    - Triggers Vesper's floating overlay on a 750ms Volume Down hold or 3s Volume Up+Down hold,
 *      passing normal short Volume taps through to media volume unchanged.
 *    - Connects to VesperAutomationEngine to read AccessibilityNodeInfo and execute multi-step workflows.
 */
class VesperHardwareService : AccessibilityService() {

  private val handler = Handler(Looper.getMainLooper())
  private var volDownPressed = false
  private var volUpPressed = false
  private var volDownLongPressFired = false
  private var comboTriggered = false

  private val volDownLongPressRunnable = Runnable {
    if (volDownPressed && !volUpPressed && !comboTriggered) {
      volDownLongPressFired = true
      triggerHapticPulse()
      launchFloatingOverlay()
    }
  }

  private val comboLongPressRunnable = Runnable {
    if (volDownPressed && volUpPressed) {
      comboTriggered = true
      volDownLongPressFired = true
      triggerHapticPulse()
      launchFloatingOverlay()
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    VesperAutomationEngine.attachService(this)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

  override fun onInterrupt() {
    resetTimers()
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    val code = event.keyCode
    if (code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_UP) {
      return false
    }

    when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        if (event.repeatCount > 0) {
          return code == KeyEvent.KEYCODE_VOLUME_DOWN || (volDownPressed && volUpPressed)
        }

        if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
          volDownPressed = true
          volDownLongPressFired = false
          handler.removeCallbacks(volDownLongPressRunnable)
          handler.postDelayed(volDownLongPressRunnable, VOLUME_DOWN_HOLD_MS)

          if (volUpPressed) {
            handler.removeCallbacks(volDownLongPressRunnable)
            handler.removeCallbacks(comboLongPressRunnable)
            handler.postDelayed(comboLongPressRunnable, VOLUME_COMBO_HOLD_MS)
          }
          return true
        } else {
          volUpPressed = true
          if (volDownPressed) {
            comboTriggered = false
            handler.removeCallbacks(volDownLongPressRunnable)
            handler.removeCallbacks(comboLongPressRunnable)
            handler.postDelayed(comboLongPressRunnable, VOLUME_COMBO_HOLD_MS)
            return true
          }
          return false
        }
      }

      KeyEvent.ACTION_UP -> {
        if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
          handler.removeCallbacks(volDownLongPressRunnable)
          handler.removeCallbacks(comboLongPressRunnable)
          val wasHandled = volDownLongPressFired || comboTriggered
          volDownPressed = false
          if (!volUpPressed) comboTriggered = false

          if (!wasHandled) {
            adjustMusicVolume(AudioManager.ADJUST_LOWER)
          }
          return true
        } else {
          handler.removeCallbacks(comboLongPressRunnable)
          val wasCombo = comboTriggered
          volUpPressed = false
          if (!volDownPressed) comboTriggered = false
          return wasCombo
        }
      }
    }
    return false
  }

  private fun adjustMusicVolume(direction: Int) {
    val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
  }

  private fun triggerHapticPulse() {
    runCatching {
      val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
          vm?.defaultVibrator
        } else {
          @Suppress("DEPRECATION")
          getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(45L)
      }
    }
  }

  private fun launchFloatingOverlay() {
    val intent =
      Intent(this, AssistantOverlayActivity::class.java).apply {
        addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_NO_ANIMATION,
        )
      }
    startActivity(intent)
  }

  private fun resetTimers() {
    handler.removeCallbacks(volDownLongPressRunnable)
    handler.removeCallbacks(comboLongPressRunnable)
    volDownPressed = false
    volUpPressed = false
    volDownLongPressFired = false
    comboTriggered = false
  }

  override fun onDestroy() {
    resetTimers()
    VesperAutomationEngine.detachService(this)
    super.onDestroy()
  }

  private companion object {
    const val VOLUME_DOWN_HOLD_MS = 750L
    const val VOLUME_COMBO_HOLD_MS = 3000L
  }
}

fun isVesperAccessibilityEnabled(context: Context): Boolean {
  val expected = ComponentName(context, VesperHardwareService::class.java).flattenToString()
  val enabledServices =
    Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
  return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}
