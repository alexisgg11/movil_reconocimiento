package com.example.reconocimiento.domain

import kotlin.math.min

data class CropWindow(val left: Int,val top: Int,val side: Int)

data class PhotoCrop(val zoom: Float=1f,val centerX: Float=.5f,val centerY: Float=.5f) {
    fun window(width: Int,height: Int): CropWindow {
        require(width>0&&height>0)
        val side=(min(width,height)/zoom.coerceIn(1f,4f)).toInt().coerceAtLeast(1)
        val left=(centerX*width-side/2f).toInt().coerceIn(0,width-side)
        val top=(centerY*height-side/2f).toInt().coerceIn(0,height-side)
        return CropWindow(left,top,side)
    }
    fun moved(dx: Float,dy: Float,viewport: Float,width: Int,height: Int): PhotoCrop {
        if(viewport<=0)return this
        val w=window(width,height)
        return copy(centerX=((w.left+w.side/2f)-dx*w.side/viewport)/width,
            centerY=((w.top+w.side/2f)-dy*w.side/viewport)/height).bounded(width,height)
    }
    fun bounded(width: Int,height: Int): PhotoCrop {
        val w=window(width,height)
        return copy(centerX=(w.left+w.side/2f)/width,centerY=(w.top+w.side/2f)/height)
    }
}
