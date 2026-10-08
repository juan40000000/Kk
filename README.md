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

- **Inicio**: arriba, fijos y en la misma fila, el **reloj** y el **clima**, los dos animados
  en pixel art de 8 bits: el reloj con dígitos de 8 bits, dos puntos que parpadean y barra
  de segundos; el clima con sol, luna y estrellas, nubes, lluvia, nieve, tormenta o niebla.
- **Mosaicos**: todos muestran el nombre de la app, también los pequeños. Mantén pulsado un mosaico y **desliza** hacia la derecha/abajo para agrandarlo
  o hacia la izquierda/arriba para achicarlo (pequeño, mediano, ancho, grande), como en
  Windows Phone. Si lo mantienes pulsado y sueltas sin deslizar, sale el menú: **color
  blanco/negro**, tamaño, bloquear, pausa, mover o desanclar. Al anclar desde la lista
  también eliges si el mosaico es blanco o negro.
- **Mosaicos dinámicos**: los mosaicos medianos o más grandes giran cada pocos segundos
  y muestran información de la app: tiempo de uso hoy y veces que la abriste, próxima
  alarma (app de reloj) o batería (app de ajustes). Para el tiempo de uso hay que dar
  acceso en `• • •` → *tiempo de uso en mosaicos*. No lee notificaciones: Google Play
  Protect bloquea las APK instaladas a mano que piden ese permiso.
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

## Calamuchita (navegador)

Navegador incluido en la misma APK, con el **motor de Firefox** (GeckoView). Aparece como app
propia ("Calamuchita") y la primera vez se ancla en inicio como mosaico ancho negro.

- Barra de direcciones **abajo**, como Safari: muestra solo el dominio; tócala para buscar
  o escribir una dirección (DuckDuckGo o Google, se cambia en `•••`).
- **Pestañas en mosaico**: la actual en blanco y las demás en negro; ✕ para cerrar.
- **Página de inicio** minimalista con **favoritos en mosaico** (blancos o negros; mantén
  pulsado para cambiar el color o eliminar).
- Puede ser el navegador predeterminado del teléfono (abre los enlaces de otras apps).
- Solo para teléfonos de 64 bits (arm64) y Android 8 o superior.

## Anti-adicción

- Todo en blanco y negro, sin iconos de colores, sin globos de notificación.
- **Pausa**: marca apps como distractoras (salen en gris). Al abrirlas aparece
  "respira." con una cuenta atrás (5–60 s, configurable) y la pregunta
  *¿es una decisión o un impulso?*. Si eliges *mejor no* cuenta como impulso evitado.
- **Bloqueo por tiempo**: bloquea una app 15 min, 30 min, 1 h, 2 h, 4 h, 8 h o hasta
  mañana. Si la quieres desbloquear antes, tienes que esperar mirando la pantalla el tiempo
  configurado (1 minuto por defecto, de 30 s a 30 min en ajustes); si sales, se reinicia.
  Nota: el bloqueo funciona desde el launcher; no impide abrirla desde una notificación.
- **Apps ocultas**: no aparecen en la lista; solo si escribes 3+ letras de su nombre.
- **Contador diario** en el reloj: aperturas de hoy e impulsos evitados.

Consejo: para todo el teléfono en gris, activa *Bienestar digital → Modo descanso →
escala de grises* o *Accesibilidad → Corrección de color → Escala de grises*.

## Compilar en local

Requiere Android SDK y JDK 17: `./gradlew assembleRelease`.
