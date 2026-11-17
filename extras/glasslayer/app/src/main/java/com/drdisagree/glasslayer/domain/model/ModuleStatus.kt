package com.drdisagree.glasslayer.domain.model

data class ModuleStatus(
    val frameworkActive: Boolean = false,
    val restartNeeded: Boolean = false,
    val safeMode: Boolean = false
)
