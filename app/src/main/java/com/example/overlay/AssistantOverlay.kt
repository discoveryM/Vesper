package com.example.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppVaultStore
import com.example.resolver.AppResolutionResult
import com.example.resolver.SmartAppResolver
import com.example.ui.theme.VesperTokens
import com.example.ui.theme.capsuleHairlineBorder
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val EXTRA_VOICE_COMMAND = "vesper_extra_voice_command"

private enum class OverlayPhase {
  LISTENING,
  NEEDS_MIC_PERMISSION,
  ERROR_RETRY,
  RESOLVING,
}

/**
 * Compact obsidian bottom-sheet overlay triggered by Power button or Volume hardware hold.
 * Enforces strict zero-idle microphone teardown immediately on final speech or dismissal.
 */
@Composable
fun AssistantOverlaySheet(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val tokens = VesperTokens.colors
  val coroutineScope = rememberCoroutineScope()
  val appVaultStore = remember(context) { AppVaultStore(context.applicationContext) }
  val resolver = remember(context, appVaultStore) {
    SmartAppResolver(context.applicationContext, appVaultStore)
  }

  var phase by remember { mutableStateOf(OverlayPhase.LISTENING) }
  var partialTranscript by remember { mutableStateOf("") }
  var statusNote by remember { mutableStateOf<String?>(null) }
  var rmsLevel by remember { mutableFloatStateOf(0.15f) }
  var recognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }
  var restartAttempt by remember { mutableStateOf(0) }

  fun releaseMicrophoneImmediately() {
    recognizerRef?.let { recognizer ->
      runCatching { recognizer.stopListening() }
      runCatching { recognizer.cancel() }
      runCatching { recognizer.destroy() }
    }
    recognizerRef = null
    rmsLevel = 0f
  }

  fun startCapture(preferOffline: Boolean) {
    releaseMicrophoneImmediately()
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      phase = OverlayPhase.ERROR_RETRY
      statusNote = "Speech recognition service unavailable on this device."
      return
    }

    phase = OverlayPhase.LISTENING
    statusNote = null
    partialTranscript = ""

    val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
    recognizerRef = recognizer

    recognizer.setRecognitionListener(
      object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
          // Normalize SpeechRecognizer dB range (-2..10+) into 0.1f..1.0f
          rmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0.12f, 1.0f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
          // Stop listening immediately when utterance ends
          runCatching { recognizer.stopListening() }
        }

        override fun onError(error: Int) {
          releaseMicrophoneImmediately()
          val offlineFault =
            preferOffline &&
              (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                error == SpeechRecognizer.ERROR_SERVER ||
                error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
          if (offlineFault) {
            // Graceful fallback to standard system SpeechRecognizer if offline pack is missing
            startCapture(preferOffline = false)
            return
          }
          phase = OverlayPhase.ERROR_RETRY
          statusNote =
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
              "Didn't catch that. Tap to retry."
            } else {
              "Voice capture paused ($error). Tap to retry."
            }
        }

        override fun onResults(results: Bundle?) {
          // Strict Privacy Rule: immediately release microphone before acting on transcript
          releaseMicrophoneImmediately()
          val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          val transcript = matches?.firstOrNull()?.trim().orEmpty()
          if (transcript.isBlank()) {
            phase = OverlayPhase.ERROR_RETRY
            statusNote = "No speech detected. Tap to retry."
            return
          }

          partialTranscript = transcript
          phase = OverlayPhase.RESOLVING

          coroutineScope.launch {
            val result = resolver.resolveAndExecute(transcript, attachment = null)
            when (result) {
              is AppResolutionResult.Launched -> {
                onDismiss()
              }
              is AppResolutionResult.ChooseApp,
              AppResolutionResult.NotAnAppIntent -> {
                openVesperChatWithCommand(context, transcript)
                onDismiss()
              }
            }
          }
        }

        override fun onPartialResults(partialResults: Bundle?) {
          val partial =
            partialResults
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
          if (!partial.isNullOrBlank()) {
            partialTranscript = partial
          }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
      },
    )

    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        if (preferOffline) {
          putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
      }

    runCatching { recognizer.startListening(intent) }
      .onFailure {
        releaseMicrophoneImmediately()
        if (preferOffline) {
          startCapture(preferOffline = false)
        } else {
          phase = OverlayPhase.ERROR_RETRY
          statusNote = "Unable to start microphone capture."
        }
      }
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        startCapture(preferOffline = true)
      } else {
        phase = OverlayPhase.NEEDS_MIC_PERMISSION
      }
    }

  LaunchedEffect(restartAttempt) {
    val hasMicPermission =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (hasMicPermission) {
      startCapture(preferOffline = true)
    } else {
      phase = OverlayPhase.NEEDS_MIC_PERMISSION
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  DisposableEffect(Unit) {
    onDispose { releaseMicrophoneImmediately() }
  }

  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(Color.Transparent)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
        ) {
          releaseMicrophoneImmediately()
          onDismiss()
        },
    contentAlignment = Alignment.BottomCenter,
  ) {
    Surface(
      color = tokens.elevatedSurface,
      shape = sheetShape,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      modifier =
        Modifier
          .fillMaxWidth()
          .widthIn(max = 560.dp)
          .capsuleHairlineBorder(shape = sheetShape, isFocused = phase == OverlayPhase.LISTENING)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) { /* Consume taps inside the overlay sheet */ }
          .testTag("assistant_overlay_sheet"),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
          Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
      ) {
        // Header Row: Minimal brand label + Close Button
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier =
                Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(
                    if (phase == OverlayPhase.LISTENING) tokens.accentSilverBlue
                    else tokens.statusGreen,
                  ),
            )
            Text(
              text = "VESPER • ZERO IDLE MIC",
              style = MaterialTheme.typography.labelSmall,
              color = tokens.textSecondary,
            )
          }

          IconButton(
            onClick = {
              releaseMicrophoneImmediately()
              onDismiss()
            },
            modifier = Modifier.size(48.dp).testTag("overlay_close_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = stringResource(R.string.cd_close_overlay),
              tint = tokens.textSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (phase) {
          OverlayPhase.LISTENING, OverlayPhase.RESOLVING -> {
            SilverBlueWaveform(
              rmsLevel = if (phase == OverlayPhase.LISTENING) rmsLevel else 0.1f,
              modifier = Modifier.fillMaxWidth().height(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text =
                when {
                  partialTranscript.isNotBlank() -> partialTranscript
                  phase == OverlayPhase.RESOLVING -> "Routing command…"
                  else -> stringResource(R.string.overlay_listening)
                },
              style = MaterialTheme.typography.titleMedium,
              color = tokens.textPrimary,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("overlay_prompt_text"),
            )
          }
          OverlayPhase.NEEDS_MIC_PERMISSION -> {
            Text(
              text = stringResource(R.string.overlay_permission_needed),
              style = MaterialTheme.typography.bodyMedium,
              color = tokens.textSecondary,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
              shape = CircleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = tokens.textPrimary,
                  contentColor = tokens.canvasBackground,
                ),
            ) {
              Text(
                text = stringResource(R.string.overlay_grant_mic),
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
          OverlayPhase.ERROR_RETRY -> {
            Text(
              text = statusNote ?: stringResource(R.string.overlay_retry),
              style = MaterialTheme.typography.bodyMedium,
              color = tokens.textSecondary,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(tokens.userBubble)
                  .capsuleHairlineBorder(CircleShape, isFocused = false)
                  .clickable(role = Role.Button) { restartAttempt++ }
                  .testTag("overlay_retry_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = stringResource(R.string.overlay_retry),
                tint = tokens.accentSilverBlue,
                modifier = Modifier.size(22.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}

@Composable
private fun SilverBlueWaveform(
  rmsLevel: Float,
  modifier: Modifier = Modifier,
) {
  val tokens = VesperTokens.colors
  var tick by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    while (true) {
      tick += 0.22f
      delay(32L)
    }
  }

  val animatedRms by
    animateFloatAsState(
      targetValue = rmsLevel.coerceIn(0.1f, 1.0f),
      animationSpec = tween(90, easing = FastOutSlowInEasing),
      label = "overlay_rms",
    )

  Canvas(modifier = modifier) {
    val barCount = 19
    val barWidth = 3.dp.toPx()
    val spacing = (size.width - barCount * barWidth) / (barCount - 1)
    val maxBarHeight = size.height * 0.86f
    val minBarHeight = 5.dp.toPx()
    val centerIndex = (barCount - 1) / 2f

    for (i in 0 until barCount) {
      val distanceFactor = 1f - (kotlin.math.abs(i - centerIndex) / (centerIndex + 1f)) * 0.65f
      val wave = 0.5f + 0.5f * sin(tick + i * 0.65f)
      val height =
        (minBarHeight + (maxBarHeight - minBarHeight) * animatedRms * wave * distanceFactor)
          .coerceIn(minBarHeight, maxBarHeight)
      val x = i * (barWidth + spacing)
      val y = (size.height - height) / 2f

      drawRoundRect(
        color = tokens.accentSilverBlue.copy(alpha = 0.45f + 0.55f * distanceFactor),
        topLeft = Offset(x, y),
        size = Size(barWidth, height),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
      )
    }
  }
}

private fun openVesperChatWithCommand(context: Context, transcript: String) {
  val intent =
    Intent(context, MainActivity::class.java).apply {
      putExtra(EXTRA_VOICE_COMMAND, transcript)
      addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_SINGLE_TOP or
          Intent.FLAG_ACTIVITY_CLEAR_TOP,
      )
    }
  context.startActivity(intent)
}
