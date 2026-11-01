package com.example.reconocimiento.data

import android.graphics.Bitmap
import com.example.reconocimiento.domain.AppFailure

data class FaceQualityReading(val brightness: Double,val sharpness: Double)

object FaceQuality {
    fun measure(bitmap: Bitmap): FaceQualityReading {
        val w=bitmap.width;val h=bitmap.height
        val colors=IntArray(w*h);bitmap.getPixels(colors,0,w,0,0,w,h)
        val gray=DoubleArray(colors.size){i->val c=colors[i];.2126*((c shr 16)and 255)+.7152*((c shr 8)and 255)+.0722*(c and 255)}
        var sum=0.0;var squares=0.0;var count=0
        for(y in h/6 until h*5/6)for(x in w/6 until w*5/6){
            val i=y*w+x;val v=gray[i-1]+gray[i+1]+gray[i-w]+gray[i+w]-4*gray[i]
            sum+=v;squares+=v*v;count++
        }
        return FaceQualityReading(gray.average(),(squares/count-(sum/count)*(sum/count)).coerceAtLeast(0.0))
    }
    fun requireUsable(bitmap: Bitmap): FaceQualityReading {
        val reading=measure(bitmap)
        if(reading.brightness !in 30.0..230.0)throw AppFailure("Busca luz de frente, sin reflejos fuertes")
        if(reading.sharpness<35)throw AppFailure("La imagen está borrosa. Limpia el lente y quédate quieto")
        return reading
    }
}
