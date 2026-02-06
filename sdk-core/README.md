# SDK Core Module

Core networking and authentication infrastructure for the Pikaia Android SDK.

## Overview

The `sdk-core` module provides the foundational networking layer used by all other SDK modules. It includes:

- **HTTP Client**: Type-safe API client built on Ktor
- **Interceptor System**: Request and response interceptor pipelines
- **Error Handling**: Comprehensive error types with metadata
- **Authentication Interfaces**: Token storage and refresh coordination (implemented in `sdk-auth`)

## Key Components

### APIClient

The main HTTP client that executes API requests with automatic token refresh.

```kotlin
val config = APIClientConfig(
    baseUrl = "https://api.example.com",
    enableLogging = true
)

val client = APIClient(
    config = config,
    httpClient = HttpClient(OkHttp) { /* ... */ },
    requestInterceptors = listOf(/* ... */),
    responseInterceptors = listOf(/* ... */),
    tokenStore = tokenStore,
    authProvider = authProvider,
    refreshCoordinator = refreshCoordinator
)

// Make a request
val response = client.send(endpoint)
```

### Endpoint

Type-safe endpoint definition for API operations.

```kotlin
val endpoint = Endpoint(
    method = HTTPMethod.POST,
    path = "/v1/users",
    body = CreateUserRequest(name = "John"),
    responseSerializer = UserResponse.serializer()
)

// Or use the convenience function with type inference
val endpoint = endpoint<UserResponse>(
    method = HTTPMethod.GET,
    path = "/v1/users/123"
)
```

### Interceptors

Interceptors allow you to modify requests and observe responses.

**Request Interceptor:**
```kotlin
class AuthTokenInterceptor(private val tokenStore: TokenStore) : RequestInterceptor {
    override suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder {
        val token = tokenStore.getAccessToken()
        if (token != null) {
            request.header("Authorization", "Bearer $token")
        }
        return request
    }
}
```

**Response Interceptor:**
```kotlin
class MetricsInterceptor : ResponseInterceptor {
    override suspend fun handle(
        response: HttpResponse,
        data: String?,
        request: HttpRequest
    ) {
        // Log metrics, record analytics, etc.
    }
}
```

### Error Handling

All API errors are represented as sealed classes:

```kotlin
try {
    val response = client.send(endpoint)
} catch (e: APIError) {
    when (e) {
        is APIError.Unauthorized -> // Handle auth error
        is APIError.Client -> // Handle 4xx error
        is APIError.Server -> // Handle 5xx error
        is APIError.Transport -> // Handle network error
        is APIError.Decoding -> // Handle JSON parsing error
        // ... other error types
    }
}
```

## Authentication Interfaces

The core module defines authentication interfaces that are implemented in `sdk-auth`:

- **TokenStore**: Secure storage for access and refresh tokens
- **AuthProvider**: Logic for refreshing expired tokens
- **RefreshCoordinator**: Coalesces concurrent refresh requests

## Usage in Other Modules

Other SDK modules depend on `sdk-core` for networking:

```kotlin
// In sdk-auth module
class AuthAPI(private val client: APIClient) {
    suspend fun login(email: String): SessionResponse {
        val endpoint = endpoint<SessionResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/login",
            body = LoginRequest(email)
        )
        return client.send(endpoint)
    }
}
```

## Dependencies

- **Ktor Client**: Modern HTTP client with coroutines support
- **Kotlinx Serialization**: Type-safe JSON serialization
- **Kotlinx Datetime**: Date/time handling
- **Coroutines**: Async programming

## Testing

Unit tests are provided for:
- Request building and URL construction
- Error handling and status code mapping
- Interceptor pipeline execution
- Metadata extraction

Run tests:
```bash
./gradlew :sdk-core:test
```

## Thread Safety

All components are designed to be thread-safe:
- APIClient can be shared across coroutines
- Interceptors should be stateless or use proper synchronization
- Token refresh is coordinated to prevent race conditions (via RefreshCoordinator)
