package com.drdisagree.glasslayer.domain.model

data class FrameworkInfo(
    val name: String,
    val version: String,
    val apiVersion: Int,
    val scope: List<String>
)
