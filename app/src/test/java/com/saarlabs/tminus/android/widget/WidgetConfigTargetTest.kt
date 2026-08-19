package com.saarlabs.tminus.android.widget

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The cases here are the ones that actually produced a widget stuck on "tap to set up": a launcher
 * that omits the id, plus a stale pending id left behind by an earlier unconfigured widget.
 */
class WidgetConfigTargetTest {

    private fun configured(vararg ids: Int): (Int) -> Boolean = { it in ids.toSet() }

    @Test
    fun theLaunchersOwnIdAlwaysWins() {
        val id =
            resolveConfigTargetId(
                intentId = 7,
                pendingId = 3,
                placedIds = listOf(3, 7),
                isConfigured = configured(),
            )
        assertEquals(7, id)
    }

    @Test
    fun fallsBackToThePendingIdWhenTheLauncherOmitsIt() {
        val id =
            resolveConfigTargetId(
                intentId = INVALID_WIDGET_ID,
                pendingId = 3,
                placedIds = listOf(3),
                isConfigured = configured(),
            )
        assertEquals(3, id)
    }

    @Test
    fun aPendingIdThatIsAlreadyConfiguredIsNotReused() {
        // Widget 3 was set up earlier and left its id behind; widget 8 is the one being placed now.
        val id =
            resolveConfigTargetId(
                intentId = INVALID_WIDGET_ID,
                pendingId = 3,
                placedIds = listOf(3, 8),
                isConfigured = configured(3),
            )
        assertEquals(8, id)
    }

    @Test
    fun aPendingIdForAWidgetTheUserHasRemovedIsIgnored() {
        val id =
            resolveConfigTargetId(
                intentId = INVALID_WIDGET_ID,
                pendingId = 99,
                placedIds = listOf(4),
                isConfigured = configured(),
            )
        assertEquals(4, id)
    }

    @Test
    fun theSingleUnconfiguredWidgetIsUsedWithNoPendingIdAtAll() {
        val id =
            resolveConfigTargetId(
                intentId = INVALID_WIDGET_ID,
                pendingId = INVALID_WIDGET_ID,
                placedIds = listOf(1, 2, 5),
                isConfigured = configured(1, 2),
            )
        assertEquals(5, id)
    }

    @Test
    fun twoUnconfiguredWidgetsAndNoIdIsAmbiguousRatherThanAGuess() {
        // Guessing here would configure the wrong widget, which is the bug being fixed.
        val id =
            resolveConfigTargetId(
                intentId = INVALID_WIDGET_ID,
                pendingId = INVALID_WIDGET_ID,
                placedIds = listOf(1, 2),
                isConfigured = configured(),
            )
        assertEquals(INVALID_WIDGET_ID, id)
    }

    @Test
    fun nothingPlacedResolvesToNothing() {
        val id =
            resolveConfigTargetId(
                intentId = INVALID_WIDGET_ID,
                pendingId = INVALID_WIDGET_ID,
                placedIds = emptyList(),
                isConfigured = configured(),
            )
        assertEquals(INVALID_WIDGET_ID, id)
    }
}
