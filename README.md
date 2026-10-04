# Fuegos Artificiales 🎆 (Android)

Juego de fuegos artificiales realista para Android, hecho con OpenGL ES 2.0 y sonido sintetizado en tiempo real.

**Descargar APK:** [`apk/FuegosArtificiales.apk`](apk/FuegosArtificiales.apk)

## Instalación
1. Descarga `FuegosArtificiales.apk` en tu teléfono.
2. Ábrelo. Si Android lo pide, permite *"Instalar apps desconocidas"* para tu navegador o gestor de archivos.
3. Si Play Protect avisa de que la app es desconocida, pulsa *"Más detalles → Instalar de todas formas"* (la app no pide permisos aparte de la vibración).

Requiere Android 5.0 o superior.

## Cómo se juega
- **Toca el cielo** para lanzar una carcasa: estalla justo donde tocaste.
- **Mantén pulsado** para lanzar una ráfaga; puedes usar varios dedos a la vez.
- Encadena explosiones para subir el **combo** (hasta x15) y sumar puntos.
- Los combos cargan la barra de **GRAN FINAL**: cuando está llena, púlsala para un espectáculo de decenas de carcasas con remate de truenos.
- **AUTO** pone un espectáculo automático (y con él la Gran Final está siempre disponible).
- 🔊 / 📳 activan o quitan el sonido y la vibración.

## Modo Guerra 3D ⚔
Pulsa **⚔ GUERRA 3D** (debajo de la puntuación) para enfrentarte a la CPU en un escenario 3D: tu ciudad contra la ciudad enemiga, al otro lado de la bahía.
- **Toca la ciudad enemiga** para lanzar el arma elegida (puedes apuntar a un edificio o al aire sobre la ciudad).
- **Armas:** 🚀 Cohete (recarga rápida), 💥 Mega carcasa (gran radio), 🎇 Racimo (se divide en 6 bombetas), 🔥 Incendiario (prende fuego a todo lo que alcanza).
- **Defensa:** toca un cohete enemigo en el aire para lanzarle un interceptor (🛡 3 cargas que se recargan).
- Los edificios se incendian, el fuego se propaga a los vecinos y los carboniza, y al perder toda su vida se derrumban entre nubes de polvo.
- Gana quien reduzca la ciudad rival a ruinas. Tres dificultades: Fácil, Normal y Difícil. La CPU también intercepta tus cohetes.

## Tipos de carcasa
Peonía, Crisantemo, Sauce, Palmera, Anillo, Crossette, Estrobo, Crepitante, Kamuro, Doble pistilo, Corazón y Trueno (o Aleatorio).

## Realismo
- Física de cada estrella: velocidad inicial, resistencia del aire, gravedad y viento.
- Explosiones esféricas con distribución de Fibonacci, como las carcasas reales.
- Colores de compuestos pirotécnicos reales (estroncio, bario, cobre, sodio, magnesio...).
- Chispas de carbón que se enfrían de amarillo a rojo, estrellas que cambian de color, estrobos y crepitado.
- Estelas por persistencia de imagen y resplandor (bloom) en dos niveles.
- El cielo, el humo y el agua se iluminan con el color de cada explosión.
- Humo que se queda flotando y se desplaza con el viento.
- Ciudad nocturna con su reflejo ondulante en el agua.
- Sonido 100 % sintetizado: mortero, silbidos, estruendos con eco y crepitado. **El sonido llega con retraso según la altura**, igual que en la realidad.

## Compilar
No hace falta Gradle ni Android Studio:

```bash
sudo apt-get install aapt dalvik-exchange zipalign apksigner
./build.sh
```

El APK firmado queda en `apk/FuegosArtificiales.apk`.

Código fuente en `app/src/com/juan/fuegos/`:
- `Fireworks.java`: simulación (partículas, humo, luces, puntuación).
- `FireworksRenderer.java`: render OpenGL (estelas, bloom, cielo, agua, humo).
- `SoundEngine.java`: síntesis de sonido.
- `MainActivity.java`: interfaz y controles.
- `War.java`, `WarRenderer.java`, `WarActivity.java`: modo Guerra 3D (simulación, render 3D e interfaz).
