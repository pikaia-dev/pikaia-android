# Phase 3: Sync Engine Implementation Plan

## Overview

Phase 3 implements the offline-first sync engine that enables data synchronization between the Android app and the Pikaia backend. This follows the iOS SDK architecture with Android-specific adaptations, based on production-proven patterns from the iOS Snowball app.

**Important**: This SDK is **generic** and does NOT implement app-specific entity types (like `crm.contact`, `crm.tag`, etc.). Entity types are registered by the consuming application.

See `IOS_SDK_USAGE_ANALYSIS.md` for detailed findings from iOS implementation.

## Goals

1. **Offline-First**: App works without network, queues operations locally
2. **Automatic Sync**: Background sync with WorkManager
3. **Version-Based Conflict Resolution**: Compare sync versions, newest wins
4. **Push-Then-Pull**: Always push pending operations before pulling changes
5. **Idempotent Operations**: UUID-based idempotency keys prevent duplicates
6. **Retry Logic**: Exponential backoff for failed operations
7. **Type Safety**: Strongly-typed entities with generic handlers
8. **Dual API**: Both suspend functions and Flow-based APIs
9. **Two-Store Pattern**: Prevent sync feedback loops with separate base stores
10. **Debounced Sync**: Coalesce rapid changes with scheduler

## Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────┐
│                     Application                          │
│  - Creates/updates/deletes entities locally             │
│  - Calls syncEngine.enqueue(operation)                  │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│                    SyncEngine                            │
│  - Observable state (idle, pushing, pulling, error)     │
│  - sync(): Push → Pull cycle                            │
│  - enqueue(): Queue local changes                       │
│  - triggerFullResync(): Clear and re-pull everything    │
└──────────────────┬──────────────────────────────────────┘
                   │
         ┌─────────┴─────────┐
         │                   │
         ▼                   ▼
