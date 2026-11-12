package test;

import client.network.ChatEventListener;
import client.network.SocketClient;
import common.MessagePacket;
import common.Protocol;
import common.User;
import server.ChatServer;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SystemIntegrationTest: Kiểm thử tích hợp toàn diện hệ thống Chat Room:
 * 1. Khởi động Server + SQLite Database
 * 2. Đăng ký người dùng mới
 * 3. Đăng nhập Admin & User
 * 4. Gửi & Nhận tin nhắn phòng chung (Broadcast)
 * 5. Gửi & Nhận tin nhắn riêng tư 1-1 (Unicast)
 * 6. Kiểm thử phân quyền Admin: Mute, Kick, Ban
 * 7. Lưu trữ và nạp lịch sử tin nhắn
 */
public class SystemIntegrationTest {
    private static final int TEST_PORT = 5005;
    private static final String TEST_DB = "chat_test.db";

    private static void cleanupTestDb() {
        for (String suffix : new String[]{"", "-wal", "-shm"}) {
            new java.io.File(TEST_DB + suffix).delete();
        }
    }

    public static void main(String[] args) throws Exception {
        // Dùng CSDL test riêng để không làm mất dữ liệu thật trong chat.db
        System.setProperty("chat.db.path", TEST_DB);
        cleanupTestDb();

        System.out.println("==================================================");
        System.out.println("   BẮT ĐẦU KIỂM THỬ TÍCH HỢP HỆ THỐNG CHAT ROOM   ");
        System.out.println("==================================================");

        // 1. Khởi động Server (dùng cổng riêng để không đụng Server thật nếu đang chạy)
        ChatServer server = new ChatServer(TEST_PORT, 8085);
        boolean started = server.startServer();
        assertCondition(started, "Khởi động ChatServer tại cổng " + TEST_PORT);

        Thread.sleep(500);

        // 2. Client 1 (Alice) đăng ký tài khoản
        SocketClient clientAlice = new SocketClient();
        CountDownLatch regLatch = new CountDownLatch(1);
        AtomicBoolean regSuccess = new AtomicBoolean(false);

        boolean connOk = clientAlice.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onRegisterResult(boolean success, String message) {
                regSuccess.set(success);
                regLatch.countDown();
            }
        });

        clientAlice.register("alice_test", "alice123", Protocol.ROLE_ADMIN);
        boolean regDone = regLatch.await(3, TimeUnit.SECONDS);
        assertCondition(regDone, "Nhận phản hồi đăng ký từ Server");
        clientAlice.disconnect();

        Thread.sleep(300);

        // 3. Client Admin (admin / admin123) đăng nhập
        SocketClient clientAdmin = new SocketClient();
        CountDownLatch adminLoginLatch = new CountDownLatch(1);
        AtomicBoolean adminLoginOk = new AtomicBoolean(false);
        AtomicReference<String> adminRole = new AtomicReference<>("");

        boolean adminConn = clientAdmin.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onLoginResult(boolean success, String role, String username, String message) {
                adminLoginOk.set(success);
                adminRole.set(role);
                adminLoginLatch.countDown();
            }
        });

        clientAdmin.login("admin", "admin123");
        boolean adminLoggedIn = adminLoginLatch.await(3, TimeUnit.SECONDS);
        assertCondition(adminLoggedIn && adminLoginOk.get(), "Admin đăng nhập thành công");
        assertCondition(Protocol.ROLE_ADMIN.equalsIgnoreCase(adminRole.get()), "Quyền hạn của Admin chuẩn ROLE_ADMIN");

        // 4. Client Alice đăng nhập
        clientAlice = new SocketClient();
        CountDownLatch aliceLoginLatch = new CountDownLatch(1);
        AtomicBoolean aliceLoginOk = new AtomicBoolean(false);
        AtomicReference<String> aliceRole = new AtomicReference<>("");

        boolean aliceConn = clientAlice.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onLoginResult(boolean success, String role, String username, String message) {
                aliceLoginOk.set(success);
                aliceRole.set(role);
                aliceLoginLatch.countDown();
            }
        });

        clientAlice.login("alice_test", "alice123");
        boolean aliceLoggedIn = aliceLoginLatch.await(3, TimeUnit.SECONDS);
        assertCondition(aliceLoggedIn && aliceLoginOk.get(), "Alice đăng nhập thành công");
        assertCondition(Protocol.ROLE_USER.equalsIgnoreCase(aliceRole.get()), "Tài khoản đăng ký mới luôn mặc định là ROLE_USER");

        // Đợi đồng bộ danh sách online
        Thread.sleep(400);
        assertCondition(server.getRoomManager().getOnlineCount() == 2, "Server ghi nhận chính xác 2 Client đang online");

        // 5. Test gửi tin nhắn Broadcast (Alice -> All)
        CountDownLatch broadcastLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMsg = new AtomicReference<>("");

        // Cập nhật listener của Admin để đón tin
        SocketClient finalClientAdmin = clientAdmin;
        finalClientAdmin.disconnect();

        CountDownLatch adminReLoginLatch = new CountDownLatch(1);
        CountDownLatch msgArrivedLatch = new CountDownLatch(1);

        clientAdmin = new SocketClient();
        clientAdmin.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onLoginResult(boolean success, String role, String username, String message) {
                adminReLoginLatch.countDown();
            }
            @Override
            public void onPublicMessageReceived(String sender, String role, String timestamp, String content) {
                if ("alice_test".equals(sender)) {
                    receivedMsg.set(content);
                    msgArrivedLatch.countDown();
                }
            }
            @Override
            public void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content) {
                if ("alice_test".equals(sender)) {
                    receivedMsg.set(content);
                    msgArrivedLatch.countDown();
                }
            }
        });
        clientAdmin.login("admin", "admin123");
        adminReLoginLatch.await(3, TimeUnit.SECONDS);

        Thread.sleep(300);
        String testContent = "Xin chao moi nguoi trong phong!";
        clientAlice.sendPublicMessage(testContent);

        boolean msgReceived = msgArrivedLatch.await(3, TimeUnit.SECONDS);
        assertCondition(msgReceived, "Admin nhận được tin nhắn Broadcast từ Alice");
        assertCondition(testContent.equals(receivedMsg.get()), "Nội dung tin nhắn Broadcast khớp chính xác");

        // 6. Test tin nhắn riêng tư (Admin -> Alice 1-1)
        CountDownLatch privateLatch = new CountDownLatch(1);
        AtomicReference<String> privateMsg = new AtomicReference<>("");

        // Cập nhật listener của Alice để đón tin nhắn riêng
        final SocketClient activeAlice = clientAlice;
        activeAlice.disconnect();

        CountDownLatch aliceReLoginLatch = new CountDownLatch(1);
        clientAlice = new SocketClient();
        clientAlice.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onLoginResult(boolean success, String role, String username, String message) {
                aliceReLoginLatch.countDown();
            }
            @Override
            public void onPrivateMessageReceived(String sender, String target, String timestamp, String content) {
                if ("admin".equals(sender)) {
                    privateMsg.set(content);
                    privateLatch.countDown();
                }
            }
        });
        clientAlice.login("alice_test", "alice123");
        aliceReLoginLatch.await(3, TimeUnit.SECONDS);

        Thread.sleep(300);
        String secretMsg = "Day la tin nhan rieng tu mat 1-1";
        clientAdmin.sendPrivateMessage("alice_test", secretMsg);

        boolean privDone = privateLatch.await(3, TimeUnit.SECONDS);
        assertCondition(privDone, "Alice nhận được tin nhắn riêng từ Admin");
        assertCondition(secretMsg.equals(privateMsg.get()), "Nội dung tin nhắn riêng tư bảo mật chính xác");

        // 7. Test Tạo Nhóm và Gia Nhập Nhóm Bằng Mã PIN
        CountDownLatch createGrpLatch = new CountDownLatch(1);
        AtomicBoolean createGrpOk = new AtomicBoolean(false);
        CountDownLatch joinGrpFailLatch = new CountDownLatch(1);
        AtomicBoolean joinGrpFailOk = new AtomicBoolean(false);
        CountDownLatch joinGrpSuccessLatch = new CountDownLatch(1);
        AtomicBoolean joinGrpSuccessOk = new AtomicBoolean(false);
        CountDownLatch groupMsgLatch = new CountDownLatch(1);
        AtomicReference<String> receivedGrpMsg = new AtomicReference<>("");

        clientAlice.setEventListener(new ChatEventListenerAdapter() {
            @Override
            public void onCreateGroupResult(boolean success, String groupName, String groupCode, String message) {
                createGrpOk.set(success);
                createGrpLatch.countDown();
            }
        });

        clientAdmin.setEventListener(new ChatEventListenerAdapter() {
            @Override
            public void onJoinGroupResult(boolean success, String groupName, String message) {
                if (!success) {
                    joinGrpFailOk.set(true);
                    joinGrpFailLatch.countDown();
                } else {
                    joinGrpSuccessOk.set(true);
                    joinGrpSuccessLatch.countDown();
                }
            }
            @Override
            public void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content) {
                if ("Team Alpha".equals(groupName) && "alice_test".equals(sender)) {
                    receivedGrpMsg.set(content);
                    groupMsgLatch.countDown();
                }
            }
        });

        // Kiểm tra không còn 4 nhóm mẫu (ban đầu chỉ có duy nhất 1 phòng mặc định Friends & Family)
        assertCondition(server.getRoomManager().getGroupDAO().getAllGroups().size() == 1, "Đã xóa sạch 4 nhóm mẫu có sẵn, chỉ giữ phòng mặc định");

        // Alice tạo nhóm "Team Alpha" với mã "8888"
        clientAlice.createGroup("Team Alpha", "8888");
        boolean grpCreated = createGrpLatch.await(3, TimeUnit.SECONDS);
        assertCondition(grpCreated && createGrpOk.get(), "Alice tạo nhóm 'Team Alpha' với mã PIN '8888' thành công");

        // Admin cố gia nhập với mã PIN sai "0000" -> Thất bại
        clientAdmin.joinGroup("Team Alpha", "0000");
        boolean joinFailDone = joinGrpFailLatch.await(3, TimeUnit.SECONDS);
        assertCondition(joinFailDone && joinGrpFailOk.get(), "Admin nhập sai mã PIN và bị từ chối vào nhóm");

        // Admin gia nhập với đúng mã PIN "8888" -> Thành công
        clientAdmin.joinGroup("Team Alpha", "8888");
        boolean joinSuccessDone = joinGrpSuccessLatch.await(3, TimeUnit.SECONDS);
        assertCondition(joinSuccessDone && joinGrpSuccessOk.get(), "Admin nhập đúng mã PIN '8888' và vào nhóm thành công");

        // Alice gửi tin nhắn vào nhóm "Team Alpha" -> Admin nhận được tin nhắn nhóm
        Thread.sleep(200);
        String groupMsgContent = "Chao mung Admin den voi Team Alpha!";
        clientAlice.sendGroupMessage("Team Alpha", groupMsgContent);
        boolean grpMsgReceived = groupMsgLatch.await(3, TimeUnit.SECONDS);
        assertCondition(grpMsgReceived, "Admin nhận được tin nhắn nhóm 'Team Alpha' từ Alice");
        assertCondition(groupMsgContent.equals(receivedGrpMsg.get()), "Nội dung tin nhắn nhóm khớp chính xác");

        // 7b. Test Đặc quyền Trưởng nhóm (Alice là người tạo nhóm Team Alpha)
        clientAlice.sendGroupCommand(Protocol.GACT_MUTE, "Team Alpha", "admin");
        Thread.sleep(300);
        assertCondition(server.getRoomManager().getGroupDAO().isMutedInGroup("Team Alpha", "admin"), "Trưởng nhóm Alice chặn tin thành viên trong nhóm thành công");

        clientAlice.sendGroupCommand(Protocol.GACT_UNMUTE, "Team Alpha", "admin");
        Thread.sleep(300);
        assertCondition(!server.getRoomManager().getGroupDAO().isMutedInGroup("Team Alpha", "admin"), "Trưởng nhóm Alice mở chặn tin thành công");

        clientAlice.sendGroupCommand(Protocol.GACT_SET_CODE, "Team Alpha", "7777");
        Thread.sleep(300);
        assertCondition(server.getRoomManager().getGroupDAO().verifyGroupCode("Team Alpha", "7777"), "Trưởng nhóm Alice đổi mã PIN nhóm thành công");

        clientAlice.sendGroupCommand(Protocol.GACT_KICK, "Team Alpha", "admin");
        Thread.sleep(300);
        assertCondition(!server.getRoomManager().getGroupDAO().isMember("Team Alpha", "admin"), "Trưởng nhóm Alice kick thành viên khỏi nhóm thành công");
        assertCondition(server.getRoomManager().getGroupDAO().getUserGroups("admin").size() == 1, "Sau khi bị kick, thanh Group của Admin không còn hiển thị Team Alpha");
        assertCondition(!server.getRoomManager().getGroupDAO().createGroup("Team Beta", "7777", "admin"), "Không cho phép tạo 2 nhóm trùng mã định danh '7777'");

        CountDownLatch joinByCodeLatch = new CountDownLatch(1);
        AtomicBoolean joinByCodeOk = new AtomicBoolean(false);
        clientAdmin.setEventListener(new ChatEventListenerAdapter() {
            @Override
            public void onJoinGroupResult(boolean success, String groupName, String message) {
                if (success && "Team Alpha".equals(groupName)) {
                    joinByCodeOk.set(true);
                    joinByCodeLatch.countDown();
                }
            }
        });
        clientAdmin.joinGroup("", "7777");
        boolean joinCodeDone = joinByCodeLatch.await(3, TimeUnit.SECONDS);
        assertCondition(joinCodeDone && joinByCodeOk.get(), "Admin chỉ nhập mã định danh '7777' (không cần tên nhóm) vẫn vào được 'Team Alpha'");
        assertCondition(server.getRoomManager().getGroupDAO().getUserGroups("admin").size() == 2, "Sau khi tham gia lại bằng mã, nhóm hiện lại trong danh sách của Admin");

        // 8. Test Admin Mute Alice
        clientAdmin.sendAdminCommand(Protocol.ACT_MUTE, "alice_test", "5");
        Thread.sleep(400);
        assertCondition(server.getRoomManager().isUserMuted("alice_test"), "Server kích hoạt trạng thái MUTED cho Alice");

        // 9. Test Admin Kick Alice
        clientAdmin.sendAdminCommand(Protocol.ACT_KICK, "alice_test", "Kiem thu Kick");
        Thread.sleep(400);
        assertCondition(!server.getRoomManager().isUserOnline("alice_test"), "Alice đã bị đuổi và không còn trong danh sách Online");

        // 10. Khởi động lại Server để kiểm tra lưu trữ bền vững (Tài khoản + Nhóm + Tin nhắn nhóm)
        clientAdmin.disconnect();
        clientAlice.disconnect();
        server.stopServer();
        Thread.sleep(500);

        ChatServer serverRestarted = new ChatServer(TEST_PORT, 8081);
        boolean restarted = serverRestarted.startServer();
        assertCondition(restarted, "Khởi động lại ChatServer lần 2 thành công");

        CountDownLatch aliceRelogLatch = new CountDownLatch(1);
        CountDownLatch myGroupsLatch = new CountDownLatch(1);
        CountDownLatch teamAlphaHistLatch = new CountDownLatch(1);
        AtomicBoolean aliceRelogOk = new AtomicBoolean(false);
        AtomicBoolean hasTeamAlphaMembership = new AtomicBoolean(false);
        AtomicBoolean hasPersistedGroupMsg = new AtomicBoolean(false);

        SocketClient aliceAfterRestart = new SocketClient();
        aliceAfterRestart.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onLoginResult(boolean success, String role, String username, String message) {
                aliceRelogOk.set(success);
                aliceRelogLatch.countDown();
            }
            @Override
            public void onMyGroupsReceived(List<String> joinedGroups) {
                if (joinedGroups != null && joinedGroups.contains("Team Alpha")) {
                    hasTeamAlphaMembership.set(true);
                    myGroupsLatch.countDown();
                }
            }
            @Override
            public void onHistoryReceived(String groupName, List<MessagePacket> history) {
                if ("Team Alpha".equals(groupName) && history != null) {
                    for (MessagePacket m : history) {
                        if (groupMsgContent.equals(m.getContent())) {
                            hasPersistedGroupMsg.set(true);
                            teamAlphaHistLatch.countDown();
                        }
                    }
                }
            }
        });

        aliceAfterRestart.login("alice_test", "alice123");
        boolean relogDone = aliceRelogLatch.await(3, TimeUnit.SECONDS);
        assertCondition(relogDone && aliceRelogOk.get(), "Tài khoản đã đăng ký (alice_test) vẫn đăng nhập được sau khi khởi động lại Server");

        boolean myGrpDone = myGroupsLatch.await(3, TimeUnit.SECONDS);
        assertCondition(myGrpDone && hasTeamAlphaMembership.get(), "Quyền thành viên nhóm 'Team Alpha' vẫn được lưu trong DB");

        aliceAfterRestart.requestHistory("Team Alpha");
        boolean histDone = teamAlphaHistLatch.await(3, TimeUnit.SECONDS);
        assertCondition(histDone && hasPersistedGroupMsg.get(), "Tin nhắn nhóm 'Team Alpha' vẫn còn nguyên vẹn sau khi khởi động lại Server");

        // 10b. Kiểm thử Web API Session (giữ đăng nhập khi F5) & Kick realtime/polling
        String sessionResp = httpCall("http://127.0.0.1:8081/api/session?user=alice_test", "GET", null);
        assertCondition(sessionResp.contains("\"success\":true") && sessionResp.contains("\"alice_test\""),
                "Web /api/session khôi phục phiên đăng nhập thành công khi F5");

        String logoutResp = httpCall("http://127.0.0.1:8081/api/logout", "POST", "{\"username\":\"alice_test\"}");
        assertCondition(logoutResp.contains("\"success\":true"), "Web /api/logout đăng xuất thành công");

        String kickResp = httpCall("http://127.0.0.1:8081/api/group_action", "POST",
                "{\"action\":\"KICK\",\"groupName\":\"Team Alpha\",\"actor\":\"alice_test\",\"target\":\"admin\"}");
        assertCondition(kickResp.contains("\"success\":true"), "Web Leader (alice_test) kick member (admin) khỏi Team Alpha thành công");

        String adminGroupsAfterKick = httpCall("http://127.0.0.1:8081/api/groups?user=admin", "GET", null);
        assertCondition(!adminGroupsAfterKick.contains("\"Team Alpha\""),
                "Nhóm 'Team Alpha' bị loại bỏ ngay khỏi danh sách Groups của admin sau khi bị kick");

        String adminHistAfterKick = httpCall("http://127.0.0.1:8081/api/history?group=Team%20Alpha&user=admin", "GET", null);
        assertCondition(adminHistAfterKick.contains("\"kicked\":true"),
                "Web /api/history báo kicked=true ngay cho người dùng đã bị kick");

        // 11. Dọn dẹp tài nguyên & chỉ xóa CSDL test
        aliceAfterRestart.disconnect();
        serverRestarted.stopServer();
        cleanupTestDb();

        System.out.println("==================================================");
        System.out.println("🎉 TẤT CẢ CÁC BÀI TEST TÍCH HỢP ĐỀU THÀNH CÔNG 100%!");
        System.out.println("==================================================");
        System.out.flush();
        System.exit(0);
    }

    private static String httpCall(String urlStr, String method, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        if (jsonBody != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = conn.getResponseCode();
        java.io.InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return "";
        try (java.util.Scanner s = new java.util.Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private static void assertCondition(boolean condition, String description) {
        if (condition) {
            System.out.println("[PASS] " + description);
        } else {
            System.err.println("[FAIL] " + description);
            throw new AssertionError("Kiểm thử thất bại: " + description);
        }
    }

    private static class ChatEventListenerAdapter implements ChatEventListener {
        @Override public void onLoginResult(boolean success, String role, String username, String message) {}
        @Override public void onRegisterResult(boolean success, String message) {}
        @Override public void onCreateGroupResult(boolean success, String groupName, String groupCode, String message) {}
        @Override public void onJoinGroupResult(boolean success, String groupName, String message) {}
        @Override public void onGroupListUpdated(List<common.ChatGroup> groups) {}
        @Override public void onPublicMessageReceived(String sender, String role, String timestamp, String content) {}
        @Override public void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content) {}
        @Override public void onPrivateMessageReceived(String sender, String target, String timestamp, String content) {}
        @Override public void onSystemMessage(String content) {}
        @Override public void onUserListUpdated(List<User> onlineUsers) {}
        @Override public void onHistoryReceived(String groupName, List<MessagePacket> history) {}
        @Override public void onKicked(String reason) {}
        @Override public void onMuted(int minutes, String reason) {}
        @Override public void onUnmuted() {}
        @Override public void onChangePasswordResult(boolean success, String message) {}
        @Override public void onConnectionLost(String reason) {}
    }
}
