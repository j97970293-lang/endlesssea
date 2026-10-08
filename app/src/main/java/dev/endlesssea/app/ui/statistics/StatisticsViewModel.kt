package dev.endlesssea.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.tracking.TrackerRepository
import dev.endlesssea.data.db.EpisodeDao
import dev.endlesssea.data.db.MediaDao
import dev.endlesssea.data.db.WatchHistoryDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val trackerRepository: TrackerRepository,
    private val historyDao: WatchHistoryDao,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsState(loading = true))
    val uiState: StateFlow<StatisticsState> = _uiState.asStateFlow()
    private var statisticsJob: Job? = null

    init { refresh() }

    fun refresh() {
        statisticsJob?.cancel()
        statisticsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                // Both Room flows are long-lived: sequential collect calls never reach the UI update.
                combine(trackerRepository.links, historyDao.observeAll()) { links, history ->
                    val byService = links.groupBy { it.service }.mapValues { (_, entries) ->
                        ServiceStats(entries.sumOf { it.progress }, entries.size)
                    }
                    val recent = history.sortedByDescending { it.updatedAt }.take(10).map { entry ->
                        val media = mediaDao.byId(entry.mediaId)
                        val episode = episodeDao.byId(entry.episodeId)
                        RecentActivity(
                            title = media?.customTitle ?: media?.title ?: episode?.title ?: entry.mediaId,
                            episodeNumber = episode?.number?.toInt() ?: 0,
                            timestamp = entry.updatedAt,
                        )
                    }
                    StatisticsState(
                        totalEpisodesWatched = history.count { it.watched },
                        totalSeriesTracked = byService.values.sumOf { it.seriesTracked },
                        totalWatchTimeMs = history.sumOf { it.positionMs.coerceAtLeast(0) },
                        statsByService = byService,
                        recentActivity = recent,
                    )
                }.collect { _uiState.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false, error = "Erreur lors du chargement : ${e.message}",
                )
            }
        }
    }
}
