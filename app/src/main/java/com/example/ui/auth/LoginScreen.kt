package com.example.ui.auth

import android.content.Context
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.ui.theme.VesperTheme
import com.example.ui.theme.VesperTokens
import com.example.ui.theme.hairlineBorder
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Replace with your Google Cloud OAuth 2.0 Web Client ID to enable live Google Sign-In.
const val WEB_CLIENT_ID = "YOUR_GOOGLE_WEB_CLIENT_ID"

private val Context.vesperSessionDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "vesper_user_session")

@Immutable
data class UserProfile(
  val firstName: String,
  val email: String? = null,
  val profilePictureUri: String? = null,
  val isGuest: Boolean = false,
)

class UserSessionStore(private val context: Context) {
  private object Keys {
    val IS_SIGNED_IN = booleanPreferencesKey("is_signed_in")
    val FIRST_NAME = stringPreferencesKey("first_name")
    val EMAIL = stringPreferencesKey("email")
    val PROFILE_URI = stringPreferencesKey("profile_picture_uri")
    val IS_GUEST = booleanPreferencesKey("is_guest")
  }

  val userProfileFlow: Flow<UserProfile?> =
    context.vesperSessionDataStore.data.map { prefs ->
      if (prefs[Keys.IS_SIGNED_IN] != true) {
        null
      } else {
        UserProfile(
          firstName = prefs[Keys.FIRST_NAME].orEmpty().ifBlank { "Guest" },
          email = prefs[Keys.EMAIL],
          profilePictureUri = prefs[Keys.PROFILE_URI],
          isGuest = prefs[Keys.IS_GUEST] == true,
        )
      }
    }

  suspend fun saveProfile(profile: UserProfile) {
    context.vesperSessionDataStore.edit { prefs ->
      prefs[Keys.IS_SIGNED_IN] = true
      prefs[Keys.FIRST_NAME] = profile.firstName
      if (profile.email != null) prefs[Keys.EMAIL] = profile.email else prefs.remove(Keys.EMAIL)
      if (profile.profilePictureUri != null) {
        prefs[Keys.PROFILE_URI] = profile.profilePictureUri
      } else {
        prefs.remove(Keys.PROFILE_URI)
      }
      prefs[Keys.IS_GUEST] = profile.isGuest
    }
  }

