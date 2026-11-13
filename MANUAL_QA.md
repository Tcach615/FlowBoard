# FlowBoard — manual evaluation

Automated checks cover the JVM and framework layers (`./gradlew testDebugUnitTest`, 64 tests, all
passing). They cannot cover a real keyboard on a real device: IME activation, insertion into other
apps, haptics, GPU rendering and multi-touch timing all need hardware. This file is the checklist
for that part.

Screenshots in `docs/headless/` were rendered by the test suite from the real view hierarchy. They
show layout and colour, **not** live behaviour, and they were produced at xxhdpi (411 × 891 dp).

## Install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then: Settings → System → Languages & input → On-screen keyboard → Manage keyboards → enable
**FlowBoard**; open a text field and switch to it with the keyboard-switcher button.

## What the automated run already proved

Run `./gradlew testDebugUnitTest` and read `app/build/reports/tests/testDebugUnitTest/index.html`.

| Area | Covered headlessly |
| --- | --- |
| Direction detection, chord model, 16 slots | `DirectionTest`, `ChordTest` |
| Gesture state machine (sync window, third finger, out-of-zone, cancel, sticky lock) | `GestureEngineTest` |
| Candidate → chord stability | `ChordBoardTest` |
| Composing, undo, alternatives, backspace, space, finish, password reset | `InputControllerTest` |
| Prediction scoring, bigram back-off, personalization, password handling | `PredictionEngineTest`, `VocabularyParserTest` |
| IME registration in the merged manifest, service starts, view is created | `ImeRegistrationTest` |
| **End-to-end typing**: synthetic two-thumb gestures on the real `KeyboardView` → real engines → real `InputMethodService` → real `EditText` `InputConnection` | `HeadlessTypingTest` |
| Chord inserts mapped candidate; all 16 chords distinct; space adds one trailing space; undo restores; backspace at editor; finish without space; cancelled gesture inserts nothing; finger leaving pad rejects; password field shows PRIVATE and records no stats; normal field counts the chord | `HeadlessTypingTest` |
| Settings screen draws, switch persists, statistics row | `HeadlessScreensTest` |
| Onboarding: welcome → step 1 → all four directions → step 2 → all sixteen chords → step 3 | `HeadlessScreensTest` |

Screens rendered by that run: `docs/headless/keyboard.png`, `settings.png`,
`onboarding-1-welcome.png`, `onboarding-2-chords.png`, `onboarding-3-enable.png`.

**Not** verified anywhere yet: everything in the checklist below, especially IME activation in the
system picker, insertion into third-party apps, haptics, real multi-touch timing, and smoothness.

---

## A. Install and activate

- [ ] APK installs on a device/emulator with API 24–35 without warnings.
- [ ] After enabling it, FlowBoard appears in the system keyboard list as "FlowBoard".
- [ ] The keyboard can be selected from the nav-bar switcher and from **Choose input method** in the app.
- [ ] Selecting a text field raises the keyboard with the pads fully visible (nothing clipped).

## B. Visual layout

Compare with `docs/headless/keyboard.png`.

