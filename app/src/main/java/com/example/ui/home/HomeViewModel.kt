package com.example.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.FreeAiModel
import com.example.ai.MultiAiResearchEngine
import com.example.automation.AutomationProgressState
import com.example.automation.ParsedAutomationWorkflow
import com.example.automation.VesperAutomationEngine
import com.example.data.AppVaultStore
import com.example.data.FreeAiKeys
import com.example.data.FreeAiKeysStore
import com.example.resolver.AppResolutionResult
import com.example.resolver.AttachmentFile
import com.example.resolver.InstalledAppInfo
import com.example.resolver.SmartAppResolver
import com.example.vault.LocalPasswordVault
import com.example.vault.VaultCredentialMeta
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResponseCategory {
  LOCAL_ACTION,
  RESEARCH,
  BEAST_RESEARCH,
}

@Immutable
data class AppChooserPrompt(
  val taskKey: String,
  val intentAction: String,
  val attachment: AttachmentFile?,
  val commandText: String,
  val candidates: List<InstalledAppInfo>,
  val chosenPackageName: String? = null,
)

@Immutable
data class PendingBiometricWorkflow(
  val workflow: ParsedAutomationWorkflow,
  val credentialMeta: VaultCredentialMeta,
)

@Immutable
data class ChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val category: ResponseCategory? = null,
  val attachmentLabel: String? = null,
  val attributionTags: List<String> = emptyList(),
  val appChooser: AppChooserPrompt? = null,
)

@Immutable
data class ConversationThread(
  val id: String,
  val title: String,
  val preview: String,
  val timeAgo: String,
  val messages: List<ChatMessage>,
)

