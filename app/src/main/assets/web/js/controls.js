// Tablet-grade globe controls: drag-rotate with inertia, pinch zoom, double-tap zoom,
// tap picking, and cinematic fly-to animations. Camera orbits a fixed Earth.
import { latLonToXYZ, wrapLon } from './geo.js';

const MIN_DIST = 1.0016;   // ~10 km altitude
const MAX_DIST = 9.0;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const easeInOut = t => t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

export class GlobeControls {
  constructor(camera, dom, { onTap, onDoubleTap, onChange, onInteract }) {
    this.camera = camera;
    this.dom = dom;
    this.lat = 20; this.lon = 78; this.dist = 3.2;
    this.vLat = 0; this.vLon = 0; this.vZoom = 0;
    this.autoRotate = 0;          // deg/sec
    this.flight = null;
    this.pointers = new Map();
    this.onTap = onTap; this.onDoubleTap = onDoubleTap; this.onChange = onChange; this.onInteract = onInteract;
    this.lastTap = 0; this.lastTapPos = null;
    this.home = { lat: 20, lon: 78, dist: 3.2 };
    this._bind();
    this.apply();
    this.ready = true;
  }

  _bind() {
    const d = this.dom;
    d.style.touchAction = 'none';
    d.addEventListener('pointerdown', e => this._down(e));
    d.addEventListener('pointermove', e => this._move(e));
    d.addEventListener('pointerup', e => this._up(e));
    d.addEventListener('pointercancel', e => this._up(e, true));
    d.addEventListener('wheel', e => {
      e.preventDefault();
      this.flight = null;
      this.zoomBy(Math.exp(e.deltaY * 0.0012));
      this.onInteract && this.onInteract();
    }, { passive: false });
  }

