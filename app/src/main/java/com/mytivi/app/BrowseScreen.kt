package com.mytivi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage

/** Màn hình chính: nhóm kênh bên trái, danh sách kênh bên phải (giống TiviMate). */
@Composable
fun BrowseScreen(
    vm: MainViewModel,
    onPlay: (List<Channel>, Int) -> Unit,
    onSettings: () -> Unit,
) {
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

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MyTivi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("   ${channels.size} kênh", color = Dim)
            Spacer(Modifier.weight(1f))
            if (vm.loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            TvButton("Làm mới") { vm.refresh() }
            TvButton("Cài đặt", onSettings)
        }
        vm.error?.let { Text(it, color = ErrorRed) }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(Modifier.width(260.dp).fillMaxHeight()) {
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
                            onPlay = { onPlay(shown, i) },
                            onFavorite = { vm.toggleFavorite(ch) },
                        )
                    }
                }
            }
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
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).tvFocusable(onPlay).padding(10.dp),
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
