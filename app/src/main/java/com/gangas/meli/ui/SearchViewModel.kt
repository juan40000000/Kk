package com.gangas.meli.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gangas.core.model.Deal
import com.gangas.core.model.Risk
import com.gangas.meli.GangasApp
import com.gangas.meli.data.ScanOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortBy(val label: String) { SCORE("Score"), DISCOUNT("% bajo ref."), PRICE("Precio") }

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val outcome: ScanOutcome? = null,
    val error: String? = null,
    val sortBy: SortBy = SortBy.SCORE,
    val hideHighRisk: Boolean = false,
    val onlyAboveThreshold: Boolean = true,
) {
    fun visibleDeals(minScore: Int): List<Deal> {
        val all = outcome?.analysis?.deals.orEmpty()
            .filter { !onlyAboveThreshold || it.score >= minScore }
            .filter { !hideHighRisk || it.risk != Risk.HIGH }
        return when (sortBy) {
            SortBy.SCORE -> all.sortedByDescending { it.score }
            SortBy.DISCOUNT -> all.sortedByDescending { it.belowReference }
            SortBy.PRICE -> all.sortedBy { it.listing.price }
        }
    }
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    val container = (app as GangasApp).container
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state
    private var job: Job? = null

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setSort(s: SortBy) = _state.update { it.copy(sortBy = s) }
    fun toggleHideRisk() = _state.update { it.copy(hideHighRisk = !it.hideHighRisk) }
    fun toggleThreshold() = _state.update { it.copy(onlyAboveThreshold = !it.onlyAboveThreshold) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 2) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val s = container.settings.state.value
            try {
                val out = container.scanner.scan(s.site, q, s.pages, s.mode)
                val err = if (out.fetched == 0) {
                    "No se obtuvieron publicaciones. " + (out.errors.firstOrNull()?.let { "($it) " } ?: "") +
                        "Mercado Libre puede exigir credenciales de API: configuralas en Ajustes."
                } else null
                _state.update { it.copy(loading = false, outcome = out, error = err) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Error") }
            }
        }
    }
}
