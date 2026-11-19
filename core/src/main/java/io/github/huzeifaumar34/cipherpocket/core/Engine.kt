package io.github.huzeifaumar34.cipherpocket.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Engine {
    private val bridge = NativeBridge()

    init {
        check(bridge.nativeApiVersion() == API_VERSION) {
            "CipherPocket native API version mismatch."
        }
    }

    suspend fun execute(
        operation: Op,
        input: ByteArray,
        argument1: ByteArray = byteArrayOf(),
        argument2: ByteArray = byteArrayOf()
    ): ByteArray = withContext(Dispatchers.Default) {
        val response = bridge.nativeExecute(
            operation.id,
            input,
            argument1,
            argument2
        )
        decodeNativeResponse(response)
    }

    private companion object {
        const val API_VERSION = 1
    }
}

internal class EngineException(message: String) : IllegalStateException(message)

/**
 * Wire response: first byte is 0 (success) or 1 (error); remaining bytes are payload or UTF-8 error.
 */
internal fun decodeNativeResponse(response: ByteArray): ByteArray {
    if (response.isEmpty()) {
        throw EngineException("The native engine returned an empty response.")
    }

    return when (response[0].toInt()) {
        0 -> response.copyOfRange(1, response.size)
        1 -> throw EngineException(
            response.copyOfRange(1, response.size).toString(Charsets.UTF_8)
                .ifBlank { "The native operation failed." }
        )
        else -> throw EngineException("The native engine returned an invalid status.")
    }
}
