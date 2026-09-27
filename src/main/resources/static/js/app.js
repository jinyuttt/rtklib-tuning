const App = {
    scanResult: null,
    pairs: [],
    activePairId: null,
    paramSets: [],
    currentData: null,
    evalResults: null,
    bestIdx: -1,
    evalId: null,
    evalEventSource: null,
    _trackLoaded: false,

    async init() {
        if (localStorage.getItem('rtklib_theme') === 'light') document.body.classList.add('light');
        document.getElementById('modal-save').onclick = () => App.savePairs();
        document.getElementById('modal').addEventListener('click', e => { if (e.target.id === 'modal') App.closeModal(); });
        document.getElementById('help-modal').addEventListener('click', e => { if (e.target.id === 'help-modal') e.target.classList.remove('show'); });
        document.getElementById('track-modal').addEventListener('click', e => { if (e.target.id === 'track-modal') App.closeTrackModal(); });
        await this.loadPairs();
        this.loadTaskList();
    },

    toggleTheme() {
        document.body.classList.toggle('light');
        document.querySelector('.theme-btn').textContent = document.body.classList.contains('light') ? '☾' : '☀';
        localStorage.setItem('rtklib_theme', document.body.classList.contains('light') ? 'light' : 'dark');
    },

    /* ══════════ Scan ══════════ */
    async doScan() {
        const root = document.getElementById('root-path').value.trim();
        if (!root) { toast('请输入根目录', 'err'); return; }
        toast('扫描中...');
        try {
            const r = await fetch('/api/scan', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ root })
            }).then(r => r.json());
            this.scanResult = r;
            await this.loadPairs();
            this.renderSidebar();
            if (this.activePairId) this.onSelectionChange();
            document.getElementById('scan-status').textContent = r.stations.length + '站';
            toast(r.stations.length + ' 个站已发现', 'ok');
        } catch (e) { toast('扫描失败', 'err'); }
    },

    /* ══════════ Pairs ══════════ */
    async loadPairs() {
        try {
            const cfg = await fetch('/api/station-config').then(r => r.json());
            this.pairs = cfg.pairs || [];
        } catch (e) { this.pairs = []; }
    },

    getStations() { return this.scanResult ? this.scanResult.stations : []; },

    renderSidebar() {
        const pl = document.getElementById('pair-list');
        if (!this.pairs.length) {
            pl.innerHTML = '<div style="padding:7px 10px;font-size:10px;color:var(--text2)">扫描 → ⚙ 创建</div>';
        } else {
            pl.innerHTML = this.pairs.map(p => {
                return `<div class="side-pair ${this.activePairId === p.id ? 'active' : ''}" onclick="App.selectPair('${p.id}')">
                    <span>🔗</span><span class="lbl">${esc(p.label || (p.baseId + '→' + p.roverId))}</span>
                    <span class="rmv" onclick="event.stopPropagation();App.removePair('${p.id}')">×</span></div>`;
            }).join('');
        }
    },

    selectPair(id) {
        this.activePairId = id;
        this.renderSidebar();
        this.onSelectionChange();
    },

    removePair(id) {
        this.pairs = this.pairs.filter(p => p.id !== id);
        if (this.activePairId === id) this.activePairId = null;
        this.renderSidebar();
        fetch('/api/station-config', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ pairs: this.pairs })
        });
    },

    onSelectionChange() {
        const bar = document.getElementById('sel-bar');
        const welcome = document.getElementById('welcome');
        const directBar = document.getElementById('direct-bar');
        if (!this.activePairId) { bar.style.display = 'none'; welcome.style.display = ''; directBar.style.display = ''; return; }
        bar.style.display = '';
        welcome.style.display = 'none';
        directBar.style.display = 'none';

        const pair = this.pairs.find(p => p.id === this.activePairId);
        if (!pair) return;
        const rover = this.getStations().find(s => s.stationId === pair.roverId);
        const dl = document.getElementById('date-list');
        dl.innerHTML = '';
        if (rover && rover.dates) {
            rover.dates.forEach(d => {
                const lbl = document.createElement('label');
                lbl.style.cssText = 'display:inline-flex;align-items:center;gap:3px;cursor:pointer;padding:2px 6px;border-radius:4px;background:var(--bg3);border:1px solid var(--border);transition:all .15s';
                const cb = document.createElement('input');
                cb.type = 'checkbox';
                cb.value = d.date;
                cb.checked = true;
                cb.className = 'date-cb';
                cb.onchange = () => { App.onDateChange(); };
                const span = document.createElement('span');
                span.textContent = d.date;
                lbl.appendChild(cb);
                lbl.appendChild(span);
                dl.appendChild(lbl);
            });
        }
        this.onDateChange();
    },

    onDateChange() {
        const pair = this.pairs.find(p => p.id === this.activePairId);
        if (!pair) return;
        const rover = this.getStations().find(s => s.stationId === pair.roverId);
        const selectedDates = this.getSelectedDates();
        this._updateDateCount();
        if (!selectedDates.length) return;
        const date = selectedDates[0];
        const de = rover && rover.dates ? rover.dates.find(d => d.date === date) : null;
        if (de && de.files && de.files.length) {
            const hrs = de.files.map(f => f.hour).filter(h => h >= 0);
            if (hrs.length) {
                document.getElementById('sel-sh').value = Math.min(...hrs);
                document.getElementById('sel-eh').value = Math.max(...hrs);
            }
        }
    },

    getSelectedDates() {
        return [...document.querySelectorAll('.date-cb:checked')].map(cb => cb.value);
    },

    toggleAllDates(checked) {
        document.querySelectorAll('.date-cb').forEach(cb => { cb.checked = checked; });
        this.onDateChange();
    },

    _updateDateCount() {
        const all = document.querySelectorAll('.date-cb');
        const sel = document.querySelectorAll('.date-cb:checked');
        const el = document.getElementById('date-count');
        if (el) el.textContent = `已选 ${sel.length}/${all.length}`;
    },

    /* ══════════ Direct Load ══════════ */
    async directLoad() {
        const roverFile = document.getElementById('direct-rover').value.trim();
        const baseFile = document.getElementById('direct-base').value.trim();
        if (!roverFile) { toast('请输入测站文件路径', 'warn'); return; }

        toast('验证文件路径...');
        try {
            const checkR = await fetch('/api/scan-file-path', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ path: roverFile })
            }).then(r => r.json());
            if (checkR.error) { toast('测站文件错误: ' + checkR.error, 'err'); return; }
        } catch (e) { toast('验证测站文件失败: ' + e.message, 'err'); return; }

        if (baseFile) {
            try {
                const checkB = await fetch('/api/scan-file-path', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: baseFile })
                }).then(r => r.json());
                if (checkB.error) { toast('基站文件错误: ' + checkB.error, 'err'); return; }
            } catch (e) { toast('验证基站文件失败: ' + e.message, 'err'); return; }
        }

        this.currentData = { cacheKey: 'direct_' + Date.now(), roverFile, baseFile };

        document.getElementById('rtcm-summary').style.display = '';
        document.getElementById('rtcm-summary').innerHTML = `<span class="summary-pill">📄 测站: ${esc(roverFile.split('\\').pop().split('/').pop())}</span>` +
            (baseFile ? `<span class="summary-pill">📄 基站: ${esc(baseFile.split('\\').pop().split('/').pop())}</span>` : '');

        this.initParamSets();
        document.getElementById('param-area').style.display = '';
        document.getElementById('sol-area').style.display = 'none';
        toast('文件已就绪，可调参评估', 'ok');
    },

    /* ══════════ Load ══════════ */
    async loadAndDiagnose() {
        const pair = this.pairs.find(p => p.id === this.activePairId);
        if (!pair) { toast('请选择配对组', 'warn'); return; }
        const dates = this.getSelectedDates();
        const sh = parseInt(document.getElementById('sel-sh').value) || 0;
        const eh = parseInt(document.getElementById('sel-eh').value) || 23;
        if (!dates.length) { toast('请选择日期（Ctrl+点击多选）', 'warn'); return; }

        const date = dates[0];
        toast('加载中...');
        const root = document.getElementById('root-path').value.trim();
        let r;
        try {
            r = await fetch('/api/load-station', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ root, stationId: pair.roverId, date, startHour: sh, endHour: eh })
            }).then(r => r.json());
        } catch (e) { toast('加载失败: ' + e.message, 'err'); return; }
        if (r.error) { toast('加载错误: ' + r.error, 'err'); return; }
        if (!r.totalMessages && !r.solutionStats) { toast('加载结果为空，请检查文件路径', 'warn'); return; }

        this.currentData = r;
        this.currentData.pairId = pair.id;
        this.currentData.root = root;
        this.currentData.dates = dates;
        this.currentData.roverId = pair.roverId;
        this.currentData.baseId = pair.baseId;
        document.getElementById('eval-sh').value = sh;
        document.getElementById('eval-eh').value = eh;

        const gaps = r.gaps || [];
        const gapText = gaps.length === 0 ? '√ 连续' : '⚠ ' + gaps.length + '个缺口';
        document.getElementById('rtcm-summary').style.display = '';
        document.getElementById('rtcm-summary').innerHTML = [
            `<span class="summary-pill">📡 <b>${r.totalMessages}</b> 条消息</span>`,
            `<span class="summary-pill">⏱ <b>${r.durationSec}s</b></span>`,
            `<span class="summary-pill">📋 <b>${(r.typeSummary || []).length}</b> 种类型</span>`,
            `<span class="summary-pill" style="color:${gaps.length > 5 ? 'var(--red)' : gaps.length > 0 ? 'var(--orange)' : 'var(--green)'}">🔗 ${gapText}</span>`,
            `<span class="summary-pill">📏 平均间隔 <b>${r.totalMessages > 1 ? (r.durationSec / (r.totalMessages - 1)).toFixed(2) : '--'}s</b></span>`,
        ].join('');

        this.renderSolutionStats(r);
        document.getElementById('sol-area').style.display = '';

        this.initParamSets();
        document.getElementById('param-area').style.display = '';
        toast('加载完成', 'ok');
    },

    renderSolutionStats(r) {
        const s = r.solutionStats || {};
        const total = s.totalEpochs || 0;
        if (!total) { document.getElementById('sol-stats').innerHTML = '<div style="font-size:10px;color:var(--text2);grid-column:1/-1;text-align:center">未找到 .pos 解算文件</div>'; return; }

        const fixPct = s.fixRate || 0;
        const fixCnt = s.fixEpochs || 0;
        const fltCnt = s.floatEpochs || 0;
        const sglCnt = s.singleEpochs || 0;
        const fltPct = total ? Math.round(fltCnt / total * 1000) / 10 : 0;
        const sglPct = total ? Math.round(sglCnt / total * 1000) / 10 : 0;

        document.getElementById('sol-stats').innerHTML = `
            <div class="sol-stat-card"><div class="val c-fix">${fixPct}%</div><div class="lbl">Fixed (${fixCnt}历元)</div></div>
            <div class="sol-stat-card"><div class="val c-float">${fltPct}%</div><div class="lbl">Float (${fltCnt}历元)</div></div>
            <div class="sol-stat-card"><div class="val c-single">${sglPct}%</div><div class="lbl">Single (${sglCnt}历元)</div></div>
            <div class="sol-stat-card"><div class="val">${total}</div><div class="lbl">总历元</div></div>`;

        if (s.fixLat != null) {
            document.getElementById('sol-coord').innerHTML = `
                <div style="font-weight:600;margin-bottom:4px">📌 固定坐标</div>
                <div class="row"><span>Lat</span><b>${s.fixLat.toFixed(8)}°</b></div>
                <div class="row"><span>Lon</span><b>${s.fixLon.toFixed(8)}°</b></div>
                <div class="row"><span>H</span><b>${s.fixHgt.toFixed(3)}m</b></div>`;

            document.getElementById('sol-range').innerHTML = `
                <div style="font-weight:600;margin-bottom:4px">📏 浮动范围 (@Fixed)</div>
                <table><tr><th></th><th>N(m)</th><th>E(m)</th><th>U(m)</th></tr>
                <tr><td>Max</td><td>${s.nMax}</td><td>${s.eMax}</td><td>${s.uMax}</td></tr>
                <tr><td>Min</td><td>${s.nMin}</td><td>${s.eMin}</td><td>${s.uMin}</td></tr>
                <tr style="color:var(--accent)"><td>范围</td><td>${s.nRange}</td><td>${s.eRange}</td><td>${s.uRange}</td></tr></table>`;
        }

        if (s.ratioAvg != null) {
            document.getElementById('sol-ratio').innerHTML = `
                <div style="font-weight:600;margin-bottom:4px">📊 Ratio</div>
                <div class="row"><span>均值</span><b>${s.ratioAvg}</b></div>
                <div class="row"><span>最大</span><b>${s.ratioMax}</b></div>
                <div class="row"><span>最小</span><b>${s.ratioMin}</b></div>`;
        } else {
            document.getElementById('sol-ratio').innerHTML = '<div style="font-size:10px;color:var(--text2)">无Ratio数据</div>';
        }

        const epochs = r.solutionEpochs || [];
        const cvFix = document.getElementById('cv-fix');
        const cvDev = document.getElementById('cv-dev');
        if (cvFix && epochs.length) Charts.drawFixTimeline(cvFix, epochs);
        if (cvDev && epochs.length) {
            Charts.drawCoordDeviation(cvDev, epochs, {
                lat: s.fixLat || 0, lon: s.fixLon || 0, hgt: s.fixHgt || 0
            });
        }
    },

    /* ══════════ Tasks ══════════ */
    async saveTask() {
        const pair = this.pairs.find(p => p.id === this.activePairId);
        if (!pair || !this.currentData) { toast('请先加载数据', 'warn'); return; }
        this.paramSets.forEach((_, i) => this.readParamUI(i));

        const s = this.currentData.solutionStats || {};
        const evalResults = [];
        let bestLabel = '', bestRate = 0;
        document.querySelectorAll('.eval-result').forEach(el => {
            const lbl = el.querySelector('span:first-child')?.textContent?.replace(' ★', '').trim() || '';
            const rate = parseFloat(el.querySelector('span:last-child')?.textContent?.replace('%', '')) || 0;
            evalResults.push({ label: lbl, fixRate: rate });
            if (rate > bestRate) { bestRate = rate; bestLabel = lbl; }
        });

        const task = {
            label: pair.label || (pair.baseId + '→' + pair.roverId),
            type: 'single',
            rootPath: document.getElementById('root-path').value.trim(),
            date: this.getSelectedDates().join(','),
            pairs: [pair],
            paramSets: this.paramSets,
            evalStartHour: parseInt(document.getElementById('eval-sh').value) || 0,
            evalEndHour: parseInt(document.getElementById('eval-eh').value) || 23,
            bestParam: bestLabel,
            bestFixRate: bestRate,
            pairCount: 1,
            summary: {
                baseline: { [pair.roverId]: { fixRate: s.fixRate || 0, fixEpochs: s.fixEpochs || 0, fixLat: s.fixLat, fixLon: s.fixLon, fixHgt: s.fixHgt, nMax: s.nMax, nMin: s.nMin, eMax: s.eMax, eMin: s.eMin, uMax: s.uMax, uMin: s.uMin, ratioAvg: s.ratioAvg } },
                evalResults: evalResults
            },
            details: { [pair.roverId]: { epochs: this.currentData.solutionEpochs || [] } }
        };

        const r = await fetch('/api/tasks', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(task)
        }).then(r => r.json());
        toast('已保存: ' + (r.label || r.id), 'ok');
        this.loadTaskList();
    },

    async loadTaskList() {
        try {
            const tasks = await fetch('/api/tasks').then(r => r.json());
            const el = document.getElementById('task-list');
            if (!tasks || !tasks.length) { el.innerHTML = '<div style="padding:7px 10px;font-size:10px;color:var(--text2)">暂无</div>'; return; }
            el.innerHTML = tasks.slice(0, 12).map(t => `
                <div class="side-task" onclick="App.viewTask('${t.id}')" title="${esc(t.label||'')}">
                    <span>📋</span><span>${esc(t.label||t.id)}</span>
                    <span class="fr ${(t.bestFixRate||0) > 80 ? 'good' : 'med'}">${t.bestFixRate||'-'}%</span>
                </div>`).join('');
        } catch (e) { /* ignore */ }
    },

    async viewTask(id) {
        const t = await fetch('/api/tasks/' + id + '?part=summary').then(r => r.json());
        if (!t || !t.baseline) { toast('无数据', 'warn'); return; }
        const roverId = Object.keys(t.baseline)[0];
        const bl = t.baseline[roverId];
        const s = {
            totalEpochs: bl.fixEpochs || 0,
            fixEpochs: bl.fixEpochs || 0, floatEpochs: 0, singleEpochs: 0,
            fixRate: bl.fixRate || 0, fixLat: bl.fixLat, fixLon: bl.fixLon, fixHgt: bl.fixHgt,
            nMax: bl.nMax, nMin: bl.nMin, eMax: bl.eMax, eMin: bl.eMin, uMax: bl.uMax, uMin: bl.uMin,
            nRange: (bl.nMax||0) - (bl.nMin||0), eRange: (bl.eMax||0) - (bl.eMin||0), uRange: (bl.uMax||0) - (bl.uMin||0),
            ratioAvg: bl.ratioAvg, ratioMax: bl.ratioMax||0, ratioMin: bl.ratioMin||0
        };
        this.renderSolutionStats({ solutionStats: s, solutionEpochs: [] });
        document.getElementById('sol-area').style.display = '';

        if (t.evalResults && t.evalResults.length) {
            const best = Math.max(...t.evalResults.map(x => x.fixRate || 0), 0);
            document.getElementById('eval-results').innerHTML = t.evalResults.map(x => {
                const fr = x.fixRate || 0;
                const isBest = fr === best && best > 0;
                const cls = fr > 80 ? 'var(--green)' : fr > 50 ? 'var(--orange)' : 'var(--red)';
                return `<div class="eval-result ${isBest ? 'best' : ''}">
                    <span style="min-width:70px;font-weight:600">${esc(x.label || '')}${isBest ? ' ★' : ''}</span>
                    <span style="flex:1"><div class="fill" style="width:${fr}%;background:${cls}"></div></span>
                    <span style="font-weight:700">${fr}%</span></div>`;
            }).join('');
            document.getElementById('param-area').style.display = '';
        }

        const d = await fetch('/api/tasks/' + id + '?part=details').then(r => r.json());
        const epochs = (d[roverId] && d[roverId].epochs) || [];
        if (epochs.length) {
            Charts.drawFixTimeline(document.getElementById('cv-fix'), epochs);
            Charts.drawCoordDeviation(document.getElementById('cv-dev'), epochs, { lat: bl.fixLat || 0, lon: bl.fixLon || 0, hgt: bl.fixHgt || 0 });
        }
        toast('已加载: ' + (t.label || id), 'ok');
    },

    async clearAllTasks() {
        if (!confirm('确认清空全部历史任务？')) return;
        await fetch('/api/tasks', { method: 'DELETE' });
        document.getElementById('task-list').innerHTML = '<div style="padding:7px 10px;font-size:10px;color:var(--text2)">暂无</div>';
        toast('已清空', 'ok');
    },

    /* ══════════ Config Modal ══════════ */
    showConfig() {
        const stations = this.getStations();
        if (!this.pairs.length) this.pairs = [];

        let html = '';
        if (!stations.length) {
            html += '<div style="padding:8px;font-size:11px;color:var(--text2);margin-bottom:8px">⚠ 未扫描，站列表为空。请先扫描，或手动输入站号。</div>';
        }
        html += '<div style="max-height:340px;overflow-y:auto">';
        this.pairs.forEach((p, i) => html += this._pairRowHtml(p, i));
        html += '</div><div id="anchor"></div>';
        document.getElementById('modal-body').innerHTML = html;
        document.getElementById('modal').classList.add('show');
    },

    _pairRowHtml(p, i) {
        const ss = this.getStations();
        let bo, ro;
        if (ss.length > 0) {
            bo = ss.map(s => `<option value="${esc(s.stationId)}" ${s.stationId === p.baseId ? 'selected' : ''}>${esc(s.stationId)}</option>`).join('');
            ro = ss.map(s => `<option value="${esc(s.stationId)}" ${s.stationId === p.roverId ? 'selected' : ''}>${esc(s.stationId)}</option>`).join('');
        } else {
            bo = p.baseId ? `<option value="${esc(p.baseId)}" selected>${esc(p.baseId)}</option>` : '';
            ro = p.roverId ? `<option value="${esc(p.roverId)}" selected>${esc(p.roverId)}</option>` : '';
        }
        return `<div class="pair-builder-row pair-row" data-id="${esc(p.id)}">
            <span style="font-weight:700;font-size:10px;color:var(--text2)">#${i + 1}</span>
            <select class="sel-base" style="max-width:160px">${bo}</select>
            <span style="color:var(--text2)">→</span>
            <select class="sel-rover" style="max-width:160px">${ro}</select>
            <input type="text" class="inp-label" value="${esc(p.label || '')}" placeholder="备注" style="flex:1;min-width:70px">
            <button class="btn sm danger" onclick="this.closest('.pair-row').remove()">✕</button></div>`;
    },

    addPair() {
        const id = 'pair_' + Date.now();
        const row = this._pairRowHtml({ id, baseId: '', roverId: '', label: '' }, this.pairs.length);
        document.getElementById('anchor').insertAdjacentHTML('beforebegin', row);
    },

    async savePairs() {
        const rows = document.querySelectorAll('#modal-body .pair-row');
        const arr = [];
        rows.forEach((el, i) => {
            const b = el.querySelector('.sel-base').value;
            const r = el.querySelector('.sel-rover').value;
            const l = el.querySelector('.inp-label').value.trim();
            if (!b || !r) return;
            arr.push({ id: el.dataset.id || ('pair_' + Date.now() + '_' + i), baseId: b, roverId: r, label: l || (b + '→' + r) });
        });
        this.pairs = arr;
        await fetch('/api/station-config', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ pairs: arr })
        });
        this.closeModal();
        this.renderSidebar();
        toast('已保存', 'ok');
    },

    closeModal() { document.getElementById('modal').classList.remove('show'); },

    /* ══════════ Parameter Tuning ══════════ */
    initParamSets() {
        if (this.paramSets.length) return;
        this.paramSets = [
            { label: '默认', mode: 'kinematic', sysG: true, sysC: true, nf: 2, elevMask: 15, snrMask: 30, maxAge: 30, rejionno: 30, iono: 'broadcast', trop: 'saas', modear: 'continuous', thresar: 3.0, soltype: 'forward' },
            { label: '收紧', mode: 'kinematic', sysG: true, sysC: true, nf: 2, elevMask: 25, snrMask: 35, maxAge: 15, rejionno: 15, iono: 'broadcast', trop: 'saas', modear: 'continuous', thresar: 3.0, soltype: 'forward' },
            { label: '放宽', mode: 'kinematic', sysG: true, sysC: true, nf: 1, elevMask: 10, snrMask: 25, maxAge: 60, rejionno: 50, iono: 'off', trop: 'off', modear: 'fix-and-hold', thresar: 2.0, soltype: 'combined' },
        ];
        this.renderParamSets();
    },

    addParamSet() {
        this.paramSets.push({ label: '新建' + (this.paramSets.length + 1), mode: 'kinematic', sysG: true, sysC: true, nf: 2, elevMask: 15, snrMask: 30, maxAge: 30, rejionno: 30, iono: 'broadcast', trop: 'saas', modear: 'continuous', thresar: 3.0, soltype: 'forward' });
        this.renderParamSets();
        document.querySelector('.param-card:last-of-type')?.scrollIntoView({ behavior: 'smooth' });
    },

    removeParamSet(i) {
        if (this.paramSets.length <= 1) return;
        this.paramSets.splice(i, 1);
        this.renderParamSets();
    },

    updateParam(i, field, val) {
        const p = this.paramSets[i];
        if (field === 'label') p.label = val;
        else if (field === 'mode') p.mode = val;
        else if (field === 'iono' || field === 'trop' || field === 'modear' || field === 'soltype') p[field] = val;
        else if (field === 'sysG' || field === 'sysC') p[field] = val === 'true';
        else if (field === 'nf') p[field] = parseInt(val) || 2;
        else if (field === 'thresar') p[field] = parseFloat(val) || 3.0;
        else p[field] = parseInt(val) || 0;
    },

    readParamUI(idx) {
        const p = this.paramSets[idx];
        const card = document.querySelectorAll('.param-card')[idx];
        if (!card) return;
        p.mode = card.querySelector('[data-f="mode"]')?.value || p.mode;
        p.nf = parseInt(card.querySelector('[data-f="nf"]')?.value) || 2;
        p.elevMask = parseInt(card.querySelector('[data-f="elevMask"]')?.value) || 15;
        p.snrMask = parseInt(card.querySelector('[data-f="snrMask"]')?.value) || 30;
        p.maxAge = parseInt(card.querySelector('[data-f="maxAge"]')?.value) || 30;
        p.rejionno = parseInt(card.querySelector('[data-f="rejionno"]')?.value) || 30;
        p.iono = card.querySelector('[data-f="iono"]')?.value || 'broadcast';
        p.trop = card.querySelector('[data-f="trop"]')?.value || 'saas';
        p.modear = card.querySelector('[data-f="modear"]')?.value || 'continuous';
        p.thresar = parseFloat(card.querySelector('[data-f="thresar"]')?.value) || 3.0;
        p.soltype = card.querySelector('[data-f="soltype"]')?.value || 'forward';
    },

    renderParamSets() {
        const div = document.getElementById('param-sets');
        div.innerHTML = this.paramSets.map((p, i) => `
            <div class="param-card">
                <div class="param-card-head">
                    <input type="text" value="${esc(p.label)}" onchange="App.updateParam(${i},'label',this.value)">
                    <button class="btn sm danger" onclick="App.removeParamSet(${i})">×</button>
                </div>
                <div class="param-card-grid">
                    <div class="param-cell"><label>定位模式</label><select data-f="mode" onchange="App.updateParam(${i},'mode',this.value)">
                        ${opts(['kinematic','static','dgps','ppp','single'],p.mode)}</select></div>
                    <div class="param-cell"><label>GPS</label><select data-f="sysG" onchange="App.updateParam(${i},'sysG',this.value)">
                        <option value="true" ${p.sysG?'selected':''}>✓</option><option value="false" ${!p.sysG?'selected':''}>✗</option></select></div>
                    <div class="param-cell"><label>BDS</label><select data-f="sysC" onchange="App.updateParam(${i},'sysC',this.value)">
                        <option value="true" ${p.sysC?'selected':''}>✓</option><option value="false" ${!p.sysC?'selected':''}>✗</option></select></div>
                    <div class="param-cell"><label>频率数</label><select data-f="nf" onchange="App.updateParam(${i},'nf',this.value)">
                        <option value="1" ${(p.nf||2)===1?'selected':''}>1 (单频L1)</option>
                        <option value="2" ${(p.nf||2)===2?'selected':''}>2 (双频L1+L2)</option>
                        <option value="3" ${(p.nf||2)===3?'selected':''}>3 (三频)</option></select></div>
                    <div class="param-cell"><label>高度角掩模°</label><input data-f="elevMask" type="number" value="${p.elevMask}" min="0" max="45" onchange="App.updateParam(${i},'elevMask',this.value)"></div>
                    <div class="param-cell"><label>SNR掩模 dB</label><input data-f="snrMask" type="number" value="${p.snrMask}" min="0" max="60" onchange="App.updateParam(${i},'snrMask',this.value)"></div>
                    <div class="param-cell"><label>差分龄期上限 s</label><input data-f="maxAge" type="number" value="${p.maxAge}" min="0" max="300" onchange="App.updateParam(${i},'maxAge',this.value)"></div>
                    <div class="param-cell"><label>新息拒绝阈值 m</label><input data-f="rejionno" type="number" value="${p.rejionno}" min="0" max="100" onchange="App.updateParam(${i},'rejionno',this.value)"></div>
                    <div class="param-cell"><label>电离层</label><select data-f="iono" onchange="App.updateParam(${i},'iono',this.value)">
                        ${optsIono(p.iono)}</select></div>
                    <div class="param-cell"><label>对流层</label><select data-f="trop" onchange="App.updateParam(${i},'trop',this.value)">
                        ${optsTrop(p.trop)}</select></div>
                    <div class="param-cell"><label>模糊度</label><select data-f="modear" onchange="App.updateParam(${i},'modear',this.value)">
                        ${optsModear(p.modear||'continuous')}</select></div>
                    <div class="param-cell"><label>Ratio阈值</label><input data-f="thresar" type="number" value="${p.thresar||3.0}" min="0" max="99" step="0.5" onchange="App.updateParam(${i},'thresar',this.value)"></div>
                    <div class="param-cell"><label>解算方向</label><select data-f="soltype" onchange="App.updateParam(${i},'soltype',this.value)">
                        ${optsSoltype(p.soltype||'forward')}</select></div>
                </div></div>`).join('');
    },

    async runEvaluate() {
        if (!this.currentData && !this.activePairId) { toast('请先加载数据或选择配对组', 'warn'); return; }
        this.paramSets.forEach((_, i) => this.readParamUI(i));

        const evalSh = parseInt(document.getElementById('eval-sh').value) || 0;
        const evalEh = parseInt(document.getElementById('eval-eh').value) || 23;

        let filePairs = this._buildFilePairs(evalSh, evalEh);

        if (filePairs.length === 0) { toast('无测站文件路径，请先加载或直接输入', 'warn'); return; }

        const totalRuns = filePairs.length * this.paramSets.length;
        const evalLog = document.getElementById('eval-log');
        evalLog.style.display = '';
        evalLog.innerHTML = `<div style="color:var(--accent)">🚀 启动评估: ${filePairs.length}个时段 × ${this.paramSets.length}组参数 = ${totalRuns}次解算</div>`;

        const paramSetsPayload = this.paramSets.map(p => {
            const obj = {
                label: p.label, mode: p.mode, elevationMask: p.elevMask, snrMask: p.snrMask,
                maxAge: p.maxAge, rejionno: p.rejionno, iono: p.iono, trop: p.trop,
                modear: p.modear || 'continuous', thresar: p.thresar || 3.0,
                soltype: p.soltype || 'forward',
                sysG: p.sysG, sysC: p.sysC, nf: p.nf || 2
            };
            return obj;
        });

        try {
            const startR = await fetch('/api/analyze/evaluate', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    cacheKey: (this.currentData && this.currentData.cacheKey) || '',
                    filePairs,
                    paramSets: paramSetsPayload,
                    async: true
                })
            }).then(r => r.json());

            const evalId = startR.evalId;
            if (!evalId) {
                this._handleEvalResult(startR, filePairs);
                return;
            }

            this.evalId = evalId;
            if (this.evalEventSource) this.evalEventSource.close();
            this.evalEventSource = new EventSource('/api/analyze/evaluate-progress?evalId=' + evalId);

            this.evalEventSource.addEventListener('progress', (e) => {
                const d = JSON.parse(e.data);
                const icon = d.status === 'running' ? '🕐' : '✅';
                const info = d.epochs ? ` ${d.epochs}历元, Fixed ${d.fixRate}%` : '';
                this._appendEvalLog(`${icon} [${d.paramLabel}] ${d.file} ... (${d.fileIdx + 1}/${d.totalFiles})${info}`, d.status === 'running' ? 'var(--text2)' : 'var(--green)');
            });

            this.evalEventSource.addEventListener('paramDone', (e) => {
                const d = JSON.parse(e.data);
                this._appendEvalLog(`📊 [${d.paramLabel}] 完成: ${d.totalEpochs}历元, Fixed ${d.fixRate}%`, 'var(--accent)');
            });

            this.evalEventSource.addEventListener('done', (e) => {
                const d = JSON.parse(e.data);
                this.evalEventSource.close();
                this.evalEventSource = null;
                this._handleEvalResult(d, filePairs);
            });

            this.evalEventSource.addEventListener('error', (e) => {
                if (e.data) {
                    const d = JSON.parse(e.data);
                    this._appendEvalLog(`❌ ${d.msg}`, 'var(--red)');
                }
            });

            this.evalEventSource.onerror = () => {
                if (this.evalEventSource) {
                    this.evalEventSource.close();
                    this.evalEventSource = null;
                    this._appendEvalLog('⚠ 连接断开', 'var(--orange)');
                }
            };

        } catch (e) {
            toast('评估请求失败: ' + e.message, 'err');
        }
    },

    _buildFilePairs(evalSh, evalEh) {
        let filePairs = [];
        const pair = this.pairs.find(p => p.id === this.activePairId);
        const root = (this.currentData && this.currentData.root) || document.getElementById('root-path').value.trim();
        const dates = (this.currentData && this.currentData.dates) || this.getSelectedDates();
        const roverId = (this.currentData && this.currentData.roverId) || (pair && pair.roverId);
        const baseId = (this.currentData && this.currentData.baseId) || (pair && pair.baseId);

        if (roverId && baseId && dates.length && root) {
            for (const date of dates) {
                const roverDir = root + '\\' + roverId + '\\' + date;
                const baseDir = root + '\\' + baseId + '\\' + date;
                for (let h = evalSh; h <= evalEh; h++) {
                    filePairs.push({
                        roverFile: this._findRtcmFile(roverDir, h),
                        baseFile: this._findRtcmFile(baseDir, h),
                        date: date, hour: h
                    });
                }
            }
        }

        if (filePairs.length === 0 && this.currentData && this.currentData.roverFile) {
            filePairs.push({ roverFile: this.currentData.roverFile, baseFile: this.currentData.baseFile || '' });
        }

        if (filePairs.length === 0) {
            const directRover = document.getElementById('direct-rover').value.trim();
            const directBase = document.getElementById('direct-base').value.trim();
            if (directRover) {
                filePairs.push({ roverFile: directRover, baseFile: directBase });
            }
        }

        return filePairs;
    },

    _appendEvalLog(msg, color) {
        const evalLog = document.getElementById('eval-log');
        const line = document.createElement('div');
        line.style.color = color || 'var(--text2)';
        line.textContent = msg;
        evalLog.appendChild(line);
        if (evalLog.children.length > 80) evalLog.removeChild(evalLog.firstChild);
        evalLog.scrollTop = evalLog.scrollHeight;
    },

    _handleEvalResult(r, filePairs) {
        if (r.error) { toast(r.error, 'err'); }

        const results = r.results || [];
        const best = Math.max(...results.map(x => x.fixRate || 0), 0);

        document.getElementById('eval-results').innerHTML = results.map(x => {
            const fr = x.fixRate != null ? x.fixRate : x.keepRate;
            const isBest = fr === best && best > 0;
            const cls = fr > 80 ? 'var(--green)' : fr > 50 ? 'var(--orange)' : 'var(--red)';
            const pairInfo = x.pairCount ? ` (${x.pairCount}时段)` : '';
            return `<div class="eval-result ${isBest ? 'best' : ''}">
                <span style="min-width:70px;font-weight:600">${esc(x.label || '')}${isBest ? ' ★' : ''}</span>
                <span style="flex:1"><div class="fill" style="width:${fr}%;background:${cls}"></div></span>
                <span style="font-weight:700">${fr}%</span>
                <span style="font-size:10px;color:var(--text2)">${x.totalEpochs || 0}历元${pairInfo}</span></div>`;
        }).join('');

        this.evalResults = results;
        this.bestIdx = r.bestIdx != null ? r.bestIdx : -1;
        if (r.evalId) this.evalId = r.evalId;

        if (results.length > 0) {
            const solArea = document.getElementById('sol-area');
            solArea.style.display = '';
            document.getElementById('btn-track').style.display = '';

            const bestLabel = r.bestLabel || '最佳';
            const bestResult = results.find(x => x.label === bestLabel) || results[0];
            const totalE = bestResult.totalEpochs || 0;
            const fixE = bestResult.fixEpochs || 0;
            const floatE = bestResult.floatEpochs || 0;
            const singleE = totalE - fixE - floatE;
            const fixPct = totalE ? Math.round(fixE / totalE * 1000) / 10 : 0;
            const floatPct = totalE ? Math.round(floatE / totalE * 1000) / 10 : 0;
            const singlePct = totalE ? Math.round(singleE / totalE * 1000) / 10 : 0;

            document.getElementById('sol-stats').innerHTML = `
                <div style="font-size:11px;color:var(--accent);margin-bottom:6px;font-weight:600">📊 最佳参数: ${esc(bestLabel)}</div>
                <div class="sol-stat-card"><div class="val c-fix">${fixPct}%</div><div class="lbl">Fixed (${fixE}历元)</div></div>
                <div class="sol-stat-card"><div class="val c-float">${floatPct}%</div><div class="lbl">Float (${floatE}历元)</div></div>
                <div class="sol-stat-card"><div class="val c-single">${singlePct}%</div><div class="lbl">Single (${singleE}历元)</div></div>
                <div class="sol-stat-card"><div class="val">${totalE}</div><div class="lbl">总历元</div></div>`;

            document.getElementById('sol-coord').innerHTML = `<div style="font-weight:600;margin-bottom:4px">📌 固定坐标均值</div><div style="color:var(--text2);font-size:11px">打开轨迹图查看详细坐标</div>`;
            document.getElementById('sol-range').innerHTML = '';
            document.getElementById('sol-ratio').innerHTML = '';

            this._loadEvalCharts();
        }

        this._appendEvalLog(`✅ 评估完成: ${results.length}组参数, ${filePairs.length}个时段`, 'var(--green)');
        toast(`评估完成: ${results.length}组参数, ${filePairs.length}个时段`, 'ok');
    },

    async _loadEvalCharts() {
        if (!this.evalId || !this.evalResults || !this.evalResults.length) return;
        const paramIdx = this.bestIdx >= 0 ? this.bestIdx : 0;
        try {
            const url = `/api/analyze/track-data?evalId=${encodeURIComponent(this.evalId)}&paramIdx=${paramIdx}&resolution=overview&days=7&maxPoints=3000`;
            const resp = await fetch(url);
            const data = await resp.json();
            if (data.error) return;
            const epochs = data.epochs || [];
            if (!epochs.length) return;

            const fixEpochs = epochs.filter(e => e.fixMode === 1);
            const refEpochs = fixEpochs.length > 0 ? fixEpochs : epochs;
            const median = arr => { const s = [...arr].sort((a, b) => a - b); const m = Math.floor(s.length / 2); return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2; };
            const refPoint = {
                lat: median(refEpochs.map(e => e.latitude)),
                lon: median(refEpochs.map(e => e.longitude)),
                hgt: median(refEpochs.map(e => e.height))
            };

            requestAnimationFrame(() => {
                const cvFix = document.getElementById('cv-fix');
                const cvDev = document.getElementById('cv-dev');
                if (cvFix && epochs.length) Charts.drawFixTimeline(cvFix, epochs);
                if (cvDev && epochs.length) Charts.drawCoordDeviation(cvDev, epochs, refPoint);
            });
        } catch (e) { console.warn('加载评估图表失败:', e); }
    },

    showHelp() {
        document.getElementById('help-modal').classList.add('show');
    },

    showTrackPlot() {
        if (!this.evalId) { toast('暂无评估数据，请先评估', 'warn'); return; }
        document.getElementById('track-modal').classList.add('show');

        const sel = document.getElementById('track-param-sel');
        if (this.evalResults && this.evalResults.length) {
            sel.innerHTML = this.evalResults.map((r, i) => `<option value="${i}">${esc(r.label || '参数' + (i + 1))}</option>`).join('');
            if (this.bestIdx >= 0 && this.bestIdx < this.evalResults.length) sel.value = this.bestIdx;
        }

        const cvTrack = document.getElementById('cv-track');
        const ctx = cvTrack ? cvTrack.getContext('2d') : null;
        if (ctx) {
            ctx.clearRect(0, 0, cvTrack.width, cvTrack.height);
            ctx.fillStyle = '#0d1117';
            ctx.fillRect(0, 0, cvTrack.width, cvTrack.height);
            ctx.fillStyle = '#8b949e';
            ctx.font = '14px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('选择参数和分辨率后点击 "🔄 加载轨迹"', cvTrack.width / 2, cvTrack.height / 2);
        }
        document.getElementById('track-chart-label').textContent = 'N / E / U 偏差时序';
        document.getElementById('track-warn').style.display = 'none';
    },

    onTrackSettingChange() {
        this._checkTrackWarning();
    },

    onTrackParamChange() {
        if (this._trackLoaded) this.loadTrackData();
    },

    _checkTrackWarning() {
        const res = document.getElementById('track-resolution').value;
        const days = document.getElementById('track-days').value;
        const maxPts = document.getElementById('track-maxpts').value;
        const warn = document.getElementById('track-warn');

        if (res === 'detail' && (days === '0' || parseInt(days) >= 7) && maxPts === '0') {
            warn.textContent = '⚠ 全量数据可能较大，加载较慢';
            warn.style.display = '';
        } else if (res === 'detail' && maxPts === '0') {
            warn.textContent = '⚠ 详细+不限量，数据可能较大';
            warn.style.display = '';
        } else {
            warn.style.display = 'none';
        }
    },

    async loadTrackData() {
        if (!this.evalId) { toast('无评估ID', 'warn'); return; }

        const paramIdx = parseInt(document.getElementById('track-param-sel').value) || 0;
        const resolution = document.getElementById('track-resolution').value;
        const days = parseInt(document.getElementById('track-days').value) || 0;
        const maxPoints = parseInt(document.getElementById('track-maxpts').value) || 0;

        const btn = document.getElementById('btn-load-track');
        btn.textContent = '⏳ 加载中...';
        btn.disabled = true;

        try {
            const url = `/api/analyze/track-data?evalId=${encodeURIComponent(this.evalId)}&paramIdx=${paramIdx}&resolution=${resolution}&days=${days}&maxPoints=${maxPoints}`;
            const resp = await fetch(url);
            const data = await resp.json();

            if (data.error) { toast(data.error, 'err'); return; }

            const epochs = data.epochs || [];
            if (!epochs.length) { toast('无轨迹数据', 'warn'); return; }

            this._trackLoaded = true;
            const label = data.label || '';
            document.getElementById('track-chart-label').textContent = `${label} — N / E / U 偏差时序 (${data.returnedEpochs}/${data.totalEpochs}点)`;

            const fixEpochs = epochs.filter(e => e.fixMode === 1);
            const refEpochs = fixEpochs.length > 0 ? fixEpochs : epochs;
            const median = arr => { const s = [...arr].sort((a, b) => a - b); const m = Math.floor(s.length / 2); return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2; };
            const refPoint = {
                lat: median(refEpochs.map(e => e.latitude)),
                lon: median(refEpochs.map(e => e.longitude)),
                hgt: median(refEpochs.map(e => e.height))
            };

            requestAnimationFrame(() => {
                const cvTrack = document.getElementById('cv-track');
                if (cvTrack) Charts.drawTrackPlot(cvTrack, epochs, refPoint);
            });

            document.getElementById('sol-coord').innerHTML = `
                <div style="font-weight:600;margin-bottom:4px">📌 参考坐标 (Fixed中位数)</div>
                <div class="row"><span>Lat</span><b>${refPoint.lat.toFixed(8)}°</b></div>
                <div class="row"><span>Lon</span><b>${refPoint.lon.toFixed(8)}°</b></div>
                <div class="row"><span>H</span><b>${refPoint.hgt.toFixed(3)}m</b></div>`;

        } catch (e) {
            toast('加载轨迹失败: ' + e.message, 'err');
        } finally {
            btn.textContent = '🔄 加载轨迹';
            btn.disabled = false;
        }
    },

    closeTrackModal() {
        document.getElementById('track-modal').classList.remove('show');
        this._trackLoaded = false;
    },

    _findRtcmFile(dir, hour) {
        return dir + '\\' + hour + '.rtcm3';
    }
};

