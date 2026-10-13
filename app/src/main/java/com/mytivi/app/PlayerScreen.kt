@file:OptIn(UnstableApi::class)

package com.mytivi.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Xem kênh toàn màn hình.
 *  ▲ / CH+  : kênh kế tiếp        ▼ / CH-  : kênh trước đó
 *  OK / ◄ ► : mở danh sách kênh   MENU     : thêm/bỏ yêu thích
 *  BACK     : đóng danh sách, hoặc quay về màn hình chính
 */
@Composable
fun PlayerScreen(
    list: List<Channel>,
    startIndex: Int,
    favorites: Set<String>,
    epg: Map<String, List<Programme>>,
    onToggleFavorite: (Channel) -> Unit,
    onPlayed: (Channel) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val now = rememberNow()
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, list.lastIndex)) }
    var showList by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    val rootFocus = remember { FocusRequester() }
    val current = list[index]

    val exo = remember { buildPlayer(context) }
    PauseWhenBackground(exo)

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Không phát được kênh này (${e.errorCodeName})"
                retry++
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Đổi kênh: chờ 250ms để bấm liên tục không làm server bị gọi dồn dập
    LaunchedEffect(current.url) {
        error = null
        delay(250)
        exo.setMediaItem(MediaItem.fromUri(current.url))
        exo.prepare()
        onPlayed(current)
    }

    // Lỗi mạng / mất luồng: tự thử lại sau 5 giây
    LaunchedEffect(retry) {
        if (retry > 0) {
            delay(5_000)
            if (error != null) exo.prepare()
        }
    }

    LaunchedEffect(index, infoTick) {
        showInfo = true
        delay(4_000)
        showInfo = false
    }

    BackHandler { if (showList) showList = false else onClose() }
    LaunchedEffect(showList) { if (!showList) runCatching { rootFocus.requestFocus() } }

    fun move(step: Int) {
        index = (index + step + list.size) % list.size
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                if (showList || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { move(+1); true }
                    Key.DirectionDown, Key.ChannelDown -> { move(-1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                    Key.DirectionLeft, Key.DirectionRight -> { showList = true; true }
                    Key.Menu -> { onToggleFavorite(current); infoTick++; true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    this.player = exo
                }
            },
        )

        if (buffering && error == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        error?.let {
            Text(
                it,
                color = ErrorRed,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000))
                    .padding(24.dp),
            )
        }
        if (showInfo && !showList) {
            InfoBar(
                ch = current,
                number = index + 1,
                favorite = current.url in favorites,
                now = now,
                nowProg = nowPlaying(epg, current, now),
                nextProg = nextPlaying(epg, current, now),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (showList) {
            ChannelPanel(
                list = list,
                current = index,
                epg = epg,
                now = now,
                onPick = { i -> index = i; showList = false },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

@Composable
private fun InfoBar(
    ch: Channel,
    number: Int,
    favorite: Boolean,
    now: Long,
    nowProg: Programme?,
    nextProg: Programme?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(Color(0xE60B1220)).padding(horizontal = 40.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", style = MaterialTheme.typography.headlineLarge, color = Accent)
        Spacer(Modifier.width(20.dp))
        AsyncImage(model = ch.logo.ifBlank { null }, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name + if (favorite) "  ★" else "", style = MaterialTheme.typography.titleLarge)
            if (nowProg != null) {
                Text("${hhmm(nowProg.start)}–${hhmm(nowProg.stop)}  ${nowProg.title}", color = Color.White, maxLines = 1)
                LinearProgressIndicator(
                    progress = { progressOf(nowProg, now) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
            if (nextProg != null) {
                Text("Tiếp theo: ${hhmm(nextProg.start)}  ${nextProg.title}", color = Dim, maxLines = 1)
            }
            Text("OK: danh sách kênh   ▲▼: đổi kênh   MENU: yêu thích", color = Dim, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ChannelPanel(
    list: List<Channel>,
    current: Int,
    epg: Map<String, List<Programme>>,
    now: Long,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusCurrent = remember { FocusRequester() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focusCurrent.requestFocus() }
    }

    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxHeight()
            .width(440.dp)
            .background(Color(0xE60B1220))
            .padding(12.dp),
    ) {
        itemsIndexed(list) { i, ch ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (i == current) Modifier.focusRequester(focusCurrent) else Modifier)
                    .tvFocusable { onPick(i) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = Dim, modifier = Modifier.width(44.dp))
                Column {
                    Text(ch.name, color = if (i == current) Accent else Color.White, maxLines = 1)
                    nowPlaying(epg, ch, now)?.let { Text(it.title, color = Dim, maxLines = 1) }
                }
            }
        }
    }
}
