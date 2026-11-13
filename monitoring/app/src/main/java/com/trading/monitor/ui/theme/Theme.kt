package com.trading.monitor.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The single source of truth for every colour in the app. */
object AppColors {
    val Canvas: Color = Color(0xFFF5F7FA)
    val Surface: Color = Color(0xFFFFFFFF)
    val TextPrimary: Color = Color(0xFF172033)
    val TextSecondary: Color = Color(0xFF5B6B82)
    val Border: Color = Color(0xFFE3E8EF)

    val Primary: Color = Color(0xFF2563EB)
    val OnPrimary: Color = Color(0xFFFFFFFF)
    val SelectedRow: Color = Color(0xFFEFF4FF)

    val BullishText: Color = Color(0xFF047857)
    val BullishBg: Color = Color(0xFFECFDF5)
    val BearishText: Color = Color(0xFFB42318)
    val BearishBg: Color = Color(0xFFFEF3F2)
    val NeutralText: Color = Color(0xFF475467)
    val NeutralBg: Color = Color(0xFFF2F4F7)
    val WarningText: Color = Color(0xFFB54708)
    val WarningBg: Color = Color(0xFFFFFAEB)

    val Danger: Color = Color(0xFFB42318)
    val Placeholder: Color = Color(0xFFE3E8EF)

    val ViewerBackdrop: Color = Color(0xEB000000)
    val ViewerOnBackdrop: Color = Color(0xFFFFFFFF)

    /** Mid-tone avatars; white text on every entry clears 4.5:1 contrast. */
    val AvatarPalette: List<Color> = listOf(
        Color(0xFF1D4ED8),
        Color(0xFF0F766E),
        Color(0xFF7C3AED),
        Color(0xFFB45309),
        Color(0xFFBE185D),
        Color(0xFF0369A1),
        Color(0xFF4D7C0F),
        Color(0xFF6D28D9),
    )
}

/** The app's type scale; colours are always supplied by the caller. */
object AppType {
    val ScreenTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    )
    val SectionTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val Body: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    val Label: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val Caption: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

/** Switches a style to tabular numerals for prices, levels, scores and times. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** The one spacing scale: use these instead of ad-hoc dp values. */
object AppSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

/** Corners: 12 dp on cards and panels, 8 dp on images and chips, pill for chips and badges. */
object AppShape {
    val Card = RoundedCornerShape(12.dp)
    val Small = RoundedCornerShape(8.dp)
    val Pill = RoundedCornerShape(50)
}

/** Fixed sizes and breakpoints shared by the layouts. */
object AppSize {
    val TouchTarget = 44.dp
    val Border = 1.dp
    val FocusRing = 2.dp
    val SelectedBar = 3.dp
    val Rail = 80.dp
    val AccountsPanel = 320.dp
    val ReadingWidth = 720.dp
    val MediaMaxHeight = 420.dp
    val Avatar = 40.dp
    val AvatarSmall = 32.dp
    val WideBreakpoint = 1000.dp
    val MediumBreakpoint = 600.dp
}

/** Alpha applied to disabled content. */
const val DisabledAlpha = 0.38f

/** Draws the standard 1 dp border on a clipped white surface — borders, not shadows. */
fun Modifier.cardBorder(): Modifier = this
    .clip(AppShape.Card)
    .background(AppColors.Surface)
    .border(AppSize.Border, AppColors.Border, AppShape.Card)

/**
 * Shows a 2 dp blue ring while the element has keyboard focus. Put it before `clickable` on
 * rows and links built by hand; Material buttons already show their own focus state.
 */
fun Modifier.focusRing(shape: Shape = AppShape.Small): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(AppSize.FocusRing, AppColors.Primary, shape) else Modifier)
}

/** Provides the light Material 3 theme built from the design tokens. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = AppColors.Primary,
        onPrimary = AppColors.OnPrimary,
        primaryContainer = AppColors.SelectedRow,
        onPrimaryContainer = AppColors.Primary,
        secondaryContainer = AppColors.SelectedRow,
        onSecondaryContainer = AppColors.Primary,
        background = AppColors.Canvas,
        onBackground = AppColors.TextPrimary,
        surface = AppColors.Surface,
        onSurface = AppColors.TextPrimary,
        surfaceVariant = AppColors.NeutralBg,
        onSurfaceVariant = AppColors.TextSecondary,
        surfaceTint = AppColors.Surface,
        surfaceBright = AppColors.Surface,
        surfaceDim = AppColors.Surface,
        surfaceContainer = AppColors.Surface,
        surfaceContainerHigh = AppColors.Surface,
        surfaceContainerHighest = AppColors.Surface,
        surfaceContainerLow = AppColors.Surface,
        surfaceContainerLowest = AppColors.Surface,
        outline = AppColors.Border,
        outlineVariant = AppColors.Border,
        error = AppColors.Danger,
        onError = AppColors.ViewerOnBackdrop,
    )
    val typography = Typography(
        titleLarge = AppType.ScreenTitle,
        titleMedium = AppType.SectionTitle,
        bodyLarge = AppType.Body,
        bodyMedium = AppType.Body,
        labelLarge = AppType.Label,
        labelMedium = AppType.Label,
        bodySmall = AppType.Caption,
        labelSmall = AppType.Caption,
    )
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

/** True when the system "Remove animations" setting is on, so callers can skip animation. */
@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
