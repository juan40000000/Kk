# Monitor El Niño 🌊

App Android (APK) para seguir el fenómeno **El Niño / La Niña (ENSO)** en vivo sobre un mapa.

## Qué muestra

- **Mapa de anomalía de temperatura del mar** (diaria, satelital GHRSST MUR vía NASA GIBS): rojo = más cálido de lo normal, azul = más frío. Selector de fecha, animación de los últimos 30 días, capa de temperatura absoluta y control de opacidad.
- **Regiones Niño 1+2, 3, 3.4 y 4** dibujadas en el mapa y coloreadas con su anomalía semanal más reciente (toca una para ver detalles).
- **Estado actual** (El Niño / Neutral / La Niña y su intensidad) según la anomalía semanal de Niño 3.4 de NOAA CPC.
- **Gráficos**: Niño 3.4 de las últimas 52 semanas y el índice ONI de los últimos 10 años.
- Guarda los últimos datos para verlos sin conexión.

## Descargar el APK

Cada push compila el APK con GitHub Actions:

- **Releases → `apk-latest` → `MonitorElNino.apk`**, o
- **Actions → Build APK → artefacto `MonitorElNino-apk`**.

Ábrelo en el teléfono y permite *instalar apps de origen desconocido*. Requiere Android 7.0 o superior.

## Compilar localmente

```bash
./gradlew assembleRelease   # requiere Android SDK (ANDROID_HOME)
```

El APK queda en `app/build/outputs/apk/release/app-release.apk` (firmado con la clave debug).

## Fuentes de datos

- NOAA Climate Prediction Center: `wksst9120.for` (índices semanales) y `oni.ascii.txt` (ONI).
- NASA GIBS: `GHRSST_L4_MUR_Sea_Surface_Temperature_Anomalies`.
- Mapa base © OpenStreetMap, © CARTO. Mapa con Leaflet.
