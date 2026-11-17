package app.slotwatcher.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Mojaru refused the email/mobile or password. Retrying won't help. */
class LoginException(message: String) : Exception(message)

/** The page didn't look like we expected (layout changed, link missing). */
class LayoutException(message: String) : Exception(message)

/**
 * Talks to the teacher panel the same way Chrome does: load the login form, post it with its
 * CSRF token, then open the Pre Assessment List. One instance = one fresh login (cookies in memory).
 */
class MojaruClient(private val base: String = BASE) {

    companion object {
        const val BASE = "https://teacher.mojaru.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0 Mobile Safari/537.36"
    }

    private val cookieStore = mutableMapOf<String, Cookie>()

    private val http = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(cookieStore) {
                    cookies.forEach { cookieStore["${it.name}|${it.domain}|${it.path}"] = it }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookieStore) {
                val now = System.currentTimeMillis()
                cookieStore.values.filter { it.expiresAt > now && it.matches(url) }
            }
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml")

    private fun execute(req: Request): Document {
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Mojaru answered HTTP ${resp.code}")
            val finalUrl = resp.request.url.toString()
            return Jsoup.parse(resp.body?.string().orEmpty(), finalUrl)
        }
    }

    private fun get(url: String): Document = execute(request(url).get().build())

    private fun post(url: String, fields: Map<String, String>): Document {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return execute(request(url).post(body).build())
    }

    /** Logs in and returns the page Mojaru lands on (the dashboard). */
    fun login(user: String, password: String): Document {
        val loginPage = get("$base/teacher/login")
        val form = loginPage.selectFirst("form#kt_sign_in_form")
            ?: loginPage.selectFirst("form:has(input[type=password])")
            ?: throw LayoutException("Couldn't find the login form")

        val fields = linkedMapOf<String, String>()
        form.select("input[type=hidden][name]").forEach { fields[it.attr("name")] = it.attr("value") }
        val userField = form.selectFirst("input[name=email_or_mobile]")?.attr("name")
            ?: form.selectFirst("input[type=text][name], input[type=email][name]")?.attr("name")
            ?: "email_or_mobile"
        val passField = form.selectFirst("input[type=password][name]")?.attr("name") ?: "password"
        fields[userField] = user
        fields[passField] = password

        val action = form.absUrl("action").ifBlank { "$base/teacher/post/login" }
        val landing = post(action, fields)

        val stillOnLogin = landing.location().contains("/login") ||
            landing.selectFirst("input[type=password]") != null
        if (stillOnLogin) {
            val msg = landing.select(".alert, .invalid-feedback, .text-danger").text().trim()
            throw LoginException(msg.ifBlank { "Mojaru rejected the email/mobile or password" })
        }
        return landing
    }

    /** The Pre Assessment List link from the side menu, as an absolute URL. */
    fun findListUrl(landing: Document): String? {
        val links = landing.select("a[href]").filter { a ->
            val h = a.attr("href").lowercase()
            "pre-assessment" in h || "pre_assessment" in h || "preassessment" in h
        }
        val best = links.firstOrNull { it.text().contains("list", ignoreCase = true) } ?: links.firstOrNull()
        return best?.absUrl("href")?.takeIf { it.isNotBlank() }
    }

    /** Full check: log in, open the list for [date], read it. */
    fun check(user: String, password: String, listUrlOverride: String?, date: LocalDate): ParseResult {
        val landing = login(user, password)
        val listUrl = listUrlOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: findListUrl(landing)
            ?: throw LayoutException("Couldn't find the Pre Assessment List link after login")

        var doc = get(listUrl)
        val wanted = date.format(SlotParser.DMY_FORMAT)
        val dateInput = SlotParser.findDateInput(doc)
        if (dateInput != null && dateInput.attr("value").trim() != wanted) {
            dateUrl(doc, dateInput, wanted)?.let { doc = get(it) }
        }
        return SlotParser.parse(doc, date)
    }

    /**
     * Builds the URL that shows another date: submits the date box's GET form if it has one,
     * otherwise adds the box's name as a query parameter.
     */
    private fun dateUrl(doc: Document, input: Element, wanted: String): String? {
        val name = input.attr("name").ifBlank { return null }
        val form = input.closest("form")
        if (form != null && form.attr("method").ifBlank { "get" }.equals("get", ignoreCase = true)) {
            val action = form.absUrl("action").ifBlank { doc.location() }
            val url = action.toHttpUrlOrNull()?.newBuilder() ?: return null
            form.select("input[name], select[name]").forEach { f ->
                val v = if (f == input) wanted else f.`val`()
                url.setQueryParameter(f.attr("name"), v)
            }
            return url.build().toString()
        }
        return doc.location().toHttpUrlOrNull()?.newBuilder()
            ?.setQueryParameter(name, wanted)?.build()?.toString()
    }
}
