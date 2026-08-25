# Simulador de Meta Ads Manager

Réplica local del Administrador de Anuncios de Meta con un motor de simulación
propio. Sirve para grabar contenido mostrando campañas "corriendo" sin gastar
un peso ni tocar la API de Meta: **todos los datos se generan en tu navegador**.

```bash
npm install          # desde la raíz del monorepo
npm run dev          # http://localhost:5173
npm test             # tests del motor (las cuentas tienen que cerrar)
npm run build
```

## Cómo se usa para grabar

1. Abrí el **panel de control** con `Ctrl/Cmd + K` (o clic en la esquina inferior
   izquierda). Ahí está todo lo "de simulación": presets, inputs, escenarios.
2. Armá el escenario **fuera de cámara** y guardalo.
3. Cerrá el panel: queda el **modo limpio**, que no muestra ni un control propio.
4. `Espacio` para reproducir el time-lapse del gasto acumulándose.

### Atajos

| Atajo | Qué hace |
| --- | --- |
| `Ctrl/Cmd + K` | Abre/cierra el panel de control |
| `Espacio` | Reproducir / pausar |
| `R` | Reiniciar la reproducción desde el día 1 |
| `L` | Modo limpio on/off (apagado muestra la barra de estudio) |
| `W` | Marca de agua "SIMULACIÓN" |
| `G` | Panel de gráficos |
| `Esc` | Cierra el panel |

La marca de agua viene **prendida por defecto** a propósito.

## Motor de simulación

Todo se deriva de unos pocos inputs por conjunto de anuncios (gasto objetivo,
CPM, CTR, frecuencia, tasa de conversión, ticket promedio) y de ruido
determinístico por semilla. Las identidades que respeta:

```
Impresiones      = (Gasto / CPM) * 1000
Alcance          = Impresiones / Frecuencia
Clics en enlace  = Impresiones * CTR
CPC              = Gasto / Clics
Resultados       = Clics * TasaConversión
Costo por result = Gasto / Resultados        (0 resultados → "—")
Valor conversión = Resultados * Ticket
ROAS             = Valor conversión / Gasto  (sin compras → "—")
```

Las métricas derivadas **nunca se guardan**: se recalculan siempre sobre las
métricas base sumadas, así la fila de una campaña y la de totales cumplen las
mismas identidades que la de un anuncio suelto. Los tests de
`src/engine/__tests__/consistency.test.ts` verifican esto fila por fila.

Detalles que hacen que no se note:

- El gasto diario no es plano: fase de aprendizaje, pico, meseta y fines de
  semana más flojos, con ruido gaussiano por día.
- La frecuencia crece a lo largo del período (el alcance se satura).
- Los anuncios de un conjunto **reparten** al conjunto, pero cada creativo tiene
  su propio CPM, CTR y conversión: dos anuncios nunca muestran el mismo CTR.
- El reparto usa resto mayor, así la suma de las partes da exactamente el total.
- Mismo escenario + misma semilla = mismos números siempre.

### Dónde tocar los números

- `src/config/benchmarks.ts` — CPM, CTR, conversión y ticket por mercado
  (Argentina / Chile / España). **Ajustá esto a los datos de tus cuentas.**
- `src/config/presets.ts` — los cuatro modos rápidos: 🟢 ganadora, 🟡 normal,
  🔴 desastre, 🤡 absurdo (gasto enorme, cero ventas, CPA y ROAS en "—").
- `src/config/columns.ts` — columnas de la tabla y sus etiquetas.
- `src/config/currencies.ts` — formato de moneda (ARS `$1.234.567,89`, CLP, EUR).

## Estructura

```
src/
  engine/      motor puro y testeable (sin React)
    types.ts       modelo de datos
    random.ts      PRNG determinístico, ruido gaussiano, reparto por resto mayor
    metrics.ts     las fórmulas del Ads Manager
    series.ts      serie diaria de un conjunto (curva de gasto + ruido)
    simulate.ts    árbol campaña → conjunto → anuncio + agregados
    dates.ts       fechas en español, sin líos de zona horaria
  config/      benchmarks, presets, columnas, monedas  ← todo lo ajustable
  store/       estado (zustand + localStorage) y selectores de la tabla
  components/  la UI calcada de Meta
  lib/         formato, descargas, reproducción, atajos
```

## Escenarios

Se guardan en `localStorage` con nombre, y se exportan/importan como JSON desde
el panel (**Escenarios guardados**) para versionarlos o pasarlos entre máquinas.
La tabla también se puede exportar a CSV desde *Informes*.

## Aviso

Los datos son simulados. No hay conexión con la API de Meta, no hay backend y no
se gasta dinero real. La marca de agua "SIMULACIÓN" está prendida por defecto.
