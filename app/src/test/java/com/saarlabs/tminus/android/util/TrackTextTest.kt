package com.saarlabs.tminus.android.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Track codes come straight from MBTA's `platform_code`, which is absent for every mode except
 * commuter rail and occasionally present but empty.
 */
class TrackTextTest {

    @Test
    fun bothEndsOfATripKeepTheirOwnTrack() {
        assertEquals(listOf("2", "5"), trackCodes("2", "5"))
    }

    @Test
    fun oneTrackIsNotPaddedWithTheMissingEnd() {
        assertEquals(listOf("2"), trackCodes("2", null))
        assertEquals(listOf("5"), trackCodes(null, "5"))
    }

    @Test
    fun aSubwayTripHasNoTrackAtAll() {
        assertEquals(emptyList<String>(), trackCodes(null, null))
    }

    @Test
    fun blankPlatformCodesAreDropped() {
        // The API returns "" for some stops rather than omitting the field.
        assertEquals(emptyList<String>(), trackCodes("", "   "))
        assertEquals(listOf("3"), trackCodes("  ", "3"))
    }

    @Test
    fun departingAndArrivingOnTheSameTrackIsShownOnce() {
        assertEquals(listOf("2"), trackCodes("2", "2"))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals(listOf("2"), trackCodes(" 2 "))
    }
}
