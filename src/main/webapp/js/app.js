/**
 * ChatBox TheStars — Application Frontend Orchestrator
 * Integrates with Java Spring MVC backend, SQL Server APIs, and WebSocket STOMP.
 */

// ── Selection Helpers ──
const $ = (s) => document.querySelector(s);
const $$ = (s) => document.querySelectorAll(s);

// ── Application Core Controller ──
const ChatAppController = (() => {
    // Context Path calculation
    const contextPath = window.location.pathname.startsWith('/chatbox') ? '/chatbox' : '';

    let currentUser = null;
    let conversations = [];
    let activeConversationId = null;
    let stompClient = null;
    let subscriptions = {};
    let selectedMembersForGroup = [];
    let typingTimeout = null;
    let typingIndicatorTimeout = null;

    const TYPING_DEBOUNCE = 1000;
    const TYPING_INDICATOR_DURATION = 3000;

    // ── API Helper ──
    async function api(url, options = {}) {
        const fullUrl = contextPath + url;
        const token = sessionStorage.getItem('chat_token');
        const headers = { 'Content-Type': 'application/json', ...options.headers };
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }
        
        const res = await fetch(fullUrl, {
            headers: headers,
            ...options
        });
        if (res.status === 401 || res.status === 403) {
            sessionStorage.removeItem('chat_token');
            window.location.href = contextPath + '/login';
            return null;
        }
        if (!res.ok) {
            console.error(`API Error [${res.status}]: ${res.statusText}`);
            return null;
        }
        return res.json();
    }

    // ── Initialize App ──
    async function init() {
        const token = sessionStorage.getItem('chat_token');
        if (!token) {
            window.location.href = contextPath + '/login';
            return;
        }

        // Load logged in user
        currentUser = await api('/api/auth/me');
        if (!currentUser) {
            window.location.href = contextPath + '/login';
            return;
        }

        // Populate User Info in UI
        if ($('#sidebar-user-avatar')) {
            $('#sidebar-user-avatar').src = currentUser.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(currentUser.displayName);
        }
        if ($('#sidebar-user-name')) {
            $('#sidebar-user-name').textContent = currentUser.displayName;
        }

        // Initialize features
        setupNavigation();
        setupSearch();
        setupAdminTabs();
        setupGroupCreation();
        setupBrowseGroups();
        setupFriendsPanel();
        setupEmptyStateCta();
        setupInputHandlers();

        // Load initial conversations
        await loadConversations();
        refreshFriendsBadgeCount();

        // Connect STOMP WebSocket
        connectWebSocket();
    }

    // ── WebSocket Connection (SockJS + STOMP) ──
    function connectWebSocket() {
        setSelfStatus('connecting');

        const token = sessionStorage.getItem('chat_token');
        const socket = new SockJS(contextPath + '/ws?token=' + encodeURIComponent(token));
        stompClient = Stomp.over(socket);
        stompClient.debug = null; // Disable debug log

        stompClient.connect({}, (frame) => {
            console.log('[WS] Connected successfully');
            setSelfStatus('online');
            if (activeConversationId) {
                subscribeToConversation(activeConversationId);
            }

            // Personal queue for friend request / acceptance / removal events
            stompClient.subscribe('/user/queue/friends', (msg) => {
                handleFriendEvent(JSON.parse(msg.body));
            });

            // Broadcast of who just came online/offline, so open chats stay accurate live
            stompClient.subscribe('/topic/presence', (msg) => {
                handlePresenceEvent(JSON.parse(msg.body));
            });
        }, (error) => {
            console.error('[WS] Connection error, reconnecting in 5s...', error);
            setSelfStatus('offline');
            setTimeout(connectWebSocket, 5000);
        });
    }

    // Our own status is only truly known by whether our WebSocket is actually
    // connected right now — reading it back from the DB would lag by a poll cycle.
    function setSelfStatus(state) {
        const dot = $('#self-status-dot');
        const text = $('#self-status-text');
        if (!dot || !text) return;

        const states = {
            online: ['dot--online', 'Online'],
            connecting: ['dot--away', 'Connecting...'],
            offline: ['dot--offline', 'Offline']
        };
        const [cls, label] = states[state] || states.offline;
        dot.className = 'dot ' + cls;
        text.textContent = label;
    }

    function subscribeToConversation(id) {
        if (!stompClient || !stompClient.connected) return;

        // Unsubscribe existing
        if (subscriptions[`conv-${id}`]) {
            subscriptions[`conv-${id}`].unsubscribe();
            subscriptions[`typing-${id}`].unsubscribe();
            subscriptions[`react-${id}`].unsubscribe();
        }

        // 1. Message Subscription
        subscriptions[`conv-${id}`] = stompClient.subscribe(`/topic/conversation/${id}`, (msg) => {
            const message = JSON.parse(msg.body);
            appendMessage(message);
        });

        // 2. Typing Indicator Subscription
        subscriptions[`typing-${id}`] = stompClient.subscribe(`/topic/conversation/${id}/typing`, (msg) => {
            const typingEvent = JSON.parse(msg.body);
            showTypingIndicator(typingEvent);
        });

        // 3. Reaction Subscription
        subscriptions[`react-${id}`] = stompClient.subscribe(`/topic/conversation/${id}/reaction`, (msg) => {
            const reactionEvent = JSON.parse(msg.body);
            handleReactionUpdate(reactionEvent);
        });
    }

    // ── Navigation Flow & SPA toggles ──
    function setupNavigation() {
        // Toggle Resource Panel (Right Column)
        const toggleCol3 = $('#btn-toggle-col3');
        const colResources = $('#col-resources');
        const closeCol3 = $('#btn-close-col3');

        if (toggleCol3 && colResources) {
            toggleCol3.addEventListener('click', () => colResources.classList.toggle('hidden'));
        }
        if (closeCol3 && colResources) {
            closeCol3.addEventListener('click', () => colResources.classList.add('hidden'));
        }

        // Toggle Admin Panel
        const openAdmin = $('#btn-open-admin');
        const closeAdmin = $('#btn-close-admin');
        const adminOverlay = $('#admin-overlay');

        if (openAdmin && adminOverlay) {
            openAdmin.addEventListener('click', () => {
                adminOverlay.classList.remove('hidden');
                loadAdminUsers();
                loadAdminGroups();
                loadAdminStats();
            });
        }
        if (closeAdmin && adminOverlay) {
            closeAdmin.addEventListener('click', () => adminOverlay.classList.add('hidden'));
        }
        if (adminOverlay) {
            adminOverlay.addEventListener('click', (e) => {
                if (e.target === adminOverlay) adminOverlay.classList.add('hidden');
            });
        }

        // Logout handler
        const btnLogout = $('#btn-logout');
        if (btnLogout) {
            btnLogout.addEventListener('click', () => {
                sessionStorage.removeItem('chat_token');
                window.location.href = contextPath + '/login';
            });
        }
    }

    // ── Search & Filter ──
    function setupSearch() {
        const searchInput = $('#search-input');
        const resultsBox = $('#global-search-results');
        const resultsList = $('#global-search-list');

        if (searchInput && resultsBox && resultsList) {
            searchInput.addEventListener('input', async (e) => {
                const query = e.target.value.toLowerCase().trim();
                
                // 1. Filter local active conversations
                $$('.chat-list li').forEach(item => {
                    const name = item.querySelector('.chat-name').textContent.toLowerCase();
                    item.style.display = name.includes(query) ? 'flex' : 'none';
                });

                // 2. Global search users (Zalo-like)
                if (!query || query.length < 1) {
                    resultsBox.classList.add('hidden');
                    resultsList.innerHTML = '';
                    return;
                }

                const users = await api(`/api/conversations/users/search?q=${encodeURIComponent(query)}`) || [];
                resultsList.innerHTML = '';

                // Filter out self
                const others = users.filter(u => u.id !== currentUser.id);

                if (others.length === 0) {
                    resultsList.innerHTML = '<li style="font-size:12px; opacity:0.5; padding:6px; text-align:center;">No users found</li>';
                    resultsBox.classList.remove('hidden');
                    return;
                }

                // Fetch friend-relationship state for every result up front
                const statuses = await Promise.all(
                    others.map(u => api(`/api/friends/status/${u.id}`))
                );

                others.forEach((user, idx) => {
                    const rel = statuses[idx] || { status: 'NONE' };
                    const li = document.createElement('li');
                    li.style.cssText = 'display:flex; align-items:center; gap:10px; padding:6px 8px; cursor:pointer; border-radius:6px; transition:background var(--t-fast);';
                    li.innerHTML = `
                        <img src="${user.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(user.displayName)}" style="width:28px; height:28px; border-radius:50%;" alt="">
                        <div style="display:flex; flex-direction:column; flex:1; min-width:0;">
                            <span style="font-size:13px; font-weight:500; color:var(--text-white);">${escapeHtml(user.displayName)}</span>
                            <span style="font-size:10px; opacity:0.5;">${escapeHtml(user.email)}</span>
                        </div>
                        ${friendButtonHtml(rel)}
                    `;

                    li.addEventListener('mouseenter', () => li.style.background = 'rgba(255,255,255,0.06)');
                    li.addEventListener('mouseleave', () => li.style.background = 'transparent');

                    const friendBtn = li.querySelector('.search-friend-btn');
                    if (friendBtn && friendBtn.tagName === 'BUTTON') {
                        friendBtn.addEventListener('click', (e) => {
                            e.stopPropagation();
                            handleFriendButtonClick(user.id, friendBtn);
                        });
                    }

                    li.addEventListener('click', async () => {
                        // Create or retrieve 1-1 chat
                        const res = await api('/api/conversations', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ type: 'SINGLE', userId: user.id })
                        });

                        if (res) {
                            searchInput.value = '';
                            resultsBox.classList.add('hidden');
                            resultsList.innerHTML = '';

                            // Reload local list and select
                            await loadConversations();
                            selectConversation(res.id);
                        }
                    });

                    resultsList.appendChild(li);
                });

                resultsBox.classList.remove('hidden');
            });

            // Hide on clicking outside
            document.addEventListener('click', (e) => {
                if (!searchInput.contains(e.target) && !resultsBox.contains(e.target)) {
                    resultsBox.classList.add('hidden');
                }
            });
        }
    }

    // ── Load & Render Conversations ──
    async function loadConversations() {
        conversations = await api('/api/conversations') || [];
        renderConversations();
        updateEmptyState();
    }

    function renderConversations() {
        const listNew = $('#chat-list-new');
        const listLast = $('#chat-list-last');
        if (!listNew || !listLast) return;

        listNew.innerHTML = '';
        listLast.innerHTML = '';

        conversations.forEach(conv => {
            const li = document.createElement('li');
            li.dataset.id = conv.id;
            if (activeConversationId === conv.id) {
                li.className = 'active';
            }

            const unreadBadge = conv.unreadCount > 0 ? `<span class="unread-badge">${conv.unreadCount}</span>` : '';
            const statusClass = presenceDotClass(conv);
            const lastMsgText = conv.lastMessage ? conv.lastMessage.content : 'No messages yet';
            const lastMsgSender = conv.lastMessage ? (conv.lastMessage.sender?.displayName || 'System') : '';
            const preview = lastMsgSender ? `${lastMsgSender}: ${lastMsgText}` : lastMsgText;
            const timeStr = conv.lastMessage ? formatTime(conv.lastMessage.createdAt) : '';

            li.innerHTML = `
                <div class="chat-avatar-wrap">
                    <img src="${conv.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(conv.name)}" alt="Avatar" class="avatar avatar--sm">
                    <i class="dot ${statusClass}"></i>
                </div>
                <div class="chat-meta">
                    <div class="chat-name">${escapeHtml(conv.name)}</div>
                    <div class="chat-preview">${escapeHtml(preview)}</div>
                </div>
                <div class="chat-time">${timeStr}</div>
                ${unreadBadge}
            `;

            li.addEventListener('click', () => selectConversation(conv.id));

            if (conv.unreadCount > 0) {
                listNew.appendChild(li);
            } else {
                listLast.appendChild(li);
            }
        });

        // Hide section headers if empty
        $('#section-new').style.display = listNew.children.length > 0 ? 'block' : 'none';
    }

    // ── Select Conversation ──
    async function selectConversation(id) {
        activeConversationId = id;

        // Highlight selected
        $$('.chat-list li').forEach(li => {
            li.classList.toggle('active', parseInt(li.dataset.id) === id);
        });

        // Show UI elements
        $('#chat-empty').classList.add('hidden');
        $('#chat-header').classList.remove('hidden');
        $('#chat-messages').classList.remove('hidden');
        $('#chat-footer').classList.remove('hidden');

        // Header info
        const conv = conversations.find(c => c.id === id);
        if (conv) {
            $('#chat-avatar').src = conv.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(conv.name);
            $('#chat-name').textContent = conv.name;
            updateChatHeaderStatus(conv);
        }

        // Sub and Load
        subscribeToConversation(id);
        await loadMessages(id);
        await loadResources(id);

        // Mark as read
        api(`/api/conversations/${id}/read`, { method: 'POST' });
        const c = conversations.find(x => x.id === id);
        if (c) {
            c.unreadCount = 0;
            renderConversations();
        }
    }

    // ── Load & Render Messages ──
    async function loadMessages(id) {
        const messages = await api(`/api/conversations/${id}/messages?page=0&size=50`) || [];
        const scrollContainer = $('#messages-scroll');
        if (!scrollContainer) return;

        scrollContainer.innerHTML = '';
        // Messages arrive newest first, reverse to render chronologically
        const sorted = [...messages].reverse();
        sorted.forEach(msg => appendMessageHtml(msg));

        scrollToBottom();
    }

    function appendMessage(msg) {
        if (msg.conversationId === activeConversationId) {
            appendMessageHtml(msg);
            scrollToBottom();
            hideTypingIndicatorDirect();
        }
        
        // Refresh conversations list to update preview
        loadConversations();
    }

    function appendMessageHtml(msg) {
        const scrollContainer = $('#messages-scroll');
        if (!scrollContainer) return;

        const isSelf = msg.senderId === currentUser.id;
        const msgDiv = document.createElement('div');
        msgDiv.className = `msg ${isSelf ? 'msg--self' : 'msg--other'}`;
        msgDiv.dataset.messageId = msg.id;

        const avatarUrl = msg.sender?.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(msg.sender?.displayName || 'User');
        const timeStr = formatTime(msg.createdAt);

        let bubbleContent = '';

        if (msg.messageType === 'MEETING_LINK' || isMeetingUrl(msg.content)) {
            bubbleContent = `
                <div class="meeting-card">
                    <div class="meeting-card__top">
                        <div class="meeting-card__icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2"/></svg>
                        </div>
                        <div>
                            <div class="meeting-card__title">Zoom / Google Meet Live Session</div>
                            <div class="meeting-card__url">${escapeHtml(msg.content)}</div>
                        </div>
                    </div>
                    <a href="${msg.content}" target="_blank" class="btn-join">Join Meeting</a>
                </div>
            `;
        } else if (msg.messageType === 'IMAGE' && msg.attachments?.length) {
            bubbleContent = msg.attachments.map(att => 
                `<img src="${contextPath}${att.fileUrl}" class="message-image" style="max-width: 260px; border-radius: 12px; margin-top: 4px;" alt="Image">`
            ).join('');
        } else if (msg.messageType === 'FILE' && msg.attachments?.length) {
            const att = msg.attachments[0];
            bubbleContent = `
                <div class="msg__bubble">
                    <div class="msg__sender">${escapeHtml(msg.sender?.displayName)}</div>
                    <div>📎 <a href="${contextPath}/api/files/${att.id}" target="_blank" style="color:var(--neon-light); text-decoration:underline;">${escapeHtml(att.fileName)}</a></div>
                </div>
            `;
        } else {
            bubbleContent = `
                <div class="msg__bubble">
                    <div class="msg__sender">${escapeHtml(msg.sender?.displayName)}</div>
                    <div>${escapeHtml(msg.content)}</div>
                </div>
            `;
        }

        // Reactions
        let reactionsHtml = '';
        if (msg.reactions?.length) {
            const grouped = groupReactions(msg.reactions);
            reactionsHtml = `<div class="msg__reactions">` + 
                Object.entries(grouped).map(([emoji, uids]) => 
                    `<span class="reaction" data-emoji="${emoji}" data-msg-id="${msg.id}">
                        ${emoji} <span style="font-size:10px; opacity:0.7">${uids.length}</span>
                    </span>`
                ).join('') + `</div>`;
        }

        msgDiv.innerHTML = `
            ${!isSelf ? `<img src="${avatarUrl}" alt="Avatar" class="msg__avatar">` : ''}
            <div class="msg__body">
                ${bubbleContent}
                <div class="msg__time">${timeStr}</div>
                ${reactionsHtml}
            </div>
        `;

        // Attach reaction click handlers
        msgDiv.querySelectorAll('.reaction').forEach(chip => {
            chip.addEventListener('click', () => {
                toggleReaction(msg.id, chip.dataset.emoji);
            });
        });

        // Double click to send quick reaction 👍
        msgDiv.querySelector('.msg__bubble')?.addEventListener('dblclick', () => {
            toggleReaction(msg.id, '👍');
        });

        scrollContainer.appendChild(msgDiv);
    }

    // ── Input & Send Handlers ──
    function setupInputHandlers() {
        const input = $('#msg-input');
        const sendBtn = $('#btn-send');
        const attachBtn = $('#btn-attach');

        // Create virtual file input for uploads
        const fileInput = document.createElement('input');
        fileInput.type = 'file';
        fileInput.style.display = 'none';
        document.body.appendChild(fileInput);

        if (attachBtn) {
            attachBtn.addEventListener('click', () => fileInput.click());
        }

        fileInput.addEventListener('change', async (e) => {
            const file = e.target.files[0];
            if (!file || !activeConversationId) return;

            const formData = new FormData();
            formData.append('file', file);
            formData.append('conversationId', activeConversationId);

            // Fetch to upload REST endpoint
            const res = await fetch(contextPath + '/api/upload', {
                method: 'POST',
                body: formData
            });

            if (res.ok) {
                // Successful upload will trigger WebSocket broadcast
                fileInput.value = '';
                await loadResources(activeConversationId);
            }
        });

        function sendCurrentMessage() {
            if (!input || !activeConversationId) return;
            const text = input.value.trim();
            if (!text) return;

            const isMeeting = isMeetingUrl(text);

            stompClient.send(`/app/chat.send/${activeConversationId}`, {}, JSON.stringify({
                content: text,
                messageType: isMeeting ? 'MEETING_LINK' : 'TEXT'
            }));

            input.value = '';
            input.focus();
        }

        if (sendBtn) sendBtn.addEventListener('click', sendCurrentMessage);
        if (input) {
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    sendCurrentMessage();
                } else {
                    // Send typing indicator
                    sendTypingIndicator();
                }
            });
        }
    }

    // ── Typing Indicator ──
    function sendTypingIndicator() {
        if (!stompClient || !stompClient.connected || !activeConversationId) return;
        
        clearTimeout(typingTimeout);
        stompClient.send(`/app/chat.typing/${activeConversationId}`, {}, '{}');
        typingTimeout = setTimeout(() => {}, TYPING_DEBOUNCE);
    }

    function showTypingIndicator(evt) {
        if (evt.userId === currentUser.id) return; // Skip self

        const bar = $('#typing-bar');
        const who = $('#typing-who');

        if (bar && who) {
            who.textContent = `${evt.displayName} is typing...`;
            bar.classList.remove('hidden');
            scrollToBottom();

            clearTimeout(typingIndicatorTimeout);
            typingIndicatorTimeout = setTimeout(hideTypingIndicatorDirect, TYPING_INDICATOR_DURATION);
        }
    }

    function hideTypingIndicatorDirect() {
        $('#typing-bar')?.classList.add('hidden');
    }

    // ── Reactions ──
    function toggleReaction(messageId, emoji) {
        if (!stompClient || !stompClient.connected || !activeConversationId) return;
        stompClient.send(`/app/chat.react/${activeConversationId}`, {}, JSON.stringify({
            messageId: messageId,
            emoji: emoji
        }));
    }

    function handleReactionUpdate(evt) {
        // For simplicity, reload messages in the active screen to update reaction counts
        if (activeConversationId) {
            loadMessages(activeConversationId);
        }
    }

    // ── Load Side Panel Resources ──
    async function loadResources(conversationId) {
        const data = await api(`/api/conversations/${conversationId}/attachments`) || { images: [], files: [] };
        const imageGrid = $('#images-grid');
        const fileList = $('#files-list');

        if (!imageGrid || !fileList) return;

        imageGrid.innerHTML = '';
        fileList.innerHTML = '';

        // Render Images
        $('#badge-images').textContent = data.images.length;
        data.images.forEach(img => {
            const thumb = document.createElement('div');
            thumb.className = 'img-thumb';
            thumb.innerHTML = `<img src="${contextPath}${img.fileUrl}" alt="Thumb">`;
            imageGrid.appendChild(thumb);
        });

        // Render Files
        $('#badge-files').textContent = data.files.length;
        data.files.forEach(file => {
            const li = document.createElement('li');
            const ext = file.fileName.split('.').pop().toLowerCase();
            let iconClass = 'file-icon--other';
            if (ext === 'pdf') iconClass = 'file-icon--pdf';
            else if (ext === 'figma' || ext === 'fig') iconClass = 'file-icon--figma';
            else if (ext === 'ae' || ext === 'aep') iconClass = 'file-icon--ae';
            else if (['doc', 'docx'].includes(ext)) iconClass = 'file-icon--doc';

            li.innerHTML = `
                <div class="file-icon ${iconClass}">${ext}</div>
                <div class="file-meta">
                    <div class="file-name">${escapeHtml(file.fileName)}</div>
                    <div class="file-size">${formatFileSize(file.fileSize)}</div>
                </div>
                <a href="${contextPath}/api/files/${file.id}" target="_blank" class="file-download" title="Download">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                </a>
            `;
            fileList.appendChild(li);
        });
    }

    // ── Admin Panel Content Loading ──
    async function loadAdminUsers() {
        const users = await api('/api/conversations/users') || [];
        const tbody = $('#admin-users-tbody');
        if (!tbody) return;

        // Friend-relationship state for every user except ourselves, used to
        // render an Add Friend / Pending / Accept / Friends action per row.
        const others = users.filter(u => u.id !== currentUser.id);
        const statuses = await Promise.all(others.map(u => api(`/api/friends/status/${u.id}`)));
        const statusById = {};
        others.forEach((u, idx) => { statusById[u.id] = statuses[idx] || { status: 'NONE' }; });

        tbody.innerHTML = '';
        users.forEach(u => {
            const tr = document.createElement('tr');
            let teamClass = 'team-badge--it';
            if (u.team === 'Marketing') teamClass = 'team-badge--marketing';
            else if (u.team === 'Design') teamClass = 'team-badge--design';

            let statusClass = 'dot--online';
            if (u.status === 'AWAY') statusClass = 'dot--away';
            else if (u.status === 'OFFLINE') statusClass = 'dot--offline';

            const actionHtml = u.id === currentUser.id
                ? '<span style="opacity:0.4; font-size:11px;">You</span>'
                : friendButtonHtml(statusById[u.id]);

            tr.innerHTML = `
                <td>
                    <div class="admin-user-cell">
                        <img src="${u.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(u.displayName)}" alt="">
                        <span>${escapeHtml(u.displayName)}</span>
                    </div>
                </td>
                <td>${escapeHtml(u.email)}</td>
                <td><span class="team-badge ${teamClass}">${escapeHtml(u.team || 'IT')}</span></td>
                <td>
                    <span class="status-badge">
                        <i class="dot ${statusClass}"></i> ${u.status}
                    </span>
                </td>
                <td>${actionHtml}</td>
            `;

            const friendBtn = tr.querySelector('.search-friend-btn');
            if (friendBtn && friendBtn.tagName === 'BUTTON') {
                friendBtn.addEventListener('click', () => handleFriendButtonClick(u.id, friendBtn));
            }

            tbody.appendChild(tr);
        });
    }

    async function loadAdminGroups() {
        // Reuse conversations that are groups
        const list = $('#admin-groups-list');
        if (!list) return;

        list.innerHTML = '';
        conversations.filter(c => c.type === 'GROUP').forEach(group => {
            const li = document.createElement('li');
            li.innerHTML = `
                <div class="group-info">
                    <div class="group-avatar">${escapeHtml(group.name[0])}</div>
                    <div>
                        <div class="group-name">${escapeHtml(group.name)}</div>
                        <div class="group-members">Active Group chat</div>
                    </div>
                </div>
                <button class="btn-danger-sm" onclick="alert('Archiving: ${escapeHtml(group.name)}')">Archive</button>
            `;
            list.appendChild(li);
        });
    }

    function loadAdminStats() {
        const grid = $('#stats-grid');
        if (!grid) return;

        // Static visual stats representations
        const STATS_DATA = [
            { label: "Active Connections", value: "Online", class: "green", width: "100%" },
            { label: "Database Engine", value: "SQL Server", class: "blue", width: "100%" }
        ];

        grid.innerHTML = '';
        STATS_DATA.forEach(stat => {
            const card = document.createElement('div');
            card.className = 'stat-card';
            card.innerHTML = `
                <div class="stat-card__value ${stat.class}">${stat.value}</div>
                <div class="stat-card__label">${stat.label}</div>
            `;
            grid.appendChild(card);
        });
    }

    // ── Group Creation Logic ──
    function setupGroupCreation() {
        const modal = $('#modal-create-group');
        const cancelBtn = $('#btn-modal-cancel');
        const createBtn = $('#btn-modal-create');
        const closeBtn = $('#btn-close-modal');
        const searchInput = $('#input-member-search');

        const closeModal = () => modal.classList.add('hidden');
        if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
        if (closeBtn) closeBtn.addEventListener('click', closeModal);

        const openBtn = $('#btn-new-group');
        if (openBtn) {
            openBtn.addEventListener('click', () => {
                selectedMembersForGroup = [];
                const nameInput = $('#input-group-name');
                if (nameInput) nameInput.value = '';
                const searchMember = $('#input-member-search');
                if (searchMember) searchMember.value = '';
                const results = $('#member-results');
                if (results) results.innerHTML = '';
                const chips = $('#member-chips');
                if (chips) chips.innerHTML = '';
                modal.classList.remove('hidden');
            });
        }

        // Member search in group modal
        if (searchInput) {
            searchInput.addEventListener('input', async (e) => {
                const query = e.target.value.toLowerCase().trim();
                const resultsBox = $('#member-results');
                if (!resultsBox) return;

                if (!query || query.length < 2) {
                    resultsBox.innerHTML = '';
                    return;
                }

                const users = await api(`/api/conversations/users/search?q=${encodeURIComponent(query)}`) || [];
                resultsBox.innerHTML = '';

                users.filter(u => u.id !== currentUser.id).forEach(user => {
                    const div = document.createElement('div');
                    div.className = 'member-result';
                    div.innerHTML = `
                        <img src="${user.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(user.displayName)}" alt="">
                        <span>${escapeHtml(user.displayName)}</span>
                    `;
                    div.addEventListener('click', () => {
                        if (!selectedMembersForGroup.includes(user.id)) {
                            selectedMembersForGroup.push(user.id);
                            renderSelectedChips();
                        }
                        searchInput.value = '';
                        resultsBox.innerHTML = '';
                    });
                    resultsBox.appendChild(div);
                });
            });
        }

        function renderSelectedChips() {
            const container = $('#member-chips');
            if (!container) return;
            container.innerHTML = '';

            selectedMembersForGroup.forEach(id => {
                const chip = document.createElement('span');
                chip.className = 'chip';
                chip.innerHTML = `
                    Member ID: ${id}
                    <span class="chip__remove">&times;</span>
                `;
                chip.querySelector('.chip__remove').addEventListener('click', () => {
                    selectedMembersForGroup = selectedMembersForGroup.filter(mid => mid !== id);
                    renderSelectedChips();
                });
                container.appendChild(chip);
            });
        }

        // Submitting new group creation
        if (createBtn) {
            createBtn.addEventListener('click', async () => {
                const name = $('#input-group-name').value.trim();
                if (!name) {
                    showToast('Give the group a name first.', 'error');
                    return;
                }
                if (selectedMembersForGroup.length === 0) {
                    showToast('Add at least one member before creating the group.', 'error');
                    return;
                }

                const res = await api('/api/conversations', {
                    method: 'POST',
                    body: JSON.stringify({
                        type: 'GROUP',
                        name: name,
                        memberIds: selectedMembersForGroup
                    })
                });

                if (res) {
                    closeModal();
                    showToast(`Group "${name}" created.`, 'success');
                    await loadConversations();
                    selectConversation(res.id);
                } else {
                    showToast('Could not create the group. Please try again.', 'error');
                }
            });
        }
    }

    // ── Browse & Join Pre-created Groups ──
    function setupBrowseGroups() {
        const btnOpen = $('#btn-browse-groups');
        const modal = $('#modal-browse-groups');
        const btnClose = $('#btn-close-browse-modal');
        const btnCancel = $('#btn-browse-cancel');
        const listContainer = $('#browse-groups-list');

        const closeModal = () => modal.classList.add('hidden');

        if (btnOpen) {
            btnOpen.addEventListener('click', async () => {
                modal.classList.remove('hidden');
                listContainer.innerHTML = '<div style="text-align:center; opacity:0.5; padding:20px;">Loading groups...</div>';
                
                const groups = await api('/api/conversations/available') || [];
                listContainer.innerHTML = '';
                
                if (groups.length === 0) {
                    listContainer.innerHTML = '<div style="text-align:center; opacity:0.5; padding:20px;">No available groups found to join.</div>';
                    return;
                }
                
                groups.forEach(g => {
                    const item = document.createElement('div');
                    item.className = 'available-group-item';
                    item.innerHTML = `
                        <div class="group-info">
                            <div class="group-avatar">${escapeHtml(g.name ? g.name[0] : 'G')}</div>
                            <div class="group-name">${escapeHtml(g.name)}</div>
                        </div>
                        <button class="btn-primary btn-sm btn-join-grp" data-id="${g.id}">Join</button>
                    `;
                    
                    item.querySelector('.btn-join-grp').addEventListener('click', async (e) => {
                        const btn = e.target;
                        btn.disabled = true;
                        btn.textContent = 'Joining...';
                        
                        const res = await api(`/api/conversations/${g.id}/participants`, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ userId: currentUser.id })
                        });
                        
                        if (res) {
                            closeModal();
                            showToast(`Joined "${g.name}".`, 'success');
                            await loadConversations();
                            selectConversation(g.id);
                        } else {
                            btn.disabled = false;
                            btn.textContent = 'Join';
                            showToast('Could not join this group. Please try again.', 'error');
                        }
                    });
                    
                    listContainer.appendChild(item);
                });
            });
        }

        if (btnClose) btnClose.addEventListener('click', closeModal);
        if (btnCancel) btnCancel.addEventListener('click', closeModal);
    }

    // ── Friend Connections ──
    function friendButtonHtml(rel) {
        if (rel.status === 'ACCEPTED') {
            return `<span class="search-friend-btn is-friends">Friends</span>`;
        }
        if (rel.status === 'PENDING' && rel.direction === 'OUTGOING') {
            return `<button class="search-friend-btn is-pending" data-action="cancel" data-friendship-id="${rel.friendshipId}">Pending</button>`;
        }
        if (rel.status === 'PENDING' && rel.direction === 'INCOMING') {
            return `<button class="search-friend-btn" data-action="accept" data-friendship-id="${rel.friendshipId}">Accept</button>`;
        }
        return `<button class="search-friend-btn" data-action="add">Add Friend</button>`;
    }

    async function handleFriendButtonClick(userId, btn) {
        const action = btn.dataset.action;
        btn.disabled = true;

        if (action === 'add') {
            const res = await api('/api/friends/requests', {
                method: 'POST',
                body: JSON.stringify({ addresseeId: userId })
            });
            if (res) {
                btn.textContent = 'Pending';
                btn.classList.add('is-pending');
                btn.dataset.action = 'cancel';
                btn.dataset.friendshipId = res.id;
                showToast('Friend request sent.', 'success');
            } else {
                showToast('Could not send the friend request — a relationship may already exist.', 'error');
            }
        } else if (action === 'cancel') {
            const res = await api(`/api/friends/${btn.dataset.friendshipId}`, { method: 'DELETE' });
            if (res) {
                btn.textContent = 'Add Friend';
                btn.classList.remove('is-pending');
                btn.dataset.action = 'add';
                delete btn.dataset.friendshipId;
                showToast('Friend request canceled.', 'info');
            } else {
                showToast('Could not cancel the request. Please try again.', 'error');
            }
        } else if (action === 'accept') {
            const res = await api(`/api/friends/requests/${btn.dataset.friendshipId}/accept`, { method: 'POST' });
            if (res) {
                btn.outerHTML = '<span class="search-friend-btn is-friends">Friends</span>';
                showToast('You are now friends!', 'success');
                refreshFriendsBadgeCount();
                return;
            } else {
                showToast('Could not accept the request. Please try again.', 'error');
            }
        }

        btn.disabled = false;
        refreshFriendsBadgeCount();
    }

    function setupFriendsPanel() {
        const btnOpen = $('#btn-open-friends');
        const popover = $('#friends-overlay');
        const btnClose = $('#btn-close-friends');

        const closePopover = () => popover.classList.add('hidden');

        const openPopover = () => {
            // Anchor the popover just under the Friends button, wherever it is
            // (sidebar layout can shift on mobile), instead of a fixed guess.
            const rect = btnOpen.getBoundingClientRect();
            popover.style.top = (rect.bottom + 8) + 'px';
            popover.style.left = Math.max(14, rect.left) + 'px';

            popover.classList.remove('hidden');
            loadFriends();
        };

        if (btnOpen && popover) {
            btnOpen.addEventListener('click', (e) => {
                e.stopPropagation();
                if (popover.classList.contains('hidden')) {
                    openPopover();
                } else {
                    closePopover();
                }
            });
        }
        if (btnClose && popover) {
            btnClose.addEventListener('click', closePopover);
        }
        if (popover) {
            // Close on outside click, but not on clicks inside the popover itself.
            document.addEventListener('click', (e) => {
                if (!popover.classList.contains('hidden') &&
                    !popover.contains(e.target) && e.target !== btnOpen) {
                    closePopover();
                }
            });
            popover.addEventListener('click', (e) => e.stopPropagation());
        }

        $$('#friends-overlay .friends-tab-btn').forEach(tab => {
            tab.addEventListener('click', () => {
                $$('#friends-overlay .friends-tab-btn').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');

                const target = tab.dataset.friendsTab;
                $$('.friends-tab-content').forEach(content => {
                    content.classList.toggle('active', content.id === `friends-tab-${target}`);
                });
            });
        });
    }

    async function loadFriends() {
        const [friends, incoming, outgoing] = await Promise.all([
            api('/api/friends'),
            api('/api/friends/requests/incoming'),
            api('/api/friends/requests/outgoing')
        ]);

        renderFriendsList(friends || []);
        renderFriendRequests(incoming || [], outgoing || []);
        updateFriendsBadge((incoming || []).length);
    }

    function renderFriendsList(friendships) {
        const list = $('#friends-list');
        if (!list) return;
        list.innerHTML = '';

        if (friendships.length === 0) {
            list.innerHTML = `
                <li class="friend-item__empty">
                    <p>No friends yet.</p>
                    <button class="btn-primary btn-sm" id="btn-friends-empty-cta">Find people</button>
                </li>
            `;
            list.querySelector('#btn-friends-empty-cta')?.addEventListener('click', () => {
                $('#friends-overlay').classList.add('hidden');
                focusSearch();
            });
            return;
        }

        friendships.forEach(f => {
            const user = f.requesterId === currentUser.id ? f.addressee : f.requester;
            if (!user) return;

            const li = document.createElement('li');
            li.className = 'friend-item';
            li.innerHTML = `
                <div class="friend-item__info">
                    <img src="${user.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(user.displayName)}" alt="">
                    <div>
                        <div class="friend-item__name">${escapeHtml(user.displayName)}</div>
                        <div class="friend-item__sub">${escapeHtml(user.email)}</div>
                    </div>
                </div>
                <div class="friend-item__actions">
                    <button class="btn-primary btn-sm btn-message-friend">Message</button>
                    <button class="btn-danger-sm btn-remove-friend">Remove</button>
                </div>
            `;
            li.querySelector('.btn-message-friend').addEventListener('click', async () => {
                const res = await api('/api/conversations', {
                    method: 'POST',
                    body: JSON.stringify({ type: 'SINGLE', userId: user.id })
                });
                if (res) {
                    $('#friends-overlay').classList.add('hidden');
                    await loadConversations();
                    selectConversation(res.id);
                }
            });
            li.querySelector('.btn-remove-friend').addEventListener('click', async () => {
                if (await api(`/api/friends/${f.id}`, { method: 'DELETE' })) {
                    showToast(`Removed ${user.displayName} from your friends.`, 'info');
                    loadFriends();
                } else {
                    showToast('Could not remove this friend. Please try again.', 'error');
                }
            });
            list.appendChild(li);
        });
    }

    function renderFriendRequests(incoming, outgoing) {
        const incomingList = $('#friend-requests-incoming');
        const outgoingList = $('#friend-requests-outgoing');
        if (!incomingList || !outgoingList) return;

        incomingList.innerHTML = '';
        if (incoming.length === 0) {
            incomingList.innerHTML = '<li class="friend-item__empty"><p>No incoming requests.</p></li>';
        } else {
            incoming.forEach(f => {
                const user = f.requester;
                const li = document.createElement('li');
                li.className = 'friend-item';
                li.innerHTML = `
                    <div class="friend-item__info">
                        <img src="${user?.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(user?.displayName || '?')}" alt="">
                        <div class="friend-item__name">${escapeHtml(user?.displayName || 'Unknown')}</div>
                    </div>
                    <div class="friend-item__actions">
                        <button class="btn-primary btn-sm btn-accept-request">Accept</button>
                        <button class="btn-danger-sm btn-decline-request">Decline</button>
                    </div>
                `;
                li.querySelector('.btn-accept-request').addEventListener('click', async () => {
                    if (await api(`/api/friends/requests/${f.id}/accept`, { method: 'POST' })) {
                        showToast(`You and ${user?.displayName || 'this user'} are now friends!`, 'success');
                        loadFriends();
                    } else {
                        showToast('Could not accept this request. Please try again.', 'error');
                    }
                });
                li.querySelector('.btn-decline-request').addEventListener('click', async () => {
                    if (await api(`/api/friends/${f.id}`, { method: 'DELETE' })) {
                        showToast('Request declined.', 'info');
                        loadFriends();
                    } else {
                        showToast('Could not decline this request. Please try again.', 'error');
                    }
                });
                incomingList.appendChild(li);
            });
        }

        outgoingList.innerHTML = '';
        if (outgoing.length === 0) {
            outgoingList.innerHTML = '<li class="friend-item__empty"><p>No sent requests.</p></li>';
        } else {
            outgoing.forEach(f => {
                const user = f.addressee;
                const li = document.createElement('li');
                li.className = 'friend-item';
                li.innerHTML = `
                    <div class="friend-item__info">
                        <img src="${user?.avatar || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(user?.displayName || '?')}" alt="">
                        <div class="friend-item__name">${escapeHtml(user?.displayName || 'Unknown')}</div>
                    </div>
                    <div class="friend-item__actions">
                        <button class="btn-danger-sm btn-cancel-request">Cancel</button>
                    </div>
                `;
                li.querySelector('.btn-cancel-request').addEventListener('click', async () => {
                    if (await api(`/api/friends/${f.id}`, { method: 'DELETE' })) {
                        showToast('Friend request canceled.', 'info');
                        loadFriends();
                    } else {
                        showToast('Could not cancel this request. Please try again.', 'error');
                    }
                });
                outgoingList.appendChild(li);
            });
        }
    }

    function updateFriendsBadge(incomingCount) {
        const badge = $('#friends-requests-badge');
        if (badge) badge.textContent = incomingCount;

        const dot = $('#friends-badge-dot');
        if (dot) dot.classList.toggle('hidden', incomingCount === 0);

        const navBtn = $('#btn-open-friends');
        if (navBtn) navBtn.classList.toggle('has-alert', incomingCount > 0);
    }

    async function refreshFriendsBadgeCount() {
        const incoming = await api('/api/friends/requests/incoming');
        updateFriendsBadge((incoming || []).length);
    }

    function handleFriendEvent(evt) {
        // Any friend event may change our pending-request count and lists.
        refreshFriendsBadgeCount();

        const overlay = $('#friends-overlay');
        if (overlay && !overlay.classList.contains('hidden')) {
            loadFriends();
        }

        // Surface the events that need the user's attention; stay quiet on the rest
        // (declines/removals are handled silently elsewhere, same as most chat apps).
        if (evt.type === 'REQUEST_RECEIVED') {
            const name = evt.friendship?.requester?.displayName || 'Someone';
            showToast(`${name} sent you a friend request.`, 'info');
        } else if (evt.type === 'REQUEST_ACCEPTED') {
            const name = evt.friendship?.addressee?.displayName || 'Someone';
            showToast(`${name} accepted your friend request!`, 'success');
        }
    }

    // ── Presence (online/offline) ──
    function presenceDotClass(conv) {
        if (conv.type !== 'SINGLE') return 'dot--offline';
        if (conv.otherUserStatus === 'ONLINE') return 'dot--online';
        if (conv.otherUserStatus === 'AWAY') return 'dot--away';
        return 'dot--offline';
    }

    function updateChatHeaderStatus(conv) {
        const dot = $('#chat-status-dot');
        const text = $('#chat-status-text');
        if (!dot || !text) return;

        if (conv.type === 'GROUP') {
            dot.className = 'dot dot--online';
            text.textContent = 'Group Chat';
            return;
        }

        const labels = { ONLINE: 'Online', AWAY: 'Away', OFFLINE: 'Offline' };
        dot.className = 'dot ' + presenceDotClass(conv);
        text.textContent = labels[conv.otherUserStatus] || 'Offline';
    }

    function handlePresenceEvent(evt) {
        const conv = conversations.find(c => c.otherUserId === evt.userId);
        if (!conv) return;

        conv.otherUserStatus = evt.status;
        renderConversations();

        if (activeConversationId === conv.id) {
            updateChatHeaderStatus(conv);
        }
    }

    // ── Empty-State Guidance ──
    function updateEmptyState() {
        const title = $('#chat-empty-title');
        const subtitle = $('#chat-empty-subtitle');
        const cta = $('#btn-empty-find-people');
        if (!title || !subtitle || !cta) return;

        if (conversations.length === 0) {
            title.textContent = 'Welcome to ChatBox!';
            subtitle.textContent = "You don't have any conversations yet. Search a teammate's name to message them or send a friend request.";
            cta.classList.remove('hidden');
        } else {
            title.textContent = 'Select a Conversation';
            subtitle.textContent = 'Choose a chat from the left, or search to start a new one.';
            cta.classList.add('hidden');
        }
    }

    function focusSearch() {
        const input = $('#search-input');
        if (input) input.focus();
    }

    function setupEmptyStateCta() {
        const btn = $('#btn-empty-find-people');
        if (btn) btn.addEventListener('click', focusSearch);
    }

    // ── Toast Notifications (replaces native alert()) ──
    const TOAST_ICONS = {
        success: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6L9 17l-5-5"/></svg>',
        error: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>',
        info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>'
    };

    function showToast(message, type = 'info') {
        const stack = $('#toast-stack');
        if (!stack) return;

        const DURATION = 4000;

        const toast = document.createElement('div');
        toast.className = `toast toast--${type}`;
        toast.innerHTML = `
            <span class="toast__icon">${TOAST_ICONS[type] || TOAST_ICONS.info}</span>
            <span class="toast__message">${escapeHtml(message)}</span>
            <button class="toast__close" aria-label="Dismiss">&times;</button>
            <span class="toast__progress"></span>
        `;

        const dismiss = () => {
            toast.classList.add('is-leaving');
            setTimeout(() => toast.remove(), 280);
        };

        // Track real remaining time so a hover-pause resumes in sync with the
        // progress bar (which pauses via CSS) instead of just restarting a flat timer.
        let remaining = DURATION;
        let startedAt = Date.now();
        let dismissTimer = setTimeout(dismiss, remaining);

        toast.addEventListener('mouseenter', () => {
            clearTimeout(dismissTimer);
            remaining -= (Date.now() - startedAt);
            toast.classList.add('is-paused');
        });
        toast.addEventListener('mouseleave', () => {
            toast.classList.remove('is-paused');
            startedAt = Date.now();
            dismissTimer = setTimeout(dismiss, Math.max(remaining, 300));
        });

        toast.querySelector('.toast__close').addEventListener('click', dismiss);
        stack.appendChild(toast);
    }

    // ── Formatting Utilities ──
    function formatTime(dateStr) {
        if (!dateStr) return '';
        const d = new Date(dateStr);
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    function formatFileSize(bytes) {
        if (!bytes) return '0 B';
        const u = ['B', 'KB', 'MB', 'GB'];
        let i = 0, s = bytes;
        while (s >= 1024 && i < u.length - 1) {
            s /= 1024; i++;
        }
        return s.toFixed(1) + ' ' + u[i];
    }

    function isMeetingUrl(text) {
        return text.includes('meet.google.com') || text.includes('zoom.us/j') || text.includes('teams.microsoft.com');
    }

    function groupReactions(reactions) {
        const grouped = {};
        reactions.forEach(r => {
            if (!grouped[r.emoji]) grouped[r.emoji] = [];
            grouped[r.emoji].push(r.userId);
        });
        return grouped;
    }

    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    function scrollToBottom() {
        const container = $('#chat-messages');
        if (container) {
            container.scrollTop = container.scrollHeight;
        }
    }

    function setupAdminTabs() {
        const tabs = $$('.admin-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                tabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');

                const targetTab = tab.dataset.tab;
                $$('.admin-tab-content').forEach(content => {
                    content.classList.toggle('active', content.id === `tab-${targetTab}`);
                });
            });
        });
    }

    return { init };
})();

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    ChatAppController.init();
});
