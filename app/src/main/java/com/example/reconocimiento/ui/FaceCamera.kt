package com.example.reconocimiento.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.util.Size
import android.util.Log
import android.content.pm.ApplicationInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as DrawSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.reconocimiento.data.FaceEngine
import com.example.reconocimiento.data.FaceAlignment
import com.example.reconocimiento.data.FaceQuality
import com.example.reconocimiento.data.FaceAttributesEngine
import com.example.reconocimiento.domain.FaceCaptureGate
import com.example.reconocimiento.domain.FaceCaptureMode
import com.example.reconocimiento.domain.FacePose
import com.example.reconocimiento.domain.FacePhase
import com.example.reconocimiento.domain.FaceAction
import com.example.reconocimiento.domain.FaceGateDecision
import com.example.reconocimiento.domain.FaceCapture
import com.example.reconocimiento.domain.AppFailure
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

@Composable
fun FaceCamera(onComplete: (FaceCapture)->Unit,onCancel: ()->Unit,onError: (String)->Unit,enrollment: Boolean=false) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val completed by rememberUpdatedState(onComplete)
    val failed by rememberUpdatedState(onError)
    var guidance by remember {mutableStateOf(FaceGateDecision(FacePhase.FRONT,"Mira al frente",0f))}
    var started by remember {mutableStateOf(false)}
    val executor=remember { Executors.newSingleThreadExecutor() }
    val provider=remember { ProcessCameraProvider.getInstance(context) }
    val analyzer=remember { LiveFaceAnalyzer(context,if(enrollment)FaceCaptureMode.ENROLLMENT else FaceCaptureMode.VERIFICATION, {guidance=it}, { completed(it) }, { failed(it) }) }
    val preview=remember { PreviewView(context).apply { scaleType=PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(Unit) {
        var ownedPreview: Preview?=null
        var ownedAnalysis: ImageAnalysis?=null
        var disposed=false
        provider.addListener({
            if(!disposed)try {
                val p=Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val analysis=ImageAnalysis.Builder().setTargetResolution(Size(640,480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor,analyzer)
                provider.get().bindToLifecycle(lifecycle,CameraSelector.DEFAULT_FRONT_CAMERA,p,analysis)
                ownedPreview=p;ownedAnalysis=analysis
            } catch(e: Exception) { Log.e("ReconocimientoCamera","No se pudo vincular la cámara frontal",e);failed("No se pudo abrir la cámara frontal. Comprueba el permiso y vuelve a intentarlo.") }
        },ContextCompat.getMainExecutor(context))
        onDispose {
            disposed=true
            ownedAnalysis?.clearAnalyzer()
            if(provider.isDone)runCatching { provider.get().unbind(*listOfNotNull(ownedPreview,ownedAnalysis).toTypedArray()) }
            executor.execute { analyzer.close() }
            executor.shutdown()
        }
    }
    Dialog(onDismissRequest=onCancel,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {window?.let { WindowCompat.getInsetsController(it,it.decorView).apply {
            isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true
        }}}
        FaceCaptureScreen(guidance,started,enrollment,{started=true;analyzer.start()},analyzer::advance,analyzer::restart,onCancel) {
            AndroidView(factory={preview},modifier=Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val w=size.width*.72f;val h=minOf(size.height*.83f,w*1.35f)
                drawOval(Color.White.copy(alpha=.85f),Offset((size.width-w)/2,(size.height-h)/2),DrawSize(w,h),style=Stroke(2.dp.toPx()))
            }
        }
    }
}

@Composable
fun FaceCaptureScreen(guidance: FaceGateDecision,started: Boolean,enrollment: Boolean,start: ()->Unit,
                      advance: ()->Unit,restart: ()->Unit,cancel: ()->Unit,preview: @Composable BoxScope.()->Unit) {
    val step=when(guidance.phase){FacePhase.FRONT->0;FacePhase.TURN->1;else->2}
    val onAdvance by rememberUpdatedState(advance)
    LaunchedEffect(started,guidance.phase,guidance.ready,guidance.retry) {
        if(started&&guidance.ready&&!guidance.retry){delay(1200);onAdvance()}
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FB)).systemBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(enrollment) "Registrar rostro" else "Reconocer persona",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                TextButton(onClick=cancel,modifier=Modifier.testTag("cancelar-camara")){Text("Cancelar")}
            }
            Surface(color=DeepBlue,shape=RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(if(!started)"Antes de empezar" else if(!enrollment)"Validación frontal" else "Paso ${step+1} de 3 · ${when(step){0->"Mira al frente";1->"Gira a un lado";else->"Vuelve al frente"}}",color=Color(0xFFD3E8FF),style=MaterialTheme.typography.labelLarge)
                    Text(if(started)guidance.message else if(enrollment)"Mira al frente, gira a un lado y vuelve al frente" else "Mira al frente y mantén la cabeza quieta",color=Color.White,fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold)
                    if(started)LinearProgressIndicator(progress={guidance.progress},modifier=Modifier.fillMaxWidth(),color=Color.White,trackColor=Color(0xFF4E78A1))
                    Text(when {
                        guidance.retry -> "Pulsa Repetir paso o reinicia la captura."
                        started&&guidance.phase==FacePhase.COMPLETE -> "Capturas listas. Comprobando el rostro…"
                        started&&guidance.ready -> "Paso completado. Sigue la próxima indicación."
                        started -> "Mantén la posición hasta completar la barra."
                        !enrollment -> "Una sola posición. La captura es automática."
                        else -> "Mantén cada posición. Los pasos avanzan solos."
                    },color=Color.White,style=MaterialTheme.typography.bodyMedium)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black),content=preview)
            if(!started)PrimaryAction("Empezar captura",start,Modifier.fillMaxWidth().testTag("empezar-captura"),icon=Symbol.Camera)
            else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SecondaryAction("Reiniciar",restart,Modifier.weight(1f).testTag("reiniciar-captura"))
                if(guidance.retry)PrimaryAction("Repetir paso",advance,
                    Modifier.weight(1f).testTag("repetir-captura"))
            }
        }
}

