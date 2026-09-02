// =========================================================================
// TESELA — SPA cliente
// =========================================================================

const TILE_COLORS = [
  '#e51400', '#a20025', '#f0a30a', '#e3c800', '#60a917',
  '#008a00', '#00aba9', '#1ba1e2', '#0050ef', '#6a00ff',
  '#aa00ff', '#d80073', '#76608a', '#647687',
];
const EMOJIS = ['🔥', '💡', '🚀', '🎮', '🎵', '📷', '⚡', '🌆', '☕', '🧩', '✨', '🌊'];

// ------------------------------ Estado ------------------------------
const store = {
  token: localStorage.getItem('tesela_token') || null,
  me: null,
  route: { name: 'feed', param: null },
  feedType: 'foryou',
  compose: { color: null, emoji: null, size: 'small' },
  currentList: [],
};

// ------------------------------ API ------------------------------
async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (store.token) headers.Authorization = 'Bearer ' + store.token;
  const res = await fetch('/api' + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || 'Error del servidor');
  return data;
}

// ------------------------------ Utilidades ------------------------------
const $ = (sel, root = document) => root.querySelector(sel);
const app = () => document.getElementById('app');

function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function initials(name) {
  return String(name || '?')
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase();
}

function timeAgo(ts) {
  const s = Math.floor((Date.now() - ts) / 1000);
  if (s < 60) return 'ahora';
  const m = Math.floor(s / 60);
  if (m < 60) return m + 'm';
  const h = Math.floor(m / 60);
  if (h < 24) return h + 'h';
  const d = Math.floor(h / 24);
  if (d < 7) return d + 'd';
  return new Date(ts).toLocaleDateString('es', { day: 'numeric', month: 'short' });
}

function linkify(text) {
  return esc(text).replace(/#([\p{L}\p{N}_]+)/gu, (m, tag) => {
    return `<span class="h" data-hashtag="${esc(tag.toLowerCase())}">#${esc(tag)}</span>`;
  });
}

function toast(msg, isErr = false) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = 'toast' + (isErr ? ' err' : '');
  el.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => (el.hidden = true), 2600);
}

