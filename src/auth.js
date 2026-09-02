'use strict';

const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const db = require('./db');

const JWT_SECRET =
  process.env.JWT_SECRET || 'tesela-dev-secret-cambia-esto-en-produccion';
const TOKEN_TTL = '30d';

// Paleta de colores de acento estilo Metro para los avatares.
const AVATAR_COLORS = [
  '#e51400', '#a20025', '#f0a30a', '#e3c800', '#60a917',
  '#008a00', '#00aba9', '#1ba1e2', '#0050ef', '#6a00ff',
  '#aa00ff', '#d80073', '#a0522d', '#647687', '#76608a',
];

function hashPassword(pw) {
  return bcrypt.hashSync(pw, 10);
}

function verifyPassword(pw, hash) {
  return bcrypt.compareSync(pw, hash);
}

function signToken(user) {
  return jwt.sign({ uid: user.id }, JWT_SECRET, { expiresIn: TOKEN_TTL });
}

function publicUser(user) {
  if (!user) return null;
  const { passwordHash, email, ...rest } = user;
  return rest;
}

function findUserById(id) {
  return db.data.users.find((u) => u.id === id) || null;
}

function findUserByUsername(username) {
  const uname = String(username || '').toLowerCase();
  return db.data.users.find((u) => u.username.toLowerCase() === uname) || null;
}

function createUser({ username, displayName, email, password, bio }) {
  const id = db.nextId('u');
  const user = {
    id,
    username,
    displayName: displayName || username,
    email: email || '',
    passwordHash: hashPassword(password),
    bio: bio || '',
    avatarColor: AVATAR_COLORS[db.data.users.length % AVATAR_COLORS.length],
    createdAt: Date.now(),
  };
  db.data.users.push(user);
  db.persist();
  return user;
}

/** Middleware Express: exige un token válido en Authorization: Bearer <t>. */
function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'No autenticado' });
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    const user = findUserById(payload.uid);
    if (!user) return res.status(401).json({ error: 'Usuario no encontrado' });
    req.user = user;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Token inválido' });
  }
}

/** Igual que requireAuth pero no falla si no hay token (req.user opcional). */
function optionalAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (token) {
    try {
      const payload = jwt.verify(token, JWT_SECRET);
      req.user = findUserById(payload.uid) || null;
    } catch (_) {
      req.user = null;
    }
  }
  next();
}

module.exports = {
  hashPassword,
  verifyPassword,
  signToken,
  publicUser,
  createUser,
  findUserById,
  findUserByUsername,
  requireAuth,
  optionalAuth,
  AVATAR_COLORS,
};
