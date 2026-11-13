package client.ui;

import common.Protocol;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.net.URL;
import java.util.function.BiConsumer;

/**
 * MessageBubblePanel: Hiển thị từng tin nhắn dạng Bong bóng Chat (Chat Bubble)
 * hỗ trợ Trích dẫn trả lời tin nhắn (Reply), Sticker sinh động, và Avatar.
 */
public class MessageBubblePanel extends JPanel {
    private final String sender;
    private final String role;
    private final String timestamp;
    private final String content;
    private final boolean isMine;
    private final boolean isPrivate;
    private final boolean isSystem;
    private final BiConsumer<String, String> onReply;

    public MessageBubblePanel(String sender, String role, String timestamp, String content, boolean isMine, boolean isPrivate, boolean isSystem) {
        this(sender, role, timestamp, content, isMine, isPrivate, isSystem, null);
    }

    public MessageBubblePanel(String sender, String role, String timestamp, String content, boolean isMine, boolean isPrivate, boolean isSystem, BiConsumer<String, String> onReply) {
        this.sender = sender;
        this.role = role;
        this.timestamp = timestamp;
        this.content = content != null ? content : "";
        this.isMine = isMine;
        this.isPrivate = isPrivate;
        this.isSystem = isSystem;
        this.onReply = onReply;

        setOpaque(false);
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(6, 16, 6, 16));

