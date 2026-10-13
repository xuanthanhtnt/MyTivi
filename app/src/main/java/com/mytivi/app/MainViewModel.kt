package com.mytivi.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val cacheFile = File(app.filesDir, "playlist.m3u")
    private var refreshJob: Job? = null
    private var epgJob: Job? = null
    private var generation = 0

    var channels by mutableStateOf<List<Channel>>(emptyList())
        private set
    var epg by mutableStateOf<Map<String, List<Programme>>>(emptyMap())
        private set
    var favorites by mutableStateOf(prefs.favorites)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val playlistUrl: String get() = prefs.playlistUrl
    val epgUrl: String get() = prefs.epgUrl
    var lastChannelUrl: String
        get() = prefs.lastChannelUrl
        set(value) { prefs.lastChannelUrl = value }

    init {
        if (prefs.playlistUrl.isNotBlank()) {
            viewModelScope.launch {
                // 1) Hiện ngay danh sách đã lưu lần trước, 2) rồi cập nhật từ mạng
                if (cacheFile.exists()) {
                    runCatching {
                        channels = withContext(Dispatchers.IO) {
                            parseText(cacheFile.readText(), prefs.playlistUrl).channels
                        }
                    }
                }
                refresh()
            }
        }
    }

    fun savePlaylist(url: String, epgUrl: String) {
        prefs.playlistUrl = url
        prefs.epgUrl = epgUrl
        channels = emptyList()
        epg = emptyMap()
        refresh()
    }

    fun refresh() {
        val url = prefs.playlistUrl
        if (url.isBlank()) return
        val gen = ++generation
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading = true
            error = null
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val text = Http.getString(url)
                    cacheFile.writeText(text)
                    parseText(text, url)
                }
                channels = parsed.channels
                if (parsed.channels.isEmpty()) error = "Playlist không có kênh nào."
                loadEpg(prefs.epgUrl.ifBlank { parsed.epgUrl.orEmpty() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Không tải được playlist: ${e.message}"
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    private fun loadEpg(url: String) {
        if (url.isBlank()) return
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            try {
                epg = withContext(Dispatchers.IO) {
                    Http.open(url).use { EpgParser.parse(it, System.currentTimeMillis()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // EPG lỗi thì bỏ qua, vẫn xem kênh bình thường
            }
        }
    }

    fun toggleFavorite(ch: Channel) {
        val updated = if (ch.url in favorites) favorites - ch.url else favorites + ch.url
        favorites = updated
        prefs.favorites = updated
    }

    /** Nếu link là một luồng HLS đơn (không phải danh sách kênh) thì coi như 1 kênh. */
    private fun parseText(text: String, url: String): ParsedPlaylist {
        val parsed = M3uParser.parse(text)
        if (parsed.channels.isEmpty() && text.contains("#EXT-X-")) {
            return ParsedPlaylist(listOf(Channel("", "Luồng trực tiếp", url, "", "Khác")), null)
        }
        return parsed
    }
}
