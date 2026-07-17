package dev.pikaia.android.sdk.auth.token

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.pikaia.android.sdk.core.auth.AuthSession
import dev.pikaia.android.sdk.core.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Base64
import kotlin.time.Instant

private val Context.sdkTokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pikaia_tokens"
)

/**
 * Encrypted, persistent [TokenStore] backed by a Preferences DataStore.
 *
 * Every stored value is encrypted with a [ValueCipher] — by default
 * [KeystoreValueCipher], an Android Keystore AES-256-GCM key that never leaves the
 * device — before it hits disk. If a stored value cannot be decrypted (e.g. the
 * preferences file was restored from a backup onto a different device, or the key was
 * invalidated), the store treats it as logged out and returns null.
 *
 * ## Backups
 *
 * Consumers must exclude the token DataStore file from Auto Backup so tokens never
 * land in device backups — add to `dataExtractionRules` (API 31+) and
 * `fullBackupContent`:
 *
 * ```xml
 * <exclude domain="file" path="datastore/pikaia_tokens.preferences_pb" />
 * ```
 *
 * Even if the file is backed up, the Keystore key is not, so restored ciphertext is
 * unreadable — the exclusion avoids shipping ciphertext around, the key design makes
 * it worthless.
 *
 * @param dataStore Preferences DataStore holding the encrypted values
 * @param cipher Cipher used to protect values at rest
 */
class DataStoreTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ValueCipher
) : TokenStore {

    /**
     * Production entry point: Keystore-encrypted store on the app's token DataStore.
     *
     * @param context Application context
     */
    constructor(context: Context) : this(context.sdkTokenDataStore, KeystoreValueCipher())

    override suspend fun getSession(): AuthSession? = withContext(Dispatchers.IO) {
        val preferences = dataStore.data.first()

        val sessionJwt = preferences.decrypt(KEY_SESSION_JWT) ?: return@withContext null
        val sessionToken = preferences.decrypt(KEY_SESSION_TOKEN) ?: return@withContext null

        AuthSession(
            sessionJwt = sessionJwt,
            sessionToken = sessionToken,
            sessionExpiresAt = preferences.decrypt(KEY_SESSION_EXPIRES_AT)
                ?.let { runCatching { Instant.parse(it) }.getOrNull() },
            deviceUuid = preferences.decrypt(KEY_DEVICE_UUID)
        )
    }

    override suspend fun setSession(session: AuthSession) = withContext(Dispatchers.IO) {
        val jwt = encrypt(session.sessionJwt)
        val token = encrypt(session.sessionToken)
        val expiresAt = session.sessionExpiresAt?.let { encrypt(it.toString()) }
        val deviceUuid = session.deviceUuid?.let { encrypt(it) }

        dataStore.edit { preferences ->
            preferences[KEY_SESSION_JWT] = jwt
            preferences[KEY_SESSION_TOKEN] = token
            if (expiresAt != null) preferences[KEY_SESSION_EXPIRES_AT] = expiresAt
            else preferences.remove(KEY_SESSION_EXPIRES_AT)
            if (deviceUuid != null) preferences[KEY_DEVICE_UUID] = deviceUuid
            else preferences.remove(KEY_DEVICE_UUID)
        }
        Unit
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        dataStore.edit { preferences ->
            preferences.clear()
        }
        Unit
    }

    private fun encrypt(value: String): String =
        Base64.getEncoder().encodeToString(cipher.encrypt(value.encodeToByteArray()))

    /**
     * Decrypt a stored value; null when absent or undecryptable (wrong key, corrupt data).
     */
    private fun Preferences.decrypt(key: Preferences.Key<String>): String? {
        val encoded = this[key] ?: return null
        return runCatching {
            cipher.decrypt(Base64.getDecoder().decode(encoded)).decodeToString()
        }.getOrNull()
    }

    private companion object {
        val KEY_SESSION_JWT = stringPreferencesKey("session_jwt")
        val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        val KEY_SESSION_EXPIRES_AT = stringPreferencesKey("session_expires_at")
        val KEY_DEVICE_UUID = stringPreferencesKey("device_uuid")
    }
}
