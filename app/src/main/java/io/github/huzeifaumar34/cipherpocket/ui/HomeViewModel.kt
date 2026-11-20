package io.github.huzeifaumar34.cipherpocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.huzeifaumar34.cipherpocket.core.Engine
import io.github.huzeifaumar34.cipherpocket.core.Op
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryRecord(
    val operation: String,
    val inputPreview: String,
    val outputPreview: String,
    val createdAt: Long
)

data class HomeUiState(
    val input: String = "",
    val output: String = "",
    val argument1: String = "",
    val selectedOperation: Op = Op.B64_ENC,
    val isWorking: Boolean = false,
    val error: String? = null,
    val history: List<HistoryRecord> = emptyList()
)

class HomeViewModel : ViewModel() {
    private val engine = Engine()
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState = mutableUiState.asStateFlow()

    fun onInputChanged(value: String) {
        mutableUiState.update { it.copy(input = value, error = null) }
    }

    fun onArgumentChanged(value: String) {
        mutableUiState.update { it.copy(argument1 = value, error = null) }
    }

    fun selectOperation(operation: Op) {
        mutableUiState.update { it.copy(selectedOperation = operation, argument1 = "", output = "", error = null) }
    }

    fun swapInputOutput() {
        mutableUiState.update { it.copy(input = it.output, output = it.input, error = null) }
    }

    fun clearInput() {
        mutableUiState.update { it.copy(input = "", output = "", error = null) }
    }

    fun clearHistory() {
        mutableUiState.update { it.copy(history = emptyList()) }
    }

    /** Called when the app goes to the background: drop decrypted previews and any typed secret. */
    fun onBackgrounded() {
        mutableUiState.update { it.copy(history = emptyList(), argument1 = "") }
    }

    fun loadHistory(record: HistoryRecord) {
        mutableUiState.update { it.copy(input = record.inputPreview, output = record.outputPreview, error = null) }
    }

    fun runSelected() {
        val state = mutableUiState.value
        runOperation(state.selectedOperation, state.input, state.argument1)
    }

    fun runOperation(
        operation: Op,
        input: String,
        argument1: String = "",
        clearSensitiveArgument: Boolean = operation in setOf(
            Op.AES_ENC, Op.AES_DEC, Op.XOR_ENC, Op.XOR_DEC, Op.VIGENERE_ENC, Op.VIGENERE_DEC
        )
    ) {
        val inputBytes = input.toByteArray(StandardCharsets.UTF_8)
        val argumentBytes = argument1.toByteArray(StandardCharsets.UTF_8)
        mutableUiState.update {
            it.copy(
                selectedOperation = operation,
                input = input,
                isWorking = true,
                error = null,
                output = "",
                argument1 = if (clearSensitiveArgument) "" else it.argument1
            )
        }
        viewModelScope.launch {
            var resultBytes: ByteArray? = null
            try {
                val completed = engine.execute(operation, inputBytes, argumentBytes)
                resultBytes = completed
                val resultText = String(completed, StandardCharsets.UTF_8)
                mutableUiState.update { current ->
                    val entry = HistoryRecord(
                        operation = operation.name,
                        inputPreview = input.take(240),
                        outputPreview = resultText.take(500),
                        createdAt = System.currentTimeMillis()
                    )
                    current.copy(
                        output = resultText,
                        isWorking = false,
                        history = (listOf(entry) + current.history).take(100),
                        argument1 = if (clearSensitiveArgument) "" else current.argument1
                    )
                }
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isWorking = false,
                        error = error.message ?: "The operation could not be completed.",
                        argument1 = if (clearSensitiveArgument) "" else it.argument1
                    )
                }
            } finally {
                inputBytes.fill(0)
                argumentBytes.fill(0)
                resultBytes?.fill(0)
            }
        }
    }

    suspend fun executeRawFile(encrypt: Boolean, input: ByteArray, password: String): ByteArray {
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        return try {
            engine.execute(
                if (encrypt) Op.AES_ENC_RAW else Op.AES_DEC_RAW,
                input,
                passwordBytes
            )
        } finally {
            passwordBytes.fill(0)
        }
    }
}
