package com.oratakashi.uangku.core.libs.core_crypto.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.SecureStorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecureStorage] backed by a non-exportable AES-256-GCM key held in the AndroidKeyStore
 * (hardware-backed where available). Values are encrypted with that key and the resulting
 * `iv‖ciphertext` is stored, base64-encoded, in a private SharedPreferences file.
 *
 * Replaces the deprecated `EncryptedSharedPreferences`/`MasterKey` (androidx.security.crypto)
 * without pulling that library in. Disk I/O runs on [Dispatchers.IO].
 *
 * @since 17 July 2026
 */
class AndroidKeystoreSecureStorage(
    context: Context,
    private val prefsName: String = DEFAULT_PREFS_NAME,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SecureStorage {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(value.encodeToByteArray())
            val stored = EncodingUtils.encodeBase64(cipher.iv + ciphertext)
            if (!prefs.edit().putString(key, stored).commit()) {
                throw SecureStorageException.WriteFailure()
            }
        } catch (e: SecureStorageException) {
            throw e
        } catch (e: Throwable) {
            throw SecureStorageException.WriteFailure(e)
        }
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(key, null) ?: return@withContext null
        try {
            val raw = EncodingUtils.decodeBase64(stored)
            val iv = raw.copyOfRange(0, IV_SIZE)
            val ciphertext = raw.copyOfRange(IV_SIZE, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext).decodeToString()
        } catch (e: Throwable) {
            throw SecureStorageException.ReadFailure(e)
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        try {
            if (!prefs.edit().remove(key).commit()) throw SecureStorageException.DeleteFailure()
        } catch (e: SecureStorageException) {
            throw e
        } catch (e: Throwable) {
            throw SecureStorageException.DeleteFailure(e)
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            if (!prefs.edit().clear().commit()) throw SecureStorageException.DeleteFailure()
        } catch (e: SecureStorageException) {
            throw e
        } catch (e: Throwable) {
            throw SecureStorageException.DeleteFailure(e)
        }
    }

    override suspend fun contains(key: String): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(key)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val DEFAULT_PREFS_NAME = "uangku_core_crypto_secure_storage"
        const val DEFAULT_KEY_ALIAS = "uangku_core_crypto_secure_storage_key"
    }
}
