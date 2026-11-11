# OnePlus 12R test plan (OxygenOS / Android 16)

These tests check StandBy Clock **1.1.0** on a real OnePlus 12R. They could not be run in the
build environment, so every result below must be recorded on the device.

## Before you start

1. Install `StandByClock-1.1.0-release.apk`. 1.1.0 is signed with the same key as 1.0.0, so it
   installs as an update.
2. Note the phone's details: **Settings › About device**. Record the OxygenOS version and build number.
3. In StandBy Clock open **Diagnostics** (bottom of the start screen, or Settings › Diagnostics).
   Tap **Copy report** and paste it at the top of your results. It records the Android version,
   model, screen-saver availability, lock-screen state and permissions.
4. Recommended OxygenOS settings for these tests:
   * **App info › StandBy Clock › Battery usage → Unrestricted.** OxygenOS otherwise freezes
     background apps. This only matters for test 12.
   * Keep a secure lock (PIN/fingerprint) so you see real lock-screen behaviour.
   * Note your **Settings › Display › Screen timeout** (Diagnostics shows it too).
5. Optional, but best for failures: connect a computer with `adb`.

### How to capture logs

| What | How |
|---|---|
| In-app event log | Diagnostics › **Recent events** (last 80 events), then **Copy report** |
| App log (adb) | `adb logcat -c` before the test, then `adb logcat -s NiteStand:V` while testing |
| Window / lock-screen state | `adb shell dumpsys window \| grep -iE "mCurrentFocus\|keyguard\|showWhenLocked"` |
| Activity stack | `adb shell dumpsys activity activities \| grep -iE "standbyclock\|ResumedActivity"` |
| Screen / wakefulness | `adb shell dumpsys power \| grep -iE "mWakefulness\|mIsPowered\|mStayOn\|FULL_WAKE\|KEEP_SCREEN"` |
| Screen saver | `adb shell dumpsys dreams` and `adb shell settings get secure screensaver_enabled` / `screensaver_components` / `screensaver_activate_on_sleep` |
| Notifications | `adb shell dumpsys notification --noredact \| grep -A20 standbyclock` |
| Full-screen intent permission | `adb shell appops get com.nitestand.standbyclock USE_FULL_SCREEN_INTENT` |
| Background restrictions | `adb shell dumpsys deviceidle whitelist \| grep standby` and `adb shell cmd appops get com.nitestand.standbyclock RUN_ANY_IN_BACKGROUND` |
| Broken behaviour you can see | Screenshot or short screen recording |

For each test, fill in: **Pass/Fail**, **Actual result**, and for a failure the logs listed.

---

## 1. Manual StandBy

**Steps.** Unplug the charger. Open StandBy Clock and tap **Preview**. Then return and tap
**Start StandBy** with Activation mode = *Manual*.

**Expected**
* Preview: the full-screen clock appears immediately in landscape, whatever the orientation or
  charger. The status and navigation bars are hidden. *"Double-tap to exit · Swipe to change clock"* shows for about 3 s.
* Manual mode, Start StandBy: the waiting screen says "Manual mode". Tapping **Start StandBy** shows the clock.
* Double-tap, or the back gesture, closes it.

**Record.** Did the clock appear? How long did it take? Did the bars hide? Did exiting work?

**If it fails, capture.** The Recent events list, `adb logcat -s NiteStand:V`, and the activity stack.

## 2. Charging StandBy

**Steps.** Set Activation mode = *Charging + landscape*. Unplug the phone. Tap **Start StandBy**,
hold the phone horizontally, then plug in the charger.

**Expected**
* Unplugged: the waiting screen shows "Connect to power" with *Connected to power* unchecked.
* Within about 1 s of plugging in, both checks turn on and the clock appears.
* The screen stays on while charging. Diagnostics › Charging shows `CHARGING` (or `FULL`).

**Record.** Time from plugging in to the clock appearing. The Charging status in Diagnostics.

**If it fails, capture.** The Copy report (Live state › Charging), the events,
`adb shell dumpsys battery`, and `dumpsys power | grep mIsPowered`.

