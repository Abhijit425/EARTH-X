// 2D label overlay with zoom-aware filtering, horizon culling and collision avoidance.
import * as THREE from '../lib/three.module.min.js';
import { latLonToXYZ } from './geo.js';

const tmp = new THREE.Vector3();

export class Labels {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.items = [];          // {kind, name, lat, lon, prio, minAlt, maxAlt, ref, xyz}
    this.hits = [];           // on-screen tappable items
    this.enabled = { countries: true, cities: true, geography: true, states: true };
    this.dpr = 1;
    this.highlight = null;
  }

  resize(w, h, dpr) {
    this.dpr = dpr;
    this.canvas.width = Math.round(w * dpr); this.canvas.height = Math.round(h * dpr);
    this.canvas.style.width = w + 'px'; this.canvas.style.height = h + 'px';
    this.w = w; this.h = h;
  }

  _add(kind, name, lat, lon, prio, minAlt, maxAlt, ref) {
    this.items.push({ kind, name, lat, lon, prio, minAlt, maxAlt, ref, xyz: latLonToXYZ(lat, lon, 1.001) });
  }

  setData({ countries, cities, geography, curated }) {
    this.items = [];
    for (const c of curated.continents) this._add('continent', c.n.toUpperCase(), c.lat, c.lon, 100, 4500, 1e9, c);
    for (const g of geography) {
      if (g.k === 'ocean') this._add('ocean', g.n, g.lat, g.lon, 90 - g.r, 1500, 1e9, g);
      else if (g.k === 'sea') this._add('sea', g.n, g.lat, g.lon, 40 - g.r, 120, 6000 - g.r * 400, g);
      else if (g.k === 'mountain' || g.k === 'plateau') this._add('mountain', g.n, g.lat, g.lon, 45 - g.r * 2, 60, g.r <= 2 ? 14000 : 7000 - g.r * 500, g);
      else if (g.k === 'desert') this._add('desert', g.n, g.lat, g.lon, 40 - g.r * 2, 60, g.r <= 2 ? 12000 : 6000 - g.r * 400, g);
      else if (g.k === 'landmark') this._add('landmark', g.n, g.lat, g.lon, 20, 0, 3000, g);
      else if (g.r <= 4) this._add('region', g.n, g.lat, g.lon, 20 - g.r, 50, 3000, g);
    }
    for (const c of countries) {
      const size = Math.sqrt(c.area || 1);
      const maxAlt = size > 1500 ? 16000 : size > 700 ? 11000 : size > 250 ? 7000 : 3500;
      this._add('country', c.short || c.name, c.lat, c.lon, 60 + Math.log10(c.area || 10) * 5, 250, maxAlt, c);
    }
    cities.forEach((c, i) => {
      const p = c.pop || 0;
      const maxAlt = c.cap ? 6000 : p > 8e6 ? 6000 : p > 3e6 ? 3500 : p > 1e6 ? 2000 : p > 5e5 ? 1000 : 500;
      this._add('city', c.n, c.lat, c.lon, (c.cap ? 30 : 0) + Math.log10(p + 10) * 4, 0, maxAlt, { ...c, index: i });
    });
    for (const p of curated.peaks) this._add('peak', `${p.n} ▲${p.elev.toLocaleString()} m`, p.lat, p.lon, 28, 0, 2500, p);
    for (const t of curated.trenches) this._add('trench', `${t.n} ▼${t.depth.toLocaleString()} m`, t.lat, t.lon, 26, 0, 4000, t);
    this.items.sort((a, b) => b.prio - a.prio);
  }

  setStates(states) {
    this.items = this.items.filter(i => i.kind !== 'state');
    for (const s of states) if (s.n) this._add('state', s.n, s.lat, s.lon, 22, 0, 1400, s);
    this.items.sort((a, b) => b.prio - a.prio);
  }

  draw(camera, altKm, opts) {
    const ctx = this.ctx, dpr = this.dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, this.w, this.h);
    this.hits = [];
    if (!opts.visible) return;
    const camPos = camera.position;
    const camLen = camPos.length();
    const occupied = [];
    const W = this.w, H = this.h;
    let drawn = 0;
    const limit = 220;
    for (const it of this.items) {
      if (drawn > limit) break;
      if (altKm < it.minAlt || altKm > it.maxAlt) continue;
      const k = it.kind;
      if ((k === 'country' || k === 'continent') && !this.enabled.countries) continue;
      if (k === 'city' && !this.enabled.cities) continue;
      if (k === 'state' && !this.enabled.states) continue;
      if ((k === 'mountain' || k === 'desert' || k === 'ocean' || k === 'sea' || k === 'region' || k === 'peak' || k === 'trench' || k === 'landmark') && !this.enabled.geography) continue;
      const [x, y, z] = it.xyz;
      // horizon test: point visible if dot(p, cam) > 1 (for unit sphere)
      const facing = (x * camPos.x + y * camPos.y + z * camPos.z) / camLen;
      if (facing < 1 / camLen + 0.02) continue;
      tmp.set(x, y, z).project(camera);
      if (tmp.z > 1) continue;
      const sx = (tmp.x * 0.5 + 0.5) * W, sy = (-tmp.y * 0.5 + 0.5) * H;
      if (sx < -50 || sx > W + 50 || sy < -20 || sy > H + 20) continue;
      const edgeFade = Math.min(1, (facing - 1 / camLen) * 6);
      const style = STYLES[k];
      ctx.font = style.font;
      const text = it.name;
      const tw = ctx.measureText(text).width + (style.letter ? text.length * style.letter : 0);
      const hasDot = style.dot;
      const bx = hasDot ? sx + 6 : sx - tw / 2, by = hasDot ? sy - 7 : sy - 8;
      const box = [bx - 3, by - 3, bx + tw + 3, by + 17];
      if (occupied.some(o => !(box[2] < o[0] || box[0] > o[2] || box[3] < o[1] || box[1] > o[3]))) {
        if (k === 'city' && altKm < 900) this._dot(ctx, sx, sy, it, 0.5 * edgeFade);
        continue;
      }
      occupied.push(box);
      ctx.globalAlpha = edgeFade * (this.highlight && this.highlight === it.ref ? 1 : style.alpha);
      if (hasDot) this._dot(ctx, sx, sy, it, edgeFade);
      ctx.fillStyle = style.color;
      ctx.shadowColor = 'rgba(0,0,0,0.85)'; ctx.shadowBlur = 4;
      if (style.letter) {
        let cx = bx;
        for (const ch of text) { ctx.fillText(ch, cx, by + 12); cx += ctx.measureText(ch).width + style.letter; }
      } else ctx.fillText(text, bx, by + 12);
      ctx.shadowBlur = 0;
      ctx.globalAlpha = 1;
      if (k === 'city' || k === 'peak' || k === 'trench' || k === 'landmark') this.hits.push({ x: sx, y: sy, item: it });
      drawn++;
    }
  }

  _dot(ctx, x, y, it, a) {
    ctx.globalAlpha = a;
    const cap = it.ref && it.ref.cap;
    ctx.fillStyle = it.kind === 'peak' ? '#ffb347' : it.kind === 'trench' ? '#7fd3ff' : cap ? '#ffd166' : '#ffffff';
    ctx.beginPath(); ctx.arc(x, y, cap ? 3.4 : 2.4, 0, Math.PI * 2); ctx.fill();
    if (cap) { ctx.strokeStyle = 'rgba(255,209,102,0.7)'; ctx.lineWidth = 1; ctx.beginPath(); ctx.arc(x, y, 6, 0, Math.PI * 2); ctx.stroke(); }
  }

  hitTest(x, y, radius = 22) {
    let best = null, bd = radius;
    for (const h of this.hits) {
      const d = Math.hypot(h.x - x, h.y - y);
      if (d < bd) { bd = d; best = h.item; }
    }
    return best;
  }
}

