package com.drdisagree.glasslayer.domain.model

enum class BatterySaverAction(val code: Int, val mode: PerformanceMode?) {
    NONE(0, null),
    BALANCED(3, PerformanceMode.BALANCED),
    SAVER(4, PerformanceMode.SAVER),
    ULTRA(1, PerformanceMode.ULTRA),
    PAUSE(2, null)
}
