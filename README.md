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
- **atajo rápido**: mantén pulsado el icono de la app → "Dictar nota".
- toca un tile para editarlo, seguir dictando, cambiar su color o compartirlo.
- mantén pulsado un tile para borrarlo.
- **buscar** entre tus notas y elegir el **color** de énfasis.

El dictado usa el reconocimiento de voz del teléfono (app de Google).
