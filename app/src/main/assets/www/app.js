'use strict';

// ---------- Fuentes de datos ----------
var URL_WEEKLY = 'https://www.cpc.ncep.noaa.gov/data/indices/wksst9120.for';
var URL_ONI = 'https://www.cpc.ncep.noaa.gov/data/indices/oni.ascii.txt';
var GIBS = 'https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/';
var LAYERS = {
  anom: {
    id: 'GHRSST_L4_MUR_Sea_Surface_Temperature_Anomalies',
    legend: 'linear-gradient(90deg,#08306b,#2171b5,#6baed6,#f7f7f7,#fc9272,#de2d26,#67000d)',
    labels: ['Más frío', 'Normal', 'Más cálido']
  },
  sst: {
    id: 'GHRSST_L4_MUR_Sea_Surface_Temperature',
    legend: 'linear-gradient(90deg,#2c0a5e,#2166ac,#4dac26,#fee08b,#f46d43,#a50026)',
    labels: ['Frío', '', 'Cálido']
  }
};

// Regiones Niño (longitudes en 0–360 para no cortar en el antimeridiano)
var REGIONS = [
  { key: 'n12', name: 'Niño 1+2', bounds: [[-10, 270], [0, 280]], desc: '0–10°S, 90–80°O' },
  { key: 'n3', name: 'Niño 3', bounds: [[-5, 210], [5, 270]], desc: '5°N–5°S, 150–90°O' },
  { key: 'n34', name: 'Niño 3.4', bounds: [[-5, 190], [5, 240]], desc: '5°N–5°S, 170–120°O' },
  { key: 'n4', name: 'Niño 4', bounds: [[-5, 160], [5, 210]], desc: '5°N–5°S, 160°E–150°O' }
];

// ---------- Puente nativo (evita CORS dentro del APK) ----------
var pending = {};
var seq = 0;
window.__bridgeResolve = function (id, err, body) {
  var p = pending[id];
  if (!p) return;
  delete pending[id];
  if (err) p.reject(new Error(err)); else p.resolve(body);
};
function fetchText(url) {
  if (window.Android && window.Android.fetchText) {
    return new Promise(function (resolve, reject) {
      var id = 'r' + (++seq);
      pending[id] = { resolve: resolve, reject: reject };
      window.Android.fetchText(url, id);
    });
  }
  return fetch(url, { cache: 'no-store' }).then(function (r) {
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.text();
  });
}

function store(key, val) { try { localStorage.setItem(key, JSON.stringify(val)); } catch (e) {} }
function load(key) { try { return JSON.parse(localStorage.getItem(key)); } catch (e) { return null; } }

// ---------- Parsers ----------
var MONTHS = { JAN: 0, FEB: 1, MAR: 2, APR: 3, MAY: 4, JUN: 5, JUL: 6, AUG: 7, SEP: 8, OCT: 9, NOV: 10, DEC: 11 };

function parseWeekly(text) {
  var out = [];
  text.split('\n').forEach(function (line) {
    var m = line.match(/^\s*(\d{2})([A-Z]{3})(\d{4})(.*)$/);
    if (!m || !(m[2] in MONTHS)) return;
    var nums = (m[4].match(/-?\d+\.\d+/g) || []).map(Number);
    if (nums.length < 8) return;
    out.push({
      date: Date.UTC(+m[3], MONTHS[m[2]], +m[1]),
      n12: { sst: nums[0], a: nums[1] },
      n3: { sst: nums[2], a: nums[3] },
      n34: { sst: nums[4], a: nums[5] },
      n4: { sst: nums[6], a: nums[7] }
    });
  });
  return out;
}

function parseOni(text) {
  var out = [];
  text.split('\n').forEach(function (line) {
    var m = line.match(/^\s*([A-Z]{3})\s+(\d{4})\s+(-?\d+\.\d+)\s+(-?\d+\.\d+)/);
    if (m) out.push({ seas: m[1], year: +m[2], total: +m[3], anom: +m[4] });
  });
  return out;
}