## 3. Screen saver mode

**Steps.**
1. Diagnostics › **Open screen saver settings**. Note what opens. If OxygenOS has no screen
   saver page, the Display or Settings home opens instead, and Diagnostics shows
   *Settings page present: No*.
2. If a screen saver page exists, choose **StandBy Clock** and set *When to start: While charging*.
3. Set Activation mode = *Screen saver*. Plug in, place the phone horizontally on a stand, and
   wait for the screen timeout.

**Expected**
* With a screen saver page: after the timeout, StandBy appears above the lock screen.
  * In portrait it shows a dim clock and turns the screen off after 30 s.
  * Double-tap or back returns to the locked lock screen.
* Without a screen saver page, OxygenOS removed the feature. Record that; this mode cannot work
  on this build. Use test 5 or 12 instead.
* Unplugged, the screen saver should **not** start. This is Android's rule: screen savers only
  start while charging or docked.

**Record.** Whether the page exists. The "When to start" options offered. The Screen saver
section of Diagnostics. Whether StandBy started.

**If it fails, capture.** `adb shell dumpsys dreams`, the `settings get secure screensaver_*`
values, and Recent events. When the system starts the screen saver, "Screen saver started by
the system" appears there.

## 4. Landscape activation (no charger, app open)

**Steps.** Unplug. Set Activation mode = *Landscape only*. Tap **Start StandBy** while holding
the phone upright, then turn it horizontal.

**Expected**
* Upright: "Turn your phone sideways".
* Horizontal (either direction): the clock appears within about 1 s and the screen stays on.
* Lying flat on a table does **not** count as landscape. A stand or about 30° tilt is fine.

**Record.** Diagnostics › Physical orientation: the roll and tilt values while on your stand.
Did it activate? How long did it take?

**If it fails, capture.** The roll and tilt values from Diagnostics. Try Settings › Orientation
sensitivity › *Relaxed* and record whether that fixes it.

## 5. Quick Settings activation

**Steps.** Pull down Quick Settings › edit (pencil) › add the **NiteStand** tile. Unplug.
1. Phone unlocked: tap the tile.
2. Lock the phone, wake it to the lock screen, open Quick Settings, and tap the tile.
3. With StandBy showing, open Quick Settings (swipe from the top edge) and tap the tile again.

**Expected**
1. StandBy appears immediately, without a charger. The tile shows **On**.
2. On the lock screen, StandBy appears **over** the lock screen without unlocking. OxygenOS may
   first ask you to unlock, because some builds require unlocking for Quick Settings tiles; record which happens.
3. Tapping the active tile closes StandBy, and the tile returns to off.

**Record.** Behaviour for each step. Whether OxygenOS asked to unlock in step 2.

**If it fails, capture.** Recent events (`Tile: starting StandBy (locked=…, secure=…)`) and the activity stack.

## 6. Lock-screen behaviour

**Steps.** Start StandBy (tile or Preview). Press the power button to turn the screen off, then
press it again.

**Expected**
* StandBy is shown **above** the lock screen. The phone is still locked: notifications and apps stay protected.
* Double-tap shows the normal lock screen, which asks for your PIN or fingerprint.
* With Settings › *Show on lock screen* off, the lock screen appears instead of StandBy.

**Record.** What appeared after power-on. Whether exiting led to the secure lock screen.

**If it fails, capture.** `dumpsys window | grep -iE "keyguard|showWhenLocked"` and Diagnostics › Keyguard locked now.

## 7. Screen timeout

**Steps.**
1. While charging, start StandBy and wait longer than your system screen timeout.
2. Unplug. Set *On battery, keep screen on* = **5 minutes**. Start StandBy and wait 6 minutes.
3. Leave the waiting screen (conditions not met) open for longer than the system timeout.

**Expected**
1. Charging: the screen stays on indefinitely.
2. On battery: the screen stays on for 5 minutes, then turns off after the system timeout.
   Events show "battery timeout (5 min) reached". Waking the phone shows StandBy again with a new 5-minute window.