function colorFor(seed) {
  let h = 0;
  for (let i = 0; i < String(seed).length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return TILE_COLORS[h % TILE_COLORS.length];
}

function avatar(user, cls = '') {
  const color = (user && user.avatarColor) || colorFor((user && user.username) || '?');
  return `<div class="avatar ${cls}" style="background:${color}">${esc(
    initials(user && user.displayName)
  )}</div>`;
}

// Set de iconos de línea (estilo Lucide) embebidos como SVG: sin
// dependencias de red, consistentes y nítidos en cualquier pantalla.
const ICONS = {
  home: '<path d="M3 10.6 12 3l9 7.6"/><path d="M5.5 9.4V21h13V9.4"/>',
  grid: '<rect x="3" y="3" width="7.5" height="7.5" rx="1.2"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="1.2"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="1.2"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1.2"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 20.5c0-4.2 3.6-6.5 7.5-6.5s7.5 2.3 7.5 6.5"/>',
  heart: '<path d="M12 20.3S3.6 14.6 3.6 8.9A4.3 4.3 0 0 1 12 6.1a4.3 4.3 0 0 1 8.4 2.8c0 5.7-8.4 11.4-8.4 11.4Z"/>',
  repeat: '<path d="M17 1.5 21 5.5 17 9.5"/><path d="M3 11.5v-1a4 4 0 0 1 4-4h14"/><path d="M7 22.5 3 18.5 7 14.5"/><path d="M21 12.5v1a4 4 0 0 1-4 4H3"/>',
  message: '<path d="M20.5 15.5a2 2 0 0 1-2 2H8l-4.5 4V5.5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2Z"/>',
  share: '<circle cx="18" cy="5" r="2.6"/><circle cx="6" cy="12" r="2.6"/><circle cx="18" cy="19" r="2.6"/><path d="M8.4 13.4 15.6 17.6"/><path d="M15.6 6.4 8.4 10.6"/>',
  power: '<path d="M12 2.5v9"/><path d="M6.6 6.6a8 8 0 1 0 10.8 0"/>',
  close: '<path d="M5.5 5.5 18.5 18.5"/><path d="M18.5 5.5 5.5 18.5"/>',
};
function icon(name, cls = '') {
  return `<svg class="icn ${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name] || ''}</svg>`;
}

// ------------------------------ Router ------------------------------
function navigate(name, param = null) {
  store.route = { name, param };
  const hash = param ? `#/${name}/${param}` : `#/${name}`;
  if (location.hash !== hash) location.hash = hash;
  render();
}

function parseHash() {
  const raw = location.hash.replace(/^#\/?/, '');
  const [name = 'feed', param = null] = raw.split('/');
  store.route = { name: name || 'feed', param: param ? decodeURIComponent(param) : null };
}

window.addEventListener('hashchange', () => {
  parseHash();
  render();
});

// =========================================================================
// RENDER PRINCIPAL
// =========================================================================
async function render() {
  if (!store.token || !store.me) {
    renderAuth();
    return;
  }
  const { name } = store.route;
  app().innerHTML = shell(bodyFor(name));
  wireShell();

  if (name === 'feed') loadFeed();
  else if (name === 'explore') loadExplore();
  else if (name === 'profile') loadProfile(store.route.param || store.me.username);
  else if (name === 'hashtag') loadHashtag(store.route.param);
  else loadFeed();

  loadRail();
}

function bodyFor(name) {
  if (name === 'explore') {
    return `
      <div class="topbar"><h2>explorar</h2></div>
      <div id="content" class="content"><div class="loading"><div class="spinner"></div>cargando teselas…</div></div>`;
  }
  if (name === 'profile') {
    return `<div id="content" class="content"><div class="loading"><div class="spinner"></div></div></div>`;
  }
  if (name === 'hashtag') {
    return `
      <div class="topbar"><h2>#${esc(store.route.param || '')}</h2></div>
      <div id="content" class="content"><div class="loading"><div class="spinner"></div></div></div>`;
  }
  // feed
  return `
    <div class="topbar">
      <h2>tesela</h2>
      <div class="tabs">
        <button data-feed="foryou" class="${store.feedType === 'foryou' ? 'is-active' : ''}">para ti</button>
        <button data-feed="following" class="${store.feedType === 'following' ? 'is-active' : ''}">siguiendo</button>
      </div>
    </div>
    ${composeBox()}
    <div id="content" class="content"><div class="loading"><div class="spinner"></div>armando tu feed…</div></div>`;
}

// ------------------------------ Shell ------------------------------
function shell(inner) {
  const r = store.route.name;
  const item = (name, ic, label) =>
    `<a class="nav__item ${r === name ? 'is-active' : ''}" data-nav="${name}"><span class="ic">${ic}</span>${label}</a>`;
  return `
  <div class="shell">
    <aside class="nav">
      <div class="nav__brand">tes<b>ela</b></div>
      ${item('feed', icon('home'), 'Inicio')}
      ${item('explore', icon('grid'), 'Explorar')}
      <a class="nav__item" data-nav="profile"><span class="ic">${icon('user')}</span>Mi perfil</a>
      <div class="nav__spacer"></div>
      <div class="nav__me">
        ${avatar(store.me, 'avatar--sm')}
        <div style="flex:1;min-width:0">
          <div style="font-weight:600;font-size:14px">${esc(store.me.displayName)}</div>
          <small>@${esc(store.me.username)}</small>
        </div>
        <button class="close-x" id="logout" title="Salir" aria-label="Salir">${icon('power')}</button>
      </div>
    </aside>

    <main class="main">${inner}</main>

    <aside class="rail" id="rail"></aside>
  </div>

  <nav class="tabbar"><div class="tabbar__inner">
    <button data-nav="feed" class="${r === 'feed' ? 'is-active' : ''}">${icon('home')}<small>inicio</small></button>
    <button data-nav="explore" class="${r === 'explore' ? 'is-active' : ''}">${icon('grid')}<small>explorar</small></button>
    <button data-nav="profile" class="${r === 'profile' ? 'is-active' : ''}">${icon('user')}<small>perfil</small></button>
  </div></nav>`;
}

function wireShell() {
  document.querySelectorAll('[data-nav]').forEach((el) => {
    el.addEventListener('click', () => navigate(el.dataset.nav));
  });
  const logout = $('#logout');
  if (logout) logout.addEventListener('click', doLogout);

  document.querySelectorAll('[data-feed]').forEach((btn) => {
    btn.addEventListener('click', () => {
      store.feedType = btn.dataset.feed;
      document.querySelectorAll('[data-feed]').forEach((b) =>
        b.classList.toggle('is-active', b.dataset.feed === store.feedType)
      );
      loadFeed();
    });
  });
}

// =========================================================================
// COMPOSE
// =========================================================================
function composeBox() {
  const sw = TILE_COLORS.slice(0, 8)
    .map(
      (c) =>
        `<button class="swatch" style="background:${c}" data-color="${c}" title="tesela ${c}"></button>`
    )
    .join('');
  const em = EMOJIS.map((e) => `<button data-emoji="${e}">${e}</button>`).join('');
  return `
  <div class="compose">
    ${avatar(store.me)}
    <div class="compose__body">
      <textarea id="compose-text" maxlength="280" placeholder="¿Qué está pasando? Usa #hashtags…"></textarea>
      <div class="compose__tools">
        <div class="swatches" id="swatches">
          <button class="swatch is-active" style="background:#1c1c1c;border-color:#444" data-color="" title="sin tesela"></button>
          ${sw}
        </div>
        <div class="emoji-pick" id="emojis">${em}</div>
        <div class="compose__spacer"></div>
        <span class="compose__count" id="cc">280</span>
        <button class="btn btn--sm" id="post-btn">publicar</button>
      </div>
    </div>
  </div>`;
}

function wireCompose() {
  const ta = $('#compose-text');
  const cc = $('#cc');
  const btn = $('#post-btn');
  if (!ta) return;
  store.compose = { color: null, emoji: null, size: 'small' };

  ta.addEventListener('input', () => {
    const left = 280 - ta.value.length;
    cc.textContent = left;
    cc.className = 'compose__count' + (left < 0 ? ' over' : left < 30 ? ' warn' : '');
    ta.style.height = 'auto';
    ta.style.height = Math.min(ta.scrollHeight, 220) + 'px';
  });

  $('#swatches').addEventListener('click', (e) => {
    const b = e.target.closest('[data-color]');
    if (!b) return;
    store.compose.color = b.dataset.color || null;
    document.querySelectorAll('#swatches .swatch').forEach((s) => s.classList.remove('is-active'));
    b.classList.add('is-active');
  });
  $('#emojis').addEventListener('click', (e) => {
    const b = e.target.closest('[data-emoji]');
    if (!b) return;
    const on = store.compose.emoji === b.dataset.emoji;
    store.compose.emoji = on ? null : b.dataset.emoji;
    document.querySelectorAll('#emojis button').forEach((s) => (s.style.background = ''));
    if (!on) b.style.background = 'var(--surface-2)';
  });

  btn.addEventListener('click', submitPost);
}

async function submitPost() {
  const ta = $('#compose-text');
  const text = ta.value.trim();
  if (!text) return toast('Escribe algo primero', true);
  if (text.length > 280) return toast('Máximo 280 caracteres', true);
  const btn = $('#post-btn');
  btn.disabled = true;
  try {
    const size = store.compose.color ? (text.length > 120 ? 'wide' : 'small') : 'small';
    await api('/posts', {
      method: 'POST',
      body: {
        text,
        tileColor: store.compose.color,
        emoji: store.compose.emoji,
        size,
      },
    });
    ta.value = '';
    $('#cc').textContent = '280';
    ta.style.height = 'auto';
    toast('¡Publicado! ✨');
    if (store.route.name === 'feed') loadFeed();
  } catch (err) {
    toast(err.message, true);
  } finally {
    btn.disabled = false;
  }
}

// =========================================================================
// FEED
// =========================================================================
async function loadFeed() {
  wireCompose();
  const c = $('#content');
  if (!c) return;
  c.innerHTML = `<div class="loading"><div class="spinner"></div>armando tu feed…</div>`;
  try {
    const { items } = await api('/feed?type=' + store.feedType);
    if (!items.length) {
      c.innerHTML = emptyState(
        store.feedType === 'following' ? '🙋' : '✨',
        store.feedType === 'following'
          ? 'Tu círculo está en silencio'
          : 'Aún no hay nada para ti',
        store.feedType === 'following'
          ? 'Sigue a más gente en Explorar para llenar este feed.'
          : 'Publica algo o explora tendencias para entrenar tu algoritmo.'
      );
      return;
    }
    store.currentList = items;
    c.innerHTML = items.map(postCard).join('');
    wirePosts(c);
    observeImpressions(c);
  } catch (err) {
    c.innerHTML = emptyState('⚠️', 'No se pudo cargar', err.message);
  }
}

function postCard(p) {
  const reasons =
    p.reasons && p.reasons.length
      ? `<div class="post__reasons">${p.reasons
          .map((r, i) => `<span class="chip ${i === 0 ? 'chip--accent' : ''}">${esc(r)}</span>`)
          .join('')}</div>`
      : '';
  const banner = p.tileColor
    ? `<div class="post__banner" style="background:${esc(p.tileColor)}">
         ${p.emoji ? `<span class="emoji">${esc(p.emoji)}</span>` : ''}
         ${linkify(p.text)}
       </div>`
    : `<div class="post__text">${linkify(p.text)}</div>`;
  return `
  <article class="post" data-post="${p.id}">
    <a data-user="${esc(p.author.username)}">${avatar(p.author)}</a>
    <div class="post__body">
      <div class="post__head">
        <span class="post__name" data-user="${esc(p.author.username)}" style="cursor:pointer">${esc(p.author.displayName)}</span>
        <span class="post__handle" data-user="${esc(p.author.username)}" style="cursor:pointer">@${esc(p.author.username)}</span>
        <span class="post__time">· ${timeAgo(p.createdAt)}</span>
      </div>
      ${reasons}
      ${p.tileColor ? banner : `<div class="post__text">${linkify(p.text)}</div>`}
      <div class="post__actions">
        <button class="act like ${p.liked ? 'is-on' : ''}" data-act="like" aria-label="Me gusta"><span class="ic">${icon('heart')}</span><span class="n">${p.counts.like}</span></button>
        <button class="act repost ${p.reposted ? 'is-on' : ''}" data-act="repost" aria-label="Eco"><span class="ic">${icon('repeat')}</span><span class="n">${p.counts.repost}</span></button>
        <button class="act comment" data-act="comment" aria-label="Comentar"><span class="ic">${icon('message')}</span><span class="n">${p.counts.comment}</span></button>
        <button class="act" data-act="share" aria-label="Compartir"><span class="ic">${icon('share')}</span></button>
      </div>
    </div>
  </article>`;
}

function wirePosts(root) {
  root.querySelectorAll('[data-user]').forEach((el) => {
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      navigate('profile', el.dataset.user);
    });
  });
  root.querySelectorAll('[data-hashtag]').forEach((el) => {
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      navigate('hashtag', el.dataset.hashtag);
    });
  });
  root.querySelectorAll('.post').forEach((card) => {
    const id = card.dataset.post;
    card.querySelectorAll('[data-act]').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        handleAction(btn.dataset.act, id, btn, card);
      });
    });
    // Tocar la tesela abre el modo inmersivo (TikTok) a partir de ese post.
    card.style.cursor = 'pointer';
    card.addEventListener('click', () => openImmersive(store.currentList, id));
  });
}

