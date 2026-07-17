# Pikaia SDK Sync

Bidirectional synchronization engine for Android applications.

## Overview

The Sync SDK provides a robust, production-ready solution for syncing data between Android apps and Pikaia backend services. It implements a push-then-pull strategy with automatic conflict resolution, retry logic, and background sync capabilities.

## Key Features

- **Bidirectional Sync**: Push local changes, pull remote updates
- **Conflict Resolution**: Version-based automatic conflict resolution
- **Debounced Operations**: 5-second debounce to coalesce rapid changes
- **Background Sync**: WorkManager integration for periodic background sync
- **Retry Logic**: Exponential backoff for failed operations
- **Type-Safe**: Generic implementation with no hardcoded entity types
- **Observable**: Flow-based state observation
- **Thread-Safe**: Mutex-protected operations

## Architecture

### Core Components

1. **SyncEngine**: Orchestrates push-then-pull sync operations
2. **OperationQueue**: Manages pending operations with Room persistence
3. **SyncChangeApplier**: Routes server changes to entity-specific handlers
4. **SyncScheduler**: Debounces sync requests (5-second default)
5. **SyncWorker**: WorkManager worker for background sync
6. **SyncAPI**: HTTP endpoints for push/pull operations

### Data Flow

```
App makes change → OperationQueue → SyncEngine (push) → Server
                                                    ↓
App database ← ChangeApplier ← SyncEngine (pull) ← Server
```

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":sdk-sync"))
}
```

## Quick Start

### 1. Define Entity Handler

Implement `EntityChangeHandler` for each entity type:

```kotlin
class ContactChangeHandler(
    private val contactStore: BaseContactStore
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
        val existing = contactStore.getById(entityId)

        // Version-based conflict resolution
        if (existing == null || version > existing.syncVersion) {
            when (intent) {
                SyncIntent.CREATE, SyncIntent.UPDATE -> {
                    contactStore.upsert(entity.copy(syncVersion = version))
                }
                SyncIntent.DELETE -> {
                    contactStore.delete(entityId)
                }
            }
        }
    }

    override suspend fun applyDelete(entityId: String, payload: String?) {
        contactStore.delete(entityId)
    }
}
```

### 2. Initialize Sync Components

```kotlin
// Create sync components
val syncDatabase = SyncDatabase.getInstance(context)
val operationQueue = OperationQueue(syncDatabase.syncOperationDao())
val cursorStore = SyncCursorStore(context)
val syncAPI = SyncAPI(apiClient)

// Create change applier and register handlers
val changeApplier = DefaultSyncChangeApplier()
changeApplier.register("crm.contact", ContactChangeHandler(baseContactStore))
changeApplier.register("crm.tag", TagChangeHandler(baseTagStore))

// Create sync engine
val syncEngine = SyncEngine(
    syncAPI = syncAPI,
    operationQueue = operationQueue,
    cursorStore = cursorStore,
    changeApplier = changeApplier
)

// Create scheduler (optional, for debouncing)
val syncScheduler = SyncScheduler(syncEngine, coroutineScope)
syncScheduler.start()

// Set up WorkManager for background sync (optional)
SyncWorkerProvider.syncEngine = syncEngine
val workScheduler = SyncWorkScheduler(context)
workScheduler.schedulePeriodicSync(intervalMinutes = 15)
```

### 3. Queue Operations

When local data changes, queue sync operations:

```kotlin
// After creating/updating a contact
operationQueue.enqueue(
    entityType = "crm.contact",
    entityId = contact.id,
    intent = SyncIntent.CREATE, // or UPDATE
    payload = Json.encodeToString(contact)
)

// Trigger sync (debounced)
syncScheduler.scheduleSync()
```

### 4. Observe Sync State

```kotlin
syncEngine.state.collect { state ->
    when (state) {
        is SyncState.Idle -> {}
        is SyncState.Syncing -> {
            // Show progress: state.phase (PUSHING or PULLING)
        }
        is SyncState.Success -> {
            // state.result has counts
        }
        is SyncState.Error -> {
            // Handle error: state.error, state.exception
        }
    }
}
```

## Two-Store Pattern

To prevent infinite sync loops, use separate stores for app operations and sync handlers:

- **Syncable Store**: Used by app code, triggers sync operations
- **Base Store**: Used by change handlers, direct database access

```kotlin
// Syncable store (used by app)
class SyncableContactStore(
    private val baseStore: BaseContactStore,
    private val operationQueue: OperationQueue,
    private val syncScheduler: SyncScheduler
) {
    suspend fun create(contact: Contact) {
        baseStore.insert(contact)

        // Queue sync operation
        operationQueue.enqueue(
            entityType = "crm.contact",
            entityId = contact.id,
            intent = SyncIntent.CREATE,
            payload = Json.encodeToString(contact)
        )

        syncScheduler.scheduleSync()
    }
}

// Base store (used by change handler)
class BaseContactStore {
    fun insert(contact: Contact) {
        // Direct database insert, no sync operation
    }
}
```

## Advanced Usage

See [EXAMPLE.md](./EXAMPLE.md) for detailed examples including:

- Conflict resolution strategies
- Error handling patterns
- Background sync configuration
- Testing strategies

## API Reference

### SyncEngine

Main sync orchestrator:

```kotlin
suspend fun sync(
    entityTypes: List<String>? = null,
    limit: Int? = null
): SyncResult

suspend fun forcePush(): Int
suspend fun forcePull(): PullResult
suspend fun forceResync()

val state: StateFlow<SyncState>
```

### OperationQueue

Manages pending operations:

```kotlin
suspend fun enqueue(
    entityType: String,
    entityId: String,
    intent: SyncIntent,
    payload: String? = null
): String

suspend fun getPending(): List<SyncOperationEntity>
fun observePendingCount(): Flow<Int>
fun observeFailedOperations(): Flow<List<SyncOperationEntity>>
```

### SyncScheduler

Debounces sync requests:

```kotlin
fun start()
fun stop()
fun scheduleSync(entityTypes: List<String>? = null, limit: Int? = null)
fun syncNow(entityTypes: List<String>? = null, limit: Int? = null)
fun cancelPendingSync()
```

## License

Copyright (c) 2026 Pikaia. All rights reserved.
