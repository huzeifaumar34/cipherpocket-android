package io.github.huzeifaumar34.cipherpocket.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.huzeifaumar34.cipherpocket.core.Op
import java.io.ByteArrayOutputStream
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppPage { TOOLS, DETECT, VAULT, FILES, HISTORY, SETTINGS }

private val operationGroups = listOf(
    "Encoding" to listOf(Op.BIN_ENC, Op.BIN_DEC, Op.HEX_ENC, Op.HEX_DEC, Op.B64_ENC, Op.B64_DEC, Op.MORSE_ENC, Op.MORSE_DEC),
    "Classical" to listOf(Op.CAESAR, Op.CAESAR_BRUTE, Op.ROT13, Op.ATBASH, Op.VIGENERE_ENC, Op.VIGENERE_DEC, Op.XOR_ENC, Op.XOR_DEC),
    "Text" to listOf(Op.REVERSE, Op.UPPER, Op.LOWER, Op.STATS, Op.FREQ),
    "Analysis" to listOf(Op.AUTO_DETECT, Op.PASSWORD_CHECK, Op.BASE_CONVERT)
)

private fun operationLabel(op: Op, pt: Boolean): String = when (op) {
    Op.BIN_ENC -> if (pt) "Texto para binário" else "Text to binary"
    Op.BIN_DEC -> if (pt) "Binário para texto" else "Binary to text"
    Op.HEX_ENC -> if (pt) "Texto para hexadecimal" else "Text to hexadecimal"
    Op.HEX_DEC -> if (pt) "Hexadecimal para texto" else "Hexadecimal to text"
    Op.B64_ENC -> "Base64 encode"
    Op.B64_DEC -> "Base64 decode"
    Op.MORSE_ENC -> if (pt) "Texto para Morse" else "Text to Morse"
    Op.MORSE_DEC -> if (pt) "Morse para texto" else "Morse to text"
    Op.CAESAR -> if (pt) "Cifra de César" else "Caesar cipher"
    Op.CAESAR_BRUTE -> if (pt) "Força bruta de César" else "Caesar brute force"
    Op.ROT13 -> "ROT13"
    Op.ATBASH -> "Atbash"
    Op.VIGENERE_ENC -> if (pt) "Vigenère — cifrar" else "Vigenère — encrypt"
    Op.VIGENERE_DEC -> if (pt) "Vigenère — decifrar" else "Vigenère — decrypt"
    Op.XOR_ENC -> if (pt) "XOR — cifrar (hex)" else "XOR — encrypt (hex)"
    Op.XOR_DEC -> if (pt) "XOR — decifrar (hex)" else "XOR — decrypt (hex)"
    Op.AES_ENC -> if (pt) "AES-256-GCM — cifrar" else "AES-256-GCM — encrypt"
    Op.AES_DEC -> if (pt) "AES-256-GCM — decifrar" else "AES-256-GCM — decrypt"
    Op.AUTO_DETECT -> if (pt) "Detetar formato" else "Detect format"
    Op.REVERSE -> if (pt) "Inverter texto" else "Reverse text"
    Op.UPPER -> if (pt) "Maiúsculas" else "Uppercase"
    Op.LOWER -> if (pt) "Minúsculas" else "Lowercase"
    Op.STATS -> if (pt) "Estatísticas" else "Statistics"
    Op.FREQ -> if (pt) "Frequência de letras" else "Letter frequency"
    Op.BASE_CONVERT -> if (pt) "Conversor de bases 2–36" else "Base converter 2–36"
    Op.PASSWORD_CHECK -> if (pt) "Verificar palavra-passe" else "Password check"
    Op.AES_ENC_RAW -> "AES raw encrypt"
    Op.AES_DEC_RAW -> "AES raw decrypt"
}

private fun categoryLabel(category: String, pt: Boolean): String = when (category) {
    "Encoding" -> if (pt) "Codificação" else category
    "Classical" -> if (pt) "Cifras clássicas" else category
    "Text" -> if (pt) "Texto" else category
    else -> if (pt) "Análise" else category
}

