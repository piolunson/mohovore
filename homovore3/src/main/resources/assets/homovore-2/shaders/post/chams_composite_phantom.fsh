#version 330

// PHANTOM fill: a raymarched IFS tunnel drawn in screen space, so the
// silhouette reads as a window onto it rather than a painted surface. The
// entity's own colour is not used. Adapted from "Phantom Star" by Kali,
// which in turn borrows the accumulation from Phantom Mode by aiekick.
//
// The field is expensive, so everything that does not vary along the ray is
// lifted out of the march: the three IFS rotations depend on time alone, which
// took a thousand sin/cos pairs per pixel when they were rebuilt inside the
// loops and now take three. The march also stops as soon as the result can no
// longer be seen, which is exact rather than an approximation, see below.
//
// The tunnel is drawn large, through a narrow field of view, which is what pays
// for the march taking a third fewer and longer steps than it used to.
//
// Time never reaches a shader as a big number. chamsTime counts seconds since
// the world started, so within minutes it is in the hundreds, and sin, cos and
// mod all lose their low bits at that size: the animation jitters, worse the
// longer the world has been open and worse again at high FillSpeed. Every use
// below is therefore wrapped to a period it is exactly invariant under, so the
// motion is unchanged but the arguments stay small.

#moj_import <homovore-2:chams_shade.glsl>

// Set once per pixel by chamsFill; the field functions below read them.
float phantomTime;
mat2 phantomFold1;  // rot(t * 0.3), the xy fold inside the IFS
mat2 phantomFold2;  // rot(t * 0.1), the xz fold inside the IFS
mat2 phantomSpin;   // rot(t), the xz spin that closes the IFS

const float phantomPi = acos(-1.0);

// Alpha falls off as 1 - t * 0.03, so nothing past this contributes.
const float phantomFar = 1.0 / 0.03;

const float phantomTau = 2.0 * phantomPi;
const float phantomZoom = 0.6;   // field of view; smaller is a closer tunnel
const float phantomStep = 0.75;  // fraction of the distance estimate per step
const int phantomSteps = 64;
// Longer steps means fewer samples along the same ray, so the accumulation is
// scaled back up to the density the original 0.5 step gave.
const float phantomGain = 0.5 / phantomStep;

mat2 phantomRot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

// Fold into one of r wedges around the origin. Cheaper than building a matrix:
// only one angle is ever rotated by, so the sin/cos are applied directly.
vec2 phantomPmod(vec2 p, float r) {
    float a = atan(p.x, p.y) + phantomPi / r;
    float n = (phantomPi * 2.0) / r;
    a = floor(a / n) * n;
    float c = cos(-a), s = sin(-a);
    return vec2(p.x * c - p.y * s, p.x * s + p.y * c);
}

float phantomBox(vec3 p, vec3 b) {
    vec3 d = abs(p) - b;
    return min(max(d.x, max(d.y, d.z)), 0.0) + length(max(d, 0.0));
}

float phantomIfsBox(vec3 p) {
    for (int i = 0; i < 5; i++) {
        p = abs(p) - 1.0;
        p.xy *= phantomFold1;
        p.xz *= phantomFold2;
    }
    p.xz *= phantomSpin;
    return phantomBox(p, vec3(0.4, 0.8, 0.3));
}

float phantomMap(vec3 p) {
    vec3 q = p;
    q.x = mod(q.x - 5.0, 10.0) - 5.0;
    q.y = mod(q.y - 5.0, 10.0) - 5.0;
    q.z = mod(q.z, 16.0) - 8.0;
    q.xy = phantomPmod(q.xy, 5.0);
    return phantomIfsBox(q);
}

vec4 chamsFill(vec2 uv, vec3 color) {
    phantomTime = chamsTime();

    // A rotation repeats every 2 pi of its own angle, so wrapping the time it
    // is built from leaves the matrix bit for bit what it was.
    phantomFold1 = phantomRot(mod(phantomTime, phantomTau / 0.3) * 0.3);
    phantomFold2 = phantomRot(mod(phantomTime, phantomTau / 0.1) * 0.1);
    phantomSpin = phantomRot(mod(phantomTime, phantomTau));

    vec2 fragCoord = uv * OutSize;
    vec2 p = (fragCoord * 2.0 - OutSize) / min(OutSize.x, OutSize.y) * phantomZoom;

    // The field repeats every 16 along z, and the camera only moves along z, so
    // flying it 16 forward is the same view. Keeping it inside one period keeps
    // every position the march evaluates small as well.
    vec3 cPos = vec3(0.0, 0.0, -mod(3.0 * phantomTime, 16.0));
    vec3 cDir = vec3(0.0, 0.0, -1.0);
    vec3 cUp = vec3(sin(mod(phantomTime, phantomTau)), 1.0, 0.0);
    vec3 cSide = cross(cDir, cUp);

    vec3 ray = normalize(cSide * p.x + cUp * p.y + cDir);

    // The bands are shells sweeping past the camera, 30 apart and moving at 24
    // a second, so 1.25 seconds of time is one whole shell: wrapping there is
    // exact. They ride the march distance rather than the distance to the
    // origin, which the camera wrap above would otherwise shift.
    float bandBase = 24.0 * mod(phantomTime, 1.25);

    float acc = 0.0;
    float acc2 = 0.0;
    float t = 0.0;
    for (int i = 0; i < phantomSteps; i++) {
        // Past the far plane the fill is fully transparent whatever it holds,
        // so the remaining steps cannot change a single pixel.
        if (t > phantomFar) break;

        vec3 pos = cPos + ray * t;
        float dist = max(abs(phantomMap(pos)), 0.03);
        float a = exp(-dist * 3.0);
        // Branchless form of "inside a band, count it twice and again in acc2".
        float band = step(mod(t + bandBase, 30.0), 3.0);
        a *= 1.0 + band;
        acc2 += a * band;
        acc += a;
        t += dist * phantomStep;
    }

    acc *= phantomGain;
    acc2 *= phantomGain;
    vec3 col = vec3(acc * 0.01,
                    acc * 0.011 + acc2 * 0.002,
                    acc * 0.012 + acc2 * 0.005);
    return vec4(col, FillAlpha * clamp(1.0 - t * 0.03, 0.0, 1.0));
}

#moj_import <homovore-2:chams_main.glsl>
