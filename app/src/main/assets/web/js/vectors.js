// Vector geography: country & state borders, mountain outlines, tectonic plates,
// selection mask rasterisation and point-in-polygon picking.
import * as THREE from '../lib/three.module.min.js';
import { latLonToXYZ, unwrapRing, pointInPolygons } from './geo.js';

function ringsToSegments(rings, r, out) {
  for (const ring of rings) {
    for (let i = 0; i < ring.length - 1; i++) {
      const a = latLonToXYZ(ring[i][1], ring[i][0], r);
      const b = latLonToXYZ(ring[i + 1][1], ring[i + 1][0], r);
      out.push(a[0], a[1], a[2], b[0], b[1], b[2]);
    }
  }
}

function lineMesh(positions, color, opacity) {
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  const m = new THREE.LineBasicMaterial({ color, transparent: true, opacity, depthWrite: false });
  const l = new THREE.LineSegments(g, m);
  l.renderOrder = 3;
  return l;
}

export class Vectors {
  constructor(scene, maskSize = 2048) {
    this.scene = scene;
    this.group = new THREE.Group();
    scene.add(this.group);
    this.countries = new Map();   // id -> {polys(unwrapped), bbox, raw}
    this.states = null;
    this.maskCanvas = document.createElement('canvas');
    this.maskCanvas.width = maskSize; this.maskCanvas.height = maskSize / 2;
    this.maskCtx = this.maskCanvas.getContext('2d');
    this.maskTex = new THREE.CanvasTexture(this.maskCanvas);
    this.maskTex.colorSpace = THREE.NoColorSpace;
    this.maskTex.wrapS = THREE.RepeatWrapping;
    this.selected = null;
    this.mountainPolys = [];
    this.showMountains = false;
    this.borderOpacity = 0.4;
    this._redrawMask();
  }

  loadCountries(shapes) {
    const pos = [];
    for (const s of shapes) {
      const polys = s.p.map(poly => poly.map(unwrapRing));
      let minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
      for (const poly of polys) for (const [x, y] of poly[0]) {
        if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
      }
      this.countries.set(s.id, { polys, bbox: [minX, minY, maxX, maxY], raw: s.p });
      ringsToSegments(s.p.map(p => p[0]), 1.0007, pos);
    }
    this.borders = lineMesh(pos, 0xd8ecff, this.borderOpacity);
    this.group.add(this.borders);
  }

  loadStates(states) {
    const pos = [];
    for (const s of states) ringsToSegments(s.p.map(p => p[0]), 1.0005, pos);
    this.states = lineMesh(pos, 0xffffff, 0.0);
    this.states.visible = false;
    this.group.add(this.states);
    this.stateList = states.map(s => ({ n: s.n, c: s.c, t: s.t, lat: s.lat, lon: s.lon, polys: s.p.map(p => p.map(unwrapRing)) }));
  }

  loadMountains(list) {
    const pos = [];
    for (const m of list) {
      ringsToSegments(m.p.map(p => p[0]), 1.0009, pos);
      this.mountainPolys.push(m);
    }
    this.mountainLines = lineMesh(pos, 0xffa040, 0.85);
    this.mountainLines.visible = false;
    this.group.add(this.mountainLines);
  }

  loadPlates(list) {
    const pos = [];
    for (const p of list) ringsToSegments([p.l], 1.0011, pos);
    this.plates = lineMesh(pos, 0xff4d6d, 0.85);
    this.plates.visible = false;
    this.group.add(this.plates);
  }

  setPlates(v) { if (this.plates) this.plates.visible = v; }
  setMountains(v) {
    this.showMountains = v;
    if (this.mountainLines) this.mountainLines.visible = v;
    this._redrawMask();
  }
  setBorders(v) { if (this.borders) this.borders.visible = v; }

  /** altitude-based fading of borders and state lines */
  updateForAltitude(altKm, statesEnabled) {
    if (this.borders) this.borders.material.opacity = this.borderOpacity * (altKm > 15000 ? 0.6 : 1);
    if (this.states) {
      const a = statesEnabled ? Math.max(0, Math.min(1, (2600 - altKm) / 1600)) * 0.38 : 0;
      this.states.material.opacity = a;
      this.states.visible = a > 0.01;
    }
  }

  select(id) {
    this.selected = id;
    if (this.selLine) { this.group.remove(this.selLine); this.selLine.geometry.dispose(); this.selLine = null; }
    const c = id && this.countries.get(id);
    if (c) {
      const pos = [];
      ringsToSegments(c.raw.map(p => p[0]), 1.0012, pos);
      this.selLine = lineMesh(pos, 0x5ce1ff, 1.0);
      this.selLine.renderOrder = 5;
      this.group.add(this.selLine);
    }
    this._redrawMask();
  }

  pulse(t) { if (this.selLine) this.selLine.material.opacity = 0.7 + 0.3 * Math.sin(t * 3); }

  _fillPolys(ctx, polys, W, H) {
    ctx.beginPath();
    for (const poly of polys) for (const ring of poly) {
      for (const off of [-360, 0, 360]) {
        ring.forEach(([x, y], i) => {
          const px = ((x + off + 180) / 360) * W, py = ((90 - y) / 180) * H;
          i ? ctx.lineTo(px, py) : ctx.moveTo(px, py);
        });
        ctx.closePath();
      }
    }
    ctx.fill('evenodd');
  }

  _redrawMask() {
    const ctx = this.maskCtx, W = this.maskCanvas.width, H = this.maskCanvas.height;
    ctx.globalCompositeOperation = 'source-over';
    ctx.fillStyle = 'rgb(0,0,0)';
    ctx.fillRect(0, 0, W, H);
    ctx.globalCompositeOperation = 'lighter';
    const c = this.selected && this.countries.get(this.selected);
    if (c) { ctx.fillStyle = 'rgb(255,0,0)'; this._fillPolys(ctx, c.polys, W, H); }
    if (this.showMountains) {
      ctx.fillStyle = 'rgb(0,255,0)';
      for (const m of this.mountainPolys) this._fillPolys(ctx, m.p.map(p => p.map(unwrapRing)), W, H);
    }
    this.maskTex.needsUpdate = true;
  }

  countryAt(lat, lon) {
    for (const [id, c] of this.countries) {
      const [x0, y0, x1, y1] = c.bbox;
      if (lat < y0 || lat > y1) continue;
      const inX = (lon >= x0 && lon <= x1) || (lon + 360 >= x0 && lon + 360 <= x1) || (lon - 360 >= x0 && lon - 360 <= x1);
      if (!inX) continue;
      if (pointInPolygons(lat, lon, c.polys)) return id;
    }
    return null;
  }

  stateAt(lat, lon, countryId) {
    if (!this.stateList) return null;
    for (const s of this.stateList) {
      if (countryId && s.c !== countryId) continue;
      if (pointInPolygons(lat, lon, s.polys)) return s;
    }
    return null;
  }
}
