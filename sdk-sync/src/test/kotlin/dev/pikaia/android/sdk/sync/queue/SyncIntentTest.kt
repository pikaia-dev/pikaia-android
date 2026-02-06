package dev.pikaia.android.sdk.sync.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncIntentTest {

    @Test
    fun `toApiString returns lowercase intent`() {
        assertEquals("create", SyncIntent.CREATE.toApiString())
        assertEquals("update", SyncIntent.UPDATE.toApiString())
        assertEquals("delete", SyncIntent.DELETE.toApiString())
    }

    @Test
    fun `fromApiString parses lowercase intent`() {
        assertEquals(SyncIntent.CREATE, SyncIntent.fromApiString("create"))
        assertEquals(SyncIntent.UPDATE, SyncIntent.fromApiString("update"))
        assertEquals(SyncIntent.DELETE, SyncIntent.fromApiString("delete"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromApiString throws for unknown intent`() {
        SyncIntent.fromApiString("unknown")
    }

    @Test
    fun `roundtrip conversion preserves intent`() {
        for (intent in SyncIntent.values()) {
            val apiString = intent.toApiString()
            val parsed = SyncIntent.fromApiString(apiString)
            assertEquals(intent, parsed)
        }
    }
}
