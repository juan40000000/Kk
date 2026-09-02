'use strict';

/**
 * Motor de recomendación "Para Ti" — inspirado en el feed algorítmico de TikTok.
 *
 * La idea: no mostramos los posts en orden cronológico, sino rankeados por una
 * combinación de señales. Cada usuario ve un feed distinto que se adapta a lo
 * que le interesa, con algo de exploración para descubrir contenido nuevo.
 *
 * score = (engagement) x (frescura) x (afinidad) x (novedad) + exploración
 *
 *  - engagement: likes, ecos (reposts) y comentarios pesan distinto.
 *  - frescura:   decaimiento temporal tipo "gravedad" (como Hacker News).
 *  - afinidad:   qué tanto se parece el post a lo que el usuario ya consumió
 *                (autores y hashtags con los que interactuó antes).
 *  - novedad:    penaliza lo que el usuario ya vio para no repetir.
 *  - exploración: ruido aleatorio pequeño => descubrimiento / diversidad.
 */

const db = require('./db');

// Pesos de cada tipo de interacción al construir el perfil de afinidad.
const INTERACTION_WEIGHTS = {
  view: 0.4, // vio el post en el feed
  dwell: 1.2, // se quedó mirándolo (tiempo)
  like: 3.0,
  comment: 4.0,
  repost: 5.0,
  open: 2.0, // abrió comentarios / detalle
  profile: 2.5, // visitó el perfil del autor
};

// Pesos de engagement público al calcular el "calor" de un post.
const ENGAGEMENT_WEIGHTS = { like: 3, repost: 5, comment: 4 };

const HOUR = 1000 * 60 * 60;

/**
 * Construye el perfil de gustos de un usuario a partir de su historial de
 * interacciones: mapas de afinidad por autor y por hashtag.
 */
function buildTasteProfile(userId) {
  const { interactions } = db.data;
  const authorAffinity = Object.create(null);
  const tagAffinity = Object.create(null);
  const seenPosts = new Set();

  for (const it of interactions) {
    if (it.userId !== userId) continue;
    const w = INTERACTION_WEIGHTS[it.type] || 0.3;
    // Decaimiento del historial: lo reciente pesa más que lo viejo.
    const ageDays = (Date.now() - it.ts) / (HOUR * 24);
    const recencyBoost = Math.exp(-ageDays / 14); // vida media ~2 semanas
    const contrib = w * recencyBoost;

    if (it.authorId) {
      authorAffinity[it.authorId] = (authorAffinity[it.authorId] || 0) + contrib;
    }
    if (Array.isArray(it.hashtags)) {
      for (const tag of it.hashtags) {
        tagAffinity[tag] = (tagAffinity[tag] || 0) + contrib;
      }
    }
    if (it.type === 'view' || it.type === 'dwell') seenPosts.add(it.postId);
  }

  return { authorAffinity, tagAffinity, seenPosts };
}

function engagementScore(post, counts) {
  const c = counts[post.id] || { like: 0, repost: 0, comment: 0 };
  return (
    1 +
    c.like * ENGAGEMENT_WEIGHTS.like +
    c.repost * ENGAGEMENT_WEIGHTS.repost +
    c.comment * ENGAGEMENT_WEIGHTS.comment
  );
}

function freshnessScore(post) {
  const ageHours = Math.max(0, (Date.now() - post.createdAt) / HOUR);
  // Gravedad: pierde fuerza con el tiempo pero nunca llega a cero de golpe.
  return 1 / Math.pow(ageHours + 2, 1.35);
}

function affinityScore(post, profile, followingSet) {
  let score = 1;

  // Afinidad con el autor.
  const authorAff = profile.authorAffinity[post.authorId] || 0;
  score += Math.log1p(authorAff) * 1.6;

  // Afinidad con los hashtags del post.
  let tagAff = 0;
  for (const tag of post.hashtags || []) {
    tagAff += profile.tagAffinity[tag] || 0;
  }
  score += Math.log1p(tagAff) * 1.3;

  // Pequeño empujón si el usuario ya sigue al autor (pero el "Para Ti"
  // no se limita a lo que sigue: por eso es solo un factor más).
  if (followingSet.has(post.authorId)) score *= 1.25;

  return score;
}

function noveltyScore(post, profile) {
  // Si ya lo vio, lo bajamos fuerte (pero no lo eliminamos del todo).
  return profile.seenPosts.has(post.id) ? 0.25 : 1;
}

