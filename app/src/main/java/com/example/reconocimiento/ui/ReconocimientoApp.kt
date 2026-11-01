package com.example.reconocimiento.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reconocimiento.domain.*

data class Confirmation(val title: String, val message: String, val action: () -> Unit)

@Composable
fun ReconocimientoApp(
    state: AppState, vm: AppViewModel, refresh: () -> Unit, capture: () -> Unit, photo: () -> Unit,
    save: () -> Unit, delete: (Person) -> Unit, prepareFingerprint: () -> Unit,
    facial: () -> Unit, fingerprint: () -> Unit, configure: (Site) -> Unit,
    locate: ((Position) -> Unit) -> Unit, sensorSettings: () -> Unit,
    networkInfo: ((NetworkInfo) -> Unit) -> Unit = {},
    checkLocation: ()->Unit = {},openCalibration: ()->Unit = {},testFace: (String?)->Unit = {},selectDate: (String)->Unit = {},next: (()->Unit)? = null,
    downloadReport: (String?,String?)->Unit = {_,_->}
) {
    val scope=rememberCoroutineScope()
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var configurationDirty by rememberSaveable { mutableStateOf(false) }
    var reportOpen by rememberSaveable {mutableStateOf(false)}
    fun navigate(page: Int) {
        if (state.page == 2 && page != 2 && configurationDirty) {
            confirmation = Confirmation("Descartar configuración", "¿Salir sin guardar los cambios de la sede?") {
                configurationDirty = false
                vm.change { it.copy(page = page) }
            }
        } else vm.change { it.copy(page = page) }
    }
    BackHandler(enabled = state.page == 2 && configurationDirty) { navigate(1) }
    fun closeEditor() {
        if (state.form?.dirty == true) confirmation = Confirmation("Descartar cambios", "¿Cerrar la ficha sin guardar?") { vm.closeEditor() }
        else vm.closeEditor()
    }
    fun checkZone(action: () -> Unit) {
        if (state.site.latitude == null || state.site.longitude == null) vm.error("Configura la zona de asistencia antes de marcar.")
        else action()
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = Color.White) {
                Column {
                    Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(Symbol.IdCard, Modifier.size(27.dp))
                        Text("Reconocimiento", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(refresh, enabled = !state.loading) { Text("Actualizar") }
                    }
                    if(state.syncUnavailable)Text("Sin conexión. Mostrando los últimos datos.",Modifier.padding(horizontal=20.dp,vertical=4.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    if (state.loading) {
                        state.activity?.let { Text(it,Modifier.padding(horizontal=20.dp,vertical=6.dp),color=DeepBlue,style=MaterialTheme.typography.bodyMedium) }
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    }
                    else HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }, bottomBar = {
            Surface(color = Color.White, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    listOf("Personas" to Symbol.Person, "Asistencia" to Symbol.Clock, "Configuración" to Symbol.Settings).forEachIndexed { index, item ->
                        val selected = state.page == index
                        NavigationBarItem(selected, { navigate(index) },
                            enabled = !state.loading, modifier = Modifier.testTag("nav-$index"),
                            icon = { AppIcon(item.second, color = if (selected) MaterialTheme.colorScheme.primary else MutedBlue) },
                            label = { Text(item.first, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFE6F0FC)))
                    }
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(top = 18.dp)) {
            when (state.page) {
                0 -> PeopleScreen(state, { vm.openEditor() }, { person -> vm.change { it.copy(detail = person) } },
                    vm::openEditor, { person -> confirmation = Confirmation("Eliminar persona", "¿Eliminar a ${person.name}? Su historial se conservará.") { delete(person) } })
                1 -> AttendanceScreen(state, { checkZone(facial) }, { checkZone(fingerprint) }, { vm.change { it.copy(page = 2) } }, { a -> vm.change { it.copy(attendanceDetail=a) } },selectDate,{reportOpen=true})
                2 -> ConfigurationScreen(state.site, state.loading, { site ->
                    confirmation = Confirmation("Guardar configuración", "¿Guardar ${site.name} con un radio máximo de ${site.radius.toInt()} m?") { configure(site) }
                }, locate, sensorSettings, { configurationDirty = it }, networkInfo,state.zoneCheck,checkLocation,openCalibration)
            }
        }
    }
    state.form?.let { form ->
        EditorDialog(if (form.original == null) "Nueva persona" else "Editar persona", ::closeEditor, !state.loading,
            actions = {
                SecondaryAction(if(form.original==null&&form.tab==1) "Atrás" else "Cancelar", {
                    if(form.original==null&&form.tab==1)vm.draft {it.copy(tab=0)} else closeEditor()
                }, enabled = !state.loading)
                PrimaryAction(if(form.original==null&&form.tab==0) "Siguiente" else "Guardar", {
                    when {
                        form.dataError!=null -> vm.error(form.dataError!!)
                        form.original==null&&form.tab==0 -> if(next!=null)next() else scope.launch {vm.work {vm.nextPerson();null}}.let {Unit}
                        else -> confirmation = Confirmation("Guardar persona", "¿Guardar los datos de ${form.name.trim()}?" +
                            if(!form.hasFace || !form.hasFingerprint) "\n\nPendiente: ${listOfNotNull(if(!form.hasFace) "rostro" else null,if(!form.hasFingerprint) "huella" else null).joinToString(" y ")}. ${if(!form.hasFace&&!form.hasFingerprint) "No podrá marcar hasta registrar un método." else "Podrá marcar con el método registrado."}" else "") { save() }
                    }
                }, enabled = !state.loading)
            }) {
            PersonEditor(form, state.loading, vm, capture, photo, prepareFingerprint, {
                confirmation = Confirmation("Retirar huella", "El vínculo de huella se retirará al guardar la ficha.") {
                    vm.draft { it.copy(fingerprint = null, removeFingerprint = true) }
                }
            })
        }
    }
    if(reportOpen)AttendanceReportDialog(state.attendanceDate,state.loading,vm.repository::attendanceReport,downloadReport,{reportOpen=false})
    state.detail?.let { person ->
        EditorDialog("Ficha de persona", { vm.change { it.copy(detail = null) } }, !state.loading,
            actions = {
                SecondaryAction("Cerrar", { vm.change { it.copy(detail = null) } }, enabled = !state.loading)
                PrimaryAction("Editar", { vm.openEditor(person) }, enabled = !state.loading, icon = Symbol.Edit)
            }) {
            IdentitySummary(person.photo, person.name, person.dni)
            if(!person.face&&!person.fingerprint)BiometricWarning()
            StatusRow(Symbol.Camera, "Reconocimiento facial", if (!person.face) "Pendiente" else if(person.faceSource=="GALERIA") "Foto importada" else "Registrado")
            StatusRow(Symbol.Fingerprint, "Huella digital", if (person.fingerprint) "Vinculada" else "Sin registrar")
        }
    }
    state.attendanceDetail?.let { attendance ->
        AttendanceDetail(attendance) { vm.change { it.copy(attendanceDetail=null) } }
    }
    if(state.calibrationOpen)CalibrationDialog(state.people,state.loading,state.trialSummary,state.trialResult,testFace) {
        vm.change {it.copy(calibrationOpen=false,trialResult=null)}
    }
    confirmation?.let { item ->
        AlertDialog(onDismissRequest = { if (!state.loading) confirmation = null }, title = { Text(item.title) }, text = { Text(item.message) },
            confirmButton = { TextButton({ confirmation = null; item.action() }, enabled = !state.loading) { Text("Confirmar") } },
            dismissButton = { TextButton({ confirmation = null }, modifier=Modifier.testTag("cancelar-confirmacion"), enabled = !state.loading) { Text("Cancelar") } })
    }
    state.message?.let { message ->
        AlertDialog(onDismissRequest = { vm.change { it.copy(message = null) } },
            title = { Text(if (state.success) "Guardado" else "Revisa los datos") }, text = { Text(message) },
            confirmButton = { TextButton({ vm.change { it.copy(message = null) } }) { Text("Aceptar") } })
    }
}

@Composable
private fun StatusRow(icon: Symbol, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIcon(icon)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = DeepBlue, style = MaterialTheme.typography.labelLarge)
    }
}
