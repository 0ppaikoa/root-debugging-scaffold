package app.aoki.example.module

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ModuleService : XposedServiceHelper.OnServiceListener {

    private val _service = MutableStateFlow<XposedService?>(null)
    val service: StateFlow<XposedService?> = _service.asStateFlow()

    @Volatile
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        _service.value = service
    }

    override fun onServiceDied(service: XposedService) {
        if (_service.value === service) {
            _service.value = null
        }
    }
}
