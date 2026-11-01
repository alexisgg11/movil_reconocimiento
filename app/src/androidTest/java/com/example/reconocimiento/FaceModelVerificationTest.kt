package com.example.reconocimiento

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.example.reconocimiento.data.FaceAlignment
import com.example.reconocimiento.data.FaceEngine
import com.example.reconocimiento.data.FaceQuality
import com.example.reconocimiento.domain.AppFailure
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class FaceModelVerificationTest {
    @Test fun featurelessBlurredFramesAreRejectedBeforeRecognition() {
        val image=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.rgb(128,128,128))
        try {
            assertEquals(0.0,FaceQuality.measure(image).sharpness,.00001)
            assertThrows(AppFailure::class.java){FaceQuality.requireUsable(image)}
        } finally {image.recycle()}
    }
    @Test fun referencePhotosSeparateMatchingAndDifferentPeople() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures=File(context.getExternalFilesDir(null),"model-fixtures")
        val detector=FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build())
        try {
            FaceEngine(context).use { engine ->
                var closeCapture: FloatArray?=null
                val vectors=listOf(1,2,3,11).map { i ->
                    val path=File(fixtures,"img$i.jpg")
                    assertTrue("Ejecuta scripts/test-face-model.ps1 para cargar las imágenes de referencia.",path.isFile)
                    val bitmap=BitmapFactory.decodeFile(path.path)
                    try {
                        val faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),20,TimeUnit.SECONDS)
                        assertEquals("La imagen $i debe contener un rostro",1,faces.size)
                        val aligned=FaceAlignment.align(bitmap,faces.single()) ?: error("Faltan puntos de ojos en imagen $i")
                        try {
                            if(i==1)assertTrue("La foto de referencia debe superar el filtro inicial de nitidez",FaceQuality.requireUsable(aligned).sharpness>=35)
                            if(i==1){
                                val variation=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888)
                                try {
                                    val lighting=ColorMatrix(floatArrayOf(1f,0f,0f,0f,8f,0f,1f,0f,0f,8f,0f,0f,1f,0f,8f,0f,0f,0f,1f,0f))
                                    Canvas(variation).drawBitmap(aligned,1f,1f,Paint().apply {colorFilter=ColorMatrixColorFilter(lighting)})
                                    closeCapture=engine.embedding(variation)
                                } finally {variation.recycle()}
                            }
                            engine.embedding(aligned)
                        } finally {aligned.recycle()}
                    } finally {bitmap.recycle()}
                }
                fun cosine(a: FloatArray,b: FloatArray)=a.indices.sumOf {a[it].toDouble()*b[it]}
                val same=cosine(vectors[0],vectors[1]);val different=cosine(vectors[0],vectors[2])
                val alternative=cosine(vectors[0],vectors[3]);val alternative2=cosine(vectors[1],vectors[3])
                val controlled=cosine(vectors[0],closeCapture!!)
                File(context.getExternalFilesDir(null),"face-verification.json").writeText("{\"variacionControlada\":$controlled,\"mismaPersona1_2\":$same,\"mismaPersona1_11\":$alternative,\"mismaPersona2_11\":$alternative2,\"distintaPersona1_3\":$different,\"umbral\":0.8}")
                assertTrue("Persona distinta aceptada: $different",different<.8)
                assertTrue("El modelo no separa las identidades: $same vs $different",same>different+.15)
                assertTrue("Una variación leve de luz y posición no alcanza el umbral: $controlled",controlled>=.8)
            }
        } finally {detector.close()}
    }
}
