package com.example.reconocimiento

import com.example.reconocimiento.domain.*
import com.example.reconocimiento.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AttendanceActionsTest {
    private class Device : DeviceOperations {
        var captures=0;var positions=0;var signatures=0
        var confirmed=false;var signedBeforeFace=false
        val captureModes=mutableListOf<FaceCaptureMode>()
        override suspend fun captureFace(mode: FaceCaptureMode): FaceCapture {captureModes.add(mode);signedBeforeFace=signatures>0&&confirmed;captures++;return FaceCapture(List(3){FloatArray(512){i->if(i==0)1f else 0f}},"")}
        override suspend fun currentPosition(): Position {positions++;return Position(0.0,positions/100000.0,5f,0,false)}
        override fun publicKey()="public-key"
        override suspend fun sign(challenge: Challenge): String {signatures++;return "signed-${challenge.id}"}
        override suspend fun confirmFaceForFingerprint(){confirmed=true}
    }
    private class Repository : AttendanceRepository {
        val challenges=mutableListOf<Pair<String,Position?>>()
        var blocked=false;var marks=0;var bindings=0;var lastCapture: FaceCapture?=null;var lastSignature: String?=null
        var savedChange: PersonChange?=null
        var faceRequired=false
        override suspend fun challenge(purpose: String,position: Position?,publicKey: String?,dni: String?): Challenge {
            if(blocked)throw AppFailure("Fuera de zona")
            challenges.add(purpose to position);return Challenge(challenges.size.toString(),"nonce",purpose,faceRequired=faceRequired)
        }
        override suspend fun mark(challenge: Challenge,signature: String?,capture: FaceCapture?): Attendance {
            marks++;lastCapture=capture;lastSignature=signature;return Attendance("Ana Pérez","12345678","2026-10-31T12:00:00Z",challenge.purpose,5.0,null)
        }
        override suspend fun bind(challenge: Challenge,signature: String,capture: FaceCapture){bindings++;lastCapture=capture;lastSignature=signature}
        override suspend fun people()=emptyList<Person>()
        override suspend fun attendance(date: String?)=emptyList<Attendance>()
        override suspend fun site()=Site()
        override suspend fun configure(site: Site)=site
        override suspend fun delete(dni: String){}
        override suspend fun unbind(dni: String){}
        override suspend fun save(change: PersonChange): Person {savedChange=change;return Person(change.dni,change.name,change.photo,true,change.fingerprint!=null)}
    }
    @Test fun facialUsesFreshLocationAfterCaptureAndDoesNotRequireFingerprint()=runBlocking {
        val repo=Repository();val device=Device();AttendanceActions(repo,device).markFacial()
        assertEquals(1,device.captures);assertEquals(2,device.positions);assertEquals(0,device.signatures)
        assertNotEquals(repo.challenges.first().second,repo.challenges.last().second)
        assertEquals(1,repo.marks);assertNotNull(repo.lastCapture);assertNull(repo.lastSignature)
        assertEquals(listOf(FaceCaptureMode.VERIFICATION),device.captureModes)
    }
    @Test fun fingerprintDoesNotRequireAnotherFaceCapture()=runBlocking {
        val repo=Repository();val device=Device();AttendanceActions(repo,device).markFingerprint()
        assertEquals(0,device.captures);assertEquals(1,device.signatures);assertEquals("HUELLA",repo.challenges.single().first)
        assertNull(repo.lastCapture);assertNotNull(repo.lastSignature)
    }
    @Test fun outsideTheZoneDoesNotOpenTheCameraOrMark()=runBlocking {
        val repo=Repository().apply {blocked=true};val device=Device()
        try {AttendanceActions(repo,device).markFacial();fail("Debe bloquear la marcación")}catch(_: AppFailure){}
        assertEquals(0,device.captures);assertEquals(0,repo.marks)
    }
    @Test fun bindingVerifiesFaceAndSignatureWithoutRegisteringAttendance()=runBlocking {
        val repo=Repository();val device=Device()
        AttendanceActions(repo,device).bind(Person("12345678","Ana Pérez","",true,false))
        assertEquals(1,device.captures);assertEquals(1,device.signatures);assertEquals(1,repo.bindings);assertEquals(0,repo.marks)
    }
    @Test fun newFingerprintIsPreparedButNotPersistedBeforeSaving()=runBlocking {
        val repo=Repository();val device=Device()
        val proof=AttendanceActions(repo,device).prepareFingerprint("12345678",null)
        assertEquals("ALTA_HUELLA",proof.challenge.purpose);assertNull(proof.capture)
        assertEquals(0,device.captures);assertEquals(1,device.signatures);assertEquals(0,repo.bindings);assertNull(repo.savedChange)
    }
    @Test fun editingFingerprintVerifiesExistingFaceWithoutMutatingTheProfile()=runBlocking {
        val repo=Repository().apply {faceRequired=true};val device=Device()
        val proof=AttendanceActions(repo,device).prepareFingerprint("12345678",Person("12345678","Ana Pérez","",true,true,"version"))
        assertEquals("EDITAR_HUELLA",proof.challenge.purpose);assertNotNull(proof.capture)
        assertEquals(1,device.captures);assertEquals(1,device.signatures);assertEquals(0,repo.bindings)
        assertTrue(device.signedBeforeFace)
        assertEquals(listOf(FaceCaptureMode.VERIFICATION),device.captureModes)
    }
    @Test fun sameLinkedKeyRenewsFingerprintWithoutACamera()=runBlocking {
        val repo=Repository();val device=Device()
        val proof=AttendanceActions(repo,device).prepareFingerprint("12345678",Person("12345678","Ana Pérez","",true,true,"version"))
        assertNull(proof.capture);assertEquals(0,device.captures);assertEquals(1,device.signatures);assertFalse(device.confirmed)
    }
    @Test fun fingerprintOnlyProfileCanEnrollWithoutBeingForcedToCaptureFace()=runBlocking {
        val repo=Repository();val device=Device()
        val proof=AttendanceActions(repo,device).prepareFingerprint("12345678",Person("12345678","Ana Pérez","",false,false,"version"))
        assertEquals("EDITAR_HUELLA",proof.challenge.purpose);assertNull(proof.capture)
        assertEquals(0,device.captures);assertEquals(1,device.signatures)
    }
    @Test fun editingNameAndPhotoDoesNotRequireASecondFaceCapture()=runBlocking {
        val repo=Repository();val vm=AppViewModel(repo)
        vm.openEditor(Person("12345678","Ana Pérez","foto-anterior",true,true,"version"))
        vm.draft {it.copy(name="Ana María Pérez",photo="foto-nueva")}
        vm.savePerson()
        val sent=repo.savedChange!!
        assertEquals("Ana María Pérez",sent.name);assertEquals("foto-nueva",sent.photo)
        assertNull(sent.capture);assertNull(sent.fingerprint);assertFalse(sent.removeFingerprint)
        assertEquals("version",sent.original!!.version)
    }
}
