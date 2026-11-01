package com.example.reconocimiento

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.reconocimiento.domain.*
import com.example.reconocimiento.ui.FaceCaptureScreen
import com.example.reconocimiento.ui.theme.ReconocimientoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import java.io.File

class ManualFaceCaptureTest {
    @get:Rule val compose=createComposeRule()
    @After fun restoreTestClock(){compose.mainClock.autoAdvance=true}
    @Test fun validatedPositionsAdvanceAutomaticallyAndNeverReturnToCompletedSteps() {
        val front=FacePose(0f,0f,0f,.99f,.99f,.5f,.5f,.5f)
        val gate=FaceCaptureGate().apply {reset(0)}
        var state by mutableStateOf(gate.snapshot())
        var started by mutableStateOf(false)
        var now=0L;var finished=false
        compose.mainClock.autoAdvance=false
        compose.setContent {ReconocimientoTheme {
            FaceCaptureScreen(state,started,true,{started=true},{
                if(gate.phase==FacePhase.COMPLETE)finished=true
                else {gate.continueStep(now);state=gate.snapshot()}
            },{gate.reset(now);state=gate.snapshot()},{}) {Text("Vista previa",color=Color.White)}
        }}
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("empezar-captura").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("continuar-captura").assertDoesNotExist()
        compose.runOnIdle {
            for(t in 0L..3000L step 100)gate.update(front,t)
            gate.baselineAccepted(3000);now=5000;state=gate.update(front.copy(insideFrame=false),now)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Frente validado").assertExists()
        compose.mainClock.advanceTimeBy(1400)
        compose.onNodeWithText("Paso 2 de 3 · Gira a un lado").assertExists()
        compose.runOnIdle {
            gate.invalidate();state=gate.update(front.copy(insideFrame=false),5100)
            assertEquals(FacePhase.TURN,gate.phase)
            for(t in 5200L..8500L step 100)state=gate.update(front.copy(yaw=22f),t)
            now=10000
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Giro validado").assertExists()
        screenshot("giro-validado-automatico")
        compose.mainClock.advanceTimeBy(1400)
        compose.onNodeWithText("Paso 3 de 3 · Vuelve al frente").assertExists()
        compose.runOnIdle {
            for(t in 10100L..16000L step 100){
                val decision=gate.update(front,t)
                if(decision.action==FaceAction.SAMPLE)gate.sampleAccepted(t)
            }
            state=gate.snapshot()
            assertFalse(finished)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Captura completada").assertExists()
        compose.onNodeWithText("Finalizar").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1400)
        compose.runOnIdle {assertTrue(finished)}
    }
    @Test fun verificationKeepsOneVisiblePositionUntilAllFrontalSamplesAreReady() {
        val front=FacePose(0f,0f,0f,.99f,.99f,.5f,.5f,.5f)
        val gate=FaceCaptureGate(FaceCaptureMode.VERIFICATION).apply {reset(0)}
        var state by mutableStateOf(gate.snapshot())
        var started by mutableStateOf(false)
        var finished=false
        compose.mainClock.autoAdvance=false
        compose.setContent {ReconocimientoTheme {
            FaceCaptureScreen(state,started,false,{started=true},{finished=true},{gate.reset(0);state=gate.snapshot()},{}) {Text("Vista previa",color=Color.White)}
        }}
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Mira al frente, gira a un lado y vuelve al frente").assertDoesNotExist()
        compose.onNodeWithTag("empezar-captura").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            for(t in 0L..3000L step 100)if(gate.update(front,t).action==FaceAction.SAMPLE)gate.sampleAccepted(t)
            state=gate.snapshot();assertEquals(1,gate.samples)
        }
        compose.mainClock.advanceTimeBy(1400)
        compose.runOnIdle {assertFalse(finished)}
        compose.onNodeWithText("Validación frontal").assertExists()
        compose.onNodeWithText("Paso 3 de 3",substring=true).assertDoesNotExist()
        screenshot("unica-posicion-frontal")
        compose.runOnIdle {
            gate.invalidate()
            state=gate.update(front.copy(yaw=20f),3100)
            assertEquals(1,gate.samples)
            for(t in 3200L..5500L step 100)if(gate.update(front,t).action==FaceAction.SAMPLE)gate.sampleAccepted(t)
            state=gate.snapshot();assertEquals(3,gate.samples)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Captura completada").assertExists()
        compose.mainClock.advanceTimeBy(1400)
        compose.runOnIdle {assertTrue(finished)}
    }
    private fun screenshot(name: String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"qa-manual/$name.png")
        file.parentFile?.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
