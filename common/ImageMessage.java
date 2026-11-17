package common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Inline image payload shared by TCP, HTTP and persisted chat history. */
public final class ImageMessage {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_BASE64 = ((MAX_BYTES + 2) / 3) * 4;
    public static final String PREFIX = "[[IMAGE|";
    private ImageMessage() {}

    public static String mainContent(String content) {
        if (content.startsWith("[[REPLY|")) {
            int end = content.indexOf("]]");
            if (end > 8) return content.substring(end + 2);
        }
        return content;
    }

    public static String encode(byte[] bytes) throws IOException {
        String mime = inspect(bytes, null).mime;
        return PREFIX + mime + "|" + Base64.getEncoder().encodeToString(bytes) + "]]";
    }

    public static void validateContent(String content) throws IOException {
        String main = mainContent(content);
        if (main.startsWith(PREFIX)) decode(main);
    }

    public static BufferedImage decode(String payload) throws IOException {
        if (!payload.startsWith(PREFIX) || !payload.endsWith("]]"))
            throw new IOException("Dữ liệu ảnh không hợp lệ.");
        int sep = payload.indexOf('|', PREFIX.length());
        if (sep < 0 || sep > 24) throw new IOException("Định dạng ảnh không hợp lệ.");
        String mime = payload.substring(PREFIX.length(), sep);
        String data = payload.substring(sep + 1, payload.length() - 2);
        if (data.length() > MAX_BASE64) throw new IOException("Ảnh phải nhỏ hơn hoặc bằng 2 MB.");
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            if (!Base64.getEncoder().encodeToString(bytes).equals(data))
                throw new IOException("Dữ liệu ảnh không hợp lệ.");
            return inspect(bytes, mime).image;
        } catch (IllegalArgumentException e) {
            throw new IOException("Dữ liệu ảnh không hợp lệ.", e);
        }
    }

    private static Result inspect(byte[] bytes, String expectedMime) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new IOException("Ảnh phải có dung lượng từ 1 byte đến 2 MB.");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Chỉ hỗ trợ ảnh JPG, PNG và GIF.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                String mime = "jpeg".equals(format) ? "image/jpeg" : "image/" + format;
                if (!("image/jpeg".equals(mime) || "image/png".equals(mime) || "image/gif".equals(mime))
                        || (expectedMime != null && !mime.equals(expectedMime)))
                    throw new IOException("Chỉ hỗ trợ ảnh JPG, PNG và GIF đúng định dạng.");
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 4096 || h > 4096 || (long) w * h > 12000000)
                    throw new IOException("Ảnh tối đa 4096 px mỗi chiều và 12 triệu điểm ảnh.");
                // Decode on the server too: reject corrupt files before storing/broadcasting.
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("Không đọc được ảnh.");
                return new Result(mime, image);
            } finally { reader.dispose(); }
        }
    }

    private static final class Result {
        final String mime;
        final BufferedImage image;
        Result(String mime, BufferedImage image) { this.mime = mime; this.image = image; }
    }
}
