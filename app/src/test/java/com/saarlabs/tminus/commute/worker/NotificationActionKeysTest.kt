package com.saarlabs.tminus.commute.worker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationActionKeysTest {

    private val now = 1_787_157_000_000L

    @Test
    fun aSnoozeIsDueOnceItsFireTimeHasPassed() {
        val entries = mapOf(snoozeKey("p1", now - 1_000L) to now - 1_000L)
        assertEquals(snoozeKey("p1", now - 1_000L), dueSnoozeKey(entries, "p1", now))
    }

    @Test
    fun aSnoozeInTheFutureIsNotDueYet() {
        val entries = mapOf(snoozeKey("p1", now + 60_000L) to now + 60_000L)
        assertNull(dueSnoozeKey(entries, "p1", now))
    }

    @Test
    fun aSnoozeBelongsToOneProfileOnly() {
        // Two commutes can be alerting at once; snoozing one must not silence the other.
        val entries = mapOf(snoozeKey("p1", now - 1_000L) to now - 1_000L)
        assertNull(dueSnoozeKey(entries, "p2", now))
    }

    @Test
    fun unrelatedMarkersAreNeverMistakenForSnoozes() {
        val entries =
            mapOf(
                "leave_p1_trip9_${now}" to now,
                "acc_watch1_1027253" to now,
                muteKey("p1", now) to now,
            )
        assertNull(dueSnoozeKey(entries, "p1", now))
    }

    @Test
    fun muteKeysAreScopedToBothProfileAndServiceDay() {
        val day2 = now + 24 * 60 * 60 * 1000L
        assertNotEquals(muteKey("a", now), muteKey("b", now))
        assertNotEquals(muteKey("a", now), muteKey("a", day2))
    }

    @Test
    fun actionMarkersSurvivePruningAsLongValues() {
        // The receiver stores a Long, so expiredDeliveryKeys reads the value directly rather than
        // falling back to the key's trailing segment. A Boolean here would expire wrongly.
        val fresh = mapOf(muteKey("p1", now) to now, snoozeKey("p1", now) to now)
        assertTrue(expiredDeliveryKeys(fresh, now).isEmpty())

        val stale = mapOf(muteKey("p1", now) to now - 4L * 24 * 60 * 60 * 1000)
        assertEquals(listOf(muteKey("p1", now)), expiredDeliveryKeys(stale, now))
    }
}
