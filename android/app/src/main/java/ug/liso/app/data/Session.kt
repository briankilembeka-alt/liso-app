package ug.liso.app.data

import android.content.Context

/** Keeps the sign-in token on the phone. Cleared on sign-out or when the server rejects it. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("liso_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().apply { if (value == null) remove("token") else putString("token", value) }.apply()

    val signedIn: Boolean get() = token != null
}
