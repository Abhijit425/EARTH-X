// EARTH-X 3D engine entry point. Runs inside an Android WebView (or any WebGL2 browser for testing).
// Communication: Android -> JS via window.EarthX.cmd(json); JS -> Android via AndroidBridge.postMessage(json).
import * as THREE from '../lib/three.module.min.js';
import { GlobeControls } from './controls.js';
import { Vectors } from './vectors.js';
import { Labels } from './labels.js';
import { Quakes } from './quakes.js';
import { Weather } from './weather.js';
import { Imagery } from './imagery.js';
import * as G from './geo.js';
import * as S from './shaders.js';

const params = new URLSearchParams(location.search);
const CFG = {
  quality: params.get('quality') || 'high',          // low | high | ultra
  has8k: params.get('has8k') === '1',
  lowPower: params.get('lowPower') === '1',
  netBase: params.get('net') || 'https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi?',
  online: params.get('online') !== '0',
};

// ------------------------------------------------------------------ bridge
function emit(type, payload = {}) {
  const msg = JSON.stringify({ type, payload });
  if (window.AndroidBridge && window.AndroidBridge.postMessage) window.AndroidBridge.postMessage(msg);
  else if (window.__earthxLog) window.__earthxLog.push(msg);
}
window.__earthxLog = window.AndroidBridge ? null : [];
window.addEventListener('error', e => emit('error', { msg: String(e.message || e) }));
window.addEventListener('unhandledrejection', e => emit('error', { msg: String(e.reason && e.reason.message || e.reason) }));

// ------------------------------------------------------------------ renderer
const container = document.getElementById('globe');
const labelCanvas = document.getElementById('labels');
emit('progress', { stage: 'boot', pct: 5 });
let renderer;
try {
  renderer = new THREE.WebGLRenderer({ antialias: CFG.quality !== 'low', alpha: false, powerPreference: CFG.lowPower ? 'low-power' : 'high-performance', preserveDrawingBuffer: false });
} catch (e) {
  emit('error', { msg: 'FATAL: WebGL 2 is not available in this device\'s WebView (' + (e && e.message || e) + '). Update "Android System WebView" / Chrome from the Play Store.' });
  throw e;
}
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.05;
renderer.setClearColor(0x000003, 1);
renderer.autoClear = false;
container.appendChild(renderer.domElement);
const gl = renderer.getContext();
const maxTex = gl.getParameter(gl.MAX_TEXTURE_SIZE);
const maxAniso = renderer.capabilities.getMaxAnisotropy();

const camera = new THREE.PerspectiveCamera(40, 1, 0.01, 20);
const scene = new THREE.Scene();
const skyScene = new THREE.Scene();
const skyCam = new THREE.PerspectiveCamera(40, 1, 0.1, 1000);

let basePixelRatio = Math.min(window.devicePixelRatio || 1, CFG.quality === 'ultra' ? 2.5 : CFG.quality === 'high' ? 2 : 1.25);
let pixelRatio = basePixelRatio;
let dirty = true;
const markDirty = () => { dirty = true; };

// ------------------------------------------------------------------ sun & time
const sunDir = new THREE.Vector3(1, 0, 0);
const time = { simMs: Date.now(), speed: 1, playing: true, live: true, lastEmit: 0 };
function updateSun() {
  const s = G.subsolarPoint(time.simMs);
  const [x, y, z] = G.latLonToXYZ(s.lat, s.lon);
  sunDir.set(x, y, z).normalize();
  sunSprite.position.copy(sunDir).multiplyScalar(400);
  return s;
}

// ------------------------------------------------------------------ textures
const loader = new THREE.TextureLoader();
function placeholder(rgba) { const t = new THREE.DataTexture(new Uint8Array(rgba), 1, 1); t.needsUpdate = true; return t; }
const black = placeholder([0, 0, 0, 255]);
const flatNormal = placeholder([128, 128, 255, 255]);
const clear = placeholder([0, 0, 0, 0]);
function loadTex(path, srgb = true) {
  return new Promise(res => loader.load(path, t => {
    t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
    t.anisotropy = Math.min(8, maxAniso);
    t.wrapS = THREE.RepeatWrapping;
    t.minFilter = THREE.LinearMipmapLinearFilter;
    res(t);
  }, undefined, () => res(null)));
}