- [ ] Toolbar shows five buttons in order: `⌫  ␣  ✓  ↺  ⇄`.
- [ ] Composing preview bar sits under the toolbar; empty state reads "Two thumbs, four directions".
- [ ] Two circular pads with `LEFT` / `RIGHT` centres, four labelled corners each.
- [ ] Candidate labels are legible at 1× and not clipped on small screens (try a 5" phone and a large one).
- [ ] Keyboard height feels right in portrait and landscape (portrait ≈ 330 dp, landscape ≈ 210 dp); no overlap with the app content or the navigation bar.
- [ ] Light theme (Settings → Display) actually repaints background, toolbar, preview and labels; nothing stays dark or becomes unreadable.
- [ ] Turning **Candidate labels off** hides the pad labels but keeps the pads usable.

## C. Typing with two thumbs

- [ ] Holding one thumb on each pad centre and dragging both to corners arms a chord; lifting either thumb commits exactly one candidate.
- [ ] Committing inserts the candidate shown in that corner (left pad labels = right thumb direction and vice versa).
- [ ] `⌫` deletes a character of the composing word; when nothing is composing it deletes in the host app.
- [ ] `␣` commits the composing word plus exactly one space.
- [ ] `✓` commits the word with **no** space added.
- [ ] `↺` undoes the last insertion and restores the composing word.
- [ ] `⇄` rolls back the last insertion while keeping the same chord mapping.
- [ ] Text lands correctly in: the FlowBoard settings screen, Messages/SMS, WhatsApp or Slack, Chrome address bar, a search field, Gmail body.
- [ ] Straight horizontal or vertical drags are not guessed into a direction.
- [ ] A third finger does not corrupt an in-progress chord.
- [ ] A thumb that lifts and lands again does not insert a second word (sticky lock).

## D. Rejection feedback

- [ ] Dragging only one thumb and lifting it shows "Move both thumbs to a corner" (or "Both thumbs must land together") in the preview bar and inserts nothing.
- [ ] Sliding a thumb far outside its pad shows "Finger left its pad" and inserts nothing.
- [ ] Switching apps or a system interruption mid-gesture shows "Gesture cancelled" and inserts nothing.
- [ ] Status text disappears after ~1.5–2.5 s.

## E. Haptics and feedback

- [ ] With **Haptic feedback** on, committing a chord vibrates lightly and toolbar taps do too; with it off, no vibration.
- [ ] Toolbar buttons visibly depress while pressed and do not fire if you drag off before lifting.

## F. Settings (persistence)

- [ ] Synchronisation window slider changes the value shown and survives leaving and reopening the screen, and a keyboard restart.
- [ ] Dead zone slider does the same; at 45 % deliberate corner drags start to be ignored, at 15 % small drags already count.
- [ ] Toggles (labels, light theme, haptics, personalization) persist across app restart and are picked up by the keyboard without reinstalling.
- [ ] Statistics increase while typing (chords committed, corrections on undo/alternatives) and **Reset statistics** zeroes them.
- [ ] On a password field the counter does **not** increase.
- [ ] Language spinner shows English and its explanatory note; selecting from it does not crash.

## G. Privacy behaviour (visible side)

- [ ] Typing into a password field (e.g. lock screen PIN field, a login form) shows **PRIVATE** in amber in the preview bar.
- [ ] Text typed before entering the password field does not influence password-field suggestions.
- [ ] No network permission is requested at install (check App info → Permissions: only vibration, if any).

## H. Onboarding and app entry

Compare with `docs/headless/onboarding-1-welcome.png`, `onboarding-2-chords.png`, `onboarding-3-enable.png`.

- [ ] Launcher icon opens onboarding at the welcome step.
- [ ] **Start practice** shows Step 1; covering all four corners with both thumbs updates "Left x/4 · Right x/4" and enables **Continue**.
- [ ] Step 2 counts up "n / 16" and only enables **Continue** at 16/16; skipped chords are not counted.
- [ ] **Skip for now** works on both practice steps.
- [ ] Step 3 buttons open the system keyboard settings and the input-method picker.
- [ ] **Finish** opens Settings; onboarding is not re-shown automatically on next launch.
- [ ] **Replay the tutorial** in Settings returns to the welcome step.

## I. Edge cases worth a deliberate attempt

- [ ] Rotate while the keyboard is open: layout rebuilds, no crash, no stale gesture.
- [ ] Type with prediction producing a "<space>" candidate label; committing it inserts a space and no duplicate space follows.
- [ ] Undo immediately after switching fields: nothing is deleted from the new field.
- [ ] Long press on text fields/other IME features: FlowBoard has no long-press menu — confirm that is acceptable.
- [ ] Back-to-back chords at speed do not drop or duplicate words.
- [ ] Battery/thermal: type for two minutes and confirm no runaway CPU (the tick runnable should only run around gestures).

## J. Accessibility / comfort

- [ ] Pads are reachable with two thumbs on a phone held normally, without stretching.
- [ ] Landscape mode keeps both pads reachable.
- [ ] Text is readable at default font scale and at 1.3×; check nothing overlaps at large font sizes.
- [ ] Left-handed use: pads are fixed left/right, confirm that is acceptable for the intended interaction.

---

## Reporting

For anything that fails, note the device model, Android version, app, and what you did just before
it failed. The gesture state machine and rejection reasons are the most likely places for a real
device to differ from the headless run.
