package com.drdisagree.glasslayer.xposed.hook

import android.util.Log
import com.drdisagree.glasslayer.xposed.report.HookReporter
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap

object HookEnv {

    private const val TAG = "GlassLayer"

    lateinit var module: XposedInterface
        private set

    lateinit var classLoader: ClassLoader
        private set

    @Volatile
    var owner: String = ""

    private val classes = ConcurrentHashMap<String, Any>()
    private val missing = Any()

    fun init(module: XposedInterface, classLoader: ClassLoader) {
        this.module = module
        this.classLoader = classLoader
    }

    fun findClass(name: String): Class<*>? {
        val cached = classes.getOrPut(name) {
            try {
                Class.forName(name, false, classLoader)
            } catch (_: Throwable) {
                missing
            }
        }
        return cached as? Class<*>
    }

    fun findClass(vararg names: String): Class<*>? {
        names.forEach { name -> findClass(name)?.let { return it } }
        return null
    }

    fun log(message: String, throwable: Throwable? = null) {
        HookReporter.log(message, throwable)
        try {
            val priority = if (throwable == null) Log.INFO else Log.ERROR
            if (throwable == null) {
                module.log(priority, TAG, message)
            } else {
                module.log(priority, TAG, message, throwable)
            }
        } catch (_: Throwable) {
        }
    }
}
