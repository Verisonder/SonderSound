"""
Reference for the detector in app/src/main/java/.../detect. Replays enrolment and
streaming detection on speech synthesised with espeak-ng, using the same models as the app.

    apt install espeak-ng && pip install numpy scipy ai-edge-litert
    cp ../../app/src/main/assets/*.tflite .
    python3 replay.py            # scores and false alerts
    python3 replay.py --background   # rebuilds background.f32

Kotlin tests pin the pure parts (trim, speech mask, padding) to what this file computes.
"""
import sys, numpy as np
from synth import say
import emb as EM

R = 16000; WIN = 19200; HOP = 3840
rng = np.random.default_rng(7)

def energy_frames(x):
    n = len(x) // 160
    return np.array([np.sqrt(np.mean(x[i*160:(i+1)*160]**2)) + 1e-9 for i in range(n)])

def trim(x):
    w = 400; n = max(0, 1 + (len(x) - w) // 160)
    if n == 0: return x
    e = np.array([np.sqrt(np.mean((x[i*160:i*160+w] / 32768)**2)) + 1e-9 for i in range(n)])
    thr = max(e.max() * 10**(-1.5), np.percentile(e, 10) * 3)
    idx = np.where(e > thr)[0]
    if len(idx) == 0: return x
    a = max(idx[0] - 5, 0); b = min(idx[-1] + 5, n - 1)
    return x[a*160:b*160 + w]

def pad(x, total=WIN):
    if len(x) >= total: return x
    l = (total - len(x)) // 2
    return np.concatenate([rng.normal(0, 30, l), x, rng.normal(0, 30, total - len(x) - l)])

def windows(x):
    s = EM.melspec(np.clip(x, -32768, 32767).astype(np.int16)); out = []; pos = []
    for i in range(0, s.shape[0] - 76 + 1, 8):
        w = s[i:i+76][None, :, :, None].astype(np.float32)
        EM.emb.set_tensor(EM.ei, w); EM.emb.invoke()
        out.append(np.squeeze(EM.emb.get_tensor(EM.eo))); pos.append(i)
    return np.array(out), pos

def speech_mask(x, starts):
    en = energy_frames(x); thr = max(en.max() * 0.1, 1); keep = []
    for i in starts:
        seg = en[i+15:i+61] if i + 61 <= len(en) else en[i:]
        keep.append(len(seg) > 0 and (seg > thr).mean() > 0.35)
    return np.array(keep)

def norm(a): return a / np.linalg.norm(a, axis=-1, keepdims=True)

def background():
    words = ["water","table","morning","window","seven","music","yesterday","coffee","driving","perhaps",
             "bonjour","merci","shukran","inshallah","telephone","garden"]
    voices = ["en-us","en-gb+f2","fr","ar","en-us+m7"]
    B = []
    for i, w in enumerate(words):
        x = pad(say(w, voices[i % 5], 50, 160)); e, p = windows(x); k = speech_mask(x, p)
        B.append(e[k] if k.any() else e)
    return np.concatenate(B).mean(0)

def main():
    if "--background" in sys.argv:
        background().astype("<f4").tofile("background.f32"); print("wrote background.f32"); return
    mu = np.fromfile("../../app/src/main/assets/background.f32", dtype="<f4")
    centre = lambda v: norm(v - mu)
    def enrol_vec(x):
        x = pad(trim(x))[:WIN]; e, p = windows(x); k = speech_mask(x, p)
        return centre(e[k].mean(0) if k.any() else e.mean(0))
    takes = [say("Ali", "en-us", p, s) for p, s in [(45,150),(50,170),(55,140),(40,180),(60,160)]]
    C = norm(np.mean([enrol_vec(t) for t in takes], 0))
    def stream(x):
        x = np.concatenate([rng.normal(0, 30, WIN), x, rng.normal(0, 30, WIN)]); best = -1
        for a in range(0, len(x) - WIN + 1, HOP):
            seg = x[a:a+WIN]
            if np.sqrt(np.mean(seg**2)) < 200: continue
            e, p = windows(seg); k = speech_mask(seg, p)
            if k.any(): best = max(best, float((centre(e[k]) @ C).max()))
        return best
    pos = {"other voice": say("Ali","en-gb+f2",70,160), "french": say("Ali","fr",50,160), "arabic": say("Ali","ar",50,160),
           "slow": say("Ali","en-us+m7",35,110), "in a sentence": say("hey Ali come here","en-us",50,160),
           "female": say("Ali","fr+f3",75,170), "fast": say("Ali","en-us+m1",50,220)}
    neg = {"hello": say("hello","en-us",50,160), "alley": say("alley","en-us",50,160), "a lot": say("a lot","en-us",50,160),
           "Adil": say("Adil","fr",50,160), "yallah": say("yallah","ar",50,160), "Hala": say("Hala","ar",50,160),
           "daddy": say("daddy","en-us",50,160), "lily": say("lily","en-gb+f2",60,160)}
    for k, v in pos.items(): print(f"should match   {k:14s} {stream(v):.2f}")
    for k, v in neg.items(): print(f"should not     {k:14s} {stream(v):.2f}")

if __name__ == "__main__":
    main()
