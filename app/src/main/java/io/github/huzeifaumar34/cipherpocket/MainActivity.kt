package io.github.huzeifaumar34.cipherpocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.huzeifaumar34.cipherpocket.ui.CipherPocketApp
import io.github.huzeifaumar34.cipherpocket.ui.theme.CipherPocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CipherPocketTheme {
                CipherPocketApp()
            }
        }
    }
}
