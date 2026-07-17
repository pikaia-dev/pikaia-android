# iOS Pikaia SDK Usage Analysis

## Executive Summary

This document analyzes how the Pikaia SDK is actually used in a production iOS platform implementation, providing insights for the Android implementation. The analysis covers initialization patterns, authentication flows, sync patterns, and answers critical design questions.

## Table of Contents

1. [SDK Initialization](#sdk-initialization)
2. [Authentication Patterns](#authentication-patterns)
3. [Sync Engine Usage](#sync-engine-usage)
4. [Data Flow Patterns](#data-flow-patterns)
5. [Answers to Open Questions](#answers-to-open-questions)
6. [API Usage Patterns](#api-usage-patterns)
7. [Key Takeaways for Android](#key-takeaways-for-android)

---

## SDK Initialization

### Location
`PlatformApp/Dependencies/Container/AppContainer.swift:86-125`

### Pattern: Dependency Injection with Factory

```swift
// 1. Create APIClient with interceptors
apiClient.register { @MainActor in
    PikaiaSDK.APIClient(
        config: .init(baseURL: AppEnvironment.current.pikaiaApiBaseURL),
        requestInterceptors: [
            AuthTokenInterceptor(tokenStore: self.sdkTokenStore(), mode: .deviceOnly),
            LogRequestInterceptor(),
        ],
        responseInterceptors: [
            LogResponseInterceptor(),
        ],
        tokenStore: self.sdkTokenStore(),
        authProvider: nil  // ⚠️ NOTE: authProvider is nil - no automatic token refresh
    )
}

// 2. Wrap SDK APIs in adapters
authAPI.register {
    let sdkAuthAPI = PikaiaSDK.AuthAPI(client: self.apiClient())
    return AuthAPIAdapter(authAPI: sdkAuthAPI)
}

deviceAPI.register {
    let sdkDeviceAPI = PikaiaSDK.DeviceAPI(client: self.apiClient())
    return DeviceAPIAdapter(deviceAPI: sdkDeviceAPI)
}

mediaAPI.register {
    let sdkMediaAPI = PikaiaSDK.MediaAPI(client: self.apiClient())
    return MediaAPIAdapter(mediaAPI: sdkMediaAPI)
}
```

### Key Observations

1. **AuthTokenInterceptor Mode**: Uses `deviceOnly` mode
   - Only injects `Device-Token` header
   - No Bearer token injection
   - App uses device-based authentication, not session tokens

2. **No Automatic Token Refresh**: `authProvider: nil`
   - No automatic 401 handling
   - Tokens don't expire or are long-lived
   - Simplifies auth model

3. **Adapter Pattern**: SDK APIs wrapped in adapters
   - Converts between SDK types and app's Interface types
   - Isolates SDK dependency from feature modules
   - Makes SDK swappable (theoretically)

4. **TokenStore**: Uses KeychainTokenStore
   - Secure storage via iOS Keychain
   - Implements `PikaiaSDK.TokenStore` protocol

---

## Authentication Patterns

### Device Provisioning Flow

**Location**: `PlatformApp/Dependencies/Implementation/API/AuthAPIAdapter.swift:65-89`

```swift
// Called once on first app launch
func provisionDevice(
    request: Interfaces.DeviceProvisionRequest,
    mobileAPIKey: String
) async throws -> Interfaces.DeviceProvisionResponse {
    let sdkRequest = PikaiaSDK.DeviceProvisionRequest(
        deviceUuid: request.deviceUuid,  // Persistent device UUID
        platform: request.platform,       // "ios"
        email: request.email,
        name: request.name,
        phoneNumber: request.phoneNumber,
        deviceName: request.deviceName,   // e.g., "iPhone 15 Pro"
        osVersion: request.osVersion,     // e.g., "18.0"
        appVersion: request.appVersion    // e.g., "1.0.0"
    )
    let response = try await authAPI.provisionDevice(
        request: sdkRequest,
        mobileAPIKey: mobileAPIKey
    )
    // Response includes:
    // - deviceToken: Long-lived token for API authentication
    // - userId, memberId, organizationId
    // - isNewUser: Whether this is first-time provision
    return response
}
```

### Token Usage

**Storage**: Device token stored in Keychain (KeychainTokenStore.swift)

**Injection**: AuthTokenInterceptor adds `Device-Token` header to all authenticated requests

**Lifecycle**:
- Device token is persistent across app launches
- No observed token refresh flow in the iOS app
- Token likely has very long expiration (months/years)

### No Magic Link Flow Used

The iOS app does **NOT** use magic link authentication flow in production. The following SDK methods are unused:
- `sendMagicLink()`
- `authenticateMagicLink()`
- `createOrganization()`
- `exchangeSession()`

The app uses **device provisioning only** for authentication.

---

## Sync Engine Usage

### Initialization

**Location**: `PlatformApp/Dependencies/Container/AppContainer.swift:127-192`

```swift
syncEngine.register { @MainActor in
    // 1. Configure storage location
    let storageURL = URL.applicationSupportDirectory.appending(path: "PikaiaSync.store")
    let config = SyncConfiguration(storageURL: storageURL)

    // 2. Create API client wrapper
    let syncAPIClient = PikaiaSDK.DefaultSyncAPIClient(apiClient: self.apiClient())

    // 3. Initialize engine
    let engine = SyncEngine(
        configuration: config,
        apiClient: syncAPIClient
    )

    // 4. Configure logging
    let sdkLogDestination = SDKLogDestination(logger: self.logger())
    PikaiaLogger.addDestination(sdkLogDestination)
    PikaiaLogger.setMinimumLevel(.info)

    // 5. Register entity change handlers
    let applier = DefaultSyncChangeApplier()
    applier.register(
        ContactChangeHandler(store: contactBaseStore, tagStore: tagBaseStore),
        for: "crm.contact"
    )
    applier.register(
        TagChangeHandler(store: tagBaseStore),
        for: "crm.tag"
    )
    applier.register(
        ContactTagChangeHandler(contactStore: contactBaseStore, tagStore: tagBaseStore),
        for: "crm.contact_tag"
    )
    applier.register(
        ContactNoteChangeHandler(store: noteStore, contactStore: contactBaseStore),
        for: "crm.note"
    )
    engine.setChangeApplier(applier)

    return engine
}
```

### Entity Types Registered

The app syncs 4 entity types:

1. **`crm.contact`** - Customer/contact records
2. **`crm.tag`** - Tags for categorization
3. **`crm.contact_tag`** - Many-to-many relationship (contact ↔ tag)
4. **`crm.note`** - Notes attached to contacts

### Syncable Store Pattern

**Location**: `PlatformApp/Dependencies/Sync/SyncableStore/SyncableContactStore.swift`

**Pattern**: Decorator pattern wrapping base stores

```swift
@MainActor
final class SyncableContactStore: ContactStore {
    private let store: ContactStore        // Base store (direct SwiftData access)
    private let syncEngine: SyncEngine
    private let syncScheduler: SyncScheduler
    private let logger: Logger

    // Read operations: delegate directly to base store
    func allContacts(...) throws -> [Contact] {
        try store.allContacts(...)
    }

    // Write operations: persist locally + enqueue to sync
    func addContact(_ contact: Contact) {
        // 1. Persist locally first (immediate)
        store.addContact(contact)

        // 2. Enqueue to sync engine (non-blocking Task)
        Task { @MainActor in
            do {
                let payload = try contact.encodeToSyncPayload()
                try syncEngine.enqueue(
                    entityType: "crm.contact",
                    entityId: contact.id,
                    intent: .create,
                    payload: payload
                )
                // 3. Request debounced sync
                syncScheduler.requestSync(reason: "contact_create")
            } catch {
                logger.error("Failed to enqueue", error: error)
            }
        }
    }

    func updateContact(_ contact: Contact) {
        let previous = store.contact(id: contact.id)
        store.updateContact(contact)

        Task { @MainActor in
            // Compute diff payload for efficient sync
            let payload: Data
            if let previous, let diffPayload = CodableContact.diff(current: contact, previous: previous) {
                payload = diffPayload  // Only send changed fields
            } else {
                payload = try contact.encodeToSyncPayload()  // Full payload
            }

            try syncEngine.enqueue(
                entityType: "crm.contact",
                entityId: contact.id,
                intent: .update,
                payload: payload
            )
            syncScheduler.requestSync(reason: "contact_update")
        }
    }

    func deleteContact(withID id: String) {
        store.deleteContact(withID: id)

        Task { @MainActor in
            try syncEngine.enqueue(
                entityType: "crm.contact",
                entityId: id,
                intent: .delete,
                payload: Data()  // Empty payload for deletes
            )
            syncScheduler.requestSync(reason: "contact_delete")
        }
    }
}
```

### Key Patterns

1. **Local-First**: Always persist locally first
2. **Non-Blocking Sync**: Enqueue in Task, don't await
3. **Diff Payloads**: For updates, only send changed fields
4. **Empty Deletes**: Delete operations have empty payload
5. **Debounced Trigger**: `syncScheduler.requestSync()` debounces multiple rapid changes

### Sync Scheduler

**Location**: `PlatformApp/Dependencies/Sync/SyncScheduler.swift`

**Purpose**: Debounces and coalesces sync requests

```swift
@MainActor
final class SyncScheduler {
    private let debounceNanoseconds: UInt64  // Default: 5 seconds

    // Called by syncable stores after enqueue
    func requestSync(reason: String) {
        hasPendingRequest = true
        scheduledTask?.cancel()  // Cancel previous debounce

        scheduledTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: debounceNanoseconds)
            guard !Task.isCancelled else { return }
            await self.runSyncIfNeeded(trigger: "debounced:\(reason)")
        }
    }

    // Immediate sync (no debounce)
    func syncNow(reason: String) {
        hasPendingRequest = true
        scheduledTask?.cancel()
        Task { @MainActor in
            await self.runSyncIfNeeded(trigger: "immediate:\(reason)")
        }
    }

    private func runSyncIfNeeded(trigger: String) async {
        // 1. Check if device is provisioned
        let deviceToken = await tokenStore.deviceToken
        guard deviceToken != nil else {
            logger.debug("Sync skipped: device not provisioned")
            return
        }

        // 2. Prevent concurrent syncs
        if syncTask != nil {
            logger.debug("Sync already in progress; will run again if needed")
            return
        }

        // 3. Run sync
        try await syncEngine.sync()

        // 4. Check for more pending operations
        if hasPendingRequest || syncEngine.hasPendingOperations {
            await runSyncIfNeeded(trigger: "post_run:\(trigger)")
        }
    }
}
```

### Entity Change Handlers

**Location**: `PlatformApp/Dependencies/Sync/EntityChangeHandlers/ContactChangeHandler.swift`

**Purpose**: Apply server changes to local database

```swift
struct ContactChangeHandler: EntityChangeHandler {
    private let store: ContactStore        // Base store (not syncable)
    private let tagStore: TagStore

    func decode(_ data: Data) throws -> Contact {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let codable = try decoder.decode(CodableContact.self, from: data)
        return codable.toDomain(contactStore: store)
    }

    @MainActor
    func apply(_ entity: Contact, intent: SyncIntent, entityId: String) async throws {
        // Normalize IDs to uppercase
        let normalizedId = entityId.uppercased()

        switch intent {
        case .create, .update:
            if let existing = store.contact(id: normalizedId) {
                // ⚠️ CONFLICT RESOLUTION: Compare sync versions
                let incomingSyncVersion = entity.syncVersion ?? 0
                let existingSyncVersion = existing.syncVersion ?? 0

                if incomingSyncVersion > existingSyncVersion {
                    store.updateContact(entity)  // Server version is newer
                }
                // else: skip update, local version is same or newer
            } else {
                // Entity doesn't exist locally, create it
                store.addContact(entity)
            }
        case .delete:
            store.deleteContact(withID: normalizedId)
        }
    }

    @MainActor
    func applyDelete(entityId: String, payload: Data) async throws {
        let normalizedId = entityId.uppercased()
        store.deleteContact(withID: normalizedId)
    }
}
```

### Sync Backfill Service

**Location**: `PlatformApp/Dependencies/Sync/SyncBackfillService.swift`

**Purpose**: One-time migration of existing local data to sync engine

```swift
@MainActor
final class SyncBackfillService {
    /// Backfill all unsynced entities (syncVersion == nil)
    func backfillUnsyncedEntities() async throws -> BackfillResult {
        var result = BackfillResult()

        // 1. Backfill tags first (referenced by contacts)
        result.tagsEnqueued = try await backfillTags()

        // 2. Backfill contacts
        result.contactsEnqueued = try await backfillContacts()

        // 3. Backfill contact-tag associations
        result.contactTagsEnqueued = try await backfillContactTags()

        // 4. Backfill notes
        result.notesEnqueued = try await backfillNotes()

        // 5. Trigger sync if any entities were enqueued
        if result.totalEnqueued > 0 {
            syncScheduler.requestSync(reason: "backfill_unsynced")
        }

        return result
    }

    private func backfillContacts() async throws -> Int {
        let unsyncedContacts = try contactStore.unsyncedContacts()

        for contact in unsyncedContacts {
            let payload = try contact.encodeToSyncPayload()
            try syncEngine.enqueue(
                entityType: "crm.contact",
                entityId: contact.id,
                intent: .create,  // All backfilled as creates
                payload: payload
            )
        }

        return unsyncedContacts.count
    }
}
```

**When Used**: One-time during app migration to enable sync for pre-existing local data

---

## Data Flow Patterns

### Write Flow (User Creates Contact)

```
┌────────────────────────────────────────────────┐
│ 1. UI Layer (ViewModel)                       │
│    contactStore.addContact(newContact)         │
└─────────────────┬──────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────┐
│ 2. SyncableContactStore                        │
│    a. store.addContact(newContact)  ← Persist  │
│    b. Task { syncEngine.enqueue(...) }         │
│    c. syncScheduler.requestSync()              │
└─────────────────┬──────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────┐
│ 3. SyncEngine                                  │
│    Saves operation to SwiftData queue          │
│    Status: pending                             │
└─────────────────┬──────────────────────────────┘
                  │
                  ▼ (debounced, 5 seconds)
┌────────────────────────────────────────────────┐
│ 4. SyncScheduler                               │
│    Triggers sync() after debounce              │
└─────────────────┬──────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────┐
│ 5. SyncEngine.sync()                           │
│    Phase 1: Push pending operations            │
│    Phase 2: Pull server changes                │
└─────────────────┬──────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────┐
│ 6. POST /v1/sync/push                          │
│    Server processes, returns success           │
│    Operation marked as synced                  │
└────────────────────────────────────────────────┘
```

### Read Flow (Sync Pulls Server Changes)

```
┌────────────────────────────────────────────────┐
│ 1. SyncEngine.sync()                           │
│    GET /v1/sync/pull?since=<cursor>            │
└─────────────────┬──────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────┐
│ 2. Server Response                             │
│    changes: [...]                              │
│    cursor: "new_cursor_value"                  │
│    hasMore: true/false                         │
│    forceResync: false                          │
└─────────────────┬──────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────┐
│ 3. DefaultSyncChangeApplier                    │
│    Routes each change to handler by entityType │
└─────────────────┬──────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────┐
│ 4. ContactChangeHandler                        │
│    a. decode(data) → Contact entity            │
│    b. Check syncVersion                        │
│    c. store.updateContact(entity) if newer     │
└─────────────────┬──────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────┐
│ 5. Base ContactStore                           │
│    SwiftData persistence (no re-enqueue!)      │
└────────────────────────────────────────────────┘
```

### Critical: Two-Store Pattern

The app uses **two separate store instances** to prevent sync feedback loops:

1. **SyncableStore** - Used by app features
   - Wraps base store
   - Enqueues operations to sync engine

2. **BaseStore** - Used by change handlers
   - Direct SwiftData access
   - Does NOT enqueue to sync engine
   - Prevents infinite loop: server change → apply → enqueue → push → server change...

---

## Answers to Open Questions

### 1. Conflict Resolution Strategy

**Answer**: **Version-based conflict resolution, server wins on equal versions**

**Evidence**: `ContactChangeHandler.swift:36-43`

```swift
if let existing = store.contact(id: normalizedId) {
    let incomingSyncVersion = entity.syncVersion ?? 0
    let existingSyncVersion = existing.syncVersion ?? 0

    if incomingSyncVersion > existingSyncVersion {
        store.updateContact(entity)  // Apply server change
    }
    // else: skip update, local is same or newer version
}
```

**Logic**:
- Each entity has a `syncVersion` field (integer)
- Server increments syncVersion on every change
- On pull:
  - If server version > local version: Apply server change
  - If server version ≤ local version: Skip (local is current)
- On push:
  - Server resolves conflicts and increments version
  - Next pull will have higher version, forcing local update

**Key Insight**: This is not "server wins always", it's "newest version wins based on syncVersion counter"

### 2. Full Resync Trigger

**Answer**: **Two scenarios trigger full resync**

**Scenario A: Server Signals `forceResync`**

`SyncAPIPullResponse` includes `forceResync: Bool` field

```swift
public struct SyncAPIPullResponse: Decodable {
    let changes: [SyncAPIPullChange]
    let cursor: String?
    let hasMore: Bool
    let forceResync: Bool  // ← Server can force resync
}
```

When `forceResync: true`:
1. Client calls `syncEngine.triggerFullResync()`
2. Clears local operation queue
3. Resets cursor to nil
4. Pulls all data from scratch

**Scenario B: Manual Trigger**

```swift
// Location: SyncEngine.swift
public func triggerFullResync() async throws {
    logger.warn("Full resync triggered", category: "sync_engine")

    // 1. Clear all pending operations
    try await operationQueue.clearAll()

    // 2. Reset cursor
    try await cursorManager.clearCursor()

    // 3. Pull from beginning
    try await sync()
}
```

**When Manual Resync Needed**:
- Data corruption detected
- User requests "reset sync state"
- After schema migration

### 3. Entity Type Filtering

**Answer**: **Supported by API, but NOT used in iOS app**

**API Support**: `SyncAPI.swift:28-41`

```swift
public func pull(
    since: String?,
    entityTypes: [String]?,  // ← Optional filter
    limit: Int?
) async throws -> SyncAPIPullResponse {
    if let entityTypes, !entityTypes.isEmpty {
        queryItems.append(
            URLQueryItem(name: "entity_types", value: entityTypes.joined(separator: ","))
        )
    }
    // ...
}
```

**App Usage**: Always pulls ALL entity types

```swift
// DefaultSyncAPIClient never passes entityTypes
let response = try await syncAPI.pull(
    since: cursor,
    entityTypes: nil,  // ← Always nil
    limit: nil
)
```

**Recommendation for Android**: Implement filtering support in API, but don't expose it in SyncEngine by default. Could be useful for future selective sync features.

### 4. Cursor Format

**Answer**: **Opaque string, server-defined**

**Evidence**:
- Client treats cursor as opaque string
- No parsing or manipulation on client side
- Server returns cursor in response
- Client stores and sends back verbatim

```swift
// Store cursor exactly as received
func setCursor(_ cursor: String) async throws {
    try await cursorManager.setCursor(cursor)
}

// Send cursor exactly as stored
let response = try await syncAPI.pull(since: storedCursor, ...)
```

**Format Observed**: Not exposed to client. Could be:
- Base64-encoded timestamp + offset
- Database cursor/offset
- UUID of last synced record

**Recommendation**: Treat as opaque string in Android implementation

### 5. Push Batch Failures

**Answer**: **Partial failure handling - process results individually**

**Evidence**: Push response includes per-operation status

```swift
public struct SyncAPIPushResponse: Decodable {
    let results: [SyncAPIPushResult]
}

public struct SyncAPIPushResult: Decodable {
    let idempotencyKey: String
    let status: String  // "success", "conflict", "error"
    let message: String?
}
```

**Handling Logic**:

```swift
// Process each result individually
for result in response.results {
    switch result.status {
    case "success":
        operationQueue.markSynced(result.idempotencyKey)

    case "conflict":
        // Server rejected due to conflict
        operationQueue.markSynced(result.idempotencyKey)
        // Note: Still mark as synced! Server is authoritative.

    case "error":
        // Increment retry count
        if retryCount < maxRetries {
            operationQueue.scheduleRetry(result.idempotencyKey)
        } else {
            operationQueue.markFailed(result.idempotencyKey)
        }
    }
}
```

**Key Insight**: Failures are handled per-operation, not per-batch. A batch with 50 operations might have 45 succeed, 3 conflict, and 2 error. Each is handled independently.

---

## API Usage Patterns

### Async/Await Only (No Flow/Combine)

**Observation**: iOS SDK provides **only async/await** functions, no reactive streams

```swift
// SDK provides this:
public func push(request: SyncAPIPushRequest) async throws -> SyncAPIPushResponse

// NOT this:
public func pushPublisher(request: SyncAPIPushRequest) -> AnyPublisher<SyncAPIPushResponse, Error>
```

**App Usage**: All SDK calls use async/await

```swift
// Typical usage
Task {
    do {
        let response = try await authAPI.provisionDevice(request: request, mobileAPIKey: key)
        // Handle response
    } catch {
        // Handle error
    }
}
```

**Implication for Android**: While Android SDK should provide both suspend functions AND Flow variants (as planned), the iOS pattern shows that async/await alone is sufficient for most use cases.

### Adapter Pattern for Type Conversion

**Pattern**: App defines its own types in `Interfaces` module, adapters convert SDK types

```swift
// App's interface
protocol AuthAPI {
    func provisionDevice(...) async throws -> Interfaces.DeviceProvisionResponse
}

// Adapter converts SDK types to Interface types
struct AuthAPIAdapter: Interfaces.AuthAPI {
    private let authAPI: PikaiaSDK.AuthAPI

    func provisionDevice(...) async throws -> Interfaces.DeviceProvisionResponse {
        let sdkRequest = PikaiaSDK.DeviceProvisionRequest(...)
        let sdkResponse = try await authAPI.provisionDevice(...)

        // Convert SDK type → Interface type
        return Interfaces.DeviceProvisionResponse(
            deviceToken: sdkResponse.deviceToken,
            userId: sdkResponse.userId,
            ...
        )
    }
}
```

**Benefits**:
- Features depend on `Interfaces`, not `PikaiaSDK`
- SDK is swappable (theoretically)
- Clear boundary between SDK and app code

**Recommendation for Android**: Consider providing similar adapter pattern guidance in documentation

### Payload Format

**Important Discovery**: iOS SDK uses `[String: Any]` for payloads, NOT `Data` or `String`

```swift
public struct SyncAPIPushOperation {
    public nonisolated(unsafe) let data: [String: Any]  // ← Dictionary, not Data
    // ...
}

public struct SyncAPIPullChange {
    public nonisolated(unsafe) let data: [String: Any]?  // ← Dictionary, not Data
    // ...
}
```

**But**: App code serializes to Data/JSON before enqueueing

```swift
// App converts entity to Data
let payload = try contact.encodeToSyncPayload()  // Returns Data

// SDK stores as Data internally in SwiftData
@Model
class SyncOperationRecord {
    var payload: Data  // Stored as Data
}

// When pushing, SDK converts Data → [String: Any] for API
```

**Android Implication**: Use String (JSON) for payloads, not [String: Any] (no such type in JSON). Keep it simple:

```kotlin
data class SyncOperation(
    val payload: String  // JSON string
)
```

---

## Key Takeaways for Android

### 1. Authentication: Device-Based, Not Session-Based

- Use `Device-Token` header, not `Bearer`
- AuthTokenInterceptor mode: `DEVICE_ONLY`
- No automatic token refresh needed (long-lived tokens)
- AuthProvider can be null

### 2. Sync: Two-Store Pattern is Critical

```kotlin
// Feature modules use this:
class SyncableContactStore(
    private val baseStore: ContactStore,
    private val syncEngine: SyncEngine
) : ContactStore {
    override suspend fun addContact(contact: Contact) {
        baseStore.addContact(contact)  // Persist
        syncEngine.enqueue(...)        // Enqueue
    }
}

// Change handlers use this:
class ContactChangeHandler(
    private val baseStore: ContactStore  // NOT syncable store!
) : EntityChangeHandler<Contact> {
    override suspend fun apply(entity: Contact, ...) {
        baseStore.updateContact(entity)  // Direct, no enqueue
    }
}
```

**Critical**: Change handlers MUST use base stores to avoid infinite loop

### 3. Conflict Resolution: Version-Based

```kotlin
data class Contact(
    val id: String,
    val name: String,
    val syncVersion: Int?  // Server-managed version counter
)

// In change handler:
val existing = store.getContact(entityId)
if (existing != null) {
    val incomingVersion = entity.syncVersion ?: 0
    val existingVersion = existing.syncVersion ?: 0

    if (incomingVersion > existingVersion) {
        store.updateContact(entity)  // Server version is newer
    }
    // else: skip, local is current
}
```

### 4. Debounced Sync Trigger

```kotlin
class SyncScheduler(private val debounceMillis: Long = 5000) {
    private var scheduledJob: Job? = null

    fun requestSync(reason: String) {
        scheduledJob?.cancel()
        scheduledJob = scope.launch {
            delay(debounceMillis)
            syncEngine.sync()
        }
    }

    fun syncNow(reason: String) {
        scheduledJob?.cancel()
        scope.launch {
            syncEngine.sync()
        }
    }
}
```

### 5. Entity Types

Register 4 entity types in Android app (matching iOS):
- `crm.contact`
- `crm.tag`
- `crm.contact_tag`
- `crm.note`

### 6. Payload Handling

- **For enqueue**: Use JSON String payload
- **For updates**: Support diff payloads (only changed fields)
- **For deletes**: Empty string payload

```kotlin
// Create/Update
val payload = Json.encodeToString(contact)

// Update (diff)
val payload = Json.encodeToString(
    CodableContact.diff(current, previous)
)

// Delete
val payload = ""  // Empty
```

### 7. API Design

```kotlin
// Sync API - use query parameters
class SyncAPI(private val client: APIClient) {
    suspend fun pull(
        since: String? = null,
        entityTypes: List<String>? = null,
        limit: Int? = null
    ): SyncPullResponse {
        // GET /v1/sync/pull?since=X&entity_types=crm.contact,crm.tag&limit=100
    }

    suspend fun push(request: SyncPushRequest): SyncPushResponse {
        // POST /v1/sync/push
    }
}
```

### 8. Response Models

```kotlin
@Serializable
data class SyncPullResponse(
    val changes: List<SyncChange>,
    val cursor: String?,
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("force_resync")
    val forceResync: Boolean  // ← Don't forget this field!
)

@Serializable
data class SyncPushResponse(
    val results: List<PushResult>
)

@Serializable
data class PushResult(
    @SerialName("idempotency_key")
    val idempotencyKey: String,  // operation_id in Android
    val status: String,  // "success", "conflict", "error"
    val message: String?
)
```

### 9. Flow Variants (Android-Specific)

While iOS only uses async/await, provide Flow variants for Android:

```kotlin
class SyncAPI(private val client: APIClient) {
    // Suspend (primary)
    suspend fun pull(since: String? = null): SyncPullResponse

    // Flow (reactive)
    fun pullFlow(since: String? = null): Flow<SyncPullResponse> = flow {
        emit(pull(since))
    }
}
```

### 10. Background Sync

iOS doesn't show WorkManager usage (might use BGAppRefreshTask). For Android:

```kotlin
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val syncEngine = // Get from DI
            syncEngine.sync()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

---

## Updated Phase 3 Recommendations

Based on iOS analysis, update Phase 3 plan with:

1. **Change `nextCursor` to `cursor`** in SyncPullResponse
2. **Add `forceResync: Boolean`** to SyncPullResponse
3. **Change `PushResult` to use `idempotencyKey`** (not `operationId`)
4. **Add Two-Store Pattern** to architecture docs
5. **Document sync version conflict resolution** clearly
6. **Use String (JSON) payloads**, not complex types
7. **Implement diff payload support** for updates
8. **Make entity filtering optional** (API supports, but don't require)
9. **Add SyncScheduler** with 5-second debounce
10. **Document backfill pattern** for migrating existing data

---

## Conclusion

The iOS app provides a proven, production-tested pattern for:
- Device-based authentication (simple, no token refresh)
- Offline-first sync with local persistence
- Version-based conflict resolution
- Debounced sync triggering
- Two-store pattern preventing feedback loops

The Android implementation should follow these patterns closely while adding Android-specific enhancements (Flow APIs, WorkManager integration, Room instead of SwiftData).

**Most Important Insight**: The two-store pattern (syncable wrapper + base store) is critical for preventing infinite sync loops. Change handlers MUST use base stores that don't re-enqueue operations.
