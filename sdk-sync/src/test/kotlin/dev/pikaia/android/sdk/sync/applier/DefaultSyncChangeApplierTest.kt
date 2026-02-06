package dev.pikaia.android.sdk.sync.applier

import dev.pikaia.android.sdk.sync.data.response.SyncChange
import dev.pikaia.android.sdk.sync.queue.SyncIntent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultSyncChangeApplierTest {

    private lateinit var applier: DefaultSyncChangeApplier
    private lateinit var testHandler: TestEntityHandler

    @Before
    fun setup() {
        applier = DefaultSyncChangeApplier()
        testHandler = TestEntityHandler()
    }

    @Test
    fun `applyChanges returns empty result for empty list`() = runTest {
        val result = applier.applyChanges(emptyList())

        assertEquals(0, result.successCount)
        assertEquals(0, result.failureCount)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `applyChanges routes to registered handler`() = runTest {
        applier.register("test.entity", testHandler)

        val change = SyncChange(
            entityType = "test.entity",
            entityId = "123",
            operation = "create",
            data = """{"id":"123","value":"test"}""",
            version = 1,
            updatedAt = Clock.System.now()
        )

        val result = applier.applyChanges(listOf(change))

        assertEquals(1, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(1, testHandler.appliedEntities.size)
        assertEquals("123", testHandler.appliedEntities[0].id)
    }

    @Test
    fun `applyChanges fails when no handler registered`() = runTest {
        val change = SyncChange(
            entityType = "unknown.entity",
            entityId = "123",
            operation = "create",
            data = """{"id":"123"}""",
            version = 1,
            updatedAt = Clock.System.now()
        )

        val result = applier.applyChanges(listOf(change))

        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(1, result.failures.size)
        assertTrue(result.failures[0].error.contains("No handler registered"))
    }

    @Test
    fun `applyChanges handles DELETE operations`() = runTest {
        applier.register("test.entity", testHandler)

        val change = SyncChange(
            entityType = "test.entity",
            entityId = "123",
            operation = "delete",
            data = null,
            version = 1,
            updatedAt = Clock.System.now()
        )

        val result = applier.applyChanges(listOf(change))

        assertEquals(1, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(1, testHandler.deletedIds.size)
        assertEquals("123", testHandler.deletedIds[0])
    }

    @Test
    fun `applyChanges fails for CREATE UPDATE without payload`() = runTest {
        applier.register("test.entity", testHandler)

        val change = SyncChange(
            entityType = "test.entity",
            entityId = "123",
            operation = "create",
            data = null, // Missing payload
            version = 1,
            updatedAt = Clock.System.now()
        )

        val result = applier.applyChanges(listOf(change))

        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertTrue(result.failures[0].error.contains("Payload is null"))
    }

    @Test
    fun `applyChanges processes multiple changes`() = runTest {
        applier.register("test.entity", testHandler)

        val changes = listOf(
            SyncChange(
                entityType = "test.entity",
                entityId = "1",
                operation = "create",
                data = """{"id":"1","value":"one"}""",
                version = 1,
                updatedAt = Clock.System.now()
            ),
            SyncChange(
                entityType = "test.entity",
                entityId = "2",
                operation = "update",
                data = """{"id":"2","value":"two"}""",
                version = 1,
                updatedAt = Clock.System.now()
            ),
            SyncChange(
                entityType = "test.entity",
                entityId = "3",
                operation = "delete",
                data = null,
                version = 1,
                updatedAt = Clock.System.now()
            )
        )

        val result = applier.applyChanges(changes)

        assertEquals(3, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(2, testHandler.appliedEntities.size)
        assertEquals(1, testHandler.deletedIds.size)
    }

    @Test
    fun `applyChanges continues after individual failures`() = runTest {
        applier.register("test.entity", testHandler)

        val changes = listOf(
            SyncChange(
                entityType = "test.entity",
                entityId = "1",
                operation = "create",
                data = """{"id":"1","value":"one"}""",
                version = 1,
                updatedAt = Clock.System.now()
            ),
            SyncChange(
                entityType = "unknown.entity", // Will fail
                entityId = "2",
                operation = "create",
                data = """{"id":"2"}""",
                version = 1,
                updatedAt = Clock.System.now()
            ),
            SyncChange(
                entityType = "test.entity",
                entityId = "3",
                operation = "create",
                data = """{"id":"3","value":"three"}""",
                version = 1,
                updatedAt = Clock.System.now()
            )
        )

        val result = applier.applyChanges(changes)

        assertEquals(2, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(2, testHandler.appliedEntities.size)
    }

    @Test
    fun `unregister removes handler`() = runTest {
        applier.register("test.entity", testHandler)
        applier.unregister("test.entity")

        val change = SyncChange(
            entityType = "test.entity",
            entityId = "123",
            operation = "create",
            data = """{"id":"123","value":"test"}""",
            version = 1,
            updatedAt = Clock.System.now()
        )

        val result = applier.applyChanges(listOf(change))

        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertTrue(result.failures[0].error.contains("No handler registered"))
    }

    // Test entity and handler
    data class TestEntity(
        val id: String,
        val value: String
    )

    class TestEntityHandler : EntityChangeHandler<TestEntity> {
        val appliedEntities = mutableListOf<TestEntity>()
        val deletedIds = mutableListOf<String>()

        override fun decode(payload: String): TestEntity {
            // Simple JSON parsing for tests
            val idMatch = Regex(""""id":"([^"]+)"""").find(payload)
            val valueMatch = Regex(""""value":"([^"]+)"""").find(payload)

            return TestEntity(
                id = idMatch?.groupValues?.get(1) ?: "",
                value = valueMatch?.groupValues?.get(1) ?: ""
            )
        }

        override suspend fun apply(
            entity: TestEntity,
            intent: SyncIntent,
            entityId: String,
            version: Int
        ) {
            appliedEntities.add(entity)
        }

        override suspend fun applyDelete(entityId: String, payload: String?) {
            deletedIds.add(entityId)
        }
    }
}
