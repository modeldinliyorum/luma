package com.luma.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.luma.reader.data.ThemeMode

/** Highlighter amber, shared by the EPUB marks and the PDF pencil overlay. */
const val HighlightMarkArgb: Int = 0x59E6C34A

/** The colours offered in the highlight picker. Yellow is the default. */
data class HighlightColorOption(val label: String, val rgb: Int)

val HighlightColorOptions = listOf(
    HighlightColorOption("Yellow", 0xFFF5C518.toInt()),
    HighlightColorOption("Green", 0xFF7BD88F.toInt()),
    HighlightColorOption("Blue", 0xFF6FA8DC.toInt()),
    HighlightColorOption("Pink", 0xFFF29CB5.toInt()),
    HighlightColorOption("Orange", 0xFFF2A65A.toInt()),
    HighlightColorOption("Purple", 0xFFB48EF5.toInt())
)

/** Force full opacity on an RGB colour so swatches stay visible. */
fun opaqueColor(rgb: Int): Color = Color(0xFF000000.toInt() or rgb)

/** Near-black ink and a single pale "moonlight" secondary keep it monochrome. */
private val Night = Color(0xFF0A0A0B)
private val NightSurface = Color(0xFF141417)
private val NightVariant = Color(0xFF1E1E22)
private val Moonstone = Color(0xFFC7CBD9)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEDEDEF),
    onPrimary = Night,
    secondary = Moonstone,
    onSecondary = Night,
    background = Night,
    onBackground = Color(0xFFE8E8EA),
    surface = NightSurface,
    onSurface = Color(0xFFE8E8EA),
    surfaceVariant = NightVariant,
    onSurfaceVariant = Color(0xFF9A9AA2),
    outline = Color(0xFF2A2A2F)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C1C1F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5A5F6B),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAF9),
    onBackground = Color(0xFF141416),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141416),
    surfaceVariant = Color(0xFFEDEDEE),
    onSurfaceVariant = Color(0xFF6B6B70),
    outline = Color(0xFFD8D8DA)
)

/** Serif for titles reads as a book; sans for controls stays out of the way. */
private val LumaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    )
)

@Composable
fun LumaTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = LumaTypography,
        content = content
    )
}
