'use strict';

/**
 * Genera datos de demostración para Tesela:
 * usuarios, teselas (posts), likes, ecos, comentarios, seguimientos e
 * interacciones — suficiente para que el feed "Para Ti" tenga con qué trabajar.
 *
 *   npm run seed
 *
 * Todos los usuarios tienen la contraseña: demo123
 */

const db = require('./src/db');
const auth = require('./src/auth');

const PASSWORD = 'demo123';

const USERS = [
  ['ada', 'Ada Lovelace', 'La primera programadora. Vivo entre algoritmos y máquinas analíticas.'],
  ['neo', 'Neo Metric', 'Diseñador de teselas. Amo el flat design y los colores sólidos.'],
  ['sofia', 'Sofía Ríos', 'Fotógrafa urbana 📷 Buscando la luz perfecta en cada esquina.'],
  ['marco', 'Marco Polo', 'Viajero digital 🌍 Cuento historias de lugares que quizá inventé.'],
  ['luna', 'Luna Cyan', 'Música electrónica 🎵 synthwave, vaporwave y todo lo -wave.'],
  ['dev_tomas', 'Tomás Dev', 'Full-stack. JavaScript de día, Rust de noche. Café siempre.'],
  ['valen', 'Valentina Sol', 'Ilustradora ✨ pixeles con alma. Comisiones abiertas.'],
  ['gamer_kev', 'Kevin GG', 'Speedrunner 🎮 retro y competitivo. WR o nada.'],
];

const POSTS = [
  ['neo', 'El flat design nunca murió, solo estaba esperando volver. #diseño #metro', '#0050ef', '🧩'],
  ['neo', 'Las teselas vivas de Windows Phone eran el futuro y nadie lo entendió. Cambio de opinión.', null, null],
  ['ada', 'Un algoritmo no es magia: es una receta. La diferencia es a quién dejas cocinar. #tech #algoritmos', null, null],
  ['ada', 'Escribiendo notas sobre la máquina analítica. 180 años después seguimos con las mismas ideas. #historia', '#6a00ff', '💡'],
  ['sofia', 'La ciudad a las 6am es otra ciudad. #fotografia #urbano', '#f0a30a', '📷'],
  ['sofia', 'Regla no escrita: la mejor foto siempre está una cuadra más allá. #fotografia', null, null],
  ['marco', 'Perdido en un mercado que no aparece en ningún mapa. 10/10 recomendado. #viajes', '#008a00', '🌆'],
  ['marco', 'Consejo de viaje: aprende a decir "gracias" y "otra ronda" en cada idioma. #viajes #tips', null, null],
  ['luna', 'Nuevo set de synthwave subido. Neón, bajos y nostalgia del futuro. #musica #synthwave', '#d80073', '🎵'],
  ['luna', 'El silencio también es parte de la canción. #musica', null, null],
  ['dev_tomas', 'Hoy aprendí que el mejor código es el que no tienes que escribir. #dev #programacion', '#00aba9', '⚡'],
  ['dev_tomas', 'Debuggear es como ser detective en una película donde también eres el asesino. #dev #humor', null, null],
  ['dev_tomas', 'Hot take: los feeds algorítmicos son solo grafos de afinidad con marketing. #algoritmos #tech', '#1ba1e2', '🚀'],
  ['valen', 'Terminé una ilustración pixel art de una ciudad flotante. Se siente como un sueño. #arte #pixelart', '#aa00ff', '✨'],
  ['valen', 'El pixel art enseña algo valioso: cada punto cuenta. #arte', null, null],
  ['gamer_kev', 'NUEVO RÉCORD PERSONAL en el nivel 1-1. Las manos me tiemblan. #gaming #speedrun', '#e51400', '🎮'],
  ['gamer_kev', '¿Retro o moderno? Los dos. Pero el retro no te sostiene la mano. #gaming', null, null],
  ['ada', 'Pregunta honesta: ¿un feed que aprende de ti te libera o te encierra? #tech #algoritmos', null, null],
  ['neo', 'Diseñé un mosaico donde cada tesela es un pensamiento. Se llama Tesela. #diseño #metro', '#60a917', '🧩'],
  ['sofia', 'Colores sólidos, sombras honestas. Así debería ser todo. #diseño #fotografia', null, null],
  ['luna', 'Colaboración sorpresa con Valen: música + pixel art. Se viene algo. #arte #musica', '#76608a', '🌊'],
  ['marco', 'La mejor red social es la que te hace salir a la calle. Ironía intencional. #viajes', null, null],
];

// (autor, sobre-post-índice, texto)
const COMMENTS = [
  ['ada', 0, '¡Totalmente de acuerdo! El minimalismo funcional es atemporal.'],
  ['sofia', 0, 'Los colores sólidos fotografían increíble además.'],
  ['dev_tomas', 2, 'Esta frase debería estar en la entrada de todas las oficinas.'],
  ['neo', 4, 'Esa luz dorada 😍 ¿qué cámara usaste?'],
  ['luna', 8, '🔥🔥 ya lo estoy escuchando en repeat'],
  ['valen', 12, 'Grafos de afinidad con buen marketing, jajaja exacto.'],
  ['gamer_kev', 12, 'Como todo en la vida honestamente.'],
  ['marco', 13, 'Una ciudad flotante es literalmente mi sueño recurrente.'],
  ['ada', 18, 'El nombre es perfecto. Cada tesela, una idea.'],
  ['dev_tomas', 18, 'Ok esto lo tengo que usar.'],
];