┌────────────────┐  ┌────────────────────┐
│OperationQueue  │  │  SyncCursorStore   │
│  (Room DB)     │  │  (SharedPrefs)     │
│  - Pending ops │  │  - Last cursor     │
│  - Retry logic │  │  - Sync metadata   │
└────────┬───────┘  └────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│                      SyncAPI                             │
│  - push(operations): Batch push to server               │
│  - pull(cursor): Get changes since cursor               │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│                 Pikaia Backend API                       │
│  POST /v1/sync/push                                      │
│  POST /v1/sync/pull                                      │
└─────────────────────────────────────────────────────────┘
```

### Component Breakdown

```
sdk-sync/
├── src/main/kotlin/dev/pikaia/android/sdk/sync/
│   ├── api/
│   │   └── SyncAPI.kt                    # Sync endpoints (push/pull)
│   │
│   ├── data/
│   │   ├── request/
│   │   │   ├── SyncPushRequest.kt        # Batch push request
│   │   │   └── SyncOperation.kt          # Individual operation DTO
│   │   ├── response/
│   │   │   ├── SyncPushResponse.kt       # Push result per operation
│   │   │   ├── SyncPullResponse.kt       # Changes + cursor + forceResync
│   │   │   ├── PushResult.kt             # Individual operation result
│   │   │   └── SyncChange.kt             # Server change representation
│   │   └── entity/
│   │       └── SyncOperationEntity.kt    # Room entity for queued ops
│   │
│   ├── database/
│   │   ├── SyncDatabase.kt               # Room database definition
│   │   └── SyncOperationDao.kt           # DAO for operations
│   │
│   ├── engine/
│   │   ├── SyncEngine.kt                 # Main sync orchestrator
│   │   ├── SyncConfiguration.kt          # Configuration options
│   │   ├── SyncState.kt                  # Engine state (sealed class)
│   │   └── SyncResult.kt                 # Sync operation results
│   │
│   ├── queue/
│   │   ├── OperationQueue.kt             # Operation queue manager
│   │   ├── SyncOperationStatus.kt        # Operation status enum
│   │   └── SyncIntent.kt                 # Create/Update/Delete enum
│   │
│   ├── applier/
│   │   ├── SyncChangeApplier.kt          # Interface for applying changes
│   │   ├── DefaultSyncChangeApplier.kt   # Handler routing implementation
│   │   ├── EntityChangeHandler.kt        # Entity-specific handler interface
│   │   ├── SyncBatchResult.kt            # Batch application result
│   │   └── ChangeResult.kt               # Individual change result
│   │
│   ├── cursor/
│   │   └── SyncCursorStore.kt            # Cursor storage with SharedPreferences
│   │
│   ├── retry/
│   │   └── SyncRetryPolicy.kt            # Exponential backoff retry logic
│   │
│   ├── scheduler/
│   │   └── SyncScheduler.kt              # Debounced sync scheduler
│   │
│   └── worker/
│       └── SyncWorker.kt                 # WorkManager background sync
│
└── README.md
```

## Key Findings from iOS Implementation

Based on analysis of production iOS Snowball app (see `IOS_SDK_USAGE_ANALYSIS.md`):

### Critical Patterns

1. **Two-Store Pattern** (prevents sync feedback loops)
   - **Syncable stores**: Used by app features, enqueue to sync engine
   - **Base stores**: Used by change handlers, NO enqueuing
   - Without this, server changes would trigger local writes → enqueue → push → infinite loop

2. **Version-Based Conflict Resolution**
   - Each entity has `syncVersion: Int?` field (server-managed)
   - Change handler compares versions: only apply if `incoming > local`
   - NOT "server always wins" - it's "newest version wins"

3. **Debounced Sync Scheduler**
   - 5-second debounce coalesces rapid changes
   - `requestSync(reason)`: debounced trigger
   - `syncNow(reason)`: immediate trigger

4. **Authentication Mode**
   - iOS app uses `deviceOnly` mode exclusively
   - No automatic token refresh (long-lived device tokens)
   - No magic link flow in production

5. **API Specifics**
   - Pull response field: `cursor` (not `nextCursor`)
   - Pull response includes: `forceResync: Boolean`
   - Push result field: `idempotencyKey` (not `operationId`)
   - Payloads: JSON strings (not complex types)

### Entity Types (App-Specific, NOT in SDK)

The SDK is **generic** - it does NOT hardcode entity types. In the Snowball iOS app, these types are registered:
- `crm.contact` - Customer records
- `crm.tag` - Tags
- `crm.contact_tag` - Relationships
- `crm.note` - Notes

But your app can register ANY entity types - the SDK is agnostic.

## Core Components

### 1. SyncAPI

Provides REST endpoints for push/pull operations.

**Endpoints:**
```kotlin
class SyncAPI(private val client: APIClient) {
    // Suspend variants
    suspend fun push(request: SyncPushRequest): SyncPushResponse
    suspend fun pull(
        since: String? = null,
        entityTypes: List<String>? = null,
        limit: Int? = null
    ): SyncPullResponse

    // Flow variants
    fun pushFlow(request: SyncPushRequest): Flow<SyncPushResponse>
    fun pullFlow(
        since: String? = null,
        entityTypes: List<String>? = null,
        limit: Int? = null
    ): Flow<SyncPullResponse>
}
```

**Note**: Pull uses query parameters (GET request), not request body:
- `GET /v1/sync/pull?since=<cursor>&entity_types=type1,type2&limit=100`
- `POST /v1/sync/push` with request body

### 2. SyncScheduler

Debounces and coalesces sync requests to prevent excessive network calls.

**Purpose**: When user makes rapid changes (e.g., edits contact 5 times in 10 seconds), trigger ONE sync, not five.

```kotlin
class SyncScheduler(
    private val syncEngine: SyncEngine,
    private val tokenStore: TokenStore,
    private val debounceMillis: Long = 5000,  // 5 seconds default
    private val scope: CoroutineScope
) {
    private var scheduledJob: Job? = null
    private var syncJob: Job? = null
    private var hasPendingRequest = false

    /**
     * Request a sync soon (debounced).
     * Multiple rapid calls are coalesced into a single sync.
     */
    fun requestSync(reason: String) {
        hasPendingRequest = true
        scheduledJob?.cancel()

        scheduledJob = scope.launch {
            delay(debounceMillis)
            runSyncIfNeeded(trigger = "debounced:$reason")
        }
    }

    /**
     * Run sync immediately (no debounce).
     * Still coalesces concurrent requests.
     */
    fun syncNow(reason: String) {
        hasPendingRequest = true
        scheduledJob?.cancel()
        scheduledJob = null

        scope.launch {
            runSyncIfNeeded(trigger = "immediate:$reason")
        }
    }

    private suspend fun runSyncIfNeeded(trigger: String) {
        // Skip if no pending changes
        if (!hasPendingRequest && !syncEngine.hasPendingOperations) return

        // Skip if device not provisioned
        val deviceToken = tokenStore.getDeviceToken()
        if (deviceToken == null) {
            Log.d("SyncScheduler", "Sync skipped: device not provisioned")
            hasPendingRequest = false
            return
        }

        // Prevent concurrent syncs
        if (syncJob?.isActive == true) {
            Log.d("SyncScheduler", "Sync already in progress")
            return
        }

        hasPendingRequest = false

        syncJob = scope.launch {
            try {
                syncEngine.sync()
            } catch (e: Exception) {
                Log.e("SyncScheduler", "Sync failed", e)
            }
        }

        syncJob?.join()

        // Check if more work accumulated during sync
        if (hasPendingRequest || syncEngine.hasPendingOperations) {
            runSyncIfNeeded(trigger = "post_run:$trigger")
        }
    }
}
```

**Usage**:
```kotlin
// After local write
contactStore.addContact(contact)
syncScheduler.requestSync(reason = "contact_create")  // Debounced