3. Waiting screen: the screen turns off normally. It is never kept on.

**Record.** The actual time until the screen turned off in each case.

**If it fails, capture.** `dumpsys power | grep -iE "mStayOn|KEEP_SCREEN|mWakefulness"` and Recent events.

## 8. Burn-in protection

**Steps.** Set Settings › Burn-in protection › Movement interval = **30 seconds**. Start
StandBy. Watch a digit's edge against a fixed reference (e.g. the screen corner) for 2 minutes.

**Expected.** About every 30 s the whole layout drifts a few pixels over roughly 4 s. You notice
it only if you look closely. With *Reduce motion* on, it jumps instead of drifting.

**Record.** Whether you saw the movement and whether it was distracting.

**If it fails, capture.** A short screen recording.

## 9. Portrait → landscape transition

**Steps.** Mode *Charging + landscape*, charger connected, Start StandBy upright. Then turn the
phone horizontal in both directions, and turn it 180°.

**Expected.** The clock appears when horizontal. Turning it 180° flips the UI the right way up
within about 1 s. A small wobble does not flicker between states.

**Record.** Whether both directions worked and whether the 180° flip worked.

**If it fails, capture.** The Diagnostics roll angle in each position, plus events.

## 10. Landscape → portrait transition

**Steps.** With StandBy active (mode *Charging + landscape* or *Landscape only*), pick the phone
up into portrait.

**Expected**
* Within about 1 s StandBy returns to the waiting screen. The bars reappear and the screen may time out normally.
* Putting it back horizontal shows the clock again.
* In *Quick Settings* / *Manual* / Preview, StandBy stays on in portrait (the UI stays landscape) until you exit.

**Record.** Observed behaviour and the delay.

**If it fails, capture.** Events (`StandBy activity: WAITING_FOR_LANDSCAPE …`) and the Diagnostics roll angle.

## 11. Unplugging while StandBy is active

**Steps.** Mode *Charging + landscape*, StandBy active on the charger. Unplug.

**Expected.** Within about 1 s StandBy goes to "Connect to power" and stops keeping the screen
on. Plugging back in restores the clock. In *Landscape only* mode, unplugging changes nothing
(the battery timeout from test 7 applies).

**Record.** The delay, and whether the waiting screen appeared.

**If it fails, capture.** Events, `adb shell dumpsys battery`, and the Charging line in Diagnostics.

---

## 12. Experimental: lock-screen auto start without a charger (the main goal)

**Steps.**
1. Settings › Activation mode = **Landscape only**. Turn on **Lock-screen auto start**.
   * Allow notifications when asked.
   * If asked, allow **Full-screen notifications** (Android 14+).
   * Set **Battery usage → Unrestricted** for StandBy Clock.
2. Check Diagnostics: *Lock-screen watcher running: Yes*, *Notifications allowed: Yes*,
   *Full-screen intents allowed: Yes*.
3. Unplug. Lock the phone (screen off). Put it **horizontally** on the stand.
4. Wake the screen: press power, or double-tap to wake if enabled.
5. Repeat with the phone upright, then turn it horizontal while the lock screen is still showing.
6. Double-tap to exit StandBy and leave the phone horizontal. Then pick it up and put it back.
7. Reboot the phone and repeat step 3–4 without opening the app.

**Expected**
* 4: within about 1–2 s of waking, StandBy appears over the lock screen. The phone stays locked.
* 5: StandBy appears when the phone is turned horizontal on the lock screen.
* 6: exiting shows the lock screen. StandBy does **not** reopen until the phone is picked up
  (portrait) and put back, or the screen goes off and is woken again.
* 7: after a reboot the watcher restarts by itself. This needs Unrestricted battery usage.
* If full-screen notifications are **not** allowed, a "StandBy" notification appears on the lock
  screen instead. Tapping it should open StandBy; record whether OxygenOS asks to unlock first.
