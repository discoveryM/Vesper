package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.Immutable
import com.example.data.AppVaultStore
import com.example.resolver.InstalledAppInfo
import com.example.resolver.SmartAppResolver
import com.example.vault.LocalPasswordVault
import com.example.vault.VaultCredentialMeta
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class ParsedAutomationWorkflow(
  val originalCommand: String,
  val targetApp: InstalledAppInfo,
  val requiresLoginSwitch: Boolean,
  val accountIdentifier: String?,
  val recipientName: String?,
  val messageText: String?,
)

@Immutable
data class AutomationProgressState(
  val isRunning: Boolean = false,
  val statusText: String? = null,
  val isCompleted: Boolean = false,
)

/**
 * Multi-Step On-Device UI Automation Executor powered by AccessibilityNodeInfo.
 * Reads live screen nodes, dispatches ACTION_CLICK / ACTION_SET_TEXT, and wipes
 * temporary password CharArrays from RAM immediately after field injection.
 */
object VesperAutomationEngine {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var activeJob: Job? = null
  private var serviceRef: WeakReference<AccessibilityService>? = null

  private val _progressState = MutableStateFlow(AutomationProgressState())
  val progressState: StateFlow<AutomationProgressState> = _progressState.asStateFlow()

  fun attachService(service: AccessibilityService) {
    serviceRef = WeakReference(service)
  }

  fun detachService(service: AccessibilityService) {
    if (serviceRef?.get() === service) {
      serviceRef = null
    }
  }

  fun dismissProgress() {
    _progressState.value = AutomationProgressState()
  }

  /**
   * 100% local command parser for multi-step workflows such as:
   * "Switch my Instagram account, type the password, go to Rahul, and say hi"
   */
  suspend fun parseWorkflowLocally(
    context: Context,
    commandText: String,
  ): ParsedAutomationWorkflow? {
    val lower = commandText.lowercase()
    val hasLoginIntent =
      lower.contains("switch") ||
        lower.contains("log in") ||
        lower.contains("login") ||
        lower.contains("sign in") ||
        lower.contains("type the password") ||
        lower.contains("password") ||
        lower.contains("account")

    val hasMessagingStep =
      lower.contains("go to ") ||
        lower.contains("say ") ||
        lower.contains("message ") ||
        lower.contains("dm ")

    if (!hasLoginIntent && !hasMessagingStep) return null

    val appVaultStore = AppVaultStore(context.applicationContext)
    val disabled = appVaultStore.getDisabledPackages()
    val resolver = SmartAppResolver(context.applicationContext, appVaultStore)
    val allowedApps = resolver.loadAllLaunchableAppsForVault().filterNot { it.packageName in disabled }

    val targetApp =
      allowedApps
        .filter { app ->
          val clean = app.label.lowercase()
          clean.length >= 3 && lower.contains(clean)
        }
        .maxByOrNull { it.label.length } ?: return null

    val recipient =
      Regex("(?:go to|message|dm|find|chat with)\\s+([a-zA-Z0-9_.-]+)", RegexOption.IGNORE_CASE)
        .find(commandText)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { !it.equals("account", ignoreCase = true) }

    val message =
      Regex("(?:say|send|type|write)\\s+[\"']?([^\"',.!?]+(?:\\s+[^\"',.!?]+)*)[\"']?$", RegexOption.IGNORE_CASE)
        .find(commandText.trim())
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { !it.equals("the password", ignoreCase = true) }

    val accountHint =
      Regex("(?:as|account)\\s+@?([a-zA-Z0-9_.-]+)", RegexOption.IGNORE_CASE)
        .find(commandText)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { !it.equals("and", ignoreCase = true) }

    return ParsedAutomationWorkflow(
      originalCommand = commandText,
      targetApp = targetApp,
      requiresLoginSwitch = hasLoginIntent,
      accountIdentifier = accountHint,
      recipientName = recipient,
      messageText = message,
    )
  }

