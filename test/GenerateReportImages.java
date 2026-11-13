package test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

public class GenerateReportImages {
    public static void main(String[] args) {
        File outDir = new File("docs_images");
        if (!outDir.exists()) outDir.mkdirs();

        System.out.println("Dang tao toan bo anh so do va giao dien cho bao cao...");

        // 1. Sơ đồ kiến trúc tổng quan (Star Topology)
        generateArchitectureDiagram(new File(outDir, "architecture_diagram.png"));

        // 2. Sơ đồ thực thể CSDL (ERD)
        generateDatabaseERD(new File(outDir, "database_erd.png"));

        // 3. Sơ đồ hoạt động (Activity Flow)
        generateActivityFlow(new File(outDir, "activity_flow_diagram.png"));

        // 4. Giao diện Đăng nhập / Đăng ký
        generateLoginMockup(new File(outDir, "screenshot_login.png"));

        // 5. Giao diện Server Monitor Dashboard
        generateServerMockup(new File(outDir, "screenshot_server.png"));

        // 6. Giao diện Chat Messenger 3 cột & Sticker
        generateChatMockup(new File(outDir, "screenshot_chat.png"));

        // 7. Bảng tổng hợp Sticker Messenger
        generateStickerShowcase(new File(outDir, "sticker_showcase.png"));

        System.out.println("Hoan tat tao toan bo 7 anh tai thu muc docs_images/!");
    }

