# Đồ Án Lập Trình Mạng: Hệ Thống Chat Room Nhiều Người (Multi-client Chat Server)

Hệ thống phòng chat trực tuyến đa người dùng thời gian thực (Real-time Multi-client Chat Room) được xây dựng theo mô hình kiến trúc **Client - Server** bằng ngôn ngữ **Java**, hỗ trợ đồng thời **App Desktop Java Swing (Socket TCP Đa luồng)** và **Giao diện Web / Điện thoại (HTTP + Server-Sent Events)** đồng bộ 100% với nhau qua cơ sở dữ liệu **SQLite**.

---

## ✨ Các Tính Năng Chính Hiện Có

- **Tài khoản & Phân quyền chặt chẽ (SQLite + SHA-256)**:
  - Đăng ký tài khoản mới mặc định chỉ cấp quyền `USER`; chỉ có duy nhất 1 tài khoản `ADMIN` hệ thống (`admin`).
  - Lưu trữ bền vững tài khoản, nhóm chat, danh sách thành viên và lịch sử tin nhắn trong SQLite (khởi động lại Server không bị mất dữ liệu).
  - Giao diện Web tự động giữ phiên đăng nhập khi F5/tải lại trang (`sessionStorage` / `localStorage` + `/api/session`) và chỉ thoát khi bấm **Đăng Xuất**.
- **Nhóm Chat Riêng Tư & Mã Định Danh Độc Nhất**:
  - Chỉ hiển thị phòng mặc định `Friends & Family` và những nhóm do chính người dùng tạo hoặc đã tham gia (đã loại bỏ các nhóm mẫu cố định).
  - Mỗi nhóm có **Mã định danh (PIN) riêng không trùng lặp**; người dùng chỉ cần bấm **Tham Gia Nhóm** và nhập mã nhóm để vào thẳng nhóm.
  - Thanh bên hiển thị rõ số lượng thành viên từng nhóm & số người đang Online.
- **Đặc Quyền Trưởng Nhóm**:
  - Người tạo nhóm tự động là **Trưởng nhóm** với quyền: **Kick** thành viên khỏi nhóm, **Chặn / Mở chặn tin nhắn** trong nhóm, **Đổi mã PIN nhóm**, **Xóa lịch sử nhóm**, **Giải tán nhóm**.
  - Khi thành viên bị Kick khỏi nhóm, nhóm đó tự động biến mất ngay khỏi thanh `Groups` của người đó (không làm phiền bằng popup lặp), và người đó vẫn có thể tham gia lại sau bằng mã nhóm nếu muốn.
- **Trải Nghiệm Chat Sinh Động & Đa Thiết Bị**:
  - Hỗ trợ **Trả lời (Reply/Quote)** tin nhắn của người khác trong nhóm.
  - Bảng chọn **Icon / Emoji** và **12 Sticker** sinh động đồng bộ hiển thị đẹp trên cả Desktop Swing lẫn Web.
  - **Responsive Mobile UI**: Khi mở Web trên điện thoại/máy tính bảng, thanh danh sách nhóm (`☰`) và danh sách thành viên / đặc quyền trưởng nhóm (`👥`) tự động thu gọn thành ngăn kéo trượt mượt mà, vừa khít màn hình và bàn phím ảo.

---

## 👥 Phân Chia Vai Trò & Trách Nhiệm Chi Tiết (4 Thành Viên)

