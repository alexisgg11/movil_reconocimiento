package com.example.reconocimiento.data

import android.os.Build
import com.example.reconocimiento.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

class HttpAttendanceRepository : AttendanceRepository {
    private val base = if (Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE.contains("ranchu"))
        "http://10.0.2.2:8082/api" else "http://127.0.0.1:8082/api"

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): String = requestBytes(path,method,body).toString(Charsets.UTF_8)
    private suspend fun requestBytes(path: String, method: String = "GET", body: JSONObject? = null): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL("$base$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 7000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val bytes = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                val error = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull()
                throw AppFailure(error?.optString("mensaje")?.takeIf { it.isNotBlank() } ?: "El servidor rechazó la operación.", error?.optString("codigo") ?: "HTTP_$code")
            }
            bytes
        } catch (e: IOException) {
            throw AppFailure("No se pudo conectar con el servidor. Comprueba la conexión del teléfono y que el servidor esté encendido.", "CONEXION")
        } finally { connection.disconnect() }
    }
    private fun person(j: JSONObject) = Person(j.getString("dni"), j.getString("nombre"), j.optString("foto"), j.getBoolean("rostro"), j.getBoolean("huella"),j.optString("version"),j.optString("origenRostro","CAMARA"))
    private fun site(j: JSONObject) = Site(j.getString("nombre"), if(j.isNull("latitud")) null else j.getDouble("latitud"), if(j.isNull("longitud")) null else j.getDouble("longitud"), j.getDouble("radio"), j.optString("red"), j.getDouble("umbral"), j.getLong("revision"),j.optString("version"))
    private fun attendance(j: JSONObject) = Attendance(j.getString("nombre"), j.getString("dni"), j.getString("instante"), j.getString("metodo"), j.getDouble("distancia"), if(j.isNull("similitud")) null else j.getDouble("similitud"),j.getLong("id"),j.optString("foto"),j.optString("fotoRegistro"),if(j.isNull("umbral")) null else j.getDouble("umbral"),j.optDouble("precision"),j.optString("ip"),j.optString("tipoFotoRegistro","FICHA"))
    private fun vector(v: FloatArray) = JSONArray(v.map { it.toDouble() })
    override suspend fun people(): List<Person> { val a=JSONArray(request("/personas")); return List(a.length()) { person(a.getJSONObject(it)) } }
    override suspend fun ensureDniAvailable(dni: String) { request("/personas/$dni/disponibilidad") }
    override suspend fun validateFaceEnrollment(change: PersonChange) {
        request("/personas/validar","POST",PersonPayloads.validation(change))
    }
    override suspend fun save(change: PersonChange): Person {
        val j=PersonPayloads.save(change)
        return person(JSONObject(request(if(change.original==null) "/personas" else "/personas/${change.dni}",if(change.original==null) "POST" else "PUT",j)))
    }
    override suspend fun delete(dni: String) { request("/personas/$dni","DELETE") }
    override suspend fun unbind(dni: String) { request("/personas/$dni/dispositivo","DELETE") }
    override suspend fun site() = site(JSONObject(request("/configuracion")))
    override suspend fun configure(site: Site) = site(JSONObject(request("/configuracion","PUT",JSONObject()
        .put("nombre",site.name).put("latitud",site.latitude).put("longitud",site.longitude).put("radio",site.radius)
        .put("red",site.network.trim()).put("umbral",site.threshold).put("revision",site.revision).put("version",site.version))))
    override suspend fun attendance(date: String?): List<Attendance> { val a=JSONArray(request("/asistencias"+(date?.let {"?fecha=${java.time.LocalDate.parse(it)}"} ?: "")));return List(a.length()) { attendance(a.getJSONObject(it)) } }
    private fun reportQuery(from: String?,to: String?): String {
        if(from==null&&to==null)return ""
        if(from==null||to==null)throw AppFailure("Selecciona las dos fechas del reporte.")
        return "?desde=${java.time.LocalDate.parse(from)}&hasta=${java.time.LocalDate.parse(to)}"
    }
    override suspend fun attendanceReport(from: String?,to: String?): List<Attendance> {
        val a=JSONArray(request("/asistencias/reporte"+reportQuery(from,to)))
        return List(a.length()){attendance(a.getJSONObject(it))}
    }
    override suspend fun attendanceWorkbook(from: String?,to: String?)=requestBytes("/asistencias/reporte.xlsx"+reportQuery(from,to))
    override suspend fun network() = NetworkInfo(JSONObject(request("/red")).getString("ip"))
    private fun summary(j: JSONObject)=TrialSummary(j.getLong("total"),j.getLong("correctos"),j.getLong("falsosRechazos"),j.getLong("falsasAceptaciones"),j.getLong("identidadesIncorrectas"))
    override suspend fun trialSummary()=summary(JSONObject(request("/verificacion/resumen?capturaVersion=$CAPTURE_POLICY")))
    override suspend fun testFace(capture: FaceCapture,expectedDni: String?): FaceTestResult {
        val j=JSONObject(request("/verificacion/rostro","POST",JSONObject().put("modelo",FACE_MODEL).put("vector",vector(capture.average())).put("dniEsperado",expectedDni).put("capturaVersion",CAPTURE_POLICY)))
        val t=j.getJSONObject("prueba")
        return FaceTestResult(t.getString("resultado"),t.getDouble("similitud"),if(t.isNull("similitudEsperada"))null else t.getDouble("similitudEsperada"),t.getDouble("umbral"),if(t.isNull("dniReconocido"))null else t.getString("dniReconocido"),summary(j.getJSONObject("resumen")),if(t.isNull("dniEsperado"))null else t.getString("dniEsperado"))
    }
    override suspend fun checkZone(position: Position): ZoneCheck {
        val p=JSONObject().put("latitud",position.latitude).put("longitud",position.longitude).put("precision",position.accuracy).put("edadMs",position.ageMs).put("simulada",position.mock)
        val j=JSONObject(request("/verificacion/ubicacion","POST",p))
        return ZoneCheck(j.getBoolean("permitida"),j.getString("mensaje"),if(j.isNull("distancia"))null else j.getDouble("distancia"),j.getDouble("precision"),j.getDouble("radio"),j.getBoolean("redPermitida"),j.getString("codigo"))
    }
    override suspend fun challenge(purpose: String, position: Position?, publicKey: String?, dni: String?): Challenge {
        val p=position?.let { JSONObject().put("latitud",it.latitude).put("longitud",it.longitude).put("precision",it.accuracy).put("edadMs",it.ageMs).put("simulada",it.mock) }
        val j=JSONObject(request("/retos","POST",JSONObject().put("proposito",purpose).put("dni",dni).put("clave",publicKey).put("ubicacion",p)))
        return Challenge(j.getString("id"),j.getString("nonce"),j.getString("proposito"),if(j.isNull("dni")) "" else j.getString("dni"),if(j.isNull("nombre")) "" else j.getString("nombre"),j.optBoolean("requiereRostro",false))
    }
    override suspend fun bind(challenge: Challenge, signature: String, capture: FaceCapture) {
        request("/dispositivos/vincular","POST",JSONObject().put("reto",challenge.id).put("firma",signature).put("modelo",FACE_MODEL).put("vector",vector(capture.average())))
    }
    override suspend fun mark(challenge: Challenge, signature: String?, capture: FaceCapture?): Attendance {
        val j=JSONObject().put("reto",challenge.id).put("firma",signature)
        capture?.let { j.put("modelo",FACE_MODEL).put("vector",vector(it.average())).put("foto",it.photo) }
        return attendance(JSONObject(request("/asistencias","POST",j)))
    }
}
