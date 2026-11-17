package com.drdisagree.glasslayer.xposed.hook

import java.util.WeakHashMap

private val extras = WeakHashMap<Any, MutableMap<String, Any?>>()

fun Any?.extra(name: String): Any? {
    val target = this ?: return null
    return synchronized(extras) { extras[target]?.get(name) }
}

fun Any?.setExtra(name: String, value: Any?) {
    val target = this ?: return
    synchronized(extras) {
        if (value == null) {
            extras[target]?.remove(name)
        } else {
            extras.getOrPut(target) { HashMap() }[name] = value
        }
    }
}