@Immutable
data class HomeUiState(
  val selectedModel: FreeAiModel = FreeAiModel.GEMINI_2_5_FLASH,
  val activeThreadId: String? = null,
  val messages: List<ChatMessage> = emptyList(),
  val threads: List<ConversationThread> = emptyList(),
  val inputText: String = "",
  val pendingAttachment: AttachmentFile? = null,
  val isListening: Boolean = false,
  val isGenerating: Boolean = false,
  val liveSynthesisStatus: String? = null,
  val speakingMessageId: String? = null,
  val copiedMessageId: String? = null,
  val installedApps: List<InstalledAppInfo> = emptyList(),
  val disabledPackages: Set<String> = emptySet(),
  val isLoadingApps: Boolean = false,
  val freeAiKeys: FreeAiKeys = FreeAiKeys(),
  val vaultCredentials: List<VaultCredentialMeta> = emptyList(),
  val biometricLockEnabled: Boolean = true,
  val pendingSaveOnceWorkflow: ParsedAutomationWorkflow? = null,
  val pendingBiometricWorkflow: PendingBiometricWorkflow? = null,
  val automationProgress: AutomationProgressState = AutomationProgressState(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val appVaultStore = AppVaultStore(appContext)
  private val keysStore = FreeAiKeysStore(appContext)
  private val passwordVault = LocalPasswordVault(appContext)
  private val resolver = SmartAppResolver(appContext, appVaultStore)
  private val researchEngine = MultiAiResearchEngine(keysStore)

  private val _uiState =
    MutableStateFlow(
      HomeUiState(
        threads = defaultThreads(),
        freeAiKeys = keysStore.getKeys(),
        vaultCredentials = passwordVault.credentialsFlow.value,
        biometricLockEnabled = passwordVault.biometricLockFlow.value,
      ),
    )
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private var activeRequestJob: Job? = null
  private var copiedResetJob: Job? = null
  private var speakingResetJob: Job? = null

  init {
    viewModelScope.launch {
      appVaultStore.disabledPackagesFlow.collect { disabled ->
        _uiState.update { it.copy(disabledPackages = disabled) }
      }
    }
    viewModelScope.launch {
      keysStore.keysFlow.collect { keys ->
        _uiState.update { it.copy(freeAiKeys = keys) }
      }
    }
    viewModelScope.launch {
      passwordVault.credentialsFlow.collect { creds ->
        _uiState.update { it.copy(vaultCredentials = creds) }
      }
    }
    viewModelScope.launch {
      passwordVault.biometricLockFlow.collect { lock ->
        _uiState.update { it.copy(biometricLockEnabled = lock) }
      }
    }
    viewModelScope.launch {
      VesperAutomationEngine.progressState.collect { progress ->
        _uiState.update { it.copy(automationProgress = progress) }
      }
    }
  }

  fun onInputChanged(value: String) {
    _uiState.update { it.copy(inputText = value) }
  }

  fun selectModel(model: FreeAiModel) {
    _uiState.update { it.copy(selectedModel = model) }
  }

  fun setListening(listening: Boolean) {
    _uiState.update { it.copy(isListening = listening) }
  }

  fun onCameraBitmapCaptured(bitmap: Bitmap) {
    viewModelScope.launch {
      val attachment = SmartAppResolver.saveBitmapForHandoff(appContext, bitmap)
      if (attachment != null) {
        _uiState.update { it.copy(pendingAttachment = attachment) }
      }
    }
  }

  fun onUriPicked(uri: Uri, fallbackMime: String) {
    viewModelScope.launch {
      val attachment = SmartAppResolver.resolveAttachmentMetadata(appContext, uri, fallbackMime)
      _uiState.update { it.copy(pendingAttachment = attachment) }
    }
  }

  fun clearAttachment() {
    _uiState.update { it.copy(pendingAttachment = null) }
  }

  fun handleIncomingVoiceCommand(transcript: String) {
    val clean = transcript.trim()
    if (clean.isNotEmpty()) submitCommand(overrideText = clean)
  }

  fun submitCommand(overrideText: String? = null) {
    val state = _uiState.value
    val rawText = (overrideText ?: state.inputText).trim()
    val attachment = state.pendingAttachment
    if (rawText.isEmpty() && attachment == null) return

    val promptText = rawText.ifEmpty { "Open ${attachment?.displayName ?: "file"}" }
    val threadId = state.activeThreadId ?: UUID.randomUUID().toString()
    val userMessage =
      ChatMessage(
        id = UUID.randomUUID().toString(),
        isUser = true,
        text = promptText,
        attachmentLabel = attachment?.displayName,
      )

    activeRequestJob?.cancel()
    _uiState.update {
      it.copy(
        activeThreadId = threadId,
        messages = it.messages + userMessage,
        inputText = if (overrideText == null) "" else it.inputText,
        pendingAttachment = null,
        isListening = false,
        isGenerating = true,
        liveSynthesisStatus = "Checking local workflows & apps…",
      )
    }

    activeRequestJob =
      viewModelScope.launch {
        // Phase 5: Multi-step UI automation & Save-Once Password Vault check (100% local)
        if (attachment == null) {
          val workflow = VesperAutomationEngine.parseWorkflowLocally(appContext, promptText)
          if (workflow != null && workflow.requiresLoginSwitch) {
            val savedCred =
              passwordVault.findCredentialForApp(
                packageName = workflow.targetApp.packageName,
                accountHint = workflow.accountIdentifier,
              )
            if (savedCred == null) {
              // Trigger first-time "Save [App Name] Login Once" capture sheet
              _uiState.update {
                it.copy(
                  isGenerating = false,
                  liveSynthesisStatus = null,
                  pendingSaveOnceWorkflow = workflow,
                )
              }
              return@launch
            } else if (_uiState.value.biometricLockEnabled) {
              _uiState.update {
                it.copy(
                  isGenerating = false,
                  liveSynthesisStatus = null,
                  pendingBiometricWorkflow = PendingBiometricWorkflow(workflow, savedCred),
                )
              }
              return@launch
            } else {
              runWorkflowNow(threadId, promptText, workflow, savedCred)
              return@launch
            }
          }
        }

        // Phase 2: 100% local SmartAppResolver
        when (val resolution = resolver.resolveAndExecute(promptText, attachment)) {
          is AppResolutionResult.Launched -> {
            appendAssistantMessage(
              threadId = threadId,
              titleSeed = promptText,
              message =
                ChatMessage(
                  id = UUID.randomUUID().toString(),
                  isUser = false,
                  text = resolution.summary,
                  category = ResponseCategory.LOCAL_ACTION,
                  attributionTags = listOf("LOCAL HANDOFF", resolution.app.label.uppercase()),
                ),
            )
          }

          is AppResolutionResult.ChooseApp -> {
            appendAssistantMessage(
              threadId = threadId,
              titleSeed = promptText,
              message =
                ChatMessage(
                  id = UUID.randomUUID().toString(),
                  isUser = false,
                  text = "Choose an installed app to handle this locally:",
                  category = ResponseCategory.LOCAL_ACTION,
                  appChooser =
                    AppChooserPrompt(
                      taskKey = resolution.taskKey,
                      intentAction = resolution.intentAction,
                      attachment = resolution.attachment,
                      commandText = resolution.commandText,
                      candidates = resolution.candidates,
                    ),
                ),
            )
          }

          AppResolutionResult.NotAnAppIntent -> {
            if (attachment != null) {
              appendAssistantMessage(
                threadId = threadId,
                titleSeed = promptText,
                message =
                  ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text =
                      "Kept ${attachment.displayName} strictly on-device. Mention an installed app or ask to edit, view, or send this file locally.",
                    category = ResponseCategory.LOCAL_ACTION,
                    attributionTags = listOf("PRIVACY FIREWALL"),
                  ),
              )
              return@launch
            }

            // Phase 4: Free Multi-LLM Research Engine (plain text only)
            val model = _uiState.value.selectedModel
            val researchResult =
              researchEngine.ask(
                plainTextQuestion = promptText,
                model = model,
                onLiveStatus = { status ->
                  _uiState.update { it.copy(liveSynthesisStatus = status) }
                },
              )

            appendAssistantMessage(
              threadId = threadId,
              titleSeed = promptText,
              message =
                ChatMessage(
                  id = UUID.randomUUID().toString(),
                  isUser = false,
                  text = researchResult.text,
                  category =
                    if (researchResult.isBeastReport) ResponseCategory.BEAST_RESEARCH
                    else ResponseCategory.RESEARCH,
                  attributionTags = researchResult.attributionTags,
                ),
            )
          }
        }
      }
  }

  fun onSaveOnceConfirmed(username: String, passwordChars: CharArray) {
    val workflow = _uiState.value.pendingSaveOnceWorkflow ?: run {
      passwordChars.fill('\u0000')
      return
    }
    val threadId = _uiState.value.activeThreadId ?: UUID.randomUUID().toString()
    val savedMeta =
      passwordVault.saveCredential(
        packageName = workflow.targetApp.packageName,
        appLabel = workflow.targetApp.label,
        username = username,
        passwordChars = passwordChars,
      )
    _uiState.update { it.copy(pendingSaveOnceWorkflow = null) }
    runWorkflowNow(threadId, workflow.originalCommand, workflow, savedMeta)
  }

  fun dismissSaveOnce() {
    _uiState.update { it.copy(pendingSaveOnceWorkflow = null) }
  }

  fun onBiometricWorkflowApproved() {
    val pending = _uiState.value.pendingBiometricWorkflow ?: return
    val threadId = _uiState.value.activeThreadId ?: UUID.randomUUID().toString()
    _uiState.update { it.copy(pendingBiometricWorkflow = null) }
    runWorkflowNow(threadId, pending.workflow.originalCommand, pending.workflow, pending.credentialMeta)
  }

  fun dismissBiometricWorkflow() {
    _uiState.update { it.copy(pendingBiometricWorkflow = null) }
  }

  private fun runWorkflowNow(
    threadId: String,
    titleSeed: String,
    workflow: ParsedAutomationWorkflow,
    credentialMeta: VaultCredentialMeta?,
  ) {
    VesperAutomationEngine.executeWorkflow(
      context = appContext,
      workflow = workflow,
      credentialMeta = credentialMeta,
      vault = passwordVault,
      onResultMessage = { resultText ->
        appendAssistantMessage(
          threadId = threadId,
          titleSeed = titleSeed,
          message =
            ChatMessage(
              id = UUID.randomUUID().toString(),
              isUser = false,
              text = resultText,
              category = ResponseCategory.LOCAL_ACTION,
              attributionTags =
                listOf("ON-DEVICE AUTOMATION", workflow.targetApp.label.uppercase()),
            ),
        )
      },
    )
  }

  fun onAppChosenFromCard(messageId: String, app: InstalledAppInfo) {
    val msg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
    val chooser = msg.appChooser ?: return

    viewModelScope.launch {
      val launched =
        resolver.launchChosenApp(
          app = app,
          taskKey = chooser.taskKey,
          intentAction = chooser.intentAction,
          attachment = chooser.attachment,
          commandText = chooser.commandText,
        )
      _uiState.update { state ->
        val updatedMessages =
          state.messages.map { item ->
            if (item.id == messageId) {
              item.copy(
                text =
                  if (launched) "Launched ${app.label} and saved your preference for this task."
                  else "Unable to open ${app.label}.",
                appChooser = chooser.copy(chosenPackageName = app.packageName),
                attributionTags = listOf("LOCAL HANDOFF", app.label.uppercase()),
              )
            } else {
              item
            }
          }
        state.copy(messages = updatedMessages)
      }
    }
  }

  fun startNewChat() {
    activeRequestJob?.cancel()
    _uiState.update {
      it.copy(
        activeThreadId = null,
        messages = emptyList(),
        inputText = "",
        pendingAttachment = null,
        isListening = false,
        isGenerating = false,
        liveSynthesisStatus = null,
        speakingMessageId = null,
      )
    }
  }

  fun selectThread(thread: ConversationThread) {
    activeRequestJob?.cancel()
    _uiState.update {
      it.copy(
        activeThreadId = thread.id,
        messages = thread.messages,
        inputText = "",
        pendingAttachment = null,
        isListening = false,
        isGenerating = false,
        liveSynthesisStatus = null,
        speakingMessageId = null,
      )
    }
  }

  fun markMessageCopied(messageId: String) {
    copiedResetJob?.cancel()
    _uiState.update { it.copy(copiedMessageId = messageId) }
    copiedResetJob =
      viewModelScope.launch {
        delay(1600L)
        _uiState.update {
          if (it.copiedMessageId == messageId) it.copy(copiedMessageId = null) else it
        }
      }
  }

  fun toggleSpeakMessage(messageId: String) {
    speakingResetJob?.cancel()
    val alreadySpeaking = _uiState.value.speakingMessageId == messageId
    if (alreadySpeaking) {
      _uiState.update { it.copy(speakingMessageId = null) }
    } else {
      _uiState.update { it.copy(speakingMessageId = messageId) }
      speakingResetJob =
        viewModelScope.launch {
          delay(4000L)
          _uiState.update {
            if (it.speakingMessageId == messageId) it.copy(speakingMessageId = null) else it
          }
        }
    }
  }

  fun loadInstalledAppsForVault() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingApps = true) }
      val apps = resolver.loadAllLaunchableAppsForVault()
      _uiState.update { it.copy(installedApps = apps, isLoadingApps = false) }
    }
  }

  fun toggleAppVaultPermission(packageName: String, enabled: Boolean) {
    viewModelScope.launch { appVaultStore.setAppEnabled(packageName, enabled) }
  }

  fun saveVaultCredential(app: InstalledAppInfo, username: String, passwordChars: CharArray) {
    passwordVault.saveCredential(app.packageName, app.label, username, passwordChars)
  }

  fun deleteVaultCredential(credentialId: String) {
    passwordVault.deleteCredential(credentialId)
  }

  fun readVaultPasswordTemp(credentialId: String): CharArray? =
    passwordVault.readPasswordIntoTempRam(credentialId)

  fun toggleBiometricLock(enabled: Boolean) {
    passwordVault.setBiometricLockEnabled(enabled)
  }

  fun saveFreeAiKeys(geminiKey: String, openRouterOrGroqKey: String) {
    keysStore.saveKeys(geminiKey, openRouterOrGroqKey)
  }

  fun clearFreeAiKeys() {
    keysStore.clearKeys()
  }

  private fun appendAssistantMessage(
    threadId: String,
    titleSeed: String,
    message: ChatMessage,
  ) {
    _uiState.update { state ->
      val nextMessages = state.messages + message
      val existing = state.threads.find { it.id == threadId }
      val updatedThread =
        ConversationThread(
          id = threadId,
          title = existing?.title ?: titleSeed.take(36),
          preview = message.text.take(60),
          timeAgo = "Just now",
          messages = nextMessages,
        )
      state.copy(
        messages = nextMessages,
        isGenerating = false,
        liveSynthesisStatus = null,
        threads = listOf(updatedThread) + state.threads.filterNot { it.id == threadId },
      )
    }
  }

  private fun defaultThreads(): List<ConversationThread> =
    listOf(
      ConversationThread(
        id = "thread_demo_1",
        title = "Beast Deep Research • Rust vs Zig",
        preview = "Cross-verified across Gemini + DeepSeek R1",
        timeAgo = "2h ago",
        messages =
          listOf(
            ChatMessage(
              id = "d1_u",
              isUser = true,
              text = "Compare memory safety models in Rust vs Zig",
            ),
            ChatMessage(
              id = "d1_a",
              isUser = false,
              category = ResponseCategory.BEAST_RESEARCH,
              text =
                "Rust enforces compile-time ownership and borrow checking to eliminate data races without a runtime GC, while Zig relies on explicit allocator control, compile-time comptime checks, and optional runtime safety assertions.",
              attributionTags = listOf("GEMINI 2.5 FLASH", "DEEPSEEK R1"),
            ),
          ),
      ),
    )
}
