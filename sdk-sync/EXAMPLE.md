# Pikaia SDK Sync - Usage Examples

This document provides detailed examples of using the Sync SDK in real-world scenarios.

## Complete Setup Example

### 1. Define Your Entities

```kotlin
@Serializable
data class Contact(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val syncVersion: Int = 0,
    val updatedAt: Instant
)
```

### 2. Create Base Store

The base store provides direct database access without triggering sync operations:

```kotlin
class BaseContactStore(private val database: AppDatabase) {

    suspend fun getById(id: String): Contact? {
        return database.contactDao().getById(id)
    }

    suspend fun getAll(): List<Contact> {
        return database.contactDao().getAll()
    }

    suspend fun upsert(contact: Contact) {
        database.contactDao().upsert(contact.toEntity())
    }

    suspend fun delete(id: String) {
        database.contactDao().deleteById(id)
    }
}
```

### 3. Implement Change Handler

```kotlin
class ContactChangeHandler(
    private val baseStore: BaseContactStore
) : EntityChangeHandler<Contact> {

    override fun decode(payload: String): Contact {
        return Json.decodeFromString(payload)
    }

    override suspend fun apply(
        entity: Contact,
        intent: SyncIntent,
        entityId: String,
        version: Int
    ) {
        val existing = baseStore.getById(entityId)

        // Only apply if incoming version is newer
        if (existing == null || version > existing.syncVersion) {
            when (intent) {
                SyncIntent.CREATE, SyncIntent.UPDATE -> {
                    val updated = entity.copy(syncVersion = version)
                    baseStore.upsert(updated)
                    Log.i(TAG, "Applied ${intent.name} for contact $entityId (v$version)")
                }
                SyncIntent.DELETE -> {
                    baseStore.delete(entityId)
                    Log.i(TAG, "Deleted contact $entityId")
                }
            }
        } else {
            Log.d(TAG, "Skipped contact $entityId - local version is newer")
        }
    }

    override suspend fun applyDelete(entityId: String, payload: String?) {
        baseStore.delete(entityId)
    }

    companion object {
        private const val TAG = "ContactChangeHandler"
    }
}
```

### 4. Create Syncable Store

The syncable store wraps the base store and queues sync operations:

```kotlin
class SyncableContactStore(
    private val baseStore: BaseContactStore,
    private val operationQueue: OperationQueue,
    private val syncScheduler: SyncScheduler
) {

    // Read operations delegate to base store
    suspend fun getById(id: String): Contact? = baseStore.getById(id)
    suspend fun getAll(): List<Contact> = baseStore.getAll()

    // Write operations trigger sync
    suspend fun create(contact: Contact) {
        // 1. Save locally
        baseStore.upsert(contact)

        // 2. Queue sync operation
        operationQueue.enqueue(
            entityType = "crm.contact",
            entityId = contact.id,
            intent = SyncIntent.CREATE,
            payload = Json.encodeToString(contact)
        )

        // 3. Schedule debounced sync
        syncScheduler.scheduleSync()
    }

    suspend fun update(contact: Contact) {
        baseStore.upsert(contact)

        operationQueue.enqueue(
            entityType = "crm.contact",
            entityId = contact.id,
            intent = SyncIntent.UPDATE,
            payload = Json.encodeToString(contact)
        )

        syncScheduler.scheduleSync()
    }

    suspend fun delete(id: String) {
        baseStore.delete(id)

        // For deletes, payload can be null
        operationQueue.enqueue(
            entityType = "crm.contact",
            entityId = id,
            intent = SyncIntent.DELETE,
            payload = null
        )

        syncScheduler.scheduleSync()
    }
}
```

### 5. Initialize Sync System

```kotlin
class SyncModule(
    private val context: Context,
    private val apiClient: APIClient,
    private val coroutineScope: CoroutineScope
) {

    // Base stores
    private val baseContactStore = BaseContactStore(appDatabase)
    private val baseTagStore = BaseTagStore(appDatabase)

    // Sync components
    private val syncDatabase = SyncDatabase.getInstance(context)
    private val operationQueue = OperationQueue(syncDatabase.syncOperationDao())
    private val cursorStore = SyncCursorStore(context)
    private val syncAPI = SyncAPI(apiClient)

    // Change applier
    private val changeApplier = DefaultSyncChangeApplier().apply {
        register("crm.contact", ContactChangeHandler(baseContactStore))
        register("crm.tag", TagChangeHandler(baseTagStore))
    }

    // Sync engine
    val syncEngine = SyncEngine(
        syncAPI = syncAPI,
        operationQueue = operationQueue,
        cursorStore = cursorStore,
        changeApplier = changeApplier
    )

    // Sync scheduler
    val syncScheduler = SyncScheduler(
        syncEngine = syncEngine,
        scope = coroutineScope,
        debounceDelay = 5.seconds
    )

    // Syncable stores (used by app)
    val contactStore = SyncableContactStore(
        baseStore = baseContactStore,
        operationQueue = operationQueue,
        syncScheduler = syncScheduler
    )

    fun initialize() {
        // Start scheduler
        syncScheduler.start()

        // Set up background sync
        SyncWorkerProvider.syncEngine = syncEngine
        val workScheduler = SyncWorkScheduler(context)
        workScheduler.schedulePeriodicSync(intervalMinutes = 15)

        // Optionally trigger initial sync
        coroutineScope.launch {
            try {
                syncEngine.sync()
            } catch (e: Exception) {
                Log.e(TAG, "Initial sync failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "SyncModule"
    }
}
```

## Usage in App

### Creating a Contact

