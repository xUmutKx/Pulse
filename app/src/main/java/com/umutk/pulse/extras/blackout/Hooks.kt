package com.umutk.pulse.extras.blackout

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

/** What a hook sees. The same shape for the legacy API and the modern one, so the hook code is written once. */
interface HookParam {
    val thisObject: Any?
    val args: Array<Any?>
    val method: Executable
    var result: Any?
    var throwable: Throwable?
}

/** One callback. Override what you need, like the legacy XC_MethodHook. */
open class Hook {
    open fun beforeHookedMethod(param: HookParam) {}
    open fun afterHookedMethod(param: HookParam) {}
}

/**
 * One entry point for hooks, used by both the legacy LSPosed API and the modern libxposed API. [modern] is set by the modern entry
 * class; when it is null the legacy API is used. Only one of them ever installs hooks in a process (see [ModuleCore]).
 */
object Hooks {
    /** Kept for the reports; this build only has the legacy entry, so it is always null. */
    val modern: Any? = null

    fun log(msg: String) {
        try { XposedBridge.log(msg) } catch (_: Throwable) { Log.i("BlackOut", msg) }
    }

    fun find(name: String): Class<*>? = try { Class.forName(name) } catch (_: Throwable) { null }

    /** Every method called [name] declared in [cls] (the legacy hookAllMethods). */
    fun all(cls: Class<*>, name: String, h: Hook) {
        for (m in cls.declaredMethods) if (m.name == name) hookExecutable(m, h)
    }

    /** Every constructor of [cls] (the legacy hookAllConstructors). */
    fun ctors(cls: Class<*>, h: Hook) {
        for (c in cls.declaredConstructors) hookExecutable(c, h)
    }

    /** One method by name and parameter types; the last argument is the [Hook] (the legacy findAndHookMethod shape). */
    fun one(cls: Class<*>, name: String, vararg rest: Any) {
        val h = rest.last() as Hook
        val types = rest.dropLast(1).map { it as Class<*> }.toTypedArray()
        val m = findExact(cls, name, types) ?: throw NoSuchMethodException("$cls.$name")
        hookExecutable(m, h)
    }

    /** Same as [one], for a class found by name in the app's own class loader (used for BlackOut's own status check). */
    fun oneIn(loader: ClassLoader?, className: String, name: String, h: Hook) {
        val cls = Class.forName(className, false, loader)
        for (m in cls.declaredMethods) if (m.name == name) hookExecutable(m, h)
    }

    /** The method itself, or the first one with the same name and types up the class chain (what the legacy helper did). */
    private fun findExact(cls: Class<*>, name: String, types: Array<Class<*>>): Method? {
        var k: Class<*>? = cls
        while (k != null) {
            try { return k.getDeclaredMethod(name, *types) } catch (_: NoSuchMethodException) { }
            k = k.superclass
        }
        return null
    }

    private fun hookExecutable(e: Executable, h: Hook) {
        XposedBridge.hookMethod(e, LegacyHook(h))
    }
}

private class LegacyParam(private val p: XC_MethodHook.MethodHookParam) : HookParam {
    override val thisObject: Any? get() = p.thisObject
    override val args: Array<Any?> get() = p.args
    override val method: Executable get() = p.method as Executable
    override var result: Any?
        get() = p.result
        set(v) { p.result = v }
    // shortcut: the legacy stub has no throwable accessors and no hook here reads one, so it is always null on the legacy side, upgrade with the real API jar
    override var throwable: Throwable?
        get() = null
        set(v) { }
}

private class LegacyHook(private val h: Hook) : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) { h.beforeHookedMethod(LegacyParam(param)) }
    override fun afterHookedMethod(param: MethodHookParam) { h.afterHookedMethod(LegacyParam(param)) }
}