// ------------------------------------------------------------------ globe
const segs = CFG.quality === 'low' ? [128, 64] : [256, 128];
const earthGeo = new THREE.SphereGeometry(1, segs[0], segs[1]);
const earthMat = new THREE.ShaderMaterial({
  vertexShader: S.earthVertex, fragmentShader: S.earthFragment,
  uniforms: {
    dayMap: { value: black }, nightMap: { value: black }, brcMap: { value: black }, normalMap: { value: flatNormal },
    waterMap: { value: black }, elevMap: { value: black }, satMap: { value: black }, overlayMap: { value: clear },
    maskMap: { value: black }, sunDir: { value: sunDir }, satMix: { value: 0 }, overlayOpacity: { value: 0.85 },
    nightEnabled: { value: 1 }, lightsIntensity: { value: 1.0 }, cloudShadow: { value: 1 }, cloudU: { value: 0 },
    elevMode: { value: 0 }, normalStrength: { value: 1.0 }, selPulse: { value: 0 }, ambient: { value: 0.06 },
  },
});
const earth = new THREE.Mesh(earthGeo, earthMat);
scene.add(earth);

const cloudMat = new THREE.ShaderMaterial({
  vertexShader: S.cloudVertex, fragmentShader: S.cloudFragment,
  uniforms: { brcMap: { value: black }, sunDir: { value: sunDir }, opacity: { value: 0 } },
  transparent: true, depthWrite: false,
});
const clouds = new THREE.Mesh(new THREE.SphereGeometry(1.0065, segs[0] / 2, segs[1] / 2), cloudMat);
clouds.renderOrder = 8;
scene.add(clouds);

const atmoMat = new THREE.ShaderMaterial({
  vertexShader: S.atmoVertex, fragmentShader: S.atmoFragment,
  uniforms: { sunDir: { value: sunDir }, strength: { value: 1.5 }, scaleH: { value: 0.016 } },
  side: THREE.BackSide, blending: THREE.AdditiveBlending, transparent: true, depthWrite: false,
});
const atmosphere = new THREE.Mesh(new THREE.SphereGeometry(1.08, 96, 48), atmoMat);
atmosphere.renderOrder = 9;
scene.add(atmosphere);

// stars & sun in their own scene (no depth conflicts with the near plane)
const starMat = new THREE.MeshBasicMaterial({ map: black, side: THREE.BackSide, depthWrite: false, color: new THREE.Color(0.55, 0.55, 0.6) });
const stars = new THREE.Mesh(new THREE.SphereGeometry(500, 64, 32), starMat);
skyScene.add(stars);
const sunCanvas = document.createElement('canvas'); sunCanvas.width = sunCanvas.height = 256;
{ const c = sunCanvas.getContext('2d'); const g = c.createRadialGradient(128, 128, 0, 128, 128, 128);
  g.addColorStop(0, 'rgba(255,255,250,1)'); g.addColorStop(0.08, 'rgba(255,248,220,0.95)'); g.addColorStop(0.25, 'rgba(255,200,120,0.25)'); g.addColorStop(1, 'rgba(255,160,60,0)');
  c.fillStyle = g; c.fillRect(0, 0, 256, 256); }
const sunTex = new THREE.CanvasTexture(sunCanvas); sunTex.colorSpace = THREE.SRGBColorSpace;
const sunSprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: sunTex, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true }));
sunSprite.scale.set(70, 70, 1);
skyScene.add(sunSprite);

// ------------------------------------------------------------------ modules
const vectors = new Vectors(scene, CFG.quality === 'low' ? 1024 : 2048);
earthMat.uniforms.maskMap.value = vectors.maskTex;
const labels = new Labels(labelCanvas);
const quakes = new Quakes(scene);
const weather = new Weather();
earthMat.uniforms.overlayMap.value = weather.tex;
const imagery = new Imagery(scene, { netBase: CFG.netBase, onStatus: s => emit('imagery', s) });
imagery.sunDir = sunDir;
imagery.online = CFG.online;
imagery.maxSize = CFG.quality === 'low' ? 2048 : 4096;
imagery.patchSize = CFG.quality === 'ultra' ? 2048 : CFG.quality === 'high' ? 2048 : 1024;

