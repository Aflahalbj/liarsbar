"""Suara roulette, disintesis sendiri (tanpa sampel luar; lihat CREDITS.txt).

    python tools/make_sounds.py            -> semua suara ke src/main/resources/assets/liarsbar/sounds
    python tools/make_sounds.py gunshot    -> hanya yang disebut
    python tools/make_sounds.py --preview  -> juga tulis WAV ke folder pratinjau (argumen berikutnya)

Semua mono 44,1 kHz (suara posisional Minecraft harus mono), OGG Vorbis lewat ffmpeg.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy import signal

SR = 44100
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets",
                    "liarsbar", "sounds")
rng = np.random.default_rng(1234)


# ------------------------------------------------------------ bahan dasar

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def noise(seconds):
    return rng.standard_normal(int(seconds * SR))


def env_exp(seconds, decay, attack=0.0005):
    t = t_axis(seconds)
    a = np.clip(t / max(attack, 1e-6), 0, 1)
    return a * np.exp(-t / decay)


def lowpass(x, hz, order=4):
    b, a = signal.butter(order, hz / (SR / 2), "low")
    return signal.lfilter(b, a, x)


def highpass(x, hz, order=4):
    b, a = signal.butter(order, hz / (SR / 2), "high")
    return signal.lfilter(b, a, x)


def bandpass(x, lo, hi, order=3):
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], "band")
    return signal.lfilter(b, a, x)


def place(dst, src, at):
    i = int(at * SR)
    n = min(len(src), len(dst) - i)
    if n > 0:
        dst[i:i + n] += src[:n]
    return dst


def ring(seconds, freqs, decay, amps=None):
    """Logam berdenting: beberapa mode sinus yang meluruh (sedikit tak harmonis)."""
    t = t_axis(seconds)
    out = np.zeros_like(t)
    amps = amps or [1.0] * len(freqs)
    for f, a in zip(freqs, amps):
        out += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-t / (decay * (1200.0 / f) ** 0.3))
    return out


def click(seconds=0.06, bright=6000, body=1800, decay=0.004, ring_freqs=(3150, 4710, 6220), ring_decay=0.03,
          ring_amp=0.35):
    """Klik mekanik logam: ledakan noise sangat pendek + denting singkat."""
    n = noise(seconds) * env_exp(seconds, decay)
    tick = bandpass(n, body, min(bright, SR / 2 - 100)) * 1.4 + highpass(n, bright) * 0.5
    return tick + ring(seconds, list(ring_freqs), ring_decay) * ring_amp * env_exp(seconds, 0.02, 0.0002)


def reverb(x, seconds=0.9, mix=0.25, damp=3500):
    """Gema ruangan sederhana: konvolusi dengan respons impuls noise yang meluruh."""
    ir = noise(seconds) * np.exp(-t_axis(seconds) / (seconds / 5))
    ir = lowpass(ir, damp, 2)
    ir[0] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    wet = signal.fftconvolve(x, ir)[:len(x) + int(seconds * SR)]
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    return dry * (1 - mix) + wet * mix


def normalize(x, peak=0.9):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def fade_edges(x, ms_in=1.0, ms_out=15.0):
    n_in = int(ms_in / 1000 * SR)
    n_out = int(ms_out / 1000 * SR)
    if n_in:
        x[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


# ------------------------------------------------------------ suara

def heartbeat():
    """Satu detak "lub-dub": dua dentuman rendah teredam, yang kedua lebih pelan."""
    def thump(freq, dur, decay, amp):
        t = t_axis(dur)
        sweep = freq * (1 + 0.6 * np.exp(-t / 0.02))
        phase = 2 * np.pi * np.cumsum(sweep) / SR
        body = np.sin(phase) * env_exp(dur, decay, 0.004)
        thud = lowpass(noise(dur), 180) * env_exp(dur, decay * 0.5, 0.002) * 0.6
        return (body + thud) * amp
    out = np.zeros(int(0.62 * SR))
    place(out, thump(52, 0.3, 0.07, 1.0), 0.0)
    place(out, thump(46, 0.3, 0.06, 0.62), 0.19)
    out = lowpass(out, 220, 2)
    return fade_edges(normalize(out, 0.95))


def revolver_spin():
    """Silinder diputar: deret klik ratchet yang melambat (sekitar 1,3 detik) di atas desis gesekan."""
    dur = 1.6
    out = np.zeros(int(dur * SR))
    tm = 0.02
    gap = 0.028
    k = 0
    while tm < 1.35:
        c = click(0.05, bright=7000, body=2400, decay=0.0025, ring_freqs=(4200, 5900, 7300), ring_decay=0.012,
                  ring_amp=0.25)
        amp = 0.55 + 0.45 * (1 - tm / 1.35) * (1 if k % 6 else 1.25)
        place(out, c * amp, tm + rng.uniform(-0.0015, 0.0015))
        gap *= 1.07
        tm += gap
        k += 1
    whir = bandpass(noise(dur), 900, 4000) * np.exp(-t_axis(dur) / 0.5) * 0.06
    out += whir
    return fade_edges(normalize(reverb(out, 0.35, 0.12), 0.8))


def revolver_open():
    out = np.zeros(int(0.35 * SR))
    place(out, click(0.08, 5200, 1500, 0.006, (2300, 3400, 5100), 0.04, 0.45), 0.0)
    place(out, click(0.06, 6500, 2000, 0.003, (3100, 4400), 0.02, 0.3) * 0.5, 0.05)
    return fade_edges(normalize(reverb(out, 0.3, 0.12), 0.75))


def revolver_close():
    """Silinder dibanting menutup: klak tajam dengan denting panjang."""
    out = np.zeros(int(0.5 * SR))
    place(out, click(0.2, 5000, 1300, 0.008, (1900, 2850, 4300, 6100), 0.08, 0.6), 0.0)
    return fade_edges(normalize(reverb(out, 0.4, 0.15), 0.85))


def hammer_cock():
    """Hammer dikokang: dua tahap, klik pelan lalu klak yang mengunci."""
    out = np.zeros(int(0.5 * SR))
    place(out, click(0.05, 6000, 2600, 0.003, (3600, 5200), 0.015, 0.2) * 0.45, 0.0)
    place(out, bandpass(noise(0.12), 1500, 5000) * env_exp(0.12, 0.03) * 0.15, 0.02)
    place(out, click(0.16, 5500, 1700, 0.006, (2600, 3900, 5600), 0.05, 0.55), 0.17)
    return fade_edges(normalize(reverb(out, 0.35, 0.12), 0.85))


def dry_fire():
    """Pelatuk ditarik, chamber kosong: hammer jatuh, klik keras yang kering."""
    out = np.zeros(int(0.6 * SR))
    place(out, click(0.04, 7000, 2800, 0.002, (4100,), 0.008, 0.1) * 0.25, 0.0)
    place(out, click(0.3, 4800, 1200, 0.007, (1700, 2600, 3900, 5400), 0.06, 0.55) * 1.0, 0.05)
    return fade_edges(normalize(reverb(out, 0.6, 0.2), 0.9))


def gunshot():
    """Revolver di dalam bar: transien tajam, badan ledakan, dentuman rendah, gema ruangan."""
    dur = 2.6
    out = np.zeros(int(dur * SR))
    crack = highpass(noise(0.012), 2500) * env_exp(0.012, 0.002, 0.0001) * 1.0
    place(out, crack, 0.0)
    blast_n = noise(0.5)
    blast = lowpass(blast_n, 3500, 2) * env_exp(0.5, 0.06, 0.0004) * 1.2
    place(out, blast, 0.0)
    t = t_axis(0.8)
    boom = np.sin(2 * np.pi * np.cumsum(70 * (1 + 1.5 * np.exp(-t / 0.03))) / SR) * env_exp(0.8, 0.16, 0.002)
    place(out, boom * 0.9, 0.0)
    mech = click(0.1, 6000, 2000, 0.004, (2900, 4100), 0.03, 0.3) * 0.25
    place(out, mech, 0.0)
    tail = lowpass(noise(dur), 1400, 2) * np.exp(-t_axis(dur) / 0.45) * 0.18
    out += tail
    out = reverb(out, 1.4, 0.3, 2500)
    out = np.tanh(out * 2.2) / np.tanh(2.2)
    return fade_edges(normalize(out, 0.98), 0.2, 80)


def tinnitus():
    """Dengung telinga sesudah tembakan: nada tinggi tipis yang bergetar lalu memudar pelan."""
    dur = 5.0
    t = t_axis(dur)
    f1, f2 = 4180, 4196
    tone = np.sin(2 * np.pi * f1 * t) * 0.6 + np.sin(2 * np.pi * f2 * t) * 0.4
    tone += 0.15 * np.sin(2 * np.pi * 6230 * t + 0.4)
    env = np.clip(t / 0.08, 0, 1) * np.exp(-t / 1.9)
    hiss = bandpass(noise(dur), 3000, 9000) * 0.04 * np.exp(-t / 1.2)
    return fade_edges(normalize(tone * env + hiss, 0.5), 2, 300)


def breath(dur, lo, hi, attack, release, amp, shape=1.0):
    t = t_axis(dur)
    env = np.minimum(np.clip(t / attack, 0, 1) ** shape, np.clip((dur - t) / release, 0, 1))
    n = bandpass(noise(dur), lo, hi, 2)
    formant = bandpass(noise(dur), 900, 1500, 2) * 0.6
    return (n + formant) * env * amp


def exhale_relief():
    """Napas lega: hembusan panjang yang bergetar sedikit."""
    dur = 1.9
    out = breath(dur, 300, 2600, 0.12, 1.2, 1.0, 0.7)
    t = t_axis(dur)
    out *= 1 + 0.15 * np.sin(2 * np.pi * 7 * t)
    out = lowpass(out, 2800, 2)
    return fade_edges(normalize(reverb(out, 0.4, 0.1), 0.6), 30, 200)


def inhale_tense():
    """Tarikan napas tertahan sebelum menarik pelatuk."""
    dur = 0.9
    out = breath(dur, 600, 4200, 0.5, 0.12, 1.0, 1.6)
    out = highpass(out, 400, 2)
    return fade_edges(normalize(reverb(out, 0.3, 0.08), 0.45), 20, 60)


def body_fall():
    """Badan jatuh dari kursi: dentuman berat di lantai kayu, kursi ikut berderak."""
    dur = 1.4
    out = np.zeros(int(dur * SR))
    t = t_axis(0.6)
    thud = np.sin(2 * np.pi * np.cumsum(62 * (1 + 0.8 * np.exp(-t / 0.03))) / SR) * env_exp(0.6, 0.09, 0.003)
    thud += lowpass(noise(0.6), 400) * env_exp(0.6, 0.05, 0.002) * 0.8
    place(out, thud, 0.0)
    for at, amp in ((0.18, 0.5), (0.27, 0.32), (0.33, 0.2)):
        knock = bandpass(noise(0.12), 250, 1800) * env_exp(0.12, 0.018, 0.001)
        knock += ring(0.12, [410, 690], 0.04) * 0.3 * env_exp(0.12, 0.03)
        place(out, knock * amp, at)
    return fade_edges(normalize(reverb(out, 0.7, 0.22), 0.85))


def gun_table():
    """Revolver diletakkan di meja kayu."""
    out = np.zeros(int(0.5 * SR))
    knock = bandpass(noise(0.12), 300, 2200) * env_exp(0.12, 0.02, 0.001)
    knock += ring(0.12, [520, 880], 0.04) * 0.25 * env_exp(0.12, 0.03)
    place(out, knock, 0.0)
    place(out, click(0.08, 6000, 2400, 0.003, (3300, 4700), 0.02, 0.2) * 0.35, 0.012)
    return fade_edges(normalize(reverb(out, 0.4, 0.15), 0.7))


def gun_pickup():
    """Revolver diambil dari meja: geser pelan lalu denting kecil."""
    dur = 0.45
    out = bandpass(noise(dur), 1200, 6000) * np.minimum(np.clip(t_axis(dur) / 0.1, 0, 1),
                                                        np.exp(-t_axis(dur) / 0.12)) * 0.25
    place(out, click(0.08, 6500, 2600, 0.003, (3500, 5100), 0.02, 0.2) * 0.5, 0.2)
    return fade_edges(normalize(reverb(out, 0.3, 0.1), 0.55))


ALL = {
    "heartbeat": heartbeat,
    "revolver_spin": revolver_spin,
    "revolver_open": revolver_open,
    "revolver_close": revolver_close,
    "hammer_cock": hammer_cock,
    "dry_fire": dry_fire,
    "gunshot": gunshot,
    "tinnitus": tinnitus,
    "exhale_relief": exhale_relief,
    "inhale_tense": inhale_tense,
    "body_fall": body_fall,
    "gun_table": gun_table,
    "gun_pickup": gun_pickup,
}


def write(name, data, preview=None):
    from scipy.io import wavfile
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    os.makedirs(ROOT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        wavfile.write(wav, SR, pcm)
        out = os.path.join(ROOT, name + ".ogg")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", out],
                       check=True)
        if preview:
            os.makedirs(preview, exist_ok=True)
            wavfile.write(os.path.join(preview, name + ".wav"), SR, pcm)
    print("%-15s %5.2f s" % (name, len(data) / SR))


if __name__ == "__main__":
    args = sys.argv[1:]
    preview = None
    if "--preview" in args:
        i = args.index("--preview")
        preview = args[i + 1]
        args = args[:i] + args[i + 2:]
    for name, fn in ALL.items():
        if not args or name in args:
            write(name, fn(), preview)