  suspend fun clearSession() {
    runCatching {
      CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
    context.vesperSessionDataStore.edit { it.clear() }
  }
}

@Composable
fun LoginScreen(
  onSignInSuccess: (UserProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = VesperTokens.colors
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val sessionStore = remember(context) { UserSessionStore(context.applicationContext) }

  var isAuthenticating by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var contentVisible by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { contentVisible = true }

  fun handleGoogleSignIn() {
    if (isAuthenticating) return
    errorMessage = null

    if (WEB_CLIENT_ID == "YOUR_GOOGLE_WEB_CLIENT_ID" || WEB_CLIENT_ID.isBlank()) {
      errorMessage = context.getString(R.string.login_web_client_id_hint)
      return
    }

    isAuthenticating = true
    coroutineScope.launch {
      try {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption =
          GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setNonce(generateSecureRandomNonce())
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        val result =
          credentialManager.getCredential(
            context = MutableContextWrapper(context),
            request = request,
          )

        val credential = result.credential
        if (
          credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
          val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
          val firstName =
            googleIdCredential.givenName?.takeIf { it.isNotBlank() }
              ?: googleIdCredential.displayName
                ?.trim()
                ?.substringBefore(" ")
                ?.takeIf { it.isNotBlank() }
              ?: "Friend"

          val profile =
            UserProfile(
              firstName = firstName,
              email = googleIdCredential.id,
              profilePictureUri = googleIdCredential.profilePictureUri?.toString(),
              isGuest = false,
            )
          sessionStore.saveProfile(profile)
          onSignInSuccess(profile)
        } else {
          errorMessage = "Unsupported credential type returned."
        }
      } catch (_: GetCredentialCancellationException) {
        errorMessage = "Sign-in cancelled."
      } catch (_: NoCredentialException) {
        errorMessage = "No Google account available on this device."
      } catch (e: GoogleIdTokenParsingException) {
        errorMessage = e.localizedMessage ?: "Unable to parse Google ID token."
      } catch (e: GetCredentialException) {
        errorMessage = e.localizedMessage ?: "Google authentication failed."
      } finally {
        isAuthenticating = false
      }
    }
  }

  fun handleGuestSignIn() {
    if (isAuthenticating) return
    isAuthenticating = true
    coroutineScope.launch {
      val guestProfile = UserProfile(firstName = "Guest", email = null, isGuest = true)
      sessionStore.saveProfile(guestProfile)
      isAuthenticating = false
      onSignInSuccess(guestProfile)
    }
  }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(tokens.canvasBackground)
        .background(
          Brush.radialGradient(
            colors =
              listOf(
                tokens.accentSilverBlue.copy(alpha = 0.06f),
                Color.Transparent,
              ),
            radius = 900f,
          ),
        )
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp),
    contentAlignment = Alignment.Center,
  ) {
    AnimatedVisibility(
      visible = contentVisible,
      enter =
        fadeIn(tween(420, easing = FastOutSlowInEasing)) +
          slideInVertically(
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            initialOffsetY = { 24 },
          ),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
      ) {
        Spacer(modifier = Modifier.weight(1f))

        // Clean geometric monochrome Vesper emblem
        VesperMonochromeEmblem()

        Spacer(modifier = Modifier.height(28.dp))

        Text(
          text = stringResource(R.string.login_title),
          style = MaterialTheme.typography.displaySmall,
          color = tokens.textPrimary,
          modifier = Modifier.testTag("login_title"),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = stringResource(R.string.login_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = tokens.textSecondary,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("login_subtitle"),
        )

        Spacer(modifier = Modifier.weight(1.15f))

        // Inline subtle feedback pill when needed
        AnimatedVisibility(
          visible = errorMessage != null,
          enter = fadeIn(tween(180)),
          exit = fadeOut(tween(150)),
        ) {
          errorMessage?.let { message ->
            Surface(
              color = tokens.elevatedSurface,
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(bottom = 16.dp)
                  .hairlineBorder(RoundedCornerShape(14.dp), tokens.capsuleBorder)
                  .testTag("login_error_banner"),
            ) {
              Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
              )
            }
          }
        }

        // Sleek Pill-Shaped "Continue with Google" Button (#F5F5F7 fill, #0A0A0C text)
        Button(
          onClick = ::handleGoogleSignIn,
          enabled = !isAuthenticating,
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = tokens.textPrimary,
              contentColor = tokens.canvasBackground,
              disabledContainerColor = tokens.textPrimary.copy(alpha = 0.72f),
              disabledContentColor = tokens.canvasBackground,
            ),
          elevation = null,
          modifier =
            Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("google_sign_in_button"),
        ) {
          if (isAuthenticating) {
            CircularProgressIndicator(
              color = tokens.canvasBackground,
              strokeWidth = 2.dp,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.login_signing_in),
              style = MaterialTheme.typography.labelLarge,
            )
          } else {
            GoogleGVectorIcon(modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.login_continue_google),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary subtle text link: "Continue as Guest"
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .clickable(enabled = !isAuthenticating, role = Role.Button) { handleGuestSignIn() }
              .padding(horizontal = 20.dp, vertical = 12.dp)
              .testTag("guest_sign_in_button"),
        ) {
          Text(
            text = stringResource(R.string.login_continue_guest),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary,
          )
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun VesperMonochromeEmblem() {
  val tokens = VesperTokens.colors
  val shape = RoundedCornerShape(22.dp)
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .size(68.dp)
        .clip(shape)
        .background(tokens.elevatedSurface)
        .hairlineBorder(shape = shape, color = tokens.capsuleBorder),
  ) {
    Canvas(modifier = Modifier.size(30.dp)) {
      val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
      // Outer crisp ring
      drawCircle(
        color = tokens.textPrimary.copy(alpha = 0.22f),
        radius = size.minDimension * 0.45f,
        style = Stroke(width = 1.2.dp.toPx()),
      )
      // Minimalist geometric V-aperture
      val path =
        Path().apply {
          moveTo(size.width * 0.22f, size.height * 0.28f)
          lineTo(size.width * 0.50f, size.height * 0.76f)
          lineTo(size.width * 0.78f, size.height * 0.28f)
        }
      drawPath(path = path, color = tokens.textPrimary, style = stroke)
      // Subtle silver-blue focus node
      drawCircle(
        color = tokens.accentSilverBlue,
        radius = 2.4.dp.toPx(),
        center = Offset(size.width * 0.50f, size.height * 0.28f),
      )
    }
  }
}

@Composable
private fun GoogleGVectorIcon(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val strokeWidth = size.minDimension * 0.20f
    val inset = strokeWidth / 2f
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val topLeft = Offset(inset, inset)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

    // Blue right/bottom-right segment + crossbar
    drawArc(
      color = Color(0xFF4285F4),
      startAngle = -10f,
      sweepAngle = 55f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = stroke,
    )
    // Green bottom segment
    drawArc(
      color = Color(0xFF34A853),
      startAngle = 45f,
      sweepAngle = 95f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = stroke,
    )
    // Yellow left segment
    drawArc(
      color = Color(0xFFFBBC05),
      startAngle = 140f,
      sweepAngle = 80f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = stroke,
    )
    // Red top segment
    drawArc(
      color = Color(0xFFEA4335),
      startAngle = 220f,
      sweepAngle = 100f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = stroke,
    )
    // Horizontal bar of the 'G'
    val centerY = size.height * 0.5f
    drawLine(
      color = Color(0xFF4285F4),
      start = Offset(size.width * 0.50f, centerY),
      end = Offset(size.width * 0.96f, centerY),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Butt,
    )
  }
}

private fun generateSecureRandomNonce(byteLength: Int = 32): String {
  val randomBytes = ByteArray(byteLength)
  SecureRandom().nextBytes(randomBytes)
  return Base64.encodeToString(randomBytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun LoginScreenPreview() {
  VesperTheme {
    LoginScreen(onSignInSuccess = {})
  }
}
