# SonderSound — Specification

Package `com.verisonder.sondersound`. Android. GPL-3.0-only.

## 1. Detection

- The user enrols a custom sound by recording samples: a name, a doorbell, anything. No per-user
  model training; enrolment averages speech embeddings and the live sound is compared against
  them. Details in DETECTION.md, models and their licence in MODELS.md.
- Voice-activity gate runs first; the matcher only runs on speech or sound energy.
- Listening uses the **phone microphone**, not the earbuds (earbud mics force call mode and ruin music).
- On a match: duck or pause music, play a chime in the earphones.

### Setup (first launch, and "Add sound" in My sounds)

1. Name the sound.
2. Record it 5–8 times. Different distances and voice levels.
3. Optional: hand the phone to someone else to record it too.
4. Test: say it once more; the app shows whether it matched.
5. Optional calibration: listen to the room for a minute and set the threshold above what it hears.
- Language-independent. Works for any name in any language.
- Per-sound sensitivity.

## 2. The buffer

- Rolling buffer of the last **15 or 30 seconds**, user's choice. Only that much is held.
- Held **in memory only**. Never written to disk unless the user saves a clip (§5).
- 30 → 15 drops the older half at once. 15 → 30 fills from the moment of the switch; the screen shows
  how many seconds are actually held.
- On trigger the clip is frozen; the rolling buffer keeps recording separately.
- Triggers: main screen, quick-settings tile, notification action. Earbud long-press is not offered:
  media buttons go to whichever app last played music, so it cannot work reliably.

## 3. Play back

- Plays the frozen clip in the earphones, from memory. Offline. Any language.

## 4. Transcribe (optional)

- Only shown once the user has saved a Gemini API key. No backend: the app calls Gemini directly.
- Key checked with one call when pasted. Stored with Android Keystore encryption, excluded from backups.
- "Get a key" link to Google AI Studio.
- Clip sent inline as WAV (30 s is under 1 MB, far below the 20 MB limit), in the background.
  Model `gemini-3.8-flash` through the Interactions endpoint, with generateContent as fallback.
- Prompt: verbatim, no translation or summary; keep code-switching as spoken; `[unclear]` for
  unclear parts. Setting for Darija script: Arabic script or Latin (Arabizi).
- Offline: keep in memory, retry on reconnect, discard after a limit. Never to disk.
- Errors, one line: key rejected · limit reached · offline.
- One-time disclosure before first use: clips you transcribe are sent to Google using your key.

## 5. Saved clips (optional)

- Setting **Save detection clips**, off by default. "Keep clips after the app closes."
- Off: clips are in memory, lost on app close or restart.
- On: saved encrypted in app-private storage, auto-deleted after 1 h · 24 h · 7 days · never.
- Long-press a clip: Save (exempt from auto-delete) or Delete. "Delete all clips" button.

## 6. Layout

Modelled on Google Sound Notifications.

Main screen: snooze (top left), settings gear (top right), title, big **On/Off** toggle, buffer card
(▶ Last N s · Transcribe), **My sounds** row, list of detections with time and ▶.

Behind the gear: buffer length, Gemini key, Darija script, chime, sensitivity, music ducking, earbud
shortcut, save clips, auto-delete, delete all.

## 7. Rules carried from SonderAssist

- On-screen text: one short line per option, two at most.
- Everything optional is off by default.
- Anything that can fail silently shows what it did on screen.
- Detector changes are replayed against recordings before they ship.

## 8. Play Store

- Microphone foreground-service declaration; prominent disclosure before the mic starts.
- Data Safety: audio stays on device; transcription clips go to Google only on request.

## 9. First step

Python prototype of the detector before Android code. The test recordings are for development
only; users never do this. They do the setup in §1.

- The name recorded the way setup asks (5–8 times, one voice), to build the enrolment.
- The name from other people at 1–5 m, to check it still matches voices that were not enrolled.
- Ordinary noise with no name, and near-misses, to count false alerts.
