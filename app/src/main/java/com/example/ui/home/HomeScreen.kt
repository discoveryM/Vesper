package com.example.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.ui.auth.UserProfile
import com.example.ui.theme.VesperTheme
import com.example.ui.theme.VesperTokens
import com.example.ui.theme.capsuleHairlineBorder
import com.example.ui.theme.hairlineBorder
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ExecutionMode(val pillLabel: String, val menuTitle: String, val subtitle: String) {
  AUTO_HYBRID("Hybrid", "Auto Hybrid", "Routes device intents locally, research to cloud"),
  LOCAL_ONLY("Local", "Local Only", "100% on-device execution • Zero external calls"),
  CLOUD_RESEARCH("Cloud", "Cloud Research", "Deep synthesis with web & document reasoning"),
}

enum class ResponseTag {
  LOCAL_ACTION,
  RESEARCH,
}

enum class AttachmentKind(val label: String) {
  CAMERA("Camera Capture"),
  GALLERY("Photo Selected"),
  DOCUMENT("Document Attached"),
}

@Immutable
data class ChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val tag: ResponseTag? = null,
  val attachmentLabel: String? = null,
)

@Immutable
data class ConversationThread(
  val id: String,
  val title: String,
  val preview: String,
  val timeAgo: String,
  val messages: List<ChatMessage>,
)

