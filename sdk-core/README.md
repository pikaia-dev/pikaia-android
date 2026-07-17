# sdk-core

Networking foundation for the Pikaia Android SDKs: a type-safe Ktor client, declarative
endpoints, non-throwing results, typed errors, and the session-based auth abstractions
that `sdk-auth` implements.

## Consumption contract

The SDKs model only the surface shared by every product built on the pikaia backend
stack. Product-specific endpoints (whatever a product's backend fork adds) live in the
consuming app, built on this module's `APIClient` — see [EXAMPLE.md](EXAMPLE.md).
Product-specific values are configuration, never SDK constants:

- `baseUrl` carries the backend's full API prefix (e.g. `https://api.example.com/api/v1`);
  endpoint paths are relative (`auth/me`, `devices/`).
- Deep-link schemes (e.g. for device-link QR codes) belong to the consumer.

## APIClient

```kotlin
val client = APIClient(
    config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
    requestInterceptors = listOf(/* e.g. AuthTokenInterceptor from sdk-auth */),
    tokenStore = tokenStore,             // optional: enables the 401-refresh path
    authProvider = authProvider,         // optional: performs the session refresh
    refreshCoordinator = coordinator,    // optional: coalesces concurrent refreshes
    engine = null                        // injectable for tests (MockEngine)
)

val result: ApiResult<MeResponse> = client.sendResult(endpoint)
```

`sendResult` is the single public request path. On a 401 with the auth wiring present,
the client refreshes the session through the coordinator, persists it, and retries the
request exactly once — never in a loop.

## Endpoints

```kotlin
// Bodiless
val me = endpoint<MeResponse>(HTTPMethod.GET, "auth/me")

// With a JSON body
val send = endpoint<MagicLinkSendRequest, MessageResponse>(
    method = HTTPMethod.POST,
    path = "auth/magic-link/send",
    body = MagicLinkSendRequest(email = "jane@example.com")
)
```

## Results and errors

Every call folds into `ApiResult` — no exceptions to catch:

```kotlin
when (val result = client.sendResult(me)) {
    is ApiResult.Success -> render(result.value)
    is ApiResult.Failure -> when (val error = result.error) {
        is APIError.Http -> show(error.detail ?: "HTTP ${error.statusCode}")
        is APIError.RateLimited -> scheduleRetry(error.retryAfter)
        is APIError.Transport -> showOffline()
        is APIError.Decoding -> report(error)
        is APIError.RefreshFailed -> goToLogin()
    }
}
```

The backend reports errors as a uniform `{"detail": "..."}` body; the parsed message is
available as `APIError.Http.detail` (and on `RateLimited`, together with the parsed
`Retry-After` header). `getOrThrow()` is the escape hatch when throwing semantics fit
better.

## Auth abstractions

`AuthSession` models the backend's Stytch-based session: `sessionJwt` (short-lived
bearer credential), `sessionToken` (opaque long-lived session), optional
`sessionExpiresAt` and `deviceUuid`. Three interfaces around it — `TokenStore`
(persistence), `AuthProvider` (refresh), `RefreshCoordinator` (coalescing) — are
implemented in `sdk-auth`; see that module's README for the canonical assembly.

## Interceptors

`RequestInterceptor.adapt(builder)` mutates outgoing requests (auth headers, tracing);
`ResponseInterceptor.handle(response, body, request)` observes responses.
`LogRequestInterceptor`/`LogResponseInterceptor` are provided for debugging.

## Testing

The engine is constructor-injectable, so unit tests run against Ktor's `MockEngine`
without touching the network — every SDK test suite in this repo works this way.
