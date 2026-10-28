package ru.flobsterable.cosplay2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.flobsterable.cosplay2.feature.update.isNewerVersion
import ru.flobsterable.cosplay2.model.compareAppVersions

class AppVersionComparisonTest {
    @Test
    fun stableInstallDoesNotOfferSameVersionPrerelease() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0-rc.1"))
    }

    @Test
    fun prereleaseInstallOffersSameVersionStableRelease() {
        assertTrue(isNewerVersion("1.0.0-rc.1", "1.0.0"))
    }

    @Test
    fun prereleaseIdentifiersFollowNumericAndLexicalPrecedence() {
        val versions = listOf(
            "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta",
            "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0"
        )
        versions.zipWithNext().forEach { (older, newer) ->
            assertTrue(compareAppVersions(older, newer) < 0, "$older < $newer")
            assertTrue(compareAppVersions(newer, older) > 0, "$newer > $older")
        }
    }

    @Test
    fun numericVersionsAndPrefixRemainCompatible() {
        assertTrue(isNewerVersion("v1.9.9", "v1.10.0"))
        assertTrue(isNewerVersion("1.0.0", "1.0.1-rc.1"))
        assertFalse(isNewerVersion("2.0.0", "1.99.99"))
        assertEquals(0, compareAppVersions("v1.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun buildMetadataDoesNotChangePrecedence() {
        assertEquals(0, compareAppVersions("v1.0.0+build.1", "1.0.0+build.2"))
        assertEquals(0, compareAppVersions("1.0.0-rc.1+build.1", "1.0.0-rc.1+build.2"))
    }
}
