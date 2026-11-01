package com.example.reconocimiento

import com.example.reconocimiento.data.PersonPayloads
import com.example.reconocimiento.domain.*
import org.junit.Assert.*
import org.junit.Test

class PersonPayloadsTest {
    private val vectors=List(3){FloatArray(512){i->if(i==0)1f else 0f}}
    private val original=Person("12345678","Ana Pérez","original",true,true,"version-actual")
    private fun change()=PersonChange(original.dni,original.name,"foto-ficha",FaceCapture(vectors,"foto-rostro"),
        FingerprintProof(Challenge("reto","nonce","EDITAR_HUELLA"),"firma",FaceCapture(vectors,"prueba")),false,original)

    @Test fun editingFacePreviewContainsOnlyEnrollmentFieldsAndKeepsVersionOutside() {
        val body=PersonPayloads.validation(change());val enrollment=body.getJSONObject("registro")
        assertEquals("version-actual",body.getString("version"));assertEquals(original.dni,enrollment.getString("dni"))
        assertEquals("foto-ficha",enrollment.getString("foto"));assertEquals("foto-rostro",enrollment.getString("fotoRostro"))
        assertEquals(3,enrollment.getJSONArray("vectores").length())
        assertFalse(enrollment.has("version"));assertFalse(enrollment.has("eliminarHuella"));assertFalse(enrollment.has("huella"))
        assertEquals(setOf("dni","nombre","foto","fotoRostro","modelo","vectores","origenRostro","revisionVisual"),enrollment.keys().asSequence().toSet())
    }
    @Test fun finalEditingPayloadKeepsRetirementAndFingerprintProofInsteadOfLeakingDni() {
        val body=PersonPayloads.save(change())
        assertFalse(body.has("dni"));assertEquals("version-actual",body.getString("version"))
        assertFalse(body.getBoolean("eliminarHuella"));assertEquals("reto",body.getJSONObject("huella").getString("reto"))
        assertEquals(512,body.getJSONObject("huella").getJSONArray("vector").length())
        val retirement=PersonPayloads.save(change().copy(fingerprint=null,removeFingerprint=true))
        assertTrue(retirement.getBoolean("eliminarHuella"));assertFalse(retirement.has("huella"))
        val creation=PersonPayloads.save(change().copy(original=null))
        assertEquals(original.dni,creation.getString("dni"));assertFalse(creation.has("version"));assertFalse(creation.has("eliminarHuella"))
    }
}
