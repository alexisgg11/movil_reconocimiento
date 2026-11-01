package com.example.reconocimiento

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.graphics.Bitmap
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.reconocimiento.data.*
import com.example.reconocimiento.domain.*
import com.example.reconocimiento.ui.*
import com.example.reconocimiento.ui.theme.ReconocimientoTheme
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : FragmentActivity() {
    private lateinit var vm: AppViewModel
    private var cameraVisible by mutableStateOf(false)
    private var cameraMode by mutableStateOf(FaceCaptureMode.ENROLLMENT)
    private var cameraResult: CompletableDeferred<FaceCapture>?=null
    private var photoResult: CompletableDeferred<Uri?>?=null
    private var cropSource by mutableStateOf<Bitmap?>(null)
    private var cropResult: CompletableDeferred<Bitmap?>?=null
    private val photoLauncher=registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->photoResult?.complete(uri)}
    private var permissionResult: CancellableContinuation<Unit>?=null
    private val reportLauncher=registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {uri->
        val file=java.io.File(cacheDir,"reporte-pendiente.xlsx")
        if(uri!=null)lifecycleScope.launch {vm.work {
            withContext(Dispatchers.IO) {
                if(!file.isFile)throw AppFailure("Vuelve a descargar el reporte.")
                contentResolver.openOutputStream(uri,"w")?.use {output->file.inputStream().use {it.copyTo(output)}}
                    ?: throw AppFailure("No se pudo guardar en la ubicación elegida.")
                file.delete()
            }
            "Reporte Excel guardado."
        }} else file.delete()
    }
    private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionResult?.let { continuation ->
            if(continuation.isActive) {
                if(result.values.all {it})continuation.resume(Unit)
                else continuation.resumeWithException(AppFailure("Autoriza los permisos solicitados en los ajustes de la aplicación para continuar."))
            }
        }
        permissionResult=null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm=ViewModelProvider(this,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>): T = AppViewModel(HttpAttendanceRepository()) as T
        })[AppViewModel::class.java]
        val biometrics=DeviceBiometrics(this)
        val location=DeviceLocation(applicationContext)
        val operations=object: DeviceOperations {
            override suspend fun captureFace(mode: FaceCaptureMode): FaceCapture {
                permissions(Manifest.permission.CAMERA)
                val result=CompletableDeferred<FaceCapture>()
                cameraResult=result;cameraMode=mode;cameraVisible=true
                return try {withTimeout(300000){result.await()}} finally {cameraVisible=false;cameraResult=null}
            }
            override suspend fun currentPosition(): Position {
                permissions(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
                return location.current().also {p->vm.change {it.copy(position=p)}}
            }
            override fun publicKey()=biometrics.publicKey()
            override suspend fun sign(challenge: Challenge)=biometrics.sign(challenge)
            override suspend fun confirmFaceForFingerprint()=suspendCancellableCoroutine<Unit> {continuation->
                val dialog=android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Confirmar identidad")
                    .setMessage("Huella confirmada. Ahora verifica el rostro de esta ficha para vincular el teléfono por primera vez.")
                    .setPositiveButton("Verificar rostro"){_,_->if(continuation.isActive)continuation.resume(Unit)}
                    .setNegativeButton("Cancelar"){_,_->if(continuation.isActive)continuation.resumeWithException(AppFailure("Vinculación cancelada."))}
                    .setOnCancelListener {if(continuation.isActive)continuation.resumeWithException(AppFailure("Vinculación cancelada."))}.show()
                continuation.invokeOnCancellation {dialog.dismiss()}
            }
        }
        val actions=AttendanceActions(vm.repository,operations) {stage->vm.change {it.copy(activity=when(stage){
            AttendanceStage.LOCATION -> "Comprobando ubicación…"
            AttendanceStage.FACE_CAPTURE -> "Capturando rostro…"
            AttendanceStage.FACE_COMPARE -> "Comparando con personas registradas…"
            AttendanceStage.FINGERPRINT -> "Esperando la huella de la cuenta vinculada…"
        })}}
        fun work(block: suspend ()->String?) { lifecycleScope.launch {vm.work {networkPermission();block()}} }
        setContent {
            ReconocimientoTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                ReconocimientoApp(state,vm,
                    refresh={work {vm.refresh();null}},
                    capture={work {vm.validateIdentity();val capture=operations.captureFace();vm.change {it.copy(activity="Validando registro facial…")};vm.acceptFace(capture);null}},
                    photo={work {
                        vm.validateIdentity()
                        val result=CompletableDeferred<Uri?>();photoResult=result
                        try {
                            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            result.await()?.let {uri->
                                val bitmap=PhotoImporter(applicationContext).load(uri)
                                val cropped=CompletableDeferred<Bitmap?>();cropResult=cropped;cropSource=bitmap
                                try {
                                    cropped.await()?.let {photo->
                                        cropSource=null;cropResult=null
                                        try {
                                            vm.change {it.copy(activity="Validando foto y comprobando duplicados…")}
                                            vm.acceptFace(GalleryFaceEnrollment(applicationContext).prepare(photo,true))
                                        } finally {photo.recycle()}
                                    }
                                } finally {cropSource=null;cropResult=null}
                            }
                        } finally {photoResult=null}
                        null
                    }},
                    next={work {vm.nextPerson();null}},
                    save={work {vm.savePerson()}},
                    delete={person->work {vm.repository.delete(person.dni);vm.change {it.copy(detail=null)};vm.refresh();"Persona eliminada. Ya no podrá marcar asistencia."}},
                    prepareFingerprint={work {val target=vm.fingerprintTarget();vm.fingerprintPrepared(actions.prepareFingerprint(target.dni,target.original));null}},
                    facial={work {vm.change {it.copy(activity="Verificando ubicación y rostro…")};val attendance=actions.markFacial();vm.returnToToday();vm.refresh();vm.change {it.copy(attendanceDetail=attendance)};null}},
                    fingerprint={work {vm.change {it.copy(activity="Verificando la cuenta vinculada…")};val attendance=actions.markFingerprint();vm.returnToToday();vm.refresh();vm.change {it.copy(attendanceDetail=attendance)};null}},
                    selectDate={date->work {vm.selectAttendanceDate(date);null}},
                    downloadReport={from,to->work {
                        vm.change {it.copy(activity="Preparando reporte Excel…")}
                        val bytes=vm.repository.attendanceWorkbook(from,to)
                        withContext(Dispatchers.IO){java.io.File(cacheDir,"reporte-pendiente.xlsx").writeBytes(bytes)}
                        reportLauncher.launch("asistencias_${from ?: "historial"}${to?.let {"_$it"}.orEmpty()}.xlsx")
                        null
                    }},
                    configure={site->work {val saved=vm.repository.configure(site);vm.change {it.copy(site=saved,zoneCheck=null)};"Configuración guardada."}},
                    locate={callback->work {callback(operations.currentPosition());null}},
                    networkInfo={callback->work {
                        callback(vm.repository.network());null
                    }},
                    checkLocation={work {val result=vm.repository.checkZone(operations.currentPosition());vm.change {it.copy(zoneCheck=result)};null}},
                    openCalibration={work {vm.change {it.copy(calibrationOpen=true,trialResult=null)};val summary=vm.repository.trialSummary();vm.change {it.copy(trialSummary=summary)};null}},
                    testFace={expected->work {val capture=operations.captureFace(FaceCaptureMode.VERIFICATION);vm.change {it.copy(activity="Comparando rostro de prueba…")};val result=vm.repository.testFace(capture,expected);vm.change {it.copy(trialResult=result,trialSummary=result.summary)};null}},
                    sensorSettings={
                        val intent=if(Build.VERSION.SDK_INT>=30)Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                            .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            else Intent(Settings.ACTION_SECURITY_SETTINGS)
                        runCatching {startActivity(intent)}.onFailure {vm.error("Abre los ajustes de seguridad del teléfono para administrar las huellas.")}
                    })
                if(cameraVisible)FaceCamera(enrollment=cameraMode==FaceCaptureMode.ENROLLMENT,
                    onComplete={cameraResult?.complete(it)},
                    onCancel={cameraResult?.completeExceptionally(AppFailure("Captura cancelada."))},
                    onError={cameraResult?.completeExceptionally(AppFailure(it))})
                cropSource?.let {PhotoCropDialog(it,{photo->if(cropResult?.complete(photo)!=true)photo.recycle()},{cropResult?.complete(null)})}
            }
        }
        work {vm.refresh();null}
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while(true){delay(5000);vm.synchronize()}
            }
        }
    }
    private suspend fun networkPermission() {
        if(Build.VERSION.SDK_INT>=37)permissions("android.permission.ACCESS_LOCAL_NETWORK")
    }
    private suspend fun permissions(vararg requested: String) {
        val missing=requested.filter {ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED}
        if(missing.isEmpty())return
        suspendCancellableCoroutine<Unit> { continuation ->
            permissionResult=continuation
            permissionLauncher.launch(missing.toTypedArray())
            continuation.invokeOnCancellation {permissionResult=null}
        }
    }
}
