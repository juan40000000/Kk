'use strict';

const path = require('path');
const express = require('express');
const api = require('./src/api');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json({ limit: '256kb' }));

// API
app.use('/api', api);

// Front estático (SPA)
const PUBLIC_DIR = path.join(__dirname, 'public');
app.use(express.static(PUBLIC_DIR));

// Cualquier otra ruta la resuelve el SPA (client-side routing).
app.get('*', (req, res) => {
  res.sendFile(path.join(PUBLIC_DIR, 'index.html'));
});

app.listen(PORT, () => {
  console.log('');
  console.log('  ████████╗███████╗███████╗███████╗██╗      █████╗');
  console.log('  ╚══██╔══╝██╔════╝██╔════╝██╔════╝██║     ██╔══██╗');
  console.log('     ██║   █████╗  ███████╗█████╗  ██║     ███████║');
  console.log('     ██║   ██╔══╝  ╚════██║██╔══╝  ██║     ██╔══██║');
  console.log('     ██║   ███████╗███████║███████╗███████╗██║  ██║');
  console.log('     ╚═╝   ╚══════╝╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝');
  console.log('');
  console.log(`  Tesela corriendo en  http://localhost:${PORT}`);
  console.log('  Red social · teselas Metro · círculos G+ · feed algorítmico');
  console.log('');
});
