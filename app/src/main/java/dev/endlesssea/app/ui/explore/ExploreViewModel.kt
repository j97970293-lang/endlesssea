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

data class ExploreRowUi(val title: String, val items: List<SearchItemUi>)

data class ExploreUiState(
    val loading: Boolean = true,
    val rows: List<ExploreRowUi> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val extensionCount: Int = 0,
)

/** Explore (spec §11) : pour chaque extension activée, une rangée de son catalogue principal. */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val registry: ExtensionRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _uiState.value = ExploreUiState(loading = true)
        val extensions = registry.enabledExtensions()
        if (extensions.isEmpty()) {
            _uiState.value = ExploreUiState(loading = false, extensionCount = 0)
            return@launch
        }
        val rows = mutableListOf<ExploreRowUi>()
        val errors = mutableMapOf<String, String>()

        coroutineScope {
            extensions.map { (pkg, ext) ->
                async {
                    runCatching {
                        // « main » : catalogue par défaut — les providers ignorent la catégorie si unique
                        ext.getMainPage(MainPageRequest(category = "main", page = 1))
                    }.onSuccess { page ->
                        if (page.items.isNotEmpty()) {
                            val wrapped = ExploreRowUi(
                                title = "${ext.info.name}" + if (page.hasNextPage) " · suite →" else "",
                                items = page.items.take(20).map {
                                    SearchItemUi(
                                        id = "${ext.info.id}:${it.url}", title = it.title,
                                        posterUrl = it.posterUrl,
                                        subtitle = it.year?.toString() ?: it.type.name,
                                    )
                                },
                            )
                            synchronized(rows) { rows += wrapped }
                        }
                    }.onFailure { e ->
                        synchronized(errors) { errors[ext.info.name] = e.message ?: "erreur" }
                    }
                }
            }.forEach { it.await() }
        }

        _uiState.value = ExploreUiState(
            loading = false,
            rows = rows.sortedBy { it.title },
            errors = errors,
            extensionCount = extensions.size,
        )
    }
}
