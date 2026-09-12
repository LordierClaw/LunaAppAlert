# App Alert

Ứng dụng Android nhắc khi mở hoặc sử dụng các ứng dụng bạn chọn. Giao diện tiếng Việt, xây bằng Kotlin và Jetpack Compose theo [thiết kế Stitch](https://stitch.withgoogle.com/projects/9266226264746735965?pli=1).

## Cài đặt và sử dụng

1. Cài APK đã ký từ [Releases](https://github.com/LordierClaw/LunaAppAlert/releases) hoặc phần bàn giao. Thiết bị cần Android 8.0/API 26 trở lên; phiên bản ưu tiên là Android 16/API 36.
2. Mở **App Alert → Thiết lập App Alert** và bật **Truy cập sử dụng ứng dụng** trong Cài đặt Android.
3. Khi dùng cảnh báo phủ màn hình, bật **Hiển thị trên ứng dụng khác**. Cấp quyền thông báo khi dùng cảnh báo thông báo.
4. Thêm ứng dụng đã cài, tùy chọn tạo nhóm, rồi thêm quy tắc cho nhóm hoặc riêng ứng dụng.
5. Mở ứng dụng đích. **Tiếp tục** đóng cảnh báo; **Thoát ứng dụng** đưa bạn về màn hình chính.

Trạng thái theo dõi xuất hiện trong Cài đặt và thông báo dịch vụ nền. Có thể tạm dừng mà không mất cấu hình. Quyền chạy nền/pin là hướng dẫn tăng độ ổn định, không phải điều kiện bắt buộc khi thiết lập.

## Quy tắc

| Loại | Khi cảnh báo |
|---|---|
| Khi mở ứng dụng | Một lần khi bắt đầu phiên sử dụng mới |
| Dùng liên tục | Đạt ngưỡng phút trong cùng phiên; có thể lặp hữu hạn |
| Tổng hôm nay | Đạt ngưỡng tích lũy của **từng ứng dụng** trong ngày địa phương |

Quy tắc riêng chỉ ghi đè quy tắc nhóm có cùng loại và cùng ngưỡng. Các ngưỡng khác nhau cùng áp dụng. Quy tắc bị ghi đè vẫn hiển thị kèm lý do. Tắt nhóm giữ nguyên công tắc và quy tắc của các ứng dụng con.

- Lặp 20 phút / mỗi 5 phút / 3 lần: cảnh báo ở phút 20, 25, 30, 35. Khi Android giao sự kiện trễ, các lần đã quá hạn được gộp để tránh dồn cảnh báo.
- Về Home, khóa máy hoặc chuyển sang ứng dụng khác (kể cả App Alert) kết thúc phiên dùng liên tục.
- Xóa nhóm giữ các ứng dụng và quy tắc riêng; chúng chuyển về **Chưa phân nhóm**.
- Hoàn tác xóa quy tắc khôi phục cùng quy tắc; cảnh báo Tổng hôm nay đã phát sẽ không phát lại trong ngày đó.
- Gỡ ứng dụng đích khỏi điện thoại giữ cấu hình; cài lại cùng package sẽ nhận lại cấu hình.

## Dữ liệu và quyền riêng tư

Không tài khoản, mạng, đồng bộ, quảng cáo hoặc analytics. Cấu hình dùng Room/DataStore; tắt Android cloud backup và chuyển dữ liệu tự động. Chỉ giữ bộ đếm ngày hiện tại và trạng thái phiên cần thiết. Không có lịch sử, biểu đồ hoặc lịch chặn ứng dụng. Khi khôi phục bộ đếm hôm nay, dịch vụ có thể đọc sự kiện trước nửa đêm để xác định ứng dụng đã mở xuyên ngày; không giữ lịch sử đó.

Ứng dụng dùng UsageStats và foreground service `specialUse`. Quyền overlay/thông báo chỉ cần cho kiểu cảnh báo tương ứng; thiếu quyền không tự chuyển kiểu cảnh báo. Tài khoản/đồng bộ, lịch giới hạn và nội dung blocking trong một số bản mẫu Stitch được loại bỏ theo phạm vi sản phẩm đã chốt.

## Build

Android Studio và SDK API 37; Gradle 9.6, Android Gradle Plugin 9.4, Java 25 (đã cấu hình trong `gradle/gradle-daemon-jvm.properties`). Gradle tự tìm JDK theo cấu hình daemon. Giữ SDK cục bộ trong `local.properties`, không commit file này.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest :test-target:assembleAlphaDebug :test-target:assembleBetaDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions cũng build APK kiểm thử và lưu báo cáo. Kho mã nguồn riêng tư không kèm khóa ký. Xem [báo cáo kiểm chứng](docs/verification.md) và [ảnh giao diện](docs/screenshots.md).

## Kiểm thử E2E

Mô-đun `test-target` tạo hai ứng dụng thử độc lập; không được đóng gói trong APK App Alert. Kiểm thử dùng UI Automator để thao tác màn hình hệ thống, ứng dụng đích, thông báo và overlay thật.

Chạy nhanh luồng chính trên Android 16:

```powershell
.\tools\Setup-LunaEmulators.ps1 -Apis 36
.\tools\Start-LunaEmulator.ps1 -Api 36
.\tools\Test-LunaE2E.ps1 -Api 36
```

Bộ kiểm thử mở rộng:

```powershell
.\tools\Setup-LunaEmulators.ps1
.\tools\Start-LunaEmulator.ps1 -Api 37
.\tools\Test-LunaE2E.ps1
.\tools\Test-LunaLifecycle.ps1
.\tools\Test-LunaNotifications.ps1
.\tools\Test-LunaRecovery.ps1
```

Xem tham số ở đầu mỗi script để chọn SDK, serial và máy ảo. Bộ kiểm thử bao gồm thời gian thật một phút và một lần lặp; unit test sử dụng thời gian giả cho các tình huống dài, đổi ngày/múi giờ và quy tắc ưu tiên. Kết quả thiết bị, ảnh chụp và log được lưu ở `artifacts/`; báo cáo nghiệm thu ở `docs/verification.md`.

## Ký APK

```powershell
.\tools\Build-SignedApk.ps1
```

Script tạo hoặc dùng lại khóa trong `%USERPROFILE%/.android/lunaappalert-signing/`, bên ngoài repository. Mật khẩu được bảo vệ bằng Windows DPAPI; không in ra console. APK và SHA-256 được ghi vào `artifacts/release/`. Giữ bản sao an toàn của khóa để ký các bản cập nhật. File credential DPAPI chỉ giải mã được bằng tài khoản Windows đã tạo **trên cùng máy tính**; trước khi chuyển máy hoặc cài lại Windows, cần sao lưu riêng mật khẩu khóa vào trình quản lý mật khẩu an toàn. Chỉ sao chép `release.jks` và `credential.xml` sang máy khác là chưa đủ.

## Giới hạn đã biết

Android không đảm bảo sự kiện sử dụng có độ trễ cố định. Foreground service giảm khả năng bị dừng nhưng không vượt qua thao tác force-stop, hạn chế pin của hãng hoặc cơ chế chặn overlay của ứng dụng khác. Sau force-stop, cần mở App Alert để chạy lại. Đo lường ưu tiên tương tác một ứng dụng ở foreground; multi-window/PiP phụ thuộc sự kiện Android cung cấp.

Font Inter được đóng gói theo SIL Open Font License 1.1 (xem Cài đặt → Giấy phép nguồn mở). Cảnh báo chỉ nhắc và đưa về Home theo thao tác người dùng, không force-stop hoặc khóa ứng dụng đích.
