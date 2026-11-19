package client.ui;

import client.network.ChatEventListener;
import client.network.SocketClient;
import common.ChatGroup;
import common.MessagePacket;
import common.Protocol;
import common.User;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/**
 * LoginFrame: Giao diện Đăng nhập & Đăng ký Neumorphic / Soft UI
 * được thiết kế tỉ mỉ theo đúng chuẩn hình ảnh mẫu:
 * - Logo ngọn sóng/lửa 3D sắc nét trên thẻ bo tròn Neumorphism
 * - Tiêu đề "Welcome Back", phụ đề "Sign in to continue"
 * - Hộp nhập bo tròn dạng viên thuốc với icon 👤, 🔒 và nút ẩn/hiện mật khẩu 👁
 * - Checkbox "Remember Me" bo tròn và liên kết "Forgot Password?"
 * - Nút bấm lớn màu xanh dương Gradient kèm huy hiệu mũi tên trắng ->
 * - Đường kẻ phân cách "OR CONTINUE WITH"
 * - 4 nút Neumorphic squircle: Google, Discord, Facebook, và Cấu hình Server (Host/Port)
 * - Chuyển đổi linh hoạt giữa màn hình Đăng Nhập và Đăng Ký (Sign Up)
 */
public class LoginFrame extends JFrame implements ChatEventListener {
    // Cấu hình máy chủ
    private String serverHost = "127.0.0.1";
    private int serverPort = 5000;

    // Trạng thái giao diện
    private boolean isRegisterMode = false;
    private boolean showPassword = false;
    private boolean rememberMe = true;

    // Các trường nhập liệu
    private JTextField txtUser;
    private JPasswordField txtPass;
    private JLabel lblTitle;
    private JLabel lblSub;
    private JButton btnMainAction;
    private JLabel lblToggleMode;
    private JLabel lblStatus;

    private final SocketClient socketClient;

    public LoginFrame() {
        super("Chat Room - Đăng nhập");
        this.socketClient = new SocketClient();
        initComponents();
    }