// User-triggered
syncScheduler.syncNow(reason = "user_pull_refresh")  // Immediate
```

### 3. SyncEngine

Main orchestrator managing the sync lifecycle.

**State Machine:**
```kotlin
sealed class SyncState {
    object Idle : SyncState()
    data class Pushing(val batchNumber: Int, val totalBatches: Int) : SyncState()
    data class Pulling(val cursor: String?) : SyncState()
    data class Error(val message: String, val cause: Throwable?) : SyncState()
    data class Completed(val pushedCount: Int, val pulledCount: Int) : SyncState()
}
```

**Public API:**
```kotlin
class SyncEngine(
    private val config: SyncConfiguration,
    private val syncAPI: SyncAPI,
    private val operationQueue: OperationQueue,
    private val cursorStore: SyncCursorStore,
    private val changeApplier: SyncChangeApplier,
    private val retryPolicy: SyncRetryPolicy
) {
    // Observable state
    val state: StateFlow<SyncState>
    val lastSyncTimestamp: StateFlow<Instant?>

    // Suspend variants
    suspend fun sync(): SyncResult
    suspend fun enqueue(operation: SyncOperation)
    suspend fun triggerFullResync()

    // Flow variants
    fun syncFlow(): Flow<SyncState>
    fun enqueueFlow(operation: SyncOperation): Flow<Unit>
    fun fullResyncFlow(): Flow<SyncState>
}
```

**Configuration:**
```kotlin
data class SyncConfiguration(
    val databasePath: String,
    val pushBatchSize: Int = 50,
    val pullPageSize: Int = 100,
    val maxRetryAttempts: Int = 5,
    val enableAutoSync: Boolean = true,
    val syncIntervalMinutes: Long = 15
)
```

### 3. OperationQueue

Manages pending operations with Room persistence.

```kotlin
interface OperationQueue {
    suspend fun enqueue(operation: SyncOperation)
    suspend fun getPending(limit: Int): List<SyncOperationEntity>
    suspend fun markInProgress(operationIds: List<String>)
    suspend fun markSynced(operationIds: List<String>)
    suspend fun markFailed(operationId: String, error: String)
    suspend fun scheduleRetry(operationId: String, nextRetryAt: Instant)
    suspend fun clearAll()

    // Flow variants
    fun observePendingCount(): Flow<Int>
    fun observeFailedOperations(): Flow<List<SyncOperationEntity>>
}
```

**Room Entity:**
```kotlin
@Entity(tableName = "sync_operations")
data class SyncOperationEntity(
    @PrimaryKey
    val operationId: String,        // UUID for idempotency
    val entityType: String,
    val entityId: String,
    val intent: String,             // "create", "update", "delete"
    val payload: String,            // JSON payload
    val status: String,             // "pending", "in_progress", "synced", "failed"
    val retryCount: Int,
    val nextRetryAt: Long?,         // Unix timestamp
    val createdAt: Long,
    val lastError: String?
)
```

### 4. SyncChangeApplier

Handler pattern for applying server changes to local data.

```kotlin
interface SyncChangeApplier {
    suspend fun applyChanges(changes: List<SyncChange>): SyncBatchResult
}

