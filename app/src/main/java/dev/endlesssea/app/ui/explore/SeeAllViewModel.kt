package dev.endlesssea.app.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.ui.search.SearchItemUi
import dev.endlesssea.extensions.api.model.MainPageRequest
import dev.endlesssea.extensions.loader.ExtensionRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeeAllUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val items: List<SearchItemUi> = emptyList(),
    val hasNextPage: Boolean = false,
    val extensionName: String = "",
    val error: String? = null,
)

/**
 * « Tout voir » : pagine le catalogue principal d'une extension (grille infinie).
 */
@HiltViewModel
class SeeAllViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val registry: ExtensionRegistry,
) : ViewModel() {

    val pkg: String = checkNotNull(savedStateHandle["pkg"])

    /** Rangée de catalogue paginée (« main » si l'extension n'en déclare pas). */
    private val category: String = savedStateHandle["category"] ?: "main"

    private val _uiState = MutableStateFlow(SeeAllUiState())
    val uiState: StateFlow<SeeAllUiState> = _uiState

    private var nextPage = 1

    init { loadNext() }

    fun loadNext() = viewModelScope.launch {
        val st = _uiState.value
        if (st.loadingMore || (!st.loading && !st.hasNextPage)) return@launch
        _uiState.value = st.copy(loading = st.items.isEmpty(), loadingMore = true, error = null)
        runCatching {
            val ext = registry.instance(pkg)
            _uiState.value = _uiState.value.copy(extensionName = ext.info.name)
            ext.getMainPage(MainPageRequest(category = category, page = nextPage))
        }.onSuccess { page ->
            val wrapped = page.items.map {
                SearchItemUi(
                    id = "${pkg}:${it.url}", title = it.title,
                    posterUrl = it.posterUrl,
                    subtitle = it.year?.toString() ?: it.type.name,
                    rating = it.rating,
                    audioLangs = it.audioLangs.map { l -> l.name },
                )
            }
            _uiState.value = _uiState.value.copy(
                loading = false, loadingMore = false,
                items = _uiState.value.items + wrapped,
                hasNextPage = page.hasNextPage,
            )
            nextPage++
        }.onFailure { e ->
            _uiState.value = _uiState.value.copy(
                loading = false, loadingMore = false,
                error = e.message ?: "Impossible de charger ce catalogue",
            )
        }
    }
}
