# Pulso Global

App Android que cruza **noticias en vivo de guerras y conflictos** con **las bolsas del mundo** y muestra cómo se conectan.

**Descargar APK:** [`dist/PulsoGlobal.apk`](dist/PulsoGlobal.apk) → abrirlo en el teléfono y permitir "instalar apps de origen desconocido".

## Pantallas
- **Mundo**: globo 3D (o mapa) con focos de guerra, **desastres naturales en vivo** (sismos USGS, alertas GDACS, eventos NASA EONET), bolsas del mundo y arcos animados hacia los mercados que afectan.
- **Noticias**: "Lo clave ahora" (fragmentos automáticos que juntan titular, clip y activos afectados) + feed en vivo. Cada noticia muestra sus **activos relacionados operables en IOL** (▲ a favor / ▼ en contra según la proyección, con la variación del día) y un clip relacionado.
- **Proyección**: grafo estilo Obsidian *eventos → factores → instrumentos IOL* (verde a favor, rojo en contra) con horizonte de días o semanas, ranking y "Red actual" (conflictos ↔ países ↔ activos ↔ noticias ↔ desastres).
- **Mercados**: Argentina · IOL (acciones, CEDEARs, ETFs, bonos AL/GD y LECAPs/BONCAPs detectadas en vivo) y Mundo (índices, commodities, divisas), más un índice de tensión global.
- **En vivo**: radios de noticias en vivo (Argentina e internacionales, vía radio-browser.info) con reproductor que sigue sonando en segundo plano, y clips recientes de DW, France 24, CNN en Español, BBC Mundo, TN, Al Jazeera, Reuters y Bloomberg ordenados por relevancia.

**Novedades 3.x** (todo con fuentes gratuitas, sin claves ni APIs pagas)
- **¿Ya se movió?**: en cada noticia, el precio al momento de la publicación contra el último (barras de 5 min; CEDEARs medidos con su acción en Nueva York).
- **Videos dentro de la app** (reproductor a pantalla completa) y **radio en segundo plano** con controles en la notificación y la pantalla de bloqueo.
- **Mapa vivo**: misiles con trayectoria y explosiones donde se reportan ataques (GDELT y ciudades citadas en las noticias; el origen es ilustrativo) y huracanes reales (NHC y GDACS) con su categoría, vientos y rumbo proyectado.

Fuentes: Google Noticias, GDELT GEO, NHC, BBC Mundo, DW, Al Jazeera, GDELT, USGS, GDACS, NASA EONET, Yahoo Finance, data912.com (paneles BYMA), YouTube RSS y radio-browser.info. La proyección es un modelo heurístico de exposición a factores (`web/model.js`), no una predicción ni asesoramiento financiero.

## Estructura
- `web/` — interfaz (HTML/CSS/JS + d3 y el mapa mundial embebidos, funciona sin CDN).
- `web/data.js` — focos de conflicto, activos globales y fuentes de noticias.
- `web/model.js` — factores, instrumentos IOL y sus sensibilidades, reglas de desastres, radios y canales.
- `android/` — contenedor nativo (WebView + puente HTTP sin CORS).
- `build.sh` — compila el APK sin Gradle: `apt-get install aapt apksigner zipalign dalvik-exchange && ./build.sh`.

Para probar la interfaz en una PC basta con abrir `web/index.html` (usa datos de ejemplo porque el navegador bloquea las fuentes por CORS).

> No es asesoramiento financiero.
