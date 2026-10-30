// Grading noir meja (Art Bible): bloom berambang, S-curve, black point, split-tone halus, grain, vignette,
// letterbox 2,39:1 saat sinematik, plus efek roulette (CA + vignette berdenyut, desaturasi, kilat, gelap).
// Tanpa depth of field: blur tidak boleh menutupi kesalahan. Lihat PostFx.java.
uniform sampler2D DiffuseSampler0;

uniform float Vignette;
uniform float Pulse;
uniform float Aberration;
uniform float Desaturate;
uniform float Flash;
uniform float Darken;
uniform float Grain;
uniform float Bloom;
uniform float BloomThreshold;
uniform float Contrast;
uniform float Curve;
uniform float BlackPoint;
uniform float BaseVignette;
uniform float Exposure;
uniform float Letterbox;
uniform vec2 Texel;
uniform float Seed;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7)) + Seed) * 43758.5453);
}

vec3 sampleCA(vec2 uv, vec2 offset) {
    return vec3(texture(DiffuseSampler0, uv + offset).r,
                texture(DiffuseSampler0, uv).g,
                texture(DiffuseSampler0, uv - offset).b);
}

vec3 bright(vec2 uv) {
    vec3 c = texture(DiffuseSampler0, uv).rgb;
    float l = max(max(c.r, c.g), c.b);
    return c * smoothstep(BloomThreshold, 1.0, l);
}

void main() {
    vec2 uv = texCoord;
    vec2 centered = uv - 0.5;
    float aspect = Texel.y / Texel.x;

    // Letterbox: bar hitam atas-bawah (gambar tidak dipotong, hanya ditutup).
    float bar = Letterbox * 0.128;
    if (uv.y < bar || uv.y > 1.0 - bar) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 color;
    if (Aberration > 0.0001) {
        color = sampleCA(uv, centered * Aberration * (1.0 + 2.0 * dot(centered, centered)));
    } else {
        color = texture(DiffuseSampler0, uv).rgb;
    }

    // Bloom: hanya yang benar-benar terang (bohlam, kaca jendela, strip rak, api moncong).
    if (Bloom > 0.001) {
        // Spiral sudut emas yang diputar acak per piksel (interleaved gradient noise), bobot Gaussian:
        // sumber kecil yang terang menyebar halus, tidak berlipat jadi salinan kotak di titik sampel tetap.
        vec3 glow = vec3(0.0);
        float total = 0.0;
        float spin = 6.2832 * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        const int TAPS = 32;
        for (int i = 0; i < TAPS; i++) {
            float f = (float(i) + 0.5) / float(TAPS);
            float radius = 0.026 * sqrt(f);
            float a = float(i) * 2.39996 + spin;
            float w = exp(-3.0 * f);
            glow += bright(uv + vec2(cos(a), sin(a)) * radius * vec2(1.0, aspect)) * w;
            total += w;
        }
        color += glow / total * Bloom * 2.2;
    }

    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, vec3(luma), Desaturate);

    color *= Exposure;
    // Bahu filmik: highlight (atas kepala di bawah sorot, kulit terang) tidak terpotong putih rata.
    color = color * (1.0 + color * 0.15) / (1.0 + color * 0.55);
    // Black point + S-curve: hitam jadi pekat, highlight terbuka.
    color = max(color - BlackPoint, 0.0) / (1.0 - BlackPoint);
    vec3 k = vec3(Contrast);
    vec3 s = pow(color, k) / (pow(color, k) + pow(1.0 - min(color, 0.9999), k));
    color = mix(color, s, Curve);
    // Split-tone halus: bayangan sedikit ke cyan, highlight sedikit hangat.
    luma = dot(color, vec3(0.299, 0.587, 0.114));
    color += (1.0 - smoothstep(0.0, 0.25, luma)) * vec3(-0.004, 0.002, 0.008);
    color += smoothstep(0.55, 1.0, luma) * vec3(0.02, 0.005, -0.015);

    // Vignette dasar + vignette roulette yang menyempit tiap detak jantung.
    float d = length(centered * vec2(1.0, 0.82));
    color *= 1.0 - BaseVignette * smoothstep(0.38, 0.85, d);
    float inner = mix(0.62, 0.48, Pulse * 0.6);
    float v = 1.0 - smoothstep(inner - 0.42, inner + 0.18, d);
    color *= mix(1.0, v, clamp(Vignette, 0.0, 1.0));
    color += vec3(0.06, 0.0, 0.0) * Pulse * Vignette * (1.0 - v);

    color += (hash(uv * 1000.0) - 0.5) * Grain * (0.5 + 0.5 * (1.0 - luma));
    color = mix(color, vec3(1.0, 0.98, 0.94), clamp(Flash, 0.0, 1.0));
    color = mix(color, vec3(0.0), clamp(Darken, 0.0, 1.0));
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
