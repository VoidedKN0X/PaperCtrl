package com.vknxworks.paperctrl.ui.organise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundlePollingTest {
    @Test fun backsOffUpToOnceAMinute() {
        assertEquals(4_000L, BundlePolling.delayFor(0))
        assertEquals(8_000L, BundlePolling.delayFor(1))
        assertEquals(16_000L, BundlePolling.delayFor(2))
        assertEquals(32_000L, BundlePolling.delayFor(3))
        assertEquals(60_000L, BundlePolling.delayFor(4))
        assertEquals(60_000L, BundlePolling.delayFor(5))
        assertEquals(60_000L, BundlePolling.delayFor(1_000))
        assertEquals(4_000L, BundlePolling.delayFor(-1))
    }

    @Test fun stopsAfterTenMinutes() {
        assertTrue(BundlePolling.keepGoing(0))
        assertTrue(BundlePolling.keepGoing(9 * 60_000L))
        assertFalse(BundlePolling.keepGoing(10 * 60_000L))
    }
}
