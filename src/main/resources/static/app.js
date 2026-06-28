// SuperBizAgent - Modern Frontend Application
const SUPPORTED_UPLOAD_EXTENSIONS = [
    '.txt', '.md', '.markdown', '.log',
    '.json', '.yaml', '.yml',
    '.java', '.py', '.sql',
    '.pdf', '.docx', '.xlsx', '.pptx',
    '.html', '.htm'
];
const SUPPORTED_UPLOAD_LABEL = 'TXT、Markdown、日志、JSON/YAML、代码、PDF、Word、Excel、PPT、HTML';

class SuperBizAgentApp {
    constructor() {
        this.apiBaseUrl = '/api';
        this.currentMode = 'stream';
        this.userId = this.loadUserId();
        this.sessionId = this.generateSessionId();
        this.isStreaming = false;
        this.currentChatHistory = [];
        this.chatHistories = this.loadChatHistories();
        this.isCurrentChatFromHistory = false;
        this.isDarkMode = this.loadThemePreference();

        this.initElements();
        this.bindEvents();
        this.initMarkdown();
        this.applyTheme();
        this.applyUploadSupport();
        this.renderChatHistory();
        this.updateUI();
    }

    // ==================== Initialization ====================

    initElements() {
        this.sidebar = document.getElementById('sidebar');
        this.sidebarCollapseBtn = document.getElementById('sidebarCollapseBtn');
        this.sidebarExpandBtn = document.getElementById('sidebarExpandBtn');
        this.newChatBtn = document.getElementById('newChatBtn');
        this.aiOpsBtn = document.getElementById('aiOpsBtn');
        this.themeToggle = document.getElementById('themeToggle');
        this.themeIcon = document.getElementById('themeIcon');

        this.chatContainer = document.getElementById('chatContainer');
        this.welcomeScreen = document.getElementById('welcomeScreen');
        this.chatMessages = document.getElementById('chatMessages');

        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.uploadBtn = document.getElementById('uploadBtn');
        this.fileInput = document.getElementById('fileInput');
        this.modeChip = document.getElementById('modeChip');
        this.modeText = document.getElementById('modeText');
        this.modePopup = document.getElementById('modePopup');

        this.chatHistoryList = document.getElementById('chatHistoryList');
        this.toastContainer = document.getElementById('toastContainer');
    }

