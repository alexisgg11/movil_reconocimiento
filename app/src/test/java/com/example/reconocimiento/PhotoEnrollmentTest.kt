package com.example.reconocimiento

import com.example.reconocimiento.domain.*
import com.example.reconocimiento.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PhotoEnrollmentTest {
    @Test fun cropRemainsInsidePortraitLandscapeAndZoomedImages() {
        for((width,height) in listOf(800 to 1200,1200 to 800,512 to 512)) {
            for(zoom in listOf(1f,2f,4f)) {
                val crop=PhotoCrop(zoom).moved(-10000f,10000f,400f,width,height)
                val w=crop.window(width,height)
                assertTrue(w.side>0);assertTrue(w.left>=0);assertTrue(w.top>=0)
                assertTrue(w.left+w.side<=width);assertTrue(w.top+w.side<=height)
                assertEquals((minOf(width,height)/zoom).toInt(),w.side)
            }
        }
        assertEquals(CropWindow(200,0,800),PhotoCrop().window(1200,800))
    }
    @Test fun frontalPhotoRulesRejectTurnTiltMissingLandmarksAndClosedEyes() {
        fun valid(yaw: Float=0f,pitch: Float=0f,roll: Float=0f,left: Float?=.95f,right: Float?=.95f,complete: Boolean=true)=
            EnrollmentPhotoRules.validate(yaw,pitch,roll,left,right,complete)
        valid()
        for(action in listOf<()->Unit>({valid(yaw=8f)},{valid(pitch=11f)},{valid(roll=8f)},
            {valid(yaw=Float.NaN)},{valid(left=null)},{valid(right=.5f)},{valid(complete=false)})) {
            try {action();fail("Una foto inválida no debe aceptarse")}catch(_: AppFailure){}
        }
    }
    private class Repository : AttendanceRepository {
        var unavailable=false
        var previewRejected=false
        var queries=0
        var preview: PersonChange?=null
        override suspend fun people()=emptyList<Person>()
        override suspend fun ensureDniAvailable(dni: String){queries++;if(unavailable)throw AppFailure("DNI existente","DNI_DUPLICADO")}
        override suspend fun validateFaceEnrollment(change: PersonChange){if(previewRejected)throw AppFailure("Rostro duplicado","ROSTRO_DUPLICADO");preview=change}
        override suspend fun save(change: PersonChange)=error("No escribir en estas pruebas")
        override suspend fun delete(dni: String){}
        override suspend fun unbind(dni: String){}
        override suspend fun site()=Site()
        override suspend fun configure(site: Site)=site
        override suspend fun attendance(date: String?)=emptyList<Attendance>()
        override suspend fun challenge(purpose: String,position: Position?,publicKey: String?,dni: String?)=error("Sin reto")
        override suspend fun bind(challenge: Challenge,signature: String,capture: FaceCapture){}
        override suspend fun mark(challenge: Challenge,signature: String?,capture: FaceCapture?)=error("Sin marcación")
    }
    private fun editor(repo: Repository)=AppViewModel(repo).apply {openEditor();draft {it.copy(dni="12345678",name="Ana Pérez")}}
    @Test fun existingDniOrConnectionFailureDoesNotAdvanceTheForm()=runBlocking {
        val repo=Repository().apply {unavailable=true};val vm=editor(repo)
        try {vm.nextPerson();fail("No debe avanzar")}catch(_: AppFailure){}
        assertEquals(0,vm.state.value.form!!.tab);assertNull(vm.state.value.form!!.verifiedDni)
        repo.unavailable=false;vm.nextPerson()
        assertEquals(1,vm.state.value.form!!.tab);assertEquals("12345678",vm.state.value.form!!.verifiedDni)
        assertEquals(2,repo.queries)
    }
    @Test fun duplicateFaceDoesNotReplaceTheCurrentDraftButValidImportSuppliesBothPhotos()=runBlocking {
        val repo=Repository();val vm=editor(repo)
        val capture=FaceCapture(listOf(FloatArray(512){if(it==0)1f else 0f}),"rostro","GALERIA",true,"recorte")
        repo.previewRejected=true
        try {vm.acceptFace(capture);fail("No debe aceptar el duplicado")}catch(_: AppFailure){}
        assertNull(vm.state.value.form!!.capture);assertEquals("",vm.state.value.form!!.photo)
        repo.previewRejected=false;vm.acceptFace(capture)
        assertEquals("recorte",vm.state.value.form!!.photo);assertTrue(vm.state.value.form!!.hasFace)
        assertEquals(1,repo.preview!!.capture!!.vectors.size);assertEquals("GALERIA",repo.preview!!.capture!!.source)
        assertEquals("rostro",vm.state.value.form!!.capture!!.photo)
    }
}