| Thành viên | Phụ trách chính | Module & Lớp phụ trách | Nhiệm vụ kỹ thuật |
| :--- | :--- | :--- | :--- |
| **Thành viên 1** | **Core Server, Concurrency & Tích hợp** | `common/Protocol.java`<br>`server/ChatServer.java`<br>`server/ClientHandler.java`<br>`server/RoomManager.java`<br>`server/ui/ServerGUI.java`<br>`server/ServerMain.java` | • Thiết kế giao thức truyền tin (Application Protocol)<br>• Khởi tạo `ServerSocket`, quản lý luồng bằng `ExecutorService` (Thread Pool)<br>• Quản lý danh sách online thread-safe (`ConcurrentHashMap`)<br>• Định tuyến tin nhắn: Broadcast (phòng chung), Unicast (chat riêng 1-1)<br>• Xây dựng giao diện Server Monitor xem log, quản lý kết nối, kick client |
| **Thành viên 2** | **Backend & Security (Auth, Phân quyền, DB)** | `common/User.java`<br>`common/MessagePacket.java`<br>`server/db/DatabaseConnection.java`<br>`server/db/UserDAO.java`<br>`server/db/MessageDAO.java` | • Thiết kế lược đồ CSDL SQLite (`users`, `messages`), bật chế độ WAL<br>• Mã hóa mật khẩu bảo mật chuẩn **SHA-256** kết hợp Salt<br>• Module Đăng nhập / Đăng ký, chống đăng nhập trùng lặp 1 tài khoản ở 2 nơi<br>• Phân quyền RBAC: User thường vs Admin (Kick, Mute, Ban, Alert, Clear History)<br>• Lưu trữ và nạp lại 30 tin nhắn gần nhất khi login |
| **Thành viên 3** | **Client Core (Xử lý Mạng & Luồng dữ liệu)** | `client/network/ChatEventListener.java`<br>`client/network/SocketClient.java`<br>`client/network/ServerListener.java` | • Xây dựng `SocketClient` kết nối TCP Socket tới Server qua IP và Port<br>• Quản lý luồng đọc bất đồng bộ `ServerListener` (Listener Thread) không làm đơ UI<br>• Đóng gói (Serialize) và giải mã (Deserialize) gói tin theo Protocol<br>• Xử lý an toàn ngoại lệ mạng: timeout, mất kết nối, tự đóng socket khi thoát |
| **Thành viên 4** | **Frontend & GUI (Giao diện người dùng)** | `client/ui/LoginFrame.java`<br>`client/ui/ChatRoomFrame.java`<br>`client/ui/ChangePasswordDialog.java`<br>`client/ClientMain.java` | • Thiết kế giao diện Đăng nhập / Đăng ký và cấu hình IP/Port Server<br>• Thiết kế phòng chat Messenger: danh sách online, icon vai trò 👑 Admin / 👤 User<br>• Khung chat HTML hiện đại, phân biệt bong bóng chat của mình, người khác, tin mật 1-1<br>• Thanh công cụ và menu chuột phải Quản trị Admin (Kick, Mute, Ban)<br>• Gắn sự kiện (Event Listener), hỗ trợ phím Enter để gửi |

---

## 📡 Đặc Tả Giao Thức Bản Tin (Application Layer Protocol)

Hệ thống sử dụng giao thức chuỗi văn bản UTF-8 phân tách bởi ký tự gạch đứng `|`:

