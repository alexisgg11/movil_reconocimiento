package com.example.reconocimiento.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.reconocimiento.domain.AppFailure
import com.example.reconocimiento.domain.FaceAttributes
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

class FaceAttributesEngine(context: Context): AutoCloseable {
    private val interpreter: Interpreter
    private val inputScale: Float
    private val inputZero: Int
    private val outputScale: Float
    private val outputZero: Int
    init {
        val model=context.assets.openFd("face_attributes.tflite").use {fd->
            FileInputStream(fd.fileDescriptor).use {it.channel.map(FileChannel.MapMode.READ_ONLY,fd.startOffset,fd.declaredLength)}
        }
        interpreter=Interpreter(model,Interpreter.Options().apply {setNumThreads(4);setUseXNNPACK(true)})
        try {
            val input=interpreter.getInputTensor(0);val output=interpreter.getOutputTensor(0)
            require(input.shape().contentEquals(intArrayOf(1,128,128,3))&&input.dataType()==DataType.UINT8)
            require(output.shape().contentEquals(intArrayOf(1,5))&&output.dataType()==DataType.UINT8)
            inputScale=input.quantizationParams().scale;inputZero=input.quantizationParams().zeroPoint
            outputScale=output.quantizationParams().scale;outputZero=output.quantizationParams().zeroPoint
            require(inputScale>0&&outputScale>0)
        } catch(e: Exception){interpreter.close();throw e}
    }
    fun inspect(source: Bitmap,bounds: Rect): FaceAttributes {
        val rect=Rect(bounds)
        if(!rect.intersect(0,0,source.width,source.height)||rect.width()<32||rect.height()<32)
            throw AppFailure("Acerca el rostro para comprobar los lentes.")
        val scale=min(128f/rect.width(),128f/rect.height())
        val width=floor(rect.width()*scale).toInt().coerceIn(1,128)
        val height=floor(rect.height()*scale).toInt().coerceIn(1,128)
        val left=(128-width)/2;val top=(128-height)/2
        val padded=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        val colors=IntArray(128*128)
        try {
            padded.eraseColor(Color.BLACK)
            Canvas(padded).drawBitmap(source,rect,RectF(left.toFloat(),top.toFloat(),(left+width).toFloat(),(top+height).toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
            padded.getPixels(colors,0,128,0,0,128,128)
        } finally {padded.recycle()}
        val input=ByteBuffer.allocateDirect(colors.size*3).order(ByteOrder.nativeOrder())
        for(color in colors)for(channel in 0..2) {
            val shift=(2-channel)*8
            val value=((color shr shift)and 255)/255f
            input.put((value/inputScale+inputZero).roundToInt().coerceIn(0,255).toByte())
        }
        input.rewind()
        val output=ByteBuffer.allocateDirect(5).order(ByteOrder.nativeOrder())
        interpreter.run(input,output);output.rewind()
        val values=FloatArray(5){((output.get().toInt()and 255)-outputZero)*outputScale}
        return FaceAttributes(values[0],values[1],values[2],values[3],values[4])
    }
    override fun close(){interpreter.close()}
}
