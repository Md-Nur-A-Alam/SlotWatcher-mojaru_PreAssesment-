package app.slotwatcher.net

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Loads a panel page in a hidden WebView so its JavaScript runs.
 *
 * The Pre Assessment List fills in its "At a Glance" counters and the slot table with
 * JavaScript after the page loads. The raw HTML from the server has 0 counters and an empty
 * table, so reading it directly reports "no slot booked" even when slots are booked.
 */
object PageRenderer {

    data class Rendered(val url: String, val html: String)

    /** Snapshot: is the page idle (no jQuery/DataTables requests running), and its full HTML. */
    private const val SNAPSHOT_JS = """
        (function () {
          var busy = !!(window.jQuery && window.jQuery.active > 0);
          var p = document.querySelectorAll('.dataTables_processing');
          for (var i = 0; i < p.length; i++) {
            var s = window.getComputedStyle(p[i]);
            if (s.display !== 'none' && s.visibility !== 'hidden') busy = true;
          }
          return JSON.stringify({
            ready: document.readyState === 'complete' && !busy,
            html: document.documentElement.outerHTML
          });
        })()
    """

    /** If the slot table is paged (DataTables), show every row on one page. */
    private const val SHOW_ALL_ROWS_JS = """
        (function () {
          var n = 0;
          try {
            if (window.jQuery && jQuery.fn.dataTable) {
              jQuery('table').each(function () {
                if (jQuery.fn.dataTable.isDataTable(this)) {
                  var t = jQuery(this).DataTable();
                  if (t.page.info().pages > 1) { t.page.len(1000).draw(); n++; }
                }
              });
            }
          } catch (e) {}
          return n;
        })()
    """

    /** Puts [wanted] (dd-MM-yyyy) into the page's date box the way a person would. */
    private fun setDateJs(wanted: String) = """
        (function () {
          var inputs = document.querySelectorAll('input');
          for (var i = 0; i < inputs.length; i++) {
            var el = inputs[i];
            if (!/^\d{2}-\d{2}-\d{4}$/.test((el.value || '').trim())) continue;
            if (el.value.trim() === '$wanted') return 'same';
            if (el._flatpickr) { el._flatpickr.setDate('$wanted', true, 'd-m-Y'); }
            el.value = '$wanted';
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
            if (window.jQuery) { jQuery(el).trigger('change'); }
            return 'set';
          }
          return 'none';
        })()
    """

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun render(
        ctx: Context,
        url: String,
        cookies: List<Cookie>,
        wantedDate: String,
        userAgent: String,
    ): Rendered = withContext(Dispatchers.Main) {
        withTimeout(60_000) {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            suspendCancellableCoroutine { c -> cm.removeAllCookies { c.resume(Unit) } }
            val origin = url.toHttpOrigin()
            cookies.forEach { cm.setCookie(origin, it.toString()) }
            cm.flush()

            val web = WebView(ctx.applicationContext)
            try {
                web.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    userAgentString = userAgent
                }
                val loaded = CompletableDeferred<Unit>()
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, u: String?) {
                        loaded.complete(Unit)
                    }
                }
                web.loadUrl(url)
                loaded.await()

                var html = waitUntilIdle(web)
                if (eval(web, setDateJs(wantedDate)) == "set") {
                    delay(800)
                    html = waitUntilIdle(web)
                }
                if ((eval(web, SHOW_ALL_ROWS_JS).toIntOrNull() ?: 0) > 0) {
                    delay(800)
                    html = waitUntilIdle(web)
                }
                Rendered(web.url ?: url, html)
            } finally {
                web.stopLoading()
                web.destroy()
                cm.removeAllCookies(null)
            }
        }
    }

    /** Polls until the page is idle and its HTML has stopped changing (max ~20 s). */
    private suspend fun waitUntilIdle(web: WebView): String {
        var last = ""
        var stableFor = 0
        repeat(34) { i ->
            delay(600)
            val snap = runCatching { JSONObject(eval(web, SNAPSHOT_JS)) }.getOrNull() ?: return@repeat
            val html = snap.optString("html")
            stableFor = if (snap.optBoolean("ready") && html == last) stableFor + 1 else 0
            last = html
            if (stableFor >= 2 && i >= 3) return html
        }
        return last
    }

    /** Runs JavaScript and returns its result as a plain string. */
    private suspend fun eval(web: WebView, js: String): String =
        suspendCancellableCoroutine { c ->
            web.evaluateJavascript(js) { raw ->
                val value = runCatching {
                    when (val v = JSONTokener(raw ?: "null").nextValue()) {
                        is String -> v
                        JSONObject.NULL -> ""
                        else -> v.toString()
                    }
                }.getOrDefault(raw.orEmpty())
                c.resume(value)
            }
        }

    private fun String.toHttpOrigin(): String {
        val i = indexOf('/', indexOf("://") + 3)
        return if (i < 0) this else substring(0, i)
    }
}
