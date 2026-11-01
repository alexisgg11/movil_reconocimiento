package com.example.reconocimiento.ui

import androidx.lifecycle.ViewModel
import com.example.reconocimiento.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

fun attendanceToday(): String=LocalDate.now(ZoneId.of("America/Lima")).toString()

data class PersonDraft(
    val dni: String = "",
    val name: String = "",
    val photo: String = "",
    val capture: FaceCapture? = null,
    val fingerprint: FingerprintProof? = null,
    val removeFingerprint: Boolean = false,
    val original: Person? = null,
    val tab: Int = 0,
    val verifiedDni: String? = null
) {
    val hasFace get() = capture != null || original?.face == true
    val dataError: String? get() = when {
        !dni.matches(Regex("[0-9]{8}")) -> "El DNI debe tener ocho dígitos."
        name.trim().length !in 3..120 || !name.trim().matches(Regex("[\\p{L}\\p{M}][\\p{L}\\p{M} .’'-]+")) -> "Escribe un nombre completo válido, solo con letras."
        else -> null
    }
    val hasFingerprint get() = fingerprint != null ||
        (!removeFingerprint && capture == null && original?.fingerprint == true)
    val dirty get() = dni != original?.dni.orEmpty() || name != original?.name.orEmpty() ||
        photo != original?.photo.orEmpty() || capture != null || fingerprint != null || removeFingerprint
    fun payload() = PersonChange(dni, name.trim(), photo, capture, fingerprint, removeFingerprint, original)
}

data class AppState(
    val page: Int = 1,
    val loading: Boolean = false,
    val people: List<Person> = emptyList(),
    val site: Site = Site(),
    val attendance: List<Attendance> = emptyList(),
    val attendanceDate: String = attendanceToday(),
    val followToday: Boolean = true,
    val attendanceError: String? = null,
    val syncUnavailable: Boolean = false,
    val message: String? = null,
    val success: Boolean = false,
    val position: Position? = null,
    val form: PersonDraft? = null,
    val detail: Person? = null,
    val attendanceDetail: Attendance? = null,
    val activity: String? = null,
    val zoneCheck: ZoneCheck? = null,
    val calibrationOpen: Boolean = false,
    val trialSummary: TrialSummary = TrialSummary(),
    val trialResult: FaceTestResult? = null
)

