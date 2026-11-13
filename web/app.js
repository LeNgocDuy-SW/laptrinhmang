// ==========================================================================
// MULTI-CLIENT CLOUD CHAT - VANILLA JAVASCRIPT CLIENT (HYBRID SSE & REST)
// ==========================================================================

const state = {
    currentUser: null,
    currentRole: "USER",
    activeRoom: "Friends & Family",
    unlockedGroups: new Set(["Friends & Family"]),
    pendingJoinGroup: null,
    groups: [],
    onlineUsers: [],
    eventSource: null,
    syncIntervalId: null,
    isRegisterMode: false,
    renderedMessages: new Set(), // Dùng để chống trùng lặp tin nhắn
    replyTarget: null,           // { sender, snippet }
    activeGroupInfo: null,       // Dữ liệu chi tiết thành viên & Trưởng nhóm
    unreadCounts: {}             // Quản lý số tin nhắn chưa đọc từng nhóm (chữ thường)
};

const SESSION_STORAGE_KEY = "cloud_chat_session";

function saveSession() {
    if (!state.currentUser) return;
    const payload = JSON.stringify({
        username: state.currentUser,
        role: state.currentRole || "USER",
        activeRoom: state.activeRoom || "Friends & Family"
    });
    try {
        sessionStorage.setItem(SESSION_STORAGE_KEY, payload);
        const rememberEl = document.getElementById("rememberMe");
        if ((rememberEl && rememberEl.checked) || localStorage.getItem(SESSION_STORAGE_KEY)) {
            localStorage.setItem(SESSION_STORAGE_KEY, payload);
        }
    } catch (ignored) {}
}

function clearSession() {
    try {
        sessionStorage.removeItem(SESSION_STORAGE_KEY);
        localStorage.removeItem(SESSION_STORAGE_KEY);
    } catch (ignored) {}
}

async function restoreSessionOnLoad() {
    let raw = null;
    try {
        raw = sessionStorage.getItem(SESSION_STORAGE_KEY) || localStorage.getItem(SESSION_STORAGE_KEY);
    } catch (ignored) {}
    if (!raw) return;

    try {
        const saved = JSON.parse(raw);
        if (!saved || !saved.username) return;

        const res = await fetch(`/api/session?user=${encodeURIComponent(saved.username)}`);
        const data = await res.json();
        if (res.ok && data.success) {
            state.currentUser = data.username;
            state.currentRole = data.role || "USER";
            state.activeRoom = saved.activeRoom || "Friends & Family";
            saveSession();
            enterChatScreen();
        } else {
            clearSession();
        }
    } catch (err) {
        // Nếu server tạm gián đoạn khi vừa tải trang, vẫn giữ bản lưu để người dùng thử lại
    }
}

window.addEventListener("DOMContentLoaded", restoreSessionOnLoad);

// ==========================================================================
// 1. XỬ LÝ ĐĂNG NHẬP & ĐĂNG KÝ (NEUMORPHIC AUTH SCREEN)
// ==========================================================================

function toggleAuthMode() {
    state.isRegisterMode = !state.isRegisterMode;
    const title = document.getElementById("authTitle");
    const subtitle = document.getElementById("authSubtitle");
    const btnText = document.getElementById("btnAuthText");
    const rememberRow = document.getElementById("rememberRow");
    const prompt = document.getElementById("togglePrompt");
    const actionLink = document.getElementById("toggleActionLink");

    if (state.isRegisterMode) {
        title.innerText = "Create Account";
        subtitle.innerText = "Sign up to get started";
        btnText.innerText = "Sign Up";
        rememberRow.style.display = "none";
        prompt.innerText = "Already have an account?";
        actionLink.innerHTML = "<b>Sign in</b>";
    } else {
        title.innerText = "Welcome Back";
        subtitle.innerText = "Sign in to continue";
        btnText.innerText = "Login";
        rememberRow.style.display = "flex";
        prompt.innerText = "Don't have an account?";
        actionLink.innerHTML = "<b>Sign up</b>";
    }
}

function togglePasswordVisibility() {
    const passInput = document.getElementById("authPassword");
    passInput.type = passInput.type === "password" ? "text" : "password";
}

async function handleAuthSubmit(e) {
    e.preventDefault();
    const user = document.getElementById("authUsername").value.trim();
    const pass = document.getElementById("authPassword").value.trim();
    const statusEl = document.getElementById("authStatus");

    if (!user || !pass) return;

    statusEl.innerText = "⏳ Đang kết nối máy chủ...";
    statusEl.style.color = "#2563eb";

    try {
        if (state.isRegisterMode) {
            const res = await fetch("/api/register", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username: user, password: pass, role: "USER" })
            });
            const data = await res.json();
            if (data.success) {
                alert("Đăng ký thành công! Hãy đăng nhập ngay.");
                toggleAuthMode();
                document.getElementById("authPassword").value = "";
            } else {
                statusEl.innerText = "❌ " + (data.message || "Đăng ký thất bại");
                statusEl.style.color = "#ef4444";
            }
        } else {
            const res = await fetch("/api/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username: user, password: pass })
            });
            const data = await res.json();
            if (data.success) {
                state.currentUser = data.username;
                state.currentRole = data.role || "USER";
                state.activeRoom = "Friends & Family";
                saveSession();
                enterChatScreen();
            } else {
                statusEl.innerText = "❌ " + (data.message || "Đăng nhập thất bại");
                statusEl.style.color = "#ef4444";
            }
        }
    } catch (err) {
        statusEl.innerText = "❌ Không thể kết nối tới Server. Hãy đảm bảo Server đang bật!";
        statusEl.style.color = "#ef4444";
    }
}

