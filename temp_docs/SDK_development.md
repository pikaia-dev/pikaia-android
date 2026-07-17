# Pikaia Android SDK - Development Plan

## Table of Contents
1. [iOS SDK Analysis](#ios-sdk-analysis)
2. [Android SDK Architecture](#android-sdk-architecture)
3. [Module Structure](#module-structure)
4. [Technology Stack](#technology-stack)
5. [Implementation Plan](#implementation-plan)
6. [Translation Considerations](#translation-considerations)
7. [Testing Strategy](#testing-strategy)
8. [Dependency Injection](#dependency-injection)
9. [Code Consistency Guidelines](#code-consistency-guidelines)
10. [Timeline & Estimates](#timeline--estimates)
11. [Open Questions](#open-questions)

---

## iOS SDK Analysis

### Architecture Overview

The iOS Pikaia SDK is a **single-module, zero-dependency package** built with Swift 6.2, targeting iOS 18+ and macOS 15+. It implements an offline-first sync engine with a clean, protocol-oriented architecture.

**Package Information:**
- **Language**: Swift 6.2
- **Platforms**: iOS 18+, macOS 15+
- **Dependencies**: None (zero external dependencies)
- **Architecture**: Single SPM module with internal/public API separation

### Core Components

#### 1. Networking Layer (`Networking/`)

**APIClient** - Core HTTP client using URLSession
- Automatic token refresh on 401 responses
- Request/response interceptor pipelines
- JSON encoding/decoding with ISO8601 dates
- Configurable timeouts and headers
- Error handling with metadata (request IDs, headers)

**Endpoint** - Type-safe endpoint definition
- Generic request/response types: `Endpoint<Request, Response>`
- Custom decoders support
- Query parameters, headers, body support
- Extension methods for common patterns (Decodable responses, EmptyResponse)

**Interceptors** - Pipeline architecture:
- `AuthTokenInterceptor`: Injects Bearer or DeviceToken auth headers
  - Modes: `bearerOnly`, `deviceOnly`, `preferBearer`, `preferDevice`
- `LogRequestInterceptor` / `LogResponseInterceptor`: Debug logging
- Request pipeline: adapt requests before sending
- Response pipeline: handle responses after receiving

**Error Handling** - Comprehensive error types:
```swift
enum APIError: Error {
    case invalidURL
    case transport(URLError)
    case client(statusCode: Int, data: Data?, metadata: APIErrorMetadata?)
    case server(statusCode: Int, data: Data?, metadata: APIErrorMetadata?)
    case decoding(Error)
    case unauthorized
    case refreshFailed(underlying: Error)
    case cancelled
}
```

#### 2. Authentication (`Auth/`)

**TokenStore Protocol** - Storage abstraction
- Access token, refresh token storage
- Device token, device refresh token storage
- `InMemoryTokenStore` implementation provided
- Apps can provide custom implementations (Keychain, etc.)

**RefreshCoordinator** - Token refresh management
- Uses Swift actors for thread safety
- Coalesces concurrent refresh requests
- Prevents multiple simultaneous refresh calls
- `DefaultRefreshCoordinator` implementation using actor pattern

**AuthProvider Protocol** - Abstraction for auth refresh logic
- Allows apps to customize token refresh behavior
- Called by APIClient when 401 encountered

#### 3. API Clients (`API/`)

Four specialized API client classes wrapping REST endpoints:

**AuthAPI** - Authentication endpoints
- Magic link flow: send/authenticate
- Discovery: organization selection/creation
- Device provisioning: shadow users for offline-first
- Mobile provisioning: session token creation
- Session management: logout, getMe
- Profile updates: name, phone verification
- Phone OTP: send/verify

**DeviceAPI** - Device management
- Device linking via QR codes
- List linked devices
- Session refresh
- Device revocation

**MediaAPI** - File management
- Presigned S3 upload URLs
- Upload confirmation
- Image deletion

**SyncAPI** - Data synchronization
- Push operations with batch support
- Pull changes with cursor-based pagination
- Entity type filtering

**Data Transfer Objects (DTOs)**:
- All requests/responses as strongly-typed structs
- Codable conformance for JSON serialization
- Snake_case JSON field mapping via CodingKeys
- ISO8601 date encoding/decoding
- Sendable conformance for Swift 6 concurrency

#### 4. Sync Engine (`SyncEngine/`)

**Public API:**

**SyncEngine** (MainActor, Observable)
- **State Management**:
  - States: `idle`, `pushing(batchSize)`, `pulling(cursor)`, `error(message)`
  - Observable state for UI binding
  - Last sync timestamp tracking

- **Core Methods**:
  - `sync()`: Full sync cycle (push then pull)
  - `enqueue()`: Queue local changes for sync
  - `triggerFullResync()`: Clear local state and re-pull everything

- **Strategy**:
  - Push-then-pull: Always push pending operations before pulling
  - Conflict resolution: Server wins (server version is authoritative)
  - Batch processing: Configurable batch sizes

**SyncConfiguration** - Engine configuration
```swift
struct SyncConfiguration {
    let storageURL: URL              // SQLite database location
    let pushBatchSize: Int = 50      // Operations per push batch
    let maxRetryAttempts: Int = 5    // Max retries before failure
}
```

**SyncChangeApplier Protocol** - Change application interface
- Apps implement this to apply server changes to local data
- `applyChanges(_ changes: [SyncChange]) async -> SyncBatchResult`
- Returns success/failure per change

**DefaultSyncChangeApplier** - Handler routing implementation
- Routes changes to entity-specific handlers
- `register(_ handler: EntityChangeHandler<T>, for entityType: String)`
- Type-erased handler boxing for protocol flexibility

**EntityChangeHandler Protocol** - Entity-specific change handling
```swift
protocol EntityChangeHandler {
    associatedtype Entity: Decodable
    func decode(_ payload: Data) throws -> Entity
    func apply(_ entity: Entity, intent: SyncIntent, entityId: String) async throws
    func applyDelete(entityId: String, payload: Data) async throws
}
```

**Data Models:**

**SyncChange** - Server change representation
```swift
struct SyncChange {
    let entityType: String
    let entityId: String
    let intent: SyncIntent        // .create, .update, .delete
    let payload: Data
    let serverVersion: Int64
}
```

**SyncPushRequest** - Batch push request
```swift
struct SyncPushRequest {
    struct Operation {
        let operationId: String       // UUID for idempotency
        let entityType: String
        let entityId: String
        let intent: SyncIntent
        let payload: Data
        let retryCount: Int
        let clientTimestamp: Date
    }
    let operations: [Operation]
}
```

**PushResult** - Individual operation result
```swift
struct PushResult {
    let operationId: String
    let status: Status              // .success, .conflict, .error
    let errorMessage: String?
}
```

**SyncPullResponse** - Pull response from server
```swift
struct SyncPullResponse {
    let changes: [SyncChange]
    let nextCursor: String?
    let hasMore: Bool
}
```

**Internal Components:**

**OperationQueue** (MainActor) - Pending operations management
- Uses SwiftData for persistence
- Manages operation lifecycle: pending → in_progress → synced/failed
- Automatic retry scheduling with exponential backoff
- Max 5 retry attempts (configurable)
- Query methods:
  - `getPending(limit:)`: Fetch operations ready to sync
  - `enqueue()`: Add new operation
  - `markInProgress()`, `markSynced()`, `markFailed()`
  - `scheduleRetry()`: Schedule retry with backoff
  - `clearAll()`: Full resync preparation

**SyncOperationRecord** - SwiftData persistence model
```swift
@Model
class SyncOperationRecord {
    @Attribute(.unique) var operationId: String
    var entityType: String
    var entityId: String
    var intent: String              // "create", "update", "delete"
    var payload: Data
    var status: String              // "pending", "in_progress", "failed", "completed"
    var retryCount: Int
    var nextRetryAt: Date?          // When to retry (exponential backoff)
    var createdAt: Date
    var lastError: String?
}
```

**SyncRetryPolicy** - Retry scheduling logic
- Exponential backoff: 2^attempt seconds
- Max delay: 300 seconds (5 minutes)
- Calculates next retry timestamp

**SyncCursorManager** - Cursor persistence
- Stores last sync cursor for incremental pulls
- Persisted using SwiftData
- `getCursor()`, `setCursor()`, `clearCursor()`

**SyncModelContainer** - SwiftData schema management
- Schema versioning (V1, V2 with migrations)
- Separate database from app's main database
- Isolated storage for SDK operations

### Key Design Patterns

1. **Protocol-Oriented Design**:
   - Heavy use of protocols for testability and extensibility
   - TokenStore, AuthProvider, RefreshCoordinator, SyncChangeApplier all protocols
   - Allows apps to customize behavior

2. **Sendable Conformance**:
   - Thread-safe data structures using Swift 6 strict concurrency
   - All DTOs marked Sendable
   - Prevents data races at compile time

3. **Actor Isolation**:
   - `@MainActor` for UI-bound components (SyncEngine)
   - Actors for coordination (DefaultRefreshCoordinator)
   - Thread-safe by design

4. **Type Safety**:
   - Generic endpoints: `Endpoint<Request, Response>`
   - Strongly-typed DTOs
   - Compile-time type checking

5. **Offline-First**:
   - Local operation queue with persistence
   - Automatic retry with exponential backoff
   - Idempotent operations (UUID-based)
   - Works without network connectivity

6. **Cursor-Based Sync**:
   - Incremental pull from server
   - Server-side pagination
   - Efficient for large datasets

7. **Handler Pattern**:
   - Apps register entity-specific change handlers
   - Decouples sync engine from domain logic
   - Type-safe with generics

8. **Interceptor Pipeline**:
   - Extensible request/response processing
   - Chain of responsibility pattern
   - Easy to add logging, metrics, custom headers

### API Endpoint Coverage

Based on the OpenAPI specification analysis:

**Authentication (11 endpoints)** ✅ Implemented
- Magic link flow
- Organization discovery and selection
- Device/mobile provisioning
- Session management and refresh
- Profile management
- Phone OTP verification
- Passkey authentication (WebAuthn)

**Device Management (8 endpoints)** ✅ Implemented
- QR code linking
- Device list and revocation
- Session refresh

**Media (6 endpoints)** ✅ Implemented
- Presigned S3 uploads
- Upload confirmation
- Image deletion

**Sync (2 endpoints)** ✅ Implemented
- Push operations
- Pull changes

**Not in iOS SDK (yet to implement in Android):**
- Organization management (6 endpoints)
- Billing (6 endpoints via Stripe)
- Webhooks (8 endpoints)
- Directory (2 endpoints - Google Workspace)

---

## Android SDK Architecture

### Design Principles

The Android SDK should mirror the iOS architecture while following Kotlin/Android best practices:

1. **Module Independence**: Each module can be used standalone
2. **Zero Breaking Changes**: BOM ensures version compatibility
3. **Offline-First**: Same sync strategy as iOS
4. **Type Safety**: Leverage Kotlin's type system
5. **Testability**: Dependency injection with Koin
6. **Thread Safety**: Coroutines + proper synchronization
7. **Idiomatic Kotlin**: Follow Kotlin conventions while maintaining API parity with iOS

---

## Module Structure

### Recommended Structure (Option A)

```
pikaia-android/
├── sdk-core/         → Networking, interceptors, base types, error handling
├── sdk-auth/         → Authentication API, token management, auth interceptors
├── sdk-sync/         → Sync engine, operation queue, change appliers
├── sdk-media/        → Media upload/download functionality (NEW)
├── sdk-device/       → Device linking and management (NEW)
└── sdk-bom/          → Bill of Materials for version management
```

**Rationale:**
- `sdk-core`: Provides shared networking infrastructure (APIClient, Endpoint, interceptors)
- `sdk-auth`: Authentication domain (tokens, auth API, refresh coordination)
- `sdk-sync`: Sync engine domain (offline-first operations, Room database)
- `sdk-media`: Media domain (S3 uploads, image management)
- `sdk-device`: Device management domain (QR linking, device list)
- `sdk-bom`: Version coordination across modules

**Benefits:**
- Clear separation of concerns
- Modules can be used independently
- Easy to test each module in isolation
- Logical grouping by domain
- Straightforward dependency graph: `sdk-auth` → `sdk-core`, `sdk-sync` → `sdk-core`

### Alternative Structure (Option B)

```
pikaia-android/
├── sdk-network/      → Pure networking (APIClient, Endpoint, interceptors)
├── sdk-auth/         → Auth API + token management
├── sdk-sync/         → Sync engine
├── sdk-media/        → Media API
├── sdk-device/       → Device API
├── sdk-models/       → Shared DTOs and domain models
└── sdk-bom/          → Bill of Materials
```

**Note**: Option A is recommended for balance between modularity and simplicity.

---

## Technology Stack

### Core Libraries

| iOS Component | Android Equivalent | Rationale |
|---------------|-------------------|-----------|
| URLSession | **Ktor Client** | Multiplatform-ready, modern Kotlin, coroutine-native |
| SwiftData | **Room Database** | Official Android ORM, mature, SQLite-based |
| Combine / AsyncSequence | **Kotlin Flow** | Standard for reactive streams in Kotlin |
| Swift Actors | **Mutex + Coroutines** | Kotlin structured concurrency for thread safety |
| `@Observable` | **StateFlow** | Observable state for Compose UI |
| JSONEncoder/Decoder | **Kotlinx Serialization** | Multiplatform, compiler plugin, type-safe |

### Key Dependencies

```kotlin
// Networking
implementation("io.ktor:ktor-client-core:2.3.+")
implementation("io.ktor:ktor-client-okhttp:2.3.+")
implementation("io.ktor:ktor-client-content-negotiation:2.3.+")
implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.+")

// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.+")
implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.+")

// Persistence
implementation("androidx.room:room-runtime:2.6.+")
implementation("androidx.room:room-ktx:2.6.+")
ksp("androidx.room:room-compiler:2.6.+")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.+")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.+")

// Secure Storage
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// Dependency Injection
implementation("io.insert-koin:koin-android:3.5.+")
implementation("io.insert-koin:koin-androidx-compose:3.5.+")

// Optional: Background Work
implementation("androidx.work:work-runtime-ktx:2.9.+")
```

### Why Ktor Over Retrofit?

**Ktor Benefits:**
- Kotlin Multiplatform ready (future KMP support)
- Coroutines-first design
- Lightweight and modern
- Built by JetBrains (same as Kotlin)
- Extensible with features/plugins

**Retrofit Benefits:**
- More Android-native
- Larger ecosystem of converters
- More familiar to Android developers

**Recommendation**: Ktor for future-proofing and Kotlin-first design.

### Why Kotlinx Serialization Over Moshi?

**Kotlinx Serialization Benefits:**
- Compiler plugin (safer, faster)
- Multiplatform support
- No reflection (better for code size/performance)
- Kotlin-first design

**Moshi Benefits:**
- More mature
- Better error messages
- Larger ecosystem

**Recommendation**: Kotlinx Serialization for type safety and KMP readiness.

---

## Implementation Plan

### Phase 1: Foundation (sdk-core)

**Deliverables**: Networking layer, interceptors, error handling, base types

#### 1.1 HTTP Client Infrastructure

**APIClient** - Core HTTP client using Ktor
```kotlin
class APIClient(
    private val config: APIClientConfig,
    private val httpClient: HttpClient,
    private val requestInterceptors: List<RequestInterceptor>,
    private val responseInterceptors: List<ResponseInterceptor>,
    private val tokenStore: TokenStore?,
    private val authProvider: AuthProvider?,
    private val refreshCoordinator: RefreshCoordinator
) {
    suspend fun <Req, Res> send(endpoint: Endpoint<Req, Res>): Res {
        // Build request from endpoint
        // Apply request interceptors
        // Execute HTTP request
        // Handle 401 with refresh logic
        // Apply response interceptors
        // Decode response
        // Handle errors
    }

    private suspend fun handleUnauthorized(
        endpoint: Endpoint<*, *>,
        allowRefresh: Boolean
    ): Response {
        // Check if refresh allowed
        // Get refresh token from store
        // Call refreshCoordinator.refresh()
        // Update tokens in store
        // Retry request once
    }
}
```

**Endpoint** - Generic endpoint definition
```kotlin
data class Endpoint<Req, Res>(
    val method: HttpMethod,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: Req? = null,
    val responseSerializer: KSerializer<Res>
)

// Extension for common patterns
inline fun <reified Res> Endpoint(
    method: HttpMethod,
    path: String,
    query: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    body: Any? = null
): Endpoint<Any, Res> = Endpoint(
    method = method,
    path = path,
    query = query,
    headers = headers,
    body = body,
    responseSerializer = serializer()
)
```

**APIClientConfig** - Configuration
```kotlin
data class APIClientConfig(
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = mapOf(
        "Content-Type" to "application/json"
    ),
    val timeout: Duration = 30.seconds,
    val enableLogging: Boolean = false
)
```

#### 1.2 Error Handling

**APIError** - Sealed class hierarchy
```kotlin
sealed class APIError : Exception() {
    data class InvalidURL(val url: String) : APIError() {
        override val message = "Invalid URL: $url"
    }

    data class Transport(val cause: Throwable) : APIError() {
        override val message = "Network transport error: ${cause.message}"
    }

    data class Client(
        val statusCode: Int,
        val data: String?,
        val metadata: APIErrorMetadata?
    ) : APIError() {
        override val message = buildMessage("Client error", statusCode, metadata)
    }

    data class Server(
        val statusCode: Int,
        val data: String?,
        val metadata: APIErrorMetadata?
    ) : APIError() {
        override val message = buildMessage("Server error", statusCode, metadata)
    }

    data class Decoding(val cause: Throwable) : APIError() {
        override val message = "Failed to decode response: ${cause.message}"
    }

    object Unauthorized : APIError() {
        override val message = "The request was unauthorized"
    }

    data class RefreshFailed(val cause: Throwable) : APIError() {
        override val message = "Failed to refresh authentication: ${cause.message}"
    }

    object Cancelled : APIError() {
        override val message = "The request was cancelled"
    }

    private companion object {
        fun buildMessage(
            prefix: String,
            statusCode: Int,
            metadata: APIErrorMetadata?
        ): String {
            return if (metadata?.requestId != null) {
                "$prefix with status code $statusCode. Request ID: ${metadata.requestId}"
            } else {
                "$prefix with status code $statusCode"
            }
        }
    }
}

data class APIErrorMetadata(
    val requestId: String?,
    val headers: Map<String, String>
)
```

#### 1.3 Interceptor System

**RequestInterceptor** - Request adaptation
```kotlin
interface RequestInterceptor {
    suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder
    suspend fun shouldRetry(response: HttpResponse, data: String?): Boolean = false
}

// Extension for pipeline chaining
suspend fun List<RequestInterceptor>.adapt(
    request: HttpRequestBuilder
): HttpRequestBuilder {
    return fold(request) { req, interceptor -> interceptor.adapt(req) }
}
```

**ResponseInterceptor** - Response handling
```kotlin
interface ResponseInterceptor {
    suspend fun handle(response: HttpResponse, data: String?, request: HttpRequest)
}

// Extension for pipeline chaining
suspend fun List<ResponseInterceptor>.handle(
    response: HttpResponse,
    data: String?,
    request: HttpRequest
) {
    forEach { interceptor -> interceptor.handle(response, data, request) }
}
```

**LogRequestInterceptor** - Debug logging
```kotlin
class LogRequestInterceptor(
    private val tag: String = "PikaiaSDK"
) : RequestInterceptor {
    override suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder {
        Log.d(tag, "→ ${request.method.value} ${request.url}")
        request.headers.forEach { key, values ->
            if (key.lowercase() != "authorization") {
                Log.d(tag, "  $key: ${values.joinToString()}")
            }
        }
        return request
    }
}
```

**LogResponseInterceptor** - Response logging
```kotlin
class LogResponseInterceptor(
    private val tag: String = "PikaiaSDK"
) : ResponseInterceptor {
    override suspend fun handle(
        response: HttpResponse,
        data: String?,
        request: HttpRequest
    ) {
        Log.d(tag, "← ${response.status.value} ${request.method.value} ${request.url}")
        data?.let { Log.d(tag, "  Body: ${it.take(200)}") }
    }
}
```

#### 1.4 Common Types

**EmptyBody / EmptyResponse**
```kotlin
object EmptyBody

@Serializable
object EmptyResponse
```

**HTTPMethod Enum**
```kotlin
enum class HTTPMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE")
}
```

**Package Structure:**
```
sdk-core/src/main/kotlin/dev/pikaia/android/sdk/core/
├── networking/
│   ├── APIClient.kt
│   ├── APIClientConfig.kt
│   ├── Endpoint.kt
│   ├── APIError.kt
│   ├── HTTPMethod.kt
│   ├── EmptyTypes.kt
│   └── interceptors/
│       ├── RequestInterceptor.kt
│       ├── ResponseInterceptor.kt
│       ├── LogRequestInterceptor.kt
│       └── LogResponseInterceptor.kt
└── internal/
    └── (internal utilities)
```

---

### Phase 2: Authentication (sdk-auth)

**Deliverables**: Token management, refresh coordination, auth interceptor, auth API

#### 2.1 Token Management

**TokenStore Interface**
```kotlin
interface TokenStore {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun getDeviceToken(): String?
    suspend fun getDeviceRefreshToken(): String?

    suspend fun setTokens(access: String, refresh: String)
    suspend fun setDeviceTokens(device: String, refresh: String)

    suspend fun clear()
}
```

**EncryptedTokenStore** - Secure implementation
```kotlin
class EncryptedTokenStore(context: Context) : TokenStore {
    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "pikaia_tokens",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }

    override suspend fun getRefreshToken(): String? = withContext(Dispatchers.IO) {
        sharedPrefs.getString(KEY_REFRESH_TOKEN, null)
    }

    override suspend fun getDeviceToken(): String? = withContext(Dispatchers.IO) {
        sharedPrefs.getString(KEY_DEVICE_TOKEN, null)
    }

    override suspend fun getDeviceRefreshToken(): String? = withContext(Dispatchers.IO) {
        sharedPrefs.getString(KEY_DEVICE_REFRESH_TOKEN, null)
    }

    override suspend fun setTokens(access: String, refresh: String) {
        withContext(Dispatchers.IO) {
            sharedPrefs.edit {
                putString(KEY_ACCESS_TOKEN, access)
                putString(KEY_REFRESH_TOKEN, refresh)
            }
        }
    }

    override suspend fun setDeviceTokens(device: String, refresh: String) {
        withContext(Dispatchers.IO) {
            sharedPrefs.edit {
                putString(KEY_DEVICE_TOKEN, device)
                putString(KEY_DEVICE_REFRESH_TOKEN, refresh)
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            sharedPrefs.edit { clear() }
        }
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_DEVICE_TOKEN = "device_token"
        const val KEY_DEVICE_REFRESH_TOKEN = "device_refresh_token"
    }
}
```

**InMemoryTokenStore** - Testing implementation
```kotlin
class InMemoryTokenStore : TokenStore {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var deviceToken: String? = null
    private var deviceRefreshToken: String? = null

    override suspend fun getAccessToken() = accessToken
    override suspend fun getRefreshToken() = refreshToken
    override suspend fun getDeviceToken() = deviceToken
    override suspend fun getDeviceRefreshToken() = deviceRefreshToken

    override suspend fun setTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
    }

    override suspend fun setDeviceTokens(device: String, refresh: String) {
        deviceToken = device
        deviceRefreshToken = refresh
    }

    override suspend fun clear() {
        accessToken = null
        refreshToken = null
        deviceToken = null
        deviceRefreshToken = null
    }
}
```

#### 2.2 Refresh Coordination

**RefreshCoordinator Interface**
```kotlin
interface RefreshCoordinator {
    suspend fun refresh(
        refreshToken: String,
        authProvider: AuthProvider
    ): Pair<String, String?> // (accessToken, newRefreshToken?)
}
```

**DefaultRefreshCoordinator** - Coalescing implementation
```kotlin
class DefaultRefreshCoordinator : RefreshCoordinator {
    private val mutex = Mutex()
    private var currentTask: Deferred<Pair<String, String?>>? = null

    override suspend fun refresh(
        refreshToken: String,
        authProvider: AuthProvider
    ): Pair<String, String?> {
        mutex.withLock {
            // If refresh already in progress, return that result
            currentTask?.let { return it.await() }

            // Start new refresh
            val task = CoroutineScope(Dispatchers.IO).async {
                try {
                    authProvider.refreshTokens(refreshToken)
                } finally {
                    mutex.withLock {
                        currentTask = null
                    }
                }
            }

            currentTask = task
            return task.await()
        }
    }
}
```

**AuthProvider Interface**
```kotlin
interface AuthProvider {
    suspend fun refreshTokens(refreshToken: String): Pair<String, String?>
}
```

#### 2.3 Auth Interceptor

**AuthTokenInterceptor** - Token injection
```kotlin
class AuthTokenInterceptor(
    private val tokenStore: TokenStore,
    private val mode: Mode = Mode.PREFER_BEARER
) : RequestInterceptor {

    enum class Mode {
        BEARER_ONLY,
        DEVICE_ONLY,
        PREFER_BEARER,
        PREFER_DEVICE
    }

    override suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder {
        val bearer = tokenStore.getBearerCredentials()
        val device = tokenStore.getDeviceCredentials()

        val credential = selectCredential(bearer, device)
        credential?.let {
            request.header("Authorization", "${it.scheme} ${it.token}")
        }

        return request
    }

    private fun selectCredential(
        bearer: Credential?,
        device: Credential?
    ): Credential? {
        return when (mode) {
            Mode.BEARER_ONLY -> bearer
            Mode.DEVICE_ONLY -> device
            Mode.PREFER_BEARER -> bearer ?: device
            Mode.PREFER_DEVICE -> device ?: bearer
        }
    }

    private suspend fun TokenStore.getBearerCredentials(): Credential? {
        val token = getAccessToken() ?: return null
        return Credential("Bearer", token, getRefreshToken())
    }

    private suspend fun TokenStore.getDeviceCredentials(): Credential? {
        val token = getDeviceToken() ?: return null
        return Credential("DeviceToken", token, getDeviceRefreshToken())
    }

    private data class Credential(
        val scheme: String,
        val token: String,
        val refreshToken: String?
    )
}
```

#### 2.4 Auth API

**AuthAPI** - Authentication endpoints
```kotlin
class AuthAPI(private val client: APIClient) {

    // Magic Link
    suspend fun sendMagicLink(request: MagicLinkSendRequest): MessageResponse {
        val endpoint = Endpoint<MagicLinkSendRequest, MessageResponse>(
            method = HttpMethod.Post,
            path = "/v1/auth/magic-link/send",
            body = request
        )
        return client.send(endpoint)
    }

    suspend fun authenticateMagicLink(
        request: MagicLinkAuthenticateRequest
    ): MagicLinkAuthenticateResponse {
        val endpoint = Endpoint<MagicLinkAuthenticateRequest, MagicLinkAuthenticateResponse>(
            method = HttpMethod.Post,
            path = "/v1/auth/magic-link/authenticate",
            body = request
        )
        return client.send(endpoint)
    }

    // Discovery
    suspend fun createOrganization(
        request: DiscoveryCreateOrgRequest
    ): SessionResponse {
        val endpoint = Endpoint<DiscoveryCreateOrgRequest, SessionResponse>(
            method = HttpMethod.Post,
            path = "/v1/auth/discovery/create-org",
            body = request
        )
        return client.send(endpoint)
    }

    suspend fun exchangeSession(
        request: DiscoveryExchangeRequest
    ): SessionResponse {
        val endpoint = Endpoint<DiscoveryExchangeRequest, SessionResponse>(
            method = HttpMethod.Post,
            path = "/v1/auth/discovery/exchange",
            body = request
        )
        return client.send(endpoint)
    }

    // Device Provisioning
    suspend fun provisionDevice(
        request: DeviceProvisionRequest,
        mobileAPIKey: String
    ): DeviceProvisionResponse {
        val endpoint = Endpoint<DeviceProvisionRequest, DeviceProvisionResponse>(
            method = HttpMethod.Post,
            path = "/v1/auth/device/provision",
            headers = mapOf("X-Mobile-API-Key" to mobileAPIKey),
            body = request
        )
        return client.send(endpoint)
    }

    // Mobile Provisioning
    suspend fun provisionMobile(
        request: MobileProvisionRequest,
        mobileAPIKey: String
    ): SessionResponse {
        val endpoint = Endpoint<MobileProvisionRequest, SessionResponse>(
            method = HttpMethod.Post,
            path = "/v1/auth/mobile/provision",
            headers = mapOf("X-Mobile-API-Key" to mobileAPIKey),
            body = request
        )
        return client.send(endpoint)
    }

    // Session
    suspend fun logout(): MessageResponse {
        val endpoint = Endpoint<EmptyBody, MessageResponse>(
            method = HttpMethod.Post,
            path = "/v1/auth/logout"
        )
        return client.send(endpoint)
    }

    suspend fun getMe(): MeResponse {
        val endpoint = Endpoint<EmptyBody, MeResponse>(
            method = HttpMethod.Get,
            path = "/v1/auth/me"
        )
        return client.send(endpoint)
    }

    // Profile
    suspend fun updateProfile(request: UpdateProfileRequest): UserInfo {
        val endpoint = Endpoint<UpdateProfileRequest, UserInfo>(
            method = HttpMethod.Patch,
            path = "/v1/auth/me/profile",
            body = request
        )
        return client.send(endpoint)
    }

    // Phone
    suspend fun sendPhoneOtp(request: SendPhoneOtpRequest): PhoneOtpResponse {
        val endpoint = Endpoint<SendPhoneOtpRequest, PhoneOtpResponse>(
            method = HttpMethod.Post,
            path = "/v1/auth/phone/send-otp",
            body = request
        )
        return client.send(endpoint)
    }

    suspend fun verifyPhoneOtp(request: VerifyPhoneOtpRequest): UserInfo {
        val endpoint = Endpoint<VerifyPhoneOtpRequest, UserInfo>(
            method = HttpMethod.Post,
            path = "/v1/auth/phone/verify-otp",
            body = request
        )
        return client.send(endpoint)
    }
}
```

#### 2.5 DTOs

**Request/Response models** with Kotlinx Serialization:

```kotlin
// Magic Link
@Serializable
data class MagicLinkSendRequest(
    val email: String
)

@Serializable
data class MagicLinkAuthenticateRequest(
    val token: String
)

@Serializable
data class MagicLinkAuthenticateResponse(
    @SerialName("interim_session_token") val interimSessionToken: String,
    val organizations: List<OrganizationInfo>
)

@Serializable
data class OrganizationInfo(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("organization_name") val organizationName: String,
    val role: String
)

// Discovery
@Serializable
data class DiscoveryCreateOrgRequest(
    @SerialName("interim_session_token") val interimSessionToken: String,
    @SerialName("organization_name") val organizationName: String
)

@Serializable
data class DiscoveryExchangeRequest(
    @SerialName("interim_session_token") val interimSessionToken: String,
    @SerialName("organization_id") val organizationId: String
)

// Session
@Serializable
data class SessionResponse(
    @SerialName("session_token") val sessionToken: String,
    @SerialName("session_jwt") val sessionJwt: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("expires_at") val expiresAt: Instant,
    @SerialName("user_id") val userId: Int,
    @SerialName("member_id") val memberId: String,
    @SerialName("organization_id") val organizationId: String
)

// Device Provisioning
@Serializable
data class DeviceProvisionRequest(
    @SerialName("device_uuid") val deviceUuid: String,
    val email: String,
    val platform: String,
    val name: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("os_version") val osVersion: String = "",
    @SerialName("app_version") val appVersion: String = ""
)

@Serializable
data class DeviceProvisionResponse(
    @SerialName("device_token") val deviceToken: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("token_expires_at") val tokenExpiresAt: Instant,
    @SerialName("user_id") val userId: Int,
    @SerialName("member_id") val memberId: Int,
    @SerialName("organization_id") val organizationId: Int,
    @SerialName("is_new_user") val isNewUser: Boolean
)

// Mobile Provisioning
@Serializable
data class MobileProvisionRequest(
    val email: String,
    val name: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("organization_id") val organizationId: String? = null
)

// User Info
@Serializable
data class UserInfo(
    @SerialName("user_id") val userId: Int,
    val email: String?,
    val name: String?,
    @SerialName("phone_number") val phoneNumber: String?,
    @SerialName("phone_verified") val phoneVerified: Boolean
)

@Serializable
data class UpdateProfileRequest(
    val name: String
)

// Phone OTP
@Serializable
data class SendPhoneOtpRequest(
    @SerialName("phone_number") val phoneNumber: String
)

@Serializable
data class PhoneOtpResponse(
    @SerialName("method_id") val methodId: String
)

@Serializable
data class VerifyPhoneOtpRequest(
    @SerialName("method_id") val methodId: String,
    val code: String
)

// Me Response
@Serializable
data class MeResponse(
    val user: UserInfo,
    val member: MemberInfo,
    val organization: OrganizationDetail
)

@Serializable
data class MemberInfo(
    @SerialName("member_id") val memberId: String,
    val role: String,
    val status: String
)

@Serializable
data class OrganizationDetail(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("organization_name") val organizationName: String
)

// Common
@Serializable
data class MessageResponse(
    val message: String
)

// Custom serializer for Instant (ISO8601)
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}
```

**Package Structure:**
```
sdk-auth/src/main/kotlin/dev/pikaia/android/sdk/auth/
├── api/
│   ├── AuthAPI.kt
│   └── models/
│       ├── AuthModels.kt
│       ├── SessionModels.kt
│       └── ProfileModels.kt
├── token/
│   ├── TokenStore.kt
│   ├── EncryptedTokenStore.kt
│   └── InMemoryTokenStore.kt
├── refresh/
│   ├── RefreshCoordinator.kt
│   ├── DefaultRefreshCoordinator.kt
│   └── AuthProvider.kt
└── interceptors/
    └── AuthTokenInterceptor.kt
```

---

### Phase 3: Sync Engine (sdk-sync)

**Deliverables**: Sync engine, Room persistence, operation queue, change appliers

#### 3.1 Configuration

**SyncConfiguration** - Engine configuration
```kotlin
data class SyncConfiguration(
    val databaseName: String = "pikaia_sync.db",
    val pushBatchSize: Int = 50,
    val maxRetryAttempts: Int = 5
)
```

#### 3.2 Sync DTOs

**SyncChange** - Server change representation
```kotlin
@Serializable
data class SyncChange(
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    val intent: SyncIntent,
    @Serializable(with = ByteArraySerializer::class)
    val payload: ByteArray,
    @SerialName("server_version") val serverVersion: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncChange) return false
        return entityType == other.entityType &&
               entityId == other.entityId &&
               intent == other.intent &&
               payload.contentEquals(other.payload) &&
               serverVersion == other.serverVersion
    }

    override fun hashCode(): Int {
        var result = entityType.hashCode()
        result = 31 * result + entityId.hashCode()
        result = 31 * result + intent.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + serverVersion.hashCode()
        return result
    }
}

@Serializable
enum class SyncIntent {
    @SerialName("create") CREATE,
    @SerialName("update") UPDATE,
    @SerialName("delete") DELETE
}
```

**Push Request/Response**
```kotlin
@Serializable
data class SyncPushRequest(
    val operations: List<Operation>
) {
    @Serializable
    data class Operation(
        @SerialName("idempotency_key") val idempotencyKey: String,
        @SerialName("entity_type") val entityType: String,
        @SerialName("entity_id") val entityId: String,
        val intent: String,
        @Serializable(with = InstantSerializer::class)
        @SerialName("client_timestamp") val clientTimestamp: Instant,
        @SerialName("base_version") val baseVersion: Int? = null,
        @SerialName("retry_count") val retryCount: Int,
        val data: JsonObject
    )
}

@Serializable
data class SyncPushResponse(
    val results: List<PushResult>
)

@Serializable
data class PushResult(
    @SerialName("idempotency_key") val idempotencyKey: String,
    val status: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("server_timestamp") val serverTimestamp: Instant? = null,
    @SerialName("server_version") val serverVersion: Int? = null,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_message") val errorMessage: String? = null
)
```

**Pull Response**
```kotlin
@Serializable
data class SyncPullResponse(
    val changes: List<PullChange>,
    val cursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("force_resync") val forceResync: Boolean = false
)

@Serializable
data class PullChange(
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    val operation: String,
    val data: JsonObject? = null,
    val version: Int,
    @Serializable(with = InstantSerializer::class)
    @SerialName("updated_at") val updatedAt: Instant
)
```

**Batch Result**
```kotlin
data class SyncBatchResult(
    val appliedCount: Int,
    val errors: Map<String, String>
) {
    val isFullySuccessful: Boolean get() = errors.isEmpty()
}
```

#### 3.3 Persistence Layer (Room)

**SyncDatabase** - Room database
```kotlin
@Database(
    entities = [
        SyncOperationEntity::class,
        SyncCursorEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(SyncTypeConverters::class)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun syncOperationDao(): SyncOperationDao
    abstract fun syncCursorDao(): SyncCursorDao

    companion object {
        fun create(context: Context, databaseName: String): SyncDatabase {
            return Room.databaseBuilder(
                context,
                SyncDatabase::class.java,
                databaseName
            )
            .fallbackToDestructiveMigration() // TODO: Add proper migrations
            .build()
        }
    }
}
```

**SyncOperationEntity** - Operation persistence model
```kotlin
@Entity(tableName = "sync_operations")
data class SyncOperationEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String = UUID.randomUUID().toString().lowercase(),

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "intent")
    val intent: String, // "create", "update", "delete"

    @ColumnInfo(name = "payload")
    val payload: ByteArray,

    @ColumnInfo(name = "status")
    val status: String, // "pending", "in_progress", "failed", "completed"

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long? = null, // Timestamp in millis

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_error")
    val lastError: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncOperationEntity) return false
        return operationId == other.operationId &&
               entityType == other.entityType &&
               entityId == other.entityId &&
               intent == other.intent &&
               payload.contentEquals(other.payload) &&
               status == other.status &&
               retryCount == other.retryCount &&
               nextRetryAt == other.nextRetryAt &&
               createdAt == other.createdAt &&
               lastError == other.lastError
    }

    override fun hashCode(): Int {
        var result = operationId.hashCode()
        result = 31 * result + entityType.hashCode()
        result = 31 * result + entityId.hashCode()
        result = 31 * result + intent.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + retryCount
        result = 31 * result + (nextRetryAt?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (lastError?.hashCode() ?: 0)
        return result
    }
}

enum class OperationStatus(val value: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    FAILED("failed"),
    COMPLETED("completed")
}
```

**SyncCursorEntity** - Cursor persistence
```kotlin
@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1, // Single row

    @ColumnInfo(name = "cursor")
    val cursor: String
)
```

**DAOs** - Data access objects
```kotlin
@Dao
interface SyncOperationDao {
    @Query("""
        SELECT * FROM sync_operations
        WHERE status = 'pending'
        AND (next_retry_at IS NULL OR next_retry_at <= :now)
        ORDER BY created_at ASC
        LIMIT :limit
    """)
    suspend fun getPending(now: Long, limit: Int): List<SyncOperationEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM sync_operations WHERE status = 'pending' LIMIT 1)")
    fun hasPending(): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: SyncOperationEntity)

    @Update
    suspend fun update(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations WHERE operation_id = :operationId")
    suspend fun getById(operationId: String): SyncOperationEntity?

    @Query("DELETE FROM sync_operations WHERE operation_id = :operationId")
    suspend fun deleteById(operationId: String)

    @Query("DELETE FROM sync_operations")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'pending'")
    suspend fun pendingCount(): Int
}

@Dao
interface SyncCursorDao {
    @Query("SELECT cursor FROM sync_cursor WHERE id = 1")
    suspend fun getCursor(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCursor(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursor WHERE id = 1")
    suspend fun clearCursor()
}
```

**Type Converters**
```kotlin
class SyncTypeConverters {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? {
        return value?.let { Base64.encodeToString(it, Base64.DEFAULT) }
    }

    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.let { Base64.decode(it, Base64.DEFAULT) }
    }
}
```

#### 3.4 Operation Queue

**PendingOperation** - DTO for pending operations
```kotlin
data class PendingOperation(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val intent: SyncIntent,
    val payload: ByteArray,
    val retryCount: Int,
    val createdAt: Instant
)
```

**OperationQueue** - Operation management
```kotlin
class OperationQueue(
    private val dao: SyncOperationDao,
    private val retryPolicy: SyncRetryPolicy,
    private val maxRetryAttempts: Int
) {
    suspend fun enqueue(
        entityType: String,
        entityId: String,
        intent: SyncIntent,
        payload: ByteArray
    ) {
        val entity = SyncOperationEntity(
            entityType = entityType,
            entityId = entityId.lowercase(),
            intent = intent.name.lowercase(),
            payload = payload,
            status = OperationStatus.PENDING.value
        )
        dao.insert(entity)
    }

    suspend fun getPending(limit: Int): List<PendingOperation> {
        val now = System.currentTimeMillis()
        return dao.getPending(now, limit).map { entity ->
            PendingOperation(
                operationId = entity.operationId,
                entityType = entity.entityType,
                entityId = entity.entityId,
                intent = SyncIntent.valueOf(entity.intent.uppercase()),
                payload = entity.payload,
                retryCount = entity.retryCount,
                createdAt = Instant.fromEpochMilliseconds(entity.createdAt)
            )
        }
    }

    suspend fun hasPending(): Boolean {
        val now = System.currentTimeMillis()
        return dao.getPending(now, 1).isNotEmpty()
    }

    fun hasPendingFlow(): Flow<Boolean> {
        return dao.hasPending()
    }

    suspend fun markInProgress(operationIds: List<String>) {
        operationIds.forEach { id ->
            dao.getById(id.lowercase())?.let { entity ->
                dao.update(entity.copy(status = OperationStatus.IN_PROGRESS.value))
            }
        }
    }

    suspend fun markSynced(operationId: String) {
        dao.deleteById(operationId.lowercase())
    }

    suspend fun scheduleRetry(operationId: String, error: String) {
        val entity = dao.getById(operationId.lowercase()) ?: return

        val newRetryCount = entity.retryCount + 1

        if (newRetryCount >= maxRetryAttempts) {
            // Permanently failed
            dao.update(entity.copy(
                status = OperationStatus.FAILED.value,
                lastError = error,
                retryCount = newRetryCount
            ))
        } else {
            // Schedule retry
            val nextRetryAt = retryPolicy.nextRetryTimestamp(newRetryCount)
            dao.update(entity.copy(
                status = OperationStatus.PENDING.value,
                retryCount = newRetryCount,
                nextRetryAt = nextRetryAt,
                lastError = error
            ))
        }
    }

    suspend fun markFailed(operationId: String, error: String) {
        val entity = dao.getById(operationId.lowercase()) ?: return
        dao.update(entity.copy(
            status = OperationStatus.FAILED.value,
            lastError = error
        ))
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun pendingCount(): Int {
        return dao.pendingCount()
    }
}
```

**SyncRetryPolicy** - Exponential backoff
```kotlin
class SyncRetryPolicy {
    fun nextRetryTimestamp(attempt: Int): Long {
        // Exponential backoff: 2^attempt seconds, max 5 minutes
        val delaySeconds = min(300, 2.0.pow(attempt).toInt())
        return System.currentTimeMillis() + delaySeconds * 1000
    }
}
```

#### 3.5 Cursor Manager

**SyncCursorManager** - Cursor persistence
```kotlin
class SyncCursorManager(private val dao: SyncCursorDao) {
    suspend fun getCursor(): String? {
        return dao.getCursor()
    }

    suspend fun setCursor(cursor: String) {
        dao.setCursor(SyncCursorEntity(id = 1, cursor = cursor))
    }

    suspend fun clearCursor() {
        dao.clearCursor()
    }
}
```

#### 3.6 Change Applier System

**SyncChangeApplier** - Change application interface
```kotlin
interface SyncChangeApplier {
    suspend fun applyChanges(changes: List<SyncChange>): SyncBatchResult
}
```

**DefaultSyncChangeApplier** - Handler routing
```kotlin
class DefaultSyncChangeApplier : SyncChangeApplier {
    private val handlers = mutableMapOf<String, EntityChangeHandler<*>>()

    fun <T> register(handler: EntityChangeHandler<T>, entityType: String) {
        handlers[entityType] = handler
    }

    override suspend fun applyChanges(changes: List<SyncChange>): SyncBatchResult {
        var appliedCount = 0
        val errors = mutableMapOf<String, String>()

        for (change in changes) {
            val handler = handlers[change.entityType]
            if (handler == null) {
                errors[change.entityId] = "No handler registered for entity type: ${change.entityType}"
                continue
            }

            try {
                @Suppress("UNCHECKED_CAST")
                (handler as EntityChangeHandler<Any>).applyChange(change)
                appliedCount++
            } catch (e: Exception) {
                errors[change.entityId] = e.message ?: "Unknown error"
            }
        }

        return SyncBatchResult(appliedCount, errors)
    }
}
```

**EntityChangeHandler** - Entity-specific handler interface
```kotlin
interface EntityChangeHandler<T> {
    suspend fun decode(payload: ByteArray): T
    suspend fun apply(entity: T, intent: SyncIntent, entityId: String)
    suspend fun applyDelete(entityId: String, payload: ByteArray)

    suspend fun applyChange(change: SyncChange) {
        when (change.intent) {
            SyncIntent.DELETE -> applyDelete(change.entityId, change.payload)
            else -> {
                val entity = decode(change.payload)
                apply(entity, change.intent, change.entityId)
            }
        }
    }
}
```

#### 3.7 Sync Engine

**SyncEngine** - Main coordinator
```kotlin
class SyncEngine(
    private val configuration: SyncConfiguration,
    private val apiClient: SyncAPIClient,
    private val database: SyncDatabase
) {
    sealed class State {
        object Idle : State()
        data class Pushing(val batchSize: Int) : State()
        data class Pulling(val cursor: String?) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastSyncAt = MutableStateFlow<Instant?>(null)
    val lastSyncAt: StateFlow<Instant?> = _lastSyncAt.asStateFlow()

    private val operationQueue = OperationQueue(
        dao = database.syncOperationDao(),
        retryPolicy = SyncRetryPolicy(),
        maxRetryAttempts = configuration.maxRetryAttempts
    )

    private val cursorManager = SyncCursorManager(database.syncCursorDao())

    private var changeApplier: SyncChangeApplier? = null

    val hasPendingOperations: Flow<Boolean> = operationQueue.hasPendingFlow()

    fun setChangeApplier(applier: SyncChangeApplier) {
        changeApplier = applier
    }

    suspend fun enqueue(
        entityType: String,
        entityId: String,
        intent: SyncIntent,
        payload: ByteArray
    ) {
        operationQueue.enqueue(entityType, entityId, intent, payload)
    }

    suspend fun sync() {
        try {
            // Push first
            push()

            // Then pull
            pull()

            _lastSyncAt.value = Clock.System.now()
            _state.value = State.Idle
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Unknown error")
            throw e
        }
    }

    suspend fun triggerFullResync() {
        try {
            // Clear pending operations
            operationQueue.clearAll()

            // Clear cursor
            cursorManager.clearCursor()

            // Pull all data
            pull()

            _lastSyncAt.value = Clock.System.now()
            _state.value = State.Idle
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Unknown error")
            throw e
        }
    }

    private suspend fun push() {
        while (true) {
            val pending = operationQueue.getPending(configuration.pushBatchSize)

            if (pending.isEmpty()) {
                break
            }

            _state.value = State.Pushing(pending.size)

            // Mark as in progress
            val operationIds = pending.map { it.operationId }
            operationQueue.markInProgress(operationIds)

            // Build request
            val request = SyncPushRequest(
                operations = pending.map { op ->
                    SyncPushRequest.Operation(
                        idempotencyKey = op.operationId,
                        entityType = op.entityType,
                        entityId = op.entityId,
                        intent = op.intent.name.lowercase(),
                        clientTimestamp = op.createdAt,
                        baseVersion = null,
                        retryCount = op.retryCount,
                        data = Json.parseToJsonElement(op.payload.decodeToString()).jsonObject
                    )
                }
            )

            // Push to server
            val results: List<PushResult>
            try {
                results = apiClient.push(request)
            } catch (e: Exception) {
                // Network failure - schedule retries for all
                for (id in operationIds) {
                    operationQueue.scheduleRetry(id, e.message ?: "Network error")
                }
                _state.value = State.Error(e.message ?: "Push failed")
                throw SyncError.PushFailed(e.message ?: "Push failed")
            }

            // Process results
            for (result in results) {
                when (result.status) {
                    "success" -> {
                        operationQueue.markSynced(result.idempotencyKey)
                    }
                    "conflict" -> {
                        // Server wins - remove local operation
                        operationQueue.markSynced(result.idempotencyKey)
                    }
                    "error" -> {
                        val errorMessage = result.errorMessage ?: "Unknown error"
                        operationQueue.scheduleRetry(result.idempotencyKey, errorMessage)
                    }
                }
            }
        }
    }

    private suspend fun pull() {
        val applier = changeApplier
            ?: throw SyncError.NoChangeApplier("Change applier not set")

        var cursor = cursorManager.getCursor()

        while (true) {
            _state.value = State.Pulling(cursor)

            val response: SyncPullResponse
            try {
                response = apiClient.pull(cursor)
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Pull failed")
                throw SyncError.PullFailed(e.message ?: "Pull failed")
            }

            // Apply changes
            if (response.changes.isNotEmpty()) {
                val syncChanges = response.changes.map { change ->
                    SyncChange(
                        entityType = change.entityType,
                        entityId = change.entityId,
                        intent = when (change.operation) {
                            "create" -> SyncIntent.CREATE
                            "update" -> SyncIntent.UPDATE
                            "delete" -> SyncIntent.DELETE
                            else -> SyncIntent.UPDATE
                        },
                        payload = change.data?.toString()?.encodeToByteArray() ?: byteArrayOf(),
                        serverVersion = change.version.toLong()
                    )
                }

                val result = applier.applyChanges(syncChanges)

                // Log errors but continue
                if (!result.isFullySuccessful) {
                    result.errors.forEach { (entityId, error) ->
                        Log.e("PikaiaSDK", "Failed to apply change for $entityId: $error")
                    }
                }
            }

            // Update cursor
            if (response.cursor != null) {
                cursorManager.setCursor(response.cursor)
                cursor = response.cursor
            }

            // Check if more data available
            if (!response.hasMore) {
                break
            }
        }
    }
}
```

**SyncError** - Sync-specific errors
```kotlin
sealed class SyncError(message: String) : Exception(message) {
    data class PushFailed(val errorMessage: String) : SyncError(errorMessage)
    data class PullFailed(val errorMessage: String) : SyncError(errorMessage)
    data class NoChangeApplier(val errorMessage: String) : SyncError(errorMessage)
    data class OperationNotFound(val operationId: String) :
        SyncError("Operation not found: $operationId")
}
```

#### 3.8 Sync API Client

**SyncAPIClient** - Interface
```kotlin
interface SyncAPIClient {
    suspend fun push(request: SyncPushRequest): List<PushResult>
    suspend fun pull(cursor: String? = null): SyncPullResponse
}
```

**DefaultSyncAPIClient** - Implementation
```kotlin
class DefaultSyncAPIClient(private val apiClient: APIClient) : SyncAPIClient {

    override suspend fun push(request: SyncPushRequest): List<PushResult> {
        val endpoint = Endpoint<SyncPushRequest, SyncPushResponse>(
            method = HttpMethod.Post,
            path = "/v1/sync/push",
            body = request
        )
        val response = apiClient.send(endpoint)
        return response.results
    }

    override suspend fun pull(cursor: String?): SyncPullResponse {
        val query = cursor?.let { mapOf("since" to it) } ?: emptyMap()

        val endpoint = Endpoint<EmptyBody, SyncPullResponse>(
            method = HttpMethod.Get,
            path = "/v1/sync/pull",
            query = query
        )
        return apiClient.send(endpoint)
    }
}
```

**Package Structure:**
```
sdk-sync/src/main/kotlin/dev/pikaia/android/sdk/sync/
├── SyncEngine.kt
├── SyncConfiguration.kt
├── SyncError.kt
├── api/
│   ├── SyncAPIClient.kt
│   ├── DefaultSyncAPIClient.kt
│   └── models/
│       ├── SyncChange.kt
│       ├── SyncPushRequest.kt
│       ├── SyncPullResponse.kt
│       └── PushResult.kt
├── applier/
│   ├── SyncChangeApplier.kt
│   ├── DefaultSyncChangeApplier.kt
│   └── EntityChangeHandler.kt
├── persistence/
│   ├── SyncDatabase.kt
│   ├── entities/
│   │   ├── SyncOperationEntity.kt
│   │   └── SyncCursorEntity.kt
│   └── dao/
│       ├── SyncOperationDao.kt
│       └── SyncCursorDao.kt
└── internal/
    ├── OperationQueue.kt
    ├── SyncCursorManager.kt
    └── SyncRetryPolicy.kt
```

---

### Phase 4: Additional Modules

#### 4.1 sdk-media

**MediaAPI** - File management
```kotlin
class MediaAPI(private val client: APIClient) {

    suspend fun requestUploadUrl(request: UploadRequest): UploadResponse {
        val endpoint = Endpoint<UploadRequest, UploadResponse>(
            method = HttpMethod.Post,
            path = "/v1/media/upload/request",
            body = request
        )
        return client.send(endpoint)
    }

    suspend fun confirmUpload(request: ConfirmUploadRequest): ImageResponse {
        val endpoint = Endpoint<ConfirmUploadRequest, ImageResponse>(
            method = HttpMethod.Post,
            path = "/v1/media/upload/confirm",
            body = request
        )
        return client.send(endpoint)
    }

    suspend fun deleteImage(imageId: String): MessageResponse {
        val endpoint = Endpoint<EmptyBody, MessageResponse>(
            method = HttpMethod.Delete,
            path = "/v1/media/images/$imageId"
        )
        return client.send(endpoint)
    }
}
```

**DTOs**
```kotlin
@Serializable
data class UploadRequest(
    val filename: String,
    @SerialName("content_type") val contentType: String,
    val purpose: String
)

@Serializable
data class UploadResponse(
    @SerialName("upload_id") val uploadId: String,
    @SerialName("upload_url") val uploadUrl: String,
    val fields: Map<String, String>
)

@Serializable
data class ConfirmUploadRequest(
    @SerialName("upload_id") val uploadId: String
)

@Serializable
data class ImageResponse(
    @SerialName("image_id") val imageId: String,
    val url: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null
)
```

**MediaUploader** - Helper for S3 uploads
```kotlin
class MediaUploader(private val mediaAPI: MediaAPI) {

    suspend fun uploadImage(
        file: File,
        contentType: String = "image/jpeg",
        purpose: String = "avatar"
    ): ImageResponse {
        // 1. Request upload URL
        val uploadResponse = mediaAPI.requestUploadUrl(
            UploadRequest(
                filename = file.name,
                contentType = contentType,
                purpose = purpose
            )
        )

        // 2. Upload to S3
        uploadToS3(file, uploadResponse)

        // 3. Confirm upload
        return mediaAPI.confirmUpload(
            ConfirmUploadRequest(uploadId = uploadResponse.uploadId)
        )
    }

    private suspend fun uploadToS3(file: File, uploadResponse: UploadResponse) {
        // Multipart upload to S3 using presigned URL
        // Implementation using OkHttp or Ktor
    }
}
```

#### 4.2 sdk-device

**DeviceAPI** - Device management
```kotlin
class DeviceAPI(private val client: APIClient) {

    suspend fun initiateLinking(): InitiateLinkResponse {
        val endpoint = Endpoint<EmptyBody, InitiateLinkResponse>(
            method = HttpMethod.Post,
            path = "/v1/device/link/initiate"
        )
        return client.send(endpoint)
    }

    suspend fun completeLinking(request: DeviceLinkCompleteRequest): DeviceLinkCompleteResponse {
        val endpoint = Endpoint<DeviceLinkCompleteRequest, DeviceLinkCompleteResponse>(
            method = HttpMethod.Post,
            path = "/v1/device/link/complete",
            body = request
        )
        return client.send(endpoint)
    }

    suspend fun listDevices(): DeviceListResponse {
        val endpoint = Endpoint<EmptyBody, DeviceListResponse>(
            method = HttpMethod.Get,
            path = "/v1/device/list"
        )
        return client.send(endpoint)
    }

    suspend fun revokeDevice(deviceId: Int): MessageResponse {
        val endpoint = Endpoint<EmptyBody, MessageResponse>(
            method = HttpMethod.Delete,
            path = "/v1/device/$deviceId"
        )
        return client.send(endpoint)
    }

    suspend fun refreshSession(request: SessionRefreshRequest): SessionRefreshResponse {
        val endpoint = Endpoint<SessionRefreshRequest, SessionRefreshResponse>(
            method = HttpMethod.Post,
            path = "/v1/device/session/refresh",
            body = request
        )
        return client.send(endpoint)
    }
}
```

**DTOs**
```kotlin
@Serializable
data class InitiateLinkResponse(
    val token: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("expires_at") val expiresAt: Instant
)

@Serializable
data class DeviceLinkCompleteRequest(
    val token: String,
    @SerialName("device_uuid") val deviceUuid: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("app_version") val appVersion: String? = null
)

@Serializable
data class DeviceLinkCompleteResponse(
    @SerialName("session_token") val sessionToken: String,
    @SerialName("session_jwt") val sessionJwt: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("session_expires_at") val sessionExpiresAt: Instant,
    @SerialName("device_id") val deviceId: Int,
    @SerialName("user_id") val userId: Int,
    @SerialName("member_id") val memberId: String,
    @SerialName("organization_id") val organizationId: String
)

@Serializable
data class DeviceListResponse(
    val devices: List<DeviceInfo>
)

@Serializable
data class DeviceInfo(
    @SerialName("device_id") val deviceId: Int,
    @SerialName("device_name") val deviceName: String,
    val platform: String,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @Serializable(with = InstantSerializer::class)
    @SerialName("last_seen_at") val lastSeenAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    @SerialName("created_at") val createdAt: Instant
)

@Serializable
data class SessionRefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class SessionRefreshResponse(
    @SerialName("session_token") val sessionToken: String,
    @SerialName("session_jwt") val sessionJwt: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("expires_at") val expiresAt: Instant
)
```

---

### Phase 5: BOM & Sample Integration

#### 5.1 sdk-bom

**build.gradle.kts**
```kotlin
plugins {
    id("java-platform")
    `maven-publish`
}

group = "dev.pikaia.android"
version = project.findProperty("pikaia.sdk.version") as String? ?: "0.1.0-SNAPSHOT"

dependencies {
    constraints {
        api(project(":sdk-core"))
        api(project(":sdk-auth"))
        api(project(":sdk-sync"))
        api(project(":sdk-media"))
        api(project(":sdk-device"))
    }
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
            groupId = "dev.pikaia.android"
            artifactId = "sdk-bom"
            version = project.version.toString()
        }
    }
}
```

#### 5.2 Sample App Usage

**build.gradle.kts** (app level)
```kotlin
dependencies {
    // Use BOM for version management
    implementation(platform(project(":sdk-bom")))

    // Add SDK modules without version numbers
    implementation(project(":sdk-core"))
    implementation(project(":sdk-auth"))
    implementation(project(":sdk-sync"))
    implementation(project(":sdk-media"))
    implementation(project(":sdk-device"))
}
```

**Koin Setup**
```kotlin
// In Application class
startKoin {
    androidContext(this@PikaiaApp)
    modules(
        sdkCoreModule,
        sdkAuthModule,
        sdkSyncModule,
        appModule
    )
}
```

**SDK Initialization**
```kotlin
class PikaiaApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin with SDK modules
        startKoin {
            androidContext(this@PikaiaApp)
            modules(
                sdkCoreModule(
                    baseUrl = "https://api.example.com",
                    enableLogging = BuildConfig.DEBUG
                ),
                sdkAuthModule,
                sdkSyncModule(
                    databaseName = "pikaia_sync.db",
                    pushBatchSize = 50
                ),
                appModule
            )
        }
    }
}

// Koin modules
fun sdkCoreModule(baseUrl: String, enableLogging: Boolean) = module {
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            if (enableLogging) {
                install(Logging) {
                    level = LogLevel.ALL
                }
            }
        }
    }

    single {
        APIClientConfig(
            baseUrl = baseUrl,
            enableLogging = enableLogging
        )
    }

    single {
        APIClient(
            config = get(),
            httpClient = get(),
            requestInterceptors = listOfNotNull(
                get<TokenStore?>()?.let { AuthTokenInterceptor(it) },
                if (enableLogging) LogRequestInterceptor() else null
            ),
            responseInterceptors = listOfNotNull(
                if (enableLogging) LogResponseInterceptor() else null
            ),
            tokenStore = getOrNull(),
            authProvider = getOrNull(),
            refreshCoordinator = DefaultRefreshCoordinator()
        )
    }
}

val sdkAuthModule = module {
    single<TokenStore> { EncryptedTokenStore(androidContext()) }
    single { AuthAPI(get()) }
}

fun sdkSyncModule(databaseName: String, pushBatchSize: Int) = module {
    single {
        SyncConfiguration(
            databaseName = databaseName,
            pushBatchSize = pushBatchSize
        )
    }

    single {
        SyncDatabase.create(androidContext(), get<SyncConfiguration>().databaseName)
    }

    single {
        DefaultSyncAPIClient(get())
    }

    single {
        SyncEngine(
            configuration = get(),
            apiClient = get(),
            database = get()
        )
    }
}
```

**Example: Auth Flow**
```kotlin
class AuthViewModel(
    private val authAPI: AuthAPI,
    private val tokenStore: TokenStore
) : ViewModel() {

    suspend fun loginWithMagicLink(email: String) {
        // 1. Send magic link
        authAPI.sendMagicLink(MagicLinkSendRequest(email))

        // 2. User clicks link, gets token, authenticates
        val authResponse = authAPI.authenticateMagicLink(
            MagicLinkAuthenticateRequest(token = "...")
        )

        // 3. User selects organization
        val sessionResponse = authAPI.exchangeSession(
            DiscoveryExchangeRequest(
                interimSessionToken = authResponse.interimSessionToken,
                organizationId = authResponse.organizations.first().organizationId
            )
        )

        // 4. Store tokens
        tokenStore.setTokens(
            access = sessionResponse.sessionToken,
            refresh = sessionResponse.sessionJwt
        )
    }
}
```

**Example: Sync Usage**
```kotlin
// Define entity model
@Serializable
data class Contact(
    val id: String,
    val name: String,
    val email: String
)

// Implement change handler
class ContactChangeHandler(
    private val contactRepository: ContactRepository
) : EntityChangeHandler<Contact> {

    override suspend fun decode(payload: ByteArray): Contact {
        return Json.decodeFromString(payload.decodeToString())
    }

    override suspend fun apply(
        entity: Contact,
        intent: SyncIntent,
        entityId: String
    ) {
        when (intent) {
            SyncIntent.CREATE -> contactRepository.insert(entity)
            SyncIntent.UPDATE -> contactRepository.update(entity)
            SyncIntent.DELETE -> contactRepository.delete(entityId)
        }
    }

    override suspend fun applyDelete(entityId: String, payload: ByteArray) {
        contactRepository.delete(entityId)
    }
}

// Setup sync engine
class SyncManager(
    private val syncEngine: SyncEngine,
    private val contactChangeHandler: ContactChangeHandler
) {
    init {
        val applier = DefaultSyncChangeApplier()
        applier.register(contactChangeHandler, "contact")
        syncEngine.setChangeApplier(applier)
    }

    suspend fun syncNow() {
        syncEngine.sync()
    }

    suspend fun enqueueContactUpdate(contact: Contact) {
        val payload = Json.encodeToString(contact).encodeToByteArray()
        syncEngine.enqueue(
            entityType = "contact",
            entityId = contact.id,
            intent = SyncIntent.UPDATE,
            payload = payload
        )
    }
}

// In ViewModel
class HomeViewModel(
    private val syncManager: SyncManager,
    private val syncEngine: SyncEngine
) : ViewModel() {

    val syncState = syncEngine.state.asStateFlow()
    val hasPendingChanges = syncEngine.hasPendingOperations.asStateFlow()

    fun sync() {
        viewModelScope.launch {
            try {
                syncManager.syncNow()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            syncManager.enqueueContactUpdate(contact)
        }
    }
}
```

---

## Translation Considerations

### Swift → Kotlin Mapping

| Swift Feature | Kotlin Equivalent | Notes |
|---------------|-------------------|-------|
| `async/await` | `suspend` functions | Direct mapping, same concept |
| `Actor` | `Mutex` + coroutines | Use `Mutex` to serialize access to shared state |
| `@MainActor` | `Dispatchers.Main` + `withContext` | Explicit dispatcher switching |
| `@Observable` | `StateFlow` / `LiveData` | StateFlow preferred for Compose |
| `Sendable` protocol | Thread-safe types | Ensure immutability or synchronization |
| `Equatable` / `Codable` | `equals()` + `@Serializable` | Data classes auto-implement equals |
| SwiftData | Room | Different APIs, similar persistence concepts |
| `nonisolated(unsafe)` | `@Volatile` or synchronized blocks | For thread-safe mutable state |
| Protocol extensions | Extension functions | Similar expressiveness |
| Generics | Generics + reified | Kotlin has reified type parameters |
| Enum with associated values | Sealed classes | More powerful in Kotlin |
| `guard let` / `if let` | `?.let {}` / null checks | Kotlin's null safety |
| Error throwing | `Result<T>` or exceptions | Prefer sealed class results for domain errors |
| Optional chaining (`?.`) | Null-safe operator (`?.`) | Same syntax! |
| `URLSession` | `Ktor Client` | Modern HTTP client with coroutines |
| `Combine` | `Flow` | Reactive streams |
| `defer` | `try-finally` | Kotlin doesn't have defer, use finally |
| Property observers (`didSet`) | Delegate properties | Use custom delegates |

### Threading Model

**iOS (Swift 6):**
- Actors for thread-safe state
- `@MainActor` for UI-bound code
- Sendable for safe cross-actor data

**Android (Kotlin):**
- Coroutines with dispatchers
- `Dispatchers.Main` for UI
- `StateFlow` for observable state
- `Mutex` for critical sections

**Example Translation:**

iOS:
```swift
@MainActor
class SyncEngine: ObservableObject {
    @Published var state: State = .idle

    func sync() async {
        state = .syncing
        // ...
    }
}
```

Android:
```kotlin
class SyncEngine {
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun sync() {
        withContext(Dispatchers.Main) {
            _state.value = State.Syncing
        }
        // ...
    }
}
```

### JSON Serialization

**iOS:** Codable with custom CodingKeys
```swift
struct SessionResponse: Codable {
    let sessionToken: String

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
    }
}
```

**Android:** Kotlinx Serialization with @SerialName
```kotlin
@Serializable
data class SessionResponse(
    @SerialName("session_token") val sessionToken: String
)
```

### Error Handling

**iOS:** Error enum conforming to Error protocol
```swift
enum APIError: Error {
    case unauthorized
    case server(Int)
}
```

**Android:** Sealed class extending Exception
```kotlin
sealed class APIError : Exception() {
    object Unauthorized : APIError()
    data class Server(val statusCode: Int) : APIError()
}
```

---

## Testing Strategy

### Unit Tests

**sdk-core**
- HTTP client request building
- Interceptor pipeline execution
- Error parsing and handling
- Endpoint serialization

**sdk-auth**
- Token storage (encrypted and in-memory)
- Refresh coordination (coalescing)
- Auth interceptor credential selection
- API endpoint DTOs serialization

**sdk-sync**
- Operation queue CRUD operations
- Retry policy calculations
- Change applier routing
- Sync engine state management
- Push/pull logic (with mocked API client)

**Example Test:**
```kotlin
class OperationQueueTest {
    private lateinit var database: SyncDatabase
    private lateinit var queue: OperationQueue

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SyncDatabase::class.java
        ).build()

        queue = OperationQueue(
            dao = database.syncOperationDao(),
            retryPolicy = SyncRetryPolicy(),
            maxRetryAttempts = 3
        )
    }

    @Test
    fun `enqueue adds operation to database`() = runBlocking {
        queue.enqueue(
            entityType = "contact",
            entityId = "123",
            intent = SyncIntent.UPDATE,
            payload = "{}".encodeToByteArray()
        )

        val pending = queue.getPending(10)
        assertEquals(1, pending.size)
        assertEquals("contact", pending[0].entityType)
    }

    @Test
    fun `retry policy uses exponential backoff`() {
        val policy = SyncRetryPolicy()

        val retry1 = policy.nextRetryTimestamp(1)
        val retry2 = policy.nextRetryTimestamp(2)
        val retry3 = policy.nextRetryTimestamp(3)

        val delay1 = retry1 - System.currentTimeMillis()
        val delay2 = retry2 - System.currentTimeMillis()
        val delay3 = retry3 - System.currentTimeMillis()

        assertTrue(delay2 > delay1)
        assertTrue(delay3 > delay2)
    }
}
```

### Integration Tests

**Auth Flow**
- Magic link authentication
- Organization selection
- Token refresh on 401
- Mock server using MockWebServer

**Sync Flow**
- Enqueue → Push → Pull cycle
- Conflict resolution
- Retry on failure
- In-memory database

**Example:**
```kotlin
@RunWith(AndroidJUnit4::class)
class SyncIntegrationTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var syncEngine: SyncEngine

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val config = APIClientConfig(baseUrl = mockWebServer.url("/").toString())
        val client = APIClient(config, /* ... */)
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SyncDatabase::class.java
        ).build()

        syncEngine = SyncEngine(
            configuration = SyncConfiguration(),
            apiClient = DefaultSyncAPIClient(client),
            database = database
        )
    }

    @Test
    fun `full sync cycle completes successfully`() = runBlocking {
        // Mock push response
        mockWebServer.enqueue(MockResponse().setBody("""
            {"results": [{"idempotency_key": "123", "status": "success"}]}
        """))

        // Mock pull response
        mockWebServer.enqueue(MockResponse().setBody("""
            {"changes": [], "has_more": false}
        """))

        // Enqueue operation
        syncEngine.enqueue(
            entityType = "contact",
            entityId = "123",
            intent = SyncIntent.UPDATE,
            payload = """{"name": "John"}""".encodeToByteArray()
        )

        // Setup change applier
        val applier = DefaultSyncChangeApplier()
        syncEngine.setChangeApplier(applier)

        // Sync
        syncEngine.sync()

        // Verify state
        assertEquals(SyncEngine.State.Idle, syncEngine.state.value)
        assertNotNull(syncEngine.lastSyncAt.value)
    }
}
```

### Instrumented Tests

**Room Migrations**
- Verify schema migrations work correctly
- Test data preservation across versions

**Encrypted Storage**
- Verify tokens persisted securely
- Test on real device (not emulator)

---

## Dependency Injection

### Koin Modules

**Core Module**
```kotlin
fun sdkCoreModule(baseUrl: String, enableLogging: Boolean) = module {
    // HTTP Client
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }

            if (enableLogging) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.ALL
                }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 30000
            }
        }
    }

    // Config
    single {
        APIClientConfig(
            baseUrl = baseUrl,
            enableLogging = enableLogging
        )
    }

    // Interceptors
    factory { LogRequestInterceptor() }
    factory { LogResponseInterceptor() }
    factory { (tokenStore: TokenStore) -> AuthTokenInterceptor(tokenStore) }

    // API Client
    single {
        val tokenStore: TokenStore? = getOrNull()
        val authProvider: AuthProvider? = getOrNull()

        APIClient(
            config = get(),
            httpClient = get(),
            requestInterceptors = buildList {
                tokenStore?.let { add(AuthTokenInterceptor(it)) }
                if (enableLogging) add(get<LogRequestInterceptor>())
            },
            responseInterceptors = buildList {
                if (enableLogging) add(get<LogResponseInterceptor>())
            },
            tokenStore = tokenStore,
            authProvider = authProvider,
            refreshCoordinator = DefaultRefreshCoordinator()
        )
    }
}
```

**Auth Module**
```kotlin
val sdkAuthModule = module {
    // Token Store
    single<TokenStore> {
        EncryptedTokenStore(androidContext())
    }

    // Auth API
    single { AuthAPI(get()) }

    // Optional: Auth Provider for custom refresh logic
    factory<AuthProvider> {
        object : AuthProvider {
            override suspend fun refreshTokens(refreshToken: String): Pair<String, String?> {
                val api: AuthAPI = get()
                // Implement token refresh using DeviceAPI or custom endpoint
                throw NotImplementedError("Implement token refresh")
            }
        }
    }
}
```

**Sync Module**
```kotlin
fun sdkSyncModule(
    databaseName: String = "pikaia_sync.db",
    pushBatchSize: Int = 50,
    maxRetryAttempts: Int = 5
) = module {

    // Configuration
    single {
        SyncConfiguration(
            databaseName = databaseName,
            pushBatchSize = pushBatchSize,
            maxRetryAttempts = maxRetryAttempts
        )
    }

    // Database
    single {
        SyncDatabase.create(
            context = androidContext(),
            databaseName = get<SyncConfiguration>().databaseName
        )
    }

    // Sync API Client
    single<SyncAPIClient> {
        DefaultSyncAPIClient(get())
    }

    // Sync Engine
    single {
        SyncEngine(
            configuration = get(),
            apiClient = get(),
            database = get()
        )
    }

    // Change Applier (app should provide and configure)
    factory { DefaultSyncChangeApplier() }
}
```

**App Module Example**
```kotlin
val appModule = module {
    // Repositories
    single { ContactRepository(get()) }

    // Change Handlers
    factory { ContactChangeHandler(get()) }

    // View Models
    viewModel { AuthViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { SyncViewModel(get()) }

    // Sync Setup
    single {
        val syncEngine: SyncEngine = get()
        val applier = DefaultSyncChangeApplier()

        // Register entity handlers
        applier.register(get<ContactChangeHandler>(), "contact")
        // applier.register(get<OtherHandler>(), "other_entity")

        syncEngine.setChangeApplier(applier)

        syncEngine
    }
}
```

---

## Code Consistency Guidelines

### 1. Package Naming

Mirror iOS structure for consistency:

```
dev.pikaia.android.sdk.core.networking
dev.pikaia.android.sdk.core.networking.interceptors
dev.pikaia.android.sdk.auth
dev.pikaia.android.sdk.auth.token
dev.pikaia.android.sdk.sync
dev.pikaia.android.sdk.sync.applier
```

### 2. Naming Conventions

**Classes:** PascalCase (same as iOS)
```kotlin
class APIClient
class SyncEngine
class DefaultRefreshCoordinator
```

**Functions:** camelCase (same as iOS)
```kotlin
fun sendMagicLink()
fun applyChanges()
fun getPending()
```

**Constants:** UPPER_SNAKE_CASE (Kotlin convention)
```kotlin
const val MAX_RETRY_ATTEMPTS = 5
const val DEFAULT_BATCH_SIZE = 50
```

**Private properties:** camelCase with underscore prefix for backing properties
```kotlin
private val _state = MutableStateFlow<State>(State.Idle)
val state: StateFlow<State> = _state.asStateFlow()
```

### 3. Documentation

Use KDoc matching Swift's DocC format:

```kotlin
/**
 * Executes API endpoints using Ktor with optional authentication handling.
 *
 * The client automatically handles:
 * - Token refresh on 401 responses
 * - Request/response interceptor pipelines
 * - JSON encoding/decoding with ISO8601 dates
 *
 * @param config Configuration for the API client
 * @param httpClient The HTTP client instance
 * @param tokenStore Optional token storage for authentication
 *
 * @see APIClientConfig
 * @see TokenStore
 */
class APIClient(
    private val config: APIClientConfig,
    private val httpClient: HttpClient,
    private val tokenStore: TokenStore? = null
) {
    /**
     * Sends an HTTP request and returns the decoded response.
     *
     * @param endpoint The endpoint definition
     * @return The decoded response
     * @throws APIError if the request fails
     */
    suspend fun <Req, Res> send(endpoint: Endpoint<Req, Res>): Res {
        // ...
    }
}
```

### 4. Error Handling

Prefer sealed class results for domain errors:

```kotlin
// For expected errors
sealed class AuthResult {
    data class Success(val session: SessionResponse) : AuthResult()
    data class Failure(val error: AuthError) : AuthResult()
}

sealed class AuthError {
    object InvalidCredentials : AuthError()
    object NetworkError : AuthError()
    data class ServerError(val message: String) : AuthError()
}

// For unexpected errors
suspend fun login(): AuthResult {
    return try {
        val response = authAPI.authenticateMagicLink(...)
        AuthResult.Success(response)
    } catch (e: APIError) {
        AuthResult.Failure(AuthError.NetworkError)
    }
}
```

### 5. Immutability

Use `data class` with `val` for DTOs:

```kotlin
@Serializable
data class SessionResponse(
    @SerialName("session_token") val sessionToken: String,
    @SerialName("session_jwt") val sessionJwt: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("expires_at") val expiresAt: Instant
)
```

Prefer immutable collections:

```kotlin
// Good
val operations: List<Operation> = listOf(...)

// Avoid
val operations: MutableList<Operation> = mutableListOf(...)
```

### 6. Null Safety

Leverage Kotlin's null safety:

```kotlin
// Good
suspend fun getAccessToken(): String? {
    return tokenStore.getAccessToken()
}

// Use safe calls and elvis operator
val token = tokenStore.getAccessToken() ?: return null
```

### 7. Coroutine Usage

Always use structured concurrency:

```kotlin
// Good - in ViewModel
fun sync() {
    viewModelScope.launch {
        try {
            syncEngine.sync()
        } catch (e: SyncError) {
            _error.value = e.message
        }
    }
}

// Avoid - unstructured
GlobalScope.launch {
    syncEngine.sync()
}
```

### 8. StateFlow vs LiveData

Prefer StateFlow for new code:

```kotlin
// Good - StateFlow with Compose
private val _state = MutableStateFlow<State>(State.Idle)
val state: StateFlow<State> = _state.asStateFlow()

// In Composable
val state by viewModel.state.collectAsState()

// LiveData if needed for Java interop
val stateLiveData: LiveData<State> = state.asLiveData()
```

### 9. Extension Functions

Use extensions for API convenience:

```kotlin
// Extension on TokenStore
suspend fun TokenStore.isAuthenticated(): Boolean {
    return getAccessToken() != null
}

// Extension on List<RequestInterceptor>
suspend fun List<RequestInterceptor>.adapt(
    request: HttpRequestBuilder
): HttpRequestBuilder {
    return fold(request) { req, interceptor ->
        interceptor.adapt(req)
    }
}
```

### 10. File Organization

One public class per file (Kotlin convention):

```
APIClient.kt        → class APIClient
APIError.kt         → sealed class APIError
TokenStore.kt       → interface TokenStore + InMemoryTokenStore
```

Group related small classes:

```
SyncModels.kt       → SyncChange, SyncIntent, SyncBatchResult
```

---

## Timeline & Estimates

### Phase-by-Phase Breakdown

| Phase | Module | Tasks | Complexity | Estimated Time |
|-------|--------|-------|------------|----------------|
| **Phase 1** | sdk-core | Networking, interceptors, errors, base types | Medium | 2-3 weeks |
| **Phase 2** | sdk-auth | Token management, auth API, interceptors | Medium | 1-2 weeks |
| **Phase 3** | sdk-sync | Sync engine, Room DB, operation queue, appliers | High | 3-4 weeks |
| **Phase 4a** | sdk-media | Media API, upload helpers | Low | 1 week |
| **Phase 4b** | sdk-device | Device API, linking helpers | Low | 1 week |
| **Phase 5** | Integration | BOM, sample app, docs | Medium | 1-2 weeks |
| **Testing** | All | Unit tests, integration tests | High | 2-3 weeks |
| **Documentation** | All | KDoc, architecture docs, guides | Medium | 1-2 weeks |

**Total Estimated Time:** 12-18 weeks (3-4.5 months)

### Recommended Approach

1. **Week 1-3**: Phase 1 (sdk-core) - Foundation
2. **Week 4-5**: Phase 2 (sdk-auth) - Authentication
3. **Week 6-9**: Phase 3 (sdk-sync) - Sync engine (most complex)
4. **Week 10**: Phase 4a (sdk-media) - Media uploads
5. **Week 11**: Phase 4b (sdk-device) - Device linking
6. **Week 12-13**: Phase 5 - Integration & sample app
7. **Week 14-16**: Testing & bug fixes
8. **Week 17-18**: Documentation & polish

### Complexity Breakdown

**Most Complex Components:**
1. **Sync Engine** (sdk-sync)
   - Room persistence with migrations
   - Operation queue with retry logic
   - Push/pull coordination
   - Change applier routing
   - State management

2. **API Client** (sdk-core)
   - Interceptor pipeline
   - Auto token refresh on 401
   - Error handling with metadata
   - Generic endpoint system

3. **Token Refresh** (sdk-auth)
   - Coalescing concurrent requests
   - Thread-safe coordination
   - Integration with API client

**Straightforward Components:**
1. **DTOs** - Direct Swift → Kotlin translation
2. **Media/Device APIs** - Standard REST endpoints
3. **BOM** - Simple dependency management

---

## Open Questions

### 1. HTTP Client Preference

**Question:** Prefer **Ktor** (multiplatform) or **Retrofit** (Android-native)?

**Recommendation:** **Ktor**
- Future KMP support
- Coroutines-first design
- Modern, lightweight
- Less boilerplate

**Decision needed:** Confirm Ktor or prefer Retrofit?

---

### 2. Serialization Library

**Question:** **Kotlinx Serialization** (multiplatform) or **Moshi** (Android-native)?

**Recommendation:** **Kotlinx Serialization**
- Compiler plugin (safer, faster)
- Multiplatform support
- No reflection
- Better code size

**Decision needed:** Confirm Kotlinx Serialization?

---

### 3. Background Sync Integration

**Question:** Should sync engine support WorkManager integration for periodic background sync?

**Recommendation:** **Yes, as optional integration**
- Provide `SyncWorker` class in sdk-sync
- Apps can schedule periodic sync
- Battery-efficient with WorkManager constraints
- Document setup in sample app

**Decision needed:** Include WorkManager integration?

---

### 4. Minimum SDK Version

**Question:** Confirm min SDK 26 (Android 8.0)?

**Current:**
- Room: Works on SDK 26+
- EncryptedSharedPreferences: Requires SDK 23+
- Ktor: Works on SDK 21+

**Recommendation:** **SDK 26** (Android 8.0+)
- Covers ~94% of devices (as of 2025)
- All dependencies work smoothly
- Reasonable baseline

**Decision needed:** Confirm SDK 26 or lower?

---

### 5. Module Structure

**Question:** Add `sdk-media` and `sdk-device` modules now, or combine into existing modules?

**Option A (Recommended):** Separate modules
- Clear separation of concerns
- Independent versioning
- Optional dependencies

**Option B:** Combine into existing
- `sdk-core`: Include media/device
- Fewer modules to manage
- Less granular

**Recommendation:** **Option A** (separate modules)

**Decision needed:** Separate or combined?

---

### 6. Proactive Sync

**Question:** Should SDK auto-sync on network reconnection?

**Recommendation:** **Yes, with opt-out config**
- Register NetworkCallback to detect connectivity
- Auto-trigger sync when online
- Configurable: `SyncConfiguration.autoSyncOnReconnect = true/false`
- Document battery impact

**Decision needed:** Include auto-sync feature?

---

### 7. Logging Strategy

**Question:** Built-in logging or require app to provide logger?

**Option A:** Built-in (current plan)
- Use Android Log for development
- Disabled in release builds
- Simple to use

**Option B:** Injected logger interface
```kotlin
interface SyncLogger {
    fun log(level: Level, message: String)
}
```
- Apps control logging destination
- More flexible
- Extra setup

**Recommendation:** **Option A** with opt-in verbose mode

**Decision needed:** Built-in or injectable logging?

---

### 8. API Error Response Parsing

**Question:** Should SDK parse error response bodies for structured error messages?

**Current:** Just status codes + raw data

**Enhanced:**
```kotlin
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>?
)
```

**Recommendation:** **Parse structured errors** if backend provides them

**Decision needed:** Confirm backend error format?

---

### 9. Testing Requirements

**Question:** What test coverage is required?

**Recommendation:**
- **Unit tests:** 80%+ coverage
- **Integration tests:** Critical flows (auth, sync)
- **Instrumented tests:** Database, encryption

**Decision needed:** Coverage requirements?

---

### 10. Documentation Format

**Question:** What documentation format?

**Options:**
- KDoc + Dokka HTML output
- Markdown docs (like this)
- GitHub wiki
- Dedicated docs site (MkDocs, Docusaurus)

**Recommendation:** **KDoc + Markdown guides**
- KDoc for API reference
- Markdown for architecture and guides
- Host on GitHub Pages

**Decision needed:** Documentation strategy?

---

## Conclusion

This plan provides a comprehensive roadmap for translating the iOS Pikaia SDK to Android while maintaining:

✅ **Architectural consistency** with iOS version
✅ **Kotlin best practices** and idioms
✅ **Modular structure** with BOM versioning
✅ **Offline-first** sync with Room persistence
✅ **Type safety** with Kotlinx Serialization
✅ **Testing strategy** for quality assurance
✅ **Clear documentation** for developers

The estimated timeline of **12-18 weeks** accounts for:
- Implementation complexity
- Testing and bug fixes
- Documentation
- Integration with sample app

**Next Steps:**
1. Review and confirm decisions on open questions
2. Set up project structure (gradle modules, packages)
3. Begin Phase 1: sdk-core networking layer
4. Iterate with regular reviews after each phase

**Success Criteria:**
- [ ] All modules compile without errors
- [ ] Unit tests pass with 80%+ coverage
- [ ] Integration tests verify critical flows
- [ ] Sample app demonstrates all features
- [ ] Documentation complete (KDoc + guides)
- [ ] BOM published for version management
- [ ] Code reviewed and meets quality standards