```kotlin
class ContactViewModel(
    private val contactStore: SyncableContactStore
) : ViewModel() {

    fun createContact(name: String, email: String) {
        viewModelScope.launch {
            try {
                val contact = Contact(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    email = email,
                    syncVersion = 0,
                    updatedAt = Clock.System.now()
                )

                contactStore.create(contact)
                // Sync will be automatically scheduled

            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
```

### Observing Sync State

```kotlin
class SyncStatusViewModel(
    private val syncEngine: SyncEngine,
    private val operationQueue: OperationQueue
) : ViewModel() {

    val syncState = syncEngine.state
        .stateIn(viewModelScope, SharingStarted.Lazily, SyncState.Idle)

    val pendingCount = operationQueue.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val failedOperations = operationQueue.observeFailedOperations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
```

### Displaying Sync UI

```kotlin
@Composable
fun SyncStatusBar(
    viewModel: SyncStatusViewModel
) {
    val syncState by viewModel.syncState.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (val state = syncState) {
            is SyncState.Idle -> {
                if (pendingCount > 0) {
                    Text("$pendingCount pending changes")
                } else {
                    Text("All synced")
                }
            }
            is SyncState.Syncing -> {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Syncing (${state.phase.name})")
            }
            is SyncState.Success -> {
                Icon(Icons.Default.CheckCircle, "Success")
                Spacer(Modifier.width(8.dp))
                Text("Sync complete")
            }
            is SyncState.Error -> {
                Icon(Icons.Default.Error, "Error")
                Spacer(Modifier.width(8.dp))
                Text("Sync failed: ${state.error}")
            }
        }
    }
}
```

## Manual Sync Operations

### Force Immediate Sync

```kotlin
class SyncViewModel(
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    fun forceSyncNow() {
        syncScheduler.syncNow()
    }
}
```

### Sync Specific Entity Types

```kotlin
// Only sync contacts
syncScheduler.syncNow(entityTypes = listOf("crm.contact"))
```

### Force Complete Resync

```kotlin
viewModelScope.launch {
    try {
        // Clear cursor to restart from beginning
        syncEngine.forceResync()

        // Trigger sync
        syncEngine.sync()
    } catch (e: Exception) {
        Log.e(TAG, "Force resync failed", e)
    }
}
```

## Error Handling

### Handling Failed Operations

```kotlin
class SyncErrorHandler(
    private val operationQueue: OperationQueue
) {

    suspend fun getFailedOperations(): List<SyncOperationEntity> {
        return operationQueue.observeFailedOperations()
            .first()
    }

    suspend fun retryFailed() {
        val failed = getFailedOperations()

        for (op in failed) {
            // Reset retry count and status
            operationQueue.scheduleRetry(op.idempotencyKey, 0)
        }
    }

    suspend fun clearFailed() {
        val failed = getFailedOperations()

        for (op in failed) {
            operationQueue.deleteOperation(op.idempotencyKey)
        }
    }
}
```

## Testing

### Testing Change Handlers

```kotlin
@Test
fun `apply creates new contact`() = runTest {
    val handler = ContactChangeHandler(baseStore)
    val contact = Contact(
        id = "123",
        name = "John Doe",
        email = "john@example.com",
        syncVersion = 0,
        updatedAt = Clock.System.now()
    )

    handler.apply(
        entity = contact,
        intent = SyncIntent.CREATE,
        entityId = "123",
        version = 1
    )

    val stored = baseStore.getById("123")
    assertNotNull(stored)
    assertEquals("John Doe", stored.name)
    assertEquals(1, stored.syncVersion)
}

@Test
fun `apply skips older version`() = runTest {
    // Create contact with version 5
    baseStore.upsert(contact.copy(syncVersion = 5))

    val handler = ContactChangeHandler(baseStore)

    // Try to apply version 3
    handler.apply(
        entity = contact.copy(name = "Updated"),
        intent = SyncIntent.UPDATE,
        entityId = "123",
        version = 3
    )

    // Should not update
    val stored = baseStore.getById("123")
    assertEquals(5, stored.syncVersion)
    assertNotEquals("Updated", stored.name)
}
```

### Testing Sync Engine

```kotlin
@Test
fun `sync pushes pending operations`() = runTest {
    val operation = SyncOperationEntity(
        idempotencyKey = "test-key",
        entityType = "crm.contact",
        entityId = "123",
        intent = "create",
        payload = """{"id":"123","name":"Test"}""",
        status = SyncOperationStatus.PENDING,
        retryCount = 0,
        nextRetryAt = null,
        createdAt = Clock.System.now()
    )

    operationQueue.enqueue(/* ... */)

    val result = syncEngine.sync()

    assertEquals(1, result.pushedCount)
}
```

## Best Practices

1. **Always use two-store pattern** to prevent sync loops
2. **Version-based conflict resolution** - always compare versions
3. **Debounce writes** - let the scheduler coalesce rapid changes
4. **Handle offline gracefully** - operations queue automatically
5. **Monitor failed operations** - provide UI to retry/clear
6. **Test conflict scenarios** - ensure version comparison works
7. **Use background sync** - keep data fresh with WorkManager
8. **Log liberally** - sync issues are easier to debug with logs

## Troubleshooting

### Operations Not Syncing

- Check network connectivity
- Verify SyncScheduler is started
- Check for failed operations in queue
- Ensure API credentials are valid

### Infinite Sync Loops

- Verify two-store pattern is used correctly
- Ensure change handlers use base store (not syncable store)
- Check that sync operations are only queued from syncable store

### Conflicts Not Resolving

- Verify version field is being set correctly
- Ensure server returns version in sync changes
- Check that change handler compares versions properly