| Chiều gửi | Header | Cú pháp Gói tin | Ý nghĩa / Xử lý |
| :--- | :--- | :--- | :--- |
| **Client $\rightarrow$ Server** | `REGISTER` | `REGISTER\|username\|password\|role` | Yêu cầu đăng ký tài khoản mới (Server luôn gán mặc định `USER`) |
| **Server $\rightarrow$ Client** | `REGISTER_RES`| `REGISTER_RES\|SUCCESS/FAILED\|thông_báo` | Phản hồi kết quả đăng ký |
| **Client $\rightarrow$ Server** | `LOGIN` | `LOGIN\|username\|password` | Yêu cầu đăng nhập vào hệ thống |
| **Server $\rightarrow$ Client** | `LOGIN_RES` | `LOGIN_RES\|SUCCESS\|role\|username`<br>`LOGIN_RES\|FAILED\|lý_do` | Phản hồi kết quả đăng nhập kèm vai trò quyền hạn |
| **Client $\rightarrow$ Server** | `CREATE_GROUP` | `CREATE_GROUP\|groupName\|groupCode` | Tạo nhóm chat mới kèm mã định danh duy nhất |
| **Client $\rightarrow$ Server** | `JOIN_GROUP` | `JOIN_GROUP\|groupName\|groupCode` | Tham gia nhóm (hỗ trợ nhập chỉ mã nhóm với `groupName` rỗng) |
| **Client $\rightarrow$ Server** | `GET_GROUPS` | `GET_GROUPS` | Lấy danh sách các nhóm người dùng đang tham gia |
| **Server $\rightarrow$ Client** | `GROUP_LIST` | `GROUP_LIST\|g1:creator:count,...` | Cập nhật danh sách nhóm và số lượng thành viên |
| **Client $\rightarrow$ Server** | `MSG_GROUP` / `MSG_ALL` | `MSG_GROUP\|groupName\|nội_dung`<br>`MSG_ALL\|nội_dung` | Gửi tin nhắn nhóm (hỗ trợ cú pháp `[[REPLY\|...]]` và `[[STICKER\|...]]`) |
| **Server $\rightarrow$ Client** | `NEW_MSG` | `NEW_MSG\|groupName\|sender\|role\|time\|nội_dung` | Broadcast tin nhắn mới tới các Client |
| **Client $\rightarrow$ Server** | `MSG_PRIVATE` | `MSG_PRIVATE\|target_username\|nội_dung` | Gửi tin nhắn riêng tư bảo mật 1-1 |
| **Server $\rightarrow$ Client** | `NEW_PRIVATE_MSG`| `NEW_PRIVATE_MSG\|sender\|target\|time\|nội_dung` | Chuyển tiếp tin nhắn riêng tới đích và phản hồi người gửi |
| **Server $\rightarrow$ Client** | `SYSTEM_MSG` | `SYSTEM_MSG\|nội_dung_thông_báo` | Thông báo trạng thái hệ thống |
| **Client $\rightarrow$ Server** | `GET_HISTORY` | `GET_HISTORY\|groupName` | Nạp lịch sử tin nhắn đã lưu của nhóm |
| **Server $\rightarrow$ Client** | `HISTORY` | `HISTORY\|groupName\|count\|item1;;item2;;...` | Trả về lịch sử tin nhắn của nhóm |
| **Client $\rightarrow$ Server** | `GET_USERS` | `GET_USERS` | Yêu cầu cập nhật danh sách người dùng online |
| **Server $\rightarrow$ Client** | `USER_LIST` | `USER_LIST\|u1:ROLE:STATUS,u2:ROLE:STATUS` | Cập nhật danh sách và trạng thái (Active/Muted) |
| **Client(Leader) $\rightarrow$ Server** | `GROUP_CMD` | `GROUP_CMD\|KICK\|groupName\|target`<br>`GROUP_CMD\|MUTE\|groupName\|target`<br>`GROUP_CMD\|UNMUTE\|groupName\|target`<br>`GROUP_CMD\|SET_CODE\|groupName\|newCode`<br>`GROUP_CMD\|CLEAR_HISTORY\|groupName`<br>`GROUP_CMD\|DELETE\|groupName` | Các lệnh quản trị nhóm dành cho Trưởng nhóm / Admin |
| **Server $\rightarrow$ Client** | `GROUP_KICKED` | `GROUP_KICKED\|groupName\|lý_do` | Thông báo rút nhóm ra khỏi danh sách của thành viên bị kick/giải tán |
| **Client(Admin) $\rightarrow$ Server** | `ADMIN_CMD` | `ADMIN_CMD\|KICK\|target\|lý_do`<br>`ADMIN_CMD\|MUTE\|target\|phút`<br>`ADMIN_CMD\|UNMUTE\|target`<br>`ADMIN_CMD\|BAN\|target\|lý_do`<br>`ADMIN_CMD\|ALERT\|nội_dung`<br>`ADMIN_CMD\|CLEAR_HISTORY` | Các lệnh quản trị toàn hệ thống của Admin |
| **Server $\rightarrow$ Client** | `KICKED` | `KICKED\|lý_do` | Buộc Client bị đuổi khỏi hệ thống |
| **Server $\rightarrow$ Client** | `MUTED` | `MUTED\|phút\|lý_do` | Thông báo khóa quyền phát ngôn của Client |
| **Client $\rightarrow$ Server** | `CHANGE_PASS` | `CHANGE_PASS\|mật_khẩu_cũ\|mật_khẩu_mới` | Đổi mật khẩu tài khoản |
| **Client $\rightarrow$ Server** | `LOGOUT` | `LOGOUT` | Đăng xuất an toàn |

---

## 📂 Cấu Trúc Thư Mục Dự Án (Chuẩn Chỉ Không Xung Đột)

