package com.drdisagree.glasslayer.ui.status

data class StatusStep(
    val title: String,
    val value: String,
    val state: StepState,
    val action: StepAction? = null
)
