# DefaultRefreshCoordinator - Design & Implementation

## Problem Statement

### The Race Condition Problem

When multiple API requests fail with 401 (Unauthorized) at the same time, we need to prevent this scenario:

```
Request A fails with 401 → Starts token refresh
Request B fails with 401 → Starts token refresh  ❌ DUPLICATE!
Request C fails with 401 → Starts token refresh  ❌ DUPLICATE!
```

**What we want instead:**

```
Request A fails with 401 → Starts token refresh
Request B fails with 401 → Waits for Request A's refresh
Request C fails with 401 → Waits for Request A's refresh
All three requests get the new token from the same refresh operation
```

### Real-World Scenario

```kotlin
// User opens app, makes 3 simultaneous API calls
viewModelScope.launch { api.getUser() }      // Request A
viewModelScope.launch { api.getPosts() }     // Request B
viewModelScope.launch { api.getComments() }  // Request C

// All three have expired tokens
// All fail with 401 at roughly the same time
// Without coordination: 3 refresh calls to server ❌
// With coordination: 1 refresh call, 3 requests wait ✅
```

## Solution: DefaultRefreshCoordinator

### Core Concept

**Coalesce concurrent refresh requests into a single operation.**

The coordinator acts as a "traffic cop" that ensures only one token refresh happens at a time, and all waiting callers receive the same result.

### Implementation Strategy

We'll use Kotlin's **Mutex** and **Deferred** to coordinate concurrent access:

1. **Mutex**: Ensures only one coroutine enters the critical section
2. **Deferred**: Allows multiple coroutines to await the same result
3. **State tracking**: Remember if a refresh is in progress

## Proposed Implementation

```kotlin
class DefaultRefreshCoordinator : RefreshCoordinator {
    private val mutex = Mutex()
    private var currentRefreshTask: Deferred<Pair<String, String?>>? = null

    override suspend fun refresh(
        refreshToken: String,
        authProvider: AuthProvider
    ): Pair<String, String?> {
        // Lock the mutex to ensure only one coroutine proceeds
        mutex.withLock {
            // Check if refresh is already in progress
            currentRefreshTask?.let { existingTask ->
                // Another coroutine is already refreshing
                // Wait for it and return the same result
                return existingTask.await()
            }

            // No refresh in progress - start a new one
            val refreshTask = CoroutineScope(Dispatchers.IO).async {
                try {
                    // Call the auth provider to refresh tokens
                    authProvider.refreshTokens(refreshToken)
                } finally {
                    // Clean up: mark refresh as complete
                    mutex.withLock {
                        currentRefreshTask = null
                    }
                }
            }

            // Store the task so other callers can await it
            currentRefreshTask = refreshTask

            // Wait for our own refresh to complete
            return refreshTask.await()
        }
    }
}
```

## How It Works - Step by Step

### Scenario: 3 Concurrent Requests

**Timeline:**

```
Time 0ms:
  Request A: Calls refresh() → Acquires mutex → Starts authProvider.refreshTokens()
  Request B: Calls refresh() → Blocked on mutex (waiting)
  Request C: Calls refresh() → Blocked on mutex (waiting)

Time 50ms:
  Request A: Still executing authProvider.refreshTokens() on network
  Request B: Still waiting for mutex
  Request C: Still waiting for mutex

Time 100ms:
  Request A: Gets tokens from server: (newAccess, newRefresh)
  Request A: Sets currentRefreshTask = null
  Request A: Releases mutex
  Request A: Returns (newAccess, newRefresh)

Time 101ms:
  Request B: Acquires mutex
  Request B: Sees currentRefreshTask is null (refresh complete)
  Request B: Releases mutex immediately
  Request B: Returns the SAME (newAccess, newRefresh) ← From completed Deferred

Time 102ms:
  Request C: Acquires mutex
  Request C: Sees currentRefreshTask is null
  Request C: Releases mutex immediately
  Request C: Returns the SAME (newAccess, newRefresh) ← From completed Deferred
```

**Result**: Only 1 network call, all 3 requests get the same new tokens!

## Alternative Implementation (Simpler)

If the above is too complex, here's a simpler version:

```kotlin
class DefaultRefreshCoordinator : RefreshCoordinator {
    private val mutex = Mutex()

    override suspend fun refresh(
        refreshToken: String,
        authProvider: AuthProvider
    ): Pair<String, String?> {
        // Simple approach: Only one refresh at a time
        // Later requests wait, then do their own refresh
        mutex.withLock {
            return authProvider.refreshTokens(refreshToken)
        }
    }
}
```

