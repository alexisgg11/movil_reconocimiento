package com.example.reconocimiento.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.TimeUnit

data class PhotoInspection(val bitmap: Bitmap,val aligned: Bitmap?,val light: Double,
                           val yaw: Float?,val tilt: Float?,val points: List<Pair<Float,Float>>) : AutoCloseable {
    override fun close(){bitmap.recycle();aligned?.recycle()}
}

class PhotoInspector {
    fun inspect(photo: String): PhotoInspection {
        val bytes=Base64.decode(photo,Base64.DEFAULT)
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("No se pudo leer la fotografía")
        val detector=FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build())
        try {
            val pixels=IntArray(bitmap.width*bitmap.height)
            bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val light=pixels.sumOf { .2126*((it shr 16)and 255)+.7152*((it shr 8)and 255)+.0722*(it and 255) }/pixels.size
            val faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),10,TimeUnit.SECONDS)
            val face=faces.singleOrNull()
            val points=listOf(FaceLandmark.LEFT_EYE,FaceLandmark.RIGHT_EYE,FaceLandmark.NOSE_BASE,FaceLandmark.MOUTH_LEFT,FaceLandmark.MOUTH_RIGHT)
                .mapNotNull {face?.getLandmark(it)?.position?.let {p->p.x/bitmap.width to p.y/bitmap.height}}
            return PhotoInspection(bitmap,face?.let {FaceAlignment.align(bitmap,it)},light,face?.headEulerAngleY,face?.headEulerAngleZ,points)
        } catch(e: Exception){bitmap.recycle();throw e}
        finally {detector.close()}
    }
}
