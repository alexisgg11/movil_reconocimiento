package com.example.reconocimiento.data

import com.example.reconocimiento.domain.*
import org.json.JSONArray
import org.json.JSONObject

object PersonPayloads {
    private fun vector(v: FloatArray)=JSONArray(v.map {it.toDouble()})
    private fun faceFields(change: PersonChange)=JSONObject().put("nombre",change.name).put("foto",change.photo).also {j->
        change.capture?.let {capture->
            j.put("modelo",FACE_MODEL).put("vectores",JSONArray(capture.vectors.map(::vector)))
                .put("fotoRostro",capture.photo).put("origenRostro",capture.source).put("revisionVisual",capture.visuallyReviewed)
        }
    }
    fun validation(change: PersonChange)=JSONObject()
        .put("registro",faceFields(change).put("dni",change.dni))
        .put("version",change.original?.version)

    fun save(change: PersonChange)=faceFields(change).also {j->
        change.fingerprint?.let {proof->
            val fingerprint=JSONObject().put("reto",proof.challenge.id).put("firma",proof.signature)
            proof.capture?.let {fingerprint.put("modelo",FACE_MODEL).put("vector",vector(it.average()))}
            j.put("huella",fingerprint)
        }
        if(change.original==null)j.put("dni",change.dni)
        else j.put("version",change.original.version).put("eliminarHuella",change.removeFingerprint)
    }
}
