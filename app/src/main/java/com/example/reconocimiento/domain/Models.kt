package com.example.reconocimiento.domain

const val FACE_MODEL = "facenet512-fp16-v1"
const val CAPTURE_POLICY = "frontal-unico-v6"

data class Person(val dni: String, val name: String, val photo: String, val face: Boolean, val fingerprint: Boolean,val version: String="",val faceSource: String="CAMARA")
data class Site(val name: String = "Sede principal", val latitude: Double? = null, val longitude: Double? = null,
                val radius: Double = 100.0, val network: String = "", val threshold: Double = 0.8, val revision: Long = 0,val version: String="")
data class Position(val latitude: Double, val longitude: Double, val accuracy: Float, val ageMs: Long, val mock: Boolean)
data class FaceCapture(val vectors: List<FloatArray>, val photo: String, val source: String="CAMARA",val visuallyReviewed: Boolean=false,val profilePhoto: String=photo) {
    fun average(): FloatArray {
        require(vectors.isNotEmpty())
        val out = FloatArray(512) { i -> vectors.sumOf { it[i].toDouble() }.toFloat() / vectors.size }
        val norm = kotlin.math.sqrt(out.sumOf { it.toDouble() * it }).toFloat()
        require(norm > 0)
        return FloatArray(out.size) { out[it] / norm }
    }
}
data class Challenge(val id: String, val nonce: String, val purpose: String, val dni: String="", val name: String="",val faceRequired: Boolean=false) {
    fun message() = "RECONOCIMIENTO/1|$id|$nonce|$purpose"
}
data class Attendance(val name: String, val dni: String, val instant: String, val method: String, val distance: Double, val similarity: Double?,
                      val id: Long=0, val photo: String="", val referencePhoto: String="",val threshold: Double?=null,
                      val accuracy: Double=0.0,val ip: String="",val referenceKind: String="FICHA")
data class NetworkInfo(val serverIp: String, val deviceIp: String="")
data class ZoneCheck(val allowed: Boolean,val message: String,val distance: Double?,val accuracy: Double,val radius: Double,val networkAllowed: Boolean,val code: String="") {
    fun explanation(): String = when {
        allowed -> "Estás dentro de la zona autorizada."
        code=="IP_NO_PERMITIDA" || !networkAllowed -> "Esta conexión no está autorizada para marcar."
        code=="UBICACION_SIMULADA" -> "Desactiva la ubicación simulada y vuelve a comprobar."
        code=="ZONA_SIN_CONFIGURAR" -> "Guarda primero la ubicación de la sede."
        code=="UBICACION_ANTIGUA" -> "Actualiza tu ubicación y vuelve a comprobar."
        code=="UBICACION_IMPRECISA" -> "El GPS aún no tiene suficiente precisión. Busca mejor señal."
        distance!=null&&distance-accuracy>radius -> "Estás fuera de la zona permitida. Acércate a la sede."
        code=="FUERA_DE_ZONA" -> "El margen del GPS no permite confirmar que estés dentro. Busca mejor señal y vuelve a comprobar."
        else -> message
    }
}
data class TrialSummary(val total: Long=0,val correct: Long=0,val falseReject: Long=0,val falseAccept: Long=0,val wrongIdentity: Long=0)
data class FaceTestResult(val outcome: String,val score: Double,val expectedScore: Double?,val threshold: Double,val matchedDni: String?,val summary: TrialSummary,val expectedDni: String?=null)
class AppFailure(message: String, val code: String = "ERROR") : Exception(message)
data class FingerprintProof(val challenge: Challenge,val signature: String,val capture: FaceCapture?=null)
data class PersonChange(val dni: String,val name: String,val photo: String,val capture: FaceCapture?=null,
                        val fingerprint: FingerprintProof?=null,val removeFingerprint: Boolean=false,val original: Person?=null)

interface AttendanceRepository {
    suspend fun people(): List<Person>
    suspend fun ensureDniAvailable(dni: String) {
        if(people().any {it.dni==dni})throw AppFailure("Ese DNI ya está registrado. Abre su ficha para editarlo.","DNI_DUPLICADO")
    }
    suspend fun validateFaceEnrollment(change: PersonChange): Unit = throw AppFailure("No se pudo validar el registro facial con el servidor.")
    suspend fun save(change: PersonChange): Person
    suspend fun delete(dni: String)
    suspend fun unbind(dni: String)
    suspend fun site(): Site
    suspend fun configure(site: Site): Site
    suspend fun attendance(date: String?=null): List<Attendance>
    suspend fun attendanceReport(from: String?,to: String?): List<Attendance> = throw AppFailure("No se pudo consultar el reporte.")
    suspend fun attendanceWorkbook(from: String?,to: String?): ByteArray = throw AppFailure("No se pudo descargar el reporte.")
    suspend fun network(): NetworkInfo = throw AppFailure("No se pudo consultar la conexión.")
    suspend fun checkZone(position: Position): ZoneCheck = throw AppFailure("No se pudo comprobar la ubicación.")
    suspend fun testFace(capture: FaceCapture,expectedDni: String?): FaceTestResult = throw AppFailure("No se pudo ejecutar la prueba facial.")
    suspend fun trialSummary(): TrialSummary = TrialSummary()
    suspend fun challenge(purpose: String, position: Position? = null, publicKey: String? = null, dni: String? = null): Challenge
    suspend fun bind(challenge: Challenge, signature: String, capture: FaceCapture)
    suspend fun mark(challenge: Challenge, signature: String? = null, capture: FaceCapture? = null): Attendance
}
