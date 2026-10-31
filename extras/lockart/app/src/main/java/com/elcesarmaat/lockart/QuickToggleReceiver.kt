package com.elcesarmaat.lockart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Guarda el cambio que hizo el interruptor del bloqueo. SystemUI ya lo aplicó en memoria, pero las preferencias
 * remotas son de solo lectura para él: aquí se escriben en las locales y, en cuanto LSPosed entrega su servicio
 * (puede tardar un momento si la app arrancó solo por este aviso), también en las remotas.
 */
class QuickToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.hasExtra(Config.K_AOD_LYRICS)) return
        val value = intent.getBooleanExtra(Config.K_AOD_LYRICS, false)
        val cfg = Config()
        val prefs = context.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        cfg.load(prefs)
        cfg.aodLyrics = value
        cfg.save(prefs)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val service = withTimeoutOrNull(WAIT_MS) { Xposed.service.filterNotNull().first() }
                if (service != null) cfg.save(service.getRemotePreferences(Config.PREFS))
                else Log.w("LockArt", "Sin servicio de LSPosed: el cambio se subirá al abrir la app")
            } catch (t: Throwable) {
                Log.w("LockArt", "No se pudo guardar el interruptor en las preferencias remotas", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val WAIT_MS = 5000L
    }
}
