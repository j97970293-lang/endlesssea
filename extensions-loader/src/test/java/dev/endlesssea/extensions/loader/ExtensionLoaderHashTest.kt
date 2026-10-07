package dev.endlesssea.extensions.loader

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * §tests (extensions-loader) — première couche de vérification d'un paquet :
 * l'empreinte SHA-256 doit correspondre à celle publiée dans l'index du dépôt
 * (docs/en/04). Un paquet modifié ne doit jamais pouvoir s'installer.
 */
class ExtensionLoaderHashTest {

    private val abcSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @Test
    fun `l'empreinte d'un tableau d'octets est exacte`() {
        assertThat(ExtensionLoader.sha256("abc".toByteArray())).isEqualTo(abcSha)
        assertThat(ExtensionLoader.sha256(ByteArray(0))).isEqualTo(emptySha)
    }

    @Test
    fun `l'empreinte d'un fichier correspond a celle de son contenu`() {
        val file = File.createTempFile("paquet", ".esx").apply { writeBytes("abc".toByteArray()) }
        try {
            assertThat(ExtensionLoader.sha256(file)).isEqualTo(abcSha)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `un paquet modifie ne correspond plus a l'empreinte publiee`() {
        val published = abcSha
        val tampered = "abc ".toByteArray() // un octet ajouté = paquet rejeté
        assertThat(ExtensionLoader.sha256(tampered)).isNotEqualTo(published)
    }

    @Test
    fun `l'empreinte est toujours une chaine hexadecimale de 64 caracteres`() {
        assertThat(ExtensionLoader.sha256(ByteArray(300_000) { (it % 97).toByte() }))
            .matches("[0-9a-f]{64}")
    }
}
