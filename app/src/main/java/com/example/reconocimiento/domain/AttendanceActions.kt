package com.example.reconocimiento.domain

interface DeviceOperations {
    suspend fun captureFace(mode: FaceCaptureMode=FaceCaptureMode.ENROLLMENT): FaceCapture
    suspend fun currentPosition(): Position
    fun publicKey(): String
    suspend fun sign(challenge: Challenge): String
    suspend fun confirmFaceForFingerprint() {}
}

enum class AttendanceStage { LOCATION, FACE_CAPTURE, FACE_COMPARE, FINGERPRINT }

class AttendanceActions(private val repository: AttendanceRepository,private val device: DeviceOperations,
                        private val stage: (AttendanceStage)->Unit = {}) {
    suspend fun markFacial(): MarkingResult {
        stage(AttendanceStage.LOCATION)
        repository.challenge("FACIAL",device.currentPosition())
        stage(AttendanceStage.FACE_CAPTURE)
        val capture=device.captureFace(FaceCaptureMode.VERIFICATION)
        stage(AttendanceStage.LOCATION)
        val challenge=repository.challenge("FACIAL",device.currentPosition())
        stage(AttendanceStage.FACE_COMPARE)
        return repository.markForResult(challenge,capture=capture)
    }
    suspend fun markFingerprint(): MarkingResult {
        stage(AttendanceStage.LOCATION)
        val key=device.publicKey()
        val challenge=repository.challenge("HUELLA",device.currentPosition(),key)
        stage(AttendanceStage.FINGERPRINT)
        return repository.markForResult(challenge,signature=device.sign(challenge))
    }
    suspend fun bind(person: Person) {
        val key=device.publicKey()
        val capture=device.captureFace(FaceCaptureMode.VERIFICATION)
        val challenge=repository.challenge("VINCULAR",publicKey=key,dni=person.dni)
        repository.bind(challenge,device.sign(challenge),capture)
    }
    suspend fun prepareFingerprint(dni: String,original: Person?): FingerprintProof {
        val key=device.publicKey()
        val challenge=repository.challenge(if(original==null) "ALTA_HUELLA" else "EDITAR_HUELLA",publicKey=key,dni=dni)
        val signature=device.sign(challenge)
        val capture=if(challenge.faceRequired){device.confirmFaceForFingerprint();device.captureFace(FaceCaptureMode.VERIFICATION)} else null
        return FingerprintProof(challenge,signature,capture)
    }
}