async function handleAction(act, id, btn, card) {
  try {
    if (act === 'like') {
      const { liked, count } = await api(`/posts/${id}/like`, { method: 'POST' });
      btn.classList.toggle('is-on', liked);
      btn.querySelector('.n').textContent = count;
    } else if (act === 'repost') {
      const { reposted, count } = await api(`/posts/${id}/repost`, { method: 'POST' });
      btn.classList.toggle('is-on', reposted);
      btn.querySelector('.n').textContent = count;
      toast(reposted ? 'Eco publicado 🔁' : 'Eco retirado');
    } else if (act === 'comment') {
      openComments(id);
    } else if (act === 'share') {
      const url = location.origin + '/#/post/' + id;
      try {
        await navigator.clipboard.writeText(url);
        toast('Enlace copiado 📋');
      } catch {
        toast(url);
      }
    }
  } catch (err) {
    toast(err.message, true);
  }
}

// Impresiones (dwell) para alimentar el algoritmo — estilo TikTok.
function observeImpressions(root) {
  if (!('IntersectionObserver' in window)) return;
  const seen = new Set();
  const timers = new Map();
  const io = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        const id = e.target.dataset.post;
        if (e.isIntersecting) {
          if (!timers.has(id)) {
            timers.set(
              id,
              setTimeout(() => {
                if (!seen.has(id)) {
                  seen.add(id);
                  api('/interactions', { method: 'POST', body: { postId: id, type: 'dwell' } }).catch(() => {});
                }
              }, 1500)
            );
          }
        } else {
          clearTimeout(timers.get(id));
          timers.delete(id);
        }
      }
    },
    { threshold: 0.6 }
  );
  root.querySelectorAll('.post').forEach((el) => io.observe(el));
}

