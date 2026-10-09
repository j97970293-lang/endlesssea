package dev.endlesssea.app.tracking

import dev.endlesssea.data.db.TrackerAccountEntity
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AniListLibraryTest {
    private fun page(id: Int, more: Boolean, status: String = "CURRENT") = """{"data":{"Page":{"pageInfo":{"hasNextPage":$more},"mediaList":[{"progress":7,"status":"$status","media":{"id":$id,"title":{"userPreferred":"Titre $id"},"episodes":null,"seasonYear":2024,"coverImage":{"large":"https://poster/$id"}}}]}}}"""
    @Test fun parsesTitleProgressAndUnknownTotalWithoutInventingZero() {
        val p=parseAniListPage(JSONObject(page(42,false)))
        assertEquals("Titre 42",p.entries.single().title);assertEquals(7,p.entries.single().progress)
        assertEquals("WATCHING",p.entries.single().status);assertNull(p.entries.single().total);assertFalse(p.hasNext)
    }
    @Test fun remoteStatusesArePreserved() {
        for(status in listOf("PLANNING","COMPLETED","PAUSED","DROPPED","REPEATING")) assertEquals(status,parseAniListPage(JSONObject(page(1,false,status))).entries.single().status)
    }
    @Test(expected=TrackerError::class) fun errorsMustNotBeTreatedAsAnEmptyLibrary() {
        parseAniListPage(JSONObject("""{"errors":[{"message":"Unauthorized"}],"data":null}"""))
    }
    @Test fun confirmedEmptyListIsValid() {
        assertTrue(parseAniListPage(JSONObject("""{"data":{"Page":{"pageInfo":{"hasNextPage":false},"mediaList":[]}}}""")).entries.isEmpty())
    }
    @Test fun importsAllPagesAndUsesTheAuthenticatedViewer() = runBlocking {
        val pages=mutableListOf<Int>()
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            val buffer=okio.Buffer();chain.request().body!!.writeTo(buffer)
            val payload=JSONObject(buffer.readUtf8())
            val body=if(payload.getString("query").contains("Viewer")) """{"data":{"Viewer":{"id":99}}}""" else {
                val v=payload.getJSONObject("variables");assertEquals(99,v.getInt("user"));val n=v.getInt("page");pages+=n;page(n,n==1)
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val entries=AniListService(http).library(TrackerAccountEntity(service="ANILIST",accessToken="unit-test",userName="test"))
        assertEquals(listOf(1,2),pages);assertEquals(listOf("1","2"),entries.map{it.id})
    }
    @Test fun missingPageFailsRatherThanPublishingPartialData() {
        assertThrows(Exception::class.java) {parseAniListPage(JSONObject("""{"data":{}}"""))}
    }
}