@Composable
fun HomeScreen(
  userProfile: UserProfile,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = VesperTokens.colors
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val coroutineScope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val clipboardManager = LocalClipboardManager.current

  var selectedMode by remember { mutableStateOf(ExecutionMode.AUTO_HYBRID) }
  var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
  var threads by remember { mutableStateOf(defaultRecentThreads()) }
  var activeThreadId by remember { mutableStateOf<String?>(null) }
  var inputText by remember { mutableStateOf("") }
  var pendingAttachment by remember { mutableStateOf<AttachmentKind?>(null) }
  var isListening by remember { mutableStateOf(false) }
  var isGenerating by remember { mutableStateOf(false) }
  var speakingMessageId by remember { mutableStateOf<String?>(null) }
  var copiedMessageId by remember { mutableStateOf<String?>(null) }
  var showPrivacyCard by remember { mutableStateOf(false) }
  var showAttachmentSheet by remember { mutableStateOf(false) }
  var showVaultSheet by remember { mutableStateOf(false) }
  var localSandboxIsolation by remember { mutableStateOf(true) }
  var zeroBackgroundListening by remember { mutableStateOf(true) }

  BackHandler(enabled = drawerState.isOpen) {
    coroutineScope.launch { drawerState.close() }
  }

  LaunchedEffect(showPrivacyCard) {
    if (showPrivacyCard) {
      delay(3000L)
      showPrivacyCard = false
    }
  }

  LaunchedEffect(isListening) {
    if (isListening) {
      delay(2400L)
      if (isListening) {
        if (inputText.isBlank()) {
          inputText = "Summarize today's focus items"
        }
        isListening = false
      }
    }
  }

  LaunchedEffect(messages.size, isGenerating) {
    val target = (messages.size + if (isGenerating) 1 else 0) - 1
    if (target >= 0) listState.animateScrollToItem(target)
  }

  fun sendMessage() {
    val trimmed = inputText.trim()
    val attachment = pendingAttachment
    if (trimmed.isEmpty() && attachment == null) return

    val promptText = trimmed.ifEmpty { "Process ${attachment?.label ?: "attachment"}" }
    val threadId = activeThreadId ?: UUID.randomUUID().toString()
    val userMessage =
      ChatMessage(
        id = UUID.randomUUID().toString(),
        isUser = true,
        text = promptText,
        attachmentLabel = attachment?.label,
      )

    activeThreadId = threadId
    messages = messages + userMessage
    inputText = ""
    pendingAttachment = null
    isListening = false
    isGenerating = true

    coroutineScope.launch {
      delay(480L)
      val (tag, reply) = generateMockResponse(promptText, selectedMode, attachment)
      val assistantMessage =
        ChatMessage(
          id = UUID.randomUUID().toString(),
          isUser = false,
          text = reply,
          tag = tag,
        )
      val updated = messages + assistantMessage
      messages = updated
      isGenerating = false
      val existing = threads.find { it.id == threadId }
      val newThread =
        ConversationThread(
          id = threadId,
          title = existing?.title ?: promptText.take(34),
          preview = reply.take(58),
          timeAgo = "Just now",
          messages = updated,
        )
      threads = listOf(newThread) + threads.filterNot { it.id == threadId }
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    scrimColor = tokens.canvasBackground.copy(alpha = 0.65f),
    drawerContent = {
      VesperDrawerContent(
        threads = threads,
        activeThreadId = activeThreadId,
        onNewChat = {
          activeThreadId = null
          messages = emptyList()
          isGenerating = false
          coroutineScope.launch { drawerState.close() }
        },
        onSelectThread = { thread ->
          activeThreadId = thread.id
          messages = thread.messages
          isGenerating = false
          coroutineScope.launch { drawerState.close() }
        },
        onOpenVault = {
          coroutineScope.launch { drawerState.close() }
          showVaultSheet = true
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
          selectedMode = selectedMode,
          onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
          onSelectMode = { selectedMode = it },
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
          targetState = messages.isNotEmpty() || isGenerating,
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
                  PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 136.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize().widthIn(max = 680.dp).testTag("chat_feed"),
              ) {
                items(messages, key = { it.id }) { msg ->
                  if (msg.isUser) {
                    UserPillBubble(msg)
                  } else {
                    AssistantResponseBlock(
                      message = msg,
                      isCopied = copiedMessageId == msg.id,
                      isSpeaking = speakingMessageId == msg.id,
                      onCopy = {
                        clipboardManager.setText(AnnotatedString(msg.text))
                        copiedMessageId = msg.id
                        coroutineScope.launch {
                          delay(1600L)
                          if (copiedMessageId == msg.id) copiedMessageId = null
                        }
                      },
                      onSpeak = {
                        speakingMessageId = if (speakingMessageId == msg.id) null else msg.id
                      },
                    )
                  }
                }
                if (isGenerating) {
                  item(key = "processing") { ProcessingIndicator() }
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

        // Bottom Dock: Subtle obsidian fade + Floating Minimalist Input Capsule
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
            inputText = inputText,
            pendingAttachment = pendingAttachment,
            isListening = isListening,
            onInputChanged = { inputText = it },
            onOpenAttachments = { showAttachmentSheet = true },
            onClearAttachment = { pendingAttachment = null },
            onToggleListening = { isListening = !isListening },
            onSend = ::sendMessage,
          )
        }
      }
    }
  }

  if (showAttachmentSheet) {
    AttachmentBottomSheet(
      onDismiss = { showAttachmentSheet = false },
      onSelect = {
        pendingAttachment = it
        showAttachmentSheet = false
      },
    )
  }

  if (showVaultSheet) {
    VaultSettingsBottomSheet(
      localSandbox = localSandboxIsolation,
      zeroListening = zeroBackgroundListening,
      onToggleSandbox = { localSandboxIsolation = it },
      onToggleZeroListening = { zeroBackgroundListening = it },
      onClearHistory = {
        activeThreadId = null
        messages = emptyList()
        threads = emptyList()
        showVaultSheet = false
      },
      onDismiss = { showVaultSheet = false },
    )
  }
}

@Composable
private fun VesperTopBar(
  userProfile: UserProfile,
  selectedMode: ExecutionMode,
  onOpenDrawer: () -> Unit,
  onSelectMode: (ExecutionMode) -> Unit,
  onPrivacyClick: () -> Unit,
  onSignOut: () -> Unit,
) {
  val tokens = VesperTokens.colors
  var modeExpanded by remember { mutableStateOf(false) }
  var avatarMenuExpanded by remember { mutableStateOf(false) }

  Box(
    modifier =
      Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 12.dp),
  ) {
    // Left: Minimal 2-line menu icon
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

    // Center: Minimalist mode pill ("Hybrid ▾")
    Box(modifier = Modifier.align(Alignment.Center)) {
      val dotColor by
        animateColorAsState(
          targetValue =
            if (selectedMode == ExecutionMode.CLOUD_RESEARCH) {
              tokens.accentSilverBlue
            } else {
              tokens.statusGreen
            },
          label = "mode_dot",
        )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
          Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(tokens.elevatedSurface)
            .hairlineBorder(CircleShape, tokens.hairlineBorder)
            .clickable(role = Role.Button) { modeExpanded = true }
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("mode_pill"),
      ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(
          text = selectedMode.pillLabel,
          style = MaterialTheme.typography.labelLarge,
          color = tokens.textPrimary,
        )
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowDown,
          contentDescription = stringResource(R.string.cd_execution_mode_selector),
          tint = tokens.textSecondary,
          modifier = Modifier.size(16.dp),
        )
      }

      DropdownMenu(
        expanded = modeExpanded,
        onDismissRequest = { modeExpanded = false },
        offset = DpOffset((-42).dp, 8.dp),
        containerColor = tokens.elevatedSurface,
        shape = RoundedCornerShape(18.dp),
        modifier =
          Modifier
            .width(232.dp)
            .hairlineBorder(RoundedCornerShape(18.dp), tokens.capsuleBorder),
      ) {
        ExecutionMode.entries.forEach { mode ->
          val active = mode == selectedMode
          DropdownMenuItem(
            onClick = {
              onSelectMode(mode)
              modeExpanded = false
            },
            text = {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = mode.menuTitle,
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (active) tokens.accentSilverBlue else tokens.textPrimary,
                )
                Text(
                  text = mode.subtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.textSecondary,
                  maxLines = 2,
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

    // Right: Privacy Shield + User Google Profile Avatar (Coil AsyncImage with Initial Fallback)
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
          UserProfileAvatar(
            firstName = userProfile.firstName,
            photoUri = userProfile.profilePictureUri,
          )
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
          Text(
            text = initial,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textPrimary,
          )
        },
        error = {
          Text(
            text = initial,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textPrimary,
          )
        },
      )
    } else {
      Text(
        text = initial,
        style = MaterialTheme.typography.labelMedium,
        color = tokens.textPrimary,
      )
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

  // Vertically and horizontally centered with generous negative space and zero suggestion cards
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
      Text(
        text = message.text,
        style = MaterialTheme.typography.bodyLarge,
        color = tokens.textPrimary,
      )
    }
  }
}

