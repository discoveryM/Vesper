package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FreeAiKeys
import com.example.ui.theme.VesperTokens
import com.example.ui.theme.capsuleHairlineBorder
import com.example.ui.theme.hairlineBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
  onDismiss: () -> Unit,
  onCameraClick: () -> Unit,
  onGalleryClick: () -> Unit,
  onDocumentClick: () -> Unit,
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
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
      Text(
        text = stringResource(R.string.sheet_attach_title),
        style = MaterialTheme.typography.titleMedium,
        color = tokens.textPrimary,
      )
      Spacer(modifier = Modifier.height(24.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SheetCircleOption(
          icon = Icons.Outlined.CameraAlt,
          label = stringResource(R.string.attach_camera),
          testTag = "attach_camera_option",
          onClick = {
            onDismiss()
            onCameraClick()
          },
        )
        SheetCircleOption(
          icon = Icons.Outlined.PhotoLibrary,
          label = stringResource(R.string.attach_gallery),
          testTag = "attach_gallery_option",
          onClick = {
            onDismiss()
            onGalleryClick()
          },
        )
        SheetCircleOption(
          icon = Icons.Outlined.Description,
          label = stringResource(R.string.attach_document),
          testTag = "attach_document_option",
          onClick = {
            onDismiss()
            onDocumentClick()
          },
        )
      }
      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

@Composable
private fun SheetCircleOption(
  icon: ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit,
) {
  val tokens = VesperTokens.colors
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier =
      Modifier
        .clip(RoundedCornerShape(16.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(8.dp)
        .testTag(testTag),
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
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tokens.textPrimary,
        modifier = Modifier.size(22.dp),
      )
    }
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeAiKeysSheet(
  currentKeys: FreeAiKeys,
  onSave: (String, String) -> Unit,
  onClear: () -> Unit,
  onDismiss: () -> Unit,
) {
  val tokens = VesperTokens.colors
  var geminiInput by remember(currentKeys) { mutableStateOf(currentKeys.geminiKey) }
  var openRouterInput by remember(currentKeys) { mutableStateOf(currentKeys.openRouterOrGroqKey) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = tokens.elevatedSurface,
    contentColor = tokens.textPrimary,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Key,
            contentDescription = null,
            tint = tokens.accentSilverBlue,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = stringResource(R.string.keys_title),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
          )
        }
        if (currentKeys.hasAnyKey) {
          Box(
            modifier =
              Modifier
                .clip(CircleShape)
                .background(tokens.userBubble)
                .hairlineBorder(CircleShape, tokens.hairlineBorder)
                .padding(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Text(
              text = stringResource(R.string.keys_saved_badge),
              style = MaterialTheme.typography.bodySmall,
              color = tokens.statusGreen,
            )
          }
        }
      }

      Text(
        text = stringResource(R.string.keys_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = tokens.textSecondary,
      )

      HorizontalDivider(color = tokens.hairlineBorder)

      EncryptedKeyInputField(
        label = stringResource(R.string.keys_gemini_label),
        placeholder = stringResource(R.string.keys_gemini_hint),
        value = geminiInput,
        onValueChange = { geminiInput = it },
        testTag = "gemini_key_input",
      )

      EncryptedKeyInputField(
        label = stringResource(R.string.keys_openrouter_label),
        placeholder = stringResource(R.string.keys_openrouter_hint),
        value = openRouterInput,
        onValueChange = { openRouterInput = it },
        testTag = "openrouter_key_input",
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (currentKeys.hasAnyKey) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(tokens.userBubble)
                .hairlineBorder(CircleShape, tokens.capsuleBorder)
                .clickable(role = Role.Button) {
                  geminiInput = ""
                  openRouterInput = ""
                  onClear()
                  onDismiss()
                }
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .testTag("clear_keys_button"),
          ) {
            Text(
              text = stringResource(R.string.keys_clear),
              style = MaterialTheme.typography.labelLarge,
              color = tokens.textSecondary,
            )
          }
        }

        Button(
          onClick = {
            onSave(geminiInput, openRouterInput)
            onDismiss()
          },
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = tokens.textPrimary,
              contentColor = tokens.canvasBackground,
            ),
          modifier = Modifier.weight(1f).height(48.dp).testTag("save_keys_button"),
        ) {
          Text(
            text = stringResource(R.string.keys_save),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun EncryptedKeyInputField(
  label: String,
  placeholder: String,
  value: String,
  onValueChange: (String) -> Unit,
  testTag: String,
) {
  val tokens = VesperTokens.colors
  var reveal by remember { mutableStateOf(false) }
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
          Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textMuted,
          )
        }
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          visualTransformation =
            if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.textPrimary),
          cursorBrush = SolidColor(tokens.accentSilverBlue),
          modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
      }
      IconButton(onClick = { reveal = !reveal }, modifier = Modifier.size(40.dp)) {
        Icon(
          imageVector = if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
          contentDescription = null,
          tint = tokens.textSecondary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