private class LiveFaceAnalyzer(context: Context,private val mode: FaceCaptureMode,private val instruction: (FaceGateDecision)->Unit,
                               private val complete: (FaceCapture)->Unit,private val error: (String)->Unit) : ImageAnalysis.Analyzer, AutoCloseable {
    private val main=ContextCompat.getMainExecutor(context)
    private val debuggable=context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val serial=Executors.newSingleThreadExecutor()
    private val detector=FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).enableTracking().build())
    private val engineDelegate=lazy { FaceEngine(context.applicationContext) }
    private val engine by engineDelegate
    private val attributesDelegate=lazy {FaceAttributesEngine(context.applicationContext)}
    private val attributes by attributesDelegate
    private val working=AtomicBoolean(false)
    private val stopped=AtomicBoolean(false)
    private val cleaned=AtomicBoolean(false)
    private val active=AtomicBoolean(false)
    private val paused=AtomicBoolean(false)
    private val gate=FaceCaptureGate(mode)
    private val vectors=mutableListOf<FloatArray>()
    private var photo=""
    private var faceErrors=0
    private var baseline: FloatArray?=null
    private var tracking: Int?=null
    private var startedAt=0L
    private var lastReport=0L
    private var lastAttributeCheck=0L
    private var attributeMessage: String?=null
    private var retrying=false
    private var pendingCapture: FaceCapture?=null
    fun start(){serial.execute {if(!stopped.get()){startedAt=android.os.SystemClock.elapsedRealtime();reset();active.set(true);say(gate.snapshot().message)}}}
    fun restart(){serial.execute {if(!stopped.get()){reset();say(gate.snapshot().message)}}}
    fun advance(){serial.execute {
        if(stopped.get())return@execute
        pendingCapture?.let {capture->pendingCapture=null;stopped.set(true);main.execute {complete(capture)};return@execute}
        val now=android.os.SystemClock.elapsedRealtime()
        if(retrying){retrying=false;gate.retryCurrentStep(now)}
        else if(!gate.continueStep(now))return@execute
        paused.set(false);say(gate.snapshot().message)
    }}
    private fun reset(){
        gate.reset(android.os.SystemClock.elapsedRealtime());vectors.clear();photo="";baseline=null;tracking=null
        lastAttributeCheck=0;attributeMessage=null;retrying=false;pendingCapture=null;paused.set(false)
    }
    private fun accessories(bitmap: Bitmap,face: Face,now: Long,force: Boolean=false): String? {
        if(force||lastAttributeCheck==0L||now-lastAttributeCheck>=400){
            val start=android.os.SystemClock.elapsedRealtime()
            val reading=attributes.inspect(bitmap,face.boundingBox)
            attributeMessage=reading.blockingMessage()
            if(debuggable)Log.d("ReconocimientoCapture","accessories eyeglasses=${reading.eyeglasses} sunglasses=${reading.sunglasses} mask=${reading.mask} ms=${android.os.SystemClock.elapsedRealtime()-start}")
            lastAttributeCheck=now
        }
        return attributeMessage
    }
    private fun say(message: String,progress: Float?=null) {
        val state=gate.snapshot().let {it.copy(message=message,progress=progress ?: it.progress,retry=retrying)}
        if(state.ready||state.retry)paused.set(true)
        main.execute {if(!stopped.get())instruction(state)}
    }
    override fun analyze(image: ImageProxy) {
        if(stopped.get()||!active.get()||paused.get()||!working.compareAndSet(false,true)){image.close();return}
        val bitmap=try {
            val raw=image.toBitmap()
            if(image.imageInfo.rotationDegrees==0)raw else Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) },true).also { raw.recycle() }
        } catch(e: Exception) { working.set(false);image.close();return }
        detector.process(InputImage.fromBitmap(bitmap,0))
            .addOnSuccessListener(serial) { faces ->
                if(!stopped.get())try { process(bitmap,faces) } catch(e: Exception) {
                    if(e is AppFailure){gate.invalidate();say(e.message ?: "Mejora la iluminación")}
                    else { faceErrors++; if(faceErrors>=3){stopped.set(true);main.execute { error("No se pudo procesar el modelo facial. ${e.message.orEmpty()}") }}
                    else say("Vuelve a centrar tu rostro") }
                }
            }.addOnFailureListener(serial){say("No se pudo detectar el rostro. Repite la captura.")}
            .addOnCompleteListener(serial){bitmap.recycle();image.close();working.set(false);if(stopped.get())release()}
    }
    private fun process(bitmap: Bitmap,faces: List<Face>) {
        if(faces.size!=1){gate.invalidate();lastAttributeCheck=0;say(if(faces.isEmpty()) "Pon tu cara dentro del óvalo" else "Deja una sola persona en cámara");return}
        val face=faces.first()
        if(tracking!=null&&face.trackingId!=tracking){gate.invalidate();lastAttributeCheck=0}
        tracking=face.trackingId
        val now=android.os.SystemClock.elapsedRealtime()
        val box=face.boundingBox
        val pose=FacePose(face.headEulerAngleY,face.headEulerAngleX,face.headEulerAngleZ,face.leftEyeOpenProbability,face.rightEyeOpenProbability,
            box.exactCenterX()/bitmap.width,box.exactCenterY()/bitmap.height,box.width().toFloat()/bitmap.width,
            box.left>0&&box.top>0&&box.right<bitmap.width&&box.bottom<bitmap.height)
        if(pose.insideFrame&&pose.width in .29f.. .76f&&kotlin.math.abs(pose.yaw)<=15&&kotlin.math.abs(pose.pitch)<=15&&kotlin.math.abs(pose.roll)<=15) {
            accessories(bitmap,face,now)?.let {message->gate.invalidate();say(message);return}
        }
        val decision=gate.update(pose,now)
        if(debuggable&&(decision.action!=FaceAction.NONE||now-lastReport>=1000)) {
            lastReport=now
            Log.d("ReconocimientoCapture","t=${now-startedAt} phase=${decision.phase} action=${decision.action} yaw=${pose.yaw} pitch=${pose.pitch} roll=${pose.roll} eyes=${pose.leftEye},${pose.rightEye} hold=${decision.progress}")
        }
        say(decision.message,decision.progress)
        if(decision.action==FaceAction.NONE)return
        accessories(bitmap,face,now,force=lastAttributeCheck!=now)?.let {message->gate.invalidate();say(message);return}
        val cropped=FaceAlignment.align(bitmap,face) ?: run {gate.invalidate();say("Mira al lente con los ojos visibles");return}
        try {
            val quality=FaceQuality.requireUsable(cropped)
            if(debuggable)Log.d("ReconocimientoCapture","quality brightness=${quality.brightness} sharpness=${quality.sharpness}")
            val vector=engine.embedding(cropped)
            if(decision.action==FaceAction.BASELINE){baseline=vector;gate.baselineAccepted(android.os.SystemClock.elapsedRealtime());say(gate.snapshot().message);return}
            val reference=baseline ?: if(mode==FaceCaptureMode.VERIFICATION)vector.also {baseline=it} else return
            if(reference.indices.sumOf {reference[it].toDouble()*vector[it]}<0.8 || vectors.any { previous -> previous.indices.sumOf {previous[it].toDouble()*vector[it]}<0.8 }){
                vectors.clear();photo="";gate.retryCurrentStep(android.os.SystemClock.elapsedRealtime());retrying=true
                if(mode==FaceCaptureMode.VERIFICATION)baseline=null
                say(if(mode==FaceCaptureMode.VERIFICATION)"El rostro cambió durante la captura. Repite con la misma persona." else "El rostro no coincide con el primer paso. Repite con la misma persona.");return
            }
            vectors.add(vector);gate.sampleAccepted(android.os.SystemClock.elapsedRealtime())
            if(photo.isEmpty()) {val bytes=ByteArrayOutputStream();cropped.compress(Bitmap.CompressFormat.JPEG,85,bytes);photo=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)}
            if(vectors.size==3)pendingCapture=FaceCapture(vectors.toList(),photo)
            say(gate.snapshot().message,if(vectors.size==3)1f else vectors.size/3f)
        } finally {cropped.recycle()}
    }
    private fun release(){if(cleaned.compareAndSet(false,true)){if(engineDelegate.isInitialized())engine.close();if(attributesDelegate.isInitialized())attributes.close();serial.shutdown()}}
    override fun close(){stopped.set(true);detector.close();if(!working.get())release()}
}
