package server.db;

import common.ChatGroup;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * GroupDAO: Thao tác CSDL về Nhóm Chat và Mã Nhóm (chat_groups & group_members).
 */
public class GroupDAO {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Kiểm tra mã định danh nhóm đã được sử dụng bởi nhóm khác chưa
     */
    public boolean isCodeExists(String groupCode) {
        if (groupCode == null || groupCode.trim().isEmpty()) return false;
        String sql = "SELECT 1 FROM chat_groups WHERE group_code = ? AND group_name != 'Friends & Family'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Tìm tên nhóm từ mã định danh nhóm duy nhất
     */
    public String findGroupByCode(String inputCode) {
        if (inputCode == null || inputCode.trim().isEmpty()) return null;
        String sql = "SELECT group_name FROM chat_groups WHERE group_code = ? AND group_name != 'Friends & Family'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, inputCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("group_name");
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi tìm nhóm theo mã: " + e.getMessage());
        }
        return null;
    }

    /**
     * Tạo nhóm mới có mã bảo vệ (tên nhóm và mã nhóm đều duy nhất)
     */
    public boolean createGroup(String groupName, String groupCode, String creator) {
        if (groupName == null || groupName.trim().isEmpty() || groupCode == null || groupCode.trim().isEmpty()) {
            return false;
        }
        groupName = groupName.trim();
        groupCode = groupCode.trim();
        if (isCodeExists(groupCode)) {
            return false;
        }
        String now = LocalDateTime.now().format(FMT);

        String sql = "INSERT INTO chat_groups (group_name, group_code, creator, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, groupCode);
            ps.setString(3, creator);
            ps.setString(4, now);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                // Thêm người tạo làm thành viên đầu tiên
                addMember(groupName, creator);
                return true;
            }
        } catch (SQLException e) {
            // Tên nhóm đã tồn tại
        }
        return false;
    }

    /**
     * Xác thực mã nhóm khi người dùng muốn tham gia
     */
    public boolean verifyGroupCode(String groupName, String inputCode) {
        if (groupName == null || inputCode == null) return false;
        String sql = "SELECT group_code FROM chat_groups WHERE group_name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String actualCode = rs.getString("group_code");
                    return actualCode.trim().equals(inputCode.trim());
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi kiểm tra mã nhóm: " + e.getMessage());
        }
        return false;
    }

    /**
     * Thêm thành viên vào nhóm sau khi nhập đúng mã
     */
    public boolean addMember(String groupName, String username) {
        String now = LocalDateTime.now().format(FMT);
        String sql = "INSERT OR IGNORE INTO group_members (group_name, username, joined_at) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, username);
            ps.setString(3, now);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Kiểm tra người dùng đã tham gia nhóm này chưa
     */
    public boolean isMember(String groupName, String username) {
        String sql = "SELECT 1 FROM group_members WHERE group_name = ? AND username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Lấy danh sách tất cả các nhóm kèm số lượng thành viên
     */
    public List<ChatGroup> getAllGroups() {
        List<ChatGroup> list = new ArrayList<>();
        String sql = "SELECT g.id, g.group_name, g.group_code, g.creator, g.created_at, "
                + "(SELECT COUNT(*) FROM group_members m WHERE m.group_name = g.group_name) as member_count "
                + "FROM chat_groups g ORDER BY g.id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new ChatGroup(
                        rs.getInt("id"),
                        rs.getString("group_name"),
                        rs.getString("group_code"),
                        rs.getString("creator"),
                        rs.getString("created_at"),
                        Math.max(1, rs.getInt("member_count"))
                ));
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi tải danh sách nhóm: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy danh sách nhóm mà người dùng đang là thành viên hoặc người tạo
     */
    public List<ChatGroup> getUserGroups(String username) {
        List<ChatGroup> list = new ArrayList<>();
        if (username == null || username.trim().isEmpty()) return list;
        String u = username.trim();
        String sql = "SELECT g.id, g.group_name, g.group_code, g.creator, g.created_at, "
                + "(SELECT COUNT(*) FROM group_members m WHERE m.group_name = g.group_name) as member_count "
                + "FROM chat_groups g "
                + "WHERE g.group_name = 'Friends & Family' "
                + "   OR g.creator = ? "
                + "   OR EXISTS (SELECT 1 FROM group_members m2 WHERE m2.group_name = g.group_name AND m2.username = ?) "
                + "ORDER BY g.id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u);
            ps.setString(2, u);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ChatGroup(
                            rs.getInt("id"),
                            rs.getString("group_name"),
                            rs.getString("group_code"),
                            rs.getString("creator"),
                            rs.getString("created_at"),
                            Math.max(1, rs.getInt("member_count"))
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi tải nhóm của user: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy danh sách tên các nhóm mà người dùng đã tham gia hoặc tạo ra
     */
    public List<String> getJoinedGroups(String username) {
        List<String> list = new ArrayList<>();
        if (username == null || username.trim().isEmpty()) return list;
        String sql = "SELECT DISTINCT group_name FROM group_members WHERE username = ? "
                + "UNION SELECT DISTINCT group_name FROM chat_groups WHERE creator = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("group_name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi tải nhóm đã tham gia: " + e.getMessage());
        }
        return list;
    }

    public String getGroupCreator(String groupName) {
        String sql = "SELECT creator FROM chat_groups WHERE group_name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("creator");
            }
        } catch (SQLException ignored) {}
        return "";
    }

    public String getGroupCode(String groupName) {
        String sql = "SELECT group_code FROM chat_groups WHERE group_name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("group_code");
            }
        } catch (SQLException ignored) {}
        return "";
    }

    public boolean isGroupLeader(String groupName, String username) {
        if (groupName == null || username == null) return false;
        String creator = getGroupCreator(groupName.trim());
        return creator != null && !creator.isEmpty() && creator.equalsIgnoreCase(username.trim());
    }

    public List<String> getGroupMembers(String groupName) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT username FROM group_members WHERE group_name = ? "
                + "UNION SELECT creator FROM chat_groups WHERE group_name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi lấy thành viên nhóm: " + e.getMessage());
        }
        return list;
    }

    public boolean removeMember(String groupName, String username) {
        String sql = "DELETE FROM group_members WHERE group_name = ? AND username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean muteInGroup(String groupName, String username, String mutedBy) {
        String now = LocalDateTime.now().format(FMT);
        String sql = "INSERT OR REPLACE INTO group_mutes (group_name, username, muted_by, muted_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, username);
            ps.setString(3, mutedBy);
            ps.setString(4, now);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean unmuteInGroup(String groupName, String username) {
        String sql = "DELETE FROM group_mutes WHERE group_name = ? AND username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isMutedInGroup(String groupName, String username) {
        String sql = "SELECT 1 FROM group_mutes WHERE group_name = ? AND username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean updateGroupCode(String groupName, String newCode) {
        if (newCode == null || newCode.trim().isEmpty()) return false;
        String trimmed = newCode.trim();
        if (isCodeExists(trimmed) && !trimmed.equals(getGroupCode(groupName))) {
            return false;
        }
        String sql = "UPDATE chat_groups SET group_code = ? WHERE group_name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trimmed);
            ps.setString(2, groupName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean deleteGroup(String groupName) {
        if ("Friends & Family".equalsIgnoreCase(groupName)) return false;
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM group_mutes WHERE group_name = ?")) {
                ps.setString(1, groupName);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM group_members WHERE group_name = ?")) {
                ps.setString(1, groupName);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM messages WHERE group_name = ?")) {
                ps.setString(1, groupName);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chat_groups WHERE group_name = ?")) {
                ps.setString(1, groupName);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }
}

