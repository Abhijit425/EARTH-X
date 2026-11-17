// Weather visualisation from a regular Open-Meteo grid (retrieved natively by Android).
// Everything drawn here is an *interpolated visualisation* of real model values at grid points.
import * as THREE from '../lib/three.module.min.js';
import { latLonToXYZ } from './geo.js';

const W = 1024, H = 512;
const TEMP_STOPS = [[-40, [140, 60, 200]], [-25, [70, 80, 220]], [-10, [60, 150, 240]], [0, [120, 210, 250]],
  [10, [120, 220, 140]], [20, [240, 230, 90]], [30, [250, 150, 50]], [40, [220, 40, 40]], [50, [140, 0, 30]]];
const PRECIP_STOPS = [[0, [0, 0, 0, 0]], [0.1, [90, 200, 255, 90]], [1, [40, 140, 255, 170]], [4, [40, 220, 120, 210]], [10, [250, 220, 40, 230]], [25, [250, 60, 60, 240]]];

function ramp(stops, v) {
  if (v <= stops[0][0]) return stops[0][1];
  for (let i = 1; i < stops.length; i++) {
    if (v <= stops[i][0]) {
      const [a, ca] = stops[i - 1], [b, cb] = stops[i];
      const t = (v - a) / (b - a);
      return ca.map((c, k) => c + (cb[k] - c) * t);
    }
  }
  return stops[stops.length - 1][1];
}

export const WEATHER_ICONS = { 0: '☀️', 1: '🌤️', 2: '⛅', 3: '☁️', 45: '🌫️', 48: '🌫️', 51: '🌦️', 53: '🌦️', 55: '🌧️', 56: '🌧️', 57: '🌧️',
  61: '🌧️', 63: '🌧️', 65: '🌧️', 66: '🌧️', 67: '🌧️', 71: '🌨️', 73: '🌨️', 75: '❄️', 77: '❄️', 80: '🌦️', 81: '🌧️', 82: '⛈️', 85: '🌨️', 86: '❄️', 95: '⛈️', 96: '⛈️', 99: '⛈️' };

export class Weather {
  constructor() {
    this.canvas = document.createElement('canvas');
    this.canvas.width = W; this.canvas.height = H;
    this.ctx = this.canvas.getContext('2d');
    this.tex = new THREE.CanvasTexture(this.canvas);
    this.tex.wrapS = THREE.RepeatWrapping;
    this.tex.colorSpace = THREE.SRGBColorSpace;
    this.grid = null;
    this.layer = 'none';
    this.particles = [];
    this.windStatic = null;
  }

