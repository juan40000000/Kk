'use strict';

const express = require('express');
const db = require('./db');
const auth = require('./auth');
const algo = require('./algorithm');

const router = express.Router();

const HASHTAG_RE = /#([\p{L}\p{N}_]+)/gu;
const MAX_POST_LEN = 280;

function extractHashtags(text) {
  const tags = new Set();
  let m;
  HASHTAG_RE.lastIndex = 0;
  while ((m = HASHTAG_RE.exec(text)) !== null) {
    tags.add(m[1].toLowerCase());
  }
  return [...tags].slice(0, 8);
}

/** Enriquece un post con datos del autor y contadores/estado para el front. */
function decoratePost(post, viewerId) {
  const author = auth.findUserById(post.authorId);
  const { likes, reposts, comments } = db.data;
  const likeCount = likes.filter((l) => l.postId === post.id).length;
  const repostCount = reposts.filter((r) => r.postId === post.id).length;
  const commentCount = comments.filter((c) => c.postId === post.id).length;
  return {
    ...post,
    author: auth.publicUser(author),
    counts: { like: likeCount, repost: repostCount, comment: commentCount },
    liked: viewerId
      ? likes.some((l) => l.postId === post.id && l.userId === viewerId)
      : false,
    reposted: viewerId
      ? reposts.some((r) => r.postId === post.id && r.userId === viewerId)
      : false,
  };
}

// ---------------------------------------------------------------------------
// Autenticación
// ---------------------------------------------------------------------------

router.post('/register', (req, res) => {
  const { username, displayName, email, password, bio } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ error: 'Usuario y contraseña son obligatorios' });
  }
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(username)) {
    return res.status(400).json({
      error: 'El usuario debe tener 3-20 caracteres (letras, números o _)',
    });
  }
  if (String(password).length < 6) {
    return res.status(400).json({ error: 'La contraseña debe tener al menos 6 caracteres' });
  }
  if (auth.findUserByUsername(username)) {
    return res.status(409).json({ error: 'Ese nombre de usuario ya existe' });
  }
  const user = auth.createUser({ username, displayName, email, password, bio });
  const token = auth.signToken(user);
  res.json({ token, user: auth.publicUser(user) });
});

router.post('/login', (req, res) => {
  const { username, password } = req.body || {};
  const user = auth.findUserByUsername(username);
  if (!user || !auth.verifyPassword(password || '', user.passwordHash)) {
    return res.status(401).json({ error: 'Usuario o contraseña incorrectos' });
  }
  const token = auth.signToken(user);
  res.json({ token, user: auth.publicUser(user) });
});

router.get('/me', auth.requireAuth, (req, res) => {
  const { follows, posts } = db.data;
  const following = follows.filter((f) => f.followerId === req.user.id).length;
  const followers = follows.filter((f) => f.followingId === req.user.id).length;
  const postCount = posts.filter((p) => p.authorId === req.user.id).length;
  res.json({
    user: auth.publicUser(req.user),
    stats: { following, followers, posts: postCount },
  });
});

// ---------------------------------------------------------------------------
// Posts
// ---------------------------------------------------------------------------

router.post('/posts', auth.requireAuth, (req, res) => {
  const { text, tileColor, size, emoji } = req.body || {};
  const clean = String(text || '').trim();
  if (!clean) return res.status(400).json({ error: 'El post no puede estar vacío' });
  if (clean.length > MAX_POST_LEN) {
    return res.status(400).json({ error: `Máximo ${MAX_POST_LEN} caracteres` });
  }
  const post = {
    id: db.nextId('p'),
    authorId: req.user.id,
    text: clean,
    hashtags: extractHashtags(clean),
    tileColor: tileColor || null, // color de la tesela (estilo Metro)
    size: ['small', 'wide', 'large'].includes(size) ? size : 'small',
    emoji: emoji || null,
    createdAt: Date.now(),
  };
  db.data.posts.push(post);
  db.persist();
  res.json({ post: decoratePost(post, req.user.id) });
});

router.get('/posts/:id', auth.optionalAuth, (req, res) => {
  const post = db.data.posts.find((p) => p.id === req.params.id);
  if (!post) return res.status(404).json({ error: 'Post no encontrado' });
  res.json({ post: decoratePost(post, req.user && req.user.id) });
});

router.delete('/posts/:id', auth.requireAuth, (req, res) => {
  const idx = db.data.posts.findIndex((p) => p.id === req.params.id);
  if (idx === -1) return res.status(404).json({ error: 'Post no encontrado' });
  if (db.data.posts[idx].authorId !== req.user.id) {
    return res.status(403).json({ error: 'No puedes borrar este post' });
  }
  const [removed] = db.data.posts.splice(idx, 1);
  db.data.likes = db.data.likes.filter((l) => l.postId !== removed.id);
  db.data.reposts = db.data.reposts.filter((r) => r.postId !== removed.id);
  db.data.comments = db.data.comments.filter((c) => c.postId !== removed.id);
  db.persist();
  res.json({ ok: true });
});