// =========================================================================
// EXPLORAR — teselas Metro
// =========================================================================
async function loadExplore() {
  const c = $('#content');
  try {
    const { trends, hot } = await api('/explore');
    const sizes = ['large', 'small', 'wide', 'small', 'small', 'wide', 'small', 'large', 'small', 'small', 'wide', 'small'];
    const tiles = trends
      .map((t, i) => {
        const color = TILE_COLORS[i % TILE_COLORS.length];
        const size = sizes[i % sizes.length];
        const glyph = EMOJIS[i % EMOJIS.length];
        return `
        <button class="tile tile--${size}" data-hashtag="${esc(t.tag)}" style="background:${color}">
          <span class="tile__kicker">tendencia</span>
          <span class="tile__glyph">${glyph}</span>
          <div class="tile__flip">
            <div class="tile__title">#${esc(t.tag)}</div>
            <div class="tile__meta">${t.posts} ${t.posts === 1 ? 'tesela' : 'teselas'}</div>
          </div>
        </button>`;
      })
      .join('');

    const hotHtml = hot.length
      ? hot.map(postCard).join('')
      : emptyState('🧩', 'Sin destacados todavía', 'Sé el primero en publicar algo.');

    store.currentList = hot;
    c.innerHTML = `
      <div class="tiles">${tiles || ''}</div>
      <div class="section-head"><span class="metro-kicker">lo más caliente</span></div>
      <div>${hotHtml}</div>`;

    c.querySelectorAll('[data-hashtag]').forEach((el) =>
      el.addEventListener('click', () => navigate('hashtag', el.dataset.hashtag))
    );
    wirePosts(c);
    animateTiles(c);
  } catch (err) {
    c.innerHTML = emptyState('⚠️', 'No se pudo cargar', err.message);
  }
}

