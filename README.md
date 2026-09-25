# 📡 Radio Escucha SDR

App Android para radioaficionados y curiosos del espectro: escucha señales de radio con un dongle
**RTL-SDR**, muestra el espectro y la cascada (waterfall) en tiempo real y **busca automáticamente
señales interesantes** en las bandas que elijas.

## Funciones

- **Espectro + cascada** de 1 MHz de ancho. Tocá una señal para sintonizarla; arrastrá para recorrer la banda.
- **Demoduladores**: WFM (FM comercial), NFM (repetidoras, marina, PMR), AM (banda aérea, CB),
  USB/LSB (banda lateral) y CW (Morse, con tono de 700 Hz).
- **🔎 Buscar señales**: barre una banda (2 m, 70 cm, aérea, marina, satélites, PMR/FRS, ISM 433,
  FM, CB o un rango propio) y lista las emisiones que encuentra con su SNR, banda, modo sugerido y
  cuántas veces se escucharon. Tocá un resultado para escucharlo.
- **★ Favoritos** con frecuencias interesantes predefinidas: ISS (voz, APRS, repetidor), APRS,
  satélites Meteor-M, emergencia aeronáutica 121.5, marino canal 16, NOAA, PMR446, FRS, FT8, WWV…
  y los tuyos propios.
- **Plan de bandas** integrado: muestra en qué banda estás y elige el modo adecuado.
- **S-meter**, silenciador (squelch), control de ganancia y corrección ppm.
- **⏺ Grabación** del audio a WAV (en `Android/data/com.radioescucha.app/files/Music`).
- **Modo Demo** con emisoras simuladas (ISS, APRS, baliza CW, banda aérea, FM…) para probar sin hardware.

## Hardware y fuentes de señal

| Fuente | Qué necesitás |
|---|---|
| Dongle RTL-SDR por USB | Un RTL-SDR (RTL2832U + R820T/R828D, p. ej. RTL-SDR Blog V3/V4), cable OTG y la app gratuita **RTL-SDR driver** (Martin Marinov) de Google Play. La app la abre sola. |
| Servidor rtl_tcp | Una PC o Raspberry Pi con el dongle corriendo `rtl_tcp -a 0.0.0.0`. Poné la IP y el puerto en ⚙ Ajustes. |
| Demo | Nada: señales simuladas. |

Rango típico: 24 MHz – 1,7 GHz. Para HF (onda corta, 40 m, 20 m…) hace falta un dongle con muestreo
directo/HF (como el V4) o un conversor ascendente.

## Descargar el APK

Cada push compila el APK con GitHub Actions (workflow *Compilar APK*). Descargalo desde la sección
**Releases** del repositorio (`RadioEscucha-SDR.apk`) o desde los artefactos de la ejecución del workflow.

> El APK está firmado con una clave de depuración generada en cada compilación: para actualizar a una
> versión nueva puede ser necesario desinstalar la anterior.

## Compilar localmente

Requiere JDK 17 y el SDK de Android (API 35):

```bash
./gradlew testDebugUnitTest assembleRelease
# APK en app/build/outputs/apk/release/app-release.apk
```

## Cómo funciona

```
RTL-SDR ─ rtl_tcp ─► IQ 8 bits @ 1,024 MS/s ─► FFT 1024 ─► espectro / cascada / escáner
                                         └──► mezcla + FIR ↓4 ─► 256 kS/s ─► WFM ─► audio 32 kHz
                                                          └──► FIR ↓8 ─► 32 kS/s ─► NFM/AM/SSB/CW
```

Todo el procesamiento de señal está escrito en Kotlin puro (`app/src/main/java/.../dsp`), sin
bibliotecas externas, y tiene pruebas unitarias que verifican cada demodulador con señales sintéticas.

Escuchar está permitido en la mayoría de los países, pero respetá la privacidad de las comunicaciones
y la legislación local.
