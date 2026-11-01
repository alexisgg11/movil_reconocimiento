package com.example.reconocimiento

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.reconocimiento.data.FaceAttributesEngine
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

class FaceAttributesDeviceTest {
    @Test fun pinnedAccessoryPhotosRunThroughTheMobileModel() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=context.getExternalFilesDir(null)!!
        val detector=FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).build())
        val report=JSONArray()
        try {
            val modelHash=context.assets.open("face_attributes.tflite").use {input->
                val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(8192)
                var count=input.read(buffer);while(count>=0){digest.update(buffer,0,count);count=input.read(buffer)}
                digest.digest().joinToString(""){"%02x".format(it)}
            }
            assertEquals("8a320b1c03c741edfab1391c590a763494c659fc42706e4e09c4c211f4211913",modelHash)
            FaceAttributesEngine(context).use {engine->
                val paths=listOf(1,2,3).map {File(root,"model-fixtures/img$it.jpg")}+
                    listOf("eyeglasses","eyeglasses_man","sunglasses","sunglasses_man","sunglasses_tinted","mask","mask_man","closed_eyes").map {File(root,"attribute-fixtures/$it.jpg")}
                for(path in paths) {
                    assertTrue("Falta la foto de prueba ${path.name}",path.isFile)
                    val bitmap=BitmapFactory.decodeFile(path.path)
                    try {
                        val faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),15,TimeUnit.SECONDS)
                        assertEquals("La referencia ${path.name} debe tener un rostro",1,faces.size)
                        val start=android.os.SystemClock.elapsedRealtime()
                        val result=engine.inspect(bitmap,faces.single().boundingBox)
                        report.put(JSONObject().put("archivo",path.name).put("lentes",result.eyeglasses).put("sol",result.sunglasses)
                            .put("mascarilla",result.mask).put("ojoIzq",result.leftEye).put("ojoDer",result.rightEye)
                            .put("ms",android.os.SystemClock.elapsedRealtime()-start).put("bloqueo",result.blockingMessage()))
                        assertTrue(result.eyeglasses.isFinite());assertTrue(result.eyeglasses in 0f..1f)
                        when {
                            path.name.startsWith("eyeglasses") -> {assertTrue(result.eyeglasses>=.5f);assertTrue(result.blockingMessage()!!.contains("lentes"))}
                            path.name.startsWith("sunglasses") -> {assertTrue(result.sunglasses>=.5f);assertTrue(result.blockingMessage()!!.contains("lentes"))}
                            path.name.startsWith("mask") -> {assertTrue(result.mask>=.5f);assertTrue(result.blockingMessage()!!.contains("mascarilla"))}
                            path.name=="closed_eyes.jpg" -> {assertTrue(minOf(result.leftEye,result.rightEye)<.5f);assertNull(result.blockingMessage())}
                            else -> assertNull("No debe bloquear la referencia sin accesorios ${path.name}",result.blockingMessage())
                        }
                    } finally {bitmap.recycle()}
                }
            }
        } finally {detector.close();File(root,"face-attributes-results.json").writeText(report.toString(2))}
        assertEquals(11,report.length())
    }
}
