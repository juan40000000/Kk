'use strict';

/**
 * Almacén de datos ultra-ligero respaldado por un único archivo JSON.
 * Elegido a propósito para evitar dependencias nativas (better-sqlite3, etc.)
 * y que el proyecto corra con solo `npm install` en cualquier entorno.
 *
 * No es apto para alta concurrencia real, pero es más que suficiente para
 * un prototipo / demo de la red social. Toda escritura persiste en disco
 * de forma atómica (write a temp + rename).
 */

const fs = require('fs');
const path = require('path');

const DATA_DIR = path.join(__dirname, '..', 'data');
const DATA_FILE = path.join(DATA_DIR, 'tesela.json');

const EMPTY = {
  users: [],
  posts: [],
  likes: [],
  reposts: [],
  comments: [],
  follows: [],
  interactions: [],
  _seq: 0,
};

let state = null;
let writeTimer = null;

function ensureLoaded() {
  if (state) return state;
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    if (fs.existsSync(DATA_FILE)) {
      const raw = fs.readFileSync(DATA_FILE, 'utf8');
      state = Object.assign({}, EMPTY, JSON.parse(raw || '{}'));
    } else {
      state = JSON.parse(JSON.stringify(EMPTY));
      flushNow();
    }
  } catch (err) {
    console.error('[db] No se pudo cargar la base, iniciando vacía:', err.message);
    state = JSON.parse(JSON.stringify(EMPTY));
  }
  return state;
}

function flushNow() {
  if (!state) return;
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
  const tmp = DATA_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(state, null, 0));
  fs.renameSync(tmp, DATA_FILE);
}

/** Persistencia diferida (agrupa escrituras en ráfaga). */
function persist() {
  if (writeTimer) return;
  writeTimer = setTimeout(() => {
    writeTimer = null;
    try {
      flushNow();
    } catch (err) {
      console.error('[db] Error al persistir:', err.message);
    }
  }, 40);
}

function nextId(prefix) {
  const s = ensureLoaded();
  s._seq += 1;
  return `${prefix}_${Date.now().toString(36)}${s._seq.toString(36)}`;
}

module.exports = {
  get data() {
    return ensureLoaded();
  },
  nextId,
  persist,
  flushNow,
  DATA_FILE,
  /** Solo para tests / seed: reemplaza todo el estado. */
  _replaceState(newState) {
    state = Object.assign({}, EMPTY, newState);
    flushNow();
  },
};
