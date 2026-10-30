// Liar's Table (pack liarsbar:noir): point light tanpa ambient dan tanpa "reflectivity" bawaan Veil,
// supaya sisi yang membelakangi lampu benar-benar gelap. Wrap sedikit lebih besar dari sorot karena
// point light dipakai untuk pendar bohlam dan pengisi lembut.
#include veil:common
#include veil:deferred_utils
#include veil:color_utilities
#include veil:light

#define WRAP 0.3

in vec3 lightPos;
in vec3 lightColor;
in float radius;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D DiffuseDepthSampler;

uniform vec2 ScreenSize;

out vec4 fragColor;

// Jendela redaman halus (lihat area.fsh), dengan sedikit kuadrat-terbalik supaya titik dekat bohlam lebih terang.
float window(float distance, float radius) {
    float s = clamp(distance / radius, 0.0, 1.0);
    float w = 1.0 - s * s * s * s;
    return w * w / (1.0 + 4.0 * s * s);
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / ScreenSize;

    vec4 albedoColor = texture(AlbedoSampler, screenUv);
    if (albedoColor.a == 0) {
        discard;
    }

    float depth = texture(DiffuseDepthSampler, screenUv).r;
    vec3 pos = viewToWorldSpace(viewPosFromDepth(depth, screenUv));
    vec3 offset = lightPos - pos;

    vec3 normalVS = texture(NormalSampler, screenUv).xyz;
    vec3 lightDirection = (VeilCamera.ViewMat * vec4(normalize(offset), 0.0)).xyz;
    float diffuse = clamp((dot(normalVS, lightDirection) + WRAP) / (1.0 + WRAP), 0.0, 1.0);
    diffuse *= window(length(offset), radius);

    fragColor = vec4(albedoColor.rgb * diffuse * lightColor, albedoColor.a);
}
