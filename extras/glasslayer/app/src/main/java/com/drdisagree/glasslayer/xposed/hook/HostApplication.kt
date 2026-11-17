package com.drdisagree.glasslayer.xposed.hook

import android.annotation.SuppressLint
import android.app.Application

object HostApplication {

    private const val ACTIVITY_THREAD = "android.app.ActivityThread"
    private const val CURRENT_APPLICATION = "currentApplication"

    @SuppressLint("PrivateApi")
    fun get(): Application? = try {
        Class.forName(ACTIVITY_THREAD).getMethod(CURRENT_APPLICATION).invoke(null) as? Application
    } catch (_: Throwable) {
        null
    }
}