    bindEvents() {
        // Sidebar
        this.sidebarCollapseBtn.addEventListener('click', () => this.toggleSidebar());
        this.sidebarExpandBtn.addEventListener('click', () => this.toggleSidebar());
        this.newChatBtn.addEventListener('click', () => this.newChat());
        this.themeToggle.addEventListener('click', () => this.toggleTheme());

        // Top bar
        this.aiOpsBtn.addEventListener('click', () => this.triggerAIOps());

        // Input
        this.sendButton.addEventListener('click', () => this.sendMessage());
        this.messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });
        this.messageInput.addEventListener('input', () => this.autoResizeTextarea());

        // File upload
        this.uploadBtn.addEventListener('click', () => this.fileInput.click());
        this.fileInput.addEventListener('change', (e) => this.handleFileSelect(e));

        // Mode selector
        this.modeChip.addEventListener('click', (e) => {
            e.stopPropagation();
            this.modeChip.classList.toggle('active');
            this.modePopup.classList.toggle('visible');
        });

        document.querySelectorAll('.mode-option').forEach(opt => {
            opt.addEventListener('click', () => {
                const mode = opt.dataset.mode;
                this.selectMode(mode);
            });
        });

        document.addEventListener('click', (e) => {
            if (!this.modeChip.contains(e.target) && !this.modePopup.contains(e.target)) {
                this.modeChip.classList.remove('active');
                this.modePopup.classList.remove('visible');
            }
        });

        // Welcome cards
        document.querySelectorAll('.welcome-card').forEach(card => {
            card.addEventListener('click', () => {
                const prompt = card.dataset.prompt;
                if (prompt) {
                    this.messageInput.value = prompt;
                    this.sendMessage();
                }
            });
        });
    }

    initMarkdown() {
        const check = () => {
            if (typeof marked !== 'undefined') {
                marked.setOptions({ breaks: true, gfm: true, headerIds: false, mangle: false });
                if (typeof hljs !== 'undefined') {
                    marked.setOptions({
                        highlight: (code, lang) => {
                            if (lang && hljs.getLanguage(lang)) {
                                try { return hljs.highlight(code, { language: lang }).value; } catch (e) {}
                            }
                            return code;
                        }
                    });
                }
            } else {
                setTimeout(check, 100);
            }
        };
        check();
    }

    // ==================== Theme ====================

    loadThemePreference() {
        return localStorage.getItem('theme') === 'dark';
    }

    loadUserId() {
        const existing = localStorage.getItem('superBizUserId');
        if (existing) return existing;
        const created = 'web_' + Math.random().toString(36).substring(2, 10) + '_' + Date.now();
        localStorage.setItem('superBizUserId', created);
        return created;
    }

    headers(extra = {}) {
        return { ...extra, 'X-User-Id': this.userId };
    }

    applyTheme() {
        document.documentElement.setAttribute('data-theme', this.isDarkMode ? 'dark' : 'light');
        if (this.themeIcon) {
            this.themeIcon.className = this.isDarkMode ? 'ri-sun-line' : 'ri-moon-line';
        }
        const label = this.themeToggle?.querySelector('span');
        if (label) label.textContent = this.isDarkMode ? '浅色模式' : '深色模式';
    }

    toggleTheme() {
        this.isDarkMode = !this.isDarkMode;
        localStorage.setItem('theme', this.isDarkMode ? 'dark' : 'light');
        this.applyTheme();
    }

    // ==================== Sidebar ====================

    toggleSidebar() {
        this.sidebar.classList.toggle('collapsed');
    }

    // ==================== Mode ====================

    selectMode(mode) {
        this.currentMode = mode;
        this.modeText.textContent = mode === 'stream' ? '流式' : '快速';
        document.querySelectorAll('.mode-option').forEach(opt => {
            opt.classList.toggle('active', opt.dataset.mode === mode);
        });
        this.modeChip.classList.remove('active');
        this.modePopup.classList.remove('visible');
        this.showToast(mode === 'stream' ? '已切换到流式模式' : '已切换到快速模式', 'info');
    }

    // ==================== UI State ====================

    updateUI() {
        if (this.sendButton) this.sendButton.disabled = this.isStreaming;
        if (this.messageInput) this.messageInput.disabled = this.isStreaming;
    }

    showWelcome() {
        this.welcomeScreen.classList.remove('hidden');
        this.chatMessages.classList.add('hidden');
    }

    hideWelcome() {
        this.welcomeScreen.classList.add('hidden');
        this.chatMessages.classList.remove('hidden');
    }

    autoResizeTextarea() {
        const ta = this.messageInput;
        ta.style.height = 'auto';
        ta.style.height = Math.min(ta.scrollHeight, 150) + 'px';
    }

    scrollToBottom() {
        this.chatContainer.scrollTop = this.chatContainer.scrollHeight;
    }

    // ==================== Messages ====================

    addMessage(type, content, isStreaming = false, saveToHistory = true) {
        this.hideWelcome();

        if (!isStreaming && saveToHistory && content) {
            this.currentChatHistory.push({ type, content, timestamp: new Date().toISOString() });
        }

        const msgDiv = document.createElement('div');
        msgDiv.className = `message ${type}${isStreaming ? ' streaming' : ''}`;

        // Avatar
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.innerHTML = type === 'assistant'
            ? '<i class="ri-robot-2-line"></i>'
            : '<i class="ri-user-3-line"></i>';
        msgDiv.appendChild(avatar);

        // Body
        const body = document.createElement('div');
        body.className = 'message-body';

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        if (type === 'assistant' && !isStreaming && content) {
            contentDiv.innerHTML = this.renderMarkdown(content);
            this.highlightCode(contentDiv);
        } else {
            contentDiv.textContent = content || '';
        }

        body.appendChild(contentDiv);
        msgDiv.appendChild(body);
        this.chatMessages.appendChild(msgDiv);
        this.scrollToBottom();

        return msgDiv;
    }

    addLoadingMessage() {
        this.hideWelcome();

        const msgDiv = document.createElement('div');
        msgDiv.className = 'message assistant';

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.innerHTML = '<i class="ri-robot-2-line"></i>';
        msgDiv.appendChild(avatar);

        const body = document.createElement('div');
        body.className = 'message-body';

        const loading = document.createElement('div');
        loading.className = 'loading-indicator';
        loading.innerHTML = `
            <div class="loading-dots"><span></span><span></span><span></span></div>
            <span class="loading-text">思考中...</span>
        `;
        body.appendChild(loading);
        msgDiv.appendChild(body);
        this.chatMessages.appendChild(msgDiv);
        this.scrollToBottom();

        return msgDiv;
    }

    // ==================== Send Message ====================

    async sendMessage() {
        const message = this.messageInput.value.trim();
        if (!message || this.isStreaming) return;

        this.addMessage('user', message);
        this.messageInput.value = '';
        this.messageInput.style.height = 'auto';
        this.isStreaming = true;
        this.updateUI();

        try {
            if (this.currentMode === 'quick') {
                await this.sendQuickMessage(message);
            } else {
                await this.sendStreamMessage(message);
            }
        } catch (error) {
            console.error('发送失败:', error);
            this.addMessage('assistant', '抱歉，请求出现错误：' + error.message);
        } finally {
            this.isStreaming = false;
            this.updateUI();
            if (this.isCurrentChatFromHistory) {
                this.updateCurrentChatHistory();
            }
            this.renderChatHistory();
        }
    }

    async sendQuickMessage(message) {
        const loadingEl = this.addLoadingMessage();

        try {
            const res = await fetch(`${this.apiBaseUrl}/chat`, {
                method: 'POST',
                headers: this.headers({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ Id: this.sessionId, UserId: this.userId, Question: message })
            });

            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();

            loadingEl.remove();

            if (data.code === 200 && data.data?.success) {
                this.addMessage('assistant', data.data.answer || '（无回复）');
            } else {
                throw new Error(data.data?.errorMessage || data.message || '请求失败');
            }
        } catch (e) {
            loadingEl.remove();
            throw e;
        }
    }

    async sendStreamMessage(message) {
        const res = await fetch(`${this.apiBaseUrl}/chat_stream`, {
            method: 'POST',
            headers: this.headers({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ Id: this.sessionId, UserId: this.userId, Question: message })
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const msgEl = this.addMessage('assistant', '', true);
        const contentDiv = msgEl.querySelector('.message-content');
        let fullResponse = '';

        await this.processSSEStream(res, (chunk) => {
            fullResponse += chunk;
            contentDiv.innerHTML = this.renderMarkdown(fullResponse);
            this.highlightCode(contentDiv);
            this.scrollToBottom();
        });

        msgEl.classList.remove('streaming');
        contentDiv.innerHTML = this.renderMarkdown(fullResponse);
        this.highlightCode(contentDiv);

        if (fullResponse) {
            this.currentChatHistory.push({
                type: 'assistant', content: fullResponse, timestamp: new Date().toISOString()
            });
        }
    }

    // ==================== AI Ops ====================

    async triggerAIOps() {
        if (this.isStreaming) {
            this.showToast('请等待当前操作完成', 'warning');
            return;
        }

        this.newChat();
        this.hideWelcome();

        const loadingEl = this.addLoadingMessage();
        loadingEl.querySelector('.loading-text').textContent = '正在分析告警...';

        this.isStreaming = true;
        this.updateUI();

        try {
            const res = await fetch(`${this.apiBaseUrl}/ai_ops`, {
                method: 'POST',
                headers: this.headers({ 'Content-Type': 'application/json' })
            });

            if (!res.ok) throw new Error(`HTTP ${res.status}`);

            loadingEl.remove();

            const msgEl = this.addMessage('assistant', '', true);
            msgEl.classList.add('aiops');
            const contentDiv = msgEl.querySelector('.message-content');
            let fullResponse = '';

            await this.processSSEStream(res, (chunk) => {
                fullResponse += chunk;
                contentDiv.innerHTML = this.renderMarkdown(fullResponse);
                this.highlightCode(contentDiv);
                this.scrollToBottom();
            });

            msgEl.classList.remove('streaming');
            contentDiv.innerHTML = this.renderMarkdown(fullResponse);
            this.highlightCode(contentDiv);

            if (fullResponse) {
                this.currentChatHistory.push({
                    type: 'assistant', content: fullResponse, timestamp: new Date().toISOString()
                });
            }
        } catch (error) {
            loadingEl.remove();
            this.addMessage('assistant', '智能运维分析失败：' + error.message);
        } finally {
            this.isStreaming = false;
            this.updateUI();
        }
    }

    // ==================== SSE Processing ====================

    async processSSEStream(response, onChunk) {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (!line.trim() || line.startsWith('id:') || line.startsWith('event:')) continue;

                    if (line.startsWith('data:')) {
                        const rawData = line.substring(5).trim();
                        if (rawData === '[DONE]') return;

                        // Try to parse multiple JSON objects in one line
                        const jsonPattern = /\{"type"\s*:\s*"[^"]+"\s*,\s*"data"\s*:\s*(?:"(?:[^"\\]|\\.)*"|null)\}/g;
                        const matches = rawData.match(jsonPattern);

                        if (matches && matches.length > 0) {
                            for (const jsonStr of matches) {
                                try {
                                    const msg = JSON.parse(jsonStr);
                                    if (msg.type === 'content' && msg.data) onChunk(msg.data);
                                    else if (msg.type === 'done') return;
                                    else if (msg.type === 'error') throw new Error(msg.data || '服务端错误');
                                } catch (e) {
                                    if (e.message.includes('服务端')) throw e;
                                }
                            }
                        } else {
                            try {
                                const msg = JSON.parse(rawData);
                                if (msg.type === 'content' && msg.data) onChunk(msg.data);
                                else if (msg.type === 'done') return;
                                else if (msg.type === 'error') throw new Error(msg.data || '服务端错误');
                            } catch (e) {
                                if (e.message.includes('服务端')) throw e;
                                // Non-JSON data, treat as raw content
                                if (rawData) onChunk(rawData);
                            }
                        }
                    }
                }
            }
        } finally {
            reader.releaseLock();
        }
    }

    // ==================== File Upload ====================

    applyUploadSupport() {
        if (this.fileInput) {
            this.fileInput.accept = SUPPORTED_UPLOAD_EXTENSIONS.join(',');
        }
        if (this.uploadBtn) {
            this.uploadBtn.title = `上传文件到知识库，支持 ${SUPPORTED_UPLOAD_LABEL}`;
        }
    }

    handleFileSelect(event) {
        const file = event.target.files[0];
        if (!file) return;

        const ext = '.' + file.name.split('.').pop().toLowerCase();
        if (!SUPPORTED_UPLOAD_EXTENSIONS.includes(ext)) {
            this.showToast(`仅支持 ${SUPPORTED_UPLOAD_LABEL} 文件`, 'error');
            this.fileInput.value = '';
            return;
        }

        this.uploadFile(file);
    }

    async uploadFile(file) {
        if (file.size > 50 * 1024 * 1024) {
            this.showToast('文件大小不能超过 50MB', 'error');
            return;
        }

        this.isStreaming = true;
        this.updateUI();
        this.showToast(`正在上传 ${file.name}...`, 'info');

        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('sessionId', this.sessionId);
            formData.append('namespace', 'default');

            const res = await fetch(`${this.apiBaseUrl}/upload`, {
                method: 'POST',
                headers: this.headers(),
                body: formData
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);

            const data = await res.json();
            if (data.code === 200 || data.message === 'success') {
                this.showToast(`${file.name} 上传成功`, 'success');
                this.addMessage('assistant', `📄 **${file.name}** 已成功上传到知识库，现在可以基于该文档进行问答了。`);
            } else {
                throw new Error(data.message || '上传失败');
            }
        } catch (e) {
            this.showToast('上传失败: ' + e.message, 'error');
        } finally {
            this.fileInput.value = '';
            this.isStreaming = false;
            this.updateUI();
        }
    }

    // ==================== Chat History ====================

    newChat() {
        if (this.isStreaming) {
            this.showToast('请等待当前操作完成', 'warning');
            return;
        }

        if (this.currentChatHistory.length > 0) {
            this.isCurrentChatFromHistory ? this.updateCurrentChatHistory() : this.saveCurrentChat();
        }

        this.currentChatHistory = [];
        this.isCurrentChatFromHistory = false;
        this.sessionId = this.generateSessionId();
        this.chatMessages.innerHTML = '';
        this.showWelcome();
        this.renderChatHistory();
    }

    saveCurrentChat() {
        if (!this.currentChatHistory.length) return;
        if (this.chatHistories.find(h => h.id === this.sessionId)) {
            this.updateCurrentChatHistory();
            return;
        }

        const firstUser = this.currentChatHistory.find(m => m.type === 'user');
        const title = firstUser
            ? firstUser.content.substring(0, 30) + (firstUser.content.length > 30 ? '...' : '')
            : '新对话';

        this.chatHistories.unshift({
            id: this.sessionId, title, messages: [...this.currentChatHistory],
            createdAt: new Date().toISOString(), updatedAt: new Date().toISOString()
        });

        if (this.chatHistories.length > 50) this.chatHistories = this.chatHistories.slice(0, 50);
        this.saveChatHistories();
    }

    updateCurrentChatHistory() {
        const idx = this.chatHistories.findIndex(h => h.id === this.sessionId);
        if (idx === -1) { this.saveCurrentChat(); return; }

        this.chatHistories[idx].messages = [...this.currentChatHistory];
        this.chatHistories[idx].updatedAt = new Date().toISOString();
        this.saveChatHistories();
    }

    loadChatHistories() {
        try { return JSON.parse(localStorage.getItem('chatHistories') || '[]'); }
        catch { return []; }
    }

    saveChatHistories() {
        try { localStorage.setItem('chatHistories', JSON.stringify(this.chatHistories)); }
        catch (e) { console.error('保存历史失败:', e); }
    }

    renderChatHistory() {
        this.chatHistoryList.innerHTML = '';
        this.chatHistories.forEach(history => {
            const item = document.createElement('div');
            item.className = `history-item${history.id === this.sessionId ? ' active' : ''}`;

            item.innerHTML = `
                <div class="history-item-content">
                    <span class="history-item-title">${this.escapeHtml(history.title)}</span>
                </div>
                <button class="history-item-delete" title="删除">
                    <i class="ri-close-line"></i>
                </button>
            `;

            item.addEventListener('click', (e) => {
                if (!e.target.closest('.history-item-delete')) this.loadChatHistory(history.id);
            });

            item.querySelector('.history-item-delete').addEventListener('click', (e) => {
                e.stopPropagation();
                this.deleteChatHistory(history.id);
            });

            this.chatHistoryList.appendChild(item);
        });
    }

    loadChatHistory(historyId) {
        const history = this.chatHistories.find(h => h.id === historyId);
        if (!history) return;

        if (this.currentChatHistory.length > 0 && this.sessionId !== historyId) {
            this.isCurrentChatFromHistory ? this.updateCurrentChatHistory() : this.saveCurrentChat();
        }

        this.sessionId = history.id;
        this.currentChatHistory = [...history.messages];
        this.isCurrentChatFromHistory = true;

        this.chatMessages.innerHTML = '';
        this.hideWelcome();
        history.messages.forEach(msg => this.addMessage(msg.type, msg.content, false, false));
        this.renderChatHistory();
    }

    deleteChatHistory(historyId) {
        this.chatHistories = this.chatHistories.filter(h => h.id !== historyId);
        this.saveChatHistories();
        this.renderChatHistory();

        if (this.sessionId === historyId) {
            this.currentChatHistory = [];
            this.chatMessages.innerHTML = '';
            this.sessionId = this.generateSessionId();
            this.showWelcome();
        }
    }

    // ==================== Utilities ====================

    generateSessionId() {
        return 'sess_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now();
    }

    renderMarkdown(content) {
        if (!content) return '';
        if (typeof marked === 'undefined') return this.escapeHtml(content);
        try { return marked.parse(content); }
        catch { return this.escapeHtml(content); }
    }

    highlightCode(container) {
        if (typeof hljs !== 'undefined' && container) {
            container.querySelectorAll('pre code').forEach(block => {
                if (!block.classList.contains('hljs')) hljs.highlightElement(block);
            });
        }
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    showToast(message, type = 'info') {
        const icons = { info: 'ri-information-line', success: 'ri-check-line', warning: 'ri-alert-line', error: 'ri-error-warning-line' };
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.innerHTML = `<i class="${icons[type] || icons.info}"></i><span>${message}</span>`;
        this.toastContainer.appendChild(toast);

        setTimeout(() => {
            toast.classList.add('removing');
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }
}

// Initialize
document.addEventListener('DOMContentLoaded', () => new SuperBizAgentApp());
