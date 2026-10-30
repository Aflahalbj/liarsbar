// Liar's Table (pack liarsbar:noir): sorot kerucut lampu gantung untuk look noir.
// Beda dengan bawaan Veil: Lambert asli (wrap kecil supaya kepala kotak tidak patah), tanpa ambient
// dan tanpa "reflectivity" yang mengoles warna lampu rata ke semua permukaan, dan kerucut dengan
// inti rata + tepi lembut (kolam cahaya bertepi tegas, bukan gradasi sepanjang kerucut).
#include veil:common
#include veil:deferred_utils
#include veil:color_utilities
#include veil:light

#define WRAP 0.12
#define EDGE 0.72

in mat4 lightMat;
in vec3 lightColor;
in vec2 size;
in float maxAngle;
in float maxDistance;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D DiffuseDepthSampler;

uniform vec2 ScreenSize;

out vec4 fragColor;

float sacos(float x) {
    float y = abs(clamp(x, -1.0, 1.0));
    float z = (-0.168577 * y + 1.56723) * sqrt(1.0 - y);
    return mix(0.5 * 3.1415927, z, sign(x));
}

// Sama dengan Veil, tanpa inverse(mat4) per piksel: matriks lampu = rotasi R + translasi t, jadi
// lokal = R*p - t dan baliknya R^T*(lokal + t).
struct AreaLightResult { vec3 position; float angle; };
AreaLightResult closestPointOnPlaneAndAngle(vec3 point, mat4 planeMatrix, vec2 planeSize) {
    mat3 r = mat3(planeMatrix);
    vec3 t = planeMatrix[3].xyz;
    vec3 localSpacePoint = r * point - t;
    vec3 localSpacePointOnPlane = vec3(clamp(localSpacePoint.xy, -planeSize, planeSize), 0);
    vec3 direction = normalize(localSpacePoint - localSpacePointOnPlane);
    float angle = sacos(direction.z);
    return AreaLightResult(transpose(r) * (localSpacePointOnPlane + t), angle);
}

// Jendela redaman halus: hampir penuh sampai ~70% jarak, lalu turun ke 0 (bukan (1-s)^3 bawaan Veil).
float window(float distance, float radius) {
    float s = clamp(distance / radius, 0.0, 1.0);
    float w = 1.0 - s * s * s * s;
    return w * w;
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / ScreenSize;

    vec4 albedoColor = texture(AlbedoSampler, screenUv);
    if (albedoColor.a == 0) {
        discard;
    }

    vec3 normalVS = texture(NormalSampler, screenUv).xyz;
    float depth = texture(DiffuseDepthSampler, screenUv).r;
    vec3 pos = viewToWorldSpace(viewPosFromDepth(depth, screenUv));

    AreaLightResult info = closestPointOnPlaneAndAngle(pos, lightMat, size);
    vec3 offset = info.position - pos;
    vec3 lightDirection = (VeilCamera.ViewMat * vec4(normalize(offset), 0.0)).xyz;

    float diffuse = clamp((dot(normalVS, lightDirection) + WRAP) / (1.0 + WRAP), 0.0, 1.0);
    diffuse *= window(length(offset), maxDistance);
    float cone = clamp(info.angle / maxAngle, 0.0, 1.0);
    diffuse *= 1.0 - smoothstep(EDGE, 1.0, cone);

    fragColor = vec4(albedoColor.rgb * diffuse * lightColor, albedoColor.a);
}