const state = {
  labelsVisible: true, statesEnabled: true, cloudsEnabled: true, cloudTarget: 0.9, satTarget: 0,
  measure: false, measurePts: [], measureObj: null, data: null, selCountry: null, rotateCloudsSpeed: 0.0035,
};

// ------------------------------------------------------------------ controls & picking
const ray = new THREE.Raycaster();
const ndc = new THREE.Vector2();
function screenToLatLon(x, y) {
  const r = renderer.domElement.getBoundingClientRect();
  ndc.set(((x - r.left) / r.width) * 2 - 1, -((y - r.top) / r.height) * 2 + 1);
  ray.setFromCamera(ndc, camera);
  const o = ray.ray.origin, d = ray.ray.direction;
  const b = o.dot(d), c = o.lengthSq() - 1, disc = b * b - c;
  if (disc < 0) return null;
  const t = -b - Math.sqrt(disc);
  const p = o.clone().addScaledVector(d, t);
  return G.xyzToLatLon(p.x, p.y, p.z);
}

let lastCamEmit = 0, settleTimer = null;
const controls = new GlobeControls(camera, renderer.domElement, {
  onChange: () => {
    markDirty();
    const now = performance.now();
    if (now - lastCamEmit > 180) { lastCamEmit = now; emitCamera(); }
    clearTimeout(settleTimer);
    settleTimer = setTimeout(onSettled, 380);
  },
  onInteract: () => emit('interact'),
  onTap: (x, y) => handleTap(x, y),
  onDoubleTap: (x, y) => {
    const ll = screenToLatLon(x, y);
    if (ll) controls.flyTo(ll.lat, ll.lon, 1 + (controls.dist - 1) * 0.38, 900);
  },
});

function emitCamera() {
  const alt = G.altitudeKmFromDistance(controls.dist);
  emit('camera', { lat: +controls.lat.toFixed(4), lon: +controls.lon.toFixed(4), altKm: Math.round(alt), level: G.zoomLevel(alt), levelName: G.LEVEL_NAMES[G.zoomLevel(alt)] });
}
function onSettled() {
  emitCamera();
  const alt = G.altitudeKmFromDistance(controls.dist);
  imagery.updateDetail(controls.lat, controls.lon, alt);
  if (alt < 2600 && !vectors.states && state.data) loadStates();
}

function handleTap(x, y) {
  const r = renderer.domElement.getBoundingClientRect();
  const lx = x - r.left, ly = y - r.top;
  const ll = screenToLatLon(x, y);
  if (state.measure) { if (ll) addMeasurePoint(ll); return; }
  const q = quakes.pick(camera, lx, ly, r.width, r.height);
  if (q) { quakes.select(q.id); emit('quakeTapped', { id: q.id }); markDirty(); return; }
  const hit = labels.hitTest(lx, ly);
  if (hit && hit.kind === 'city') { emit('cityTapped', { index: hit.ref.index, name: hit.ref.n, lat: hit.ref.lat, lon: hit.ref.lon }); return; }
  if (hit && (hit.kind === 'peak' || hit.kind === 'trench' || hit.kind === 'landmark')) { emit('placeTapped', { name: hit.ref.n, kind: hit.kind, lat: hit.lat, lon: hit.lon }); return; }
  if (!ll) { emit('spaceTapped'); return; }
  const id = vectors.countryAt(ll.lat, ll.lon);
  const alt = G.altitudeKmFromDistance(controls.dist);
  let st = null;
  if (id && alt < 2600 && vectors.stateList) st = vectors.stateAt(ll.lat, ll.lon, id);
  if (id) selectCountry(id, false);
  emit('locationTapped', { lat: +ll.lat.toFixed(4), lon: +ll.lon.toFixed(4), countryId: id, state: st ? { name: st.n, type: st.t } : null });
}

