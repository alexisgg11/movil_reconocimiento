package com.example.reconocimiento.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.reconocimiento.domain.*
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GalleryFaceEnrollment(private val context: Context) {
    suspend fun prepare(bitmap: Bitmap,visuallyReviewed: Boolean): FaceCapture = withContext(Dispatchers.Default) {
        if(!visuallyReviewed)throw AppFailure("Revisa que no haya lentes ni objetos sobre el rostro.")
        val detector=FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build())
        try {
            val faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),15,TimeUnit.SECONDS)
            if(faces.isEmpty())throw AppFailure("No se encontró un rostro. Selecciona una foto de la persona.","SIN_ROSTRO")
            if(faces.size!=1)throw AppFailure("La foto debe mostrar una sola persona.","VARIOS_ROSTROS")
            val face=faces.single()
            FaceAttributesEngine(context).use {it.inspect(bitmap,face.boundingBox).requireClear()}
            val points=listOf(FaceLandmark.LEFT_EYE,FaceLandmark.RIGHT_EYE,FaceLandmark.NOSE_BASE,
                FaceLandmark.MOUTH_LEFT,FaceLandmark.MOUTH_RIGHT).map {face.getLandmark(it)?.position}
            EnrollmentPhotoRules.validate(face.headEulerAngleY,face.headEulerAngleX,face.headEulerAngleZ,
                face.leftEyeOpenProbability,face.rightEyeOpenProbability,points.all {p->p!=null&&p.x in 1f..(bitmap.width-2f)&&p.y in 1f..(bitmap.height-2f)})
            val bounds=face.boundingBox
            if(bounds.width()<128||bounds.width()<bitmap.width*.3||bounds.left<0||bounds.top<0||bounds.right>bitmap.width||bounds.bottom>bitmap.height)
                throw AppFailure("Incluye la cabeza completa y acerca el rostro con el recorte, sin cortar la frente ni el mentón.","ENCUADRE_INVALIDO")
            val aligned=FaceAlignment.align(bitmap,face) ?: throw AppFailure("No se distinguen bien los ojos. Usa una foto más nítida.")
            try {
                FaceQuality.requireUsable(aligned)
                val vector=FaceEngine(context).use {it.embedding(aligned)}
                FaceCapture(listOf(vector),jpeg(aligned),"GALERIA",true,jpeg(bitmap))
            } finally {aligned.recycle()}
        } finally {detector.close()}
    }
    private fun jpeg(bitmap: Bitmap): String {
        val output=ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG,88,output)
        return Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP)
    }
}