// ---------- Utilidades ----------
function fmtA(v) { return (v > 0 ? '+' : v < 0 ? '−' : '') + Math.abs(v).toFixed(1) + '°C'; }
function fmtDate(ms) {
  return new Date(ms).toLocaleDateString('es', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
}
function isoDate(d) { return d.toISOString().slice(0, 10); }
function anomColor(a) {
  var t = Math.max(-2.5, Math.min(2.5, a)) / 2.5;
  if (t >= 0) return 'rgba(232,70,46,' + (0.25 + 0.7 * t).toFixed(2) + ')';
  return 'rgba(42,134,200,' + (0.25 + 0.7 * -t).toFixed(2) + ')';
}
function phase(a) {
  if (a >= 0.5) {
    var s = a >= 2 ? 'muy fuerte' : a >= 1.5 ? 'fuerte' : a >= 1 ? 'moderado' : 'débil';
    return { label: 'El Niño', strength: s, cls: 'warm' };
  }
  if (a <= -0.5) {
    var s2 = a <= -2 ? 'muy fuerte' : a <= -1.5 ? 'fuerte' : a <= -1 ? 'moderada' : 'débil';
    return { label: 'La Niña', strength: s2, cls: 'cool' };
  }
  return { label: 'Neutral', strength: '', cls: 'neutral' };
}

// ---------- Mapa ----------
var map = L.map('map', {
  center: [-10, 230], zoom: 2, minZoom: 2, maxZoom: 10,
  worldCopyJump: true, zoomControl: false, attributionControl: true,
  maxBounds: [[-85, -720], [85, 1080]], maxBoundsViscosity: 1
});
L.control.zoom({ position: 'bottomleft' }).addTo(map);

// Mapa base sin clave de API (Esri)
var ESRI = 'https://server.arcgisonline.com/ArcGIS/rest/services/';
L.tileLayer(ESRI + 'Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
  maxNativeZoom: 16, maxZoom: 19,
  attribution: 'Mapa &copy; Esri'
}).addTo(map);

// Fronteras, países y ciudades por encima de la capa de temperatura
map.createPane('labels');
map.getPane('labels').style.zIndex = 450;
map.getPane('labels').style.pointerEvents = 'none';
L.tileLayer(ESRI + 'Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}', {
  maxNativeZoom: 16, maxZoom: 19, pane: 'labels'
}).addTo(map);

// ---------- Mi ubicación ----------
var LocateControl = L.Control.extend({
  options: { position: 'bottomleft' },
  onAdd: function () {
    var btn = L.DomUtil.create('a', 'leaflet-bar locate-btn');
    btn.href = '#';
    btn.title = 'Mi ubicación';
    btn.innerHTML = '📍';
    L.DomEvent.on(btn, 'click', function (e) { L.DomEvent.preventDefault(e); L.DomEvent.stopPropagation(e); locate(); });
    return btn;
  }
});
map.addControl(new LocateControl());

var myMarker = null;
function showMyLocation(lat, lon) {
  var ll = [lat, lon];
  if (myMarker) map.removeLayer(myMarker);
  myMarker = L.circleMarker(ll, { radius: 8, color: '#fff', weight: 3, fillColor: '#ffd166', fillOpacity: 1 })
    .addTo(map)
    .bindPopup('<b>Tu ubicación</b><br>' + lat.toFixed(3) + ', ' + lon.toFixed(3))
    .openPopup();
  map.setView(ll, 5);
  store('myLoc', ll);
}
window.__onLocation = function (lat, lon, err) {
  if (err) { alert('No se pudo obtener tu ubicación: ' + err); return; }
  showMyLocation(lat, lon);
};
function locate() {
  if (window.Android && window.Android.requestLocation) {
    window.Android.requestLocation();
  } else if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      function (p) { showMyLocation(p.coords.latitude, p.coords.longitude); },
      function (e) { window.__onLocation(0, 0, e.message); });
  }
}

var layerKey = 'anom';
var opacity = 0.8;
var currentDate = new Date();
currentDate.setUTCHours(0, 0, 0, 0);
currentDate.setUTCDate(currentDate.getUTCDate() - 1);
var latestAvailable = new Date(currentDate);
var fallbackSteps = 0;
var tileOk = false, tileErr = 0;
var playing = null;