function selectCountry(id, fly) {
  state.selCountry = id;
  vectors.select(id);
  markDirty();
  if (fly && id && state.data) {
    const c = state.data.countries.find(c => c.id === id);
    if (c) flyToBBox(c.bbox, c.lat, c.lon);
  }
}

function flyToBBox(b, lat, lon) {
  let w = b[2] - b[0], h = b[3] - b[1];
  if (w > 300) w = 60; // antimeridian-spanning (e.g. Russia, Fiji): use a sensible span
  const span = Math.max(w * Math.cos((lat || 0) * Math.PI / 180), h, 1.2);
  const altKm = Math.min(16000, Math.max(250, span * 111 * 1.35));
  controls.flyTo(lat, lon, 1 + altKm / G.EARTH_RADIUS_KM);
}

// ------------------------------------------------------------------ measurement
function addMeasurePoint(ll) {
  if (state.measurePts.length >= 2) clearMeasure();
  state.measurePts.push(ll);
  if (!state.measureObj) { state.measureObj = new THREE.Group(); scene.add(state.measureObj); }
  const dot = new THREE.Mesh(new THREE.SphereGeometry(0.004 * Math.max(0.15, controls.dist - 1), 12, 8), new THREE.MeshBasicMaterial({ color: 0x5ce1ff }));
  dot.position.set(...G.latLonToXYZ(ll.lat, ll.lon, 1.002));
  state.measureObj.add(dot);
  if (state.measurePts.length === 2) {
    const [a, b] = state.measurePts;
    const pts = G.greatCirclePoints(a.lat, a.lon, b.lat, b.lon, 128).map(p => new THREE.Vector3(...p).multiplyScalar(1.0025));
    const line = new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), new THREE.LineBasicMaterial({ color: 0x5ce1ff, transparent: true, opacity: 0.95 }));
    line.renderOrder = 7;
    state.measureObj.add(line);
    emit('measure', { a, b, km: G.haversineKm(a.lat, a.lon, b.lat, b.lon), bearing: G.bearingDeg(a.lat, a.lon, b.lat, b.lon) });
  } else emit('measure', { a: ll, b: null, km: null });
  markDirty();
}
function clearMeasure() {
  state.measurePts = [];
  if (state.measureObj) { scene.remove(state.measureObj); state.measureObj.traverse(o => { o.geometry && o.geometry.dispose(); o.material && o.material.dispose(); }); state.measureObj = null; }
  markDirty();
}

// ------------------------------------------------------------------ data
async function getJSON(p) { const r = await fetch(p); if (!r.ok) throw new Error(p + ' ' + r.status); return r.json(); }
async function loadData() {
  const [countries, shapes, cities, geography, curated, mountains, plates] = await Promise.all([
    getJSON('data/countries.json'), getJSON('data/countries.geo.json'), getJSON('data/cities.json'),
    getJSON('data/geography.json'), getJSON('data/curated.json'), getJSON('data/mountains.geo.json'), getJSON('data/plates.geo.json')]);
  state.data = { countries, cities, geography, curated };
  vectors.loadCountries(shapes);
  vectors.loadMountains(mountains);
  vectors.loadPlates(plates);
  labels.setData(state.data);
  if (state.selCountry) vectors.select(state.selCountry);
  markDirty();
}
let statesLoading = false;
async function loadStates() {
  if (statesLoading) return; statesLoading = true;
  try { const st = await getJSON('data/states.geo.json'); vectors.loadStates(st); labels.setStates(st); markDirty(); }
  catch (e) { emit('error', { msg: 'states: ' + e.message }); }
}