  setGrid(g) {
    this.grid = g;
    this.nLat = g.lats.length; this.nLon = g.lons.length;
    this.lat0 = g.lats[0]; this.dLat = g.lats[1] - g.lats[0];
    this.lon0 = g.lons[0]; this.dLon = g.lons[1] - g.lons[0];
    // u/v wind components (m/s eastward / northward). Open-Meteo direction = where wind comes FROM.
    const n = g.wspd.length;
    this.u = new Float32Array(n); this.v = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      const s = (g.wspd[i] ?? 0) / 3.6, d = (g.wdir[i] ?? 0) * Math.PI / 180;
      this.u[i] = -s * Math.sin(d); this.v[i] = -s * Math.cos(d);
    }
    this.render();
  }

  /** bilinear sample on the regular grid with longitude wrap; returns NaN outside latitude coverage. */
  sample(arr, lat, lon) {
    const fy = (lat - this.lat0) / this.dLat;
    if (fy < 0 || fy > this.nLat - 1) {
      const row = fy < 0 ? 0 : this.nLat - 1;
      return this._rowSample(arr, row, lon);
    }
    const y0 = Math.floor(fy), y1 = Math.min(this.nLat - 1, y0 + 1), ty = fy - y0;
    return this._rowSample(arr, y0, lon) * (1 - ty) + this._rowSample(arr, y1, lon) * ty;
  }
  _rowSample(arr, row, lon) {
    let fx = ((lon - this.lon0) / this.dLon) % this.nLon;
    if (fx < 0) fx += this.nLon;
    const x0 = Math.floor(fx) % this.nLon, x1 = (x0 + 1) % this.nLon, tx = fx - Math.floor(fx);
    const a = arr[row * this.nLon + x0], b = arr[row * this.nLon + x1];
    if (a == null && b == null) return NaN;
    if (a == null) return b; if (b == null) return a;
    return a * (1 - tx) + b * tx;
  }

  setLayer(name) {
    this.layer = name;
    this.particles = [];
    this.render();
  }

  render() {
    const ctx = this.ctx;
    ctx.clearRect(0, 0, W, H);
    if (!this.grid || this.layer === 'none' || this.layer === 'markers') { this.tex.needsUpdate = true; return; }
    const sw = 256, sh = 128;
    const img = ctx.createImageData(sw, sh);
    const g = this.grid;
    for (let j = 0; j < sh; j++) {
      const lat = 90 - (j + 0.5) * 180 / sh;
      for (let i = 0; i < sw; i++) {
        const lon = -180 + (i + 0.5) * 360 / sw;
        let c = [0, 0, 0, 0];
        if (this.layer === 'temperature') {
          const t = this.sample(g.temp, lat, lon);
          if (!isNaN(t)) c = [...ramp(TEMP_STOPS, t), 175];
        } else if (this.layer === 'clouds') {
          const cc = this.sample(g.cloud, lat, lon);
          if (!isNaN(cc)) c = [245, 248, 255, Math.min(235, cc * 2.2)];
        } else if (this.layer === 'precipitation') {
          const p = this.sample(g.precip, lat, lon);
          if (!isNaN(p)) c = ramp(PRECIP_STOPS, p);
        } else if (this.layer === 'wind') {
          const s = this.sample(g.wspd, lat, lon);
          if (!isNaN(s)) { const k = Math.min(1, s / 60); c = [40 + 200 * k, 90 + 60 * k, 200 - 120 * k, 70 + 60 * k]; }
        }
        const o = (j * sw + i) * 4;
        img.data[o] = c[0]; img.data[o + 1] = c[1]; img.data[o + 2] = c[2]; img.data[o + 3] = c[3];
      }
    }
    // upscale smoothly
    const tmp = document.createElement('canvas'); tmp.width = sw; tmp.height = sh;
    tmp.getContext('2d').putImageData(img, 0, 0);
    ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(tmp, 0, 0, W, H);
    if (this.layer === 'wind') {
      this.windStatic = ctx.getImageData(0, 0, W, H);
      this._seedParticles();
    }
    this.tex.needsUpdate = true;
  }

  _seedParticles() {
    this.particles = [];
    for (let i = 0; i < 2600; i++) this.particles.push(this._newParticle());
  }
  _newParticle() {
    const lat = Math.asin(Math.random() * 2 - 1) * 180 / Math.PI * 0.95;
    return { lat, lon: Math.random() * 360 - 180, age: Math.random() * 80 | 0 };
  }

  /** animate wind particles; returns true if the texture changed */
  animate(dt) {
    if (this.layer !== 'wind' || !this.grid || !this.windStatic) return false;
    const ctx = this.ctx;
    // fade previous trails toward the static speed field
    ctx.globalCompositeOperation = 'source-over';
    ctx.globalAlpha = 0.12;
    ctx.putImageData(this.windStatic, 0, 0); // static background
    ctx.globalAlpha = 1;
    if (!this.trail) { this.trail = document.createElement('canvas'); this.trail.width = W; this.trail.height = H; this.tctx = this.trail.getContext('2d'); }
    const t = this.tctx;
    t.globalCompositeOperation = 'destination-in';
    t.fillStyle = 'rgba(0,0,0,0.90)'; t.fillRect(0, 0, W, H);
    t.globalCompositeOperation = 'source-over';
    t.strokeStyle = 'rgba(255,255,255,0.85)'; t.lineWidth = 1.1;
    t.beginPath();
    const k = Math.min(dt, 0.05) * 1.6;   // visual speed factor (deg per (m/s) per s)
    for (const p of this.particles) {
      const u = this.sample(this.u, p.lat, p.lon), v = this.sample(this.v, p.lat, p.lon);
      if (isNaN(u) || isNaN(v) || ++p.age > 90) { Object.assign(p, this._newParticle(), { age: 0 }); continue; }
      const x0 = (p.lon + 180) / 360 * W, y0 = (90 - p.lat) / 180 * H;
      p.lon += u * k / Math.max(0.2, Math.cos(p.lat * Math.PI / 180));
      p.lat += v * k;
      if (p.lon > 180) p.lon -= 360; if (p.lon < -180) p.lon += 360;
      if (Math.abs(p.lat) > 85) { Object.assign(p, this._newParticle()); continue; }
      const x1 = (p.lon + 180) / 360 * W, y1 = (90 - p.lat) / 180 * H;
      if (Math.abs(x1 - x0) < W / 2) { t.moveTo(x0, y0); t.lineTo(x1, y1); }
    }
    t.stroke();
    ctx.drawImage(this.trail, 0, 0);
    this.tex.needsUpdate = true;
    return true;
  }

  /** condition markers drawn on the label canvas */
  drawMarkers(ctx, camera, w, h) {
    if (this.layer !== 'markers' || !this.grid) return;
    const g = this.grid, cam = camera.position, cl = cam.length();
    const vv = new THREE.Vector3();
    ctx.font = '18px sans-serif';
    ctx.textAlign = 'center';
    const step = cl > 3 ? 2 : 1;
    for (let j = 0; j < this.nLat; j += step) for (let i = 0; i < this.nLon; i += step) {
      const idx = j * this.nLon + i;
      const code = g.code[idx];
      if (code == null) continue;
      const [x, y, z] = latLonToXYZ(g.lats[j], g.lons[i], 1.002);
      if ((x * cam.x + y * cam.y + z * cam.z) / cl < 1 / cl + 0.05) continue;
      vv.set(x, y, z).project(camera);
      const sx = (vv.x * 0.5 + 0.5) * w, sy = (-vv.y * 0.5 + 0.5) * h;
      ctx.fillText(WEATHER_ICONS[code] || '•', sx, sy + 6);
      const t = g.temp[idx];
      if (t != null) { ctx.font = '600 10px sans-serif'; ctx.fillStyle = '#fff'; ctx.fillText(Math.round(t) + '°', sx, sy + 20); ctx.font = '18px sans-serif'; }
    }
    ctx.textAlign = 'start';
  }
}
