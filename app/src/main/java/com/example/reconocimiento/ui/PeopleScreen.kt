package com.example.reconocimiento.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.reconocimiento.domain.Person

@Composable
fun PeopleScreen(state: AppState, onNew: () -> Unit, onView: (Person) -> Unit,
                 onEdit: (Person) -> Unit, onDelete: (Person) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val people = state.people.filter { query.isBlank() || it.dni.contains(query) || it.name.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Personas", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            PrimaryAction("Nuevo", onNew, enabled = !state.loading, icon = Symbol.Plus)
        }
        OutlinedTextField(query, { query = it }, label = { Text("Buscar por DNI o nombre") }, singleLine = true,
            leadingIcon = { AppIcon(Symbol.Search) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
        Text("${people.size} personas", style = MaterialTheme.typography.bodySmall, color = MutedBlue)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (people.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(Symbol.Person, Modifier.size(36.dp), color = MutedBlue)
                    Text(if (query.isBlank()) "Aún no hay personas registradas" else "No se encontraron personas", color = MutedBlue)
                }
            }
            items(people, key = { it.dni }) { person ->
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column {
                        Surface(onClick = { onView(person) }, color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Photo(person.photo, person.name, Modifier.size(60.dp, 68.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("DNI ${person.dni}", style = MaterialTheme.typography.bodyMedium, color = MutedBlue)
                                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
                                        val pending=!person.face||!person.fingerprint
                                        val color=if(!person.face&&!person.fingerprint) MaterialTheme.colorScheme.error else if(pending) Color(0xFF996000) else MaterialTheme.colorScheme.primary
                                        if(pending)AppIcon(Symbol.Warning,Modifier.size(17.dp),color,"Registro biométrico incompleto")
                                        Text(when {
                                            !person.face&&!person.fingerprint -> "Faltan rostro y huella"
                                            !person.face -> "Falta registrar rostro"
                                            !person.fingerprint -> "Falta registrar huella"
                                            else -> "Facial y huella registrados"
                                        },style=MaterialTheme.typography.labelSmall,color=color)
                                    }
                                }
                                AppIcon(Symbol.ChevronRight, color = MutedBlue)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth()) {
                            PersonAction(Symbol.Eye, "Ver ficha", !state.loading, { onView(person) }, Modifier.weight(1f))
                            PersonAction(Symbol.Edit, "Editar persona", !state.loading, { onEdit(person) }, Modifier.weight(1f).testTag("editar-${person.dni}"))
                            PersonAction(Symbol.Delete, "Eliminar persona", !state.loading, { onDelete(person) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonAction(symbol: Symbol, label: String, enabled: Boolean, action: () -> Unit, modifier: Modifier) {
    IconButton(action, modifier.height(46.dp), enabled = enabled) { AppIcon(symbol, description = label) }
}

@Composable
fun PersonEditor(form: PersonDraft, loading: Boolean, vm: AppViewModel, capture: () -> Unit,
                 photo: () -> Unit, fingerprint: () -> Unit, removeFingerprint: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var faceHelp by remember {mutableStateOf(false)}
    IdentitySummary(form.photo, form.name, form.dni, if (loading || (form.original==null&&form.verifiedDni!=form.dni)) null else photo)
    PrimaryTabRow(selectedTabIndex = form.tab, containerColor = MaterialTheme.colorScheme.surface) {
        listOf("Datos", "Biometría").forEachIndexed { index, text ->
            Tab(form.tab == index, { vm.draft { it.copy(tab = index) } }, enabled = !loading&&(index==0||form.original!=null||form.verifiedDni==form.dni&&form.dataError==null),
                text = { Text(text) }, modifier = Modifier.testTag("form-tab-$index"))
        }
    }
    if (form.tab == 0) {
        OutlinedTextField(form.dni, { value -> vm.draft { it.copy(dni = value.filter(Char::isDigit).take(8), fingerprint = null,verifiedDni=null,capture=null,photo="") } },
            label = { Text("DNI *") }, singleLine = true, enabled = !loading && form.original == null,
            shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth().testTag("dni"))
        OutlinedTextField(form.name, { value -> vm.draft { it.copy(name = value.take(120)) } },
            label = { Text("Nombre completo *") }, singleLine = true, enabled = !loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().testTag("nombre"))
    } else {
        if(!form.hasFace&&!form.hasFingerprint)BiometricWarning()
        BiometricSection(Symbol.Camera, "Reconocimiento facial", when {
            form.capture?.source == "GALERIA" -> "Foto validada, lista para guardar"
            form.capture != null -> "Captura nueva lista"
            form.original?.faceSource == "GALERIA" && form.hasFace -> "Registrado desde una foto"
            form.hasFace -> "Registrado"
            else -> "Pendiente"
        },info={faceHelp=true},enabled=!loading) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PrimaryAction(if (form.hasFace) "Actualizar" else "Añadir", capture,
                    Modifier.weight(1f).testTag("capturar-rostro"), !loading, icon = Symbol.Camera)
                SecondaryAction("Importar",photo,Modifier.weight(1f).testTag("importar-rostro"),!loading,icon=Symbol.Import)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BiometricSection(Symbol.Fingerprint, "Huella digital", when {
            form.fingerprint != null -> "Lista para guardar"
            form.hasFingerprint -> "Vinculada"
            form.removeFingerprint -> "Se retirará al guardar"
            else -> "Sin registrar"
        }) {
            PrimaryAction(if (form.hasFingerprint) "Actualizar huella" else "Registrar huella", fingerprint,
                Modifier.fillMaxWidth().testTag("registrar-huella"), !loading, secondary = true, icon = Symbol.Fingerprint)
            if (form.hasFingerprint || form.original?.fingerprint == true) TextButton(removeFingerprint, enabled = !loading) { Text("Retirar vínculo de huella") }
            Text("Vincula esta persona a la biometría del teléfono. Solo una persona por dispositivo; Android administra las huellas.", style = MaterialTheme.typography.bodySmall, color = MutedBlue)
        }
    }
    if(faceHelp)AlertDialog(onDismissRequest={faceHelp=false},
        title={Text("Indicaciones del rostro")},
        text={Text("Usa una foto frontal, nítida y sin lentes ni objetos sobre el rostro. La foto también se usará en la ficha.")},
        confirmButton={TextButton({faceHelp=false}){Text("Entendido")}})
}

@Composable
fun BiometricWarning() {
    Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth().testTag("sin-biometria")) {
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            AppIcon(Symbol.Warning,color=MaterialTheme.colorScheme.error)
            Text("Sin rostro ni huella. No podrá marcar asistencia hasta registrar un método.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BiometricSection(icon: Symbol, title: String, status: String, info: (()->Unit)?=null,enabled: Boolean=true,content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(icon, Modifier.size(26.dp))
            Column(Modifier.weight(1f)) { SectionTitle(title); Text(status, style = MaterialTheme.typography.bodySmall, color = MutedBlue) }
            if(info!=null)IconButton(info,enabled=enabled,modifier=Modifier.testTag("ayuda-rostro")) {
                AppIcon(Symbol.Info,description="Ver indicaciones del reconocimiento facial")
            }
        }
        content()
    }
}
