package com.example.reconocimiento.domain

data class FaceAttributes(val leftEye: Float,val rightEye: Float,val eyeglasses: Float,val mask: Float,val sunglasses: Float) {
    fun blockingMessage(): String? {
        if(listOf(leftEye,rightEye,eyeglasses,mask,sunglasses).any {!it.isFinite()||it !in 0f..1f})
            return "No se pudo comprobar el rostro. Repite la captura."
        if(maxOf(eyeglasses,sunglasses)>=.5f)return "Retira los lentes para continuar"
        if(mask>=.5f)return "Retira la mascarilla para continuar"
        if(maxOf(eyeglasses,sunglasses,mask)>=.25f)return "No se distingue bien el rostro. Mejora la luz y despeja tu cara"
        return null
    }
    fun requireClear(){blockingMessage()?.let {throw AppFailure(it,"ROSTRO_CUBIERTO")}}
}
