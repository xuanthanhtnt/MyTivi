@file:OptIn(UnstableApi::class)

package com.mytivi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Màn hình chính: nhóm kênh | danh sách kênh | khung xem trước (giống TiviMate). */
@Composable
fun BrowseScreen(
    vm: MainViewModel,
    onPlay: (List<Channel>, Int) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val channels = vm.channels
    val favorites = vm.favorites
    val epg = vm.epg
    val now = rememberNow()

    var sel by remember { mutableIntStateOf(1) }          // 0 = Yêu thích, 1 = Tất cả, 2.. = nhóm
    var query by remember { mutableStateOf("") }
    val groups = remember(channels) { channels.map { it.group }.distinct() }
    val labels = remember(groups) { listOf("★ Yêu thích", "Tất cả") + groups }
    val shown = remember(channels, favorites, groups, sel, query) {
        val base = when (sel) {
            0 -> channels.filter { it.url in favorites }
            1 -> channels
            else -> groups.getOrNull(sel - 2)?.let { g -> channels.filter { it.group == g } } ?: channels
        }
        if (query.isBlank()) base else base.filter { it.name.contains(query, ignoreCase = true) }
    }

    // ---- Xem trước: phát im lặng, độ phân giải thấp, kênh nào đang được chọn ----
    var previewChannel by remember { mutableStateOf<Channel?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }
    val previewPlayer = remember {
        buildPlayer(context).apply {
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(854, 480)    // nhẹ cho box yếu, kênh HLS sẽ tự chọn luồng thấp
                .build()
        }
    }
    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                previewLoading = state == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                previewError = true
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()      // vào xem toàn màn hình là giải phóng ngay
        }
    }
    LaunchedEffect(previewChannel?.url) {
        val ch = previewChannel ?: return@LaunchedEffect
        previewError = false
        previewPlayer.stop()
        previewPlayer.clearMediaItems()
        delay(700)                        // lướt nhanh qua nhiều kênh thì không tải từng kênh
        previewPlayer.setMediaItem(MediaItem.fromUri(ch.url))
        previewPlayer.prepare()
        previewPlayer.play()
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MyTivi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val updated = if (vm.lastUpdated > 0) " · cập nhật ${hhmm(vm.lastUpdated)}" else ""
            Text("   ${channels.size} kênh$updated", color = Dim)
            Spacer(Modifier.weight(1f))
            if (vm.loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            TvButton("Làm mới") { vm.refresh() }
            TvButton("Cài đặt", onSettings)
        }
        vm.error?.let { Text(it, color = ErrorRed) }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(Modifier.width(240.dp).fillMaxHeight()) {
                itemsIndexed(labels) { i, label ->
                    GroupItem(
                        label = label,
                        selected = i == sel,
                        modifier = if (i == 1) Modifier.focusRequester(firstFocus) else Modifier,
                    ) { sel = i }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Tìm kênh…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (shown.isEmpty() && !vm.loading) {
                    Text(
                        if (sel == 0) "Chưa có kênh yêu thích. Bấm ☆ bên phải kênh để thêm." else "Không có kênh nào.",
                        color = Dim,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(shown) { i, ch ->
                        ChannelRow(
                            number = i + 1,
                            ch = ch,
                            favorite = ch.url in favorites,
                            now = now,
                            programme = nowPlaying(epg, ch, now),
                            onFocus = { previewChannel = ch },
                            onPlay = { onPlay(shown, i) },
                            onFavorite = { vm.toggleFavorite(ch) },
                        )
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            PreviewPanel(
                exo = previewPlayer,
                channel = previewChannel,
                loading = previewLoading,
                error = previewError,
                epg = epg,
                now = now,
                modifier = Modifier.width(380.dp),
            )
        }
    }
}

@Composable
private fun PreviewPanel(
    exo: ExoPlayer,
    channel: Channel?,
    loading: Boolean,
    error: Boolean,
    epg: Map<String, List<Programme>>,
    now: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        isFocusable = false
                        this.player = exo
                    }
                },
            )
            when {
                channel == null -> Text(
                    "Chọn một kênh để xem trước",
                    color = Dim,
                    modifier = Modifier.align(Alignment.Center),
                )
                error -> Text(
                    "Không xem trước được kênh này",
                    color = ErrorRed,
                    modifier = Modifier.align(Alignment.Center),
                )
                loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    strokeWidth = 3.dp,
                )
                else -> Unit
            }
        }
        if (channel != null) {
            Spacer(Modifier.height(10.dp))
            Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            val cur = nowPlaying(epg, channel, now)
            if (cur != null) {
                Text(cur.title, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                LinearProgressIndicator(
                    progress = { progressOf(cur, now) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
            nextPlaying(epg, channel, now)?.let {
                Text("Tiếp theo: ${hhmm(it.start)}  ${it.title}", color = Dim, maxLines = 1, fontSize = 14.sp)
            }
            Text("Bấm OK để xem toàn màn hình", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Mục nhóm: di chuyển focus tới đâu thì danh sách kênh đổi theo tới đó. */
@Composable
fun GroupItem(label: String, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (selected) Accent else Color.White,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .background(if (focused) FocusBg else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
fun ChannelRow(
    number: Int,
    ch: Channel,
    favorite: Boolean,
    now: Long,
    programme: Programme?,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onFocus() }   // chọn tới kênh nào thì khung xem trước chạy kênh đó
                .tvFocusable(onPlay)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$number", color = Dim, modifier = Modifier.width(48.dp))
            AsyncImage(
                model = ch.logo.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(ch.name, maxLines = 1, fontSize = 18.sp)
                if (programme != null) {
                    Text(programme.title, color = Dim, maxLines = 1, fontSize = 14.sp)
                    LinearProgressIndicator(
                        progress = { progressOf(programme, now) },
                        modifier = Modifier.fillMaxWidth(0.6f).padding(top = 4.dp),
                    )
                }
            }
        }
        Text(
            if (favorite) "★" else "☆",
            color = Gold,
            fontSize = 24.sp,
            modifier = Modifier.tvFocusable(onFavorite).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
