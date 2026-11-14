// Second half of the distance transform, and the shading that comes with it.
// Walking the column and combining each row distance with the row offset gives
// the true distance to the nearest lit pixel, which the outline and the glow
// are both read off. Pixels the silhouette covers never get here: they are the
// fill, and belong to whichever fill shader imported this file.

void main() {
    vec4 inside = texture(OrigSampler, texCoord);
    if (inside.a > 0.0) {
        fragColor = chamsFill(texCoord, inside.rgb);
        return;
    }

    float texel = 1.0 / InSize.y;
    float reach = float(Radius);
    float scale = reach + 1.0;

    float distance = scale;
    vec3 color = vec3(0.0);
    for (int dy = -Radius; dy <= Radius; dy++) {
        vec4 row = texture(InSampler, texCoord + vec2(0.0, texel * float(dy)));
        float dx = row.a * scale;
        if (dx > reach) continue;
        float d = sqrt(dx * dx + float(dy) * float(dy));
        if (d < distance) {
            distance = d;
            color = row.rgb;
        }
    }

    if (distance > reach) {
        fragColor = vec4(0.0);
        return;
    }

    // Half a pixel of slack on the outline edge keeps it from stair-stepping.
    float alpha = OutlineAlpha * clamp(OutlineWidth + 0.5 - distance, 0.0, 1.0);
    if (GlowRadius > 0.0) {
        float falloff = 1.0 - min(distance / GlowRadius, 1.0);
        alpha = max(alpha, clamp(GlowIntensity * falloff * falloff, 0.0, 1.0));
    }

    fragColor = vec4(color, alpha);
}
