# How detection works

A sound is taught with 5–8 takes. Listening compares the last 1.2 s of audio with those
takes every 240 ms.

## Pipeline

1. **Speech embedding.** Two TFLite models (see MODELS.md): 1.2 s of 16 kHz audio → 32-band
   mel spectrogram → one 96-number embedding per 760 ms, every 80 ms.
2. **Speech mask.** Embeddings that mostly cover silence are dropped (`Features.speechMask`).
3. **Centring.** Every embedding has the mean embedding of ordinary speech
   (`assets/background.f32`) subtracted, then is normalised. Without this, all speech looks
   alike: raw cosine similarities sit between 0.83 and 0.95 for everything.
4. **Enrolment.** Each take is trimmed to the sound, padded to 1.2 s, embedded, masked and
   centred. The sound is the normalised mean of its takes.
5. **Score.** The best cosine between any speech embedding in the window and the sound.
6. **Fire** when the score reaches the threshold: 0.75 at the cautious end, 0.50 at the eager
   end, 0.625 in the middle. At most once every 3 s.

A window is only evaluated when its loudest 10 ms is at least 4x (12 dB) above its quiet
frames. This is relative on purpose. The first version skipped anything under RMS 200, and in
replay that threw away every far or whispered call before comparing it:

| Call | Fixed RMS 200 | Relative gate |
|---|---|---|
| normal | 0.85 | 0.85 |
| far, -24 dB | not evaluated | 0.80 |
| very far, -34 dB | not evaluated | 0.76 |
| whisper | 0.77 | 0.77 |
| quiet whisper | not evaluated | 0.73 |

Other words stayed at or below 0.58 with the relative gate, and plain room noise is never
evaluated. A far call buried under loud street noise still fails (0.31): at that point it
is not in the audio.

## Why not MFCC and DTW

Tried first, in replay. Across different voices it did not separate the name from other
words at any setting: every negative scored inside the range of the positives.

## Replay results

`tools/replay/replay.py`, takes from one synthetic voice, streaming as the app does:

| Should match | Score | Should not | Score |
|---|---|---|---|
| other voice | 0.86 | hello | 0.29 |
| Arabic voice | 0.89 | a lot | 0.17 |
| slow | 0.83 | Adil | 0.41 |
| fast | 0.84 | yallah | 0.29 |
| female | 0.71 | Hala | 0.40 |
| in a sentence | 0.70 | daddy | 0.47 |
| French voice | 0.62 | lily | 0.58 |
| | | **alley** | **0.77** |

Two minutes of continuous speech in English, French and Darija without the name: no false
alerts at any threshold from 0.50 up. "Alley" is pronounced almost exactly like "Ali" and
will fire.

These are synthetic voices. The real numbers come from the Test step in setup and the score
shown on the main screen while listening.

## Matching modes

Settings → Matching. **Average** compares with one blend of all takes. **Each take** compares
with every take and keeps the closest. **Both** uses whichever is higher. Each detection shows
which one matched ("avg" or "take").

Replay, same calls and words, lowest score among calls that should match against highest
among words that should not ("alley" left out, it is effectively the same word):

| Takes | Average | Each take |
|---|---|---|
| 5 similar takes | 0.61 vs 0.58, gap +0.03 | 0.65 vs 0.63, gap +0.03 |
| 8 varied takes (whisper, far, other voices) | 0.70 vs 0.51, gap **+0.18** | 0.72 vs 0.63, gap +0.09 |

Each take raises every score, other words included, so it catches more and mistakes more.
Average with varied takes separated best. Both behaves like Each take, since the closest take
is almost always at least as close as the blend. Average is the default.
