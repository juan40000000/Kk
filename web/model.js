/* Modelo de impacto: eventos (guerras y desastres) -> factores de mercado -> instrumentos operables en IOL.
   Los pesos son heurísticos (dirección y magnitud relativa), no una predicción de precios. */
window.PG_MODEL = (function () {
  // + significa que el factor "sube" (más precio, más riesgo o más disrupción, según el caso)
  const factors = {
    oil: { name: 'Petróleo', up: 'sube el crudo' },
    gas: { name: 'Gas natural', up: 'sube el gas' },
    gold: { name: 'Oro', up: 'demanda de refugio' },
    agri: { name: 'Granos / agro', up: 'suben granos y fertilizantes' },
    metals: { name: 'Metales', up: 'suben cobre y metales' },
    uranium: { name: 'Uranio', up: 'oferta de uranio en riesgo' },
    defense: { name: 'Gasto en defensa', up: 'más gasto militar' },
    shipping: { name: 'Fletes marítimos', up: 'rutas cortadas, fletes más caros' },
    chips: { name: 'Cadena de chips', up: 'riesgo de disrupción en semiconductores' },
    aviation: { name: 'Aviación', up: 'espacio aéreo y vuelos afectados' },
    insurance: { name: 'Siniestros', up: 'pérdidas para aseguradoras' },
    rebuild: { name: 'Reconstrucción', up: 'demanda de maquinaria y acero' },
    riskoff: { name: 'Aversión al riesgo', up: 'fuga hacia activos seguros' },
    argrisk: { name: 'Riesgo Argentina', up: 'sube el riesgo país' }
  };

  // shock de cada conflicto sobre los factores (0..1)
  const conflictShock = {
    ukr: { agri: 0.7, gas: 0.6, defense: 0.8, riskoff: 0.3, metals: 0.2 },
    mideast: { oil: 0.5, gold: 0.5, defense: 0.7, riskoff: 0.4, aviation: 0.2 },
    iran: { oil: 1, gas: 0.5, gold: 0.6, riskoff: 0.6, shipping: 0.5, defense: 0.5, aviation: 0.3 },
    redsea: { shipping: 1, oil: 0.5, riskoff: 0.1 },
    taiwan: { chips: 1, riskoff: 0.7, defense: 0.6 },
    korea: { chips: 0.3, riskoff: 0.3, defense: 0.4 },
    sudan: { gold: 0.2, agri: 0.1 },
    drc: { metals: 0.6 },
    sahel: { uranium: 0.7, gold: 0.3 },
    myanmar: { metals: 0.3 },
    indpak: { riskoff: 0.3, defense: 0.3 },
    venezuela: { oil: 0.3, riskoff: 0.1 }
  };

  // Instrumentos verificados en IOL (mercado BCBA). sens: sensibilidad a cada factor (-1..1)
  const instruments = [
    // Acciones argentinas
    { id: 'YPFD', name: 'YPF', type: 'Acciones', src: 'arg_stocks', sens: { oil: 0.8, gas: 0.3, argrisk: -0.6, riskoff: -0.3 } },
    { id: 'PAMP', name: 'Pampa Energía', type: 'Acciones', src: 'arg_stocks', sens: { gas: 0.6, oil: 0.4, argrisk: -0.6, riskoff: -0.2 } },
    { id: 'TGSU2', name: 'Transp. Gas del Sur', type: 'Acciones', src: 'arg_stocks', sens: { gas: 0.7, argrisk: -0.5 } },
    { id: 'CEPU', name: 'Central Puerto', type: 'Acciones', src: 'arg_stocks', sens: { gas: 0.2, argrisk: -0.6 } },
    { id: 'GGAL', name: 'Grupo Galicia', type: 'Acciones', src: 'arg_stocks', sens: { argrisk: -1, riskoff: -0.5 } },
    { id: 'BMA', name: 'Banco Macro', type: 'Acciones', src: 'arg_stocks', sens: { argrisk: -0.9, riskoff: -0.4 } },
    { id: 'TXAR', name: 'Ternium Argentina', type: 'Acciones', src: 'arg_stocks', sens: { rebuild: 0.5, metals: 0.3, argrisk: -0.5 } },
    { id: 'ALUA', name: 'Aluar', type: 'Acciones', src: 'arg_stocks', sens: { metals: 0.7, gas: -0.2, argrisk: -0.4 } },
    { id: 'CRES', name: 'Cresud', type: 'Acciones', src: 'arg_stocks', sens: { agri: 0.8, argrisk: -0.5 } },
    { id: 'LOMA', name: 'Loma Negra', type: 'Acciones', src: 'arg_stocks', sens: { rebuild: 0.4, argrisk: -0.5 } },
    // CEDEARs de empresas
    { id: 'VIST', name: 'Vista Energy', type: 'CEDEARs', src: 'arg_cedears', sens: { oil: 0.9, argrisk: -0.4 } },
    { id: 'XOM', name: 'ExxonMobil', type: 'CEDEARs', src: 'arg_cedears', sens: { oil: 0.8, gas: 0.3 } },
    { id: 'CVX', name: 'Chevron', type: 'CEDEARs', src: 'arg_cedears', sens: { oil: 0.8 } },
    { id: 'PBR', name: 'Petrobras', type: 'CEDEARs', src: 'arg_cedears', sens: { oil: 0.7, riskoff: -0.2 } },
    { id: 'LMT', name: 'Lockheed Martin', type: 'CEDEARs', src: 'arg_cedears', sens: { defense: 1 } },
    { id: 'RTX', name: 'RTX (Raytheon)', type: 'CEDEARs', src: 'arg_cedears', sens: { defense: 0.8, aviation: -0.3 } },
    { id: 'B', name: 'Barrick Mining', type: 'CEDEARs', src: 'arg_cedears', sens: { gold: 0.9 } },
    { id: 'NEM', name: 'Newmont', type: 'CEDEARs', src: 'arg_cedears', sens: { gold: 1 } },
    { id: 'FCX', name: 'Freeport-McMoRan', type: 'CEDEARs', src: 'arg_cedears', sens: { metals: 1, riskoff: -0.3 } },
    { id: 'VALE', name: 'Vale', type: 'CEDEARs', src: 'arg_cedears', sens: { metals: 0.7, riskoff: -0.3 } },
    { id: 'CCJ', name: 'Cameco', type: 'CEDEARs', src: 'arg_cedears', sens: { uranium: 1 } },
    { id: 'MOS', name: 'Mosaic', type: 'CEDEARs', src: 'arg_cedears', sens: { agri: 0.7, gas: -0.2 } },
    { id: 'DE', name: 'Deere', type: 'CEDEARs', src: 'arg_cedears', sens: { agri: 0.5, rebuild: 0.2 } },
    { id: 'CAT', name: 'Caterpillar', type: 'CEDEARs', src: 'arg_cedears', sens: { rebuild: 0.8, metals: 0.2 } },
    { id: 'BRKB', name: 'Berkshire Hathaway', type: 'CEDEARs', src: 'arg_cedears', sens: { insurance: -0.6, riskoff: -0.2 } },
    { id: 'DAL', name: 'Delta Air Lines', type: 'CEDEARs', src: 'arg_cedears', sens: { oil: -0.7, aviation: -1, riskoff: -0.4 } },
    { id: 'BA', name: 'Boeing', type: 'CEDEARs', src: 'arg_cedears', sens: { aviation: -0.5, defense: 0.3 } },
    { id: 'TSM', name: 'TSMC', type: 'CEDEARs', src: 'arg_cedears', sens: { chips: -1, riskoff: -0.4 } },
    { id: 'NVDA', name: 'Nvidia', type: 'CEDEARs', src: 'arg_cedears', sens: { chips: -0.7, riskoff: -0.6 } },
    { id: 'MELI', name: 'MercadoLibre', type: 'CEDEARs', src: 'arg_cedears', sens: { riskoff: -0.6, argrisk: -0.3, shipping: -0.2 } },
    // ETFs (como CEDEAR)
    { id: 'SPY', name: 'S&P 500 (SPY)', type: 'ETFs', src: 'arg_cedears', sens: { riskoff: -0.8, shipping: -0.1 } },
    { id: 'QQQ', name: 'Nasdaq 100 (QQQ)', type: 'ETFs', src: 'arg_cedears', sens: { riskoff: -0.9, chips: -0.4 } },
    { id: 'GLD', name: 'Oro (GLD)', type: 'ETFs', src: 'arg_cedears', sens: { gold: 1 } },
    { id: 'SLV', name: 'Plata (SLV)', type: 'ETFs', src: 'arg_cedears', sens: { gold: 0.6, metals: 0.4 } },
    { id: 'XLE', name: 'Energía (XLE)', type: 'ETFs', src: 'arg_cedears', sens: { oil: 0.9, gas: 0.3 } },
    { id: 'USO', name: 'Petróleo (USO)', type: 'ETFs', src: 'arg_cedears', sens: { oil: 1 } },
    { id: 'ITA', name: 'Defensa (ITA)', type: 'ETFs', src: 'arg_cedears', sens: { defense: 0.9, aviation: -0.2 } },
    { id: 'URA', name: 'Uranio (URA)', type: 'ETFs', src: 'arg_cedears', sens: { uranium: 0.9 } },
    { id: 'SMH', name: 'Semiconductores (SMH)', type: 'ETFs', src: 'arg_cedears', sens: { chips: -0.9, riskoff: -0.5 } },
    { id: 'XLF', name: 'Financieras (XLF)', type: 'ETFs', src: 'arg_cedears', sens: { riskoff: -0.6, insurance: -0.2 } },
    { id: 'EEM', name: 'Emergentes (EEM)', type: 'ETFs', src: 'arg_cedears', sens: { riskoff: -0.8, chips: -0.2 } },
    { id: 'EWZ', name: 'Brasil (EWZ)', type: 'ETFs', src: 'arg_cedears', sens: { riskoff: -0.6, oil: 0.3, metals: 0.3, agri: 0.3 } },
    // Bonos soberanos en dólares
    { id: 'AL30', name: 'Bonar 2030', type: 'Bonos', src: 'arg_bonds', sens: { argrisk: -1, riskoff: -0.6 } },
    { id: 'GD30', name: 'Global 2030', type: 'Bonos', src: 'arg_bonds', sens: { argrisk: -1, riskoff: -0.6 } },
    { id: 'GD35', name: 'Global 2035', type: 'Bonos', src: 'arg_bonds', sens: { argrisk: -1, riskoff: -0.8 } },
    { id: 'AE38', name: 'Bonar 2038', type: 'Bonos', src: 'arg_bonds', sens: { argrisk: -1, riskoff: -0.8 } },
    { id: 'GD41', name: 'Global 2041', type: 'Bonos', src: 'arg_bonds', sens: { argrisk: -1, riskoff: -0.9 } }
    // Las LECAPs se agregan en vivo desde el panel de letras (cambian de ticker al vencer)
  ];
  const lecapSens = { argrisk: -0.3, riskoff: -0.1 };

  // Palabras que vinculan una noticia con un factor (sin dirección)
  const factorWords = [
    { re: /petr[óo]leo|crudo|\boil\b|brent|wti|opep|opec|vaca muerta/i, f: 'oil' },
    { re: /\bgas\b|gnl|\blng\b/i, f: 'gas' },
    { re: /\boro\b|\bgold\b/i, f: 'gold' },
    { re: /trigo|ma[íi]z|soja|cereal|grano|fertiliz|wheat|grain|cosecha/i, f: 'agri' },
    { re: /cobre|litio|aluminio|metal|copper|cobalto/i, f: 'metals' },
    { re: /uranio|uranium/i, f: 'uranium' },
    { re: /defensa|armas|armamento|weapons|defense|otan|nato/i, f: 'defense' },
    { re: /flete|naviera|shipping|buque|contenedor|puerto/i, f: 'shipping' },
    { re: /chips?|semiconductor|tsmc|nvidia/i, f: 'chips' },
    { re: /vuelos?|aerol[íi]nea|aeropuerto|espacio a[ée]reo|airline/i, f: 'aviation' },
    { re: /asegurador|reasegur|insur/i, f: 'insurance' },
    { re: /reconstrucci|infraestructura/i, f: 'rebuild' },
    { re: /riesgo pa[íi]s|bonos? argentin|merval|milei|caputo|bcra|lecap/i, f: 'argrisk' },
    { re: /p[áa]nico|volatilidad|desplome|refugio|sell-?off|selloff/i, f: 'riskoff' }
  ];

  // ---------- desastres naturales ----------
  const disasterTypes = {
    EQ: { name: 'Sismo', color: '#FFC857', icon: '◎' },
    TC: { name: 'Ciclón / huracán', color: '#4CC9F0', icon: '🌀' },
    FL: { name: 'Inundación', color: '#4895EF', icon: '≋' },
    VO: { name: 'Volcán', color: '#FF7A3D', icon: '▲' },
    WF: { name: 'Incendio forestal', color: '#FF9F43', icon: '✸' },
    DR: { name: 'Sequía', color: '#C9A26B', icon: '☀' },
    ST: { name: 'Tormenta severa', color: '#90E0EF', icon: '⚡' }
  };
  const disasterWords = [
    { re: /terremoto|sismo|temblor|earthquake|tsunami/i, t: 'EQ' },
    { re: /hurac[áa]n|tif[óo]n|cicl[óo]n|hurricane|typhoon|cyclone|tormenta tropical/i, t: 'TC' },
    { re: /inundaci|crecida|desborde|flood/i, t: 'FL' },
    { re: /erupci[óo]n|volc[áa]n|volcano|eruption/i, t: 'VO' },
    { re: /incendio forestal|incendios forestales|wildfire|bushfire/i, t: 'WF' },
    { re: /sequ[íi]a|drought/i, t: 'DR' }
  ];
  const inBox = (lat, lon, a, b, c, d) => lat >= a && lat <= b && lon >= c && lon <= d;
  function regionOf(lat, lon) {
    if (inBox(lat, lon, -56, -21, -74, -53)) return 'argentina';
    if (inBox(lat, lon, -35, 5, -82, -34)) return 'sudamerica';
    if (inBox(lat, lon, 17, 31.5, -98, -79)) return 'golfo';
    if (inBox(lat, lon, 24, 50, -125, -66)) return 'eeuu';
    if (inBox(lat, lon, 20, 46, 118, 146)) return 'asiaeste';
    if (inBox(lat, lon, -11, 22, 94, 141)) return 'sudeste';
    if (inBox(lat, lon, 12, 40, 32, 63)) return 'medioriente';
    if (inBox(lat, lon, 35, 71, -11, 40)) return 'europa';
    return 'otro';
  }
  // shock de un desastre según tipo, región y severidad (0..1)
  function disasterShock(type, lat, lon) {
    const r = regionOf(lat, lon);
    const s = {};
    if (type === 'EQ') {
      Object.assign(s, { insurance: 0.6, rebuild: 0.5 });
      if (r === 'asiaeste') s.chips = 0.6;
      if (r === 'sudamerica' || r === 'argentina') s.metals = 0.6;
      if (r === 'argentina') s.argrisk = 0.3;
    } else if (type === 'TC') {
      Object.assign(s, { insurance: 0.8, rebuild: 0.5, aviation: 0.3 });
      if (r === 'golfo' || r === 'eeuu') { s.oil = 0.6; s.gas = 0.7; }
      if (r === 'asiaeste' || r === 'sudeste') { s.chips = 0.2; s.shipping = 0.3; }
    } else if (type === 'FL') {
      Object.assign(s, { insurance: 0.4, rebuild: 0.4, agri: 0.3 });
      if (r === 'argentina' || r === 'sudamerica') s.agri = 0.5;
      if (r === 'argentina') s.argrisk = 0.3;
    } else if (type === 'DR') {
      s.agri = 0.8;
      if (r === 'argentina') s.argrisk = 0.6;
    } else if (type === 'VO') {
      s.aviation = 0.8;
      if (r === 'sudeste') s.metals = 0.1;
    } else if (type === 'WF') {
      Object.assign(s, { insurance: 0.4, aviation: 0.1 });
    } else if (type === 'ST') {
      Object.assign(s, { insurance: 0.3 });
    }
    return s;
  }

  const disasterFeeds = [
    { name: 'USGS', kind: 'usgs', url: 'https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_day.geojson' },
    { name: 'GDACS', kind: 'gdacs', url: 'https://www.gdacs.org/xml/rss.xml' },
    { name: 'NASA EONET', kind: 'eonet', url: 'https://eonet.gsfc.nasa.gov/api/v3/events?status=open&days=10&limit=60' }
  ];
  const disasterNewsFeed = { name: 'Google Noticias · Desastres', kind: 'rss', url: 'https://news.google.com/rss/search?q=terremoto+OR+hurac%C3%A1n+OR+tif%C3%B3n+OR+inundaciones+OR+erupci%C3%B3n+OR+%22incendio+forestal%22+when:2d&hl=es-419&gl=AR&ceid=AR:es-419' };

  // ---------- radios en vivo (se resuelven en vivo contra radio-browser.info) ----------
  const radioServers = ['https://de1.api.radio-browser.info', 'https://fi1.api.radio-browser.info', 'https://de2.api.radio-browser.info'];
  const radios = [
    { q: 'Radio Mitre', group: 'Argentina' },
    { q: 'La Red', group: 'Argentina', country: 'AR' },
    { q: 'Cadena 3', group: 'Argentina' },
    { q: 'Radio Rivadavia', group: 'Argentina' },
    { q: 'Radio Nacional', group: 'Argentina', country: 'AR' },
    { q: 'Radio 10', group: 'Argentina', country: 'AR' },
    { q: 'Radio Continental', group: 'Argentina' },
    { q: 'El Destape', group: 'Argentina' },
    { q: 'RFI Español', group: 'Internacional en español' },
    { q: 'Radio Exterior de España', group: 'Internacional en español' },
    { q: 'W Radio', group: 'Internacional en español', country: 'CO' },
    { q: 'BBC World Service', group: 'Internacional' },
    { q: 'NPR', group: 'Internacional', country: 'US' },
    { q: 'Bloomberg Radio', group: 'Internacional' },
    { q: 'France Info', group: 'Internacional' },
    { q: 'DW', group: 'Internacional', country: 'DE' }
  ];

  // ---------- clips (canales de YouTube, el ID del canal se resuelve en vivo desde el @handle) ----------
  const channels = [
    { handle: 'dwespanol', name: 'DW Español' },
    { handle: 'France24_es', name: 'France 24 Español' },
    { handle: 'cnnee', name: 'CNN en Español' },
    { handle: 'BBCMundo', name: 'BBC Mundo' },
    { handle: 'todonoticias', name: 'TN' },
    { handle: 'AlJazeeraEnglish', name: 'Al Jazeera', id: 'UCNye-wNBqNL5ZzHSJj3l8Bg' },
    { handle: 'DWNews', name: 'DW News', id: 'UCknLrEdhRCp1aegoMqRaCZg' },
    { handle: 'Reuters', name: 'Reuters', id: 'UChqUTb7kYRX8-EiaN3XFrSQ' },
    { handle: 'markets', name: 'Bloomberg' }
  ];

  const argFeeds = ['arg_stocks', 'arg_cedears', 'arg_bonds', 'arg_notes'].map((k) => ({ key: k, url: 'https://data912.com/live/' + k }));
  const iolUrl = (sym) => 'https://iol.invertironline.com/titulo/cotizacion/BCBA/' + encodeURIComponent(sym);

  return { factors, conflictShock, instruments, lecapSens, factorWords, disasterTypes, disasterWords, disasterShock, regionOf, disasterFeeds, disasterNewsFeed, radioServers, radios, channels, argFeeds, iolUrl };
})();