@Composable
private fun AssistantResponseBlock(
  message: ChatMessage,
  isCopied: Boolean,
  isSpeaking: Boolean,
  onCopy: () -> Unit,
  onSpeak: () -> Unit,
) {
  val tokens = VesperTokens.colors
  val tagLabel =
    if (message.tag == ResponseTag.RESEARCH) {
      stringResource(R.string.tag_research)
    } else {
      stringResource(R.string.tag_local_action)
    }
  val dotColor = if (message.tag == ResponseTag.RESEARCH) tokens.accentSilverBlue else tokens.statusGreen

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(dotColor))
      Text(
        text = tagLabel,
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textSecondary,
      )
    }

    Text(
      text = message.text,
      style = MaterialTheme.typography.bodyLarge,
      color = tokens.textPrimary,
    )

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
private fun ProcessingIndicator() {
  val tokens = VesperTokens.colors
  val pulse by
    rememberInfiniteTransition(label = "processing").animateFloat(
      initialValue = 0.25f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
      label = "processing_alpha",
    )
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(tokens.accentSilverBlue.copy(alpha = pulse)),
    )
    Text(
      text = stringResource(R.string.tag_processing),
      style = MaterialTheme.typography.labelSmall,
      color = tokens.textSecondary,
    )
  }
}

