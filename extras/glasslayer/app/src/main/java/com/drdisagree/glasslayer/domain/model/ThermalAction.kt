package com.drdisagree.glasslayer.domain.model

enum class ThermalAction(val mode: PerformanceMode?) {
    NONE(null),
    BALANCED(PerformanceMode.BALANCED),
    SAVER(PerformanceMode.SAVER),
    ULTRA(PerformanceMode.ULTRA),
    PAUSE(null)
}
