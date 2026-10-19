package app.lull.data

import android.content.Context

/** Plain preferences shared by the screens and the playback service. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** High quality on Wi-Fi (Premium accounts get the best stream YouTube offers). */
    var wifiHighQuality: Boolean
        get() = prefs.getBoolean("wifi_high", true)
        set(v) = prefs.edit().putBoolean("wifi_high", v).apply()

    var mobileHighQuality: Boolean
        get() = prefs.getBoolean("mobile_high", false)
        set(v) = prefs.edit().putBoolean("mobile_high", v).apply()

    var downloadHighQuality: Boolean
        get() = prefs.getBoolean("download_high", true)
        set(v) = prefs.edit().putBoolean("download_high", v).apply()

    var downloadWifiOnly: Boolean
        get() = prefs.getBoolean("download_wifi_only", true)
        set(v) = prefs.edit().putBoolean("download_wifi_only", v).apply()

    /** Off by default: playback stops when the queue ends. */
    var radioAfterQueue: Boolean
        get() = prefs.getBoolean("radio_after_queue", false)
        set(v) = prefs.edit().putBoolean("radio_after_queue", v).apply()

    var sleepDefaultMinutes: Int
        get() = prefs.getInt("sleep_default", 30)
        set(v) = prefs.edit().putInt("sleep_default", v).apply()
}