        initComponents();
    }

    private void initComponents() {
        if (isSystem) {
            JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            centerPanel.setOpaque(false);

            JLabel lblSys = new JLabel("ℹ️ " + content);
            lblSys.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            lblSys.setForeground(UIUtils.TEXT_MUTED);
            centerPanel.add(lblSys);

            add(centerPanel, BorderLayout.CENTER);
            return;
        }

        // Tách Reply & Sticker từ content
        String replySender = null;
        String replySnippet = null;
        String mainContent = content;

        if (mainContent.startsWith("[[REPLY|")) {
            int endIdx = mainContent.indexOf("]]");
            if (endIdx > 8) {
                String meta = mainContent.substring(8, endIdx);
                String[] parts = meta.split("\\|", 2);
                replySender = parts.length > 0 ? parts[0] : "";
                replySnippet = parts.length > 1 ? parts[1] : "";
                mainContent = mainContent.substring(endIdx + 2);
            }
        }

        String stickerIcon = null;
        String stickerLabel = null;
        if (mainContent.startsWith("[[STICKER|") && mainContent.endsWith("]]")) {
            String meta = mainContent.substring(10, mainContent.length() - 2);
            String[] parts = meta.split("\\|", 2);
            stickerIcon = parts.length > 0 ? parts[0] : "🎉";
            stickerLabel = parts.length > 1 ? parts[1] : "Sticker";
        }

        final String plainSnippet = stickerLabel != null ? "[Sticker] " + stickerLabel : mainContent;

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);

        if (!isMine) {
            AvatarPanel av = new AvatarPanel(sender, 36);
            JPanel avWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            avWrap.setOpaque(false);
            avWrap.add(av);
            row.add(avWrap, BorderLayout.WEST);
        }

        JPanel contentCol = new JPanel();
        contentCol.setLayout(new BoxLayout(contentCol, BoxLayout.Y_AXIS));
        contentCol.setOpaque(false);

        // Hàng thông tin người gửi, Thời gian & nút Trả lời
        JPanel metaRow = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 0));
        metaRow.setOpaque(false);

        String displayName = isMine ? "Bạn" : sender;
        JLabel lblName = new JLabel(displayName);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblName.setForeground(UIUtils.TEXT_DARK);
        metaRow.add(lblName);

        if (Protocol.ROLE_ADMIN.equalsIgnoreCase(role)) {
            JLabel lblRole = new JLabel("👑 ADMIN");
            lblRole.setFont(new Font("Segoe UI", Font.BOLD, 10));
            lblRole.setForeground(UIUtils.GOLD_ADMIN);
            metaRow.add(lblRole);
        }

        if (isPrivate) {
            JLabel lblPriv = new JLabel("🔒 [MẬT]");
            lblPriv.setFont(new Font("Segoe UI", Font.BOLD, 10));
            lblPriv.setForeground(new Color(124, 58, 237));
            metaRow.add(lblPriv);
        }

        JLabel lblTime = new JLabel(timestamp != null ? timestamp : "");
        lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblTime.setForeground(UIUtils.TEXT_MUTED);
        metaRow.add(lblTime);

        if (onReply != null) {
            JButton btnReply = new JButton("↩ Trả lời");
            btnReply.setFont(new Font("Segoe UI", Font.BOLD, 10));
            btnReply.setForeground(UIUtils.PRIMARY_BLUE);
            btnReply.setContentAreaFilled(false);
            btnReply.setBorder(new EmptyBorder(0, 4, 0, 4));
            btnReply.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnReply.addActionListener(e -> onReply.accept(sender, plainSnippet));
            metaRow.add(btnReply);
        }

        contentCol.add(metaRow);
        contentCol.add(Box.createVerticalStrut(3));

        JPanel bubbleRow = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        bubbleRow.setOpaque(false);

        if (stickerIcon != null) {
            bubbleRow.add(new StickerBox(stickerIcon, stickerLabel, replySender, replySnippet));
        } else {
            bubbleRow.add(new BubbleBox(mainContent, replySender, replySnippet, isMine, isPrivate));
        }

        contentCol.add(bubbleRow);
        row.add(contentCol, BorderLayout.CENTER);

        if (isMine) {
            AvatarPanel av = new AvatarPanel(sender, 36);
            JPanel avWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            avWrap.setOpaque(false);
            avWrap.add(av);
            row.add(avWrap, BorderLayout.EAST);
        }

        add(row, BorderLayout.CENTER);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    /**
     * StickerBox: Hiển thị Sticker lớn sinh động (hỗ trợ cả ảnh GIF hoạt họa như Messenger và Emoji)
     */
    private static class StickerBox extends JPanel {
        public StickerBox(String icon, String label, String replySender, String replySnippet) {
            setOpaque(false);
            setLayout(new BorderLayout(0, 4));
            setBorder(new EmptyBorder(4, 10, 4, 10));

            if (replySender != null) {
                add(createQuoteBox(replySender, replySnippet), BorderLayout.NORTH);
            }

            boolean isImage = icon.endsWith(".gif") || icon.endsWith(".png") || icon.endsWith(".webp") || icon.endsWith(".jpg")
                    || icon.contains("stickers/") || icon.startsWith("http://") || icon.startsWith("https://");

            if (isImage) {
                ImageIcon imgIcon = null;
                try {
                    String clean = icon.startsWith("/") ? icon.substring(1) : icon;
                    File f1 = new File(clean);
                    File f2 = new File("web/" + clean);
                    File f3 = new File("client/" + clean);
                    if (f1.exists()) {
                        imgIcon = new ImageIcon(f1.getAbsolutePath());
                    } else if (f2.exists()) {
                        imgIcon = new ImageIcon(f2.getAbsolutePath());
                    } else if (f3.exists()) {
                        imgIcon = new ImageIcon(f3.getAbsolutePath());
                    } else if (icon.startsWith("http://") || icon.startsWith("https://")) {
                        imgIcon = new ImageIcon(new URL(icon));
                    } else {
                        URL res = MessageBubblePanel.class.getResource("/" + clean);
                        if (res != null) imgIcon = new ImageIcon(res);
                    }
                } catch (Exception ignored) {}

                if (imgIcon != null && imgIcon.getIconWidth() > 0) {
                    JLabel lblImg = new JLabel(imgIcon);
                    lblImg.setToolTipText(label != null ? label : "Sticker");
                    lblImg.setCursor(new Cursor(Cursor.HAND_CURSOR));

                    if (label != null && !label.isEmpty() && !label.equalsIgnoreCase("Sticker")) {
                        JPanel imgBox = new JPanel(new BorderLayout(0, 2));
                        imgBox.setOpaque(false);
                        imgBox.add(lblImg, BorderLayout.CENTER);

                        JLabel lblCap = new JLabel(label, JLabel.CENTER);
                        lblCap.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                        lblCap.setForeground(new Color(100, 116, 139));
                        imgBox.add(lblCap, BorderLayout.SOUTH);

                        add(imgBox, BorderLayout.CENTER);
                    } else {
                        add(lblImg, BorderLayout.CENTER);
                    }
                    return;
                }
            }

            JPanel card = new JPanel(new BorderLayout(0, 2)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    UIUtils.enableAntialiasing(g2);
                    GradientPaint gp = new GradientPaint(0, 0, new Color(240, 249, 255), getWidth(), getHeight(), new Color(238, 242, 255));
                    g2.setPaint(gp);
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 22, 22));
                    g2.setColor(new Color(191, 219, 254));
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 22, 22));
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            card.setOpaque(false);
            card.setBorder(new EmptyBorder(10, 18, 10, 18));

            JLabel lblEmoji = new JLabel(icon, JLabel.CENTER);
            lblEmoji.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 44));

            JLabel lblCap = new JLabel(label, JLabel.CENTER);
            lblCap.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblCap.setForeground(UIUtils.PRIMARY_BLUE);

            card.add(lblEmoji, BorderLayout.CENTER);
            card.add(lblCap, BorderLayout.SOUTH);
            add(card, BorderLayout.CENTER);
        }
    }

    private static JPanel createQuoteBox(String replySender, String replySnippet) {
        JPanel quote = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                g2.setColor(new Color(255, 255, 255, 160));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(UIUtils.PRIMARY_BLUE);
                g2.fillRoundRect(0, 2, 4, getHeight() - 4, 4, 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        quote.setOpaque(false);
        quote.setBorder(new EmptyBorder(4, 10, 4, 10));

        String shortSnip = replySnippet != null && replySnippet.length() > 48
                ? replySnippet.substring(0, 48) + "..."
                : (replySnippet != null ? replySnippet : "");
        JLabel lblQuote = new JLabel("<html><b>↩ " + escapeHtml(replySender) + ":</b> " + escapeHtml(shortSnip) + "</html>");
        lblQuote.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblQuote.setForeground(new Color(71, 85, 105));
        quote.add(lblQuote, BorderLayout.CENTER);
        return quote;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * BubbleBox: Khối bong bóng chat bo tròn mềm mại (kèm khối Reply nếu có)
     */
    private static class BubbleBox extends JComponent {
        private final JTextArea textArea;
        private final boolean isMine;
        private final boolean isPrivate;
        private JPanel quotePanel;

        public BubbleBox(String text, String replySender, String replySnippet, boolean isMine, boolean isPrivate) {
            this.isMine = isMine;
            this.isPrivate = isPrivate;

            setLayout(new BorderLayout(0, 4));
            setOpaque(false);

            if (replySender != null && !replySender.isEmpty()) {
                quotePanel = createQuoteBox(replySender, replySnippet);
                JPanel qWrap = new JPanel(new BorderLayout());
                qWrap.setOpaque(false);
                qWrap.setBorder(new EmptyBorder(8, 12, 0, 12));
                qWrap.add(quotePanel, BorderLayout.CENTER);
                add(qWrap, BorderLayout.NORTH);
            }

            textArea = new JTextArea(text);
            textArea.setOpaque(false);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            Color textColor;
            if (isMine) textColor = UIUtils.BUBBLE_MY_TEXT;
            else if (isPrivate) textColor = UIUtils.BUBBLE_PRIVATE_TEXT;
            else textColor = UIUtils.BUBBLE_OTHER_TEXT;

            textArea.setForeground(textColor);
            textArea.setBorder(new EmptyBorder(quotePanel != null ? 2 : 8, 14, 8, 14));

            add(textArea, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredSize() {
            int maxWidth = 480;
            FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
            int textWidth = fm.stringWidth(textArea.getText());
            int qHeight = quotePanel != null ? quotePanel.getPreferredSize().height + 12 : 0;
            int qWidth = quotePanel != null ? quotePanel.getPreferredSize().width + 24 : 0;

            if (textWidth > maxWidth || qWidth > maxWidth) {
                textArea.setSize(new Dimension(maxWidth, Short.MAX_VALUE));
                Dimension d = textArea.getPreferredSize();
                return new Dimension(maxWidth, d.height + qHeight);
            } else {
                Dimension d = textArea.getPreferredSize();
                return new Dimension(Math.max(Math.max(d.width, qWidth), 48), d.height + qHeight);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UIUtils.enableAntialiasing(g2);

            int w = getWidth();
            int h = getHeight();
            int arc = 20;

            Color bg;
            Color border;
            if (isMine) {
                bg = UIUtils.BUBBLE_MY_BG;
                border = new Color(191, 219, 254);
            } else if (isPrivate) {
                bg = UIUtils.BUBBLE_PRIVATE_BG;
                border = new Color(216, 180, 254);
            } else {
                bg = UIUtils.BUBBLE_OTHER_BG;
                border = UIUtils.BORDER_LIGHT;
            }

            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));

            g2.setColor(border);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
