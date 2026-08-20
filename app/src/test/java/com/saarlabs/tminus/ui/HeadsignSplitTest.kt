package com.saarlabs.tminus.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class HeadsignSplitTest {
    @Test
    fun `plain destination has no qualifier`() {
        assertEquals("Worcester" to null, splitHeadsign("Worcester"))
    }

    @Test
    fun `trailing parenthetical becomes the qualifier`() {
        assertEquals(
            "South Station" to "Express to Boston Landing after Wellesley Farms",
            splitHeadsign("South Station (Express to Boston Landing after Wellesley Farms)"),
        )
    }

    @Test
    fun `parenthetical that is not at the end is left alone`() {
        assertEquals(
            "Ashmont (Shuttle) via JFK" to null,
            splitHeadsign("Ashmont (Shuttle) via JFK"),
        )
    }

    @Test
    fun `unbalanced headsign is left alone`() {
        assertEquals("Forest Hills)" to null, splitHeadsign("Forest Hills)"))
    }

    @Test
    fun `empty halves are left alone`() {
        assertEquals("(Express)" to null, splitHeadsign("(Express)"))
        assertEquals("Readville ()" to null, splitHeadsign("Readville ()"))
    }
}
