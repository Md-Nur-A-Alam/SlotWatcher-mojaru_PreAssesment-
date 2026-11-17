package app.slotwatcher

import app.slotwatcher.check.CheckTimes
import app.slotwatcher.check.WatchSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CheckTimesTest {

    private val day = LocalDate.of(2026, 11, 18)
    private val example = WatchSession(day, LocalTime.of(18, 25), LocalTime.of(21, 15), 10)

    @Test
    fun `user's example gives 18 checks from 6_25 to 9_15`() {
        val t = CheckTimes.of(example)
        assertEquals(18, t.size)
        assertEquals(day.atTime(18, 25), t.first())
        assertEquals(day.atTime(18, 35), t[1])
        assertEquals(day.atTime(21, 5), t[t.size - 2])
        assertEquals(day.atTime(21, 15), t.last())
    }

    @Test
    fun `end not on a gap step stops at the last step before it`() {
        val t = CheckTimes.of(example.copy(end = LocalTime.of(21, 20)))
        assertEquals(day.atTime(21, 15), t.last())
    }

    @Test
    fun `next check skips times already passed`() {
        val now = day.atTime(19, 2)
        assertEquals(day.atTime(19, 5), CheckTimes.nextAfter(example, now))
        assertEquals(day.atTime(18, 35), CheckTimes.nextAfter(example, day.atTime(18, 25)))
        assertNull(CheckTimes.nextAfter(example, day.atTime(21, 15)))
    }

    @Test
    fun validation() {
        val before = LocalDateTime.of(2026, 11, 18, 12, 0)
        assertNull(CheckTimes.validate(example, before))
        assertNotNull(CheckTimes.validate(example.copy(gapMinutes = 0), before))
        assertNotNull(CheckTimes.validate(example.copy(end = LocalTime.of(18, 0)), before))
        assertNotNull(CheckTimes.validate(example, LocalDateTime.of(2026, 11, 19, 12, 0)))
        assertNotNull(CheckTimes.validate(example, day.atTime(21, 30)))
    }
}
