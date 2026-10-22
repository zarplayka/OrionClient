#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

const float BLUR_OFFSET = 4.5;
const float GLASS_OPACITY = 0.22;

void main() {
    vec4 foreground = texture(Sampler0, texCoord0);
    float coverage = texture(Sampler2, texCoord0).a;
    if (coverage <= 0.001) {
        if (foreground.a <= 0.001) discard;
        fragColor = foreground * vertexColor * ColorModulator;
        return;
    }

    vec2 pixel = 1.0 / vec2(textureSize(Sampler1, 0));
    vec2 screenUv = gl_FragCoord.xy * pixel;
    vec2 uvMin = pixel * 0.5;
    vec2 uvMax = vec2(1.0) - uvMin;
    vec2 offset = pixel * BLUR_OFFSET;
    // Пять билинейных выборок вместо 49 выборок с exp на каждом пикселе панели.
    vec3 blurred = texture(Sampler1, clamp(screenUv, uvMin, uvMax)).rgb * 0.5;
    blurred += texture(Sampler1, clamp(screenUv + offset, uvMin, uvMax)).rgb * 0.125;
    blurred += texture(Sampler1, clamp(screenUv - offset, uvMin, uvMax)).rgb * 0.125;
    blurred += texture(Sampler1, clamp(screenUv + vec2(offset.x, -offset.y), uvMin, uvMax)).rgb * 0.125;
    blurred += texture(Sampler1, clamp(screenUv + vec2(-offset.x, offset.y), uvMin, uvMax)).rgb * 0.125;
    float luminance = dot(blurred, vec3(0.2126, 0.7152, 0.0722));
    // Сохраняем цвет мира, слегка приглушая детали под светлыми надписями.
    vec3 glass = mix(vec3(luminance), blurred, 1.08);
    glass = mix(glass, vec3(0.18, 0.19, 0.20), 0.16);
    float glassAlpha = coverage * GLASS_OPACITY;
    float alpha = foreground.a + glassAlpha * (1.0 - foreground.a);
    vec3 premultiplied = foreground.rgb * foreground.a
            + glass * glassAlpha * (1.0 - foreground.a);
    fragColor = vec4(premultiplied / max(alpha, 0.001), alpha) * vertexColor * ColorModulator;
}
