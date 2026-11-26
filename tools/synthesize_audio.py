"""Regenerate the original Ocean Hunter audio from oscillator and noise voices."""
import math
import random
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

RATE = 22050
TAU = math.tau
DEST = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"

def pitch(note):
    return 440 * 2 ** ((note - 69) / 12)

def save(name, signal):
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "track.wav"
        with wave.open(str(wav), "wb") as audio:
            audio.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
            audio.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x)) * 32767)) for x in signal))
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(wav), "-c:a", "libvorbis",
                        "-q:a", "3", str(DEST / f"{name}.ogg")], check=True)

def cue(name, length, start, end, noise, curve, third):
    rng = random.Random(name)
    signal = []
    for i in range(int(length * RATE)):
        t = i / RATE
        u = t / length
        phase = TAU * (start * t + (end - start) * t * u / 2)
        env = min(t / .015, 1) * (1 - u) ** curve
        signal.append(env * (.46 * math.sin(phase) + .12 * math.sin(phase * third) + noise * rng.uniform(-1, 1)))
    save(name, signal)

def music(name, beat, progression, melody, energy):
    length = beat * 32
    signal = [0.0] * round(length * RATE)
    rng = random.Random(name)

    def voice(start, duration, note, volume, mode):
        freq = pitch(note)
        for i in range(round(duration * RATE)):
            t = i / RATE
            envelope = min(1, t / (.085 if mode == "pad" else .014)) * min(1, (duration - t) / .15)
            phase = TAU * freq * t
            if mode == "pad":
                sample = math.sin(phase) + .18 * math.sin(phase * 2.003) + .12 * math.sin(phase * .501)
                sample *= .64 + .36 * math.sin(TAU * .35 * t + note)
            elif mode == "bell":
                sample = (math.sin(phase) + .24 * math.sin(phase * 2.01)) * math.exp(-t * 4)
            else:
                sample = math.sin(phase + 13 * math.exp(-t * 60)) * math.exp(-t * 9) + .14 * rng.uniform(-1, 1) * math.exp(-t * 25)
            target = (round(start * RATE) + i) % len(signal)
            signal[target] += sample * volume * envelope

    for bar in range(8):
        chord = progression[bar % len(progression)]
        for note in chord:
            voice(bar * 4 * beat, 4.6 * beat, note, .044, "pad")
        voice(bar * 4 * beat, 2.5 * beat, chord[0] - 12, .06, "pad")
        for slot in range(4):
            note = melody[(bar * 4 + slot) % len(melody)]
            if note:
                voice((bar * 4 + slot) * beat, 2.5 * beat, note, .085, "bell")
                voice((bar * 4 + slot + .72) * beat, 1.3 * beat, note, .024, "bell")
            if energy and (slot % 2 == 0 or energy > 1):
                voice((bar * 4 + slot) * beat, beat, 27 + slot * 2, .076 * energy, "kick")
    save(name, signal)

if __name__ == "__main__":
    DEST.mkdir(parents=True, exist_ok=True)
    for entry in [
        ("click", .09, 480, 710, .05, 2, 2), ("fire", .20, 430, 58, .13, 2, 2.03),
        ("blast", .43, 140, 36, .30, 2.2, 1.66), ("coin", .19, 590, 1350, .01, 2, 1.99),
        ("treasure", .42, 392, 1174, .03, 1.6, 1.5), ("shield", .38, 170, 620, .10, 1.3, 1.99),
        ("hurt", .31, 185, 51, .22, 1.8, 1.47), ("upgrade", .57, 388, 893, .02, 1.7, 1.5),
        ("win", .65, 460, 980, .01, 1.1, 1.5), ("lose", .56, 314, 95, .09, 1.3, 2.01)
    ]:
        cue(*entry)
    music("menu", .55, [(48,55,60,64),(45,52,60,64),(41,48,57,60),(43,50,59,62)],
          [72,0,76,79,76,72,0,69,72,0,77,76,74,71,0,67], 0)
    music("explore", .64, [(45,52,57,60),(41,48,53,60),(48,55,60,64),(43,50,55,59)],
          [69,0,72,76,0,77,72,0,67,76,0,72,74,0,71,0], 0)
    music("action", .40, [(45,52,57,60),(43,50,55,59),(41,48,53,57),(40,47,52,56)],
          [69,72,76,72,67,71,74,71,65,69,72,69,68,71,76,80], 1)
    music("boss", .35, [(38,45,50,53),(34,41,46,50),(35,42,47,50),(33,40,45,49)],
          [62,65,69,65,58,62,65,62,59,62,66,62,61,64,69,73], 2)
