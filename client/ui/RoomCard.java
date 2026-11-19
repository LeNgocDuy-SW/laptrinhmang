package client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * RoomCard: Thẻ hiển thị phòng chat nổi bật (Favourites Card) với ngăn xếp Avatar như ảnh thiết kế.
 */
public class RoomCard extends JPanel {
    private final String title;
    private final String dateText;
    private int onlineCount;
    private int unreadCount;
    private boolean isHover = false;
    private boolean isSelected = false;

    public RoomCard(String title, String dateText, int onlineCount, int unreadCount, Runnable onClick) {
        this.title = title;
        this.dateText = dateText;
        this.onlineCount = onlineCount;
        this.unreadCount = unreadCount;

        setOpaque(false);
        setPreferredSize(new Dimension(228, 68));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHover = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClick != null) onClick.run();
            }
        });
    }

    public void setOnlineCount(int count) {
        this.onlineCount = count;
        repaint();
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
        repaint();
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setSelected(boolean sel) {
        this.isSelected = sel;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIUtils.enableAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();
        int arc = 10;
        if (isSelected) {
            g2.setColor(new Color(225, 241, 238));
            g2.fill(new RoundRectangle2D.Float(2, 2, w - 4, h - 4, arc, arc));
            g2.setColor(UIUtils.PRIMARY_BLUE);
            g2.fillRoundRect(2, 12, 3, h - 24, 3, 3);
        } else if (isHover) {
            g2.setColor(new Color(239, 243, 242));
            g2.fill(new RoundRectangle2D.Float(2, 2, w - 4, h - 4, arc, arc));
        }

        int avSize = 42;
        int avX = 12;
        int avatarY = (h - avSize) / 2;
        Color[] colors = UIUtils.getAvatarGradient(title);
        g2.setPaint(new GradientPaint(avX, avatarY, colors[0], avX + avSize, avatarY + avSize, colors[1]));
        g2.fill(new Ellipse2D.Float(avX, avatarY, avSize, avSize));
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        String initial = title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase();
        FontMetrics initialMetrics = g2.getFontMetrics();
        g2.drawString(initial, avX + (avSize - initialMetrics.stringWidth(initial)) / 2,
                avatarY + (avSize - initialMetrics.getHeight()) / 2 + initialMetrics.getAscent());

        int textX = avX + avSize + 10;
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.setColor(UIUtils.TEXT_DARK);
        g2.drawString(title, textX, avatarY + 17);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.setColor(UIUtils.TEXT_SECONDARY);
        g2.drawString(onlineCount + " đang hoạt động", textX, avatarY + 33);

        if (unreadCount > 0) {
            String bText = unreadCount > 99 ? "99+" : String.valueOf(unreadCount);
            int bw = unreadCount > 9 ? 26 : 20;
            int bh = 20;
            int bx = w - 12 - bw;
            int by = (h - bh) / 2;

            g2.setColor(UIUtils.BADGE_RED);
            g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 12, 12));

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            FontMetrics bfm = g2.getFontMetrics();
            int btx = bx + (bw - bfm.stringWidth(bText)) / 2;
            int bty = by + ((bh - bfm.getHeight()) / 2) + bfm.getAscent();
            g2.drawString(bText, btx, bty);
        }

        g2.dispose();
    }
}