@Composable
fun CipherPocketApp(
    initialSharedText: String? = null,
    language: String,
    theme: String,
    clipboardSeconds: Int,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    onClipboardSecondsChange: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val systemLanguage = configuration.locales[0].language
    val pt = language == "pt" || (language == "system" && systemLanguage == "pt")
    val t: (String, String) -> String = { english, portuguese -> if (pt) portuguese else english }
    var page by rememberSaveable { mutableStateOf(if (initialSharedText.isNullOrBlank()) AppPage.TOOLS else AppPage.DETECT) }
    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            viewModel.onInputChanged(initialSharedText)
            page = AppPage.DETECT
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    ScreenSecurity(enabled = page == AppPage.VAULT || page == AppPage.FILES)

    fun copyOutput(value: String) {
        val clip = ClipData.newPlainText("CipherPocket", value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        if (clipboardSeconds > 0) {
            scope.launch {
                delay(clipboardSeconds * 1000L)
                val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (current == value) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
                    else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }

    fun shareOutput(value: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, value)
        }
        context.startActivity(Intent.createChooser(intent, t("Share result", "Partilhar resultado")))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Triple(AppPage.TOOLS, "T", t("Tools", "Ferramentas")),
                    Triple(AppPage.DETECT, "?", t("Detect", "Detetar")),
                    Triple(AppPage.VAULT, "V", "Vault"),
                    Triple(AppPage.FILES, "F", t("Files", "Ficheiros")),
                    Triple(AppPage.HISTORY, "↶", t("History", "Histórico")),
                    Triple(AppPage.SETTINGS, "⋯", t("Settings", "Definições"))
                )
                items.forEach { item ->
                    NavigationBarItem(
                        selected = page == item.first,
                        onClick = { page = item.first },
                        icon = { Text(item.second, fontWeight = FontWeight.Bold) },
                        label = { Text(item.third, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header(pt = pt)
            when (page) {
                AppPage.TOOLS -> ToolsPage(
                    state = state,
                    pt = pt,
                    t = t,
                    onSelectOperation = viewModel::selectOperation,
                    onInputChanged = viewModel::onInputChanged,
                    onArgumentChanged = viewModel::onArgumentChanged,
                    onRun = viewModel::runSelected,
                    onSwap = viewModel::swapInputOutput,
                    onClear = viewModel::clearInput,
                    onCopy = { copyOutput(state.output) },
                    onShare = { shareOutput(state.output) }
                )
                AppPage.DETECT -> DetectPage(
                    state = state,
                    t = t,
                    onInputChanged = viewModel::onInputChanged,
                    onRun = { viewModel.runOperation(Op.AUTO_DETECT, state.input) },
                    onCopy = { copyOutput(state.output) }
                )
                AppPage.VAULT -> VaultPage(
                    state = state,
                    pt = pt,
                    t = t,
                    onInputChanged = viewModel::onInputChanged,
                    onRun = { encrypt, input, password ->
                        viewModel.runOperation(
                            if (encrypt) Op.AES_ENC else Op.AES_DEC,
                            input,
                            password,
                            clearSensitiveArgument = true
                        )
                    },
                    onCopy = { copyOutput(state.output) }
                )
                AppPage.FILES -> FilesPage(
                    t = t,
                    onExternalActivityChanged = viewModel::setExternalActivityActive,
                    onProcessFile = { encrypt, data, password -> viewModel.executeRawFile(encrypt, data, password) }
                )
                AppPage.HISTORY -> HistoryPage(
                    records = state.history,
                    pt = pt,
                    onClear = viewModel::clearHistory,
                    onLoad = viewModel::loadHistory
                )
                AppPage.SETTINGS -> SettingsPage(
                    language = language,
                    theme = theme,
                    clipboardSeconds = clipboardSeconds,
                    t = t,
                    onLanguageChange = onLanguageChange,
                    onThemeChange = onThemeChange,
                    onClipboardSecondsChange = onClipboardSecondsChange
                )
            }
            Text(
                text = t("Your text stays on this device. CipherPocket works without network access.",
                    "O teu texto permanece neste dispositivo. O CipherPocket funciona sem acesso à rede."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Header(pt: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("CP", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                }
            }
            Column(modifier = Modifier.padding(start = 11.dp)) {
                Text("CipherPocket", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (pt) "PRIVADO POR DEFINIÇÃO" else "PRIVATE BY DESIGN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = "● OFFLINE",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ToolsPage(
    state: HomeUiState,
    pt: Boolean,
    t: (String, String) -> String,
    onSelectOperation: (Op) -> Unit,
    onInputChanged: (String) -> Unit,
    onArgumentChanged: (String) -> Unit,
    onRun: () -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    var category by rememberSaveable { mutableStateOf("Encoding") }
    var expanded by remember { mutableStateOf(false) }
    val selected = state.selectedOperation
    val argumentLabel = when (selected) {
        Op.CAESAR -> t("Shift (for example, 3 or -3)", "Deslocamento (por exemplo, 3 ou -3)")
        Op.VIGENERE_ENC, Op.VIGENERE_DEC, Op.XOR_ENC, Op.XOR_DEC, Op.AES_ENC, Op.AES_DEC ->
            t("Password / key", "Palavra-passe / chave")
        Op.BASE_CONVERT -> t("Input base:output base (example: 10:2)", "Base de entrada:base de saída (exemplo: 10:2)")
        else -> ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(t("Your pocket toolkit", "A tua caixa de ferramentas"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                t("Choose an operation. Everything runs locally on the device.", "Escolhe uma operação. Tudo é processado localmente no dispositivo."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            operationGroups.forEach { group ->
                FilterChip(
                    selected = category == group.first,
                    onClick = {
                        category = group.first
                        onSelectOperation(group.second.first())
                    },
                    label = { Text(categoryLabel(group.first, pt)) }
                )
            }
        }
        ElevatedCard(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("Operation", "Operação"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(operationLabel(selected, pt), modifier = Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        operationGroups.first { it.first == category }.second.forEach { op ->
                            DropdownMenuItem(
                                text = { Text(operationLabel(op, pt)) },
                                onClick = {
                                    onSelectOperation(op)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                if (argumentLabel.isNotEmpty()) {
                    OutlinedTextField(
                        value = state.argument1,
                        onValueChange = onArgumentChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(argumentLabel) },
                        singleLine = true,
                        visualTransformation = if (selected in listOf(Op.AES_ENC, Op.AES_DEC, Op.XOR_ENC, Op.XOR_DEC, Op.VIGENERE_ENC, Op.VIGENERE_DEC)) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
                    )
                }
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Input", "Entrada")) },
                    placeholder = { Text(t("Type or paste text here…", "Escreve ou cola o texto aqui…")) },
                    minLines = 4,
                    maxLines = 8,
                    enabled = !state.isWorking,
                    shape = RoundedCornerShape(15.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onRun, enabled = !state.isWorking, modifier = Modifier.weight(1f)) {
                        Text(t("Run", "Executar"))
                    }
                    OutlinedButton(onClick = onSwap, enabled = state.output.isNotEmpty() && !state.isWorking) {
                        Text(t("Swap", "Trocar"))
                    }
                    OutlinedButton(onClick = onClear, enabled = state.input.isNotEmpty() || state.output.isNotEmpty()) {
                        Text(t("Clear", "Limpar"))
                    }
                }
                if (state.isWorking) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(t("Processing…", "A processar…"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutputPanel(
                    output = state.output,
                    emptyText = t("Your result will appear here.", "O resultado vai aparecer aqui."),
                    t = t,
                    onCopy = onCopy,
                    onShare = onShare
                )
                if (selected in listOf(Op.CAESAR, Op.CAESAR_BRUTE, Op.ROT13, Op.ATBASH, Op.VIGENERE_ENC, Op.VIGENERE_DEC, Op.XOR_ENC, Op.XOR_DEC)) {
                    Text(
                        t("Educational only: classical ciphers and repeating-key XOR do not protect secrets.",
                          "Apenas educativo: cifras clássicas e XOR com chave repetida não protegem segredos."),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectPage(
    state: HomeUiState,
    t: (String, String) -> String,
    onInputChanged: (String) -> Unit,
    onRun: () -> Unit,
    onCopy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("Detect a format", "Detetar um formato"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            t("Paste a message to check whether it looks like Base64, hexadecimal, binary or Morse.",
              "Cola uma mensagem para verificar se parece Base64, hexadecimal, binário ou Morse."),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(t("Message", "Mensagem")) },
            minLines = 5,
            maxLines = 9
        )
        Button(onClick = onRun, enabled = !state.isWorking) { Text(t("Detect format", "Detetar formato")) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutputPanel(
            output = state.output,
            emptyText = t("Detection results will appear here.", "Os resultados aparecerão aqui."),
            t = t,
            onCopy = onCopy,
            onShare = {}
        )
    }
}

@Composable
private fun VaultPage(
    state: HomeUiState,
    pt: Boolean,
    t: (String, String) -> String,
    onInputChanged: (String) -> Unit,
    onRun: (Boolean, String, String) -> Unit,
    onCopy: () -> Unit
) {
    var encrypt by rememberSaveable { mutableStateOf(true) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                password = ""
                confirmation = ""
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("Vault", "Cofre"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            t("Protect text with AES-256-GCM. The password is never saved in operation history.",
              "Protege texto com AES-256-GCM. A palavra-passe nunca é guardada no histórico."),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = encrypt, onClick = { encrypt = true }, label = { Text(t("Encrypt", "Cifrar")) })
            FilterChip(selected = !encrypt, onClick = { encrypt = false; confirmation = "" }, label = { Text(t("Decrypt", "Decifrar")) })
        }
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (encrypt) t("Plain text", "Texto simples") else t("Ciphertext (Base64)", "Texto cifrado (Base64)")) },
            minLines = 4,
            maxLines = 8
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(t("Password", "Palavra-passe")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        if (encrypt) {
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Confirm password", "Confirmar palavra-passe")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Text(
                text = t("Password strength: ", "Força da palavra-passe: ") + passwordStrengthLabel(password, pt),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
            if (password.isNotEmpty() && password.length < 12) {
                Text(
                    t("Use 12 or more characters for a stronger passphrase. You may still continue.",
                      "Usa 12 ou mais caracteres para uma frase-passe mais forte. Podes continuar mesmo assim."),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (encrypt && confirmation.isNotEmpty() && password != confirmation) {
            Text(t("Passwords do not match.", "As palavras-passe não coincidem."), color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                val secret = password
                onRun(encrypt, state.input, secret)
                password = ""
                confirmation = ""
            },
            enabled = !state.isWorking && password.isNotEmpty() && (!encrypt || password == confirmation)
        ) {
            Text(if (encrypt) t("Encrypt text", "Cifrar texto") else t("Decrypt text", "Decifrar texto"))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutputPanel(
            output = state.output,
            emptyText = t("Your encrypted or decrypted text will appear here.", "O texto cifrado ou decifrado aparecerá aqui."),
            t = t,
            onCopy = onCopy,
            onShare = {}
        )
        Text(
            t("The format uses a random salt and nonce, PBKDF2-HMAC-SHA256 (600,000 iterations) and an authenticated GCM tag.",
              "O formato usa sal e nonce aleatórios, PBKDF2-HMAC-SHA256 (600 000 iterações) e uma etiqueta GCM autenticada."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun passwordStrengthLabel(password: String, pt: Boolean): String {
    val score = (if (password.length >= 12) 2 else if (password.length >= 8) 1 else 0) +
        (if (password.any { it.isLowerCase() }) 1 else 0) +
        (if (password.any { it.isUpperCase() }) 1 else 0) +
        (if (password.any { it.isDigit() }) 1 else 0) +
        (if (password.any { !it.isLetterOrDigit() }) 1 else 0)
    return when {
        password.isEmpty() -> "—"
        score <= 1 -> if (pt) "Muito fraca" else "Very weak"
        score == 2 -> if (pt) "Fraca" else "Weak"
        score == 3 -> if (pt) "Razoável" else "Fair"
        score == 4 -> if (pt) "Boa" else "Good"
        else -> if (pt) "Forte" else "Strong"
    }
}

@Composable
private fun FilesPage(
    t: (String, String) -> String,
    onExternalActivityChanged: (Boolean) -> Unit,
    onProcessFile: suspend (Boolean, ByteArray, String) -> ByteArray
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var encrypt by rememberSaveable { mutableStateOf(true) }
    var password by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var processingJob by remember { mutableStateOf<Job?>(null) }
    var externalActivityActive by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !externalActivityActive) {
                processingJob?.cancel()
                processingJob = null
                password = ""
                pendingBytes?.fill(0)
                pendingBytes = null
                status = ""
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        externalActivityActive = false
        onExternalActivityChanged(false)
        val data = pendingBytes
        if (uri != null && data != null) {
            processingJob?.cancel()
            processingJob = scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(data) }
                            ?: throw IllegalStateException("Could not open the destination file.")
                    }
                    status = t("File saved successfully.", "Ficheiro guardado com sucesso.")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    status = error.message ?: t("Could not save the file.", "Não foi possível guardar o ficheiro.")
                } finally {
                    data.fill(0)
                    pendingBytes = null
                    password = ""
                }
            }
        } else {
            data?.fill(0)
            pendingBytes = null
            password = ""
            status = t("Save cancelled.", "Gravação cancelada.")
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        externalActivityActive = false
        onExternalActivityChanged(false)
        if (uri != null) {
            val encryptNow = encrypt
            val passwordNow = password
            processingJob?.cancel()
            processingJob = scope.launch {
                var input: ByteArray? = null
                try {
                    status = t("Reading file…", "A ler o ficheiro…")
                    input = withContext(Dispatchers.IO) { readLimitedFile(context, uri) }
                    val output = onProcessFile(encryptNow, input, passwordNow)
                    currentCoroutineContext().ensureActive()
                    pendingBytes = output
                    status = t("Choose where to save the result.", "Escolhe onde guardar o resultado.")
                    externalActivityActive = true
                    onExternalActivityChanged(true)
                    saveLauncher.launch(if (encryptNow) "CipherPocket-encrypted.cp" else "CipherPocket-decrypted.bin")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    status = error.message ?: t("File processing failed.", "O processamento do ficheiro falhou.")
                    password = ""
                    pendingBytes?.fill(0)
                    pendingBytes = null
                } finally {
                    input?.fill(0)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("File vault", "Cofre de ficheiros"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            t("Encrypt or decrypt files locally. The initial limit is 10 MB; no storage permission is required.",
              "Cifra ou decifra ficheiros localmente. O limite inicial é 10 MB; não é necessária permissão de armazenamento."),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = encrypt, onClick = { encrypt = true }, label = { Text(t("Encrypt", "Cifrar")) })
            FilterChip(selected = !encrypt, onClick = { encrypt = false }, label = { Text(t("Decrypt", "Decifrar")) })
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(t("Password", "Palavra-passe")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = {
                externalActivityActive = true
                onExternalActivityChanged(true)
                openLauncher.launch(arrayOf("*/*"))
            },
            enabled = password.isNotEmpty()
        ) {
            Text(t("Choose file", "Escolher ficheiro"))
        }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        pendingBytes?.let {
            Text(t("Result ready. Complete the system save dialog.", "Resultado pronto. Conclui a janela de gravação do sistema."))
        }
        Text(
            t("Files use the raw AES format (binary output, no Base64 expansion). Password and temporary byte buffers are cleared after processing.",
              "Os ficheiros usam o formato AES raw (saída binária, sem expansão Base64). A palavra-passe e os buffers temporários são limpos após o processamento."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun readLimitedFile(context: Context, uri: Uri): ByteArray {
    val limit = 10 * 1024 * 1024
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    context.contentResolver.openInputStream(uri)?.use { input ->
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) {
                buffer.fill(0)
                throw IllegalArgumentException("Files are limited to 10 MB in this version.")
            }
            output.write(buffer, 0, count)
        }
    } ?: throw IllegalStateException("Could not open the selected file.")
    buffer.fill(0)
    return output.toByteArray()
}

@Composable
private fun HistoryPage(
    records: List<HistoryRecord>,
    pt: Boolean,
    onClear: () -> Unit,
    onLoad: (HistoryRecord) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (pt) "Histórico desta sessão" else "Session history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClear, enabled = records.isNotEmpty()) { Text(if (pt) "Limpar" else "Clear") }
        }
        Text(
            if (pt) "O histórico fica apenas na memória e desaparece quando a app é fechada."
            else "History stays in memory only and disappears when the app is closed.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (records.isEmpty()) {
            Text(if (pt) "Ainda não existem operações." else "No operations yet.")
        }
        records.forEach { record ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onLoad(record) },
                shape = RoundedCornerShape(17.dp)
            ) {
                Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.operation.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                    Text(record.inputPreview, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(record.outputPreview, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                    Text(Date(record.createdAt).toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    language: String,
    theme: String,
    clipboardSeconds: Int,
    t: (String, String) -> String,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    onClipboardSecondsChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(t("Settings", "Definições"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Language", "Idioma"), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language == "system", onClick = { onLanguageChange("system") }, label = { Text(t("System", "Sistema")) })
                FilterChip(selected = language == "en", onClick = { onLanguageChange("en") }, label = { Text("English") })
                FilterChip(selected = language == "pt", onClick = { onLanguageChange("pt") }, label = { Text("Português") })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Appearance", "Aparência"), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = theme == "system", onClick = { onThemeChange("system") }, label = { Text(t("System", "Sistema")) })
                FilterChip(selected = theme == "light", onClick = { onThemeChange("light") }, label = { Text(t("Light", "Claro")) })
                FilterChip(selected = theme == "dark", onClick = { onThemeChange("dark") }, label = { Text(t("Dark", "Escuro")) })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Clear copied text after", "Limpar texto copiado após"), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30, 60).forEach { seconds ->
                    FilterChip(
                        selected = clipboardSeconds == seconds,
                        onClick = { onClipboardSecondsChange(seconds) },
                        label = { Text(if (seconds == 0) t("Never", "Nunca") else seconds.toString() + "s") }
                    )
                }
            }
            Text(t("Default: 30 seconds. Clipboard items are marked as sensitive on Android 13 and later.",
                "Padrão: 30 segundos. Os itens da área de transferência são marcados como sensíveis no Android 13 e posteriores."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ElevatedCard(shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Privacy and open-source notices", "Privacidade e licenças open source"), fontWeight = FontWeight.SemiBold)
                Text(
                    t("CipherPocket is offline-only, does not save operation history to disk and disables Android backup. Project code is MIT licensed. OpenSSL 3.5 is distributed under Apache License 2.0.",
                      "O CipherPocket funciona offline, não guarda o histórico em disco e desativa as cópias de segurança Android. O código do projeto usa a licença MIT. O OpenSSL 3.5 é distribuído sob Apache License 2.0."),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun OutputPanel(
    output: String,
    emptyText: String,
    t: (String, String) -> String,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(t("Output", "Resultado"), fontWeight = FontWeight.SemiBold)
            Row {
                TextButton(onClick = onCopy, enabled = output.isNotEmpty()) { Text(t("Copy", "Copiar")) }
                TextButton(onClick = onShare, enabled = output.isNotEmpty()) { Text(t("Share", "Partilhar")) }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = output.ifEmpty { emptyText },
                modifier = Modifier.padding(14.dp),
                color = if (output.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ScreenSecurity(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val activity = view.context.findActivity()
        if (enabled) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
