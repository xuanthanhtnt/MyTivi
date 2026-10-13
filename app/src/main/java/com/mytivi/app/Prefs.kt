package com.mytivi.app

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mytivi", Context.MODE_PRIVATE)

    var playlistUrl: String
        get() = sp.getString("playlist_url", "") ?: ""
        set(v) { sp.edit().putString("playlist_url", v).apply() }

    var epgUrl: String
        get() = sp.getString("epg_url", "") ?: ""
        set(v) { sp.edit().putString("epg_url", v).apply() }

    var lastChannelUrl: String
        get() = sp.getString("last_channel", "") ?: ""
        set(v) { sp.edit().putString("last_channel", v).apply() }

    var favorites: Set<String>
        get() = HashSet(sp.getStringSet("favorites", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("favorites", HashSet(v)).apply() }
}
