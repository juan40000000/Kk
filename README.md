# 🧩 Tesela

Una **red social web estilo Twitter** con una interfaz que mezcla tres mundos:

- 🟦 **Windows Phone / Metro** → teselas planas de colores sólidos, tipografía ligera y *live tiles* que se voltean solos.
- 🔴 **Google+** → feed de tarjetas, columnas limpias y "Círculos" para seguir gente.
- ▶️ **TikTok** → un feed **"Para Ti" algorítmico** que aprende de lo que te gusta, más un **modo inmersivo** vertical con scroll-snap.

> **Tesela** = cada pieza de un mosaico. Cada post es una tesela; tu feed es el mosaico que el algoritmo arma para ti.

![Stack](https://img.shields.io/badge/stack-Node%20%2B%20Express%20%2B%20Vanilla%20JS-1ba1e2)

---

## ✨ Características

| Área | Qué hace |
|------|----------|
| **Feed "Para Ti"** | Rankea posts por engagement × frescura × afinidad × novedad + exploración. Cada usuario ve un feed distinto. |
| **Transparencia** | Cada tesela muestra *por qué* está en tu feed ("Popular ahora", "Te interesa #tech", "De un círculo tuyo"…). |
| **Teselas Metro** | Al publicar puedes darle color y emoji a tu post → aparece como una tesela viva. |
| **Explorar** | Mosaico de tendencias (hashtags) con *live tiles* al estilo Windows Phone. |
| **Círculos (G+)** | Sigue usuarios; el feed "Siguiendo" es cronológico. |
| **Modo TikTok** | Feed inmersivo a pantalla completa con scroll vertical y snap. |
| **Interacciones sociales** | Likes ❤️, ecos 🔁 (reposts), comentarios 💬 y compartir. |
| **Aprendizaje** | El algoritmo registra vistas, permanencia (*dwell*), likes, comentarios y visitas a perfiles para afinar tus recomendaciones. |

---

## 🚀 Cómo ejecutarlo

Requisitos: **Node.js 18+**. No hay dependencias nativas ni paso de *build*.

```bash
# 1. Instalar dependencias
npm install

# 2. (opcional pero recomendado) cargar datos de demostración
npm run seed

# 3. Levantar el servidor
npm start
```

Abre **http://localhost:3000**

### Usuarios de demostración

Tras `npm run seed`, entra con cualquiera de estos usuarios y la contraseña **`demo123`**:

```
@ada  @neo  @sofia  @marco  @luna  @dev_tomas  @valen  @gamer_kev
```

O simplemente crea tu propia cuenta desde la pantalla de inicio.

---

## 🧠 Cómo funciona el algoritmo "Para Ti"

Definido en [`src/algorithm.js`](src/algorithm.js). Para cada candidato:

```
score = engagement × frescura × afinidad × novedad + exploración
```

- **engagement** — likes (×3), ecos (×5) y comentarios (×4).
- **frescura** — decaimiento temporal tipo "gravedad" (como Hacker News): `1 / (edad+2)^1.35`.
- **afinidad** — construida a partir de tu historial: autores y hashtags con los que interactuaste (con decaimiento: lo reciente pesa más).
- **novedad** — penaliza lo que ya viste para no repetir.
- **exploración** — ruido aleatorio pequeño → descubrimiento y diversidad.

Cuanto más usas la app (ves, das like, comentas, visitas perfiles), más se personaliza tu feed. Igual que TikTok, pero con el código a la vista.

---

## 🗂️ Estructura

```
tesela/
├── server.js              # Servidor Express + estáticos
├── seed.js                # Datos de demostración
├── src/
│   ├── db.js              # Almacén JSON en archivo (sin deps nativas)
│   ├── auth.js            # Registro, login, JWT, bcrypt
│   ├── algorithm.js       # Motor de recomendación "Para Ti"
│   └── api.js             # Rutas REST de la API
└── public/
    ├── index.html
    ├── css/style.css      # Sistema visual Metro + G+ + TikTok
    └── js/app.js          # SPA en JavaScript vanilla (sin framework)
```

---

## 🔌 API (resumen)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/register` · `/api/login` | Autenticación (devuelve JWT) |
| `GET`  | `/api/me` | Perfil propio + estadísticas |
| `GET`  | `/api/feed?type=foryou\|following` | Feed algorítmico o cronológico |
| `POST` | `/api/posts` | Crear tesela |
| `POST` | `/api/posts/:id/like` · `/repost` | Like / eco (toggle) |
| `GET`/`POST` | `/api/posts/:id/comments` | Comentarios |
| `GET`  | `/api/explore` | Tendencias + destacados |
| `GET`  | `/api/hashtag/:tag` | Teselas de un hashtag |
| `GET`  | `/api/users/:username` | Perfil público + posts |
| `POST` | `/api/users/:username/follow` | Seguir / dejar de seguir (toggle) |
| `GET`  | `/api/suggestions` | A quién seguir |
| `POST` | `/api/interactions` | Señales del algoritmo (view/dwell/open) |

---

## ⚙️ Notas técnicas

- **Persistencia**: un único archivo `data/tesela.json` (escritura atómica). Elegido a propósito para evitar dependencias nativas y que corra en cualquier entorno con solo `npm install`.
- **Auth**: JWT (30 días) + contraseñas con bcrypt. Configura `JWT_SECRET` por variable de entorno en producción.
- **Sin framework en el front**: SPA en JS vanilla con router por hash, para mantenerlo transparente y sin *build step*.
- **Puerto**: `PORT` (por defecto 3000).

---

Hecho como demostración de que una interfaz puede ser **plana como Metro, social como G+ y adictiva como TikTok** a la vez. 🧩
