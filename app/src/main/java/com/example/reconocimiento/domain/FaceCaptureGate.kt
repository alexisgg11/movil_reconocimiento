package com.example.reconocimiento.domain

import kotlin.math.abs
import kotlin.math.hypot

enum class FacePhase { FRONT, TURN, RETURN, CAPTURE, COMPLETE }
enum class FaceCaptureMode { ENROLLMENT, VERIFICATION }
enum class FaceAction { NONE, BASELINE, SAMPLE }
data class FacePose(val yaw: Float,val pitch: Float,val roll: Float,val leftEye: Float?,val rightEye: Float?,
                    val centerX: Float,val centerY: Float,val width: Float,val insideFrame: Boolean=true)
data class FaceGateDecision(val phase: FacePhase,val message: String,val progress: Float,val action: FaceAction=FaceAction.NONE,
                            val ready: Boolean=false,val retry: Boolean=false)

class FaceCaptureGate(private val mode: FaceCaptureMode=FaceCaptureMode.ENROLLMENT) {
    private val frontalOnly get()=mode==FaceCaptureMode.VERIFICATION
    var phase=FacePhase.FRONT;private set
    var samples=0;private set
    var awaitingContinue=false;private set
    private var entered=0L
    private var stableSince: Long?=null
    private var previous: FacePose?=null
    private var lastFrame: Long?=null
    private var frames=0

    fun reset(now: Long){samples=0;enter(FacePhase.FRONT,now)}
    fun invalidate(){stableSince=null;previous=null;frames=0}
    private fun enter(next: FacePhase,now: Long){phase=next;entered=now;awaitingContinue=false;invalidate();lastFrame=null}
    fun baselineAccepted(now: Long){check(!frontalOnly&&phase==FacePhase.FRONT&&!awaitingContinue);entered=now;awaitingContinue=true;invalidate()}
    fun continueStep(now: Long): Boolean {
        if(!awaitingContinue)return false
        val next=when(phase){FacePhase.FRONT->FacePhase.TURN;FacePhase.TURN->FacePhase.RETURN;else->return false}
        enter(next,now);return true
    }
    fun retryCurrentStep(now: Long){
        val next=if(frontalOnly)FacePhase.FRONT else if(phase==FacePhase.CAPTURE||phase==FacePhase.COMPLETE)FacePhase.RETURN else phase
        if(frontalOnly||next==FacePhase.RETURN)samples=0
        enter(next,now)
    }
    fun sampleAccepted(now: Long){
        check((phase==FacePhase.RETURN||phase==FacePhase.CAPTURE||frontalOnly&&phase==FacePhase.FRONT)&&!awaitingContinue)
        samples++
        enter(if(samples==3)FacePhase.COMPLETE else FacePhase.CAPTURE,now)
        if(samples==3)awaitingContinue=true
    }
    private fun baseMessage()=when(phase){
        FacePhase.FRONT -> "Mira al frente y mantén la cabeza quieta"
        FacePhase.TURN -> "Gira un poco a un lado y mantén la posición"
        FacePhase.RETURN -> "Vuelve al frente y mantén la cabeza quieta"
        FacePhase.CAPTURE -> if(frontalOnly)"Sigue mirando al frente sin moverte" else "Mira al frente sin moverte · ${samples} de 3 capturas"
        FacePhase.COMPLETE -> "Captura completada"
    }
    private fun captureProgress(part: Float=0f)=if(frontalOnly||phase==FacePhase.RETURN||phase==FacePhase.CAPTURE)(samples+part)/3f else part
    fun snapshot(): FaceGateDecision {
        val message=if(awaitingContinue)when(phase){
            FacePhase.FRONT->"Frente validado";FacePhase.TURN->"Giro validado";else->"Captura completada"
        } else baseMessage()
        return FaceGateDecision(phase,message,if(awaitingContinue)1f else captureProgress(),ready=awaitingContinue)
    }

    fun update(p: FacePose,now: Long): FaceGateDecision {
        if(awaitingContinue||phase==FacePhase.COMPLETE)return snapshot()
        if(lastFrame?.let {now-it !in 0..500}==true)invalidate()
        lastFrame=now
        val base=baseMessage()
        val reason=when {
            !listOf(p.yaw,p.pitch,p.roll,p.centerX,p.centerY,p.width).all {it.isFinite()} || p.leftEye?.isFinite()==false || p.rightEye?.isFinite()==false -> "Vuelve a centrarte para medir tu posición"
            !p.insideFrame -> "Coloca toda tu cara dentro de la imagen"
            p.width<.29f -> "Acerca un poco el teléfono"
            p.width>.76f -> "Aleja un poco el teléfono"
            abs(p.centerX-.5f)>.14f || abs(p.centerY-.5f)>.16f -> "Centra tu cara en el óvalo"
            abs(p.pitch)>10 || abs(p.roll)>7 -> "Endereza la cabeza a la altura del teléfono"
            phase==FacePhase.TURN && abs(p.yaw)>32 -> "Gira menos la cabeza"
            phase==FacePhase.TURN && abs(p.yaw)<18 -> base
            phase!=FacePhase.TURN && abs(p.yaw)>7 -> "Mira de frente al lente"
            phase!=FacePhase.TURN && (p.leftEye==null||p.rightEye==null||p.leftEye<.75f||p.rightEye<.75f) -> "Abre los ojos y mira al lente"
            else -> null
        }
        if(reason!=null){invalidate();return FaceGateDecision(phase,reason,captureProgress())}
        val moving=previous?.let {abs(p.yaw-it.yaw)>3||abs(p.pitch-it.pitch)>3||abs(p.roll-it.roll)>3||hypot(p.centerX-it.centerX,p.centerY-it.centerY)>.025f} ?: false
        if(moving){invalidate();previous=p;return FaceGateDecision(phase,"Mantén la posición sin moverte",captureProgress())}
        previous=p;frames++
        if(stableSince==null)stableSince=now
        val hold=if(phase==FacePhase.CAPTURE)1000L else 2000L
        val duration=now-stableSince!!
        val readingTime=if(phase==FacePhase.CAPTURE)0L else 3000L
        val progress=minOf(duration.toFloat()/hold,if(readingTime==0L)1f else (now-entered).toFloat()/readingTime,1f)
        if(duration<hold||frames<5||now-entered<readingTime)return FaceGateDecision(phase,base,captureProgress(progress.coerceAtMost(.95f)))
        return when(phase){
            FacePhase.FRONT -> FaceGateDecision(phase,"Posición frontal estable",captureProgress(1f),if(frontalOnly)FaceAction.SAMPLE else FaceAction.BASELINE)
            FacePhase.TURN -> {awaitingContinue=true;invalidate();snapshot()}
            FacePhase.RETURN,FacePhase.CAPTURE -> FaceGateDecision(phase,"Tomando captura ${samples+1} de 3",captureProgress(1f),FaceAction.SAMPLE)
            else -> FaceGateDecision(phase,base,progress)
        }
    }
}
