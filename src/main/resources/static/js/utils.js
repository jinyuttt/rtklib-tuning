(function (global) {
    'use strict';

    const API_BASE = '';

    async function api(path, opts = {}) {
        const url = API_BASE + path;
        const cfg = Object.assign({}, opts);
        if (cfg.body && !(cfg.body instanceof FormData) && typeof cfg.body !== 'string') {
            cfg.headers = Object.assign({ 'Content-Type': 'application/json' }, cfg.headers || {});
            cfg.body = JSON.stringify(cfg.body);
        }
        let res;
        try { res = await fetch(url, cfg); }
        catch (e) { throw new Error('网络错误: ' + e.message); }
        let json;
        try { json = await res.json(); }
        catch (e) { throw new Error('响应非 JSON (HTTP ' + res.status + ')'); }
        if (!res.ok || json.ok === false || json.success === false) throw new Error(json.error || ('HTTP ' + res.status));
        return json.data !== undefined ? json.data : json;
    }

    function upload(file, onProgress) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            const fd = new FormData();
            fd.append('file', file);
            xhr.upload.onprogress = e => {
                if (e.lengthComputable && onProgress) onProgress(e.loaded / e.total);
            };
            xhr.onload = () => {
                let json;
                try { json = JSON.parse(xhr.responseText); }
                catch (e) { return reject(new Error('响应非 JSON')); }
                if (xhr.status === 200 && json.ok !== false) {
                    resolve(json.data !== undefined ? json.data : json);
                } else {
                    reject(new Error(json.error || ('HTTP ' + xhr.status)));
                }
            };
            xhr.onerror = () => reject(new Error('网络错误'));
            xhr.open('POST', API_BASE + '/api/solve/upload');
            xhr.send(fd);
        });
    }

    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function formatBytes(n) {
        if (n == null) return '--';
        if (n < 1024) return n + ' B';
        if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
        if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
        return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB';
    }

    function formatTime(ts) {
        const d = new Date(ts);
        const p = n => String(n).padStart(2, '0');
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }

    const FIX_STATE = { FIX: 1, FLOAT: 2, SBAS: 3, DGPS: 4, SINGLE: 5, PPP: 6 };
    function fixModeToState(q) {
        if (q === 1) return 'fix';
        if (q === 2) return 'float';
        return 'single';
    }
    const STATE_COLOR = { fix: '#22c55e', float: '#f59e0b', single: '#ef4444' };

    function toast(msg, type) {
        let el = document.getElementById('toast');
        if (!el) {
            el = document.createElement('div');
            el.id = 'toast';
            el.className = 'toast hidden';
            document.body.appendChild(el);
        }
        el.textContent = msg;
        el.className = 'toast ' + (type || '');
        clearTimeout(el._t);
        el._t = setTimeout(() => el.classList.add('hidden'), 2800);
    }

    global.U = { api, upload, esc, formatBytes, formatTime, fixModeToState, FIX_STATE, STATE_COLOR, toast };
})(window);