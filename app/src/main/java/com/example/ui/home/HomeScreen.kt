package com.example.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.ai.FreeAiModel
import com.example.automation.AutomationProgressState
import com.example.automation.VesperAutomationEngine
import com.example.hardware.isVesperAccessibilityEnabled
import com.example.resolver.AttachmentFile
import com.example.resolver.InstalledAppInfo
import com.example.ui.auth.UserProfile
import com.example.ui.theme.VesperTokens
import com.example.ui.theme.capsuleHairlineBorder
import com.example.ui.theme.hairlineBorder
import com.example.vault.LocalPasswordVault
import com.example.vault.SaveOnceCredentialSheet
import com.example.vault.VaultHubBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
  userProfile: UserProfile,
  incomingVoiceCommand: String?,
  onConsumeVoiceCommand: () -> Unit,
  onSignOut: () -> Unit,
  viewModel: HomeViewModel = viewModel(),
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val tokens = VesperTokens.colors
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val coroutineScope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val clipboardManager = LocalClipboardManager.current

  var showPrivacyCard by remember { mutableStateOf(false) }
  var showAttachmentSheet by remember { mutableStateOf(false) }
  var showVaultAppsSheet by remember { mutableStateOf(false) }
  var showFreeKeysSheet by remember { mutableStateOf(false) }
  var inlineRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

  // Forward voice command transcribed by floating overlay
  LaunchedEffect(incomingVoiceCommand) {
    if (!incomingVoiceCommand.isNullOrBlank()) {
      viewModel.handleIncomingVoiceCommand(incomingVoiceCommand)
      onConsumeVoiceCommand()
    }
  }

  // Trigger BiometricPrompt when a saved-login automation requires verification
  val activity = context as? FragmentActivity
  LaunchedEffect(uiState.pendingBiometricWorkflow) {
    val pending = uiState.pendingBiometricWorkflow ?: return@LaunchedEffect
    if (activity == null) {
      viewModel.onBiometricWorkflowApproved()
      return@LaunchedEffect
    }
    LocalPasswordVault.authenticateWithBiometrics(
      activity = activity,
      title = "Verify to Auto-Fill ${pending.workflow.targetApp.label}",
      subtitle = "Use fingerprint, face, or device PIN for ${pending.credentialMeta.username}",
      onSuccess = { viewModel.onBiometricWorkflowApproved() },
      onError = { viewModel.dismissBiometricWorkflow() },
    )
  }

  // Real Attachment Launchers (Camera, Gallery, Document)
  val cameraLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
      if (bitmap != null) viewModel.onCameraBitmapCaptured(bitmap)
    }
  val galleryLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) viewModel.onUriPicked(uri, "image/*")
    }
  val documentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) viewModel.onUriPicked(uri, "*/*")
    }

  fun releaseInlineMic() {
    inlineRecognizer?.let { r ->
      runCatching { r.stopListening() }
      runCatching { r.cancel() }
      runCatching { r.destroy() }
    }
    inlineRecognizer = null
    viewModel.setListening(false)
  }

  fun startInlineMicCapture() {
    releaseInlineMic()
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
    inlineRecognizer = recognizer
    viewModel.setListening(true)
    recognizer.setRecognitionListener(
      object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
          runCatching { recognizer.stopListening() }
        }
        override fun onError(error: Int) {
          releaseInlineMic()
        }
        override fun onResults(results: Bundle?) {
          val text =
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
          releaseInlineMic()
          if (!text.isNullOrEmpty()) {
            viewModel.onInputChanged(text)
          }
        }
        override fun onPartialResults(partialResults: Bundle?) {
          val partial =
            partialResults
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
          if (!partial.isNullOrEmpty()) viewModel.onInputChanged(partial)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
      },
    )
    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
      }
    runCatching { recognizer.startListening(intent) }.onFailure { releaseInlineMic() }
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) startInlineMicCapture()
    }

  DisposableEffect(Unit) {
    onDispose { releaseInlineMic() }
  }

  BackHandler(enabled = drawerState.isOpen) {
    coroutineScope.launch { drawerState.close() }
  }

  LaunchedEffect(showPrivacyCard) {
    if (showPrivacyCard) {
      delay(3000L)
      showPrivacyCard = false
    }
  }

  LaunchedEffect(uiState.messages.size, uiState.isGenerating) {
    val target = (uiState.messages.size + if (uiState.isGenerating) 1 else 0) - 1
    if (target >= 0) listState.animateScrollToItem(target)
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    scrimColor = tokens.canvasBackground.copy(alpha = 0.65f),
    drawerContent = {
      VesperDrawerContent(
        threads = uiState.threads,
        activeThreadId = uiState.activeThreadId,
        isAccessibilityTriggerActive = isVesperAccessibilityEnabled(context),
        hasAnyAiKey = uiState.freeAiKeys.hasAnyKey,
        onNewChat = {
          viewModel.startNewChat()
          coroutineScope.launch { drawerState.close() }
        },
        onSelectThread = { thread ->
          viewModel.selectThread(thread)
          coroutineScope.launch { drawerState.close() }
        },
        onOpenPowerSettings = {
          runCatching {
            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
          }
        },
        onOpenAccessibilitySettings = {
          runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
          }
        },
        onOpenVaultApps = {
          coroutineScope.launch { drawerState.close() }
          showVaultAppsSheet = true
        },
        onOpenFreeAiKeys = {
          coroutineScope.launch { drawerState.close() }
          showFreeKeysSheet = true
        },
        onSignOut = {
          coroutineScope.launch {
            drawerState.close()
            onSignOut()
          }
        },
      )
    },
    modifier = modifier.fillMaxSize(),
  ) {
    Scaffold(
      containerColor = tokens.canvasBackground,
      contentColor = tokens.textPrimary,
      topBar = {
        VesperTopBar(
          userProfile = userProfile,
          selectedModel = uiState.selectedModel,
          onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
          onSelectModel = viewModel::selectModel,
          onPrivacyClick = { showPrivacyCard = !showPrivacyCard },
          onSignOut = onSignOut,
        )
      },
    ) { innerPadding ->
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(tokens.canvasBackground)
            .padding(top = innerPadding.calculateTopPadding()),
      ) {
        AnimatedContent(
          targetState = uiState.messages.isNotEmpty() || uiState.isGenerating,
          transitionSpec = {
            (fadeIn(tween(240)) + slideInVertically(initialOffsetY = { it / 16 }))
              .togetherWith(fadeOut(tween(160)))
          },
          label = "vesper_stage_transition",
          modifier = Modifier.fillMaxSize(),
        ) { hasConversation ->
          if (!hasConversation) {
            CenteredGreetingEmptyState(
              userFirstName = userProfile.firstName,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
              LazyColumn(
                state = listState,
                contentPadding =
                  PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize().widthIn(max = 680.dp).testTag("chat_feed"),
              ) {
                items(uiState.messages, key = { it.id }) { msg ->
                  if (msg.isUser) {
                    UserPillBubble(msg)
                  } else {
                    AssistantResponseBlock(
                      message = msg,
                      isCopied = uiState.copiedMessageId == msg.id,
                      isSpeaking = uiState.speakingMessageId == msg.id,
                      onSelectAppFromChooser = { app ->
                        viewModel.onAppChosenFromCard(msg.id, app)
                      },
                      onCopy = {
                        clipboardManager.setText(AnnotatedString(msg.text))
                        viewModel.markMessageCopied(msg.id)
                      },
                      onSpeak = { viewModel.toggleSpeakMessage(msg.id) },
                    )
                  }
                }
                if (uiState.isGenerating) {
                  item(key = "synthesizing_indicator") {
                    LiveSynthesisIndicator(
                      statusLine = uiState.liveSynthesisStatus ?: stringResource(R.string.tag_processing),
                    )
                  }
                }
              }
            }
          }
        }

        PrivacyFloatingBanner(
          visible = showPrivacyCard,
          onDismiss = { showPrivacyCard = false },
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )

        // Phase 5: Minimalist Live Automation Progress Pill
        AutomationProgressPill(
          progress = uiState.automationProgress,
          onDismiss = { VesperAutomationEngine.dismissProgress() },
          modifier =
            Modifier
              .align(Alignment.TopCenter)
              .padding(top = if (showPrivacyCard) 54.dp else 8.dp),
        )

        Box(
          contentAlignment = Alignment.BottomCenter,
          modifier =
            Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .background(
                Brush.verticalGradient(
                  listOf(
                    Color.Transparent,
                    tokens.canvasBackground.copy(alpha = 0.9f),
                    tokens.canvasBackground,
                  ),
                ),
              ),
        ) {
          FloatingInputCapsule(
            inputText = uiState.inputText,
            pendingAttachment = uiState.pendingAttachment,
            isListening = uiState.isListening,
            onInputChanged = viewModel::onInputChanged,
            onOpenAttachments = { showAttachmentSheet = true },
            onClearAttachment = viewModel::clearAttachment,
            onToggleListening = {
              if (uiState.isListening) {
                releaseInlineMic()
              } else {
                val granted =
                  ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) startInlineMicCapture()
                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            onSend = { viewModel.submitCommand() },
          )
        }
      }
    }
  }

  if (showAttachmentSheet) {
    AttachmentPickerSheet(
      onDismiss = { showAttachmentSheet = false },
      onCameraClick = { cameraLauncher.launch() },
      onGalleryClick = { galleryLauncher.launch("image/*") },
      onDocumentClick = { documentLauncher.launch("*/*") },
    )
  }

  if (showVaultAppsSheet) {
    VaultHubBottomSheet(
      apps = uiState.installedApps,
      disabledPackages = uiState.disabledPackages,
      isLoadingApps = uiState.isLoadingApps,
      credentials = uiState.vaultCredentials,
      biometricLockEnabled = uiState.biometricLockEnabled,
      onLoadApps = viewModel::loadInstalledAppsForVault,
      onToggleApp = viewModel::toggleAppVaultPermission,
      onSaveCredential = viewModel::saveVaultCredential,
      onDeleteCredential = viewModel::deleteVaultCredential,
      onReadPasswordTemp = viewModel::readVaultPasswordTemp,
      onToggleBiometricLock = viewModel::toggleBiometricLock,
      onDismiss = { showVaultAppsSheet = false },
    )
  }

  uiState.pendingSaveOnceWorkflow?.let { pendingWorkflow ->
    SaveOnceCredentialSheet(
      appLabel = pendingWorkflow.targetApp.label,
      initialUsername = pendingWorkflow.accountIdentifier.orEmpty(),
      onSaveAndContinue = viewModel::onSaveOnceConfirmed,
      onDismiss = viewModel::dismissSaveOnce,
    )
  }

  if (showFreeKeysSheet) {
    FreeAiKeysSheet(
      currentKeys = uiState.freeAiKeys,
      onSave = viewModel::saveFreeAiKeys,
      onClear = viewModel::clearFreeAiKeys,
      onDismiss = { showFreeKeysSheet = false },
    )
  }
}

