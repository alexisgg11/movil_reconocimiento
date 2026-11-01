package com.example.reconocimiento

import com.example.reconocimiento.domain.*
import org.junit.Assert.*
import org.junit.Test

class FaceCaptureGateTest {
    private val front=FacePose(0f,0f,0f,.99f,.99f,.5f,.5f,.5f)
    private fun feed(g: FaceCaptureGate,p: FacePose,from: Long,to: Long): FaceGateDecision {
        var last=FaceGateDecision(g.phase,"",0f)
        for(t in from..to step 100)last=g.update(p,t)
        return last
    }
    private fun capturePhase(): Pair<FaceCaptureGate,Long> {
        val gate=FaceCaptureGate().apply {reset(0)}
        assertEquals(FaceAction.BASELINE,feed(gate,front,0,3000).action)
        gate.baselineAccepted(3000)
        assertEquals(FacePhase.FRONT,gate.phase);assertTrue(gate.awaitingContinue)
        assertTrue(gate.continueStep(10000))
        feed(gate,front.copy(yaw=22f),10100,13000)
        assertEquals(FacePhase.TURN,gate.phase);assertTrue(gate.awaitingContinue)
        assertTrue(gate.continueStep(20000))
        assertEquals(FacePhase.RETURN,gate.phase)
        return gate to 20000L
    }
    @Test fun oneFrontalFrameAndFastGesturesDoNotAdvance() {
        val gate=FaceCaptureGate().apply {reset(0)}
        assertEquals(FaceAction.NONE,gate.update(front,0).action)
        assertEquals(FaceAction.NONE,feed(gate,front,100,2900).action)
        assertEquals(FaceAction.BASELINE,gate.update(front,3000).action)
        gate.baselineAccepted(3000)
        assertTrue(gate.continueStep(10000))
        feed(gate,front.copy(yaw=22f),10100,10400)
        feed(gate,front,10500,15000)
        assertEquals(FacePhase.TURN,gate.phase)
        assertFalse(gate.awaitingContinue)
    }
    @Test fun tiltedSidewaysClosedOrUnmeasuredEyesNeverBecomeAFrontalCapture() {
        for(bad in listOf(front.copy(yaw=9f),front.copy(pitch=12f),front.copy(roll=9f),front.copy(leftEye=.3f),front.copy(rightEye=null),front.copy(yaw=Float.NaN))) {
            val gate=FaceCaptureGate().apply {reset(0)}
            assertEquals(FaceAction.NONE,feed(gate,bad,0,5000).action)
        }
    }
    @Test fun movementOrLeavingFrameRestartsTheStableHold() {
        val gate=FaceCaptureGate().apply {reset(0)}
        feed(gate,front,0,2800)
        assertEquals(FaceAction.NONE,gate.update(front.copy(centerX=.57f),2900).action)
        assertEquals(FaceAction.NONE,gate.update(front,3000).action)
        assertEquals(FaceAction.NONE,feed(gate,front,3100,5000).action)
        assertEquals(FaceAction.BASELINE,gate.update(front,5100).action)
        gate.invalidate()
        assertEquals(FaceAction.NONE,gate.update(front.copy(insideFrame=false),5200).action)
    }
    @Test fun samplesRequireFrontalStabilityAndSpacingAfterTheReturn() {
        val (gate,start)=capturePhase()
        assertEquals(FaceAction.NONE,feed(gate,front.copy(yaw=15f),start+100,start+4000).action)
        assertEquals(FaceAction.NONE,feed(gate,front,start+4100,start+6000).action)
        assertEquals(FaceAction.SAMPLE,gate.update(front,start+6100).action)
        gate.sampleAccepted(start+6100)
        assertEquals(FaceAction.NONE,gate.update(front,start+6200).action)
        assertEquals(FaceAction.SAMPLE,feed(gate,front,start+6300,start+7200).action)
        gate.sampleAccepted(start+7200)
        assertEquals(2,gate.samples)
        assertEquals(FacePhase.CAPTURE,gate.phase)
        assertEquals(FaceAction.SAMPLE,feed(gate,front,start+7300,start+8300).action)
        gate.sampleAccepted(start+8300)
        assertEquals(FacePhase.COMPLETE,gate.phase);assertTrue(gate.snapshot().ready)
        assertEquals(FaceAction.NONE,gate.update(front,start+9000).action)
    }
    @Test fun gapsCannotBeCountedAsContinuousFrontalStability() {
        val gate=FaceCaptureGate().apply {reset(0)}
        gate.update(front,0);gate.update(front,4000)
        assertEquals(FaceAction.NONE,gate.update(front,4100).action)
    }
    @Test fun completedStepsWaitForAnExplicitContinueAndCannotBeSkippedByDoubleTap() {
        val gate=FaceCaptureGate().apply {reset(0)}
        feed(gate,front,0,3000);gate.baselineAccepted(3000)
        feed(gate,front.copy(yaw=25f,insideFrame=false),4000,50000)
        assertEquals(FacePhase.FRONT,gate.phase);assertTrue(gate.snapshot().ready)
        assertTrue(gate.continueStep(50000));assertFalse(gate.continueStep(50001))
        assertEquals(FacePhase.TURN,gate.phase)
        feed(gate,front.copy(yaw=22f),50100,53000)
        feed(gate,front,54000,120000)
        assertEquals(FacePhase.TURN,gate.phase);assertTrue(gate.snapshot().ready)
        assertTrue(gate.continueStep(120000));assertEquals(FacePhase.RETURN,gate.phase)
    }
    @Test fun visibilityProblemsAndRetryKeepTheCompletedStepsAndValidSamplePrefix() {
        val (gate,start)=capturePhase()
        assertEquals(FaceAction.SAMPLE,feed(gate,front,start+100,start+3000).action)
        gate.sampleAccepted(start+3000)
        val invalid=gate.update(front.copy(insideFrame=false),start+3100)
        assertEquals(FacePhase.CAPTURE,invalid.phase);assertEquals(1f/3f,invalid.progress,.001f)
        gate.invalidate();assertEquals(FacePhase.CAPTURE,gate.phase)
        gate.retryCurrentStep(start+4000)
        assertEquals(FacePhase.RETURN,gate.phase);assertEquals(0,gate.samples)
        assertFalse(gate.snapshot().ready)
        gate.reset(start+5000);assertEquals(FacePhase.FRONT,gate.phase)
    }
    @Test fun verificationUsesOneFrontalPositionWithThreeStableSpacedSamples() {
        val gate=FaceCaptureGate(FaceCaptureMode.VERIFICATION).apply {reset(0)}
        assertEquals(FaceAction.NONE,feed(gate,front,0,2900).action)
        assertEquals(FaceAction.SAMPLE,gate.update(front,3000).action)
        gate.sampleAccepted(3000)
        assertEquals(1f/3f,gate.snapshot().progress,.001f)
        assertFalse(gate.awaitingContinue)
        assertEquals(FaceAction.NONE,gate.update(front,3100).action)
        assertEquals(FaceAction.SAMPLE,feed(gate,front,3200,4100).action)
        gate.sampleAccepted(4100)
        assertEquals(2f/3f,gate.snapshot().progress,.001f)
        assertEquals(FaceAction.NONE,gate.update(front,4200).action)
        assertEquals(FaceAction.SAMPLE,feed(gate,front,4300,5200).action)
        gate.sampleAccepted(5200)
        assertEquals(FacePhase.COMPLETE,gate.phase);assertTrue(gate.snapshot().ready)
        assertEquals(3,gate.samples)
    }
    @Test fun verificationRequiresFrontAndOpenEyesThroughoutAndRetainsAcceptedProgress() {
        for(bad in listOf(front.copy(yaw=9f),front.copy(pitch=12f),front.copy(roll=9f),front.copy(leftEye=.3f),front.copy(rightEye=null),front.copy(insideFrame=false))) {
            val gate=FaceCaptureGate(FaceCaptureMode.VERIFICATION).apply {reset(0)}
            assertEquals(FaceAction.NONE,feed(gate,bad,0,5000).action)
            assertEquals(FacePhase.FRONT,gate.phase)
            assertEquals(FaceAction.SAMPLE,feed(gate,front,5100,7200).action)
            gate.sampleAccepted(7200)
            val rejected=feed(gate,bad,7300,10000)
            assertEquals(FaceAction.NONE,rejected.action);assertEquals(1f/3f,rejected.progress,.001f)
            assertEquals(1,gate.samples);assertEquals(FacePhase.CAPTURE,gate.phase)
            gate.retryCurrentStep(11000)
            assertEquals(0,gate.samples);assertEquals(FacePhase.FRONT,gate.phase)
            assertFalse(gate.snapshot().ready)
        }
    }
}