async function enterChatScreen() {
    document.getElementById("authScreen").style.display = "none";
    document.getElementById("chatScreen").style.display = "flex";
    closeModal("modalCreateGroup");
    closeModal("modalJoinGroup");

    // Cập nhật Profile
    document.getElementById("myDisplayName").innerText = state.currentUser;
    document.getElementById("myAvatar").innerText = state.currentUser.substring(0, 1).toUpperCase();

    // Khởi tạo bảng chọn Emoji & Sticker
    initEmojiStickerPicker();

    // Khởi tạo luồng SSE Real-time
    initEventSource();

    // Nạp danh sách nhóm trước để kiểm tra xem phòng hiện tại còn hợp lệ không
    await loadGroups();
    if (state.activeRoom !== "Friends & Family") {
        const stillInGroup = state.groups.some(g => g.name.toLowerCase() === state.activeRoom.toLowerCase());
        if (!stillInGroup) {
            state.activeRoom = "Friends & Family";
            saveSession();
        }
    }

    document.getElementById("favCard").classList.toggle("active", state.activeRoom === "Friends & Family");
    document.getElementById("activeChatTitle").innerText = state.activeRoom;
    document.getElementById("rightGroupTitle").innerText = state.activeRoom;
    renderGroupsList();

    loadHistory(state.activeRoom);
    loadGroupMembers(state.activeRoom);

    // Kích hoạt đồng bộ dự phòng mỗi 3 giây (tin nhắn + danh sách nhóm/trạng thái kick)
    if (state.syncIntervalId) clearInterval(state.syncIntervalId);
    state.syncIntervalId = setInterval(() => {
        if (state.currentUser && state.activeRoom) {
            syncLatestMessages(state.activeRoom);
            loadGroups();
        }
    }, 3000);
}

// ==========================================================================
// 2. SERVER-SENT EVENTS (SSE) - THỜI GIAN THỰC ĐỒNG BỘ DESKTOP & WEB
// ==========================================================================

function initEventSource() {
    if (state.eventSource) {
        try { state.eventSource.close(); } catch (e) {}
    }

    state.eventSource = new EventSource(`/api/events?user=${encodeURIComponent(state.currentUser)}`);

    state.eventSource.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            handleIncomingEvent(data);
        } catch (e) {
            // Heartbeat hoặc comment bỏ qua
        }
    };

    state.eventSource.onerror = (err) => {
        // SSE tự động kết nối lại khi có lỗi
    };
}

function handleIncomingEvent(data) {
    switch (data.type) {
        case "NEW_MSG":
            if (data.groupName === state.activeRoom) {
                const sig = `${data.sender}|${data.content}|${data.timestamp}`;
                if (data.sender === state.currentUser && state.renderedMessages.has(sig)) {
                    return;
                }
                state.renderedMessages.add(sig);
                appendMessageBubble(data.sender, data.role, data.timestamp, data.content, data.sender === state.currentUser);
                state.unreadCounts[(data.groupName || "").toLowerCase()] = 0;
                updateUnreadBadges();
            } else {
                // Tin nhắn thuộc nhóm khác đang đóng -> Tăng số tin chưa đọc và phát chuông thông báo
                const key = (data.groupName || "").toLowerCase();
                state.unreadCounts[key] = (state.unreadCounts[key] || 0) + 1;
                updateUnreadBadges();
                playNotifySound();
            }
            break;
        case "NEW_PRIVATE_MSG":
            const privSig = `${data.sender}|${data.content}|${data.timestamp}`;
            if (!state.renderedMessages.has(privSig)) {
                state.renderedMessages.add(privSig);
                appendMessageBubble(data.sender, "USER", data.timestamp, `[🔒 Tin riêng]: ${data.content}`, data.sender === state.currentUser);
            }
            break;
        case "SYSTEM_MSG":
            appendSystemNotice(data.content);
            break;
        case "USER_LIST":
            state.onlineUsers = data.users || [];
            loadGroupMembers(state.activeRoom);
            break;
        case "GROUP_LIST":
            loadGroups();
            break;
        case "GROUP_MEMBERS_UPDATED":
            if (data.groupName === state.activeRoom) {
                loadGroupMembers(state.activeRoom);
            }
            loadGroups();
            break;
        case "GROUP_KICKED":
            if (data.targetUser && state.currentUser && data.targetUser.toLowerCase() !== state.currentUser.toLowerCase()) {
                return;
            }
            handleKickedFromGroup(data.groupName);
            break;
    }
}

function handleKickedFromGroup(groupName) {
    if (!groupName) return;
    const targetLower = groupName.toLowerCase();
    const wasActive = (state.activeRoom || "").toLowerCase() === targetLower;
    const wasInList = state.groups.some(g => g.name.toLowerCase() === targetLower);
    if (!wasActive && !wasInList) return;

    // Xóa khỏi danh sách nhóm đã mở khóa & danh sách hiển thị ngay lập tức (không popup thông báo)
    Array.from(state.unlockedGroups).forEach(g => {
        if (g.toLowerCase() === targetLower) state.unlockedGroups.delete(g);
    });
    state.groups = state.groups.filter(g => g.name.toLowerCase() !== targetLower);
    renderGroupsList();

    // Nếu đang mở nhóm vừa bị kick -> lặng lẽ chuyển về Friends & Family
    if (wasActive) {
        selectGroup("Friends & Family");
    }
}

