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
            Format.speechBooked(VoiceLanguage.English, 2, listOf("18:30" to null, "19:10" to null)),
        )
        // The screenshot case: three booked rows with participant names.
        assertEquals(
            "3 slots booked. 6:40 PM, Rukiya Mahmud. 6:50 PM, সাবা. 7:00 PM, nur afza.",
            Format.speechBooked(
                VoiceLanguage.English, 3,
                listOf("18:40" to "Rukiya Mahmud", "18:50" to "সাবা", "19:00" to "nur afza"),
            ),
        )
        assertEquals(
            "2 slots booked. 6:40 PM, Rukiya Mahmud. 1 more could not be read.",
            Format.speechBooked(VoiceLanguage.English, 2, listOf("18:40" to "Rukiya Mahmud")),
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
            Format.speechBooked(VoiceLanguage.Bangla, 2, listOf("18:30" to null, "19:10" to null)),
        )
        assertEquals(
            "১টি স্লট বুক হয়েছে। সন্ধ্যা ৬টা ৪০ মিনিট, Rukiya Mahmud।",
            Format.speechBooked(VoiceLanguage.Bangla, 1, listOf("18:40" to "Rukiya Mahmud")),
        )
    }

    @Test
    fun `login is masked`() {
        assertEquals("so•••@gmail.com", maskLogin("someone@gmail.com"))
        assertEquals("017•••••678", maskLogin("01712345678"))
    }
}
