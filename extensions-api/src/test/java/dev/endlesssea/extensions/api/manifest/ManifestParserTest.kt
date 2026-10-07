package dev.endlesssea.extensions.api.manifest

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import org.junit.Test

/**
 * §tests (extensions-api) — le manifeste est le contrat PUBLIC des extensions :
 * un champ optionnel oublié par un tiers ne doit jamais casser l'installation,
 * un champ obligatoire manquant doit échouer franchement (l'utilisateur voit
 * alors « paquet invalide » au lieu d'une extension à moitié installée).
 */
class ManifestParserTest {

    @Test
    fun `un manifeste minimal prend tous les defauts documentes`() {
        val m = ManifestParser.parseManifest(
            """
            {
              "id": "org.example.demo",
              "name": "Demo",
              "version": 3,
              "versionName": "1.2.0",
              "apiVersion": 1
            }
            """.trimIndent(),
        )
        assertThat(m.id).isEqualTo("org.example.demo")
        assertThat(m.version).isEqualTo(3)
        assertThat(m.description).isEmpty()
        assertThat(m.author.name).isEmpty()
        assertThat(m.languages).isEmpty()
        assertThat(m.types).isEmpty()
        assertThat(m.permissions).isEmpty()
        assertThat(m.iconUrl).isNull()
        assertThat(m.entryClass).isNull()
        assertThat(m.nsfw).isFalse()
        // Capacités par défaut : tout sauf l'authentification (spec §3)
        assertThat(m.capabilities.search).isTrue()
        assertThat(m.capabilities.servers).isTrue()
        assertThat(m.capabilities.subtitles).isTrue()
        assertThat(m.capabilities.downloads).isTrue()
        assertThat(m.capabilities.auth).isFalse()
    }

    @Test
    fun `les descriptions localisees sont conservees telles quelles`() {
        val m = ManifestParser.parseManifest(
            """
            {
              "id": "org.example.loc", "name": "Loc", "version": 1,
              "versionName": "1.0", "apiVersion": 1,
              "description": { "fr": "Source française", "en": "French source" },
              "languages": ["fr", "en"], "types": ["ANIME", "SERIES"],
              "author": { "name": "Quelqu'un", "url": "https://example.org" }
            }
            """.trimIndent(),
        )
        assertThat(m.description["fr"]).isEqualTo("Source française")
        assertThat(m.description["en"]).isEqualTo("French source")
        assertThat(m.languages).containsExactly("fr", "en").inOrder()
        assertThat(m.types).containsExactly("ANIME", "SERIES").inOrder()
        assertThat(m.author.name).isEqualTo("Quelqu'un")
        assertThat(m.author.url).isEqualTo("https://example.org")
    }

    @Test
    fun `un champ inconnu est ignore, jamais fatal`() {
        val m = ManifestParser.parseManifest(
            """
            {
              "id": "org.example.future", "name": "Future", "version": 9,
              "versionName": "0.9", "apiVersion": 2,
              "champQuiNExistePasEncore": { "a": [1, 2, 3] },
              "capabilities": { "search": true, "auth": true, "nouveau": true }
            }
            """.trimIndent(),
        )
        assertThat(m.id).isEqualTo("org.example.future")
        assertThat(m.capabilities.auth).isTrue()
        assertThat(m.capabilities.subtitles).isTrue() // défaut conservé
    }

    @Test
    fun `un champ obligatoire manquant leve une erreur de serialisation`() {
        val broken = """
            { "id": "org.example.broken", "name": "Broken", "version": 1, "apiVersion": 1 }
        """.trimIndent() // versionName manquant
        val thrown = runCatching { ManifestParser.parseManifest(broken) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SerializationException::class.java)
    }

    // ------------------------------------------------------------- dépôts

    @Test
    fun `un index de depot lit plusieurs extensions et leurs drapeaux`() {
        val index = ManifestParser.parseRepoIndex(
            """
            {
              "name": "Dépôt officiel",
              "description": "Sources légales maintenues",
              "url": "https://repo.example.org/index.json",
              "extensions": [
                {
                  "id": "org.example.un", "name": "Un", "version": 4,
                  "versionName": "1.4", "apiVersion": 1,
                  "apkUrl": "https://repo.example.org/un.esx",
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "minAppVersion": 12, "size": 40960, "nsfw": false
                },
                {
                  "id": "org.example.deux", "name": "Deux", "version": 1,
                  "versionName": "0.1", "apiVersion": 1,
                  "apkUrl": "https://repo.example.org/deux.json",
                  "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "kind": "DECLARATIVE", "languages": ["fr"], "types": ["MOVIE"]
                }
              ]
            }
            """.trimIndent(),
        )
        assertThat(index.name).isEqualTo("Dépôt officiel")
        assertThat(index.extensions).hasSize(2)
        val un = index.extensions[0]
        assertThat(un.kind).isEqualTo("COMPILED") // défaut documenté
        assertThat(un.minAppVersion).isEqualTo(12)
        assertThat(un.size).isEqualTo(40960L)
        assertThat(un.sha256).hasLength(64)
        val deux = index.extensions[1]
        assertThat(deux.kind).isEqualTo("DECLARATIVE")
        assertThat(deux.apkUrl).endsWith("deux.json")
        assertThat(deux.languages).containsExactly("fr")
        assertThat(deux.nsfw).isFalse()
    }

    @Test
    fun `un index sans aucune extension reste valide`() {
        val index = ManifestParser.parseRepoIndex(
            """{ "name": "Vide", "url": "https://repo.example.org/index.json" }""",
        )
        assertThat(index.extensions).isEmpty()
        assertThat(index.description).isEmpty()
        assertThat(index.iconUrl).isNull()
    }
}