* **Not expected to work:** while the screen is **off**, nothing happens. Android gives apps no
  sensor data and no way to turn the screen on without user action or a charger event, unless a
  wake lock is held, which this app deliberately avoids. You must wake the phone.

**Record.** Each step's result and delay. Whether the persistent notification "Lock-screen
StandBy is ready" stays present. Whether OxygenOS stopped the service after some hours
(Diagnostics › watcher running).

**If it fails, capture**
* Recent events. You should see `Watcher: SCREEN_ON …`, `conditions met … launching StandBy`
  and `launch posted (full-screen intent allowed|NOT allowed)`.
* `adb shell dumpsys notification --noredact | grep -A20 standbyclock`
* `adb shell appops get com.nitestand.standbyclock USE_FULL_SCREEN_INTENT`
* `adb shell dumpsys activity services com.nitestand.standbyclock`
* `adb logcat -s NiteStand:V ActivityTaskManager:I` (look for "Background activity launch blocked" or "Abort background activity starts").

---

## 13. Clock faces (v1.2.0)

**Steps.** Settings › Clock faces. For each of the 15 faces:
1. Tap it. It is marked selected, and the sheet opens.
2. Change the colour.
3. Tap **Preview full screen** with the phone horizontal on the stand.
4. Swipe left or right on StandBy to step through the faces.

