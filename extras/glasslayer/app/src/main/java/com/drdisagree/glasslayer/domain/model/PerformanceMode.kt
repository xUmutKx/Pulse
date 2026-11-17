package com.drdisagree.glasslayer.domain.model

enum class PerformanceMode(val maxFps: Int) {
    FULL(0),
    BALANCED(60),
    SAVER(30),
    ULTRA(15)
}