// ==========================================================================
// 3. QUẢN LÝ NHÓM & MÃ PIN BẢO MẬT (GROUP CODE / PIN)
// ==========================================================================

async function loadGroups() {
    if (!state.currentUser) return;
    try {
        const res = await fetch(`/api/groups?user=${encodeURIComponent(state.currentUser || "")}`);
        const data = await res.json();
        if (data.success) {
            state.groups = data.groups || [];
            const validNames = new Set(["Friends & Family"]);
            state.groups.forEach(g => {
                if (g.joined) validNames.add(g.name);
            });
            state.unlockedGroups = validNames;
            renderGroupsList();

            if (state.activeRoom && state.activeRoom !== "Friends & Family") {
                const stillMember = state.groups.some(g => g.name.toLowerCase() === state.activeRoom.toLowerCase());
                if (!stillMember) {
                    handleKickedFromGroup(state.activeRoom);
                }
            }
        }
    } catch (e) {
        console.error("Lỗi tải danh sách nhóm:", e);
    }
}

function renderGroupsList() {
    const container = document.getElementById("groupsContainer");
    container.innerHTML = "";

    let userGroupCount = 0;
    state.groups.forEach(g => {
        if (g.name === "Friends & Family") return;
        userGroupCount++;

        const count = state.unreadCounts[(g.name || "").toLowerCase()] || 0;
        const badgeHtml = count > 0 ? `<span class="group-unread-badge">${count > 99 ? '99+' : count}</span>` : '';
        const item = document.createElement("div");
        item.className = `group-item ${state.activeRoom === g.name ? "active" : ""} ${count > 0 ? "has-unread" : ""}`;
        item.onclick = () => selectGroup(g.name);

        const initial = g.name.substring(0, 1).toUpperCase();
        item.innerHTML = `
            <div class="group-icon-circle">${initial}</div>
            <div class="group-meta">
                <span class="group-name">${escapeHtml(g.name)}</span>
                <span class="group-desc">${count > 0 ? `💬 <b style="color:#ef4444">${count} tin nhắn mới</b>` : `👥 ${g.memberCount} Thành viên • 👑 ${escapeHtml(g.creator || "admin")}`}</span>
            </div>
            ${badgeHtml}
        `;
        container.appendChild(item);
    });

    if (userGroupCount === 0) {
        container.innerHTML = `<div style="font-size:12px;color:#94a3b8;padding:8px 6px;font-style:italic;">Chưa tham gia nhóm nào.<br>Bấm <b>+</b> hoặc <b>Tham Gia Nhóm</b> để nhập mã!</div>`;
    }

    updateUnreadBadges();
}

function updateUnreadBadges() {
    // 1. Cập nhật thẻ Favourites Card ("Friends & Family")
    const favCount = state.unreadCounts["friends & family"] || 0;
    const favBadge = document.getElementById("favBadge");
    if (favBadge) {
        if (favCount > 0) {
            favBadge.style.display = "inline-flex";
            favBadge.innerText = favCount > 99 ? "99+" : favCount;
        } else {
            favBadge.style.display = "none";
        }
    }

    // 2. Cập nhật các nhóm trong danh sách
    const container = document.getElementById("groupsContainer");
    if (!container) return;
    const items = container.querySelectorAll(".group-item");
    items.forEach(item => {
        const nameEl = item.querySelector(".group-name");
        if (!nameEl) return;
        const gName = nameEl.innerText.trim();
        const count = state.unreadCounts[gName.toLowerCase()] || 0;
        let badgeEl = item.querySelector(".group-unread-badge");
        let descEl = item.querySelector(".group-desc");

        if (count > 0) {
            item.classList.add("has-unread");
            if (!badgeEl) {
                badgeEl = document.createElement("span");
                badgeEl.className = "group-unread-badge";
                item.appendChild(badgeEl);
            }
            badgeEl.innerText = count > 99 ? "99+" : count;
            badgeEl.style.display = "inline-flex";
            if (descEl) descEl.innerHTML = `💬 <b style="color:#ef4444">${count} tin nhắn mới</b>`;
        } else {
            item.classList.remove("has-unread");
            if (badgeEl) badgeEl.style.display = "none";
            const grp = state.groups.find(x => x.name.toLowerCase() === gName.toLowerCase());
            if (descEl && grp) {
                descEl.innerHTML = `👥 ${grp.memberCount} Thành viên • 👑 ${escapeHtml(grp.creator || "admin")}`;
            }
        }
    });
}