// Live tiles: voltean su contenido periódicamente (efecto Windows Phone).
function animateTiles(root) {
  const tiles = [...root.querySelectorAll('.tile')];
  if (!tiles.length) return;
  clearInterval(animateTiles._i);
  animateTiles._i = setInterval(() => {
    const t = tiles[Math.floor(Math.random() * tiles.length)];
    if (!document.body.contains(t)) return clearInterval(animateTiles._i);
    t.classList.add('flipping');
    setTimeout(() => t.classList.remove('flipping'), 700);
  }, 2200);
}

// =========================================================================
// HASHTAG
// =========================================================================
async function loadHashtag(tag) {
  const c = $('#content');
  try {
    const { posts } = await api('/hashtag/' + encodeURIComponent(tag));
    store.currentList = posts;
    c.innerHTML = posts.length
      ? posts.map(postCard).join('')
      : emptyState('🔍', 'Sin teselas con #' + esc(tag), 'Sé el primero en usar este hashtag.');
    wirePosts(c);
  } catch (err) {
    c.innerHTML = emptyState('⚠️', 'Error', err.message);
  }
}

// =========================================================================
// PERFIL
// =========================================================================
async function loadProfile(username) {
  const c = $('#content');
  try {
    const data = await api('/users/' + encodeURIComponent(username));
    const u = data.user;
    const isMe = u.id === store.me.id;
    const color = u.avatarColor || colorFor(u.username);
    store.currentList = data.posts;
    c.innerHTML = `
      <div class="topbar"><h2>${esc(u.displayName)}</h2></div>
      <div class="profile__cover" style="background:linear-gradient(135deg, ${color}, ${colorFor(u.displayName)})"></div>
      <div class="profile__info">
        ${avatar(u, 'avatar--lg')}
        <div class="profile__meta">
          <h2>${esc(u.displayName)}</h2>
          <div class="handle">@${esc(u.username)}</div>
        </div>
        ${
          isMe
            ? `<button class="btn btn--ghost btn--sm" id="logout2">salir</button>`
            : `<button class="btn ${data.isFollowing ? 'btn--ghost' : ''}" id="follow-btn">${
                data.isFollowing ? 'en tu círculo ✓' : '+ seguir'
              }</button>`
        }
      </div>
      ${u.bio ? `<div class="profile__bio" style="padding:0 22px">${esc(u.bio)}</div>` : ''}
      <div class="profile__stats">
        <span><b>${data.stats.posts}</b> teselas</span>
        <span><b>${data.stats.followers}</b> seguidores</span>
        <span><b>${data.stats.following}</b> siguiendo</span>
      </div>
      <div style="border-top:8px solid var(--bg-2)"></div>
      <div id="profile-posts">${
        data.posts.length
          ? data.posts.map(postCard).join('')
          : emptyState('📭', 'Sin teselas todavía', isMe ? 'Publica tu primera tesela.' : '')
      }</div>`;

    wirePosts(c);
    const fb = $('#follow-btn');
    if (fb)
      fb.addEventListener('click', async () => {
        try {
          const { following } = await api(`/users/${encodeURIComponent(u.username)}/follow`, {
            method: 'POST',
          });
          fb.textContent = following ? 'en tu círculo ✓' : '+ seguir';
          fb.classList.toggle('btn--ghost', following);
          toast(following ? `Agregaste a @${u.username} a tu círculo` : 'Dejaste de seguir');
        } catch (err) {
          toast(err.message, true);
        }
      });
    const l2 = $('#logout2');
    if (l2) l2.addEventListener('click', doLogout);
  } catch (err) {
    c.innerHTML = emptyState('👻', 'Usuario no encontrado', err.message);
  }
}

