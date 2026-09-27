const Tune = {
    scanResult: null,
    pairs: [],
    activePairId: null,
    paramSets: [],
    currentData: null,

    /* ══════════ 初始化 ══════════ */
    async init() {
        if (localStorage.getItem('rtklib_theme') === 'light') document.body.classList.add('light');
        await this.loadPairs();
        this.loadTaskList();
        this.renderPairList();
    },

    toggleTheme() {
        const light = document.body.classList.toggle('light');
        localStorage.setItem('rtklib_theme', light ? 'light' : 'dark');
        const btn = document.querySelector('.theme-btn');
        if (btn) btn.textContent = light ? '☾' : '☀';
    },

    /* ══════════ 扫描 ══════════ */
    async doScan() {
        const root = document.getElementById('root-path').value.trim();
        if (!root) { U.toast('请输入根目录', 'err'); return; }
        U.toast('扫描中...');
        try {
            const r = await U.api('/api/scan', { method: 'POST', body: { root } });
            this.scanResult = r;
            this.renderPairList();
            document.getElementById('scan-status').textContent = (r.stations || []).length + ' 个站';
            U.toast((r.stations || []).length + ' 个站已发现', 'ok');
        } catch (e) {
            U.toast('扫描失败: ' + e.message, 'err');
        }
    },

    getStations() {
        return this.scanResult ? (this.scanResult.stations || []) : [];
    },

    /* ══════════ 配对组 ══════════ */
    async loadPairs() {
        try {
            const cfg = await U.api('/api/pairs');
            this.pairs = cfg.pairs || [];
        } catch (e) {
            this.pairs = [];
        }
    },

    renderPairList() {
        const el = document.getElementById('pair-list');
        if (!el) return;
        if (!this.pairs.length) {
            el.innerHTML = '<div class="empty-mini">扫描后点 ⚙ 创建</div>';
            return;
        }
        el.innerHTML = this.pairs.map(p => `
      <div class="side-item ${this.activePairId === p.id ? 'active' : ''}" onclick="Tune.selectPair('${p.id}')">
        <span>🔗</span>
        <span class="lbl">${U.esc(p.label || (p.baseId + '→' + p.roverId))}</span>
        <span class="rmv" onclick="event.stopPropagation();Tune.removePair('${p.id}')">×</span>
      </div>`).join('');
    },

    selectPair(id) {
        this.activePairId = id;
        this.renderPairList();
        this.onSelectionChange();
    },

    async removePair(id) {
        this.pairs = this.pairs.filter(p => p.id !== id);
        if (this.activePairId === id) this.activePairId = null;
        this.renderPairList();
        try {
            await U.api('/api/pairs', { method: 'PUT', body: { pairs: this.pairs } });
        } catch (e) {
            U.toast('删除失败: ' + e.message, 'err');
        }
    },

    onSelectionChange() {
        const bar = document.getElementById('sel-bar');
        const welcome = document.getElementById('welcome');
        if (!bar || !welcome) return;

        if (!this.activePairId) {
            bar.style.display = 'none';
            welcome.style.display = '';
            return;
        }
        bar.style.display = '';
        welcome.style.display = 'none';

        const pair = this.pairs.find(p => p.id === this.activePairId);
        if (!pair) return;
        const rover = this.getStations().find(s => s.stationId === pair.roverId);
        const ds = document.getElementById('sel-date');
        if (!ds) return;
        ds.innerHTML = '';
        if (rover && rover.dates) {
            rover.dates.forEach(d => ds.appendChild(new Option(d.date, d.date)));
        }
        if (ds.options.length) ds.selectedIndex = ds.options.length - 1;
        this.onDateChange();
    },

    onDateChange() {
        const pair = this.pairs.find(p => p.id === this.activePairId);
        if (!pair) return;
        const rover = this.getStations().find(s => s.stationId === pair.roverId);
        const dateEl = document.getElementById('sel-date');
        if (!dateEl) return;
        const date = dateEl.value;
        const de = rover && rover.dates ? rover.dates.find(d => d.date === date) : null;
        if (de && de.files && de.files.length) {
            const hrs = de.files.map(f => f.hour).filter(h => h >= 0);
            if (hrs.length) {
                document.getElementById('sel-sh').value = Math.min(...hrs);
                document.getElementById('sel-eh').value = Math.max(...hrs);
            }
        }
    },

    /* ══════════ 配对组弹窗 ══════════ */
    openPairModal() {
        const stations = this.getStations();
        let html = '';
        if (!stations.length) {
            html += '<div class="modal-hint">⚠ 未扫描，站列表为空。可手动输入站号。</div>';
        }
        html += '<div class="pair-rows" id="pair-rows"></div>';
        document.getElementById('modal-body').innerHTML = html;
        const rows = document.getElementById('pair-rows');
        this.pairs.forEach(p => rows.insertAdjacentHTML('beforeend', this._pairRowHtml(p)));
        if (!this.pairs.length) this.addPairRow();
        document.getElementById('modal').classList.add('show');
    },

    addPairRow() {
        document.getElementById('pair-rows').insertAdjacentHTML('beforeend', this._pairRowHtml({
            id: 'pair_' + Date.now(), baseId: '', roverId: '', label: ''
        }));
    },

    _pairRowHtml(p) {
        const ss = this.getStations();
        let bo, ro;
        if (ss.length) {
            bo = ss.map(s => `<option value="${U.esc(s.stationId)}" ${s.stationId === p.baseId ? 'selected' : ''}>${U.esc(s.stationId)}</option>`).join('');
            ro = ss.map(s => `<option value="${U.esc(s.stationId)}" ${s.stationId === p.roverId ? 'selected' : ''}>${U.esc(s.stationId)}</option>`).join('');
        } else {
            bo = p.baseId ? `<option value="${U.esc(p.baseId)}" selected>${U.esc(p.baseId)}</option>` : '';
            ro = p.roverId ? `<option value="${U.esc(p.roverId)}" selected>${U.esc(p.roverId)}</option>` : '';
        }
        return `<div class="pair-row" data-id="${U.esc(p.id)}">
      <select class="sel-base">${bo}</select>
      <span class="arrow">→</span>
      <select class="sel-rover">${ro}</select>
      <input type="text" class="inp-label" value="${U.esc(p.label || '')}" placeholder="备注">
      <button class="icon-btn" onclick="this.closest('.pair-row').remove()">✕</button>
    </div>`;
    },

    async savePairs() {
        const rows = document.querySelectorAll('#pair-rows .pair-row');
        const arr = [];
        rows.forEach((el, i) => {
            const b = el.querySelector('.sel-base').value.trim();
            const r = el.querySelector('.sel-rover').value.trim();
            const l = el.querySelector('.inp-label').value.trim();
            if (!b || !r) return;
            arr.push({ id: el.dataset.id || ('pair_' + Date.now() + '_' + i), baseId: b, roverId: r, label: l || (b + '→' + r) });
        });
        this.pairs = arr;
        try {
            await U.api('/api/pairs', { method: 'PUT', body: { pairs: arr } });
            this.closeModal();
            this.renderPairList();
            U.toast('已保存', 'ok');
        } catch (e) {
            U.toast('保存失败: ' + e.message, 'err');
        }
    },

    closeModal() {
        document.getElementById('modal').classList.remove('show');
    },

    /* ══════════ 加载时段 ══════════ */
    async loadStation() {
        const pair = this.pairs.find(p => p.id === this.activePairId);
        if (!pair) { U.toast('请选择配对组', 'warn'); return; }
        const date = document.getElementById('sel-date').value;
        const sh = parseInt(document.getElementById('sel-sh').value) || 0;
        const eh = parseInt(document.getElementById('sel-eh').value) || 23;
        if (!date) { U.toast('请选择日期', 'warn'); return; }

        U.toast('加载中...');
        const root = document.getElementById('root-path').value.trim();
        try {
            const r = await U.api('/api/load', {
                method: 'POST',
                body: { root, stationId: pair.roverId, baseId: pair.baseId, date, startHour: sh, endHour: eh }
            });
            this.currentData = r;
            this.renderSummary(r);
            this.renderSolutionStats(r);
            document.getElementById('sol-area').style.display = '';
            this.initParamSets();
            document.getElementById('param-area').style.display = '';
            document.getElementById('eval-sh').value = sh;
            document.getElementById('eval-eh').value = eh;
            U.toast('加载完成', 'ok');
        } catch (e) {
            U.toast('加载失败: ' + e.message, 'err');
        }
    },

    renderSummary(r) {
        const gaps = r.gaps || [];
        const gapText = gaps.length === 0 ? '✓ 连续' : '⚠ ' + gaps.length + ' 个缺口';
        const gapColor = gaps.length > 5 ? 'var(--red)' : gaps.length > 0 ? 'var(--orange)' : 'var(--green)';
        const el = document.getElementById('rtcm-summary');
        if (!el) return;
        el.style.display = '';
        el.innerHTML = `
      <span class="summary-pill">📡 <b>${r.totalMessages || 0}</b> 条消息</span>
      <span class="summary-pill">⏱ <b>${r.durationSec || 0}s</b></span>
      <span class="summary-pill">📋 <b>${(r.typeSummary || []).length}</b> 种类型</span>
      <span class="summary-pill" style="color:${gapColor}">🔗 ${gapText}</span>`;
    },

    renderSolutionStats(r) {
        const s = r.solutionStats || {};
        const total = s.totalEpochs || 0;
        const el = document.getElementById('sol-stats');
        if (!el) return;
        if (!total) {
            el.innerHTML = '<div class="empty-mini" style="grid-column:1/-1">未找到 .pos 解算文件</div>';
            return;
        }
        const fixCnt = s.fixEpochs || 0;
        const floatCnt = s.floatEpochs || 0;
        const singleCnt = s.singleEpochs || 0;
        const pct = n => total ? Math.round(n / total * 1000) / 10 : 0;
        el.innerHTML = `
      <div class="stat-card"><div class="val c-fix">${s.fixRate || pct(fixCnt)}%</div><div class="lbl">Fixed (${fixCnt})</div></div>
      <div class="stat-card"><div class="val c-float">${pct(floatCnt)}%</div><div class="lbl">Float (${floatCnt})</div></div>
      <div class="stat-card"><div class="val c-single">${pct(singleCnt)}%</div><div class="lbl">Single (${singleCnt})</div></div>
      <div class="stat-card"><div class="val">${total}</div><div class="lbl">总历元</div></div>`;

        const epochs = r.solutionEpochs || [];
        const cvFix = document.getElementById('cv-fix');
        const cvDev = document.getElementById('cv-dev');
        if (cvFix && epochs.length && window.Charts) {
            Charts.drawFixTimeline(cvFix, epochs);
        }
        if (cvDev && epochs.length && window.Charts) {
            Charts.drawCoordDeviation(cvDev, epochs, { lat: s.fixLat || 0, lon: s.fixLon || 0, hgt: s.fixHgt || 0 });
        }
    },

    /* ══════════ 参数组合 ══════════ */
    initParamSets() {
        if (this.paramSets.length) return;
        this.paramSets = [
            this._mkParam('默认', 15, 30, 30, 30, 'broadcast', 'saas', 'continuous', 3.0, 'forward'),
            this._mkParam('收紧', 25, 35, 15, 15, 'broadcast', 'saas', 'continuous', 3.0, 'forward'),
            this._mkParam('放宽', 10, 25, 60, 50, 'off', 'off', 'fix-and-hold', 2.0, 'combined')
        ];
        this.renderParamSets();
    },

    _mkParam(label, elevMask, snrMask, maxAge, rejionno, iono, trop, modear, thresar, soltype) {
        return {
            label,
            mode: 'kinematic',
            systems: ['GPS', 'BDS'],
            freqs: ['L1', 'L2'],
            elevMask, snrMask, maxAge, rejionno,
            iono, trop, modear, thresar, soltype
        };
    },

    addParamSet() {
        const last = this.paramSets[this.paramSets.length - 1];
        const base = last
            ? JSON.parse(JSON.stringify(last))
            : this._mkParam('新建', 15, 30, 30, 30, 'broadcast', 'saas', 'continuous', 3.0, 'forward');
        base.label = '组合' + (this.paramSets.length + 1);
        this.paramSets.push(base);
        this.renderParamSets();
    },

    removeParamSet(i) {
        if (this.paramSets.length <= 1) return;
        this.paramSets.splice(i, 1);
        this.renderParamSets();
    },

    renderParamSets() {
        const el = document.getElementById('param-sets');
        if (!el) return;
        el.innerHTML = this.paramSets.map((p, i) => `
      <div class="param-card">
        <div class="param-card-head">
          <input type="text" value="${U.esc(p.label)}" onchange="Tune.updateParam(${i},'label',this.value)">
          <button class="icon-btn" onclick="Tune.removeParamSet(${i})">✕</button>
        </div>
        <div class="param-card-grid">
          <div class="param-cell"><label>模式</label>
            <select onchange="Tune.updateParam(${i},'mode',this.value)">
              ${this._opts(['kinematic', 'static', 'dgps', 'ppp', 'single'], p.mode)}
            </select></div>
          <div class="param-cell"><label>系统</label>
            ${this._checkgroup(i, 'systems', ['GPS', 'BDS', 'GLO', 'GAL', 'QZS'], p.systems)}</div>
          <div class="param-cell"><label>频率</label>
            ${this._checkgroup(i, 'freqs', ['L1', 'L2', 'L5'], p.freqs)}</div>
          <div class="param-cell"><label>高度角°</label>
            <input type="number" value="${p.elevMask}" onchange="Tune.updateParam(${i},'elevMask',this.value)"></div>
          <div class="param-cell"><label>SNR dB</label>
            <input type="number" value="${p.snrMask}" onchange="Tune.updateParam(${i},'snrMask',this.value)"></div>
          <div class="param-cell"><label>龄期 s</label>
            <input type="number" value="${p.maxAge}" onchange="Tune.updateParam(${i},'maxAge',this.value)"></div>
          <div class="param-cell"><label>新息阈值 m</label>
            <input type="number" value="${p.rejionno}" onchange="Tune.updateParam(${i},'rejionno',this.value)"></div>
          <div class="param-cell"><label>电离层</label>
            <select onchange="Tune.updateParam(${i},'iono',this.value)">
              ${this._optsKV([['off', '关闭'], ['broadcast', '广播'], ['ifree', '无电离层'], ['est', '估计']], p.iono)}
            </select></div>
          <div class="param-cell"><label>对流层</label>
            <select onchange="Tune.updateParam(${i},'trop',this.value)">
              ${this._optsKV([['off', '关闭'], ['saas', 'Saastamoinen'], ['sbas', 'SBAS'], ['est', '估计']], p.trop)}
            </select></div>
          <div class="param-cell"><label>模糊度</label>
            <select onchange="Tune.updateParam(${i},'modear',this.value)">
              ${this._optsKV([['continuous', '连续'], ['instantaneous', '瞬时'], ['fix-and-hold', '固定保持'], ['off', '关闭']], p.modear)}
            </select></div>
          <div class="param-cell"><label>Ratio阈值</label>
            <input type="number" step="0.5" value="${p.thresar}" onchange="Tune.updateParam(${i},'thresar',this.value)"></div>
          <div class="param-cell"><label>解算方向</label>
            <select onchange="Tune.updateParam(${i},'soltype',this.value)">
              ${this._optsKV([['forward', '前向'], ['backward', '后向'], ['combined', '组合']], p.soltype)}
            </select></div>
        </div>
      </div>`).join('');
    },

    _opts(arr, cur) {
        return arr.map(v => `<option value="${v}" ${v === cur ? 'selected' : ''}>${v}</option>`).join('');
    },

    _optsKV(arr, cur) {
        return arr.map(([v, l]) => `<option value="${v}" ${v === cur ? 'selected' : ''}>${l}</option>`).join('');
    },

    _checkgroup(i, key, options, cur) {
        const curArr = cur || [];
        return `<div style="display:flex;gap:4px;flex-wrap:wrap">` + options.map(o =>
            `<label style="display:inline-flex;align-items:center;gap:2px;font-size:9px;cursor:pointer">
        <input type="checkbox" ${curArr.includes(o) ? 'checked' : ''} onchange="Tune.toggleParam(${i},'${key}','${o}',this.checked)">${o}
      </label>`).join('') + `</div>`;
    },

    updateParam(i, key, val) {
        const p = this.paramSets[i];
        if (!p) return;
        if (key === 'label') p.label = val;
        else if (['elevMask', 'snrMask', 'maxAge', 'rejionno'].includes(key)) p[key] = parseInt(val) || 0;
        else if (key === 'thresar') p[key] = parseFloat(val) || 3.0;
        else p[key] = val;
    },

    toggleParam(i, key, opt, on) {
        const p = this.paramSets[i];
        if (!p) return;
        const set = new Set(p[key] || []);
        on ? set.add(opt) : set.delete(opt);
        p[key] = [...set];
    },

    /* ══════════ 评估 ══════════ */
    async runEvaluate() {
        if (!this.currentData) { U.toast('请先加载数据', 'warn'); return; }
        const sh = parseInt(document.getElementById('eval-sh').value) || 0;
        const eh = parseInt(document.getElementById('eval-eh').value) || 23;

        U.toast('评估中...');
        try {
            const r = await U.api('/api/evaluate', {
                method: 'POST',
                body: {
                    cacheKey: this.currentData.cacheKey || '',
                    roverFile: this.currentData.roverFile || '',
                    baseFile: this.currentData.baseFile || '',
                    evalRange: { startHour: sh, endHour: eh },
                    paramSets: this.paramSets
                }
            });
            this.renderEvalResults(r.results || []);
        } catch (e) {
            U.toast('评估失败: ' + e.message, 'err');
        }
    },

    renderEvalResults(results) {
        const best = Math.max(...results.map(x => x.fixRate || 0), 0);
        const el = document.getElementById('eval-results');
        if (!el) return;
        el.innerHTML = results.map(x => {
            const fr = x.fixRate != null ? x.fixRate : (x.keepRate || 0);
            const isBest = fr === best && best > 0;
            const cls = fr > 80 ? 'var(--green)' : fr > 50 ? 'var(--orange)' : 'var(--red)';
            return `<div class="eval-result ${isBest ? 'best' : ''}">
        <span class="lbl">${U.esc(x.label || '')}${isBest ? ' ★' : ''}</span>
        <span class="bar"><div class="fill" style="width:${fr}%;background:${cls}"></div></span>
        <span class="pct">${fr}%</span></div>`;
        }).join('');
    },

    /* ══════════ 历史任务 ══════════ */
    async saveTask() {
        const pair = this.pairs.find(p => p.id === this.activePairId);
        if (!pair || !this.currentData) { U.toast('请先加载数据', 'warn'); return; }

        const evalResults = [];
        let bestLabel = '', bestRate = 0;
        document.querySelectorAll('.eval-result').forEach(el => {
            const lbl = el.querySelector('.lbl')?.textContent?.replace(' ★', '').trim() || '';
            const rate = parseFloat(el.querySelector('.pct')?.textContent) || 0;
            evalResults.push({ label: lbl, fixRate: rate });
            if (rate > bestRate) { bestRate = rate; bestLabel = lbl; }
        });

        const task = {
            label: pair.label || (pair.baseId + '→' + pair.roverId),
            rootPath: document.getElementById('root-path').value.trim(),
            date: document.getElementById('sel-date').value,
            pair,
            paramSets: this.paramSets,
            evalRange: {
                startHour: parseInt(document.getElementById('eval-sh').value) || 0,
                endHour: parseInt(document.getElementById('eval-eh').value) || 23
            },
            bestParam: bestLabel,
            bestFixRate: bestRate,
            summary: {
                baseline: this.currentData.solutionStats || {},
                evalResults
            }
        };
        try {
            const r = await U.api('/api/tasks', { method: 'POST', body: task });
            U.toast('已保存: ' + (r.label || r.id), 'ok');
            this.loadTaskList();
        } catch (e) {
            U.toast('保存失败: ' + e.message, 'err');
        }
    },

    async loadTaskList() {
        try {
            const tasks = await U.api('/api/tasks');
            const el = document.getElementById('task-list');
            if (!el) return;
            if (!tasks || !tasks.length) {
                el.innerHTML = '<div class="empty-mini">暂无</div>';
                return;
            }
            el.innerHTML = tasks.slice(0, 12).map(t => {
                const rate = t.bestFixRate || 0;
                const cls = rate > 80 ? 'good' : rate > 50 ? 'med' : 'bad';
                return `<div class="side-item" onclick="Tune.viewTask('${t.id}')" title="${U.esc(t.label || '')}">
          <span>📋</span>
          <span class="lbl">${U.esc(t.label || t.id)}</span>
          <span class="fr ${cls}">${rate || '-'}%</span>
        </div>`;
            }).join('');
        } catch (e) {
            /* ignore */
        }
    },

    async viewTask(id) {
        try {
            const t = await U.api('/api/tasks/' + id + '?part=full');
            const bl = (t.summary && t.summary.baseline) || {};
            this.paramSets = t.paramSets || [];
            this.renderParamSets();
            document.getElementById('param-area').style.display = '';

            const s = {
                totalEpochs: bl.totalEpochs || bl.fixEpochs || 0,
                fixEpochs: bl.fixEpochs || 0,
                floatEpochs: bl.floatEpochs || 0,
                singleEpochs: bl.singleEpochs || 0,
                fixRate: bl.fixRate || 0
            };
            this.renderSolutionStats({ solutionStats: s, solutionEpochs: [] });
            document.getElementById('sol-area').style.display = '';

            const ev = (t.summary && t.summary.evalResults) || [];
            if (ev.length) this.renderEvalResults(ev);

            U.toast('已加载任务: ' + (t.label || id), 'ok');
        } catch (e) {
            U.toast('加载任务失败: ' + e.message, 'err');
        }
    },

    async clearTasks() {
        if (!confirm('确认清空全部历史任务？')) return;
        try {
            await U.api('/api/tasks', { method: 'DELETE' });
            document.getElementById('task-list').innerHTML = '<div class="empty-mini">暂无</div>';
            U.toast('已清空', 'ok');
        } catch (e) {
            U.toast('清空失败: ' + e.message, 'err');
        }
    }
};

document.addEventListener('DOMContentLoaded', () => Tune.init());