"""
The "order created" chime (app/src/main/res/raw/order_created.wav), made from scratch here
— pure sine tones, no samples or recordings — so it's ours and free of anyone's copyright.

A bright three-note rise (G5 → C6 → E6), each note a soft bell: a few partials, a 6 ms
attack and an exponential fade. Mono, 44.1 kHz, 16-bit, about 1.1 s, peak ≈ −3 dBFS.

    python3 tools/sounds/make_order_chime.py
"""
import math
import os
import struct
import wave

RATE = 44100
LENGTH = 1.1  # seconds
# (start s, frequency Hz, loudness)
NOTES = [(0.00, 783.99, 0.80), (0.11, 1046.50, 0.85), (0.22, 1318.51, 1.00)]
# (frequency multiple, loudness) — a touch of bell, not a pure beep
PARTIALS = [(1.0, 1.0), (2.0, 0.32), (3.0, 0.10), (4.2, 0.04)]
ATTACK = 0.006
DECAY = 0.32  # time constant, s

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "raw", "order_created.wav")


def main():
    n = int(RATE * LENGTH)
    buf = [0.0] * n
    for start, freq, loud in NOTES:
        s0 = int(start * RATE)
        for i in range(s0, n):
            t = (i - s0) / RATE
            env = (t / ATTACK if t < ATTACK else 1.0) * math.exp(-t / DECAY)
            if env < 1e-4:
                break
            v = 0.0
            for mult, amp in PARTIALS:
                v += amp * math.sin(2 * math.pi * freq * mult * t)
            buf[i] += loud * env * v
    # a short fade-out so the end never clicks
    fade = int(0.08 * RATE)
    for k in range(fade):
        buf[n - fade + k] *= 1 - k / fade
    peak = max(abs(x) for x in buf) or 1.0
    gain = (10 ** (-3 / 20)) / peak
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with wave.open(OUT, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, x * gain)) * 32767)) for x in buf))
    print(os.path.normpath(OUT), os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
