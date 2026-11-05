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
    private val adminFaceAlias = "reconocimiento_dispositivo_admin_v1"
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

    fun administratorDeviceKey(): String {
        val keyStore = store()
        if (!keyStore.containsAlias(adminFaceAlias)) {
            val spec = KeyGenParameterSpec.Builder(adminFaceAlias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(spec)
                generateKeyPair()
            }
        }
        return Base64.encodeToString(store().getCertificate(adminFaceAlias).publicKey.encoded, Base64.NO_WRAP)
    }

    fun signAdministratorDevice(challenge: Challenge): String {
        val key = store().getKey(adminFaceAlias, null) as? PrivateKey
            ?: throw AppFailure("Autoriza este dispositivo antes de usar el acceso facial.")
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(key)
            update(challenge.message().toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
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
            val title = when {
                challenge.purpose == "HUELLA" -> "Marcar asistencia"
                challenge.purpose.startsWith("LOGIN_") -> "Iniciar sesión"
                else -> "Registrar huella"
            }
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title)
                .setSubtitle("Confirma con la biometría vinculada al teléfono")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancelar").build(),BiometricPrompt.CryptoObject(signature))
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}