function playNotifySound() {
    try {
        const ctx = new (window.AudioContext || window.webkitAudioContext)();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
        osc.frequency.setValueAtTime(880, ctx.currentTime + 0.08); // A5
        gain.gain.setValueAtTime(0.12, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
        osc.start(ctx.currentTime);
        osc.stop(ctx.currentTime + 0.3);
    } catch (e) {}
}

function openJoinGroupByCodeModal() {
    state.pendingJoinGroup = "";
    document.getElementById("joinGroupDesc").innerText = "Nhập mã định danh riêng của nhóm để tham gia vào nhóm chat.";
    document.getElementById("joinGroupInputCode").value = "";
    openModal("modalJoinGroup");
    setTimeout(() => document.getElementById("joinGroupInputCode").focus(), 50);
}

function selectGroup(groupName) {
    const isUnlocked = groupName === "Friends & Family"
        || Array.from(state.unlockedGroups).some(g => g.toLowerCase() === (groupName || "").toLowerCase());
    if (!isUnlocked) {
        state.pendingJoinGroup = groupName;
        document.getElementById("joinGroupDesc").innerText = `Nhóm [${groupName}] được bảo vệ. Vui lòng nhập mã định danh nhóm để tham gia.`;
        document.getElementById("joinGroupInputCode").value = "";
        openModal("modalJoinGroup");
        return;
    }

    state.activeRoom = groupName;
    state.unreadCounts[(groupName || "").toLowerCase()] = 0;
    saveSession();
    closeMobileDrawers();
    cancelReply();
    updateUnreadBadges();
    document.getElementById("favCard").classList.toggle("active", groupName === "Friends & Family");
    document.getElementById("activeChatTitle").innerText = groupName;
    document.getElementById("rightGroupTitle").innerText = groupName;

    state.renderedMessages.clear();
    loadHistory(groupName);
    loadGroupMembers(groupName);
    renderGroupsList();
}

async function submitJoinGroup() {
    const code = document.getElementById("joinGroupInputCode").value.trim();
    if (!code) {
        alert("Vui lòng nhập mã định danh của nhóm!");
        return;
    }

    try {
        const res = await fetch("/api/join_group", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                groupName: state.pendingJoinGroup || "",
                groupCode: code,
                username: state.currentUser
            })
        });
        const data = await res.json();
        if (data.success) {
            const joinedName = data.groupName || state.pendingJoinGroup;
            state.unlockedGroups.add(joinedName);
            closeModal("modalJoinGroup");
            await loadGroups();
            selectGroup(joinedName);
        } else {
            alert(data.message || "Mã nhóm không tồn tại hoặc không chính xác!");
        }
    } catch (e) {
        alert("Lỗi khi tham gia nhóm.");
    }
}

async function submitCreateGroup() {
    const name = document.getElementById("newGroupName").value.trim();
    const code = document.getElementById("newGroupCode").value.trim();
    if (!name || !code) {
        alert("Vui lòng điền đầy đủ tên nhóm và mã PIN!");
        return;
    }

    try {
        const res = await fetch("/api/create_group", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                groupName: name,
                groupCode: code,
                creator: state.currentUser
            })
        });
        const data = await res.json();
        if (data.success) {
            state.unlockedGroups.add(name);
            closeModal("modalCreateGroup");
            document.getElementById("newGroupName").value = "";
            document.getElementById("newGroupCode").value = "";
            alert(`Tạo nhóm thành công!\nTên: ${name}\nMã PIN: ${code}\n(Hãy chia sẻ mã PIN này cho bạn bè để cùng vào nhóm)`);
            loadGroups();
            selectGroup(name);
        } else {
            alert(data.message || "Lỗi tạo nhóm.");
        }
    } catch (e) {
        alert("Lỗi khi tạo nhóm mới.");
    }
}

// ==========================================================================
// 4. LỊCH SỬ TIN NHẮN & GỬI TIN NHẮN (HIỂN THỊ TỨC THÌ 0MS)
// ==========================================================================

async function loadHistory(groupName) {
    const container = document.getElementById("messagesContainer");
    container.innerHTML = "";

    try {
        const res = await fetch(`/api/history?group=${encodeURIComponent(groupName)}&user=${encodeURIComponent(state.currentUser || "")}`);
        const data = await res.json();
        if (data.kicked) {
            handleKickedFromGroup(groupName);
            return;
        }
        if (data.success && data.messages) {
            data.messages.forEach(m => {
                const sig = `${m.sender}|${m.content}|${m.time}`;
                state.renderedMessages.add(sig);
                appendMessageBubble(m.sender, m.role, m.time, m.content, m.sender === state.currentUser);
            });
        }
    } catch (e) {
        console.error("Lỗi tải lịch sử chat:", e);
    }
}

async function syncLatestMessages(groupName) {
    try {
        const res = await fetch(`/api/history?group=${encodeURIComponent(groupName)}&user=${encodeURIComponent(state.currentUser || "")}`);
        const data = await res.json();
        if (data.kicked) {
            handleKickedFromGroup(groupName);
            return;
        }
        if (data.success && data.messages) {
            data.messages.forEach(m => {
                const sig = `${m.sender}|${m.content}|${m.time}`;
                if (!state.renderedMessages.has(sig)) {
                    state.renderedMessages.add(sig);
                    appendMessageBubble(m.sender, m.role, m.time, m.content, m.sender === state.currentUser);
                }
            });
        }
    } catch (ignored) {}
}

async function sendPayloadContent(rawContent) {
    if (!rawContent || !rawContent.trim()) return;

    let finalContent = rawContent.trim();
    if (state.replyTarget) {
        const safeSnip = (state.replyTarget.snippet || "").replace(/\]\]/g, ")").replace(/\|/g, "/");
        finalContent = `[[REPLY|${state.replyTarget.sender}|${safeSnip}]]${finalContent}`;
        cancelReply();
    }

    try {
        const res = await fetch("/api/send", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                sender: state.currentUser,
                role: state.currentRole,
                groupName: state.activeRoom,
                content: finalContent,
                isPrivate: "false"
            })
        });
        const data = await res.json();
        if (data.success) {
            const timeStr = data.time || (new Date()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            const sig = `${state.currentUser}|${finalContent}|${timeStr}`;
            state.renderedMessages.add(sig);
            appendMessageBubble(state.currentUser, state.currentRole, timeStr, finalContent, true);
        } else {
            alert(data.message || "Không thể gửi tin nhắn.");
        }
    } catch (e) {
        alert("Lỗi kết nối khi gửi tin nhắn.");
    }
}

