/* Globo WebGL hiperrealista (three.js): texturas satelitales de la NASA, día/noche según la posición
   real del sol, luces de ciudades, reflejo del sol en el océano, relieve, nubes, atmósfera, misiles con
   estela, explosiones con partículas y un HUD táctico. Devuelve null si el dispositivo no tiene WebGL. */
window.PG_Globe3D = function (opts) {
  'use strict';
  const T = window.THREE;
  if (!T) return null;
  const canvas = opts.canvas;
  let renderer;
  try {
    renderer = new T.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  } catch (e) { return null; }
  if (!renderer.getContext()) return null;

  const D2R = Math.PI / 180;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const DPR = Math.min(window.devicePixelRatio || 1, 2);
  renderer.setPixelRatio(DPR);
  renderer.setClearColor(0x01030a, 1);

  const scene = new T.Scene();
  const camera = new T.PerspectiveCamera(45, 1, 0.01, 200);
  const earth = new T.Group();
  scene.add(earth);

  // lat/lon -> vector (coincide con las UV de SphereGeometry: lon 0 en +X, lon 90E en -Z)
  function ll2v(lat, lon, r, out) {
    const la = lat * D2R, lo = lon * D2R, c = Math.cos(la);
    return (out || new T.Vector3()).set(r * c * Math.cos(lo), r * Math.sin(la), -r * c * Math.sin(lo));
  }
  function v2ll(v) {
    const r = v.length();
    return [Math.asin(v.y / r) / D2R, Math.atan2(-v.z, v.x) / D2R];
  }

  // posición del sol (punto subsolar) a partir de la hora UTC real
  function sunLL(date) {
    const start = Date.UTC(date.getUTCFullYear(), 0, 0);
    const day = (date - start) / 864e5;
    const g = (2 * Math.PI / 365) * (day - 1);
    const decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g);
    const eqt = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
    const mins = date.getUTCHours() * 60 + date.getUTCMinutes() + date.getUTCSeconds() / 60;
    return [decl / D2R, -((mins + eqt) / 4 - 180)];
  }
  const sunDir = new T.Vector3();
  function updateSun() { const [la, lo] = sunLL(new Date()); ll2v(la, lo, 1, sunDir); }
  updateSun();

  // ---------- texturas ----------
  const loader = new T.TextureLoader();
  const aniso = renderer.capabilities.getMaxAnisotropy();
  const blank = new T.DataTexture(new Uint8Array([20, 30, 60, 255]), 1, 1); blank.needsUpdate = true;
  function tex(url) {
    const t = loader.load(url, () => { t.needsUpdate = true; });
    t.anisotropy = Math.min(8, aniso);
    return t;
  }
  const dayMap = tex('tex/day.jpg'), nightMap = tex('tex/night.jpg'), waterMap = tex('tex/water.jpg');
  const topoMap = tex('tex/topo.jpg'), cloudMap = tex('tex/clouds.jpg');
  cloudMap.wrapS = T.RepeatWrapping;

  // textura de "tinte": países en conflicto pintados en una proyección equirectangular
  const tintCanvas = document.createElement('canvas'); tintCanvas.width = 2048; tintCanvas.height = 1024;
  const tintMap = new T.CanvasTexture(tintCanvas);
  function paintTint(heatByCountry) {
    const c = tintCanvas.getContext('2d');
    c.clearRect(0, 0, 2048, 1024);
    const proj = d3.geoEquirectangular().scale(1024 / Math.PI).translate([1024, 512]);
    const path = d3.geoPath(proj, c);
    opts.world.forEach((f) => {
      const h = heatByCountry[f.properties.name]; if (!h) return;
      c.beginPath(); path(f);
      c.fillStyle = `rgba(255,40,60,${0.08 + h * 0.035})`; c.fill();
      c.strokeStyle = 'rgba(255,90,110,0.95)'; c.lineWidth = 2.2; c.stroke();
    });
    tintMap.needsUpdate = true;
  }

  // ---------- Tierra ----------
  const U = {
    dayMap: { value: dayMap }, nightMap: { value: nightMap }, waterMap: { value: waterMap }, topoMap: { value: topoMap },
    cloudMap: { value: cloudMap }, tintMap: { value: tintMap }, sunDir: { value: sunDir }, mode: { value: 1 },
    time: { value: 0 }, cloudShift: { value: 0 }
  };
  const earthMat = new T.ShaderMaterial({
    uniforms: U,
    vertexShader: `
      varying vec2 vUv; varying vec3 vN; varying vec3 vP;
      void main() {
        vUv = uv;
        vN = normalize((modelMatrix * vec4(normal, 0.0)).xyz);
        vec4 wp = modelMatrix * vec4(position, 1.0); vP = wp.xyz;
        gl_Position = projectionMatrix * viewMatrix * wp;
      }`,
    fragmentShader: `
      uniform sampler2D dayMap, nightMap, waterMap, topoMap, cloudMap, tintMap;
      uniform vec3 sunDir; uniform float mode, time, cloudShift;
      varying vec2 vUv; varying vec3 vN; varying vec3 vP;
      void main() {
        vec3 n = normalize(vN);
        // relieve a partir del mapa de altura
        float tx = 1.0 / 2048.0;
        float hC = texture2D(topoMap, vUv).r;
        float hX = texture2D(topoMap, vUv + vec2(tx, 0.0)).r;
        float hY = texture2D(topoMap, vUv + vec2(0.0, tx)).r;
        vec3 east = normalize(vec3(n.z, 0.0, -n.x) + vec3(1e-4, 0.0, 0.0));
        vec3 north = cross(n, east);
        vec3 nb = normalize(n - (east * (hX - hC) + north * (hY - hC)) * 5.0);
        float ndlG = dot(n, sunDir);
        float ndl = max(dot(nb, sunDir), 0.0);
        float dayF = smoothstep(-0.10, 0.20, ndlG);
        vec3 day = texture2D(dayMap, vUv).rgb;
        vec3 night = texture2D(nightMap, vUv).rgb;
        float water = texture2D(waterMap, vUv).r;
        float cs = texture2D(cloudMap, vUv + vec2(cloudShift + 0.0015, -0.001)).r;
        vec3 V = normalize(cameraPosition - vP);
        vec3 H = normalize(sunDir + V);
        float nh = max(dot(n, H), 0.0);
        vec3 lit = day * (0.05 + 1.15 * ndl) * (1.0 - cs * 0.45);
        lit += vec3(1.0, 0.93, 0.78) * pow(nh, 90.0) * water * 1.1 * (1.0 - cs);
        lit += vec3(0.55, 0.72, 1.0) * pow(nh, 9.0) * water * 0.10;
        vec3 nightCol = night * vec3(1.0, 0.76, 0.42) * 2.2 * (1.0 - water * 0.9);
        vec3 col = mix(nightCol + day * 0.02, lit, dayF);
        float tw = 1.0 - smoothstep(0.0, 0.25, abs(ndlG));
        col += vec3(0.95, 0.38, 0.12) * tw * 0.16;
        vec4 tint = texture2D(tintMap, vUv);
        float pulse = 0.7 + 0.3 * sin(time * 2.4);
        col = mix(col, col * 0.6 + vec3(1.0, 0.2, 0.25) * 0.5, tint.a * pulse);
        float fres = pow(1.0 - max(dot(n, V), 0.0), 2.6);
        col += vec3(0.28, 0.52, 1.0) * fres * (0.12 + 0.88 * dayF) * 0.75;
        float l = dot(col, vec3(0.299, 0.587, 0.114));
        if (mode > 1.5) {            // térmico (blanco = caliente)
          float heat = l * 1.25 + tint.a * pulse * 0.55 + (1.0 - dayF) * dot(night, vec3(0.33)) * 1.4;
          col = vec3(heat);
        } else if (mode > 0.5) {     // táctico: gradación fría
          col = mix(col, vec3(l * 0.78, l * 1.02, l * 1.0), 0.35) * 1.06;
        }
        gl_FragColor = vec4(col, 1.0);
      }`
  });
  const earthMesh = new T.Mesh(new T.SphereGeometry(1, 160, 80), earthMat);
  earth.add(earthMesh);

  // nubes
  const cloudMat = new T.ShaderMaterial({
    uniforms: { cloudMap: { value: cloudMap }, sunDir: { value: sunDir }, mode: U.mode },
    transparent: true, depthWrite: false,
    vertexShader: `varying vec2 vUv; varying vec3 vN; void main(){ vUv = uv; vN = normalize((modelMatrix * vec4(normal,0.0)).xyz); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: `uniform sampler2D cloudMap; uniform vec3 sunDir; uniform float mode; varying vec2 vUv; varying vec3 vN;
      void main(){ float a = texture2D(cloudMap, vUv).r; float d = dot(normalize(vN), sunDir);
        float lit = 0.08 + 0.95 * smoothstep(-0.15, 0.35, d);
        vec3 c = vec3(lit) * mix(vec3(1.0,0.75,0.6), vec3(1.0), smoothstep(0.0, 0.3, d));
        if (mode > 1.5) c = vec3(0.35 + a * 0.2);
        gl_FragColor = vec4(c, a * (mode > 1.5 ? 0.45 : 0.82)); }`
  });
  const clouds = new T.Mesh(new T.SphereGeometry(1.007, 96, 48), cloudMat);
  earth.add(clouds);

  // atmósfera (halo)
  const atmo = new T.Mesh(new T.SphereGeometry(1.13, 64, 32), new T.ShaderMaterial({
    uniforms: { sunDir: { value: sunDir }, mode: U.mode },
    side: T.BackSide, transparent: true, blending: T.AdditiveBlending, depthWrite: false,
    vertexShader: `varying vec3 vNv; varying vec3 vNw; void main(){ vNv = normalize(normalMatrix * normal); vNw = normalize((modelMatrix*vec4(normal,0.0)).xyz); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: `uniform vec3 sunDir; uniform float mode; varying vec3 vNv; varying vec3 vNw;
      void main(){ float i = pow(clamp(0.78 - dot(vNv, vec3(0.0,0.0,1.0)), 0.0, 1.0), 3.2);
        float day = 0.25 + 0.75 * smoothstep(-0.35, 0.4, dot(vNw, sunDir));
        vec3 c = mode > 1.5 ? vec3(0.7) : mode > 0.5 ? vec3(0.2, 0.62, 0.9) : vec3(0.32, 0.6, 1.0);
        gl_FragColor = vec4(c * i * day * (mode > 0.5 && mode < 1.5 ? 0.85 : 1.15), 1.0); }`
  }));
  scene.add(atmo);

  // estrellas
  (function () {
    const N = 2600, pos = new Float32Array(N * 3), col = new Float32Array(N * 3);
    for (let i = 0; i < N; i++) {
      const u = Math.random() * 2 - 1, th = Math.random() * Math.PI * 2, r = 60 + Math.random() * 30, s = Math.sqrt(1 - u * u);
      pos.set([r * s * Math.cos(th), r * u, r * s * Math.sin(th)], i * 3);
      const b = 0.45 + Math.random() * 0.55, warm = Math.random();
      col.set([b * (0.85 + warm * 0.15), b * 0.9, b * (1.05 - warm * 0.2)], i * 3);
    }
    const g = new T.BufferGeometry();
    g.setAttribute('position', new T.BufferAttribute(pos, 3)); g.setAttribute('color', new T.BufferAttribute(col, 3));
    scene.add(new T.Points(g, new T.PointsMaterial({ size: 1.6, sizeAttenuation: false, vertexColors: true, transparent: true, opacity: 0.9 })));
  })();

  // fronteras y retícula
  const borders = (function () {
    const mesh = topojson.mesh(window.WORLD_TOPO, window.WORLD_TOPO.objects.countries);
    const pts = [];
    mesh.coordinates.forEach((line) => {
      for (let i = 1; i < line.length; i++) {
        const a = ll2v(line[i - 1][1], line[i - 1][0], 1.0016), b = ll2v(line[i][1], line[i][0], 1.0016);
        pts.push(a.x, a.y, a.z, b.x, b.y, b.z);
      }
    });
    const g = new T.BufferGeometry(); g.setAttribute('position', new T.Float32BufferAttribute(pts, 3));
    const m = new T.LineSegments(g, new T.LineBasicMaterial({ color: 0xaac4ff, transparent: true, opacity: 0.2, depthWrite: false }));
    earth.add(m);
    return m;
  })();
  const grid = (function () {
    const pts = [];
    for (let lat = -80; lat <= 80; lat += 20) for (let lon = -180; lon < 180; lon += 3) {
      const a = ll2v(lat, lon, 1.002), b = ll2v(lat, lon + 3, 1.002); pts.push(a.x, a.y, a.z, b.x, b.y, b.z);
    }
    for (let lon = -180; lon < 180; lon += 20) for (let lat = -84; lat < 84; lat += 3) {
      const a = ll2v(lat, lon, 1.002), b = ll2v(lat + 3, lon, 1.002); pts.push(a.x, a.y, a.z, b.x, b.y, b.z);
    }
    const g = new T.BufferGeometry(); g.setAttribute('position', new T.Float32BufferAttribute(pts, 3));
    const m = new T.LineSegments(g, new T.LineBasicMaterial({ color: 0x5dffb0, transparent: true, opacity: 0.07, depthWrite: false }));
    earth.add(m);
    return m;
  })();

  // ---------- sprites procedurales ----------
  function spriteTex(draw, size) {
    const c = document.createElement('canvas'); c.width = c.height = size || 128;
    draw(c.getContext('2d'), c.width);
    const t = new T.CanvasTexture(c); return t;
  }
  const softTex = spriteTex((g, s) => {
    const r = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
    r.addColorStop(0, 'rgba(255,255,255,1)'); r.addColorStop(0.25, 'rgba(255,255,255,.8)'); r.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = r; g.fillRect(0, 0, s, s);
  });
  const smokeTex = spriteTex((g, s) => {
    for (let i = 0; i < 26; i++) {
      const x = s / 2 + (Math.random() - 0.5) * s * 0.45, y = s / 2 + (Math.random() - 0.5) * s * 0.45, r = s * (0.12 + Math.random() * 0.2);
      const gr = g.createRadialGradient(x, y, 0, x, y, r);
      gr.addColorStop(0, 'rgba(255,255,255,.22)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = gr; g.fillRect(0, 0, s, s);
    }
  });
  const cycloneTex = spriteTex((g, s) => {
    g.translate(s / 2, s / 2);
    g.filter = 'blur(5px)';
    for (let arm = 0; arm < 5; arm++) {
      g.beginPath();
      for (let i = 0; i <= 80; i++) {
        const r = s * 0.5 * (0.08 + 0.92 * i / 80), a = arm * Math.PI * 2 / 5 - Math.log(r / (s * 0.04)) * 1.7;
        const x = Math.cos(a) * r, y = Math.sin(a) * r; if (i) g.lineTo(x, y); else g.moveTo(x, y);
      }
      g.strokeStyle = 'rgba(255,255,255,.85)'; g.lineWidth = s * 0.07; g.lineCap = 'round'; g.stroke();
    }
    g.filter = 'blur(12px)';
    const gr = g.createRadialGradient(0, 0, s * 0.04, 0, 0, s * 0.5);
    gr.addColorStop(0, 'rgba(255,255,255,.9)'); gr.addColorStop(0.5, 'rgba(255,255,255,.45)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.beginPath(); g.arc(0, 0, s * 0.5, 0, Math.PI * 2); g.fill();
    g.filter = 'none';
    g.globalCompositeOperation = 'destination-out';
    g.fillStyle = 'rgba(0,0,0,1)'; g.beginPath(); g.arc(0, 0, s * 0.035, 0, Math.PI * 2); g.fill();
  }, 512);

  // ---------- sistema de partículas (fuego aditivo + humo normal) ----------
  function particleSystem(max, texture, additive) {
    const pos = new Float32Array(max * 3), col = new Float32Array(max * 4), size = new Float32Array(max);
    const g = new T.BufferGeometry();
    g.setAttribute('position', new T.BufferAttribute(pos, 3));
    g.setAttribute('pcolor', new T.BufferAttribute(col, 4));
    g.setAttribute('psize', new T.BufferAttribute(size, 1));
    const mat = new T.ShaderMaterial({
      uniforms: { map: { value: texture }, scale: { value: 600 } },
      transparent: true, depthWrite: false, blending: additive ? T.AdditiveBlending : T.NormalBlending,
      vertexShader: `attribute vec4 pcolor; attribute float psize; uniform float scale; varying vec4 vC;
        void main(){ vC = pcolor; vec4 mv = modelViewMatrix * vec4(position, 1.0); gl_PointSize = psize * scale / -mv.z; gl_Position = projectionMatrix * mv; }`,
      fragmentShader: `uniform sampler2D map; varying vec4 vC; void main(){ vec4 t = texture2D(map, gl_PointCoord); gl_FragColor = vec4(vC.rgb, vC.a * t.a); }`
    });
    const pts = new T.Points(g, mat); pts.frustumCulled = false;
    scene.add(pts);
    const list = [];
    return {
      mat,
      emit(p) { if (list.length < max) { const k = Math.max(zs, 0.5); p.s0 *= k; p.s1 *= k; list.push(Object.assign({ age: 0 }, p)); } },
      update(dt) {
        let n = 0;
        for (let i = list.length - 1; i >= 0; i--) {
          const p = list[i]; p.age += dt;
          if (p.age > p.life) { list.splice(i, 1); continue; }
          const k = p.age / p.life;
          if (p.vel) { p.pos.addScaledVector(p.vel, dt); if (p.drag) p.vel.multiplyScalar(1 - p.drag * dt); }
          const c = p.color(k);
          pos.set([p.pos.x, p.pos.y, p.pos.z], n * 3);
          col.set([c[0], c[1], c[2], p.alpha(k)], n * 4);
          size[n] = p.s0 + (p.s1 - p.s0) * k;
          n++;
        }
        g.setDrawRange(0, n);
        g.attributes.position.needsUpdate = true; g.attributes.pcolor.needsUpdate = true; g.attributes.psize.needsUpdate = true;
      }
    };
  }
  const fire = particleSystem(900, softTex, true);
  const smoke = particleSystem(500, smokeTex, false);
  const fixed = (r, g, b) => () => [r, g, b];
  const fireColor = (k) => [1, 0.95 - k * 0.6, 0.75 - k * 0.72];

  // ---------- capas de marcadores ----------
  const markers = new T.Group(); earth.add(markers);
  const arcsGroup = new T.Group(); earth.add(arcsGroup);
  const stormGroup = new T.Group(); earth.add(stormGroup);
  let zs = 1;          // escala de marcadores según la distancia de la cámara
  let scaled = [];     // [objeto, tamaño base] que se reescalan con el zoom
  let items = [];       // objetos con posición para etiquetas y toques
  let strikes = [];
  let storms = [];
  let animated = [];    // funciones de animación por cuadro
  const ringGeo = new T.RingGeometry(0.75, 1, 48);

  function orientToSurface(obj, normal) { obj.lookAt(normal.clone().multiplyScalar(2)); }
  function disposeGroup(g) {
    while (g.children.length) {
      const o = g.children.pop();
      o.traverse((x) => { if (x.geometry && x.geometry !== ringGeo) x.geometry.dispose(); if (x.material && x.material.dispose) x.material.dispose(); });
    }
  }

  function addRing(pos, normal, color, size, opacity) {
    const m = new T.Mesh(ringGeo, new T.MeshBasicMaterial({ color, transparent: true, opacity, side: T.DoubleSide, depthWrite: false, blending: T.AdditiveBlending }));
    m.position.copy(pos); orientToSurface(m, normal); m.scale.setScalar(size);
    scaled.push([m, size]); markers.add(m); return m;
  }
  function addGlow(pos, color, size, opacity) {
    const s = new T.Sprite(new T.SpriteMaterial({ map: softTex, color, transparent: true, opacity: opacity == null ? 1 : opacity, depthWrite: false, blending: T.AdditiveBlending }));
    s.position.copy(pos); s.scale.setScalar(size); scaled.push([s, size]); markers.add(s); return s;
  }
  function beacon(pos, normal, color, height) {
    const geo = new T.CylinderGeometry(0.0018, 0.0045, height, 10, 1, true);
    geo.translate(0, height / 2, 0);
    const mat = new T.ShaderMaterial({
      uniforms: { color: { value: new T.Color(color) }, h: { value: height }, time: U.time },
      transparent: true, depthWrite: false, blending: T.AdditiveBlending, side: T.DoubleSide,
      vertexShader: `varying float vY; void main(){ vY = position.y; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
      fragmentShader: `uniform vec3 color; uniform float h, time; varying float vY; void main(){ float k = vY / h; float a = (1.0 - k) * (0.55 + 0.25 * sin(time * 3.0 - k * 8.0)); gl_FragColor = vec4(color, a); }`
    });
    const m = new T.Mesh(geo, mat);
    m.position.copy(pos);
    m.quaternion.setFromUnitVectors(new T.Vector3(0, 1, 0), normal);
    scaled.push([m, 1]); markers.add(m); return m;
  }

  // arco elevado entre dos puntos con guiones que fluyen
  const arcMatCache = {};
  function arcLine(a, b, color, lift, alpha) {
    const ip = d3.geoInterpolate([a[1], a[0]], [b[1], b[0]]);
    const N = 64, pts = [], ld = [];
    for (let i = 0; i <= N; i++) {
      const t = i / N, [lon, lat] = ip(t);
      pts.push(ll2v(lat, lon, 1.004 + Math.sin(Math.PI * t) * lift));
      ld.push(t);
    }
    const g = new T.BufferGeometry().setFromPoints(pts);
    g.setAttribute('ld', new T.Float32BufferAttribute(ld, 1));
    const key = color + ':' + alpha;
    const mat = arcMatCache[key] || (arcMatCache[key] = new T.ShaderMaterial({
      uniforms: { color: { value: new T.Color(color) }, time: U.time, alpha: { value: alpha }, dim: { value: 1 } },
      transparent: true, depthWrite: false, blending: T.AdditiveBlending,
      vertexShader: `attribute float ld; varying float vL; void main(){ vL = ld; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
      fragmentShader: `uniform vec3 color; uniform float time, alpha, dim; varying float vL;
        void main(){ float d = fract(vL * 9.0 - time * 0.7); float a = smoothstep(0.0, 0.15, d) * (1.0 - smoothstep(0.35, 0.5, d));
          gl_FragColor = vec4(color, (0.18 + a * 0.82) * alpha * dim * sin(3.14159 * vL)); }`
    }));
    const line = new T.Line(g, mat);
    arcsGroup.add(line);
    return line;
  }

  const layers = { links: true, markets: true, disasters: true, strikes: true };
  let selected = null;

  function rebuild() {
    disposeGroup(markers); disposeGroup(arcsGroup); disposeGroup(stormGroup);
    Object.keys(arcMatCache).forEach((k) => delete arcMatCache[k]);
    items = []; animated = []; scaled = [];
    const data = opts.data();
    paintTint(data.countryHeat);

    // focos de conflicto: anillos que laten + haz de luz vertical
    data.conflicts.forEach((c) => {
      const n = ll2v(c.lat, c.lon, 1), p = n.clone().multiplyScalar(1.003);
      const heat = c.heat;
      const core = addGlow(p, 0xff3b55, 0.035 + heat * 0.006);
      const r1 = addRing(p, n, 0xff4d6d, 0.01, 0.9);
      const r2 = addRing(p, n, 0xff4d6d, 0.01, 0.6);
      scaled = scaled.filter((x) => x[0] !== r1 && x[0] !== r2 && x[0] !== core);
      const beam = beacon(p, n, 0xff4d6d, 0.04 + heat * 0.022);
      const ph = Math.random();
      animated.push((t) => {
        const k1 = ((t * 0.55) + ph) % 1, k2 = ((t * 0.55) + ph + 0.5) % 1;
        r1.scale.setScalar((0.008 + k1 * (0.035 + heat * 0.006)) * zs); r1.material.opacity = (1 - k1) * 0.85;
        r2.scale.setScalar((0.008 + k2 * (0.035 + heat * 0.006)) * zs); r2.material.opacity = (1 - k2) * 0.85;
        const sel = selected === c.id;
        core.material.opacity = 0.75 + 0.25 * Math.sin(t * 4 + ph * 6);
        core.scale.setScalar((0.035 + heat * 0.006) * (sel ? 1.6 : 1) * zs);
        beam.visible = true;
      });
      items.push({ type: 'conf', id: c.id, pos: p, label: c.short, cls: 'gl-conf', pri: 10 + heat, sub: c.sub });
    });

    // plazas financieras
    if (layers.markets) data.hubs.forEach((h) => {
      const n = ll2v(h.lat, h.lon, 1), p = n.clone().multiplyScalar(1.004);
      const color = h.chg == null ? 0x8a95b4 : h.chg >= 0 ? 0x2ee6a6 : 0xff5c7a;
      addGlow(p, color, 0.022, 0.95);
      addRing(p, n, color, 0.009, 0.8);
      items.push({ type: 'hub', hub: h, pos: p, label: h.label, cls: 'gl-hub ' + (h.chg == null ? '' : h.chg >= 0 ? 'up' : 'down'), pri: h.big ? 4 : 1, minZoom: h.big ? 0 : 2.6 });
    });

    // arcos conflicto -> mercados y desastre -> mercados
    if (layers.links) {
      data.links.forEach((l) => {
        const line = arcLine(l.from, l.to, l.color, 0.03 + Math.min(0.12, d3.geoDistance([l.from[1], l.from[0]], [l.to[1], l.to[0]]) * 0.06), l.alpha);
        line.userData.owner = l.owner;
      });
    }

    // desastres
    if (layers.disasters) data.disasters.forEach((d) => {
      if (d.type === 'TC') return;
      const n = ll2v(d.lat, d.lon, 1), p = n.clone().multiplyScalar(1.004);
      const color = new T.Color(d.color);
      const glow = addGlow(p, color, 0.02 + d.sev * 0.025);
      if (d.type === 'EQ') {
        const rings = [0, 1, 2].map(() => addRing(p, n, color, 0.01, 0.8));
        scaled = scaled.filter((x) => !rings.includes(x[0]));
        animated.push((t) => rings.forEach((r, i) => { const k = ((t * 0.45) + i / 3) % 1; r.scale.setScalar((0.006 + k * (0.03 + d.sev * 0.05)) * zs); r.material.opacity = (1 - k) * 0.8; }));
      } else if (d.type === 'VO') {
        animated.push((t, dt) => { if (Math.random() < dt * 6) smoke.emit({ pos: n.clone().multiplyScalar(1.006), vel: n.clone().multiplyScalar(0.012).add(new T.Vector3((Math.random() - 0.5) * 0.004, 0, (Math.random() - 0.5) * 0.004)), s0: 0.01, s1: 0.045, life: 3.5, color: fixed(0.5, 0.47, 0.45), alpha: (k) => (1 - k) * 0.5 }); });
      } else if (d.type === 'WF') {
        animated.push((t, dt) => {
          glow.material.opacity = 0.6 + 0.4 * Math.abs(Math.sin(t * 9 + d.lat));
          if (Math.random() < dt * 4) smoke.emit({ pos: n.clone().multiplyScalar(1.005), vel: n.clone().multiplyScalar(0.006), s0: 0.008, s1: 0.03, life: 3, color: fixed(0.35, 0.33, 0.32), alpha: (k) => (1 - k) * 0.35 });
        });
      }
      items.push({ type: 'dis', id: d.id, pos: p, label: d.label, cls: 'gl-dis', color: d.color, pri: 3 + d.sev * 4, minZoom: d.sev >= 0.6 ? 0 : 2.4 });
    });

    // ciclones: espiral de nubes orientada sobre la superficie, girando
    storms = layers.disasters ? data.cyclones : [];
    storms.forEach((c) => {
      const n = ll2v(c.lat, c.lon, 1), p = n.clone().multiplyScalar(1.011);
      const sizeRad = (300 + c.cat * 90) / 6371 * 2;
      const plane = new T.Mesh(new T.PlaneGeometry(1, 1), new T.MeshBasicMaterial({ map: cycloneTex, transparent: true, depthWrite: false, opacity: 0.95 }));
      const holder = new T.Object3D(); holder.position.copy(p); orientToSurface(holder, n);
      plane.scale.setScalar(sizeRad); holder.add(plane); stormGroup.add(holder);
      const eye = addRing(n.clone().multiplyScalar(1.012), n, new T.Color(c.color), sizeRad * 0.09, 0.9);
      const sgn = c.lat >= 0 ? 1 : -1;
      animated.push((t) => { plane.rotation.z = sgn * t * 0.5; eye.material.opacity = 0.6 + 0.4 * Math.sin(t * 3); });
      if (c.track && c.track.length > 1) {
        const pts = c.track.map((q) => ll2v(q[0], q[1], 1.006));
        const g = new T.BufferGeometry().setFromPoints(pts);
        const line = new T.Line(g, new T.LineDashedMaterial({ color: 0xffffff, dashSize: 0.01, gapSize: 0.008, transparent: true, opacity: 0.7 }));
        line.computeLineDistances(); stormGroup.add(line);
        c.track.slice(1).forEach((q, i) => items.push({ type: 'track', pos: ll2v(q[0], q[1], 1.006), label: (i + 1) * 24 + 'h', cls: 'gl-track', pri: 0, minZoom: 0 }));
      }
      items.push({ type: 'storm', id: c.id, pos: p, label: c.label, cls: 'gl-storm', color: c.color, pri: 9 });
    });

    // ataques: se animan en cada cuadro
    strikes = layers.strikes ? data.strikes.map((s) => {
      const to = [s.lat, s.lon];
      const from = s.from || [s.lat + 2, s.lon - 2];
      const ip = d3.geoInterpolate([from[1], from[0]], [to[1], to[0]]);
      const dist = d3.geoDistance([from[1], from[0]], [to[1], to[0]]);
      const lift = clamp(dist * 0.45, 0.02, 0.16);
      const path = [];
      for (let i = 0; i <= 80; i++) { const t = i / 80, [lon, lat] = ip(t); path.push(ll2v(lat, lon, 1.004 + Math.sin(Math.PI * t) * lift)); }
      const n = ll2v(to[0], to[1], 1);
      const p = n.clone().multiplyScalar(1.004);
      const mark = addGlow(p, 0xff6b3d, 0.016, 0.9);
      const st = { id: s.id, path, n, p, mark, phase: s.phase || Math.random(), exploded: -1, period: 6.5 + (s.phase || 0) * 2 };
      items.push({ type: 'strike', id: s.id, pos: p, label: 'IMPACTO', cls: 'gl-strike', pri: 2, minZoom: 1.9, tactical: true });
      return st;
    }) : [];
  }

  const shocks = [];   // ondas expansivas (anillos)
  function explode(st) {
    const n = st.n, p = st.p;
    fire.emit({ pos: p.clone(), s0: 0.08, s1: 0.42, life: 0.35, color: fixed(1, 1, 0.92), alpha: (k) => (1 - k) * 0.95 });
    fire.emit({ pos: p.clone(), s0: 0.2, s1: 0.6, life: 0.18, color: fixed(1, 0.85, 0.6), alpha: (k) => (1 - k) * 0.5 });
    for (let i = 0; i < 14; i++) {
      const dir = new T.Vector3(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize().multiplyScalar(0.01 + Math.random() * 0.02).add(n.clone().multiplyScalar(0.012));
      fire.emit({ pos: p.clone(), vel: dir, drag: 2.5, s0: 0.05, s1: 0.18 + Math.random() * 0.1, life: 0.8 + Math.random() * 0.6, color: fireColor, alpha: (k) => (1 - k) * (1 - k) });
    }
    for (let i = 0; i < 18; i++) {
      const dir = new T.Vector3(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize().multiplyScalar(0.05 + Math.random() * 0.06).add(n.clone().multiplyScalar(0.03));
      fire.emit({ pos: p.clone(), vel: dir, drag: 3.5, s0: 0.022, s1: 0.004, life: 0.7 + Math.random() * 0.5, color: fixed(1, 0.75, 0.35), alpha: (k) => 1 - k });
    }
    for (let i = 0; i < 9; i++) {
      smoke.emit({ pos: p.clone().addScaledVector(n, 0.004), vel: n.clone().multiplyScalar(0.006 + Math.random() * 0.008).add(new T.Vector3((Math.random() - 0.5) * 0.006, (Math.random() - 0.5) * 0.006, (Math.random() - 0.5) * 0.006)),
        drag: 0.4, s0: 0.07, s1: 0.28, life: 3.2 + Math.random() * 1.8, color: fixed(0.22, 0.2, 0.2), alpha: (k) => Math.min(1, k * 6) * (1 - k) * 0.55 });
    }
    const ring = new T.Mesh(ringGeo, new T.MeshBasicMaterial({ color: 0xffd2a0, transparent: true, opacity: 0.9, side: T.DoubleSide, depthWrite: false, blending: T.AdditiveBlending }));
    ring.position.copy(p); orientToSurface(ring, n); scene.add(ring);
    shocks.push({ ring, age: 0, zs });
    // sacudón de cámara si el impacto está a la vista y cerca
    const toCam = camera.position.clone().sub(p);
    if (toCam.length() < 2.6 && toCam.normalize().dot(n) > 0.3) shake = Math.max(shake, 0.006);
  }

  function animateStrikes(t, dt) {
    strikes.forEach((st) => {
      const f = ((t / st.period) + st.phase) % 1;
      const cycle = Math.floor((t / st.period) + st.phase);
      if (f < 0.45) {
        const g = f / 0.45;
        const i = Math.min(st.path.length - 2, Math.floor(g * (st.path.length - 1)));
        const fr = g * (st.path.length - 1) - i;
        const head = st.path[i].clone().lerp(st.path[i + 1], fr);
        fire.emit({ pos: head, s0: 0.045, s1: 0.045, life: 0.03, color: fixed(1, 0.97, 0.85), alpha: () => 1 });
        fire.emit({ pos: head.clone(), s0: 0.07, s1: 0.025, life: 0.22, color: fixed(1, 0.55, 0.15), alpha: (k) => (1 - k) * 0.8 });
        smoke.emit({ pos: head.clone(), s0: 0.014, s1: 0.065, life: 1.8, color: fixed(0.88, 0.88, 0.9), alpha: (k) => (1 - k) * 0.5 });
      } else if (st.exploded !== cycle) {
        st.exploded = cycle; explode(st);
      }
      st.mark.material.opacity = 0.55 + 0.45 * Math.abs(Math.sin(t * 5 + st.phase * 9));
    });
    for (let i = shocks.length - 1; i >= 0; i--) {
      const s = shocks[i]; s.age += dt;
      const k = s.age / 0.9;
      if (k >= 1) { scene.remove(s.ring); s.ring.material.dispose(); shocks.splice(i, 1); continue; }
      s.ring.scale.setScalar((0.008 + k * 0.13) * Math.max(s.zs, 0.5)); s.ring.material.opacity = (1 - k) * (1 - k) * 0.7;
    }
  }

  // ---------- cámara y gestos ----------
  let camLat = 22, camLon = 25, dist = 0, vel = [0, 0], lastInteract = 0, fly = null, shake = 0;
  let fitDist = 3.2, maxDist = 4.5;
  let W = 1, H = 1, running = false, last = 0;
  const pointers = new Map(); let pinch = null, moved = 0, downAt = null;

  function resize() {
    const r = canvas.getBoundingClientRect(); W = Math.max(1, r.width); H = Math.max(1, r.height);
    renderer.setSize(W, H, false);
    camera.aspect = W / H;
    camera.setViewOffset(W, H, 0, H * 0.05, W, H);
    camera.updateProjectionMatrix();
    const halfV = camera.fov * D2R / 2, halfH = Math.atan(Math.tan(halfV) * camera.aspect);
    fitDist = 1.1 / Math.sin(Math.min(halfV, halfH));
    maxDist = fitDist * 1.35;
    if (!dist) dist = fitDist; else dist = Math.min(dist, maxDist);
    const s = H * DPR / (2 * Math.tan(camera.fov * D2R / 2));
    fire.mat.uniforms.scale.value = s; smoke.mat.uniforms.scale.value = s;
  }

  canvas.addEventListener('pointerdown', (e) => {
    canvas.setPointerCapture(e.pointerId);
    pointers.set(e.pointerId, [e.clientX, e.clientY]);
    lastInteract = performance.now(); vel = [0, 0]; fly = null;
    if (pointers.size === 1) { moved = 0; downAt = [e.clientX, e.clientY]; }
    if (pointers.size === 2) { const [a, b] = Array.from(pointers.values()); pinch = { d: Math.hypot(a[0] - b[0], a[1] - b[1]), dist }; }
  });
  canvas.addEventListener('pointermove', (e) => {
    if (!pointers.has(e.pointerId)) return;
    const prev = pointers.get(e.pointerId);
    pointers.set(e.pointerId, [e.clientX, e.clientY]);
    lastInteract = performance.now();
    if (pointers.size === 2 && pinch) {
      const [a, b] = Array.from(pointers.values());
      dist = clamp(pinch.dist * pinch.d / Math.max(10, Math.hypot(a[0] - b[0], a[1] - b[1])), 1.12, maxDist);
      moved = 99; return;
    }
    const dx = e.clientX - prev[0], dy = e.clientY - prev[1];
    moved += Math.abs(dx) + Math.abs(dy);
    const k = 0.11 * (dist - 1) * (700 / H);
    camLon -= dx * k; camLat = clamp(camLat + dy * k, -80, 80);
    vel = [-dx * k, dy * k];
  });
  function up(e) {
    if (!pointers.has(e.pointerId)) return;
    pointers.delete(e.pointerId);
    if (pointers.size < 2) pinch = null;
    if (pointers.size === 0 && moved < 8 && downAt) tap(e);
    if (pointers.size === 0) downAt = null;
  }
  canvas.addEventListener('pointerup', up);
  canvas.addEventListener('pointercancel', up);
  canvas.addEventListener('wheel', (e) => { e.preventDefault(); dist = clamp(dist * Math.exp(e.deltaY * 0.0012), 1.12, maxDist); lastInteract = performance.now(); }, { passive: false });

  const tmp = new T.Vector3(), camDir = new T.Vector3();
  function project(v) {
    tmp.copy(v).project(camera);
    return [(tmp.x + 1) / 2 * W, (1 - tmp.y) / 2 * H, tmp.z];
  }
  function facing(v) { camDir.copy(camera.position).normalize(); return v.clone().normalize().dot(camDir) > 0.12; }

  function tap(e) {
    const r = canvas.getBoundingClientRect();
    const x = e.clientX - r.left, y = e.clientY - r.top;
    let best = null, bd = 30;
    items.forEach((it) => {
      if (it.type === 'track' || !facing(it.pos)) return;
      const [px, py] = project(it.pos);
      const d = Math.hypot(px - x, py - y) - (it.type === 'conf' ? 6 : 0);
      if (d < bd) { bd = d; best = it; }
    });
    if (!best) { selected = null; opts.onSelect(null); return; }
    if (best.type !== 'hub') selected = best.id;
    opts.onTap(best);
  }

  // ---------- etiquetas HTML y HUD ----------
  const labelLayer = opts.labels;
  const labelEls = new Map();
  function labelsFrame() {
    const placed = [];
    const vis = [];
    items.forEach((it) => {
      if (!it.label) return;
      if (it.minZoom && dist > it.minZoom) return;
      if (it.tactical && U.mode.value < 0.5) return;
      if (!facing(it.pos)) return;
      const [x, y, z] = project(it.pos);
      if (z > 1 || x < -40 || x > W + 40 || y < -20 || y > H + 20) return;
      vis.push({ it, x, y });
    });
    vis.sort((a, b) => b.it.pri - a.it.pri);
    const seen = new Set();
    vis.forEach(({ it, x, y }) => {
      const key = it.type + ':' + (it.id || it.label + it.pos.x.toFixed(3));
      const w = it.label.length * 6.6 + 14, h = 18;
      const off = it.type === 'conf' ? -24 : it.type === 'storm' ? 26 : it.type === 'strike' ? -16 : 12;
      const rect = [x - w / 2, y + off - h / 2, w, h];
      if (placed.some((q) => rect[0] < q[0] + q[2] && rect[0] + rect[2] > q[0] && rect[1] < q[1] + q[3] && rect[1] + rect[3] > q[1])) return;
      placed.push(rect);
      let el = labelEls.get(key);
      if (!el) {
        el = document.createElement('div');
        labelLayer.appendChild(el); labelEls.set(key, el);
      }
      el.className = 'gl-label ' + it.cls + (selected && it.id === selected ? ' sel' : '');
      if (el.textContent !== it.label) el.textContent = it.label;
      if (it.color) el.style.color = it.color;
      el.style.transform = `translate(${(x - w / 2).toFixed(1)}px, ${(y + off - h / 2).toFixed(1)}px)`;
      seen.add(key);
    });
    labelEls.forEach((el, key) => { if (!seen.has(key)) { el.remove(); labelEls.delete(key); } });
    // marco de "objetivo fijado"
    const lock = opts.lock;
    const target = selected && items.find((it) => it.id === selected && it.type !== 'track');
    if (lock && target && facing(target.pos)) {
      const [x, y] = project(target.pos);
      const s = 34 + Math.sin(performance.now() / 180) * 4;
      lock.style.display = 'block';
      lock.style.transform = `translate(${x - s}px, ${y - s}px)`;
      lock.style.width = lock.style.height = s * 2 + 'px';
    } else if (lock) lock.style.display = 'none';
  }

  // ---------- bucle ----------
  let sunTimer = 0;
  function frame(now) {
    if (!running) return;
    const dt = Math.min(0.05, (now - (last || now)) / 1000); last = now;
    const t = now / 1000;
    U.time.value = t;
    sunTimer += dt; if (sunTimer > 30) { sunTimer = 0; updateSun(); }
    clouds.rotation.y += dt * 0.004;
    U.cloudShift.value = -clouds.rotation.y / (Math.PI * 2);

    const idle = now - lastInteract;
    if (fly) {
      const k = clamp((now - fly.t0) / 1100, 0, 1), e = d3.easeCubicInOut(k);
      camLat = fly.lat0 + (fly.lat1 - fly.lat0) * e; camLon = fly.lon0 + (fly.lon1 - fly.lon0) * e; dist = fly.d0 + (fly.d1 - fly.d0) * e;
      if (k >= 1) fly = null;
    } else if (Math.abs(vel[0]) > 0.002 || Math.abs(vel[1]) > 0.002) {
      camLon += vel[0]; camLat = clamp(camLat + vel[1], -80, 80); vel = [vel[0] * 0.93, vel[1] * 0.93];
    } else if (idle > 4000 && !selected && !pointers.size) {
      camLon += dt * 2.2;
    }
    zs = clamp((dist - 1) / (fitDist - 1), 0.12, 1.25);
    scaled.forEach(([o, b]) => o.scale.setScalar(b * zs));
    ll2v(camLat, camLon, dist, camera.position);
    if (shake > 0.0001) {
      camera.position.add(new T.Vector3((Math.random() - 0.5) * shake, (Math.random() - 0.5) * shake, (Math.random() - 0.5) * shake));
      shake *= Math.pow(0.02, dt);
    }
    camera.up.set(0, 1, 0);
    camera.lookAt(0, 0, 0);

    animated.forEach((f) => f(t, dt));
    animateStrikes(t, dt);
    fire.update(dt); smoke.update(dt);
    arcsGroup.children.forEach((l) => { l.material.uniforms.dim.value = selected && l.userData.owner !== selected ? 0.15 : 1; });
    renderer.render(scene, camera);
    labelsFrame();
    if (opts.onFrame) opts.onFrame({ lat: camLat, lon: camLon, alt: (dist - 1) * 6371, t });
    requestAnimationFrame(frame);
  }

  function flyTo(lat, lon, id, d) {
    selected = id || null;
    let lon1 = lon; while (lon1 - camLon > 180) lon1 -= 360; while (lon1 - camLon < -180) lon1 += 360;
    fly = { t0: performance.now(), lat0: camLat, lon0: camLon, d0: dist, lat1: clamp(lat - 4, -75, 75), lon1, d1: d || Math.min(dist, 1 + (fitDist - 1) * 0.45) };
  }

  function setMode(m) {
    U.mode.value = m;
    borders.material.color.set(m > 1.5 ? 0xffffff : m > 0.5 ? 0x7dffd0 : 0xaac4ff);
    borders.material.opacity = m > 0.5 ? 0.32 : 0.2;
    grid.visible = m > 0.5;
  }
  setMode(1);

  return {
    start() { if (running) return; running = true; resize(); last = 0; requestAnimationFrame(frame); },
    stop() { running = false; },
    resize,
    rebuild,
    setMode,
    setLayer(k, on) { layers[k] = on; rebuild(); },
    select(id) { selected = id; },
    flyTo,
    view() { return { lat: camLat, lon: camLon, dist }; },
    sunLL
  };
};