@Composable
private fun FloatingInputCapsule(
  inputText: String,
  pendingAttachment: AttachmentKind?,
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
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
          ) {
            Text(
              text = attachment.label,
              style = MaterialTheme.typography.labelMedium,
              color = tokens.textPrimary,
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
                if (isListening) {
                  stringResource(R.string.input_listening_placeholder)
                } else {
                  stringResource(R.string.input_placeholder)
                },
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
  onNewChat: () -> Unit,
  onSelectThread: (ConversationThread) -> Unit,
  onOpenVault: () -> Unit,
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
        .widthIn(max = 312.dp)
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
          .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
      Text(
        text = stringResource(R.string.drawer_brand_title),
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textSecondary,
      )

      Spacer(modifier = Modifier.height(18.dp))

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
            .padding(horizontal = 14.dp, vertical = 12.dp)
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

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = stringResource(R.string.drawer_recent_threads),
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
      )

      Spacer(modifier = Modifier.height(10.dp))

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
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
      Spacer(modifier = Modifier.height(8.dp))

      DrawerFooterRow(
        icon = Icons.Outlined.Tune,
        label = stringResource(R.string.drawer_vault_settings),
        tint = tokens.textPrimary,
        testTag = "drawer_vault_settings",
        onClick = onOpenVault,
      )
      DrawerFooterRow(
        icon = Icons.AutoMirrored.Outlined.Logout,
        label = stringResource(R.string.drawer_sign_out),
        tint = tokens.textSecondary,
        testTag = "drawer_sign_out",
        onClick = onSignOut,
      )
    }
  }
}

