package app.aoki.example.ui.detail

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetailUiState(
    val itemId: String,
)

class DetailViewModel(itemId: String) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState(itemId = itemId))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
}
