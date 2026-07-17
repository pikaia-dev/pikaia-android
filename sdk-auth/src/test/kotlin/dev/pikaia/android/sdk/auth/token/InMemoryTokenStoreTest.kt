package dev.pikaia.android.sdk.auth.token

import dev.pikaia.android.sdk.core.auth.AuthSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryTokenStoreTest {

    @Test
    fun `round trips a session`() = runTest {
        val store = InMemoryTokenStore()
        val session = AuthSession("jwt-1", "st-1", deviceUuid = "uuid-1")

        store.setSession(session)

        assertEquals(session, store.getSession())
    }

    @Test
    fun `starts empty and clears`() = runTest {
        val store = InMemoryTokenStore()
        assertNull(store.getSession())

        store.setSession(AuthSession("jwt-1", "st-1"))
        store.clear()

        assertNull(store.getSession())
    }
}
