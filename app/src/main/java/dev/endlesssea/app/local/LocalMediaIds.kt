package dev.endlesssea.app.local

/** Identifiant de série stable commun aux fiches, à l'historique et au lecteur local. */
object LocalMediaIds {
    fun series(folderUri: String): String = "local:$folderUri"
}