@Composable
private fun DrawerFooterRow(
  icon: ImageVector,
  label: String,
  tint: Color,
  testTag: String,
  onClick: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier =
      Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(14.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 12.dp)
        .testTag(testTag),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentBottomSheet(
  onDismiss: () -> Unit,
  onSelect: (AttachmentKind) -> Unit,
) {
  val tokens = VesperTokens.colors
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = tokens.elevatedSurface,
    contentColor = tokens.textPrimary,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
      Text(
        text = stringResource(R.string.sheet_attach_title),
        style = MaterialTheme.typography.titleMedium,
        color = tokens.textPrimary,
      )
      Spacer(modifier = Modifier.height(24.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SheetCircleOption(Icons.Outlined.CameraAlt, stringResource(R.string.attach_camera)) {
          onSelect(AttachmentKind.CAMERA)
        }
        SheetCircleOption(Icons.Outlined.PhotoLibrary, stringResource(R.string.attach_gallery)) {
          onSelect(AttachmentKind.GALLERY)
        }
        SheetCircleOption(Icons.Outlined.Description, stringResource(R.string.attach_document)) {
          onSelect(AttachmentKind.DOCUMENT)
        }
      }
      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

@Composable
private fun SheetCircleOption(icon: ImageVector, label: String, onClick: () -> Unit) {
  val tokens = VesperTokens.colors
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(8.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier
          .size(60.dp)
          .clip(CircleShape)
          .background(tokens.userBubble)
          .hairlineBorder(CircleShape, tokens.capsuleBorder),
    ) {
      Icon(imageVector = icon, contentDescription = label, tint = tokens.textPrimary, modifier = Modifier.size(22.dp))
    }
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultSettingsBottomSheet(
  localSandbox: Boolean,
  zeroListening: Boolean,
  onToggleSandbox: (Boolean) -> Unit,
  onToggleZeroListening: (Boolean) -> Unit,
  onClearHistory: () -> Unit,
  onDismiss: () -> Unit,
) {
  val tokens = VesperTokens.colors
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = tokens.elevatedSurface,
    contentColor = tokens.textPrimary,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
      Text(text = stringResource(R.string.vault_title), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
      HorizontalDivider(color = tokens.hairlineBorder)
      VaultSwitchRow(stringResource(R.string.vault_sandbox_label), stringResource(R.string.vault_sandbox_desc), localSandbox, onToggleSandbox)
      VaultSwitchRow(stringResource(R.string.vault_zero_listening_label), stringResource(R.string.vault_zero_listening_desc), zeroListening, onToggleZeroListening)
      val clearShape = RoundedCornerShape(14.dp)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
          Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(clearShape)
            .background(tokens.userBubble)
            .hairlineBorder(clearShape, tokens.hairlineBorder)
            .clickable(role = Role.Button, onClick = onClearHistory)
            .padding(vertical = 12.dp),
      ) {
        Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.vault_clear_sessions), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun VaultSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  val tokens = VesperTokens.colors
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
      Text(text = title, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
      Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = tokens.canvasBackground,
          checkedTrackColor = tokens.accentSilverBlue,
          uncheckedThumbColor = tokens.textSecondary,
          uncheckedTrackColor = tokens.userBubble,
        ),
    )
  }
}

private fun generateMockResponse(
  prompt: String,
  mode: ExecutionMode,
  attachment: AttachmentKind?,
): Pair<ResponseTag, String> {
  val lower = prompt.lowercase()
  val tag =
    when {
      mode == ExecutionMode.LOCAL_ONLY -> ResponseTag.LOCAL_ACTION
      mode == ExecutionMode.CLOUD_RESEARCH -> ResponseTag.RESEARCH
      lower.contains("open") || lower.contains("send") || lower.contains("message") ||
        lower.contains("compress") || attachment != null -> ResponseTag.LOCAL_ACTION
      else -> ResponseTag.RESEARCH
    }
  val text =
    when {
      attachment == AttachmentKind.CAMERA ->
        "Captured frame in Vesper's ephemeral sandbox. Zero EXIF or location metadata retained."
      attachment == AttachmentKind.GALLERY ->
        "Image context staged locally. Ready for analysis, text extraction, or app handoff."
      attachment == AttachmentKind.DOCUMENT ->
        "Document parsed in local memory. Ask for a concise brief, key citations, or compression."
      tag == ResponseTag.LOCAL_ACTION ->
        "Executed inside Vesper's local sandbox (${mode.menuTitle}) with no background telemetry for \"$prompt\"."
      else ->
        "Synthesized in ${mode.menuTitle} mode: breaking \"$prompt\" down into clean, verifiable steps with minimal noise."
    }
  return tag to text
}

private fun defaultRecentThreads(): List<ConversationThread> =
  listOf(
    ConversationThread(
      id = "t1",
      title = "Q4 Strategy Brief",
      preview = "Distilled 12 key action items",
      timeAgo = "2h ago",
      messages =
        listOf(
          ChatMessage("t1_u", isUser = true, text = "Summarize the Q4 Strategy Brief PDF", attachmentLabel = "Document Attached"),
          ChatMessage("t1_a", isUser = false, tag = ResponseTag.RESEARCH, text = "Distilled the Q4 Strategy Brief into three core priorities: latency reduction under 120ms, zero-trust local sandboxing, and streamlined onboarding."),
        ),
    ),
    ConversationThread(
      id = "t2",
      title = "Device Focus Schedule",
      preview = "Staged Do Not Disturb until 18:00",
      timeAgo = "Yesterday",
      messages =
        listOf(
          ChatMessage("t2_u", isUser = true, text = "Enable deep focus mode until 6 PM"),
          ChatMessage("t2_a", isUser = false, tag = ResponseTag.LOCAL_ACTION, text = "Staged Focus profile locally until 18:00 with priority calls preserved."),
        ),
    ),
  )

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun HomeScreenPreview() {
  VesperTheme {
    HomeScreen(
      userProfile = UserProfile(firstName = "Alex", email = "alex@example.com"),
      onSignOut = {},
    )
  }
}
