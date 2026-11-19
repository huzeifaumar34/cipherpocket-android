package io.github.huzeifaumar34.cipherpocket.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val CipherDark = darkColorScheme(
    primary = Color(0xFF9DECCB),
    onPrimary = Color(0xFF062116),
    secondary = Color(0xFFB6CDBF),
    background = Color(0xFF0B1210),
    surface = Color(0xFF111C18),
    surfaceVariant = Color(0xFF1A2923),
    onBackground = Color(0xFFE4F1EA),
    onSurface = Color(0xFFE4F1EA),
    outline = Color(0xFF52665B)
)

private val CipherLight = lightColorScheme(
    primary = Color(0xFF146B4A),
    onPrimary = Color.White,
    secondary = Color(0xFF426B59),
    background = Color(0xFFF5F8F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFE7EEE9),
    onBackground = Color(0xFF15221B),
    onSurface = Color(0xFF15221B),
    outline = Color(0xFF718278)
)

@Composable
fun CipherPocketTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDark = darkTheme ?: isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDark ->
            dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        useDark -> CipherDark
        else -> CipherLight
    }
    MaterialTheme(colorScheme = colors, content = content)
}