class AppViewModel(val repository: AttendanceRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var generation = 0L

    fun change(block: (AppState) -> AppState) { mutable.value = block(mutable.value) }
    fun draft(block: (PersonDraft) -> PersonDraft) {
        change { state -> state.copy(form = state.form?.let(block)) }
    }

    suspend fun work(block: suspend () -> String?) {
        if (mutable.value.loading) return
        generation++
        change { it.copy(loading = true, message = null) }
        try {
            val result = block()
            if (result != null) change { it.copy(message = result, success = true) }
        } catch (e: TimeoutCancellationException) {
            error("La operación tardó demasiado. Intenta nuevamente.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if(e is AppFailure && e.code == "RETO_VENCIDO") draft { it.copy(fingerprint = null, tab = 1) }
            error(if (e is AppFailure) e.message.orEmpty() else "No se pudo completar la operación. Intenta nuevamente.")
        } finally {
            change { it.copy(loading = false, activity = null) }
        }
    }

    suspend fun refresh() {
        val request=++generation
        val date=if(mutable.value.followToday)attendanceToday() else mutable.value.attendanceDate
        try {
            val site = repository.site()
            val people = repository.people()
            val list = repository.attendance(date)
            if(request==generation)applySnapshot(site, people, list,date)
        } catch(e: CancellationException){throw e}
        catch(e: Exception){if(request==generation)change {it.copy(syncUnavailable=true)};throw e}
    }

    suspend fun synchronize() {
        if (mutable.value.loading) return
        val started = generation
        val date=if(mutable.value.followToday)attendanceToday() else mutable.value.attendanceDate
        try {
            val site = repository.site()
            val people = repository.people()
            val list = repository.attendance(date)
            if (started == generation && !mutable.value.loading) applySnapshot(site, people, list,date)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {if(started==generation&&!mutable.value.loading)change {it.copy(syncUnavailable=true)}}
    }

    private fun applySnapshot(site: Site, people: List<Person>, list: List<Attendance>,date: String) {
        change { state -> state.copy(site = site, people = people, attendance = list,attendanceDate=date,attendanceError=null,syncUnavailable=false,zoneCheck=if(state.site==site)state.zoneCheck else null,
            attendanceDetail=state.attendanceDetail?.let {selected->list.find {if(selected.id>0)it.id==selected.id else it.dni==selected.dni&&it.instant==selected.instant}},
            detail = state.detail?.let { selected -> people.find { it.dni == selected.dni } }) }
    }

    suspend fun selectAttendanceDate(date: String) {
        val parsed=runCatching {LocalDate.parse(date)}.getOrNull() ?: throw AppFailure("Selecciona una fecha válida.")
        if(parsed>LocalDate.parse(attendanceToday()))throw AppFailure("Selecciona hoy o un día anterior.")
        val request=++generation
        change {it.copy(attendanceDate=date,followToday=date==attendanceToday(),attendance=emptyList(),attendanceError=null,attendanceDetail=null)}
        try {
            val list=repository.attendance(date)
            if(request==generation)change {it.copy(attendance=list,syncUnavailable=false)}
        } catch(e: CancellationException){throw e}
        catch(e: Exception){if(request==generation)change {it.copy(attendanceError="No se pudieron cargar las asistencias. Pulsa Actualizar para reintentar.")};throw e}
    }

    fun returnToToday(){generation++;change {it.copy(attendanceDate=attendanceToday(),followToday=true,attendance=emptyList(),attendanceError=null,attendanceDetail=null)}}

    fun openEditor(person: Person? = null) {
        change { it.copy(form = PersonDraft(dni = person?.dni.orEmpty(), name = person?.name.orEmpty(),
            photo = person?.photo.orEmpty(), original = person), detail = null) }
    }
    fun closeEditor() { change { it.copy(form = null) } }
    fun faceCaptured(capture: FaceCapture) {
        draft { it.copy(capture = capture, photo = capture.profilePhoto, fingerprint = null) }
    }
    suspend fun validateIdentity(): PersonDraft {
        val form=mutable.value.form ?: throw AppFailure("Abre la ficha de la persona.")
        form.dataError?.let {throw AppFailure(it)}
        if(form.original==null)repository.ensureDniAvailable(form.dni)
        if(mutable.value.form!=form)throw AppFailure("Los datos cambiaron. Revisa la ficha antes de continuar.")
        draft {it.copy(verifiedDni=it.dni)}
        return mutable.value.form!!
    }
    suspend fun nextPerson() {
        validateIdentity()
        draft {it.copy(tab=1)}
    }
    suspend fun acceptFace(capture: FaceCapture) {
        val form=mutable.value.form ?: throw AppFailure("Abre la ficha de la persona.")
        repository.validateFaceEnrollment(form.copy(capture=capture,photo=capture.profilePhoto).payload())
        if(mutable.value.form!=form)throw AppFailure("Los datos cambiaron. Vuelve a seleccionar el rostro.")
        faceCaptured(capture)
    }
    fun fingerprintPrepared(proof: FingerprintProof) {
        draft { it.copy(fingerprint = proof, removeFingerprint = false) }
    }
    fun fingerprintTarget(): PersonDraft {
        val form = mutable.value.form ?: throw AppFailure("Abre la ficha de la persona.")
        if (!form.dni.matches(Regex("[0-9]{8}"))) throw AppFailure("Completa el DNI antes de registrar la huella.")
        return form
    }
    fun error(message: String) { change { it.copy(message = message, success = false) } }

    suspend fun savePerson(): String {
        val form = mutable.value.form ?: throw AppFailure("Abre la ficha de la persona.")
        form.dataError?.let {throw AppFailure(it)}
        val saved = repository.save(form.payload())
        change { state -> state.copy(form = null,
            people = (state.people.filterNot { it.dni == saved.dni } + saved).sortedBy { it.name },
            detail = saved) }
        return "Datos de ${saved.name} guardados."
    }
}
