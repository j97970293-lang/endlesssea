package dev.endlesssea.app.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * §fournisseur-en-haut : le sélecteur de fournisseur vit désormais dans la barre
 * du haut (il remplace l'icône Extensions, déplacée dans les Réglages), mais la
 * feuille et la liste des sources appartiennent à l'accueil.
 *
 * Ce petit bus évite de remonter un ViewModel dans MainActivity : la barre pousse
 * une demande d'ouverture, l'accueil l'observe et affiche la feuille.
 */
object HomeUiBus {
    private val _providerSheetRequest = MutableStateFlow(0)

    /** Incrémenté à chaque appui sur le bouton « Fournisseur » de la barre du haut. */
    val providerSheetRequest: StateFlow<Int> = _providerSheetRequest

    fun openProviderSheet() { _providerSheetRequest.value += 1 }

    private val _currentProvider = MutableStateFlow("Toutes mes sources")

    /** Libellé du fournisseur actif, affiché dans la barre du haut. */
    val currentProvider: StateFlow<String> = _currentProvider

    private val _currentProviderId = MutableStateFlow("ALL")

    /** Id de l'extension sélectionnée (ou "ALL") — utilisé par la recherche. */
    val currentProviderId: StateFlow<String> = _currentProviderId

    fun publishProvider(label: String, id: String = "ALL") {
        _currentProvider.value = label
        _currentProviderId.value = id
    }
}