// =========================================================================
// RAIL (tendencias + sugerencias)
// =========================================================================
async function loadRail() {
  const rail = $('#rail');
  if (!rail) return;
  try {
    const [{ trends }, { suggestions }] = await Promise.all([
      api('/explore'),
      api('/suggestions'),
    ]);
    const trendHtml = trends.length
      ? trends
          .slice(0, 6)
          .map(
            (t) =>
              `<div class="trend-row" data-hashtag="${esc(t.tag)}"><span class="tag">#${esc(
                t.tag
              )}</span><small>${t.posts} ${t.posts === 1 ? 'tesela' : 'teselas'}</small></div>`
          )
          .join('')
      : '<small style="color:var(--text-faint)">Aún no hay tendencias</small>';
    const suggHtml = suggestions.length
      ? suggestions
          .map(
            (s) => `
        <div class="sugg-row">
          ${avatar(s.user, 'avatar--sm')}
          <div class="meta" data-user="${esc(s.user.username)}" style="cursor:pointer">
            <b>${esc(s.user.displayName)}</b>
            <small>@${esc(s.user.username)} · ${s.followers} seg.</small>
          </div>
          <button class="btn btn--sm btn--ghost" data-follow="${esc(s.user.username)}">seguir</button>
        </div>`
          )
          .join('')
      : '<small style="color:var(--text-faint)">Nadie por sugerir</small>';

    rail.innerHTML = `
      <div class="rail__card">
        <h3>tendencias</h3>
        ${trendHtml}
      </div>
      <div class="rail__card">
        <h3>a quién seguir</h3>
        ${suggHtml}
      </div>
      <div class="rail__card" style="font-size:12px;color:var(--text-faint);line-height:1.5">
        <b style="color:var(--text-dim)">tesela</b> · teselas Metro, círculos estilo G+ y un feed que aprende de ti como TikTok.
      </div>`;

    rail.querySelectorAll('[data-hashtag]').forEach((el) =>
      el.addEventListener('click', () => navigate('hashtag', el.dataset.hashtag))
    );
    rail.querySelectorAll('[data-user]').forEach((el) =>
      el.addEventListener('click', () => navigate('profile', el.dataset.user))
    );
    rail.querySelectorAll('[data-follow]').forEach((btn) =>
      btn.addEventListener('click', async () => {
        try {
          const { following } = await api(`/users/${encodeURIComponent(btn.dataset.follow)}/follow`, {
            method: 'POST',
          });
          btn.textContent = following ? 'siguiendo ✓' : 'seguir';
          if (following) toast('Agregado a tu círculo');
        } catch (err) {
          toast(err.message, true);
        }
      })
    );
  } catch (_) {
    rail.innerHTML = '';
  }
}

// =========================================================================
// COMENTARIOS (modal)
// =========================================================================
async function openComments(postId) {
  const back = document.createElement('div');
  back.className = 'modal-backdrop';
  back.innerHTML = `
    <div class="modal">
      <div class="modal__head">
        <h3>comentarios</h3>
        <button class="close-x" id="cm-close" aria-label="Cerrar">${icon('close')}</button>
      </div>
      <div class="modal__body" id="cm-body"><div class="loading"><div class="spinner"></div></div></div>
      <div class="modal__foot">
        <input id="cm-input" placeholder="Escribe un comentario…" maxlength="280" />
        <button class="btn btn--sm" id="cm-send">enviar</button>
      </div>
    </div>`;
  document.body.appendChild(back);
  const close = () => back.remove();
  back.addEventListener('click', (e) => {
    if (e.target === back) close();
  });
  $('#cm-close', back).addEventListener('click', close);

  async function refresh() {
    const { comments } = await api(`/posts/${postId}/comments`);
    const body = $('#cm-body', back);
    body.innerHTML = comments.length
      ? comments
          .map(
            (c) => `
        <div class="comment">
          ${avatar(c.author, 'avatar--sm')}
          <div class="body">
            <b>${esc(c.author.displayName)}</b> <span style="color:var(--text-faint);font-size:13px">@${esc(
              c.author.username
            )} · ${timeAgo(c.createdAt)}</span>
            <div class="t">${linkify(c.text)}</div>
          </div>
        </div>`
          )
          .join('')
      : `<div class="empty" style="padding:36px"><div class="big">💬</div>Sé el primero en comentar</div>`;
  }
  await refresh();

  const send = async () => {
    const input = $('#cm-input', back);
    const text = input.value.trim();
    if (!text) return;
    try {
      await api(`/posts/${postId}/comments`, { method: 'POST', body: { text } });
      input.value = '';
      await refresh();
      // Actualiza contador en la tarjeta si está visible.
      const card = document.querySelector(`.post[data-post="${postId}"] .act.comment .n`);
      if (card) card.textContent = parseInt(card.textContent || '0', 10) + 1;
    } catch (err) {
      toast(err.message, true);
    }
  };
  $('#cm-send', back).addEventListener('click', send);
  $('#cm-input', back).addEventListener('keydown', (e) => {
    if (e.key === 'Enter') send();
  });
}

