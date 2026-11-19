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
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Loads a panel page in a hidden WebView so its JavaScript runs, then reads every page of the
 * slot table.
 *
 * The Pre Assessment List fills in its slot table after the page loads, and splits it into pages
 * of 15 rows ("1 2" below the table). Reading too early gives an empty table; reading only the
 * first page misses bookings on the others.
 */
object PageRenderer {

    /** [pages] = the full HTML of each table page, first page first. */
    data class Rendered(val url: String, val pages: List<String>)

    private data class Snapshot(val ready: Boolean, val rows: Int, val first: String, val html: String)

    /**
     * Page state: idle or not, how many table rows hold a time slot ("18:30 - 18:40"),
     * the first such row's text (to notice a page change), and the full HTML.
     */
    private const val SNAPSHOT_JS = """
        (function () {
          var re = /\d{1,2}:\d{2}\s*[-–—]\s*\d{1,2}:\d{2}/;
          var rows = Array.prototype.filter.call(document.querySelectorAll('tr'), function (tr) {
            return re.test(tr.textContent || '');
          });
          if (!rows.length) {
            // Not a <table>: count the smallest elements holding a time slot instead.
            rows = Array.prototype.filter.call(document.body ? document.body.querySelectorAll('*') : [], function (el) {
              if (!re.test(el.textContent || '')) return false;
              for (var k = 0; k < el.children.length; k++) if (re.test(el.children[k].textContent || '')) return false;
              return true;
            });
          }
          var busy = !!(window.jQuery && window.jQuery.active > 0);
          var p = document.querySelectorAll('.dataTables_processing, [wire\\:loading], .loading, .spinner-border');
          for (var i = 0; i < p.length; i++) {
            var s = window.getComputedStyle(p[i]);
            if (s.display !== 'none' && s.visibility !== 'hidden' && p[i].offsetParent !== null) busy = true;
          }
          return JSON.stringify({
            ready: document.readyState === 'complete' && !busy,
            rows: rows.length,
            first: rows.length ? rows[0].textContent.replace(/\s+/g, ' ').trim() : '',
            html: document.documentElement.outerHTML
          });
        })()
    """

    /**
     * Finds the numbered page buttons of the slot table: inside a .pagination-like element, or
     * else any small numeric link/button that comes after the last table (not the counters above).
     */
    private const val FIND_PAGERS_FN = """
        function swPagers() {
          var isNum = function (el) { return /^\d+$/.test((el.textContent || '').trim()) && !el.closest('table'); };
          var sel = '.pagination a, .pagination button, .pagination li, [class*="paginat"] a, [class*="paginat"] button, ' +
                    'nav[aria-label*="agination"] a, nav[aria-label*="agination"] button';
          var els = Array.prototype.filter.call(document.querySelectorAll(sel), isNum);
          if (!els.length) {
            var tables = document.querySelectorAll('table');
            if (tables.length) {
              var t = tables[tables.length - 1];
              els = Array.prototype.filter.call(document.querySelectorAll('a, button, li, span'), function (el) {
                return isNum(el) && el.children.length === 0 &&
                  (t.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING) &&
                  el.getBoundingClientRect().width < 90;
              });
            }
          }
          return els;
        }
    """

    private const val PAGE_LABELS_JS = """
        (function () {
          $FIND_PAGERS_FN
          var out = [];
          swPagers().forEach(function (el) { var t = el.textContent.trim(); if (out.indexOf(t) < 0) out.push(t); });
          return JSON.stringify(out);
        })()
    """

    private fun clickPageJs(label: String) = """
        (function () {
          $FIND_PAGERS_FN
          var els = swPagers();
          for (var i = 0; i < els.length; i++) {
            if (els[i].textContent.trim() !== '$label') continue;
            var el = els[i];
            if (el.tagName === 'LI' && el.querySelector('a, button')) el = el.querySelector('a, button');
            el.click();
            return 'clicked';
          }
          return 'none';
        })()
    """

    /** If the slot table is a DataTables table, show every row on one page. */
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

    private const val MAX_PAGES = 20

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun render(
        ctx: Context,
        url: String,
        cookies: List<Cookie>,
        wantedDate: String,
        userAgent: String,
    ): Rendered = withContext(Dispatchers.Main) {
        withTimeout(180_000) {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            suspendCancellableCoroutine { c -> cm.removeAllCookies { c.resume(Unit) } }
            val origin = url.toHttpOrigin()
            cookies.forEach { cm.setCookie(origin, it.toString()) }
            cm.flush()

            val web = WebView(ctx.applicationContext)
            try {
                // A WebView that is never shown has a 0×0 size. Give it a phone-sized one, so the
                // page lays out its table the way it does in Chrome.
                val w = 1080
                val h = 2400
                web.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
                )
                web.layout(0, 0, w, h)
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

                var snap = waitForRows(web)
                if (eval(web, setDateJs(wantedDate)) == "set") {
                    delay(800)
                    snap = waitForRows(web, notFirst = snap.first)
                }
                if ((eval(web, SHOW_ALL_ROWS_JS).toIntOrNull() ?: 0) > 0) {
                    delay(800)
                    snap = waitForRows(web)
                }

                val pages = mutableListOf(snap.html)
                val labels = runCatching { JSONArray(eval(web, PAGE_LABELS_JS)) }.getOrNull()
                    ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    .orEmpty()
                    .mapNotNull { it.toIntOrNull() }
                    .filter { it > 1 }
                    .distinct()
                    .sorted()
                    .take(MAX_PAGES - 1)
                for (n in labels) {
                    val before = snap.first
                    if (eval(web, clickPageJs(n.toString())) != "clicked") break
                    delay(600)
                    snap = waitForRows(web, notFirst = before)
                    if (snap.first == before) break // the page didn't change: stop rather than repeat rows
                    pages += snap.html
                }
                Rendered(web.url ?: url, pages)
            } finally {
                web.stopLoading()
                web.destroy()
                cm.removeAllCookies(null)
            }
        }
    }

    /**
     * Polls until the page is idle, its HTML has stopped changing, and the slot rows have loaded
     * (and, after a page click, until the first row is different). Gives up after ~40 s and
     * returns what's there; the parser then decides whether it can be trusted.
     */
    private suspend fun waitForRows(web: WebView, notFirst: String? = null): Snapshot {
        var last: Snapshot? = null
        var stableFor = 0
        repeat(66) { i ->
            delay(600)
            val snap = runCatching {
                val o = JSONObject(eval(web, SNAPSHOT_JS))
                Snapshot(o.optBoolean("ready"), o.optInt("rows"), o.optString("first"), o.optString("html"))
            }.getOrNull() ?: return@repeat
            stableFor = if (snap.ready && snap.html == last?.html) stableFor + 1 else 0
            last = snap
            val rowsReady = snap.rows > 0 && (notFirst == null || snap.first != notFirst)
            if (rowsReady && stableFor >= 2 && i >= 2) return snap
        }
        return last ?: Snapshot(false, 0, "", "")
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
