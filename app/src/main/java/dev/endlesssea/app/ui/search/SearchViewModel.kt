package dev.endlesssea.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.core.util.FileNames
import dev.endlesssea.extensions.api.error.SourceException
import dev.endlesssea.extensions.api.model.FilterSet
import dev.endlesssea.extensions.loader.ExtensionRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SearchItemUi> = emptyList(),
    val perExtensionErrors: Map<String, String> = emptyMap(),
)

/**
 * Agrège les recherches de toutes les extensions activées (spec §12) avec débounce,
 * déduplication (titre normalisé + année + type) et erreurs isolées par extension.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val registry: ExtensionRegistry,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(350)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .flatMapLatest { query ->
                    kotlinx.coroutines.flow.flow { emit(runSearch(query)) }
                }
                .collect { (results, errors) ->
                    _uiState.value = _uiState.value.copy(loading = false, results = results, perExtensionErrors = errors)
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query, loading = query.isNotBlank())
        queryFlow.value = query
    }

    /** Requête fan-out sur les extensions actives, en parallèle, erreurs isolées. */
    private suspend fun runSearch(query: String): Pair<List<SearchItemUi>, Map<String, String>> = coroutineScope {
        val results = mutableListOf<dev.endlesssea.extensions.api.model.SearchItem>()
        val errors = mutableMapOf<String, String>()

        registry.enabledExtensions().map { (name, ext) ->
            async {
                runCatching { ext.search(query, page = 1, filters = FilterSet()) }
                    .onSuccess { page -> synchronized(results) { results += page.items } }
                    .onFailure { e ->
                        val msg = if (e is SourceException) e.toUserMessage() else "Erreur inconnue"
                        synchronized(errors) { errors[name] = msg }
                    }
            }
        }.forEach { it.await() }
        // items sorted implicitly by arrival per-extension; dedup happens in deduped()
        results.deduped() to errors
    }

    private fun List<dev.endlesssea.extensions.api.model.SearchItem>.deduped(): List<SearchItemUi> =
        distinctBy { "${FileNames.normalizedKey(it.title)}#${it.year}#${it.type}" }
            .map { SearchItemUi(it.id, it.title, it.posterUrl, subtitle = it.type.name) }

    private fun SourceException.toUserMessage(): String = when (this) {
        is SourceException.SourceUnavailable -> "Source indisponible"
        SourceException.NoResults -> "Aucun résultat"
        is SourceException.CaptchaRequired -> "Vérification requise"
        is SourceException.NetworkError -> "Connexion impossible"
        is SourceException.RateLimited -> "Trop de requêtes"
        else -> message ?: "Erreur inconnue"
    }
}