function gibsUrl() {
  return GIBS + LAYERS[layerKey].id + '/default/' + isoDate(currentDate) +
    '/GoogleMapsCompatible_Level7/{z}/{y}/{x}.png';
}

var sstLayer = L.tileLayer(gibsUrl(), {
  maxNativeZoom: 7, maxZoom: 8, opacity: opacity,
  attribution: 'SST: NASA GIBS / JPL MUR · Índices: NOAA CPC'
}).addTo(map);

sstLayer.on('loading', function () { tileOk = false; tileErr = 0; });
sstLayer.on('tileload', function () { tileOk = true; });
sstLayer.on('tileerror', function () { tileErr++; });
sstLayer.on('load', function () {
  // Si el día aún no está publicado, retrocede automáticamente.
  if (!tileOk && tileErr > 0 && !playing && fallbackSteps < 7) {
    fallbackSteps++;
    currentDate.setUTCDate(currentDate.getUTCDate() - 1);
    latestAvailable = new Date(currentDate);
    refreshLayer();
  }
});

function refreshLayer() {
  sstLayer.setUrl(gibsUrl());
  document.getElementById('dateLabel').textContent = fmtDate(currentDate.getTime());
  document.getElementById('nextDay').disabled = currentDate >= latestAvailable;
}

function renderLegend() {
  var l = LAYERS[layerKey];
  document.getElementById('legend').innerHTML =
    '<div class="bar" style="background:' + l.legend + '"></div>' +
    '<div class="labels"><span>' + l.labels[0] + '</span><span>' + l.labels[1] +
    '</span><span>' + l.labels[2] + '</span></div>';
}

var rects = {};
REGIONS.forEach(function (r) {
  rects[r.key] = L.rectangle(r.bounds, { color: '#ffffff', weight: 1.5, dashArray: '4 3', fillOpacity: 0.05 })
    .addTo(map)
    .bindTooltip(r.name, { permanent: true, direction: 'center', className: 'rlabel', opacity: 0.85 })
    .on('click', function () { showRegion(r.key); });
});

// ---------- Datos ENSO ----------
var weekly = load('weekly') || [];
var oni = load('oni') || [];

function showRegion(key) {
  var r = REGIONS.filter(function (x) { return x.key === key; })[0];
  var last = weekly[weekly.length - 1];
  var html = '<b>' + r.name + '</b><br><small>' + r.desc + '</small>';
  if (last) {
    html += '<br>Anomalía: <b>' + fmtA(last[key].a) + '</b><br>Temperatura: ' + last[key].sst.toFixed(1) +
      '°C<br><small>Semana del ' + fmtDate(last.date) + '</small>';
  }
  var b = L.latLngBounds(r.bounds);
  L.popup().setLatLng(b.getCenter()).setContent(html).openOn(map);
}

function renderAll() {
  var last = weekly[weekly.length - 1];
  var lastOni = oni[oni.length - 1];
  var statusEl = document.getElementById('status');
  var updated = document.getElementById('updated');

  if (last) {
    // Condiciones actuales según la anomalía semanal de Niño 3.4 (el ONI llega con ~2 meses de retraso).
    var p = phase(last.n34.a);
    statusEl.className = 'chip ' + p.cls;
    statusEl.textContent = p.label + (p.strength ? ' ' + p.strength : '') + ' · ' + fmtA(last.n34.a);
    updated.textContent = 'Niño 3.4 semana del ' + fmtDate(last.date) +
      (lastOni ? ' · ONI ' + lastOni.seas + ' ' + lastOni.year + ': ' + fmtA(lastOni.anom) : '');

    var html = '';
    REGIONS.forEach(function (r) {
      var v = last[r.key];
      html += '<div class="region" data-key="' + r.key + '" style="background:' + anomColor(v.a) + '">' +
        '<div class="name">' + r.name + '</div><div class="val">' + fmtA(v.a) + '</div>' +
        '<div class="sst">' + v.sst.toFixed(1) + '°C</div></div>';
      rects[r.key].setStyle({ fillColor: v.a >= 0 ? '#e8462e' : '#2a86c8', fillOpacity: Math.min(0.45, Math.abs(v.a) / 5) });
    });
    document.getElementById('regions').innerHTML = html;
    Array.prototype.forEach.call(document.querySelectorAll('.region'), function (el) {
      el.onclick = function () { collapse(true); showRegion(el.getAttribute('data-key')); };
    });

    var prev = weekly[weekly.length - 2];
    var trend = prev ? last.n34.a - prev.n34.a : 0;
    document.getElementById('weekNote').textContent =
      'Niño 3.4: ' + (trend > 0.05 ? 'subiendo' : trend < -0.05 ? 'bajando' : 'estable') +
      ' respecto a la semana anterior (' + fmtA(trend) + '). Umbral El Niño: +0,5°C · La Niña: −0,5°C.';
  }
  drawWeekly();
  drawOni();
}

