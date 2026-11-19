package io.github.huzeifaumar34.cipherpocket.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineProtocolTest {
    @Test
    fun successResponseReturnsPayloadWithoutStatusByte() {
        val response = byteArrayOf(0, 65, 66, 67)
        assertArrayEquals(byteArrayOf(65, 66, 67), decodeNativeResponse(response))
    }

    @Test
    fun errorResponseBecomesEngineException() {
        val response = byteArrayOf(1) + "Invalid Base64 input.".toByteArray()
        val error = assertThrows(EngineException::class.java) {
            decodeNativeResponse(response)
        }
        assertEquals("Invalid Base64 input.", error.message)
    }

    @Test
    fun emptyResponseIsRejected() {
        assertThrows(EngineException::class.java) {
            decodeNativeResponse(byteArrayOf())
        }
    }

    @Test
    fun unknownStatusIsRejected() {
        assertThrows(EngineException::class.java) {
            decodeNativeResponse(byteArrayOf(7, 1, 2))
        }
    }
}
