# Báo cáo kiểm chứng App Alert 1.0

Báo cáo này phân biệt kiểm thử đã thực thi với các ca mới được định nghĩa. Mã nguồn dùng package `dev.lordierclaw.lunaappalert`, min SDK 26, compile/target SDK 37. Hai ứng dụng thử là `dev.lordierclaw.fixture.alpha` và `dev.lordierclaw.fixture.beta`.

## Build và bộ xử lý quy tắc

Đã chạy thành công trên Windows với Gradle 9.6, AGP 9.4 và JDK 25:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --console=plain
```

Lint không có lỗi, còn 28 cảnh báo về cách khai báo, tài nguyên và phiên bản thư viện. Bộ xử lý thuần Kotlin có **57 kiểm thử đạt**, không có lỗi hoặc bỏ qua:

| Bộ kiểm thử | Số ca | Nội dung |
|---|---:|---|
| RuleResolverTest | 13 | Ghi đè cùng chữ ký, ngưỡng khác nhau, công tắc cha/con, quyền phụ thuộc |
| RuleEvaluatorTest | 14 | Ba loại kích hoạt, lặp hữu hạn, tổng từng app/ngày, phục hồi và loại trạng thái cũ |
| SessionTrackerTest | 7 | Chuyển app, cùng package, khóa máy và phục hồi phiên |
| DailyUsageTrackerTest | 9 | Nửa đêm, múi giờ/DST, thời gian riêng từng ứng dụng, sự kiện đến trễ |
| ForegroundReducerTest | 14 | Loại sự kiện trùng, Home, app cấu hình, bàn phím, khôi phục trước API 28 và biên thời gian |

Báo cáo máy tạo: `app/build/test-results/testDebugUnitTest/` và `app/build/reports/`.

## Dữ liệu trên Android

`RepositoryTest` đã đạt **9/9 trên từng API 26, 35 và 37**. Các ca dùng Room trên Android, DataStore và bộ mã hóa checkpoint thật: ràng buộc package/chữ ký quy tắc, cascade khi xóa nhóm, giữ ứng dụng và quy tắc riêng khi chuyển nhóm, giữ công tắc con, lưu/đọc thiết lập và phục hồi JSON hợp lệ/hỏng.

## Ma trận thiết bị

Máy ảo x86_64 được cài từ Android SDK chính thức, chạy bằng WHPX; mỗi lần chỉ chạy một máy ảo. Luồng chính dùng UI Automator thao tác xuyên ứng dụng và Cài đặt Android. Ngưỡng một phút và khoảng lặp một phút dùng thời gian thật.

| Máy ảo | Luồng chính | CRUD/UI | Khóa và gỡ/cài lại | Tiến trình/reboot | Giao diện |
|---|---|---|---|---|---|
| Luna_E2E_API37 | Đạt debug 220 giây; release cuối 217 giây | Đạt | Đạt | Đạt | Đạt chữ 200%, ngang, bàn phím mềm và 320 × 568 dp trên release cuối |
| Luna_E2E_API35 | Đạt debug 222 giây; release cuối 223 giây | Đạt | Đạt | Đạt | Đạt chữ 200%, ngang, bàn phím mềm và 320 × 568 dp trên release cuối |
| Luna_E2E_API26 | Đạt debug 213 giây; release cuối 218 giây | Đạt | Đạt | Đạt | Đạt chữ 200%, ngang, bàn phím mềm và 320 × 568 dp trên release cuối |

Các trạng thái trên chỉ được đổi thành “Đạt” sau khi có log thực thi thành công. Những lần sửa bộ thao tác UI trước khi chạy lại không được tính là kết quả đạt. [Kết quả tổng hợp từ log](verification-results.json) ghi số ca, thời gian và checksum đã đối chiếu. Các ca chức năng chạy trên debug; luồng chính và kiểm tra giao diện được xác nhận lại trực tiếp trên APK release cuối.

## Phạm vi các ca E2E

- `LunaE2ETest`: cài mới, cấp quyền qua giao diện Android, tạo nhóm, chọn hai ứng dụng đã cài, tạo bốn quy tắc, ghi đè nhóm bằng app rule, chuyển activity cùng package, thông báo thật, overlay thật, một phút liên tục, một lần lặp, Tiếp tục/Thoát và thu hồi/khôi phục quyền.
- `LunaConfigUITest`: công tắc cha/con, chuyển nhóm và hoàn tác, từ chối quy tắc trùng, xóa nhóm giữ ứng dụng cùng quy tắc riêng.
- `LunaRuleCrudTest`: đổi tên nhóm, sửa lời nhắc, xóa quy tắc và hoàn tác qua giao diện; khôi phục đúng cấu hình ban đầu.
- `LunaLifecycleTest` cùng `Test-LunaLifecycle.ps1`: khóa/mở màn hình tạo phiên mới, gỡ ứng dụng đích bằng Android, giữ cấu hình và tự nhận lại khi cài cùng package.
- `LunaRecoveryTest` cùng `Test-LunaRecovery.ps1`: force-stop chỉ phục hồi khi mở lại, giết tiến trình thật và khởi động lại thiết bị. Script ghi trạng thái foreground service và overlay trước khi instrumentation khởi động lại tiến trình đích.
- `LunaNotificationTest` cùng `Test-LunaNotifications.ps1`: thu hồi và cấp lại quyền thông báo thật, không phát thông báo khi bị từ chối, giữ quyền ghi đè của app rule và tiếp tục phát overlay hợp lệ. API 33 trở lên dùng [lệnh kiểm thử chính thức của Android](https://developer.android.com/develop/ui/compose/notifications/notification-permission#test); API 26 thao tác công tắc thông báo trong giao diện Settings.
- `LunaVisualTest`: ảnh các màn hình theo Stitch, chữ 200%, xoay ngang và bàn phím; kiểm tra nút Lưu/Quay lại còn dùng được và dữ liệu không đổi.
- `LunaOverlayTest`: overlay thật ở chữ 200%, không vẽ đè vùng thanh trạng thái Android, đóng bằng Tiếp tục và không tự tạo phiên mới.
- `UsageObserverTest`: khóa màn hình thật 20 giây, tạo observer mới từ nguồn Android và xác nhận tổng hôm nay loại khoảng bị khóa. Unit test bổ sung tình huống trước API 28, khi nguồn sự kiện chưa có `SCREEN_NON_INTERACTIVE`.
- `Test-LunaSmallScreen.ps1`: đổi viewport thành 320 × 568 dp, chạy lại giao diện và overlay chữ 200%, xoay ngang, nhập liệu và lưu thật; khôi phục kích thước/density ban đầu sau kiểm thử.

Kiểm thử chữ lớn phát hiện lỗi overlay vẽ vào nền thanh trạng thái và footer chiếm quá nhiều chỗ ở cửa sổ thấp. Thao tác Lưu chuyển thành nút dấu kiểm khi cửa sổ thấp. Nếu bàn phím làm vùng hiển thị còn dưới 200 dp, Quay lại/Lưu nằm hai bên biểu mẫu để dành chiều cao cho ô nhập. Phần nội dung vẫn cuộn được. Kiểm thử xác nhận ít nhất 48 dp chiều cao ô nhập không bị cửa sổ bàn phím che, đồng thời lưu đúng dữ liệu.

Rà soát mã phát hiện nguồn sự kiện Android 8 chưa có sự kiện khóa màn hình được [bổ sung từ API 28](https://developer.android.com/reference/android/app/usage/UsageEvents.Event#SCREEN_NON_INTERACTIVE). Bộ tính tổng ngày nay xử lý khoảng resume/pause riêng, còn phiên liên tục vẫn giữ cách chuyển activity trong cùng package. Trên API 26–27, một checkpoint đi qua khoảng background chưa xác minh sẽ không được dùng để khôi phục thời gian liên tục cũ. Các ca hồi quy đã được chứng kiến thất bại trước khi sửa và đạt sau khi sửa.

E2E API 26 phát hiện Settings không nhận đường dẫn quyền sử dụng có `package:`. App Alert kiểm tra khả năng mở đường dẫn trực tiếp và dùng màn hình quyền chung khi cần. Luồng kiểm thử vẫn cấp quyền bằng thao tác trong Settings, không dùng lệnh shell để bỏ qua bước này.

Log đầy đủ, ảnh PNG và cây giao diện XML nằm trong `artifacts/e2e/api<level>/`. Xem [ảnh giao diện đã kiểm tra](screenshots.md). Lệnh tái lập được ghi tại [tools/README.md](../tools/README.md).

## APK bàn giao

APK release đã được ký bằng khóa RSA 4096 bit ngoài repository và vượt qua kiểm tra chữ ký v2/v3. Kiểm tra zipalign 16 KB cũng đạt cho các thư viện native. File checksum được tạo trực tiếp từ APK tại `artifacts/release/SHA256SUMS.txt`.

Chính APK release đã ký, không sửa đổi giữa các lần cài, đã đạt luồng E2E chính:

| API | Thời gian thực thi | Kết quả |
|---|---:|---|
| 26 | 217,690 giây | Đạt |
| 35 | 223,178 giây | Đạt |
| 37 | 216,956 giây | Đạt |

SHA-256 của APK trên cả ba máy:

```text
5022ad107a0f88ebebf8fd9f9c48487d725a7419caa53af5e80096ce49fe68e4
```

Package đã cài không có cờ `DEBUGGABLE`. Bộ instrumentation được ký cùng chứng thư để thao tác và kiểm tra bản release; mã kiểm thử không nằm trong APK bàn giao. Bằng chứng ở `artifacts/release-e2e/api<level>/`.

## Giới hạn kết quả

Kết quả E2E chỉ xác nhận trên các emulator thực sự đã chạy. Android có thể giao sự kiện trễ; hạn chế nền của hãng, force-stop và các ứng dụng chặn overlay vẫn áp dụng. Không có thử nghiệm thiết bị vật lý trong đợt này. Daily Total tính riêng từng ứng dụng; không có tài khoản, đồng bộ, chặn ứng dụng hoặc lịch sử sử dụng.
