package com.oratakashi.uangku.core.libs.core_crypto.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.oratakashi.uangku.core.libs.core_crypto.exception.SecureStorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException

/**
 * Android implementation of SecureStorage using EncryptedSharedPreferences.
 *
 * Security design:
 * - Master key encryption: AES256-SIV
 * - Value encryption: AES256-CBC
 * - Key storage: Android Keystore (hardware-backed when available)
 *
 * All operations execute on [Dispatchers.IO] to avoid blocking UI threads.
 * Initialization is lazy to defer keystore overhead until first use.
 *
 * @param context Android application context used for accessing shared preferences
 * @throws SecureStorageException.NotAvailable if EncryptedSharedPreferences cannot be initialized
 * @since 15 May 2026
 */
actual class SecureStorage(private val context: Context) {
    private val preferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFERENCES_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw when (e) {
                is IOException,
                is KeyStoreException,
                is NoSuchAlgorithmException -> SecureStorageException.NotAvailable(e)
                else -> SecureStorageException.NotAvailable(e)
            }
        }
    }

    actual suspend fun put(key: String, value: String) {
        withContext(Dispatchers.IO) {
            try {
                preferences.edit().putString(key, value).apply()
            } catch (e: Exception) {
                throw SecureStorageException.WriteFailure(e)
            }
        }
    }

    actual suspend fun get(key: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                preferences.getString(key, null)
            } catch (e: Exception) {
                throw SecureStorageException.ReadFailure(e)
            }
        }
    }

    actual suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            try {
                preferences.edit().remove(key).apply()
            } catch (e: Exception) {
                throw SecureStorageException.DeleteFailure(e)
            }
        }
    }

    actual suspend fun clear() {
        withContext(Dispatchers.IO) {
            try {
                preferences.edit().clear().apply()
            } catch (e: Exception) {
                throw SecureStorageException.DeleteFailure(e)
            }
        }
    }

    actual suspend fun contains(key: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                preferences.contains(key)
            } catch (e: Exception) {
                throw SecureStorageException.ReadFailure(e)
            }
        }
    }

    private companion object {
        private const val PREFERENCES_FILE = "secure_storage"
    }
}



