# SDK Core Usage Examples

This document provides practical examples of using the sdk-core module.

## Basic Setup

### 1. Create an HTTP Client

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

val httpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        })
    }
}
```

### 2. Configure the API Client

```kotlin
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.APIClientConfig
import kotlin.time.Duration.Companion.seconds

val config = APIClientConfig(
    baseUrl = "https://api.joinsnowball.io",
    defaultHeaders = mapOf(
        "Content-Type" to "application/json",
        "X-Client-Version" to "1.0.0"
    ),
    timeout = 30.seconds,
    enableLogging = true // Enable for development
)

val apiClient = APIClient(
    config = config,
    httpClient = httpClient,
    requestInterceptors = emptyList(), // Add auth interceptor from sdk-auth
    responseInterceptors = emptyList()
)
```

## Making API Requests

### Simple GET Request

```kotlin
import dev.pikaia.android.sdk.core.networking.HTTPMethod
import dev.pikaia.android.sdk.core.networking.endpoint
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val name: String,
    val email: String
)

suspend fun getUser(userId: Int): User {
    val endpoint = endpoint<User>(
        method = HTTPMethod.GET,
        path = "/v1/users/$userId"
    )

    return apiClient.send(endpoint)
}
```

### POST Request with Body

```kotlin
import kotlinx.serialization.SerialName

@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String
)

@Serializable
data class CreateUserResponse(
    val id: Int,
    val name: String,
    val email: String,
    @SerialName("created_at") val createdAt: String
)

suspend fun createUser(name: String, email: String): CreateUserResponse {
    val endpoint = endpoint<CreateUserResponse>(
        method = HTTPMethod.POST,
        path = "/v1/users",
        body = CreateUserRequest(name, email)
    )

    return apiClient.send(endpoint)
}
```

### Request with Query Parameters

```kotlin
@Serializable
data class UserListResponse(
    val users: List<User>,
    val total: Int,
    @SerialName("has_more") val hasMore: Boolean
)

suspend fun searchUsers(query: String, limit: Int = 20): UserListResponse {
    val endpoint = endpoint<UserListResponse>(
        method = HTTPMethod.GET,
        path = "/v1/users",
        query = mapOf(
            "q" to query,
            "limit" to limit.toString()
        )
    )

    return apiClient.send(endpoint)
}
```

### Request with Custom Headers

```kotlin
suspend fun uploadFile(fileId: String, apiKey: String): Unit {
    val endpoint = endpoint<EmptyResponse>(
        method = HTTPMethod.POST,
        path = "/v1/files/$fileId/upload",
        headers = mapOf("X-API-Key" to apiKey)
    )

    apiClient.send(endpoint)
}
```

## Error Handling

### Basic Error Handling

```kotlin
import dev.pikaia.android.sdk.core.networking.APIError

suspend fun getUserSafely(userId: Int): Result<User> {
    return try {
        val user = getUser(userId)
        Result.success(user)
    } catch (e: APIError) {
        when (e) {
            is APIError.Unauthorized -> {
                // Handle auth error - maybe redirect to login
                Result.failure(Exception("Please log in"))
            }
            is APIError.Client -> {
                if (e.statusCode == 404) {
                    Result.failure(Exception("User not found"))
                } else {
                    Result.failure(Exception("Client error: ${e.message}"))
                }
            }
            is APIError.Server -> {
                // Server error - maybe retry later
                Result.failure(Exception("Server error: ${e.message}"))
            }
            is APIError.Transport -> {
                // Network error - check connection
                Result.failure(Exception("Network error: ${e.message}"))
            }
            is APIError.Decoding -> {
                // JSON parsing failed
                Result.failure(Exception("Invalid response format"))
            }
            is APIError.Cancelled -> {
                Result.failure(Exception("Request cancelled"))
            }
            else -> {
                Result.failure(e)
            }
        }
    }
}
```

### Using Error Metadata

```kotlin
suspend fun getUserWithMetadata(userId: Int) {
    try {
        val user = getUser(userId)
        println("User: $user")
    } catch (e: APIError.Client) {
        println("Client error ${e.statusCode}")

        e.metadata?.let { metadata ->
            println("Request ID: ${metadata.requestId}")
            println("Headers: ${metadata.headers}")
        }

        // Raw response data for debugging
        println("Response: ${e.data}")
    }
}
```

## Using Interceptors

### Custom Request Interceptor

```kotlin
import dev.pikaia.android.sdk.core.networking.interceptors.RequestInterceptor
import io.ktor.client.request.*