interface EntityChangeHandler<T> {
    fun decode(payload: String): T
    suspend fun apply(entity: T, intent: SyncIntent, entityId: String)
    suspend fun applyDelete(entityId: String, payload: String)
}

class DefaultSyncChangeApplier : SyncChangeApplier {
    private val handlers = mutableMapOf<String, EntityChangeHandler<*>>()

    fun <T> register(entityType: String, handler: EntityChangeHandler<T>)

    override suspend fun applyChanges(changes: List<SyncChange>): SyncBatchResult {
        // Route each change to appropriate handler
    }
}
```

**Example Usage (App Code - NOT in SDK):**
```kotlin
// Example: Contact entity in your app
class ContactChangeHandler(
    private val contactStore: ContactStore  // Base store, NOT syncable!
) : EntityChangeHandler<Contact> {

    override fun decode(payload: String): Contact {
        return Json.decodeFromString(payload)
    }

    override suspend fun apply(entity: Contact, intent: SyncIntent, entityId: String) {
        when (intent) {
            SyncIntent.CREATE, SyncIntent.UPDATE -> {
                val existing = contactStore.getContact(entityId)
                if (existing != null) {
                    // Version-based conflict resolution
                    val incomingVersion = entity.syncVersion ?: 0
                    val existingVersion = existing.syncVersion ?: 0

                    if (incomingVersion > existingVersion) {
                        contactStore.updateContact(entity)  // Apply server change
                    }
                    // else: skip, local version is current
                } else {
                    contactStore.addContact(entity)  // New entity
                }
            }
            SyncIntent.DELETE -> contactStore.deleteContact(entityId)
        }
    }

    override suspend fun applyDelete(entityId: String, payload: String) {
        contactStore.deleteContact(entityId)
    }
}

// Register handlers in app initialization
val changeApplier = DefaultSyncChangeApplier()
changeApplier.register("crm.contact", ContactChangeHandler(baseContactStore))
changeApplier.register("crm.tag", TagChangeHandler(baseTagStore))
// ... register other entity types your app uses
```

### 5. SyncRetryPolicy

Exponential backoff for failed operations.

```kotlin
interface SyncRetryPolicy {
    fun calculateNextRetry(retryCount: Int): Instant
    fun shouldRetry(retryCount: Int, maxRetries: Int): Boolean
}

class ExponentialBackoffRetryPolicy : SyncRetryPolicy {
    override fun calculateNextRetry(retryCount: Int): Instant {
        // 2^retryCount seconds, max 5 minutes
        val delaySec = minOf(2.0.pow(retryCount).toInt(), 300)
        return Clock.System.now() + delaySec.seconds
    }

    override fun shouldRetry(retryCount: Int, maxRetries: Int): Boolean {
        return retryCount < maxRetries
    }
}
```

### 6. SyncCursorStore

Persists last sync cursor for incremental pulls.

```kotlin
interface SyncCursorStore {
    suspend fun getCursor(): String?
    suspend fun setCursor(cursor: String)
    suspend fun clearCursor()
}

class SharedPrefsCursorStore(context: Context) : SyncCursorStore {
    private val prefs = context.getSharedPreferences("pikaia_sync", Context.MODE_PRIVATE)

    override suspend fun getCursor(): String? = withContext(Dispatchers.IO) {
        prefs.getString("last_cursor", null)
    }

    override suspend fun setCursor(cursor: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString("last_cursor", cursor).apply()
    }

