const Charts = {
    colors: {
        gps: '#3b82f6', bds: '#ef4444', glo: '#22c55e', gal: '#8b5cf6', qzs: '#f59e0b',
        bg: '#1a1e2e', grid: '#2a3040', text: '#8b949e', textB: '#e6edf3',
        fix: '#22c55e', float: '#f59e0b', single: '#ef4444',
        filtered: 'rgba(239,68,68,.4)'
    },

    _satPalette: null,
    _satIdx: 0,

    satColor(satId) {
        if (!this._satPalette) this._satPalette = new Map();
        if (!this._satPalette.has(satId)) {
            const hues = [210, 0, 145, 270, 35, 180, 310, 60, 95, 240, 15, 125, 290, 50, 200, 340, 80, 165, 255, 110];
            const h = hues[this._satIdx % hues.length];
            const s = 70 + (this._satIdx % 3) * 10;
            const l = 55 + (this._satIdx % 4) * 5;
            this._satPalette.set(satId, `hsl(${h},${s}%,${l}%)`);
            this._satIdx++;
        }
        return this._satPalette.get(satId);
    },

    resetSatPalette() {
        this._satPalette = null;
        this._satIdx = 0;
    },

    sysColor(satId) {
        const p = (satId || '')[0].toUpperCase();
        return this.colors[p === 'G' ? 'gps' : p === 'C' ? 'bds' : p === 'R' ? 'glo' : p === 'E' ? 'gal' : 'qzs'];
    },

    gpsTime(t) {
        if (!t) return '';
        const sec = t % 86400;
        const h = Math.floor(sec / 3600);
        const m = Math.floor((sec % 3600) / 60);
        const s = sec % 60;
        return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
    },

    /* ────────── SkyPlot ────────── */
    drawSkyPlot(canvas, satellites, opts = {}) {
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const w = canvas.clientWidth, h = canvas.clientHeight;
        canvas.width = w * dpr; canvas.height = h * dpr;
        ctx.scale(dpr, dpr);

        const cx = w / 2, cy = h / 2, R = Math.min(w, h) / 2 - 28;
        ctx.clearRect(0, 0, w, h);

        ctx.fillStyle = this.colors.bg;
        ctx.beginPath(); ctx.arc(cx, cy, R + 15, 0, Math.PI * 2); ctx.fill();

        [0.33, 0.66, 1].forEach(ratio => {
            ctx.beginPath(); ctx.arc(cx, cy, R * ratio, 0, Math.PI * 2);
            ctx.strokeStyle = this.colors.grid; ctx.lineWidth = 0.6; ctx.stroke();
            ctx.fillStyle = this.colors.text; ctx.font = '9px sans-serif';
            ctx.fillText(Math.round(90 - ratio * 90) + '°', cx + 3, cy - R * ratio - 2);
        });
        [0, 90, 180, 270].forEach(a => {
            const rad = a * Math.PI / 180;
            ctx.beginPath(); ctx.moveTo(cx, cy);
            ctx.lineTo(cx + Math.sin(rad) * R, cy - Math.cos(rad) * R);
            ctx.strokeStyle = this.colors.grid; ctx.stroke();
            ctx.fillStyle = this.colors.text;
            const label = a === 0 ? 'N' : a === 90 ? 'E' : a === 180 ? 'S' : 'W';
            ctx.fillText(label, cx + Math.sin(rad) * (R + 13) - 5, cy - Math.cos(rad) * (R + 13) + 4);
        });

        (satellites || []).forEach(s => {
            const el = s.elevation || 0, az = s.azimuth || 0;
            const r = (90 - el) / 90 * R;
            const rad = (az - 90) * Math.PI / 180;
            const x = cx + Math.cos(rad) * r, y = cy + Math.sin(rad) * r;
            const color = s.filtered ? this.colors.filtered : this.sysColor(s.id);

            ctx.beginPath(); ctx.arc(x, y, 6, 0, Math.PI * 2);
            ctx.fillStyle = color; ctx.fill();
            ctx.fillStyle = '#fff'; ctx.font = 'bold 7px sans-serif';
            ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
            ctx.fillText(s.id.replace(/^./, ''), x, y);
            if (opts.showSnr && s.snr) {
                ctx.fillStyle = this.colors.textB;
                ctx.font = '8px sans-serif'; ctx.fillText(s.snr, x, y + 11);
            }
            ctx.textAlign = 'start';
        });

        if (opts.gdop != null) {
            ctx.fillStyle = this.colors.textB;
            ctx.font = '10px monospace';
            const info = `GDOP:${opts.gdop} HDOP:${opts.hdop || '-'} VDOP:${opts.vdop || '-'}`;
            ctx.fillText(info, 6, 14);
        }
    },

    /* ────────── Satellite Visibility ────────── */
    drawSatVisibility(canvas, timeline, fixModes) {
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = canvas.clientHeight;
        canvas.width = W * dpr; canvas.height = H * dpr;
        ctx.scale(dpr, dpr);

        const pad = { t: 8, r: 10, b: fixModes ? 28 : 8, l: 48 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = this.colors.bg;
        ctx.fillRect(pad.l, pad.t, pw, ph);

        if (!timeline || !timeline.length) { ctx.fillStyle = this.colors.text; ctx.fillText('无数据', pad.l + 10, pad.t + 20); return; }

        const satIds = [...new Set(timeline.flatMap(e => Object.keys(e.sats || {})))].sort();
        if (!satIds.length) return;
        const barH = Math.max(4, ph / satIds.length);
        const t0 = timeline[0].t, tN = timeline[timeline.length - 1].t;
        const tRange = tN - t0 || 1;

        satIds.forEach((sid, ri) => {
            const y = pad.t + ri * barH;
            timeline.forEach(e => {
                const x = pad.l + (e.t - t0) / tRange * pw;
                const sat = e.sats && e.sats[sid];
                ctx.fillStyle = sat ? this.sysColor(sid) : 'rgba(42,48,64,.5)';
                ctx.fillRect(x, y, Math.max(1, pw / timeline.length), barH - 0.5);
            });
        });

        ctx.fillStyle = this.colors.textB; ctx.font = '9px monospace';
        satIds.forEach((sid, ri) => {
            const y = pad.t + ri * barH + barH / 2 + 3;
            ctx.fillText(sid, 3, y);
        });

        ctx.fillStyle = this.colors.text; ctx.font = '9px sans-serif';
        ctx.fillText(new Date(t0).toLocaleTimeString(), pad.l, pad.t + ph + 13);
        ctx.textAlign = 'right';
        ctx.fillText(new Date(tN).toLocaleTimeString(), W - pad.r, pad.t + ph + 13);
        ctx.textAlign = 'start';

        if (fixModes && fixModes.length) {
            const my = pad.t + ph + 12;
            fixModes.forEach(m => {
                const x = pad.l + (m.t - t0) / tRange * pw;
                const col = m.mode >= 10 ? this.colors.fix : m.mode >= 5 ? this.colors.float : this.colors.single;
                ctx.fillStyle = col;
                ctx.fillRect(x, my, Math.max(1, pw / fixModes.length), 8);
            });
        }
    },

    /* ────────── SNR Timeline ────────── */
    drawSnrTimeline(canvas, timeline, fixModes) {
        this.resetSatPalette();
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = canvas.clientHeight;
        canvas.width = W * dpr; canvas.height = H * dpr;
        ctx.scale(dpr, dpr);

        const pad = { t: 8, r: 12, b: fixModes ? 46 : 36, l: 34 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = this.colors.bg;
        ctx.fillRect(pad.l, pad.t, pw, ph);

        if (!timeline || !timeline.length) { ctx.fillStyle = this.colors.text; ctx.fillText('无数据', pad.l + 8, pad.t + 20); return; }

        const satIds = [...new Set(timeline.flatMap(e => Object.keys(e.snrs || {})))].sort();
        const t0 = timeline[0].t, tN = timeline[timeline.length - 1].t;
        const tRange = tN - t0 || 1;
        const snrMin = 20, snrMax = 55;

        [snrMin, 25, 30, 35, 40, 45, 50, snrMax].forEach(v => {
            const y = pad.t + ph - (v - snrMin) / (snrMax - snrMin) * ph;
            ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y);
            ctx.strokeStyle = v === 30 ? 'rgba(248,81,73,.25)' : this.colors.grid;
            ctx.lineWidth = v === 30 ? 0.8 : 0.3; ctx.stroke();
            ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif';
            ctx.fillText(v, 2, y + 3);
        });

        const tickCount = Math.min(6, timeline.length);
        ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif'; ctx.textAlign = 'center';
        for (let i = 0; i <= tickCount; i++) {
            const t = t0 + tRange * i / tickCount;
            const x = pad.l + pw * i / tickCount;
            ctx.fillText(this.gpsTime(Math.round(t)), x, pad.t + ph + 12);
            ctx.beginPath(); ctx.moveTo(x, pad.t + ph); ctx.lineTo(x, pad.t + ph + 3);
            ctx.strokeStyle = this.colors.grid; ctx.lineWidth = 0.5; ctx.stroke();
        }
        ctx.textAlign = 'start';

        satIds.forEach(sid => {
            ctx.beginPath(); let first = true;
            timeline.forEach(e => {
                const v = e.snrs && e.snrs[sid];
                if (v == null) { first = true; return; }
                const x = pad.l + (e.t - t0) / tRange * pw;
                const y = pad.t + ph - (v - snrMin) / (snrMax - snrMin) * ph;
                if (first) { ctx.moveTo(x, y); first = false; } else ctx.lineTo(x, y);
            });
            ctx.strokeStyle = this.satColor(sid); ctx.lineWidth = 1; ctx.stroke();
        });

        let lx = pad.l, ly = pad.t + ph + 22;
        ctx.font = '8px sans-serif';
        satIds.forEach((sid, i) => {
            if (lx > W - pad.r - 40) { lx = pad.l; ly += 12; }
            ctx.fillStyle = this.satColor(sid);
            ctx.fillRect(lx, ly - 6, 10, 3);
            ctx.fillStyle = this.colors.text;
            ctx.fillText(sid, lx + 12, ly);
            lx += ctx.measureText(sid).width + 20;
        });

        if (fixModes && fixModes.length) {
            const my = ly + 10;
            fixModes.forEach(m => {
                const x = pad.l + (m.t - t0) / tRange * pw;
                const col = m.mode >= 10 ? this.colors.fix : m.mode >= 5 ? this.colors.float : this.colors.single;
                ctx.fillStyle = col;
                ctx.fillRect(x, my, Math.max(1, pw / fixModes.length), 6);
            });
            ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif';
            ctx.fillText('Fix状态', pad.l, my + 14);
        }
    },

    /* ────────── Multipath ────────── */
    drawMultipathTimeline(canvas, timeline, fixModes) {
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = canvas.clientHeight;
        canvas.width = W * dpr; canvas.height = H * dpr;
        ctx.scale(dpr, dpr);

        const pad = { t: 8, r: 12, b: fixModes ? 30 : 20, l: 34 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = this.colors.bg;
        ctx.fillRect(pad.l, pad.t, pw, ph);

        if (!timeline || !timeline.length) { ctx.fillStyle = this.colors.text; ctx.fillText('无数据', pad.l + 8, pad.t + 20); return; }

        const satIds = [...new Set(timeline.flatMap(e => Object.keys(e.mp || {})))].sort();
        const t0 = timeline[0].t, tN = timeline[timeline.length - 1].t;
        const tRange = tN - t0 || 1;

        [-0.5, 0, 0.5].forEach(v => {
            const y = pad.t + ph / 2 + v / 1.5 * ph;
            ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y);
            ctx.strokeStyle = v === 0 ? this.colors.grid : 'rgba(42,48,64,.6)';
            ctx.lineWidth = v === 0 ? 0.6 : 0.3; ctx.stroke();
            ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif';
            ctx.fillText(v.toFixed(1), 2, y + 3);
        });

        satIds.forEach(sid => {
            ctx.beginPath(); let first = true;
            timeline.forEach(e => {
                const v = e.mp && e.mp[sid];
                if (v == null) { first = true; return; }
                const x = pad.l + (e.t - t0) / tRange * pw;
                const y = pad.t + ph / 2 - v / 1.5 * ph;
                if (first) { ctx.moveTo(x, y); first = false; } else ctx.lineTo(x, y);
            });
            ctx.strokeStyle = this.sysColor(sid); ctx.lineWidth = 1; ctx.stroke();
        });

        if (fixModes && fixModes.length) {
            const my = pad.t + ph + 14;
            fixModes.forEach(m => {
                const x = pad.l + (m.t - t0) / tRange * pw;
                const col = m.mode >= 10 ? this.colors.fix : m.mode >= 5 ? this.colors.float : this.colors.single;
                ctx.fillStyle = col;
                ctx.fillRect(x, my, Math.max(1, pw / fixModes.length), 8);
            });
        }
    },

    /* ────────── Fix State Timeline ────────── */
    drawFixTimeline(canvas, epochs) {
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = canvas.clientHeight;
        canvas.width = W * dpr; canvas.height = H * dpr;
        ctx.scale(dpr, dpr);

        const pad = { t: 6, r: 8, l: 8, b: 18 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = this.colors.bg;
        ctx.fillRect(pad.l, pad.t, pw, ph);

        if (!epochs || !epochs.length) { ctx.fillStyle = this.colors.text; ctx.font = '10px sans-serif'; ctx.fillText('无数据', pad.l + 8, pad.t + 20); return; }

        const t0 = epochs[0].timestamp || epochs[0].t || 0;
        const tN = epochs[epochs.length - 1].timestamp || epochs[epochs.length - 1].t || t0 + 3600000;
        const tRange = tN - t0 || 1;
        const barW = Math.max(1, pw / epochs.length);

        epochs.forEach((e, i) => {
            const x = pad.l + ((e.timestamp || e.t) - t0) / tRange * pw;
            const mode = e.fixMode != null ? e.fixMode : e.mode;
            const col = mode >= 10 ? this.colors.fix : (mode >= 5 || mode === 2) ? this.colors.float : this.colors.single;
            ctx.fillStyle = col;
            ctx.fillRect(x, pad.t, barW, ph);
        });

        ctx.fillStyle = this.colors.text; ctx.font = '9px sans-serif';
        ctx.textAlign = 'left';
        ctx.fillText(new Date(t0).toLocaleTimeString(), pad.l, H - pad.b + 13);
        ctx.textAlign = 'right';
        ctx.fillText(new Date(tN).toLocaleTimeString(), W - pad.r, H - pad.b + 13);
        ctx.textAlign = 'start';

        const lx = pad.l + pw + 6, ly = pad.t + 8;
        ctx.fillStyle = this.colors.fix; ctx.fillRect(lx, ly, 10, 4);
        ctx.fillStyle = this.colors.float; ctx.fillRect(lx, ly + 10, 10, 4);
        ctx.fillStyle = this.colors.single; ctx.fillRect(lx, ly + 20, 10, 4);
        ctx.fillStyle = this.colors.textB; ctx.font = '9px sans-serif';
        ctx.fillText('Fix', lx + 14, ly + 5);
        ctx.fillText('Float', lx + 14, ly + 15);
        ctx.fillText('Single', lx + 14, ly + 25);
    },

    /* ────────── Coordinate Deviation (N/E/U) ────────── */
    drawCoordDeviation(canvas, epochs, refPoint) {
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = canvas.clientHeight;
        canvas.width = W * dpr; canvas.height = H * dpr;
        ctx.scale(dpr, dpr);

        if (!refPoint) refPoint = { lat: 0, lon: 0, hgt: 0 };

        const pad = { t: 4, r: 8, l: 42, b: 26 };
        const pw = W - pad.l - pad.r, ph = (H - pad.t - pad.b) / 3;
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = this.colors.bg;
        ctx.fillRect(pad.l, pad.t, pw, H - pad.t - pad.b);

        if (!epochs || !epochs.length) { ctx.fillStyle = this.colors.text; ctx.font = '10px sans-serif'; ctx.fillText('无数据', pad.l + 8, pad.t + 20); return; }

        const t0 = epochs[0].timestamp || epochs[0].t || 0;
        const tN = epochs[epochs.length - 1].timestamp || epochs[epochs.length - 1].t || t0 + 3600000;
        const tRange = tN - t0 || 1;

        const dLat = 111000 * Math.cos(refPoint.lat * Math.PI / 180);
        const dev = epochs.map(e => ({
            t: e.timestamp || e.t,
            n: (e.latitude - refPoint.lat) * 111000,
            e: (e.longitude - refPoint.lon) * dLat,
            u: e.height - refPoint.hgt,
            fix: (e.fixMode != null ? e.fixMode : e.mode) === 1
        }));

        const lineColors = { n: '#79c0ff', e: '#d2a8ff', u: '#ffa657' };
        const fixColor = '#56d364';
        const floatColor = '#e3b341';

        const drawBand = (yOff, label, getter, lineColor) => {
            const y0 = pad.t + yOff * ph;
            const vals = dev.map(d => getter(d));
            const absMax = Math.max(Math.abs(Math.max(...vals)), Math.abs(Math.min(...vals)), 0.02);
            const range = absMax * 1.2;

            [0, -range * 0.5, range * 0.5].forEach(v => {
                const y = y0 + ph / 2 - v / (range * 2) * ph;
                ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y);
                ctx.strokeStyle = v === 0 ? 'rgba(88,166,255,.35)' : 'rgba(48,54,61,.5)';
                ctx.lineWidth = v === 0 ? 0.6 : 0.3; ctx.stroke();
                if (Math.abs(v) < 0.001) {
                    ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif';
                    ctx.fillText('0', pad.l - 2, y + 3);
                }
            });

            ctx.beginPath();
            dev.forEach((d, i) => {
                const x = pad.l + (d.t - t0) / tRange * pw;
                const v = getter(d);
                const y = y0 + ph / 2 - v / (range * 2) * ph;
                if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
            });
            ctx.strokeStyle = lineColor; ctx.lineWidth = 1; ctx.stroke();

            dev.forEach(d => {
                const x = pad.l + (d.t - t0) / tRange * pw;
                const v = getter(d);
                const y = y0 + ph / 2 - v / (range * 2) * ph;
                ctx.fillStyle = d.fix ? fixColor : floatColor;
                ctx.beginPath(); ctx.arc(x, y, 1.0, 0, Math.PI * 2); ctx.fill();
            });

            ctx.fillStyle = this.colors.textB; ctx.font = 'bold 9px sans-serif';
            ctx.fillText(label, 4, y0 + ph / 2 + 3);
            ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif';
            ctx.fillText('±' + (range * 100).toFixed(1) + 'cm', pad.l - 38, y0 + 12);
        };

        drawBand(0, 'N', d => d.n, lineColors.n);
        drawBand(1, 'E', d => d.e, lineColors.e);
        drawBand(2, 'U', d => d.u, lineColors.u);

        ctx.fillStyle = this.colors.text; ctx.font = '9px sans-serif';
        ctx.textAlign = 'left';
        ctx.fillText(new Date(t0).toLocaleTimeString(), pad.l, H - pad.b + 13);
        ctx.textAlign = 'right';
        ctx.fillText(new Date(tN).toLocaleTimeString(), W - pad.r, H - pad.b + 13);
        ctx.textAlign = 'start';
    },

    drawDopSeries(canvas, series) {
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = canvas.clientHeight;
        canvas.width = W * dpr; canvas.height = H * dpr;
        ctx.scale(dpr, dpr);

        const pad = { t: 8, r: 12, b: 32, l: 34 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = this.colors.bg;
        ctx.fillRect(pad.l, pad.t, pw, ph);

        if (!series || !series.length) {
            ctx.fillStyle = this.colors.text; ctx.font = '10px sans-serif';
            ctx.fillText('无DOP数据', pad.l + 8, pad.t + 20); return;
        }

        const t0 = series[0].t, tN = series[series.length - 1].t;
        const tRange = tN - t0 || 1;
        const allVals = series.flatMap(s => [s.gdop || 0, s.hdop || 0, s.vdop || 0]);
        const maxDop = Math.max(...allVals, 6);

        for (let v = 0; v <= maxDop; v += 2) {
            const y = pad.t + ph - v / maxDop * ph;
            ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y);
            ctx.strokeStyle = this.colors.grid; ctx.lineWidth = 0.3; ctx.stroke();
            ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif';
            ctx.fillText(v, 2, y + 3);
        }

        const tickCount = Math.min(6, series.length);
        ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif'; ctx.textAlign = 'center';
        for (let i = 0; i <= tickCount; i++) {
            const t = t0 + tRange * i / tickCount;
            const x = pad.l + pw * i / tickCount;
            ctx.fillText(this.gpsTime(Math.round(t)), x, pad.t + ph + 12);
            ctx.beginPath(); ctx.moveTo(x, pad.t + ph); ctx.lineTo(x, pad.t + ph + 3);
            ctx.strokeStyle = this.colors.grid; ctx.lineWidth = 0.5; ctx.stroke();
        }
        ctx.textAlign = 'start';

        const drawLine = (getter, color) => {
            ctx.beginPath(); let first = true;
            series.forEach(s => {
                const x = pad.l + (s.t - t0) / tRange * pw;
                const y = pad.t + ph - getter(s) / maxDop * ph;
                if (first) { ctx.moveTo(x, y); first = false; } else ctx.lineTo(x, y);
            });
            ctx.strokeStyle = color; ctx.lineWidth = 1.2; ctx.stroke();
        };

        drawLine(s => s.gdop || 0, '#3b82f6');
        drawLine(s => s.hdop || 0, '#22c55e');
        drawLine(s => s.vdop || 0, '#f59e0b');

        const lx = pad.l, ly = pad.t + ph + 24;
        ctx.font = '9px sans-serif';
        ctx.fillStyle = '#3b82f6'; ctx.fillRect(lx, ly - 5, 12, 3);
        ctx.fillStyle = this.colors.text; ctx.fillText('GDOP', lx + 16, ly);
        const wG = ctx.measureText('GDOP').width + 24;
        ctx.fillStyle = '#22c55e'; ctx.fillRect(lx + wG, ly - 5, 12, 3);
        ctx.fillStyle = this.colors.text; ctx.fillText('HDOP', lx + wG + 16, ly);
        const wH = ctx.measureText('HDOP').width + 24;
        ctx.fillStyle = '#f59e0b'; ctx.fillRect(lx + wG + wH, ly - 5, 12, 3);
        ctx.fillStyle = this.colors.text; ctx.fillText('VDOP', lx + wG + wH + 16, ly);
    },

    drawSnrBar(canvas, dist) {
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = canvas.clientHeight;
        canvas.width = W * dpr; canvas.height = H * dpr;
        ctx.scale(dpr, dpr);

        const pad = { t: 8, r: 12, b: 20, l: 34 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = this.colors.bg;
        ctx.fillRect(pad.l, pad.t, pw, ph);

        if (!dist || !dist.length) {
            ctx.fillStyle = this.colors.text; ctx.font = '10px sans-serif';
            ctx.fillText('无SNR数据', pad.l + 8, pad.t + 20); return;
        }

        const maxCount = Math.max(...dist.map(d => d.count || 0), 1);
        const barW = Math.max(4, pw / dist.length - 2);

        dist.forEach((d, i) => {
            const x = pad.l + i * (pw / dist.length) + 1;
            const h = (d.count || 0) / maxCount * ph;
            const color = this.sysColor(d.satId || 'G');
            ctx.fillStyle = color;
            ctx.fillRect(x, pad.t + ph - h, barW, h);
        });

        ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif';
        ctx.fillText('SNR (dB)', pad.l, H - 4);
    },

    drawTrackPlot(canvas, epochs, refPoint) {
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = canvas.clientHeight;
        canvas.width = W * dpr; canvas.height = H * dpr;
        ctx.scale(dpr, dpr);

        if (!refPoint) refPoint = { lat: 0, lon: 0, hgt: 0 };

        const pad = { t: 6, r: 12, b: 28, l: 62 };
        const pw = W - pad.l - pad.r;
        const bandH = (H - pad.t - pad.b) / 3;
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = '#0d1117';
        ctx.fillRect(0, 0, W, H);

        if (!epochs || !epochs.length) {
            ctx.fillStyle = '#8b949e'; ctx.font = '12px sans-serif';
            ctx.fillText('无轨迹数据', pad.l + 10, pad.t + 20); return;
        }

        const t0 = epochs[0].timestamp || epochs[0].t || 0;
        const tN = epochs[epochs.length - 1].timestamp || epochs[epochs.length - 1].t || t0 + 3600000;
        const tRange = tN - t0 || 1;

        const dLat = 111000 * Math.cos(refPoint.lat * Math.PI / 180);
        const dev = epochs.map(e => ({
            t: e.timestamp || e.t,
            n: (e.latitude - refPoint.lat) * 111000,
            e: (e.longitude - refPoint.lon) * dLat,
            u: e.height - refPoint.hgt,
            fix: e.fixMode === 1 ? 'fix' : e.fixMode === 2 ? 'float' : 'single',
            ratio: e.ratio || 0
        }));

        const fixColor = '#56d364';
        const floatColor = '#e3b341';
        const singleColor = '#f85149';
        const lineColors = { n: '#79c0ff', e: '#d2a8ff', u: '#ffa657' };
        const colorMap = { fix: fixColor, float: floatColor, single: singleColor };

        const drawBand = (idx, label, getter, lineColor) => {
            const y0 = pad.t + idx * bandH;
            const vals = dev.map(d => getter(d));
            const absMax = Math.max(Math.abs(Math.max(...vals)), Math.abs(Math.min(...vals)), 0.01);
            const range = absMax * 1.3;

            for (let g = 0; g <= 4; g++) {
                const y = y0 + bandH * g / 4;
                ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y);
                ctx.strokeStyle = g === 2 ? 'rgba(88,166,255,.35)' : 'rgba(48,54,61,.6)';
                ctx.lineWidth = g === 2 ? 0.8 : 0.3; ctx.stroke();
            }

            for (let g = 0; g <= 8; g++) {
                const x = pad.l + pw * g / 8;
                ctx.beginPath(); ctx.moveTo(x, y0); ctx.lineTo(x, y0 + bandH);
                ctx.strokeStyle = 'rgba(48,54,61,.4)'; ctx.lineWidth = 0.2; ctx.stroke();
            }

            ctx.beginPath();
            dev.forEach((d, i) => {
                const x = pad.l + (d.t - t0) / tRange * pw;
                const v = getter(d);
                const y = y0 + bandH / 2 - v / (range * 2) * bandH;
                if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
            });
            ctx.strokeStyle = lineColor; ctx.lineWidth = 1.2; ctx.stroke();

            const ptSize = dev.length > 5000 ? 0.6 : dev.length > 2000 ? 0.8 : 1.0;
            dev.forEach(d => {
                const x = pad.l + (d.t - t0) / tRange * pw;
                const v = getter(d);
                const y = y0 + bandH / 2 - v / (range * 2) * bandH;
                ctx.fillStyle = colorMap[d.fix] || floatColor;
                ctx.beginPath(); ctx.arc(x, y, ptSize, 0, Math.PI * 2); ctx.fill();
            });

            ctx.fillStyle = '#c9d1d9'; ctx.font = 'bold 11px sans-serif';
            ctx.fillText(label, 4, y0 + bandH / 2 + 4);

            ctx.fillStyle = '#8b949e'; ctx.font = '8px monospace';
            ctx.textAlign = 'right';
            ctx.fillText('+' + (range * 100).toFixed(1) + 'cm', pad.l - 3, y0 + 8);
            ctx.fillText('-' + (range * 100).toFixed(1) + 'cm', pad.l - 3, y0 + bandH - 2);
            ctx.fillText('0', pad.l - 3, y0 + bandH / 2 + 3);
            ctx.textAlign = 'start';

            if (idx < 2) {
                ctx.beginPath(); ctx.moveTo(pad.l, y0 + bandH); ctx.lineTo(W - pad.r, y0 + bandH);
                ctx.strokeStyle = 'rgba(88,166,255,.15)'; ctx.lineWidth = 0.5; ctx.stroke();
            }
        };

        drawBand(0, 'N', d => d.n, lineColors.n);
        drawBand(1, 'E', d => d.e, lineColors.e);
        drawBand(2, 'U', d => d.u, lineColors.u);

        const tSteps = Math.min(6, Math.ceil(pw / 100));
        ctx.fillStyle = '#8b949e'; ctx.font = '9px sans-serif';
        for (let i = 0; i <= tSteps; i++) {
            const t = t0 + tRange * i / tSteps;
            const x = pad.l + pw * i / tSteps;
            const lbl = new Date(t);
            const str = lbl.getHours().toString().padStart(2, '0') + ':' + lbl.getMinutes().toString().padStart(2, '0');
            ctx.textAlign = 'center';
            ctx.fillText(str, x, H - pad.b + 14);
        }
        ctx.textAlign = 'start';

        const fixCnt = dev.filter(d => d.fix === 'fix').length;
        const fltCnt = dev.filter(d => d.fix === 'float').length;
        const sglCnt = dev.filter(d => d.fix === 'single').length;
        const lx = W - pad.r - 180, ly = pad.t + 4;
        ctx.font = '9px sans-serif';
        ctx.fillStyle = fixColor; ctx.fillRect(lx, ly, 8, 4);
        ctx.fillStyle = '#c9d1d9'; ctx.fillText(`Fix ${fixCnt}`, lx + 12, ly + 5);
        ctx.fillStyle = floatColor; ctx.fillRect(lx + 70, ly, 8, 4);
        ctx.fillStyle = '#c9d1d9'; ctx.fillText(`Float ${fltCnt}`, lx + 82, ly + 5);
        ctx.fillStyle = singleColor; ctx.fillRect(lx + 140, ly, 8, 4);
        ctx.fillStyle = '#c9d1d9'; ctx.fillText(`Single ${sglCnt}`, lx + 152, ly + 5);
    },

    drawRatioTimeline(canvas, epochs) {
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const W = canvas.clientWidth, H = canvas.clientHeight;
        canvas.width = W * dpr; canvas.height = H * dpr;
        ctx.scale(dpr, dpr);

        const pad = { t: 8, r: 12, b: 26, l: 34 };
        const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = this.colors.bg;
        ctx.fillRect(pad.l, pad.t, pw, ph);

        if (!epochs || !epochs.length) {
            ctx.fillStyle = this.colors.text; ctx.font = '10px sans-serif';
            ctx.fillText('无Ratio数据', pad.l + 8, pad.t + 20); return;
        }

        const t0 = epochs[0].timestamp || epochs[0].t || 0;
        const tN = epochs[epochs.length - 1].timestamp || epochs[epochs.length - 1].t || t0 + 3600000;
        const tRange = tN - t0 || 1;

        const ratios = epochs.map(e => e.ratio || 0);
        const maxRatio = Math.max(...ratios, 10);

        for (let v = 0; v <= maxRatio; v += Math.max(1, Math.round(maxRatio / 5))) {
            const y = pad.t + ph - v / maxRatio * ph;
            ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y);
            ctx.strokeStyle = this.colors.grid; ctx.lineWidth = 0.3; ctx.stroke();
            ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif';
            ctx.fillText(v, 2, y + 3);
        }

        const thresar = 3.0;
        const yThres = pad.t + ph - thresar / maxRatio * ph;
        ctx.beginPath(); ctx.moveTo(pad.l, yThres); ctx.lineTo(W - pad.r, yThres);
        ctx.strokeStyle = 'rgba(248,81,73,.5)'; ctx.lineWidth = 1; ctx.setLineDash([4, 3]); ctx.stroke();
        ctx.setLineDash([]);
        ctx.fillStyle = this.colors.text; ctx.font = '8px sans-serif';
        ctx.fillText('Ratio=' + thresar, W - pad.r - 50, yThres - 4);

        ctx.beginPath(); let first = true;
        epochs.forEach(e => {
            const x = pad.l + ((e.timestamp || e.t) - t0) / tRange * pw;
            const v = e.ratio || 0;
            const y = pad.t + ph - v / maxRatio * ph;
            if (first) { ctx.moveTo(x, y); first = false; } else ctx.lineTo(x, y);
        });
        ctx.strokeStyle = '#58a6ff'; ctx.lineWidth = 1; ctx.stroke();

        epochs.forEach(e => {
            const x = pad.l + ((e.timestamp || e.t) - t0) / tRange * pw;
            const v = e.ratio || 0;
            const y = pad.t + ph - v / maxRatio * ph;
            const mode = e.fixMode;
            const col = mode === 1 ? this.colors.fix : mode === 2 ? this.colors.float : this.colors.single;
            ctx.fillStyle = col;
            ctx.beginPath(); ctx.arc(x, y, 1.2, 0, Math.PI * 2); ctx.fill();
        });

        ctx.fillStyle = this.colors.text; ctx.font = '9px sans-serif';
        ctx.textAlign = 'left';
        ctx.fillText(new Date(t0).toLocaleTimeString(), pad.l, H - pad.b + 13);
        ctx.textAlign = 'right';
        ctx.fillText(new Date(tN).toLocaleTimeString(), W - pad.r, H - pad.b + 13);
        ctx.textAlign = 'start';
    }
};