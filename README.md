# 📡 WiFi Radar — experimento Android

App Android que escanea las redes WiFi cercanas, las relaciona con tu posición GPS y
**estima dónde está físicamente cada router** (la misma idea que usan Google/Apple para
ubicarte sin GPS, o proyectos como [WiGLE](https://wigle.net)).

## Qué hace

| Pestaña | Contenido |
|---|---|
| **Lista** | Redes en tiempo real: señal (dBm y barras), distancia estimada, banda/canal, seguridad, fabricante (por la MAC), estándar Wi-Fi 4/5/6/7, y pistas como *"probable hotspot de un móvil"*, *"dispositivo IoT ESP32"*, *"impresora Wi-Fi Direct"*, *"posible cámara IP"*. Toca una red para ver el detalle y su ubicación estimada. |
| **Radar** | Radar animado con cada red a su distancia estimada (escala logarítmica). El ángulo es decorativo: el WiFi del móvil no da dirección. |
| **Mapa** | Mapa oscuro (OpenStreetMap/CARTO) con tu recorrido y la ubicación estimada de todos los routers vistos, coloreados por seguridad. |
| **🚶 Movimiento** | **Detector de movimiento por WiFi.** Tu cuerpo (≈60 % agua) absorbe y refleja la señal: si alguien pasa entre el móvil y el router, la señal tiembla. Con el móvil quieto, calibra con la habitación vacía y la app pita/vibra cuando detecta a alguien moverse, incluso detrás de una pared. |
| **🎯 Geiger** | **Encuentra físicamente cualquier router.** Elige una red y el móvil pita como un contador Geiger: cada vez más rápido al acercarte, con indicador 🔥 más caliente / 🧊 más frío. |
| **📍 Lugares** | **Ubicación en interiores sin GPS.** Enseña a la app cada habitación (30 s) y después adivina en cuál estás sólo por la "huella" WiFi, con porcentaje de confianza. |
| **Stats** | Congestión de canales y **mejor canal para tu router**, % de redes abiertas/WEP/WPS, fabricantes, nombres de red más repetidos, dispositivos curiosos. Exporta a CSV en formato WiGLE. |

### ¿Cómo estima la ubicación de un router?
Cada vez que escanea con GPS guarda `(red, potencia, lat, lon)`. La ubicación del router es el
**centroide ponderado por potencia** (en mW) de todas sus observaciones: donde la señal fue
más fuerte, más cerca debería estar. Cuanto más camines alrededor, mejor la estimación.

La distancia se estima con el modelo de pérdida en espacio libre:
`d = 10^((27.55 − 20·log10(f_MHz) + |RSSI|) / 20)` — en interiores con paredes suele sobreestimar.

## Descargar el APK
Cada push compila el APK con GitHub Actions y lo publica en
**Releases → `apk-latest` → `WifiRadar.apk`**. Ábrelo desde el móvil, descárgalo e instálalo
(Android pedirá permitir *instalar apps de origen desconocido*).

## Consejos
- Acepta el permiso de **ubicación** (Android lo exige para ver redes WiFi) y activa el GPS.
- El WiFi tiene que estar encendido, pero no hace falta conectarse a nada.
- Android limita a ~4 escaneos cada 2 minutos. Para escaneo continuo: *Opciones de desarrollador →
  desactivar "Limitación de búsqueda de Wi-Fi"*.
- Todo se guarda sólo en el teléfono (SQLite). El mapa necesita Internet para las teselas.

## Compilar localmente
Requiere JDK 17 y Android SDK (compileSdk 35):
```
gradle :app:assembleRelease
```
APK en `app/build/outputs/apk/release/app-release.apk`.

> Uso educativo: la app sólo **escucha** las balizas públicas que emiten los routers; no se conecta
> ni intenta acceder a ninguna red.
