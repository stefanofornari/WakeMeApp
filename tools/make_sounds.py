#!/usr/bin/env python3
"""Generates the bundled notification sounds (notify/US-000008): short, original, synthesized tones.

Usage: python3 tools/make_sounds.py [output-folder]
(default: src/main/resources/ste/wakemeapp/notify/sounds). 16-bit mono WAV at 44.1 kHz, peak about -2 dBFS,
so the volume setting has room both ways. Change the parameters below and run again to tweak a sound.
"""
import math
import random
import struct
import sys
import wave

RATE = 44100
PEAK = 0.8


def tone(freq, seconds, decay, start=0.0, gain=1.0, glide=0.0):
    """A sine with exponential decay and a 5 ms attack; glide is the frequency drop over the tone (Hz)."""
    n = int(seconds * RATE)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = freq - glide * (t / seconds)
        phase += 2 * math.pi * f / RATE
        attack = min(1.0, t / 0.005)
        out.append(gain * attack * math.exp(-decay * t) * math.sin(phase))
    return [0.0] * int(start * RATE) + out


def mix(*tracks):
    length = max(len(t) for t in tracks)
    out = [0.0] * length
    for t in tracks:
        for i, v in enumerate(t):
            out[i] += v
    return out


def normalise(samples):
    peak = max(abs(v) for v in samples) or 1.0
    fade = int(0.01 * RATE)
    for i in range(fade):              # no click at the end
        samples[-1 - i] *= i / fade
    return [v / peak * PEAK for v in samples]


def write(folder, name, samples):
    with wave.open(f"{folder}/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(v * 32767)) for v in normalise(samples)))


def chime():       # two soft rising notes, C6 then E6
    return mix(tone(1046.5, 0.9, 5.0), tone(2093.0, 0.9, 7.0, gain=0.25),
               tone(1318.5, 1.0, 5.0, start=0.18), tone(2637.0, 1.0, 7.0, start=0.18, gain=0.25))


def pop():         # a short drop in pitch, like a bubble
    return mix(tone(900.0, 0.18, 22.0, glide=600.0), tone(450.0, 0.18, 30.0, gain=0.4))


def ping():        # one clear high note
    return mix(tone(1760.0, 0.8, 7.0), tone(3520.0, 0.8, 12.0, gain=0.2))


def bell():        # inharmonic partials, a long soft ring
    return mix(tone(660.0, 1.4, 3.2), tone(1320.0 * 1.0, 1.4, 4.0, gain=0.5),
               tone(1790.0, 1.2, 5.0, gain=0.35), tone(2430.0, 1.0, 7.0, gain=0.2))


if __name__ == "__main__":
    folder = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/ste/wakemeapp/notify/sounds"
    random.seed(1)
    for name, make in (("chime", chime), ("pop", pop), ("ping", ping), ("bell", bell)):
        write(folder, name, make())
