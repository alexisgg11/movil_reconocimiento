package com.example.reconocimiento.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.reconocimiento.domain.AppFailure
import com.example.reconocimiento.domain.Position
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeviceLocation(private val context: Context) {
    @Suppress("MissingPermission", "DEPRECATION")
    suspend fun current(): Position = withTimeout(18000) {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            throw AppFailure("Activa la ubicación precisa para comprobar el radio de asistencia.")
        suspendCancellableCoroutine { continuation ->
            val token=CancellationTokenSource()
            val request=CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0).setDurationMillis(15000).setGranularity(Granularity.GRANULARITY_FINE).build()
            LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(request,token.token)
                .addOnSuccessListener { location ->
                    if(!continuation.isActive) return@addOnSuccessListener
                    if(location==null) continuation.resumeWithException(AppFailure("No se obtuvo una ubicación. Activa el GPS e intenta al exterior."))
                    else {
                        val age=(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos)/1000000
                        val mock=if(Build.VERSION.SDK_INT>=31) location.isMock else location.isFromMockProvider
                        if(mock) continuation.resumeWithException(AppFailure("Desactiva la ubicación simulada para marcar."))
                        else if(age !in 0..15000 || !location.hasAccuracy() || location.accuracy !in 0.1f..25f)
                            continuation.resumeWithException(AppFailure("La ubicación todavía no es suficientemente precisa. Acércate a una ventana o sal al exterior."))
                        else continuation.resume(Position(location.latitude,location.longitude,location.accuracy,age,false))
                    }
                }.addOnFailureListener { if(continuation.isActive) continuation.resumeWithException(AppFailure("No se pudo obtener tu ubicación. Comprueba que el GPS esté activado.")) }
            continuation.invokeOnCancellation { token.cancel() }
        }
    }
}
