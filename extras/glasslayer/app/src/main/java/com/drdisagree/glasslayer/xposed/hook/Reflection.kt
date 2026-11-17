package com.drdisagree.glasslayer.xposed.hook

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

private val missing = Any()

private val fieldCache = object : ClassValue<ConcurrentHashMap<String, Any>>() {
    override fun computeValue(type: Class<*>): ConcurrentHashMap<String, Any> = ConcurrentHashMap()
}

private val methodCache = object : ClassValue<ConcurrentHashMap<String, Any>>() {
    override fun computeValue(type: Class<*>): ConcurrentHashMap<String, Any> = ConcurrentHashMap()
}

fun Class<*>.findField(name: String): Field? {
    val cached = fieldCache.get(this).getOrPut(name) { lookupField(name) ?: missing }
    return cached as? Field
}

private fun Class<*>.lookupField(name: String): Field? {
    var current: Class<*>? = this
    while (current != null) {
        try {
            return current.getDeclaredField(name).apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            current = current.superclass
        }
    }
    return null
}

fun Any?.field(name: String): Any? = try {
    this?.javaClass?.findField(name)?.get(this)
} catch (_: Throwable) {
    null
}

fun Any?.anyField(vararg names: String): Any? {
    names.forEach { name -> field(name)?.let { return it } }
    return null
}

fun Any?.setField(name: String, value: Any?) {
    val target = this ?: return
    runCatching { target.javaClass.findField(name)?.set(target, value) }
}

fun Any?.call(name: String, vararg args: Any?): Any? {
    val target = this ?: return null
    return try {
        target.javaClass.findCallable(name, args)?.invoke(target, *args)
    } catch (_: Throwable) {
        null
    }
}

fun Class<*>?.callStatic(name: String, vararg args: Any?): Any? {
    val type = this ?: return null
    return try {
        type.findCallable(name, args)?.invoke(null, *args)
    } catch (_: Throwable) {
        null
    }
}

private fun Class<*>.findCallable(name: String, args: Array<out Any?>): Method? {
    val key = if (args.isEmpty()) name else name + args.joinToString(prefix = "(", postfix = ")") { it?.javaClass?.name ?: "null" }
    val cached = methodCache.get(this).getOrPut(key) {
        findMethod(name) { method ->
            method.parameterTypes.size == args.size &&
                    method.parameterTypes.withIndex().all { (index, type) -> type.accepts(args[index]) }
        } ?: missing
    }
    return cached as? Method
}

private fun Class<*>.accepts(value: Any?): Boolean {
    if (value == null) return !isPrimitive
    if (isInstance(value)) return true
    return when (name) {
        "boolean" -> value is Boolean
        "int" -> value is Int
        "long" -> value is Long
        "float" -> value is Float
        "double" -> value is Double
        "short" -> value is Short
        "byte" -> value is Byte
        "char" -> value is Char
        else -> false
    }
}
