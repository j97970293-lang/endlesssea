package dev.endlesssea.app.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * §fiche-locale : dernier scan partagé entre les écrans.
 *
 * La fiche d'un dossier local s'ouvre comme la fiche d'une source ; elle doit
 * pouvoir lire la liste des fichiers sans relancer un scan complet.
 */
object LocalLibraryCache {
    private val _files = MutableStateFlow<List<dev.endlesssea.app.ui.library.LocalVideoUi>>(emptyList())
    val files: StateFlow<List<dev.endlesssea.app.ui.library.LocalVideoUi>> = _files

    fun publish(list: List<dev.endlesssea.app.ui.library.LocalVideoUi>) { _files.value = list }

    fun folder(parentUri: String) = _files.value.filter { it.parentUri == parentUri }
}
