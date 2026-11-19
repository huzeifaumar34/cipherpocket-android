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

data class HomeUiState(
    val input: String = "",
    val output: String = "",
    val isWorking: Boolean = false,
    val error: String? = null
)

class HomeViewModel : ViewModel() {
    private val engine = Engine()
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState = mutableUiState.asStateFlow()

    fun onInputChanged(value: String) {
        mutableUiState.update { it.copy(input = value, error = null) }
    }

    fun encodeBase64() = runOperation(Op.B64_ENC)

    fun decodeBase64() = runOperation(Op.B64_DEC)

    private fun runOperation(operation: Op) {
        val inputBytes = mutableUiState.value.input.toByteArray(StandardCharsets.UTF_8)
        viewModelScope.launch {
            mutableUiState.update { it.copy(isWorking = true, error = null, output = "") }
            try {
                val result = engine.execute(operation, inputBytes)
                mutableUiState.update {
                    it.copy(output = String(result, StandardCharsets.UTF_8), isWorking = false)
                }
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isWorking = false,
                        error = error.message ?: "The operation could not be completed."
                    )
                }
            } finally {
                inputBytes.fill(0)
            }
        }
    }
}
