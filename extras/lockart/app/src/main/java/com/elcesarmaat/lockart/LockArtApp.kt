package com.elcesarmaat.lockart

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Servicio de LSPosed para la app (preferencias remotas, datos del framework); null si el módulo no está activo. */
object Xposed {
    private val _service = MutableStateFlow<XposedService?>(null)
    val service: StateFlow<XposedService?> = _service

    internal fun bind(s: XposedService) {
        _service.value = s
    }

    internal fun died(s: XposedService) {
        if (_service.value === s) _service.value = null
    }
}

class LockArtApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Solo se puede registrar una vez por proceso, por eso va aquí y no en la actividad.
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) = Xposed.bind(service)
            override fun onServiceDied(service: XposedService) = Xposed.died(service)
        })
    }
}
