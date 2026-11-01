package com.example.reconocimiento.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.reconocimiento.domain.AppFailure
import com.example.reconocimiento.domain.Challenge
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.*
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeviceBiometrics(private val activity: FragmentActivity) {
    private val alias="reconocimiento_biometria_v1"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun publicKey(): String {
        val available=BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if(available!=BiometricManager.BIOMETRIC_SUCCESS) throw AppFailure("Configura una huella y el bloqueo de pantalla en los ajustes del teléfono antes de vincularlo.")
        var keyStore=store()
        if(!keyStore.containsAlias(alias)) {
            val generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore")
            val specification=KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256).setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if(Build.VERSION.SDK_INT>=30)specification.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG)
            else { @Suppress("DEPRECATION") specification.setUserAuthenticationValidityDurationSeconds(-1) }
            generator.initialize(specification.build())
            generator.generateKeyPair()
            keyStore=store()
        }
        return Base64.encodeToString(keyStore.getCertificate(alias).publicKey.encoded,Base64.NO_WRAP)
    }
    suspend fun sign(challenge: Challenge): String {
        val signature=try {
            Signature.getInstance("SHA256withECDSA").apply { initSign(store().getKey(alias,null) as PrivateKey) }
        } catch(e: KeyPermanentlyInvalidatedException) {
            store().deleteEntry(alias)
            throw AppFailure("Las huellas del teléfono cambiaron. Desvincula el dispositivo desde Personas y vuelve a vincularlo.")
        }
        return suspendCancellableCoroutine { continuation ->
            val prompt=BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),object: BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if(!continuation.isActive)return
                    try {
                        val approved=result.cryptoObject?.signature ?: throw AppFailure("La autenticación no autorizó la firma.")
                        approved.update(challenge.message().toByteArray(Charsets.UTF_8))
                        continuation.resume(Base64.encodeToString(approved.sign(),Base64.NO_WRAP))
                    } catch(e: Exception) { continuation.resumeWithException(e) }
                }
                override fun onAuthenticationError(errorCode: Int,errString: CharSequence) {
                    if(continuation.isActive)continuation.resumeWithException(AppFailure(errString.toString(),"BIOMETRIA_CANCELADA"))
                }
            })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(if(challenge.purpose=="HUELLA") "Marcar asistencia" else "Registrar huella")
                .setSubtitle(if(challenge.purpose=="HUELLA") "${challenge.name} · DNI ${challenge.dni}" else "Confirma la huella de esta persona")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancelar").build(),BiometricPrompt.CryptoObject(signature))
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}
