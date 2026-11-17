// GLSL for the EARTH-X globe. Written for THREE.ShaderMaterial (WebGL2 / GLSL3 via three's prefix).

export const earthVertex = /* glsl */`
varying vec2 vUv;
varying vec3 vNormalW;
varying vec3 vPosW;
void main() {
  vUv = uv;
  vec4 wp = modelMatrix * vec4(position, 1.0);
  vPosW = wp.xyz;
  vNormalW = normalize(mat3(modelMatrix) * normal);
  gl_Position = projectionMatrix * viewMatrix * wp;
}`;

export const earthFragment = /* glsl */`
uniform sampler2D dayMap;
uniform sampler2D nightMap;
uniform sampler2D brcMap;      // r: bump, g: roughness, b: clouds
uniform sampler2D normalMap;
uniform sampler2D waterMap;
uniform sampler2D elevMap;
uniform sampler2D satMap;
uniform sampler2D overlayMap;
uniform sampler2D maskMap;     // r: selected country, g: mountains, b: hover
uniform vec3 sunDir;
uniform float satMix;
uniform float overlayOpacity;
uniform float nightEnabled;
uniform float lightsIntensity;
uniform float cloudShadow;
uniform float cloudU;
uniform float elevMode;        // 0 natural, 1 hypsometric elevation
uniform float normalStrength;
uniform float selPulse;
uniform float ambient;
varying vec2 vUv;
varying vec3 vNormalW;
varying vec3 vPosW;

vec3 hypsometric(float h) {
  // land elevation tint (low green -> brown -> white)
  vec3 c1 = vec3(0.13, 0.42, 0.20);
  vec3 c2 = vec3(0.55, 0.62, 0.30);
  vec3 c3 = vec3(0.62, 0.45, 0.28);
  vec3 c4 = vec3(0.50, 0.38, 0.32);
  vec3 c5 = vec3(0.96, 0.96, 0.98);
  if (h < 0.15) return mix(c1, c2, h / 0.15);
  if (h < 0.35) return mix(c2, c3, (h - 0.15) / 0.2);
  if (h < 0.6) return mix(c3, c4, (h - 0.35) / 0.25);
  return mix(c4, c5, clamp((h - 0.6) / 0.4, 0.0, 1.0));
}

void main() {
  vec3 N = normalize(vNormalW);
  vec3 V = normalize(cameraPosition - vPosW);
  vec3 L = normalize(sunDir);

  // analytic tangent frame on the sphere: u -> east, v -> north
  vec3 up = abs(N.y) > 0.999 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
  vec3 east = normalize(cross(up, N));
  vec3 north = cross(N, east);
  vec3 nTS = texture2D(normalMap, vUv).xyz * 2.0 - 1.0;
  vec3 Nb = normalize(east * nTS.x * normalStrength + north * nTS.y * normalStrength + N * nTS.z);

  float water = texture2D(waterMap, vUv).r;
  vec4 brc = texture2D(brcMap, vUv);
  float rough = mix(0.85, 0.3, water);

  vec3 day = texture2D(dayMap, vUv).rgb;
  if (satMix > 0.0) day = mix(day, texture2D(satMap, vUv).rgb, satMix);
  if (elevMode > 0.5) {
    float h = texture2D(elevMap, vUv).r;
    vec3 sea = mix(vec3(0.02, 0.10, 0.28), vec3(0.08, 0.30, 0.55), clamp(dot(day, vec3(0.3, 0.3, 0.4)) * 2.5, 0.0, 1.0));
    vec3 land = hypsometric(h);
    day = mix(land, sea, smoothstep(0.4, 0.6, water));
  }

  float NdL = dot(N, L);                       // geometric (terminator)
  float lit = max(dot(Nb, L), 0.0);            // shaded relief
  float dayF = smoothstep(-0.08, 0.18, NdL);

  // cloud shadow (clouds drift; sample with offset)
  float cs = texture2D(brcMap, vec2(fract(vUv.x - cloudU), vUv.y)).b;
  float shadow = 1.0 - cloudShadow * cs * 0.55;

  vec3 col = day * (ambient + lit * 1.15) * shadow;

  // ocean sun glint
  vec3 H = normalize(L + V);
  float spec = pow(max(dot(Nb, H), 0.0), mix(18.0, 140.0, water)) * water * (1.0 - rough * 0.6);
  col += vec3(1.0, 0.92, 0.78) * spec * 0.9 * dayF * shadow;

  // twilight warmth near terminator
  float tw = exp(-pow(NdL * 9.0, 2.0));
  col += vec3(0.35, 0.12, 0.02) * tw * 0.25 * day;

  vec3 nightCol = vec3(0.0);
  if (nightEnabled > 0.5) {
    vec3 lights = texture2D(nightMap, vUv).rgb;
    lights = pow(lights, vec3(1.25)) * 3.2;
    nightCol = lights * lightsIntensity * (1.0 - cs * 0.6);
    col = mix(nightCol + day * vec3(0.02, 0.025, 0.04), col, dayF);
  }

  // atmosphere haze on the limb
  float fres = pow(1.0 - max(dot(N, V), 0.0), 3.0);
  col += vec3(0.30, 0.55, 1.0) * fres * 0.55 * smoothstep(-0.3, 0.6, NdL);

  // data overlay (weather etc.)
  vec4 ov = texture2D(overlayMap, vUv);
  col = mix(col, ov.rgb * mix(0.55, 1.0, dayF), ov.a * overlayOpacity);

  // masks
  vec4 m = texture2D(maskMap, vUv);
  col = mix(col, vec3(0.20, 0.85, 1.0) * mix(0.7, 1.2, dayF), m.r * (0.13 + 0.07 * selPulse));
  col = mix(col, vec3(1.0, 0.62, 0.20) * mix(0.5, 1.1, dayF), m.g * 0.16);

  gl_FragColor = vec4(col, 1.0);
  #include <tonemapping_fragment>
  #include <colorspace_fragment>
}`;

