package dev.endlesssea.app.local

/**
 * §nom-fichier — un URI SAF ressemble à
 * `content://…/document/primary%3AMovies%2FOne%20Piece%2Fep01.mp4`.
 * Prendre « le dernier segment » donne donc un CHEMIN (« primary:Movies/ep01.mp4 »),
 * d'où les titres illisibles. Ce helper rend toujours un nom de fichier propre.
 */
object LocalNames {

    fun fileName(uri: String): String {
        val decoded = android.net.Uri.decode(uri.substringBefore('?'))
        return decoded.substringAfterLast('/').substringAfterLast(':').ifBlank { decoded }
    }

    /** Nom lisible : sans extension, underscores remplacés. */
    fun pretty(uri: String): String {
        val name = fileName(uri)
        val base = name.substringBeforeLast('.')
        return base.replace('_', ' ').replace('.', ' ').trim().ifBlank { name }
    }
}
