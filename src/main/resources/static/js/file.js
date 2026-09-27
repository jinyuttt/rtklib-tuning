const FileAnalysis = {
    init() {
        if (localStorage.getItem('rtklib_theme') === 'light') document.body.classList.add('light');
        const dz = document.getElementById('drop-zone');
        const input = document.getElementById('file-input');
        dz.onclick = () => input.click();
        input.onchange = () => { if (input.files[0]) this.onFile(input.files[0]); input.value = ''; };

        dz.addEventListener('dragover', e => { e.preventDefault(); dz.classList.add('dragover'); });
        dz.addEventListener('dragleave', () => dz.classList.remove('dragover'));
        dz.addEventListener('drop', e => {
            e.preventDefault(); dz.classList.remove('dragover');
            if (e.dataTransfer.files[0]) this.onFile(e.dataTransfer.files[0]);
        });
    },

    toggleTheme() {
        const light = document.body.classList.toggle('light');
        localStorage.setItem('rtklib_theme', light ? 'light' : 'dark');
        document.querySelector('.theme-btn').textContent = light ? '☾' : '☀';
    },

    async onServerPath() {
        const path = document.getElementById('server-path').value.trim();
        if (!path) { U.toast('请输入文件路径', 'err'); return; }
        document.getElementById('analysis-result').style.display = 'none';
        U.toast('分析中...');
        try {
            const r = await U.api('/api/file/analyze', { method: 'POST', body: { path } });
            this.render(r);
        } catch (e) { U.toast('分析失败: ' + e.message, 'err'); }
    },

    async onFile(file) {
        document.getElementById('analysis-result').style.display = 'none';
        U.toast('上传分析中...');
        try {
            const fd = new FormData();
            fd.append('file', file);
            const res = await fetch('/api/file/analyze', { method: 'POST', body: fd });
            const json = await res.json();
            if (!res.ok || json.ok === false) throw new Error(json.error || ('HTTP ' + res.status));
            this.render(json.data !== undefined ? json.data : json);
        } catch (e) { U.toast('分析失败: ' + e.message, 'err'); }
    },

    render(r) {
        document.getElementById('analysis-result').style.display = '';
        if (r.type === 'rtcm') this.renderRtcm(r);
        else if (r.type === 'solution') this.renderSolution(r);
        else this.renderGeneric(r);
        this.loadCharts(r.charts || {});
    },

    renderRtcm(r) {
        const ts = r.typeSummary || [];
        document.getElementById('stat-grid').innerHTML = [
            this._sc('📄', r.totalMessages || 0, '消息总数'),
            this._sc('⏱', (r.durationSec || 0).toFixed(1) + 's', '时长'),
            this._sc('📋', ts.length, '消息类型'),
            this._sc('📏', U.formatBytes(r.totalBytes), '文件大小')
        ].join('');

        const gaps = r.gaps || [];
        document.getElementById('gap-list').innerHTML = gaps.length === 0
            ? '<div class="gap-ok">✓ 连续</div>'
            : gaps.slice(0, 100).map(g =>
                `<div class="gap-row">#${g.index} 间隔 ${g.gapSec}s</div>`).join('');

        U.toast('RTCM 分析完成', 'ok');
    },

    renderSolution(r) {
        document.getElementById('stat-grid').innerHTML = [
            this._sc('📊', r.totalEpochs || 0, '总历元'),
            this._sc('🟢', r.fixEpochs || 0, '固定解'),
            this._sc('🟡', r.floatEpochs || 0, '浮动解'),
            this._sc('📈', (r.fixRate || 0) + '%', '固定率')
        ].join('');
        document.getElementById('gap-list').innerHTML = '<div class="gap-row" style="color:var(--text2)">--</div>';
        U.toast('解算文件分析完成', 'ok');
    },

    renderGeneric(r) {
        document.getElementById('stat-grid').innerHTML = `<div class="empty-mini" style="grid-column:1/-1">未知文件类型</div>`;
        document.getElementById('gap-list').innerHTML = '';
    },

    _sc(icon, val, lbl) {
        return `<div class="stat-card"><div class="val">${icon} ${U.esc(val)}</div><div class="lbl">${U.esc(lbl)}</div></div>`;
    },

    loadCharts(charts) {
        if (typeof Charts === 'undefined') {
            console.warn('Charts object not loaded');
            return;
        }
        const sky = document.getElementById('cv-sky');
        if (sky && charts.sky && charts.sky.length) {
            Charts.drawSkyPlot(sky, charts.sky, charts.dop ? { gdop: charts.dop.gdop, hdop: charts.dop.hdop, vdop: charts.dop.vdop } : {});
        }
        const dop = document.getElementById('cv-dop');
        if (dop && charts.dopSeries && charts.dopSeries.length) Charts.drawDopSeries(dop, charts.dopSeries);
        const snr = document.getElementById('cv-snr');
        if (snr && charts.snrSeries && charts.snrSeries.length) Charts.drawSnrTimeline(snr, charts.snrSeries);
        const snrBar = document.getElementById('cv-snr-bar');
        if (snrBar && charts.snrDist) Charts.drawSnrBar(snrBar, charts.snrDist);

        if (charts.epochs && charts.epochs.length) {
            const epochs = charts.epochs;
            if (sky) Charts.drawFixTimeline(sky, epochs);
            if (dop) Charts.drawCoordDeviation(dop, epochs, {
                lat: epochs.filter(e => e.fixMode === 1).reduce((s, e) => s + e.latitude, 0) / Math.max(1, epochs.filter(e => e.fixMode === 1).length) || 0,
                lon: epochs.filter(e => e.fixMode === 1).reduce((s, e) => s + e.longitude, 0) / Math.max(1, epochs.filter(e => e.fixMode === 1).length) || 0,
                hgt: epochs.filter(e => e.fixMode === 1).reduce((s, e) => s + e.height, 0) / Math.max(1, epochs.filter(e => e.fixMode === 1).length) || 0
            });
        }
    }
};

document.addEventListener('DOMContentLoaded', () => FileAnalysis.init());