@Composable
private fun VesperTopBar(
  userProfile: UserProfile,
  selectedModel: FreeAiModel,
  onOpenDrawer: () -> Unit,
  onSelectModel: (FreeAiModel) -> Unit,
  onPrivacyClick: () -> Unit,
  onSignOut: () -> Unit,
) {
  val tokens = VesperTokens.colors
  var modelMenuExpanded by remember { mutableStateOf(false) }
  var avatarMenuExpanded by remember { mutableStateOf(false) }

  Box(
    modifier =
      Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 12.dp),
  ) {
    IconButton(
      onClick = onOpenDrawer,
      modifier = Modifier.align(Alignment.CenterStart).size(48.dp).testTag("menu_button"),
    ) {
      Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.75.dp.toPx()
        drawLine(
          color = tokens.textPrimary,
          start = Offset(0f, size.height * 0.34f),
          end = Offset(size.width, size.height * 0.34f),
          strokeWidth = stroke,
          cap = StrokeCap.Round,
        )
        drawLine(
          color = tokens.textPrimary,
          start = Offset(0f, size.height * 0.66f),
          end = Offset(size.width * 0.72f, size.height * 0.66f),
          strokeWidth = stroke,
          cap = StrokeCap.Round,
        )
      }
    }

    // Center: Free Multi-LLM Selector Pill (defaults to "Gemini 2.5 Flash ▾")
    Box(modifier = Modifier.align(Alignment.Center)) {
      val isBeast = selectedModel == FreeAiModel.BEAST_MODE
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
          Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(tokens.elevatedSurface)
            .hairlineBorder(CircleShape, if (isBeast) tokens.focusedBorder else tokens.hairlineBorder)
            .clickable(role = Role.Button) { modelMenuExpanded = true }
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("model_selector_pill"),
      ) {
        Box(
          modifier =
            Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(if (isBeast) tokens.accentSilverBlue else tokens.statusGreen),
        )
        Text(
          text = selectedModel.pillLabel,
          style = MaterialTheme.typography.labelLarge,
          color = tokens.textPrimary,
          maxLines = 1,
        )
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowDown,
          contentDescription = stringResource(R.string.cd_select_ai_model),
          tint = tokens.textSecondary,
          modifier = Modifier.size(16.dp),
        )
      }

      DropdownMenu(
        expanded = modelMenuExpanded,
        onDismissRequest = { modelMenuExpanded = false },
        offset = DpOffset((-64).dp, 8.dp),
        containerColor = tokens.elevatedSurface,
        shape = RoundedCornerShape(18.dp),
        modifier =
          Modifier
            .width(304.dp)
            .hairlineBorder(RoundedCornerShape(18.dp), tokens.capsuleBorder),
      ) {
        FreeAiModel.entries.forEach { model ->
          val active = model == selectedModel
          DropdownMenuItem(
            onClick = {
              onSelectModel(model)
              modelMenuExpanded = false
            },
            modifier = Modifier.testTag("model_option_${model.name.lowercase()}"),
            text = {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = model.displayName,
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (active) tokens.accentSilverBlue else tokens.textPrimary,
                )
                Text(
                  text = model.subtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.textSecondary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            },
            trailingIcon = {
              if (active) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = null,
                  tint = tokens.accentSilverBlue,
                  modifier = Modifier.size(16.dp),
                )
              }
            },
          )
        }
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.align(Alignment.CenterEnd),
    ) {
      IconButton(
        onClick = onPrivacyClick,
        modifier = Modifier.size(48.dp).testTag("privacy_shield_button"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Shield,
          contentDescription = stringResource(R.string.cd_privacy_sandbox_status),
          tint = tokens.textSecondary,
          modifier = Modifier.size(20.dp),
        )
      }

      Box {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier
              .size(48.dp)
              .clip(CircleShape)
              .clickable(role = Role.Button) { avatarMenuExpanded = true }
              .testTag("user_avatar_button"),
        ) {
          UserProfileAvatar(userProfile.firstName, userProfile.profilePictureUri)
        }

        DropdownMenu(
          expanded = avatarMenuExpanded,
          onDismissRequest = { avatarMenuExpanded = false },
          containerColor = tokens.elevatedSurface,
          shape = RoundedCornerShape(16.dp),
          modifier =
            Modifier
              .width(210.dp)
              .hairlineBorder(RoundedCornerShape(16.dp), tokens.capsuleBorder),
        ) {
          DropdownMenuItem(
            onClick = {},
            enabled = false,
            text = {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = userProfile.firstName,
                  style = MaterialTheme.typography.bodyMedium,
                  color = tokens.textPrimary,
                )
                Text(
                  text = userProfile.email ?: "Guest Session",
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            },
          )
          HorizontalDivider(color = tokens.hairlineBorder)
          DropdownMenuItem(
            onClick = {
              avatarMenuExpanded = false
              onSignOut()
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.Logout,
                contentDescription = null,
                tint = tokens.textSecondary,
                modifier = Modifier.size(18.dp),
              )
            },
            text = {
              Text(
                text = stringResource(R.string.drawer_sign_out),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
              )
            },
          )
        }
      }
    }
  }
}

