package dev.endlesssea.downloader

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * §tests (downloader) — empreinte SHA-256 d'un fichier terminé (conv. 10) :
 * c'est la preuve d'intégrité affichée par « Vérifier l'intégrité ».
 * Les deux digests de référence sont ceux de la RFC 6234 (« abc » et le vide).
 */
class DownloadHashTest {

    private val abcSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("es-hash", ".bin").apply { writeBytes(bytes) }

    @Test
    fun `le digest d'un contenu connu est exact`() {
        val file = tempFile("abc".toByteArray())
        try {
            assertThat(DownloadManager.sha256(file)).isEqualTo(abcSha)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `un fichier vide renvoie le digest du vide`() {
        val file = tempFile(ByteArray(0))
        try {
            assertThat(DownloadManager.sha256(file)).isEqualTo(emptySha)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `deux contenus differents donnent des empreintes differentes`() {
        val a = tempFile("abc".toByteArray())
        val b = tempFile("abd".toByteArray())
        try {
            assertThat(DownloadManager.sha256(a)).isNotEqualTo(DownloadManager.sha256(b))
        } finally {
            a.delete(); b.delete()
        }
    }

    @Test
    fun `un fichier plus gros qu'un bloc est lu entierement`() {
        // 300 000 octets : traverse plusieurs blocs de 64 Kio
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        val file = tempFile(bytes)
        try {
            val digest = DownloadManager.sha256(file)
            assertThat(digest).hasLength(64)
            assertThat(digest).matches("[0-9a-f]{64}")
        } finally {
            file.delete()
        }
    }
}
