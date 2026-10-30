package com.umutk.pulse.extras.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDictionaryTest {

    @Test
    fun swap_needs_exactly_two_neighbouring_letters_exchanged() {
        assertTrue(KeyboardDictionary.isSwap("teh", "the"))
        assertTrue(KeyboardDictionary.isSwap("kitpa", "kitap"))
        assertFalse(KeyboardDictionary.isSwap("the", "the"))
        assertFalse(KeyboardDictionary.isSwap("tha", "the"))
        assertFalse(KeyboardDictionary.isSwap("abc", "cba"))
        assertFalse(KeyboardDictionary.isSwap("ab", "abc"))
    }

    @Test
    fun one_slip_is_similar_and_a_different_word_is_not() {
        assertTrue(KeyboardDictionary.similar("helo", "hello"))
        assertTrue(KeyboardDictionary.similar("teh", "the"))
        assertFalse(KeyboardDictionary.similar("hello", "world"))
        assertFalse(KeyboardDictionary.similar("a".repeat(31), "a".repeat(31)))
    }

    @Test
    fun loose_similarity_ignores_very_short_words() {
        assertFalse(KeyboardDictionary.similarLoose("ab", "ac"))
        assertFalse(KeyboardDictionary.similarLoose("hello", "world"))
    }
}
