#!/usr/bin/env python3
"""Synthesizes the control sounds into talkback/src/main/res/raw/control_*.wav.

    python3 make_sounds.py

The sounds are made here from sine waves and noise, so they are original and fall under
Backtalk's license. They are short and full of high frequencies, because the 3D effect needs
those to tell where a sound is. Each is mono, 16 bit, 44.1 kHz, the rate of the HRTFs, and
peaks at half of full scale to leave room for the HRTF filters.
"""

import math
import os
import random
import struct
import wave

RATE = 44100
PEAK = 0.5
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "talkback", "src", "main", "res", "raw")


def silence(seconds):
    return [0.0] * int(seconds * RATE)


def mix_into(buffer, sound, at_seconds=0.0, gain=1.0):
    start = int(at_seconds * RATE)
    if len(buffer) < start + len(sound):
        buffer.extend([0.0] * (start + len(sound) - len(buffer)))
    for i, s in enumerate(sound):
        buffer[start + i] += s * gain
    return buffer


def mode(freq, decay, seconds, phase=0.0):
    """A struck resonance: a sine that dies away with time constant decay."""
    return [
        math.sin(2 * math.pi * freq * i / RATE + phase) * math.exp(-i / RATE / decay)
        for i in range(int(seconds * RATE))
    ]


def glide(start_freq, end_freq, seconds, harmonics=(1.0,), attack=0.004):
    """A tone whose pitch slides exponentially, with a short fade in and a fade out."""
    out = []
    phase = 0.0
    n = int(seconds * RATE)
    for i in range(n):
        t = i / n
        freq = start_freq * (end_freq / start_freq) ** t
        phase += 2 * math.pi * freq / RATE
        envelope = min(1.0, i / RATE / attack) * (1 - t) ** 2
        out.append(envelope * sum(a * math.sin(k * phase) for k, a in enumerate(harmonics, 1)))
    return out


def noise(seconds, seed):
    rng = random.Random(seed)
    return [rng.uniform(-1, 1) for _ in range(int(seconds * RATE))]


def bandpass(signal, center, q, end_center=None):
    """A two-pole resonant filter, optionally sweeping its center to end_center."""
    out = []
    y1 = y2 = 0.0
    n = len(signal)
    for i, x in enumerate(signal):
        c = center if end_center is None else center * (end_center / center) ** (i / n)
        w = 2 * math.pi * c / RATE
        r = math.exp(-w / (2 * q))
        y = (1 - r) * x + 2 * r * math.cos(w) * y1 - r * r * y2
        y2, y1 = y1, y
        out.append(y)
    return out


def decay_envelope(signal, decay, attack=0.001):
    return [
        s * min(1.0, i / RATE / attack) * math.exp(-i / RATE / decay)
        for i, s in enumerate(signal)
    ]


def click(seconds=0.004, seed=0, center=5000):
    return decay_envelope(bandpass(noise(seconds, seed), center, 1.5), seconds / 3)


def button():
    # A wood block: two struck resonances and a click.
    out = mix_into(silence(0.08), mode(1100, 0.018, 0.08))
    mix_into(out, mode(2650, 0.010, 0.08), gain=0.6)
    return mix_into(out, click(seed=1), gain=0.8)


def checkbox():
    # Two quick ticks, like a box being marked.
    out = silence(0.11)
    for at, seed in ((0.0, 2), (0.045, 3)):
        tick = mix_into(mode(1800, 0.008, 0.04), click(seed=seed, center=3200), gain=1.2)
        mix_into(out, tick, at)
    return out


def switch():
    # Noise sweeping upward, then a soft catch, like a lever flicked over.
    sweep = decay_envelope(bandpass(noise(0.09, 4), 700, 4, end_center=4200), 0.06, attack=0.02)
    out = mix_into(silence(0.12), sweep, gain=4.0)
    return mix_into(out, mix_into(mode(1500, 0.01, 0.03), click(seed=5)), 0.085, gain=0.7)


def radio_button():
    # A round bloop that rises in pitch.
    out = mix_into(silence(0.1), glide(600, 1150, 0.1, harmonics=(1.0, 0.35, 0.15)))
    return mix_into(out, click(seed=6, center=3000), gain=0.6)


def edit_text():
    # A typewriter clack: noise through two resonances over a low thump.
    clack = noise(0.06, 7)
    body = [a + b for a, b in zip(bandpass(clack, 2300, 6), bandpass(clack, 4600, 5))]
    out = mix_into(silence(0.09), decay_envelope(body, 0.015), gain=3.0)
    return mix_into(out, mode(210, 0.02, 0.06), gain=0.5)


def combo_box():
    # Three falling ticks, like a list dropping open.
    out = silence(0.13)
    for k, freq in enumerate((1700, 1350, 1050)):
        tick = mix_into(mode(freq, 0.012, 0.05), click(seed=8 + k, center=4000), gain=0.6)
        mix_into(out, tick, k * 0.032)
    return out


def image():
    # A glassy bell: frequency modulation that softens as it rings.
    out = []
    n = int(0.16 * RATE)
    for i in range(n):
        t = i / RATE
        index = 2.5 * math.exp(-t / 0.03)
        modulator = math.sin(2 * math.pi * 1500 * 1.41 * t)
        out.append(math.sin(2 * math.pi * 1500 * t + index * modulator) * math.exp(-t / 0.045))
    return [s * min(1.0, i / RATE / 0.002) for i, s in enumerate(out)]


def slider():
    # A zip: a bright tone sliding upward with a little breath.
    out = mix_into(silence(0.12), glide(450, 1700, 0.11, harmonics=(1.0, 0.5, 0.33, 0.25, 0.2)))
    breath = decay_envelope(bandpass(noise(0.11, 11), 1200, 2, end_center=5000), 0.05, attack=0.01)
    return mix_into(out, breath, gain=1.5)


def link():
    # Two bell notes going up, like a chain link chiming.
    out = silence(0.15)
    for at, freq in ((0.0, 1319), (0.05, 1976)):
        note = mix_into(mode(freq, 0.03, 0.1), mode(freq * 2.76, 0.012, 0.1), gain=0.3)
        mix_into(out, note, at)
    return out


SOUNDS = {
    "control_button": button,
    "control_checkbox": checkbox,
    "control_switch": switch,
    "control_radio_button": radio_button,
    "control_edit_text": edit_text,
    "control_combo_box": combo_box,
    "control_image": image,
    "control_slider": slider,
    "control_link": link,
}


def write(name, samples):
    peak = max(abs(s) for s in samples) or 1.0
    # A 2 ms fade out so no sound ends in a click.
    fade = int(0.002 * RATE)
    for i in range(fade):
        samples[-1 - i] *= i / fade
    frames = b"".join(
        struct.pack("<h", int(round(s / peak * PEAK * 32767))) for s in samples
    )
    path = os.path.join(OUT_DIR, f"{name}.wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(frames)
    print(f"Wrote {os.path.normpath(path)}: {len(samples) * 1000 // RATE} ms")


def main():
    for name, make in SOUNDS.items():
        write(name, make())


if __name__ == "__main__":
    main()
