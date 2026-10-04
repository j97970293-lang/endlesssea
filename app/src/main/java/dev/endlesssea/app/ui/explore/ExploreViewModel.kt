package dev.endlesssea.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.ui.search.SearchItemUi
import dev.endlesssea.extensions.api.model.MainPageRequest
import dev.endlesssea.extensions.loader.ExtensionRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreRowUi(
    val title: String,
    val items: List<SearchItemUi>,
    /** Identifiant du paquet d'extension ayant produit la rangée. */
    val pkg: String,
    /** Clé de catalogue (rangée) pour « Tout voir » — « main » si unique. */
    val category: String = "main",
)

data class ExploreUiState(
    val loading: Boolean = true,
    val rows: List<ExploreRowUi> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val extensionCount: Int = 0,
    /** (pkg → nom d'affichage) de toutes les extensions activées, même en erreur. */
    val extensions: List<Pair<String, String>> = emptyList(),
)

/** Explore (spec §11) : pour chaque extension activée, une rangée de son catalogue principal. */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val registry: ExtensionRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState

    init { refresh() }

    private companion object {
        /** Plafond de rangées par source sur Explorer (évite un mur avec 14 genres). */
        const val MAX_CATEGORIES_PER_EXT = 6
    }

    fun refresh() = viewModelScope.launch {
        _uiState.value = ExploreUiState(loading = true, extensions = _uiState.value.extensions)
        val extensions = registry.enabledExtensions()
        if (extensions.isEmpty()) {
            _uiState.value = ExploreUiState(loading = false, extensionCount = 0, extensions = emptyList())
            return@launch
        }
        val extLabels = extensions.map { (_, ext) -> ext.info.id to ext.info.name }
        val rows = mutableListOf<ExploreRowUi>()
        val errors = mutableMapOf<String, String>()

        coroutineScope {
            extensions.map { (pkg, ext) ->
                async {
                    // ---- Catalogues par genre : l'extension déclare ses rangées (api≤1 ignoré → « main »)
                    val declared = runCatching { ext.categories() }.getOrDefault(emptyList())
                        .filter { it.key.isNotBlank() }
                    val catList = declared.ifEmpty {
                        listOf(dev.endlesssea.extensions.api.model.HomeCategory("main", ext.info.name))
                    }
                    catList.take(MAX_CATEGORIES_PER_EXT).forEach { cat ->
                        val actualKey = if (declared.isEmpty()) "main" else cat.key
                        runCatching {
                            ext.getMainPage(MainPageRequest(category = actualKey, page = 1))
                        }.onSuccess { page ->
                            if (page.items.isNotEmpty()) {
                                val wrapped = ExploreRowUi(
                                    title = if (declared.isEmpty()) ext.info.name
                                    else "${ext.info.name} — ${cat.title}",
                                    pkg = ext.info.id,
                                    category = cat.key,
                                    items = page.items.take(20).map {
                                        SearchItemUi(
                                            id = "${ext.info.id}:${it.url}", title = it.title,
                                            posterUrl = it.posterUrl,
                                            subtitle = it.year?.toString() ?: it.type.name,
                                            rating = it.rating,
                                            audioLangs = it.audioLangs.map { l -> l.name },
                                        )
                                    },
                                )
                                synchronized(rows) { rows += wrapped }
                            }
                        }.onFailure { e ->
                            synchronized(errors) { errors["${ext.info.name} · ${cat.title}"] = e.message ?: "erreur" }
                        }
                    }
                }
            }.forEach { it.await() }
        }

        _uiState.value = ExploreUiState(
            loading = false,
            rows = rows.sortedBy { it.title },
            errors = errors,
            extensionCount = extensions.size,
            extensions = extLabels,
        )
    }
}
