# NiteStand — premium StandBy redesign: technical plan

## 1. Current architecture (v1.2.0)

**Activation (stable, untouched).** Battery/orientation flows feed `StandByStateMachine`, which drives
`StandByController` → `StandByUiState`. The surfaces that consume it are `StandByActivity` (window
flags, brightness, battery timer) and `StandByDreamService`, plus the lock-screen watcher and the tile.

**Presentation.** `StandByScreen` stacks a pointer layer, a `Crossfade(active)`, `ActiveStandBy`
(optional side panel) and `WidgetHost(ClockWidget)`. `ClockWidget` then calls `ClockFace(when(theme))`,
which renders the face composables. The digital faces draw through `TimeText`, a native Paint on a
Canvas. The analog faces draw on a Canvas.

**Styling.** `StandByStyle` is built from a `ClockPalette` and an animated `nightFraction`.

## 2. Why it still feels like an app

| Area | Weakness |
|---|---|
| Launch / exit | Default `Animation.Activity` window transition. Exit calls `finish()` immediately with no fade, so the screen cuts to the keyguard. |
| Time changes | `TimeText` replaces the whole string every minute, so all four digits redraw at once. |
| Digit metrics | Tabular figures leave a large gap around "1". The colon is a font glyph with font spacing. Font size is fitted to the *current* string, so the clock can change size when the digits change. |
| Face change | `Crossfade` uses the default easing and no direction. A swipe only fires once the finger lifts, so nothing follows the finger. |
| Burn-in | `animateIntOffsetAsState` moves in whole pixels with linear easing. A slow drift becomes visible 1-pixel steps. |
| Hint | A text hint ("Double-tap to exit · Swipe…") appears on every activation. That reads as app UI. |
| Night | Red only. Secondary and tertiary text don't have their own levels, motion speed doesn't change, and the side panel uses Roboto with a battery glyph. |
| Colour change | Instant. |
| Widgets | A side panel built from Roboto labels and a drawn battery icon. It reads as Android UI. |
| Settings | A flat list that exposes implementation details ("Require charging", orientation internals). |

## 3. Rendering architecture

- **`ClockFace` interface.** A registry of independent face objects (`Render(ClockState, Modifier)`)
  replaces the `when`. `ClockState` bundles the time data, the animated style and the motion settings.
- **`TimeText` v2.** One Canvas, no recomposition while it animates.
  - Per-glyph layout with proportional figures and a **custom-drawn colon**: round dots sized to the
    stroke weight and centred on the digit band.
  - **Constant scale**, fitted to the widest possible time, so the clock never changes size.
  - Per-glyph animated transitions, described in section 4.
- **Composition layers inside `StandByScreen`:**
  - an ambient black layer
  - a *stage* that carries the entrance/exit transform and the burn-in translation (sub-pixel,
    applied in `graphicsLayer`, no recomposition)
  - pages: Clock, plus an optional Info page in a `HorizontalPager` that uses a depth transition
  - a tertiary overlay with page dots, shown on tap
- **Information layer.** Widgets render as floating typography using Nunito with an opacity hierarchy.
  There are no cards, and the default clock page carries nothing extra.

## 4. Animation strategy

All animations share named tokens (`Motion`): duration × night factor (1.6), emphasised-decelerate
easing for entrances, standard easing for exits, and instant changes under reduced motion.

| Event | Animation |
|---|---|
| Enter | Window fade from black (custom window animation). The stage then animates alpha 0→1 and scale 0.94→1 over 1100 ms (emphasised decelerate). Secondary layers follow 400 ms later. |
| Exit | Stage alpha →0 and scale →0.97 over 380 ms, then `finish()` with a fade-out window animation. |
| Minute change | Changed glyphs slide 0.12 em up and fade out while new glyphs rise in. Unchanged glyphs glide to their new x. 700 ms. |
| Seconds | 220 ms fade. |
| Analog hands | Shortest-path angle animation (300 ms), never spinning backwards through 12. |
| Face change | Directional. Vertical swipe drags the face (rubber-band); on release the new face enters with fade, 6 % offset and 0.96→1 scale. |
| Colour change | 450 ms colour interpolation. |
| Night | 2.5 s interpolation into a dimmer environment, with a 1.5 s brightness ramp. |
| Burn-in | Every 45 s, a deterministic sub-pixel move of ≤6 dp with an 8 s (night: 13 s) ease-in-out curve. |
| Pages | Depth transition: alpha, scale and parallax translation. |

## 5. State management

The activation state still comes from the controller, unchanged. Presentation state lives in
`StandByScreen`:

- an `entrance` Animatable
- an `exiting` flag (owned by the host so back, the tile, double-tap and the dream share one path)
- the face-change direction
- a drag offset
- an animated style

Values that change every frame are read only in draw/layer lambdas, so they don't recompose.

## 6. Performance strategy

- No continuous animations. Frames are produced only during transitions:
  - minute changes: 0.7 s per minute
  - burn-in moves: 8 s every 45 s
  - user gestures
- Paints are reused, and the glyph layout is cached per string.
- No allocations inside draw loops beyond small arrays.
- The clock recomposes once per minute (or once per second if seconds are on, and then only the
  clock). Sensors, receivers and timers keep their lifecycle-bound behaviour.
- 60 FPS and CPU use can only be *measured on the device*. The test plan includes `dumpsys gfxinfo`.