    private static void enableAntialiasing(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    // =========================================================================
    // 1. SƠ ĐỒ KIẾN TRÚC TỔNG QUAN
    // =========================================================================
    private static void generateArchitectureDiagram(File file) {
        int w = 960, h = 540;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        enableAntialiasing(g2);

        g2.setColor(new Color(248, 250, 252));
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g2.drawString("SƠ ĐỒ KIẾN TRÚC TỔNG QUAN HỆ THỐNG (STAR TOPOLOGY)", 40, 45);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g2.setColor(new Color(100, 116, 139));
        g2.drawString("Mô hình phân tầng Client - Central Server đa giao thức kết nối CSDL SQLite WAL", 40, 68);

        drawContainer(g2, 40, 90, 260, 410, "1. CÁC MÁY TRẠM (CLIENTS)", new Color(239, 246, 255), new Color(191, 219, 254));
        drawContainer(g2, 340, 90, 280, 410, "2. MÁY CHỦ TRUNG TÂM (SERVER CORE)", new Color(245, 243, 255), new Color(221, 214, 254));
        drawContainer(g2, 660, 90, 260, 410, "3. TẦNG DỮ LIỆU (PERSISTENCE)", new Color(236, 253, 245), new Color(167, 243, 208));

        drawBox(g2, 55, 130, 230, 160, "Desktop Client (Swing)",
                new String[]{"• Java Swing Desktop UI", "• SocketClient (TCP Socket)", "• ServerListener (Luồng ngầm)", "• Giao tiếp cổng 5000 liên tục"},
                new Color(37, 99, 235), Color.WHITE);

        drawBox(g2, 55, 310, 230, 160, "Web / Mobile Client (SPA)",
                new String[]{"• HTML5 / CSS3 / Vanilla JS", "• Server-Sent Events (SSE)", "• HTTP POST REST API", "• Cổng 8080 (Responsive Phone)"},
                new Color(14, 165, 233), Color.WHITE);

        drawBox(g2, 355, 130, 250, 110, "TCP Socket Server (5000)",
                new String[]{"• ServerSocket lắng nghe 5000", "• Executors.newCachedThreadPool()", "• ClientHandler (1 luồng / client)"},
                new Color(99, 102, 241), Color.WHITE);

        drawBox(g2, 355, 250, 250, 110, "Web Server & SSE Gateway",
                new String[]{"• HttpServer lắng nghe 8080", "• Phục vụ giao diện Web & Sticker", "• SSE Broadcaster Queue"},
                new Color(139, 92, 246), Color.WHITE);

        drawBox(g2, 355, 370, 250, 110, "RoomManager & Monitor",
                new String[]{"• ConcurrentHashMap Online Users", "• Điều phối Broadcast / Unicast", "• ServerGUI (Dashboard)"},
                new Color(79, 70, 229), Color.WHITE);

        drawBox(g2, 675, 130, 230, 95, "DatabaseConnection (JDBC)",
                new String[]{"• Kết nối JDBC SQLite nhúng", "• Cấu hình WAL (Write-Ahead)", "• Mã hóa băm SHA-256 + Salt"},
                new Color(16, 185, 129), Color.WHITE);

        drawBox(g2, 675, 240, 230, 120, "Tầng Data Access Object (DAO)",
                new String[]{"• UserDAO (Auth, Ban, Status)", "• GroupDAO (Nhóm chat & PIN)", "• MessageDAO (Lưu trữ lịch sử)"},
                new Color(5, 150, 105), Color.WHITE);

        drawBox(g2, 675, 375, 230, 105, "Tập Tin CSDL (chat.db)",
                new String[]{"• users (id, username, hash...)", "• groups & group_members", "• messages (nội dung, reply, sticker)"},
                new Color(4, 120, 87), Color.WHITE);

        g2.setColor(new Color(71, 85, 105));
        g2.setStroke(new BasicStroke(2.0f));
        drawArrow(g2, 285, 200, 355, 185, "TCP Socket (5000)");
        drawArrow(g2, 285, 370, 355, 305, "HTTP / SSE (8080)");
        drawArrow(g2, 605, 185, 675, 175, "SQL");
        drawArrow(g2, 605, 305, 675, 300, "CRUD");
        drawArrow(g2, 790, 360, 790, 375, "");

        g2.dispose();
        try { ImageIO.write(img, "png", file); } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================================
    // 2. SƠ ĐỒ THỰC THỂ CSDL (ERD)
    // =========================================================================
    private static void generateDatabaseERD(File file) {
        int w = 920, h = 480;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        enableAntialiasing(g2);

        g2.setColor(new Color(248, 250, 252));
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g2.drawString("SƠ ĐỒ THỰC THỂ LIÊN KẾT CƠ SỞ DỮ LIỆU (ERD - SQLite WAL)", 40, 45);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g2.setColor(new Color(100, 116, 139));
        g2.drawString("Lược đồ quan hệ 4 bảng lưu trữ trong file chat.db phục vụ toàn bộ hệ thống", 40, 68);

        drawTableCard(g2, 40, 100, 240, 280, "users (Người Dùng)", new String[]{
                "🔑 id : INTEGER [PK, Auto]",
                "⭐ username : TEXT [UNIQUE]",
                "🔒 password_hash : TEXT [SHA-256]",
                "👑 role : TEXT [ADMIN / USER]",
                "🚫 is_banned : INTEGER [0/1]",
                "🕒 created_at : TEXT"
        }, new Color(37, 99, 235));

        drawTableCard(g2, 340, 100, 240, 240, "groups (Nhóm Chat & PIN)", new String[]{
                "🔑 id : INTEGER [PK, Auto]",
                "🏷️ group_name : TEXT",
                "🔢 group_code : TEXT [UNIQUE PIN]",
                "👤 creator_username : TEXT [FK]",
                "🕒 created_at : TEXT"
        }, new Color(124, 58, 237));

        drawTableCard(g2, 340, 360, 240, 100, "group_members", new String[]{
                "🔑 id : INTEGER [PK]",
                "🔗 group_id : INTEGER [FK]",
                "👤 username : TEXT",
                "🕒 joined_at : TEXT"
        }, new Color(13, 148, 136));

        drawTableCard(g2, 640, 100, 240, 290, "messages (Lịch Sử Chat)", new String[]{
                "🔑 id : INTEGER [PK, Auto]",
                "🏷️ group_name : TEXT",
                "👤 sender : TEXT",
                "🎯 recipient : TEXT",
                "💬 content : TEXT (Reply, Sticker)",
                "🕒 timestamp : TEXT",
                "🔒 is_private : INTEGER [0/1]"
        }, new Color(5, 150, 105));

        g2.setColor(new Color(100, 116, 139));
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[]{6.0f, 6.0f}, 0.0f));
        g2.drawLine(280, 200, 340, 200);
        g2.drawLine(460, 340, 460, 360);
        g2.drawLine(580, 200, 640, 200);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString("1 : N (Tạo nhóm)", 270, 190);
        g2.drawString("1 : N (Tin nhắn)", 570, 190);

        g2.dispose();
        try { ImageIO.write(img, "png", file); } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================================
    // 3. SƠ ĐỒ HOẠT ĐỘNG (ACTIVITY FLOW)
    // =========================================================================
    private static void generateActivityFlow(File file) {
        int w = 920, h = 420;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        enableAntialiasing(g2);

        g2.setColor(new Color(248, 250, 252));
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g2.drawString("QUY TRÌNH XÁC THỰC, TẠO NHÓM PIN & ĐỊNH TUYẾN TIN NHẮN", 40, 45);

        drawStepBox(g2, 40, 90, 190, 280, "Bước 1: Xác Thực",
                new Color(59, 130, 246),
                new String[]{"• Nhập tài khoản/mật khẩu", "• Bắt tay Socket TCP", "• Server băm SHA-256", "• Kiểm tra cấm & trùng lặp", "• Trả LOGIN_RES"});

        drawStepBox(g2, 260, 90, 190, 280, "Bước 2: Nạp Nhóm & Lịch Sử",
                new Color(139, 92, 246),
                new String[]{"• Nạp danh sách Nhóm", "• Nạp 30 tin gần nhất", "• Cập nhật Online Users", "• Mở ChatRoomFrame", "• Sẵn sàng nhận sự kiện"});

        drawStepBox(g2, 480, 90, 190, 280, "Bước 3: Tham Gia Mã PIN",
                new Color(16, 185, 129),
                new String[]{"• Tạo nhóm kèm mã PIN", "• Hoặc nhập PIN tham gia", "• Gán quyền Trưởng nhóm", "• Đồng bộ tới toàn mạng", "• Không cần tìm kiếm rác"});

        drawStepBox(g2, 700, 90, 190, 280, "Bước 4: Nhắn Tin & Sticker",
                new Color(245, 158, 11),
                new String[]{"• Gửi tin thường / Quote", "• Chọn Sticker Pusheen động", "• Server lưu SQLite WAL", "• Broadcast TCP + Web SSE", "• Hiển thị thời gian thực"});

        g2.setColor(new Color(71, 85, 105));
        drawArrow(g2, 230, 230, 260, 230, "");
        drawArrow(g2, 450, 230, 480, 230, "");
        drawArrow(g2, 670, 230, 700, 230, "");

        g2.dispose();
        try { ImageIO.write(img, "png", file); } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================================
    // 4. GIAO DIỆN ĐĂNG NHẬP (MOCKUP LOGIN FRAME)
    // =========================================================================
    private static void generateLoginMockup(File file) {
        int w = 500, h = 600;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        enableAntialiasing(g2);

        // Nền Neumorphism
        g2.setColor(new Color(241, 245, 249));
        g2.fillRect(0, 0, w, h);

        // Khung Card chính
        int cardX = 40, cardY = 30, cardW = 420, cardH = 540;
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 24, 24));
        g2.setColor(new Color(226, 232, 240));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 24, 24));

        // Logo
        g2.setColor(new Color(37, 99, 235));
        g2.fillOval(cardX + cardW / 2 - 36, cardY + 30, 72, 72);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI Emoji", Font.BOLD, 36));
        g2.drawString("💬", cardX + cardW / 2 - 20, cardY + 80);

        // Tên ứng dụng
        g2.setColor(new Color(15, 23, 42));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g2.drawString("Chat Room Messenger", cardX + 90, cardY + 135);

        g2.setColor(new Color(100, 116, 139));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.drawString("Hệ thống phòng chat trực tuyến đa luồng Java", cardX + 90, cardY + 158);

        // Trường Username
        g2.setColor(new Color(51, 65, 85));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString("Tên đăng nhập (Username)", cardX + 40, cardY + 200);
        drawInputField(g2, cardX + 40, cardY + 210, 340, 44, "admin");

        // Trường Password
        g2.setColor(new Color(51, 65, 85));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString("Mật khẩu (Password)", cardX + 40, cardY + 280);
        drawInputField(g2, cardX + 40, cardY + 290, 340, 44, "••••••••••••");

        // Nút Đăng nhập
        g2.setColor(new Color(37, 99, 235));
        g2.fill(new RoundRectangle2D.Float(cardX + 40, cardY + 365, 340, 48, 14, 14));
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.drawString("ĐĂNG NHẬP VÀO HỆ THỐNG", cardX + 95, cardY + 395);

        // Nút Đăng ký tài khoản
        g2.setColor(new Color(241, 245, 249));
        g2.fill(new RoundRectangle2D.Float(cardX + 40, cardY + 425, 340, 44, 14, 14));
        g2.setColor(new Color(71, 85, 105));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.drawString("Đăng ký tài khoản mới", cardX + 130, cardY + 452);

        // Footer host info
        g2.setColor(new Color(148, 163, 184));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.drawString("Kết nối tới máy chủ TCP Host: 127.0.0.1 | Port: 5000", cardX + 75, cardY + 510);

        g2.dispose();
        try { ImageIO.write(img, "png", file); } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================================
    // 5. GIAO DIỆN SERVER MONITOR (MOCKUP SERVER GUI)
    // =========================================================================
    private static void generateServerMockup(File file) {
        int w = 920, h = 540;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        enableAntialiasing(g2);

        g2.setColor(new Color(241, 245, 249));
        g2.fillRect(0, 0, w, h);

        // Header bar
        g2.setColor(new Color(30, 41, 59));
        g2.fillRect(0, 0, w, 60);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g2.drawString("🖥️ CHAT SERVER HOST - BẢNG ĐIỀU KHIỂN GIÁM SÁT TRUNG TÂM", 25, 38);

        // Badges trạng thái
        drawStatusBadge(g2, 730, 18, "● SERVER ĐANG CHẠY", new Color(16, 185, 129));

        // 3 Thẻ thống kê nhanh
        drawStatCard(g2, 25, 75, 270, 75, "CỔNG KẾT NỐI", "TCP: 5000  |  Web: 8080", new Color(37, 99, 235));
        drawStatCard(g2, 325, 75, 270, 75, "CLIENTS ONLINE", "3 Tài Khoản Đang Hoạt Động", new Color(16, 185, 129));
        drawStatCard(g2, 625, 75, 270, 75, "TỔNG TIN NHẮN", "148 Gói Tin Đã Định Tuyến", new Color(139, 92, 246));

        // Bảng bên trái: Danh sách kết nối
        drawContainer(g2, 25, 165, 380, 350, "👥 DANH SÁCH CLIENT TRỰC TUYẾN", Color.WHITE, new Color(226, 232, 240));

        // Header bảng
        g2.setColor(new Color(248, 250, 252));
        g2.fillRect(35, 200, 360, 28);
        g2.setColor(new Color(71, 85, 105));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.drawString("USERNAME", 45, 218);
        g2.drawString("VAI TRÒ", 160, 218);
        g2.drawString("ĐỊA CHỈ IP", 270, 218);

        // Các hàng
        drawClientRow(g2, 35, 235, "👑 admin", "ADMIN", "127.0.0.1", new Color(245, 158, 11));
        drawClientRow(g2, 35, 275, "👤 user1", "USER", "192.168.1.15", new Color(37, 99, 235));
        drawClientRow(g2, 35, 315, "👤 user2", "USER", "192.168.1.42", new Color(37, 99, 235));

        // Nút Kick trên Server
        g2.setColor(new Color(239, 68, 68));
        g2.fill(new RoundRectangle2D.Float(45, 465, 160, 36, 10, 10));
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString("⛔ KICK CLIENT", 75, 488);

        // Nút Alert toàn phòng
        g2.setColor(new Color(79, 70, 229));
        g2.fill(new RoundRectangle2D.Float(220, 465, 165, 36, 10, 10));
        g2.setColor(Color.WHITE);
        g2.drawString("📢 PHÁT THÔNG BÁO", 235, 488);

        // Khung bên phải: Server Console Logs
        drawContainer(g2, 425, 165, 470, 350, "📜 NHẬT KÝ MẠNG THỜI GIAN THỰC (SERVER LOGS)", new Color(15, 23, 42), new Color(30, 41, 59));

        g2.setColor(new Color(34, 197, 94));
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        int logY = 210;
        String[] logs = {
                "[09:12:00] [SERVER] ServerSocket dang lang nghe tai cong 5000...",
                "[09:12:01] [WEB] HttpServer lang nghe tai cong 8080 (SSE enabled).",
                "[09:12:05] [TUNNEL] Cloudflare Tunnel: https://chat-demo.trycloudflare.com",
                "[09:13:20] [AUTH] Client 'admin' xac thuc thanh cong (ROLE_ADMIN, 127.0.0.1).",
                "[09:13:21] [GROUP] Nguoi dung 'admin' nap 3 nhom chat va 30 tin nhan cu.",
                "[09:14:02] [AUTH] Client 'user1' ket noi tu 192.168.1.15.",
                "[09:15:30] [GROUP] 'user1' tham gia nhom 'DuAnJava' bang ma PIN [GR9823].",
                "[09:16:10] [MSG] [DuAnJava] admin: [[STICKER|pusheen/love.gif|Tha tim]]",
                "[09:16:11] [BROADCAST] Phat tin nhan toi 2 client trong nhom qua TCP & SSE."
        };
        for (String l : logs) {
            g2.drawString(l, 440, logY);
            logY += 28;
        }

        g2.dispose();
        try { ImageIO.write(img, "png", file); } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================================
    // 6. GIAO DIỆN CHAT MESSENGER 3 CỘT (MOCKUP CHAT ROOM FRAME)
    // =========================================================================
    private static void generateChatMockup(File file) {
        int w = 1040, h = 640;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        enableAntialiasing(g2);

        g2.setColor(new Color(241, 245, 249));
        g2.fillRect(0, 0, w, h);

        // CỘT 1: GROUPS (240px)
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, 240, h);
        g2.setColor(new Color(226, 232, 240));
        g2.drawLine(240, 0, 240, h);

        // Header cột 1
        g2.setColor(new Color(15, 23, 42));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g2.drawString("💬 Nhóm Của Tôi", 20, 36);

        // Nút Tạo nhóm & Tham gia PIN
        drawMiniPillBtn(g2, 20, 52, 95, 28, "✨ Tạo Nhóm", new Color(37, 99, 235));
        drawMiniPillBtn(g2, 125, 52, 95, 28, "🔑 Nhập PIN", new Color(16, 185, 129));

        // Danh sách nhóm
        drawGroupItem(g2, 10, 95, 220, 54, "Friends & Family", "3 online", true, "FREE");
        drawGroupItem(g2, 10, 155, 220, 54, "Nhóm Đồ Án Mạng", "2 online", false, "GR9823");
        drawGroupItem(g2, 10, 215, 220, 54, "Team Gamers", "2 online", false, "GM7722");

        // CỘT 3: MEMBERS & LEADER CONTROLS (240px)
        int rightX = w - 240;
        g2.setColor(Color.WHITE);
        g2.fillRect(rightX, 0, 240, h);
        g2.setColor(new Color(226, 232, 240));
        g2.drawLine(rightX, 0, rightX, h);

        // Header cột 3
        g2.setColor(new Color(15, 23, 42));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.drawString("👥 Thành Viên (3 Online)", rightX + 16, 36);

        // Danh sách members
        drawMemberItem(g2, rightX + 14, 60, "admin (Bạn)", "👑 ADMIN", new Color(245, 158, 11));
        drawMemberItem(g2, rightX + 14, 110, "user1", "👤 USER", new Color(59, 130, 246));
        drawMemberItem(g2, rightX + 14, 160, "user2", "👤 USER", new Color(59, 130, 246));

        // Khung đặc quyền trưởng nhóm / admin
        g2.setColor(new Color(248, 250, 252));
        g2.fill(new RoundRectangle2D.Float(rightX + 12, 230, 216, 380, 14, 14));
        g2.setColor(new Color(226, 232, 240));
        g2.draw(new RoundRectangle2D.Float(rightX + 12, 230, 216, 380, 14, 14));

        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString("👑 ĐẶC QUYỀN TRƯỞNG NHÓM", rightX + 22, 255);

        drawLeaderBtn(g2, rightX + 22, 270, 196, 32, "🔑 Đổi Mã PIN Nhóm", new Color(79, 70, 229));
        drawLeaderBtn(g2, rightX + 22, 310, 196, 32, "🔇 Cấm Chat Thành Viên", new Color(217, 119, 6));
        drawLeaderBtn(g2, rightX + 22, 350, 196, 32, "⛔ Kick Khỏi Nhóm", new Color(225, 29, 72));
        drawLeaderBtn(g2, rightX + 22, 390, 196, 32, "🧹 Xóa Lịch Sử Chat", new Color(100, 116, 139));
        drawLeaderBtn(g2, rightX + 22, 430, 196, 32, "💥 Giải Tán Nhóm", new Color(159, 18, 57));

        // CỘT 2: KHUNG CHAT MESSENGER (CHÍNH GIỮA)
        int midX = 240, midW = rightX - 240;

        // Header cuộc trò chuyện
        g2.setColor(Color.WHITE);
        g2.fillRect(midX, 0, midW, 60);
        g2.setColor(new Color(226, 232, 240));
        g2.drawLine(midX, 60, midX + midW, 60);

        g2.setColor(new Color(15, 23, 42));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
        g2.drawString("Friends & Family", midX + 20, 28);

        g2.setColor(new Color(100, 116, 139));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.drawString("Mã PIN Nhóm: FREE  •  3 thành viên trực tuyến  •  Đồng bộ Web & Swing", midX + 20, 48);

        // Vùng tin nhắn
        g2.setColor(new Color(248, 250, 252));
        g2.fillRect(midX, 61, midW, h - 135);

        // Tin 1: Khác (Bên trái)
        drawChatBubbleLeft(g2, midX + 20, 80, "user1", "USER", "09:14 AM", "Chào cả nhà! Em vừa vào nhóm bằng điện thoại qua link Cloudflare cực mượt!");

        // Tin 2: Mình (Bên phải)
        drawChatBubbleRight(g2, midX + midW - 380, 150, "admin", "09:15 AM", "Chào user1! Đồ án đã nâng cấp kho Sticker Pusheen hoạt hình Messenger rồi đấy!");

        // Tin 3: Sticker Pusheen Hoạt Họa (Thực tế)
        drawStickerBubble(g2, midX + midW - 220, 225, "d:/BtMangmaytinh/web/stickers/pusheen/144884755685791.gif", "Thả tim", "09:16 AM");

        // Tin 4: Reply Quote (Bên trái)
        drawReplyBubbleLeft(g2, midX + 20, 360, "user2", "USER", "admin", "Chào user1! Đồ án đã nâng cấp...", "Em vừa test gửi Sticker và tính năng Reply trên Web rất đẹp luôn!");

        // Footer Input Bar (Dưới cùng)
        int footY = h - 74;
        g2.setColor(Color.WHITE);
        g2.fillRect(midX, footY, midW, 74);
        g2.setColor(new Color(226, 232, 240));
        g2.drawLine(midX, footY, midX + midW, footY);

        // Nút chọn Emoji & Sticker
        g2.setColor(new Color(241, 245, 249));
        g2.fillOval(midX + 20, footY + 16, 42, 42);
        g2.setColor(new Color(100, 116, 139));
        g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        g2.drawString("😊", midX + 31, footY + 43);

        // Ô nhập văn bản
        g2.setColor(new Color(248, 250, 252));
        g2.fill(new RoundRectangle2D.Float(midX + 75, footY + 14, midW - 195, 46, 24, 24));
        g2.setColor(new Color(203, 213, 225));
        g2.draw(new RoundRectangle2D.Float(midX + 75, footY + 14, midW - 195, 46, 24, 24));

        g2.setColor(new Color(148, 163, 184));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g2.drawString("Nhập tin nhắn... (Nhấn Enter để gửi)", midX + 95, footY + 42);

        // Nút Gửi (Send)
        g2.setColor(new Color(37, 99, 235));
        g2.fill(new RoundRectangle2D.Float(midX + midW - 105, footY + 14, 85, 46, 24, 24));
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.drawString("GỬI ➤", midX + midW - 86, footY + 42);

        g2.dispose();
        try { ImageIO.write(img, "png", file); } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================================
    // 7. BẢNG TỔNG HỢP STICKER MESSENGER (STICKER SHOWCASE)
    // =========================================================================
    private static void generateStickerShowcase(File file) {
        int w = 920, h = 480;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        enableAntialiasing(g2);

        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g2.drawString("BỘ SƯU TẬP 62+ STICKER HOẠT HỌA PHONG CÁCH MESSENGER", 40, 45);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g2.setColor(new Color(100, 116, 139));
        g2.drawString("Được nhúng sẵn trong hệ thống, tự động phát ảnh động mượt mà trên cả Web và Desktop Swing", 40, 68);

        // Vẽ 12 sticker mẫu với ảnh GIF thực tế
        String[][] sampleStickers = {
                {"d:/BtMangmaytinh/web/stickers/pusheen/144884739019126.gif", "Chào bạn"},
                {"d:/BtMangmaytinh/web/stickers/pusheen/144884755685791.gif", "Thả tim"},
                {"d:/BtMangmaytinh/web/stickers/pusheen/144884765685790.gif", "Nhảy múa"},
                {"d:/BtMangmaytinh/web/stickers/pusheen/144884775685789.gif", "Cười tít"},
                {"d:/BtMangmaytinh/web/stickers/pusheen/144884792352454.gif", "Làm việc"},
                {"d:/BtMangmaytinh/web/stickers/pusheen/144884815685785.gif", "Ngủ khò"},
                {"d:/BtMangmaytinh/web/stickers/pusheen_eats/554423661312155.gif", "Ăn pizza"},
                {"d:/BtMangmaytinh/web/stickers/pusheen_eats/554423671312154.gif", "Ăn kem"},
                {"d:/BtMangmaytinh/web/stickers/pusheen_eats/554423684645486.gif", "Cà phê"},
                {"d:/BtMangmaytinh/web/stickers/pusheen_eats/554423694645485.gif", "Bánh kem"},
                {"d:/BtMangmaytinh/web/stickers/pusheen/144884825685784.gif", "Thả like"},
                {"d:/BtMangmaytinh/web/stickers/pusheen/144884835685783.gif", "Bắn tim"}
        };

        int startX = 40, startY = 100, boxW = 125, boxH = 160;
        for (int i = 0; i < sampleStickers.length; i++) {
            int r = i / 6;
            int c = i % 6;
            int bx = startX + c * (boxW + 20);
            int by = startY + r * (boxH + 20);

            // Card
            g2.setColor(new Color(248, 250, 252));
            g2.fill(new RoundRectangle2D.Float(bx, by, boxW, boxH, 16, 16));
            g2.setColor(new Color(226, 232, 240));
            g2.draw(new RoundRectangle2D.Float(bx, by, boxW, boxH, 16, 16));

            // Load and draw GIF
            try {
                File gf = new File(sampleStickers[i][0]);
                if (gf.exists()) {
                    Image gImg = ImageIO.read(gf);
                    if (gImg != null) {
                        g2.drawImage(gImg, bx + 18, by + 18, 90, 90, null);
                    }
                }
            } catch (Exception ignored) {}

            // Label
            g2.setColor(new Color(30, 41, 59));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.drawString(sampleStickers[i][1], bx + 36, by + 135);
        }

        g2.dispose();
        try { ImageIO.write(img, "png", file); } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================================
    // HÀM TIỆN ÍCH VẼ GIAO DIỆN MOCKUP
    // =========================================================================
    private static void drawContainer(Graphics2D g2, int x, int y, int w, int h, String title, Color bg, Color border) {
        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 16, 16));
        g2.setColor(border);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 16, 16));

        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.drawString(title, x + 14, y + 26);
    }

    private static void drawBox(Graphics2D g2, int x, int y, int w, int h, String header, String[] items, Color headColor, Color textColor) {
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
        g2.setColor(new Color(226, 232, 240));
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 12, 12));

        g2.setColor(headColor);
        g2.fill(new RoundRectangle2D.Float(x, y, w, 28, 12, 12));
        g2.fillRect(x, y + 16, w, 12);
        g2.setColor(textColor);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString(header, x + 10, y + 19);

        g2.setColor(new Color(51, 65, 85));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        int py = y + 46;
        for (String it : items) {
            g2.drawString(it, x + 10, py);
            py += 17;
        }
    }

    private static void drawTableCard(Graphics2D g2, int x, int y, int w, int h, String title, String[] cols, Color headerBg) {
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
        g2.setColor(new Color(203, 213, 225));
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 12, 12));

        g2.setColor(headerBg);
        g2.fill(new RoundRectangle2D.Float(x, y, w, 32, 12, 12));
        g2.fillRect(x, y + 20, w, 12);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString(title, x + 12, y + 21);

        g2.setColor(new Color(51, 65, 85));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        int py = y + 54;
        for (String c : cols) {
            g2.drawString(c, x + 12, py);
            py += 22;
        }
    }

    private static void drawStepBox(Graphics2D g2, int x, int y, int w, int h, String title, Color color, String[] lines) {
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 14, 14));
        g2.setColor(new Color(226, 232, 240));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 14, 14));

        g2.setColor(color);
        g2.fill(new RoundRectangle2D.Float(x, y, w, 36, 14, 14));
        g2.fillRect(x, y + 22, w, 14);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.drawString(title, x + 12, y + 23);

        g2.setColor(new Color(51, 65, 85));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        int py = y + 65;
        for (String l : lines) {
            g2.drawString(l, x + 12, py);
            py += 24;
        }
    }

    private static void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2, String label) {
        g2.drawLine(x1, y1, x2, y2);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int arrowSize = 6;
        int xArrow1 = (int) (x2 - arrowSize * Math.cos(angle - Math.PI / 6));
        int yArrow1 = (int) (y2 - arrowSize * Math.sin(angle - Math.PI / 6));
        int xArrow2 = (int) (x2 - arrowSize * Math.cos(angle + Math.PI / 6));
        int yArrow2 = (int) (y2 - arrowSize * Math.sin(angle + Math.PI / 6));
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(x2, y2);
        arrowHead.addPoint(xArrow1, yArrow1);
        arrowHead.addPoint(xArrow2, yArrow2);
        g2.fill(arrowHead);

        if (label != null && !label.isEmpty()) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(new Color(71, 85, 105));
            g2.drawString(label, (x1 + x2) / 2 - 20, (y1 + y2) / 2 - 6);
        }
    }

    private static void drawInputField(Graphics2D g2, int x, int y, int w, int h, String val) {
        g2.setColor(new Color(248, 250, 252));
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
        g2.setColor(new Color(203, 213, 225));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g2.drawString(val, x + 14, y + 27);
    }

    private static void drawStatusBadge(Graphics2D g2, int x, int y, String text, Color c) {
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
        g2.fill(new RoundRectangle2D.Float(x, y, 160, 26, 12, 12));
        g2.setColor(c);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.drawString(text, x + 16, y + 17);
    }

    private static void drawStatCard(Graphics2D g2, int x, int y, int w, int h, String label, String val, Color barColor) {
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 14, 14));
        g2.setColor(new Color(226, 232, 240));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 14, 14));

        g2.setColor(barColor);
        g2.fill(new RoundRectangle2D.Float(x, y, 6, h, 4, 4));

        g2.setColor(new Color(100, 116, 139));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.drawString(label, x + 18, y + 24);

        g2.setColor(new Color(15, 23, 42));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2.drawString(val, x + 18, y + 50);
    }

    private static void drawClientRow(Graphics2D g2, int x, int y, String user, String role, String ip, Color badge) {
        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString(user, x + 10, y + 18);

        g2.setColor(badge);
        g2.drawString("[" + role + "]", x + 125, y + 18);

        g2.setColor(new Color(100, 116, 139));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.drawString(ip, x + 235, y + 18);

        g2.setColor(new Color(241, 245, 249));
        g2.drawLine(x + 10, y + 26, x + 350, y + 26);
    }

    private static void drawMiniPillBtn(Graphics2D g2, int x, int y, int w, int h, String txt, Color c) {
        g2.setColor(c);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.drawString(txt, x + 12, y + 18);
    }

    private static void drawGroupItem(Graphics2D g2, int x, int y, int w, int h, String name, String sub, boolean active, String pin) {
        if (active) {
            g2.setColor(new Color(239, 246, 255));
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
            g2.setColor(new Color(191, 219, 254));
            g2.draw(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
        }
        g2.setColor(new Color(15, 23, 42));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString(name, x + 12, y + 22);

        g2.setColor(new Color(100, 116, 139));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.drawString(sub + " • PIN: " + pin, x + 12, y + 40);
    }

    private static void drawMemberItem(Graphics2D g2, int x, int y, String name, String badge, Color bColor) {
        g2.setColor(new Color(241, 245, 249));
        g2.fillOval(x, y, 32, 32);
        g2.setColor(new Color(71, 85, 105));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.drawString(name.substring(0, 1).toUpperCase(), x + 11, y + 21);

        g2.setColor(new Color(15, 23, 42));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString(name, x + 40, y + 16);

        g2.setColor(bColor);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.drawString(badge, x + 40, y + 28);
    }

    private static void drawLeaderBtn(Graphics2D g2, int x, int y, int w, int h, String txt, Color c) {
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 8, 8));
        g2.setColor(new Color(226, 232, 240));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 8, 8));
        g2.setColor(c);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.drawString(txt, x + 12, y + 20);
    }

    private static void drawChatBubbleLeft(Graphics2D g2, int x, int y, String user, String role, String time, String text) {
        g2.setColor(new Color(71, 85, 105));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.drawString(user + " (" + role + ") • " + time, x, y);

        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(x, y + 6, 380, 42, 16, 16));
        g2.setColor(new Color(226, 232, 240));
        g2.draw(new RoundRectangle2D.Float(x, y + 6, 380, 42, 16, 16));

        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.drawString(text, x + 12, y + 32);
    }

    private static void drawChatBubbleRight(Graphics2D g2, int x, int y, String user, String time, String text) {
        g2.setColor(new Color(100, 116, 139));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.drawString("Bạn (" + user + ") • " + time, x + 180, y);

        g2.setColor(new Color(37, 99, 235));
        g2.fill(new RoundRectangle2D.Float(x, y + 6, 360, 44, 16, 16));

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.drawString(text, x + 12, y + 32);
    }

    private static void drawStickerBubble(Graphics2D g2, int x, int y, String path, String label, String time) {
        g2.setColor(new Color(100, 116, 139));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.drawString("Sticker: " + label + " • " + time, x + 40, y);

        try {
            File sf = new File(path);
            if (sf.exists()) {
                Image sImg = ImageIO.read(sf);
                if (sImg != null) {
                    g2.drawImage(sImg, x + 40, y + 6, 105, 105, null);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void drawReplyBubbleLeft(Graphics2D g2, int x, int y, String user, String role, String replyTarget, String snippet, String text) {
        g2.setColor(new Color(71, 85, 105));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.drawString(user + " (" + role + ")", x, y);

        // Quote
        g2.setColor(new Color(241, 245, 249));
        g2.fill(new RoundRectangle2D.Float(x, y + 6, 360, 24, 8, 8));
        g2.setColor(new Color(37, 99, 235));
        g2.fill(new RoundRectangle2D.Float(x, y + 6, 3, 24, 2, 2));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.drawString("↩ @" + replyTarget + ": " + snippet, x + 10, y + 22);

        // Bubble
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(x, y + 34, 400, 38, 16, 16));
        g2.setColor(new Color(226, 232, 240));
        g2.draw(new RoundRectangle2D.Float(x, y + 34, 400, 38, 16, 16));

        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.drawString(text, x + 12, y + 58);
    }
}
