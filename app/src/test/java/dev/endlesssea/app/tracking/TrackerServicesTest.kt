package dev.endlesssea.app.tracking

import dev.endlesssea.data.db.TrackerAccountEntity
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Requêtes capturées localement : aucun vrai compte ni réseau utilisé. */
class TrackerServicesTest {
    private fun client(handler: (Request) -> Pair<Int, String>): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
    private fun Request.text(): String = Buffer().also { body?.writeTo(it) }.readUtf8()

    @Test fun shikimoriUpdatesExistingRateRatherThanPostingDuplicate() = runBlocking {
        var updated = false
        val http = client { request ->
            when (request.url.encodedPath) {
                "/api/users/whoami" -> 200 to """{"id":7}"""
                "/api/v2/user_rates" -> {
                    assertEquals("GET", request.method)
                    assertEquals("7", request.url.queryParameter("user_id"))
                    assertEquals("42", request.url.queryParameter("target_id"))
                    200 to """[{"id":99,"target_id":42,"target_type":"Anime"}]"""
                }
                "/api/v2/user_rates/99" -> {
                    assertEquals("PATCH", request.method)
                    val rate = JSONObject(request.text()).getJSONObject("user_rate")
                    assertEquals(3, rate.getInt("episodes")); assertEquals("watching", rate.getString("status"))
                    updated = true
                    200 to """{"id":99}"""
                }
                else -> error("Unexpected ${request.url}")
            }
        }
        assertTrue(ShikimoriService(http).pushProgress(TrackerAccountEntity("SHIKIMORI", accessToken = "test"), "42", 3, 12, "WATCHING"))
        assertTrue(updated)
    }
    @Test fun shikimoriCreatesRateWithRequiredUserId() = runBlocking {
        var created = false
        val http = client { request ->
            if (request.url.encodedPath.endsWith("whoami")) 200 to """{"id":7}"""
            else if (request.method == "GET") 200 to "[]"
            else {
                assertEquals("POST", request.method)
                val rate = JSONObject(request.text()).getJSONObject("user_rate")
                assertEquals(7, rate.getInt("user_id")); assertEquals(42, rate.getInt("target_id"))
                assertEquals("Anime", rate.getString("target_type")); assertEquals("completed", rate.getString("status"))
                created = true
                201 to """{"id":99}"""
            }
        }
        assertTrue(ShikimoriService(http).pushProgress(TrackerAccountEntity("SHIKIMORI", accessToken = "test"), "42", 12, 12, "WATCHING"))
        assertTrue(created)
    }
    @Test fun malRefreshKeepsRotatedRefreshTokenAndRealExpiry() = runBlocking {
        val before = System.currentTimeMillis()
        val service = MalService(client { request ->
            assertEquals("POST", request.method)
            assertEquals("client_id=public+client&grant_type=refresh_token&refresh_token=old%26token", request.text())
            assertFalse(request.text().contains("client_secret"))
            200 to """{"access_token":"new","refresh_token":"rotated","expires_in":7200}"""
        })
        val result = service.refresh(TrackerAccountEntity("MAL", clientId = "public client", refreshToken = "old&token"))!!
        assertEquals("new", result.accessToken); assertEquals("rotated", result.refreshToken)
        assertTrue(result.expiresAt >= before + 7_200_000L)
        assertTrue(result.expiresAt <= System.currentTimeMillis() + 7_200_000L)
    }
    @Test fun malMapsPlanningToApiStatusAndPropagates401() = runBlocking {
        val account = TrackerAccountEntity("MAL", accessToken = "test")
        val service = MalService(client { request ->
            assertEquals("PUT", request.method)
            assertEquals("num_watched_episodes=0&status=plan_to_watch", request.text())
            200 to "{}"
        })
        assertTrue(service.pushProgress(account, "42", 0, 12, "PLANNING"))
        try {
            MalService(client { 401 to "{}" }).pushProgress(account, "42", 3, 12, "WATCHING")
            fail("401 must reach the repository for refresh")
        } catch (error: TrackerError) { assertEquals(401, error.httpCode) }
    }
}
