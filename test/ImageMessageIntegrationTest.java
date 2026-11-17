package test;

import common.ImageMessage;
import common.MessagePacket;
import common.Protocol;
import server.ChatServer;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Runs against an isolated database and ephemeral ports; never touches chat.db. */
public class ImageMessageIntegrationTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("[PASS] " + message);
    }
    private static int port() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }
    private static String until(BufferedReader in, String prefix) throws IOException {
        String line;
        while ((line = in.readLine()) != null) if (line.startsWith(prefix)) return line;
        throw new EOFException(prefix);
    }
    private static byte[] picture(String format, int width) throws IOException {
        BufferedImage image = new BufferedImage(width, 16, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff336699);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) throw new IOException(format);
        return out.toByteArray();
    }
    private static void rejects(String payload) throws IOException {
        try { ImageMessage.validateContent(payload); }
        catch (IOException expected) { return; }
        throw new AssertionError("Invalid image accepted");
    }
    private static String http(int port, String path, String json, int status) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        c.setConnectTimeout(3000); c.setReadTimeout(5000);
        if (json != null) {
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) { out.write(json.getBytes(StandardCharsets.UTF_8)); }
        }
        try {
            check(c.getResponseCode() == status, path + " HTTP " + status);
            try (InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally { c.disconnect(); }
    }
    private static String body(String payload, String group) {
        return "{\"sender\":\"admin\",\"role\":\"ADMIN\",\"groupName\":\"" + group
            + "\",\"content\":\"" + payload + "\",\"isPrivate\":\"false\"}";
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("chat-image-test-");
        System.setProperty("chat.db.path", directory.resolve("test.db").toString());
        int tcp = port(), web = port();
        ChatServer server = null;
        HttpURLConnection events = null;
        int exit = 1;
        try {
            for (String format : new String[]{"png", "jpeg", "gif"}) {
                String payload = ImageMessage.encode(picture(format, 24));
                check(ImageMessage.decode(payload).getWidth() == 24, format + " encode/decode");
            }
            String image = ImageMessage.encode(picture("png", 24));
            String reply = "[[REPLY|alice|photo]]" + image;
            ImageMessage.validateContent(reply);
            check(ImageMessage.decode(ImageMessage.mainContent(reply)).getHeight() == 16, "Reply wrapping image");
            rejects("[[IMAGE|image/png|!!!!]]");
            rejects(image.replace("image/png", "image/jpeg"));
            rejects("[[IMAGE|image/svg+xml|PHN2Zz4=]]");
            rejects("[[IMAGE|image/png|" + Base64.getEncoder().encodeToString(new byte[ImageMessage.MAX_BYTES + 1]) + "]]");
            rejects("[[IMAGE|image/png|" + Base64.getEncoder().encodeToString(picture("png", 4097)) + "]]");
            check(true, "Reject invalid, mismatched, oversized and excessive-dimension images");
            MessagePacket packet = new MessagePacket("PUBLIC", "admin", "ADMIN", "ALL", reply);
            check(reply.equals(MessagePacket.decodeFromHistory(packet.encodeForHistory()).getContent()), "History codec preserves image and reply");
            final String displayImage = reply;
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                client.ui.MessageBubblePanel panel = new client.ui.MessageBubblePanel("admin", "ADMIN", "12:00", displayImage, true, false, false);
                check(panel.getPreferredSize().height > 30, "Desktop photo bubble renders");
            });
            server = new ChatServer(tcp, web);
            check(server.startServer(), "Start isolated server");
            try (Socket socket = new Socket("127.0.0.1", tcp)) {
                socket.setSoTimeout(5000);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println("LOGIN|admin|admin123");
                check(until(in, "LOGIN_RES|").contains("SUCCESS"), "TCP login");
                events = (HttpURLConnection) new URL("http://127.0.0.1:" + web + "/api/events?user=admin").openConnection();
                events.setReadTimeout(5000);
                BufferedReader sse = new BufferedReader(new InputStreamReader(events.getInputStream(), StandardCharsets.UTF_8));
                out.println("MSG_GROUP|" + Protocol.DEFAULT_GROUP + "|" + reply);
                check(until(in, "NEW_MSG|").endsWith(reply), "TCP photo broadcast preserves bytes");
                String event;
                do { event = until(sse, "data:"); } while (!event.contains("NEW_MSG"));
                check(event.contains(reply), "Live SSE delivers complete image with reply");
                String jpeg = ImageMessage.encode(picture("jpeg", 32));
                check(http(web, "/api/send", body(jpeg, Protocol.DEFAULT_GROUP), 200).contains("true"), "HTTP image accepted");
                check(until(in, "NEW_MSG|").endsWith(jpeg), "Web image reaches desktop TCP");
                check(http(web, "/api/history?user=admin", null, 200).contains(reply), "HTTP history includes image");
                out.println("GET_HISTORY|" + Protocol.DEFAULT_GROUP);
                String history = until(in, "HISTORY|");
                boolean found = false;
                for (String item : history.split("\\|", 4)[3].split(";;")) {
                    MessagePacket m = MessagePacket.decodeFromHistory(item);
                    if (m != null && reply.equals(m.getContent())) found = true;
                }
                check(found, "TCP history decodes original image");
                out.println("MSG_GROUP|" + Protocol.DEFAULT_GROUP + "|[[IMAGE|image/png|!!!!]]");
                check(!until(in, "SYSTEM_MSG|").isEmpty(), "TCP rejects malformed image");
                http(web, "/api/send", body("[[IMAGE|image/png|!!!!]]", Protocol.DEFAULT_GROUP), 400);
                http(web, "/api/send", body(image, "Missing Room"), 403);
                server.getRoomManager().getGroupDAO().muteInGroup(Protocol.DEFAULT_GROUP, "admin", "admin");
                http(web, "/api/send", body(image, Protocol.DEFAULT_GROUP), 403);
                server.getRoomManager().getGroupDAO().unmuteInGroup(Protocol.DEFAULT_GROUP, "admin");
                check(server.getMessageDAO().getGroupMessages(Protocol.DEFAULT_GROUP, 50).size() == 2, "Rejected images are not stored");
                http(web, "/api/send", body("A".repeat(ImageMessage.MAX_BASE64 + 17000), Protocol.DEFAULT_GROUP), 413);
                out.println("LOGOUT");
            }
            events.disconnect(); events = null;
            server.stopServer();
            server = new ChatServer(tcp, web);
            check(server.startServer(), "Restart isolated server");
            check(http(web, "/api/history?user=admin", null, 200).contains(reply), "Image persists after server restart");
            exit = 0;
        } catch (Throwable error) { error.printStackTrace(); }
        finally {
            if (events != null) events.disconnect();
            if (server != null) server.stopServer();
            for (String suffix : new String[]{"", "-wal", "-shm"}) Files.deleteIfExists(directory.resolve("test.db" + suffix));
            Files.deleteIfExists(directory);
        }
        System.exit(exit);
    }
}
