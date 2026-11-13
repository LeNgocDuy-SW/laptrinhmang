package client.ui;

import client.network.ChatEventListener;
import client.network.SocketClient;
import common.ChatGroup;
import common.MessagePacket;
import common.Protocol;
import common.User;
import java.io.File;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatRoomFrame: Màn hình giao diện chính chuẩn thiết kế Dribbble 3 cột.
 * Hỗ trợ Tạo nhóm mới, Xác thực mã nhóm (PIN), Phân tách tin nhắn theo nhóm và Chat riêng 1-1.
 */
public class ChatRoomFrame extends JFrame implements ChatEventListener {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final SocketClient socketClient;
    private final String currentUsername;
    private final String currentRole;

    private String activeRoom = "Friends & Family";
    private String activeGroupCreator = "admin";
    private String privateChatTarget = null; // null = chat nhóm
    private boolean isMuted = false;

    // Trạng thái trả lời tin nhắn (Reply)
    private String replyTargetSender = null;
    private String replyTargetSnippet = null;
    private JPanel replyPreviewBar;
    private JLabel lblReplyPreview;

    // Danh sách nhóm mà người dùng này đã mở khóa bằng mã
    private final Set<String> unlockedGroups = new HashSet<>();

    // Bộ đếm tin nhắn chưa đọc cho từng nhóm (chữ thường để so khớp an toàn)
    private final Map<String, Integer> unreadGroupCounts = new ConcurrentHashMap<>();

    // Danh sách dữ liệu
    private List<User> onlineUsers = new ArrayList<>();
    private List<String[]> groupMembers = new ArrayList<>();
    private final List<GroupItemPanel> groupPanels = new ArrayList<>();
    private final JPanel groupsScrollContainer;

    // UI Cột trái
    private RoomCard favouritesCard;
    private PillTextField txtSearchLeft;

    // UI Cột giữa
    private JLabel lblChatTitle;
    private JLabel lblChatSubtitle;
    private JPanel messagesContainer;
    private JScrollPane scrollMessages;
    private JTextField txtChatInput;
    private PillButton btnSend;

    // UI Cột phải
    private JLabel lblRightGroupTitle;
    private JLabel lblRightMemberCount;
    private JPanel membersListContainer;
    private JPanel leaderControlsPanel;

    public ChatRoomFrame(SocketClient socketClient, String username, String role) {
        super("Multi-Client Chat - " + username + " (" + role + ")");
        this.socketClient = socketClient;
        this.currentUsername = username;
        this.currentRole = role;

        // Mặc định mở khóa nhóm chính Friends & Family
        unlockedGroups.add("Friends & Family");

        // Đăng ký nhận sự kiện mạng từ Server
        if (socketClient != null) {
            socketClient.setEventListener(this);
        }

        groupsScrollContainer = new JPanel();
        groupsScrollContainer.setLayout(new BoxLayout(groupsScrollContainer, BoxLayout.Y_AXIS));
        groupsScrollContainer.setOpaque(false);

        initComponents();
        setupEvents();

        // Nạp danh sách & lịch sử nhóm mặc định khi vừa khởi động
        if (socketClient != null) {
            socketClient.requestUserList();
            socketClient.requestGroupList();
            socketClient.requestHistory(activeRoom);
            socketClient.requestGroupMembers(activeRoom);
        }
    }

    private void initComponents() {
        setSize(1200, 750);
        setMinimumSize(new Dimension(1020, 620));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UIUtils.BG_MAIN);

        JPanel leftSidebar = createLeftSidebar();
        JPanel centerChat = createCenterChatArea();
        JPanel rightSidebar = createRightSidebar();

        root.add(leftSidebar, BorderLayout.WEST);
        root.add(centerChat, BorderLayout.CENTER);
        root.add(rightSidebar, BorderLayout.EAST);

