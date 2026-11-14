#version 330

// SOLID fill: the entity's own colour, flat, at the configured alpha.

#moj_import <homovore-2:chams_shade.glsl>

vec4 chamsFill(vec2 uv, vec3 color) {
    return vec4(color, FillAlpha);
}

#moj_import <homovore-2:chams_main.glsl>