@Composable
private fun UserProfileAvatar(firstName: String, photoUri: String?) {
  val tokens = VesperTokens.colors
  val initial = firstName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "V"
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .size(32.dp)
        .clip(CircleShape)
        .background(tokens.userBubble)
        .hairlineBorder(CircleShape, tokens.capsuleBorder),
  ) {
    if (!photoUri.isNullOrBlank()) {
      SubcomposeAsyncImage(
        model = photoUri,
        contentDescription = stringResource(R.string.cd_user_avatar),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(CircleShape),
        loading = {
          Text(text = initial, style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary)
        },
        error = {
          Text(text = initial, style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary)
        },
      )
    } else {
      Text(text = initial, style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary)
    }
  }
}

@Composable
private fun CenteredGreetingEmptyState(
  userFirstName: String,
  modifier: Modifier = Modifier,
) {
  val tokens = VesperTokens.colors
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { visible = true }

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.padding(horizontal = 24.dp, vertical = 24.dp).padding(bottom = 84.dp),
  ) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn(tween(450, easing = FastOutSlowInEasing)),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
      ) {
        Text(
          text = stringResource(R.string.empty_greeting_prefix, userFirstName),
          style = MaterialTheme.typography.displaySmall,
          color = tokens.textPrimary,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("empty_greeting"),
        )
        Text(
          text = stringResource(R.string.empty_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = tokens.textSecondary,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 8.dp).testTag("empty_subtitle"),
        )
      }
    }
  }
}

