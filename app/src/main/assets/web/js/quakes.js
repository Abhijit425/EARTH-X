// USGS earthquake markers: animated, magnitude-sized, depth-coloured GPU points.
import * as THREE from '../lib/three.module.min.js';
import { latLonToXYZ } from './geo.js';
import { quakeVertex, quakeFragment } from './shaders.js';

const v = new THREE.Vector3();

export class Quakes {
  constructor(scene) {
    this.scene = scene;
    this.list = [];
    this.mat = new THREE.ShaderMaterial({
      vertexShader: quakeVertex, fragmentShader: quakeFragment,
      uniforms: { uTime: { value: 0 }, uScale: { value: 1 } },
      transparent: true, depthWrite: false, blending: THREE.NormalBlending,
    });
    this.points = null;
    this.visible = true;
    this.selectedId = null;
  }

  /** events: [{id, mag, depth, lat, lon, time, place}] — exactly as received from USGS (via Android). */
  set(events) {
    this.list = events.filter(e => Number.isFinite(e.lat) && Number.isFinite(e.lon));
    if (this.points) { this.scene.remove(this.points); this.points.geometry.dispose(); }
    const n = this.list.length;
    const pos = new Float32Array(n * 3), mag = new Float32Array(n), dep = new Float32Array(n), age = new Float32Array(n), sel = new Float32Array(n);
    // smaller events first so large ones draw on top
    this.list.sort((a, b) => (a.mag ?? 0) - (b.mag ?? 0));
    this.list.forEach((e, i) => {
      const [x, y, z] = latLonToXYZ(e.lat, e.lon, 1.0025);
      pos.set([x, y, z], i * 3);
      mag[i] = e.mag ?? 0; dep[i] = e.depth ?? 0; age[i] = 0; sel[i] = e.id === this.selectedId ? 1 : 0;
    });
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    g.setAttribute('aMag', new THREE.BufferAttribute(mag, 1));
    g.setAttribute('aDepth', new THREE.BufferAttribute(dep, 1));
    g.setAttribute('aAge', new THREE.BufferAttribute(age, 1));
    g.setAttribute('aSel', new THREE.BufferAttribute(sel, 1));
    this.points = new THREE.Points(g, this.mat);
    this.points.renderOrder = 6;
    this.points.frustumCulled = false;
    this.points.visible = this.visible;
    this.scene.add(this.points);
  }

  select(id) {
    this.selectedId = id;
    if (!this.points) return;
    const a = this.points.geometry.getAttribute('aSel');
    this.list.forEach((e, i) => { a.array[i] = e.id === id ? 1 : 0; });
    a.needsUpdate = true;
  }

  setVisible(b) { this.visible = b; if (this.points) this.points.visible = b; }

  update(t, pixelRatio) {
    this.mat.uniforms.uTime.value = t;
    this.mat.uniforms.uScale.value = pixelRatio;
  }

  pick(camera, x, y, w, h, radius = 24) {
    if (!this.visible || !this.points) return null;
    const cam = camera.position, cl = cam.length();
    let best = null, bd = radius;
    for (const e of this.list) {
      const [px, py, pz] = latLonToXYZ(e.lat, e.lon, 1.0025);
      if ((px * cam.x + py * cam.y + pz * cam.z) / cl < 1 / cl) continue;
      v.set(px, py, pz).project(camera);
      const sx = (v.x * 0.5 + 0.5) * w, sy = (-v.y * 0.5 + 0.5) * h;
      const d = Math.hypot(sx - x, sy - y) - Math.max(0, (e.mag ?? 0) - 3) * 2;
      if (d < bd) { bd = d; best = e; }
    }
    return best;
  }
}
