package com.example.reconocimiento.data

import android.content.Context
import android.graphics.Bitmap
import com.example.reconocimiento.domain.AppFailure
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.io.FileInputStream
import kotlin.math.*

/** FaceNet 512: RGB 160x160, prewhitening por imagen, salida normalizada L2. */
class FaceEngine(context: Context) : AutoCloseable {
    private val interpreter: Interpreter
    init {
        val model=context.assets.openFd("facenet_512.tflite").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input -> input.channel.map(FileChannel.MapMode.READ_ONLY,descriptor.startOffset,descriptor.declaredLength) }
        }
        interpreter=Interpreter(model,Interpreter.Options().apply { setNumThreads(4); setUseXNNPACK(true) })
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1,160,160,3)))
        require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1,512)))
    }
    fun embedding(bitmap: Bitmap): FloatArray {
        val scaled=Bitmap.createScaledBitmap(bitmap,160,160,true)
        val colors=IntArray(160*160);scaled.getPixels(colors,0,160,0,0,160,160)
        if(scaled!==bitmap)scaled.recycle()
        val pixels=FloatArray(colors.size*3);var k=0
        for(c in colors){pixels[k++]=((c shr 16) and 255).toFloat();pixels[k++]=((c shr 8) and 255).toFloat();pixels[k++]=(c and 255).toFloat()}
        val mean=pixels.average().toFloat()
        if(mean !in 30f..230f) throw AppFailure("Mejora la iluminación del rostro.")
        val deviation=max(sqrt(pixels.sumOf { ((it-mean)*(it-mean)).toDouble() }/pixels.size).toFloat(),1f/sqrt(pixels.size.toFloat()))
        val buffer=ByteBuffer.allocateDirect(pixels.size*4).order(ByteOrder.nativeOrder())
        for(p in pixels)buffer.putFloat((p-mean)/deviation)
        buffer.rewind()
        val result=Array(1){FloatArray(512)};interpreter.run(buffer,result)
        val norm=sqrt(result[0].sumOf { it.toDouble()*it }).toFloat()
        if(!norm.isFinite()||norm<=0) throw AppFailure("No se pudo procesar el rostro. Repite la captura.")
        return FloatArray(512){result[0][it]/norm}
    }
    override fun close() { interpreter.close() }
}