/**
 * Precalcula los contadores de engagement por post en una sola pasada.
 */
function computeCounts() {
  const { likes, reposts, comments } = db.data;
  const counts = Object.create(null);
  const bump = (id, k) => {
    if (!counts[id]) counts[id] = { like: 0, repost: 0, comment: 0 };
    counts[id][k] += 1;
  };
  for (const l of likes) bump(l.postId, 'like');
  for (const r of reposts) bump(r.postId, 'repost');
  for (const c of comments) bump(c.postId, 'comment');
  return counts;
}

/**
 * Devuelve los IDs de los posts rankeados para el feed "Para Ti".
 * @returns Array<{post, score, reasons}>
 */
function forYouFeed(userId, { limit = 40 } = {}) {
  const { posts, follows } = db.data;
  const profile = buildTasteProfile(userId);
  const counts = computeCounts();
  const followingSet = new Set(
    follows.filter((f) => f.followerId === userId).map((f) => f.followingId)
  );

  const ranked = posts
    .filter((p) => p.authorId !== userId) // no me muestres mis propios posts en Para Ti
    .map((post) => {
      const eng = engagementScore(post, counts);
      const fresh = freshnessScore(post);
      const aff = affinityScore(post, profile, followingSet);
      const nov = noveltyScore(post, profile);
      const exploration = Math.random() * 0.9; // diversidad / descubrimiento

      const score = eng * fresh * aff * nov + exploration;

      return {
        post,
        score,
        reasons: describeReasons(post, profile, followingSet, counts),
      };
    });

  ranked.sort((a, b) => b.score - a.score);
  return ranked.slice(0, limit);
}

/**
 * Feed cronológico de la gente que sigo ("Siguiendo" / Círculos, estilo G+).
 */
function followingFeed(userId, { limit = 40 } = {}) {
  const { posts, follows } = db.data;
  const followingSet = new Set(
    follows.filter((f) => f.followerId === userId).map((f) => f.followingId)
  );
  followingSet.add(userId); // incluye mis propios posts
  return posts
    .filter((p) => followingSet.has(p.authorId))
    .sort((a, b) => b.createdAt - a.createdAt)
    .slice(0, limit)
    .map((post) => ({ post, score: 0, reasons: [] }));
}

/** Explica por qué un post está en tu feed (transparencia del algoritmo). */
function describeReasons(post, profile, followingSet, counts) {
  const reasons = [];
  if (followingSet.has(post.authorId)) reasons.push('De un círculo tuyo');
  const c = counts[post.id] || { like: 0, repost: 0, comment: 0 };
  if (c.like + c.repost + c.comment >= 5) reasons.push('Popular ahora');
  for (const tag of post.hashtags || []) {
    if ((profile.tagAffinity[tag] || 0) > 1) {
      reasons.push(`Te interesa #${tag}`);
      break;
    }
  }
  if ((profile.authorAffinity[post.authorId] || 0) > 2) {
    reasons.push('Autor que te gusta');
  }
  const ageHours = (Date.now() - post.createdAt) / HOUR;
  if (ageHours < 3) reasons.push('Recién publicado');
  if (reasons.length === 0) reasons.push('Descubrimiento para ti');
  return reasons.slice(0, 2);
}

/**
 * Tendencias: hashtags con más impulso (engagement reciente), para las
 * teselas de "Explorar" (estilo mosaico de Windows Phone).
 */
function trendingHashtags({ limit = 12 } = {}) {
  const { posts } = db.data;
  const counts = computeCounts();
  const tagStats = Object.create(null);

  for (const post of posts) {
    const ageHours = (Date.now() - post.createdAt) / HOUR;
    const recency = Math.exp(-ageHours / 36);
    const c = counts[post.id] || { like: 0, repost: 0, comment: 0 };
    const heat = (1 + c.like * 2 + c.repost * 3 + c.comment * 2) * recency;
    for (const tag of post.hashtags || []) {
      if (!tagStats[tag]) tagStats[tag] = { tag, heat: 0, posts: 0 };
      tagStats[tag].heat += heat;
      tagStats[tag].posts += 1;
    }
  }

  return Object.values(tagStats)
    .sort((a, b) => b.heat - a.heat)
    .slice(0, limit);
}

module.exports = {
  forYouFeed,
  followingFeed,
  trendingHashtags,
  computeCounts,
  buildTasteProfile,
  INTERACTION_WEIGHTS,
};
