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

Windows quieter than RMS 200 are not evaluated, which is most of the day.

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
