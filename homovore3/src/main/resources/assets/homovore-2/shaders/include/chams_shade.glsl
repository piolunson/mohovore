#version 330

// Everything the composite pass needs before it can shade a pixel, shared by
// every fill shader. A fill variant imports this, defines chamsFill, and then
// imports chams_main.glsl, which is the pass itself.

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;
uniform sampler2D OrigSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ChamsStyle {
    int Radius;
    float OutlineWidth;
    float OutlineAlpha;
    float GlowRadius;
    float GlowIntensity;
    float FillAlpha;
    float FillSpeed;
};

in vec2 texCoord;

out vec4 fragColor;

// What a fill shader answers: the colour to paint a pixel that the silhouette
// covers, given that pixel's screen coordinate and the entity's own colour.
// The alpha it returns is final, so a variant scales it by FillAlpha itself.
vec4 chamsFill(vec2 uv, vec3 color);

// Seconds since the world started, near enough: GameTime is the day cycle as a
// fraction, smoothed by the partial tick, so it wraps every twenty minutes.
//
// It is a large number, and a fill that feeds it straight into sin, cos or mod
// will jitter once it grows, since a float carries about seven digits in total.
// Wrap it to a period the effect is invariant under before using it.
float chamsTime() {
    return GameTime * 24000.0 / 20.0 * FillSpeed;
}