  _down(e) {
    this.dom.setPointerCapture(e.pointerId);
    this.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY, x0: e.clientX, y0: e.clientY, t0: performance.now() });
    this.flight = null;
    this.vLat = this.vLon = this.vZoom = 0;
    if (this.pointers.size === 2) this._pinchStart();
    this.onInteract && this.onInteract();
  }

  _pinchStart() {
    const [a, b] = [...this.pointers.values()];
    this.pinch = { d0: Math.hypot(a.x - b.x, a.y - b.y), dist0: this.dist, moved: false };
  }

  degPerPixel() {
    // angular size of the visible globe region per pixel at current altitude
    const alt = this.dist - 1;
    const fov = this.camera.fov * Math.PI / 180;
    const visible = 2 * Math.atan(Math.tan(fov / 2) * alt) ; // radians on surface (approx near)
    const h = this.dom.clientHeight || 800;
    return Math.min(0.35, (visible * 180 / Math.PI) / h * 1.15);
  }

  _move(e) {
    const p = this.pointers.get(e.pointerId);
    if (!p) return;
    const dx = e.clientX - p.x, dy = e.clientY - p.y;
    p.x = e.clientX; p.y = e.clientY;
    if (this.pointers.size === 1) {
      const k = this.degPerPixel();
      this.lon = wrapLon(this.lon - dx * k / Math.max(0.25, Math.cos(this.lat * Math.PI / 180)) * 0.9);
      this.lat = clamp(this.lat + dy * k, -85, 85);
      const dt = Math.max(8, performance.now() - (p.tm || performance.now() - 16));
      p.tm = performance.now();
      this.vLon = -dx * k / Math.max(0.25, Math.cos(this.lat * Math.PI / 180)) * 0.9 / dt * 1000;
      this.vLat = dy * k / dt * 1000;
      this.apply();
    } else if (this.pointers.size === 2 && this.pinch) {
      const [a, b] = [...this.pointers.values()];
      const d = Math.hypot(a.x - b.x, a.y - b.y);
      if (d > 0 && this.pinch.d0 > 0) {
        const s = this.pinch.d0 / d;
        this.setDist(1 + (this.pinch.dist0 - 1) * s);
        this.pinch.moved = true;
      }
    }
  }

  _up(e, cancel) {
    const p = this.pointers.get(e.pointerId);
    this.pointers.delete(e.pointerId);
    if (this.pointers.size < 2) this.pinch = null;
    if (!p || cancel) return;
    const moved = Math.hypot(e.clientX - p.x0, e.clientY - p.y0);
    const dur = performance.now() - p.t0;
    if (moved < 10 && dur < 350 && this.pointers.size === 0) {
      this.vLat = this.vLon = 0;
      const now = performance.now();
      const near = this.lastTapPos && Math.hypot(e.clientX - this.lastTapPos.x, e.clientY - this.lastTapPos.y) < 40;
      if (now - this.lastTap < 300 && near) {
        this.lastTap = 0;
        clearTimeout(this.tapTimer);
        this.onDoubleTap && this.onDoubleTap(e.clientX, e.clientY);
      } else {
        this.lastTap = now; this.lastTapPos = { x: e.clientX, y: e.clientY };
        const x = e.clientX, y = e.clientY;
        this.tapTimer = setTimeout(() => this.onTap && this.onTap(x, y), 260);
      }
    } else if (performance.now() - (p.tm || 0) > 90) {
      this.vLat = this.vLon = 0;    // finger rested before lift: no fling
    }
  }

  setDist(d) { this.dist = clamp(d, MIN_DIST, MAX_DIST); this.apply(); }
  zoomBy(f) { this.setDist(1 + (this.dist - 1) * f); }
  zoomAnimated(f) { this.flyTo(this.lat, this.lon, 1 + (this.dist - 1) * f, 700); }
  rotateBy(dLon, dLat = 0) { this.flyTo(clamp(this.lat + dLat, -85, 85), this.lon + dLon, this.dist, 650); }
  reset() { this.flyTo(this.home.lat, this.home.lon, this.home.dist, 1800); }

  /** Cinematic flight: arcs outwards for long hops. */
  flyTo(lat, lon, dist = this.dist, ms) {
    const from = { lat: this.lat, lon: this.lon, dist: this.dist };
    let dLon = wrapLon(lon - from.lon);
    const ang = Math.acos(Math.max(-1, Math.min(1,
      Math.sin(from.lat * Math.PI / 180) * Math.sin(lat * Math.PI / 180) +
      Math.cos(from.lat * Math.PI / 180) * Math.cos(lat * Math.PI / 180) * Math.cos(dLon * Math.PI / 180)))) * 180 / Math.PI;
    const dur = ms || clamp(900 + ang * 14 + Math.abs(Math.log(dist - 1) - Math.log(from.dist - 1)) * 220, 900, 3600);
    const peak = Math.max(from.dist, dist, 1 + Math.min(2.6, ang / 55));
    this.vLat = this.vLon = 0;
    this.flight = { from, to: { lat, lon: from.lon + dLon, dist: clamp(dist, MIN_DIST, MAX_DIST) }, peak, t0: performance.now(), dur };
  }

  /** Returns true when the camera moved (needs a redraw). */
  update(dt) {
    let moved = false;
    if (this.flight) {
      const f = this.flight;
      const t = Math.min(1, (performance.now() - f.t0) / f.dur);
      const k = easeInOut(t);
      this.lat = f.from.lat + (f.to.lat - f.from.lat) * k;
      this.lon = wrapLon(f.from.lon + (f.to.lon - f.from.lon) * k);
      // log-space altitude with a mid-flight lift
      const la = Math.log(f.from.dist - 1), lb = Math.log(f.to.dist - 1), lp = Math.log(f.peak - 1);
      const base = la + (lb - la) * k;
      const lift = Math.sin(Math.PI * t) * Math.max(0, lp - Math.max(la, lb)) ;
      this.dist = 1 + Math.exp(base + lift);
      if (t >= 1) this.flight = null;
      moved = true;
    } else if (this.pointers.size === 0) {
      if (Math.abs(this.vLat) > 0.01 || Math.abs(this.vLon) > 0.01) {
        this.lat = clamp(this.lat + this.vLat * dt, -85, 85);
        this.lon = wrapLon(this.lon + this.vLon * dt);
        const decay = Math.exp(-dt * 3.2);
        this.vLat *= decay; this.vLon *= decay;
        moved = true;
      }
      if (this.autoRotate) { this.lon = wrapLon(this.lon + this.autoRotate * dt); moved = true; }
    }
    if (moved) this.apply();
    return moved;
  }

  apply() {
    const [x, y, z] = latLonToXYZ(this.lat, this.lon, this.dist);
    this.camera.position.set(x, y, z);
    this.camera.up.set(0, 1, 0);
    this.camera.lookAt(0, 0, 0);
    const alt = this.dist - 1;
    this.camera.near = clamp(alt * 0.35, 0.00015, 0.5);
    this.camera.far = this.dist + 2;
    this.camera.updateProjectionMatrix();
    this.ready && this.onChange && this.onChange();
  }
}