export const cloudVertex = earthVertex;
export const cloudFragment = /* glsl */`
uniform sampler2D brcMap;
uniform vec3 sunDir;
uniform float opacity;
varying vec2 vUv;
varying vec3 vNormalW;
varying vec3 vPosW;
void main() {
  float c = texture2D(brcMap, vUv).b;
  c = smoothstep(0.08, 0.9, c);
  vec3 N = normalize(vNormalW);
  float NdL = dot(N, normalize(sunDir));
  float dayF = smoothstep(-0.12, 0.25, NdL);
  vec3 col = vec3(1.0) * (0.04 + 0.96 * max(NdL, 0.0));
  col = mix(col, vec3(1.0, 0.6, 0.35), exp(-pow(NdL * 8.0, 2.0)) * 0.35);
  gl_FragColor = vec4(col, c * opacity * mix(0.18, 1.0, dayF));
  #include <tonemapping_fragment>
  #include <colorspace_fragment>
}`;

export const atmoVertex = /* glsl */`
varying vec3 vPosW;
void main() {
  vec4 wp = modelMatrix * vec4(position, 1.0);
  vPosW = wp.xyz;
  gl_Position = projectionMatrix * viewMatrix * wp;
}`;
// Limb glow: density falls off exponentially with the ray's closest approach to the planet.
export const atmoFragment = /* glsl */`
uniform vec3 sunDir;
uniform float strength;
uniform float scaleH;
varying vec3 vPosW;
void main() {
  vec3 D = normalize(vPosW - cameraPosition);
  float t = -dot(cameraPosition, D);
  vec3 Q = cameraPosition + D * max(t, 0.0);
  float h = length(Q);
  if (h < 1.0) discard;
  float dens = exp(-(h - 1.0) / scaleH);
  float s = dot(normalize(Q), normalize(sunDir));
  float dayF = smoothstep(-0.25, 0.3, s);
  vec3 sky = vec3(0.30, 0.60, 1.0);
  vec3 dusk = vec3(1.0, 0.48, 0.20);
  vec3 col = mix(dusk, sky, smoothstep(-0.02, 0.35, s));
  float a = dens * strength * (0.03 + dayF);
  gl_FragColor = vec4(col * a, 1.0);
  #include <colorspace_fragment>
}`;

// Detail / imagery patch: a lat-lon window textured with NASA GIBS imagery.
export const patchFragment = /* glsl */`
uniform sampler2D map;
uniform vec3 sunDir;
uniform float opacity;
uniform float nightEnabled;
varying vec2 vUv;
varying vec3 vNormalW;
varying vec3 vPosW;
void main() {
  vec3 c = texture2D(map, vUv).rgb;
  vec3 N = normalize(vNormalW);
  float NdL = dot(N, normalize(sunDir));
  float dayF = nightEnabled > 0.5 ? smoothstep(-0.08, 0.18, NdL) : 1.0;
  float lit = nightEnabled > 0.5 ? (0.12 + max(NdL, 0.0) * 1.05) : 1.0;
  vec2 e = min(vUv, 1.0 - vUv);
  float edge = smoothstep(0.0, 0.06, min(e.x, e.y));
  gl_FragColor = vec4(c * lit * mix(0.06, 1.0, dayF), opacity * edge);
  #include <tonemapping_fragment>
  #include <colorspace_fragment>
}`;

export const quakeVertex = /* glsl */`
attribute float aMag;
attribute float aDepth;
attribute float aAge;
attribute float aSel;
uniform float uTime;
uniform float uScale;
varying vec3 vColor;
varying float vPulse;
varying float vSel;
void main() {
  vec3 n = normalize(position);
  vec4 wp = modelMatrix * vec4(position, 1.0);
  vec3 toCam = normalize(cameraPosition - wp.xyz);
  float facing = dot(n, toCam);
  vec4 mv = viewMatrix * wp;
  gl_Position = projectionMatrix * mv;
  float size = (4.0 + pow(max(aMag, 0.0), 1.7) * 2.2) * uScale;
  gl_PointSize = facing > 0.05 ? size * (1.0 + aSel * 0.6) : 0.0;
  // color by depth: shallow red/orange, intermediate yellow, deep blue/purple
  vec3 shallow = vec3(1.0, 0.25, 0.15), mid = vec3(1.0, 0.78, 0.2), deep = vec3(0.35, 0.45, 1.0);
  vColor = aDepth < 70.0 ? mix(shallow, mid, aDepth / 70.0) : mix(mid, deep, clamp((aDepth - 70.0) / 230.0, 0.0, 1.0));
  vPulse = fract(uTime * 0.6 + aMag * 0.37);
  vSel = aSel;
}`;
export const quakeFragment = /* glsl */`
varying vec3 vColor;
varying float vPulse;
varying float vSel;
void main() {
  vec2 p = gl_PointCoord * 2.0 - 1.0;
  float r = length(p);
  if (r > 1.0) discard;
  float core = smoothstep(0.42, 0.30, r);
  float ring = smoothstep(0.08, 0.0, abs(r - (0.35 + vPulse * 0.62))) * (1.0 - vPulse);
  float a = core * 0.95 + ring * 0.9;
  vec3 c = mix(vColor, vec3(1.0), vSel * 0.5 + core * 0.15);
  gl_FragColor = vec4(c, a);
  #include <colorspace_fragment>
}`;
