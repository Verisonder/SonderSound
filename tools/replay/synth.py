import subprocess, numpy as np, io, wave
from scipy.signal import resample_poly
def say(text, voice="en-us", pitch=50, speed=160):
    out = subprocess.run(["espeak-ng","-v",voice,"-p",str(pitch),"-s",str(speed),"--stdout",text],capture_output=True).stdout
    w = wave.open(io.BytesIO(out)); sr=w.getframerate(); x=np.frombuffer(w.readframes(w.getnframes()),dtype=np.int16).astype(np.float64)
    x = resample_poly(x, 16000, sr)
    return x
