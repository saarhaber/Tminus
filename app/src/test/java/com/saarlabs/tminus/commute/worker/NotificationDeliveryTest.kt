package com.saarlabs.tminus.commute.worker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationDeliveryTest {

    private val now = 1_770_000_000_000L // 2026-02-01, well past the plausibility floor
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun aMarkerDeliveredTodayIsKept() {
        assertEquals(emptyList<String>(), expiredDeliveryKeys(mapOf("leave_1_t9_x" to now), now))
    }

    @Test
    fun aTripMarkerFromLastWeekIsPruned() {
        val key = "leave_1_t9_x"
        assertEquals(listOf(key), expiredDeliveryKeys(mapOf(key to now - 7 * day), now))
    }

    @Test
    fun anAlertMarkerSurvivesLongerThanATripMarker() {
        // The bug this pins: an elevator outage lasting a fortnight must not re-notify because a
        // trip-length retention expired its marker.
        assertEquals(
            emptyList<String>(),
            expiredDeliveryKeys(mapOf("acc_3_521190" to now - 10 * day), now),
        )
        assertEquals(
            listOf("acc_3_521190"),
            expiredDeliveryKeys(mapOf("acc_3_521190" to now - 40 * day), now),
        )
    }

    @Test
    fun aLegacyTripMarkerStillExpiresOnTheEpochInItsKey() {
        val fresh = "leave_1_t9_${now - day}"
        val stale = "leave_1_t9_${now - 7 * day}"
        assertEquals(listOf(stale), expiredDeliveryKeys(mapOf(fresh to true, stale to true), now))
    }

    @Test
    fun aLegacyAlertMarkerIsDroppedOnceRatherThanReadAsA1970Timestamp() {
        // "acc_3_521190" ends in an MBTA alert id, not an epoch. Treating it as one is what made
        // every accessibility marker look expired the moment it was written.
        assertEquals(
            listOf("acc_3_521190"),
            expiredDeliveryKeys(mapOf("acc_3_521190" to true), now),
        )
    }

    @Test
    fun retentionIsLongerForAlertsThanForTrips() {
        assertTrue(deliveryRetentionMs("acc_1_2") > deliveryRetentionMs("leave_1_2_3"))
    }

    @Test
    fun routeColoursParseWithAndWithoutTheHash() {
        assertEquals(0xFFDA291C.toInt(), argbFromHexOrNull("#DA291C"))
        assertEquals(0xFFDA291C.toInt(), argbFromHexOrNull("DA291C"))
        assertEquals(0xFFDA291C.toInt(), argbFromHexOrNull("  da291c  "))
    }

    @Test
    fun anEightDigitColourKeepsItsOwnAlpha() {
        assertEquals(0x80DA291C.toInt(), argbFromHexOrNull("80DA291C"))
    }

    @Test
    fun aMissingOrUnusableColourFallsBackToNull() {
        // The API returns "" for some routes, and an unknown route id yields no colour at all.
        assertNull(argbFromHexOrNull(null))
        assertNull(argbFromHexOrNull(""))
        assertNull(argbFromHexOrNull("red"))
        assertNull(argbFromHexOrNull("#FFF"))
    }
}