class ApiKeyInterceptor(private val apiKey: String) : RequestInterceptor {
    override suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder {
        request.header("X-API-Key", apiKey)
        return request
    }
}

// Use it when creating APIClient
val apiClient = APIClient(
    config = config,
    httpClient = httpClient,
    requestInterceptors = listOf(
        ApiKeyInterceptor("your-api-key"),
        LogRequestInterceptor() // For debugging
    )
)
```

### Custom Response Interceptor

```kotlin
import dev.pikaia.android.sdk.core.networking.interceptors.ResponseInterceptor
import io.ktor.client.request.*
import io.ktor.client.statement.*
import android.util.Log

class MetricsInterceptor : ResponseInterceptor {
    override suspend fun handle(
        response: HttpResponse,
        data: String?,
        request: HttpRequest
    ) {
        val duration = response.responseTime.timestamp - response.requestTime.timestamp
        Log.d("Metrics", "Request to ${request.url} took ${duration}ms")

        // Send to analytics service
        // Analytics.track("api_request", mapOf(
        //     "url" to request.url.toString(),
        //     "status" to response.status.value,
        //     "duration" to duration
        // ))
    }
}
```

## Advanced Patterns

### Pagination

```kotlin
@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val cursor: String?,
    @SerialName("has_more") val hasMore: Boolean
)

suspend fun getAllUsers(): List<User> {
    val allUsers = mutableListOf<User>()
    var cursor: String? = null

    do {
        val queryParams = mutableMapOf<String, String>()
        cursor?.let { queryParams["cursor"] = it }

        val endpoint = endpoint<PaginatedResponse<User>>(
            method = HTTPMethod.GET,
            path = "/v1/users",
            query = queryParams
        )

        val response = apiClient.send(endpoint)
        allUsers.addAll(response.items)
        cursor = response.cursor
    } while (response.hasMore)

    return allUsers
}
```

### Retry Logic

```kotlin
import kotlinx.coroutines.delay

suspend fun <T> retryRequest(
    maxAttempts: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: APIError.Server) {
            // Retry on server errors
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        } catch (e: APIError.Transport) {
            // Retry on network errors
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }

    // Last attempt
    return block()
}

// Usage
val user = retryRequest {
    getUser(123)
}
```

### Cancellation Support

```kotlin
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun fetchDataWithCancellation(scope: CoroutineScope) {
    val job = scope.launch(Dispatchers.IO) {
        try {
            val user = getUser(123)
            println("User: $user")
        } catch (e: APIError.Cancelled) {
            println("Request was cancelled")
        }
    }

    // Cancel after 5 seconds if needed
    scope.launch {
        delay(5000)
        job.cancel()
    }
}
```

## Testing

### Mock API Client for Testing

```kotlin
class MockAPIClient : APIClient(
    config = APIClientConfig(baseUrl = "https://mock.api"),
    httpClient = HttpClient(Mock),
    requestInterceptors = emptyList(),
    responseInterceptors = emptyList()
)

// In your test
@Test
fun `test user retrieval`() = runBlocking {
    val mockClient = MockAPIClient()
    // Configure mock responses
    // ...
}
```

## Best Practices

1. **Reuse HTTP Client**: Create one HttpClient instance and share it across your app
2. **Use Dependency Injection**: Inject APIClient via Koin/Dagger for easy testing
3. **Handle Errors Gracefully**: Always wrap API calls in try-catch blocks
4. **Log Selectively**: Only enable logging in debug builds
5. **Use Coroutines**: All API calls are suspend functions - call from coroutine scopes
6. **Type Safety**: Define clear request/response models with Kotlinx Serialization
7. **Interceptors**: Use interceptors for cross-cutting concerns (auth, logging, metrics)

## Next Steps

- Add authentication with `sdk-auth` module (coming in Phase 2)
- Implement offline sync with `sdk-sync` module (coming in Phase 3)
- Handle file uploads with `sdk-media` module (coming in Phase 4)