// =========================================================================
// MODO INMERSIVO (TikTok)
// =========================================================================
async function openImmersive(items, startId) {
  if (!items || !items.length) {
    try {
      const res = await api('/feed?type=foryou');
      items = res.items;
    } catch (err) {
      return toast(err.message, true);
    }
  }
  if (!items.length) return toast('No hay teselas para el modo inmersivo', true);

  const wrap = document.createElement('div');
  wrap.className = 'immersive';
  wrap.innerHTML =
    `<button class="immersive__close" id="imm-close" aria-label="Cerrar">${icon('close')}</button>` +
    items.map(immCard).join('') +
    `<div class="imm-hint">desliza ↑ para la siguiente</div>`;
  document.body.appendChild(wrap);
  document.body.style.overflow = 'hidden';
  // Posiciona el modo inmersivo en la tesela tocada.
  if (startId) {
    const t = wrap.querySelector(`.imm-card[data-post="${startId}"]`);
    if (t) wrap.scrollTop = t.offsetTop;
  }

  const close = () => {
    wrap.remove();
    document.body.style.overflow = '';
  };
  $('#imm-close', wrap).addEventListener('click', close);
  document.addEventListener('keydown', function onKey(e) {
    if (e.key === 'Escape') {
      close();
      document.removeEventListener('keydown', onKey);
    }
  });

  wrap.querySelectorAll('.imm-card').forEach((card) => {
    const id = card.dataset.post;
    card.querySelectorAll('[data-act]').forEach((btn) =>
      btn.addEventListener('click', async () => {
        const act = btn.dataset.act;
        try {
          if (act === 'like') {
            const { liked, count } = await api(`/posts/${id}/like`, { method: 'POST' });
            btn.classList.toggle('is-on', liked);
            btn.querySelector('.n').textContent = count;
          } else if (act === 'repost') {
            const { reposted, count } = await api(`/posts/${id}/repost`, { method: 'POST' });
            btn.classList.toggle('is-on', reposted);
            btn.querySelector('.n').textContent = count;
          } else if (act === 'comment') {
            openComments(id);
          }
        } catch (err) {
          toast(err.message, true);
        }
      })
    );
  });

  // dwell tracking en modo inmersivo
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            const id = e.target.dataset.post;
            api('/interactions', { method: 'POST', body: { postId: id, type: 'dwell' } }).catch(() => {});
          }
        }
      },
      { threshold: 0.7, root: wrap }
    );
    wrap.querySelectorAll('.imm-card').forEach((el) => io.observe(el));
  }
}

function immCard(p) {
  const bg = p.tileColor || colorFor(p.author.username);
  const glyph = p.emoji || EMOJIS[Math.floor(Math.random() * EMOJIS.length)];
  return `
  <section class="imm-card" data-post="${p.id}" style="background:linear-gradient(160deg, ${bg}, #000 130%)">
    <div class="imm-card__glyph">${esc(glyph)}</div>
    <div class="imm-card__text">${linkify(p.text)}</div>
    <div class="imm-card__author">
      ${avatar(p.author)}
      <div><b>${esc(p.author.displayName)}</b><br><small style="opacity:.8">@${esc(p.author.username)}</small></div>
    </div>
    <div class="imm-card__rail">
      <button class="imm-act like ${p.liked ? 'is-on' : ''}" data-act="like" aria-label="Me gusta"><span class="ic">${icon('heart')}</span><span class="n">${p.counts.like}</span></button>
      <button class="imm-act repost ${p.reposted ? 'is-on' : ''}" data-act="repost" aria-label="Eco"><span class="ic">${icon('repeat')}</span><span class="n">${p.counts.repost}</span></button>
      <button class="imm-act comment" data-act="comment" aria-label="Comentar"><span class="ic">${icon('message')}</span><span class="n">${p.counts.comment}</span></button>
    </div>
  </section>`;
}

