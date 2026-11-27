package com.example.resolver

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.example.data.AppVaultStore
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class InstalledAppInfo(
  val packageName: String,
  val label: String,
  val icon: ImageBitmap? = null,
  val isSystemApp: Boolean = false,
)

@Immutable
data class AttachmentFile(
  val uri: Uri,
  val displayName: String,
  val mimeType: String,
)

sealed interface AppResolutionResult {
  data class Launched(val app: InstalledAppInfo, val summary: String) : AppResolutionResult

  data class ChooseApp(
    val taskKey: String,
    val intentAction: String,
    val attachment: AttachmentFile?,
    val commandText: String,
    val candidates: List<InstalledAppInfo>,
  ) : AppResolutionResult

  data object NotAnAppIntent : AppResolutionResult
}

/**
 * 100% on-device app & task resolver powered by PackageManager (<queries>) and AppVaultStore.
 * Never uploads installed app lists or local file URIs to any cloud endpoint.
 */
class SmartAppResolver(
  private val context: Context,
  private val appVaultStore: AppVaultStore,
) {
  private val pm: PackageManager = context.packageManager

  suspend fun loadAllLaunchableAppsForVault(): List<InstalledAppInfo> =
    withContext(Dispatchers.IO) {
      val launcherIntent =
        Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
      val resolveList = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
      resolveList
        .mapNotNull { info ->
          val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
          if (pkg == context.packageName) return@mapNotNull null
          val appInfo = info.activityInfo.applicationInfo
          val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
          val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
          if (label.isEmpty()) return@mapNotNull null
          InstalledAppInfo(
            packageName = pkg,
            label = label,
            icon = info.loadIcon(pm)?.toImageBitmap(),
            isSystemApp = isSystem,
          )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<InstalledAppInfo> { it.isSystemApp }.thenBy { it.label.lowercase() })
    }

  suspend fun resolveAndExecute(
    commandText: String,
    attachment: AttachmentFile?,
  ): AppResolutionResult =
    withContext(Dispatchers.Default) {
      val disabledPackages = appVaultStore.getDisabledPackages()
      val allowedApps =
        loadAllLaunchableAppsForVault().filterNot { it.packageName in disabledPackages }

      // Step A: Natural App Name & Fuzzy Matching
      val explicitMatch = findBestAppNameMatch(commandText, allowedApps)
      if (explicitMatch != null) {
        val action = inferIntentAction(commandText, attachment)
        val launched = executeHandoff(explicitMatch, action, attachment, commandText)
        if (launched) {
          return@withContext AppResolutionResult.Launched(
            app = explicitMatch,
            summary = buildLaunchSummary(explicitMatch, action, attachment),
          )
        }
      }

      // Step B: Smart Intent & Capability Discovery (when no app name is spoken)
      val inferredTask = inferTaskCapability(commandText, attachment)
      if (inferredTask != null) {
        val candidates =
          queryCapableApps(
            action = inferredTask.intentAction,
            mimeType = inferredTask.mimeType,
            disabledPackages = disabledPackages,
            allowedAppsByPkg = allowedApps.associateBy { it.packageName },
          )

        if (candidates.isNotEmpty()) {
          // Check remembered preference first
          val rememberedPkg = appVaultStore.getRememberedChoice(inferredTask.taskKey)
          val rememberedApp = candidates.firstOrNull { it.packageName == rememberedPkg }
          if (rememberedApp != null && executeHandoff(rememberedApp, inferredTask.intentAction, attachment, commandText)) {
            return@withContext AppResolutionResult.Launched(
              app = rememberedApp,
              summary = buildLaunchSummary(rememberedApp, inferredTask.intentAction, attachment),
            )
          }

          // Step C: Auto-launch if single clear match, else return minimalist selector card
          if (candidates.size == 1) {
            val single = candidates.first()
            if (executeHandoff(single, inferredTask.intentAction, attachment, commandText)) {
              return@withContext AppResolutionResult.Launched(
                app = single,
                summary = buildLaunchSummary(single, inferredTask.intentAction, attachment),
              )
            }
          }

          return@withContext AppResolutionResult.ChooseApp(
            taskKey = inferredTask.taskKey,
            intentAction = inferredTask.intentAction,
            attachment = attachment,
            commandText = commandText,
            candidates = candidates.take(8),
          )
        }
      }

      AppResolutionResult.NotAnAppIntent
    }

  suspend fun launchChosenApp(
    app: InstalledAppInfo,
    taskKey: String,
    intentAction: String,
    attachment: AttachmentFile?,
    commandText: String,
  ): Boolean {
    appVaultStore.saveRememberedChoice(taskKey, app.packageName)
    return withContext(Dispatchers.Main) {
      executeHandoff(app, intentAction, attachment, commandText)
    }
  }

  private fun executeHandoff(
    app: InstalledAppInfo,
    action: String,
    attachment: AttachmentFile?,
    commandText: String,
  ): Boolean {
    return runCatching {
      val intent =
        if (attachment != null) {
          when (action) {
            Intent.ACTION_SEND ->
              Intent(Intent.ACTION_SEND).apply {
                type = attachment.mimeType
                putExtra(Intent.EXTRA_STREAM, attachment.uri)
                putExtra(Intent.EXTRA_TEXT, commandText)
                setPackage(app.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
            Intent.ACTION_EDIT ->
              Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(attachment.uri, attachment.mimeType)
                setPackage(app.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
            else ->
              Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(attachment.uri, attachment.mimeType)
                setPackage(app.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
          }
        } else {
          pm.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
              addCategory(Intent.CATEGORY_LAUNCHER)
              setPackage(app.packageName)
            }
        }

      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

      // Verify target handles the constructed URI intent; gracefully fall back to app launcher
      if (attachment != null && intent.resolveActivity(pm) == null) {
        val fallback = pm.getLaunchIntentForPackage(app.packageName)?.apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        context.startActivity(fallback)
      } else {
        context.startActivity(intent)
      }
      true
    }.getOrDefault(false)
  }

  private fun findBestAppNameMatch(
    command: String,
    apps: List<InstalledAppInfo>,
  ): InstalledAppInfo? {
    val cleanCommand = command.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
    if (cleanCommand.isBlank()) return null
    val commandWords = cleanCommand.split(Regex("\\s+")).filter { it.length >= 3 }

    // 1. Exact phrase / substring match on app label (prefer longer, more specific app names)
    val exactMatches =
      apps
        .filter { app ->
          val labelNorm = app.label.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
          labelNorm.length >= 3 &&
            (cleanCommand.contains(" $labelNorm ") ||
              cleanCommand.startsWith("$labelNorm ") ||
              cleanCommand.endsWith(" $labelNorm") ||
              cleanCommand == labelNorm)
        }
        .sortedByDescending { it.label.length }

    if (exactMatches.isNotEmpty()) return exactMatches.first()

    // 2. Fuzzy typo tolerance against app label tokens
    var bestMatch: InstalledAppInfo? = null
    var bestScore = Int.MAX_VALUE

    for (app in apps) {
      val labelTokens =
        app.label.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim().split(Regex("\\s+"))
      for (token in labelTokens) {
        if (token.length < 4) continue
        for (word in commandWords) {
          if (word.length < 4) continue
          val maxAllowed = if (token.length >= 6) 2 else 1
          val dist = levenshtein(word, token)
          if (dist <= maxAllowed && dist < bestScore) {
            bestScore = dist
            bestMatch = app
          }
        }
      }
    }
    return bestMatch
  }

  private data class InferredTask(
    val taskKey: String,
    val intentAction: String,
    val mimeType: String,
  )

  private fun inferIntentAction(command: String, attachment: AttachmentFile?): String {
    val lower = command.lowercase()
    return when {
      lower.contains("edit") || lower.contains("crop") || lower.contains("filter") ||
        lower.contains("retouch") -> Intent.ACTION_EDIT
      lower.contains("send") || lower.contains("share") || lower.contains("message") ||
        lower.contains("dm") -> Intent.ACTION_SEND
      attachment != null -> Intent.ACTION_VIEW
      else -> Intent.ACTION_MAIN
    }
  }

  private fun inferTaskCapability(command: String, attachment: AttachmentFile?): InferredTask? {
    val lower = command.lowercase()
    val hasAttachment = attachment != null

    val isEdit =
      lower.contains("edit") || lower.contains("crop") || lower.contains("filter") ||
        lower.contains("enhance")
    val isSend =
      lower.contains("send") || lower.contains("share") || lower.contains("message") ||
        lower.contains("text someone") || lower.contains("chat")
    val isViewOrOpen =
      lower.contains("open") || lower.contains("view") || lower.contains("read") ||
        lower.contains("compress") || lower.contains("play")

    if (hasAttachment) {
      val mime = attachment!!.mimeType.ifBlank { "*/*" }
      val action =
        when {
          isEdit -> Intent.ACTION_EDIT
          isSend -> Intent.ACTION_SEND
          else -> Intent.ACTION_VIEW
        }
      val normalizedMime =
        when {
          mime.startsWith("image/") -> "image/*"
          mime == "application/pdf" -> "application/pdf"
          mime.startsWith("video/") -> "video/*"
          mime.startsWith("text/") -> "text/plain"
          else -> mime
        }
      return InferredTask("${action}_$normalizedMime", action, normalizedMime)
    }

    return when {
      isEdit && (lower.contains("photo") || lower.contains("image") || lower.contains("picture")) ->
        InferredTask("edit_image/*", Intent.ACTION_EDIT, "image/*")
      isViewOrOpen && lower.contains("pdf") ->
        InferredTask("view_application/pdf", Intent.ACTION_VIEW, "application/pdf")
      isSend && (lower.contains("message") || lower.contains("someone") || lower.contains("text")) ->
        InferredTask("send_text/plain", Intent.ACTION_SEND, "text/plain")
      isViewOrOpen && (lower.contains("music") || lower.contains("song") || lower.contains("audio")) ->
        InferredTask("view_audio/*", Intent.ACTION_VIEW, "audio/*")
      else -> null
    }
  }

  private fun queryCapableApps(
    action: String,
    mimeType: String,
    disabledPackages: Set<String>,
    allowedAppsByPkg: Map<String, InstalledAppInfo>,
  ): List<InstalledAppInfo> {
    val queryIntent =
      if (action == Intent.ACTION_SEND) {
        Intent(Intent.ACTION_SEND).apply { type = mimeType }
      } else {
        Intent(action).apply { setDataAndType(Uri.parse("content://vesper.local/item"), mimeType) }
      }

    val resolved: List<ResolveInfo> =
      pm.queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY)

    return resolved
      .mapNotNull { info ->
        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
        if (pkg == context.packageName || pkg in disabledPackages) return@mapNotNull null
        allowedAppsByPkg[pkg] ?: InstalledAppInfo(
          packageName = pkg,
          label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg },
          icon = info.loadIcon(pm)?.toImageBitmap(),
        )
      }
      .distinctBy { it.packageName }
      .sortedBy { it.label.lowercase() }
  }

  private fun buildLaunchSummary(
    app: InstalledAppInfo,
    action: String,
    attachment: AttachmentFile?,
  ): String {
    if (attachment == null) {
      return "Opened ${app.label} locally on-device."
    }
    val verb =
      when (action) {
        Intent.ACTION_EDIT -> "Handed off ${attachment.displayName} to ${app.label} for editing"
        Intent.ACTION_SEND -> "Prepared ${attachment.displayName} in ${app.label} to share"
        else -> "Opened ${attachment.displayName} in ${app.label}"
      }
    return "$verb with scoped read-only URI permission."
  }

  private fun levenshtein(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
      var prev = dp[0]
      dp[0] = i
      for (j in 1..b.length) {
        val temp = dp[j]
        dp[j] =
          if (a[i - 1] == b[j - 1]) prev
          else 1 + minOf(prev, dp[j], dp[j - 1])
        prev = temp
      }
    }
    return dp[b.length]
  }

  private fun Drawable.toImageBitmap(): ImageBitmap? {
    return runCatching {
      if (this is BitmapDrawable && bitmap != null) {
        bitmap.asImageBitmap()
      } else {
        val w = intrinsicWidth.coerceIn(48, 128)
        val h = intrinsicHeight.coerceIn(48, 128)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp.asImageBitmap()
      }
    }.getOrNull()
  }

  companion object {
    fun saveBitmapForHandoff(context: Context, bitmap: Bitmap): AttachmentFile? {
      return runCatching {
        val dir = File(context.cacheDir, "vesper_captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val uri =
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        AttachmentFile(
          uri = uri,
          displayName = file.name,
          mimeType = "image/jpeg",
        )
      }.getOrNull()
    }

    fun resolveAttachmentMetadata(context: Context, uri: Uri, fallbackMime: String): AttachmentFile {
      val mime = context.contentResolver.getType(uri) ?: fallbackMime
      var name = "Attached File"
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
              name = cursor.getString(idx)?.takeIf { it.isNotBlank() } ?: name
            }
          }
        }
      return AttachmentFile(uri = uri, displayName = name, mimeType = mime)
    }
  }
}
