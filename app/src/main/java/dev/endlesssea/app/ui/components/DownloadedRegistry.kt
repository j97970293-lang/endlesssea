package dev.endlesssea.app.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * §telecharge-visible : registre global des médias possédant au moins un
 * fichier téléchargé.
 *
 * Toutes les cartes (accueil, recherche, bibliothèque) l'observent pour
 * afficher la pastille « téléchargé », et la fiche s'en sert pour lire le
 * fichier local MÊME quand les données mobiles sont actives.
 */
object DownloadedRegistry {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids

    fun set(values: Collection<String>) { _ids.value = values.toSet() }
    fun add(id: String) { _ids.value = _ids.value + id }
    fun remove(id: String) { _ids.value = _ids.value - id }
    fun has(id: String?): Boolean = id != null && id in _ids.value
}
