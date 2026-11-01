package com.example.reconocimiento

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.reconocimiento.data.FaceEngine
import com.example.reconocimiento.data.HttpAttendanceRepository
import com.example.reconocimiento.ui.attendanceToday
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File

class RecognitionDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun ready(){compose.waitUntil(20000){runCatching {compose.onNodeWithText("Actualizar").assertIsEnabled();true}.getOrDefault(false)};compose.waitForIdle()}
    @Test fun yesterdayLoadsThroughTheApiAndOpensItsHistoricalDetail(){
        ready()
        val day=java.time.LocalDate.parse(attendanceToday()).minusDays(1).toString()
        val records=runBlocking {HttpAttendanceRepository().attendance(day)}
        compose.onNodeWithTag("lista-asistencia").performScrollToNode(hasTestTag("asistencias-anterior"))
        compose.onNodeWithTag("asistencias-anterior").performClick()
        ready()
        if(records.isNotEmpty()){
            val item=records.first()
            compose.onNodeWithTag("lista-asistencia").performScrollToNode(hasTestTag("asistencia-${item.dni}"))
            compose.onNodeWithTag("asistencia-${item.dni}").performClick()
            compose.onNodeWithText("Detalle de asistencia").assertExists()
            screenshot("asistencia-historica-real")
        } else compose.onNodeWithText("No hay asistencias para esta fecha.").assertExists()
    }
    @Test fun reportLoadsAllHistoryAndDownloadsAGenuineExcelWorkbookReadOnly(){
        ready()
        val repository=HttpAttendanceRepository()
        val rows=runBlocking {repository.attendanceReport(null,null)}
        assertTrue(rows.all {it.photo.isEmpty()&&it.referencePhoto.isEmpty()})
        val bytes=runBlocking {repository.attendanceWorkbook(null,null)}
        val entries=mutableSetOf<String>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use {zip->
            while(true){val entry=zip.nextEntry ?: break;entries.add(entry.name);zip.closeEntry()}
        }
        assertTrue(entries.contains("xl/workbook.xml"));assertTrue(entries.contains("xl/worksheets/sheet1.xml"))
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qa/reporte-verificado.xlsx")
        file.parentFile?.mkdirs();file.writeBytes(bytes)
        compose.onNodeWithTag("lista-asistencia").performScrollToNode(hasTestTag("abrir-reporte"))
        compose.onNodeWithTag("abrir-reporte").performClick()
        compose.waitUntil(15000){runCatching {compose.onNodeWithTag("descargar-excel").assertIsEnabled();true}.getOrDefault(false)}
        compose.onNodeWithText("Todo el historial").assertExists()
        screenshot("reporte-historial-real")
        assertEquals(rows.map {it.id},runBlocking {repository.attendanceReport(null,null)}.map {it.id})
    }
    @Test fun personFormRejectsMissingDniBeforeSaving(){
        ready();compose.onNodeWithTag("nav-0").performClick()
        compose.onNodeWithText("Nuevo").performClick()
        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithText("El DNI debe tener ocho dígitos.").assertExists()
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNodeWithTag("dni").performTextInput("01234567")
        compose.onNodeWithTag("nombre").performTextInput("Persona de prueba")
        compose.onNodeWithTag("nombre").performImeAction()
        compose.onNode(hasText("Persona de prueba") and hasAnyAncestor(hasTestTag("ficha-en-vivo"))).assertExists()
        compose.onNodeWithText("DNI 01234567").assertExists()
        screenshot("ficha")
        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithTag("sin-biometria").assertExists()
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("¿Guardar los datos de Persona de prueba?",substring=true).assertExists()
        compose.onNodeWithText("No podrá marcar hasta registrar un método.",substring=true).assertExists()
        compose.onNodeWithTag("cancelar-confirmacion").performClick()
        compose.onNodeWithTag("form-tab-1").performClick()
        compose.onNodeWithTag("registrar-huella").assertExists()
        screenshot("biometria")
    }
    @Test fun mapEditsThePinAndRadiusWithoutSavingTheDraft(){
        ready();compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Editar").performScrollTo().performClick()
        compose.onNodeWithTag("manual-lat").performTextReplacement("-12.046374")
        compose.onNodeWithTag("manual-lon").performTextReplacement("-77.042793")
        compose.onNodeWithText("Aplicar").performClick()
        compose.onNodeWithTag("radio").performScrollTo().performTextReplacement("150")
        compose.activityRule.scenario.onActivity {it.currentFocus?.let {view->
            (it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(view.windowToken,0)
        }}
        compose.waitUntil(15000){runCatching {js("String(circle.getRadius())").contains("150")}.getOrDefault(false)}
        compose.waitUntil(10000){js("map.getSize().y > 100 && pin.getElement().getBoundingClientRect().height > 0") == "true"}
        js("pin.setLatLng([-12.046,-77.042]);pin.fire('dragend');true")
        compose.waitUntil(10000){compose.onAllNodesWithText("-12.046000, -77.042000").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("mapa-zona").performScrollTo()
        val diagnostic=js("JSON.stringify({size:map.getSize(),inner:[innerWidth,innerHeight],center:pin.getLatLng(),radius:circle.getRadius(),tiles:document.querySelectorAll('.leaflet-tile-loaded').length,rect:document.getElementById('map').getBoundingClientRect().toJSON(),body:document.body.getBoundingClientRect().toJSON(),css:getComputedStyle(document.getElementById('map')).cssText,controls:document.querySelectorAll('.leaflet-control').length})")
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"map-diagnostic.txt").writeText(diagnostic)
        Thread.sleep(2000)
        screenshot("mapa")
    }
    @Test fun modelRunsOnTheDeviceWithFiniteNormalizedAndStableOutput(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pixels=IntArray(160*160){i->android.graphics.Color.rgb(50+i%150,60+(i/160)%140,80+i%100)}
        val image=Bitmap.createBitmap(pixels,160,160,Bitmap.Config.ARGB_8888)
        FaceEngine(context).use {engine->
            val a=engine.embedding(image);val b=engine.embedding(image)
            assertEquals(512,a.size)
            assertTrue(a.all {it.isFinite()})
            assertEquals(1.0,a.sumOf {it.toDouble()*it},0.0001)
            assertArrayEquals(a,b,0.00001f)
        }
        image.recycle()
    }
    @Test fun cameraShowsLargeGuidanceAndClearEnrollmentPurpose(){
        ready();compose.onNodeWithTag("nav-0").performClick()
        compose.onNodeWithText("Nuevo").performClick()
        compose.onNodeWithTag("dni").performTextInput("01234567")
        compose.onNodeWithTag("nombre").performTextInput("Prueba de cámara")
        compose.onNodeWithTag("nombre").performImeAction()
        compose.onNodeWithText("Siguiente").performClick()
        compose.waitUntil(20000){compose.onAllNodesWithTag("capturar-rostro").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("capturar-rostro").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("Antes de empezar").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Antes de empezar").assertExists()
        compose.onNodeWithTag("empezar-captura").performClick()
        compose.onNodeWithText("Paso 1 de 3 · Mira al frente").assertExists()
        compose.onNodeWithTag("continuar-captura").assertDoesNotExist()
        screenshot("camara-guiada")
        compose.onNodeWithTag("cancelar-camara").performClick()
    }
    @Test fun verificationCameraUsesOneFrontalPositionAndDoesNotSaveATrialOnCancel(){
        ready()
        val before=runBlocking {HttpAttendanceRepository().trialSummary()}
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Probar reconocimiento facial").performScrollTo().performClick()
        compose.waitUntil(20000){runCatching {compose.onNodeWithText("Probar rostro").assertIsEnabled();true}.getOrDefault(false)}
        compose.onNodeWithText("Probar rostro").performClick()
        try {compose.waitUntil(15000){compose.onAllNodesWithText("Antes de empezar").fetchSemanticsNodes().isNotEmpty()}}
        catch(e: Throwable){
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            File(instrumentation.targetContext.getExternalFilesDir(null),"qa/verificacion-error.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle();throw e
        }
        compose.onNodeWithText("Mira al frente y mantén la cabeza quieta").assertExists()
        compose.onNodeWithText("Mira al frente, gira a un lado y vuelve al frente").assertDoesNotExist()
        compose.onNodeWithTag("empezar-captura").performClick()
        compose.onNodeWithText("Validación frontal").assertExists()
        compose.onNodeWithText("Paso 1 de 3",substring=true).assertDoesNotExist()
        screenshot("verificacion-frontal")
        compose.onNodeWithTag("cancelar-camara").performClick()
        ready()
        assertEquals(before,runBlocking {HttpAttendanceRepository().trialSummary()})
    }
    private fun webView(view: View): WebView? {
        if(view is WebView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)webView(view.getChildAt(i))?.let {return it}
        return null
    }
    private fun js(script: String): String {
        val latch=CountDownLatch(1);var value=""
        compose.runOnUiThread {
            val map=webView(compose.activity.window.decorView)
            if(map==null)latch.countDown() else map.evaluateJavascript(script){value=it;latch.countDown()}
        }
        assertTrue(latch.await(5,TimeUnit.SECONDS));return value
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(1500)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),"qa").apply {mkdirs()}.resolve("$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
}
