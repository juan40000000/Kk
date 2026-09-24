# Astro Glow — Viaje a través de la luz

Juego de plataformas arcade estilo clásico donde un astronauta recorre seis
galaxias llenas de luz y oscuridad, con música generativa inspiracional.

## Mundos

| # | Mundo | Ambiente |
|---|-------|----------|
| 1 | Bosque Bioluminiscente | hongos gigantes que brillan, luciérnagas |
| 2 | Cavernas de Cristal | cristales de neón, estalactitas |
| 3 | Tundra de Aurora | auroras boreales animadas, pinos nevados |
| 4 | Océano Nebular | rayos de luz submarinos, algas y medusas (gravedad baja) |
| 5 | Volcán Estelar | ríos de lava, brasas flotantes |
| 6 | Jardín Cósmico | islas flotantes, planeta con anillos |

Cada mundo tiene 2 niveles, su propia paleta, iluminación dinámica (oscuridad con
halos de luz), frase inspiradora y banda sonora generada en tiempo real con Web Audio
(pads, arpegios, bajo, campanas y reverb).

## Cómo se juega

- **Mover:** botones ◀ ▶ (táctil) o flechas / A-D.
- **Saltar:** botón de salto o Espacio. Pulsa otra vez en el aire para el **doble salto con propulsor**.
- Salta sobre las sombras para vencerlas; evita pinchos y lava.
- Golpea las **cajas de energía ✦** desde abajo: polvo estelar, **escudo de luz** o vidas extra.
- 100 de polvo estelar = 1 vida. Los **muelles** te lanzan alto; los **puntos de control** guardan tu avance.
- Llega al **portal de luz** al final de cada nivel.

## Instalar el APK

Cada push que cambia `game/` o `android/` ejecuta el workflow **Construir APK**
(GitHub Actions), que compila el APK firmado y lo publica en **Releases**
(`AstroGlow.apk`). Descárgalo desde el móvil y ábrelo (permite instalar apps de
origen desconocido).

Compilar localmente (requiere Android SDK y JDK 17):

```bash
cd android
./gradlew assembleRelease
# APK: android/app/build/outputs/apk/release/app-release.apk
```

El APK se firma con `android/app/astroglow-dev.keystore` (clave de desarrollo
incluida para que las actualizaciones se instalen sobre la versión anterior).
Para publicar en Play Store define tu propio keystore con las variables
`ASTRO_KEYSTORE`, `ASTRO_KEYSTORE_PASSWORD`, `ASTRO_KEY_ALIAS` y `ASTRO_KEY_PASSWORD`.

## Probar en el navegador

```bash
npx serve game   # o abre game/index.html directamente
```

## Estructura

- `game/` — el juego HTML5 (Canvas 2D, sin dependencias)
  - `js/core.js` — utilidades, entrada y motor de audio generativo
  - `js/worlds.js` — mundos, paletas y canciones
  - `js/level.js` — generador de niveles por bloques
  - `js/draw.js` — arte procedural (fondos parallax, tiles, personajes)
  - `js/game.js` — física, estados, iluminación y UI
- `android/` — app nativa (WebView a pantalla completa, sin conexión)
