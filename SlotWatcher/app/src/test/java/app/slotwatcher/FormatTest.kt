package app.slotwatcher

import app.slotwatcher.data.Format
import app.slotwatcher.data.VoiceLanguage
import app.slotwatcher.ui.maskLogin
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class FormatTest {

    @Test
    fun `english announcements`() {
        assertEquals(
            "2 slots booked: 6:30 PM and 7:10 PM.",
            Format.speechBooked(VoiceLanguage.English, 2, listOf("18:30", "19:10")),
        )
        assertEquals(
            "No slot booked. Next check at 6:45 PM.",
            Format.speechNoneBooked(VoiceLanguage.English, LocalDateTime.of(2026, 11, 18, 18, 45)),
        )
        assertEquals("No slot booked. That was the last check.", Format.speechNoneBooked(VoiceLanguage.English, null))
    }

    @Test
    fun `bangla announcements`() {
        assertEquals("সন্ধ্যা ৬টা ৩০ মিনিট", Format.bnTime(LocalTime.of(18, 30)))
        assertEquals("রাত ৯টা", Format.bnTime(LocalTime.of(21, 0)))
        assertEquals(
            "২টি স্লট বুক হয়েছে। সময়: সন্ধ্যা ৬টা ৩০ মিনিট এবং সন্ধ্যা ৭টা ১০ মিনিট।",
            Format.speechBooked(VoiceLanguage.Bangla, 2, listOf("18:30", "19:10")),
        )
    }

    @Test
    fun `login is masked`() {
        assertEquals("so•••@gmail.com", maskLogin("someone@gmail.com"))
        assertEquals("017•••••678", maskLogin("01712345678"))
    }
}
