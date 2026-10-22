/* Base de conocimiento: focos de conflicto, activos de mercado y cómo se conectan.
   La actividad ("calor") de cada foco se recalcula en vivo según el volumen de noticias. */
window.PG_DATA = (function () {
  const conflicts = [
    {
      id: 'ukr', name: 'Rusia – Ucrania', short: 'Ucrania', lat: 48.6, lon: 35.5, base: 5,
      region: 'Europa del Este',
      countries: ['Ukraine', 'Russia'],
      summary: 'Guerra de alta intensidad en el este y sur de Ucrania, con ataques a infraestructura energética y rutas del Mar Negro.',
      channels: 'Granos del Mar Negro, gas europeo, gasto en defensa de la UE y el euro.',
      keywords: ['ucrania', 'ukrain', 'kiev', 'kyiv', 'zelensk', 'kremlin', 'putin', 'donbás', 'donbas', 'jarkov', 'kharkiv', 'odesa', 'zaporiyia', 'zaporizh', 'crimea', 'rusia', 'russia', 'moscú', 'moscow'],
      assets: ['ZW=F', 'TTF=F', '^GDAXI', '^STOXX50E', 'EURUSD=X', 'RHM.DE', 'GC=F']
    },
    {
      id: 'mideast', name: 'Israel – Gaza – Líbano', short: 'Gaza', lat: 31.45, lon: 34.4, base: 5,
      region: 'Medio Oriente',
      countries: ['Israel', 'Palestine', 'Lebanon', 'Syria'],
      summary: 'Conflicto en Gaza y frentes asociados en Líbano, Siria y Cisjordania; riesgo de escalada regional.',
      channels: 'Prima de riesgo en petróleo, oro como refugio, bolsa de Tel Aviv y contratistas de defensa.',
      keywords: ['gaza', 'israel', 'hamás', 'hamas', 'hezbol', 'hezbollah', 'líbano', 'lebanon', 'cisjordania', 'west bank', 'netanyahu', 'idf', 'rafah', 'siria', 'syria'],
      assets: ['BZ=F', 'GC=F', 'TA35.TA', 'LMT', 'ILS=X']
    },
    {
      id: 'iran', name: 'Irán – Golfo / Ormuz', short: 'Ormuz', lat: 26.6, lon: 56.3, base: 4,
      region: 'Golfo Pérsico',
      countries: ['Iran', 'Iraq'],
      summary: 'Tensión entre Irán, Israel y EE.UU.; programa nuclear y seguridad en el estrecho de Ormuz (≈20% del petróleo mundial).',
      channels: 'Brent y WTI, volatilidad (VIX), oro, petroleras y transporte marítimo.',
      keywords: ['irán', 'iran', 'teherán', 'tehran', 'ormuz', 'hormuz', 'jamenei', 'khamenei', 'nuclear iran', 'golfo pérsico', 'persian gulf', 'irak', 'iraq'],
      assets: ['BZ=F', 'CL=F', 'GC=F', '^VIX', 'XOM']
    },
    {
      id: 'redsea', name: 'Mar Rojo – Yemen', short: 'Mar Rojo', lat: 14.5, lon: 42.6, base: 4, labelBelow: true,
      region: 'Mar Rojo / Bab el-Mandeb',
      countries: ['Yemen', 'Eritrea', 'Djibouti'],
      summary: 'Ataques hutíes contra el tráfico marítimo; desvíos por el Cabo de Buena Esperanza.',
      channels: 'Fletes y navieras, petróleo, inflación de bienes importados en Europa.',
      keywords: ['mar rojo', 'red sea', 'hutí', 'huti', 'houthi', 'yemen', 'bab el-mandeb', 'adén', 'aden', 'suez'],
      assets: ['BZ=F', 'MAERSK-B.CO', 'ZIM', '^STOXX50E']
    },
    {
      id: 'taiwan', name: 'Estrecho de Taiwán', short: 'Taiwán', lat: 24.2, lon: 120.6, base: 3,
      region: 'Asia-Pacífico',
      countries: ['Taiwan', 'China'],
      summary: 'Presión militar de China sobre Taiwán y competencia tecnológica EE.UU.–China.',
      channels: 'Semiconductores (TSMC, Nvidia), Nasdaq, Hong Kong y bolsa de Taiwán.',
      keywords: ['taiwán', 'taiwan', 'taipéi', 'taipei', 'estrecho de taiwán', 'pla ', 'ejército popular', 'xi jinping', 'beijing', 'pekín', 'china'],
      assets: ['TSM', 'NVDA', '^IXIC', '^TWII', '^HSI', '000001.SS']
    },
    {
      id: 'korea', name: 'Península de Corea', short: 'Corea', lat: 38.6, lon: 126.8, base: 2,
      region: 'Asia Oriental',
      countries: ['North Korea', 'South Korea'],
      summary: 'Pruebas de misiles de Corea del Norte y cooperación militar con Rusia.',
      channels: 'KOSPI, won coreano, Samsung y cadena de chips.',
      keywords: ['corea del norte', 'north korea', 'pyongyang', 'kim jong', 'seúl', 'seoul', 'misil balístico', 'ballistic missile'],
      assets: ['^KS11', 'KRW=X', '005930.KS']
    },
    {
      id: 'sudan', name: 'Sudán', short: 'Sudán', lat: 15.5, lon: 32.5, base: 4,
      region: 'África Oriental',
      countries: ['Sudan', 'S. Sudan', 'Chad'],
      summary: 'Guerra civil entre el ejército y las RSF; una de las peores crisis humanitarias del mundo.',
      channels: 'Oro (exportaciones informales), goma arábiga, corredores del Mar Rojo.',
      keywords: ['sudán', 'sudan', 'jartum', 'khartoum', 'darfur', 'rsf', 'fuerzas de apoyo rápido', 'rapid support', 'el fasher'],
      assets: ['GC=F', 'BZ=F']
    },
    {
      id: 'drc', name: 'RD Congo – M23', short: 'Congo', lat: -1.7, lon: 29.2, base: 3,
      region: 'Grandes Lagos',
      countries: ['Dem. Rep. Congo', 'Rwanda', 'Uganda', 'Burundi'],
      summary: 'Combates en Kivu con el M23; región clave en minerales críticos.',
      channels: 'Cobalto, cobre, coltán: baterías y electrónica.',
      keywords: ['congo', 'rdc', 'm23', 'goma', 'kivu', 'kinshasa', 'ruanda', 'rwanda', 'cobalto', 'cobalt'],
      assets: ['HG=F', 'GLEN.L']
    },
    {
      id: 'sahel', name: 'Sahel', short: 'Sahel', lat: 14.5, lon: -1.5, base: 3,
      region: 'África Occidental',
      countries: ['Mali', 'Burkina Faso', 'Niger'],
      summary: 'Insurgencia yihadista y juntas militares en Malí, Burkina Faso y Níger.',
      channels: 'Uranio (Níger), oro (Malí, Burkina) y seguridad energética europea.',
      keywords: ['sahel', 'malí', 'mali', 'burkina', 'níger', 'niger', 'bamako', 'niamey', 'uagadugú', 'ouagadougou', 'yihadista', 'jihadist'],
      assets: ['URA', 'GC=F']
    },
    {
      id: 'myanmar', name: 'Myanmar', short: 'Myanmar', lat: 21.0, lon: 96.0, base: 3,
      region: 'Sudeste Asiático',
      countries: ['Myanmar'],
      summary: 'Guerra civil entre la junta y fuerzas de resistencia étnicas y prodemocráticas.',
      channels: 'Tierras raras y estaño; cadenas de suministro del sudeste asiático.',
      keywords: ['myanmar', 'birmania', 'burma', 'junta militar', 'naipyidó', 'rangún', 'yangon', 'arakan'],
      assets: ['REMX']
    },
    {
      id: 'indpak', name: 'India – Pakistán', short: 'Cachemira', lat: 34.1, lon: 74.8, base: 2,
      region: 'Asia del Sur',
      countries: ['India', 'Pakistan'],
      summary: 'Rivalidad nuclear y choques recurrentes en Cachemira.',
      channels: 'Sensex, rupia india y flujos de inversión a mercados emergentes.',
      keywords: ['cachemira', 'kashmir', 'pakistán', 'pakistan', 'islamabad', 'nueva delhi', 'new delhi', 'india '],
      assets: ['^BSESN', 'INR=X']
    },
    {
      id: 'venezuela', name: 'Venezuela – Caribe', short: 'Venezuela', lat: 10.5, lon: -66.9, base: 3,
      region: 'América Latina',
      countries: ['Venezuela', 'Colombia', 'Guyana'],
      summary: 'Tensión política y militar entre EE.UU. y Venezuela, despliegue naval en el Caribe y disputa del Esequibo.',
      channels: 'Petróleo pesado, sanciones, bonos y riesgo país regional.',
      keywords: ['venezuela', 'caracas', 'maduro', 'caribe', 'caribbean', 'esequibo', 'pdvsa', 'guyana'],
      assets: ['CL=F', '^MERV', '^BVSP']
    }
  ];

  // lat/lon = plaza donde se negocia (para dibujar el arco en el mapa)
  const assets = [
    { id: '^GSPC', name: 'S&P 500', cat: 'Índices', hub: 'Nueva York', lat: 40.71, lon: -74.01 },
    { id: '^IXIC', name: 'Nasdaq', cat: 'Índices', hub: 'Nueva York', lat: 40.76, lon: -73.98 },
    { id: '^DJI', name: 'Dow Jones', cat: 'Índices', hub: 'Nueva York', lat: 40.70, lon: -74.02 },
    { id: '^VIX', name: 'VIX (miedo)', cat: 'Índices', hub: 'Chicago', lat: 41.88, lon: -87.63, inverse: true },
    { id: '^STOXX50E', name: 'Euro Stoxx 50', cat: 'Índices', hub: 'Fráncfort', lat: 50.11, lon: 8.68 },
    { id: '^GDAXI', name: 'DAX', cat: 'Índices', hub: 'Fráncfort', lat: 50.12, lon: 8.66 },
    { id: '^FTSE', name: 'FTSE 100', cat: 'Índices', hub: 'Londres', lat: 51.51, lon: -0.09 },
    { id: '^N225', name: 'Nikkei 225', cat: 'Índices', hub: 'Tokio', lat: 35.68, lon: 139.77 },
    { id: '^HSI', name: 'Hang Seng', cat: 'Índices', hub: 'Hong Kong', lat: 22.28, lon: 114.16 },
    { id: '000001.SS', name: 'Shanghái Comp.', cat: 'Índices', hub: 'Shanghái', lat: 31.23, lon: 121.47 },
    { id: '^TWII', name: 'Taiex', cat: 'Índices', hub: 'Taipéi', lat: 25.03, lon: 121.56 },
    { id: '^KS11', name: 'KOSPI', cat: 'Índices', hub: 'Seúl', lat: 37.57, lon: 126.98 },
    { id: '^BSESN', name: 'Sensex', cat: 'Índices', hub: 'Bombay', lat: 18.93, lon: 72.83 },
    { id: 'TA35.TA', name: 'TA-35', cat: 'Índices', hub: 'Tel Aviv', lat: 32.07, lon: 34.79 },
    { id: '^BVSP', name: 'Bovespa', cat: 'Índices', hub: 'São Paulo', lat: -23.55, lon: -46.63 },
    { id: '^MERV', name: 'Merval', cat: 'Índices', hub: 'Buenos Aires', lat: -34.60, lon: -58.38 },

    { id: 'BZ=F', name: 'Petróleo Brent', cat: 'Materias primas', hub: 'Londres (ICE)', lat: 51.52, lon: -0.08 },
    { id: 'CL=F', name: 'Petróleo WTI', cat: 'Materias primas', hub: 'Nueva York (NYMEX)', lat: 40.71, lon: -74.0 },
    { id: 'GC=F', name: 'Oro', cat: 'Materias primas', hub: 'Nueva York (COMEX)', lat: 40.72, lon: -74.0 },
    { id: 'TTF=F', name: 'Gas TTF (Europa)', cat: 'Materias primas', hub: 'Ámsterdam', lat: 52.37, lon: 4.90 },
    { id: 'ZW=F', name: 'Trigo', cat: 'Materias primas', hub: 'Chicago (CBOT)', lat: 41.88, lon: -87.63 },
    { id: 'HG=F', name: 'Cobre', cat: 'Materias primas', hub: 'Nueva York (COMEX)', lat: 40.70, lon: -74.0 },

    { id: 'EURUSD=X', name: 'EUR/USD', cat: 'Divisas', hub: 'Londres', lat: 51.50, lon: -0.12 },
    { id: 'ILS=X', name: 'USD/ILS (shekel)', cat: 'Divisas', hub: 'Tel Aviv', lat: 32.08, lon: 34.78 },
    { id: 'KRW=X', name: 'USD/KRW (won)', cat: 'Divisas', hub: 'Seúl', lat: 37.56, lon: 126.97 },
    { id: 'INR=X', name: 'USD/INR (rupia)', cat: 'Divisas', hub: 'Bombay', lat: 18.94, lon: 72.84 },
    { id: 'BTC-USD', name: 'Bitcoin', cat: 'Divisas', hub: 'Global', lat: 47.37, lon: 8.54 },

    { id: 'LMT', name: 'Lockheed Martin', cat: 'Defensa y energía', hub: 'Nueva York', lat: 40.73, lon: -74.0 },
    { id: 'RHM.DE', name: 'Rheinmetall', cat: 'Defensa y energía', hub: 'Fráncfort', lat: 50.10, lon: 8.70 },
    { id: 'XOM', name: 'ExxonMobil', cat: 'Defensa y energía', hub: 'Nueva York', lat: 40.74, lon: -74.0 },
    { id: 'MAERSK-B.CO', name: 'Maersk', cat: 'Defensa y energía', hub: 'Copenhague', lat: 55.68, lon: 12.57 },
    { id: 'ZIM', name: 'ZIM Shipping', cat: 'Defensa y energía', hub: 'Nueva York', lat: 40.75, lon: -74.0 },
    { id: 'URA', name: 'ETF Uranio', cat: 'Defensa y energía', hub: 'Nueva York', lat: 40.70, lon: -74.03 },
    { id: 'REMX', name: 'ETF Tierras raras', cat: 'Defensa y energía', hub: 'Nueva York', lat: 40.69, lon: -74.0 },
    { id: 'GLEN.L', name: 'Glencore', cat: 'Defensa y energía', hub: 'Londres', lat: 51.51, lon: -0.10 },

    { id: 'TSM', name: 'TSMC', cat: 'Tecnología', hub: 'Nueva York', lat: 40.71, lon: -74.0 },
    { id: 'NVDA', name: 'Nvidia', cat: 'Tecnología', hub: 'Nueva York', lat: 40.76, lon: -73.97 },
    { id: '005930.KS', name: 'Samsung', cat: 'Tecnología', hub: 'Seúl', lat: 37.58, lon: 126.99 }
  ];

  // Palabras de mercado: una noticia que las menciona se vincula a esos activos
  const marketWords = [
    { re: /petr[óo]leo|crudo|\boil\b|brent|wti|opep|opec/i, assets: ['BZ=F', 'CL=F'] },
    { re: /\boro\b|\bgold\b/i, assets: ['GC=F'] },
    { re: /\bgas\b|gnl|\blng\b/i, assets: ['TTF=F'] },
    { re: /trigo|cereal|grano|wheat|grain/i, assets: ['ZW=F'] },
    { re: /wall street|s&p|dow jones|nasdaq|bolsas?\b|acciones|stocks?|mercados?/i, assets: ['^GSPC'] },
    { re: /chips?|semiconductor|nvidia|tsmc/i, assets: ['TSM', 'NVDA'] },
    { re: /bitcoin|cripto/i, assets: ['BTC-USD'] },
    { re: /merval|riesgo pa[íi]s|argentin/i, assets: ['^MERV'] },
    { re: /defensa|armas|weapons|defense|rheinmetall|lockheed/i, assets: ['LMT', 'RHM.DE'] },
    { re: /flete|naviera|shipping|contenedor|maersk/i, assets: ['MAERSK-B.CO'] },
    { re: /cobre|copper|cobalto/i, assets: ['HG=F'] },
    { re: /\beuro\b/i, assets: ['EURUSD=X'] },
    { re: /volatilidad|p[áa]nico|vix/i, assets: ['^VIX'] }
  ];

  const feeds = [
    { name: 'Google Noticias · Guerras', kind: 'rss', url: 'https://news.google.com/rss/search?q=guerra+OR+%22conflicto+armado%22+OR+bombardeo+OR+ataque+militar+when:2d&hl=es-419&gl=AR&ceid=AR:es-419' },
    { name: 'Google Noticias · Mercados', kind: 'rss', url: 'https://news.google.com/rss/search?q=(petr%C3%B3leo+OR+bolsas+OR+oro+OR+%22Wall+Street%22)+(guerra+OR+conflicto+OR+tensi%C3%B3n)+when:2d&hl=es-419&gl=AR&ceid=AR:es-419' },
    { name: 'BBC Mundo', kind: 'rss', url: 'https://feeds.bbci.co.uk/mundo/rss.xml' },
    { name: 'DW Español', kind: 'rss', url: 'https://rss.dw.com/rdf/rss-sp-all' },
    { name: 'Al Jazeera', kind: 'rss', url: 'https://www.aljazeera.com/xml/rss/all.xml' },
    { name: 'GDELT', kind: 'gdelt', url: 'https://api.gdeltproject.org/api/v2/doc/doc?query=(war%20OR%20airstrike%20OR%20missile%20OR%20guerra)%20(sourcelang:spanish%20OR%20sourcelang:english)&mode=artlist&format=json&maxrecords=60&sort=datedesc&timespan=24h' }
  ];

  return { conflicts, assets, marketWords, feeds };
})();
