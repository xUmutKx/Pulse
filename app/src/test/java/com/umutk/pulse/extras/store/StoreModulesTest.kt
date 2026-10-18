package com.umutk.pulse.extras.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreModulesTest {

    @Test
    fun versions_compare_part_by_part() {
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertTrue(compareVersions("1.8.4", "1.8.4.1") < 0)
    }

    @Test
    fun sha256_matches_the_known_vector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc".toByteArray()))
    }
}