// ---------- Gráficos SVG ----------
function svgChart(el, values, labels, opts) {
  var W = 340, H = 150, pl = 28, pr = 6, pt = 8, pb = 18;
  if (!values.length) { el.innerHTML = '<p class="note">Sin datos.</p>'; return; }
  var max = Math.max(1, Math.ceil(Math.max.apply(null, values) * 2) / 2);
  var min = Math.min(-1, Math.floor(Math.min.apply(null, values) * 2) / 2);
  var y = function (v) { return pt + (max - v) / (max - min) * (H - pt - pb); };
  var x = function (i) { return pl + i * (W - pl - pr) / Math.max(1, values.length - 1); };
  var s = '<svg viewBox="0 0 ' + W + ' ' + H + '" preserveAspectRatio="none">';
  [max, 0.5, 0, -0.5, min].forEach(function (v) {
    var dash = (v === 0.5 || v === -0.5) ? ' stroke-dasharray="3 3"' : '';
    var col = v === 0.5 ? '#e8462e' : v === -0.5 ? '#2a86c8' : 'rgba(255,255,255,0.15)';
    s += '<line x1="' + pl + '" x2="' + (W - pr) + '" y1="' + y(v) + '" y2="' + y(v) + '" stroke="' + col + '"' + dash + '/>';
    s += '<text x="2" y="' + (y(v) + 3) + '">' + (v > 0 ? '+' : '') + v.toFixed(1) + '</text>';
  });
  if (opts.bars) {
    var bw = (W - pl - pr) / values.length;
    values.forEach(function (v, i) {
      var top = Math.min(y(v), y(0)), h = Math.abs(y(v) - y(0));
      s += '<rect x="' + (pl + i * bw) + '" y="' + top + '" width="' + Math.max(1, bw - 0.6) + '" height="' + h +
        '" fill="' + (v >= 0.5 ? '#e8462e' : v <= -0.5 ? '#2a86c8' : '#6b7d90') + '"/>';
    });
  } else {
    var pts = values.map(function (v, i) { return x(i) + ',' + y(v); }).join(' ');
    s += '<polyline points="' + pts + '" fill="none" stroke="#ffd166" stroke-width="2"/>';
    var li = values.length - 1;
    s += '<circle cx="' + x(li) + '" cy="' + y(values[li]) + '" r="3.5" fill="#ffd166"/>';
  }
  var step = Math.ceil(labels.length / 5);
  labels.forEach(function (lab, i) {
    if (lab && i % step === 0) {
      var xx = opts.bars ? pl + (i + 0.5) * (W - pl - pr) / labels.length : x(i);
      s += '<text x="' + xx + '" y="' + (H - 4) + '" text-anchor="middle">' + lab + '</text>';
    }
  });
  el.innerHTML = s + '</svg>';
}

function drawWeekly() {
  var w = weekly.slice(-52);
  svgChart(document.getElementById('weeklyChart'),
    w.map(function (d) { return d.n34.a; }),
    w.map(function (d) { return new Date(d.date).toLocaleDateString('es', { month: 'short', year: '2-digit', timeZone: 'UTC' }); }),
    {});
}

function drawOni() {
  var o = oni.slice(-120);
  svgChart(document.getElementById('oniChart'),
    o.map(function (d) { return d.anom; }),
    o.map(function (d) { return d.seas === 'DJF' ? String(d.year) : ''; }),
    { bars: true });
}