        setContentPane(root);
    }

    // =========================================================================
    // CỘT 1: SIDEBAR TRÁI (CHANNELS, FAVOURITES, GROUPS, PROFILE)
    // =========================================================================
    private JPanel createLeftSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 14));
        sidebar.setPreferredSize(new Dimension(290, 0));
        sidebar.setBackground(UIUtils.BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, UIUtils.BORDER_LIGHT),
                new EmptyBorder(16, 16, 16, 16)
        ));

        // 1. Profile & Add Button
        JPanel profileHeader = new JPanel(new BorderLayout(10, 0));
        profileHeader.setOpaque(false);

        AvatarPanel userAvatar = new AvatarPanel(currentUsername, 42, true, true);
        profileHeader.add(userAvatar, BorderLayout.WEST);

        JPanel nameCol = new JPanel(new GridLayout(2, 1, 0, 2));
        nameCol.setOpaque(false);
        JLabel lblHello = new JLabel("Hello there");
        lblHello.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblHello.setForeground(UIUtils.TEXT_MUTED);

        JLabel lblName = new JLabel(currentUsername);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblName.setForeground(UIUtils.TEXT_DARK);

        nameCol.add(lblHello);
        nameCol.add(lblName);
        profileHeader.add(nameCol, BorderLayout.CENTER);

        PillButton btnAdd = new PillButton("+", PillButton.Style.CIRCULAR_ICON);
        btnAdd.setToolTipText("Tạo nhóm mới hoặc tham gia bằng mã PIN");
        btnAdd.addActionListener(e -> showAddGroupPopup(btnAdd));
        profileHeader.add(btnAdd, BorderLayout.EAST);

        // 2. Search Field
        txtSearchLeft = new PillTextField("Search", "🔍");
        txtSearchLeft.setPreferredSize(new Dimension(250, 36));
        txtSearchLeft.addCaretListener(e -> filterSidebar());

        JPanel topSection = new JPanel(new BorderLayout(0, 12));
        topSection.setOpaque(false);
        topSection.add(profileHeader, BorderLayout.NORTH);
        topSection.add(txtSearchLeft, BorderLayout.SOUTH);
        sidebar.add(topSection, BorderLayout.NORTH);

        // 3. Scrollable List: Favourites & Groups
        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.setOpaque(false);

        // Mục Favourites
        JLabel lblFav = new JLabel("Favourites");
        lblFav.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblFav.setForeground(UIUtils.TEXT_SECONDARY);
        lblFav.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollContent.add(lblFav);
        scrollContent.add(Box.createVerticalStrut(8));

        favouritesCard = new RoomCard("Friends & Family", "25 Jun, 2022", 1, 0, () -> onSelectGroup("Friends & Family"));
        favouritesCard.setSelected(true);
        favouritesCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollContent.add(favouritesCard);
        scrollContent.add(Box.createVerticalStrut(16));

        // Mục Groups
        JLabel lblGroups = new JLabel("Groups (Nhóm Người Dùng Tạo)");
        lblGroups.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblGroups.setForeground(UIUtils.TEXT_SECONDARY);
        lblGroups.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollContent.add(lblGroups);
        scrollContent.add(Box.createVerticalStrut(8));

        scrollContent.add(groupsScrollContainer);

        JScrollPane sidebarScroll = new JScrollPane(scrollContent);
        sidebarScroll.setOpaque(false);
        sidebarScroll.getViewport().setOpaque(false);
        sidebarScroll.setBorder(BorderFactory.createEmptyBorder());
        sidebarScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        sidebarScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        sidebar.add(sidebarScroll, BorderLayout.CENTER);
        return sidebar;
    }

    // =========================================================================
    // CỘT 2: KHUNG CHAT TRUNG TÂM (CHAT MESSAGES & PILL INPUT BAR)
    // =========================================================================
    private JPanel createCenterChatArea() {
        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setBackground(Color.WHITE);

        // 1. Header Bar
        JPanel chatHeader = new JPanel(new BorderLayout(10, 0));
        chatHeader.setBackground(Color.WHITE);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIUtils.BORDER_LIGHT),
                new EmptyBorder(14, 20, 14, 20)
        ));

        JPanel titleCol = new JPanel(new GridLayout(2, 1, 0, 2));
        titleCol.setOpaque(false);

        lblChatTitle = new JLabel("Friends & Family");
        lblChatTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblChatTitle.setForeground(UIUtils.TEXT_DARK);

        lblChatSubtitle = new JLabel("🟢 1 Online");
        lblChatSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblChatSubtitle.setForeground(UIUtils.ONLINE_GREEN);

        titleCol.add(lblChatTitle);
        titleCol.add(lblChatSubtitle);
        chatHeader.add(titleCol, BorderLayout.WEST);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerRight.setOpaque(false);

        PillButton btnCreateGroupQuick = new PillButton("✨ Tạo Nhóm", PillButton.Style.SECONDARY);
        btnCreateGroupQuick.setPreferredSize(new Dimension(105, 32));
        btnCreateGroupQuick.addActionListener(e -> new CreateGroupDialog(this, socketClient).setVisible(true));
        headerRight.add(btnCreateGroupQuick);

        PillButton btnJoinGroupQuick = new PillButton("🔑 Tham Gia Nhóm", PillButton.Style.PRIMARY);
        btnJoinGroupQuick.setPreferredSize(new Dimension(135, 32));
        btnJoinGroupQuick.addActionListener(e -> new JoinGroupDialog(this, socketClient, "").setVisible(true));
        headerRight.add(btnJoinGroupQuick);

        chatHeader.add(headerRight, BorderLayout.EAST);
        center.add(chatHeader, BorderLayout.NORTH);

        // 2. Khung bong bóng tin nhắn
        messagesContainer = new JPanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(Color.WHITE);
        messagesContainer.setBorder(new EmptyBorder(10, 0, 10, 0));

        scrollMessages = new JScrollPane(messagesContainer);
        scrollMessages.setBackground(Color.WHITE);
        scrollMessages.getViewport().setBackground(Color.WHITE);
        scrollMessages.setBorder(BorderFactory.createEmptyBorder());
        scrollMessages.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollMessages.getVerticalScrollBar().setUnitIncrement(16);
        scrollMessages.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        center.add(scrollMessages, BorderLayout.CENTER);

        // 3. Thanh trả lời tin nhắn (Reply Preview) + Thanh nhập liệu Pill
        JPanel bottomWrap = new JPanel(new BorderLayout(0, 6));
        bottomWrap.setBackground(Color.WHITE);
        bottomWrap.setBorder(new EmptyBorder(8, 20, 16, 20));

        replyPreviewBar = new JPanel(new BorderLayout(8, 0));
        replyPreviewBar.setBackground(new Color(239, 246, 255));
        replyPreviewBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, UIUtils.PRIMARY_BLUE),
                new EmptyBorder(6, 10, 6, 10)
        ));
        lblReplyPreview = new JLabel("↩ Đang trả lời...");
        lblReplyPreview.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblReplyPreview.setForeground(UIUtils.TEXT_DARK);
        JButton btnCancelReply = new JButton("✕");
        btnCancelReply.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCancelReply.setForeground(UIUtils.TEXT_MUTED);
        btnCancelReply.setContentAreaFilled(false);
        btnCancelReply.setBorder(null);
        btnCancelReply.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancelReply.addActionListener(e -> clearReplyState());
        replyPreviewBar.add(lblReplyPreview, BorderLayout.CENTER);
        replyPreviewBar.add(btnCancelReply, BorderLayout.EAST);
        replyPreviewBar.setVisible(false);
        bottomWrap.add(replyPreviewBar, BorderLayout.NORTH);

        JPanel pillInputBox = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                int arc = getHeight();
                g2.setColor(new Color(248, 249, 251));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.setColor(UIUtils.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pillInputBox.setOpaque(false);
        pillInputBox.setPreferredSize(new Dimension(0, 48));
        pillInputBox.setBorder(new EmptyBorder(4, 14, 4, 6));

        JButton btnAttach = new JButton("📎");
        btnAttach.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        btnAttach.setBorder(null);
        btnAttach.setContentAreaFilled(false);
        btnAttach.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAttach.setToolTipText("Gửi file đính kèm");
        btnAttach.addActionListener(e -> sendFileAttachment());

        txtChatInput = new JTextField();
        txtChatInput.setOpaque(false);
        txtChatInput.setBorder(null);
        txtChatInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtChatInput.setForeground(UIUtils.TEXT_DARK);
        txtChatInput.setCaretColor(UIUtils.PRIMARY_BLUE);
        txtChatInput.putClientProperty("JTextField.placeholderText", "Type a message...");

        JPanel inputActionsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        inputActionsRight.setOpaque(false);

        JButton btnEmoji = new JButton("😊");
        btnEmoji.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        btnEmoji.setBorder(null);
        btnEmoji.setContentAreaFilled(false);
        btnEmoji.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEmoji.addActionListener(e -> showEmojiPicker());

        btnSend = new PillButton("Send", PillButton.Style.PRIMARY);
        btnSend.setPreferredSize(new Dimension(78, 38));

        inputActionsRight.add(btnEmoji);
        inputActionsRight.add(btnSend);

        pillInputBox.add(btnAttach, BorderLayout.WEST);
        pillInputBox.add(txtChatInput, BorderLayout.CENTER);
        pillInputBox.add(inputActionsRight, BorderLayout.EAST);

        bottomWrap.add(pillInputBox, BorderLayout.CENTER);
        center.add(bottomWrap, BorderLayout.SOUTH);

        return center;
    }

    // =========================================================================
    // CỘT 3: SIDEBAR PHẢI (GROUP INFO & MEMBERS LIST)
    // =========================================================================
    private JPanel createRightSidebar() {
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setPreferredSize(new Dimension(270, 0));
        right.setBackground(Color.WHITE);
        right.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, UIUtils.BORDER_LIGHT),
                new EmptyBorder(16, 16, 16, 16)
        ));

        // Top Info
        JPanel topInfo = new JPanel(new BorderLayout(0, 6));
        topInfo.setOpaque(false);

        JPanel infoTitleRow = new JPanel(new BorderLayout());
        infoTitleRow.setOpaque(false);
        JLabel lblGInfo = new JLabel("Group Info");
        lblGInfo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblGInfo.setForeground(UIUtils.TEXT_MUTED);

        JButton btnMore = new JButton("•••");
        btnMore.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnMore.setBorder(null);
        btnMore.setContentAreaFilled(false);
        btnMore.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnMore.addActionListener(e -> showOptionsMenu(btnMore));

        infoTitleRow.add(lblGInfo, BorderLayout.WEST);
        infoTitleRow.add(btnMore, BorderLayout.EAST);
        topInfo.add(infoTitleRow, BorderLayout.NORTH);

        lblRightGroupTitle = new JLabel("Friends & Family");
        lblRightGroupTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblRightGroupTitle.setForeground(UIUtils.TEXT_DARK);
        topInfo.add(lblRightGroupTitle, BorderLayout.CENTER);

        JPanel metaCol = new JPanel(new GridLayout(2, 1, 0, 4));
        metaCol.setOpaque(false);
        JLabel lblDetails = new JLabel("👥 Group details: Phòng chat bảo mật");
        lblDetails.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDetails.setForeground(UIUtils.TEXT_SECONDARY);

        JLabel lblDate = new JLabel("📅 25 Jun, 2022");
        lblDate.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDate.setForeground(UIUtils.TEXT_MUTED);

        metaCol.add(lblDetails);
        metaCol.add(lblDate);
        topInfo.add(metaCol, BorderLayout.SOUTH);

        PillTextField txtSearchInChat = new PillTextField("Search in chat", "🔍");
        txtSearchInChat.setPreferredSize(new Dimension(230, 34));

        JPanel topSection = new JPanel(new BorderLayout(0, 12));
        topSection.setOpaque(false);
        topSection.add(topInfo, BorderLayout.NORTH);
        topSection.add(txtSearchInChat, BorderLayout.SOUTH);
        right.add(topSection, BorderLayout.NORTH);

        // Danh sách Members
        JPanel membersSection = new JPanel(new BorderLayout(0, 8));
        membersSection.setOpaque(false);

        JPanel memberHeaderRow = new JPanel(new BorderLayout());
        memberHeaderRow.setOpaque(false);

        lblRightMemberCount = new JLabel("Members");
        lblRightMemberCount.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblRightMemberCount.setForeground(UIUtils.TEXT_DARK);

        JLabel lblChevron = new JLabel("∨");
        lblChevron.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblChevron.setForeground(UIUtils.TEXT_MUTED);

        memberHeaderRow.add(lblRightMemberCount, BorderLayout.WEST);
        memberHeaderRow.add(lblChevron, BorderLayout.EAST);
        membersSection.add(memberHeaderRow, BorderLayout.NORTH);

        membersListContainer = new JPanel();
        membersListContainer.setLayout(new BoxLayout(membersListContainer, BoxLayout.Y_AXIS));
        membersListContainer.setOpaque(false);

        JScrollPane scrollMembers = new JScrollPane(membersListContainer);
        scrollMembers.setOpaque(false);
        scrollMembers.getViewport().setOpaque(false);
        scrollMembers.setBorder(BorderFactory.createEmptyBorder());
        scrollMembers.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollMembers.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        membersSection.add(scrollMembers, BorderLayout.CENTER);
        right.add(membersSection, BorderLayout.CENTER);

        // Thanh đặc quyền Trưởng nhóm ở đáy sidebar phải
        leaderControlsPanel = new JPanel(new GridLayout(3, 1, 0, 6));
        leaderControlsPanel.setOpaque(false);
        leaderControlsPanel.setBorder(BorderFactory.createTitledBorder("👑 Đặc Quyền Trưởng Nhóm"));

        PillButton btnChangePin = new PillButton("🔑 Đổi Mã PIN Nhóm", PillButton.Style.SECONDARY);
        btnChangePin.addActionListener(e -> {
            String newPin = JOptionPane.showInputDialog(this, "Nhập mã PIN mới cho nhóm [" + activeRoom + "]:", "Đổi Mã Nhóm", JOptionPane.QUESTION_MESSAGE);
            if (newPin != null && !newPin.trim().isEmpty()) {
                socketClient.sendGroupCommand(Protocol.GACT_SET_CODE, activeRoom, newPin.trim());
            }
        });

        PillButton btnClearGrpHist = new PillButton("🧹 Xóa Lịch Sử Nhóm", PillButton.Style.SECONDARY);
        btnClearGrpHist.addActionListener(e -> {
            int cf = JOptionPane.showConfirmDialog(this, "Xóa toàn bộ tin nhắn trong nhóm [" + activeRoom + "]?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (cf == JOptionPane.YES_OPTION) {
                socketClient.sendGroupCommand(Protocol.GACT_CLEAR_HISTORY, activeRoom, "");
                messagesContainer.removeAll();
                messagesContainer.revalidate();
                messagesContainer.repaint();
            }
        });

        PillButton btnDeleteGrp = new PillButton("🗑️ Giải Tán Nhóm", PillButton.Style.DANGER);
        btnDeleteGrp.addActionListener(e -> {
            if (Protocol.DEFAULT_GROUP.equalsIgnoreCase(activeRoom)) {
                JOptionPane.showMessageDialog(this, "Không thể xóa phòng mặc định!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int cf = JOptionPane.showConfirmDialog(this, "Bạn chắc chắn muốn giải tán vĩnh viễn nhóm [" + activeRoom + "]?", "Giải Tán Nhóm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (cf == JOptionPane.YES_OPTION) {
                socketClient.sendGroupCommand(Protocol.GACT_DELETE, activeRoom, "");
            }
        });

        leaderControlsPanel.add(btnChangePin);
        leaderControlsPanel.add(btnClearGrpHist);
        leaderControlsPanel.add(btnDeleteGrp);
        leaderControlsPanel.setVisible(false);
        right.add(leaderControlsPanel, BorderLayout.SOUTH);

        return right;
    }

    private void setupEvents() {
        btnSend.addActionListener(e -> performSendMessage());
        txtChatInput.addActionListener(e -> performSendMessage());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doLogout();
            }
        });
    }

    // =========================================================================
    // XỬ LÝ CHỌN NHÓM & YÊU CẦU MÃ NHÓM (GROUP CODE VERIFICATION)
    // =========================================================================
    private void onSelectGroup(String groupName) {
        // Kiểm tra xem đã mở khóa nhóm này bằng mã nhóm chưa
        if (!unlockedGroups.contains(groupName)) {
            // Mở hộp thoại yêu cầu nhập mã nhóm
            JoinGroupDialog dlg = new JoinGroupDialog(this, socketClient, groupName);
            dlg.setVisible(true);
            return;
        }

        // Đã mở khóa -> Chuyển phòng
        this.activeRoom = groupName;
        this.privateChatTarget = null;
        clearReplyState();

        // Xóa thông báo tin nhắn chưa đọc của nhóm vừa chọn
        unreadGroupCounts.put(groupName.toLowerCase(), 0);
        if ("Friends & Family".equalsIgnoreCase(groupName)) {
            if (favouritesCard != null) favouritesCard.setUnreadCount(0);
        }

        favouritesCard.setSelected("Friends & Family".equalsIgnoreCase(groupName));
        for (GroupItemPanel g : groupPanels) {
            boolean isThis = g.getGroupName().equalsIgnoreCase(groupName);
            g.setSelected(isThis);
            if (isThis) {
                g.setBadgeCount(0);
            }
        }

        lblChatTitle.setText(groupName);
        lblChatTitle.setForeground(UIUtils.TEXT_DARK);
        lblChatSubtitle.setText("🟢 " + Math.max(1, onlineUsers.size()) + " Online");
        lblRightGroupTitle.setText(groupName);

        // Xóa tin nhắn cũ trên màn hình và yêu cầu Server nạp lịch sử + danh sách thành viên nhóm
        messagesContainer.removeAll();
        messagesContainer.revalidate();
        messagesContainer.repaint();

        socketClient.requestHistory(groupName);
        socketClient.requestGroupMembers(groupName);
        txtChatInput.requestFocus();
    }

    private void setReplyTarget(String sender, String snippet) {
        this.replyTargetSender = sender;
        this.replyTargetSnippet = snippet != null ? snippet.replace("\n", " ").replace("|", "/") : "";
        String shortSnip = replyTargetSnippet.length() > 45 ? replyTargetSnippet.substring(0, 45) + "..." : replyTargetSnippet;
        lblReplyPreview.setText("<html><b>↩ Đang trả lời @" + sender + ":</b> " + shortSnip + "</html>");
        replyPreviewBar.setVisible(true);
        txtChatInput.requestFocus();
    }

    private void clearReplyState() {
        this.replyTargetSender = null;
        this.replyTargetSnippet = null;
        if (replyPreviewBar != null) {
            replyPreviewBar.setVisible(false);
        }
    }

    private void sendFormattedContent(String rawContent) {
        if (isMuted) {
            JOptionPane.showMessageDialog(this, "Bạn đang bị cấm chat bởi Admin!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (rawContent == null || rawContent.trim().isEmpty()) return;

        String payloadContent = rawContent.trim();
        if (replyTargetSender != null) {
            String safeSnip = replyTargetSnippet != null ? replyTargetSnippet.replace("]]", ")") : "";
            payloadContent = "[[REPLY|" + replyTargetSender + "|" + safeSnip + "]]" + payloadContent;
            clearReplyState();
        }

        if (privateChatTarget != null) {
            socketClient.sendPrivateMessage(privateChatTarget, payloadContent);
        } else {
            socketClient.sendGroupMessage(activeRoom, payloadContent);
        }
    }

    private void performSendMessage() {
        String text = txtChatInput.getText().trim();
        if (text.isEmpty()) return;

        sendFormattedContent(text);
        txtChatInput.setText("");
        txtChatInput.requestFocus();
    }

    private void addMessageBubble(String sender, String role, String time, String content, boolean isMine, boolean isPrivate, boolean isSystem) {
        MessageBubblePanel bubble = new MessageBubblePanel(sender, role, time, content, isMine, isPrivate, isSystem,
                isSystem ? null : (s, snip) -> setReplyTarget(s, snip));
        bubble.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagesContainer.add(bubble);
        messagesContainer.revalidate();
        messagesContainer.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar v = scrollMessages.getVerticalScrollBar();
            v.setValue(v.getMaximum());
        });
    }

    private void showAddGroupPopup(Component source) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem itemCreate = new JMenuItem("✨ Tạo Phòng / Nhóm Mới (Đặt Mã Nhóm)");
        itemCreate.setFont(new Font("Segoe UI", Font.BOLD, 12));
        itemCreate.addActionListener(e -> new CreateGroupDialog(this, socketClient).setVisible(true));
        menu.add(itemCreate);

        JMenuItem itemJoin = new JMenuItem("🔑 Tham Gia Nhóm Bằng Mã");
        itemJoin.addActionListener(e -> new JoinGroupDialog(this, socketClient, "").setVisible(true));
        menu.add(itemJoin);

        JMenuItem itemPrivate = new JMenuItem("💬 Bắt Đầu Chat Riêng 1-1 Với Người Dùng");
        itemPrivate.addActionListener(e -> {
            String target = JOptionPane.showInputDialog(this, "Nhập tên tài khoản muốn chat riêng:", "Chat 1-1", JOptionPane.QUESTION_MESSAGE);
            if (target != null && !target.trim().isEmpty()) {
                switchToPrivateChat(target.trim());
            }
        });
        menu.add(itemPrivate);

        menu.show(source, 0, source.getHeight());
    }

    private void switchToPrivateChat(String target) {
        this.privateChatTarget = target;
        favouritesCard.setSelected(false);
        for (GroupItemPanel g : groupPanels) g.setSelected(false);

        lblChatTitle.setText("🔒 Chat riêng với: @" + target);
        lblChatTitle.setForeground(new Color(124, 58, 237));
        lblChatSubtitle.setText("Cuộc trò chuyện bảo mật 1-1");
        lblRightGroupTitle.setText("@" + target);

        messagesContainer.removeAll();
        messagesContainer.revalidate();
        messagesContainer.repaint();
        txtChatInput.requestFocus();
    }

    private boolean isCurrentGroupLeader() {
        return (activeGroupCreator != null && activeGroupCreator.equalsIgnoreCase(currentUsername))
                || Protocol.ROLE_ADMIN.equalsIgnoreCase(currentRole);
    }

    private void refreshMembersUI() {
        membersListContainer.removeAll();

        if (leaderControlsPanel != null) {
            leaderControlsPanel.setVisible(privateChatTarget == null && isCurrentGroupLeader());
        }

        if (!groupMembers.isEmpty() && privateChatTarget == null) {
            int onlineInGrp = 0;
            for (String[] m : groupMembers) {
                String uname = m[0];
                boolean isOnline = "1".equals(m[1]);
                boolean isGrpMuted = "1".equals(m[2]);
                if (isOnline) onlineInGrp++;

                boolean isMe = uname.equalsIgnoreCase(currentUsername);
                boolean isLeader = uname.equalsIgnoreCase(activeGroupCreator);
                String displayRole = isLeader ? "TRƯỞNG NHÓM" : "USER";
                String status = isGrpMuted ? Protocol.STATUS_MUTED : (isOnline ? Protocol.STATUS_ACTIVE : "OFFLINE");

                User u = new User(uname, isLeader ? Protocol.ROLE_ADMIN : displayRole, status);
                MemberItemPanel item = new MemberItemPanel(u, isMe, isMe ? () -> {} : () -> showUserActionsPopup(u, isGrpMuted));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                membersListContainer.add(item);
            }
            lblRightMemberCount.setText("Thành viên nhóm (" + groupMembers.size() + ")");
            lblChatSubtitle.setText("👥 " + groupMembers.size() + " Thành viên • 🟢 " + Math.max(1, onlineInGrp) + " Online");
        } else {
            User me = new User(currentUsername, currentRole, isMuted ? Protocol.STATUS_MUTED : Protocol.STATUS_ACTIVE);
            MemberItemPanel meItem = new MemberItemPanel(me, true, () -> {});
            meItem.setAlignmentX(Component.LEFT_ALIGNMENT);
            membersListContainer.add(meItem);

            for (User u : onlineUsers) {
                if (u.getUsername().equalsIgnoreCase(currentUsername)) continue;
                MemberItemPanel item = new MemberItemPanel(u, false, () -> showUserActionsPopup(u, u.isMuted()));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                membersListContainer.add(item);
            }
            lblRightMemberCount.setText("Members (" + onlineUsers.size() + ")");
            if (privateChatTarget == null) {
                lblChatSubtitle.setText("👥 " + Math.max(1, onlineUsers.size()) + " Thành viên • 🟢 " + Math.max(1, onlineUsers.size()) + " Online");
            }
        }

        favouritesCard.setOnlineCount(Math.max(1, onlineUsers.size()));

        membersListContainer.revalidate();
        membersListContainer.repaint();
    }

    private void showUserActionsPopup(User target, boolean isGrpMuted) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem itemMsg = new JMenuItem("💬 Nhắn tin riêng với @" + target.getUsername());
        itemMsg.addActionListener(e -> switchToPrivateChat(target.getUsername()));
        menu.add(itemMsg);

        // Đặc quyền Trưởng Nhóm (trên nhóm đang mở)
        if (privateChatTarget == null && isCurrentGroupLeader() && !target.getUsername().equalsIgnoreCase(currentUsername)) {
            menu.addSeparator();
            if (isGrpMuted) {
                JMenuItem itemUnmuteGrp = new JMenuItem("🔊 Mở chặn nhắn tin trong nhóm [" + activeRoom + "]");
                itemUnmuteGrp.addActionListener(e -> socketClient.sendGroupCommand(Protocol.GACT_UNMUTE, activeRoom, target.getUsername()));
                menu.add(itemUnmuteGrp);
            } else {
                JMenuItem itemMuteGrp = new JMenuItem("🔇 Chặn @" + target.getUsername() + " nhắn tin trong nhóm");
                itemMuteGrp.addActionListener(e -> socketClient.sendGroupCommand(Protocol.GACT_MUTE, activeRoom, target.getUsername()));
                menu.add(itemMuteGrp);
            }

            if (!Protocol.DEFAULT_GROUP.equalsIgnoreCase(activeRoom)) {
                JMenuItem itemKickGrp = new JMenuItem("👢 Kick @" + target.getUsername() + " khỏi nhóm [" + activeRoom + "]");
                itemKickGrp.setForeground(UIUtils.BADGE_RED);
                itemKickGrp.addActionListener(e -> socketClient.sendGroupCommand(Protocol.GACT_KICK, activeRoom, target.getUsername()));
                menu.add(itemKickGrp);
            }
        }

        if (Protocol.ROLE_ADMIN.equalsIgnoreCase(currentRole)) {
            menu.addSeparator();

            JMenuItem itemKick = new JMenuItem("👢 [ADMIN] Kick @" + target.getUsername() + " khỏi Server");
            itemKick.addActionListener(e -> socketClient.sendAdminCommand(Protocol.ACT_KICK, target.getUsername(), "Bị Admin đuổi"));
            menu.add(itemKick);

            if (target.isMuted()) {
                JMenuItem itemUnmute = new JMenuItem("🔊 [ADMIN] Mở cấm chat toàn hệ thống");
                itemUnmute.addActionListener(e -> socketClient.sendAdminCommand(Protocol.ACT_UNMUTE, target.getUsername(), ""));
                menu.add(itemUnmute);
            } else {
                JMenu menuMute = new JMenu("🔇 [ADMIN] Cấm chat toàn hệ thống");
                JMenuItem m5 = new JMenuItem("5 phút");
                m5.addActionListener(e -> socketClient.sendAdminCommand(Protocol.ACT_MUTE, target.getUsername(), "5"));
                JMenuItem mForever = new JMenuItem("Vô thời hạn");
                mForever.addActionListener(e -> socketClient.sendAdminCommand(Protocol.ACT_MUTE, target.getUsername(), "0"));
                menuMute.add(m5);
                menuMute.add(mForever);
                menu.add(menuMute);
            }

            JMenuItem itemBan = new JMenuItem("🔒 [ADMIN] Khóa tài khoản (BAN)");
            itemBan.setForeground(UIUtils.BADGE_RED);
            itemBan.addActionListener(e -> socketClient.sendAdminCommand(Protocol.ACT_BAN, target.getUsername(), "Vi phạm quy tắc"));
            menu.add(itemBan);
        }

        menu.show(this, getWidth() - 260, 180);
    }

    private void filterSidebar() {
        String kw = txtSearchLeft.getText().trim().toLowerCase();
        for (GroupItemPanel g : groupPanels) {
            g.setVisible(kw.isEmpty() || g.getGroupName().toLowerCase().contains(kw));
        }
    }

    private void showOptionsMenu(JButton source) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem itemPass = new JMenuItem("🔑 Đổi mật khẩu");
        itemPass.addActionListener(e -> new ChangePasswordDialog(this, socketClient).setVisible(true));
        menu.add(itemPass);

        if (Protocol.ROLE_ADMIN.equalsIgnoreCase(currentRole)) {
            menu.addSeparator();
            JMenuItem itemAlert = new JMenuItem("📢 Phát thông báo Admin");
            itemAlert.addActionListener(e -> {
                String msg = JOptionPane.showInputDialog(this, "Nội dung thông báo toàn phòng:", "Thông Báo Admin", JOptionPane.QUESTION_MESSAGE);
                if (msg != null && !msg.trim().isEmpty()) {
                    socketClient.sendAdminCommand(Protocol.ACT_ALERT, "", msg.trim());
                }
            });
            menu.add(itemAlert);

            JMenuItem itemClear = new JMenuItem("🧹 Xóa sạch lịch sử");
            itemClear.addActionListener(e -> socketClient.sendAdminCommand(Protocol.ACT_CLEAR_HISTORY, "", ""));
            menu.add(itemClear);
        }

        menu.addSeparator();
        JMenuItem itemLogout = new JMenuItem("🚪 Đăng xuất");
        itemLogout.addActionListener(e -> doLogout());
        menu.add(itemLogout);

        menu.show(source, 0, source.getHeight());
    }

    private void showEmojiPicker() {
        JPopupMenu pickerPopup = new JPopupMenu();
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Tab 1: 30 Emoji biểu cảm
        String[] emojis = {
                "😀", "😂", "🥹", "😍", "🥰", "😎",
                "🥳", "🤩", "🤔", "😭", "😡", "🤯",
                "👍", "👎", "👏", "🙏", "🤝", "✌️",
                "❤️", "💔", "🔥", "⭐", "🎉", "🚀",
                "💯", "👀", "☕", "🍕", "🎵", "✨"
        };
        JPanel emojiGrid = new JPanel(new GridLayout(5, 6, 4, 4));
        emojiGrid.setBackground(Color.WHITE);
        emojiGrid.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (String emo : emojis) {
            JButton btn = new JButton(emo);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
            btn.setContentAreaFilled(false);
            btn.setBorder(null);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                txtChatInput.setText(txtChatInput.getText() + emo);
                pickerPopup.setVisible(false);
                txtChatInput.requestFocus();
            });
            emojiGrid.add(btn);
        }
        tabs.addTab("😊 Icon", emojiGrid);

        // Tab 2: Sticker Hoạt Hình Messenger (Pusheen Cat)
        String[][] pusheenStickers = {
                {"web/stickers/pusheen/144884739019126.gif", "Chào bạn"},
                {"web/stickers/pusheen/144884755685791.gif", "Thả tim"},
                {"web/stickers/pusheen/144884765685790.gif", "Nhảy múa"},
                {"web/stickers/pusheen/144884775685789.gif", "Cười tít"},
                {"web/stickers/pusheen/144884792352454.gif", "Làm việc"},
                {"web/stickers/pusheen/144884815685785.gif", "Ngủ khò"},
                {"web/stickers/pusheen/144884825685784.gif", "Thả like"},
                {"web/stickers/pusheen/144884835685783.gif", "Bắn tim"},
                {"web/stickers/pusheen/144884852352448.gif", "Khóc hu hu"},
                {"web/stickers/pusheen/144884879019112.gif", "Thùng giấy"},
                {"web/stickers/pusheen/144884895685777.gif", "Trà sữa"},
                {"web/stickers/pusheen/144884905685776.gif", "Cực ngầu"}
        };
        JPanel pusheenGrid = new JPanel(new GridLayout(3, 4, 6, 6));
        pusheenGrid.setBackground(Color.WHITE);
        pusheenGrid.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (String[] st : pusheenStickers) {
            ImageIcon icon = null;
            try {
                File f = new File(st[0]);
                if (f.exists()) {
                    icon = new ImageIcon(f.getAbsolutePath());
                } else {
                    File f2 = new File("client/" + st[0].replace("web/", ""));
                    if (f2.exists()) icon = new ImageIcon(f2.getAbsolutePath());
                }
            } catch (Exception ignored) {}

            JButton sBtn;
            if (icon != null && icon.getIconWidth() > 0) {
                Image scaled = icon.getImage().getScaledInstance(38, 38, Image.SCALE_SMOOTH);
                sBtn = new JButton(new ImageIcon(scaled));
            } else {
                sBtn = new JButton(st[1]);
            }
            sBtn.setToolTipText(st[1]);
            sBtn.setBackground(new Color(248, 250, 252));
            sBtn.setFocusPainted(false);
            sBtn.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));
            sBtn.setPreferredSize(new Dimension(58, 52));
            sBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            sBtn.addActionListener(e -> {
                sendFormattedContent("[[STICKER|" + st[0] + "|" + st[1] + "]]");
                pickerPopup.setVisible(false);
                txtChatInput.requestFocus();
            });
            pusheenGrid.add(sBtn);
        }
        tabs.insertTab("🐱 Pusheen", null, pusheenGrid, "Sticker hoạt hình Pusheen", 0);
        tabs.setSelectedIndex(0);

        // Tab 3: 12 Sticker cảm xúc nhanh
        String[][] stickers = {
                {"👋", "Xin chào!"}, {"😂", "Cười xỉu"}, {"❤️", "Yêu quá!"}, {"👍", "Quá chuẩn"},
                {"🔥", "Cháy quá"}, {"🚀", "Chốt đơn!"}, {"😢", "Buồn ghê"}, {"🌙", "Ngủ ngon"},
                {"🙏", "Cảm ơn!"}, {"⭐", "Tuyệt vời"}, {"👀", "Hóng biến"}, {"🎉", "Chúc mừng"}
        };
        JPanel stickerGrid = new JPanel(new GridLayout(3, 4, 6, 6));
        stickerGrid.setBackground(Color.WHITE);
        stickerGrid.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (String[] st : stickers) {
            JButton sBtn = new JButton("<html><center><span style='font-size:18px'>" + st[0] + "</span><br><span style='font-size:9px'>" + st[1] + "</span></center></html>");
            sBtn.setBackground(new Color(240, 249, 255));
            sBtn.setFocusPainted(false);
            sBtn.setBorder(BorderFactory.createLineBorder(new Color(191, 219, 254), 1));
            sBtn.setPreferredSize(new Dimension(64, 52));
            sBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            sBtn.addActionListener(e -> {
                sendFormattedContent("[[STICKER|" + st[0] + "|" + st[1] + "]]");
                pickerPopup.setVisible(false);
                txtChatInput.requestFocus();
            });
            stickerGrid.add(sBtn);
        }
        tabs.addTab("✨ Cảm xúc", stickerGrid);

        pickerPopup.add(tabs);
        pickerPopup.show(btnSend, -280, -270);
    }

    private void sendFileAttachment() {
        JFileChooser chooser = new JFileChooser();
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            String fileName = chooser.getSelectedFile().getName();
            long sizeKb = chooser.getSelectedFile().length() / 1024;
            String msg = "📎 [Đính kèm tệp]: " + fileName + " (" + sizeKb + " KB)";
            if (privateChatTarget != null) {
                socketClient.sendPrivateMessage(privateChatTarget, msg);
            } else {
                socketClient.sendGroupMessage(activeRoom, msg);
            }
        }
    }

    private void doLogout() {
        int cf = JOptionPane.showConfirmDialog(this, "Bạn có muốn đăng xuất khỏi hệ thống?", "Đăng xuất", JOptionPane.YES_NO_OPTION);
        if (cf == JOptionPane.YES_OPTION) {
            if (socketClient != null) {
                socketClient.logout();
            }
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }

    // =========================================================================
    // CALLBACKS NHẬN TÍN HIỆU TỪ SERVERLISTENER (THREAD MẠNG)
    // =========================================================================

    @Override
    public void onPublicMessageReceived(String sender, String role, String timestamp, String content) {
        if ("[JOIN_NOTIFY]".equals(content)) return;
        onGroupMessageReceived(Protocol.DEFAULT_GROUP, sender, role, timestamp, content);
    }

    @Override
    public void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content) {
        if ("[JOIN_NOTIFY]".equals(content)) return;
        if (groupName.equalsIgnoreCase(activeRoom) && privateChatTarget == null) {
            boolean isMine = sender.equalsIgnoreCase(currentUsername);
            String time = timestamp != null ? timestamp : LocalDateTime.now().format(TIME_FMT);
            addMessageBubble(sender, role, time, content, isMine, false, false);
            unreadGroupCounts.put(groupName.toLowerCase(), 0);
        } else {
            // Tin nhắn thuộc nhóm khác đang đóng -> Tăng số đếm tin chưa đọc và hiện huy hiệu ngoài danh sách
            String key = groupName.toLowerCase();
            int newCount = unreadGroupCounts.getOrDefault(key, 0) + 1;
            unreadGroupCounts.put(key, newCount);

            if ("Friends & Family".equalsIgnoreCase(groupName)) {
                if (favouritesCard != null) {
                    favouritesCard.setUnreadCount(newCount);
                }
            } else {
                for (GroupItemPanel g : groupPanels) {
                    if (g.getGroupName().equalsIgnoreCase(groupName)) {
                        g.setBadgeCount(newCount);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onPrivateMessageReceived(String sender, String target, String timestamp, String content) {
        boolean isMine = sender.equalsIgnoreCase(currentUsername);
        String time = timestamp != null ? timestamp : LocalDateTime.now().format(TIME_FMT);
        addMessageBubble(sender, "USER", time, content, isMine, true, false);
    }

    @Override
    public void onSystemMessage(String content) {
        addMessageBubble("System", "SYSTEM", "", content, false, false, true);
    }

    @Override
    public void onCreateGroupResult(boolean success, String groupName, String groupCode, String message) {
        if (success) {
            unlockedGroups.add(groupName);
            JOptionPane.showMessageDialog(this, "Tạo nhóm thành công!\nTên nhóm: " + groupName + "\nMã nhóm (PIN): " + groupCode + "\n(Hãy chia sẻ mã này cho bạn bè để cùng vào nhóm)", "Tạo Nhóm Thành Công", JOptionPane.INFORMATION_MESSAGE);
            onSelectGroup(groupName);
        } else {
            JOptionPane.showMessageDialog(this, message, "Lỗi Tạo Nhóm", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onJoinGroupResult(boolean success, String groupName, String message) {
        if (success) {
            unlockedGroups.add(groupName);
            onSelectGroup(groupName);
            JOptionPane.showMessageDialog(this, "Đã mở khóa và tham gia nhóm [" + groupName + "] thành công!", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, message, "Không Thể Vào Nhóm", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onMyGroupsReceived(List<String> joinedGroups) {
        if (joinedGroups != null) {
            unlockedGroups.addAll(joinedGroups);
        }
    }

    @Override
    public void onGroupListUpdated(List<ChatGroup> groups) {
        groupsScrollContainer.removeAll();
        groupPanels.clear();
        unlockedGroups.clear();
        unlockedGroups.add(Protocol.DEFAULT_GROUP);

        boolean stillInActive = Protocol.DEFAULT_GROUP.equalsIgnoreCase(activeRoom);
        int userGroupCount = 0;
        for (ChatGroup g : groups) {
            unlockedGroups.add(g.getGroupName());
            if (g.getGroupName().equalsIgnoreCase(activeRoom)) {
                stillInActive = true;
            }
            if ("Friends & Family".equalsIgnoreCase(g.getGroupName())) continue;
            userGroupCount++;
            String sub = g.getMemberCount() + " Thành viên • 👑 " + (g.getCreator().isEmpty() ? "admin" : g.getCreator());
            int unread = unreadGroupCounts.getOrDefault(g.getGroupName().toLowerCase(), 0);
            GroupItemPanel item = new GroupItemPanel(g.getGroupName(), sub, unread, () -> onSelectGroup(g.getGroupName()));
            item.setSelected(g.getGroupName().equalsIgnoreCase(activeRoom));
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            groupPanels.add(item);
            groupsScrollContainer.add(item);
            groupsScrollContainer.add(Box.createVerticalStrut(4));
        }

        if (favouritesCard != null) {
            int favUnread = unreadGroupCounts.getOrDefault("friends & family", 0);
            favouritesCard.setUnreadCount(favUnread);
        }

        if (userGroupCount == 0) {
            JLabel lblEmpty = new JLabel("<html><i>Chưa tham gia nhóm nào.<br>Bấm <b>+</b> hoặc <b>Tham Gia Nhóm</b>!</i></html>");
            lblEmpty.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblEmpty.setForeground(UIUtils.TEXT_MUTED);
            lblEmpty.setBorder(new EmptyBorder(6, 6, 6, 6));
            lblEmpty.setAlignmentX(Component.LEFT_ALIGNMENT);
            groupsScrollContainer.add(lblEmpty);
        }

        groupsScrollContainer.revalidate();
        groupsScrollContainer.repaint();

        if (!stillInActive) {
            onSelectGroup(Protocol.DEFAULT_GROUP);
        }
    }

    @Override
    public void onGroupMembersUpdated(String groupName, String creator, List<String[]> members) {
        if (groupName.equalsIgnoreCase(activeRoom)) {
            this.activeGroupCreator = creator != null ? creator : "";
            this.groupMembers = new ArrayList<>(members);
            refreshMembersUI();
        }
    }

    @Override
    public void onGroupKicked(String groupName, String reason) {
        unlockedGroups.removeIf(g -> g.equalsIgnoreCase(groupName));
        if (groupName.equalsIgnoreCase(activeRoom)) {
            onSelectGroup(Protocol.DEFAULT_GROUP);
        }
    }

    @Override
    public void onUserListUpdated(List<User> onlineUsers) {
        this.onlineUsers = new ArrayList<>(onlineUsers);
        refreshMembersUI();
        if (socketClient != null && activeRoom != null) {
            socketClient.requestGroupMembers(activeRoom);
        }

        for (User u : onlineUsers) {
            if (u.getUsername().equalsIgnoreCase(currentUsername)) {
                this.isMuted = u.isMuted();
                break;
            }
        }
    }

    @Override
    public void onHistoryReceived(String groupName, List<MessagePacket> history) {
        if (groupName.equalsIgnoreCase(activeRoom)) {
            messagesContainer.removeAll();
            for (MessagePacket msg : history) {
                boolean isMine = msg.getSender().equalsIgnoreCase(currentUsername);
                boolean isPriv = MessagePacket.TYPE_PRIVATE.equalsIgnoreCase(msg.getType());
                addMessageBubble(msg.getSender(), msg.getSenderRole(), msg.getTimestamp(), msg.getContent(), isMine, isPriv, false);
            }
            messagesContainer.revalidate();
            messagesContainer.repaint();
        }
    }

    @Override
    public void onKicked(String reason) {
        JOptionPane.showMessageDialog(this, "Bạn đã bị đuổi khỏi phòng!\nLý do: " + reason, "Bị trục xuất", JOptionPane.WARNING_MESSAGE);
        dispose();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    @Override
    public void onMuted(int minutes, String reason) {
        this.isMuted = true;
        JOptionPane.showMessageDialog(this, "Bạn đã bị cấm chat " + (minutes > 0 ? minutes + " phút" : "vô thời hạn") + ".\nLý do: " + reason, "Cấm Chat", JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void onUnmuted() {
        this.isMuted = false;
        JOptionPane.showMessageDialog(this, "Bạn đã được quản trị viên mở lại quyền chat!", "Mở Cấm Chat", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void onChangePasswordResult(boolean success, String message) {
        JOptionPane.showMessageDialog(this, message, success ? "Thành công" : "Lỗi", success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void onConnectionLost(String reason) {
        JOptionPane.showMessageDialog(this, reason, "Mất kết nối", JOptionPane.ERROR_MESSAGE);
        dispose();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    @Override public void onLoginResult(boolean success, String role, String username, String message) {}
    @Override public void onRegisterResult(boolean success, String message) {}
}
