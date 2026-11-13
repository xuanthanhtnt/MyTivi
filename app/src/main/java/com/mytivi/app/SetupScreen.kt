package com.mytivi.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private enum class SourceKind { Playlist, Epg }

/** Hộp thoại đang mở: thêm (source = null) hoặc sửa một nguồn. */
private data class SourceEdit(val kind: SourceKind, val source: Source?)

/**
 * Cài đặt (điều khiển bằng remote):
 *  - NHIỀU danh sách kênh (M3U / luồng .m3u8) và NHIỀU link lịch phát sóng (EPG, XMLTV).
 *  - Mỗi nguồn 1 hàng: chọn tên để sửa · Đang bật/Đang tắt · ▲ Lên · Xóa.
 *  - Mọi thay đổi chỉ áp dụng khi bấm "Lưu" (Back / "Hủy" là bỏ).
 */
@Composable
fun SetupScreen(
    initialPlaylists: List<Source>,
    initialEpgs: List<Source>,
    initialUseAutoEpg: Boolean,
    canCancel: Boolean,
    version: String,
    onSave: (playlists: List<Source>, epgs: List<Source>, useAutoEpg: Boolean) -> Unit,
    onCancel: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val playlists = remember { mutableStateListOf<Source>().apply { addAll(initialPlaylists) } }
    val epgs = remember { mutableStateListOf<Source>().apply { addAll(initialEpgs) } }
    var useAutoEpg by remember { mutableStateOf(initialUseAutoEpg) }
    var err by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<SourceEdit?>(null) }
    var deleting by remember { mutableStateOf<SourceEdit?>(null) }
    val topFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    var refocusSave by remember { mutableIntStateOf(0) }   // đóng hộp thoại xong thì đưa focus về nút "Lưu"

    BackHandler(enabled = canCancel && editor == null && deleting == null) { onCancel() }
    LaunchedEffect(Unit) {
        if (initialPlaylists.isEmpty()) {
            // Lần đầu dùng: mở luôn hộp thoại thêm danh sách kênh
            editor = SourceEdit(SourceKind.Playlist, null)
        } else {
            withFrameNanos { }
            runCatching { topFocus.requestFocus() }
        }
    }
    LaunchedEffect(refocusSave) {
        if (refocusSave == 0) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { saveFocus.requestFocus() }
    }

    fun listFor(kind: SourceKind) = if (kind == SourceKind.Playlist) playlists else epgs

    fun closeDialogs() {
        editor = null
        deleting = null
        refocusSave++
    }

    fun save() {
        err = when {
            playlists.isEmpty() -> "Thêm ít nhất 1 danh sách kênh (.m3u)."
            playlists.none { it.enabled } -> "Bật ít nhất 1 danh sách kênh."
            else -> null
        }
        if (err == null) onSave(playlists.toList(), epgs.toList(), useAutoEpg)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 24.dp),
    ) {
        Text("Cài đặt MyTivi", style = MaterialTheme.typography.headlineMedium)
        Text("Phiên bản $version", color = Dim, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        SourceSection(
            title = "Danh sách kênh (M3U)",
            hint = "Thêm được nhiều danh sách. Kênh của các danh sách đang bật được gộp lại; " +
                "ngăn nhóm kênh (◄) chia mục theo từng danh sách.",
            items = playlists,
            emptyText = "Chưa có danh sách kênh nào.",
            addLabel = "+ Thêm danh sách kênh",
            topFocus = topFocus,
            onAdd = { editor = SourceEdit(SourceKind.Playlist, null) },
            onEdit = { editor = SourceEdit(SourceKind.Playlist, it) },
            onToggle = { s -> playlists.replaceById(s.copy(enabled = !s.enabled)) },
            onMoveUp = { s -> playlists.moveUp(s) },
            onDelete = { deleting = SourceEdit(SourceKind.Playlist, it) },
        )
        Spacer(Modifier.height(16.dp))
        SourceSection(
            title = "Lịch phát sóng (EPG)",
            hint = "Không bắt buộc. Thêm được nhiều link XMLTV (.xml, .xml.gz); link ở trên được ưu tiên " +
                "khi nhiều EPG có cùng kênh.",
            items = epgs,
            emptyText = "Chưa thêm link EPG nào.",
            addLabel = "+ Thêm link EPG",
            topFocus = null,
            onAdd = { editor = SourceEdit(SourceKind.Epg, null) },
            onEdit = { editor = SourceEdit(SourceKind.Epg, it) },
            onToggle = { s -> epgs.replaceById(s.copy(enabled = !s.enabled)) },
            onMoveUp = { s -> epgs.moveUp(s) },
            onDelete = { deleting = SourceEdit(SourceKind.Epg, it) },
        )
        Spacer(Modifier.height(10.dp))
        TvAction(
            label = "Tự dùng EPG ghi sẵn trong playlist (url-tvg): " + if (useAutoEpg) "BẬT" else "TẮT",
            color = if (useAutoEpg) Accent else Color.White,
            onClick = { useAutoEpg = !useAutoEpg },
        )

        err?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = ErrorRed)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvAction("Lưu", onClick = { save() }, color = Accent, requester = saveFocus)
            TvAction("Kiểm tra cập nhật", onClick = onCheckUpdate)
            if (canCancel) TvAction("Hủy", onClick = onCancel)
        }
    }

    editor?.let { e ->
        SourceDialog(
            edit = e,
            onDismiss = { closeDialogs() },
            onSave = { name, url ->
                val list = listFor(e.kind)
                val src = e.source
                if (src == null) {
                    val fallback = if (e.kind == SourceKind.Playlist) "Danh sách ${list.size + 1}" else "EPG ${list.size + 1}"
                    list.add(Source(MainViewModel.newSourceId(), name.ifBlank { fallback }, url))
                } else {
                    list.replaceById(src.copy(name = name.ifBlank { src.name }, url = url))
                }
                err = null
                closeDialogs()
            },
        )
    }
    deleting?.let { d ->
        val src = d.source
        if (src != null) {
            ConfirmDeleteDialog(
                name = src.name,
                isPlaylist = d.kind == SourceKind.Playlist,
                onConfirm = {
                    listFor(d.kind).removeAll { it.id == src.id }
                    closeDialogs()
                },
                onDismiss = { closeDialogs() },
            )
        }
    }
}

