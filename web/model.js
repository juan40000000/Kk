/* Modelo de impacto: eventos (guerras y desastres) -> factores de mercado -> instrumentos operables en IOL.
   Los pesos son heurísticos (dirección y magnitud relativa), no una predicción de precios. */
window.PG_MODEL = (function () {
  // + significa que el factor "sube" (más precio, más riesgo o más disrupción, según el caso)
  const factors = {
    oil: { short: 'Petróleo', name: 'Petróleo', up: 'sube el crudo' },
    gas: { short: 'Gas', name: 'Gas natural', up: 'sube el gas' },
    gold: { short: 'Oro', name: 'Oro', up: 'demanda de refugio' },
    agri: { short: 'Agro', name: 'Granos / agro', up: 'suben granos y fertilizantes' },
    metals: { short: 'Metales', name: 'Metales', up: 'suben cobre y metales' },
    uranium: { short: 'Uranio', name: 'Uranio', up: 'oferta de uranio en riesgo' },
    defense: { short: 'Defensa', name: 'Gasto en defensa', up: 'más gasto militar' },
    shipping: { short: 'Fletes', name: 'Fletes marítimos', up: 'rutas cortadas, fletes más caros' },
    chips: { short: 'Chips', name: 'Cadena de chips', up: 'riesgo de disrupción en semiconductores' },
    aviation: { short: 'Aviación', name: 'Aviación', up: 'espacio aéreo y vuelos afectados' },
    insurance: { short: 'Seguros', name: 'Siniestros', up: 'pérdidas para aseguradoras' },
    rebuild: { short: 'Reconstrucción', name: 'Reconstrucción', up: 'demanda de maquinaria y acero' },
    riskoff: { short: 'Riesgo global', name: 'Aversión al riesgo', up: 'fuga hacia activos seguros' },
    argrisk: { short: 'Riesgo AR', name: 'Riesgo Argentina', up: 'sube el riesgo país' }
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
    { re: /petr[óo]leo|crudo|\boil\b|\bbrent\b|\bwti\b|\bopep\b|\bopec\b|vaca muerta/i, f: 'oil' },
    { re: /\bgas natural\b|\bgas\b|\bgnl\b|\blng\b|gasoducto/i, f: 'gas' },
    { re: /\boro\b|\bgold\b/i, f: 'gold' },
    { re: /\btrigo\b|\bma[íi]z\b|\bsoja\b|cereales|granos|fertilizante|\bwheat\b|\bgrain|cosecha/i, f: 'agri' },
    { re: /\bcobre\b|\blitio\b|aluminio|\bcopper\b|cobalto|tierras raras/i, f: 'metals' },
    { re: /uranio|uranium/i, f: 'uranium' },
    { re: /gasto (militar|en defensa)|industria de (la )?defensa|armamento|\barmas\b|misiles|\botan\b|\bnato\b|contratistas de defensa|ministerio de defensa/i, f: 'defense' },
    { re: /\bfletes?\b|navieras?|\bshipping\b|buques? (mercantes?|cargueros?|petroleros?)|contenedores|transporte mar[íi]timo|canal de (suez|panam[áa])/i, f: 'shipping' },
    { re: /semiconductor|\bchips?\b|\btsmc\b|\bnvidia\b/i, f: 'chips' },
    { re: /aerol[íi]neas?|espacio a[ée]reo|vuelos (cancelados|suspendidos|desviados)|\bairlines?\b/i, f: 'aviation' },
    { re: /asegurador|reasegur|\binsurers?\b/i, f: 'insurance' },
    { re: /reconstrucci[óo]n/i, f: 'rebuild' },
    { re: /riesgo pa[íi]s|bonos? (soberanos|argentinos)|\bbcra\b|\blecaps?\b/i, f: 'argrisk' },
    { re: /p[áa]nico (en|de) (los )?mercados|volatilidad|desplome de (las )?bolsas|activos? refugio|\bsell-?off\b/i, f: 'riskoff' }
  ];

  // Menciones directas de empresas / instrumentos en el texto
  const entities = [
    { re: /\bypf\b/i, ids: ['YPFD'] }, { re: /vaca muerta/i, ids: ['YPFD', 'VIST'] }, { re: /\bvista (energy|oil)/i, ids: ['VIST'] },
    { re: /pampa energ/i, ids: ['PAMP'] }, { re: /\btgs\b|transportadora de gas del sur/i, ids: ['TGSU2'] }, { re: /central puerto/i, ids: ['CEPU'] },
    { re: /grupo galicia|banco galicia/i, ids: ['GGAL'] }, { re: /banco macro/i, ids: ['BMA'] }, { re: /ternium/i, ids: ['TXAR'] },
    { re: /\baluar\b/i, ids: ['ALUA'] }, { re: /\bcresud\b/i, ids: ['CRES'] }, { re: /loma negra/i, ids: ['LOMA'] },
    { re: /\bexxon/i, ids: ['XOM'] }, { re: /\bchevron\b/i, ids: ['CVX'] }, { re: /petrobras/i, ids: ['PBR'] },
    { re: /lockheed/i, ids: ['LMT'] }, { re: /raytheon|\brtx\b/i, ids: ['RTX'] }, { re: /\bbarrick\b/i, ids: ['B'] },
    { re: /newmont/i, ids: ['NEM'] }, { re: /freeport/i, ids: ['FCX'] }, { re: /minera vale|mineradora vale|\bvale s\.?a\b/i, ids: ['VALE'] },
    { re: /cameco/i, ids: ['CCJ'] }, { re: /\bmosaic\b/i, ids: ['MOS'] }, { re: /john deere|\bdeere\b/i, ids: ['DE'] },
    { re: /caterpillar/i, ids: ['CAT'] }, { re: /berkshire|warren buffett/i, ids: ['BRKB'] }, { re: /delta air|aerol[íi]nea delta/i, ids: ['DAL'] },
    { re: /\bboeing\b/i, ids: ['BA'] }, { re: /\btsmc\b/i, ids: ['TSM'] }, { re: /\bnvidia\b/i, ids: ['NVDA'] },
    { re: /mercado ?libre/i, ids: ['MELI'] }, { re: /wall street|s&p ?500/i, ids: ['SPY'] }, { re: /\bnasdaq\b/i, ids: ['QQQ'] },
    { re: /mercados emergentes/i, ids: ['EEM'] }, { re: /bovespa/i, ids: ['EWZ'] },
    { re: /riesgo pa[íi]s|bonos? (soberanos|argentinos|en d[óo]lares)|\bal30\b|\bgd30\b|bonares|bonos globales/i, ids: ['GD30', 'AL30'] },
    { re: /\blecaps?\b|\bboncaps?\b|letras del tesoro/i, ids: ['@lecap'] }
  ];
  const upRe = /\bsuben?\b|subi[óo]|dispar|\btrepa|\balza\b|r[ée]cord|m[áa]ximos?|se encarece|repunt|\bganan?\b|\bsoars?\b|\bjumps?\b|\brises?\b|\bsurges?\b/i;
  const downRe = /\bcaen?\b|cay[óo]|\bbajan?\b|desplom|retroced|m[íi]nimos?|pierden?|se abarata|derrumb|\bplunges?\b|\bfalls?\b|\bdrops?\b|\bslumps?\b/i;
  // notas de deportes/espectáculos que comparten palabras con los eventos ("Huracán", "Ciclón", "defensa"…)
  const offTopicRe = /\bvs\.?\s|\bvs\b|partido|f[úu]tbol|liga profesional|\btorneo\b|copa (de la liga|argentina|libertadores|sudamericana|del mundo)|alineaci|formaciones|d[óo]nde ver|cu[áa]ndo juegan|\bhinchas\b|\bgol(es)?\b|campeonato|cl[áa]sico|\bnba\b|\btenis\b|f[óo]rmula 1|\bhor[óo]scopo|telenovela|reality/i;
  // palabras amplias que solo cuentan si la nota además habla de guerra
  const weakKeywords = ['rusia', 'russia', 'moscú', 'moscow', 'putin', 'kremlin', 'china', 'beijing', 'pekín', 'xi jinping', 'india ', 'pakistán', 'pakistan', 'caribe', 'caribbean', 'guyana', 'siria', 'syria', 'irak', 'iraq', 'suez', 'nuclear iran', 'seúl', 'seoul', 'rwanda', 'ruanda', 'cobalto', 'cobalt', 'beijing'];


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
    { re: /terremoto|\bsismo|earthquake|tsunami/i, t: 'EQ' },
    { re: /tif[óo]n|tormenta tropical|cicl[óo]n tropical|typhoon|hurricane|cyclone/i, t: 'TC' },
    { re: /hurac[áa]n|cicl[óo]n/i, t: 'TC', ctx: true },
    { re: /inundaci|crecida|\bflood/i, t: 'FL' },
    { re: /erupci[óo]n|volc[áa]n|volcano|eruption/i, t: 'VO' },
    { re: /incendio forestal|incendios forestales|wildfire|bushfire/i, t: 'WF' },
    { re: /sequ[íi]a|drought/i, t: 'DR' }
  ];
  const disasterCtx = /categor[íi]a \d|vientos|toca tierra|tocar[áa] tierra|landfall|evacua|lluvias|marejada|meteorol|\bnhc\b|centro nacional de huracanes|costa|damnificad|v[íi]ctimas|muertos|\bdaños\b|alerta|tormenta|devast|emergencia|km\/h/i;
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

  return { entities, upRe, downRe, offTopicRe, weakKeywords, disasterCtx, factors, conflictShock, instruments, lecapSens, factorWords, disasterTypes, disasterWords, disasterShock, regionOf, disasterFeeds, disasterNewsFeed, radioServers, radios, channels, argFeeds, iolUrl };
})();