    private void initComponents() {
        setSize(460, 700);
        setMinimumSize(new Dimension(420, 640));
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel backgroundPanel = new JPanel(new GridBagLayout());
        backgroundPanel.setBackground(UIUtils.BG_MAIN);

        // Thẻ trung tâm Neumorphic Card
        JPanel cardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                UIUtils.drawRoundedCard(g2, 0, 0, getWidth(), getHeight(), 12,
                        Color.WHITE, UIUtils.BORDER_LIGHT);
                g2.dispose();
            }
        };
        cardPanel.setOpaque(false);
        cardPanel.setPreferredSize(new Dimension(380, 650));
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));
        cardPanel.setBorder(new EmptyBorder(18, 20, 14, 20));

        // 1. Logo 3D Wave/Flame trên thẻ Neumorphic squircle
        JPanel logoContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        logoContainer.setOpaque(false);
        logoContainer.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel logoSquircle = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                int w = getWidth();
                int h = getHeight();
                int arc = 28;

                // Bóng mờ Neumorphic ngoài (dưới phải)
                g2.setColor(new Color(200, 214, 235, 160));
                g2.fillRoundRect(4, 5, w - 8, h - 8, arc, arc);

                // Vùng sáng Neumorphic (trên trái)
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(1, 1, w - 5, h - 5, arc, arc);

                // Nền chính của squircle
                GradientPaint sqGrad = new GradientPaint(0, 0, new Color(255, 255, 255), 0, h, new Color(241, 246, 255));
                g2.setPaint(sqGrad);
                g2.fillRoundRect(2, 2, w - 6, h - 6, arc, arc);

                // Viền mờ tinh tế
                g2.setColor(new Color(230, 238, 248));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(2, 2, w - 6, h - 6, arc, arc);

                // --- VẼ BIỂU TƯỢNG 3D FLAME / WAVE RIBBONS ---
                int cx = w / 2;
                int cy = h / 2;

                // Dải sóng trái (Xanh biển sâu)
                Path2D.Double waveLeft = new Path2D.Double();
                waveLeft.moveTo(cx - 16, cy + 18);
                waveLeft.curveTo(cx - 24, cy + 6, cx - 18, cy - 8, cx - 6, cy - 14);
                waveLeft.curveTo(cx - 12, cy - 2, cx - 10, cy + 8, cx - 4, cy + 16);
                waveLeft.closePath();
                GradientPaint gradDarkBlue = new GradientPaint(cx - 20, cy + 18, new Color(13, 71, 200), cx - 6, cy - 14, new Color(37, 117, 252));
                g2.setPaint(gradDarkBlue);
                g2.fill(waveLeft);

                // Dải sóng giữa (Xanh ngọc lôi cuốn)
                Path2D.Double waveMid = new Path2D.Double();
                waveMid.moveTo(cx - 8, cy + 22);
                waveMid.curveTo(cx - 15, cy + 6, cx - 4, cy - 12, cx + 5, cy - 20);
                waveMid.curveTo(cx + 1, cy - 8, cx + 2, cy + 8, cx + 6, cy + 15);
                waveMid.curveTo(cx + 2, cy + 21, cx - 3, cy + 24, cx - 8, cy + 22);
                waveMid.closePath();
                GradientPaint gradMidBlue = new GradientPaint(cx - 10, cy + 22, new Color(29, 114, 254), cx + 5, cy - 20, new Color(96, 165, 250));
                g2.setPaint(gradMidBlue);
                g2.fill(waveMid);

                // Dải sóng phải (Trắng băng & Xanh nhạt sáng bóng)
                Path2D.Double waveRight = new Path2D.Double();
                waveRight.moveTo(cx + 3, cy + 14);
                waveRight.curveTo(cx - 1, cy + 2, cx + 4, cy - 12, cx + 14, cy - 22);
                waveRight.curveTo(cx + 12, cy - 10, cx + 18, cy + 4, cx + 9, cy + 12);
                waveRight.closePath();
                GradientPaint gradWhiteBlue = new GradientPaint(cx + 3, cy + 14, new Color(191, 219, 254), cx + 14, cy - 22, Color.WHITE);
                g2.setPaint(gradWhiteBlue);
                g2.fill(waveRight);

                g2.dispose();
            }
        };
        logoSquircle.setPreferredSize(new Dimension(84, 84));
        logoSquircle.setOpaque(false);
        logoContainer.add(logoSquircle);
        cardPanel.add(logoContainer);

        cardPanel.add(Box.createVerticalStrut(14));

        // 2. Tiêu đề "Welcome Back" & Phụ đề "Sign in to continue"
        lblTitle = new JLabel("Chào mừng trở lại");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblTitle.setForeground(new Color(16, 24, 40));
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(lblTitle);

        cardPanel.add(Box.createVerticalStrut(4));

        lblSub = new JLabel("Đăng nhập để tiếp tục trò chuyện");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(new Color(100, 116, 139));
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(lblSub);

        cardPanel.add(Box.createVerticalStrut(20));

        // 3. Khung nhập Tên đăng nhập (Username or Email) dạng Neumorphic Pill
        JPanel userFieldWrapper = createNeumorphicFieldPanel();
        userFieldWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel iconUser = new JLabel("  👤  ");
        iconUser.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        iconUser.setForeground(new Color(148, 163, 184));
        userFieldWrapper.add(iconUser, BorderLayout.WEST);

        txtUser = new JTextField();
        txtUser.setOpaque(false);
        txtUser.setBorder(null);
        txtUser.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtUser.setForeground(new Color(30, 41, 59));
        txtUser.setCaretColor(UIUtils.PRIMARY_BLUE);
        txtUser.putClientProperty("JTextField.placeholderText", "Tên đăng nhập");
        userFieldWrapper.add(txtUser, BorderLayout.CENTER);
        cardPanel.add(userFieldWrapper);

        cardPanel.add(Box.createVerticalStrut(12));

        // 4. Khung nhập Mật khẩu (Password) với nút ẩn/hiện 👁
        JPanel passFieldWrapper = createNeumorphicFieldPanel();
        passFieldWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel iconLock = new JLabel("  🔒  ");
        iconLock.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        iconLock.setForeground(new Color(148, 163, 184));
        passFieldWrapper.add(iconLock, BorderLayout.WEST);

        txtPass = new JPasswordField();
        txtPass.setOpaque(false);
        txtPass.setBorder(null);
        txtPass.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtPass.setForeground(new Color(30, 41, 59));
        txtPass.setCaretColor(UIUtils.PRIMARY_BLUE);
        passFieldWrapper.add(txtPass, BorderLayout.CENTER);

        JButton btnToggleEye = new JButton("👁");
        btnToggleEye.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        btnToggleEye.setBorder(new EmptyBorder(0, 0, 0, 12));
        btnToggleEye.setContentAreaFilled(false);
        btnToggleEye.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnToggleEye.setToolTipText("Hiện/Ẩn mật khẩu");
        btnToggleEye.addActionListener(e -> {
            showPassword = !showPassword;
            txtPass.setEchoChar(showPassword ? (char) 0 : '•');
            btnToggleEye.setText(showPassword ? "👁‍🗨" : "👁");
        });
        passFieldWrapper.add(btnToggleEye, BorderLayout.EAST);
        cardPanel.add(passFieldWrapper);

        cardPanel.add(Box.createVerticalStrut(12));

        // 5. Tùy chọn ghi nhớ và trợ giúp tài khoản
        JPanel rememberRow = new JPanel(new BorderLayout());
        rememberRow.setOpaque(false);
        rememberRow.setMaximumSize(new Dimension(340, 24));
        rememberRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JCheckBox chkRemember = new JCheckBox("Ghi nhớ đăng nhập", rememberMe);
        chkRemember.setOpaque(false);
        chkRemember.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkRemember.setForeground(new Color(71, 85, 105));
        chkRemember.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chkRemember.addActionListener(e -> rememberMe = chkRemember.isSelected());
        rememberRow.add(chkRemember, BorderLayout.WEST);

        JLabel lblForgot = new JLabel("Quên mật khẩu?");
        lblForgot.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblForgot.setForeground(new Color(37, 117, 252));
        lblForgot.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblForgot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showForgotPasswordDialog();
            }
        });
        rememberRow.add(lblForgot, BorderLayout.EAST);
        cardPanel.add(rememberRow);

        cardPanel.add(Box.createVerticalStrut(16));

        // 6. Nút hành động chính
        btnMainAction = new JButton("Login") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                int w = getWidth();
                int h = getHeight();
                int arc = h;

                g2.setColor(isEnabled() ? UIUtils.PRIMARY_BLUE : UIUtils.TEXT_MUTED);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                // Chữ ở giữa
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
                FontMetrics fm = g2.getFontMetrics();
                String text = getText();
                int tx = (w - fm.stringWidth(text)) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);

                g2.dispose();
            }
        };
        btnMainAction.setPreferredSize(new Dimension(340, 50));
        btnMainAction.setMaximumSize(new Dimension(340, 50));
        btnMainAction.setOpaque(false);
        btnMainAction.setContentAreaFilled(false);
        btnMainAction.setBorderPainted(false);
        btnMainAction.setFocusPainted(false);
        btnMainAction.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnMainAction.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(btnMainAction);

        cardPanel.add(Box.createVerticalStrut(18));

        cardPanel.add(Box.createVerticalStrut(20));

        JPanel alternateActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        alternateActions.setOpaque(false);
        alternateActions.setMaximumSize(new Dimension(340, 38));
        alternateActions.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnServerConfig = new JButton("⚙  Cấu hình máy chủ");
        btnServerConfig.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnServerConfig.setForeground(UIUtils.TEXT_SECONDARY);
        btnServerConfig.setBorder(new EmptyBorder(8, 10, 8, 10));
        btnServerConfig.setContentAreaFilled(false);
        btnServerConfig.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnServerConfig.addActionListener(e -> showServerConfigDialog());
        alternateActions.add(btnServerConfig);

        JButton btnSwitchToWeb = new JButton("Mở bản web ↗");
        btnSwitchToWeb.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSwitchToWeb.setForeground(UIUtils.PRIMARY_BLUE);
        btnSwitchToWeb.setBorder(new EmptyBorder(8, 10, 8, 10));
        btnSwitchToWeb.setContentAreaFilled(false);
        btnSwitchToWeb.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSwitchToWeb.addActionListener(e -> openWebClient());
        alternateActions.add(btnSwitchToWeb);
        cardPanel.add(alternateActions);

        // 9. Dòng chuyển chế độ Đăng nhập <-> Đăng ký
        lblToggleMode = new JLabel("<html>Chưa có tài khoản? <font color='#0F766E'><b>Đăng ký</b></font></html>");
        lblToggleMode.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblToggleMode.setForeground(new Color(100, 116, 139));
        lblToggleMode.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblToggleMode.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblToggleMode.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleAuthMode();
            }
        });
        cardPanel.add(lblToggleMode);

        cardPanel.add(Box.createVerticalStrut(8));

        // 10. Thanh trạng thái kết nối
        lblStatus = new JLabel("● Sẵn sàng kết nối (" + serverHost + ":" + serverPort + ")");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblStatus.setForeground(new Color(148, 163, 184));
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(lblStatus);

        backgroundPanel.add(cardPanel);
        setContentPane(backgroundPanel);

        // Sự kiện gửi form
        btnMainAction.addActionListener(e -> performSubmit());
        txtUser.addActionListener(e -> txtPass.requestFocus());
        txtPass.addActionListener(e -> performSubmit());
    }

    private void openWebClient() {
        try {
            URI webUri = new URI("http", null, serverHost, 8080, "/", null, null);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("Không tìm thấy trình duyệt mặc định.");
            }
            Desktop.getDesktop().browse(webUri);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Không mở được giao diện web tại " + serverHost + ":8080. Hãy kiểm tra máy chủ đang chạy.\n" + ex.getMessage(),
                    "Không mở được web", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Tạo khung nhập Neumorphic Pill bo cong mềm mại
     */
    private JPanel createNeumorphicFieldPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                int w = getWidth();
                int h = getHeight();
                int arc = h;

                // Đổ bóng Neumorphic nhẹ
                g2.setColor(new Color(226, 232, 240, 140));
                g2.fillRoundRect(2, 2, w - 4, h - 2, arc, arc);

                // Nền trắng tinh tế
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

                // Viền xám mờ mềm
                g2.setColor(new Color(226, 232, 240));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(340, 48));
        p.setMaximumSize(new Dimension(340, 48));
        p.setBorder(new EmptyBorder(4, 12, 4, 10));
        return p;
    }

    /**
     * Tạo nút mạng xã hội Neumorphic Squircle
     */
    private interface IconPainter {
        void paint(Graphics2D g2, int w, int h);
    }

    private JButton createNeumorphicSocialButton(String toolTip, IconPainter painter, Runnable onClick) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                int w = getWidth();
                int h = getHeight();
                int arc = 16;

                // Bóng Neumorphic dưới
                g2.setColor(new Color(214, 223, 238, 150));
                g2.fillRoundRect(2, 3, w - 4, h - 4, arc, arc);

                // Thân nút
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 2, h - 2, arc, arc);

                // Viền nhẹ
                g2.setColor(new Color(230, 238, 248));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 2, h - 2, arc, arc);

                painter.paint(g2, w, h);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(50, 48));
        btn.setMaximumSize(new Dimension(50, 48));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(toolTip);
        btn.addActionListener(e -> onClick.run());
        return btn;
    }

    /**
     * Chuyển đổi giữa chế độ Đăng nhập và Đăng ký
     */
    private void toggleAuthMode() {
        isRegisterMode = !isRegisterMode;
        if (isRegisterMode) {
            lblTitle.setText("Tạo tài khoản");
            lblSub.setText("Đăng ký để tham gia cuộc trò chuyện");
            btnMainAction.setText("Đăng ký");
            lblToggleMode.setText("<html>Đã có tài khoản? <font color='#0F766E'><b>Đăng nhập</b></font></html>");
        } else {
            lblTitle.setText("Chào mừng trở lại");
            lblSub.setText("Đăng nhập để tiếp tục trò chuyện");
            btnMainAction.setText("Đăng nhập");
            lblToggleMode.setText("<html>Chưa có tài khoản? <font color='#0F766E'><b>Đăng ký</b></font></html>");
        }
        revalidate();
        repaint();
    }

    /**
     * Hộp thoại Cấu hình Server Host/Port để chạy trên nhiều máy khác nhau
     */
    private void showServerConfigDialog() {
        JDialog dlg = new JDialog(this, "⚡ Cấu Hình Địa Chỉ Server", true);
        dlg.setSize(380, 260);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(3, 2, 10, 12));
        form.setBorder(new EmptyBorder(20, 24, 10, 24));
        form.setBackground(Color.WHITE);

        form.add(new JLabel("Server IP / Host:"));
        JTextField txtH = new JTextField(serverHost);
        form.add(txtH);

        form.add(new JLabel("Server Port:"));
        JTextField txtP = new JTextField(String.valueOf(serverPort));
        form.add(txtP);

        // Hiển thị IP LAN hiện tại của máy để gợi ý
        String localIp = "127.0.0.1";
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}
        form.add(new JLabel("Gợi ý IP mạng LAN:"));
        JLabel lblLocal = new JLabel(localIp);
        lblLocal.setForeground(new Color(37, 117, 252));
        lblLocal.setFont(new Font("Segoe UI", Font.BOLD, 12));
        form.add(lblLocal);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnBar.setBackground(Color.WHITE);

        JButton btnSave = new JButton("Lưu Cấu Hình");
        btnSave.setBackground(new Color(37, 117, 252));
        btnSave.setForeground(Color.WHITE);
        btnSave.addActionListener(e -> {
            try {
                this.serverHost = txtH.getText().trim();
                this.serverPort = Integer.parseInt(txtP.getText().trim());
                lblStatus.setText("● Cấu hình máy chủ: " + serverHost + ":" + serverPort);
                dlg.dispose();
                JOptionPane.showMessageDialog(this, "Đã lưu cấu hình kết nối tới: " + serverHost + ":" + serverPort, "Thành Công", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Port không hợp lệ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnBar.add(btnSave);
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btnBar, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void showForgotPasswordDialog() {
        JOptionPane.showMessageDialog(this,
                "Để bảo mật, vui lòng liên hệ Quản trị viên (Admin) hoặc dùng lệnh đổi mật khẩu trong phòng chat sau khi đăng nhập.",
                "Quên Mật Khẩu",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void performSubmit() {
        String username = txtUser.getText().trim();
        String password = new String(txtPass.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }

        btnMainAction.setEnabled(false);
        lblStatus.setText("⏳ Đang kết nối tới " + serverHost + ":" + serverPort + "...");
        lblStatus.setForeground(new Color(37, 117, 252));

        new Thread(() -> {
            boolean connected = socketClient.connect(serverHost, serverPort, this);
            if (!connected) {
                SwingUtilities.invokeLater(() -> {
                    btnMainAction.setEnabled(true);
                    showError("Không thể kết nối máy chủ (" + serverHost + ":" + serverPort + "). Hãy kiểm tra xem Server đã bật chưa!");
                });
                return;
            }

            if (isRegisterMode) {
                socketClient.register(username, password, Protocol.ROLE_USER);
            } else {
                socketClient.login(username, password);
            }
        }).start();
    }

    private void showError(String msg) {
        lblStatus.setText("❌ " + msg);
        lblStatus.setForeground(new Color(239, 68, 68));
    }

    // =========================================================================
    // CALLBACKS GIAO THỨC TỪ SERVERLISTENER
    // =========================================================================

    @Override
    public void onLoginResult(boolean success, String role, String username, String message) {
        SwingUtilities.invokeLater(() -> {
            btnMainAction.setEnabled(true);
            if (success) {
                lblStatus.setText("✅ Đăng nhập thành công! Đang tải...");
                lblStatus.setForeground(new Color(22, 163, 74));
                dispose();
                // Mở cửa sổ phòng chat chính
                ChatRoomFrame chatRoom = new ChatRoomFrame(socketClient, username, role);
                chatRoom.setVisible(true);
            } else {
                showError(message);
            }
        });
    }

    @Override
    public void onRegisterResult(boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            btnMainAction.setEnabled(true);
            if (success) {
                JOptionPane.showMessageDialog(this, "Đăng ký thành công! Hãy tiến hành đăng nhập.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                toggleAuthMode();
            } else {
                showError(message);
            }
        });
    }

    @Override
    public void onConnectionLost(String reason) {
        SwingUtilities.invokeLater(() -> {
            btnMainAction.setEnabled(true);
            showError(reason);
        });
    }

    @Override public void onCreateGroupResult(boolean success, String groupName, String groupCode, String message) {}
    @Override public void onJoinGroupResult(boolean success, String groupName, String message) {}
    @Override public void onGroupListUpdated(List<ChatGroup> groups) {}
    @Override public void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content) {}
    @Override public void onPublicMessageReceived(String s, String r, String t, String c) {}
    @Override public void onPrivateMessageReceived(String s, String tg, String t, String c) {}
    @Override public void onSystemMessage(String content) {}
    @Override public void onUserListUpdated(List<User> onlineUsers) {}
    @Override public void onHistoryReceived(String groupName, List<MessagePacket> history) {}
    @Override public void onKicked(String reason) {}
    @Override public void onMuted(int minutes, String reason) {}
    @Override public void onUnmuted() {}
    @Override public void onChangePasswordResult(boolean success, String message) {}
}