```text
MultiClientChatProject/
├── common/                                 # Dùng chung cho Client & Server
│   ├── Protocol.java                       # Định nghĩa hằng số Header giao thức
│   ├── ChatGroup.java                      # Thực thể Nhóm chat & Mã PIN
│   ├── User.java                           # Thực thể người dùng
│   └── MessagePacket.java                  # Thực thể đóng gói tin nhắn
├── server/                                 # Mã nguồn Máy chủ (TV1 & TV2)
│   ├── ChatServer.java                     # Lõi ServerSocket & ThreadPool
│   ├── ClientHandler.java                  # Luồng xử lý cho từng Client
│   ├── RoomManager.java                    # Quản lý danh sách online & định tuyến
│   ├── ServerEventListener.java            # Interface callback sự kiện Server
│   ├── ServerMain.java                     # Điểm chạy Server (hỗ trợ cả GUI và CLI)
│   ├── web/
│   │   └── WebChatServer.java              # Cổng HTTP + SSE đồng bộ Web & Desktop
│   ├── ui/
│   │   └── ServerGUI.java                  # Giao diện giám sát & quản trị Server
│   └── db/
│       ├── DatabaseConnection.java         # Kết nối JDBC SQLite & băm SHA-256
│       ├── UserDAO.java                    # Thao tác CSDL người dùng
│       ├── GroupDAO.java                   # Thao tác CSDL nhóm chat & mã PIN
│       └── MessageDAO.java                 # Thao tác CSDL lịch sử tin nhắn
├── client/                                 # Mã nguồn Máy trạm (TV3 & TV4)
│   ├── ClientMain.java                     # Điểm chạy Client GUI
│   ├── network/
│   │   ├── SocketClient.java               # Kết nối Socket TCP & gửi gói tin
│   │   ├── ServerListener.java             # Luồng nền lắng nghe Server
│   │   └── ChatEventListener.java         # Interface callback sự kiện về UI
│   └── ui/
│       ├── LoginFrame.java                 # Giao diện Đăng nhập & Đăng ký Neumorphism
│       ├── ChatRoomFrame.java              # Giao diện Phòng chat 3 cột & quản trị Admin
│       ├── CreateGroupDialog.java          # Hộp thoại tạo nhóm mới kèm mã PIN
│       ├── JoinGroupDialog.java            # Hộp thoại nhập mã PIN vào nhóm
│       └── ChangePasswordDialog.java       # Hộp thoại đổi mật khẩu
├── web/                                    # Giao diện Web Chat (trình duyệt / điện thoại)
│   ├── index.html
│   ├── style.css
│   └── app.js
├── test/
│   └── SystemIntegrationTest.java          # Bộ kiểm thử tích hợp tự động toàn diện
├── lib/                                    # Thư viện ngoài
│   ├── sqlite-jdbc.jar                     # JDBC Driver cho SQLite
│   ├── slf4j-api.jar
│   └── slf4j-nop.jar
├── Dockerfile                              # Cấu hình đóng gói Docker (deploy Render)
├── compile.bat                             # Script biên dịch toàn bộ dự án
├── run_server.bat                          # Script chạy Server GUI (tự bật cổng 5000 & 8080)
├── run_server_cli.bat                      # Script chạy Server CLI
├── run_client.bat                          # Script chạy Client Desktop GUI
├── run_web_client.bat                      # Script mở Web Chat (http://localhost:8080)
├── run_demo_two_clients.bat                # Script chạy thử nghiệm 1 Server + 2 Client
├── share_link_internet.bat                 # 1-Click bật Server + tạo link gửi bạn bè qua Internet
├── start_and_share.ps1                     # Script hỗ trợ tự kiểm tra cổng & copy link
├── build_jar.bat                           # Đóng gói chat_client.jar & chat_server.jar
└── run_tests.bat                           # Script chạy bài test tự động
```

---

## ⚡ Hướng Dẫn Chạy Cho Người Mới (Nhanh & Dễ Nhất)

### Yêu cầu trước khi chạy
- Máy tính Windows đã cài **JDK 11** trở lên (khuyến nghị Java 17 / 21 / 25). Kiểm tra nhanh bằng cách mở Terminal/CMD gõ `java -version`.
- *(Mã nguồn đã được biên dịch sẵn; nếu bạn vừa sửa code Java, hãy chạy `compile.bat` trước).*

---

### Cách 1: Chạy 1 file duy nhất & gửi link cho người khác chat qua Internet (Khuyên dùng)
Phù hợp khi muốn **gửi link qua Zalo/Messenger** cho bạn bè, giảng viên hoặc dùng điện thoại 4G truy cập ngay lập tức mà không cần cài gì trên máy người nhận:

1. **Nhấp đúp** file `share_link_internet.bat` (hoặc mở Terminal trong thư mục dự án và gõ `.\share_link_internet.bat`).
2. Đợi **3–8 giây** cho hệ thống tự thực hiện hết:
   - Tự bật Server Dashboard (cổng Socket `5000` và Web `8080`).
   - Tự kết nối Cloudflare Tunnel để sinh đường link HTTPS bảo mật (`https://....trycloudflare.com`).
   - **Tự động copy link vào Clipboard** và **tự mở trình duyệt**.
3. Chỉ cần mở Zalo / Messenger và bấm **`Ctrl + V`** để gửi link cho người khác.
4. *(Lưu ý: Giữ nguyên cửa sổ dòng lệnh và Server Dashboard đang mở trong lúc mọi người chat. Khi nào xong việc, bấm `Ctrl + C` để tắt).*

---

### Cách 2: Chạy thử Demo ngay trên 1 máy (App Desktop Java Swing)
Phù hợp để xem nhanh 2 tài khoản nhắn tin qua lại trên cùng 1 máy tính:

