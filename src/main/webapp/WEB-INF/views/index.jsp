<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="ChatBox_TheStars — Internal Web Chat for IT, Marketing & Graphic Design teams">
    <title>ChatBox TheStars</title>

    <!-- Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">

    <!-- Stylesheet -->
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

    <!-- ═══════════════════════════════════════════════════════════
         ANIMATED BACKGROUND (gradient mesh behind glass panels)
         ═══════════════════════════════════════════════════════════ -->
    <div class="bg-gradient" aria-hidden="true">
        <div class="bg-orb bg-orb--1"></div>
        <div class="bg-orb bg-orb--2"></div>
        <div class="bg-orb bg-orb--3"></div>
        <div class="bg-orb bg-orb--4"></div>
    </div>

    <!-- ═══════════════════════════════════════════════════════════
         SCREEN 2: MAIN DASHBOARD (3-Column Chat Layout)
         ═══════════════════════════════════════════════════════════ -->
    <section id="screen-dashboard" class="screen screen--dashboard active">
        <div class="dashboard">

            <!-- ──────── COL 1: SIDEBAR LEFT ──────── -->
            <aside class="col-sidebar glass-panel" id="col-sidebar">
                <!-- User Profile Header -->
                <header class="sidebar-header">
                    <div class="user-profile" id="user-profile-trigger">
                        <img src="https://ui-avatars.com/api/?name=Khoa+N&background=3b82f6&color=fff&size=40&bold=true&format=svg"
                             alt="Avatar" class="avatar avatar--md" id="sidebar-user-avatar">
                        <div class="user-info">
                            <span class="user-name" id="sidebar-user-name">Khoa Nguyen</span>
                            <span class="user-status"><i class="dot dot--offline" id="self-status-dot"></i> <span id="self-status-text">Connecting...</span></span>
                        </div>
                    </div>
                    <div style="display: flex; gap: 6px; align-items: center;">
                        <button class="nav-pill" id="btn-open-friends" title="View friends and requests">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>
                            <span class="nav-pill__label">Friends</span>
                            <span class="badge-dot hidden" id="friends-badge-dot"></span>
                        </button>
                        <button class="icon-btn" id="btn-open-admin" title="Admin Panel">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
                        </button>
                        <button class="icon-btn" id="btn-logout" title="Logout" style="color:var(--red)">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                        </button>
                    </div>
                </header>

                <!-- Search -->
                <div class="search-box glass-inset">
                    <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                    <input type="text" placeholder="Search chats or find people..." id="search-input" autocomplete="off">
                </div>

                <!-- Global Search Results (Zalo-like find users) -->
                <div id="global-search-results" class="hidden" style="margin: 0 14px 12px; max-height: 240px; overflow-y: auto; background: var(--inset-bg); border: 1px solid var(--inset-border); border-radius: var(--r-sm); padding: 10px;">
                    <p class="search-hint">Click a name to start chatting, or tap <strong>Add Friend</strong> to connect first.</p>
                    <ul id="global-search-list" style="list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px;">
                        <!-- Results dynamically loaded -->
                    </ul>
                </div>

                <!-- Group Actions -->
                <div style="display: flex; gap: 8px; margin: 0 14px 12px;">
                    <button class="btn-new-group" id="btn-new-group" style="margin:0; flex: 1; padding: 10px; font-size: 12px; gap: 6px;">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                        New Group
                    </button>
                    <button class="btn-ghost" id="btn-browse-groups" style="padding: 10px; border-radius: var(--r-sm); font-size: 12px; font-weight: 600; display: flex; align-items: center; justify-content: center; gap: 6px; border-color: var(--glass-border);">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                        Join Group
                    </button>
                </div>

                <!-- Chat List -->
                <nav class="chat-list-wrapper" id="chat-list-wrapper">
                    <div class="chat-section" id="section-new">
                        <h3 class="chat-section__title">New Messages</h3>
                        <ul class="chat-list" id="chat-list-new"></ul>
                    </div>
                    <div class="chat-section" id="section-last">
                        <h3 class="chat-section__title">Last Messages</h3>
                        <ul class="chat-list" id="chat-list-last"></ul>
                    </div>
                </nav>
            </aside>

            <!-- ──────── COL 2: CHAT AREA ──────── -->
            <main class="col-chat" id="col-chat">
                <!-- Empty State -->
                <div class="chat-empty" id="chat-empty">
                    <div class="chat-empty__icon">
                        <svg viewBox="0 0 100 100" fill="none">
                            <circle cx="50" cy="50" r="42" stroke="url(#emptyG)" stroke-width="1.5" opacity="0.25"/>
                            <path d="M65 58l-8-8H33a4 4 0 01-4-4V30a4 4 0 014-4h34a4 4 0 014 4v16a4 4 0 01-4 4h-2v8z" stroke="url(#emptyG)" stroke-width="2"/>
                            <line x1="38" y1="36" x2="62" y2="36" stroke="url(#emptyG)" stroke-width="2" stroke-linecap="round" opacity="0.4"/>
                            <line x1="38" y1="42" x2="54" y2="42" stroke="url(#emptyG)" stroke-width="2" stroke-linecap="round" opacity="0.3"/>
                            <defs><linearGradient id="emptyG" x1="10" y1="10" x2="90" y2="90"><stop stop-color="#60a5fa"/><stop offset="1" stop-color="#a78bfa"/></linearGradient></defs>
                        </svg>
                    </div>
                    <h2 id="chat-empty-title">Select a Conversation</h2>
                    <p id="chat-empty-subtitle">Choose a chat from the left or start a new one</p>
                    <button class="btn-primary btn-sm chat-empty__cta hidden" id="btn-empty-find-people">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                        Find people to chat with
                    </button>
                </div>

                <!-- Chat Header -->
                <header class="chat-header glass-panel-thin hidden" id="chat-header">
                    <div class="chat-header__left">
                        <button class="icon-btn btn-back hidden" id="btn-back-to-sidebar" title="Back to chats" style="margin-right: 8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="20" height="20">
                                <line x1="19" y1="12" x2="5" y2="12"></line>
                                <polyline points="12 19 5 12 12 5"></polyline>
                            </svg>
                        </button>
                        <img src="" alt="" class="avatar avatar--sm" id="chat-avatar">
                        <div>
                            <h2 class="chat-header__name" id="chat-name">...</h2>
                            <span class="chat-header__status"><i class="dot dot--online" id="chat-status-dot"></i> <span id="chat-status-text">Online</span></span>
                        </div>
                    </div>
                    <div class="chat-header__actions">
                        <button class="icon-btn" title="Voice Call"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07 19.5 19.5 0 01-6-6 19.79 19.79 0 01-3.07-8.67A2 2 0 014.11 2h3a2 2 0 012 1.72 12.84 12.84 0 00.7 2.81 2 2 0 01-.45 2.11L8.09 9.91a16 16 0 006 6l1.27-1.27a2 2 0 012.11-.45 12.84 12.84 0 002.81.7A2 2 0 0122 16.92z"/></svg></button>
                        <button class="icon-btn" title="Video Call"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2"/></svg></button>
                        <button class="icon-btn" title="Toggle Resources" id="btn-toggle-col3"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg></button>
                    </div>
                </header>

                <!-- Messages Body -->
                <div class="chat-messages hidden" id="chat-messages">
                    <div class="messages-scroll" id="messages-scroll"></div>
                    <div class="typing-bar hidden" id="typing-bar">
                        <span class="typing-dots"><span></span><span></span><span></span></span>
                        <span id="typing-who">Someone is typing...</span>
                    </div>
                </div>

                <!-- Message Input Footer -->
                <footer class="chat-footer glass-panel-thin hidden" id="chat-footer">
                    <button class="icon-btn icon-btn--circle" id="btn-attach" title="Attach File">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                    </button>
                    <div class="input-glass glass-inset">
                        <input type="text" id="msg-input" placeholder="Type something..." autocomplete="off">
                    </div>
                    <button class="btn-send" id="btn-send" title="Send">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                    </button>
                </footer>
            </main>

            <!-- ──────── COL 3: RESOURCES SIDEBAR ──────── -->
            <aside class="col-resources glass-panel hidden" id="col-resources">
                <header class="resources-header">
                    <h3>Resources</h3>
                    <button class="icon-btn icon-btn--sm" id="btn-close-col3" title="Close">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                    </button>
                </header>
                <section class="resource-section">
                    <h4 class="resource-section__title">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="16" height="16"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
                        Images <span class="badge" id="badge-images">6</span>
                    </h4>
                    <div class="images-grid" id="images-grid"></div>
                </section>
                <section class="resource-section">
                    <h4 class="resource-section__title">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="16" height="16"><path d="M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z"/><polyline points="13 2 13 9 20 9"/></svg>
                        Shared Files <span class="badge" id="badge-files">4</span>
                    </h4>
                    <ul class="files-list" id="files-list"></ul>
                </section>
            </aside>
        </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════
         SCREEN 3: ADMIN PANEL (Slide-over)
         ═══════════════════════════════════════════════════════════ -->
    <div class="admin-overlay hidden" id="admin-overlay">
        <div class="admin-panel glass-panel" id="admin-panel">
            <header class="admin-header">
                <h2>Admin Panel</h2>
                <button class="icon-btn" id="btn-close-admin"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
            </header>
            <nav class="admin-tabs">
                <button class="admin-tab active" data-tab="users"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="16" height="16"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg> Users</button>
                <button class="admin-tab" data-tab="groups"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="16" height="16"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg> Groups</button>
                <button class="admin-tab" data-tab="stats"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="16" height="16"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg> Statistics</button>
            </nav>
            <div class="admin-body">
                <!-- Tab: Users -->
                <div class="admin-tab-content active" id="tab-users">
                    <div class="admin-toolbar">
                        <div class="search-box glass-inset search-box--sm">
                            <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                            <input type="text" placeholder="Search users..." id="admin-user-search">
                        </div>
                    </div>
                    <div class="admin-table-wrapper">
                        <table class="admin-table"><thead><tr><th>User</th><th>Email</th><th>Team</th><th>Status</th><th>Action</th></tr></thead>
                            <tbody id="admin-users-tbody"></tbody>
                        </table>
                    </div>
                </div>
                <!-- Tab: Groups -->
                <div class="admin-tab-content" id="tab-groups">
                    <div class="admin-toolbar">
                        <button class="btn-primary btn-sm" id="btn-admin-create-group"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg> New Group</button>
                    </div>
                    <ul class="admin-groups-list" id="admin-groups-list"></ul>
                </div>
                <!-- Tab: Statistics -->
                <div class="admin-tab-content" id="tab-stats">
                    <div class="stats-grid" id="stats-grid"></div>
                </div>
            </div>
        </div>
    </div>

    <!-- ═══════════════════════════════════════════════════════════
         FRIENDS POPOVER (floats near the Friends button, not a full panel)
         ═══════════════════════════════════════════════════════════ -->
    <div class="friends-popover glass-card hidden" id="friends-overlay">
        <header class="friends-popover__header">
            <h2>Friends</h2>
            <button class="icon-btn icon-btn--sm" id="btn-close-friends"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
        </header>
        <nav class="admin-tabs">
            <button class="friends-tab-btn active" data-friends-tab="list">My Friends</button>
            <button class="friends-tab-btn" data-friends-tab="requests">Requests <span class="badge" id="friends-requests-badge">0</span></button>
        </nav>
        <div class="friends-popover__body">
            <!-- Tab: Friends list -->
            <div class="friends-tab-content active" id="friends-tab-list">
                <ul class="friends-list" id="friends-list"></ul>
            </div>
            <!-- Tab: Requests (incoming + outgoing) -->
            <div class="friends-tab-content" id="friends-tab-requests">
                <h4 class="resource-section__title">Incoming</h4>
                <ul class="friends-list" id="friend-requests-incoming"></ul>
                <h4 class="resource-section__title" style="margin-top:16px;">Sent</h4>
                <ul class="friends-list" id="friend-requests-outgoing"></ul>
            </div>
        </div>
    </div>

    <!-- ═══════════════════════════════════════════════════════════
         CREATE GROUP MODAL
         ═══════════════════════════════════════════════════════════ -->
    <div class="modal-overlay hidden" id="modal-create-group">
        <div class="glass-card modal-card">
            <header class="modal-header"><h3>Create New Group</h3>
                <button class="icon-btn icon-btn--sm" id="btn-close-modal"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
            </header>
            <div class="modal-body">
                <label class="form-label">Group Name</label>
                <input type="text" class="form-input glass-inset" id="input-group-name" placeholder="e.g. Design Team 2026" maxlength="70">
                <label class="form-label" style="margin-top:16px">Add Members</label>
                <input type="text" class="form-input glass-inset" id="input-member-search" placeholder="Search by name...">
                <div class="member-results" id="member-results"></div>
                <div class="member-chips" id="member-chips"></div>
            </div>
            <footer class="modal-footer">
                <button class="btn-ghost" id="btn-modal-cancel">Cancel</button>
                <button class="btn-primary" id="btn-modal-create">Create Group</button>
            </footer>
        </div>
    </div>

    <!-- ═══════════════════════════════════════════════════════════
         BROWSE GROUPS MODAL
         ═══════════════════════════════════════════════════════════ -->
    <div class="modal-overlay hidden" id="modal-browse-groups">
        <div class="glass-card modal-card">
            <header class="modal-header">
                <h3>Join Pre-created Groups</h3>
                <button class="icon-btn icon-btn--sm" id="btn-close-browse-modal"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
            </header>
            <div class="modal-body" style="max-height: 320px; overflow-y: auto; padding: 20px 24px;">
                <style>
                    .available-groups-list {
                        display: flex;
                        flex-direction: column;
                        gap: 10px;
                    }
                    .available-group-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 12px;
                        border: 1px solid var(--glass-border);
                        border-radius: var(--r-sm);
                        background: rgba(0, 0, 0, 0.15);
                    }
                    .available-group-item:hover {
                        background: rgba(255, 255, 255, 0.02);
                        border-color: var(--glass-border-light);
                    }
                    .available-group-item .group-info {
                        display: flex;
                        align-items: center;
                        gap: 12px;
                    }
                    .available-group-item .group-avatar {
                        width: 36px;
                        height: 36px;
                        border-radius: var(--r-sm);
                        background: linear-gradient(135deg, var(--neon-glow), var(--purple-glow));
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 16px;
                        font-weight: 600;
                    }
                </style>
                <div id="browse-groups-list" class="available-groups-list">
                    <!-- Loaded dynamically via js -->
                </div>
            </div>
            <footer class="modal-footer">
                <button class="btn-ghost" id="btn-browse-cancel">Close</button>
            </footer>
        </div>
    </div>

    <!-- ═══════════════════════════════════════════════════════════
         TOAST NOTIFICATIONS (replaces native alert())
         ═══════════════════════════════════════════════════════════ -->
    <div class="toast-stack" id="toast-stack" aria-live="polite"></div>

    <!-- ══════ Scripts ══════ -->
    <script src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>
    <script src="${pageContext.request.contextPath}/js/app.js"></script>
</body>
</html>