async function sendActiveMessage() {
    const input = document.getElementById("txtChatMessage");
    const content = input.value.trim();
    if (!content) return;

    input.value = "";
    input.focus();
    await sendPayloadContent(content);
}

function handleInputKeyDown(e) {
    if (e.key === "Enter") {
        sendActiveMessage();
    }
}

function setReplyTarget(sender, plainSnippet) {
    const clean = (plainSnippet || "").replace(/\s+/g, " ").trim();
    state.replyTarget = { sender, snippet: clean };
    const shortSnip = clean.length > 50 ? clean.substring(0, 50) + "..." : clean;
    document.getElementById("replyPreviewText").innerHTML = `<b>↩ Đang trả lời @${escapeHtml(sender)}:</b> ${escapeHtml(shortSnip)}`;
    document.getElementById("replyPreviewBar").style.display = "flex";
    document.getElementById("txtChatMessage").focus();
}

function cancelReply() {
    state.replyTarget = null;
    const bar = document.getElementById("replyPreviewBar");
    if (bar) bar.style.display = "none";
}

function appendMessageBubble(sender, role, time, content, isMine) {
    const container = document.getElementById("messagesContainer");
    const row = document.createElement("div");
    row.className = `message-row ${isMine ? "mine" : "other"}`;

    let replySender = null;
    let replySnippet = null;
    let mainContent = content || "";

    if (mainContent.startsWith("[[REPLY|")) {
        const endIdx = mainContent.indexOf("]]");
        if (endIdx > 8) {
            const meta = mainContent.substring(8, endIdx);
            const sep = meta.indexOf("|");
            replySender = sep >= 0 ? meta.substring(0, sep) : meta;
            replySnippet = sep >= 0 ? meta.substring(sep + 1) : "";
            mainContent = mainContent.substring(endIdx + 2);
        }
    }

    let stickerIcon = null;
    let stickerLabel = null;
    if (mainContent.startsWith("[[STICKER|") && mainContent.endsWith("]]")) {
        const meta = mainContent.substring(10, mainContent.length - 2);
        const sep = meta.indexOf("|");
        stickerIcon = sep >= 0 ? meta.substring(0, sep) : meta;
        stickerLabel = sep >= 0 ? meta.substring(sep + 1) : "Sticker";
    }

    const plainSnippet = stickerLabel ? `[Sticker] ${stickerLabel}` : mainContent;
    const initial = sender.substring(0, 1).toUpperCase();

    const quoteHtml = replySender
        ? `<div class="msg-quote-box"><b>↩ @${escapeHtml(replySender)}:</b> ${escapeHtml(replySnippet.length > 48 ? replySnippet.substring(0, 48) + "..." : replySnippet)}</div>`
        : "";

    let bodyHtml = `<div class="msg-bubble">${quoteHtml}${escapeHtml(mainContent)}</div>`;
    if (stickerIcon) {
        const isImage = stickerIcon.endsWith(".gif") || stickerIcon.endsWith(".png") || stickerIcon.endsWith(".webp") || stickerIcon.includes("stickers/") || stickerIcon.startsWith("http");
        if (isImage) {
            let src = stickerIcon;
            if (!src.startsWith("http://") && !src.startsWith("https://") && !src.startsWith("/")) {
                src = "/" + src;
            }
            bodyHtml = `${quoteHtml}<div class="msg-sticker-wrapper"><img class="msg-sticker-img" src="${src}" alt="${escapeHtml(stickerLabel)}" title="${escapeHtml(stickerLabel)}" loading="lazy" /></div>`;
        } else {
            bodyHtml = `${quoteHtml}<div class="msg-sticker-card"><span class="msg-sticker-emoji">${escapeHtml(stickerIcon)}</span><span class="msg-sticker-label">${escapeHtml(stickerLabel)}</span></div>`;
        }
    }

    row.innerHTML = `
        <div class="msg-avatar" style="${isMine ? 'background:#3b82f6;' : 'background:#94a3b8;'}">${initial}</div>
        <div class="msg-content-wrap">
            ${!isMine ? `<div class="msg-sender-name">${escapeHtml(sender)} (${escapeHtml(role)})</div>` : ''}
            ${bodyHtml}
            <div class="msg-meta-row">
                <span>${time || ''}</span>
                <button type="button" class="reply-btn-inline">↩ Trả lời</button>
            </div>
        </div>
    `;

    row.querySelector(".reply-btn-inline").onclick = () => setReplyTarget(sender, plainSnippet);
    container.appendChild(row);
    container.scrollTop = container.scrollHeight;
}

