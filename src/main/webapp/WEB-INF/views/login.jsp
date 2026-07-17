<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ChatBox TheStars — Portal</title>
    
    <!-- Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    
    <style>
        /* Specific enhancements for creative login portal */
        .portal-container {
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 20px;
            position: relative;
            z-index: 1;
        }
        .portal-card {
            width: 440px;
            max-width: 100%;
            padding: 40px;
            position: relative;
            overflow: hidden;
            transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .portal-tabs {
            display: flex;
            background: var(--inset-bg);
            border: 1px solid var(--inset-border);
            padding: 4px;
            border-radius: 12px;
            margin-bottom: 24px;
        }
        .portal-tab {
            flex: 1;
            padding: 10px;
            text-align: center;
            background: transparent;
            border: none;
            color: var(--text-secondary);
            font-size: 14px;
            font-weight: 600;
            border-radius: 8px;
            transition: all var(--t);
        }
        .portal-tab.active {
            background: var(--neon);
            color: var(--text-white);
            box-shadow: 0 4px 12px var(--neon-glow);
        }
        .form-feedback {
            padding: 10px 12px;
            border-radius: 8px;
            font-size: 13px;
            margin-bottom: 16px;
            display: none;
        }
        .form-feedback--error {
            background: rgba(248, 113, 113, 0.15);
            border: 1px solid var(--red);
            color: #fca5a5;
            display: block;
        }
        .form-feedback--success {
            background: rgba(52, 211, 153, 0.15);
            border: 1px solid var(--green);
            color: #a7f3d0;
            display: block;
        }
        .portal-footer-link {
            text-align: center;
            margin-top: 20px;
            font-size: 13px;
            color: var(--text-secondary);
        }
        .portal-footer-link span {
            color: var(--neon-light);
            cursor: pointer;
            font-weight: 600;
            transition: color var(--t-fast);
        }
        .portal-footer-link span:hover {
            color: var(--neon-bright);
        }
        .select-glass {
            appearance: none;
            -webkit-appearance: none;
            background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='rgba(255,255,255,0.4)' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'></polyline></svg>");
            background-repeat: no-repeat;
            background-position: right 12px center;
            background-size: 16px;
            padding-right: 36px !important;
        }
        .select-glass option {
            background: #0f172a;
            color: #f1f5f9;
        }
        .password-wrapper {
            position: relative;
        }
        .password-wrapper .form-input {
            padding-right: 44px;
        }
        .password-toggle {
            position: absolute;
            right: 14px;
            top: 50%;
            transform: translateY(-50%);
            cursor: pointer;
            color: var(--text-secondary);
            opacity: 0.6;
            font-size: 16px;
            user-select: none;
            transition: opacity var(--t-fast);
            display: flex;
            align-items: center;
        }
        .password-toggle:hover {
            opacity: 1;
        }
    </style>
</head>
<body>

    <!-- Animated Mesh Background -->
    <div class="bg-gradient" aria-hidden="true">
        <div class="bg-orb bg-orb--1"></div>
        <div class="bg-orb bg-orb--2"></div>
        <div class="bg-orb bg-orb--3"></div>
        <div class="bg-orb bg-orb--4"></div>
    </div>

    <div class="portal-container">
        <div class="glass-card portal-card">
            <!-- Logo Header -->
            <div class="login-logo">
                <div class="logo-ring">
                    <svg viewBox="0 0 64 64" fill="none">
                        <circle cx="32" cy="32" r="28" stroke="url(#lGrad)" stroke-width="2.5" opacity="0.8"/>
                        <circle cx="32" cy="32" r="18" stroke="url(#lGrad)" stroke-width="1.5" opacity="0.4"/>
                        <path d="M24 32l6 6 10-12" stroke="url(#lGrad)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
                        <defs><linearGradient id="lGrad" x1="0" y1="0" x2="64" y2="64">
                            <stop stop-color="#60a5fa"/><stop offset="0.5" stop-color="#3b82f6"/><stop offset="1" stop-color="#a78bfa"/>
                        </linearGradient></defs>
                    </svg>
                </div>
                <h1 class="login-title">ChatBox <span class="text-gradient">TheStars</span></h1>
                <p class="login-tagline">Where teams connect, create &amp; collaborate</p>
            </div>
            
            <div class="glass-divider"></div>

            <!-- Tab Switcher -->
            <div class="portal-tabs">
                <button class="portal-tab active" id="tab-btn-login">Sign In</button>
                <button class="portal-tab" id="tab-btn-register">Register</button>
            </div>

            <!-- Form Feedbacks -->
            <div id="error-alert" class="form-feedback form-feedback--error ${param.error != null ? '' : 'hidden'}">
                <c:if test="${param.error != null}">
                    Login failed. Please check your credentials.
                </c:if>
            </div>
            <div id="success-alert" class="form-feedback form-feedback--success hidden"></div>

            <!-- ── SIGN IN FORM ── -->
            <form id="form-login" action="${pageContext.request.contextPath}/login" method="POST">
                <div class="form-group" style="margin-bottom: 16px;">
                    <label class="form-label">Email Address (Gmail)</label>
                    <input type="email" name="email" class="form-input glass-inset" required placeholder="name@thestars.vn" autocomplete="username">
                </div>
                <div class="form-group" style="margin-bottom: 24px;">
                    <label class="form-label">Password</label>
                    <div class="password-wrapper">
                        <input type="password" name="password" class="form-input glass-inset" required placeholder="••••••••" autocomplete="current-password">
                        <span class="password-toggle">👁️</span>
                    </div>
                </div>
                <button type="submit" class="btn-primary" style="width: 100%; padding: 12px;">Sign In</button>
            </form>

            <!-- ── REGISTER FORM ── -->
            <form id="form-register" class="hidden">
                <div class="form-group" style="margin-bottom: 14px;">
                    <label class="form-label">Full Name</label>
                    <input type="text" id="reg-name" class="form-input glass-inset" required placeholder="Nguyen Van A">
                </div>
                <div class="form-group" style="margin-bottom: 14px;">
                    <label class="form-label">Email (Gmail)</label>
                    <input type="email" id="reg-email" class="form-input glass-inset" required placeholder="username@gmail.com">
                </div>
                <div class="form-group" style="margin-bottom: 14px;">
                    <label class="form-label">Team / Department</label>
                    <select id="reg-team" class="form-input glass-inset select-glass" required>
                        <option value="" disabled selected>Select your team</option>
                        <option value="IT">IT Infrastructure & Support</option>
                        <option value="Marketing">Marketing & Communication</option>
                        <option value="Design">Graphic Design</option>
                    </select>
                </div>
                <div class="form-group" style="margin-bottom: 14px;">
                    <label class="form-label">Password</label>
                    <div class="password-wrapper">
                        <input type="password" id="reg-pass" class="form-input glass-inset" required placeholder="••••••••" autocomplete="new-password">
                        <span class="password-toggle">👁️</span>
                    </div>
                </div>
                <div class="form-group" style="margin-bottom: 20px;">
                    <label class="form-label">Confirm Password</label>
                    <div class="password-wrapper">
                        <input type="password" id="reg-confirm" class="form-input glass-inset" required placeholder="••••••••" autocomplete="new-password">
                        <span class="password-toggle">👁️</span>
                    </div>
                </div>
                <button type="submit" class="btn-primary" style="width: 100%; padding: 12px;">Create Account</button>
            </form>

            <!-- Footer switcher links -->
            <div class="portal-footer-link" id="switch-link-container">
                <span id="switch-link-text">Don't have an account? Sign up</span>
            </div>
        </div>
    </div>

    <!-- Script to toggle views and handle registration API -->
    <script>
        const tabLogin = document.getElementById('tab-btn-login');
        const tabRegister = document.getElementById('tab-btn-register');
        const formLogin = document.getElementById('form-login');
        const formRegister = document.getElementById('form-register');
        const errorAlert = document.getElementById('error-alert');
        const successAlert = document.getElementById('success-alert');
        const switchLinkText = document.getElementById('switch-link-text');

        function showLoginView() {
            tabLogin.classList.add('active');
            tabRegister.classList.remove('active');
            formLogin.classList.remove('hidden');
            formRegister.classList.add('hidden');
            switchLinkText.innerHTML = "Don't have an account? <span>Sign up</span>";
            
            // Add listener to span inside
            switchLinkText.querySelector('span').addEventListener('click', showRegisterView);
        }

        function showRegisterView() {
            tabLogin.classList.remove('active');
            tabRegister.classList.add('active');
            formLogin.classList.add('hidden');
            formRegister.classList.remove('hidden');
            errorAlert.classList.add('hidden');
            switchLinkText.innerHTML = "Already have an account? <span>Sign in</span>";
            
            // Add listener to span inside
            switchLinkText.querySelector('span').addEventListener('click', showLoginView);
        }

        tabLogin.addEventListener('click', showLoginView);
        tabRegister.addEventListener('click', showRegisterView);
        
        // Initial setup for link span
        showLoginView();

        // Handle Login Form Submission via fetch
        formLogin.addEventListener('submit', async (e) => {
            e.preventDefault();
            errorAlert.classList.add('hidden');
            successAlert.classList.add('hidden');

            const email = formLogin.querySelector('input[name="email"]').value.trim();
            const password = formLogin.querySelector('input[name="password"]').value;

            try {
                const response = await fetch('${pageContext.request.contextPath}/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email, password })
                });

                const data = await response.json();
                if (response.ok) {
                    sessionStorage.setItem('chat_token', data.token);
                    window.location.href = '${pageContext.request.contextPath}/';
                } else {
                    errorAlert.textContent = data.error || "Login failed. Check your credentials.";
                    errorAlert.classList.remove('hidden');
                }
            } catch (err) {
                errorAlert.textContent = "Unable to connect to the server.";
                errorAlert.classList.remove('hidden');
            }
        });

        // Handle Registration Form Submission via fetch
        formRegister.addEventListener('submit', async (e) => {
            e.preventDefault();
            errorAlert.classList.add('hidden');
            successAlert.classList.add('hidden');

            const displayName = document.getElementById('reg-name').value.trim();
            const email = document.getElementById('reg-email').value.trim();
            const team = document.getElementById('reg-team').value;
            const password = document.getElementById('reg-pass').value;
            const confirm = document.getElementById('reg-confirm').value;

            if (password !== confirm) {
                errorAlert.textContent = "Passwords do not match!";
                errorAlert.classList.remove('hidden');
                return;
            }

            try {
                const response = await fetch('${pageContext.request.contextPath}/api/auth/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ displayName, email, team, password })
                });

                const data = await response.json();
                if (response.ok) {
                    successAlert.textContent = "Registration successful! You can now log in.";
                    successAlert.classList.remove('hidden');
                    formRegister.reset();
                    showLoginView();
                } else {
                    errorAlert.textContent = data.error || "Registration failed. Try again.";
                    errorAlert.classList.remove('hidden');
                }
            } catch (err) {
                errorAlert.textContent = "Unable to connect to the server.";
                errorAlert.classList.remove('hidden');
            }
        });

        // Toggle password visibility
        document.querySelectorAll('.password-toggle').forEach(toggle => {
            toggle.addEventListener('click', () => {
                const input = toggle.previousElementSibling;
                if (input.type === 'password') {
                    input.type = 'text';
                    toggle.textContent = '🙈';
                } else {
                    input.type = 'password';
                    toggle.textContent = '👁️';
                }
            });
        });
    </script>
</body>
</html>
