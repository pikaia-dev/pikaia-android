package dev.pikaia.android.sdk.auth.token

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.pikaia.android.sdk.core.auth.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

private class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** Reversible fake so tests can assert both round-trips and at-rest opacity. */
private class FakeValueCipher : ValueCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray =
        "ENC(".encodeToByteArray() + plaintext.map { (it + 1).toByte() }.toByteArray() + ")".encodeToByteArray()

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val text = ciphertext.decodeToString()
        check(text.startsWith("ENC(") && text.endsWith(")")) { "Not encrypted with this cipher" }
        return ciphertext.copyOfRange(4, ciphertext.size - 1).map { (it - 1).toByte() }.toByteArray()
    }
}

private class BrokenCipher : ValueCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray): ByteArray = error("key invalidated")
}

class DataStoreTokenStoreTest {

    private val fullSession = AuthSession(
        sessionJwt = "jwt-1",
        sessionToken = "st-1",
        sessionExpiresAt = Instant.parse("2027-07-17T12:00:00Z"),
        deviceUuid = "uuid-1"
    )

    @Test
    fun `round trips a full session`() = runTest {
        val store = DataStoreTokenStore(FakeDataStore(), FakeValueCipher())

        store.setSession(fullSession)

        assertEquals(fullSession, store.getSession())
    }

    @Test
    fun `round trips a session without optional fields`() = runTest {
        val store = DataStoreTokenStore(FakeDataStore(), FakeValueCipher())
        val minimal = AuthSession(sessionJwt = "jwt-1", sessionToken = "st-1")

        store.setSession(minimal)

        assertEquals(minimal, store.getSession())
    }

    @Test
    fun `overwriting drops optional fields no longer present`() = runTest {
        val store = DataStoreTokenStore(FakeDataStore(), FakeValueCipher())

        store.setSession(fullSession)
        store.setSession(AuthSession(sessionJwt = "jwt-2", sessionToken = "st-2"))

        val session = store.getSession()!!
        assertEquals("jwt-2", session.sessionJwt)
        assertNull(session.sessionExpiresAt)
        assertNull(session.deviceUuid)
    }

    @Test
    fun `returns null when nothing is stored`() = runTest {
        assertNull(DataStoreTokenStore(FakeDataStore(), FakeValueCipher()).getSession())
    }

    @Test
    fun `clear removes the session`() = runTest {
        val store = DataStoreTokenStore(FakeDataStore(), FakeValueCipher())

        store.setSession(fullSession)
        store.clear()

        assertNull(store.getSession())
    }

    @Test
    fun `values at rest are not plaintext`() = runTest {
        val dataStore = FakeDataStore()
        val store = DataStoreTokenStore(dataStore, FakeValueCipher())

        store.setSession(fullSession)

        val raw = dataStore.data.first().asMap().values.map { it.toString() }
        assertTrue(raw.isNotEmpty())
        assertTrue(raw.none { it.contains("jwt-1") || it.contains("st-1") || it.contains("uuid-1") })
    }

    @Test
    fun `undecryptable values read as logged out`() = runTest {
        val dataStore = FakeDataStore()
        DataStoreTokenStore(dataStore, FakeValueCipher()).setSession(fullSession)

        // Same file, different key — e.g. a backup restored onto another device
        assertNull(DataStoreTokenStore(dataStore, BrokenCipher()).getSession())
    }
}
