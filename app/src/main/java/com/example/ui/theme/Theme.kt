package com.example.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

// Vesper Obsidian Minimalist Color Palette
val PureObsidian = Color(0xFF0A0A0C)
val MatteCharcoal = Color(0xFF141417)
val TranslucentCharcoal = Color(0xF2141417)
val SoftGraphite = Color(0xFF1E1E22)
val HairlineBorder = Color(0x1FFFFFFF)
val CapsuleBorder = Color(0x26FFFFFF)
val FocusedSilverBorder = Color(0x66A8C7FA)
val PureAlabaster = Color(0xFFF5F5F7)
val CoolAsh = Color(0xFF8E8E93)
val MutedSlate = Color(0xFF5A5A60)
val SoftSilverBlue = Color(0xFFA8C7FA)
val SoftSilverBlueHalo = Color(0x2EA8C7FA)
val StatusEmerald = Color(0xFF4ADE80)

val InterFontFamily =
  FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
  )

val JetBrainsMonoFontFamily =
  FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
  )

private val VesperColorScheme =
  darkColorScheme(
    primary = SoftSilverBlue,
    onPrimary = PureObsidian,
    primaryContainer = SoftGraphite,
    onPrimaryContainer = PureAlabaster,
    secondary = CoolAsh,
    onSecondary = PureObsidian,
    secondaryContainer = MatteCharcoal,
    onSecondaryContainer = PureAlabaster,
    tertiary = StatusEmerald,
    onTertiary = PureObsidian,
    background = PureObsidian,
    onBackground = PureAlabaster,
    surface = MatteCharcoal,
    onSurface = PureAlabaster,
    surfaceVariant = SoftGraphite,
    onSurfaceVariant = CoolAsh,
    outline = HairlineBorder,
    outlineVariant = CapsuleBorder,
  )

val VesperTypography =
  Typography(
    // Centered Home Greeting ("Hi Alex") & Login Title ("Vesper")
    displaySmall =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
      ),
    // Chat body text (1.5x line height) and greeting subtitle
    bodyLarge =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
      ),
    // Uppercase monospace tags ("LOCAL ACTION", "RESEARCH")
    labelSmall =
      TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
      ),
  )

val VesperShapes =
  Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
  )

@Immutable
data class VesperExtendedTokens(
  val canvasBackground: Color = PureObsidian,
  val elevatedSurface: Color = MatteCharcoal,
  val drawerSurface: Color = TranslucentCharcoal,
  val userBubble: Color = SoftGraphite,
  val hairlineBorder: Color = HairlineBorder,
  val capsuleBorder: Color = CapsuleBorder,
  val focusedBorder: Color = FocusedSilverBorder,
  val textPrimary: Color = PureAlabaster,
  val textSecondary: Color = CoolAsh,
  val textMuted: Color = MutedSlate,
  val accentSilverBlue: Color = SoftSilverBlue,
  val accentSilverBlueHalo: Color = SoftSilverBlueHalo,
  val statusGreen: Color = StatusEmerald,
)

val LocalVesperTokens = staticCompositionLocalOf { VesperExtendedTokens() }

object VesperTokens {
  val colors: VesperExtendedTokens
    @Composable get() = LocalVesperTokens.current
}

fun Modifier.hairlineBorder(
  shape: Shape,
  color: Color = HairlineBorder,
  width: Dp = 1.dp,
): Modifier = this.border(width = width, color = color, shape = shape)

fun Modifier.capsuleHairlineBorder(
  shape: Shape,
  isFocused: Boolean = false,
  width: Dp = 1.dp,
): Modifier =
  this.border(
    width = width,
    color = if (isFocused) FocusedSilverBorder else CapsuleBorder,
    shape = shape,
  )

@Composable
fun VesperTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = VesperColorScheme,
    typography = VesperTypography,
    shapes = VesperShapes,
    content = content,
  )
}
