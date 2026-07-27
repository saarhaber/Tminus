package com.saarlabs.tminus.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.serialization.json.Json

class EasternTimeInstantTest {

    @Test
    fun comparesByInstant() {
        val earlier = EasternTimeInstant(2026, Month.JULY, 27, 8, 0)
        val later = EasternTimeInstant(2026, Month.JULY, 27, 8, 30)
        assertTrue(earlier < later)
        assertEquals(30.minutes, later - earlier)
    }

    @Test
    fun plusAndMinusShiftLocalTime() {
        val base = EasternTimeInstant(2026, Month.JULY, 27, 23, 50)
        val shifted = base + 20.minutes
        assertEquals(0, shifted.local.hour)
        assertEquals(10, shifted.local.minute)
        assertEquals(LocalDate(2026, 7, 28), shifted.local.date)
        assertEquals(base, shifted - 20.minutes)
    }

    @Test
    fun serviceDateRollsOverAt3am() {
        val lateNight = EasternTimeInstant(2026, Month.JULY, 28, 2, 59)
        assertEquals(LocalDate(2026, 7, 27), lateNight.serviceDate)

        val morning = EasternTimeInstant(2026, Month.JULY, 28, 3, 0)
        assertEquals(LocalDate(2026, 7, 28), morning.serviceDate)
    }

    @Test
    fun equalsIgnoresConstructionPath() {
        val fromLocal = EasternTimeInstant(2026, Month.JANUARY, 5, 12, 0)
        val fromInstant = EasternTimeInstant(fromLocal.instant)
        assertEquals(fromLocal, fromInstant)
        assertEquals(fromLocal.hashCode(), fromInstant.hashCode())
    }

    @Test
    fun serializerRoundTripsWithEasternOffset() {
        val json = Json
        val summer = EasternTimeInstant(2026, Month.JULY, 27, 9, 15)
        val summerEncoded = json.encodeToString(EasternTimeInstant.Serializer, summer)
        assertTrue(summerEncoded.contains("-04:00"), "expected EDT offset in $summerEncoded")
        assertEquals(summer, json.decodeFromString(EasternTimeInstant.Serializer, summerEncoded))

        val winter = EasternTimeInstant(2026, Month.JANUARY, 27, 9, 15)
        val winterEncoded = json.encodeToString(EasternTimeInstant.Serializer, winter)
        assertTrue(winterEncoded.contains("-05:00"), "expected EST offset in $winterEncoded")
        assertEquals(winter, json.decodeFromString(EasternTimeInstant.Serializer, winterEncoded))
    }
}