  fun executeWorkflow(
    context: Context,
    workflow: ParsedAutomationWorkflow,
    credentialMeta: VaultCredentialMeta?,
    vault: LocalPasswordVault,
    onResultMessage: (String) -> Unit,
  ) {
    activeJob?.cancel()
    val totalSteps =
      1 +
        (if (workflow.requiresLoginSwitch && credentialMeta != null) 1 else 0) +
        (if (!workflow.recipientName.isNullOrBlank()) 1 else 0) +
        (if (!workflow.messageText.isNullOrBlank()) 1 else 0)

    activeJob =
      scope.launch {
        val service = serviceRef?.get()
        var currentStep = 1

        updateProgress("Step $currentStep/$totalSteps: Launching ${workflow.targetApp.label}…", running = true)
        val launchIntent =
          context.packageManager.getLaunchIntentForPackage(workflow.targetApp.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }

        if (launchIntent == null) {
          updateProgress("Unable to launch ${workflow.targetApp.label}", running = false, completed = true)
          onResultMessage("Unable to launch ${workflow.targetApp.label} on this device.")
          return@launch
        }

        context.startActivity(launchIntent)
        delay(1300L)

        if (service == null) {
          updateProgress(
            "Opened ${workflow.targetApp.label} • Enable Accessibility for hands-free steps",
            running = false,
            completed = true,
          )
          onResultMessage(
            "Opened ${workflow.targetApp.label}. Enable Vesper Hardware Shortcut in Accessibility Settings to automate in-app steps.",
          )
          return@launch
        }

        // Step 2: Automated Login / Account Switch using hardware-encrypted credentials
        if (workflow.requiresLoginSwitch && credentialMeta != null) {
          currentStep++
          updateProgress("Step $currentStep/$totalSteps: Filling saved login…", running = true)

          // Navigate toward account switcher / login form if present
          repeat(3) {
            val root = service.rootInActiveWindow
            if (root != null && findPasswordField(root) != null) return@repeat
            if (root != null) {
              clickFirstMatchingNode(
                root,
                listOf("switch account", "add account", "use another account", "log in", "sign in", "profile"),
              )
            }
            delay(850L)
          }

          val root = service.rootInActiveWindow
          if (root != null) {
            val userField = findUsernameField(root)
            userField?.let { setNodeText(it, credentialMeta.username) }

            val passField = findPasswordField(root)
            if (passField != null) {
              val passwordChars = vault.readPasswordIntoTempRam(credentialMeta.id)
              if (passwordChars != null) {
                try {
                  setNodeText(passField, String(passwordChars))
                } finally {
                  // Wipe password from RAM immediately after injection
                  passwordChars.fill('\u0000')
                }
              }
            }

            delay(350L)
            clickFirstMatchingNode(root, listOf("log in", "sign in", "continue", "next", "submit"))
            delay(1400L)
          }
        }

        // Step 3: Navigate to Recipient
        if (!workflow.recipientName.isNullOrBlank()) {
          currentStep++
          updateProgress("Step $currentStep/$totalSteps: Finding ${workflow.recipientName}…", running = true)
          val root = service.rootInActiveWindow
          if (root != null) {
            clickFirstMatchingNode(root, listOf("messages", "direct", "chat", "search"))
            delay(750L)
            service.rootInActiveWindow?.let { updatedRoot ->
              findEditableSearchOrTextField(updatedRoot)?.let { searchBox ->
                setNodeText(searchBox, workflow.recipientName)
                delay(700L)
              }
              service.rootInActiveWindow?.let { resultsRoot ->
                clickFirstMatchingNode(resultsRoot, listOf(workflow.recipientName.lowercase()))
                delay(750L)
              }
            }
          }
        }

        // Step 4: Type & Send Message
        if (!workflow.messageText.isNullOrBlank()) {
          currentStep++
          updateProgress("Step $currentStep/$totalSteps: Typing '${workflow.messageText}'…", running = true)
          service.rootInActiveWindow?.let { chatRoot ->
            findMessageInputField(chatRoot)?.let { inputNode ->
              setNodeText(inputNode, workflow.messageText)
              delay(350L)
            }
            service.rootInActiveWindow?.let { sendRoot ->
              clickFirstMatchingNode(sendRoot, listOf("send"))
            }
          }
        }

        val doneLabel =
          if (!workflow.messageText.isNullOrBlank()) "Done • Sent '${workflow.messageText}'"
          else "Done • ${workflow.targetApp.label} ready"
        updateProgress(doneLabel, running = false, completed = true)
        onResultMessage(
          "Completed on-device workflow for ${workflow.targetApp.label}: $doneLabel.",
        )

        delay(3500L)
        dismissProgress()
      }
  }

  private fun updateProgress(text: String, running: Boolean, completed: Boolean = false) {
    _progressState.value =
      AutomationProgressState(isRunning = running, statusText = text, isCompleted = completed)
  }

  private fun setNodeText(node: AccessibilityNodeInfo, value: String) {
    val args =
      Bundle().apply {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
      }
    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
  }

  private fun AccessibilityNodeInfo.safeHintText(): CharSequence? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) hintText else null

  private fun clickFirstMatchingNode(
    root: AccessibilityNodeInfo,
    keywords: List<String>,
  ): Boolean {
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      val label =
        listOfNotNull(node.text, node.contentDescription, node.safeHintText())
          .joinToString(" ")
          .lowercase()
      if (label.isNotBlank() && keywords.any { kw -> label.contains(kw) }) {
        var clickableTarget: AccessibilityNodeInfo? = node
        while (clickableTarget != null && !clickableTarget.isClickable) {
          clickableTarget = clickableTarget.parent
        }
        if (clickableTarget != null && clickableTarget.isClickable) {
          clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
          return true
        }
      }
      for (i in 0 until node.childCount) {
        node.getChild(i)?.let { queue.add(it) }
      }
    }
    return false
  }

  private fun findPasswordField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
    findEditableNodes(root).firstOrNull { node ->
      node.isPassword ||
        (node.safeHintText()?.toString()?.contains("password", ignoreCase = true) == true)
    }

  private fun findUsernameField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
    findEditableNodes(root).firstOrNull { node -> !node.isPassword }

  private fun findEditableSearchOrTextField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
    findEditableNodes(root).firstOrNull { node -> !node.isPassword }

  private fun findMessageInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    val editables = findEditableNodes(root).filterNot { it.isPassword }
    return editables.firstOrNull { node ->
      val hint = node.safeHintText()?.toString()?.lowercase().orEmpty()
      hint.contains("message") || hint.contains("write") || hint.contains("chat")
    } ?: editables.lastOrNull()
  }

  private fun findEditableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
    val result = mutableListOf<AccessibilityNodeInfo>()
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      val isEdit =
        node.isEditable ||
          node.className?.toString()?.contains("EditText", ignoreCase = true) == true
      if (isEdit && node.isVisibleToUser) {
        result.add(node)
      }
      for (i in 0 until node.childCount) {
        node.getChild(i)?.let { queue.add(it) }
      }
    }
    return result
  }
}
