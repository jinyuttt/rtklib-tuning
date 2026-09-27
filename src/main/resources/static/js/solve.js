const Solve = {
    mode: 'kinematic',
    fileCache: {},
    paramState: null,
    polling: null,
    lastLogIdx: 0,
    lastEpochIdx: 0,
    allEpochs: [],
    allLogs: [],
    running: false,

    MODES: [
        { key: 'kinematic',     label: 'RTK-Kinematic' },
        { key: 'static',        label: 'RTK-Static' },
        { key: 'spp',           label: 'SPP' },
        { key: 'dgps',          label: 'DGPS' },
        { key: 'ppp-kinematic', label: 'PPP-Kinematic' },
        { key: 'ppp-static',    label: 'PPP-Static' }
    ],

    MODE_FILES: {
        kinematic: [
            { key: 'base',  label: '基站', required: true,  accept: '.rtcm3,.rtcm,.obs' },
            { key: 'rover', label: '测站', required: true,  accept: '.rtcm3,.rtcm,.obs' }
        ],
        static: [
            { key: 'base',  label: '基站', required: true,  accept: '.rtcm3,.rtcm,.obs' },
            { key: 'rover', label: '测站', required: true,  accept: '.rtcm3,.rtcm,.obs' }
        ],
        spp: [
            { key: 'rover', label: '观测', required: true,  accept: '.obs,.rtcm3,.rtcm' },
            { key: 'nav',   label: '导航', required: false, accept: '.nav,.n,.23n,.24n,.25n' }
        ],
        dgps: [
            { key: 'base',  label: '基站', required: true,  accept: '.rtcm3,.rtcm,.obs' },
            { key: 'rover', label: '测站', required: true,  accept: '.rtcm3,.rtcm,.obs' }
        ],
        'ppp-kinematic': [
            { key: 'rover', label: '观测', required: true,  accept: '.obs' },
            { key: 'nav',   label: '导航', required: false, accept: '.nav,.n,.23n,.24n,.25n' },
            { key: 'sp3',   label: 'SP3',  required: false, accept: '.sp3' },
            { key: 'clk',   label: 'CLK',  required: false, accept: '.clk' }
        ],
        'ppp-static': [
            { key: 'rover', label: '观测', required: true,  accept: '.obs' },
            { key: 'nav',   label: '导航', required: false, accept: '.nav,.n,.23n,.24n,.25n' },
            { key: 'sp3',   label: 'SP3',  required: false, accept: '.sp3' },
            { key: 'clk',   label: 'CLK',  required: false, accept: '.clk' }
        ]
    },

    PARAM_DEFS: [
        { key: 'systems', type: 'checkgroup', label: '系统', options: ['GPS','BDS','GLO','GAL','QZS'], def: ['GPS','BDS'] },
        { key: 'freqs',   type: 'checkgroup', label: '频率', options: ['L1','L2','L5'], def: ['L1','L2'] },
        { key: 'elevMask', type: 'number', label: '高度角', unit: '°', min: 0, max: 90, def: 15 },
        { key: 'snrMask',  type: 'number', label: 'SNR', unit: 'dB', min: 0, max: 60, def: 30 },
        { key: 'maxAge',   type: 'number', label: '龄期', unit: 's', min: 1, max: 300, def: 30 },
        { key: 'iono',     type: 'select', label: '电离层', def: 'broadcast',
            options: [['off','关闭'],['broadcast','广播'],['ifree','无电离层'],['est','估计']] },
        { key: 'trop',     type: 'select', label: '对流层', def: 'saas',
            options: [['off','关闭'],['saas','Saastamoinen'],['sbas','SBAS'],['est','估计']] },
        { key: 'modear',   type: 'select', label: '模糊度', def: 'continuous',
            options: [['continuous','连续'],['instantaneous','瞬时'],['fix-and-hold','固定保持'],['off','关闭']] },
        { key: 'thresar',  type: 'number', label: 'Ratio阈值', min: 0, max: 99, step: 0.5, def: 3.0 },
        { key: 'soltype',  type: 'select', label: '解算方向', def: 'forward',
            options: [['forward','前向'],['backward','后向'],['combined','组合']] }
    ],

    init() {
        if (localStorage.getItem('rtklib_theme') === 'light') document.body.classList.add('light');
        this.paramState = this._defaultParams();
        this.renderModeButtons();
        this.renderFileSlots();
        this.renderParams();
        this.bindPageDrop();
        this.pollRunningOnLoad();
    },

    _defaultParams() {
        const p = {};
        this.PARAM_DEFS.forEach(d => { p[d.key] = Array.isArray(d.def) ? [...d.def] : d.def; });
        return p;
    },

    toggleTheme() {
        const light = document.body.classList.toggle('light');
        localStorage.setItem('rtklib_theme', light ? 'light' : 'dark');
        document.querySelector('.theme-btn').textContent = light ? '☾' : '☀';
    },

    renderModeButtons() {
        const el = document.getElementById('mode-btns');
        el.innerHTML = this.MODES.map(m =>
            `<button class="mode-btn ${m.key===this.mode?'sel':''}" data-mode="${m.key}">${m.label}</button>`
        ).join('');
        el.querySelectorAll('.mode-btn').forEach(b => {
            b.onclick = () => this.switchMode(b.dataset.mode);
        });
    },

    switchMode(m) {
        if (m === this.mode) return;
        this.mode = m;
        document.querySelectorAll('.mode-btn').forEach(b => b.classList.toggle('sel', b.dataset.mode === m));
        this.renderFileSlots();
        this.updateRunHint();
    },

    _cache() { return this.fileCache[this.mode] || (this.fileCache[this.mode] = {}); },

    renderFileSlots() {
        const defs = this.MODE_FILES[this.mode] || [];
        const cache = this._cache();
        const el = document.getElementById('file-slots');
        el.innerHTML = defs.map(def => {
            const f = cache[def.key];
            return `<div class="file-slot">
        <label>${U.esc(def.label)}${def.required ? '<span class="req">*</span>' : ''}</label>
        <div class="drop-card ${f ? (f.status==='ok'?'ok':(f.status==='err'?'err':'')) : 'empty'}"
             data-key="${def.key}" data-accept="${def.accept}">
          ${this._slotInner(def, f)}
          <input type="file" accept="${def.accept}">
        </div>
      </div>`;
        }).join('');
        el.querySelectorAll('.drop-card').forEach(card => this.bindDropCard(card));
    },

    _slotInner(def, f) {
        if (!f) {
            return `<span class="ico">📂</span>
              <span class="meta" style="text-align:center">拖放或点击选择 ${U.esc(def.accept)}</span>`;
        }
        const icon = f.status === 'ok' ? '✅' : f.status === 'err' ? '❌' : '⏳';
        const sub = f.status === 'ok' ? U.formatBytes(f.size)
            : f.status === 'err' ? (f.error || '上传失败')
                : Math.round((f.progress || 0) * 100) + '%';
        return `<span class="ico">${icon}</span>
      <span class="meta">
        <div class="fname">${U.esc(f.name)}</div>
        <div class="fsub">${U.esc(sub)}</div>
        ${f.status === 'uploading'
            ? `<div class="bar"><i style="width:${(f.progress||0)*100}%"></i></div>`
            : ''}
      </span>
      <span class="actions">
        ${f.status === 'err' ? `<button class="icon-btn retry" data-act="retry">重试</button>` : ''}
        <button class="icon-btn" data-act="remove" title="移除">✕</button>
      </span>`;
    },

    bindDropCard(card) {
        const key = card.dataset.key;
        const input = card.querySelector('input[type=file]');

        card.onclick = e => {
            if (e.target.closest('[data-act]')) return;
            input.click();
        };
        input.onchange = () => {
            if (input.files[0]) this.handleFile(key, input.files[0]);
            input.value = '';
        };

        card.addEventListener('dragover', e => { e.preventDefault(); card.classList.add('dragover'); });
        card.addEventListener('dragleave', () => card.classList.remove('dragover'));
        card.addEventListener('drop', e => {
            e.preventDefault(); e.stopPropagation();
            card.classList.remove('dragover');
            const f = e.dataTransfer.files[0];
            if (f) this.handleFile(key, f);
        });

        card.querySelectorAll('[data-act]').forEach(btn => {
            btn.onclick = e => {
                e.stopPropagation();
                if (btn.dataset.act === 'remove') this.removeFile(key);
                if (btn.dataset.act === 'retry')  this.retryFile(key);
            };
        });
    },

    bindPageDrop() {
        let depth = 0;
        window.addEventListener('dragenter', e => {
            if (!e.dataTransfer.types.includes('Files')) return;
            depth++;
            document.body.classList.add('page-dragover');
        });
        window.addEventListener('dragleave', () => {
            depth = Math.max(0, depth - 1);
            if (depth === 0) document.body.classList.remove('page-dragover');
        });
        window.addEventListener('dragover', e => e.preventDefault());
        window.addEventListener('drop', e => {
            e.preventDefault(); depth = 0;
            document.body.classList.remove('page-dragover');
            const f = e.dataTransfer.files[0];
            if (!f) return;
            if (e.target.closest('.drop-card')) return;
            const slot = this._firstEmptySlot();
            if (!slot) { U.toast('所有槽位已满，先移除一个', 'warn'); return; }
            this.handleFile(slot.key, f);
        });
    },

    _firstEmptySlot() {
        const defs = this.MODE_FILES[this.mode] || [];
        const cache = this._cache();
        for (const d of defs) {
            const f = cache[d.key];
            if (!f || f.status === 'err') return d;
        }
        return null;
    },

    async handleFile(key, file) {
        const cache = this._cache();
        cache[key] = { name: file.name, size: file.size, status: 'uploading', progress: 0, _file: file };
        this._patchSlot(key);
        this.updateRunHint();

        try {
            const res = await U.upload(file, p => {
                const f = this._cache()[key];
                if (f) { f.progress = p; this._patchSlot(key); }
            });
            const f = this._cache()[key];
            if (!f) return;
            f.status = 'ok';
            f.path = res.path;
            f.size = res.size || file.size;
            f.md5 = res.md5;
            delete f._file;
            this._patchSlot(key);
            this.updateRunHint();
            U.toast(file.name + ' 上传完成', 'ok');
        } catch (e) {
            const f = this._cache()[key];
            if (f) { f.status = 'err'; f.error = e.message; }
            this._patchSlot(key);
            this.updateRunHint();
            U.toast(file.name + ' 上传失败: ' + e.message, 'err');
        }
    },

    _patchSlot(key) {
        const card = document.querySelector(`.drop-card[data-key="${key}"]`);
        if (!card) return;
        const def = (this.MODE_FILES[this.mode] || []).find(d => d.key === key);
        const f = this._cache()[key];
        if (!def) return;
        card.className = 'drop-card ' + (f ? (f.status==='ok'?'ok':(f.status==='err'?'err':'')) : 'empty');
        card.innerHTML = this._slotInner(def, f) + card.querySelector('input[type=file]').outerHTML;
        this.bindDropCard(card);
    },

    removeFile(key) {
        delete this._cache()[key];
        this.renderFileSlots();
        this.updateRunHint();
    },

    retryFile(key) {
        const f = this._cache()[key];
        if (f && f._file) this.handleFile(key, f._file);
    },

    renderParams() {
        const el = document.getElementById('param-grid');
        el.innerHTML = this.PARAM_DEFS.map(d => this._paramHtml(d)).join('');
        el.querySelectorAll('[data-pkey]').forEach(node => {
            const key = node.dataset.pkey;
            const def = this.PARAM_DEFS.find(d => d.key === key);
            if (def.type === 'checkgroup') {
                node.querySelectorAll('input[type=checkbox]').forEach(cb => {
                    cb.onchange = () => {
                        const set = new Set(this.paramState[key]);
                        cb.checked ? set.add(cb.value) : set.delete(cb.value);
                        this.paramState[key] = [...set];
                    };
                });
            } else if (def.type === 'number') {
                node.querySelector('input').oninput = e => {
                    const v = parseFloat(e.target.value);
                    this.paramState[key] = isNaN(v) ? def.def : v;
                };
            } else if (def.type === 'select') {
                node.querySelector('select').onchange = e => { this.paramState[key] = e.target.value; };
            }
        });
    },

    _paramHtml(d) {
        if (d.type === 'checkgroup') {
            const boxes = d.options.map(o =>
                `<label style="display:inline-flex;align-items:center;gap:3px;cursor:pointer">
          <input type="checkbox" value="${o}" ${this.paramState[d.key].includes(o) ? 'checked' : ''}>${o}
        </label>`).join('');
            return `<div class="param-item" data-pkey="${d.key}" style="grid-column:span 2">
        <span>${d.label}:</span>${boxes}</div>`;
        }
        if (d.type === 'number') {
            const v = this.paramState[d.key];
            return `<div class="param-item" data-pkey="${d.key}">
        <span>${d.label}:</span>
        <input type="number" value="${v}" min="${d.min??''}" max="${d.max??''}" step="${d.step??1}">
        ${d.unit ? `<span>${d.unit}</span>` : ''}</div>`;
        }
        if (d.type === 'select') {
            const cur = this.paramState[d.key];
            const opts = d.options.map(([v,l]) =>
                `<option value="${v}" ${v===cur?'selected':''}>${l}</option>`).join('');
            return `<div class="param-item" data-pkey="${d.key}">
        <span>${d.label}:</span><select>${opts}</select></div>`;
        }
        return '';
    },

    updateRunHint() {
        const defs = this.MODE_FILES[this.mode] || [];
        const cache = this._cache();
        const missing = defs.filter(d => d.required && (!cache[d.key] || cache[d.key].status !== 'ok'));
        const uploading = defs.some(d => cache[d.key] && cache[d.key].status === 'uploading');
        const hint = document.getElementById('run-hint');
        if (uploading) hint.textContent = '⏳ 上传中...';
        else if (missing.length) hint.textContent = '⚠ 缺少: ' + missing.map(d => d.label).join(', ');
        else hint.textContent = '✓ 文件就绪';
    },

    buildRequest() {
        const cache = this._cache();
        const files = {};
        (this.MODE_FILES[this.mode] || []).forEach(d => {
            const f = cache[d.key];
            if (f && f.status === 'ok') files[d.key] = f.path;
        });
        const req = { mode: this.mode, params: JSON.parse(JSON.stringify(this.paramState)) };
        if (files.rover) req.roverFile = files.rover;
        if (files.base)  req.baseFile  = files.base;
        if (files.nav)   req.navFile   = files.nav;
        if (files.sp3)   req.sp3File   = files.sp3;
        if (files.clk)   req.clkFile   = files.clk;
        return req;
    },

    async run() {
        if (this.running) return;
        const defs = this.MODE_FILES[this.mode] || [];
        const cache = this._cache();
        const missing = defs.filter(d => d.required && (!cache[d.key] || cache[d.key].status !== 'ok'));
        if (missing.length) { U.toast('缺少必需文件: ' + missing.map(d => d.label).join(', '), 'warn'); return; }

        this.allEpochs = [];
        this.allLogs = [];
        this.lastLogIdx = 0;
        this.lastEpochIdx = 0;
        this.clearResults();

        document.getElementById('btn-run').style.display = 'none';
        document.getElementById('btn-stop').style.display = '';
        this.appendLog('提交解算请求...', 'info');

        try {
            const req = this.buildRequest();
            await U.api('/api/solve/run', { method: 'POST', body: req });
            this.appendLog('解算已启动 | 模式: ' + this.mode, 'ok');
            this.running = true;
            this.startPolling();
        } catch (e) {
            this.appendLog('启动失败: ' + e.message, 'err');
            this.stopUI();
        }
    },

    async stop() {
        try { await U.api('/api/solve/stop', { method: 'POST' }); } catch (e) {}
        this.appendLog('已发送停止命令', 'info');
        this.stopUI();
    },

    stopUI() {
        this.running = false;
        this.stopPolling();
        document.getElementById('btn-run').style.display = '';
        document.getElementById('btn-stop').style.display = 'none';
    },

    startPolling() {
        this.stopPolling();
        this.polling = setInterval(() => this.poll(), 600);
    },
    stopPolling() {
        if (this.polling) { clearInterval(this.polling); this.polling = null; }
    },

    async poll() {
        try {
            const [resultData, logsData] = await Promise.all([
                U.api('/api/solve/result'),
                U.api('/api/solve/logs')
            ]);
            if (logsData.logs && logsData.logs.length > this.allLogs.length) {
                const newLogs = logsData.logs.slice(this.allLogs.length);
                newLogs.forEach(l => this.allLogs.push(l));
                this.renderLogs();
            }
            if (resultData.epochs && resultData.epochs.length > this.allEpochs.length) {
                const newEpochs = resultData.epochs.slice(this.allEpochs.length);
                newEpochs.forEach(e => this.allEpochs.push(e));
                document.getElementById('result-area').classList.add('show');
                this.renderStats(resultData.summary || {});
                this.drawCharts();
            }
            if (resultData.running === false && this.running) {
                document.getElementById('result-area').classList.add('show');
                if (resultData.summary && (resultData.summary.totalEpochs > 0 || resultData.summary.error)) {
                    this.renderStats(resultData.summary);
                } else {
                    this.renderStats(resultData.summary || {});
                }
                this.drawCharts();
                if (resultData.summary && resultData.summary.error) {
                    this.appendLog('解算错误: ' + resultData.summary.error, 'err');
                } else if (this.allEpochs.length === 0) {
                    this.appendLog('解算完成但无有效历元，可能文件不支持当前模式', 'warn');
                }
                this.appendLog('解算结束', 'ok');
                this.stopUI();
            }
        } catch (e) {
            if (!this._pollErrOnce) {
                this._pollErrOnce = true;
                this.appendLog('轮询异常: ' + e.message, 'err');
                setTimeout(() => { this._pollErrOnce = false; }, 5000);
            }
        }
    },

    async pollRunningOnLoad() {
        try {
            const st = await U.api('/api/solve/status');
            if (st.running) {
                this.appendLog('检测到运行中的解算，开始轮询...', 'info');
                this.running = true;
                document.getElementById('btn-run').style.display = 'none';
                document.getElementById('btn-stop').style.display = '';
                this.startPolling();
            }
        } catch (e) {}
    },

    appendLog(msg, cls) {
        this.allLogs.push({ t: Date.now(), msg, cls: cls || '' });
        this.renderLogs();
    },
    renderLogs() {
        const panel = document.getElementById('log-panel');
        const lines = this.allLogs.slice(-300).map(l => {
            if (typeof l === 'string') return `<div class="line">${U.esc(l)}</div>`;
            return `<div class="line ${l.cls || ''}">[${U.formatTime(l.t)}] ${U.esc(l.msg)}</div>`;
        });
        panel.innerHTML = lines.join('') || '<div class="line">等待输出...</div>';
        panel.scrollTop = panel.scrollHeight;
    },

    clearResults() {
        document.getElementById('result-area').classList.remove('show');
        document.getElementById('solve-stats').innerHTML = '';
        const f = document.getElementById('cv-fix');
        if (f) f.getContext('2d').clearRect(0, 0, f.width, f.height);
        const d = document.getElementById('cv-dev');
        if (d) d.getContext('2d').clearRect(0, 0, d.width, d.height);
    },

    renderStats(s) {
        const total = s.totalEpochs || this.allEpochs.length || 0;
        const success = s.successCount || this.allEpochs.length || 0;
        const fixCnt = s.fixCount != null ? s.fixCount : this.allEpochs.filter(e => e.fixMode === 1).length;
        const floatCnt = s.floatCount != null ? s.floatCount : this.allEpochs.filter(e => e.fixMode === 2).length;
        const singleCnt = s.singleCount != null ? s.singleCount : this.allEpochs.filter(e => e.fixMode >= 5).length;
        const fixRate = s.fixRate != null ? s.fixRate : (total ? Math.round(fixCnt/total*1000)/10 : 0);
        const floatRate = total ? Math.round(floatCnt/total*1000)/10 : 0;
        const singleRate = total ? Math.round(singleCnt/total*1000)/10 : 0;

        const fixed = this.allEpochs.filter(e => e.fixMode === 1);
        const hasFix = fixed.length >= 2;
        const refEpochs = hasFix ? fixed : this.allEpochs.filter(e => e.fixMode >= 1 && e.latitude !== 0);
        let coordHtml = '';
        if (refEpochs.length >= 2) {
            const refLabel = hasFix ? '固定坐标均值' : '坐标均值 (SPP)';
            const mLat = refEpochs.reduce((s,e)=>s+e.latitude,0)/refEpochs.length;
            const mLon = refEpochs.reduce((s,e)=>s+e.longitude,0)/refEpochs.length;
            const mHgt = refEpochs.reduce((s,e)=>s+e.height,0)/refEpochs.length;
            const cosLat = Math.cos(mLat * Math.PI / 180);
            const nArr = refEpochs.map(e => (e.latitude - mLat) * 111000);
            const eArr = refEpochs.map(e => (e.longitude - mLon) * 111000 * cosLat);
            const uArr = refEpochs.map(e => e.height - mHgt);
            const stat = arr => {
                const mn = Math.min(...arr), mx = Math.max(...arr);
                const mean = arr.reduce((a,b)=>a+b,0)/arr.length;
                const std = Math.sqrt(arr.reduce((s,v)=>s+(v-mean)**2,0)/arr.length);
                return { min: mn, max: mx, mean, std, range: mx - mn };
            };
            const nS = stat(nArr), eS = stat(eArr), uS = stat(uArr);
            const fmt = v => (v * 100).toFixed(2);
            coordHtml = `
          <div class="stat-card" style="grid-column:1/-1">
            <div style="font-size:11px;color:var(--text2);margin-bottom:4px">📌 ${refLabel}</div>
            <div style="font-size:10px;color:var(--text)">Lat ${mLat.toFixed(8)}°  Lon ${mLon.toFixed(8)}°  H ${mHgt.toFixed(3)}m</div>
          </div>
          <div class="stat-card" style="grid-column:1/-1">
            <div style="font-size:11px;color:var(--text2);margin-bottom:4px">📏 坐标偏差 (cm) · 相对${hasFix ? ' Fixed' : ''}均值</div>
            <table style="width:100%;font-size:10px;border-collapse:collapse">
              <tr style="color:var(--text2)"><th></th><th>N</th><th>E</th><th>U</th></tr>
              <tr><td style="color:var(--text2)">Max</td><td>${fmt(nS.max)}</td><td>${fmt(eS.max)}</td><td>${fmt(uS.max)}</td></tr>
              <tr><td style="color:var(--text2)">Min</td><td>${fmt(nS.min)}</td><td>${fmt(eS.min)}</td><td>${fmt(uS.min)}</td></tr>
              <tr><td style="color:var(--text2)">Range</td><td style="color:var(--accent)">${fmt(nS.range)}</td><td style="color:var(--accent)">${fmt(eS.range)}</td><td style="color:var(--accent)">${fmt(uS.range)}</td></tr>
              <tr><td style="color:var(--text2)">Std</td><td>${fmt(nS.std)}</td><td>${fmt(eS.std)}</td><td>${fmt(uS.std)}</td></tr>
            </table>
          </div>`;
        }

        document.getElementById('solve-stats').innerHTML = `
      <div class="stat-card"><div class="val c-fix">${fixRate}%</div><div class="lbl">Fixed (${fixCnt})</div></div>
      <div class="stat-card"><div class="val c-float">${floatRate}%</div><div class="lbl">Float (${floatCnt})</div></div>
      <div class="stat-card"><div class="val c-single">${singleRate}%</div><div class="lbl">Single (${singleCnt})</div></div>
      <div class="stat-card"><div class="val">${total}</div><div class="lbl">总历元</div></div>
      <div class="stat-card"><div class="val">${success}</div><div class="lbl">成功</div></div>
      <div class="stat-card"><div class="val">${s.failCount||0}</div><div class="lbl">失败</div></div>
      ${coordHtml}`;
    },

    drawCharts() {
        this.drawFixTimeline();
        this.drawCoordDev();
        this.drawRatioTimeline();
        this.drawSatPdop();
    },

    drawFixTimeline() {
        const canvas = document.getElementById('cv-fix');
        const epochs = this.allEpochs;
        if (!canvas || !epochs.length) return;
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = 60;
        canvas.width = W * dpr; canvas.height = H * dpr;
        canvas.style.height = H + 'px';
        const ctx = canvas.getContext('2d');
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, W, H);

        const pad = { l: 8, r: 8, t: 6, b: 18 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        const t0 = epochs[0].timestamp, tN = epochs[epochs.length-1].timestamp;
        const tR = tN - t0 || 1;
        const barW = Math.max(1, pw / epochs.length);

        epochs.forEach(e => {
            const x = pad.l + (e.timestamp - t0) / tR * pw;
            ctx.fillStyle = U.STATE_COLOR[U.fixModeToState(e.fixMode)];
            ctx.fillRect(x, pad.t, barW, ph);
        });

        ctx.fillStyle = '#8b949e'; ctx.font = '9px sans-serif';
        ctx.textAlign = 'left';  ctx.fillText(U.formatTime(t0), pad.l, H - 4);
        ctx.textAlign = 'right'; ctx.fillText(U.formatTime(tN), W - pad.r, H - 4);
        ctx.textAlign = 'left';
    },

    drawCoordDev() {
        const canvas = document.getElementById('cv-dev');
        const epochs = this.allEpochs;
        if (!canvas || !epochs.length) return;
        const fixed = epochs.filter(e => e.fixMode === 1);
        const hasFix = fixed.length >= 2;
        const refEpochs = hasFix ? fixed : epochs.filter(e => e.fixMode >= 1 && e.latitude !== 0);
        const W = canvas.clientWidth, H = 150;
        const dpr = window.devicePixelRatio || 1;

        if (!refEpochs.length) {
            canvas.width = W * dpr; canvas.height = H * dpr;
            canvas.style.height = H + 'px';
            const ctx = canvas.getContext('2d');
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            ctx.clearRect(0, 0, W, H);
            ctx.fillStyle = '#8b949e'; ctx.font = '11px sans-serif';
            ctx.textAlign = 'center'; ctx.fillText('暂无固定解', W/2, H/2);
            ctx.textAlign = 'left';
            return;
        }

        canvas.width = W * dpr; canvas.height = H * dpr;
        canvas.style.height = H + 'px';
        const ctx = canvas.getContext('2d');
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, W, H);

        const mLat = refEpochs.reduce((s,e)=>s+e.latitude,0)/refEpochs.length;
        const mLon = refEpochs.reduce((s,e)=>s+e.longitude,0)/refEpochs.length;
        const mHgt = refEpochs.reduce((s,e)=>s+e.height,0)/refEpochs.length;
        const cosLat = Math.cos(mLat * Math.PI / 180);

        const t0 = epochs[0].timestamp, tN = epochs[epochs.length-1].timestamp;
        const tR = tN - t0 || 1;
        const pad = { l: 34, r: 8, t: 4, b: 18 };
        const pw = W - pad.l - pad.r;
        const bandH = (H - pad.t - pad.b) / 3;

        const bands = [
            { label: 'N', get: e => (e.latitude - mLat) * 111000 },
            { label: 'E', get: e => (e.longitude - mLon) * 111000 * cosLat },
            { label: 'U', get: e => e.height - mHgt }
        ];

        const maxAbs = Math.max(...epochs.flatMap(e => bands.map(b => Math.abs(b.get(e)))), 0.01) * 1.2;

        bands.forEach((b, i) => {
            const y0 = pad.t + i * bandH;
            const yMid = y0 + bandH / 2;
            const scale = (bandH / 2) / maxAbs;

            ctx.strokeStyle = '#2a3040'; ctx.lineWidth = 0.6;
            ctx.beginPath(); ctx.moveTo(pad.l, yMid); ctx.lineTo(W - pad.r, yMid); ctx.stroke();

            epochs.forEach(e => {
                const x = pad.l + (e.timestamp - t0) / tR * pw;
                const y = yMid - b.get(e) * scale;
                const st = U.fixModeToState(e.fixMode);
                ctx.fillStyle = U.STATE_COLOR[st];
                ctx.globalAlpha = st === 'fix' ? 0.9 : 0.5;
                ctx.beginPath(); ctx.arc(x, y, 1.2, 0, Math.PI*2); ctx.fill();
                ctx.globalAlpha = 1;
            });

            ctx.fillStyle = '#e6edf3'; ctx.font = 'bold 9px sans-serif';
            ctx.fillText(b.label, 6, yMid + 3);
            ctx.fillStyle = '#8b949e'; ctx.font = '8px sans-serif';
            ctx.fillText('±' + (maxAbs*100).toFixed(1) + 'cm', 2, y0 + 10);
        });

        ctx.fillStyle = '#8b949e'; ctx.font = '9px sans-serif';
        ctx.textAlign = 'left';  ctx.fillText(U.formatTime(t0), pad.l, H - 4);
        ctx.textAlign = 'right'; ctx.fillText(U.formatTime(tN), W - pad.r, H - 4);
        ctx.textAlign = 'left';
    },

    drawRatioTimeline() {
        const canvas = document.getElementById('cv-ratio');
        const epochs = this.allEpochs;
        if (!canvas || !epochs.length) return;
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = 100;
        canvas.width = W * dpr; canvas.height = H * dpr;
        canvas.style.height = H + 'px';
        const ctx = canvas.getContext('2d');
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, W, H);

        const pad = { l: 34, r: 8, t: 6, b: 18 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        const t0 = epochs[0].timestamp, tN = epochs[epochs.length - 1].timestamp;
        const tR = tN - t0 || 1;

        const ratios = epochs.map(e => e.ratio || 0);
        const maxR = Math.max(...ratios, 3) * 1.1;
        const thres = this.paramState.thresar || 3.0;

        ctx.strokeStyle = '#2a3040'; ctx.lineWidth = 0.5;
        for (let v = 0; v <= maxR; v += Math.max(1, Math.round(maxR / 5))) {
            const y = pad.t + ph - v / maxR * ph;
            ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y); ctx.stroke();
            ctx.fillStyle = '#8b949e'; ctx.font = '8px sans-serif';
            ctx.fillText(v, 2, y + 3);
        }

        const thY = pad.t + ph - thres / maxR * ph;
        ctx.strokeStyle = '#f59e0b'; ctx.lineWidth = 1; ctx.setLineDash([4, 3]);
        ctx.beginPath(); ctx.moveTo(pad.l, thY); ctx.lineTo(W - pad.r, thY); ctx.stroke();
        ctx.setLineDash([]);
        ctx.fillStyle = '#f59e0b'; ctx.font = '8px sans-serif';
        ctx.fillText('Ratio=' + thres, W - pad.r - 55, thY - 3);

        epochs.forEach(e => {
            const x = pad.l + (e.timestamp - t0) / tR * pw;
            const r = e.ratio || 0;
            const y = pad.t + ph - r / maxR * ph;
            const st = U.fixModeToState(e.fixMode);
            ctx.fillStyle = U.STATE_COLOR[st];
            ctx.globalAlpha = 0.8;
            ctx.beginPath(); ctx.arc(x, y, 1, 0, Math.PI * 2); ctx.fill();
            ctx.globalAlpha = 1;
        });

        ctx.fillStyle = '#8b949e'; ctx.font = '9px sans-serif';
        ctx.textAlign = 'left';  ctx.fillText(U.formatTime(t0), pad.l, H - 4);
        ctx.textAlign = 'right'; ctx.fillText(U.formatTime(tN), W - pad.r, H - 4);
        ctx.textAlign = 'left';
    },

    drawSatPdop() {
        const canvas = document.getElementById('cv-satpdop');
        const epochs = this.allEpochs;
        if (!canvas || !epochs.length) return;
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = 100;
        canvas.width = W * dpr; canvas.height = H * dpr;
        canvas.style.height = H + 'px';
        const ctx = canvas.getContext('2d');
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, W, H);

        const pad = { l: 34, r: 34, t: 6, b: 18 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        const t0 = epochs[0].timestamp, tN = epochs[epochs.length - 1].timestamp;
        const tR = tN - t0 || 1;

        const maxSat = Math.max(...epochs.map(e => e.satCount || 0), 10) * 1.15;
        const maxPdop = Math.max(...epochs.map(e => e.pdop || 0), 2) * 1.15;

        ctx.strokeStyle = '#2a3040'; ctx.lineWidth = 0.5;
        for (let v = 0; v <= maxSat; v += Math.max(1, Math.round(maxSat / 5))) {
            const y = pad.t + ph - v / maxSat * ph;
            ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y); ctx.stroke();
            ctx.fillStyle = '#8b949e'; ctx.font = '8px sans-serif';
            ctx.fillText(v, 2, y + 3);
        }

        epochs.forEach(e => {
            const x = pad.l + (e.timestamp - t0) / tR * pw;
            const sc = e.satCount || 0;
            const y = pad.t + ph - sc / maxSat * ph;
            ctx.fillStyle = '#3b82f6';
            ctx.beginPath(); ctx.arc(x, y, 1, 0, Math.PI * 2); ctx.fill();
        });

        ctx.beginPath(); let first = true;
        epochs.forEach(e => {
            const x = pad.l + (e.timestamp - t0) / tR * pw;
            const pdop = e.pdop || 0;
            const y = pad.t + ph - pdop / maxPdop * ph;
            if (first) { ctx.moveTo(x, y); first = false; } else ctx.lineTo(x, y);
        });
        ctx.strokeStyle = '#f59e0b'; ctx.lineWidth = 1.2; ctx.stroke();

        ctx.fillStyle = '#3b82f6'; ctx.font = '9px sans-serif';
        ctx.fillText('卫星数', pad.l + 4, pad.t + 10);
        ctx.fillStyle = '#f59e0b';
        ctx.fillText('PDOP', pad.l + 50, pad.t + 10);

        for (let v = 0; v <= maxPdop; v += Math.max(1, Math.round(maxPdop / 4))) {
            const y = pad.t + ph - v / maxPdop * ph;
            ctx.fillStyle = '#8b949e'; ctx.font = '8px sans-serif';
            ctx.textAlign = 'right'; ctx.fillText(v.toFixed(0), W - 2, y + 3);
        }
        ctx.textAlign = 'left';

        ctx.fillStyle = '#8b949e'; ctx.font = '9px sans-serif';
        ctx.fillText(U.formatTime(t0), pad.l, H - 4);
        ctx.textAlign = 'right'; ctx.fillText(U.formatTime(tN), W - pad.r, H - 4);
        ctx.textAlign = 'left';
    }
};

document.addEventListener('DOMContentLoaded', () => Solve.init());