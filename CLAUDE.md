# Working in this repository

Notes for anyone — human or agent — making changes here. Read
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) first; it explains the parts that are not obvious from
the code.

## Commands

```bash
./gradlew test                 # JVM unit tests (:app and :network-json)
./gradlew :app:lintDebug       # Android Lint — must pass, CI uses abortOnError
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK — run this, see below
```

Run `assembleRelease` before pushing anything that touches resources. Release resource processing is
stricter than debug and is what catches malformed drawables; a JPEG committed with a `.png` extension
sat in the repo undetected because only debug was ever built.

## Conventions

* Kotlin, 4-space indent, trailing commas, explicit `public`/`internal` on declarations.
* All user-visible text goes in `strings.xml`. No string literals in composables.
* Times shown to users go through `formatClock` / `formatDayAndClock` / `formatMinutesFromMidnight`,
  never `toString()` on a date-time.
* Comments explain *why*, especially where the code looks odd. Most of the odd-looking code here is
  working around a platform behaviour that is worth naming.

## Things that will bite you

**Glance widgets.** Load configuration and data *inside* `provideContent`. `update()` recomposes
already-registered content and does not re-run what came before `provideContent`. Reading config up
front means a widget renders one stale state for the life of its session.

**Service days.** MBTA days run past midnight, expressed as hours ≥ 24. Use
`EasternTimeInstant.serviceDate` and service-day hours when building any time window. Calendar dates
make late-night departures disappear.

**Widget ticks.** A tick must not hit the network. Go through `ScheduleRepository`; countdowns are
recomputed locally from cached timetables.

**The stop catalogue.** ~1.7 MB of JSON and ~9 000 stops. Never parse it on the main thread, and
never filter or sort it inside a `remember` keyed on a search field — use `StopSearchIndex` off the
main thread.

**Compose resources.** Read strings with `stringResource` at composable scope and hoist them into
vals before using them in lambdas. `LocalContext.current.getString(...)` inside a lambda bypasses
Compose's configuration handling and lint rejects it.

## Testing

Extract logic worth testing into top-level `internal` functions and test those directly. Do not write
a test that reimplements the production logic and asserts against the copy — it passes forever and
proves nothing.

Prefer cases that pin real behaviour: a trip that serves both stops in the wrong order, a departure
recorded against a child platform, a window that crosses midnight.

## Before opening a PR

1. `./gradlew test :app:lintDebug assembleDebug assembleRelease`
2. Install on a device or emulator and exercise what you changed. Widget behaviour in particular
   cannot be verified from unit tests.
3. Note user-visible changes in `CHANGELOG.md`.