const SANS = '"Inter","Roboto","Segoe UI",sans-serif';
const STYLES = {
  continent: { font: `600 15px ${SANS}`, color: '#ffffff', alpha: 0.75, letter: 3 },
  ocean: { font: `italic 500 14px ${SANS}`, color: '#9fd4ff', alpha: 0.75, letter: 1.5 },
  sea: { font: `italic 12px ${SANS}`, color: '#8cc8f5', alpha: 0.75 },
  country: { font: `600 12.5px ${SANS}`, color: '#ffffff', alpha: 0.92, letter: 1.2 },
  state: { font: `500 11px ${SANS}`, color: '#d9e6f2', alpha: 0.75 },
  city: { font: `500 12px ${SANS}`, color: '#f4f7fb', alpha: 0.95, dot: true },
  mountain: { font: `italic 600 12px ${SANS}`, color: '#ffc27a', alpha: 0.9 },
  desert: { font: `italic 12px ${SANS}`, color: '#f2d39b', alpha: 0.85 },
  region: { font: `italic 11px ${SANS}`, color: '#cfe3c2', alpha: 0.8 },
  peak: { font: `600 11.5px ${SANS}`, color: '#ffd8a8', alpha: 0.95, dot: true },
  trench: { font: `600 11.5px ${SANS}`, color: '#a8e1ff', alpha: 0.95, dot: true },
  landmark: { font: `600 11.5px ${SANS}`, color: '#c8f7c5', alpha: 0.95, dot: true },
};
