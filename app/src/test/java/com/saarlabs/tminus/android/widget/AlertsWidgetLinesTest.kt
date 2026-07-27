package com.saarlabs.tminus.android.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class AlertsWidgetLinesTest {

    @Test
    fun emptySelectionMeansEveryLine() {
        assertEquals(
            "Red,Orange,Blue,Green-B,Green-C,Green-D,Green-E,Mattapan",
            AlertsWidgetLines.filterFor(emptyList()),
        )
    }

    @Test
    fun unknownIdsAreIgnored() {
        assertEquals(
            "Red,Orange,Blue,Green-B,Green-C,Green-D,Green-E,Mattapan",
            AlertsWidgetLines.filterFor(listOf("Purple")),
        )
    }

    @Test
    fun subsetKeepsCanonicalOrderAndExpandsGreenBranches() {
        assertEquals(
            "Red,Green-B,Green-C,Green-D,Green-E",
            AlertsWidgetLines.filterFor(listOf("Green", "Red")),
        )
    }

    @Test
    fun singleLine() {
        assertEquals("Mattapan", AlertsWidgetLines.filterFor(listOf("Mattapan")))
    }
}