// Like (toggle)
router.post('/posts/:id/like', auth.requireAuth, (req, res) => {
  const post = db.data.posts.find((p) => p.id === req.params.id);
  if (!post) return res.status(404).json({ error: 'Post no encontrado' });
  const { likes } = db.data;
  const existing = likes.findIndex(
    (l) => l.postId === post.id && l.userId === req.user.id
  );
  let liked;
  if (existing >= 0) {
    likes.splice(existing, 1);
    liked = false;
  } else {
    likes.push({
      id: db.nextId('l'),
      postId: post.id,
      userId: req.user.id,
      createdAt: Date.now(),
    });
    liked = true;
    recordInteraction(req.user.id, post, 'like');
  }
  db.persist();
  res.json({ liked, count: likes.filter((l) => l.postId === post.id).length });
});

// Eco / repost (toggle)
router.post('/posts/:id/repost', auth.requireAuth, (req, res) => {
  const post = db.data.posts.find((p) => p.id === req.params.id);
  if (!post) return res.status(404).json({ error: 'Post no encontrado' });
  const { reposts } = db.data;
  const existing = reposts.findIndex(
    (r) => r.postId === post.id && r.userId === req.user.id
  );
  let reposted;
  if (existing >= 0) {
    reposts.splice(existing, 1);
    reposted = false;
  } else {
    reposts.push({
      id: db.nextId('r'),
      postId: post.id,
      userId: req.user.id,
      createdAt: Date.now(),
    });
    reposted = true;
    recordInteraction(req.user.id, post, 'repost');
  }
  db.persist();
  res.json({ reposted, count: reposts.filter((r) => r.postId === post.id).length });
});

// Comentarios
router.get('/posts/:id/comments', auth.optionalAuth, (req, res) => {
  const post = db.data.posts.find((p) => p.id === req.params.id);
  if (!post) return res.status(404).json({ error: 'Post no encontrado' });
  if (req.user) recordInteraction(req.user.id, post, 'open');
  const list = db.data.comments
    .filter((c) => c.postId === post.id)
    .sort((a, b) => a.createdAt - b.createdAt)
    .map((c) => ({ ...c, author: auth.publicUser(auth.findUserById(c.userId)) }));
  res.json({ comments: list });
});

router.post('/posts/:id/comments', auth.requireAuth, (req, res) => {
  const post = db.data.posts.find((p) => p.id === req.params.id);
  if (!post) return res.status(404).json({ error: 'Post no encontrado' });
  const text = String((req.body || {}).text || '').trim();
  if (!text) return res.status(400).json({ error: 'El comentario no puede estar vacío' });
  if (text.length > MAX_POST_LEN) {
    return res.status(400).json({ error: `Máximo ${MAX_POST_LEN} caracteres` });
  }
  const comment = {
    id: db.nextId('c'),
    postId: post.id,
    userId: req.user.id,
    text,
    createdAt: Date.now(),
  };
  db.data.comments.push(comment);
  recordInteraction(req.user.id, post, 'comment');
  db.persist();
  res.json({
    comment: { ...comment, author: auth.publicUser(req.user) },
  });
});

// ---------------------------------------------------------------------------
// Feeds
// ---------------------------------------------------------------------------

router.get('/feed', auth.requireAuth, (req, res) => {
  const type = req.query.type === 'following' ? 'following' : 'foryou';
  const ranked =
    type === 'following'
      ? algo.followingFeed(req.user.id)
      : algo.forYouFeed(req.user.id);
  const items = ranked.map(({ post, reasons }) => ({
    ...decoratePost(post, req.user.id),
    reasons,
  }));
  // Registrar impresiones (el algoritmo aprende de lo que se muestra).
  for (const { post } of ranked) recordInteraction(req.user.id, post, 'view');
  res.json({ type, items });
});

// ---------------------------------------------------------------------------
// Explorar / Tendencias (teselas estilo Windows Phone)
// ---------------------------------------------------------------------------