async function loadTextures() {
  const q = CFG.quality;
  // 1) fast first frame
  const quick = await loadTex('textures/earth_day_1k.jpg');
  if (quick) { earthMat.uniforms.dayMap.value = quick; markDirty(); }
  emit('progress', { stage: 'textures', pct: 25 });
  // 2) surface detail
  const [water, normal, elev, starsT] = await Promise.all([
    loadTex('textures/earth_water_2k.jpg', false), loadTex('textures/earth_normal_2k.jpg', false),
    loadTex('textures/earth_elev_2k.jpg', false), loadTex('textures/stars_4k.jpg')]);
  if (water) earthMat.uniforms.waterMap.value = water;
  if (normal) earthMat.uniforms.normalMap.value = normal;
  if (elev) earthMat.uniforms.elevMap.value = elev;
  if (starsT) { starMat.map = starsT; starMat.needsUpdate = true; }
  markDirty();
  emit('progress', { stage: 'textures', pct: 50 });
  if (q === 'low') { emit('ready', { maxTex, quality: q }); return; }
  // 3) HD layers
  // 8K needs ~170 MB of GPU memory for the day map alone: only on capable devices, night stays 4K.
  const mem = navigator.deviceMemory || 4;
  const use8k = q === 'ultra' && CFG.has8k && maxTex >= 8192 && mem >= 4;
  const [day, night, brc] = await Promise.all([
    loadTex(use8k ? 'textures/earth_day_8k.jpg' : 'textures/earth_day_4k.jpg'),
    loadTex('textures/earth_night_4k.jpg'),
    loadTex('textures/earth_brc_4k.jpg', false)]);
  if (!day) emit('error', { msg: 'Could not load the Earth texture' + (use8k ? ' (8K)' : '') });
  if (day) { earthMat.uniforms.dayMap.value = day; if (quick) quick.dispose(); }
  if (night) earthMat.uniforms.nightMap.value = night;
  if (brc) { earthMat.uniforms.brcMap.value = brc; cloudMat.uniforms.brcMap.value = brc; }
  markDirty();
  emit('progress', { stage: 'textures', pct: 100 });
  emit('ready', { maxTex, quality: q, ultra8k: use8k });
}

// ------------------------------------------------------------------ commands from Android
const commands = {
  flyTo: p => controls.flyTo(+p.lat, +p.lon, p.altKm != null ? 1 + (+p.altKm) / G.EARTH_RADIUS_KM : controls.dist),
  flyToBBox: p => flyToBBox(p.bbox, +p.lat, +p.lon),
  selectCountry: p => selectCountry(p.id || null, !!p.fly),
  resetCamera: () => controls.reset(),
  zoom: p => controls.zoomAnimated(p.dir > 0 ? 0.55 : 1.8),
  rotate: p => controls.rotateBy(+p.dLon || 0, +p.dLat || 0),
  autoRotate: p => { controls.autoRotate = p.on ? 4 : 0; markDirty(); },
  setTime: p => { time.simMs = +p.utcMs; time.live = false; updateSun(); markDirty(); },
  setPlayback: p => { if (p.playing != null) time.playing = !!p.playing; if (p.speed != null) time.speed = +p.speed; if (p.live) { time.live = true; time.simMs = Date.now(); time.speed = 1; time.playing = true; } markDirty(); },
  setNight: p => { earthMat.uniforms.nightEnabled.value = p.on ? 1 : 0; imagery.setNight(!!p.on); markDirty(); },
  setLights: p => { earthMat.uniforms.lightsIntensity.value = +p.v; markDirty(); },
  setClouds: p => { state.cloudsEnabled = !!p.on; markDirty(); },
  setAtmosphere: p => { atmosphere.visible = !!p.on; markDirty(); },
  setBorders: p => { vectors.setBorders(!!p.on); markDirty(); },
  setStates: p => { state.statesEnabled = !!p.on; markDirty(); },
  setLabels: p => { Object.assign(labels.enabled, p); state.labelsVisible = p.all !== false; markDirty(); },
  setMountains: p => { vectors.setMountains(!!p.on); markDirty(); },
  setPlates: p => { vectors.setPlates(!!p.on); markDirty(); },
  setElevation: p => { earthMat.uniforms.elevMode.value = p.on ? 1 : 0; markDirty(); },
  setQuakes: p => { quakes.set(p.events || []); markDirty(); },
  showQuakes: p => { quakes.setVisible(!!p.on); markDirty(); },
  selectQuake: p => { quakes.select(p.id); markDirty(); },
  setWeatherGrid: p => { weather.setGrid(p); markDirty(); },
  setWeatherLayer: p => { weather.setLayer(p.layer || 'none'); earthMat.uniforms.overlayOpacity.value = p.layer === 'none' ? 0 : (p.opacity ?? 0.8); markDirty(); },
  setSatellite: p => {
    imagery.satEnabled = !!p.on;
    if (p.layer) imagery.layer = p.layer;
    if (p.date !== undefined) imagery.date = p.date;
    imagery.lastReq = null;
    if (p.on) imagery.loadGlobal(t => { earthMat.uniforms.satMap.value = t; state.satTarget = 1; markDirty(); });
    else state.satTarget = 0;
    onSettled();
  },
  setDetail: p => { imagery.detailEnabled = !!p.on; imagery.lastReq = null; onSettled(); },
  setOnline: p => { imagery.online = !!p.on; if (!p.on) imagery.clearDetail(); else { imagery.lastReq = null; onSettled(); } },
  measure: p => { state.measure = !!p.on; if (!p.on) clearMeasure(); },
  clearMeasure: () => clearMeasure(),
  setLowPower: p => { CFG.lowPower = !!p.on; markDirty(); },
  setResolution: p => { basePixelRatio = Math.min(window.devicePixelRatio || 1, +p.ratio || 1); pixelRatio = basePixelRatio; resize(); },
  highlightLabel: p => { labels.highlight = p.ref || null; markDirty(); },
  screenshot: () => takeScreenshot(),
  pause: () => { paused = true; },
  resume: () => { paused = false; markDirty(); last = performance.now(); requestAnimationFrame(frame); },
};
window.EarthX = {
  cmd(json) {
    try {
      const m = typeof json === 'string' ? JSON.parse(json) : json;
      const f = commands[m.type];
      if (f) f(m.payload || {}); else emit('error', { msg: 'unknown command ' + m.type });
    } catch (e) { emit('error', { msg: 'cmd failed: ' + e.message }); }
  },
  debug: { controls, camera, state, time },
};

