// EARTH-X geodesy & astronomy helpers. Pure functions, no THREE dependency.
// Frame: unit sphere, +Y = north pole, lon 0 on +X, lon +90E on -Z
// (matches THREE.SphereGeometry's equirectangular UV layout).

export const EARTH_RADIUS_KM = 6371.0088;
const D2R = Math.PI / 180;
const R2D = 180 / Math.PI;

export function latLonToXYZ(lat, lon, r = 1) {
  const la = lat * D2R, lo = lon * D2R;
  const c = Math.cos(la);
  return [r * c * Math.cos(lo), r * Math.sin(la), -r * c * Math.sin(lo)];
}

export function xyzToLatLon(x, y, z) {
  const r = Math.hypot(x, y, z) || 1;
  return { lat: Math.asin(Math.max(-1, Math.min(1, y / r))) * R2D, lon: Math.atan2(-z, x) * R2D };
}

export function wrapLon(lon) {
  let l = ((lon + 180) % 360 + 360) % 360 - 180;
  return l === -180 ? 180 : l;
}

/** Great-circle distance (haversine) in km. */
export function haversineKm(lat1, lon1, lat2, lon2) {
  const dLat = (lat2 - lat1) * D2R, dLon = (lon2 - lon1) * D2R;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * D2R) * Math.cos(lat2 * D2R) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
}

/** Initial bearing (deg, 0 = north). */
export function bearingDeg(lat1, lon1, lat2, lon2) {
  const p1 = lat1 * D2R, p2 = lat2 * D2R, dl = (lon2 - lon1) * D2R;
  const y = Math.sin(dl) * Math.cos(p2);
  const x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
  return (Math.atan2(y, x) * R2D + 360) % 360;
}

/** Points along the great circle (slerp), n segments. */
export function greatCirclePoints(lat1, lon1, lat2, lon2, n = 64) {
  const a = latLonToXYZ(lat1, lon1), b = latLonToXYZ(lat2, lon2);
  const dot = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
  const om = Math.acos(dot);
  const out = [];
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    let s1, s2;
    if (om < 1e-6) { s1 = 1 - t; s2 = t; } else { s1 = Math.sin((1 - t) * om) / Math.sin(om); s2 = Math.sin(t * om) / Math.sin(om); }
    out.push([a[0] * s1 + b[0] * s2, a[1] * s1 + b[1] * s2, a[2] * s1 + b[2] * s2]);
  }
  return out;
}

/** Unwrap ring longitudes so consecutive points never jump > 180°. */
export function unwrapRing(ring) {
  const out = [[ring[0][0], ring[0][1]]];
  for (let i = 1; i < ring.length; i++) {
    let lon = ring[i][0];
    const prev = out[i - 1][0];
    while (lon - prev > 180) lon -= 360;
    while (lon - prev < -180) lon += 360;
    out.push([lon, ring[i][1]]);
  }
  return out;
}

function pointInRing(x, y, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
    if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

/** polygons: [[outer, hole, ...], ...] with pre-unwrapped rings. */
export function pointInPolygons(lat, lon, polygons) {
  for (const poly of polygons) {
    for (const off of [0, 360, -360]) {
      if (pointInRing(lon + off, lat, poly[0])) {
        let inHole = false;
        for (let h = 1; h < poly.length; h++) if (pointInRing(lon + off, lat, poly[h])) { inHole = true; break; }
        if (!inHole) return true;
      }
    }
  }
  return false;
}

// ---------------------------------------------------------------- Sun
/**
 * Sub-solar point for a UTC time (ms). Low-precision solar coordinates
 * (Astronomical Almanac / USNO approximation, accurate to ~0.01°),
 * combined with Greenwich Mean Sidereal Time.
 */
export function subsolarPoint(utcMs) {
  const jd = utcMs / 86400000 + 2440587.5;
  const d = jd - 2451545.0;
  const g = (357.529 + 0.98560028 * d) * D2R;
  const q = 280.459 + 0.98564736 * d;
  const L = (q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g)) * D2R;
  const e = (23.439 - 0.00000036 * d) * D2R;
  const ra = Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L));
  const dec = Math.asin(Math.sin(e) * Math.sin(L));
  const gmstHours = ((18.697374558 + 24.06570982441908 * d) % 24 + 24) % 24;
  const lon = wrapLon(ra * R2D - gmstHours * 15);
  return { lat: dec * R2D, lon };
}

/** Solar elevation angle (deg) at a location. */
export function solarElevation(lat, lon, utcMs) {
  const s = subsolarPoint(utcMs);
  const a = latLonToXYZ(lat, lon), b = latLonToXYZ(s.lat, s.lon);
  const dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  return 90 - Math.acos(Math.max(-1, Math.min(1, dot))) * R2D;
}

export function altitudeKmFromDistance(dist) { return (dist - 1) * EARTH_RADIUS_KM; }

/** Map camera altitude to the 6 exploration levels used across the app. */
export function zoomLevel(altKm) {
  if (altKm > 12000) return 1;   // Earth from space
  if (altKm > 5000) return 2;    // continents
  if (altKm > 1500) return 3;    // countries
  if (altKm > 450) return 4;     // states / provinces
  if (altKm > 90) return 5;      // cities
  return 6;                       // regional detail (dataset-limited)
}
export const LEVEL_NAMES = ['', 'Earth from space', 'Continents', 'Countries', 'States & provinces', 'Cities', 'Regional detail'];
