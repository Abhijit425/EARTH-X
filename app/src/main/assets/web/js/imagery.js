// NASA GIBS imagery: a global satellite texture (WMS GetMap, EPSG:4326, whole world)
// plus a high-resolution regional "detail patch" streamed for the area under the camera.
// Endpoint (documented): https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi
// In the Android app every request goes through the native, host-whitelisted, caching proxy.
import * as THREE from '../lib/three.module.min.js';
import { patchFragment, earthVertex } from './shaders.js';

export const GIBS_LAYERS = {
  VIIRS_SNPP_CorrectedReflectance_TrueColor: { daily: true, fmt: 'image/jpeg', label: 'VIIRS SNPP true colour (daily, 250 m)' },
  VIIRS_NOAA20_CorrectedReflectance_TrueColor: { daily: true, fmt: 'image/jpeg', label: 'VIIRS NOAA-20 true colour (daily, 250 m)' },
  MODIS_Terra_CorrectedReflectance_TrueColor: { daily: true, fmt: 'image/jpeg', label: 'MODIS Terra true colour (daily, 250 m)' },
  MODIS_Aqua_CorrectedReflectance_TrueColor: { daily: true, fmt: 'image/jpeg', label: 'MODIS Aqua true colour (daily, 250 m)' },
  BlueMarble_NextGeneration: { daily: false, fmt: 'image/jpeg', label: 'Blue Marble Next Generation (cloud-free, 500 m)' },
  BlueMarble_ShadedRelief_Bathymetry: { daily: false, fmt: 'image/jpeg', label: 'Shaded relief + ocean bathymetry (500 m)' },
  VIIRS_Black_Marble: { daily: false, fmt: 'image/png', label: 'Black Marble night lights (2016)' },
};

export class Imagery {
  constructor(scene, { netBase, onStatus }) {
    this.scene = scene;
    this.netBase = netBase;
    this.onStatus = onStatus || (() => {});
    this.loader = new THREE.TextureLoader();
    this.loader.setCrossOrigin('anonymous');
    this.globalTex = null;
    this.layer = 'VIIRS_SNPP_CorrectedReflectance_TrueColor';
    this.date = null;
    this.satEnabled = false;
    this.detailEnabled = true;
    this.online = true;
    this.patches = [];
    this.pending = null;
    this.lastReq = null;
    this.sunDir = new THREE.Vector3(1, 0, 0);
    this.nightEnabled = true;
    this.maxSize = 4096;
    this.patchSize = 2048;
  }

  url(layer, bbox, w, h, date) {
    const info = GIBS_LAYERS[layer] || { fmt: 'image/jpeg', daily: false };
    const p = new URLSearchParams({
      SERVICE: 'WMS', REQUEST: 'GetMap', VERSION: '1.1.1', LAYERS: layer, STYLES: '', SRS: 'EPSG:4326',
      BBOX: bbox.map(v => v.toFixed(5)).join(','), WIDTH: String(w), HEIGHT: String(h), FORMAT: info.fmt, TRANSPARENT: 'FALSE',
    });
    if (info.daily && date) p.set('TIME', date);
    return this.netBase + p.toString();
  }

  _load(url) {
    return new Promise((res, rej) => this.loader.load(url, t => res(t), undefined, e => rej(e)));
  }

  async loadGlobal(onTexture) {
    if (!this.online) { this.onStatus({ scope: 'global', ok: false, msg: 'Offline — showing bundled Earth texture' }); return; }
    const w = this.maxSize, h = w / 2;
    const u = this.url(this.layer, [-180, -90, 180, 90], w, h, this.date);
    this.onStatus({ scope: 'global', ok: true, loading: true, layer: this.layer, date: this.date });
    try {
      const t = await this._load(u);
      t.colorSpace = THREE.SRGBColorSpace;
      t.anisotropy = 8;
      if (this.globalTex) this.globalTex.dispose();
      this.globalTex = t;
      onTexture(t);
      this.onStatus({ scope: 'global', ok: true, loading: false, layer: this.layer, date: this.date });
    } catch (e) {
      this.onStatus({ scope: 'global', ok: false, msg: 'NASA GIBS imagery unavailable for this layer/date — showing bundled texture' });
    }
  }