// quién sigue a quién
const FOLLOWS = [
  ['neo', 'ada'], ['neo', 'sofia'], ['neo', 'valen'],
  ['ada', 'dev_tomas'], ['ada', 'neo'],
  ['sofia', 'marco'], ['sofia', 'valen'], ['sofia', 'luna'],
  ['marco', 'sofia'], ['marco', 'luna'],
  ['luna', 'valen'], ['luna', 'sofia'],
  ['dev_tomas', 'ada'], ['dev_tomas', 'neo'], ['dev_tomas', 'gamer_kev'],
  ['valen', 'luna'], ['valen', 'neo'],
  ['gamer_kev', 'dev_tomas'], ['gamer_kev', 'marco'],
];

function pick(arr, n) {
  return [...arr].sort(() => Math.random() - 0.5).slice(0, n);
}

function run() {
  // Reset total
  db._replaceState({});

  const byName = {};
  for (const [username, displayName, bio] of USERS) {
    byName[username] = auth.createUser({
      username,
      displayName,
      password: PASSWORD,
      bio,
    });
  }

  const HASHTAG_RE = /#([\p{L}\p{N}_]+)/gu;
  const postRefs = [];
  // Escalonamos las fechas para que "frescura" tenga variedad.
  let clock = Date.now() - 1000 * 60 * 60 * 40; // hace 40h
  for (const [username, text, color, emoji] of POSTS) {
    const hashtags = [];
    let m;
    HASHTAG_RE.lastIndex = 0;
    while ((m = HASHTAG_RE.exec(text)) !== null) hashtags.push(m[1].toLowerCase());
    clock += 1000 * 60 * (60 + Math.random() * 90); // avanza 1-2.5h
    const post = {
      id: db.nextId('p'),
      authorId: byName[username].id,
      text,
      hashtags: [...new Set(hashtags)],
      tileColor: color,
      size: color ? (text.length > 120 ? 'wide' : 'small') : 'small',
      emoji: emoji || null,
      createdAt: Math.min(clock, Date.now() - 1000 * 60 * 5),
    };
    db.data.posts.push(post);
    postRefs.push(post);
  }

  // Comentarios
  for (const [username, idx, text] of COMMENTS) {
    const post = postRefs[idx];
    if (!post) continue;
    db.data.comments.push({
      id: db.nextId('c'),
      postId: post.id,
      userId: byName[username].id,
      text,
      createdAt: post.createdAt + 1000 * 60 * (10 + Math.random() * 200),
    });
  }

  // Follows
  for (const [a, b] of FOLLOWS) {
    db.data.follows.push({
      id: db.nextId('f'),
      followerId: byName[a].id,
      followingId: byName[b].id,
      circle: 'Amigos',
      createdAt: Date.now(),
    });
  }

  // Likes y ecos aleatorios (distribución sesgada => unos posts "calientes").
  const userIds = Object.values(byName).map((u) => u.id);
  for (const post of postRefs) {
    const popularity = Math.random();
    const likers = pick(
      userIds.filter((id) => id !== post.authorId),
      Math.floor(popularity * userIds.length)
    );
    for (const uid of likers) {
      db.data.likes.push({
        id: db.nextId('l'),
        postId: post.id,
        userId: uid,
        createdAt: post.createdAt + 1000 * 60 * Math.random() * 300,
      });
      // Interacción para alimentar el algoritmo.
      db.data.interactions.push({
        userId: uid,
        postId: post.id,
        authorId: post.authorId,
        hashtags: post.hashtags,
        type: 'like',
        ts: post.createdAt + 1000 * 60 * Math.random() * 300,
      });
    }
    if (popularity > 0.6) {
      const reposters = pick(
        userIds.filter((id) => id !== post.authorId),
        Math.floor(popularity * 3)
      );
      for (const uid of reposters) {
        db.data.reposts.push({
          id: db.nextId('r'),
          postId: post.id,
          userId: uid,
          createdAt: post.createdAt + 1000 * 60 * Math.random() * 300,
        });
      }
    }
  }

  db.flushNow();

  console.log('✓ Datos de demostración generados:');
  console.log(`  · ${db.data.users.length} usuarios`);
  console.log(`  · ${db.data.posts.length} teselas`);
  console.log(`  · ${db.data.comments.length} comentarios`);
  console.log(`  · ${db.data.likes.length} likes, ${db.data.reposts.length} ecos`);
  console.log(`  · ${db.data.follows.length} seguimientos`);
  console.log('');
  console.log('  Entra con cualquier usuario y contraseña "demo123":');
  console.log('  ' + USERS.map((u) => '@' + u[0]).join(', '));
  console.log('');
}

run();
