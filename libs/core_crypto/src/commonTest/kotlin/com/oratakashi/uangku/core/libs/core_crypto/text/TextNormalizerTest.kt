package com.oratakashi.uangku.core.libs.core_crypto.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Runs on Android host AND iOS simulator. The most valuable test in the suite: a broken
 * iOS normalizer would lock out every non-ASCII password permanently, and it would never
 * show up outside real users.
 *
 * Code points are written explicitly so the source-file encoding can't silently change
 * which normalization form each literal holds.
 */
class TextNormalizerTest {

    private val nfc = "café"        // café, precomposed (é = U+00E9)
    private val nfd = "café"       // café, decomposed (e + combining acute U+0301)

    @Test
    fun nfd_and_nfc_inputs_normalize_to_the_same_bytes() {
        assertNotEquals(nfc, nfd, "sanity: the two encodings differ before normalization")
        assertEquals(nfc.normalizeNfc(), nfd.normalizeNfc(), "NFC and NFD must converge after normalizeNfc")
    }

    @Test
    fun normalizeNfc_yields_the_precomposed_form() {
        assertEquals(nfc, nfd.normalizeNfc())
        assertEquals(nfc, nfc.normalizeNfc())
    }

    @Test
    fun ascii_is_unchanged() {
        assertEquals("CorrectHorse123!", "CorrectHorse123!".normalizeNfc())
    }
}