function takeScreenshot() {
  render(0);
  const out = document.createElement('canvas');
  out.width = renderer.domElement.width; out.height = renderer.domElement.height;
  const c = out.getContext('2d');
  c.drawImage(renderer.domElement, 0, 0);
  c.drawImage(labelCanvas, 0, 0, out.width, out.height);
  c.font = `${Math.round(14 * pixelRatio)}px sans-serif`; c.fillStyle = 'rgba(255,255,255,0.7)';
  c.fillText('EARTH-X · ' + new Date(time.simMs).toISOString().slice(0, 16).replace('T', ' ') + ' UTC', 16 * pixelRatio, out.height - 16 * pixelRatio);
  emit('screenshot', { dataUrl: out.toDataURL('image/jpeg', 0.92) });
}

// ------------------------------------------------------------------ resize / context loss
function resize() {
  const w = container.clientWidth, h = container.clientHeight;
  renderer.setPixelRatio(pixelRatio);
  renderer.setSize(w, h, false);
  renderer.domElement.style.width = w + 'px'; renderer.domElement.style.height = h + 'px';
  camera.aspect = skyCam.aspect = w / Math.max(1, h);
  camera.updateProjectionMatrix(); skyCam.updateProjectionMatrix();
  labels.resize(w, h, Math.min(2, window.devicePixelRatio || 1));
  markDirty();
}
window.addEventListener('resize', resize);
renderer.domElement.addEventListener('webglcontextlost', e => { e.preventDefault(); paused = true; emit('contextLost'); });
renderer.domElement.addEventListener('webglcontextrestored', () => { paused = false; emit('contextRestored'); markDirty(); last = performance.now(); requestAnimationFrame(frame); });
document.addEventListener('visibilitychange', () => { if (!document.hidden) { markDirty(); last = performance.now(); } });

// ------------------------------------------------------------------ main loop
let lastLive = 0, last = performance.now(), paused = false, frameTimes = [], lastAdapt = 0, fpsAcc = 0, fpsN = 0, lastFpsEmit = 0;
function render(t) {
  skyCam.quaternion.copy(camera.quaternion);
  renderer.clear();
  renderer.render(skyScene, skyCam);
  renderer.clearDepth();
  renderer.render(scene, camera);
  const alt = G.altitudeKmFromDistance(controls.dist);
  labels.draw(camera, alt, { visible: state.labelsVisible });
  weather.drawMarkers(labels.ctx, camera, labels.w, labels.h);
}

