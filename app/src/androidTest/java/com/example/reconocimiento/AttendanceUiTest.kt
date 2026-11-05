package com.example.reconocimiento

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.reconocimiento.domain.*
import com.example.reconocimiento.ui.*
import com.example.reconocimiento.ui.theme.ReconocimientoTheme
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import com.example.reconocimiento.data.PhotoInspector
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

class AttendanceUiTest {
    @get:Rule val compose=createComposeRule()
    private var savedPeople=emptyList<Person>()
    private val vm=AppViewModel(object: AttendanceRepository {
        override suspend fun people()=savedPeople
        override suspend fun save(change: PersonChange)=error("No guardar en pruebas de presentación")
        override suspend fun delete(dni: String){}
        override suspend fun unbind(dni: String){}
        override suspend fun site()=Site()
        override suspend fun configure(site: Site)=site
        override suspend fun attendance(date: String?)=emptyList<Attendance>()
        override suspend fun challenge(purpose: String,position: Position?,publicKey: String?,dni: String?)=error("Sin autenticación en pruebas de presentación")
        override suspend fun bind(challenge: Challenge,signature: String,capture: FaceCapture){}
        override suspend fun mark(challenge: Challenge,signature: String?,capture: FaceCapture?)=error("Sin marcación en pruebas de presentación")
        override fun activateReceipt(access: PersonalAccess) {}
    })
    private fun show(administrator: Boolean = true, access: AccessCallbacks? = null, facial: () -> Unit = {}) {
        if (administrator) vm.change { it.copy(session = AccessSession("00000001", AccessRole.ADMINISTRADOR, Instant.MAX)) }
        compose.setContent {
            ReconocimientoTheme {
                val state by vm.state.collectAsState()
                ReconocimientoApp(state,vm,{},{},{},{},{},{},facial,{},{},{},{},
                    networkInfo={it(NetworkInfo("127.0.0.1","192.168.1.45"))},selectDate={date->vm.change {it.copy(attendanceDate=date,followToday=date==attendanceToday(),attendance=emptyList())}},
                    access = access)
            }
        }
    }
    private fun accessCallbacks(
        login: (AccessMethod) -> Unit = { error("La marcación no inicia sesión administrativa.") },
        credentials: (String, String) -> Unit = { _, _ -> error("Acceso inesperado por contraseña.") }
    ) = AccessCallbacks(login, credentials, vm::endSession, vm::openReceiptProfile, {}, { _, _, _ -> }, { _, _, _, _, _ -> })

