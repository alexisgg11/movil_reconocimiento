package com.example.reconocimiento.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.reconocimiento.domain.*

class AccessCallbacks(
    val login: (AccessMethod) -> Unit,
    val loginWithCredentials: (String, String) -> Unit,
    val logout: () -> Unit,
    val openReceiptProfile: () -> Unit,
    val captureAdministrator: ((FaceCapture) -> Unit) -> Unit,
    val linkInitialFingerprint: (String, String, (FingerprintProof) -> Unit) -> Unit,
    val prepare: (String, String, PersonChange?, String, String) -> Unit
)

@Composable
fun LoginScreen(
    state: AppState,
    login: (AccessMethod) -> Unit,
    loginWithCredentials: (String, String) -> Unit,
    openAttendance: () -> Unit,
    prepare: () -> Unit,
    refresh: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val status = state.accessStatus
    val canAuthenticate = !state.loading && status.administratorCreated && !state.syncUnavailable

    Surface(Modifier.fillMaxSize().testTag("pantalla-login"), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(Symbol.IdCard, Modifier.size(30.dp))
                    Text("Reconocimiento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Surface(
                    Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Iniciar sesión", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        BiometricOptions(canAuthenticate, { login(AccessMethod.ROSTRO) }, { login(AccessMethod.HUELLA) })
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text("O con usuario y contraseña", style = MaterialTheme.typography.bodySmall, color = MutedBlue)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                        UsernameField(username, { username = it }, canAuthenticate)
                        PasswordField(password, { password = it }, canAuthenticate)
                        PrimaryAction("Ingresar", {
                            val submitted = password
                            password = ""
                            loginWithCredentials(username, submitted)
                        }, Modifier.fillMaxWidth().testTag("ingresar-credenciales"),
                            enabled = canAuthenticate && username.isNotBlank() && password.isNotEmpty())
                        if (status.setupAvailable) {
                            TextButton(prepare, enabled = !state.loading, modifier = Modifier.testTag("preparar-administrador")) {
                                Text("Configurar acceso")
                            }
                        }
                        if (state.syncUnavailable) {
                            Text("No se pudo conectar con la API.", color = MaterialTheme.colorScheme.error)
                            TextButton(refresh, enabled = !state.loading) { Text("Reintentar") }
                        }
                        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                SecondaryAction("Marcar asistencia", openAttendance, Modifier.fillMaxWidth().testTag("entrar-asistencia"),
                    enabled = !state.loading, icon = Symbol.Clock)
            }
        }
    }
}

@Composable
fun AdministratorSetupDialog(existingAdministrator: Boolean, loading: Boolean, close: () -> Unit, callbacks: AccessCallbacks) {
    var createPerson by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(PersonDraft()) }
    val dataReady = code.matches(Regex("[0-9a-fA-F]{32}")) && draft.dni.length == 8 && (!createPerson || draft.dataError == null)
    val canSave = dataReady && username.matches(Regex("[A-Za-z0-9._-]{3,40}")) &&
        password.length >= 15 && password.isNotBlank() && (!createPerson || draft.hasFace || draft.hasFingerprint)
    EditorDialog("Configurar acceso", close, !loading,
        actions = {
            SecondaryAction("Volver", close, enabled = !loading)
            PrimaryAction("Guardar", { callbacks.prepare(code, draft.dni, if (createPerson) draft.payload() else null, username, password) },
                Modifier.testTag("guardar-administrador"), !loading && canSave)
        }) {
        OutlinedTextField(code, { code = it.trim().take(32); draft = draft.copy(fingerprint = null) },
            Modifier.fillMaxWidth().testTag("codigo-preparacion"), label = { Text("Código de preparación") }, enabled = !loading,
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(8.dp))
        if (!existingAdministrator) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!createPerson, { createPerson = false; draft = draft.copy(capture = null, fingerprint = null, photo = "") },
                    { Text("Ficha existente") }, enabled = !loading)
                FilterChip(createPerson, { createPerson = true }, { Text("Nueva ficha") }, enabled = !loading)
            }
        }
        DniField(draft.dni, { draft = draft.copy(dni = it, capture = null, fingerprint = null, photo = "") }, !loading)
        if (createPerson) {
            ProfileNameField(draft.name, { draft = draft.copy(name = it) }, !loading)
            Text("Registra al menos un método biométrico.", color = MutedBlue)
            BiometricOptions(dataReady && !loading,
                { callbacks.captureAdministrator { draft = draft.copy(capture = it, photo = it.profilePhoto) } },
                { callbacks.linkInitialFingerprint(code, draft.dni) { draft = draft.copy(fingerprint = it) } })
            if (draft.hasFace || draft.hasFingerprint) {
                Text(listOfNotNull(if (draft.hasFace) "Rostro capturado" else null,
                    if (draft.hasFingerprint) "Huella preparada" else null).joinToString(" · "), color = DeepBlue)
            }
        } else {
            Text("Usa el DNI de una ficha activa con rostro o huella registrados.", color = MutedBlue)
        }
        UsernameField(username, { username = it }, !loading)
        PasswordField(password, { password = it }, !loading, "De 15 a 128 caracteres")
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun UsernameField(value: String, update: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(value, { update(it.take(40)) }, Modifier.fillMaxWidth().testTag("usuario-acceso"),
        label = { Text("Usuario") }, enabled = enabled, singleLine = true, shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
}

@Composable
private fun PasswordField(value: String, update: (String) -> Unit, enabled: Boolean, hint: String? = null) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, { update(it.take(128)) }, Modifier.fillMaxWidth().testTag("clave-acceso"),
        label = { Text("Contraseña") }, enabled = enabled, singleLine = true, shape = RoundedCornerShape(8.dp),
        supportingText = hint?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton({ visible = !visible }, enabled = enabled) {
                AppIcon(Symbol.Eye, description = if (visible) "Ocultar contraseña" else "Mostrar contraseña")
            }
        })
}

@Composable
private fun BiometricOptions(enabled: Boolean, face: () -> Unit, fingerprint: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryAction("Rostro", face, Modifier.weight(1f).testTag("acceso-rostro"), enabled, icon = Symbol.Camera)
        PrimaryAction("Huella", fingerprint, Modifier.weight(1f).testTag("acceso-huella"), enabled, secondary = true, icon = Symbol.Fingerprint)
    }
}

@Composable
private fun DniField(dni: String, update: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(dni, { update(it.filter { digit -> digit in '0'..'9' }.take(8)) },
        Modifier.fillMaxWidth().testTag("dni-acceso"), label = { Text("DNI") }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(8.dp))
}

@Composable
fun ProfileNameField(name: String, update: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(name, { update(it.take(120)) }, Modifier.fillMaxWidth(), label = { Text("Nombre completo") },
        singleLine = true, enabled = enabled, shape = RoundedCornerShape(8.dp))
}

@Composable
fun OwnProfileScreen(person: Person?, loading: Boolean, edit: (Person) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Mi ficha", style = MaterialTheme.typography.headlineSmall)
        if (person == null) {
            Text(if (loading) "Consultando tu ficha…" else "Pulsa Actualizar para consultar tu ficha.", color = MutedBlue)
        } else {
            IdentitySummary(person.photo, person.name, person.dni)
            PrimaryAction("Editar mis datos", { edit(person) }, Modifier.fillMaxWidth(), !loading, icon = Symbol.Edit)
        }
    }
}
