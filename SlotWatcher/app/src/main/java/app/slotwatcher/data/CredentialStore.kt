package app.slotwatcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Credentials(
    val login: String,
    val password: String,
    /** Optional: the Pre Assessment List URL, if auto-detection from the menu fails. */
    val listUrl: String?,
)

/** The Mojaru login, encrypted with a key held in the Android Keystore (NFR-5). */
object CredentialStore {

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences = prefs ?: synchronized(this) {
        prefs ?: run {
            val app = ctx.applicationContext
            val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                app,
                "credentials",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ).also { prefs = it }
        }
    }

    fun get(ctx: Context): Credentials? {
        val p = prefs(ctx)
        val login = p.getString("login", null) ?: return null
        val password = p.getString("password", null) ?: return null
        return Credentials(login, password, p.getString("listUrl", null))
    }

    fun save(ctx: Context, c: Credentials) {
        prefs(ctx).edit()
            .putString("login", c.login)
            .putString("password", c.password)
            .putString("listUrl", c.listUrl?.trim()?.ifEmpty { null })
            .apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
