package com.knotes.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * The default palette, navy #133155, the colour of the app icons. Other base colours can be picked
 * in Settings (see [ThemeColor]).
 *
 * It used to follow the phone's wallpaper colours (Material You dynamic colour). A One UI update
 * re-picked that palette and turned the whole app violet, text included. A fixed palette looks the
 * same whatever the wallpaper or a future update does.
 *
 * Every neutral is set too, not only the accents: Material 3's own defaults for surfaces and
 * outlines are the violet baseline, so leaving them out would bring the tint back in the greys.
 */

private val Navy = Color(0xFF133155)

private val LightNavy = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3F7),
    onPrimaryContainer = Color(0xFF0B1F38),
    inversePrimary = Color(0xFFA9C7F0),
    secondary = Color(0xFF4A5F7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5F2),
    onSecondaryContainer = Color(0xFF1B2B3F),
    tertiary = Color(0xFF2F6DB5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3E4FA),
    onTertiaryContainer = Color(0xFF0A2C55),
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF181C21),
    surface = Color(0xFFF8FAFD),
    onSurface = Color(0xFF181C21),
    surfaceVariant = Color(0xFFE0E6EE),
    onSurfaceVariant = Color(0xFF434A54),
    surfaceTint = Navy,
    inverseSurface = Color(0xFF2D3137),
    inverseOnSurface = Color(0xFFEFF1F5),
    outline = Color(0xFF737B86),
    outlineVariant = Color(0xFFC3CAD4),
    surfaceBright = Color(0xFFF8FAFD),
    surfaceDim = Color(0xFFD8DCE2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F4F8),
    surfaceContainer = Color(0xFFECEFF3),
    surfaceContainerHigh = Color(0xFFE6E9EE),
    surfaceContainerHighest = Color(0xFFE0E3E8),
)

private val DarkNavy = darkColorScheme(
    primary = Color(0xFFA9C7F0),
    onPrimary = Color(0xFF0B1F38),
    primaryContainer = Color(0xFF1F3F66),
    onPrimaryContainer = Color(0xFFD5E3F7),
    inversePrimary = Navy,
    secondary = Color(0xFFB6C7DD),
    onSecondary = Color(0xFF1F3044),
    secondaryContainer = Color(0xFF34465C),
    onSecondaryContainer = Color(0xFFD7E3F4),
    tertiary = Color(0xFF9FC8FF),
    onTertiary = Color(0xFF00315F),
    tertiaryContainer = Color(0xFF1E4C86),
    onTertiaryContainer = Color(0xFFD3E4FA),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E3E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE0E3E8),
    surfaceVariant = Color(0xFF414851),
    onSurfaceVariant = Color(0xFFC1C8D2),
    surfaceTint = Color(0xFFA9C7F0),
    inverseSurface = Color(0xFFE0E3E8),
    inverseOnSurface = Color(0xFF2D3137),
    outline = Color(0xFF8B929C),
    outlineVariant = Color(0xFF414851),
    surfaceBright = Color(0xFF363A3F),
    surfaceDim = Color(0xFF101418),
    surfaceContainerLowest = Color(0xFF0B0F13),
    surfaceContainerLow = Color(0xFF181C21),
    surfaceContainer = Color(0xFF1C2025),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF31353A),
)

/**
 * The theme's base colour, chosen in Settings → General. Applied the moment a swatch is tapped,
 * like the language, so it is seen before it is kept.
 *
 * Stored in the main settings file (and so in the settings backup) under [KEY]. [PHONE] (0) means
 * follow the phone's wallpaper colours, which is what the app did before it had its own.
 */
object ThemeColor {
    const val KEY = "theme_color"
    const val PHONE = 0
    val NAVY = 0xFF133155.toInt()

    /** The swatches offered in Settings; any other colour can be typed as hex. */
    val PRESETS: List<Int> = listOf(
        NAVY,
        0xFF00626A.toInt(), // teal
        0xFF2E6B3A.toInt(), // green
        0xFF5B3E8F.toInt(), // purple
        0xFF7A2335.toInt(), // burgundy
        0xFFA2491A.toInt(), // orange
        0xFF6B4A2B.toInt(), // brown
        0xFF3A3F45.toInt(), // graphite
    )

    private val _value = MutableStateFlow<Int?>(null)
    val value: StateFlow<Int?> = _value

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("knotes_prefs", Context.MODE_PRIVATE)

    fun load(context: Context): Int {
        _value.value?.let { return it }
        return prefs(context).getInt(KEY, NAVY).also { _value.value = it }
    }

    fun set(context: Context, argb: Int) {
        prefs(context).edit().putInt(KEY, argb).apply()
        _value.value = argb
    }
}