private fun MutableList<Source>.replaceById(s: Source) {
    val i = indexOfFirst { it.id == s.id }
    if (i >= 0) this[i] = s
}

private fun MutableList<Source>.moveUp(s: Source) {
    val i = indexOfFirst { it.id == s.id }
    if (i > 0) {
        val above = this[i - 1]
        this[i - 1] = this[i]
        this[i] = above
    }
}

/** Nút điều khiển bằng remote (tô nền + viền khi được focus). */
@Composable
private fun TvAction(
    label: String,
    onClick: () -> Unit,
    color: Color = Color.White,
    requester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        label,
        color = color,
        maxLines = 1,
        modifier = modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .tvFocusable(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun SourceSection(
    title: String,
    hint: String,
    items: List<Source>,
    emptyText: String,
    addLabel: String,
    topFocus: FocusRequester?,
    onAdd: () -> Unit,
    onEdit: (Source) -> Unit,
    onToggle: (Source) -> Unit,
    onMoveUp: (Source) -> Unit,
    onDelete: (Source) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1A2E), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(hint, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        if (items.isEmpty()) {
            Text(emptyText, color = Dim, modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp))
        }
        items.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                // Chọn tên (OK) để sửa link / tên
                Column(
                    Modifier
                        .weight(1f)
                        .then(if (i == 0 && topFocus != null) Modifier.focusRequester(topFocus) else Modifier)
                        .tvFocusable { onEdit(s) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        s.name,
                        color = if (s.enabled) Color.White else Dim,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(s.url, color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                TvAction(
                    label = if (s.enabled) "Đang bật" else "Đang tắt",
                    color = if (s.enabled) Accent else Dim,
                    onClick = { onToggle(s) },
                )
                if (i > 0) TvAction("▲ Lên", onClick = { onMoveUp(s) })
                TvAction("Xóa", onClick = { onDelete(s) }, color = ErrorRed)
            }
        }
        TvAction(
            addLabel,
            onClick = onAdd,
            color = Accent,
            requester = if (items.isEmpty()) topFocus else null,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Thêm / sửa một nguồn: link + tên hiển thị (gõ bằng bàn phím của TV). */
@Composable
private fun SourceDialog(edit: SourceEdit, onDismiss: () -> Unit, onSave: (name: String, url: String) -> Unit) {
    val isPlaylist = edit.kind == SourceKind.Playlist
    var url by remember { mutableStateOf(edit.source?.url ?: "") }
    var name by remember { mutableStateOf(edit.source?.name ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    val urlFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { urlFocus.requestFocus() }
    }

    fun submit() {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            err = "Link phải bắt đầu bằng http:// hoặc https://"
            return
        }
        onSave(name.trim(), u)
    }

    val title = when {
        edit.source != null && isPlaylist -> "Sửa danh sách kênh"
        edit.source != null -> "Sửa link EPG"
        isPlaylist -> "Thêm danh sách kênh"
        else -> "Thêm link EPG"
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .background(Color(0xFF0F1A2E), RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                if (isPlaylist) "Link file .m3u (danh sách kênh) hoặc một luồng .m3u8."
                else "Link lịch phát sóng XMLTV (.xml hoặc .xml.gz).",
                color = Dim,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    err = null
                },
                label = { Text(if (isPlaylist) "Link danh sách kênh" else "Link EPG") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(urlFocus),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tên hiển thị (không bắt buộc)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            err?.let { Text(it, color = ErrorRed, modifier = Modifier.padding(top = 8.dp)) }
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                TvAction(if (edit.source == null) "Thêm" else "Lưu", onClick = { submit() }, color = Accent)
                TvAction("Hủy", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(name: String, isPlaylist: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancelFocus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .background(Color(0xFF0F1A2E), RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text("Xóa “$name”?", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (isPlaylist) "Kênh của danh sách này sẽ không còn trong app sau khi bấm Lưu."
                else "Lịch phát sóng từ link này sẽ không còn dùng sau khi bấm Lưu.",
                color = Dim,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                TvAction("Xóa", onClick = onConfirm, color = ErrorRed)
                TvAction("Hủy", onClick = onDismiss, requester = cancelFocus)
            }
        }
    }
}
