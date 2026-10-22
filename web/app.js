/* Pulso Global — guerras, mercados y sus conexiones en vivo. */
(function () {
  'use strict';

  const D = window.PG_DATA;
  const NATIVE = window.Native && typeof window.Native.fetch === 'function';
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const DPR = Math.min(window.devicePixelRatio || 1, 2.5);

  const assetById = Object.fromEntries(D.assets.map((a) => [a.id, a]));
  const conflictById = Object.fromEntries(D.conflicts.map((c) => [c.id, c]));

  const S = {
    quotes: {},           // símbolo -> {price, prev, chg, series}
    news: [],             // noticias normalizadas
    heat: {},             // conflicto -> 1..5
    newsCount: {},        // conflicto -> nº noticias 24h
    risk: null,
    demo: false,
    online: false,
    view: 'globe',
    newsFilter: 'all',
    seenNews: new Set(),
    unseen: 0,
    lastNewsAt: 0,
    lastQuotesAt: 0
  };

  /* ================= util ================= */
  const nf = (d) => new Intl.NumberFormat('es-AR', { minimumFractionDigits: d, maximumFractionDigits: d });
  function fmtPrice(v) {
    if (v == null || !isFinite(v)) return '—';
    const a = Math.abs(v);
    return nf(a >= 1000 ? 0 : a >= 100 ? 1 : a >= 1 ? 2 : 4).format(v);
  }
  function fmtChg(v) {
    if (v == null || !isFinite(v)) return '—';
    return (v > 0 ? '+' : '') + nf(2).format(v) + '%';
  }
  const chgClass = (v) => (v == null || !isFinite(v) || Math.abs(v) < 0.005 ? 'flat' : v > 0 ? 'up' : 'down');
  function ago(t) {
    const m = Math.round((Date.now() - t) / 60000);
    if (m < 1) return 'ahora';
    if (m < 60) return `hace ${m} min`;
    const h = Math.round(m / 60);
    if (h < 24) return `hace ${h} h`;
    return `hace ${Math.round(h / 24)} d`;
  }
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  function store(k, v) { try { localStorage.setItem('pg_' + k, JSON.stringify(v)); } catch (e) { /* sin almacenamiento */ } }
  function load(k) { try { return JSON.parse(localStorage.getItem('pg_' + k)); } catch (e) { return null; } }
  function toast(msg) {
    const t = $('#toast'); t.textContent = msg; t.classList.add('show');
    clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.remove('show'), 2600);
  }
  function openUrl(u) { if (!/^https?:\/\//i.test(u || '')) return; if (NATIVE && window.Native.openUrl) window.Native.openUrl(u); else window.open(u, '_blank'); }

  /* ================= red ================= */
  const pending = {};
  let reqSeq = 0;
  window.__nativeDone = function (id, ok, data) {
    const p = pending[id]; if (!p) return; delete pending[id];
    if (!ok) return p.rej(new Error(data));
    try {
      const bin = atob(data); const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      p.res(new TextDecoder('utf-8').decode(bytes));
    } catch (e) { p.rej(e); }
  };
  function getText(url) {
    if (NATIVE) {
      return new Promise((res, rej) => {
        const id = 'r' + (++reqSeq); pending[id] = { res, rej };
        setTimeout(() => { if (pending[id]) { delete pending[id]; rej(new Error('timeout')); } }, 30000);
        window.Native.fetch(id, url);
      });
    }
    return fetch(url).then((r) => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.text(); });
  }

  /* ================= mercados ================= */
  async function fetchQuote(sym) {
    const enc = encodeURIComponent(sym);
    const tries = [
      `https://query1.finance.yahoo.com/v8/finance/chart/${enc}?range=1d&interval=5m`,
      `https://query2.finance.yahoo.com/v8/finance/chart/${enc}?range=5d&interval=1d`
    ];
    for (let i = 0; i < tries.length; i++) {
      try {
        const j = JSON.parse(await getText(tries[i]));
        const r = j.chart && j.chart.result && j.chart.result[0];
        if (!r) continue;
        const closes = ((r.indicators && r.indicators.quote && r.indicators.quote[0].close) || []).filter((v) => v != null);
        const m = r.meta || {};
        const price = m.regularMarketPrice != null ? m.regularMarketPrice : closes[closes.length - 1];
        let prev = i === 0 ? (m.chartPreviousClose != null ? m.chartPreviousClose : m.previousClose) : closes[closes.length - 2];
        if (price == null) continue;
        const chg = prev ? ((price - prev) / prev) * 100 : null;
        return { price, prev, chg, series: closes.length > 1 ? closes : [prev || price, price], cur: m.currency || '', t: Date.now() };
      } catch (e) { /* probar siguiente */ }
    }
    throw new Error('sin datos ' + sym);
  }

  async function refreshQuotes() {
    const syms = D.assets.map((a) => a.id);
    let ok = 0;
    const queue = syms.slice();
    async function worker() {
      while (queue.length) {
        const s = queue.shift();
        try { S.quotes[s] = await fetchQuote(s); ok++; } catch (e) { /* activo sin datos */ }
      }
    }
    await Promise.all([1, 2, 3, 4, 5, 6].map(worker));
    if (ok) {
      S.online = true; S.lastQuotesAt = Date.now(); store('quotes', S.quotes);
    } else if (!NATIVE && !Object.keys(S.quotes).length) {
      enableDemo();
    }
    computeRisk(); renderAll();
  }

  /* ================= noticias ================= */
  const WAR_RE = /guerra|ataque|bombarde|misil|militar|ofensiva|tropas|\bdron|alto el fuego|tregua|invasi|conflicto|combat|rehen|rehén|sancion|ej[ée]rcito|\bwar\b|airstrike|strike|missile|troops|ceasefire|hostage|military|attack|sanction|nuclear|frontera|insurg|yihad|rebel/i;

  function analyze(item) {
    const txt = (' ' + item.title + ' ' + (item.desc || '') + ' ').toLowerCase();
    const conf = D.conflicts.filter((c) => c.keywords.some((k) => txt.includes(k))).map((c) => c.id);
    const assets = new Set();
    D.marketWords.forEach((w) => { if (w.re.test(txt)) w.assets.forEach((a) => assets.add(a)); });
    conf.forEach((id) => conflictById[id].assets.slice(0, 2).forEach((a) => assets.add(a)));
    item.conf = conf;
    item.assets = Array.from(assets).slice(0, 5);
    item.market = D.marketWords.some((w) => w.re.test(txt));
    item.relevant = conf.length > 0 || WAR_RE.test(txt);
    return item;
  }

  function cleanText(html) {
    if (!html) return '';
    const d = document.createElement('div'); d.innerHTML = html;
    return (d.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 400);
  }

  function parseRSS(xml, feed) {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    const nodes = Array.from(doc.getElementsByTagName('item')).concat(Array.from(doc.getElementsByTagName('entry')));
    const tx = (el, tag) => { const n = el.getElementsByTagName(tag)[0]; return n ? n.textContent.trim() : ''; };
    return nodes.slice(0, 60).map((el) => {
      let title = tx(el, 'title');
      let source = tx(el, 'source') || feed.name;
      if (feed.url.includes('news.google.com')) {
        const i = title.lastIndexOf(' - ');
        if (i > 20) { source = title.slice(i + 3); title = title.slice(0, i); }
      }
      let link = tx(el, 'link');
      if (!link) { const l = el.getElementsByTagName('link')[0]; link = l ? l.getAttribute('href') : ''; }
      const dateStr = tx(el, 'pubDate') || tx(el, 'dc:date') || tx(el, 'updated') || tx(el, 'published');
      const t = Date.parse(dateStr) || Date.now();
      const desc = feed.url.includes('news.google.com') ? '' : cleanText(tx(el, 'description') || tx(el, 'summary'));
      return { title: cleanText(title), link, source, t, desc };
    });
  }

  function parseGdelt(txt) {
    const j = JSON.parse(txt);
    return (j.articles || []).map((a) => {
      const m = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$/.exec(a.seendate || '');
      const t = m ? Date.UTC(+m[1], m[2] - 1, +m[3], +m[4], +m[5], +m[6]) : Date.now();
      return { title: a.title, link: a.url, source: a.domain, t, desc: '' };
    });
  }

  async function refreshNews() {
    const results = await Promise.allSettled(D.feeds.map(async (f) => {
      const txt = await getText(f.url);
      return f.kind === 'gdelt' ? parseGdelt(txt) : parseRSS(txt, f);
    }));
    let items = [];
    results.forEach((r) => { if (r.status === 'fulfilled') items = items.concat(r.value); });

    if (!items.length) {
      if (!NATIVE && !S.news.length) enableDemo();
      else if (!items.length && NATIVE) setStatus(false);
      renderAll();
      return;
    }
    const cutoff = Date.now() - 48 * 3600e3;
    const seen = new Set();
    const merged = [];
    items.concat(S.news).forEach((n) => {
      if (!n.title || n.t < cutoff) return;
      const key = n.title.toLowerCase().replace(/[^a-záéíóúñ0-9]/g, '').slice(0, 70);
      if (seen.has(key)) return; seen.add(key);
      n.key = key;
      if (n.t > Date.now() + 3600e3) n.t = Date.now();
      merged.push(analyze(n));
    });
    const fresh = merged.filter((n) => n.relevant).sort((a, b) => b.t - a.t).slice(0, 220);

    const firstLoad = S.seenNews.size === 0;
    let newOnes = 0;
    fresh.forEach((n) => { if (!S.seenNews.has(n.key)) { if (!firstLoad) { n.isNew = true; newOnes++; } S.seenNews.add(n.key); } });
    S.news = fresh; S.online = true; S.lastNewsAt = Date.now();
    if (newOnes && S.view !== 'news') { S.unseen += newOnes; }
    if (newOnes && !firstLoad) toast(`${newOnes} noticia${newOnes > 1 ? 's' : ''} nueva${newOnes > 1 ? 's' : ''}`);
    store('news', S.news.slice(0, 150).map(({ isNew, ...n }) => n));
    computeHeat(); computeRisk(); Graph.rebuild(); renderAll();
  }

  function computeHeat() {
    const now = Date.now();
    D.conflicts.forEach((c) => {
      const list = S.news.filter((n) => n.conf.includes(c.id) && now - n.t < 24 * 3600e3);
      S.newsCount[c.id] = list.length;
      const weighted = list.reduce((s, n) => s + (now - n.t < 6 * 3600e3 ? 1.5 : 1), 0);
      const live = Math.min(5, Math.log2(1 + weighted) * 1.25);
      S.heat[c.id] = clamp(Math.round(c.base * 0.45 + live * 0.55 + (live > 0 ? 0.3 : 0)), 1, 5);
    });
  }

  function computeRisk() {
    const q = (s) => S.quotes[s];
    const vix = q('^VIX') ? q('^VIX').price : null;
    const vixScore = vix != null ? clamp((vix - 11) / (40 - 11), 0, 1) * 100 : 40;
    const heats = D.conflicts.map((c) => S.heat[c.id] || c.base);
    const heatScore = (heats.reduce((a, b) => a + b, 0) / (heats.length * 5)) * 100;
    const oil = q('BZ=F') ? q('BZ=F').chg || 0 : 0;
    const gold = q('GC=F') ? q('GC=F').chg || 0 : 0;
    const shock = clamp(oil * 12, 0, 40) + clamp(gold * 15, 0, 40);
    S.risk = Math.round(clamp(vixScore * 0.42 + heatScore * 0.43 + shock * 0.15 * 2.5, 0, 100));
    S.riskParts = { vix, vixScore, heatScore, oil, gold };
  }

  /* ================= modo demostración (solo vista previa en navegador) ================= */
  function enableDemo() {
    if (S.demo) return;
    S.demo = true;
    const base = { '^GSPC': 6650, '^IXIC': 22400, '^DJI': 46200, '^VIX': 18.4, '^STOXX50E': 5600, '^GDAXI': 24100, '^FTSE': 9400, '^N225': 47000, '^HSI': 25800, '000001.SS': 3850, '^TWII': 26500, '^KS11': 3600, '^BSESN': 82000, 'TA35.TA': 3200, '^BVSP': 145000, '^MERV': 2300000, 'BZ=F': 66, 'CL=F': 62, 'GC=F': 4100, 'TTF=F': 32, 'ZW=F': 520, 'HG=F': 4.9, 'EURUSD=X': 1.16, 'ILS=X': 3.3, 'KRW=X': 1420, 'INR=X': 88, 'BTC-USD': 108000, 'LMT': 480, 'RHM.DE': 1900, 'XOM': 114, 'MAERSK-B.CO': 12500, 'ZIM': 14, 'URA': 48, 'REMX': 70, 'GLEN.L': 360, 'TSM': 290, 'NVDA': 182, '005930.KS': 97000 };
    let seed = 7; const rnd = () => ((seed = (seed * 9301 + 49297) % 233280) / 233280);
    D.assets.forEach((a) => {
      const b = base[a.id] || 100; const drift = (rnd() - 0.5) * 0.035;
      const series = []; let v = b;
      for (let i = 0; i < 60; i++) { v *= 1 + drift / 60 + (rnd() - 0.5) * 0.004; series.push(v); }
      S.quotes[a.id] = { price: v, prev: b, chg: ((v - b) / b) * 100, series, t: Date.now() };
    });
    const now = Date.now();
    const demo = [
      ['Ataques con drones contra la red eléctrica de Ucrania elevan el precio del gas en Europa', 'ukr', 12],
      ['El petróleo Brent sube por temor a interrupciones en el estrecho de Ormuz', 'iran', 25],
      ['Navieras vuelven a desviar buques por ataques en el Mar Rojo; suben los fletes', 'redsea', 40],
      ['El oro marca nuevos máximos ante la escalada en Medio Oriente', 'mideast', 55],
      ['China realiza maniobras militares cerca de Taiwán; caen las tecnológicas asiáticas', 'taiwan', 70],
      ['Combates en Darfur: la ONU alerta por la crisis humanitaria en Sudán', 'sudan', 95],
      ['El M23 avanza en Kivu del Norte; el cobre reacciona en Londres', 'drc', 130],
      ['Corea del Norte lanza un misil balístico; el KOSPI abre a la baja', 'korea', 160],
      ['Tensión en el Caribe: el crudo pesado venezolano bajo la lupa de los mercados', 'venezuela', 200],
      ['Rheinmetall y las acciones de defensa europeas suben tras nuevo paquete de ayuda militar', 'ukr', 240],
      ['Wall Street cierra mixto mientras los inversores siguen los conflictos geopolíticos', null, 280],
      ['Junta de Malí y grupos yihadistas: el uranio de Níger vuelve al foco', 'sahel', 320]
    ];
    S.news = demo.map(([title, c, m], i) => analyze({ title, link: 'https://news.google.com', source: 'Ejemplo (sin conexión)', t: now - m * 60000, desc: 'Titular de demostración: la app muestra noticias reales cuando tiene conexión desde el teléfono.', key: 'demo' + i }));
    computeHeat(); computeRisk(); Graph.rebuild();
  }

  /* ================= estado / header ================= */
  function setStatus(on) {
    const dot = $('.live-dot');
    dot.classList.toggle('off', !on);
    let txt;
    if (S.demo) txt = 'Vista previa · datos de ejemplo';
    else if (!on) txt = 'Sin conexión · datos guardados';
    else {
      const t = Math.max(S.lastNewsAt, S.lastQuotesAt);
      txt = 'En vivo · ' + (t ? new Date(t).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' }) : '…');
    }
    $('#status').textContent = txt;
  }

  function renderHeader() {
    setStatus(S.online || S.demo);
    const r = S.risk;
    const el = $('#riskVal');
    el.textContent = r == null ? '—' : r;
    el.style.color = r == null ? '' : r >= 66 ? 'var(--red)' : r >= 40 ? 'var(--gold)' : 'var(--up)';
    const tapeSyms = ['^GSPC', '^IXIC', '^MERV', '^STOXX50E', 'BZ=F', 'GC=F', 'TTF=F', 'ZW=F', '^VIX', 'EURUSD=X', 'BTC-USD', '^N225', 'RHM.DE', 'LMT'];
    const html = tapeSyms.map((s) => {
      const q = S.quotes[s]; if (!q) return '';
      return `<span><b>${esc(assetById[s].name)}</b>${fmtPrice(q.price)} <em class="${chgClass(q.chg)}-t">${fmtChg(q.chg)}</em></span>`;
    }).join('');
    const track = $('#tapeTrack');
    if (track.dataset.html !== html) { track.innerHTML = html || '<span>Cargando cotizaciones…</span>'; track.dataset.html = html; }
    const b = $('#newsBadge');
    b.textContent = S.unseen > 99 ? '99+' : S.unseen;
    b.classList.toggle('show', S.unseen > 0);
  }

  function heatBars(h) { return '<span class="heat">' + [1, 2, 3, 4, 5].map((i) => `<i class="${i <= h ? 'on' : ''}"></i>`).join('') + '</span>'; }

  function renderHotStrip() {
    const list = D.conflicts.slice().sort((a, b) => (S.heat[b.id] || b.base) - (S.heat[a.id] || a.base) || (S.newsCount[b.id] || 0) - (S.newsCount[a.id] || 0));
    $('#hotStrip').innerHTML = list.map((c) => {
      const lead = c.assets.map((s) => S.quotes[s] && { s, q: S.quotes[s] }).filter(Boolean)[0];
      return `<button class="hot" data-conf="${c.id}">
        <div class="h-name"><i class="dot red"></i>${esc(c.short)}</div>
        ${heatBars(S.heat[c.id] || c.base)}
        <div class="h-meta">${S.newsCount[c.id] || 0} noticias 24h${lead ? ` · ${esc(assetById[lead.s].name.split(' ')[0])} <span class="${chgClass(lead.q.chg)}-t">${fmtChg(lead.q.chg)}</span>` : ''}</div>
      </button>`;
    }).join('');
  }

  /* ================= sparkline ================= */
  function spark(series, cls, w = 74, h = 26) {
    if (!series || series.length < 2) return `<svg class="spark" viewBox="0 0 ${w} ${h}"></svg>`;
    const mn = Math.min(...series), mx = Math.max(...series), r = mx - mn || 1;
    const pts = series.map((v, i) => [(i / (series.length - 1)) * w, h - 2 - ((v - mn) / r) * (h - 4)]);
    const d = 'M' + pts.map((p) => p[0].toFixed(1) + ',' + p[1].toFixed(1)).join('L');
    const col = cls === 'up' ? '#2EE6A6' : cls === 'down' ? '#FF5C7A' : '#8A95B4';
    const gid = 'g' + Math.random().toString(36).slice(2, 8);
    return `<svg class="spark" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">
      <defs><linearGradient id="${gid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${col}" stop-opacity=".35"/><stop offset="1" stop-color="${col}" stop-opacity="0"/></linearGradient></defs>
      <path d="${d}L${w},${h}L0,${h}Z" fill="url(#${gid})"/><path d="${d}" fill="none" stroke="${col}" stroke-width="1.6"/></svg>`;
  }

  function marketRow(sym) {
    const a = assetById[sym]; const q = S.quotes[sym];
    const c = chgClass(q && q.chg);
    return `<button class="mrow" data-asset="${esc(sym)}">
      <div><div class="nm">${esc(a.name)}</div><div class="sym">${esc(sym)} · ${esc(a.hub)}</div></div>
      ${spark(q && q.series, c)}
      <div><div class="px">${q ? fmtPrice(q.price) : '—'}</div><div class="chg ${c}">${q ? fmtChg(q.chg) : 'sin datos'}</div></div>
    </button>`;
  }

  /* ================= vista mercados ================= */
  function renderMarkets() {
    const r = S.risk == null ? 0 : S.risk;
    const p = S.riskParts || {};
    const label = r >= 66 ? 'Alta' : r >= 40 ? 'Moderada' : 'Baja';
    const hot = D.conflicts.slice().sort((a, b) => (S.heat[b.id] || 0) - (S.heat[a.id] || 0)).slice(0, 3).map((c) => c.short).join(', ');
    $('#pulseCard').innerHTML = `
      <h2>Índice de tensión geopolítica</h2>
      <div class="big">${S.risk == null ? '—' : r}<span style="font-size:15px;color:var(--muted);font-weight:600"> / 100 · ${label}</span></div>
      <div class="bar"><i style="left:calc(${r}% - 2px)"></i></div>
      <p>Combina el VIX (${p.vix != null ? nf(1).format(p.vix) : '—'}), la actividad informativa en ${D.conflicts.length} focos (${Math.round(p.heatScore || 0)}/100) y la reacción del petróleo (${fmtChg(p.oil)}) y el oro (${fmtChg(p.gold)}). Focos más activos: ${esc(hot)}.</p>`;
    const cats = [];
    D.assets.forEach((a) => { if (!cats.includes(a.cat)) cats.push(a.cat); });
    $('#marketList').innerHTML = cats.map((cat) => `<div class="mgroup"><h4>${esc(cat)}</h4>${D.assets.filter((a) => a.cat === cat).map((a) => marketRow(a.id)).join('')}</div>`).join('');
  }

  /* ================= vista noticias ================= */
  function newsCard(n) {
    const tags = n.conf.map((id) => `<span class="tag conf">${esc(conflictById[id].short)}</span>`)
      .concat(n.assets.map((s) => { const q = S.quotes[s]; return `<span class="tag ${chgClass(q && q.chg)}">${esc(assetById[s].name)} ${q ? fmtChg(q.chg) : ''}</span>`; }));
    return `<article class="news ${n.isNew ? 'fresh' : ''}" data-news="${esc(n.key)}">
      <div class="meta">${n.isNew ? '<span class="new-pill">NUEVO</span>' : ''}<span class="src">${esc(n.source)}</span>·<span>${ago(n.t)}</span></div>
      <h3>${esc(n.title)}</h3>
      ${tags.length ? `<div class="tags">${tags.join('')}</div>` : ''}
    </article>`;
  }

  function renderNews() {
    const counts = { all: S.news.length, market: S.news.filter((n) => n.market).length };
    D.conflicts.forEach((c) => { counts[c.id] = S.news.filter((n) => n.conf.includes(c.id)).length; });
    const chips = [['all', 'Todas'], ['market', 'Mercados']].concat(D.conflicts.filter((c) => counts[c.id]).sort((a, b) => counts[b.id] - counts[a.id]).map((c) => [c.id, c.short]));
    $('#newsFilters').innerHTML = chips.map(([k, l]) => `<button class="chip ${S.newsFilter === k ? 'on' : ''}" data-nf="${k}">${esc(l)}<span class="n">${counts[k] || 0}</span></button>`).join('');
    const f = S.newsFilter;
    const list = S.news.filter((n) => f === 'all' || (f === 'market' ? n.market : n.conf.includes(f)));
    $('#newsList').innerHTML = list.length ? list.slice(0, 120).map(newsCard).join('')
      : `<div class="empty">${S.online || S.demo ? 'No hay noticias para este filtro todavía.' : 'Buscando noticias en vivo…<br>Revisá tu conexión a internet.'}</div>`;
  }

  /* ================= hoja inferior (detalle) ================= */
  const sheet = { open: false };
  function openSheet(html) {
    $('#sheetBody').innerHTML = html; $('#sheetBody').scrollTop = 0;
    $('#sheet').classList.add('show'); $('#backdrop').classList.add('show'); sheet.open = true;
  }
  function closeSheet() { $('#sheet').classList.remove('show'); $('#backdrop').classList.remove('show'); sheet.open = false; Globe.select(null); }

  function miniNews(list) {
    return list.slice(0, 6).map((n) => `<button class="mini-news" data-news="${esc(n.key)}">${esc(n.title)}<small>${esc(n.source)} · ${ago(n.t)}</small></button>`).join('') || '<p style="color:var(--muted)">Sin noticias recientes.</p>';
  }

  function showConflict(id) {
    const c = conflictById[id]; if (!c) return;
    const news = S.news.filter((n) => n.conf.includes(id));
    const moves = c.assets.map((s) => S.quotes[s] && S.quotes[s].chg).filter((v) => v != null);
    const avg = moves.length ? moves.reduce((a, b) => a + Math.abs(b), 0) / moves.length : null;
    openSheet(`
      <div class="kicker">${esc(c.region)}</div>
      <h2>${esc(c.name)}</h2>${heatBars(S.heat[id] || c.base)}
      <div class="stats">
        <div class="stat"><b>${S.newsCount[id] || 0}</b><span>noticias 24 h</span></div>
        <div class="stat"><b>${c.assets.length}</b><span>activos ligados</span></div>
        <div class="stat"><b>${avg == null ? '—' : nf(2).format(avg) + '%'}</b><span>movimiento medio</span></div>
      </div>
      <p>${esc(c.summary)}</p>
      <h5>Canales de impacto</h5><p>${esc(c.channels)}</p>
      <h5>Mercados conectados</h5>${c.assets.map(marketRow).join('')}
      <h5>Últimas noticias</h5>${miniNews(news)}
      <div class="row-btns"><button class="btn" data-go="graph" data-focus="c:${id}">Ver en la red</button><button class="btn ghost" data-go="globe" data-conf-fly="${id}">Ver en el globo</button></div>`);
  }

  function showAsset(sym) {
    const a = assetById[sym]; const q = S.quotes[sym]; if (!a) return;
    const conf = D.conflicts.filter((c) => c.assets.includes(sym));
    const news = S.news.filter((n) => n.assets.includes(sym));
    const c = chgClass(q && q.chg);
    openSheet(`
      <div class="kicker">${esc(a.cat)} · ${esc(a.hub)}</div>
      <h2>${esc(a.name)}</h2>
      <div style="display:flex;align-items:baseline;gap:10px"><span style="font-size:28px;font-weight:800">${q ? fmtPrice(q.price) : '—'}</span><span class="chg ${c}" style="padding:4px 8px;border-radius:8px">${q ? fmtChg(q.chg) : 'sin datos'}</span></div>
      <div style="margin:12px 0">${q ? spark(q.series, c, 320, 90).replace('class="spark"', 'style="width:100%;height:90px"') : ''}</div>
      ${a.inverse ? '<p>Índice de volatilidad: cuando sube, el mercado descuenta más miedo.</p>' : ''}
      <h5>Conflictos que lo afectan</h5>
      ${conf.map((x) => `<button class="mini-news" data-conf="${x.id}">${esc(x.name)}<small>${esc(x.channels)}</small></button>`).join('') || '<p style="color:var(--muted)">Activo de referencia global.</p>'}
      <h5>Noticias relacionadas</h5>${miniNews(news)}
      <div class="row-btns"><button class="btn" data-go="graph" data-focus="a:${esc(sym)}">Ver en la red</button></div>`);
  }

  function showNews(key) {
    const n = S.news.find((x) => x.key === key); if (!n) return;
    n.isNew = false;
    openSheet(`
      <div class="kicker">${esc(n.source)} · ${ago(n.t)}</div>
      <h2 style="font-size:19px;line-height:1.3">${esc(n.title)}</h2>
      ${n.desc ? `<p>${esc(n.desc)}</p>` : ''}
      ${n.conf.length ? '<h5>Conflicto</h5>' + n.conf.map((id) => `<button class="mini-news" data-conf="${id}">${esc(conflictById[id].name)}<small>${esc(conflictById[id].region)}</small></button>`).join('') : ''}
      ${n.assets.length ? '<h5>Impacto en mercados</h5>' + n.assets.map(marketRow).join('') : ''}
      <div class="row-btns"><button class="btn" data-open="${esc(n.link)}">Leer la nota</button><button class="btn ghost" data-share="${esc(n.key)}">Compartir</button><button class="btn ghost" data-go="graph" data-focus="n:${esc(n.key)}">Ver en la red</button></div>`);
  }

  /* ================= GLOBO / MAPA ================= */
  const Globe = (function () {
    const canvas = $('#globe'); const ctx = canvas.getContext('2d');
    const world = topojson.feature(window.WORLD_TOPO, window.WORLD_TOPO.objects.countries).features;
    const graticule = d3.geoGraticule10();
    let W = 0, H = 0;
    let mode = 'globe';
    let rot = [-20, -25, 0];
    let zoom = 1, flatZoom = 1, flatY = 0;
    let vel = [0, 0];
    let lastInteract = 0;
    let selected = null;
    let fly = null;
    let layers = { links: true, markets: true };
    let running = false;
    const stars = Array.from({ length: 180 }, () => [Math.random(), Math.random(), Math.random() * 1.2 + 0.2, Math.random()]);
    const proj = { globe: d3.geoOrthographic().clipAngle(90).precision(0.6), flat: d3.geoNaturalEarth1().precision(0.6) };
    let hits = [];

    const hubs = (function () {
      const m = {};
      D.assets.forEach((a) => {
        const city = a.hub.split(' (')[0];
        if (city === 'Global') return;
        (m[city] = m[city] || { city, lat: a.lat, lon: a.lon, assets: [] }).assets.push(a.id);
      });
      return Object.values(m);
    })();

    const countryHeat = () => {
      const m = {};
      D.conflicts.forEach((c) => c.countries.forEach((n) => { m[n] = Math.max(m[n] || 0, S.heat[c.id] || c.base); }));
      return m;
    };

    function resize() {
      const r = canvas.getBoundingClientRect();
      W = r.width; H = r.height;
      canvas.width = Math.round(W * DPR); canvas.height = Math.round(H * DPR);
    }

    function current() {
      if (mode === 'globe') {
        const s = Math.min(W, H * 0.9) * 0.43 * zoom;
        return proj.globe.scale(s).translate([W / 2, H * 0.45]).rotate(rot);
      }
      const s = Math.max(W / 5.4, (H * 0.62) / 2.8) * flatZoom;
      const maxY = Math.max(0, (s * 2.8 - H * 0.8) / 2);
      flatY = clamp(flatY, -maxY - 40, maxY + 40);
      return proj.flat.scale(s).translate([W / 2, H * 0.45 + flatY]).rotate([rot[0], 0, 0]);
    }

    function visible(p, lonlat) {
      if (mode === 'flat') return p && p[0] > -20 && p[0] < W + 20;
      return d3.geoDistance(lonlat, [-rot[0], -rot[1]]) < Math.PI / 2 - 0.03;
    }

    function hubChange(h) {
      const v = h.assets.filter((s) => !assetById[s].inverse && S.quotes[s] && S.quotes[s].chg != null).map((s) => S.quotes[s].chg);
      return v.length ? v.reduce((a, b) => a + b, 0) / v.length : null;
    }

    function draw(t) {
      const p = current();
      const path = d3.geoPath(p, ctx);
      ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
      ctx.clearRect(0, 0, W, H);
      hits = [];

      // estrellas
      if (mode === 'globe') {
        stars.forEach(([x, y, r, ph]) => {
          ctx.globalAlpha = 0.25 + 0.35 * Math.abs(Math.sin(t / 1800 + ph * 6));
          ctx.fillStyle = '#c9d4ff'; ctx.fillRect(x * W, y * H, r, r);
        });
        ctx.globalAlpha = 1;
        const [cx, cy] = p.translate(); const R = p.scale();
        const glow = ctx.createRadialGradient(cx, cy, R * 0.9, cx, cy, R * 1.25);
        glow.addColorStop(0, 'rgba(124,92,255,.35)'); glow.addColorStop(1, 'rgba(124,92,255,0)');
        ctx.fillStyle = glow; ctx.beginPath(); ctx.arc(cx, cy, R * 1.25, 0, Math.PI * 2); ctx.fill();
        const ocean = ctx.createRadialGradient(cx - R * 0.35, cy - R * 0.35, R * 0.1, cx, cy, R);
        ocean.addColorStop(0, '#16224a'); ocean.addColorStop(1, '#0a1029');
        ctx.fillStyle = ocean; ctx.beginPath(); path({ type: 'Sphere' }); ctx.fill();
      } else {
        ctx.fillStyle = '#0a1029'; ctx.beginPath(); path({ type: 'Sphere' }); ctx.fill();
      }

      ctx.strokeStyle = 'rgba(140,160,255,.08)'; ctx.lineWidth = 0.6; ctx.beginPath(); path(graticule); ctx.stroke();

      const ch = countryHeat();
      world.forEach((f) => {
        const h = ch[f.properties.name];
        ctx.beginPath(); path(f);
        if (h) {
          const pulse = 0.5 + 0.5 * Math.sin(t / 700);
          ctx.fillStyle = `rgba(255,77,109,${0.16 + h * 0.07 + pulse * 0.05})`;
        } else ctx.fillStyle = '#1b2547';
        ctx.fill();
        ctx.strokeStyle = h ? 'rgba(255,120,140,.5)' : 'rgba(150,170,255,.16)'; ctx.lineWidth = h ? 0.9 : 0.5; ctx.stroke();
      });

      if (mode === 'globe') {
        const [cx, cy] = p.translate(); const R = p.scale();
        const shade = ctx.createRadialGradient(cx - R * 0.4, cy - R * 0.4, R * 0.2, cx, cy, R);
        shade.addColorStop(0, 'rgba(255,255,255,.05)'); shade.addColorStop(1, 'rgba(0,0,0,.35)');
        ctx.fillStyle = shade; ctx.beginPath(); path({ type: 'Sphere' }); ctx.fill();
        ctx.strokeStyle = 'rgba(160,140,255,.45)'; ctx.lineWidth = 1.2; ctx.beginPath(); path({ type: 'Sphere' }); ctx.stroke();
      }

      // conexiones conflicto -> mercado
      if (layers.links) {
        D.conflicts.forEach((c, ci) => {
          const dim = selected && selected !== c.id;
          c.assets.forEach((s, ai) => {
            const a = assetById[s];
            const q = S.quotes[s];
            const col = !q || q.chg == null ? '255,200,87' : q.chg >= 0 ? '46,230,166' : '255,92,122';
            ctx.beginPath(); path({ type: 'LineString', coordinates: [[c.lon, c.lat], [a.lon, a.lat]] });
            ctx.strokeStyle = `rgba(${col},${dim ? 0.05 : selected ? 0.75 : 0.28})`;
            ctx.lineWidth = selected === c.id ? 1.6 : 1;
            ctx.setLineDash([3, 5]); ctx.lineDashOffset = -t / 60; ctx.stroke(); ctx.setLineDash([]);
            if (!dim) {
              const interp = d3.geoInterpolate([c.lon, c.lat], [a.lon, a.lat]);
              const f = ((t / 3200) + ci * 0.17 + ai * 0.29) % 1;
              const pt = interp(f); const xy = p(pt);
              if (xy && visible(xy, pt)) {
                ctx.fillStyle = `rgba(${col},.95)`; ctx.shadowColor = `rgba(${col},1)`; ctx.shadowBlur = 8;
                ctx.beginPath(); ctx.arc(xy[0], xy[1], 1.8, 0, Math.PI * 2); ctx.fill(); ctx.shadowBlur = 0;
              }
            }
          });
        });
      }

      // posiciones de etiquetas de conflictos (prioridad) para evitar superposiciones
      const placed = [];
      D.conflicts.forEach((c) => {
        const ll = [c.lon, c.lat]; const xy = p(ll);
        if (xy && visible(xy, ll)) placed.push([xy[0] - 34, xy[1] - 30, 68, 34]);
      });
      const overlaps = (r) => placed.some((q) => r[0] < q[0] + q[2] && r[0] + r[2] > q[0] && r[1] < q[1] + q[3] && r[1] + r[3] > q[1]);

      // plazas financieras
      if (layers.markets) {
        hubs.forEach((h) => {
          const ll = [h.lon, h.lat]; const xy = p(ll);
          if (!xy || !visible(xy, ll)) return;
          const v = hubChange(h);
          const col = v == null ? '#8A95B4' : v >= 0 ? '#2EE6A6' : '#FF5C7A';
          ctx.fillStyle = col; ctx.strokeStyle = '#070B14'; ctx.lineWidth = 1.5;
          ctx.beginPath(); ctx.rect(xy[0] - 3.5, xy[1] - 3.5, 7, 7); ctx.fill(); ctx.stroke();
          const show = mode === 'flat' ? flatZoom > 1.2 || h.assets.length > 2 : zoom > 1.3 || h.assets.length > 2;
          ctx.font = '600 10px system-ui, sans-serif'; ctx.textAlign = 'left';
          const label = h.city + (v != null ? ' ' + fmtChg(v) : '');
          const w = ctx.measureText(label).width;
          const rect = [xy[0] + 6, xy[1] - 7, w + 6, 14];
          if (show && !overlaps(rect)) {
            placed.push(rect);
            ctx.fillStyle = 'rgba(7,11,20,.7)';
            ctx.fillRect(xy[0] + 6, xy[1] - 7, w + 6, 14);
            ctx.fillStyle = col; ctx.fillText(label, xy[0] + 9, xy[1] + 3.5);
          }
          hits.push({ x: xy[0], y: xy[1], type: 'hub', hub: h });
        });
      }

      // focos de conflicto
      D.conflicts.forEach((c) => {
        const ll = [c.lon, c.lat]; const xy = p(ll);
        if (!xy || !visible(xy, ll)) return;
        const h = S.heat[c.id] || c.base;
        const r = 3 + h * 1.1;
        const ph = ((t / 1600) + c.lat * 0.01) % 1;
        ctx.strokeStyle = `rgba(255,77,109,${(1 - ph) * 0.8})`; ctx.lineWidth = 1.5;
        ctx.beginPath(); ctx.arc(xy[0], xy[1], r + ph * (10 + h * 3), 0, Math.PI * 2); ctx.stroke();
        ctx.fillStyle = '#FF4D6D'; ctx.shadowColor = '#FF4D6D'; ctx.shadowBlur = selected === c.id ? 22 : 12;
        ctx.beginPath(); ctx.arc(xy[0], xy[1], r, 0, Math.PI * 2); ctx.fill(); ctx.shadowBlur = 0;
        ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(xy[0], xy[1], r * 0.35, 0, Math.PI * 2); ctx.fill();
        ctx.font = `700 ${selected === c.id ? 13 : 11}px system-ui, sans-serif`; ctx.textAlign = 'center';
        const ly = c.labelBelow ? xy[1] + r + 15 : xy[1] - r - 6;
        ctx.lineWidth = 3; ctx.strokeStyle = 'rgba(7,11,20,.85)'; ctx.strokeText(c.short, xy[0], ly);
        ctx.fillStyle = '#ffd5dd'; ctx.fillText(c.short, xy[0], ly);
        hits.push({ x: xy[0], y: xy[1], type: 'conf', id: c.id });
      });
    }

    function frame(t) {
      if (!running) return;
      const idle = performance.now() - lastInteract;
      if (fly) {
        const k = clamp((t - fly.t0) / 900, 0, 1); const e = d3.easeCubicInOut(k);
        rot = fly.i(e); if (k >= 1) fly = null;
      } else if (Math.abs(vel[0]) > 0.01 || Math.abs(vel[1]) > 0.01) {
        rot[0] += vel[0]; rot[1] = clamp(rot[1] + vel[1], -75, 75); vel = [vel[0] * 0.93, vel[1] * 0.93];
      } else if (idle > 3500 && !selected && !pointers.size) {
        rot[0] += mode === 'globe' ? 0.08 : 0.03;
      }
      draw(t);
      requestAnimationFrame(frame);
    }

    // ----- gestos -----
    const pointers = new Map();
    let pinch = null, downAt = null, moved = 0;
    canvas.addEventListener('pointerdown', (e) => {
      canvas.setPointerCapture(e.pointerId);
      pointers.set(e.pointerId, [e.clientX, e.clientY]);
      lastInteract = performance.now(); vel = [0, 0]; fly = null;
      if (pointers.size === 1) { downAt = [e.clientX, e.clientY]; moved = 0; }
      if (pointers.size === 2) {
        const [a, b] = Array.from(pointers.values());
        pinch = { d: Math.hypot(a[0] - b[0], a[1] - b[1]), z: mode === 'globe' ? zoom : flatZoom };
      }
    });
    canvas.addEventListener('pointermove', (e) => {
      if (!pointers.has(e.pointerId)) return;
      const prev = pointers.get(e.pointerId);
      pointers.set(e.pointerId, [e.clientX, e.clientY]);
      lastInteract = performance.now();
      if (pointers.size === 2 && pinch) {
        const [a, b] = Array.from(pointers.values());
        const z = clamp(pinch.z * Math.hypot(a[0] - b[0], a[1] - b[1]) / pinch.d, mode === 'globe' ? 0.7 : 1, 8);
        if (mode === 'globe') zoom = z; else flatZoom = z;
        moved = 99; return;
      }
      const dx = e.clientX - prev[0], dy = e.clientY - prev[1];
      moved += Math.abs(dx) + Math.abs(dy);
      if (mode === 'globe') {
        const k = 0.28 / zoom;
        rot[0] += dx * k; rot[1] = clamp(rot[1] - dy * k, -75, 75);
        vel = [dx * k, -dy * k];
      } else {
        const s = current().scale();
        rot[0] += (dx / s) * (180 / Math.PI) * 0.95; flatY += dy;
        vel = [(dx / s) * 50, 0];
      }
    });
    function up(e) {
      if (!pointers.has(e.pointerId)) return;
      pointers.delete(e.pointerId);
      if (pointers.size < 2) pinch = null;
      if (pointers.size === 0 && moved < 8 && downAt) tap(e);
      if (pointers.size === 0) downAt = null;
    }
    canvas.addEventListener('pointerup', up);
    canvas.addEventListener('pointercancel', up);
    canvas.addEventListener('wheel', (e) => {
      e.preventDefault();
      const f = Math.exp(-e.deltaY * 0.0015);
      if (mode === 'globe') zoom = clamp(zoom * f, 0.7, 8); else flatZoom = clamp(flatZoom * f, 1, 8);
      lastInteract = performance.now();
    }, { passive: false });

    function tap(e) {
      const r = canvas.getBoundingClientRect();
      const x = e.clientX - r.left, y = e.clientY - r.top;
      let best = null, bd = 26;
      hits.forEach((h) => { const d = Math.hypot(h.x - x, h.y - y) - (h.type === 'conf' ? 6 : 0); if (d < bd) { bd = d; best = h; } });
      if (!best) { if (selected) { selected = null; } return; }
      if (best.type === 'conf') { select(best.id); showConflict(best.id); }
      else {
        const h = best.hub;
        openSheet(`<div class="kicker">Plaza financiera</div><h2>${esc(h.city)}</h2>
          ${h.assets.map(marketRow).join('')}
          <h5>Conflictos con impacto aquí</h5>
          ${D.conflicts.filter((c) => c.assets.some((s) => h.assets.includes(s))).map((c) => `<button class="mini-news" data-conf="${c.id}">${esc(c.name)}<small>${esc(c.channels)}</small></button>`).join('') || '<p style="color:var(--muted)">Sin vínculos directos.</p>'}`);
      }
    }

    function select(id) { selected = id; }
    function flyTo(id) {
      const c = conflictById[id]; if (!c) return;
      selected = id;
      const target = [-c.lon, mode === 'globe' ? clamp(-c.lat + 8, -75, 75) : 0, 0];
      const from = rot.slice();
      while (target[0] - from[0] > 180) target[0] -= 360;
      while (target[0] - from[0] < -180) target[0] += 360;
      fly = { i: d3.interpolate(from, target), t0: performance.now() };
      if (mode === 'globe') zoom = Math.max(zoom, 1.25);
    }

    $('#projSeg').addEventListener('click', (e) => {
      const b = e.target.closest('button'); if (!b) return;
      mode = b.dataset.proj;
      $$('#projSeg button').forEach((x) => x.classList.toggle('on', x === b));
      if (mode === 'flat') { rot[1] = 0; flatY = 0; }
      else rot[1] = -25;
    });
    $('#layerSeg').addEventListener('click', (e) => {
      const b = e.target.closest('button'); if (!b) return;
      layers[b.dataset.layer] = !layers[b.dataset.layer];
      b.classList.toggle('on', layers[b.dataset.layer]);
    });

    return {
      start() { if (running) return; running = true; resize(); requestAnimationFrame(frame); },
      stop() { running = false; },
      resize,
      select,
      flyTo
    };
  })();

  /* ================= GRAFO estilo Obsidian ================= */
  const Graph = (function () {
    const canvas = $('#graph'); const ctx = canvas.getContext('2d');
    let W = 0, H = 0, transform = d3.zoomIdentity;
    let nodes = [], links = [], byId = {};
    let focus = null, neighbors = new Set();
    let show = { conflict: true, country: true, asset: true, news: true };
    let active = false;
    const COLORS = { conflict: '#FF4D6D', country: '#9AA3BF', news: '#A28BFF' };

    const sim = d3.forceSimulation()
      .force('link', d3.forceLink().id((d) => d.id).distance((l) => l.dist).strength((l) => l.str))
      .force('charge', d3.forceManyBody().strength((d) => (d.type === 'conflict' ? -340 : d.type === 'news' ? -35 : -130)).distanceMax(480))
      .force('x', d3.forceX(0).strength(0.035))
      .force('y', d3.forceY(0).strength(0.035))
      .force('collide', d3.forceCollide((d) => d.r + 3))
      .alphaDecay(0.025)
      .on('tick', () => { if (active) draw(); });

    function nodeColor(d) {
      if (d.type === 'asset') { const q = S.quotes[d.ref]; const c = chgClass(q && q.chg); return c === 'up' ? '#2EE6A6' : c === 'down' ? '#FF5C7A' : '#C9CFE0'; }
      return COLORS[d.type];
    }

    function rebuild() {
      const old = byId; const N = []; const L = [];
      const add = (n) => { const o = old[n.id]; if (o) { n.x = o.x; n.y = o.y; n.vx = o.vx; n.vy = o.vy; } N.push(n); };
      const countries = new Set(); const assets = new Set();
      D.conflicts.forEach((c) => {
        add({ id: 'c:' + c.id, ref: c.id, type: 'conflict', label: c.short, r: 7 + (S.heat[c.id] || c.base) * 1.6 });
        c.countries.forEach((k) => { countries.add(k); L.push({ source: 'c:' + c.id, target: 'k:' + k, dist: 34, str: 0.7 }); });
        c.assets.forEach((s) => { assets.add(s); L.push({ source: 'c:' + c.id, target: 'a:' + s, dist: 90, str: 0.25 }); });
      });
      const ES = { 'Ukraine': 'Ucrania', 'Russia': 'Rusia', 'Israel': 'Israel', 'Palestine': 'Palestina', 'Lebanon': 'Líbano', 'Syria': 'Siria', 'Iran': 'Irán', 'Iraq': 'Irak', 'Yemen': 'Yemen', 'Eritrea': 'Eritrea', 'Djibouti': 'Yibuti', 'Taiwan': 'Taiwán', 'China': 'China', 'North Korea': 'Corea del Norte', 'South Korea': 'Corea del Sur', 'Sudan': 'Sudán', 'S. Sudan': 'Sudán del Sur', 'Chad': 'Chad', 'Dem. Rep. Congo': 'RD Congo', 'Rwanda': 'Ruanda', 'Uganda': 'Uganda', 'Burundi': 'Burundi', 'Mali': 'Malí', 'Burkina Faso': 'Burkina Faso', 'Niger': 'Níger', 'Myanmar': 'Myanmar', 'India': 'India', 'Pakistan': 'Pakistán', 'Venezuela': 'Venezuela', 'Colombia': 'Colombia', 'Guyana': 'Guyana' };
      countries.forEach((k) => add({ id: 'k:' + k, ref: k, type: 'country', label: ES[k] || k, r: 4 }));
      assets.forEach((s) => add({ id: 'a:' + s, ref: s, type: 'asset', label: assetById[s].name, r: 6 }));
      // activos de la misma categoría se enlazan débilmente (clusters)
      const arr = Array.from(assets);
      arr.forEach((s, i) => arr.slice(i + 1).forEach((t) => { if (assetById[s].cat === assetById[t].cat && assetById[s].cat === 'Materias primas') L.push({ source: 'a:' + s, target: 'a:' + t, dist: 70, str: 0.04 }); }));
      S.news.filter((n) => n.conf.length).slice(0, 45).forEach((n) => {
        add({ id: 'n:' + n.key, ref: n.key, type: 'news', label: n.title.length > 42 ? n.title.slice(0, 40) + '…' : n.title, r: 2.6 });
        n.conf.forEach((c) => L.push({ source: 'n:' + n.key, target: 'c:' + c, dist: 26, str: 0.5 }));
        n.assets.filter((s) => assets.has(s)).slice(0, 2).forEach((s) => L.push({ source: 'n:' + n.key, target: 'a:' + s, dist: 60, str: 0.03 }));
      });
      nodes = N; links = L; byId = Object.fromEntries(N.map((n) => [n.id, n]));
      nodes.forEach((n) => { n.deg = 0; });
      links.forEach((l) => { byId[l.source] && byId[l.source].deg++; byId[l.target] && byId[l.target].deg++; });
      sim.nodes(nodes); sim.force('link').links(links);
      sim.alpha(Object.keys(old).length ? 0.3 : 1).restart();
      if (focus && !byId[focus]) setFocus(null);
    }

    function resize() {
      const r = canvas.getBoundingClientRect(); W = r.width; H = r.height;
      canvas.width = Math.round(W * DPR); canvas.height = Math.round(H * DPR);
    }

    const visibleNode = (n) => show[n.type];
    function draw() {
      ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
      ctx.clearRect(0, 0, W, H);
      ctx.save();
      ctx.translate(W / 2 + transform.x, H / 2 + transform.y); ctx.scale(transform.k, transform.k);
      const k = transform.k;
      links.forEach((l) => {
        if (!visibleNode(l.source) || !visibleNode(l.target)) return;
        const on = focus && (l.source.id === focus || l.target.id === focus);
        ctx.strokeStyle = on ? 'rgba(162,139,255,.85)' : focus ? 'rgba(160,170,200,.05)' : 'rgba(160,170,200,.16)';
        ctx.lineWidth = (on ? 1.6 : 0.8) / Math.sqrt(k);
        ctx.beginPath(); ctx.moveTo(l.source.x, l.source.y); ctx.lineTo(l.target.x, l.target.y); ctx.stroke();
      });
      nodes.forEach((n) => {
        if (!visibleNode(n)) return;
        const dim = focus && n.id !== focus && !neighbors.has(n.id);
        ctx.globalAlpha = dim ? 0.15 : 1;
        const col = nodeColor(n);
        if (n.id === focus || n.type === 'conflict') { ctx.shadowColor = col; ctx.shadowBlur = n.id === focus ? 24 : 10; }
        ctx.fillStyle = col; ctx.beginPath();
        if (n.type === 'asset') { const a = n.r * 0.9; ctx.moveTo(n.x, n.y - a * 1.25); ctx.lineTo(n.x + a * 1.25, n.y); ctx.lineTo(n.x, n.y + a * 1.25); ctx.lineTo(n.x - a * 1.25, n.y); ctx.closePath(); }
        else ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2);
        ctx.fill(); ctx.shadowBlur = 0;
        if (n.type === 'conflict') { ctx.strokeStyle = 'rgba(255,255,255,.85)'; ctx.lineWidth = 1.4; ctx.beginPath(); ctx.arc(n.x, n.y, n.r * 0.45, 0, Math.PI * 2); ctx.stroke(); }
        if (n.id === focus) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.5 / k; ctx.stroke(); }
        const showLabel = n.type === 'conflict' || n.id === focus || (focus && neighbors.has(n.id)) ||
          (n.type === 'asset' && k > 1.15) || (n.type === 'country' && k > 1.3) || (n.type === 'news' && k > 2.2);
        if (showLabel && !dim) {
          const fs = (n.type === 'conflict' ? 12 : n.type === 'news' ? 9 : 10.5) / Math.sqrt(k);
          ctx.font = `${n.type === 'conflict' ? 700 : 500} ${fs}px system-ui, sans-serif`; ctx.textAlign = 'center';
          ctx.fillStyle = n.type === 'conflict' ? '#ffd5dd' : 'rgba(225,230,245,.85)';
          let label = n.label;
          if (n.type === 'asset') { const q = S.quotes[n.ref]; if (q && q.chg != null) label += ' ' + fmtChg(q.chg); }
          ctx.fillText(label, n.x, n.y + n.r + fs + 1);
        }
      });
      ctx.globalAlpha = 1; ctx.restore();
    }

    function find(x, y) {
      const p = transform.invert([x - W / 2, y - H / 2]);
      let best = null, bd = 18 / transform.k;
      nodes.forEach((n) => { if (!visibleNode(n)) return; const d = Math.hypot(n.x - p[0], n.y - p[1]) - n.r; if (d < bd) { bd = d; best = n; } });
      return best;
    }

    function setFocus(id) {
      focus = id; neighbors = new Set();
      if (id) links.forEach((l) => { if (l.source.id === id) neighbors.add(l.target.id); if (l.target.id === id) neighbors.add(l.source.id); });
      $('#graphHint').textContent = id ? `${byId[id] ? byId[id].label : ''} · ${neighbors.size} conexiones · tocá el fondo para soltar` : 'Tocá un nodo para ver sus conexiones · pellizcá para zoom';
      draw();
    }

    function centerOn(id) {
      const n = byId[id]; if (!n) return;
      const k = Math.max(transform.k, 1.4);
      d3.select(canvas).transition().duration(700).call(zoomer.transform, d3.zoomIdentity.translate(-n.x * k, -n.y * k).scale(k));
    }

    const zoomer = d3.zoom().scaleExtent([0.25, 6]).on('zoom', (e) => { transform = e.transform; draw(); });
    const sel = d3.select(canvas);
    sel.call(d3.drag()
      .container(canvas)
      .subject((e) => { const n = find(e.x, e.y); return n ? n : null; })
      .on('start', (e) => { if (!e.active) sim.alphaTarget(0.25).restart(); e.subject.fx = e.subject.x; e.subject.fy = e.subject.y; e.subject._moved = 0; })
      .on('drag', (e) => { e.subject.fx += e.dx / transform.k; e.subject.fy += e.dy / transform.k; e.subject._moved += Math.abs(e.dx) + Math.abs(e.dy); })
      .on('end', (e) => {
        if (!e.active) sim.alphaTarget(0);
        e.subject.fx = null; e.subject.fy = null;
        if (e.subject._moved < 6) tapNode(e.subject);
      }));
    sel.call(zoomer).on('dblclick.zoom', null);
    sel.call(zoomer.transform, d3.zoomIdentity.scale(0.8));
    canvas.addEventListener('click', (e) => {
      const r = canvas.getBoundingClientRect();
      if (!find(e.clientX - r.left, e.clientY - r.top) && focus) setFocus(null);
    });

    function tapNode(n) {
      setFocus(n.id);
      if (n.type === 'conflict') showConflict(n.ref);
      else if (n.type === 'asset') showAsset(n.ref);
      else if (n.type === 'news') showNews(n.ref);
      else {
        const conf = D.conflicts.filter((c) => c.countries.includes(n.ref));
        openSheet(`<div class="kicker">País</div><h2>${esc(n.label)}</h2><h5>Involucrado en</h5>${conf.map((c) => `<button class="mini-news" data-conf="${c.id}">${esc(c.name)}<small>${esc(c.summary)}</small></button>`).join('')}`);
      }
    }

    $('#graphFilters').addEventListener('click', (e) => {
      const b = e.target.closest('button'); if (!b) return;
      show[b.dataset.g] = !show[b.dataset.g]; b.classList.toggle('on', show[b.dataset.g]); draw();
    });

    return {
      rebuild,
      activate() { active = true; resize(); draw(); sim.alpha(Math.max(sim.alpha(), 0.05)).restart(); },
      deactivate() { active = false; },
      focusOn(id) { if (!byId[id]) return; setFocus(id); setTimeout(() => centerOn(id), 50); },
      redraw() { if (active) draw(); },
      resize
    };
  })();

  /* ================= navegación ================= */
  function go(view) {
    S.view = view;
    $$('.tab').forEach((t) => t.classList.toggle('on', t.dataset.view === view));
    $$('.view').forEach((v) => v.classList.toggle('active', v.id === 'view-' + view));
    if (view === 'globe') Globe.start(); else Globe.stop();
    if (view === 'graph') Graph.activate(); else Graph.deactivate();
    if (view === 'news') { S.unseen = 0; renderNews(); }
    if (view === 'markets') renderMarkets();
    renderHeader();
  }

  function renderAll() {
    renderHeader(); renderHotStrip();
    if (S.view === 'news') renderNews();
    if (S.view === 'markets') renderMarkets();
    Graph.redraw();
  }

  $$('.tab').forEach((t) => t.addEventListener('click', () => go(t.dataset.view)));
  $('#backdrop').addEventListener('click', closeSheet);

  // Delegación de clicks para tarjetas y botones dinámicos
  document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-conf],[data-asset],[data-news],[data-nf],[data-open],[data-share],[data-go]');
    if (!el) return;
    const d = el.dataset;
    if (d.go) {
      closeSheet(); go(d.go);
      if (d.focus) setTimeout(() => Graph.focusOn(d.focus), 120);
      if (d.confFly) Globe.flyTo(d.confFly);
      return;
    }
    if (d.nf) { S.newsFilter = d.nf; renderNews(); return; }
    if (d.open) { openUrl(d.open); return; }
    if (d.share) {
      const n = S.news.find((x) => x.key === d.share);
      if (n) { const txt = `${n.title}\n${n.link}\n— vía Pulso Global`; if (NATIVE && window.Native.share) window.Native.share(txt); else if (navigator.share) navigator.share({ text: txt }).catch(() => {}); }
      return;
    }
    if (d.conf) { if (el.classList.contains('hot')) Globe.flyTo(d.conf); showConflict(d.conf); Globe.select(d.conf); return; }
    if (d.asset) { showAsset(d.asset); return; }
    if (d.news) { showNews(d.news); renderNews(); }
  });

  // swipe hacia abajo para cerrar la hoja
  (function () {
    let y0 = null; const sh = $('#sheet');
    sh.addEventListener('touchstart', (e) => { if ($('#sheetBody').scrollTop <= 0) y0 = e.touches[0].clientY; }, { passive: true });
    sh.addEventListener('touchmove', (e) => { if (y0 != null && e.touches[0].clientY - y0 > 70) { y0 = null; closeSheet(); } }, { passive: true });
    sh.addEventListener('touchend', () => { y0 = null; });
  })();

  window.handleBack = function () {
    if (sheet.open) { closeSheet(); return true; }
    if (S.view !== 'globe') { go('globe'); return true; }
    return false;
  };
  window.onAppPause = function () { Globe.stop(); };
  window.onAppResume = function () { if (S.view === 'globe') Globe.start(); tick(true); };

  window.addEventListener('resize', () => { Globe.resize(); Graph.resize(); Graph.redraw(); });

  /* ================= ciclo de actualización ================= */
  let lastQ = 0, lastN = 0;
  function tick(force) {
    const now = Date.now();
    if (force || now - lastQ > 75e3) { lastQ = now; refreshQuotes(); }
    if (force || now - lastN > 180e3) { lastN = now; refreshNews(); }
    renderHeader();
  }

  // arranque: primero lo guardado, después en vivo
  const cq = load('quotes'); if (cq) S.quotes = cq;
  const cn = load('news'); if (cn) { S.news = cn.map(analyze); S.news.forEach((n) => S.seenNews.add(n.key)); }
  computeHeat(); computeRisk(); Graph.rebuild();
  go('globe'); renderAll();
  tick(true);
  setInterval(() => tick(false), 15e3);
})();