Repeat a few faces with each of these:
* 12-hour with AM/PM on and off
* 24-hour
* Show seconds on
* Night mode (Settings › Night mode › Always on, or the sheet's *Preview night mode*)
* Portrait (Preview while holding the phone upright)

**Expected**
* The time fills the screen on a true-black background. No status or navigation bar, no buttons.
  The *Double-tap to exit* hint disappears after about 3 s.
* The digits are vertically centred and nothing touches the camera cutout or the screen edges.
* In 12-hour mode, AM/PM sits small at the top right of the minutes, with seconds under it.
* Night mode fades to dim red over about 2.5 s, and brightness ramps down over about 1.5 s. There
  are no white pixels in night mode.
* After about 30–120 s (Settings › Burn-in protection) the whole clock has drifted a few pixels.
  It drifts slowly and never jumps.
* Exiting StandBy restores normal brightness and the system bars.

**Record.** A photo of each face on the stand, in a dark room, at normal brightness and in night
mode. Note any face that looks too bright, too small, off-centre, clipped by the cutout, or
stutters when seconds are on.

**If it fails, capture.** A screenshot (`adb exec-out screencap -p > face.png`), the face name and
settings, and `adb shell dumpsys gfxinfo com.nitestand.standbyclock` for any stutter.

---

## 14. Ambient experience and performance (v2.0.0)

**Steps (in a dark room, phone horizontal on the stand)**
1. **Enter.** Use the tile, Preview, or lock-screen auto start. Watch the first second.
2. **Minute change.** Wait for a minute change, with the Large face at 24-hour.
3. **Face change.** Swipe up and down on the clock several times, and switch faces in the gallery.
4. **Colour.** Change the colour in Settings › Appearance, then open StandBy.
5. **Info page.** Swipe sideways to the information page and back.
6. **Tap.** Tap once, then double-tap.
7. **Night.** Night mode: Always on. Open StandBy, then turn Night off from the tile/app and back.
8. **Burn-in.** Leave it for 5 minutes and watch a digit against a fixed edge.

**Expected**
1. No sliding Android transition: the screen fades from black and the clock settles in. There is
   no white flash and no status or navigation bar.
2. Only the changed digit(s) move: old up and out, new up and in, in under a second. There is no
   flicker, and the other digits don't move.
3. The clock follows your finger slightly and springs back. The new face arrives from the swipe
   direction.
4. The colours blend smoothly; nothing snaps.
5. The pages recede and fade as they move. There are no cards or boxes.
6. A tap shows faint page dots plus "Double-tap to exit" for about 3 s. A double tap fades out and
   returns to the lock screen or launcher without a black-frame glitch, and normal brightness
   returns.
7. The screen dims gradually into red over about 2.5 s. Brightness ramps down. Secondary text is
   clearly fainter than the time.
8. You don't notice any movement unless you compare against a fixed reference. It never jumps.

**Frame rate (please measure; it could not be measured in the build environment)**
```
adb shell dumpsys gfxinfo com.nitestand.standbyclock reset
# perform steps 2–5 for ~1 minute
adb shell dumpsys gfxinfo com.nitestand.standbyclock | grep -E "Total frames|Janky|50th|90th|99th"
```
Expected: janky frames < 5 %, and a 90th percentile below the display frame time (16.6 ms at 60 Hz).

**CPU / battery at rest:** `adb shell top -n 1 -o %CPU,ARGS | grep standby` while StandBy has been
idle for a minute should show ~0 % except during the once-per-minute transition.

**If it fails, capture:** a screen recording (`adb shell screenrecord /sdcard/ns.mp4`), the
gfxinfo output, and Diagnostics › Copy report.

---

## 15. Automatic StandBy Launch (v2.1.0) — the critical acceptance test

**Setup (once)**
1. Install `NiteStand-2.1.0-release.apk` over the previous version.
2. Open NiteStand › Settings › Behavior:
   * Activation mode: **Landscape only** (or *Charging + landscape* with a charger)
   * **Lock-screen auto start: on**
3. Under the switch, the readiness block lists only what is still needed. Tap **Fix** on each
   *Required* item:
   * **Notifications** for NiteStand — allow.
   * **Full-screen notifications** — allow. On Android 14+ this is the switch that lets Android open
     StandBy over the lock screen. On OxygenOS it opens *Special app access › Full-screen
     notifications*.
   * *(Recommended)* **Battery usage: Unrestricted**, so OxygenOS does not stop the watcher.

   When everything is set it shows "Ready: Android can open StandBy automatically."
4. Diagnostics › *Automatic launch readiness* should show **Verdict: Ready**.

**Test**
1. Unlock the phone and use it normally for a few seconds.
2. Put the OnePlus 12R horizontally on the stand.
3. Press the power button to lock it.
4. Wait up to ~2 s.

**PASS:** the display comes back on by itself showing a black screen, the clock condenses in,
there is no notification UI, and the phone stays locked (double-tap shows your secure lock screen).

**FAIL:** a notification "StandBy didn't open automatically" appears instead.
→ Open Diagnostics › **Last activation attempt** and copy the report. The trace shows where it
stopped (for example "Full-screen intent posted … full-screen access DENIED", or
"Launch blocked: StandBy did not resume (system/OEM declined the launch)").

### Scenarios — record each separately

| # | Scenario | Expected (documented activation model) |
|---|---|---|
| A | NiteStand app open, phone horizontal → press power | Screen turns back on into StandBy (trace: "phone locked … Activity resumed … renderer active") |
| B | NiteStand closed/swiped away, phone horizontal → press power | Same as A. Requires the watcher service to be alive (Unrestricted battery). |
| C | Phone already locked and dark, left horizontal | Nothing happens while dark (by design — no sensors without a wake). Wake it (power / double-tap to wake): StandBy opens on the lock screen (trace: "woken on lock screen"). |
| D | Start StandBy from the app / tile / Preview | Opens immediately, no notification (trace: "Manual launch…"). |
| E | Fallback | Turn **Full-screen notifications off** and repeat A. Expected: one quiet notification explaining that full-screen notifications are off, with an "Allow automatic start" action. Repeat locking: no further notifications until you pick the phone up or unlock. |
| F | No loop | Let StandBy's battery timeout turn the screen off, or press power while StandBy shows. Expected: the screen stays off (no relaunch). After exiting StandBy with a double tap and pressing power while still horizontal: no relaunch. |

**Capture on failure:** Diagnostics › Copy report (includes the trace),
`adb logcat -s NiteStand:V ActivityTaskManager:I NotificationService:I` while repeating the
test (look for "Background activity launch blocked", "full screen intent" or "Abort background
activity starts"), and `adb shell appops get com.nitestand.standbyclock USE_FULL_SCREEN_INTENT`.
