# Gangas ML — detector de errores de precio en Mercado Libre (Android)

App Android (Kotlin + Jetpack Compose) que analiza cientos de publicaciones de Mercado Libre
y detecta las que están **anormalmente baratas**: errores de tipeo, precios mal cargados y gangas reales,
filtrando el ruido típico (accesorios, precios señuelo, réplicas).

## Descargar el APK
Cada push compila el APK con GitHub Actions:
- **Releases** → `build-N` → `GangasML.apk`, o
- **Actions** → último run de *Build APK* → artefacto `GangasML-apk`.

Instalalo en el teléfono habilitando "instalar apps de origen desconocido".

## Técnicas de detección (`core/…/engine/DealEngine.kt`)
1. **Muestreo no sesgado**: combina páginas ordenadas por precio ascendente (donde aparecen los errores)
   con páginas por relevancia (precio "normal" del mercado), descargadas en paralelo con semáforo y backoff exponencial.
2. **Filtro de relevancia difuso**: normalización (acentos, "128 GB"→"128gb"), tolerancia a plurales/typos (Levenshtein ≤ 1),
   números de modelo exactos.
3. **Detección de accesorios por posición** ("Funda …", "… para iPhone 15") y de **precios señuelo**
   ("consultar", seña, cuotas, alquiler, $1 placeholder).
4. **Precio de referencia local por k-vecinos**: Jaccard ponderado por IDF (los tokens de modelo/capacidad pesan 1.5×),
   mediana ponderada por similitud, con contracción bayesiana hacia la mediana global cuando hay pocos vecinos.
   Así un "iPhone 15 Pro Max" se compara con Pro Max, no con el iPhone 15 base.
5. **Estadística robusta en escala log**: MAD y **z-score modificado de Iglewicz–Hoaglin**; **valla inferior de Tukey**.
6. **Detector de errores de tipeo**: precio ≈ referencia / 10, /100, /1000 (faltan ceros o coma mal puesta).
7. **Brecha contra el vecino más barato**, descuento publicado e **historial de precios** propio (detecta bajas entre escaneos).
8. **Score 0–100** con factor de confianza por tamaño de muestra y **nivel de riesgo** (réplicas, usados, "demasiado bueno").
9. Agrupa por condición (nuevo/usado) cuando hay muestra suficiente.

## Funciones
- Búsqueda con ranking por score, % bajo referencia o precio; chips explicando cada señal.
- **Alertas** en segundo plano (WorkManager) con notificaciones, precio máximo y score mínimo; sin repetir avisos.
- Compartir una publicación desde la app de Mercado Libre → busca similares y te dice si es ganga.
- 7 países (AR, MX, BR, CL, CO, UY, PE).

## Fuente de datos
Desde 2025 la API `sites/{SITE}/search` de Mercado Libre suele requerir token. Opciones (Ajustes):
- **Recomendado**: creá una app gratis en <https://developers.mercadolibre.com.ar/devcenter> y cargá Client ID + Secret;
  la app obtiene/renueva el token (`client_credentials`) sola.
- Sin credenciales usa el **listado web público** (parser Jsoup tolerante a los layouts `poly-card` y clásico).
  Puede romperse si ML cambia su HTML o limita el acceso; respetá los términos de uso de Mercado Libre.

> Un score alto no garantiza nada: verificá vendedor, reputación y descripción antes de comprar.
> Los errores de precio muchas veces son cancelados por el vendedor.

## Compilar localmente
```bash
./gradlew :core:test            # tests del motor (JVM)
./gradlew :app:assembleRelease  # requiere Android SDK
./gradlew -PcoreOnly :core:test # sin Android SDK
```

Firma: sin secrets se firma con la clave debug. Para una firma estable agregá los secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
