# Pulso Global

App Android que cruza **noticias en vivo de guerras y conflictos** con **las bolsas del mundo** y muestra cómo se conectan.

**Descargar APK:** [`dist/PulsoGlobal.apk`](dist/PulsoGlobal.apk) → abrirlo en el teléfono y permitir "instalar apps de origen desconocido".

## Pantallas
- **Mundo**: globo 3D giratorio (o mapa plano) con focos de conflicto que laten según la actividad informativa, plazas financieras coloreadas por su variación y arcos animados de cada conflicto hacia los mercados que impacta.
- **Red**: grafo de conocimiento estilo Obsidian (conflictos ↔ países ↔ activos ↔ noticias). Tocá un nodo para resaltar sus conexiones; arrastrá y pellizcá para navegar.
- **Noticias**: feed en vivo (Google Noticias, BBC Mundo, DW, Al Jazeera, GDELT), cada titular etiquetado con su conflicto y el movimiento de los activos afectados.
- **Mercados**: índices, materias primas, divisas, defensa/energía y tecnología con sparklines, más un **índice de tensión geopolítica** (VIX + actividad de conflictos + petróleo y oro).

Las noticias se actualizan cada 3 min y las cotizaciones (Yahoo Finance, con demora según la plaza) cada ~75 s. Lo último descargado queda guardado para verlo sin conexión.

## Estructura
- `web/` — interfaz (HTML/CSS/JS + d3 y el mapa mundial embebidos, funciona sin CDN).
- `web/data.js` — focos de conflicto, activos y reglas que los vinculan (fácil de editar).
- `android/` — contenedor nativo (WebView + puente HTTP sin CORS).
- `build.sh` — compila el APK sin Gradle: `apt-get install aapt apksigner zipalign dalvik-exchange && ./build.sh`.

Para probar la interfaz en una PC basta con abrir `web/index.html` (usa datos de ejemplo porque el navegador bloquea las fuentes por CORS).

> No es asesoramiento financiero.
