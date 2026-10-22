/* Pulso Global — guerras, desastres, mercados y sus conexiones en vivo. */
(function () {
  'use strict';

  const D = window.PG_DATA;
  const M = window.PG_MODEL;
  const NATIVE = window.Native && typeof window.Native.fetch === 'function';
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const DPR = Math.min(window.devicePixelRatio || 1, 2.5);

  const assetById = Object.fromEntries(D.assets.map((a) => [a.id, a]));
  const conflictById = Object.fromEntries(D.conflicts.map((c) => [c.id, c]));
  // factor -> activo global de referencia (para dibujar arcos de desastres)
  const factorAsset = { oil: 'BZ=F', gas: 'TTF=F', gold: 'GC=F', agri: 'ZW=F', metals: 'HG=F', uranium: 'URA', defense: 'LMT', shipping: 'MAERSK-B.CO', chips: 'TSM', aviation: '^GSPC', insurance: '^GSPC', rebuild: '^GSPC', riskoff: '^VIX', argrisk: '^MERV' };

  const S = {
    quotes: {},          // Yahoo: símbolo -> {price, prev, chg, series}
    arg: {},             // IOL/BYMA (data912): símbolo -> {price, chg, vol}
    lecaps: [],          // instrumentos LECAP/BONCAP detectados en vivo
    news: [],
    disasters: [],
    clips: [],
    radios: [],
    heat: {}, newsCount: {},
    proj: null,
    horizon: 'short',
    risk: null,
    demo: false, online: false,
    view: 'globe', newsFilter: 'all', mktTab: 'arg', projMode: 'proj',
    seenNews: new Set(), unseen: 0,
    lastNewsAt: 0, lastQuotesAt: 0
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
  function openUrl(u) {
    if (!/^https?:\/\//i.test(u || '')) return;
    if (NATIVE && window.Native.openUrl) window.Native.openUrl(u); else window.open(u, '_blank');
  }
  function cleanText(html) {
    if (!html) return '';
    const d = document.createElement('div'); d.innerHTML = html;
    return (d.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 400);
  }
  const xmlText = (el, tag) => { const n = el.getElementsByTagName(tag)[0]; return n ? n.textContent.trim() : ''; };

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
  const getJSON = (u) => getText(u).then((t) => JSON.parse(t));

  /* ================= instrumentos IOL ================= */
  function allInstruments() { return M.instruments.concat(S.lecaps); }
  function instById(id) { return allInstruments().find((i) => i.id === id); }

  /* ================= mercados globales (Yahoo) ================= */
  async function fetchQuote(sym) {
    const enc = encodeURIComponent(sym);
    const tries = [
      `https://query1.finance.yahoo.com/v8/finance/chart/${enc}?range=1d&interval=5m`,
      `https://query2.finance.yahoo.com/v8/finance/chart/${enc}?range=5d&interval=1d`
    ];
    for (let i = 0; i < tries.length; i++) {
      try {
        const j = await getJSON(tries[i]);
        const r = j.chart && j.chart.result && j.chart.result[0];
        if (!r) continue;
        const closes = ((r.indicators && r.indicators.quote && r.indicators.quote[0].close) || []).filter((v) => v != null);
        const m = r.meta || {};
        const price = m.regularMarketPrice != null ? m.regularMarketPrice : closes[closes.length - 1];
        const prev = i === 0 ? (m.chartPreviousClose != null ? m.chartPreviousClose : m.previousClose) : closes[closes.length - 2];
        if (price == null) continue;
        const chg = prev ? ((price - prev) / prev) * 100 : null;
        return { price, prev, chg, series: closes.length > 1 ? closes : [prev || price, price], t: Date.now() };
      } catch (e) { /* probar siguiente */ }
    }
    throw new Error('sin datos ' + sym);
  }

  async function refreshQuotes() {
    const queue = D.assets.map((a) => a.id);
    let ok = 0;
    async function worker() {
      while (queue.length) {
        const s = queue.shift();
        try { S.quotes[s] = await fetchQuote(s); ok++; } catch (e) { /* activo sin datos */ }
      }
    }
    await Promise.all([1, 2, 3, 4, 5].map(worker));
    if (ok) { S.online = true; S.lastQuotesAt = Date.now(); store('quotes', S.quotes); }
    else if (!NATIVE && !Object.keys(S.quotes).length) enableDemo();
    computeRisk(); renderAll();
  }

  /* ================= mercado argentino (paneles de data912 = BYMA) ================= */
  async function refreshArg() {
    const res = await Promise.allSettled(M.argFeeds.map((f) => getJSON(f.url).then((arr) => ({ key: f.key, arr: Array.isArray(arr) ? arr : (arr.data || []) }))));
    const panels = {};
    res.forEach((r) => { if (r.status === 'fulfilled') panels[r.value.key] = r.value.arr; });
    if (!Object.keys(panels).length) { if (!NATIVE && !Object.keys(S.arg).length) enableDemo(); return; }
    // ¿la variación viene en % o en fracción? se decide con las acciones/cedears
    const sample = (panels.arg_stocks || []).concat(panels.arg_cedears || []).map((x) => Math.abs(+x.pct_change || 0)).filter((v) => v > 0);
    const scale = sample.length > 10 && Math.max(...sample) < 0.3 ? 100 : 1;
    Object.entries(panels).forEach(([key, arr]) => arr.forEach((x) => {
      const sym = x.symbol || x.ticker; if (!sym) return;
      const price = +(x.c != null ? x.c : x.last != null ? x.last : x.px_ask);
      const chg = x.pct_change != null ? +x.pct_change * scale : null;
      if (!isFinite(price) || price <= 0) return;
      S.arg[sym] = { price, chg, vol: +(x.v || x.volume || 0), panel: key };
    }));
    // LECAPs / BONCAPs vigentes con más volumen
    const notes = (panels.arg_notes || []).filter((x) => /^[ST]\d{2}[A-Z]\d$/.test(x.symbol || ''))
      .sort((a, b) => (+b.v || 0) - (+a.v || 0)).slice(0, 6);
    if (notes.length) {
      S.lecaps = notes.map((x) => ({ id: x.symbol, name: (x.symbol[0] === 'S' ? 'LECAP ' : 'BONCAP ') + x.symbol, type: 'LECAPs', src: 'arg_notes', sens: M.lecapSens }));
      store('lecaps', S.lecaps);
    }
    S.online = true; store('arg', S.arg);
    computeProjection(); renderAll();
  }

  /* ================= análisis de textos ================= */
  const WAR_RE = /guerra|ataque|bombarde|misil|militar|ofensiva|tropas|\bdron|alto el fuego|tregua|invasi|conflicto|combat|reh[ée]n|sancion|ej[ée]rcito|\bwar\b|airstrike|strike|missile|troops|ceasefire|hostage|military|attack|sanction|nuclear|frontera|insurg|yihad|rebel/i;

  function analyze(item) {
    const txt = (' ' + item.title + ' ' + (item.desc || '') + ' ').toLowerCase();
    item.conf = D.conflicts.filter((c) => c.keywords.some((k) => txt.includes(k))).map((c) => c.id);
    const dw = M.disasterWords.find((w) => w.re.test(txt));
    item.dis = dw ? dw.t : null;
    const assets = new Set();
    D.marketWords.forEach((w) => { if (w.re.test(txt)) w.assets.forEach((a) => assets.add(a)); });
    item.conf.forEach((id) => conflictById[id].assets.slice(0, 2).forEach((a) => assets.add(a)));
    item.assets = Array.from(assets).slice(0, 4);
    item.factors = M.factorWords.filter((w) => w.re.test(txt)).map((w) => w.f);
    item.market = D.marketWords.some((w) => w.re.test(txt));
    item.relevant = item.conf.length > 0 || !!item.dis || WAR_RE.test(txt);
    item.iol = relatedInstruments(item);
    return item;
  }

  // Instrumentos de IOL relacionados con una noticia: shocks de sus eventos + factores que menciona
  function relatedInstruments(item) {
    const P = {};
    const add = (shock, w) => Object.entries(shock).forEach(([f, v]) => { P[f] = (P[f] || 0) + v * w; });
    item.conf.forEach((id) => add(M.conflictShock[id] || {}, 1));
    if (item.dis) add(M.disasterShock(item.dis, NaN, NaN), 0.8);
    const directional = Object.keys(P).length > 0;
    item.factors.forEach((f) => { P[f] = (P[f] || 0) + 0.3; });
    if (!Object.keys(P).length) return [];
    return allInstruments().map((ins) => {
      let s = 0; Object.entries(ins.sens).forEach(([f, v]) => { s += (P[f] || 0) * v; });
      return { id: ins.id, s, dir: directional };
    }).filter((x) => Math.abs(x.s) >= 0.18).sort((a, b) => Math.abs(b.s) - Math.abs(a.s)).slice(0, 5);
  }

  function parseRSS(xml, feed) {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    const nodes = Array.from(doc.getElementsByTagName('item')).concat(Array.from(doc.getElementsByTagName('entry')));
    const google = feed.url.includes('news.google.com');
    return nodes.slice(0, 60).map((el) => {
      let title = xmlText(el, 'title');
      let source = xmlText(el, 'source') || feed.name;
      if (google) {
        const i = title.lastIndexOf(' - ');
        if (i > 20) { source = title.slice(i + 3); title = title.slice(0, i); }
      }
      let link = xmlText(el, 'link');
      if (!link) { const l = el.getElementsByTagName('link')[0]; link = l ? l.getAttribute('href') : ''; }
      const dateStr = xmlText(el, 'pubDate') || xmlText(el, 'dc:date') || xmlText(el, 'updated') || xmlText(el, 'published');
      const t = Date.parse(dateStr) || Date.now();
      const desc = google ? '' : cleanText(xmlText(el, 'description') || xmlText(el, 'summary'));
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

  /* ================= noticias ================= */
  async function refreshNews() {
    const feeds = D.feeds.concat([M.disasterNewsFeed]);
    const results = await Promise.allSettled(feeds.map(async (f) => {
      const txt = await getText(f.url);
      return f.kind === 'gdelt' ? parseGdelt(txt) : parseRSS(txt, f);
    }));
    let items = [];
    results.forEach((r) => { if (r.status === 'fulfilled') items = items.concat(r.value); });
    if (!items.length) {
      if (!NATIVE && !S.news.length) enableDemo();
      renderAll(); return;
    }
    const cutoff = Date.now() - 48 * 3600e3;
    const seen = new Set(); const merged = [];
    items.concat(S.news).forEach((n) => {
      if (!n.title || n.t < cutoff) return;
      const key = n.title.toLowerCase().replace(/[^a-záéíóúñ0-9]/g, '').slice(0, 70);
      if (seen.has(key)) return; seen.add(key);
      n.key = key;
      if (n.t > Date.now() + 3600e3) n.t = Date.now();
      merged.push(analyze(n));
    });
    const fresh = merged.filter((n) => n.relevant).sort((a, b) => b.t - a.t).slice(0, 240);
    const firstLoad = S.seenNews.size === 0;
    let newOnes = 0;
    fresh.forEach((n) => { if (!S.seenNews.has(n.key)) { if (!firstLoad) { n.isNew = true; newOnes++; } S.seenNews.add(n.key); } });
    S.news = fresh; S.online = true; S.lastNewsAt = Date.now();
    if (newOnes && S.view !== 'news') S.unseen += newOnes;
    if (newOnes && !firstLoad) toast(`${newOnes} noticia${newOnes > 1 ? 's' : ''} nueva${newOnes > 1 ? 's' : ''}`);
    store('news', S.news.slice(0, 150).map(({ isNew, iol, ...n }) => n));
    afterData();
  }

  /* ================= desastres naturales ================= */
  async function refreshDisasters() {
    const res = await Promise.allSettled(M.disasterFeeds.map(async (f) => {
      const txt = await getText(f.url);
      if (f.kind === 'usgs') {
        return (JSON.parse(txt).features || []).map((q) => {
          const mag = q.properties.mag || 0;
          return { id: 'usgs-' + q.id, type: 'EQ', title: `Sismo M${mag.toFixed(1)} · ${q.properties.place || ''}`, place: q.properties.place || '',
            lat: q.geometry.coordinates[1], lon: q.geometry.coordinates[0], sev: clamp((mag - 4.4) / 3, 0.1, 1), mag,
            t: q.properties.time, link: q.properties.url, src: 'USGS', tsunami: !!q.properties.tsunami };
        });
      }
      if (f.kind === 'gdacs') {
        const doc = new DOMParser().parseFromString(txt, 'text/xml');
        return Array.from(doc.getElementsByTagName('item')).map((el) => {
          const type = xmlText(el, 'gdacs:eventtype');
          const level = (xmlText(el, 'gdacs:alertlevel') || '').toLowerCase();
          const lat = parseFloat(xmlText(el, 'geo:lat')), lon = parseFloat(xmlText(el, 'geo:long'));
          const t = Date.parse(xmlText(el, 'gdacs:todate') || xmlText(el, 'pubDate')) || Date.now();
          return { id: 'gdacs-' + (xmlText(el, 'gdacs:eventid') || xmlText(el, 'guid')), type, title: xmlText(el, 'title'), place: xmlText(el, 'gdacs:country'),
            lat, lon, sev: level === 'red' ? 1 : level === 'orange' ? 0.7 : 0.35, level, t, link: xmlText(el, 'link'), src: 'GDACS' };
        }).filter((d) => M.disasterTypes[d.type] && isFinite(d.lat) && isFinite(d.lon) && Date.now() - d.t < 10 * 864e5);
      }
      const j = JSON.parse(txt);
      const map = { wildfires: 'WF', volcanoes: 'VO', severeStorms: 'ST', floods: 'FL', drought: 'DR', earthquakes: 'EQ' };
      return (j.events || []).map((e) => {
        const cat = e.categories && e.categories[0] && e.categories[0].id;
        const g = e.geometry && e.geometry[e.geometry.length - 1];
        if (!g || g.type !== 'Point' || !map[cat]) return null;
        let type = map[cat];
        if (type === 'ST' && /hurricane|typhoon|cyclone|tropical/i.test(e.title)) type = 'TC';
        return { id: 'eonet-' + e.id, type, title: e.title, place: '', lat: g.coordinates[1], lon: g.coordinates[0], sev: type === 'TC' ? 0.6 : 0.3,
          t: Date.parse(g.date) || Date.now(), link: (e.sources && e.sources[0] && e.sources[0].url) || 'https://eonet.gsfc.nasa.gov', src: 'NASA EONET' };
      }).filter(Boolean);
    }));
    let list = [];
    res.forEach((r) => { if (r.status === 'fulfilled') list = list.concat(r.value); });
    if (!list.length) { if (!NATIVE && !S.disasters.length) enableDemo(); return; }
    // quitar duplicados (mismo tipo, misma zona)
    const out = [];
    list.sort((a, b) => b.sev - a.sev).forEach((d) => {
      if (out.some((o) => o.type === d.type && Math.abs(o.lat - d.lat) < 1.5 && Math.abs(o.lon - d.lon) < 1.5)) return;
      d.region = M.regionOf(d.lat, d.lon);
      d.shock = M.disasterShock(d.type, d.lat, d.lon);
      out.push(d);
    });
    S.disasters = out.slice(0, 40);
    store('disasters', S.disasters);
    afterData();
  }

  /* ================= clips (YouTube por canal) ================= */
  async function resolveChannel(ch) {
    if (ch.id) return ch.id;
    const cache = load('yt_ids') || {};
    if (cache[ch.handle]) return cache[ch.handle];
    const html = await getText('https://www.youtube.com/@' + ch.handle);
    const m = /"externalId":"(UC[\w-]{22})"/.exec(html) || /channel_id=(UC[\w-]{22})/.exec(html) || /"channelId":"(UC[\w-]{22})"/.exec(html);
    if (!m) throw new Error('canal no encontrado');
    cache[ch.handle] = m[1]; store('yt_ids', cache);
    return m[1];
  }

  async function refreshClips() {
    const res = await Promise.allSettled(M.channels.map(async (ch) => {
      const id = await resolveChannel(ch);
      const xml = await getText('https://www.youtube.com/feeds/videos.xml?channel_id=' + id);
      const doc = new DOMParser().parseFromString(xml, 'text/xml');
      return Array.from(doc.getElementsByTagName('entry')).slice(0, 15).map((e) => {
        const vid = xmlText(e, 'yt:videoId');
        const th = e.getElementsByTagName('media:thumbnail')[0];
        const views = e.getElementsByTagName('media:statistics')[0];
        return { vid, title: xmlText(e, 'title'), channel: ch.name, t: Date.parse(xmlText(e, 'published')) || Date.now(),
          thumb: th ? th.getAttribute('url') : `https://i.ytimg.com/vi/${vid}/hqdefault.jpg`, views: views ? +views.getAttribute('views') : 0,
          desc: cleanText(xmlText(e, 'media:description')).slice(0, 220) };
      });
    }));
    let clips = [];
    res.forEach((r) => { if (r.status === 'fulfilled') clips = clips.concat(r.value); });
    if (!clips.length) return;
    const cutoff = Date.now() - 3 * 864e5;
    S.clips = clips.filter((c) => c.vid && c.t > cutoff).map((c) => {
      analyze(c);
      c.score = clipScore(c);
      return c;
    }).filter((c) => c.relevant || c.market).sort((a, b) => b.score - a.score).slice(0, 60);
    store('clips', S.clips.map(({ iol, ...c }) => c));
    renderAll();
  }

  // relevancia de un clip: calor de los eventos que toca, frescura y vistas
  function clipScore(c) {
    const heat = c.conf.reduce((s, id) => s + (S.heat[id] || conflictById[id].base), 0) + (c.dis ? 3 : 0) + (c.market ? 1 : 0);
    const fresh = Math.exp(-(Date.now() - c.t) / (18 * 3600e3));
    return heat * 0.6 + fresh * 4 + Math.log10(1 + (c.views || 0)) * 0.4;
  }

  /* ================= radios ================= */
  async function refreshRadios() {
    const cached = load('radios');
    if (cached && cached.t > Date.now() - 2 * 864e5 && cached.list.length) { S.radios = cached.list; renderLive(); return; }
    let found = [];
    for (const srv of M.radioServers) {
      const res = await Promise.allSettled(M.radios.map(async (r) => {
        const url = `${srv}/json/stations/search?name=${encodeURIComponent(r.q)}${r.country ? '&countrycode=' + r.country : ''}&hidebroken=true&order=votes&reverse=true&limit=4`;
        const arr = await getJSON(url);
        const st = arr.find((s) => !s.hls && s.url_resolved && /mp3|aac|ogg/i.test(s.codec || 'mp3')) || arr[0];
        return st ? { name: st.name.trim(), url: st.url_resolved, logo: st.favicon, country: st.countrycode, tags: st.tags, codec: st.codec, group: r.group, uuid: st.stationuuid } : null;
      }));
      found = res.filter((r) => r.status === 'fulfilled' && r.value).map((r) => r.value);
      if (found.length) break;
    }
    // completar con las radios de noticias más votadas de Argentina
    try {
      const extra = await getJSON(`${M.radioServers[0]}/json/stations/search?countrycode=AR&tag=news&hidebroken=true&order=clickcount&reverse=true&limit=10`);
      extra.forEach((st) => { if (!st.hls && !found.some((f) => f.uuid === st.stationuuid || f.name === st.name.trim())) found.push({ name: st.name.trim(), url: st.url_resolved, logo: st.favicon, country: st.countrycode, tags: st.tags, codec: st.codec, group: 'Más escuchadas · Argentina', uuid: st.stationuuid }); });
    } catch (e) { /* opcional */ }
    const uniq = []; found.forEach((f) => { if (!uniq.some((u) => u.uuid === f.uuid)) uniq.push(f); });
    if (uniq.length) S.radios = uniq;
    if (uniq.length) store('radios', { t: Date.now(), list: uniq });
    renderLive();
  }

  /* ================= calor, eventos y proyección ================= */
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

  // eventos unificados: conflictos + desastres relevantes
  function events() {
    const short = S.horizon === 'short';
    const ev = D.conflicts.map((c) => {
      const heat = S.heat[c.id] || c.base;
      const w = short ? (heat / 5) * (0.6 + 0.4 * Math.min(1, (S.newsCount[c.id] || 0) / 10)) : (c.base * 0.6 + heat * 0.4) / 5;
      return { id: 'c:' + c.id, kind: 'conflict', ref: c.id, name: c.name, short: c.short, lat: c.lat, lon: c.lon, w, shock: M.conflictShock[c.id] || {} };
    });
    S.disasters.filter((d) => d.sev >= 0.3).slice(0, 14).forEach((d) => {
      const age = (Date.now() - d.t) / 3600e3;
      const w = short ? d.sev * Math.exp(-Math.max(0, age) / 72) : d.sev * 0.35;
      ev.push({ id: 'd:' + d.id, kind: 'disaster', ref: d.id, name: d.title, short: M.disasterTypes[d.type].name, lat: d.lat, lon: d.lon, w, shock: d.shock || {} });
    });
    return ev;
  }

  function computeProjection() {
    const ev = events();
    const P = {}; const why = {};
    ev.forEach((e) => Object.entries(e.shock).forEach(([f, v]) => {
      const x = v * e.w; P[f] = (P[f] || 0) + x;
      (why[f] = why[f] || []).push({ ev: e, x });
    }));
    Object.values(why).forEach((l) => l.sort((a, b) => b.x - a.x));
    const scores = allInstruments().map((ins) => {
      let s = 0; const parts = [];
      Object.entries(ins.sens).forEach(([f, v]) => { const x = (P[f] || 0) * v; s += x; if (Math.abs(x) > 0.02) parts.push({ f, x }); });
      parts.sort((a, b) => Math.abs(b.x) - Math.abs(a.x));
      return { ins, s, parts };
    });
    const max = Math.max(0.01, ...scores.map((x) => Math.abs(x.s)));
    scores.forEach((x) => { x.n = Math.round((x.s / max) * 100); });
    S.proj = {
      P, why, ev, scores,
      favor: scores.filter((x) => x.n >= 8).sort((a, b) => b.s - a.s),
      contra: scores.filter((x) => x.n <= -8).sort((a, b) => a.s - b.s),
      byId: Object.fromEntries(scores.map((x) => [x.ins.id, x]))
    };
  }

  function computeRisk() {
    const q = (s) => S.quotes[s];
    const vix = q('^VIX') ? q('^VIX').price : null;
    const vixScore = vix != null ? clamp((vix - 11) / (40 - 11), 0, 1) * 100 : 40;
    const heats = D.conflicts.map((c) => S.heat[c.id] || c.base);
    const heatScore = (heats.reduce((a, b) => a + b, 0) / (heats.length * 5)) * 100;
    const disScore = clamp(S.disasters.reduce((s, d) => s + (d.sev >= 0.7 ? d.sev : 0), 0) * 20, 0, 100);
    const oil = q('BZ=F') ? q('BZ=F').chg || 0 : 0;
    const gold = q('GC=F') ? q('GC=F').chg || 0 : 0;
    const shock = clamp(oil * 12, 0, 40) + clamp(gold * 15, 0, 40);
    S.risk = Math.round(clamp(vixScore * 0.38 + heatScore * 0.37 + disScore * 0.1 + shock * 0.15 * 2.5, 0, 100));
    S.riskParts = { vix, vixScore, heatScore, disScore, oil, gold };
  }

  function afterData() {
    computeHeat(); computeProjection(); computeRisk();
    S.news.forEach((n) => { n.iol = relatedInstruments(n); });
    S.clips.forEach((c) => { c.score = clipScore(c); });
    ConnGraph.rebuild(); ProjGraph.rebuild();
    renderAll();
  }

  /* ================= modo demostración (solo vista previa en navegador) ================= */
  function enableDemo() {
    if (S.demo) return;
    S.demo = true;
    let seed = 7; const rnd = () => ((seed = (seed * 9301 + 49297) % 233280) / 233280);
    const base = { '^GSPC': 6650, '^IXIC': 22400, '^DJI': 46200, '^VIX': 18.4, '^STOXX50E': 5600, '^GDAXI': 24100, '^FTSE': 9400, '^N225': 47000, '^HSI': 25800, '000001.SS': 3850, '^TWII': 26500, '^KS11': 3600, '^BSESN': 82000, 'TA35.TA': 3200, '^BVSP': 145000, '^MERV': 2300000, 'BZ=F': 66, 'CL=F': 62, 'GC=F': 4100, 'TTF=F': 32, 'ZW=F': 520, 'HG=F': 4.9, 'EURUSD=X': 1.16, 'ILS=X': 3.3, 'KRW=X': 1420, 'INR=X': 88, 'BTC-USD': 108000, 'LMT': 480, 'RHM.DE': 1900, 'XOM': 114, 'MAERSK-B.CO': 12500, 'ZIM': 14, 'URA': 48, 'REMX': 70, 'GLEN.L': 360, 'TSM': 290, 'NVDA': 182, '005930.KS': 97000 };
    D.assets.forEach((a) => {
      const b = base[a.id] || 100; const drift = (rnd() - 0.5) * 0.035;
      const series = []; let v = b;
      for (let i = 0; i < 60; i++) { v *= 1 + drift / 60 + (rnd() - 0.5) * 0.004; series.push(v); }
      S.quotes[a.id] = { price: v, prev: b, chg: ((v - b) / b) * 100, series, t: Date.now() };
    });
    M.instruments.forEach((i) => { S.arg[i.id] = { price: (i.type === 'Bonos' ? 80000 : 3000) * (0.3 + rnd() * 3), chg: (rnd() - 0.45) * 5 }; });
    S.lecaps = ['S28N6', 'S30E7', 'S31M7', 'T30J7'].map((s) => ({ id: s, name: (s[0] === 'S' ? 'LECAP ' : 'BONCAP ') + s, type: 'LECAPs', src: 'arg_notes', sens: M.lecapSens }));
    S.lecaps.forEach((l) => { S.arg[l.id] = { price: 100 + rnd() * 20, chg: (rnd() - 0.4) * 0.6 }; });
    const now = Date.now();
    S.disasters = [
      { id: 'demo1', type: 'EQ', title: 'Sismo M6.4 · costa de Japón (ejemplo)', place: 'Japón', lat: 36.2, lon: 141.5, sev: 0.65, mag: 6.4, t: now - 3 * 3600e3, link: 'https://earthquake.usgs.gov', src: 'Ejemplo' },
      { id: 'demo2', type: 'TC', title: 'Huracán en el Golfo de México (ejemplo)', place: 'EE.UU.', lat: 25.5, lon: -90, sev: 0.9, t: now - 5 * 3600e3, link: 'https://www.gdacs.org', src: 'Ejemplo' },
      { id: 'demo3', type: 'VO', title: 'Erupción volcánica en Indonesia (ejemplo)', place: 'Indonesia', lat: -7.5, lon: 110.4, sev: 0.5, t: now - 20 * 3600e3, link: 'https://www.gdacs.org', src: 'Ejemplo' },
      { id: 'demo4', type: 'DR', title: 'Sequía en la región pampeana (ejemplo)', place: 'Argentina', lat: -35, lon: -62, sev: 0.55, t: now - 30 * 3600e3, link: 'https://www.gdacs.org', src: 'Ejemplo' },
      { id: 'demo5', type: 'WF', title: 'Incendios forestales en California (ejemplo)', place: 'EE.UU.', lat: 38.5, lon: -121.5, sev: 0.35, t: now - 8 * 3600e3, link: 'https://eonet.gsfc.nasa.gov', src: 'Ejemplo' }
    ].map((d) => Object.assign(d, { region: M.regionOf(d.lat, d.lon), shock: M.disasterShock(d.type, d.lat, d.lon) }));
    const demo = [
      ['Ataques con drones contra la red eléctrica de Ucrania elevan el precio del gas en Europa', 12],
      ['El petróleo Brent sube por temor a interrupciones en el estrecho de Ormuz', 25],
      ['Huracán avanza sobre el Golfo de México: cierran plataformas petroleras', 33],
      ['Navieras vuelven a desviar buques por ataques en el Mar Rojo; suben los fletes', 40],
      ['El oro marca nuevos máximos ante la escalada en Medio Oriente', 55],
      ['China realiza maniobras militares cerca de Taiwán; caen las tecnológicas asiáticas', 70],
      ['Sismo de magnitud 6,4 sacude Japón: evalúan daños en fábricas de chips', 85],
      ['Combates en Darfur: la ONU alerta por la crisis humanitaria en Sudán', 95],
      ['El M23 avanza en Kivu del Norte; el cobre reacciona en Londres', 130],
      ['Corea del Norte lanza un misil balístico; el KOSPI abre a la baja', 160],
      ['La sequía golpea la cosecha de soja y el riesgo país argentino sube', 190],
      ['Rheinmetall y las acciones de defensa europeas suben tras nuevo paquete de ayuda militar', 240],
      ['Junta de Malí y grupos yihadistas: el uranio de Níger vuelve al foco', 320]
    ];
    S.news = demo.map(([title, m], i) => analyze({ title, link: 'https://news.google.com', source: 'Ejemplo (sin conexión)', t: now - m * 60000, desc: 'Titular de demostración: la app muestra noticias reales cuando tiene conexión desde el teléfono.', key: 'demo' + i }));
    S.clips = [
      ['Así fue el ataque con drones sobre Kiev', 'DW Español', 50], ['Ormuz: por qué el petróleo puede dispararse', 'France 24 Español', 120],
      ['Huracán en el Golfo: imágenes satelitales', 'CNN en Español', 200], ['Taiwán y la guerra de los chips, explicado', 'BBC Mundo', 400]
    ].map(([title, channel, m], i) => analyze({ vid: 'demo' + i, title, channel, t: now - m * 60000, thumb: '', views: 10000, desc: '' }));
    S.clips.forEach((c) => { c.score = clipScore(c); });
    S.radios = [{ name: 'Radio Mitre (ejemplo)', url: '', group: 'Argentina' }, { name: 'Cadena 3 (ejemplo)', url: '', group: 'Argentina' }, { name: 'BBC World Service (ejemplo)', url: '', group: 'Internacional' }];
    afterData();
  }

  /* ================= header ================= */
  function setStatus(on) {
    $('.live-dot').classList.toggle('off', !on);
    let txt;
    if (S.demo) txt = 'Vista previa · datos de ejemplo';
    else if (!on) txt = 'Conectando…';
    else {
      const t = Math.max(S.lastNewsAt, S.lastQuotesAt);
      txt = 'En vivo · ' + (t ? new Date(t).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' }) : '…');
    }
    $('#status').textContent = txt;
  }

  function renderHeader() {
    setStatus(S.online || S.demo);
    const r = S.risk; const el = $('#riskVal');
    el.textContent = r == null ? '—' : r;
    el.style.color = r == null ? '' : r >= 66 ? 'var(--red)' : r >= 40 ? 'var(--gold)' : 'var(--up)';
    const parts = ['^GSPC', '^MERV', 'BZ=F', 'GC=F', '^VIX'].map((s) => {
      const q = S.quotes[s]; if (!q) return '';
      return `<span><b>${esc(assetById[s].name)}</b>${fmtPrice(q.price)} <em class="${chgClass(q.chg)}-t">${fmtChg(q.chg)}</em></span>`;
    }).concat(['GGAL', 'YPFD', 'AL30', 'GD30', 'VIST', 'MELI', 'SPY'].map((s) => {
      const q = S.arg[s]; if (!q) return '';
      return `<span><b>${s}</b>$${fmtPrice(q.price)} <em class="${chgClass(q.chg)}-t">${fmtChg(q.chg)}</em></span>`;
    })).concat(['^IXIC', '^STOXX50E', 'TTF=F', 'ZW=F', 'BTC-USD', 'EURUSD=X'].map((s) => {
      const q = S.quotes[s]; if (!q) return '';
      return `<span><b>${esc(assetById[s].name)}</b>${fmtPrice(q.price)} <em class="${chgClass(q.chg)}-t">${fmtChg(q.chg)}</em></span>`;
    }));
    const html = parts.join('');
    const track = $('#tapeTrack');
    if (track.dataset.html !== html) { track.innerHTML = html || '<span>Cargando cotizaciones…</span>'; track.dataset.html = html; }
    const b = $('#newsBadge');
    b.textContent = S.unseen > 99 ? '99+' : S.unseen;
    b.classList.toggle('show', S.unseen > 0);
  }

  const heatBars = (h) => '<span class="heat">' + [1, 2, 3, 4, 5].map((i) => `<i class="${i <= h ? 'on' : ''}"></i>`).join('') + '</span>';

  function renderHotStrip() {
    const conf = D.conflicts.map((c) => ({ c, k: (S.heat[c.id] || c.base) + (S.newsCount[c.id] || 0) / 100 }));
    const dis = S.disasters.filter((d) => d.sev >= 0.6).slice(0, 4).map((d) => ({ d, k: d.sev * 5 - 0.5 }));
    const list = conf.concat(dis).sort((a, b) => b.k - a.k);
    $('#hotStrip').innerHTML = list.map((x) => {
      if (x.d) {
        const T = M.disasterTypes[x.d.type];
        return `<button class="hot dis" data-dis="${esc(x.d.id)}" style="--dc:${T.color}">
          <div class="h-name"><i class="dot" style="background:${T.color}"></i>${esc(T.name)}</div>
          ${heatBars(Math.round(x.d.sev * 5))}
          <div class="h-meta">${esc((x.d.place || x.d.title).slice(0, 26))} · ${ago(x.d.t)}</div></button>`;
      }
      const c = x.c; const top = S.proj && S.proj.scores.filter((s) => (M.conflictShock[c.id] || {}) && Object.keys(s.ins.sens).some((f) => (M.conflictShock[c.id] || {})[f])).sort((a, b) => Math.abs(b.s) - Math.abs(a.s))[0];
      return `<button class="hot" data-conf="${c.id}">
        <div class="h-name"><i class="dot red"></i>${esc(c.short)}</div>
        ${heatBars(S.heat[c.id] || c.base)}
        <div class="h-meta">${S.newsCount[c.id] || 0} noticias 24h${top ? ` · <span class="${top.s >= 0 ? 'up' : 'down'}-t">${top.ins.id} ${top.s >= 0 ? '▲' : '▼'}</span>` : ''}</div>
      </button>`;
    }).join('');
  }

  /* ================= piezas visuales ================= */
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

  function projBadge(id) {
    const p = S.proj && S.proj.byId[id];
    if (!p || Math.abs(p.n) < 8) return '<span class="pbadge flat">neutral</span>';
    return `<span class="pbadge ${p.n > 0 ? 'up' : 'down'}">${p.n > 0 ? '▲ a favor' : '▼ en contra'} ${Math.abs(p.n)}</span>`;
  }

  function iolRow(id) {
    const ins = instById(id); if (!ins) return '';
    const q = S.arg[id]; const c = chgClass(q && q.chg);
    return `<button class="mrow iol" data-ins="${esc(id)}">
      <div><div class="nm">${esc(ins.name)}</div><div class="sym">${esc(id)} · ${esc(ins.type)}</div></div>
      ${projBadge(id)}
      <div><div class="px">${q ? '$' + fmtPrice(q.price) : '—'}</div><div class="chg ${c}">${q && q.chg != null ? fmtChg(q.chg) : 'sin datos'}</div></div>
    </button>`;
  }

  function iolChip(x) {
    const q = S.arg[x.id];
    const cls = !x.dir ? 'rel' : x.s > 0 ? 'up' : 'down';
    return `<span class="tag ins ${cls}" data-ins="${esc(x.id)}">${x.dir ? (x.s > 0 ? '▲ ' : '▼ ') : ''}${esc(x.id)}${q && q.chg != null ? ` <em class="${chgClass(q.chg)}-t">${fmtChg(q.chg)}</em>` : ''}</span>`;
  }

  /* ================= momentos clave (fragmentos automáticos) ================= */
  function moments() {
    if (!S.proj) return [];
    const list = S.proj.ev.slice().sort((a, b) => b.w - a.w).slice(0, 7);
    return list.map((e) => {
      const news = S.news.filter((n) => (e.kind === 'conflict' ? n.conf.includes(e.ref) : n.dis && n.dis === (S.disasters.find((d) => d.id === e.ref) || {}).type));
      const clip = S.clips.find((c) => (e.kind === 'conflict' ? c.conf.includes(e.ref) : c.dis && c.dis === (S.disasters.find((d) => d.id === e.ref) || {}).type));
      const affected = S.proj.scores.map((s) => {
        let x = 0; Object.entries(e.shock).forEach(([f, v]) => { x += v * e.w * (s.ins.sens[f] || 0); });
        return { id: s.ins.id, s: x, dir: true };
      }).filter((x) => Math.abs(x.s) > 0.05).sort((a, b) => Math.abs(b.s) - Math.abs(a.s));
      const fav = affected.filter((x) => x.s > 0).slice(0, 2); const con = affected.filter((x) => x.s < 0).slice(0, 2);
      return { e, headline: news[0], clip, fav, con, count: news.length };
    });
  }

  function momentCard(m) {
    const e = m.e;
    const dis = e.kind === 'disaster' ? S.disasters.find((d) => d.id === e.ref) : null;
    const color = dis ? M.disasterTypes[dis.type].color : '#FF4D6D';
    const kicker = dis ? M.disasterTypes[dis.type].name : 'Conflicto · ' + conflictById[e.ref].region;
    const bg = m.clip && m.clip.thumb ? `style="--mc:${color};background-image:linear-gradient(180deg,rgba(7,11,20,.15),rgba(7,11,20,.95) 70%),url('${esc(m.clip.thumb)}')"` : `style="--mc:${color}"`;
    return `<button class="moment" ${bg} data-${e.kind === 'conflict' ? 'conf' : 'dis'}="${esc(e.ref)}">
      <div class="m-kicker"><i class="dot" style="background:${color}"></i>${esc(kicker)}${m.clip ? '<span class="m-play">▶ clip</span>' : ''}</div>
      <div class="m-title">${esc(m.headline ? m.headline.title : e.name)}</div>
      <div class="m-meta">${m.count} noticias · intensidad ${Math.round(e.w * 100)}</div>
      <div class="m-ins">${m.fav.map(iolChip).join('')}${m.con.map(iolChip).join('')}</div>
    </button>`;
  }

  function clipCard(c, big) {
    const tags = c.conf.map((id) => `<span class="tag conf">${esc(conflictById[id].short)}</span>`).concat(c.dis ? [`<span class="tag" style="color:${M.disasterTypes[c.dis].color}">${esc(M.disasterTypes[c.dis].name)}</span>`] : []);
    return `<button class="clip ${big ? 'big' : ''}" data-clip="${esc(c.vid)}">
      <div class="thumb" ${c.thumb ? `style="background-image:url('${esc(c.thumb)}')"` : ''}><span class="play">▶</span><span class="ch">${esc(c.channel)}</span></div>
      <div class="c-title">${esc(c.title)}</div>
      <div class="c-meta">${ago(c.t)}${c.views ? ' · ' + nf(0).format(c.views) + ' vistas' : ''}</div>
      ${big ? `<div class="tags">${tags.join('')}${(c.iol || []).slice(0, 3).map(iolChip).join('')}</div>` : ''}
    </button>`;
  }

  /* ================= vista noticias ================= */
  function newsCard(n) {
    const tags = n.conf.map((id) => `<span class="tag conf">${esc(conflictById[id].short)}</span>`)
      .concat(n.dis ? [`<span class="tag" style="color:${M.disasterTypes[n.dis].color};background:rgba(255,255,255,.06)">${esc(M.disasterTypes[n.dis].name)}</span>`] : [])
      .concat(n.assets.slice(0, 2).map((s) => { const q = S.quotes[s]; return `<span class="tag ${chgClass(q && q.chg)}">${esc(assetById[s].name)} ${q ? fmtChg(q.chg) : ''}</span>`; }));
    const clip = S.clips.find((c) => (n.conf.length && c.conf.some((x) => n.conf.includes(x))) || (n.dis && c.dis === n.dis));
    return `<article class="news ${n.isNew ? 'fresh' : ''}" data-news="${esc(n.key)}">
      <div class="meta">${n.isNew ? '<span class="new-pill">NUEVO</span>' : ''}<span class="src">${esc(n.source)}</span>·<span>${ago(n.t)}</span></div>
      <h3>${esc(n.title)}</h3>
      ${tags.length ? `<div class="tags">${tags.join('')}</div>` : ''}
      ${n.iol && n.iol.length ? `<div class="iol-line"><span class="iol-lbl">IOL</span>${n.iol.map(iolChip).join('')}</div>` : ''}
      ${clip ? `<div class="news-clip" data-clip="${esc(clip.vid)}"><span class="nc-thumb" ${clip.thumb ? `style="background-image:url('${esc(clip.thumb)}')"` : ''}>▶</span><span><b>${esc(clip.channel)}</b> ${esc(clip.title.slice(0, 70))}</span></div>` : ''}
    </article>`;
  }

  function renderNews() {
    const counts = { all: S.news.length, market: S.news.filter((n) => n.market).length, dis: S.news.filter((n) => n.dis).length };
    D.conflicts.forEach((c) => { counts[c.id] = S.news.filter((n) => n.conf.includes(c.id)).length; });
    const chips = [['all', 'Todas'], ['market', 'Mercados'], ['dis', 'Desastres']].concat(D.conflicts.filter((c) => counts[c.id]).sort((a, b) => counts[b.id] - counts[a.id]).map((c) => [c.id, c.short]));
    $('#newsFilters').innerHTML = chips.map(([k, l]) => `<button class="chip ${S.newsFilter === k ? 'on' : ''}" data-nf="${k}">${esc(l)}<span class="n">${counts[k] || 0}</span></button>`).join('');
    const ms = moments();
    $('#moments').innerHTML = ms.length ? `<h4 class="sec">Lo clave ahora <small>fragmentos automáticos</small></h4><div class="hscroll">${ms.map(momentCard).join('')}</div>` : '';
    const f = S.newsFilter;
    const list = S.news.filter((n) => f === 'all' || (f === 'market' ? n.market : f === 'dis' ? n.dis : n.conf.includes(f)));
    $('#newsList').innerHTML = list.length ? list.slice(0, 120).map(newsCard).join('')
      : `<div class="empty">${S.online || S.demo ? 'No hay noticias para este filtro todavía.' : 'Buscando noticias en vivo…<br>Revisá tu conexión a internet.'}</div>`;
  }

  /* ================= vista mercados ================= */
  function renderMarkets() {
    $$('#mktSeg button').forEach((b) => b.classList.toggle('on', b.dataset.mkt === S.mktTab));
    const r = S.risk == null ? 0 : S.risk; const p = S.riskParts || {};
    const label = r >= 66 ? 'Alta' : r >= 40 ? 'Moderada' : 'Baja';
    $('#pulseCard').innerHTML = `
      <h2>Índice de tensión global</h2>
      <div class="big">${S.risk == null ? '—' : r}<span style="font-size:15px;color:var(--muted);font-weight:600"> / 100 · ${label}</span></div>
      <div class="bar"><i style="left:calc(${r}% - 2px)"></i></div>
      <p>VIX ${p.vix != null ? nf(1).format(p.vix) : '—'} · conflictos ${Math.round(p.heatScore || 0)}/100 · desastres ${Math.round(p.disScore || 0)}/100 · petróleo ${fmtChg(p.oil)} · oro ${fmtChg(p.gold)}.</p>`;
    if (S.mktTab === 'arg') {
      const types = ['Acciones', 'CEDEARs', 'ETFs', 'Bonos', 'LECAPs'];
      $('#marketList').innerHTML = types.map((t) => {
        const list = allInstruments().filter((i) => i.type === t);
        if (!list.length) return '';
        return `<div class="mgroup"><h4>${t === 'LECAPs' ? 'LECAPs / BONCAPs' : t}</h4>${list.map((i) => iolRow(i.id)).join('')}</div>`;
      }).join('') + '<p class="fine">Precios en pesos de BYMA (paneles de data912.com, con demora). La proyección es un modelo de factores, no una recomendación.</p>';
    } else {
      const cats = [];
      D.assets.forEach((a) => { if (!cats.includes(a.cat)) cats.push(a.cat); });
      $('#marketList').innerHTML = cats.map((cat) => `<div class="mgroup"><h4>${esc(cat)}</h4>${D.assets.filter((a) => a.cat === cat).map((a) => marketRow(a.id)).join('')}</div>`).join('') + '<p class="fine">Cotizaciones con demora según la plaza. Fuente: Yahoo Finance.</p>';
    }
  }

  /* ================= vista en vivo: radios + clips ================= */
  const player = { audio: new Audio(), st: null };
  player.audio.preload = 'none';
  player.audio.addEventListener('playing', () => { $('#player').classList.add('playing'); renderLive(); });
  player.audio.addEventListener('pause', () => { $('#player').classList.remove('playing'); renderLive(); });
  player.audio.addEventListener('waiting', () => { $('#plState').textContent = 'Conectando…'; });
  player.audio.addEventListener('playing', () => { $('#plState').textContent = 'En vivo'; });
  player.audio.addEventListener('error', () => { $('#plState').textContent = 'No se pudo reproducir'; toast('Esa radio no responde, probá otra'); });

  function playRadio(i) {
    const st = S.radios[i]; if (!st) return;
    if (!st.url) { toast('Disponible con conexión desde el teléfono'); return; }
    if (player.st === st && !player.audio.paused) { player.audio.pause(); return; }
    player.st = st;
    player.audio.src = st.url;
    player.audio.play().catch(() => toast('No se pudo reproducir'));
    $('#plName').textContent = st.name;
    $('#plLogo').style.backgroundImage = st.logo ? `url('${st.logo.replace(/'/g, '')}')` : '';
    $('#plState').textContent = 'Conectando…';
    $('#player').classList.add('show');
    document.body.classList.add('has-player');
    if (window.Native && window.Native.keepAwake) window.Native.keepAwake(true);
  }
  function stopRadio() {
    player.audio.pause(); player.audio.removeAttribute('src'); player.st = null;
    $('#player').classList.remove('show'); document.body.classList.remove('has-player');
    renderLive();
  }

  function renderLive() {
    if (S.view !== 'live') return;
    const groups = [];
    S.radios.forEach((r) => { if (!groups.includes(r.group)) groups.push(r.group); });
    $('#radioList').innerHTML = S.radios.length ? groups.map((g) => `<h4 class="sec">${esc(g)}</h4><div class="radios">${S.radios.map((r, i) => r.group !== g ? '' : `
      <button class="radio ${player.st === r && !player.audio.paused ? 'on' : ''}" data-radio="${i}">
        <span class="r-logo" ${r.logo ? `style="background-image:url('${esc(r.logo)}')"` : ''}>${r.logo ? '' : '📻'}</span>
        <span class="r-name">${esc(r.name)}</span>
        <span class="r-eq"><i></i><i></i><i></i></span>
      </button>`).join('')}</div>`).join('')
      : `<div class="empty small">Buscando radios en vivo…</div>`;
    const clips = S.clips.slice(0, 30);
    $('#clipList').innerHTML = clips.length ? clips.map((c) => clipCard(c, true)).join('') : '<div class="empty small">Buscando clips de los últimos días…</div>';
    const ms = moments();
    $('#liveMoments').innerHTML = ms.length ? `<div class="hscroll">${ms.map(momentCard).join('')}</div>` : '';
  }

  /* ================= hoja inferior ================= */
  const sheet = { open: false };
  function openSheet(html) {
    $('#sheetBody').innerHTML = html; $('#sheetBody').scrollTop = 0;
    $('#sheet').classList.add('show'); $('#backdrop').classList.add('show'); sheet.open = true;
  }
  function closeSheet() { $('#sheet').classList.remove('show'); $('#backdrop').classList.remove('show'); sheet.open = false; Globe.select(null); }

  function miniNews(list) {
    return list.slice(0, 6).map((n) => `<button class="mini-news" data-news="${esc(n.key)}">${esc(n.title)}<small>${esc(n.source)} · ${ago(n.t)}</small></button>`).join('') || '<p style="color:var(--muted)">Sin noticias recientes.</p>';
  }
  function evAffected(e) {
    return S.proj.scores.map((s) => {
      let x = 0; Object.entries(e.shock).forEach(([f, v]) => { x += v * e.w * (s.ins.sens[f] || 0); });
      return { id: s.ins.id, s: x, dir: true };
    }).filter((x) => Math.abs(x.s) > 0.03).sort((a, b) => b.s - a.s);
  }
  function shockList(shock) {
    return Object.entries(shock).sort((a, b) => b[1] - a[1]).map(([f, v]) => `<span class="tag fac">${esc(M.factors[f].name)} ↑ <small>${esc(M.factors[f].up)}</small></span>`).join('');
  }
  function affectedBlock(e) {
    if (!S.proj) return '';
    const a = evAffected(e);
    const fav = a.filter((x) => x.s > 0).slice(0, 6); const con = a.filter((x) => x.s < 0).reverse().slice(0, 6);
    return `<h5>Factores que mueve</h5><div class="tags">${shockList(e.shock)}</div>
      <div class="two"><div><h5 class="up-t">A favor (IOL)</h5>${fav.map((x) => iolRow(x.id)).join('') || '<p class="muted">—</p>'}</div>
      <div><h5 class="down-t">En contra (IOL)</h5>${con.map((x) => iolRow(x.id)).join('') || '<p class="muted">—</p>'}</div></div>`;
  }
  function clipsFor(pred) {
    const list = S.clips.filter(pred).slice(0, 6);
    return list.length ? `<h5>Clips</h5><div class="hscroll">${list.map((c) => clipCard(c)).join('')}</div>` : '';
  }

  function showConflict(id) {
    const c = conflictById[id]; if (!c) return;
    const e = S.proj && S.proj.ev.find((x) => x.id === 'c:' + id);
    const news = S.news.filter((n) => n.conf.includes(id));
    openSheet(`
      <div class="kicker">${esc(c.region)}</div>
      <h2>${esc(c.name)}</h2>${heatBars(S.heat[id] || c.base)}
      <div class="stats">
        <div class="stat"><b>${S.newsCount[id] || 0}</b><span>noticias 24 h</span></div>
        <div class="stat"><b>${e ? Math.round(e.w * 100) : '—'}</b><span>intensidad</span></div>
        <div class="stat"><b>${S.clips.filter((x) => x.conf.includes(id)).length}</b><span>clips</span></div>
      </div>
      <p>${esc(c.summary)}</p>
      <h5>Canales de impacto</h5><p>${esc(c.channels)}</p>
      ${e ? affectedBlock(e) : ''}
      ${clipsFor((x) => x.conf.includes(id))}
      <h5>Mercados globales</h5>${c.assets.map(marketRow).join('')}
      <h5>Últimas noticias</h5>${miniNews(news)}
      <div class="row-btns"><button class="btn" data-go="proj" data-focus="c:${id}">Ver proyección</button><button class="btn ghost" data-go="globe" data-conf-fly="${id}">Ver en el globo</button></div>`);
  }

  function showDisaster(id) {
    const d = S.disasters.find((x) => x.id === id); if (!d) return;
    const T = M.disasterTypes[d.type];
    const e = S.proj && S.proj.ev.find((x) => x.id === 'd:' + id) || { id: 'd:' + id, w: d.sev, shock: d.shock || {} };
    const news = S.news.filter((n) => n.dis === d.type);
    openSheet(`
      <div class="kicker" style="color:${T.color}">${esc(T.name)} · ${esc(d.src)}</div>
      <h2>${esc(d.title)}</h2>
      <div class="stats">
        <div class="stat"><b>${d.mag ? 'M' + d.mag.toFixed(1) : d.level ? d.level.toUpperCase() : Math.round(d.sev * 100)}</b><span>${d.mag ? 'magnitud' : d.level ? 'alerta' : 'severidad'}</span></div>
        <div class="stat"><b>${ago(d.t)}</b><span>${new Date(d.t).toLocaleString('es-AR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}</span></div>
        <div class="stat"><b>${nf(1).format(d.lat)}, ${nf(1).format(d.lon)}</b><span>${esc(d.place || d.region)}</span></div>
      </div>
      ${d.tsunami ? '<p class="warn">⚠ USGS marca posible alerta de tsunami.</p>' : ''}
      ${affectedBlock(e)}
      ${clipsFor((x) => x.dis === d.type)}
      <h5>Noticias de ${esc(T.name.toLowerCase())}</h5>${miniNews(news)}
      <div class="row-btns"><button class="btn" data-open="${esc(d.link)}">Fuente oficial</button><button class="btn ghost" data-go="proj" data-focus="d:${esc(d.id)}">Ver proyección</button></div>`);
  }

  function showAsset(sym) {
    const a = assetById[sym]; const q = S.quotes[sym]; if (!a) return;
    const conf = D.conflicts.filter((c) => c.assets.includes(sym));
    const news = S.news.filter((n) => n.assets.includes(sym));
    const c = chgClass(q && q.chg);
    openSheet(`
      <div class="kicker">${esc(a.cat)} · ${esc(a.hub)}</div>
      <h2>${esc(a.name)}</h2>
      <div class="pxline"><span class="bigpx">${q ? fmtPrice(q.price) : '—'}</span><span class="chg ${c}">${q ? fmtChg(q.chg) : 'sin datos'}</span></div>
      <div style="margin:12px 0">${q ? spark(q.series, c, 320, 90).replace('class="spark"', 'style="width:100%;height:90px"') : ''}</div>
      ${a.inverse ? '<p>Índice de volatilidad: cuando sube, el mercado descuenta más miedo.</p>' : ''}
      <h5>Conflictos que lo afectan</h5>
      ${conf.map((x) => `<button class="mini-news" data-conf="${x.id}">${esc(x.name)}<small>${esc(x.channels)}</small></button>`).join('') || '<p class="muted">Activo de referencia global.</p>'}
      <h5>Noticias relacionadas</h5>${miniNews(news)}
      <div class="row-btns"><button class="btn" data-go="proj" data-mode="conn" data-focus="a:${esc(sym)}">Ver en la red</button></div>`);
  }

  function showInstrument(id) {
    const ins = instById(id); if (!ins) return;
    const q = S.arg[id]; const c = chgClass(q && q.chg);
    const p = S.proj && S.proj.byId[id];
    const why = p ? p.parts.slice(0, 4).map((x) => {
      const evs = (S.proj.why[x.f] || []).slice(0, 3).map((w) => w.ev.short).join(', ');
      const dirF = (S.proj.P[x.f] || 0) >= 0 ? '↑' : '↓';
      return `<div class="why ${x.x >= 0 ? 'up' : 'down'}"><b>${esc(M.factors[x.f].name)} ${dirF}</b> ${x.x >= 0 ? 'favorece' : 'perjudica'} <small>por ${esc(evs || '—')}</small></div>`;
    }).join('') : '';
    const sens = Object.entries(ins.sens).map(([f, v]) => `<span class="tag ${v > 0 ? 'up' : 'down'}">${esc(M.factors[f].name)} ${v > 0 ? '+' : '−'}${Math.round(Math.abs(v) * 100)}</span>`).join('');
    const news = S.news.filter((n) => (n.iol || []).some((x) => x.id === id));
    openSheet(`
      <div class="kicker">${esc(ins.type)} · BYMA · operable en IOL</div>
      <h2>${esc(ins.name)} <small class="muted">${esc(id)}</small></h2>
      <div class="pxline"><span class="bigpx">${q ? '$' + fmtPrice(q.price) : '—'}</span><span class="chg ${c}">${q && q.chg != null ? fmtChg(q.chg) : 'sin datos'}</span></div>
      <h5>Proyección por eventos (${S.horizon === 'short' ? 'días' : 'semanas'})</h5>
      ${p ? `<div class="pmeter"><i style="left:${50 + p.n / 2}%"></i></div><div class="pmeter-lbl"><span>en contra</span><b class="${p.n >= 0 ? 'up' : 'down'}-t">${p.n > 0 ? '+' : ''}${p.n}</b><span>a favor</span></div>${why}` : '<p class="muted">Sin datos suficientes.</p>'}
      <h5>Sensibilidad a factores</h5><div class="tags">${sens}</div>
      <h5>Noticias que lo mencionan o afectan</h5>${miniNews(news)}
      <div class="row-btns"><button class="btn" data-open="${esc(M.iolUrl(id))}">Ver en IOL</button><button class="btn ghost" data-go="proj" data-focus="i:${esc(id)}">Ver en proyección</button></div>
      <p class="fine">Modelo heurístico de exposición a factores; no es una recomendación de inversión.</p>`);
  }

  function showNews(key) {
    const n = S.news.find((x) => x.key === key); if (!n) return;
    n.isNew = false;
    openSheet(`
      <div class="kicker">${esc(n.source)} · ${ago(n.t)}</div>
      <h2 style="font-size:19px;line-height:1.3">${esc(n.title)}</h2>
      ${n.desc ? `<p>${esc(n.desc)}</p>` : ''}
      ${n.conf.length ? '<h5>Conflicto</h5>' + n.conf.map((id) => `<button class="mini-news" data-conf="${id}">${esc(conflictById[id].name)}<small>${esc(conflictById[id].region)}</small></button>`).join('') : ''}
      ${n.iol && n.iol.length ? '<h5>Activos relacionados en IOL</h5>' + n.iol.map((x) => iolRow(x.id)).join('') : ''}
      ${clipsFor((c) => (n.conf.length && c.conf.some((x) => n.conf.includes(x))) || (n.dis && c.dis === n.dis))}
      ${n.assets.length ? '<h5>Mercados globales</h5>' + n.assets.map(marketRow).join('') : ''}
      <div class="row-btns"><button class="btn" data-open="${esc(n.link)}">Leer la nota</button><button class="btn ghost" data-share="${esc(n.key)}">Compartir</button></div>`);
  }

  function showRanking() {
    if (!S.proj) return;
    openSheet(`
      <div class="kicker">Proyección ${S.horizon === 'short' ? 'a días' : 'a semanas'} · ${S.proj.ev.length} eventos</div>
      <h2>Quién gana y quién pierde</h2>
      <p>Cada evento empuja factores (petróleo, defensa, riesgo…) y cada instrumento de IOL tiene una exposición a esos factores. El puntaje va de −100 a +100.</p>
      <div class="two"><div><h5 class="up-t">A favor</h5>${S.proj.favor.slice(0, 12).map((x) => iolRow(x.ins.id)).join('') || '<p class="muted">—</p>'}</div>
      <div><h5 class="down-t">En contra</h5>${S.proj.contra.slice(0, 12).map((x) => iolRow(x.ins.id)).join('') || '<p class="muted">—</p>'}</div></div>
      <p class="fine">Modelo heurístico; no predice precios ni es asesoramiento financiero.</p>`);
  }

  /* ================= GLOBO / MAPA ================= */
  const Globe = (function () {
    const canvas = $('#globe'); const ctx = canvas.getContext('2d');
    const world = topojson.feature(window.WORLD_TOPO, window.WORLD_TOPO.objects.countries).features;
    const graticule = d3.geoGraticule10();
    let W = 0, H = 0, mode = 'globe';
    let rot = [-20, -25, 0];
    let zoom = 1, flatZoom = 1, flatY = 0, vel = [0, 0];
    let lastInteract = 0, selected = null, fly = null, running = false;
    const layers = { links: true, markets: true, disasters: true };
    const stars = Array.from({ length: 180 }, () => [Math.random(), Math.random(), Math.random() * 1.2 + 0.2, Math.random()]);
    const proj = { globe: d3.geoOrthographic().clipAngle(90).precision(0.6), flat: d3.geoNaturalEarth1().precision(0.6) };
    let hits = [];
    const pointers = new Map();

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

    function arc(p, path, from, to, col, alpha, width, t, seed, dim) {
      ctx.beginPath(); path({ type: 'LineString', coordinates: [from, to] });
      ctx.strokeStyle = `rgba(${col},${alpha})`; ctx.lineWidth = width;
      ctx.setLineDash([3, 5]); ctx.lineDashOffset = -t / 60; ctx.stroke(); ctx.setLineDash([]);
      if (dim) return;
      const interp = d3.geoInterpolate(from, to);
      const f = ((t / 3200) + seed) % 1;
      const pt = interp(f); const xy = p(pt);
      if (xy && visible(xy, pt)) {
        ctx.fillStyle = `rgba(${col},.95)`; ctx.shadowColor = `rgba(${col},1)`; ctx.shadowBlur = 8;
        ctx.beginPath(); ctx.arc(xy[0], xy[1], 1.8, 0, Math.PI * 2); ctx.fill(); ctx.shadowBlur = 0;
      }
    }

    function draw(t) {
      const p = current();
      const path = d3.geoPath(p, ctx);
      ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
      ctx.clearRect(0, 0, W, H);
      hits = [];

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
      const pulse = 0.5 + 0.5 * Math.sin(t / 700);
      world.forEach((f) => {
        const h = ch[f.properties.name];
        ctx.beginPath(); path(f);
        ctx.fillStyle = h ? `rgba(255,77,109,${0.16 + h * 0.07 + pulse * 0.05})` : '#1b2547';
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

      // conexiones conflicto/desastre -> mercado
      if (layers.links) {
        D.conflicts.forEach((c, ci) => {
          const dim = selected && selected !== c.id;
          c.assets.forEach((s, ai) => {
            const a = assetById[s]; const q = S.quotes[s];
            const col = !q || q.chg == null ? '255,200,87' : q.chg >= 0 ? '46,230,166' : '255,92,122';
            arc(p, path, [c.lon, c.lat], [a.lon, a.lat], col, dim ? 0.05 : selected ? 0.75 : 0.28, selected === c.id ? 1.6 : 1, t, ci * 0.17 + ai * 0.29, dim);
          });
        });
        if (layers.disasters) {
          S.disasters.filter((d) => d.sev >= 0.6).slice(0, 6).forEach((d, di) => {
            const dim = selected && selected !== d.id;
            Object.keys(d.shock || {}).slice(0, 3).forEach((f, k) => {
              const a = assetById[factorAsset[f]]; if (!a) return;
              arc(p, path, [d.lon, d.lat], [a.lon, a.lat], '120,200,255', dim ? 0.04 : 0.3, 1, t, di * 0.21 + k * 0.33, dim);
            });
          });
        }
      }

      // posiciones de etiquetas prioritarias para evitar superposiciones
      const placed = [];
      D.conflicts.forEach((c) => {
        const ll = [c.lon, c.lat]; const xy = p(ll);
        if (xy && visible(xy, ll)) placed.push([xy[0] - 34, xy[1] - 30, 68, 34]);
      });
      const overlaps = (r) => placed.some((q) => r[0] < q[0] + q[2] && r[0] + r[2] > q[0] && r[1] < q[1] + q[3] && r[1] + r[3] > q[1]);

      // desastres naturales
      if (layers.disasters) {
        S.disasters.forEach((d) => {
          const ll = [d.lon, d.lat]; const xy = p(ll);
          if (!xy || !visible(xy, ll)) return;
          const T = M.disasterTypes[d.type]; const r = 2.5 + d.sev * 4.5;
          ctx.save(); ctx.translate(xy[0], xy[1]);
          if (d.type === 'EQ') {
            for (let k = 0; k < 2; k++) {
              const ph = ((t / 1400) + k * 0.5 + d.lon * 0.01) % 1;
              ctx.strokeStyle = T.color; ctx.globalAlpha = (1 - ph) * 0.8; ctx.lineWidth = 1.2;
              ctx.beginPath(); ctx.arc(0, 0, r + ph * (8 + d.sev * 14), 0, Math.PI * 2); ctx.stroke();
            }
            ctx.globalAlpha = 1;
          } else if (d.type === 'TC') {
            ctx.rotate(-t / 500); ctx.strokeStyle = T.color; ctx.lineWidth = 1.8;
            for (let k = 0; k < 3; k++) { ctx.beginPath(); ctx.arc(0, 0, r + 4, k * 2.09, k * 2.09 + 1.2); ctx.stroke(); }
          }
          ctx.fillStyle = T.color; ctx.shadowColor = T.color; ctx.shadowBlur = selected === d.id ? 18 : 8;
          ctx.beginPath();
          if (d.type === 'VO') { ctx.moveTo(0, -r * 1.4); ctx.lineTo(r * 1.2, r); ctx.lineTo(-r * 1.2, r); ctx.closePath(); }
          else if (d.type === 'WF') { for (let k = 0; k < 8; k++) { const a = k * Math.PI / 4; const rr = k % 2 ? r * 0.6 : r * 1.3; ctx[k ? 'lineTo' : 'moveTo'](Math.cos(a) * rr, Math.sin(a) * rr); } ctx.closePath(); }
          else ctx.arc(0, 0, r, 0, Math.PI * 2);
          ctx.fill(); ctx.shadowBlur = 0; ctx.restore();
          if (d.sev >= 0.6 || selected === d.id) {
            const label = d.mag ? 'M' + d.mag.toFixed(1) : T.name;
            ctx.font = '700 10px system-ui, sans-serif'; const w = ctx.measureText(label).width;
            const rect = [xy[0] - w / 2 - 3, xy[1] + r + 3, w + 6, 13];
            if (!overlaps(rect)) {
              placed.push(rect); ctx.textAlign = 'center';
              ctx.lineWidth = 3; ctx.strokeStyle = 'rgba(7,11,20,.85)'; ctx.strokeText(label, xy[0], xy[1] + r + 13);
              ctx.fillStyle = T.color; ctx.fillText(label, xy[0], xy[1] + r + 13);
            }
          }
          hits.push({ x: xy[0], y: xy[1], type: 'dis', id: d.id });
        });
      }

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
            ctx.fillStyle = 'rgba(7,11,20,.7)'; ctx.fillRect(rect[0], rect[1], rect[2], rect[3]);
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
        const k = clamp((t - fly.t0) / 900, 0, 1);
        rot = fly.i(d3.easeCubicInOut(k)); if (k >= 1) fly = null;
      } else if (Math.abs(vel[0]) > 0.01 || Math.abs(vel[1]) > 0.01) {
        rot[0] += vel[0]; rot[1] = clamp(rot[1] + vel[1], -75, 75); vel = [vel[0] * 0.93, vel[1] * 0.93];
      } else if (idle > 3500 && !selected && !pointers.size) {
        rot[0] += mode === 'globe' ? 0.08 : 0.03;
      }
      draw(t);
      requestAnimationFrame(frame);
    }

    // ----- gestos -----
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
      hits.forEach((h) => { const d = Math.hypot(h.x - x, h.y - y) - (h.type === 'conf' ? 6 : h.type === 'dis' ? 3 : 0); if (d < bd) { bd = d; best = h; } });
      if (!best) { selected = null; return; }
      if (best.type === 'conf') { selected = best.id; showConflict(best.id); }
      else if (best.type === 'dis') { selected = best.id; showDisaster(best.id); }
      else {
        const h = best.hub;
        openSheet(`<div class="kicker">Plaza financiera</div><h2>${esc(h.city)}</h2>
          ${h.assets.map(marketRow).join('')}
          <h5>Conflictos con impacto aquí</h5>
          ${D.conflicts.filter((c) => c.assets.some((s) => h.assets.includes(s))).map((c) => `<button class="mini-news" data-conf="${c.id}">${esc(c.name)}<small>${esc(c.channels)}</small></button>`).join('') || '<p class="muted">Sin vínculos directos.</p>'}`);
      }
    }

    function flyToLL(lon, lat, id) {
      selected = id;
      const target = [-lon, mode === 'globe' ? clamp(-lat + 8, -75, 75) : 0, 0];
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
      if (mode === 'flat') { rot[1] = 0; flatY = 0; } else rot[1] = -25;
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
      select(id) { selected = id; },
      flyTo(id) { const c = conflictById[id]; if (c) flyToLL(c.lon, c.lat, id); },
      flyToDisaster(id) { const d = S.disasters.find((x) => x.id === id); if (d) flyToLL(d.lon, d.lat, id); }
    };
  })();

  /* ================= GRAFOS estilo Obsidian ================= */
  function makeGraph(opts) {
    const canvas = opts.canvas; const ctx = canvas.getContext('2d');
    let W = 0, H = 0, transform = d3.zoomIdentity;
    let nodes = [], links = [], byId = {};
    let focus = null, neighbors = new Set(), active = false;
    const hidden = {};

    const sim = d3.forceSimulation()
      .force('link', d3.forceLink().id((d) => d.id).distance((l) => l.dist).strength((l) => l.str))
      .force('charge', d3.forceManyBody().strength((d) => d.charge || -120).distanceMax(480))
      .force('x', d3.forceX((d) => d.fx0 || 0).strength((d) => (d.fx0 != null ? 0.12 : 0.035)))
      .force('y', d3.forceY((d) => d.fy0 || 0).strength((d) => (d.fy0 != null ? 0.18 : 0.035)))
      .force('collide', d3.forceCollide((d) => d.r + 3))
      .alphaDecay(0.025)
      .on('tick', () => { if (active) draw(); });

    function rebuild() {
      const old = byId;
      const g = opts.build(H || 600);
      g.nodes.forEach((n) => { const o = old[n.id]; if (o) { n.x = o.x; n.y = o.y; n.vx = o.vx; n.vy = o.vy; } });
      nodes = g.nodes; links = g.links; byId = Object.fromEntries(nodes.map((n) => [n.id, n]));
      links = links.filter((l) => byId[l.source] && byId[l.target]);
      sim.nodes(nodes); sim.force('link').links(links);
      sim.alpha(Object.keys(old).length ? 0.3 : 1).restart();
      if (focus && !byId[focus]) setFocus(null); else if (focus) setFocus(focus);
    }

    function resize() {
      const r = canvas.getBoundingClientRect(); W = r.width; H = r.height;
      canvas.width = Math.round(W * DPR); canvas.height = Math.round(H * DPR);
    }

    const vis = (n) => !hidden[n.group];
    function draw() {
      ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
      ctx.clearRect(0, 0, W, H);
      ctx.save();
      ctx.translate(W / 2 + transform.x, H / 2 + transform.y); ctx.scale(transform.k, transform.k);
      const k = transform.k;
      links.forEach((l) => {
        if (!vis(l.source) || !vis(l.target)) return;
        const on = focus && (l.source.id === focus || l.target.id === focus);
        ctx.strokeStyle = opts.linkColor ? opts.linkColor(l, on, !!focus) : (on ? 'rgba(162,139,255,.85)' : focus ? 'rgba(160,170,200,.05)' : 'rgba(160,170,200,.16)');
        ctx.lineWidth = ((on ? 1.6 : 0.8) * (l.w || 1)) / Math.sqrt(k);
        ctx.beginPath(); ctx.moveTo(l.source.x, l.source.y); ctx.lineTo(l.target.x, l.target.y); ctx.stroke();
      });
      nodes.forEach((n) => {
        if (!vis(n)) return;
        const dim = focus && n.id !== focus && !neighbors.has(n.id);
        ctx.globalAlpha = dim ? 0.15 : 1;
        const col = n.color();
        if (n.id === focus || n.glow) { ctx.shadowColor = col; ctx.shadowBlur = n.id === focus ? 24 : 10; }
        ctx.fillStyle = col; ctx.beginPath();
        if (n.shape === 'diamond') { const a = n.r * 1.15; ctx.moveTo(n.x, n.y - a); ctx.lineTo(n.x + a, n.y); ctx.lineTo(n.x, n.y + a); ctx.lineTo(n.x - a, n.y); ctx.closePath(); }
        else if (n.shape === 'hex') { for (let i = 0; i < 6; i++) { const a = Math.PI / 6 + i * Math.PI / 3; ctx[i ? 'lineTo' : 'moveTo'](n.x + Math.cos(a) * n.r, n.y + Math.sin(a) * n.r); } ctx.closePath(); }
        else ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2);
        ctx.fill(); ctx.shadowBlur = 0;
        if (n.ring) { ctx.strokeStyle = 'rgba(255,255,255,.85)'; ctx.lineWidth = 1.4; ctx.beginPath(); ctx.arc(n.x, n.y, n.r * 0.45, 0, Math.PI * 2); ctx.stroke(); }
        if (n.id === focus) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.5 / k; ctx.beginPath(); ctx.arc(n.x, n.y, n.r + 2, 0, Math.PI * 2); ctx.stroke(); }
        const showLabel = n.alwaysLabel || n.id === focus || (focus && neighbors.has(n.id)) || k > (n.labelAt || 1.2);
        if (showLabel && !dim) {
          const fs = (n.fs || 10.5) / Math.sqrt(k);
          ctx.font = `${n.bold ? 700 : 500} ${fs}px system-ui, sans-serif`; ctx.textAlign = 'center';
          ctx.lineWidth = 3 / k; ctx.strokeStyle = 'rgba(7,11,20,.8)';
          const label = n.label();
          ctx.strokeText(label, n.x, n.y + n.r + fs + 1);
          ctx.fillStyle = n.labelColor || 'rgba(225,230,245,.9)'; ctx.fillText(label, n.x, n.y + n.r + fs + 1);
        }
      });
      ctx.globalAlpha = 1; ctx.restore();
    }

    function find(x, y) {
      const p = transform.invert([x - W / 2, y - H / 2]);
      let best = null, bd = 18 / transform.k;
      nodes.forEach((n) => { if (!vis(n)) return; const d = Math.hypot(n.x - p[0], n.y - p[1]) - n.r; if (d < bd) { bd = d; best = n; } });
      return best;
    }

    function setFocus(id) {
      focus = id; neighbors = new Set();
      if (id) links.forEach((l) => { if (l.source.id === id) neighbors.add(l.target.id); if (l.target.id === id) neighbors.add(l.source.id); });
      if (opts.hint) opts.hint(id ? byId[id] : null, neighbors.size);
      draw();
    }

    const zoomer = d3.zoom().scaleExtent([0.25, 6]).on('zoom', (e) => { transform = e.transform; draw(); });
    const sel = d3.select(canvas);
    sel.call(d3.drag()
      .container(canvas)
      .subject((e) => find(e.x, e.y))
      .on('start', (e) => { if (!e.active) sim.alphaTarget(0.25).restart(); e.subject.fx = e.subject.x; e.subject.fy = e.subject.y; e.subject._moved = 0; })
      .on('drag', (e) => { e.subject.fx += e.dx / transform.k; e.subject.fy += e.dy / transform.k; e.subject._moved += Math.abs(e.dx) + Math.abs(e.dy); })
      .on('end', (e) => {
        if (!e.active) sim.alphaTarget(0);
        e.subject.fx = null; e.subject.fy = null;
        if (e.subject._moved < 6) { setFocus(e.subject.id); opts.onTap(e.subject); }
      }));
    sel.call(zoomer).on('dblclick.zoom', null);
    sel.call(zoomer.transform, d3.zoomIdentity.scale(opts.k0 || 0.8));
    canvas.addEventListener('click', (e) => {
      const r = canvas.getBoundingClientRect();
      if (!find(e.clientX - r.left, e.clientY - r.top) && focus) setFocus(null);
    });

    return {
      rebuild,
      activate() { active = true; resize(); if (!nodes.length) rebuild(); draw(); sim.alpha(Math.max(sim.alpha(), 0.05)).restart(); },
      deactivate() { active = false; },
      focusOn(id) {
        if (!byId[id]) return; setFocus(id);
        const n = byId[id]; const k = Math.max(transform.k, 1.3);
        setTimeout(() => sel.transition().duration(700).call(zoomer.transform, d3.zoomIdentity.translate(-n.x * k, -n.y * k).scale(k)), 60);
      },
      toggleGroup(g, on) { hidden[g] = !on; draw(); },
      redraw() { if (active) draw(); },
      resize
    };
  }

  const ES_COUNTRY = { 'Ukraine': 'Ucrania', 'Russia': 'Rusia', 'Israel': 'Israel', 'Palestine': 'Palestina', 'Lebanon': 'Líbano', 'Syria': 'Siria', 'Iran': 'Irán', 'Iraq': 'Irak', 'Yemen': 'Yemen', 'Eritrea': 'Eritrea', 'Djibouti': 'Yibuti', 'Taiwan': 'Taiwán', 'China': 'China', 'North Korea': 'Corea del Norte', 'South Korea': 'Corea del Sur', 'Sudan': 'Sudán', 'S. Sudan': 'Sudán del Sur', 'Chad': 'Chad', 'Dem. Rep. Congo': 'RD Congo', 'Rwanda': 'Ruanda', 'Uganda': 'Uganda', 'Burundi': 'Burundi', 'Mali': 'Malí', 'Burkina Faso': 'Burkina Faso', 'Niger': 'Níger', 'Myanmar': 'Myanmar', 'India': 'India', 'Pakistan': 'Pakistán', 'Venezuela': 'Venezuela', 'Colombia': 'Colombia', 'Guyana': 'Guyana' };
  const insColor = (id) => { const p = S.proj && S.proj.byId[id]; return !p || Math.abs(p.n) < 8 ? '#C9CFE0' : p.n > 0 ? '#2EE6A6' : '#FF5C7A'; };

  // Red de conexiones: conflictos ↔ países ↔ activos ↔ noticias ↔ desastres
  const ConnGraph = makeGraph({
    canvas: $('#graph'),
    k0: 0.8,
    hint: (n, c) => { $('#graphHint').textContent = n ? `${n.label()} · ${c} conexiones · tocá el fondo para soltar` : 'Tocá un nodo para ver sus conexiones · pellizcá para zoom'; },
    build() {
      const N = [], L = []; const countries = new Set(), assets = new Set();
      D.conflicts.forEach((c) => {
        N.push({ id: 'c:' + c.id, ref: c.id, group: 'conflict', r: 7 + (S.heat[c.id] || c.base) * 1.6, color: () => '#FF4D6D', label: () => c.short, alwaysLabel: true, bold: true, fs: 12, glow: true, ring: true, labelColor: '#ffd5dd', charge: -340 });
        c.countries.forEach((k) => { countries.add(k); L.push({ source: 'c:' + c.id, target: 'k:' + k, dist: 34, str: 0.7 }); });
        c.assets.forEach((s) => { assets.add(s); L.push({ source: 'c:' + c.id, target: 'a:' + s, dist: 90, str: 0.25 }); });
      });
      countries.forEach((k) => N.push({ id: 'k:' + k, ref: k, group: 'country', r: 4, color: () => '#9AA3BF', label: () => ES_COUNTRY[k] || k, labelAt: 1.4, charge: -130 }));
      S.disasters.filter((d) => d.sev >= 0.5).slice(0, 8).forEach((d) => {
        const T = M.disasterTypes[d.type];
        N.push({ id: 'd:' + d.id, ref: d.id, group: 'disaster', r: 5 + d.sev * 5, shape: 'hex', color: () => T.color, label: () => d.mag ? 'M' + d.mag.toFixed(1) + ' ' + (d.place || '').split(',').pop().trim() : T.name, alwaysLabel: true, glow: true, charge: -200 });
        Object.keys(d.shock || {}).forEach((f) => { const a = factorAsset[f]; if (a && assetById[a]) { assets.add(a); L.push({ source: 'd:' + d.id, target: 'a:' + a, dist: 80, str: 0.2 }); } });
      });
      assets.forEach((s) => N.push({ id: 'a:' + s, ref: s, group: 'asset', r: 6, shape: 'diamond', color: () => { const q = S.quotes[s]; const c = chgClass(q && q.chg); return c === 'up' ? '#2EE6A6' : c === 'down' ? '#FF5C7A' : '#C9CFE0'; }, label: () => { const q = S.quotes[s]; return assetById[s].name + (q && q.chg != null ? ' ' + fmtChg(q.chg) : ''); }, labelAt: 1.15, charge: -130 }));
      S.news.filter((n) => n.conf.length).slice(0, 45).forEach((n) => {
        N.push({ id: 'n:' + n.key, ref: n.key, group: 'news', r: 2.6, color: () => '#A28BFF', label: () => (n.title.length > 42 ? n.title.slice(0, 40) + '…' : n.title), labelAt: 2.2, fs: 9, charge: -35 });
        n.conf.forEach((c) => L.push({ source: 'n:' + n.key, target: 'c:' + c, dist: 26, str: 0.5 }));
        n.assets.filter((s) => assets.has(s)).slice(0, 2).forEach((s) => L.push({ source: 'n:' + n.key, target: 'a:' + s, dist: 60, str: 0.03 }));
      });
      return { nodes: N, links: L };
    },
    onTap(n) {
      if (n.group === 'conflict') showConflict(n.ref);
      else if (n.group === 'asset') showAsset(n.ref);
      else if (n.group === 'news') showNews(n.ref);
      else if (n.group === 'disaster') showDisaster(n.ref);
      else {
        const conf = D.conflicts.filter((c) => c.countries.includes(n.ref));
        openSheet(`<div class="kicker">País</div><h2>${esc(ES_COUNTRY[n.ref] || n.ref)}</h2><h5>Involucrado en</h5>${conf.map((c) => `<button class="mini-news" data-conf="${c.id}">${esc(c.name)}<small>${esc(c.summary)}</small></button>`).join('')}`);
      }
    }
  });

  // Proyección: eventos → factores → instrumentos IOL (verde a favor, rojo en contra)
  const ProjGraph = makeGraph({
    canvas: $('#projCanvas'),
    k0: 0.72,
    hint: (n, c) => { $('#projHint').textContent = n ? `${n.label()} · ${c} vínculos` : 'Eventos → factores → instrumentos IOL · tocá un nodo'; },
    linkColor: (l, on, any) => {
      const a = on ? 0.9 : any ? 0.05 : 0.32;
      if (l.kind === 'ev') return `rgba(162,139,255,${a})`;
      return l.sign > 0 ? `rgba(46,230,166,${a})` : `rgba(255,92,122,${a})`;
    },
    build(h) {
      if (!S.proj) computeProjection();
      const P = S.proj; const N = [], L = [];
      const gap = Math.max(170, h * 0.36);
      const evs = P.ev.filter((e) => e.w > 0.08).sort((a, b) => b.w - a.w).slice(0, 14);
      evs.forEach((e) => {
        const dis = e.kind === 'disaster' ? S.disasters.find((d) => d.id === e.ref) : null;
        const col = dis ? M.disasterTypes[dis.type].color : '#FF4D6D';
        N.push({ id: e.id, ref: e.ref, kind: e.kind, group: 'event', r: 5 + e.w * 9, shape: dis ? 'hex' : null, ring: !dis, glow: true, color: () => col, label: () => e.short, alwaysLabel: true, bold: true, fs: 11, labelColor: dis ? col : '#ffd5dd', fy0: -gap, charge: -220 });
      });
      const facs = Object.entries(P.P).filter(([, v]) => Math.abs(v) > 0.05).sort((a, b) => b[1] - a[1]);
      facs.forEach(([f, v]) => {
        N.push({ id: 'f:' + f, ref: f, group: 'factor', r: 5 + Math.min(10, Math.abs(v) * 5), color: () => '#A28BFF', label: () => M.factors[f].name + (v >= 0 ? ' ↑' : ' ↓'), alwaysLabel: true, bold: true, fs: 11, labelColor: '#d9ceff', fy0: 0, charge: -260, glow: true });
        (P.why[f] || []).forEach((w) => { if (evs.includes(w.ev) && w.x > 0.03) L.push({ source: w.ev.id, target: 'f:' + f, dist: gap * 0.9, str: 0.12, kind: 'ev', w: 0.6 + w.x }); });
      });
      const top = P.favor.slice(0, 11).concat(P.contra.slice(0, 11));
      top.forEach((x) => {
        const id = x.ins.id;
        N.push({ id: 'i:' + id, ref: id, group: 'ins', r: 4 + Math.abs(x.n) / 12, shape: 'diamond', color: () => insColor(id), label: () => `${id} ${x.n > 0 ? '+' : ''}${x.n}`, alwaysLabel: Math.abs(x.n) >= 30, labelAt: 1.05, fs: 10, fy0: gap, charge: -150 });
        x.parts.forEach((pt) => { if (Math.abs(pt.x) > 0.04) L.push({ source: 'f:' + pt.f, target: 'i:' + id, dist: gap * 0.9, str: 0.1, sign: pt.x, w: 0.5 + Math.min(1.5, Math.abs(pt.x)) }); });
      });
      return { nodes: N, links: L };
    },
    onTap(n) {
      if (n.group === 'ins') showInstrument(n.ref);
      else if (n.group === 'event') { if (n.kind === 'conflict') showConflict(n.ref); else showDisaster(n.ref); }
      else if (n.group === 'factor') {
        const f = n.ref; const v = S.proj.P[f] || 0;
        const ins = S.proj.scores.filter((s) => s.ins.sens[f]).map((s) => ({ id: s.ins.id, x: v * s.ins.sens[f] })).sort((a, b) => b.x - a.x);
        openSheet(`<div class="kicker">Factor</div><h2>${esc(M.factors[f].name)} ${v >= 0 ? '↑' : '↓'}</h2><p>${esc(M.factors[f].up)}. Presión ${nf(2).format(v)}.</p>
          <h5>Lo empujan</h5>${(S.proj.why[f] || []).slice(0, 8).map((w) => `<div class="why up"><b>${esc(w.ev.short)}</b> <small>${esc(w.ev.name)} · aporte ${nf(2).format(w.x)}</small></div>`).join('')}
          <div class="two"><div><h5 class="up-t">Beneficia</h5>${ins.filter((x) => x.x > 0).slice(0, 8).map((x) => iolRow(x.id)).join('') || '<p class="muted">—</p>'}</div>
          <div><h5 class="down-t">Perjudica</h5>${ins.filter((x) => x.x < 0).reverse().slice(0, 8).map((x) => iolRow(x.id)).join('') || '<p class="muted">—</p>'}</div></div>`);
      }
    }
  });

  /* ================= navegación ================= */
  function setProjMode(m) {
    S.projMode = m;
    $$('#projModeSeg button').forEach((b) => b.classList.toggle('on', b.dataset.pm === m));
    $('#projWrap').classList.toggle('show', m === 'proj');
    $('#connWrap').classList.toggle('show', m === 'conn');
    $('#view-proj').classList.toggle('mode-conn', m === 'conn');
    if (S.view === 'proj') {
      if (m === 'proj') { ConnGraph.deactivate(); ProjGraph.activate(); } else { ProjGraph.deactivate(); ConnGraph.activate(); }
    }
  }

  function go(view) {
    S.view = view;
    $$('.tab').forEach((t) => t.classList.toggle('on', t.dataset.view === view));
    $$('.view').forEach((v) => v.classList.toggle('active', v.id === 'view-' + view));
    if (view === 'globe') Globe.start(); else Globe.stop();
    if (view === 'proj') setProjMode(S.projMode); else { ConnGraph.deactivate(); ProjGraph.deactivate(); }
    if (view === 'news') { S.unseen = 0; renderNews(); }
    if (view === 'markets') renderMarkets();
    if (view === 'live') renderLive();
    renderHeader();
  }

  function renderAll() {
    renderHeader(); renderHotStrip();
    if (S.view === 'news') renderNews();
    if (S.view === 'markets') renderMarkets();
    if (S.view === 'live') renderLive();
    ConnGraph.redraw(); ProjGraph.redraw();
  }

  $$('.tab').forEach((t) => t.addEventListener('click', () => go(t.dataset.view)));
  $('#backdrop').addEventListener('click', closeSheet);
  $('#projModeSeg').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) setProjMode(b.dataset.pm); });
  $('#horizonSeg').addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    S.horizon = b.dataset.h; $$('#horizonSeg button').forEach((x) => x.classList.toggle('on', x === b));
    computeProjection(); ProjGraph.rebuild(); renderAll();
  });
  $('#rankBtn').addEventListener('click', showRanking);
  $('#mktSeg').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) { S.mktTab = b.dataset.mkt; renderMarkets(); } });
  $('#connFilters').addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    b.classList.toggle('on'); ConnGraph.toggleGroup(b.dataset.g, b.classList.contains('on'));
  });
  $('#plToggle').addEventListener('click', () => { if (player.audio.paused) player.audio.play().catch(() => {}); else player.audio.pause(); });
  $('#plClose').addEventListener('click', stopRadio);

  // Delegación de clicks
  document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-ins],[data-clip],[data-radio],[data-dis],[data-conf],[data-asset],[data-news],[data-nf],[data-open],[data-share],[data-go]');
    if (!el) return;
    const d = el.dataset;
    if (d.go) {
      closeSheet(); go(d.go);
      if (d.go === 'proj') {
        const conn = d.mode === 'conn' || (d.focus && /^[an]:/.test(d.focus));
        setProjMode(conn ? 'conn' : 'proj');
        if (d.focus) setTimeout(() => (conn ? ConnGraph : ProjGraph).focusOn(d.focus), 150);
      }
      if (d.confFly) Globe.flyTo(d.confFly);
      return;
    }
    e.stopPropagation();
    if (d.ins) { showInstrument(d.ins); return; }
    if (d.clip) { openUrl('https://www.youtube.com/watch?v=' + d.clip); return; }
    if (d.radio != null) { playRadio(+d.radio); return; }
    if (d.dis) { if (el.classList.contains('hot')) Globe.flyToDisaster(d.dis); showDisaster(d.dis); Globe.select(d.dis); return; }
    if (d.nf) { S.newsFilter = d.nf; renderNews(); return; }
    if (d.open) { openUrl(d.open); return; }
    if (d.share) {
      const n = S.news.find((x) => x.key === d.share);
      if (n) { const txt = `${n.title}\n${n.link}\n— vía Pulso Global`; if (NATIVE && window.Native.share) window.Native.share(txt); else if (navigator.share) navigator.share({ text: txt }).catch(() => {}); }
      return;
    }
    if (d.conf) { if (el.classList.contains('hot')) Globe.flyTo(d.conf); showConflict(d.conf); Globe.select(d.conf); return; }
    if (d.asset) { showAsset(d.asset); return; }
    if (d.news) { showNews(d.news); if (S.view === 'news') renderNews(); }
  }, true);

  // deslizar hacia abajo para cerrar la hoja
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
  window.addEventListener('resize', () => { Globe.resize(); ConnGraph.resize(); ProjGraph.resize(); renderAll(); });

  /* ================= ciclo de actualización ================= */
  const last = {};
  function every(key, ms, fn, force) {
    const now = Date.now();
    if (force || !last[key] || now - last[key] > ms) { last[key] = now; fn().catch(() => {}); }
  }
  function tick(force) {
    every('quotes', 75e3, refreshQuotes, force);
    every('arg', 90e3, refreshArg, force);
    every('news', 180e3, refreshNews, force);
    every('dis', 300e3, refreshDisasters, force);
    every('clips', 600e3, refreshClips, force);
    every('radios', 6 * 3600e3, refreshRadios, force && !S.radios.length);
    renderHeader();
  }

  // arranque: primero lo guardado, después en vivo
  const cq = load('quotes'); if (cq) S.quotes = cq;
  const ca = load('arg'); if (ca) S.arg = ca;
  const cl = load('lecaps'); if (cl) S.lecaps = cl;
  const cd = load('disasters'); if (cd) S.disasters = cd;
  const cn = load('news'); if (cn) { S.news = cn.map(analyze); S.news.forEach((n) => S.seenNews.add(n.key)); }
  const cc = load('clips'); if (cc) S.clips = cc.map(analyze);
  computeHeat(); computeProjection(); computeRisk();
  ConnGraph.rebuild(); ProjGraph.rebuild();
  go('globe'); renderAll();
  tick(true);
  setInterval(() => tick(false), 15e3);
})();