**Pros**: Simpler, easier to understand
**Cons**: Later requests still call authProvider.refreshTokens() but they're serialized

## Questions & Discussion

### Q1: Why use Deferred instead of just Mutex?

**With just Mutex** (simpler approach):
```kotlin
Request A: refresh() → Wait 100ms → Get new tokens
Request B: refresh() → Wait for A → Then wait ANOTHER 100ms → Get same tokens
Request C: refresh() → Wait for A & B → Then wait ANOTHER 100ms → Get same tokens
```
Total time: 300ms (sequential)

**With Deferred** (complex approach):
```kotlin
Request A: refresh() → Wait 100ms → Get new tokens
Request B: refresh() → Wait for A → Instantly get same tokens
Request C: refresh() → Wait for A → Instantly get same tokens
```
Total time: 100ms (parallel wait)

### Q2: What if the refresh fails?

Both approaches propagate the exception:

```kotlin
try {
    authProvider.refreshTokens(refreshToken)
} catch (e: Exception) {
    // Exception is thrown to all waiting callers
    throw e
}
```

### Q3: Thread safety concerns?

- **Mutex**: Ensures only one coroutine in critical section
- **Deferred**: Inherently thread-safe
- **State cleanup**: Always happens in finally block

### Q4: Memory leaks?

No - the Deferred is cleared in the finally block, allowing GC.

### Q5: What if different refresh tokens?

Current design uses the FIRST refresh token. If Request A starts with token1, and Request B starts with token2, Request B will wait for A's result (using token1).

**Is this a problem?**
- In practice, all concurrent requests should have the same refresh token
- If tokens differ, it means the app state is inconsistent
- We could add a check: `if (refreshToken != storedToken) throw Error()`

## Recommended Approach

I recommend the **complex approach with Deferred** because:

1. **Faster**: Subsequent requests don't wait for redundant network calls
2. **More efficient**: Only 1 network call regardless of concurrency
3. **Industry standard**: This is how most SDKs handle it (OkHttp, Retrofit, etc.)
4. **Not that complex**: About 20 lines of code

However, if you prefer simplicity and don't mind slight inefficiency:
- The **simple Mutex-only** approach is fine
- Trade-off: Slight delay for concurrent requests
- Benefit: Much easier to understand and maintain

## Testing Strategy

```kotlin
@Test
fun `concurrent refresh requests are coalesced`() = runBlocking {
    var refreshCallCount = 0

    val authProvider = object : AuthProvider {
        override suspend fun refreshTokens(refreshToken: String): Pair<String, String?> {
            refreshCallCount++
            delay(100) // Simulate network delay
            return "new-access-token" to "new-refresh-token"
        }
    }

    val coordinator = DefaultRefreshCoordinator()

    // Launch 10 concurrent refresh requests
    val results = (1..10).map {
        async {
            coordinator.refresh("old-refresh-token", authProvider)
        }
    }.awaitAll()

    // Verify: Only 1 refresh call was made
    assertEquals(1, refreshCallCount)

    // Verify: All got the same result
    results.forEach { result ->
        assertEquals("new-access-token", result.first)
    }
}
```

## Questions for You

1. **Which approach do you prefer?**
   - A) Complex with Deferred (faster, more efficient)
   - B) Simple with Mutex only (easier to understand)

2. **Should we validate refresh tokens match?**
   - Throw error if concurrent requests have different tokens?

3. **Should we add timeout?**
   - What if refresh takes too long? Should we timeout and fail?

4. **Should we add logging?**
   - Log when refresh starts/completes/coalesces?

Please let me know which approach you prefer, and I'll implement it accordingly!

---

## Additional Notes on DataStore

Since you mentioned we should use **DataStore instead of EncryptedSharedPreferences**, here's the plan:

### DataStore Implementation

```kotlin
class DataStoreTokenStore(context: Context) : TokenStore {
    private val dataStore = context.createDataStore(
        fileName = "pikaia_tokens.pb",
        serializer = TokensSerializer
    )

    // Use Preferences DataStore (simpler) or Proto DataStore (type-safe)?
    // Recommendation: Preferences DataStore for simplicity
}
```

**Questions:**
1. Should we use **Preferences DataStore** (key-value) or **Proto DataStore** (typed)?
2. Should we encrypt the DataStore file itself using EncryptedFile?
3. Or rely on Android's built-in file encryption (works on API 24+)?

**My Recommendation:**
- Use **Preferences DataStore** (easier, JSON-like)
- Use **EncryptedFile** wrapper for additional security
- Falls back gracefully on older devices

Let me know your preferences and I'll implement accordingly!
