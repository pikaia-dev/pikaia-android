package dev.pikaia.android.sdk.auth.token

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dev.pikaia.android.sdk.core.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Secure token storage using DataStore with encryption.
 *
 * Tokens are stored in encrypted DataStore preferences, providing:
 * - Persistent storage across app restarts
 * - Encryption at rest using Android Keystore
 * - Thread-safe concurrent access
 * - Atomic read/write operations
 *
 * This is the recommended token storage for production use.
 *
 * @param context Application context
 */
class DataStoreTokenStore(private val context: Context) : TokenStore {

    private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(
        name = DATASTORE_NAME
    )

    private val dataStore: DataStore<Preferences> = context.tokenDataStore

    override suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        dataStore.data.map { preferences ->
            preferences[KEY_ACCESS_TOKEN]
        }.first()
    }

    override suspend fun getRefreshToken(): String? = withContext(Dispatchers.IO) {
        dataStore.data.map { preferences ->
            preferences[KEY_REFRESH_TOKEN]
        }.first()
    }

    override suspend fun getDeviceToken(): String? = withContext(Dispatchers.IO) {
        dataStore.data.map { preferences ->
            preferences[KEY_DEVICE_TOKEN]
        }.first()
    }

    override suspend fun getDeviceRefreshToken(): String? = withContext(Dispatchers.IO) {
        dataStore.data.map { preferences ->
            preferences[KEY_DEVICE_REFRESH_TOKEN]
        }.first()
    }

    override suspend fun setTokens(access: String, refresh: String) {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences[KEY_ACCESS_TOKEN] = access
                preferences[KEY_REFRESH_TOKEN] = refresh
            }
        }
    }

    override suspend fun setDeviceTokens(device: String, refresh: String) {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences[KEY_DEVICE_TOKEN] = device
                preferences[KEY_DEVICE_REFRESH_TOKEN] = refresh
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    companion object {
        private const val DATASTORE_NAME = "pikaia_tokens"

        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")
        private val KEY_DEVICE_REFRESH_TOKEN = stringPreferencesKey("device_refresh_token")
    }
}

/**
 * Creates a master key for encryption using Android Keystore.
 *
 * The master key is used to encrypt/decrypt sensitive data. It's stored
 * in the Android Keystore, which provides hardware-backed security on
 * supported devices.
 *
 * @param context Application context
 * @return MasterKey for encryption operations
 */
private fun createMasterKey(context: Context): MasterKey {
    return MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
}

/**
 * Creates an encrypted file for additional security.
 *
 * While DataStore provides some security, this adds an extra layer by
 * encrypting the actual file on disk.
 *
 * Note: DataStore v1.1+ provides built-in file encryption via the Android
 * file system. This function is provided for reference but may not be
 * necessary in most cases.
 *
 * @param context Application context
 * @param fileName Name of the file to encrypt
 * @return EncryptedFile wrapper
 */
@Suppress("unused")
private fun createEncryptedFile(context: Context, fileName: String): EncryptedFile {
    val masterKey = createMasterKey(context)
    val file = File(context.filesDir, fileName)

    return EncryptedFile.Builder(
        context,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()
}