function frame(now) {
  if (paused) return;
  requestAnimationFrame(frame);
  const dt = Math.min(0.1, (now - last) / 1000);
  if (CFG.lowPower && now - last < 32) return;     // ~30 fps cap
  last = now;
  let anim = controls.update(dt);

  // simulation clock
  if (time.live) { time.simMs = Date.now(); }
  else if (time.playing) { time.simMs += dt * 1000 * time.speed; anim = anim || time.speed > 60; }
  if (now - time.lastEmit > 250) { time.lastEmit = now; emit('time', { utcMs: Math.round(time.simMs), playing: time.playing, speed: time.speed, live: time.live }); }
  const sub = updateSun();
  if (time.speed > 1 && time.playing && !time.live) dirty = true;

  // clouds drift + fade
  const alt = G.altitudeKmFromDistance(controls.dist);
  clouds.rotation.y += dt * state.rotateCloudsSpeed * (time.live ? 1 : Math.min(50, time.speed / 30 + 1));
  earthMat.uniforms.cloudU.value = (clouds.rotation.y / (Math.PI * 2)) % 1;
  const satActive = state.satTarget > 0.5 && imagery.layer.includes('TrueColor');
  const cloudGoal = state.cloudsEnabled && !satActive ? (alt < 450 ? 0 : alt < 2500 ? (alt - 450) / 2050 * 0.9 : 0.9) : 0;
  const cu = cloudMat.uniforms.opacity;
  if (Math.abs(cu.value - cloudGoal) > 0.01) { cu.value += (cloudGoal - cu.value) * Math.min(1, dt * 3); anim = true; }
  earthMat.uniforms.cloudShadow.value = cu.value;
  const sm = earthMat.uniforms.satMix;
  if (Math.abs(sm.value - state.satTarget) > 0.01) { sm.value += (state.satTarget - sm.value) * Math.min(1, dt * 2.5); anim = true; }
  earthMat.uniforms.normalStrength.value = alt < 400 ? 0.4 : 1.0;
  atmoMat.uniforms.strength.value = alt < 800 ? 0.8 : 1.5;

  vectors.updateForAltitude(alt, state.statesEnabled);
  if (vectors.selected) { earthMat.uniforms.selPulse.value = Math.sin(now / 1000 * 3) * 0.5 + 0.5; vectors.pulse(now / 1000); anim = true; }
  if (quakes.visible && quakes.list.length) { quakes.update(now / 1000, pixelRatio); anim = true; }
  if (imagery.animate(dt)) anim = true;
  if (weather.animate(dt)) anim = true;

  if (time.live && now - lastLive > 1000) { lastLive = now; dirty = true; }
  if (!(dirty || anim)) return;
  dirty = false;
  render(now);
  frameTimes.push(dt * 1000);
  fpsAcc += dt; fpsN++;
  if (now - lastFpsEmit > 2000) { emit('fps', { fps: Math.round(fpsN / Math.max(0.001, fpsAcc)), pixelRatio: +pixelRatio.toFixed(2) }); fpsAcc = 0; fpsN = 0; lastFpsEmit = now; }
  // adaptive resolution: if sustained frame time > 24 ms, lower pixel ratio; recover when fast.
  if (frameTimes.length > 45 && now - lastAdapt > 2500) {
    const avg = frameTimes.reduce((a, b) => a + b, 0) / frameTimes.length;
    frameTimes = [];
    lastAdapt = now;
    if (avg > 24 && pixelRatio > 0.75) { pixelRatio = Math.max(0.75, pixelRatio * 0.85); resize(); }
    else if (avg < 15 && pixelRatio < basePixelRatio) { pixelRatio = Math.min(basePixelRatio, pixelRatio * 1.1); resize(); }
  }
}

// ------------------------------------------------------------------ boot
resize();
updateSun();
requestAnimationFrame(frame);
loadTextures().catch(e => emit('error', { msg: 'textures: ' + e.message }));
loadData().then(() => emit('dataReady')).catch(e => emit('error', { msg: 'data: ' + e.message }));
emitCamera();
