package com.drdisagree.glasslayer.xposed.hook

import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import java.lang.reflect.Executable
import java.lang.reflect.Method

fun Class<*>.findMethod(name: String, predicate: (Method) -> Boolean = { true }): Method? {
    var current: Class<*>? = this
    while (current != null) {
        current.declaredMethods
            .firstOrNull { it.name == name && predicate(it) }
            ?.let { method ->
                method.isAccessible = true
                return method
            }
        current = current.superclass
    }
    return null
}

fun Executable.intercept(hooker: (Chain) -> Any?): HookHandle? = try {
    val owner = HookEnv.owner
    val method = "${declaringClass.simpleName}.$name"
    val tag = if (owner.isEmpty()) method else "$owner:$method"
    HookEnv.module.hook(this)
        .setExceptionMode(ExceptionMode.PROTECTIVE)
        .intercept { chain ->
            if (Guard.isBroken(tag)) return@intercept chain.proceed()
            try {
                hooker(chain).also { Guard.succeed(tag) }
            } catch (throwable: Throwable) {
                Guard.fail(tag, throwable)
                throw throwable
            }
        }
} catch (throwable: Throwable) {
    HookEnv.log("Hook failed: $this", throwable)
    null
}

fun Class<*>?.hookMatching(pattern: Regex, hooker: (Chain) -> Any?): Int {
    val type = this ?: return 0
    return type.declaredMethods
        .filter { pattern.matches(it.name) }
        .count { method -> method.intercept(hooker) != null }
}

fun Class<*>?.hookAll(name: String, hooker: (Chain) -> Any?): Int =
    hookMatching(Regex.fromLiteral(name), hooker)

fun Class<*>?.hookAllAfter(name: String, block: (Chain, Any?) -> Unit): Int =
    hookMatchingAfter(Regex.fromLiteral(name), block)

fun Class<*>?.hookMatchingAfter(pattern: Regex, block: (Chain, Any?) -> Unit): Int =
    hookMatching(pattern) { chain ->
        val result = chain.proceed()
        block(chain, result)
        result
    }

fun Class<*>?.hookAllBefore(name: String, block: (Chain) -> Unit): Int =
    hookMatchingBefore(Regex.fromLiteral(name), block)

fun Class<*>?.hookMatchingBefore(pattern: Regex, block: (Chain) -> Unit): Int =
    hookMatching(pattern) { chain ->
        block(chain)
        chain.proceed()
    }

fun Chain.proceedWithArgs(block: (Array<Any?>) -> Unit): Any? {
    val values = args.toTypedArray<Any?>()
    block(values)
    return proceed(values)
}
