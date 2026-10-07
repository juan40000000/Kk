# Monocromo

Launcher para Android al estilo **Windows Phone**, en **blanco y negro** y pensado para
**usar menos el móvil**.

## Descargar la APK

1. Entra en la pestaña **Releases** de este repositorio desde el móvil.
2. Descarga `Monocromo.apk` de la versión **Monocromo** más reciente (etiqueta `monocromo-N`).
3. Ábrela y permite "instalar apps de origen desconocido" si te lo pide.
4. Pulsa el botón de inicio y elige **Monocromo** → **Siempre**.
   (O en Monocromo: `• • •` → *elegir launcher predeterminado*.)

Cada cambio que se sube al repositorio compila una APK nueva automáticamente
(GitHub Actions) y se instala encima de la anterior sin perder tus ajustes.

## Cómo se usa

- **Inicio**: arriba, fijos, el **reloj** y el **clima** (con animación pixel art de 8 bits
  según el tiempo: sol, luna y estrellas, nubes, lluvia, nieve, tormenta o niebla).
  Debajo, los mosaicos de tus apps, sin iconos.
- **Mosaicos**: mantén pulsado un mosaico y **desliza** hacia la derecha/abajo para agrandarlo
  o hacia la izquierda/arriba para achicarlo (pequeño, mediano, ancho, grande), como en
  Windows Phone. Si lo mantienes pulsado y sueltas sin deslizar, sale el menú: **blanco o
  negro**, tamaño, mover, pausa o desanclar.
- **Clima**: toca el mosaico del clima para elegir tu ubicación o escribir una ciudad
  (datos de Open-Meteo, gratis y sin cuenta). Tócalo de nuevo para actualizar; mantenlo
  pulsado para cambiar la ciudad.
- **Desliza a la izquierda** (o toca *todas las apps →*): lista de apps en texto blanco
  sobre fondo negro, en minúsculas y agrupada por letras, como en Windows Phone.
  - Toca una letra para saltar a otra letra.
  - Escribe en *buscar* y pulsa Intro para abrir la primera coincidencia.
- **Mantén pulsada una app** de la lista: anclar a inicio, poner pausa, ocultar,
  información o desinstalar.
- **Toca el reloj**: uso de hoy. **Mantén pulsado el reloj** o `• • •`: ajustes.

## Anti-adicción

- Todo en blanco y negro, sin iconos de colores, sin globos de notificación.
- **Pausa**: marca apps como distractoras (salen en gris). Al abrirlas aparece
  "respira." con una cuenta atrás (5–60 s, configurable) y la pregunta
  *¿es una decisión o un impulso?*. Si eliges *mejor no* cuenta como impulso evitado.
- **Apps ocultas**: no aparecen en la lista; solo si escribes 3+ letras de su nombre.
- **Contador diario** en el reloj: aperturas de hoy e impulsos evitados.

Consejo: para todo el teléfono en gris, activa *Bienestar digital → Modo descanso →
escala de grises* o *Accesibilidad → Corrección de color → Escala de grises*.

## Compilar en local

Requiere Android SDK y JDK 17: `./gradlew assembleRelease`.
