package net.harutiro.gitappinstaller.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun newer_patch_is_greater() {
        assertTrue(VersionComparator.compare("1.2.4", "1.2.3") > 0)
        assertTrue(VersionComparator.compare("1.2.3", "1.2.4") < 0)
    }

    @Test
    fun v_prefix_is_ignored() {
        assertEquals(0, VersionComparator.compare("v1.2.3", "1.2.3"))
    }

    @Test
    fun release_is_newer_than_rc() {
        assertTrue(VersionComparator.compare("1.2.3", "1.2.3-rc1") > 0)
    }

    @Test
    fun rc_is_newer_than_beta() {
        assertTrue(VersionComparator.compare("1.2.3-rc1", "1.2.3-beta1") > 0)
    }

    @Test
    fun beta_is_newer_than_alpha() {
        assertTrue(VersionComparator.compare("1.2.3-beta1", "1.2.3-alpha2") > 0)
    }

    @Test
    fun major_bump_outranks_minor() {
        assertTrue(VersionComparator.compare("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun isNewer_returns_true_when_latest_is_greater() {
        assertTrue(VersionComparator.isNewer("1.2.4", "1.2.3"))
    }

    @Test
    fun isNewer_returns_false_when_equal() {
        assertFalse(VersionComparator.isNewer("1.2.3", "1.2.3"))
    }
}
