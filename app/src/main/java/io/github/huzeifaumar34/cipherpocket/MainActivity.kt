package io.github.huzeifaumar34.cipherpocket

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.huzeifaumar34.cipherpocket.ui.CipherPocketApp
import io.github.huzeifaumar34.cipherpocket.ui.HomeViewModel
import io.github.huzeifaumar34.cipherpocket.ui.theme.CipherPocketTheme

class MainActivity : ComponentActivity() {
    // Same ViewModelStore as the one Compose's viewModel() uses, so this is the same instance.
    private val homeViewModel: HomeViewModel by viewModels()

    override fun onStop() {
        super.onStop()
        // Rotation also stops the activity; only wipe when the user really leaves the app.
        if (!isChangingConfigurations) homeViewModel.onBackgrounded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = getSharedPreferences("cipherpocket-settings", MODE_PRIVATE)
        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        } else null
        setContent {
            var theme by remember {
                mutableStateOf(preferences.getString("theme", "system") ?: "system")
            }
            var language by remember {
                mutableStateOf(preferences.getString("language", "system") ?: "system")
            }
            var clipboardSeconds by remember {
                mutableIntStateOf(preferences.getInt("clipboard_seconds", 30))
            }
            val darkTheme = when (theme) {
                "dark" -> true
                "light" -> false
                else -> null
            }

            CipherPocketTheme(darkTheme = darkTheme) {
                CipherPocketApp(
                    initialSharedText = sharedText,
                    language = language,
                    theme = theme,
                    clipboardSeconds = clipboardSeconds,
                    onLanguageChange = {
                        language = it
                        preferences.edit().putString("language", it).apply()
                    },
                    onThemeChange = {
                        theme = it
                        preferences.edit().putString("theme", it).apply()
                    },
                    onClipboardSecondsChange = {
                        clipboardSeconds = it
                        preferences.edit().putInt("clipboard_seconds", it).apply()
                    }
                )
            }
        }
    }
}
