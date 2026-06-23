# MessengerAutoReply (Auto Ship Reply Bot)

**MessengerAutoReply** là một ứng dụng Android Bot tự động trả lời tin nhắn trên Facebook Messenger sử dụng **Android Accessibility Services**. Dự án được thiết kế với tiêu chí: **Tốc độ phản hồi cực nhanh (< 300ms)** và **Đảm bảo 0% lỗi trả lời nhầm (Zero false-reply guarantee)**.

Ứng dụng đặc biệt hữu ích cho các nhóm chat công việc (như nhóm nhận đơn ship, giao hàng, chốt sale...) với hệ thống bộ quy tắc từ khóa (Keyword Rules) linh hoạt.

## 🌟 Tính năng nổi bật

- **Tốc độ siêu tốc (< 300ms):** Xử lý luồng Accessibility Event mượt mà, kết hợp thuật toán tính toán Fingerprint để bắt diện tin nhắn mới ngay lập tức.
- **Zero False-Reply Guarantee (Chống trả lời nhầm):** 
  - Cơ chế Re-verify Node: Tìm lại node UI để xác minh trước khi thao tác vuốt (swipe).
  - Verify Reply Panel: Kiểm tra chính xác tên người gửi và nội dung trích dẫn sau khi vuốt, trước khi tiến hành điền text và gửi.
  - Bỏ qua tin nhắn của chính mình, tin nhắn đã thu hồi, và tin nhắn cũ.
- **Hệ thống Rule Engine (Bộ quy tắc từ khóa):** 
  - Tạo và quản lý nhiều bộ từ khóa (Rule Sets). Chỉ kích hoạt 1 bộ tại một thời điểm.
  - Mỗi quy tắc bao gồm `keywords` (từ khóa kích hoạt) và `excludes` (từ khóa loại trừ).
  - Hỗ trợ text trả lời tùy chỉnh cho từng quy tắc hoặc dùng text mặc định.
- **Giao diện người dùng (UI) trực quan:**
  - **Cấu hình:** Cài đặt tên nhóm, tên bản thân, danh sách người gửi cho phép, và text mặc định.
  - **Bộ từ khóa:** Thêm/sửa/xóa các bộ quy tắc.
  - **Lịch sử:** Xem lại lịch sử các tin nhắn đã trả lời kèm trạng thái thành công/thất bại.
  - **Log Realtime:** Theo dõi log hệ thống theo thời gian thực với màu sắc trực quan.
- **Lưu trữ cục bộ:** Sử dụng **Room Database** để lưu trữ lịch sử, cấu hình và bộ quy tắc.

## 🏗 Cấu trúc dự án

Dự án được xây dựng hoàn toàn bằng **Java + XML** theo mô hình Clean-like.
Các module chính bao gồm:
- **`service/AutoReplyAccessibilityService`**: Dịch vụ Accessibility lắng nghe sự kiện trên màn hình.
- **`engine/`**: 
  - `MessengerDetector`: Nhận diện xem có đang ở đúng màn hình chat mục tiêu hay không.
  - `SnapshotBuilder` & `SnapshotDiffEngine`: Chụp và so sánh sự thay đổi của các tin nhắn hiển thị để phát hiện tin nhắn mới.
  - `ReplyEngine`: Lõi tự động hóa thao tác (Swipe -> Verify -> Nhập text -> Click Gửi).
  - `RuleEngine`: Đối chiếu tin nhắn với tập quy tắc hiện tại.
- **`db/` & `model/`**: Định nghĩa Room Database và cấu trúc dữ liệu (`MessageSnapshot`, `ConversationSnapshot`...).
- **`ui/`**: Giao diện ứng dụng sử dụng `ViewPager2` với 4 Fragments tương ứng với 4 tab.

## 🚀 Luồng hoạt động (State Machine)

1. **DISABLED**: User chưa bật Bot.
2. **WAIT_CHAT**: Đang đợi/chưa ở đúng nhóm chat.
3. **READY**: Đã vào đúng nhóm chat Messenger, chuẩn bị nhận tin nhắn.
4. **REPLYING**: Đang tiến hành quá trình trả lời (Chặn các event mới để tránh xung đột).
   - Lọc tin -> Tìm Node -> Vuốt -> Verify -> Set Text -> Gửi -> Trở về **READY**.

## ⚙️ Hướng dẫn cài đặt và sử dụng

### 1. Cài đặt
- Clone dự án về máy:
  ```bash
  git clone <repository_url>
  ```
- Mở dự án bằng **Android Studio**.
- Build và chạy ứng dụng lên thiết bị thật (Khuyến nghị thiết bị Android có độ trễ thấp).

### 2. Cấp quyền Accessibility
- Lần đầu tiên mở app, bạn cần bật **Accessibility Service** cho ứng dụng trong mục Cài đặt (Settings) của điện thoại.

### 3. Cấu hình Bot
- Mở app, tại tab **Cấu hình**:
  - Nhập **Tên nhóm** chat trên Messenger mà bạn muốn bot hoạt động.
  - Nhập **Tên của bạn** (để bot bỏ qua tin nhắn do bạn gửi).
  - Cài đặt nội dung trả lời mặc định.
- Tại tab **Bộ từ khóa**:
  - Tạo mới một bộ từ khóa.
  - Thêm các quy tắc (Rule) mới với Từ khóa (ví dụ: `ship, nhận`), Từ trừ khóa loại trừ (ví dụ: `hủy`).
  - Bật (Enable) bộ từ khóa đó.
- Bật công tắc **Start** trên ứng dụng.
- Mở Messenger, vào đúng nhóm chat đã cấu hình và giữ màn hình sáng. Bot sẽ tự động xử lý khi có tin nhắn mới thỏa mãn điều kiện.

## 🛠 Công nghệ sử dụng
- **Ngôn ngữ:** Java
- **Giao diện:** XML, Material Design Components, ViewPager2, Navigation Component
- **Cơ sở dữ liệu:** Room Database (SQLite)
- **Kiến trúc dữ liệu:** Gson, Lifecycle ViewModel
- **Core Automation:** Android AccessibilityEvent & GestureDescription

## 🛡 Lưu ý bảo mật và tính ổn định
- Ứng dụng thao tác trực tiếp trên màn hình, không thông qua API của Facebook nên hoàn toàn **không có rủi ro checkpoint/ban tài khoản** từ phía máy chủ.
- Chỉ hoạt động khi thiết bị mở khóa và đang hiển thị trên giao diện của nhóm chat Messenger.

---
*Phiên bản hiện tại: V4 - Tối ưu hóa chống trả lời nhầm tuyệt đối.*