// =========================================================================
// AUTH VIEW
// =========================================================================
function renderAuth() {
  let mode = 'login';
  const tiles = Array.from({ length: 24 })
    .map((_, i) => `<span style="background:${TILE_COLORS[i % TILE_COLORS.length]}"></span>`)
    .join('');

  function view() {
    app().innerHTML = `
    <div class="auth">
      <div class="auth__hero">
        <div class="auth__hero-grid">${tiles}</div>
        <div class="auth__brand">
          <h1>tesela</h1>
          <p>Una red social de <b style="color:#fff">teselas</b>. La estética viva de Windows Phone, los círculos de Google+ y un feed que aprende de ti como TikTok.</p>
        </div>
        <div class="auth__hero-foot">
          <span><b>Teselas</b> vivas</span>
          <span><b>Círculos</b> para seguir</span>
          <span><b>Para Ti</b> algorítmico</span>
          <span><b>Modo</b> inmersivo</span>
        </div>
      </div>
      <div class="auth__panel">
        <div class="auth__form">
          <div class="auth__tabs">
            <button class="auth__tab ${mode === 'login' ? 'is-active' : ''}" data-mode="login">entrar</button>
            <button class="auth__tab ${mode === 'register' ? 'is-active' : ''}" data-mode="register">crear cuenta</button>
          </div>
          <div id="auth-error"></div>
          <form id="auth-form">
            ${
              mode === 'register'
                ? `<div class="field"><label>Nombre para mostrar</label><input name="displayName" placeholder="Ada Lovelace" /></div>`
                : ''
            }
            <div class="field"><label>Usuario</label><input name="username" placeholder="ada" autocomplete="username" required /></div>
            <div class="field"><label>Contraseña</label><input name="password" type="password" placeholder="mínimo 6 caracteres" autocomplete="${
              mode === 'register' ? 'new-password' : 'current-password'
            }" required /></div>
            ${
              mode === 'register'
                ? `<div class="field"><label>Bio (opcional)</label><textarea name="bio" placeholder="Cuéntanos de ti…"></textarea></div>`
                : ''
            }
            <button class="btn btn--block" type="submit">${mode === 'login' ? 'entrar' : 'crear cuenta'}</button>
          </form>
          <p style="color:var(--text-faint);font-size:13px;margin-top:18px;text-align:center">
            ${
              mode === 'login'
                ? '¿Primera vez? Crea una cuenta y empieza a teselar.'
                : 'Al crear tu cuenta aceptas divertirte.'
            }
          </p>
        </div>
      </div>
    </div>`;

    app()
      .querySelectorAll('[data-mode]')
      .forEach((b) =>
        b.addEventListener('click', () => {
          mode = b.dataset.mode;
          view();
        })
      );

    $('#auth-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const payload = Object.fromEntries(fd.entries());
      const errBox = $('#auth-error');
      errBox.innerHTML = '';
      try {
        const path = mode === 'login' ? '/login' : '/register';
        const { token, user } = await api(path, { method: 'POST', body: payload });
        store.token = token;
        store.me = user;
        localStorage.setItem('tesela_token', token);
        toast(`¡Hola, ${user.displayName}! 👋`);
        navigate('feed');
      } catch (err) {
        errBox.innerHTML = `<div class="auth__error">${esc(err.message)}</div>`;
      }
    });
  }
  view();
}

// =========================================================================
// Helpers varios
// =========================================================================
function emptyState(emoji, title, sub) {
  return `<div class="empty"><div class="big">${emoji}</div><h3 style="font-weight:300;font-size:24px;margin:8px 0">${esc(
    title
  )}</h3><p>${esc(sub || '')}</p></div>`;
}

function doLogout() {
  store.token = null;
  store.me = null;
  localStorage.removeItem('tesela_token');
  location.hash = '';
  renderAuth();
}

// =========================================================================
// Arranque
// =========================================================================
async function boot() {
  parseHash();
  if (store.token) {
    try {
      const { user } = await api('/me');
      store.me = user;
    } catch (_) {
      store.token = null;
      localStorage.removeItem('tesela_token');
    }
  }
  render();
}

boot();
