package com.drdisagree.glasslayer.xposed.hook

import com.drdisagree.glasslayer.xposed.report.HookReporter
import java.util.concurrent.ConcurrentHashMap

object Guard {

    private const val LIMIT = 20

    private val failures = ConcurrentHashMap<String, Int>()
    private val reported: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun isBroken(tag: String): Boolean = (failures[tag] ?: 0) >= LIMIT

    inline fun <T> run(tag: String, fallback: T, block: () -> T): T {
        if (isBroken(tag)) return fallback
        return try {
            block().also { succeed(tag) }
        } catch (throwable: Throwable) {
            fail(tag, throwable)
            fallback
        }
    }

    fun succeed(tag: String) {
        if (failures.isNotEmpty()) failures.remove(tag)
    }

    fun fail(tag: String, throwable: Throwable) {
        try {
            val count = failures.merge(tag, 1) { old, new -> old + new } ?: 1
            val signature = "$tag|${throwable.javaClass.name}|${throwable.stackTrace.firstOrNull()}"
            if (reported.add(signature)) HookEnv.log("$tag failed (attempt $count)", throwable)
            if (count == LIMIT) {
                HookEnv.log("$tag disabled after $LIMIT consecutive failures", throwable)
                HookReporter.broken(tag)
            }
        } catch (_: Throwable) {
        }
    }
}