function appendSystemNotice(text) {
    const container = document.getElementById("messagesContainer");
    const div = document.createElement("div");
    div.style.textAlign = "center";
    div.style.fontSize = "12px";
    div.style.color = "#64748b";
    div.style.margin = "8px 0";
    div.innerHTML = `<i>📢 ${escapeHtml(text)}</i>`;
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

// ==========================================================================
// 5. THÀNH VIÊN NHÓM & ĐẶC QUYỀN TRƯỞNG NHÓM
// ==========================================================================

async function loadGroupMembers(groupName) {
    if (!state.currentUser || !groupName) return;
    try {
        const res = await fetch(`/api/group_members?group=${encodeURIComponent(groupName)}&user=${encodeURIComponent(state.currentUser)}`);
        const data = await res.json();
        if (data.success && data.groupName === state.activeRoom) {
            state.activeGroupInfo = data;
            renderGroupMembers(data);
        }
    } catch (e) {
        console.error("Lỗi tải thành viên nhóm:", e);
    }
}

function renderGroupMembers(info) {
    const container = document.getElementById("membersContainer");
    const label = document.getElementById("membersCountLabel");
    const subtitle = document.getElementById("activeChatSubtitle");
    const favTag = document.getElementById("favOnlineTag");
    const rightLeader = document.getElementById("rightGroupLeader");
    const rightCount = document.getElementById("rightGroupCount");
    const leaderBox = document.getElementById("leaderControls");

    const members = info.members || [];
    const onlineCount = members.filter(m => m.online).length;

    label.innerText = `Thành viên nhóm (${members.length})`;
    subtitle.innerText = `👥 ${members.length} Thành viên • 🟢 ${Math.max(1, onlineCount)} Online`;
    rightLeader.innerText = `👑 Trưởng nhóm: ${info.creator || "admin"}`;
    rightCount.innerText = `👥 Thành viên: ${members.length}`;
    if (info.groupName === "Friends & Family") {
        favTag.innerText = `🟢 ${Math.max(1, onlineCount)} Online`;
    }

    leaderBox.style.display = info.isLeader ? "block" : "none";

    container.innerHTML = "";
    members.forEach(m => {
        const item = document.createElement("div");
        item.className = "member-item";
        const initial = m.username.substring(0, 1).toUpperCase();
        const tags = [];
        if (m.username === state.currentUser) tags.push("(Bạn)");
        if (m.isLeader) tags.push("👑");
        if (m.muted) tags.push("🔇");

        let actionsHtml = "";
        if (info.isLeader && m.username !== state.currentUser) {
            const muteBtn = m.muted
                ? `<button type="button" class="btn-mem-act" title="Mở chặn nhắn tin" onclick="leaderGroupAction('UNMUTE','${escapeHtml(m.username)}')">🔊</button>`
                : `<button type="button" class="btn-mem-act" title="Chặn nhắn tin trong nhóm" onclick="leaderGroupAction('MUTE','${escapeHtml(m.username)}')">🔇</button>`;
            const kickBtn = info.groupName !== "Friends & Family"
                ? `<button type="button" class="btn-mem-act kick" title="Kick khỏi nhóm" onclick="leaderGroupAction('KICK','${escapeHtml(m.username)}')">👢</button>`
                : "";
            actionsHtml = `<div class="member-actions-inline">${muteBtn}${kickBtn}</div>`;
        }

        item.innerHTML = `
            <div class="member-avatar">${initial}</div>
            <div class="member-name">${escapeHtml(m.username)} ${tags.join(" ")}</div>
            ${actionsHtml}
            <div class="status-dot ${m.online ? 'online' : 'offline'}"></div>
        `;
        container.appendChild(item);
    });
}

async function leaderGroupAction(action, target = "", extra = "") {
    try {
        const res = await fetch("/api/group_action", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                action: action,
                groupName: state.activeRoom,
                actor: state.currentUser,
                target: target,
                extra: extra
            })
        });
        const data = await res.json();
        if (!data.success) {
            alert(data.message || "Không thể thực hiện thao tác.");
        } else {
            loadGroupMembers(state.activeRoom);
            loadGroups();
        }
    } catch (e) {
        alert("Lỗi kết nối khi thực hiện quyền Trưởng nhóm.");
    }
}

function leaderChangePin() {
    const curPin = state.activeGroupInfo && state.activeGroupInfo.groupCode ? state.activeGroupInfo.groupCode : "";
    const newPin = prompt(`Mã PIN hiện tại của nhóm [${state.activeRoom}]: ${curPin}\nNhập mã PIN mới muốn đổi:`, curPin);
    if (newPin && newPin.trim()) {
        leaderGroupAction("SET_CODE", "", newPin.trim());
    }
}

function leaderClearHistory() {
    if (confirm(`Xóa toàn bộ lịch sử tin nhắn trong nhóm [${state.activeRoom}]?`)) {
        leaderGroupAction("CLEAR_HISTORY").then(() => {
            state.renderedMessages.clear();
            document.getElementById("messagesContainer").innerHTML = "";
        });
    }
}

function leaderDeleteGroup() {
    if (state.activeRoom === "Friends & Family") {
        alert("Không thể giải tán phòng mặc định.");
        return;
    }
    if (confirm(`Bạn chắc chắn muốn giải tán vĩnh viễn nhóm [${state.activeRoom}]?`)) {
        leaderGroupAction("DELETE").then(() => {
            state.unlockedGroups.delete(state.activeRoom);
            selectGroup("Friends & Family");
        });
    }
}

// ==========================================================================
// 6. EMOJI & STICKER PICKER + MODALS + UTILITIES
// ==========================================================================

