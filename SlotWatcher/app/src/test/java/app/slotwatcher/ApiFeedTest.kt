package app.slotwatcher

import app.slotwatcher.data.Format
import app.slotwatcher.data.VoiceLanguage
import app.slotwatcher.net.MojaruClient
import app.slotwatcher.net.ParseSource
import app.slotwatcher.net.SlotParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The panel's slot feed (/teacher/pre-assessments/schedule-slots), shaped like a real response. */
class ApiFeedTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val day = LocalDate.of(2026, 11, 18)

    private fun row(startUtc: String, endUtc: String, status: String, name: String?) = """
        {"programDetails":{"name":"Math Competency Test (MCT) for Abacus"},
         "bookingDetails":${if (name == null) "null" else """{"_id":"b1","studentDetails":{"user_name":"$name","mobile_number":"01*******17"}}"""},
         "slotDetails":{"_id":"s1","startDateTime":"$startUtc","endDateTime":"$endUtc","status":"$status"}}
    """

    private fun feed(booked: Int, totalPages: Int, vararg rows: String) = """
        {"success":true,"data":{"summary":{"totalSlots":18,"totalBookedSlots":$booked,"totalAvailableSlots":14},
         "details":[${rows.joinToString(",")}]},"meta":{"totalCount":18,"page":1,"limit":15,"totalPages":$totalPages}}
    """

    @Test
    fun `booked slots come with Bangladesh times and names`() {
        val p = SlotParser.parseApi(
            feed(
                2, 2,
                row("2026-11-18T12:30:00.000Z", "2026-11-18T12:40:00.000Z", "absent", "Ahnaf"),
                row("2026-11-18T12:40:00.000Z", "2026-11-18T12:50:00.000Z", "booked", "Rukiya Mahmud"),
                row("2026-11-18T12:50:00.000Z", "2026-11-18T13:00:00.000Z", "booked", "সাবা"),
                row("2026-11-18T13:00:00.000Z", "2026-11-18T13:10:00.000Z", "available", null),
            ),
            day, dhaka,
        )
        assertEquals(2, p.totalPages)
        val r = p.result
        assertEquals(ParseSource.Api, r.source)
        assertEquals(18, r.totalCounter)
        assertEquals(2, r.totalBooked)
        assertTrue(r.trustworthy)
        assertEquals(listOf("18:40 - 18:50|Rukiya Mahmud", "18:50 - 19:00|সাবা"), r.bookedSlots.map { it.historyLabel })
        assertEquals(
            "2 slots booked. 6:40 PM, Rukiya Mahmud. 6:50 PM, সাবা.",
            Format.speechBooked(VoiceLanguage.English, r.totalBooked, r.bookedSlots.map { it.start to it.participant }),
        )
    }

    @Test
    fun `a slot just after midnight in Dhaka belongs to the Dhaka date, not the UTC date`() {
        // 2026-11-17T20:00Z is 2:00 AM on 18 Nov in Dhaka.
        val r = SlotParser.parseApi(
            feed(1, 1, row("2026-11-17T20:00:00.000Z", "2026-11-17T20:10:00.000Z", "booked", "X")), day, dhaka,
        ).result
        assertEquals(listOf("02:00 - 02:10"), r.bookedSlots.map { it.label })
    }

    /**
     * Runs a real check against the panel when SW_USER and SW_PASS are set in the environment
     * (never stored in the repo). Skipped otherwise.
     */
    @Test
    fun `live check against the panel`() {
        val user = System.getenv("SW_USER")
        val pass = System.getenv("SW_PASS")
        assumeTrue(!user.isNullOrBlank() && !pass.isNullOrBlank())
        val date = System.getenv("SW_DATE")?.let(LocalDate::parse) ?: LocalDate.now()
        val r = runBlocking { MojaruClient().check(null, user!!, pass!!, null, date) }
        println("LIVE source=${r.source} pages=${r.pagesRead} total=${r.totalCounter} bookedCounter=${r.bookedCounter} rows=${r.slots.size} trustworthy=${r.trustworthy} note=${r.note}")
        r.slots.forEach { println("LIVE  ${it.label}  ${it.status}  ${it.participant ?: "-"}") }
        println("LIVE speech: " + Format.speechBooked(VoiceLanguage.English, r.totalBooked, r.bookedSlots.map { it.start to it.participant }))
        assertEquals(ParseSource.Api, r.source)
        assertTrue(r.trustworthy)
    }
}
