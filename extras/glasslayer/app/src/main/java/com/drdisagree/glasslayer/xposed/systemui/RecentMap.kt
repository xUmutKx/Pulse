package com.drdisagree.glasslayer.xposed.systemui

internal class RecentMap<K, V>(private val limit: Int) : LinkedHashMap<K, V>(limit, LOAD_FACTOR, true) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > limit

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