1. **Nhấp đúp** file `run_demo_two_clients.bat`.
2. Hệ thống tự bật **1 cửa sổ Server** + **2 cửa sổ Client**.
3. Đăng nhập 2 cửa sổ bằng 2 tài khoản mẫu:
   - Cửa sổ 1: `admin` / `admin123`
   - Cửa sổ 2: `user1` / `123456`
4. Vào phòng chat:
   - Bấm nút **`+`** (hoặc **✨ Tạo Nhóm**) để tạo nhóm mới kèm **mã định danh riêng**.
   - Ở cửa sổ còn lại, bấm nút **🔑 Tham Gia Nhóm**, nhập mã định danh nhóm -> vào nhóm và nhắn tin, Reply, gửi Sticker qua lại.

---

### Cách 3: Chạy riêng lẻ từng thành phần trên máy cá nhân
- **Chỉ bật Server**: Chạy `run_server.bat` (Server tự động lắng nghe cổng TCP `5000` và Web `8080`).
- **Mở Web Chat trên trình duyệt**: Chạy `run_web_client.bat` (hoặc truy cập `http://localhost:8080`).
- **Mở App Desktop Client**: Chạy `run_client.bat`.

---

### Cách 4: Nhiều máy tính / điện thoại kết nối cùng mạng Wi-Fi (LAN)
1. Trên máy làm Server: chạy `run_server.bat` -> nhìn góc trên bên phải Server Dashboard để lấy **IP Mạng LAN** (ví dụ `192.168.1.15`).
2. Trên **điện thoại / máy tính khác** cùng Wi-Fi:
   - **Dùng trình duyệt Web**: Mở Chrome/Safari vào địa chỉ `http://192.168.1.15:8080`.
   - **Dùng App Desktop**: Chạy `run_client.bat` (hoặc gửi file `chat_client.jar` cho máy kia chạy) -> bấm nút bánh răng **`⚙`** ở màn hình Đăng nhập -> nhập Host `192.168.1.15`, Port `5000` -> Lưu và đăng nhập.

---

### Cách 5: Biên dịch lại, Chạy Test & Đóng gói `.jar` (Dành cho người phát triển)
- **Biên dịch toàn bộ mã nguồn**: Chạy `compile.bat` hoặc gõ:
  ```powershell
  javac -encoding UTF-8 -cp ".;lib/*" common/*.java server/*.java server/db/*.java server/web/*.java server/ui/*.java client/*.java client/network/*.java client/ui/*.java test/*.java
  ```
- **Chạy bộ kiểm thử tự động (Integration Tests)**: Chạy `run_tests.bat` hoặc gõ:
  ```powershell
  java -Dfile.encoding=UTF-8 -cp ".;lib/*" test.SystemIntegrationTest
  ```
- **Chạy Server chế độ dòng lệnh (CLI)**: Chạy `run_server_cli.bat` hoặc `java -Dfile.encoding=UTF-8 -cp ".;lib/*" server.ServerMain --cli`.
- **Đóng gói file chạy `.jar`**: Chạy `build_jar.bat` để xuất ra `chat_client.jar` và `chat_server.jar`.
- **Deploy lên Cloud (Render bằng Docker)**: Dự án đã có sẵn `Dockerfile` và `.dockerignore`; đẩy code lên GitHub rồi tạo Web Service chọn **Docker** trên Render (Server tự nhận biến môi trường `PORT`).

---

## 🔑 Tài Khoản & Nhóm Mặc Định Được Seed Sẵn Trong SQLite
- **Tài khoản Quản trị viên duy nhất (ADMIN)**:
  - Username: `admin` | Mật khẩu: `admin123`
  - Quyền hạn: Toàn quyền Kick, Mute, Ban, Gửi thông báo khẩn, Xóa lịch sử.
- **Tài khoản Thành viên thường mẫu (USER)**:
  - Username: `user1` | Mật khẩu: `123456`
  - Quyền hạn: Tạo nhóm, tham gia nhóm bằng mã định danh, chat nhóm (Reply/Icon/Sticker) & nhắn riêng 1-1, xem danh sách thành viên, đổi mật khẩu. Mọi tài khoản tự đăng ký mới đều mặc định mang quyền `USER`.
- **Phòng mặc định duy nhất**: `Friends & Family` (mã PIN mặc định: `1234`, tự động mở sẵn khi vào phòng chat; không còn các nhóm mẫu dư thừa, các nhóm khác đều do người dùng tự tạo và lưu lại).

