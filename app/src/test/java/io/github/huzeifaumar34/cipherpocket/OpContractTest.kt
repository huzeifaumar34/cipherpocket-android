package io.github.huzeifaumar34.cipherpocket

import io.github.huzeifaumar34.cipherpocket.core.Op
import org.junit.Assert.assertEquals
import org.junit.Test

class OpContractTest {
    @Test
    fun operationIdsStayUniqueAndContinuous() {
        val ids = Op.entries.map { it.id }
        assertEquals((1..28).toList(), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun base64OperationIdsMatchProjectContract() {
        assertEquals(5, Op.B64_ENC.id)
        assertEquals(6, Op.B64_DEC.id)
    }
}
