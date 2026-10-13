# MyTivi – trình phát IPTV cho Android TV box

App phát playlist M3U / luồng HLS (.m3u8) với giao diện điều khiển bằng remote, lấy cảm hứng từ TiviMate.
Kotlin + Jetpack Compose + Media3 (ExoPlayer).

## Tính năng
- Thêm playlist bằng link (.m3u danh sách kênh, hoặc 1 link .m3u8 đơn)
- Nhóm kênh (group-title), logo kênh, tìm kiếm, kênh yêu thích
- Xem toàn màn hình: ▲▼ / CH+ CH- đổi kênh, OK mở danh sách kênh, MENU thêm/bỏ yêu thích
- EPG (XMLTV, hỗ trợ .gz): hiện chương trình đang phát + thanh tiến độ + chương trình tiếp theo
- **Khung xem trước**: lướt tới kênh nào, ô nhỏ bên phải tự chạy kênh đó (im lặng, độ phân giải thấp cho nhẹ)
- **Tự cập nhật**: playlist tải lại sau mỗi 3 giờ, EPG sau mỗi 6 giờ (kể cả khi box mở nhiều ngày liền);
  mở app là hiện ngay dữ liệu đã lưu rồi cập nhật ngầm; bấm *Làm mới* để cập nhật tay
- Mở app là vào thẳng kênh xem gần nhất; tự thử lại khi mất luồng
- Lưu cache playlist và EPG nên lần sau mở app có kênh và lịch ngay

## Cách build
1. Cài **Android Studio** bản Koala (2024.1) trở lên, mở thư mục `MyTivi`.
2. Đợi Gradle sync (cần Internet, Android SDK 34 – Studio sẽ tự đề nghị cài).
   Nếu Studio báo thiếu Gradle wrapper, chọn dùng Gradle 8.7 trở lên.
3. Chạy thử: tạo **Android TV emulator** (Device Manager > Create Device > TV), hoặc cắm TV box.
4. Tạo APK: menu **Build > Build APK(s)** → file `app/build/outputs/apk/debug/app-debug.apk`.

## Build APK không cần cài Android Studio (GitHub Actions)
1. Tạo tài khoản GitHub (miễn phí) và tạo một repository mới.
2. Giải nén `MyTivi.zip`, rồi tải **toàn bộ nội dung thư mục MyTivi** lên repo
   (nhớ cả thư mục ẩn `.github`; trên web chọn *Add file > Upload files* và kéo thả cả thư mục).
3. Vào tab **Actions > Build APK > Run workflow** (hoặc đợi nó tự chạy sau khi upload).
4. Sau khoảng 5–8 phút, mở lượt chạy vừa xong, kéo xuống mục **Artifacts**, tải `MyTivi-apk`.
   Giải nén sẽ có `app-debug.apk` – cài thẳng lên TV box.

## Cài lên TV box
- Bật *Tùy chọn nhà phát triển > Gỡ lỗi ADB* trên box, rồi:
  `adb connect <IP-của-box>:5555` và `adb install app-debug.apk`
- Hoặc chép APK vào USB, mở bằng file manager trên box (cần cho phép cài từ nguồn không rõ).

## Cấu trúc mã
| File | Việc |
|---|---|
| `M3uParser.kt` | Đọc #EXTINF (tvg-id, tvg-logo, group-title...) |
| `EpgParser.kt` | Đọc XMLTV, chỉ giữ -2h..+24h |
| `Http.kt` | Tải playlist/EPG bằng OkHttp, lưu file cache, tự giải nén gzip |
| `MainViewModel.kt` | Trạng thái: kênh, yêu thích, EPG, cache |
| `BrowseScreen.kt` | Màn hình nhóm + danh sách kênh |
| `PlayerFactory.kt` | Tạo ExoPlayer dùng chung (xem trước + toàn màn hình) |
| `PlayerScreen.kt` | Trình phát ExoPlayer, thanh thông tin, bảng chọn kênh |
| `SetupScreen.kt` | Nhập link playlist / EPG |

## Hạn chế hiện tại / việc nên làm tiếp
- EPG ghép theo `tvg-id`; kênh không có tvg-id sẽ không hiện lịch.
- Chưa có: nhập số kênh bằng phím số, ghi hình, catch-up, multiview, khóa phụ huynh, Xtream Codes, nhiều playlist.
- Link kiểu `url|User-Agent=...` (header riêng) chưa được hỗ trợ.

Chỉ dùng với nội dung bạn có quyền xem.

## Đổi tần suất tự cập nhật
Trong `MainViewModel.kt`, đầu file có 3 hằng số `PLAYLIST_TTL_MS`, `EPG_TTL_MS`, `CHECK_EVERY_MS`.

## Logo
Icon app: `app/src/main/res/drawable/ic_launcher.xml` (vector). Banner trên màn hình Android TV:
`app/src/main/res/drawable-xhdpi/tv_banner.png` (640x360). Có thể xóa file cũ `drawable/banner.xml` nếu còn.
