package server;

import common.ChatGroup;
import common.MessagePacket;
import common.Protocol;
import common.User;
import server.db.GroupDAO;
import server.db.MessageDAO;
import server.db.UserDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ClientHandler: Xử lý giao tiếp mạng cho từng Client.
 * Hỗ trợ Tạo nhóm mới, Xác thực mã nhóm, Chat nhóm và Chat riêng tư.
 */
public class ClientHandler implements Runnable {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final Socket socket;
    private final RoomManager roomManager;
    private final UserDAO userDAO;
    private final MessageDAO messageDAO;
    private final GroupDAO groupDAO;

    private BufferedReader in;
    private PrintWriter out;

    private String username;
    private String role = Protocol.ROLE_USER;
    private boolean authenticated = false;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, RoomManager roomManager, UserDAO userDAO, MessageDAO messageDAO) {
        this.socket = socket;
        this.roomManager = roomManager;
        this.userDAO = userDAO;
        this.messageDAO = messageDAO;
        this.groupDAO = roomManager.getGroupDAO();
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public boolean isAuthenticated() { return authenticated; }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress() != null ? socket.getRemoteSocketAddress().toString() : "Unknown";
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                processPacket(line);
            }
        } catch (IOException e) {
            // Client ngắt kết nối
        } finally {
            closeConnection();
        }
    }

    private void processPacket(String packet) {
        String[] parts = packet.split(Protocol.DELIMITER_REGEX, 2);
        String command = parts[0];
        String payload = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case Protocol.CMD_LOGIN:
                handleLogin(payload);
                break;
            case Protocol.CMD_REGISTER:
                handleRegister(payload);
                break;
            case Protocol.CMD_CREATE_GROUP:
                handleCreateGroup(payload);
                break;
            case Protocol.CMD_JOIN_GROUP:
                handleJoinGroup(payload);
                break;
            case Protocol.CMD_GET_GROUPS:
                sendGroupList();
                sendMyGroups();
                break;
            case Protocol.CMD_GET_HISTORY:
                if (authenticated) {
                    sendChatHistory(payload.trim().isEmpty() ? Protocol.DEFAULT_GROUP : payload.trim());
                }
                break;
            case Protocol.CMD_GET_GROUP_MEMBERS:
                if (authenticated && !payload.trim().isEmpty()) {
                    sendPacket(roomManager.buildGroupMembersPacket(payload.trim()));
                }
                break;
            case Protocol.CMD_GROUP_CMD:
                handleGroupCommand(payload);
                break;
            case Protocol.CMD_MSG_GROUP:
                handleGroupMessage(payload);
                break;
            case Protocol.CMD_MSG_ALL:
                handlePublicMessage(payload);
                break;
            case Protocol.CMD_MSG_PRIVATE:
                handlePrivateMessage(payload);
                break;
            case Protocol.CMD_ADMIN:
                handleAdminCommand(payload);
                break;
            case Protocol.CMD_CHANGE_PASS:
                handleChangePassword(payload);
                break;
            case Protocol.CMD_GET_USERS:
                roomManager.broadcastUserList();
                break;
            case Protocol.CMD_LOGOUT:
                closeConnection();
                break;
            default:
                sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Lệnh không hợp lệ: " + command);
                break;
        }
    }

    private void handleLogin(String payload) {
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) {
            sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Vui lòng nhập đầy đủ tài khoản và mật khẩu.");
            return;
        }

        String user = parts[0].trim();
        String pass = parts[1].trim();

        User dbUser = userDAO.login(user, pass);
        if (dbUser == null) {
            sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Sai tên đăng nhập hoặc mật khẩu.");
            return;
        }

        if (dbUser.isBanned()) {
            sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tài khoản của bạn đã bị khóa (BANNED).");
            return;
        }

        if (roomManager.isUserOnline(user)) {
            sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tài khoản đang online ở một nơi khác.");
            return;
        }

        this.username = dbUser.getUsername();
        this.role = dbUser.getRole();
        this.authenticated = true;

        sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.SUCCESS + Protocol.DELIMITER + this.role + Protocol.DELIMITER + this.username);
        roomManager.addClient(this.username, this);

        // Nạp lịch sử phòng mặc định
        sendChatHistory("Friends & Family");
        sendGroupList();
        sendMyGroups();
    }

    private void handleRegister(String payload) {
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 3);
        if (parts.length < 2) {
            sendPacket(Protocol.RES_REGISTER + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Vui lòng điền đủ thông tin.");
            return;
        }

        String user = parts[0].trim();
        String pass = parts[1].trim();

        if (user.length() < 3 || pass.length() < 3) {
            sendPacket(Protocol.RES_REGISTER + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tài khoản và mật khẩu phải từ 3 ký tự trở lên.");
            return;
        }

        // Mặc định tài khoản đăng ký mới luôn là USER (chỉ có 1 ADMIN duy nhất)
        boolean ok = userDAO.register(user, pass, Protocol.ROLE_USER);
        if (ok) {
            sendPacket(Protocol.RES_REGISTER + Protocol.DELIMITER + Protocol.SUCCESS + Protocol.DELIMITER + "Đăng ký thành công! Hãy đăng nhập.");
        } else {
            sendPacket(Protocol.RES_REGISTER + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tên đăng nhập đã tồn tại trên hệ thống.");
        }
    }

    /**
     * Tạo nhóm mới có mã bảo vệ
     */
    private void handleCreateGroup(String payload) {
        if (!authenticated) return;
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) {
            sendPacket(Protocol.RES_CREATE_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Vui lòng nhập tên nhóm và mã nhóm.");
            return;
        }

        String groupName = parts[0].trim();
        String groupCode = parts[1].trim();

        if (groupName.isEmpty() || groupCode.isEmpty()) {
            sendPacket(Protocol.RES_CREATE_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tên nhóm và mã bảo vệ không được để trống.");
            return;
        }

        boolean ok = groupDAO.createGroup(groupName, groupCode, this.username);
        if (ok) {
            sendPacket(Protocol.RES_CREATE_GROUP + Protocol.DELIMITER + Protocol.SUCCESS + Protocol.DELIMITER + groupName + Protocol.DELIMITER + groupCode);
            sendMyGroups();
            roomManager.broadcastGroupList();
            roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🎉 " + this.username + " vừa tạo nhóm mới: [" + groupName + "]!");
        } else {
            sendPacket(Protocol.RES_CREATE_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tên nhóm hoặc mã định danh [" + groupCode + "] đã được sử dụng!");
        }
    }

    /**
     * Tham gia nhóm chỉ bằng mã nhóm (hoặc kèm tên nhóm)
     */
    private void handleJoinGroup(String payload) {
        if (!authenticated) return;
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        String groupName = parts.length == 2 ? parts[0].trim() : "";
        String inputCode = (parts.length == 2 ? parts[1] : parts[0]).trim();

        if (inputCode.isEmpty()) {
            sendPacket(Protocol.RES_JOIN_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Vui lòng nhập mã nhóm.");
            return;
        }

        boolean valid;
        if (groupName.isEmpty()) {
            // Tìm trực tiếp tên nhóm theo mã định danh duy nhất
            groupName = groupDAO.findGroupByCode(inputCode);
            valid = (groupName != null);
        } else {
            valid = groupDAO.verifyGroupCode(groupName, inputCode);
        }

        if (valid) {
            groupDAO.addMember(groupName, this.username);
            sendMyGroups();
            sendPacket(Protocol.RES_JOIN_GROUP + Protocol.DELIMITER + Protocol.SUCCESS + Protocol.DELIMITER + groupName);
            sendChatHistory(groupName);
            roomManager.broadcastGroupList();
            roomManager.notifyGroupMembersUpdated(groupName);
        } else {
            sendPacket(Protocol.RES_JOIN_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Mã nhóm không tồn tại hoặc không chính xác!");
        }
    }

    private void handleGroupMessage(String payload) {
        if (!authenticated) return;
        if (roomManager.isUserMuted(this.username)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Bạn đang bị cấm chat! Không thể gửi tin.");
            return;
        }

        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) return;

        String groupName = parts[0].trim();
        String content = parts[1].trim();
        if (content.isEmpty() || "[JOIN_NOTIFY]".equals(content)) return;

        if (!Protocol.DEFAULT_GROUP.equalsIgnoreCase(groupName)
                && !groupDAO.isMember(groupName, this.username)
                && !groupDAO.isGroupLeader(groupName, this.username)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Bạn không còn là thành viên của nhóm [" + groupName + "]!");
            return;
        }

        if (groupDAO.isMutedInGroup(groupName, this.username)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🔇 Bạn đã bị Trưởng nhóm chặn gửi tin trong nhóm [" + groupName + "]!");
            return;
        }

        String time = LocalDateTime.now().format(TIME_FMT);

        MessagePacket msg = new MessagePacket(MessagePacket.TYPE_PUBLIC, this.username, this.role, "ALL", groupName, content);
        msg.setTimestamp(time);
        messageDAO.saveMessage(msg);

        // NEW_MSG|groupName|sender|role|timestamp|content
        String broadcastPacket = Protocol.RES_NEW_MSG + Protocol.DELIMITER
                + groupName + Protocol.DELIMITER
                + this.username + Protocol.DELIMITER
                + this.role + Protocol.DELIMITER
                + time + Protocol.DELIMITER
                + content;

        roomManager.broadcastToGroup(groupName, broadcastPacket);
    }

    private void handlePublicMessage(String content) {
        handleGroupMessage("Friends & Family" + Protocol.DELIMITER + content);
    }

    private void handlePrivateMessage(String payload) {
        if (!authenticated) return;
        if (roomManager.isUserMuted(this.username)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Bạn đang bị cấm chat!");
            return;
        }

        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) return;

        String target = parts[0].trim();
        String content = parts[1].trim();

        if (target.equalsIgnoreCase(this.username)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Không thể tự gửi tin nhắn riêng cho chính mình.");
            return;
        }

        String time = LocalDateTime.now().format(TIME_FMT);
        MessagePacket msg = new MessagePacket(MessagePacket.TYPE_PRIVATE, this.username, this.role, target, content);
        msg.setTimestamp(time);
        messageDAO.saveMessage(msg);

        String packet = Protocol.RES_NEW_PRIVATE_MSG + Protocol.DELIMITER
                + this.username + Protocol.DELIMITER
                + target + Protocol.DELIMITER
                + time + Protocol.DELIMITER
                + content;

        roomManager.sendPrivate(this.username, target, packet, packet);
    }

    private void handleAdminCommand(String payload) {
        if (!authenticated || !Protocol.ROLE_ADMIN.equalsIgnoreCase(this.role)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "LỖI: Bạn không có quyền quản trị viên!");
            return;
        }

        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 3);
        if (parts.length < 1) return;

        String action = parts[0].toUpperCase();
        String target = parts.length > 1 ? parts[1].trim() : "";
        String extra = parts.length > 2 ? parts[2].trim() : "";

        switch (action) {
            case Protocol.ACT_KICK:
                if (!target.isEmpty()) roomManager.kickUser(target, extra.isEmpty() ? "Bị Admin đuổi" : extra);
                break;
            case Protocol.ACT_MUTE:
                if (!target.isEmpty()) {
                    int mins = 5;
                    try { if (!extra.isEmpty()) mins = Integer.parseInt(extra); } catch (Exception ignored) {}
                    roomManager.muteUser(target, mins);
                }
                break;
            case Protocol.ACT_UNMUTE:
                if (!target.isEmpty()) roomManager.unmuteUser(target);
                break;
            case Protocol.ACT_BAN:
                if (!target.isEmpty()) {
                    userDAO.updateStatus(target, Protocol.STATUS_BANNED);
                    roomManager.kickUser(target, "Tài khoản bị khóa vĩnh viễn.");
                }
                break;
            case Protocol.ACT_ALERT:
                String alertMsg = extra.isEmpty() ? target : (target + " - " + extra);
                roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "📢 [THÔNG BÁO TỪ ADMIN]: " + alertMsg);
                break;
            case Protocol.ACT_CLEAR_HISTORY:
                messageDAO.clearHistory();
                roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🧹 Admin đã xóa toàn bộ lịch sử phòng chat.");
                break;
            default:
                break;
        }
    }

    private void handleGroupCommand(String payload) {
        if (!authenticated) return;
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 3);
        if (parts.length < 2) return;

        String action = parts[0].toUpperCase();
        String groupName = parts[1].trim();
        String extra = parts.length > 2 ? parts[2].trim() : "";

        boolean isLeader = groupDAO.isGroupLeader(groupName, this.username) || Protocol.ROLE_ADMIN.equalsIgnoreCase(this.role);
        if (!isLeader) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "LỖI: Chỉ Trưởng nhóm [" + groupName + "] mới có quyền thực hiện thao tác này!");
            return;
        }

        switch (action) {
            case Protocol.GACT_KICK:
                if (!extra.isEmpty() && !extra.equalsIgnoreCase(this.username)) {
                    groupDAO.removeMember(groupName, extra);
                    roomManager.notifyGroupKicked(extra, groupName, "Bạn đã bị Trưởng nhóm [" + this.username + "] mời khỏi nhóm [" + groupName + "].");
                    roomManager.broadcastGroupList();
                    roomManager.notifyGroupMembersUpdated(groupName);
                }
                break;
            case Protocol.GACT_MUTE:
                if (!extra.isEmpty() && !extra.equalsIgnoreCase(this.username)) {
                    groupDAO.muteInGroup(groupName, extra, this.username);
                    roomManager.notifyGroupMembersUpdated(groupName);
                    roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🔇 Trưởng nhóm " + this.username + " đã chặn @" + extra + " nhắn tin trong nhóm [" + groupName + "].");
                }
                break;
            case Protocol.GACT_UNMUTE:
                if (!extra.isEmpty()) {
                    groupDAO.unmuteInGroup(groupName, extra);
                    roomManager.notifyGroupMembersUpdated(groupName);
                    roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🔊 Trưởng nhóm " + this.username + " đã mở chặn chat cho @" + extra + " trong nhóm [" + groupName + "].");
                }
                break;
            case Protocol.GACT_SET_CODE:
                if (!extra.isEmpty() && groupDAO.updateGroupCode(groupName, extra)) {
                    sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🔑 Đã đổi mã PIN nhóm [" + groupName + "] thành: " + extra);
                }
                break;
            case Protocol.GACT_CLEAR_HISTORY:
                messageDAO.clearGroupHistory(groupName);
                roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🧹 Trưởng nhóm " + this.username + " đã xóa lịch sử tin nhắn của nhóm [" + groupName + "].");
                break;
            case Protocol.GACT_DELETE:
                if (!Protocol.DEFAULT_GROUP.equalsIgnoreCase(groupName) && groupDAO.deleteGroup(groupName)) {
                    roomManager.broadcast(Protocol.NOTIFY_GROUP_KICKED + Protocol.DELIMITER + groupName + Protocol.DELIMITER + "Nhóm [" + groupName + "] đã bị Trưởng nhóm giải tán.");
                    roomManager.broadcastGroupList();
                    roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🗑️ Nhóm [" + groupName + "] đã bị Trưởng nhóm " + this.username + " giải tán.");
                }
                break;
            default:
                break;
        }
    }

    private void handleChangePassword(String payload) {
        if (!authenticated) return;
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) return;
        boolean ok = userDAO.changePassword(this.username, parts[0], parts[1]);
        sendPacket(Protocol.RES_CHANGE_PASS + Protocol.DELIMITER + (ok ? Protocol.SUCCESS : Protocol.FAILED) + Protocol.DELIMITER + (ok ? "Đổi mật khẩu thành công!" : "Mật khẩu cũ không đúng."));
    }

    private void sendChatHistory(String groupName) {
        List<MessagePacket> history = messageDAO.getGroupMessages(groupName, 30);
        if (history.isEmpty()) return;

        StringBuilder sb = new StringBuilder(Protocol.RES_HISTORY).append(Protocol.DELIMITER);
        sb.append(groupName).append(Protocol.DELIMITER);
        sb.append(history.size()).append(Protocol.DELIMITER);
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append(";;");
            sb.append(history.get(i).encodeForHistory());
        }
        sendPacket(sb.toString());
    }

    void sendGroupList() {
        if (!authenticated || username == null) return;
        List<ChatGroup> list = groupDAO.getUserGroups(this.username);
        StringBuilder sb = new StringBuilder(Protocol.RES_GROUP_LIST).append(Protocol.DELIMITER);
        boolean first = true;
        for (ChatGroup g : list) {
            if (!first) sb.append(",");
            sb.append(g.getGroupName()).append(":").append(g.getMemberCount()).append(":").append(g.getCreator());
            first = false;
        }
        sendPacket(sb.toString());
    }

    private void sendMyGroups() {
        if (!authenticated || username == null) return;
        List<String> joined = groupDAO.getJoinedGroups(this.username);
        StringBuilder sb = new StringBuilder(Protocol.RES_MY_GROUPS).append(Protocol.DELIMITER);
        for (int i = 0; i < joined.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(joined.get(i));
        }
        sendPacket(sb.toString());
    }

    public synchronized void sendPacket(String packet) {
        if (out != null && !socket.isClosed()) {
            out.println(packet);
        }
    }

    public void closeConnection() {
        running = false;
        if (authenticated && username != null) {
            roomManager.removeClient(username, true);
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        try {
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException ignored) {}
    }
}
