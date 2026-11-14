#version 330

// TRANS fill: the trans flag, waving. The five stripes are drawn in screen
// space along a slightly tilted axis, displaced by a travelling sine so the
// silhouette reads as cloth rather than paint, lit off the slope of that same
// wave, and swept by a sheen that rolls across the stripes.
//
// The entity's own colour is not thrown away: a little of it is mixed back in
// at the end, so a friend or a target still reads as one at a glance while the
// flag stays the colour it is meant to be.
//
// Time never reaches a shader as a big number. chamsTime counts seconds since
// the world started, so within minutes it is in the hundreds and sin and mod
// lose their low bits at that size, which shows up as stutter. Each use below
// is wrapped to a period it is exactly invariant under first.

#moj_import <homovore-2:chams_shade.glsl>

const float transTau = 2.0 * acos(-1.0);

// The flag repeats every this many screen heights across the stripe axis. An
// entity covers a small part of the screen, so the pattern has to be small
// enough that a whole flag fits inside one.
const float transHeight = 0.42;

// The stripe axis, tilted off vertical, and the axis across it. Written out
// rather than normalized, which is not a constant expression here.
const vec2 transAxis = vec2(0.19612, 0.98058);
const vec2 transCross = vec2(0.98058, -0.19612);

const float transWaveAmp = 0.035;   // displacement of the stripes, in screen heights
const float transWaveFreq = 7.0;    // waves across one screen width
const float transWaveSpeed = 1.6;   // radians a second
const float transScroll = 0.07;     // screen heights a second, along the stripe axis

const float transRippleFreq = 26.0; // fine shimmer riding on the wave
const float transRippleSpeed = 3.7;

const float transSheenPeriod = 2.2; // screen widths between sheens
const float transSheenSpeed = 0.8;  // screen widths a second
const float transSheenWidth = 0.09;

// How far a stripe edge is smeared, in stripe widths, on top of the one pixel
// of antialiasing. Enough to read as a soft blend between the colours.
const float transEdge = 0.055;

const vec3 transBlue = vec3(0.357, 0.808, 0.980);
const vec3 transPink = vec3(0.961, 0.663, 0.722);
const vec3 transWhite = vec3(1.0);

float transStep(float edge, float x, float width) {
    return smoothstep(edge - width, edge + width, x);
}

// The flag across one period: blue, pink, white, pink, blue. The last stripe is
// the same colour as the first, so the pattern joins itself and the seam at the
// end of a period needs no blend of its own.
vec3 transStripes(float s, float width) {
    vec3 col = transBlue;
    col = mix(col, transPink, transStep(1.0, s, width));
    col = mix(col, transWhite, transStep(2.0, s, width));
    col = mix(col, transPink, transStep(3.0, s, width));
    col = mix(col, transBlue, transStep(4.0, s, width));
    return col;
}

vec4 chamsFill(vec2 uv, vec3 color) {
    float time = chamsTime();

    // Aspect corrected, so the stripes keep their angle and the wave keeps its
    // shape whatever the window is; one unit is one screen height.
    vec2 p = uv * OutSize / min(OutSize.x, OutSize.y);

    float along = dot(p, transAxis);
    float across = dot(p, transCross);

    // A sine is unchanged by a whole turn of its argument, so wrapping the time
    // the phase is built from leaves the wave bit for bit what it was.
    float wavePhase = transWaveFreq * across - transWaveSpeed * mod(time, transTau / transWaveSpeed);
    float ripplePhase = transRippleFreq * across - transRippleSpeed * mod(time, transTau / transRippleSpeed);

    // Whole flags are interchangeable, so the scroll only has to stay inside
    // one period of the pattern to leave the stripes where they were.
    float drift = transScroll * mod(time, transHeight / transScroll);

    float band = along + transWaveAmp * sin(wavePhase) - drift;

    // Stripe coordinate, 0..5 across one flag. The edge width is measured on
    // the unwrapped band, because fwidth of the wrapped one spikes at the seam
    // and would blur the join into a line.
    float s = fract(band / transHeight) * 5.0;
    float width = fwidth(band) * 5.0 / transHeight * 0.5 + transEdge;

    vec3 col = transStripes(s, width);

    // Cloth lighting: the slope of the wave is its derivative, so the faces
    // turning towards the light brighten and the ones turning away fall off.
    // The fine ripple rides on top at a fraction of the depth.
    float slope = cos(wavePhase);
    float shade = 0.80 + 0.20 * slope + 0.05 * sin(ripplePhase);
    col *= shade;

    // A sheen rolling across the stripes, nearest the white the closer it is.
    // It repeats every transSheenPeriod and moves at transSheenSpeed, so one
    // period of travel is exactly one sheen apart: wrapping there is exact.
    float sweep = across + transSheenSpeed * mod(time, transSheenPeriod / transSheenSpeed);
    float sheen = fract(sweep / transSheenPeriod) - 0.5;
    sheen = exp(-sheen * sheen / (transSheenWidth * transSheenWidth));
    col += vec3(0.55, 0.50, 0.60) * sheen;

    // Keep a trace of the entity's own colour so the chams still says who this
    // is, without pulling the flag off its colours.
    col = mix(col, col * (0.4 + 0.9 * color), 0.18);

    return vec4(clamp(col, 0.0, 1.0), FillAlpha);
}

#moj_import <homovore-2:chams_main.glsl>
