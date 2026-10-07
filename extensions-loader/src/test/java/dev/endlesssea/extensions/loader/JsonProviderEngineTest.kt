package dev.endlesssea.extensions.loader

import com.google.common.truth.Truth.assertThat
import dev.endlesssea.extensions.api.EsRequest
import dev.endlesssea.extensions.api.EsResponse
import dev.endlesssea.extensions.api.ExtensionHttpClient
import dev.endlesssea.extensions.api.model.MainPageRequest
import dev.endlesssea.extensions.api.model.MediaType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Test

/**
 * §tests (extensions-loader) — fournisseurs DÉCLARATIFS : le JSON porte le
 * manifeste et un catalogue statique exploité sans exécuter de code
 * (docs/en/04 §2.2). Les cas couverts sont ceux qui font « l'extension ne
 * s'affiche pas » côté utilisateur : type inconnu, JSON abîmé, manifeste vide.
 */
class JsonProviderEngineTest {

    /** Aucun test de réseau : la passerelle HTTP est un double inerte. */
    private class FakeHttp : ExtensionHttpClient {
        override suspend fun execute(request: EsRequest): EsResponse =
            EsResponse(code = 200, body = "", headers = emptyMap(), finalUrl = request.url)

        override fun dumpCookies(host: String): Map<String, String> = emptyMap()
        override val userAgent: String = "EndlessSeaTest/1.0"
    }

    private fun engine() = JsonProviderEngine(FakeHttp())

    private val providerJson = """
        {
          "manifest": {
            "id": "org.demo.catalogue",
            "name": "Catalogue de démonstration",
            "version": 2,
            "apiVersion": 1,
            "languages": ["fr", "en"],
            "types": ["ANIME", "SERIES", "PODCAST"],
            "iconUrl": "https://demo.example.org/icon.png",
            "permissions": ["INTERNET"]
          },
          "catalog": [
            {
              "id": "d1", "title": "Série démo", "type": "ANIME", "year": 2024,
              "posterUrl": "https://demo.example.org/d1.jpg",
              "genres": ["Action", "Aventure"],
              "streams": [
                { "url": "https://cdn.example.org/d1.mp4", "quality": "1080p",
                  "subtitles": [ { "url": "https://cdn.example.org/d1.vtt", "lang": "fr" } ] }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `le manifeste declaratif est lu et les types valides conserves`() {
        val info = engine().fromJson(providerJson).info
        assertThat(info.id).isEqualTo("org.demo.catalogue")
        assertThat(info.name).isEqualTo("Catalogue de démonstration")
        assertThat(info.version).isEqualTo(2)
        assertThat(info.apiVersion).isEqualTo(1)
        assertThat(info.languages).containsExactly("fr", "en").inOrder()
        assertThat(info.iconUrl).isEqualTo("https://demo.example.org/icon.png")
    }

    @Test
    fun `un type de contenu inconnu est ignore sans casser l'extension`() {
        // « PODCAST » n'existe pas dans MediaType : il doit être retiré, pas jeter.
        val types = engine().fromJson(providerJson).info.types
        assertThat(types).containsExactly(MediaType.ANIME, MediaType.SERIES)
    }

    @Test
    fun `le catalogue statique alimente la page d'accueil`() = runBlocking {
        val items = engine().fromJson(providerJson)
            .getMainPage(MainPageRequest(category = "home", page = 1)).items

        assertThat(items).hasSize(1)
        val first = items.first()
        assertThat(first.id).isEqualTo("d1")
        assertThat(first.title).isEqualTo("Série démo")
        assertThat(first.type).isEqualTo(MediaType.ANIME)
        assertThat(first.year).isEqualTo(2024)
        assertThat(first.posterUrl).isEqualTo("https://demo.example.org/d1.jpg")
    }

    @Test
    fun `un manifeste sans catalogue reste lisible`() = runBlocking {
        val raw = """
            { "manifest": { "id": "org.demo.vide", "name": "Vide", "version": 1 } }
        """.trimIndent()
        val ext = engine().fromJson(raw)
        assertThat(ext.info.id).isEqualTo("org.demo.vide")
        assertThat(ext.info.apiVersion).isEqualTo(1) // défaut documenté
        assertThat(ext.getMainPage(MainPageRequest("home", 1)).items).isEmpty()
    }

    @Test
    fun `un JSON abime leve une erreur de serialisation explicite`() {
        val thrown = runCatching { engine().fromJson("{ ceci n'est pas du JSON }") }
            .exceptionOrNull()
        assertThat(thrown).isInstanceOf(SerializationException::class.java)
    }

    @Test
    fun `un manifeste sans identifiant est refuse`() {
        val thrown = runCatching { engine().fromJson("""{ "manifest": { "name": "Sans id" } }""") }
            .exceptionOrNull()
        assertThat(thrown).isInstanceOf(SerializationException::class.java)
    }
}