    override suspend fun clearCursor() = withContext(Dispatchers.IO) {
        prefs.edit().remove("last_cursor").apply()
    }
}
```

### 7. Background Sync with WorkManager

Periodic background sync using WorkManager.

```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val syncEngine = /* Get from DI */

        return try {
            val result = syncEngine.sync()

            when (result) {
                is SyncResult.Success -> Result.success()
                is SyncResult.PartialFailure -> Result.retry()
                is SyncResult.Failure -> {
                    if (result.isRetryable) Result.retry()
                    else Result.failure()
                }
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class SyncScheduler(private val workManager: WorkManager) {
    fun schedulePeriodicSync(intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "pikaia_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork("pikaia_sync")
    }
}
```

## Data Models

### SyncOperation (DTO for API)

```kotlin
@Serializable
data class SyncOperation(
    @SerialName("idempotency_key")  // iOS uses this, not "operation_id"
    val idempotencyKey: String,
    @SerialName("entity_type")
    val entityType: String,
    @SerialName("entity_id")
    val entityId: String,
    val intent: String,             // "create", "update", "delete" as string
    val data: String,               // JSON payload as string
    @SerialName("client_timestamp")
    @Serializable(with = InstantSerializer::class)
    val clientTimestamp: Instant,
    @SerialName("base_version")
    val baseVersion: Int? = null,   // For optimistic concurrency
    @SerialName("retry_count")
    val retryCount: Int = 0
)

@Serializable
enum class SyncIntent {
    @SerialName("create") CREATE,
    @SerialName("update") UPDATE,
    @SerialName("delete") DELETE;

    fun toApiString(): String = when (this) {
        CREATE -> "create"
        UPDATE -> "update"
        DELETE -> "delete"
    }
}
```

### SyncPushRequest

```kotlin
@Serializable
data class SyncPushRequest(
    val operations: List<SyncOperation>
)
```

### SyncPushResponse

```kotlin
@Serializable
data class SyncPushResponse(
    val results: List<PushResult>
)

@Serializable
data class PushResult(
    @SerialName("idempotency_key")  // Not "operation_id"!
    val idempotencyKey: String,
    val status: String,             // "success", "conflict", "error"
    val message: String? = null
)
```

### SyncPullResponse

**Note**: Pull uses query parameters, no request DTO needed.

```kotlin
@Serializable
data class SyncPullResponse(
    val changes: List<SyncChange>,
    val cursor: String?,           // Not "next_cursor"!
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("force_resync")    // Server can trigger full resync
    val forceResync: Boolean = false
)

@Serializable
data class SyncChange(
    @SerialName("entity_type")
    val entityType: String,
    @SerialName("entity_id")
    val entityId: String,
    val operation: String,          // "create", "update", "delete"
    val data: String?,              // JSON payload, null for deletes
    val version: Int,               // Sync version for conflict resolution
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant
)
```

## Two-Store Pattern (Critical!)

**Problem**: If change handlers use syncable stores, you get infinite loops:
```
Server change → Handler applies → Syncable store enqueues → Push to server → Server change → ...
```

**Solution**: Use TWO separate store instances:

```kotlin
// 1. BASE STORE - Used by change handlers (NO sync wrapper)
class ContactStoreImpl(private val dao: ContactDao) : ContactStore {
    override suspend fun addContact(contact: Contact) {
        dao.insert(contact)  // Direct persistence only
    }
}

// 2. SYNCABLE STORE - Used by app features (WITH sync wrapper)
class SyncableContactStore(
    private val baseStore: ContactStore,
    private val syncEngine: SyncEngine,
    private val syncScheduler: SyncScheduler
) : ContactStore {
    override suspend fun addContact(contact: Contact) {
        // 1. Persist via base store
        baseStore.addContact(contact)

        // 2. Enqueue to sync engine
        syncEngine.enqueue(
            entityType = "crm.contact",
            entityId = contact.id,
            intent = SyncIntent.CREATE,
            payload = Json.encodeToString(contact)
        )

        // 3. Trigger sync
        syncScheduler.requestSync(reason = "contact_create")
    }
}

// 3. DEPENDENCY INJECTION SETUP
object DI {
    // Base store (singleton, used by change handlers)
    private val baseContactStore: ContactStore by lazy {
        ContactStoreImpl(database.contactDao())
    }

    // Syncable store (singleton, used by app features)
    val contactStore: ContactStore by lazy {
        SyncableContactStore(baseContactStore, syncEngine, syncScheduler)
    }

    // Change handler uses BASE store
    val contactChangeHandler: EntityChangeHandler<Contact> by lazy {
        ContactChangeHandler(baseContactStore)  // ← NOT syncable store!
    }
}

// 4. CHANGE HANDLER - Uses BASE store
class ContactChangeHandler(
    private val store: ContactStore  // This is BASE store!
) : EntityChangeHandler<Contact> {
    override suspend fun apply(entity: Contact, intent: SyncIntent, entityId: String) {
        when (intent) {
            SyncIntent.CREATE, SyncIntent.UPDATE -> {
                val existing = store.getContact(entityId)
                if (existing != null) {
                    // Version-based conflict resolution
                    if ((entity.syncVersion ?: 0) > (existing.syncVersion ?: 0)) {
                        store.updateContact(entity)  // NO enqueue here!
                    }
                } else {
                    store.addContact(entity)  // NO enqueue here!
                }
            }
            SyncIntent.DELETE -> store.deleteContact(entityId)
        }
    }
}
```

**Key Points**:
1. **App features** → Use syncable stores (wrapper)
2. **Change handlers** → Use base stores (no wrapper)
3. **Never** let change handlers use syncable stores!

## Sync Algorithm

### Full Sync Flow

```kotlin
suspend fun sync(): SyncResult {
    try {
        _state.value = SyncState.Idle

        // Phase 1: Push pending operations
        val pushResult = pushPendingOperations()
        if (pushResult is PushPhaseResult.Failure) {
            return SyncResult.Failure("Push failed: ${pushResult.error}")
        }

        // Phase 2: Pull changes from server
        val pullResult = pullServerChanges()
        if (pullResult is PullPhaseResult.Failure) {
            return SyncResult.Failure("Pull failed: ${pullResult.error}")
        }

        _lastSyncTimestamp.value = Clock.System.now()
        _state.value = SyncState.Completed(
            pushedCount = (pushResult as PushPhaseResult.Success).count,
            pulledCount = (pullResult as PullPhaseResult.Success).count
        )

        return SyncResult.Success

    } catch (e: Exception) {
        _state.value = SyncState.Error(e.message ?: "Unknown error", e)
        return SyncResult.Failure(e.message ?: "Sync failed", e)
    }
}

private suspend fun pushPendingOperations(): PushPhaseResult {
    var totalPushed = 0
    var batchNumber = 0

    while (true) {
        val pending = operationQueue.getPending(config.pushBatchSize)
        if (pending.isEmpty()) break

        batchNumber++
        _state.value = SyncState.Pushing(batchNumber, -1) // Unknown total

        // Mark as in-progress
        operationQueue.markInProgress(pending.map { it.operationId })

        // Convert to DTO
        val operations = pending.map { it.toSyncOperation() }
        val request = SyncPushRequest(operations)

        // Push to server
        val response = syncAPI.push(request)

        // Process results
        response.results.forEach { result ->
            when (result.status) {
                PushStatus.SUCCESS -> {
                    operationQueue.markSynced(listOf(result.operationId))
                    totalPushed++
                }
                PushStatus.CONFLICT -> {
                    // Server conflict - mark as synced (server wins)
                    operationQueue.markSynced(listOf(result.operationId))
                }
                PushStatus.ERROR -> {
                    val entity = pending.find { it.operationId == result.operationId }!!
                    if (retryPolicy.shouldRetry(entity.retryCount, config.maxRetryAttempts)) {
                        val nextRetry = retryPolicy.calculateNextRetry(entity.retryCount + 1)
                        operationQueue.scheduleRetry(result.operationId, nextRetry)
                    } else {
                        operationQueue.markFailed(result.operationId, result.errorMessage ?: "Max retries exceeded")
                    }
                }
            }
        }
    }

    return PushPhaseResult.Success(totalPushed)
}

private suspend fun pullServerChanges(): PullPhaseResult {
    var cursor = cursorStore.getCursor()
    var totalPulled = 0

    while (true) {
        _state.value = SyncState.Pulling(cursor)

        // Call API with query parameters
        val response = syncAPI.pull(
            since = cursor,
            entityTypes = null,  // Pull all types
            limit = config.pullPageSize
        )

        // Check if server requests full resync
        if (response.forceResync) {
            logger.warn("Server requested full resync")
            // Clear queue and cursor, will re-pull everything
            operationQueue.clearAll()
            cursorStore.clearCursor()
            cursor = null
            continue  // Re-pull from beginning
        }

        if (response.changes.isNotEmpty()) {
            // Apply changes via handler
            val batchResult = changeApplier.applyChanges(response.changes)
            totalPulled += batchResult.successCount
        }

        // Update cursor
        if (response.cursor != null) {
            cursorStore.setCursor(response.cursor)
            cursor = response.cursor
        }

        // Check if more pages
        if (!response.hasMore) break
    }

    return PullPhaseResult.Success(totalPulled)
}
```

## Testing Strategy

### Unit Tests

1. **SyncRetryPolicy**
   - Test exponential backoff calculation
   - Test max retry limits

2. **OperationQueue**
   - Test enqueue/dequeue operations
   - Test status transitions
   - Test retry scheduling

3. **DefaultSyncChangeApplier**
   - Test handler routing
   - Test error handling
   - Test batch processing

### Integration Tests

1. **SyncEngine**
   - Test full sync flow (push → pull)
   - Test error recovery
   - Test state transitions
   - Test cursor management

2. **Room Database**
   - Test operation persistence
   - Test concurrent access
   - Test migrations

### End-to-End Tests

1. **Background Sync**
   - Test WorkManager scheduling
   - Test network constraints
   - Test retry backoff

## Dependencies

Add to `gradle/libs.versions.toml`:

```toml
[versions]
room = "2.6.1"
work = "2.9.0"

[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
```

## Migration from Existing Code

If the app already has sync logic:

1. **Create change handlers** for each entity type
2. **Register handlers** with DefaultSyncChangeApplier
3. **Replace manual sync calls** with `syncEngine.enqueue()`
4. **Observe sync state** for UI updates

## Answers from iOS Analysis

All questions answered based on production iOS implementation:

1. **Conflict Resolution Strategy**: ✅ **Version-based comparison**
   - Each entity has `syncVersion: Int?` field (server-managed)
   - Apply change only if `incoming.syncVersion > existing.syncVersion`
   - NOT "server always wins" - it's "newest version wins"

2. **Full Resync Trigger**: ✅ **Two scenarios**
   - Server signals via `forceResync: true` in pull response
   - Manual trigger via `syncEngine.triggerFullResync()` (clears queue + cursor)

3. **Entity Filtering**: ✅ **Supported but optional**
   - API supports `entity_types` query parameter
   - iOS app doesn't use it (always pulls all types)
   - Implement in SDK, but don't require it

4. **Cursor Format**: ✅ **Opaque string**
   - Server-defined format
   - Client never parses it
   - Store and send back verbatim

5. **Push Batch Failures**: ✅ **Per-operation handling**
   - Server returns status for each operation
   - Success: mark synced
   - Conflict: mark synced (server wins on conflicts)
   - Error: retry with backoff (up to max attempts)

## Success Criteria

- [ ] SyncEngine successfully pushes and pulls operations
- [ ] Operations persist across app restarts
- [ ] Retry logic works with exponential backoff
- [ ] WorkManager schedules periodic sync
- [ ] Change handlers correctly apply server changes
- [ ] All unit tests pass
- [ ] Integration tests cover main flows
- [ ] Documentation complete (README + examples)
- [ ] Module builds successfully

## Estimated Effort

- **Core Implementation**: 3-4 days
  - SyncAPI: 0.5 day
  - Room database + DAOs: 0.5 day
  - OperationQueue: 0.5 day
  - SyncEngine: 1 day
  - ChangeApplier + handlers: 0.5 day
  - RetryPolicy: 0.25 day
  - WorkManager: 0.25 day
  - Testing: 0.5 day

- **Documentation**: 0.5 day
- **Example app integration**: 0.5 day

**Total**: ~4-5 days

## Next Steps

After approval:
1. Set up Room database schema
2. Implement SyncAPI endpoints
3. Implement OperationQueue with Room
4. Implement SyncEngine core logic
5. Implement change applier pattern
6. Implement WorkManager integration
7. Write tests
8. Write documentation and examples

---

**Ready for review and approval.**