router.get('/explore', auth.optionalAuth, (req, res) => {
  const trends = algo.trendingHashtags({ limit: 12 });
  const counts = algo.computeCounts();
  // Posts destacados: los de mayor engagement recientes.
  const hot = [...db.data.posts]
    .map((p) => {
      const c = counts[p.id] || { like: 0, repost: 0, comment: 0 };
      const ageH = (Date.now() - p.createdAt) / (1000 * 60 * 60);
      const heat = (1 + c.like * 3 + c.repost * 5 + c.comment * 4) *
        Math.exp(-ageH / 30);
      return { p, heat };
    })
    .sort((a, b) => b.heat - a.heat)
    .slice(0, 15)
    .map(({ p }) => decoratePost(p, req.user && req.user.id));
  res.json({ trends, hot });
});

router.get('/hashtag/:tag', auth.optionalAuth, (req, res) => {
  const tag = String(req.params.tag || '').toLowerCase();
  const posts = db.data.posts
    .filter((p) => (p.hashtags || []).includes(tag))
    .sort((a, b) => b.createdAt - a.createdAt)
    .map((p) => decoratePost(p, req.user && req.user.id));
  res.json({ tag, posts });
});

// ---------------------------------------------------------------------------
// Usuarios / Círculos (seguir, estilo Google+)
// ---------------------------------------------------------------------------

router.get('/users/:username', auth.optionalAuth, (req, res) => {
  const user = auth.findUserByUsername(req.params.username);
  if (!user) return res.status(404).json({ error: 'Usuario no encontrado' });
  const { follows, posts } = db.data;
  const following = follows.filter((f) => f.followerId === user.id).length;
  const followers = follows.filter((f) => f.followingId === user.id).length;
  const userPosts = posts
    .filter((p) => p.authorId === user.id)
    .sort((a, b) => b.createdAt - a.createdAt)
    .map((p) => decoratePost(p, req.user && req.user.id));
  const isFollowing = req.user
    ? follows.some((f) => f.followerId === req.user.id && f.followingId === user.id)
    : false;
  if (req.user && req.user.id !== user.id) {
    recordInteraction(req.user.id, { id: null, authorId: user.id, hashtags: [] }, 'profile');
  }
  res.json({
    user: auth.publicUser(user),
    stats: { following, followers, posts: userPosts.length },
    isFollowing,
    posts: userPosts,
  });
});

router.post('/users/:username/follow', auth.requireAuth, (req, res) => {
  const target = auth.findUserByUsername(req.params.username);
  if (!target) return res.status(404).json({ error: 'Usuario no encontrado' });
  if (target.id === req.user.id) {
    return res.status(400).json({ error: 'No puedes seguirte a ti mismo' });
  }
  const { follows } = db.data;
  const existing = follows.findIndex(
    (f) => f.followerId === req.user.id && f.followingId === target.id
  );
  let following;
  if (existing >= 0) {
    follows.splice(existing, 1);
    following = false;
  } else {
    follows.push({
      id: db.nextId('f'),
      followerId: req.user.id,
      followingId: target.id,
      circle: (req.body && req.body.circle) || 'Amigos',
      createdAt: Date.now(),
    });
    following = true;
  }
  db.persist();
  res.json({ following });
});

// Sugerencias de a quién seguir (gente popular que aún no sigues).
router.get('/suggestions', auth.requireAuth, (req, res) => {
  const { users, follows } = db.data;
  const followingSet = new Set(
    follows.filter((f) => f.followerId === req.user.id).map((f) => f.followingId)
  );
  const suggestions = users
    .filter((u) => u.id !== req.user.id && !followingSet.has(u.id))
    .map((u) => ({
      user: auth.publicUser(u),
      followers: follows.filter((f) => f.followingId === u.id).length,
    }))
    .sort((a, b) => b.followers - a.followers)
    .slice(0, 6);
  res.json({ suggestions });
});

// ---------------------------------------------------------------------------
// Señales del algoritmo (dwell / view desde el cliente)
// ---------------------------------------------------------------------------

router.post('/interactions', auth.requireAuth, (req, res) => {
  const { postId, type } = req.body || {};
  const allowed = ['view', 'dwell', 'open'];
  if (!allowed.includes(type)) return res.status(400).json({ error: 'Tipo inválido' });
  const post = db.data.posts.find((p) => p.id === postId);
  if (!post) return res.status(404).json({ error: 'Post no encontrado' });
  recordInteraction(req.user.id, post, type);
  db.persist();
  res.json({ ok: true });
});

/** Registra una interacción para alimentar el perfil de gustos del usuario. */
function recordInteraction(userId, post, type) {
  db.data.interactions.push({
    userId,
    postId: post.id,
    authorId: post.authorId,
    hashtags: post.hashtags || [],
    type,
    ts: Date.now(),
  });
  // Poda: mantenemos como mucho las últimas 5000 interacciones por rendimiento.
  if (db.data.interactions.length > 5000) {
    db.data.interactions.splice(0, db.data.interactions.length - 5000);
  }
}

module.exports = router;