@Composable
private fun UserPillBubble(message: ChatMessage) {
  val tokens = VesperTokens.colors
  val shape = RoundedCornerShape(20.dp)
  Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
    Column(
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier =
        Modifier
          .widthIn(max = 310.dp)
          .clip(shape)
          .background(tokens.userBubble)
          .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
      if (message.attachmentLabel != null) {
        Text(
          text = message.attachmentLabel.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = tokens.accentSilverBlue,
        )
      }
      Text(text = message.text, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantResponseBlock(
  message: ChatMessage,
  isCopied: Boolean,
  isSpeaking: Boolean,
  onSelectAppFromChooser: (InstalledAppInfo) -> Unit,
  onCopy: () -> Unit,
  onSpeak: () -> Unit,
) {
  val tokens = VesperTokens.colors
  val tagLabel =
    when (message.category ?: ResponseCategory.LOCAL_ACTION) {
      ResponseCategory.LOCAL_ACTION -> stringResource(R.string.tag_local_action)
      ResponseCategory.RESEARCH -> stringResource(R.string.tag_research)
      ResponseCategory.BEAST_RESEARCH -> stringResource(R.string.tag_beast_research)
    }
  val dotColor =
    if (message.category == ResponseCategory.LOCAL_ACTION) tokens.statusGreen
    else tokens.accentSilverBlue

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(dotColor))
      Text(text = tagLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
    }

    Text(text = message.text, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary)

    // Phase 2 Step C: Inline Minimalist Multi-App Chooser Card
    message.appChooser?.let { chooser ->
      if (chooser.chosenPackageName == null) {
        InlineAppChooserCard(
          candidates = chooser.candidates,
          onSelectApp = onSelectAppFromChooser,
        )
      }
    }

    // Phase 4: Model Attribution Tags at the bottom
    if (message.attributionTags.isNotEmpty()) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        message.attributionTags.forEach { tag ->
          Box(
            modifier =
              Modifier
                .clip(CircleShape)
                .background(tokens.elevatedSurface)
                .hairlineBorder(CircleShape, tokens.hairlineBorder)
                .padding(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Text(
              text = tag,
              style = MaterialTheme.typography.labelSmall,
              color = tokens.textSecondary,
            )
          }
        }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      SubtleIconAction(
        icon = if (isCopied) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
        contentDescription = stringResource(R.string.cd_copy_response),
        active = isCopied,
        onClick = onCopy,
      )
      SubtleIconAction(
        icon = Icons.AutoMirrored.Outlined.VolumeUp,
        contentDescription = stringResource(R.string.cd_speak_response),
        active = isSpeaking,
        onClick = onSpeak,
      )
      AnimatedVisibility(visible = isSpeaking) {
        Text(
          text = stringResource(R.string.speaking_response),
          style = MaterialTheme.typography.bodySmall,
          color = tokens.accentSilverBlue,
          modifier = Modifier.padding(start = 4.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineAppChooserCard(
  candidates: List<InstalledAppInfo>,
  onSelectApp: (InstalledAppInfo) -> Unit,
) {
  val tokens = VesperTokens.colors
  val cardShape = RoundedCornerShape(18.dp)

  Surface(
    color = tokens.elevatedSurface,
    shape = cardShape,
    modifier =
      Modifier
        .fillMaxWidth()
        .hairlineBorder(cardShape, tokens.capsuleBorder)
        .testTag("inline_app_chooser_card"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.chooser_card_title),
          style = MaterialTheme.typography.bodyMedium,
          color = tokens.textPrimary,
        )
        Text(
          text = stringResource(R.string.chooser_card_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = tokens.textSecondary,
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        candidates.forEach { app ->
          val chipShape = RoundedCornerShape(14.dp)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .clip(chipShape)
                .background(tokens.userBubble)
                .hairlineBorder(chipShape, tokens.hairlineBorder)
                .clickable(role = Role.Button) { onSelectApp(app) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
          ) {
            if (app.icon != null) {
              Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)),
              )
            }
            Text(
              text = app.label,
              style = MaterialTheme.typography.labelMedium,
              color = tokens.textPrimary,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SubtleIconAction(
  icon: ImageVector,
  contentDescription: String,
  active: Boolean,
  onClick: () -> Unit,
) {
  val tokens = VesperTokens.colors
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val alpha by
    animateFloatAsState(
      targetValue = if (active || pressed) 0.96f else 0.42f,
      animationSpec = tween(140),
      label = "action_alpha",
    )
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .size(48.dp)
        .clip(CircleShape)
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          role = Role.Button,
          onClick = onClick,
        ),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (active) tokens.accentSilverBlue else tokens.textPrimary,
      modifier = Modifier.size(16.dp).alpha(alpha),
    )
  }
}

@Composable
private fun LiveSynthesisIndicator(statusLine: String) {
  val tokens = VesperTokens.colors
  val pulse by
    rememberInfiniteTransition(label = "synthesis").animateFloat(
      initialValue = 0.28f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
      label = "synthesis_alpha",
    )
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.testTag("live_synthesis_status"),
  ) {
    Box(
      modifier =
        Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(tokens.accentSilverBlue.copy(alpha = pulse)),
    )
    Text(
      text = statusLine,
      style = MaterialTheme.typography.bodySmall,
      color = tokens.accentSilverBlue,
    )
  }
}

@Composable
private fun FloatingInputCapsule(
  inputText: String,
  pendingAttachment: AttachmentFile?,
  isListening: Boolean,
  onInputChanged: (String) -> Unit,
  onOpenAttachments: () -> Unit,
  onClearAttachment: () -> Unit,
  onToggleListening: () -> Unit,
  onSend: () -> Unit,
) {
  val tokens = VesperTokens.colors
  var isFocused by remember { mutableStateOf(false) }
  val canSend = inputText.isNotBlank() || pendingAttachment != null
  val capsuleShape = RoundedCornerShape(28.dp)

  val haloScale by
    rememberInfiniteTransition(label = "mic_halo").animateFloat(
      initialValue = 0.85f,
      targetValue = 1.38f,
      animationSpec =
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "halo_scale",
    )

  Surface(
    color = tokens.elevatedSurface,
    shape = capsuleShape,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    modifier =
      Modifier
        .fillMaxWidth()
        .widthIn(max = 680.dp)
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .capsuleHairlineBorder(shape = capsuleShape, isFocused = isFocused || isListening),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      AnimatedVisibility(visible = pendingAttachment != null) {
        pendingAttachment?.let { attachment ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
              Modifier
                .padding(start = 14.dp, end = 14.dp, top = 10.dp)
                .clip(CircleShape)
                .background(tokens.userBubble)
                .hairlineBorder(CircleShape, tokens.hairlineBorder)
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                .testTag("attachment_pill"),
          ) {
            Text(
              text = attachment.displayName,
              style = MaterialTheme.typography.labelMedium,
              color = tokens.accentSilverBlue,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.widthIn(max = 220.dp),
            )
            IconButton(onClick = onClearAttachment, modifier = Modifier.size(28.dp)) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_remove_attachment),
                tint = tokens.textSecondary,
                modifier = Modifier.size(14.dp),
              )
            }
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
      ) {
        IconButton(
          onClick = onOpenAttachments,
          modifier = Modifier.size(48.dp).testTag("attach_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = stringResource(R.string.cd_attach_media),
            tint = tokens.textSecondary,
            modifier = Modifier.size(22.dp),
          )
        }

        Box(
          contentAlignment = Alignment.CenterStart,
          modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 10.dp),
        ) {
          if (inputText.isEmpty()) {
            Text(
              text =
                if (isListening) stringResource(R.string.input_listening_placeholder)
                else stringResource(R.string.input_placeholder),
              style = MaterialTheme.typography.bodyLarge,
              color = if (isListening) tokens.accentSilverBlue else tokens.textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BasicTextField(
            value = inputText,
            onValueChange = onInputChanged,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.textPrimary),
            cursorBrush = SolidColor(tokens.accentSilverBlue),
            maxLines = 4,
            keyboardOptions =
              KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send,
              ),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            modifier =
              Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .testTag("command_input"),
          )
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
          if (isListening) {
            Box(
              modifier =
                Modifier
                  .size((30f * haloScale).dp)
                  .clip(CircleShape)
                  .background(tokens.accentSilverBlueHalo),
            )
          }
          IconButton(
            onClick = onToggleListening,
            modifier = Modifier.size(48.dp).testTag("mic_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Mic,
              contentDescription = stringResource(R.string.cd_voice_input),
              tint = if (isListening) tokens.accentSilverBlue else tokens.textSecondary,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        AnimatedVisibility(
          visible = canSend,
          enter =
            fadeIn(tween(140)) +
              scaleIn(
                initialScale = 0.72f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
              ),
          exit = fadeOut(tween(110)) + scaleOut(targetScale = 0.72f),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  role = Role.Button,
                ) { onSend() }
                .testTag("send_button"),
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.size(34.dp).clip(CircleShape).background(tokens.textPrimary),
            ) {
              Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.cd_send_message),
                tint = tokens.canvasBackground,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun VesperDrawerContent(
  threads: List<ConversationThread>,
  activeThreadId: String?,
  isAccessibilityTriggerActive: Boolean,
  hasAnyAiKey: Boolean,
  onNewChat: () -> Unit,
  onSelectThread: (ConversationThread) -> Unit,
  onOpenPowerSettings: () -> Unit,
  onOpenAccessibilitySettings: () -> Unit,
  onOpenVaultApps: () -> Unit,
  onOpenFreeAiKeys: () -> Unit,
  onSignOut: () -> Unit,
) {
  val tokens = VesperTokens.colors
  val drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

  ModalDrawerSheet(
    drawerContainerColor = tokens.drawerSurface,
    drawerContentColor = tokens.textPrimary,
    drawerShape = drawerShape,
    modifier =
      Modifier
        .widthIn(max = 320.dp)
        .fillMaxHeight()
        .drawBehind {
          drawLine(
            color = tokens.hairlineBorder,
            start = Offset(size.width - 0.5.dp.toPx(), 0f),
            end = Offset(size.width - 0.5.dp.toPx(), size.height),
            strokeWidth = 1.dp.toPx(),
          )
        },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxHeight()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
      Text(
        text = stringResource(R.string.drawer_brand_title),
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textSecondary,
      )

      Spacer(modifier = Modifier.height(14.dp))

      val newChatShape = RoundedCornerShape(14.dp)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
          Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(newChatShape)
            .background(tokens.userBubble)
            .hairlineBorder(newChatShape, tokens.capsuleBorder)
            .clickable(role = Role.Button, onClick = onNewChat)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag("drawer_new_chat"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Add,
          contentDescription = null,
          tint = tokens.textPrimary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = stringResource(R.string.drawer_new_chat),
          style = MaterialTheme.typography.bodyMedium,
          color = tokens.textPrimary,
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Phase 3: Hardware Button Triggers Setup Card
      val cardShape = RoundedCornerShape(16.dp)
      Surface(
        color = tokens.canvasBackground.copy(alpha = 0.5f),
        shape = cardShape,
        modifier =
          Modifier
            .fillMaxWidth()
            .hairlineBorder(cardShape, tokens.hairlineBorder)
            .testTag("drawer_hardware_card"),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Bolt,
              contentDescription = null,
              tint = tokens.accentSilverBlue,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = stringResource(R.string.drawer_hardware_triggers),
              style = MaterialTheme.typography.labelSmall,
              color = tokens.textSecondary,
            )
          }

          HardwareTriggerRow(
            icon = Icons.Outlined.PowerSettingsNew,
            label = stringResource(R.string.drawer_enable_power_launch),
            activeBadge = null,
            testTag = "btn_enable_power_launch",
            onClick = onOpenPowerSettings,
          )
          HardwareTriggerRow(
            icon = Icons.Outlined.VolumeDown,
            label = stringResource(R.string.drawer_enable_volume_trigger),
            activeBadge = if (isAccessibilityTriggerActive) "ON" else "SETUP",
            testTag = "btn_enable_volume_trigger",
            onClick = onOpenAccessibilitySettings,
          )
          Text(
            text = stringResource(R.string.drawer_volume_hint),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = stringResource(R.string.drawer_recent_threads),
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
      )

      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(threads, key = { it.id }) { thread ->
          val selected = thread.id == activeThreadId
          val itemShape = RoundedCornerShape(14.dp)
          Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
              Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(itemShape)
                .background(if (selected) tokens.userBubble else Color.Transparent)
                .then(
                  if (selected) Modifier.hairlineBorder(itemShape, tokens.hairlineBorder)
                  else Modifier,
                )
                .clickable(role = Role.Button) { onSelectThread(thread) }
                .padding(horizontal = 12.dp, vertical = 9.dp),
          ) {
            Text(
              text = thread.title,
              style = MaterialTheme.typography.bodyMedium,
              color = if (selected) tokens.accentSilverBlue else tokens.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${thread.timeAgo} • ${thread.preview}",
              style = MaterialTheme.typography.bodySmall,
              color = tokens.textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      HorizontalDivider(thickness = 1.dp, color = tokens.hairlineBorder)
      Spacer(modifier = Modifier.height(6.dp))

      DrawerFooterRow(
        icon = Icons.Outlined.Apps,
        label = stringResource(R.string.drawer_vault_connected_apps),
        badge = null,
        tint = tokens.textPrimary,
        testTag = "drawer_vault_apps",
        onClick = onOpenVaultApps,
      )
      DrawerFooterRow(
        icon = Icons.Outlined.Key,
        label = stringResource(R.string.drawer_free_ai_keys),
        badge = if (hasAnyAiKey) "READY" else "ADD",
        tint = tokens.textPrimary,
        testTag = "drawer_free_ai_keys",
        onClick = onOpenFreeAiKeys,
      )
      DrawerFooterRow(
        icon = Icons.AutoMirrored.Outlined.Logout,
        label = stringResource(R.string.drawer_sign_out),
        badge = null,
        tint = tokens.textSecondary,
        testTag = "drawer_sign_out",
        onClick = onSignOut,
      )
    }
  }
}

@Composable
private fun HardwareTriggerRow(
  icon: ImageVector,
  label: String,
  activeBadge: String?,
  testTag: String,
  onClick: () -> Unit,
) {
  val tokens = VesperTokens.colors
  val shape = RoundedCornerShape(12.dp)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier =
      Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clip(shape)
        .background(tokens.userBubble)
        .hairlineBorder(shape, tokens.hairlineBorder)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 9.dp)
        .testTag(testTag),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tokens.textPrimary, modifier = Modifier.size(16.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = tokens.textPrimary,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (activeBadge != null) {
      Text(
        text = activeBadge,
        style = MaterialTheme.typography.labelSmall,
        color = if (activeBadge == "ON") tokens.statusGreen else tokens.accentSilverBlue,
      )
    }
  }
}

@Composable
private fun DrawerFooterRow(
  icon: ImageVector,
  label: String,
  badge: String?,
  tint: Color,
  testTag: String,
  onClick: () -> Unit,
) {
  val tokens = VesperTokens.colors
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier =
      Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(14.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 10.dp)
        .testTag(testTag),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint, modifier = Modifier.weight(1f))
    if (badge != null) {
      Text(
        text = badge,
        style = MaterialTheme.typography.labelSmall,
        color = if (badge == "READY") tokens.statusGreen else tokens.accentSilverBlue,
      )
    }
  }
}

@Composable
private fun PrivacyFloatingBanner(
  visible: Boolean,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = VesperTokens.colors
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f),
    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.96f),
    modifier = modifier,
  ) {
    Surface(
      color = tokens.drawerSurface,
      shape = CircleShape,
      modifier =
        Modifier
          .hairlineBorder(CircleShape, tokens.capsuleBorder)
          .clickable { onDismiss() }
          .testTag("privacy_status_card"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(tokens.statusGreen))
        Text(
          text = stringResource(R.string.privacy_sandbox_banner),
          style = MaterialTheme.typography.labelLarge,
          color = tokens.textPrimary,
        )
      }
    }
  }
}

@Composable
private fun AutomationProgressPill(
  progress: AutomationProgressState,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = VesperTokens.colors
  AnimatedVisibility(
    visible = !progress.statusText.isNullOrBlank(),
    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f),
    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.96f),
    modifier = modifier,
  ) {
    Surface(
      color = tokens.drawerSurface,
      shape = CircleShape,
      modifier =
        Modifier
          .hairlineBorder(CircleShape, tokens.focusedBorder)
          .clickable { onDismiss() }
          .testTag("automation_progress_pill"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      ) {
        Box(
          modifier =
            Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(if (progress.isCompleted) tokens.statusGreen else tokens.accentSilverBlue),
        )
        Text(
          text = progress.statusText.orEmpty(),
          style = MaterialTheme.typography.labelLarge,
          color = tokens.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