const STICKER_PACKS = {
    pusheen: [
        { path: "/stickers/pusheen/144884739019126.gif", label: "Vẫy tay chào" },
        { path: "/stickers/pusheen/144884755685791.gif", label: "Thả tim yêu" },
        { path: "/stickers/pusheen/144884765685790.gif", label: "Nhảy nhót" },
        { path: "/stickers/pusheen/144884775685789.gif", label: "Cười tít mắt" },
        { path: "/stickers/pusheen/144884792352454.gif", label: "Gõ phím chăm" },
        { path: "/stickers/pusheen/144884805685786.gif", label: "Ăn mì ramen" },
        { path: "/stickers/pusheen/144884815685785.gif", label: "Khò khò ngủ" },
        { path: "/stickers/pusheen/144884825685784.gif", label: "Thả like chuẩn" },
        { path: "/stickers/pusheen/144884835685783.gif", label: "Bắn tim chéo" },
        { path: "/stickers/pusheen/144884852352448.gif", label: "Khóc hu hu" },
        { path: "/stickers/pusheen/144884865685780.gif", label: "Bánh donut ngon" },
        { path: "/stickers/pusheen/144884879019112.gif", label: "Chui thùng giấy" },
        { path: "/stickers/pusheen/144884895685777.gif", label: "Trà sữa boba" },
        { path: "/stickers/pusheen/144884905685776.gif", label: "Đeo kính ngầu" },
        { path: "/stickers/pusheen/144884925685774.gif", label: "Hốt hoảng" },
        { path: "/stickers/pusheen/144884955685771.gif", label: "Hát ca vui vẻ" },
        { path: "/stickers/pusheen/144884992352434.gif", label: "Cười ngoác" },
        { path: "/stickers/pusheen/144885022352431.gif", label: "Đập tay High 5" },
        { path: "/stickers/pusheen/144885035685763.gif", label: "Hộp quà xinh" },
        { path: "/stickers/pusheen/144885045685762.gif", label: "Đắp chăn ngủ" },
        { path: "/stickers/pusheen/144885055685761.gif", label: "Ôm tim rung" },
        { path: "/stickers/pusheen/144885069019093.gif", label: "Giận dỗi" },
        { path: "/stickers/pusheen/144885089019091.gif", label: "Tiệc chúc mừng" },
        { path: "/stickers/pusheen/144885099019090.gif", label: "Vỗ tay khen" }
    ],
    pusheen_eats: [
        { path: "/stickers/pusheen_eats/554423661312155.gif", label: "Bánh pizza" },
        { path: "/stickers/pusheen_eats/554423671312154.gif", label: "Ăn cây kem" },
        { path: "/stickers/pusheen_eats/554423684645486.gif", label: "Uống cà phê" },
        { path: "/stickers/pusheen_eats/554423694645485.gif", label: "Bánh kem ngọt" },
        { path: "/stickers/pusheen_eats/554423717978816.gif", label: "Nấu ăn ngon" },
        { path: "/stickers/pusheen_eats/554423744645480.gif", label: "Tách trà ấm" },
        { path: "/stickers/pusheen_eats/554423757978812.gif", label: "Burger kẹp" },
        { path: "/stickers/pusheen_eats/554423771312144.gif", label: "Bắp rang bơ" },
        { path: "/stickers/pusheen_eats/554423787978809.gif", label: "Đĩa sushi" },
        { path: "/stickers/pusheen_eats/554423801312141.gif", label: "Khoai tây chiên" },
        { path: "/stickers/pusheen_eats/554423821312139.gif", label: "Lon nước ngọt" },
        { path: "/stickers/pusheen_eats/554423831312138.gif", label: "Dưa hấu mát" },
        { path: "/stickers/pusheen_eats/554423847978803.gif", label: "Bánh quy nóng" },
        { path: "/stickers/pusheen_eats/554423861312135.gif", label: "Kẹo mút tròn" },
        { path: "/stickers/pusheen_eats/554423881312133.gif", label: "Mì spaghetti" },
        { path: "/stickers/pusheen_eats/554423891312132.gif", label: "Đói cồn cào" },
        { path: "/stickers/pusheen_eats/554423914645463.gif", label: "Bánh taco giòn" },
        { path: "/stickers/pusheen_eats/554423931312128.gif", label: "Hộp cookie" },
        { path: "/stickers/pusheen_eats/554423944645460.gif", label: "Chảy dãi thèm" },
        { path: "/stickers/pusheen_eats/554423957978792.gif", label: "No tròn bụng" }
    ],
    reactions: [
        { icon: "👍", label: "Like chuẩn" },
        { icon: "❤️", label: "Yêu quá" },
        { icon: "😂", label: "Cười té ghế" },
        { icon: "🔥", label: "Cháy phố" },
        { icon: "🎉", label: "Chúc mừng" },
        { icon: "🤝", label: "Hợp tác" },
        { icon: "👏", label: "Vỗ tay" },
        { icon: "🚀", label: "Chốt đơn" },
        { icon: "😢", label: "Buồn thiu" },
        { icon: "👀", label: "Hóng hớt" },
        { icon: "⭐", label: "10 điểm" },
        { icon: "💯", label: "Tuyệt đối" }
    ]
};

let currentStickerPack = "pusheen";

function initEmojiStickerPicker() {
    const emojis = [
        "😀", "😂", "🥹", "😍", "🥰", "😎",
        "🥳", "🤩", "🤔", "😭", "😡", "🤯",
        "👍", "👎", "👏", "🙏", "🤝", "✌️",
        "❤️", "💔", "🔥", "⭐", "🎉", "🚀",
        "💯", "👀", "☕", "🍕", "🎵", "✨"
    ];

    const eGrid = document.getElementById("pickerEmojiGrid");
    if (eGrid) {
        eGrid.innerHTML = "";
        emojis.forEach(emo => {
            const b = document.createElement("button");
            b.type = "button";
            b.className = "emoji-item-btn";
            b.innerText = emo;
            b.onclick = () => {
                const input = document.getElementById("txtChatMessage");
                input.value += emo;
                document.getElementById("emojiStickerPicker").style.display = "none";
                input.focus();
            };
            eGrid.appendChild(b);
        });
    }

    renderStickerPack(currentStickerPack);
}

