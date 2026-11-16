package com.umutk.pulse.extras.ytx

/*
 * Small helpers that newer Compose has and Pulse's Compose (1.7) has only for Double: the YTXTube code calls them on Float and Int.
 */
internal fun Float.fastCoerceIn(minimumValue: Float, maximumValue: Float): Float = coerceIn(minimumValue, maximumValue)
internal fun Int.fastCoerceIn(minimumValue: Int, maximumValue: Int): Int = coerceIn(minimumValue, maximumValue)
internal fun Double.fastCoerceIn(minimumValue: Double, maximumValue: Double): Double = coerceIn(minimumValue, maximumValue)
