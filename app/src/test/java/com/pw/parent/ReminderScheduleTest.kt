package com.pw.parent

import com.pw.parent.utils.ReminderScheduler
import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM checks for the reminder cadence (no Android framework involved), so CI can catch a
 * broken schedule before it ships.
 */
class ReminderScheduleTest {

    private fun calendarOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }

    @Test
    fun nextSlotIsAlwaysOnMondayAndWithinOneWeek() {
        val now = System.currentTimeMillis()
        val next = ReminderScheduler.nextSlotTime(now)

        assertTrue("next slot must be in the future", next > now)
        assertTrue(
            "next slot must be within 8 days",
            next <= now + TimeUnit.DAYS.toMillis(8)
        )
        assertEquals(Calendar.MONDAY, calendarOf(next).get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun nextSlotHourIsOneOfTheConfiguredSlots() {
        val next = ReminderScheduler.nextSlotTime(System.currentTimeMillis())
        val hour = calendarOf(next).get(Calendar.HOUR_OF_DAY)

        assertTrue(
            "hour $hour must be one of ${ReminderScheduler.SLOT_HOURS.toList()}",
            ReminderScheduler.SLOT_HOURS.contains(hour)
        )
        assertEquals(0, calendarOf(next).get(Calendar.MINUTE))
    }

    @Test
    fun slotCadenceCoversSixAmToSixPmEveryTwoHours() {
        assertEquals(listOf(6, 8, 10, 12, 14, 16, 18), ReminderScheduler.SLOT_HOURS.toList())
    }

    @Test
    fun slotAfterSixPmRollsOverToNextMondayMorning() {
        // Build "Monday 19:00" of the current week and ask for the next slot.
        val mondayEvening = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val next = calendarOf(ReminderScheduler.nextSlotTime(mondayEvening))
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
        assertEquals(6, next.get(Calendar.HOUR_OF_DAY))
        assertTrue("must be the following week", next.timeInMillis > mondayEvening + TimeUnit.DAYS.toMillis(6))
    }
}
