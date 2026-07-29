# HyperCeiler - ZK Clock Style Mod Edition

Bản Fork tùy biến cá nhân của công cụ **HyperCeiler (Xposed/LSPosed Module)** nhằm tích hợp trực tiếp tính năng **ZK Clock Styles** vào hệ thống HyperOS / MIUI.

> [!NOTE]
> Giải pháp này sử dụng lập trình Xposed Hook để chèn giao diện đồng hồ ZK trực tiếp vào mã nguồn của `MiuiClock` trong tiến trình `SystemUI` tại thời điểm chạy. Phương pháp này thay thế hoàn toàn cho cách mod & nạp đè APK truyền thống (tránh lỗi sập nguồn/bootloop do cơ chế SELinux và kiểm tra chữ ký Xiaomi Platform Key của tiến trình hệ thống UID 1000).

---

## ✨ Tính năng nổi bật
* **15 Kiểu Đồng Hồ ZK Style**: Hỗ trợ đầy đủ các giao diện đồng hồ đặc trưng (Dạng viên thuốc - Pills, Dạng khối vát - Asymmetric, Dạng dải màu - Gradients, Dạng tích hợp hiển thị giây - Seconds).
* **Không can thiệp file hệ thống**: Hoạt động hoàn toàn qua cơ chế Xposed, an toàn, dễ dàng cài đặt/gỡ bỏ mà không sợ treo logo (bootloop).
* **Căn chỉnh động (Vertical Auto-Centering)**: Sử dụng cấu trúc container trung gian giúp căn giữa dọc tự động đồng hồ trên mọi độ cao thanh trạng thái, không bị lỗi kéo dãn tràn viền.
* **Tự động tránh xung đột**: Tự động vô hiệu hóa tính năng căn chỉnh đồng hồ mặc định của HyperCeiler khi bật ZK Clock để tránh lỗi ghi đè lẫn nhau.

---

## 🛠️ Trình thiết kế trực quan (Visual Clock Designer)
Dự án đi kèm một công cụ thiết kế phụ trợ bằng HTML/CSS:
* **Đường dẫn**: `zk_clock_designer.html` (nằm ở thư mục cha).
* **Chức năng**: Bạn chỉ cần mở tệp này bằng trình duyệt trên máy tính, kéo trượt các thanh để tinh chỉnh khoảng cách, cỡ chữ, căn lề và nhận trực tiếp mã Kotlin được sinh tự động để dán đè vào source code.

---

## 🚀 Hướng dẫn biên dịch & Cài đặt

### 1. Biên dịch dự án (Gradle Build)
Mở terminal tại thư mục gốc của HyperCeiler và chạy lệnh biên dịch tệp APK Debug:
```powershell
# Trên Windows PowerShell / Command Prompt
.\gradlew.bat assembleDebug
```
Tệp APK đầu ra sẽ nằm tại:
`app/build/outputs/apk/debug/HyperCeiler-x.xx.xxx-xxxx-debug.apk`

### 2. Cài đặt trực tiếp qua ADB
Nếu điện thoại của bạn đã bật ADB Debug và kết nối với máy tính, bạn có thể đẩy nhanh file APK sang bộ nhớ máy và tự động cài đặt đè bằng lệnh:
```powershell
# Gửi tệp APK sang thư mục Download của điện thoại
adb push app/build/outputs/apk/debug/*.apk /sdcard/Download/app-debug.apk

# Cài đặt đè trực tiếp lên thiết bị
adb install -r app/build/outputs/apk/debug/*.apk
```

---

## ⚙️ Hướng dẫn kích hoạt trên thiết bị
1. Cài đặt tệp APK hoàn tất.
2. Mở ứng dụng **LSPosed Manager** trên điện thoại.
3. Vào tab **Modules** -> Chọn **HyperCeiler** -> Kích hoạt công tắc bật module và tích chọn các mục hệ thống cần thiết (**System Framework**, **System UI**, và **Settings**).
4. Khởi động lại điện thoại (hoặc **Khởi động lại nhanh SystemUI** từ cài đặt của HyperCeiler/LSPosed).
5. Mở ứng dụng **HyperCeiler** trên màn hình chính:
   * Đi tới: **Status Bar (Thanh trạng thái)** -> **Clock (Đồng hồ)** -> **ZK Clock Style**.
   * Bật công tắc **Enable ZK Clock Styles**.
   * Chọn mẫu đồng hồ bạn yêu thích tại mục **Clock style** (Ví dụ: Style 5 hoặc Style 11).
   * Khởi động lại nhanh SystemUI để áp dụng thay đổi.