function loadData() {
  document.getElementById('updated').textContent = 'Actualizando…';
  var a = fetchText(URL_WEEKLY).then(function (t) {
    var w = parseWeekly(t);
    if (w.length) { weekly = w; store('weekly', w); }
  });
  var b = fetchText(URL_ONI).then(function (t) {
    var o = parseOni(t);
    if (o.length) { oni = o; store('oni', o); }
  });
  Promise.all([a.catch(function (e) { return e; }), b.catch(function (e) { return e; })]).then(function (res) {
    renderAll();
    var errs = res.filter(function (r) { return r instanceof Error; });
    if (errs.length) {
      document.getElementById('updated').textContent = weekly.length
        ? 'Sin conexión: mostrando últimos datos guardados'
        : 'No se pudieron descargar los índices (' + errs[0].message + ')';
    }
  });
}

// ---------- UI ----------
var sheet = document.getElementById('sheet');
function collapse(c) { sheet.classList.toggle('collapsed', c); }
document.getElementById('handle').onclick = function () { collapse(!sheet.classList.contains('collapsed')); };
document.getElementById('status').onclick = function () { collapse(false); selectTab('idx'); };
window.handleBack = function () {
  if (!sheet.classList.contains('collapsed')) { collapse(true); return true; }
  return false;
};

function selectTab(name) {
  Array.prototype.forEach.call(document.querySelectorAll('#tabs button'), function (b) {
    b.classList.toggle('active', b.getAttribute('data-tab') === name);
  });
  Array.prototype.forEach.call(document.querySelectorAll('.tab'), function (t) {
    t.classList.toggle('active', t.id === 'tab-' + name);
  });
}
Array.prototype.forEach.call(document.querySelectorAll('#tabs button'), function (b) {
  b.onclick = function () { collapse(false); selectTab(b.getAttribute('data-tab')); };
});

function stopPlay() {
  if (!playing) return;
  clearInterval(playing);
  playing = null;
  document.getElementById('playBtn').classList.remove('on');
  document.getElementById('playBtn').textContent = '▶';
}

document.getElementById('prevDay').onclick = function () {
  stopPlay();
  currentDate.setUTCDate(currentDate.getUTCDate() - 1);
  refreshLayer();
};
document.getElementById('nextDay').onclick = function () {
  stopPlay();
  if (currentDate < latestAvailable) {
    currentDate.setUTCDate(currentDate.getUTCDate() + 1);
    refreshLayer();
  }
};
document.getElementById('layerSel').onchange = function (e) {
  layerKey = e.target.value;
  renderLegend();
  refreshLayer();
};
document.getElementById('opacity').oninput = function (e) {
  opacity = +e.target.value;
  sstLayer.setOpacity(opacity);
};
document.getElementById('playBtn').onclick = function () {
  if (playing) { stopPlay(); return; }
  var btn = document.getElementById('playBtn');
  btn.classList.add('on');
  btn.textContent = '■';
  currentDate = new Date(latestAvailable);
  currentDate.setUTCDate(currentDate.getUTCDate() - 29);
  refreshLayer();
  playing = setInterval(function () {
    if (currentDate >= latestAvailable) {
      currentDate = new Date(latestAvailable);
      currentDate.setUTCDate(currentDate.getUTCDate() - 29);
    } else {
      currentDate.setUTCDate(currentDate.getUTCDate() + 1);
    }
    refreshLayer();
  }, 1200);
};
document.getElementById('refreshBtn').onclick = function () {
  stopPlay();
  fallbackSteps = 0;
  currentDate = new Date();
  currentDate.setUTCHours(0, 0, 0, 0);
  currentDate.setUTCDate(currentDate.getUTCDate() - 1);
  latestAvailable = new Date(currentDate);
  refreshLayer();
  loadData();
};

var savedLoc = load('myLoc');
if (savedLoc) {
  myMarker = L.circleMarker(savedLoc, { radius: 8, color: '#fff', weight: 3, fillColor: '#ffd166', fillOpacity: 1 })
    .addTo(map).bindPopup('<b>Tu ubicación</b>');
}

renderLegend();
refreshLayer();
renderAll();
loadData();
// Refresca los índices cada 6 horas si la app queda abierta.
setInterval(loadData, 6 * 3600 * 1000);
