# Building a product-specific API on sdk-core

The SDKs cover the shared backend surface; endpoints your product's backend fork adds
belong in your app, built on `APIClient`. This example adds a hypothetical
`GET reports/summary` + `POST reports/` surface.

## 1. DTOs

```kotlin
@Serializable
data class ReportSummary(
    @SerialName("total_reports") val totalReports: Int,
    @SerialName("last_created_at") val lastCreatedAt: String? = null
)

@Serializable
data class CreateReportRequest(val title: String, val body: String)

@Serializable
data class ReportResponse(val id: Long, val title: String)
```

Field names mirror the backend exactly via `@SerialName`; unknown fields in responses
are ignored, so additive backend changes don't break the client.

## 2. The API class

```kotlin
class ReportsAPI(private val client: APIClient) {

    suspend fun summary(): ApiResult<ReportSummary> {
        val endpoint = endpoint<ReportSummary>(
            method = HTTPMethod.GET,
            path = "reports/summary"
        )
        return client.sendResult(endpoint)
    }

    suspend fun create(request: CreateReportRequest): ApiResult<ReportResponse> {
        val endpoint = endpoint<CreateReportRequest, ReportResponse>(
            method = HTTPMethod.POST,
            path = "reports/",
            body = request
        )
        return client.sendResult(endpoint)
    }
}
```

Reuse the same `APIClient` instance you assembled for the auth stack — your endpoints
then get bearer injection and 401-refresh for free.

## 3. Testing with MockEngine

```kotlin
@Test
fun `summary decodes the backend shape`() = runTest {
    val engine = MockEngine { request ->
        assertEquals("https://api.example.com/api/v1/reports/summary", request.url.toString())
        respond(
            """{"total_reports": 3, "last_created_at": null}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    val client = APIClient(
        config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
        engine = engine
    )

    val summary = ReportsAPI(client).summary().getOrThrow()

    assertEquals(3, summary.totalReports)
}
```
