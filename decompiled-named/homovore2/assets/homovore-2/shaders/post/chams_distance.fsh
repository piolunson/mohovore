#version 330

// First half of a separable distance transform over the entity outline target.
// For every pixel it finds the closest lit pixel on its own row and writes that
// pixel's colour, with the horizontal distance packed into the alpha channel as
// a fraction of Radius + 1. Alpha 1.0 means "no lit pixel within reach".

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ChamsShape {
    int Radius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float texel = 1.0 / InSize.x;
    float scale = float(Radius) + 1.0;

    vec4 here = texture(InSampler, texCoord);
    if (here.a > 0.0) {
        fragColor = vec4(here.rgb, 0.0);
        return;
    }

    for (int i = 1; i <= Radius; i++) {
        vec4 left = texture(InSampler, texCoord - vec2(texel * float(i), 0.0));
        vec4 right = texture(InSampler, texCoord + vec2(texel * float(i), 0.0));
        if (left.a > 0.0 || right.a > 0.0) {
            fragColor = vec4(left.a > 0.0 ? left.rgb : right.rgb, float(i) / scale);
            return;
        }
    }

    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
}