    @Test fun guestMarksOnceAndPersonalSessionShowsOnlyOwnOptions() {
        vm.change { it.copy(accessStatus = AccessStatus(administratorCreated = true), publicZone = PublicZone(configured = true)) }
        val profile = Person("12345678", "Persona de prueba", "", true, true)
        var biometricChecks = 0
        show(administrator = false, access = accessCallbacks(), facial = {
            biometricChecks++
            val entry = Attendance(profile.name, profile.dni, "2026-11-01T02:20:30Z", "FACIAL", 8.0, .93)
            vm.change { it.copy(markingReceipt = MarkingResult(entry,
                PersonalAccess(profile, AccessSession(profile.dni, AccessRole.PERSONA, Instant.MAX), false))) }
        })
        compose.onNodeWithTag("pantalla-login").assertExists()
        compose.onNodeWithTag("marcar-facial").assertDoesNotExist()
        compose.onNodeWithTag("entrar-asistencia").performClick()
        compose.onNodeWithTag("marcar-facial").assertExists()
        compose.onNodeWithTag("nav-0").assertDoesNotExist()
        compose.onNodeWithTag("nav-2").assertDoesNotExist()
        compose.onNodeWithTag("nav-3").assertDoesNotExist()
        compose.onNodeWithTag("marcar-facial").performClick()
        compose.onNodeWithText("Asistencia registrada").assertExists()
        compose.onNodeWithText("21:20 · Rostro").assertExists()
        compose.onNodeWithTag("abrir-ficha-recibo").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, biometricChecks) }
        compose.onNodeWithTag("nav-0").assertDoesNotExist()
        compose.onNodeWithTag("nav-2").assertDoesNotExist()
        compose.onNodeWithText(profile.name).assertExists()
        compose.onNodeWithText("Editar mis datos").performClick()
        compose.onNodeWithText("Editar mi ficha").assertExists()
        compose.onNodeWithTag("capturar-rostro").assertDoesNotExist()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithContentDescription("Cerrar sesión").performClick()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithText(profile.name).assertDoesNotExist()
        compose.onNodeWithTag("marcar-facial").assertExists()
        compose.onNodeWithTag("nav-3").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver al inicio").performClick()
        compose.onNodeWithTag("pantalla-login").assertExists()
    }

    @Test fun administratorLoginOpensManagementAfterBiometricConfirmation() {
        vm.change { it.copy(accessStatus = AccessStatus(administratorCreated = true)) }
        val callbacks = accessCallbacks(login = { method ->
            org.junit.Assert.assertEquals(AccessMethod.HUELLA, method)
            vm.startSession(AccessSession("12345678", AccessRole.ADMINISTRADOR, Instant.MAX))
        })
        show(administrator = false, access = callbacks)
        compose.onNodeWithTag("pantalla-login").assertExists()
        compose.onNodeWithTag("nav-0").assertDoesNotExist()
        compose.onNodeWithTag("acceso-huella").assertIsEnabled()
        compose.onNodeWithTag("dni-acceso").assertDoesNotExist()
        compose.onNodeWithText("Mi ficha").assertDoesNotExist()
        screenshot("login-simple")
        compose.onNodeWithTag("acceso-huella").performClick()
        compose.onNodeWithTag("pantalla-login").assertDoesNotExist()
        compose.onNodeWithTag("nav-0").assertIsSelected()
        compose.onNodeWithTag("nav-2").assertExists()
        compose.onNodeWithContentDescription("Cerrar sesión").performClick()
        compose.onNodeWithText("¿Cerrar sesión?").assertIsDisplayed()
        compose.onNodeWithTag("cancelar-confirmacion").performClick()
        compose.onNodeWithTag("nav-0").assertIsSelected()
        compose.onNodeWithContentDescription("Cerrar sesión").performClick()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithTag("pantalla-login").assertExists()
    }

    @Test fun administratorCanUseUsernameAndPassword() {
        vm.change { it.copy(accessStatus = AccessStatus(administratorCreated = true)) }
        show(administrator = false, access = accessCallbacks(credentials = { username, password ->
            org.junit.Assert.assertEquals("operador.qa", username)
            org.junit.Assert.assertEquals("contraseña de prueba", password)
            vm.startSession(AccessSession("12345678", AccessRole.ADMINISTRADOR, Instant.MAX))
        }))
        compose.onNodeWithTag("ingresar-credenciales").assertIsNotEnabled()
        compose.onNodeWithTag("usuario-acceso").performTextInput("operador.qa")
        compose.onNodeWithTag("clave-acceso").performTextInput("contraseña de prueba")
        compose.onNodeWithTag("ingresar-credenciales").performScrollTo().performClick()
        compose.onNodeWithTag("nav-0").assertIsSelected()
    }
    @Test fun tappingFacialAttendanceShowsIdentityScoreAndLimaTime() {
        vm.change {it.copy(attendanceDate="2026-10-31",followToday=false,attendance=listOf(Attendance("Ana Pérez","12345678","2026-11-01T02:20:30Z","FACIAL",8.0,.9345,12,threshold=.8,accuracy=5.0,ip="127.0.0.1")))}
        show()
        compose.onNodeWithTag("lista-asistencia").performScrollToNode(hasTestTag("asistencia-12345678"))
        compose.onNodeWithTag("asistencia-12345678").performClick()
        compose.onNodeWithText("Detalle de asistencia").assertExists()
        compose.onNodeWithText("93.45%").assertExists()
        compose.onNodeWithText("21:20:30 (Lima)").assertExists()
        compose.onNode(hasText("sábado 31 de octubre de 2026",ignoreCase=true) and hasAnyAncestor(isDialog())).assertExists()
        screenshot("detalle-resumen")
        compose.onNodeWithText("Distancia a la sede").assertDoesNotExist()
        compose.onNodeWithTag("ver-comparacion").performClick()
        compose.onNodeWithText("Esta marcación no conserva ambas fotos.",substring=true).assertExists()
        compose.onNodeWithTag("detalle-tab-Ubicación").performClick()
        compose.onNodeWithText("8.0 m").assertExists()
        compose.onNodeWithText("93.45%").assertDoesNotExist()
    }
    @Test fun fingerprintDetailsDoNotInventAFaceScore() {
        vm.change {it.copy(attendanceDetail=Attendance("Ana Pérez","12345678","2026-11-01T02:20:30Z","HUELLA",8.0,null))}
        show()
        compose.onNodeWithText("Huella del teléfono").assertExists()
        compose.onNodeWithText("Similitud").assertDoesNotExist()
    }
    @Test fun unconfiguredZoneBlocksBothActionsBeforeOpeningDeviceSensors() {
        show()
        compose.onNodeWithTag("marcar-facial").assertIsEnabled().performClick()
        compose.onNodeWithText("Configura la zona de asistencia antes de marcar.").assertExists()
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNodeWithTag("marcar-huella").assertIsEnabled().performClick()
        compose.onNodeWithText("Configura la zona de asistencia antes de marcar.").assertExists()
    }
    @Test fun searchShowsWhichBiometricEnrollmentIsMissing() {
        vm.change {it.copy(page=0,people=listOf(Person("12345678","Ana Pérez","",true,false),Person("87654321","Ingrid López","",false,true)))}
        show()
        compose.onNodeWithText("Falta registrar huella").assertExists()
        compose.onNodeWithText("Falta registrar rostro").assertExists()
        compose.onNodeWithText("Buscar por DNI o nombre").performTextInput("Ingrid")
        compose.onNodeWithText("Falta registrar huella").assertDoesNotExist()
        compose.onNodeWithContentDescription("Registro biométrico incompleto").assertExists()
    }
    @Test fun networkUsesServerObservedIpForUsbConnection() {
        vm.change {it.copy(page=2)};show()
        compose.onNodeWithTag("limitar-red").performScrollTo().performClick()
        compose.onNodeWithText("Usar conexión actual").performScrollTo().performClick()
        compose.onNodeWithText("IP de esta conexión: 127.0.0.1. Guarda para aplicar.").assertExists()
        compose.onNodeWithTag("red-autorizada").performScrollTo().assertTextContains("127.0.0.1")
        compose.onNodeWithText("Usar IP local del teléfono").assertDoesNotExist()
        screenshot("red-simple")
    }
    @Test fun importedReferenceIsLabeledAsEnrollmentRatherThanAnOldProfilePhoto() {
        vm.change {it.copy(attendanceDetail=Attendance("Persona de prueba","12345678","2026-11-01T02:20:30Z","FACIAL",8.0,.9345,
            photo=fixture(1),referencePhoto=fixture(2),referenceKind="GALERIA"))};show()
        compose.onNodeWithTag("ver-comparacion").performClick()
        compose.waitUntil(20000){compose.onAllNodesWithText("Foto importada").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Foto importada").assertExists()
        compose.onNodeWithText("Este registro antiguo conserva la foto de ficha",substring=true).assertDoesNotExist()
    }
    @Test fun newPersonUsesNextBeforeSaveAndWarnsWhenBothBiometricsAreMissing() {
        vm.change {it.copy(page=0)};show()
        compose.onNodeWithText("Nuevo").performClick()
        compose.onNodeWithText("Guardar").assertDoesNotExist()
        compose.onNodeWithTag("dni").performTextInput("12345678")
        compose.onNodeWithTag("nombre").performTextInput("Ana Pérez")
        compose.onNodeWithTag("nombre").performImeAction()
        screenshot("registro-paso-uno")
        compose.onNodeWithText("Siguiente").performClick()
        screenshot("registro-paso-dos")
        compose.onNodeWithTag("sin-biometria").assertExists()
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("No podrá marcar hasta registrar un método.",substring=true).assertExists()
    }
    @Test fun duplicateDniStaysOnDataAndCannotOpenBiometrics() {
        savedPeople=listOf(Person("12345678","Ana Pérez","",true,false))
        vm.change {it.copy(page=0)};vm.openEditor();show()
        compose.onNodeWithTag("form-tab-1").assertIsNotEnabled()
        compose.onNodeWithTag("dni").performTextInput("12345678")
        compose.onNodeWithTag("nombre").performTextInput("Otra Persona")
        compose.onNodeWithTag("nombre").performImeAction()
        screenshot("dni-antes-de-verificar")
        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithText("Ese DNI ya está registrado. Abre su ficha para editarlo.").assertExists()
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNodeWithTag("form-tab-1").assertIsNotEnabled()
        compose.onNodeWithText("Siguiente").assertExists()
        compose.onNodeWithText("Guardar").assertDoesNotExist()
    }
    @Test fun locationResultShowsSavedRadiusAndNetworkWithoutAnAttendance() {
        vm.change {it.copy(page=2,site=Site(latitude=0.0,longitude=0.0,radius=50.0),zoneCheck=ZoneCheck(false,"Estás fuera de la zona.",70.0,5.0,50.0,true))}
        show()
        compose.onNodeWithText("Marcación bloqueada").performScrollTo().assertExists()
        compose.onNodeWithText("Radio: 50 m · Red: permitida").assertDoesNotExist()
        compose.onNodeWithTag("detalles-zona").performScrollTo().performClick()
        compose.onNodeWithText("Radio: 50 m · Red: permitida").assertExists()
    }
    @Test fun comparisonMeasuresCapturesAndOffersAlignedOverlay() {
        val a=Attendance("Persona de prueba","12345678","2026-11-01T02:20:30Z","FACIAL",8.0,.9345,photo=fixture(1),referencePhoto=fixture(2),referenceKind="ROSTRO")
        vm.change {it.copy(attendanceDetail=a)};show()
        compose.onNodeWithTag("ver-comparacion").performClick()
        compose.waitUntil(20000){compose.onAllNodesWithText("Iluminación").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Registro facial").assertExists()
        compose.onNodeWithText("Giro de cabeza").performScrollTo().assertExists()
        screenshot("comparacion-fotos")
        compose.onNodeWithTag("superponer-fotos").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Marcación alineada").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Registro alineado").assertExists()
        screenshot("comparacion-superpuesta")
    }
    @Test fun calibrationExplainsAFalseRejectionWithoutMarkingAttendance() {
        val result=FaceTestResult("FALSO_RECHAZO",.72,.72,.8,null,TrialSummary(2,1,1),"12345678")
        vm.change {it.copy(calibrationOpen=true,people=listOf(Person("12345678","Ana Pérez","",true,false)),trialResult=result,trialSummary=result.summary)}
        show()
        compose.onNodeWithText("Esta prueba no marca asistencia.",substring=true).assertExists()
        compose.onNodeWithText("Persona registrada no reconocida").assertExists()
        compose.onNodeWithText("Persona no reconocida.",substring=true).assertExists()
        compose.onNodeWithText("72.00%",substring=true).assertDoesNotExist()
        screenshot("prueba-facial")
    }
    @Test fun unknownFaceIsRejectedWithoutDisplayingItsInternalComparisonScore() {
        val result=FaceTestResult("RECHAZO_CORRECTO",.4154,null,.8,null,TrialSummary(1,1))
        compose.setContent {ReconocimientoTheme {
            CalibrationDialog(listOf(Person("12345678","Ana Pérez","",true,false)),false,result.summary,result,{}, {})
        }}
        compose.onNodeWithText("No registrada").performClick()
        compose.onNodeWithText("Persona no registrada: rechazada correctamente").assertExists()
        compose.onNodeWithText("41.54%",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Mayor similitud",substring=true).assertDoesNotExist()
    }
    @Test fun historyUsesDayArrowsAndAClickableCalendarHeading() {
        show()
        compose.onNodeWithTag("lista-asistencia").performScrollToNode(hasTestTag("asistencias-anterior"))
        compose.onNodeWithTag("asistencias-siguiente").assertIsNotEnabled()
        compose.onNodeWithText("Ayer").assertDoesNotExist()
        compose.onNodeWithText("Elegir fecha").assertDoesNotExist()
        compose.onNodeWithTag("asistencias-anterior").performClick()
        org.junit.Assert.assertEquals(java.time.LocalDate.parse(attendanceToday()).minusDays(1).toString(),vm.state.value.attendanceDate)
        compose.onNodeWithText("No hay asistencias para esta fecha.").assertExists()
        screenshot("historial-fechas")
        compose.onNodeWithTag("asistencias-calendario").performClick()
        compose.onNodeWithText("Fecha de asistencia").assertExists()
        screenshot("calendario-asistencia")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("asistencias-siguiente").performClick()
        org.junit.Assert.assertEquals(attendanceToday(),vm.state.value.attendanceDate)
        compose.onNodeWithTag("asistencias-siguiente").assertIsNotEnabled()
    }
    @Test fun facialButtonsFitOnOneRowAndFingerprintKeepsItsOwnAction() {
        vm.openEditor(Person("12345678","Ana Pérez","",true,false,"version"))
        vm.draft {it.copy(tab=1)}
        var faces=0;var fingerprints=0
        val form=vm.state.value.form!!
        compose.setContent {ReconocimientoTheme {
            EditorDialog("Editar persona",{},true,actions={}) {
                PersonEditor(form,false,vm,{faces++},{},{fingerprints++},{})
            }
        }}
        val camera=compose.onNodeWithTag("capturar-rostro").fetchSemanticsNode().boundsInRoot
        val photo=compose.onNodeWithTag("importar-rostro").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(camera.top,photo.top,1f)
        compose.onNodeWithText("Actualizar").assertExists();compose.onNodeWithText("Importar").assertExists()
        compose.onNodeWithText("Usa una foto frontal",substring=true).assertDoesNotExist()
        compose.onNodeWithTag("ayuda-rostro").performClick()
        compose.onNodeWithText("Indicaciones del rostro").assertExists()
        compose.onNodeWithText("Usa una foto frontal",substring=true).assertExists()
        screenshot("ayuda-rostro")
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithText("Usa una foto frontal",substring=true).assertDoesNotExist()
        compose.onNodeWithTag("registrar-huella").performScrollTo().performClick()
        org.junit.Assert.assertEquals(0,faces);org.junit.Assert.assertEquals(1,fingerprints)
        screenshot("biometria-botones-compactos")
    }
    @Test fun reportOffersAllHistoryAndPeriodsAndExportsTheSelectedScope() {
        var scope: Pair<String?,String?>?=null
        compose.setContent {ReconocimientoTheme {
            AttendanceReportDialog(attendanceToday(),false,{_,_->listOf(Attendance("Ana Pérez","01234567","2026-11-01T02:20:30Z","FACIAL",8.0,.93))},{a,b->scope=a to b},{})
        }}
        compose.waitForIdle()
        compose.onNodeWithText("1 marcación").assertExists()
        compose.onNodeWithTag("descargar-excel").performClick()
        org.junit.Assert.assertEquals(null to null,scope)
        compose.onNodeWithText("Por período").performClick()
        compose.onNodeWithText("Desde").assertExists();compose.onNodeWithText("Hasta").assertExists()
        compose.onNodeWithTag("descargar-excel").assertIsEnabled().performClick()
        org.junit.Assert.assertEquals(attendanceToday(),scope!!.second)
        screenshot("reporte-periodo")
    }
    private fun fixture(number: Int): String {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val bytes=File(ctx.getExternalFilesDir(null),"model-fixtures/img$number.jpg").readBytes()
        val photo=PhotoInspector().inspect(Base64.encodeToString(bytes,Base64.NO_WRAP))
        try {
            val output=ByteArrayOutputStream()
            (photo.aligned ?: error("Faltan ojos en la imagen de prueba")).compress(Bitmap.CompressFormat.JPEG,90,output)
            return Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP)
        } finally {photo.close()}
    }
    private fun screenshot(name: String) {
        compose.waitForIdle();Thread.sleep(600)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"qa-usability/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
