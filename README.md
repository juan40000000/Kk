# Notas de voz

App de Android para tomar notas rápidas dictando con la voz, con diseño estilo
Windows Phone (Metro): fondo negro, tipografía ligera y tiles de colores vivos.

## Instalar en el teléfono

1. Abre en el teléfono: https://github.com/juan40000000/Kk/releases/latest/download/NotasVoz.apk
2. Abre el archivo descargado. Si Android lo pide, permite "instalar apps de origen desconocido"
   para tu navegador o gestor de archivos.
3. Listo: busca la app **Notas**.

Cada push compila una versión nueva automáticamente (GitHub Actions → `Construir APK`) y se
instala encima de la anterior sin perder tus notas.

## Qué hace

- **dictar**: toca el micrófono, habla y la nota se guarda al instante como un tile de color.
- **burbuja flotante**: toca "burbuja" en la barra de abajo. Aparece un micrófono que flota
  sobre todas las apps; tócalo, habla y la nota se guarda sola (se pone roja mientras escucha
  y verde al guardar). Arrástralo a cualquier borde, o hasta la X de abajo para quitarlo.
  La primera vez pide permiso para "mostrarse sobre otras apps" y para el micrófono.
- **listas por voz**: di "comprar pan, leche y huevos" (o "lista de tareas…", "pendientes…")
  y se crea una lista con casillas. También sirve decir "coma" entre elementos. En el editor,
  el botón "lista" convierte cualquier nota en lista y al revés.
- **fijar**: mantén pulsado un tile (o usa "fijar" en el editor) y la nota queda arriba, en un
  tile grande con chincheta.
- **live tiles**: los tiles se voltean solos y muestran el progreso de la lista o la fecha.
- **copia de seguridad**: "más" → guardar / restaurar copia. Puedes guardarla en el teléfono o
  en Google Drive; al restaurar se juntan con las notas que ya tienes, sin duplicar.
- **atajo rápido**: mantén pulsado el icono de la app → "Dictar nota".
- toca un tile para editarlo, seguir dictando, cambiar su color o compartirlo.
- mantén pulsado un tile para fijarlo o borrarlo.
- **buscar** entre tus notas y elegir el **color** de énfasis.

El dictado usa el reconocimiento de voz del teléfono (app de Google).
