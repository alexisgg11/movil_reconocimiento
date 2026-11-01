package com.example.reconocimiento

import com.example.reconocimiento.domain.*
import org.junit.Assert.*
import org.junit.Test

class FaceAttributesTest {
    private fun attributes(eyeglasses: Float=0f,mask: Float=0f,sunglasses: Float=0f)=FaceAttributes(.99f,.99f,eyeglasses,mask,sunglasses)
    @Test fun independentHeadsRejectClearLensesEvenWhenBothEyesAreOpen() {
        assertTrue(attributes(eyeglasses=.99f).blockingMessage()!!.contains("lentes"))
        assertTrue(attributes(sunglasses=.99f).blockingMessage()!!.contains("lentes"))
        assertTrue(attributes(mask=.99f).blockingMessage()!!.contains("mascarilla"))
        assertNotNull(attributes(eyeglasses=.9f,mask=.9f).blockingMessage())
    }
    @Test fun uncertaintyDoesNotBecomeAnAutomaticApproval() {
        assertNull(attributes(eyeglasses=.01f,mask=.03f,sunglasses=.02f).blockingMessage())
        assertNotNull(attributes(eyeglasses=.3f).blockingMessage())
        assertNotNull(attributes(mask=.4f).blockingMessage())
        assertNotNull(attributes(sunglasses=.49f).blockingMessage())
    }
    @Test fun invalidOutputsAreRejectedRatherThanInterpretedAsNoGlasses() {
        for(invalid in listOf(Float.NaN,Float.POSITIVE_INFINITY,-.01f,1.01f)) {
            val value=attributes(eyeglasses=invalid)
            assertNotNull(value.blockingMessage())
            try {value.requireClear();fail("No debe aprobar una lectura inválida")}catch(e: AppFailure){assertEquals("ROSTRO_CUBIERTO",e.code)}
        }
    }
}
