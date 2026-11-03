package app.aoki.example.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RootDemoUiState(
    val running: Boolean = false,
    val lastResult: String = "",
)

class RootDemoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RootDemoUiState())
    val uiState: StateFlow<RootDemoUiState> = _uiState.asStateFlow()

    fun runTest() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(running = true)
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                val granted = Shell.isAppGrantedRoot()?.toString() ?: "unknown"
                val r = Shell.cmd("id").exec()
                buildString {
                    appendLine("granted: $granted")
                    appendLine("success: " + r.isSuccess)
                    appendLine("exit code: " + r.code)
                    appendLine("out: " + r.out.joinToString("\n"))
                    appendLine("err: " + r.err.joinToString("\n"))
                }
            }
            _uiState.value = _uiState.value.copy(running = false, lastResult = summary)
        }
    }
}