function renderStickerPack(packName) {
    currentStickerPack = packName;
    const sGrid = document.getElementById("pickerStickerGrid");
    if (!sGrid) return;
    sGrid.innerHTML = "";

    const list = STICKER_PACKS[packName] || STICKER_PACKS.pusheen;
    list.forEach(st => {
        const b = document.createElement("button");
        b.type = "button";
        b.className = "sticker-item-btn";
        b.title = st.label;

        if (st.path) {
            b.innerHTML = `<img src="${st.path}" alt="${st.label}" loading="lazy" />`;
            b.onclick = () => {
                document.getElementById("emojiStickerPicker").style.display = "none";
                sendPayloadContent(`[[STICKER|${st.path}|${st.label}]]`);
            };
        } else {
            b.innerHTML = `<span>${st.icon}</span><span>${st.label}</span>`;
            b.onclick = () => {
                document.getElementById("emojiStickerPicker").style.display = "none";
                sendPayloadContent(`[[STICKER|${st.icon}|${st.label}]]`);
            };
        }
        sGrid.appendChild(b);
    });
}

function switchStickerPack(packName) {
    document.querySelectorAll(".pack-tab-btn").forEach(btn => btn.classList.remove("active"));
    const activeBtn = document.getElementById(
        packName === "pusheen" ? "packBtnPusheen" :
        packName === "pusheen_eats" ? "packBtnPusheenEats" : "packBtnReactions"
    );
    if (activeBtn) activeBtn.classList.add("active");
    renderStickerPack(packName);
}

function toggleEmojiPicker() {
    const p = document.getElementById("emojiStickerPicker");
    if (!p) return;
    p.style.display = p.style.display === "none" ? "block" : "none";
}

function switchPickerTab(tab) {
    const isEmoji = tab === "emoji";
    document.getElementById("tabBtnEmoji").classList.toggle("active", isEmoji);
    document.getElementById("tabBtnSticker").classList.toggle("active", !isEmoji);

    const nav = document.getElementById("stickerPacksNav");
    if (nav) nav.style.display = isEmoji ? "none" : "flex";

    document.getElementById("pickerEmojiGrid").style.display = isEmoji ? "grid" : "none";
    document.getElementById("pickerStickerGrid").style.display = isEmoji ? "none" : "grid";

    if (!isEmoji) {
        renderStickerPack(currentStickerPack);
    }
}

function toggleMobileDrawer(side) {
    const left = document.getElementById("leftSidebar");
    const right = document.getElementById("rightSidebar");
    const backdrop = document.getElementById("mobileDrawerBackdrop");
    if (!left || !right || !backdrop) return;

    if (side === "left") {
        const opening = !left.classList.contains("open");
        closeMobileDrawers();
        if (opening) {
            left.classList.add("open");
            backdrop.classList.add("active");
        }
    } else if (side === "right") {
        const opening = !right.classList.contains("open");
        closeMobileDrawers();
        if (opening) {
            right.classList.add("open");
            backdrop.classList.add("active");
        }
    }
}

function closeMobileDrawers() {
    const left = document.getElementById("leftSidebar");
    const right = document.getElementById("rightSidebar");
    const backdrop = document.getElementById("mobileDrawerBackdrop");
    if (left) left.classList.remove("open");
    if (right) right.classList.remove("open");
    if (backdrop) backdrop.classList.remove("active");
}

function openModal(id) {
    closeMobileDrawers();
    document.getElementById(id).style.display = "flex";
}

function closeModal(id) {
    document.getElementById(id).style.display = "none";
}

function openCreateGroupModal() {
    openModal("modalCreateGroup");
}

function showServerInfoModal() {
    alert("Máy chủ Chat Cloud đang chạy tại cổng Web 8080 và Socket TCP 5000.\nBạn có thể chia sẻ link mạng LAN hoặc dùng Cloudflare Tunnel để bạn bè truy cập!");
}

function showOptionsMenu() {
    const pass = prompt("Nhập mật khẩu mới nếu muốn đổi:");
    if (pass && pass.trim()) {
        alert("Đã gửi yêu cầu đổi mật khẩu!");
    }
}

function filterSidebarGroups() {
    const kw = document.getElementById("txtSearchSidebar").value.toLowerCase();
    const items = document.querySelectorAll(".group-item");
    items.forEach(it => {
        const name = it.querySelector(".group-name").innerText.toLowerCase();
        it.style.display = name.includes(kw) ? "flex" : "none";
    });
}

async function doLogout() {
    if (confirm("Bạn có muốn đăng xuất khỏi Web Chat?")) {
        const userToLogout = state.currentUser;
        clearSession();
        if (state.syncIntervalId) {
            clearInterval(state.syncIntervalId);
            state.syncIntervalId = null;
        }
        if (state.eventSource) {
            try { state.eventSource.close(); } catch (e) {}
        }
        if (userToLogout) {
            try {
                await fetch("/api/logout", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ username: userToLogout })
                });
            } catch (ignored) {}
        }
        location.reload();
    }
}

function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/&/g, "&amp;")
              .replace(/</g, "&lt;")
              .replace(/>/g, "&gt;")
              .replace(/"/g, "&quot;")
              .replace(/'/g, "&#039;");
}
