#version 330

// TURBULENCE fill: domain warped fractal noise drawn in screen space, tinted
// with the entity's own colour. The field is Inigo Quilez's two level warp,
// fbm(p + 4 * fbm(p + 4 * fbm(p))), over 3D simplex noise whose third axis is
// time, so the clouds boil in place instead of scrolling past.
//
// The warp costs seven fbm evaluations, so the octave count is what keeps it
// affordable: the field is sampled large, and at that size a fourth octave is
// finer than the screen resolves, so three of them is nineteen noise lookups
// per pixel and looks the same as four would.

#moj_import <homovore-2:chams_shade.glsl>

const int turbOctaves = 3;
const float turbScale = 0.6;  // cells of the field across the shorter screen edge

// 3D simplex noise, from Ashima Arts' webgl-noise (Stefan Gustavson, MIT).
vec3 turbMod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 turbMod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 turbPermute(vec4 x) { return turbMod289(((x * 34.0) + 1.0) * x); }
vec4 turbInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

float turbNoise(vec3 v) {
    const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
    const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);

    vec3 i = floor(v + dot(v, C.yyy));
    vec3 x0 = v - i + dot(i, C.xxx);

    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);

    vec3 x1 = x0 - i1 + C.xxx;
    vec3 x2 = x0 - i2 + C.yyy;
    vec3 x3 = x0 - D.yyy;

    i = turbMod289(i);
    vec4 p = turbPermute(turbPermute(turbPermute(
                 i.z + vec4(0.0, i1.z, i2.z, 1.0))
               + i.y + vec4(0.0, i1.y, i2.y, 1.0))
               + i.x + vec4(0.0, i1.x, i2.x, 1.0));

    float n_ = 0.142857142857;
    vec3 ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);

    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);

    vec4 x = x_ * ns.x + ns.yyyy;
    vec4 y = y_ * ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);

    vec4 s0 = floor(b0) * 2.0 + 1.0;
    vec4 s1 = floor(b1) * 2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);

    vec4 norm = turbInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;

    vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m * m, vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
}

// Normalised so the sum of the octave weights is one: the result keeps the
// -1..1 range of a single octave whatever the octave count is.
float turbFbm(vec3 p) {
    float signal = 0.0;
    float factor = 0.0;
    float scale = 0.5;
    for (int i = 0; i < turbOctaves; i++) {
        signal += turbNoise(p) * scale;
        factor += scale;
        scale *= 0.5;
        p *= 2.0;
    }
    return signal / factor;
}

float turbWarp(vec3 p) {
    vec3 q = vec3(
        turbFbm(p),
        turbFbm(p + vec3(5.2, 1.3, 0.0)),
        turbFbm(p + vec3(1.7, 9.2, 0.0)));

    vec3 r = vec3(
        turbFbm(p + 4.0 * q + vec3(1.7, 9.2, 0.0)),
        turbFbm(p + 4.0 * q + vec3(8.3, 2.8, 0.0)),
        turbFbm(p + 4.0 * q + vec3(0.0, 0.0, 5.2)));

    // One octave for the last pass, as the original does: the warp already
    // carries the detail, and a full fbm here would cost a third more noise.
    return turbNoise(p + 4.0 * r);
}

vec4 chamsFill(vec2 uv, vec3 color) {
    // Aspect corrected screen coordinates, scaled so under a cell of the field
    // crosses the screen: the warp does the rest, and an entity covers a small
    // enough part of the screen to need the room.
    vec2 p = uv * OutSize / min(OutSize.x, OutSize.y);

    float v = turbWarp(vec3(p * turbScale, chamsTime() * 0.05));
    v = clamp(v * 0.5 + 0.5, 0.0, 1.0);

    // Keep the entity's colour recognisable: the field darkens and brightens it
    // rather than replacing it, with the bright end pushed towards white.
    vec3 col = color * (0.25 + 1.1 * v) + vec3(pow(v, 6.0) * 0.5);
    return vec4(col, FillAlpha);
}

#moj_import <homovore-2:chams_main.glsl>