function opts(arr, cur) { return arr.map(v => `<option value="${v}" ${v === cur ? 'selected' : ''}>${v}</option>`).join(''); }
function optsIono(cur) { return ['off:关闭','broadcast:广播','ifree:无电离层','est:估计'].map(s => { const [v, l] = s.split(':'); return `<option value="${v}" ${v === cur ? 'selected' : ''}>${l}</option>`; }).join(''); }
function optsTrop(cur) { return ['off:关闭','saas:Saastamoinen','sbas:SBAS','est:估计'].map(s => { const [v, l] = s.split(':'); return `<option value="${v}" ${v === cur ? 'selected' : ''}>${l}</option>`; }).join(''); }
function optsModear(cur) { return ['continuous:连续','instantaneous:瞬时','fix-and-hold:固定保持','off:关闭'].map(s => { const [v, l] = s.split(':'); return `<option value="${v}" ${v === cur ? 'selected' : ''}>${l}</option>`; }).join(''); }
function optsSoltype(cur) { return ['forward:前向','backward:后向','combined:组合'].map(s => { const [v, l] = s.split(':'); return `<option value="${v}" ${v === cur ? 'selected' : ''}>${l}</option>`; }).join(''); }
function esc(s) { return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
function toast(m, t) {
    const e = document.getElementById('toast');
    e.textContent = m; e.className = 'toast ' + (t || '');
    setTimeout(() => e.classList.add('hidden'), 2800);
}
App.init();