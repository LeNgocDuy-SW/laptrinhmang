package server.db;

import common.Protocol;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DatabaseConnection: Quản lý kết nối JDBC SQLite (chat.db).
 * Tạo bảng users, messages, chat_groups, group_members.
 */
public class DatabaseConnection {
    private static final String SALT = "LTM_SECURE_SALT_2026";

    private static String getDbUrl() {
        String dbPath = System.getProperty("chat.db.path", "chat.db");
        return "jdbc:sqlite:" + dbPath;
    }

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            initDatabase();
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Không tìm thấy SQLite JDBC Driver!");
        }
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(getDbUrl());
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
            stmt.execute("PRAGMA busy_timeout=5000;");
        }
        return conn;
    }

    /**
     * Đồng bộ dữ liệu từ file WAL xuống file DB chính khi tắt Server.
     */
    public static void checkpoint() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
        } catch (SQLException ignored) {}
    }

    public static void initDatabase() {
        String sqlCreateUsers = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT UNIQUE NOT NULL, "
                + "password_hash TEXT NOT NULL, "
                + "role TEXT NOT NULL DEFAULT 'USER', "
                + "status TEXT NOT NULL DEFAULT 'ACTIVE', "
                + "created_at TEXT NOT NULL"
                + ");";

        String sqlCreateMessages = "CREATE TABLE IF NOT EXISTS messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "group_name TEXT NOT NULL DEFAULT 'Friends & Family', "
                + "type TEXT NOT NULL DEFAULT 'PUBLIC', "
                + "sender TEXT NOT NULL, "
                + "sender_role TEXT NOT NULL DEFAULT 'USER', "
                + "receiver TEXT NOT NULL DEFAULT 'ALL', "
                + "content TEXT NOT NULL, "
                + "timestamp TEXT NOT NULL"
                + ");";

        String sqlCreateGroups = "CREATE TABLE IF NOT EXISTS chat_groups ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "group_name TEXT UNIQUE NOT NULL, "
                + "group_code TEXT NOT NULL, "
                + "creator TEXT NOT NULL, "
                + "created_at TEXT NOT NULL"
                + ");";

        String sqlCreateMembers = "CREATE TABLE IF NOT EXISTS group_members ("
                + "group_name TEXT NOT NULL, "
                + "username TEXT NOT NULL, "
                + "joined_at TEXT NOT NULL, "
                + "PRIMARY KEY (group_name, username)"
                + ");";

        String sqlCreateGroupMutes = "CREATE TABLE IF NOT EXISTS group_mutes ("
                + "group_name TEXT NOT NULL, "
                + "username TEXT NOT NULL, "
                + "muted_by TEXT NOT NULL, "
                + "muted_at TEXT NOT NULL, "
                + "PRIMARY KEY (group_name, username)"
                + ");";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlCreateUsers);
            stmt.execute(sqlCreateMessages);
            try {
                stmt.execute("ALTER TABLE messages ADD COLUMN group_name TEXT NOT NULL DEFAULT 'Friends & Family';");
            } catch (SQLException ignored) {
                // Column đã tồn tại
            }
            stmt.execute(sqlCreateGroups);
            stmt.execute(sqlCreateMembers);
            stmt.execute(sqlCreateGroupMutes);
            // Xóa 4 nhóm mẫu có sẵn nếu tồn tại, chỉ giữ phòng mặc định và nhóm do người dùng tạo
            stmt.execute("DELETE FROM chat_groups WHERE group_name IN ('CoCuit', 'Weekend Meetup', 'Colleagues Meetup', 'Office Friends') AND creator = 'admin';");
            stmt.execute("DELETE FROM group_members WHERE group_name IN ('CoCuit', 'Weekend Meetup', 'Colleagues Meetup', 'Office Friends');");
            seedDefaultData(conn);
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi khởi tạo database: " + e.getMessage());
        }
    }

    private static void seedDefaultData(Connection conn) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String adminPassHash = hashPassword("admin123");
        String userPassHash = hashPassword("123456");

        String insertAdmin = "INSERT OR IGNORE INTO users (username, password_hash, role, status, created_at) "
                + "VALUES ('admin', '" + adminPassHash + "', '" + Protocol.ROLE_ADMIN + "', '" + Protocol.STATUS_ACTIVE + "', '" + now + "');";

        String insertUser1 = "INSERT OR IGNORE INTO users (username, password_hash, role, status, created_at) "
                + "VALUES ('user1', '" + userPassHash + "', '" + Protocol.ROLE_USER + "', '" + Protocol.STATUS_ACTIVE + "', '" + now + "');";

        // Chỉ giữ 1 phòng mặc định chung, không seed thêm 4 nhóm mẫu nữa
        String insertGroup1 = "INSERT OR IGNORE INTO chat_groups (group_name, group_code, creator, created_at) "
                + "VALUES ('Friends & Family', '" + Protocol.DEFAULT_GROUP_CODE + "', 'admin', '" + now + "');";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(insertAdmin);
            stmt.execute(insertUser1);
            stmt.execute(insertGroup1);
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi seed data: " + e.getMessage());
        }
    }

    public static String hashPassword(String password) {
        if (password == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((password + SALT).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi SHA-256", e);
        }
    }
}
