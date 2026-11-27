package com.example.vault

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.resolver.InstalledAppInfo
import com.example.ui.theme.VesperTokens
import com.example.ui.theme.capsuleHairlineBorder
import com.example.ui.theme.hairlineBorder

/**
 * Phase 5 First-Time "Save [App Name] Login Once" capture bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveOnceCredentialSheet(
  appLabel: String,
  initialUsername: String = "",
  onSaveAndContinue: (username: String, passwordChars: CharArray) -> Unit,
  onDismiss: () -> Unit,
) {
  val tokens = VesperTokens.colors
  var username by remember(initialUsername) { mutableStateOf(initialUsername) }
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = tokens.elevatedSurface,
    contentColor = tokens.textPrimary,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 14.dp)
          .testTag("save_once_sheet"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Lock,
          contentDescription = null,
          tint = tokens.accentSilverBlue,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = stringResource(R.string.save_once_title, appLabel),
          style = MaterialTheme.typography.titleMedium,
          color = tokens.textPrimary,
        )
      }

      // Security Chip Reassurance Badge
      Surface(
        color = tokens.canvasBackground.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        modifier =
          Modifier
            .fillMaxWidth()
            .hairlineBorder(RoundedCornerShape(14.dp), tokens.hairlineBorder),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          Box(
            modifier = Modifier.size(6.dp).clip(CircleShape).background(tokens.statusGreen),
          )
          Text(
            text = stringResource(R.string.save_once_badge),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
          )
        }
      }

      VaultTextInput(
        label = stringResource(R.string.save_once_username_label),
        placeholder = stringResource(R.string.save_once_username_hint),
        value = username,
        onValueChange = { username = it },
        isPassword = false,
        reveal = true,
        onToggleReveal = {},
        testTag = "save_once_username",
      )

      VaultTextInput(
        label = stringResource(R.string.save_once_password_label),
        placeholder = stringResource(R.string.save_once_password_hint),
        value = password,
        onValueChange = { password = it },
        isPassword = true,
        reveal = showPassword,
        onToggleReveal = { showPassword = !showPassword },
        testTag = "save_once_password",
      )

      Button(
        onClick = {
          val chars = password.toCharArray()
          password = ""
          onSaveAndContinue(username.trim(), chars)
        },
        enabled = username.isNotBlank() && password.isNotEmpty(),
        shape = CircleShape,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = tokens.textPrimary,
            contentColor = tokens.canvasBackground,
            disabledContainerColor = tokens.userBubble,
            disabledContentColor = tokens.textMuted,
          ),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_once_submit"),
      ) {
        Text(
          text = stringResource(R.string.save_once_button),
          style = MaterialTheme.typography.labelLarge,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

private enum class VaultTab {
  CONNECTED_APPS,
  SAVED_LOGINS,
}

/**
 * Combined Vault & Connected Apps Manager:
 * - Tab 1: Searchable installed user apps with opt-out toggles (Phase 2)
 * - Tab 2: Biometric-gated Saved Logins Vault Manager (Phase 5)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHubBottomSheet(
  apps: List<InstalledAppInfo>,
  disabledPackages: Set<String>,
  isLoadingApps: Boolean,
  credentials: List<VaultCredentialMeta>,
  biometricLockEnabled: Boolean,
  onLoadApps: () -> Unit,
  onToggleApp: (String, Boolean) -> Unit,
  onSaveCredential: (InstalledAppInfo, String, CharArray) -> Unit,
  onDeleteCredential: (String) -> Unit,
  onReadPasswordTemp: (String) -> CharArray?,
  onToggleBiometricLock: (Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val tokens = VesperTokens.colors
  val activity = LocalContext.current as? FragmentActivity
  var activeTab by remember { mutableStateOf(VaultTab.CONNECTED_APPS) }
  var searchQuery by remember { mutableStateOf("") }
  var revealedCredId by remember { mutableStateOf<String?>(null) }
  var revealedPasswordText by remember { mutableStateOf<String?>(null) }
  var addLoginTargetApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
  var biometricError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) { onLoadApps() }

  val filteredApps =
    remember(apps, searchQuery) {
      val q = searchQuery.trim().lowercase()
      if (q.isEmpty()) apps
      else apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }

  fun runBiometricGate(title: String, subtitle: String, action: () -> Unit) {
    if (activity == null) {
      action()
      return
    }
    LocalPasswordVault.authenticateWithBiometrics(
      activity = activity,
      title = title,
      subtitle = subtitle,
      onSuccess = {
        biometricError = null
        action()
      },
      onError = { err -> biometricError = err },
    )
  }

  ModalBottomSheet(
    onDismissRequest = {
      revealedPasswordText = null
      revealedCredId = null
      onDismiss()
    },
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = tokens.elevatedSurface,
    contentColor = tokens.textPrimary,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.88f)
          .navigationBarsPadding()
          .padding(horizontal = 20.dp),
    ) {
      // Segmented Minimalist Tabs
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(tokens.canvasBackground.copy(alpha = 0.6f))
            .hairlineBorder(CircleShape, tokens.hairlineBorder)
            .padding(4.dp),
      ) {
        VaultSegmentTab(
          label = stringResource(R.string.vault_tab_connected_apps),
          selected = activeTab == VaultTab.CONNECTED_APPS,
          onClick = { activeTab = VaultTab.CONNECTED_APPS },
          modifier = Modifier.weight(1f).testTag("vault_tab_apps"),
        )
        VaultSegmentTab(
          label = "${stringResource(R.string.vault_tab_saved_logins)} (${credentials.size})",
          selected = activeTab == VaultTab.SAVED_LOGINS,
          onClick = { activeTab = VaultTab.SAVED_LOGINS },
          modifier = Modifier.weight(1f).testTag("vault_tab_logins"),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (activeTab == VaultTab.CONNECTED_APPS) {
        Text(
          text = stringResource(R.string.vault_apps_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = tokens.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        val searchShape = RoundedCornerShape(16.dp)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(searchShape)
              .background(tokens.userBubble)
              .hairlineBorder(searchShape, tokens.capsuleBorder)
              .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = tokens.textSecondary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Box(modifier = Modifier.weight(1f)) {
            if (searchQuery.isEmpty()) {
              Text(
                text = stringResource(R.string.vault_apps_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
              )
            }
            BasicTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.textPrimary),
              cursorBrush = SolidColor(tokens.accentSilverBlue),
              modifier = Modifier.fillMaxWidth().testTag("vault_apps_search"),
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = tokens.hairlineBorder)

        when {
          isLoadingApps -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().weight(1f)) {
              CircularProgressIndicator(color = tokens.accentSilverBlue, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
          }
          filteredApps.isEmpty() -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().weight(1f)) {
              Text(text = stringResource(R.string.vault_apps_empty), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
            }
          }
          else -> {
            LazyColumn(
              modifier = Modifier.weight(1f),
              contentPadding = PaddingValues(vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filteredApps, key = { it.packageName }) { app ->
                val isEnabled = app.packageName !in disabledPackages
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(14.dp))
                      .background(tokens.canvasBackground.copy(alpha = 0.45f))
                      .hairlineBorder(RoundedCornerShape(14.dp), tokens.hairlineBorder)
                      .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                  AppIconThumb(app)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(text = app.label, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                  Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked -> onToggleApp(app.packageName, checked) },
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
            }
          }
        }
      } else {
        // Saved Logins Vault Manager
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(tokens.canvasBackground.copy(alpha = 0.45f))
              .hairlineBorder(RoundedCornerShape(14.dp), tokens.hairlineBorder)
              .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Fingerprint,
            contentDescription = null,
            tint = tokens.accentSilverBlue,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = stringResource(R.string.vault_biometric_lock_label), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
            Text(text = stringResource(R.string.vault_biometric_lock_desc), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
          }
          Switch(
            checked = biometricLockEnabled,
            onCheckedChange = onToggleBiometricLock,
            colors =
              SwitchDefaults.colors(
                checkedThumbColor = tokens.canvasBackground,
                checkedTrackColor = tokens.accentSilverBlue,
                uncheckedThumbColor = tokens.textSecondary,
                uncheckedTrackColor = tokens.userBubble,
              ),
          )
        }

        if (biometricError != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = biometricError.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.accentSilverBlue,
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Add New Login Pill
        val defaultAddApp =
          apps.firstOrNull { !it.isSystemApp }
            ?: apps.firstOrNull()
            ?: InstalledAppInfo("com.example.app", "Custom App")

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(tokens.userBubble)
              .hairlineBorder(CircleShape, tokens.capsuleBorder)
              .clickable(role = Role.Button) { addLoginTargetApp = defaultAddApp }
              .padding(vertical = 11.dp)
              .testTag("vault_add_login_button"),
        ) {
          Icon(imageVector = Icons.Outlined.Add, contentDescription = null, tint = tokens.textPrimary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(R.string.vault_add_login), style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = tokens.hairlineBorder)

        if (credentials.isEmpty()) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
              text = stringResource(R.string.vault_empty_logins),
              style = MaterialTheme.typography.bodyMedium,
              color = tokens.textSecondary,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(credentials, key = { it.id }) { cred ->
              val isRevealed = revealedCredId == cred.id && revealedPasswordText != null
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.canvasBackground.copy(alpha = 0.45f))
                    .hairlineBorder(RoundedCornerShape(14.dp), tokens.hairlineBorder)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(text = cred.appLabel, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                  Text(text = cred.username, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                  Text(
                    text = if (isRevealed) revealedPasswordText.orEmpty() else "••••••••••••",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRevealed) tokens.accentSilverBlue else tokens.textMuted,
                    modifier = Modifier.padding(top = 4.dp),
                  )
                }

                IconButton(
                  onClick = {
                    if (isRevealed) {
                      revealedCredId = null
                      revealedPasswordText = null
                    } else {
                      runBiometricGate(
                        title = "Unlock ${cred.appLabel} Password",
                        subtitle = "Verify identity to reveal encrypted credential",
                      ) {
                        val chars = onReadPasswordTemp(cred.id)
                        if (chars != null) {
                          revealedCredId = cred.id
                          revealedPasswordText = String(chars)
                          chars.fill('\u0000')
                        }
                      }
                    }
                  },
                ) {
                  Icon(
                    imageVector = if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = "Reveal password",
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(18.dp),
                  )
                }

                IconButton(
                  onClick = {
                    runBiometricGate(
                      title = "Delete ${cred.appLabel} Login",
                      subtitle = "Verify identity to remove saved credential",
                    ) {
                      if (revealedCredId == cred.id) {
                        revealedCredId = null
                        revealedPasswordText = null
                      }
                      onDeleteCredential(cred.id)
                    }
                  },
                ) {
                  Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete credential",
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  addLoginTargetApp?.let { target ->
    SaveOnceCredentialSheet(
      appLabel = target.label,
      onSaveAndContinue = { user, passChars ->
        onSaveCredential(target, user, passChars)
        addLoginTargetApp = null
      },
      onDismiss = { addLoginTargetApp = null },
    )
  }
}

@Composable
private fun VaultSegmentTab(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = VesperTokens.colors
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(CircleShape)
        .background(if (selected) tokens.userBubble else Color.Transparent)
        .clickable(role = Role.Tab, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = if (selected) tokens.textPrimary else tokens.textSecondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun AppIconThumb(app: InstalledAppInfo) {
  val tokens = VesperTokens.colors
  if (app.icon != null) {
    Image(
      bitmap = app.icon,
      contentDescription = app.label,
      modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)),
    )
  } else {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(tokens.userBubble),
    ) {
      Icon(
        imageVector = Icons.Outlined.Apps,
        contentDescription = null,
        tint = tokens.textSecondary,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
private fun VaultTextInput(
  label: String,
  placeholder: String,
  value: String,
  onValueChange: (String) -> Unit,
  isPassword: Boolean,
  reveal: Boolean,
  onToggleReveal: () -> Unit,
  testTag: String,
) {
  val tokens = VesperTokens.colors
  val shape = RoundedCornerShape(14.dp)

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(shape)
          .background(tokens.userBubble)
          .capsuleHairlineBorder(shape, isFocused = value.isNotEmpty())
          .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
      Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
        if (value.isEmpty()) {
          Text(text = placeholder, style = MaterialTheme.typography.bodyMedium, color = tokens.textMuted)
        }
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          visualTransformation =
            if (isPassword && !reveal) PasswordVisualTransformation()
            else VisualTransformation.None,
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.textPrimary),
          cursorBrush = SolidColor(tokens.accentSilverBlue),
          modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
      }
      if (isPassword) {
        IconButton(onClick = onToggleReveal, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = stringResource(R.string.vault_toggle_password_visibility),
            tint = tokens.textSecondary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}
