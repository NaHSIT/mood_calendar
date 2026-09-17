package com.example.mdd_calender.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface AuthenticatedCipher {
    fun encrypt(plainText: ByteArray, associatedData: ByteArray): CareResult<String>
    fun decrypt(cipherText: String, associatedData: ByteArray): CareResult<ByteArray>
}

/** Version 1 envelope: v1.base64(nonce).base64(AES-GCM ciphertext+tag). */
class AndroidKeystoreCipher(
    private val alias: String = "mood_calendar_care_v1",
) : AuthenticatedCipher {
    override fun encrypt(plainText: ByteArray, associatedData: ByteArray): CareResult<String> = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        val encrypted = cipher.doFinal(plainText)
        listOf(VERSION, encode(cipher.iv), encode(encrypted)).joinToString(".")
    }.fold(
        onSuccess = { CareResult.Success(it) },
        onFailure = { CareResult.Failure(CareFailure.CryptographyFailure("Sensitive value could not be encrypted")) },
    )

    override fun decrypt(cipherText: String, associatedData: ByteArray): CareResult<ByteArray> = runCatching {
        val parts = cipherText.split('.')
        require(parts.size == 3 && parts[0] == VERSION) { "Unsupported encrypted envelope" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(TAG_BITS, decode(parts[1])))
        cipher.updateAAD(associatedData)
        cipher.doFinal(decode(parts[2]))
    }.fold(
        onSuccess = { CareResult.Success(it) },
        onFailure = { CareResult.Failure(CareFailure.CryptographyFailure("Key is missing or encrypted value failed authentication")) },
    )

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun getExistingKey(): SecretKey =
        keyStore().getKey(alias, null) as? SecretKey ?: error("Encryption key is unavailable")

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val VERSION = "v1"
        fun aad(recordType: String, recordId: String): ByteArray =
            "$recordType:$recordId".toByteArray(StandardCharsets.UTF_8)
    }
}
