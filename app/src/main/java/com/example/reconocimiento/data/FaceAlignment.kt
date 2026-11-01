package com.example.reconocimiento.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

object FaceAlignment {
    fun align(source: Bitmap,face: Face): Bitmap? {
        val a=face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val b=face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val left=if(a.x<b.x)a else b;val right=if(a.x<b.x)b else a
        if(right.x-left.x<25)return null
        val transform=Matrix()
        transform.setPolyToPoly(floatArrayOf(left.x,left.y,right.x,right.y),0,floatArrayOf(52f,62f,108f,62f),0,2)
        return Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(source,transform,Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
}