/**
 * A full scheme from one base colour: the colour itself as primary, lighter and darker mixes of it
 * for containers, and neutrals faintly tinted towards it so the greys belong to the same theme.
 */
private fun schemeFrom(base: Color, dark: Boolean): ColorScheme {
    val white = Color.White
    val black = Color.Black
    val grey = Color(0xFF5F6670)
    val muted = lerp(base, grey, 0.55f)
    fun tint(neutral: Color, t: Float = 0.05f) = lerp(neutral, base, t)
    fun on(c: Color) = if (c.luminance() > 0.45f) Color(0xFF111111) else white
    return if (!dark) {
        lightColorScheme(
            primary = base, onPrimary = on(base),
            primaryContainer = lerp(base, white, 0.84f), onPrimaryContainer = lerp(base, black, 0.55f),
            inversePrimary = lerp(base, white, 0.6f),
            secondary = muted, onSecondary = on(muted),
            secondaryContainer = lerp(muted, white, 0.84f), onSecondaryContainer = lerp(muted, black, 0.6f),
            tertiary = lerp(base, white, 0.25f), onTertiary = on(lerp(base, white, 0.25f)),
            tertiaryContainer = lerp(base, white, 0.78f), onTertiaryContainer = lerp(base, black, 0.5f),
            background = tint(Color(0xFFF9FAFB), 0.02f), onBackground = Color(0xFF181C21),
            surface = tint(Color(0xFFF9FAFB), 0.02f), onSurface = Color(0xFF181C21),
            surfaceVariant = tint(Color(0xFFE2E5E9)), onSurfaceVariant = Color(0xFF444A52),
            surfaceTint = base,
            inverseSurface = Color(0xFF2D3137), inverseOnSurface = Color(0xFFEFF1F5),
            outline = Color(0xFF747A83), outlineVariant = tint(Color(0xFFC4C9D0)),
            surfaceBright = tint(Color(0xFFF9FAFB), 0.02f), surfaceDim = tint(Color(0xFFD8DBE0)),
            surfaceContainerLowest = white,
            surfaceContainerLow = tint(Color(0xFFF3F4F6), 0.03f),
            surfaceContainer = tint(Color(0xFFEDEFF2), 0.04f),
            surfaceContainerHigh = tint(Color(0xFFE7E9EC)),
            surfaceContainerHighest = tint(Color(0xFFE1E3E7)),
        )
    } else {
        val p = lerp(base, white, 0.62f)
        darkColorScheme(
            primary = p, onPrimary = lerp(base, black, 0.55f),
            primaryContainer = lerp(base, black, 0.25f), onPrimaryContainer = lerp(base, white, 0.84f),
            inversePrimary = base,
            secondary = lerp(muted, white, 0.62f), onSecondary = lerp(muted, black, 0.55f),
            secondaryContainer = lerp(muted, black, 0.3f), onSecondaryContainer = lerp(muted, white, 0.84f),
            tertiary = lerp(base, white, 0.5f), onTertiary = lerp(base, black, 0.6f),
            tertiaryContainer = lerp(base, black, 0.1f), onTertiaryContainer = lerp(base, white, 0.84f),
            background = tint(Color(0xFF111316)), onBackground = Color(0xFFE1E3E7),
            surface = tint(Color(0xFF111316)), onSurface = Color(0xFFE1E3E7),
            surfaceVariant = tint(Color(0xFF42474E)), onSurfaceVariant = Color(0xFFC2C7CE),
            surfaceTint = p,
            inverseSurface = Color(0xFFE1E3E7), inverseOnSurface = Color(0xFF2D3137),
            outline = Color(0xFF8C9199), outlineVariant = tint(Color(0xFF42474E)),
            surfaceBright = tint(Color(0xFF37393D)), surfaceDim = tint(Color(0xFF111316)),
            surfaceContainerLowest = tint(Color(0xFF0C0E10)),
            surfaceContainerLow = tint(Color(0xFF191B1F)),
            surfaceContainer = tint(Color(0xFF1D1F23)),
            surfaceContainerHigh = tint(Color(0xFF27292D)),
            surfaceContainerHighest = tint(Color(0xFF323438)),
        )
    }
}

@Composable
fun KnotesTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val stored by ThemeColor.value.collectAsState()
    val argb = stored ?: remember { ThemeColor.load(context) }
    val dark = isSystemInDarkTheme()
    val colors = when {
        argb == ThemeColor.PHONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // The navy has a hand-tuned palette; every other colour is built from it.
        argb == ThemeColor.NAVY || argb == ThemeColor.PHONE -> if (dark) DarkNavy else LightNavy
        else -> schemeFrom(Color(argb), dark)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
