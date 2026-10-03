package com.zinmedia.photoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutputCleanupTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun deletesOnlyFilesOlderThanMaxAge() {
        val now = 10_000_000L
        val old = temp.newFile("old.jpg").apply { setLastModified(now - 5_000) }
        val fresh = temp.newFile("fresh.jpg").apply { setLastModified(now - 500) }

        assertEquals(1, deleteFilesOlderThan(temp.root, maxAgeMs = 1_000, now = now))
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }
}