  /** Called when the camera has settled. lat/lon = view centre, altKm = altitude. */
  async updateDetail(lat, lon, altKm) {
    const want = this.detailEnabled && this.online && altKm < 2600;
    if (!want) { this._fadeAll(); return; }
    const latSpan = Math.max(0.35, Math.min(70, altKm / 111 * 1.9));
    const lonSpan = Math.min(140, latSpan / Math.max(0.15, Math.cos(lat * Math.PI / 180)));
    const s = Math.max(-89.9, lat - latSpan / 2), n = Math.min(89.9, lat + latSpan / 2);
    const wv = lon - lonSpan / 2, ev = lon + lonSpan / 2;
    const L = this.lastReq;
    if (L && Math.abs(L.lat - lat) < latSpan * 0.22 && Math.abs(((L.lon - lon + 540) % 360) - 180) < lonSpan * 0.22 && L.alt / altKm < 1.7 && altKm / L.alt < 1.7) return;
    this.lastReq = { lat, lon, alt: altKm };
    const layer = this.satEnabled ? this.layer : 'BlueMarble_NextGeneration';
    const aspect = lonSpan * Math.cos(lat * Math.PI / 180) / latSpan;
    const W = this.patchSize, H = Math.max(256, Math.min(this.patchSize, Math.round(W / Math.max(0.5, aspect))));
    // WMS bbox must stay within [-180,180]; split across the antimeridian is avoided by clamping.
    const w = Math.max(-180, wv), e = Math.min(180, ev);
    const url = this.url(layer, [w, s, e, n], W, H, this.date);
    const token = Symbol();
    this.pending = token;
    this.onStatus({ scope: 'detail', ok: true, loading: true, layer });
    try {
      const tex = await this._load(url);
      if (this.pending !== token) { tex.dispose(); return; }
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.anisotropy = 8;
      tex.generateMipmaps = true;
      this._addPatch(tex, w, s, e, n);
      this.onStatus({ scope: 'detail', ok: true, loading: false, layer, res: Math.round((e - w) * 111 * Math.cos(lat * Math.PI / 180) / W * 1000) });
    } catch (err) {
      this.onStatus({ scope: 'detail', ok: false, msg: 'Regional detail imagery unavailable' });
    }
  }

  _addPatch(tex, w, s, e, n) {
    const D = Math.PI / 180;
    const segW = Math.max(16, Math.round((e - w) / 1.5)), segH = Math.max(16, Math.round((n - s) / 1.5));
    const geo = new THREE.SphereGeometry(1.00035, Math.min(128, segW), Math.min(128, segH),
      (w + 180) * D, (e - w) * D, (90 - n) * D, (n - s) * D);
    const mat = new THREE.ShaderMaterial({
      vertexShader: earthVertex, fragmentShader: patchFragment,
      uniforms: { map: { value: tex }, sunDir: { value: this.sunDir }, opacity: { value: 0 }, nightEnabled: { value: this.nightEnabled ? 1 : 0 } },
      transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2,
    });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.renderOrder = 2;
    this.scene.add(mesh);
    for (const p of this.patches) p.target = 0;
    this.patches.push({ mesh, target: 1 });
  }

  _fadeAll() { for (const p of this.patches) p.target = 0; this.lastReq = null; }
  clearDetail() { this._fadeAll(); }

  setNight(b) { this.nightEnabled = b; for (const p of this.patches) p.mesh.material.uniforms.nightEnabled.value = b ? 1 : 0; }

  /** returns true while animating */
  animate(dt) {
    let busy = false;
    for (const p of this.patches) {
      const u = p.mesh.material.uniforms.opacity;
      const d = p.target - u.value;
      if (Math.abs(d) > 0.005) { u.value += Math.sign(d) * Math.min(Math.abs(d), dt * 2.5); busy = true; }
      else u.value = p.target;
    }
    this.patches = this.patches.filter(p => {
      if (p.target === 0 && p.mesh.material.uniforms.opacity.value <= 0.005) {
        this.scene.remove(p.mesh); p.mesh.geometry.dispose(); p.mesh.material.uniforms.map.value.dispose(); p.mesh.material.dispose();
        return false;
      }
      return true;
    });
    return busy;
  }
}
