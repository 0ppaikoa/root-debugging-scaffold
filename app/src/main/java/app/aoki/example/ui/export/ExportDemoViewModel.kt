package app.aoki.example.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.aoki.example.debug.DebugExporter
import app.aoki.example.debug.ExportResult
import app.aoki.example.debug.Journal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportDemoUiState(
    val running: Boolean = false,
    val lastResult: String = "",
)

class ExportDemoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExportDemoUiState())
    val uiState: StateFlow<ExportDemoUiState> = _uiState.asStateFlow()

    fun runExport() {
        if (_uiState.value.running) return
        _uiState.value = ExportDemoUiState(running = true)
        viewModelScope.launch {
            val result: ExportResult = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                Journal.append(app, "export started")
                val r = DebugExporter.export(app)
                Journal.append(app, "export finished file=" + r.zipFile.name + " download=" + r.downloadCopyStatus)
                r
            }
            val summary = buildString {
                appendLine("file: " + result.zipFile.name)
                appendLine("download copy: " + result.downloadCopyStatus)
                for (s in result.sections) {
                    appendLine(s.section.id + ": " + s.status.label + " (" + s.bytes + " bytes)")
                }
            }
            _uiState.value = ExportDemoUiState(running = false, lastResult = summary)
        }
    }
}
