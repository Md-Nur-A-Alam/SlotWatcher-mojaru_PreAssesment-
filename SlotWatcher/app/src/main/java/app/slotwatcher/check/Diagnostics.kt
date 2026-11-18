package app.slotwatcher.check

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.slotwatcher.net.ParseResult
import java.io.File
import java.time.LocalDateTime

/**
 * Keeps a copy of the last page a check read, so it can be shared from Settings when the app's
 * result doesn't match the panel. Stored in the app's private cache; only shared on request.
 */
object Diagnostics {

    private fun file(ctx: Context) = File(File(ctx.cacheDir, "diagnostics").apply { mkdirs() }, "last-page.html")

    fun saveLastPage(ctx: Context, html: String, r: ParseResult) {
        val header = "<!-- Slot Watcher diagnostics · ${LocalDateTime.now()} · source=${r.source} · " +
            "rows=${r.slots.size} booked rows=${r.bookedSlots.size} booked counter=${r.bookedCounter} " +
            "total counter=${r.totalCounter} page date=${r.shownDate} -->\n"
        runCatching { file(ctx).writeText(header + html) }
    }

    fun hasLastPage(ctx: Context): Boolean = file(ctx).exists()

    /** A share sheet for the saved page, or null if there isn't one yet. */
    fun shareIntent(ctx: Context): Intent? {
        val f = file(ctx).takeIf { it.exists() } ?: return null
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/html")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Slot Watcher: last checked page")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share last checked page")
    }
}
