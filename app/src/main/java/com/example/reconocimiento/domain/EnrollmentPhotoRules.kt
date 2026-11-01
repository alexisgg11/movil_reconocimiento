package com.example.reconocimiento.domain

import kotlin.math.abs

object EnrollmentPhotoRules {
    fun validate(yaw: Float,pitch: Float,roll: Float,leftEye: Float?,rightEye: Float?,landmarksComplete: Boolean) {
        if(!yaw.isFinite()||!pitch.isFinite()||!roll.isFinite()||abs(yaw)>7||abs(pitch)>10||abs(roll)>7)
            throw AppFailure("El rostro debe estar de frente y la cabeza recta. Elige otra foto.","FOTO_NO_FRONTAL")
        if(!landmarksComplete)throw AppFailure("No se distinguen ambos ojos, nariz y boca. Usa una foto con el rostro completo.","ROSTRO_INCOMPLETO")
        if(leftEye==null||rightEye==null||!leftEye.isFinite()||!rightEye.isFinite()||leftEye<.75f||rightEye<.75f)
            throw AppFailure("Los ojos deben verse abiertos y con claridad. Elige otra foto.","OJOS_NO_VISIBLES")
    }
}
