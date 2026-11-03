package app.aoki.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aoki.example.module.ModuleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeItem(
    val id: String,
    val title: String,
    val summary: String,
)

data class HomeUiState(
    val items: List<HomeItem> = emptyList(),
    val frameworkLine: String = "Framework: not connected",
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            items = listOf(
                HomeItem("demo-alpha", "Demo Alpha", "Placeholder destination A"),
                HomeItem("demo-beta", "Demo Beta", "Placeholder destination B"),
                HomeItem("demo-gamma", "Demo Gamma", "Placeholder destination C"),
                HomeItem("debug-export", "Debug Package", "Create a debug package"),
                HomeItem("root-demo", "Root Demo", "Run a command as root"),
            ),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ModuleService.service.collect { service ->
                _uiState.value = _uiState.value.copy(
                    frameworkLine = if (service == null) {
                        "Framework: not connected"
                    } else {
                        "Framework: " + service.frameworkName + " " + service.frameworkVersion
                    },
                )
            }
        }
    }
}
