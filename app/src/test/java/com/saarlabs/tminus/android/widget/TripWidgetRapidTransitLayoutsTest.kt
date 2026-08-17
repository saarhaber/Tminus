package com.saarlabs.tminus.android.widget

import com.saarlabs.tminus.android.widget.TripWidgetRapidTransitLayouts.TripLayout
import com.saarlabs.tminus.android.widget.TripWidgetRapidTransitLayouts.selectLayout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sizes are the dp a launcher reports for common cell spans on a phone, which is what actually
 * decides which of the three trip layouts a user sees.
 */
class TripWidgetRapidTransitLayoutsTest {

    @Test
    fun defaultFiveByFourCellsGetsTheLargeLayout() {
        assertEquals(TripLayout.LARGE, selectLayout(widthDp = 300f, heightDp = 230f))
    }

    @Test
    fun wideButShortDropsToTheSingleRowLayout() {
        // Two grid rows on a dense phone: too short to stack a header, countdown and stop pair.
        assertEquals(TripLayout.INLINE, selectLayout(widthDp = 276f, heightDp = 120f))
    }

    @Test
    fun aTallerWideWidgetIsLarge() {
        assertEquals(TripLayout.LARGE, selectLayout(widthDp = 276f, heightDp = 150f))
    }

    @Test
    fun twoByTwoSquareFallsBackToCompact() {
        assertEquals(TripLayout.COMPACT, selectLayout(widthDp = 130f, heightDp = 130f))
    }

    @Test
    fun tallAndNarrowIsCompactRatherThanLarge() {
        assertEquals(TripLayout.COMPACT, selectLayout(widthDp = 150f, heightDp = 300f))
    }

    @Test
    fun oneRowStripIsInlineEvenWhenWide() {
        assertEquals(TripLayout.INLINE, selectLayout(widthDp = 300f, heightDp = 60f))
    }

    @Test
    fun heightWinsOverWidthForTheShortestSizes() {
        // A narrow strip has no room for the stacked countdown either, so height is checked first.
        assertEquals(TripLayout.INLINE, selectLayout(widthDp = 120f, heightDp = 60f))
    }
}
