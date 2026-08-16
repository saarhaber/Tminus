# Architecture

How tMinus is put together, and why the non-obvious parts are the way they are. If you only read one
section, make it [Widgets](#widgets) — that is where most of the project's historical bugs lived.

## Modules

| Module | What it holds |
| --- | --- |
| `:app` | Everything user-facing: UI, widgets, workers, networking, storage. |
| `:network-json` | Small JSON:API helpers, kept separate so they can be unit-tested on the JVM without the Android toolchain. |
| `:baselineprofile` | Macrobenchmark module that records the startup baseline profile. Not part of a normal build. |

## The object graph

[`AppGraph`](../app/src/main/java/com/saarlabs/tminus/AppGraph.kt) is a hand-rolled container built
**lazily on first use** from any `Context`:

```kotlin
val graph = AppGraph.from(context)
graph.globalData      // stop/route catalogue
graph.schedules       // cached timetable lookups
graph.client          // MBTA V3 client for the current API key
graph.settings        // typed settings access
```

It is deliberately not initialised in `Application.onCreate`. Widgets, workers and broadcast
receivers all run in processes that may start *without* going through an activity, and an earlier
design that assigned `lateinit` statics in `onCreate` forced every caller to poll
`awaitClientReady()` for up to 15 seconds — and could still miss. Whoever asks for the graph first
builds it.

`onApiKeyChanged()` rebuilds the client and drops data fetched with the old key.

## Data

```
MbtaV3Client ──► GlobalDataRepository ──► stop & route catalogue (~1.7 MB, memory + disk)
             └─► ScheduleRepository  ──► timetables per stop set (memory + disk, 1 h TTL)
                                          │
                                          └─► StopSearchIndex (built off the main thread)
```

**`GlobalDataRepository`** owns the stop catalogue. It is about 1.7 MB of JSON, so loading is
suspending and runs on `Dispatchers.IO`; `Application.onCreate` only kicks off a background warm.
Parsing it on the main thread used to be on the critical path of every cold start, including starts
triggered by a widget update.

**`ScheduleRepository`** is what makes the per-minute widget countdown affordable. A schedule request
asks for every departure between now and the end of the service day, and MBTA timetables do not
change within a day — so one fetch answers every later tick, and the widget just re-filters against
the current time. Before this cache, each 60-second tick issued one `/schedules` request *per placed
widget*, around the clock.

**`StopSearchIndex`** pre-lowercases and pre-tokenises stop names once, so each keystroke is a scan
of prepared strings cheap enough to run on `Dispatchers.Default`. It also ranks matches (exact →
prefix → word start → substring, then rail before bus), which is why searching "Park" returns Park
Street rather than three bus stops on Hyde Park Ave.

### Service days

MBTA service days run past midnight and the V3 API expresses that with hours ≥ 24 — a train leaving
at 00:40 on Saturday belongs to Friday's service day at "24:40". Anything that builds a time window
must use `EasternTimeInstant.serviceDate` and service-day hours, not the calendar date. Getting this
wrong makes late-night departures silently vanish, which is exactly when riders need them.

`LastTrainProfile.windowEndMinutes` follows the same convention: values ≥ 1440 mean "after midnight".

## Widgets

Four Glance widgets:

| Widget | Shows |
| --- | --- |
| `MBTATripWidget` | Next trip between two stops, with commuter-rail specific layouts. |
| `MBTAStationBoardWidget` | Departure board for one stop, optionally filtered to a line and direction. |
| `MBTAFavoritesWidget` | Next departure from each starred stop. |
| `MBTAAlertsWidget` | Active service alerts for chosen line groups. |

### The rule that matters

**All configuration and data loading happens *inside* `provideContent`.**

`GlanceAppWidget.update()` recomposes content that is already registered. It does **not** re-run the
part of `provideGlance` that ran before `provideContent`. A widget that reads its configuration up
front therefore keeps that first reading for the entire life of the Glance session — which is how
widgets ended up stuck on "Tap to set up" after being configured, sometimes for minutes, until the
session happened to end.

Every widget's state lives in
[`WidgetModels.kt`](../app/src/main/java/com/saarlabs/tminus/android/widget/WidgetModels.kt) and is
produced by `rememberStationBoardState` / `rememberTripState` / `rememberFavoritesState` /
`rememberAlertsState`, which collect:

* the saved configuration, as a `Flow` from `WidgetConfigStore` — so a save from the configuration
  activity reaches a *live* session with no update call at all; and
* a refresh tick, bumped by every refresh trigger — so a refresh always yields a new composition and
  therefore a recomputed countdown, even when nothing else changed.

Countdowns are derived at render time from `renderedAt`, never baked into the loaded data. That is
what lets a tick update "3 min" to "2 min" without touching the network.

### Refresh cadence

| Trigger | Cadence | Notes |
| --- | --- | --- |
| `LiveUpdateManager` exact alarm | 60 s while the screen is on | Drops to 15 min and becomes inexact while the screen is off. |
| `WidgetUpdateWorker` periodic | 15 min | WorkManager's floor; the safety net. |
| Configuration save | immediate | Via the config flow. |
| Refresh pill | immediate | Invalidates the schedule cache first. |
| `BootCompletedReceiver` | on boot / update | Alarms do not survive a reboot. |

### Configuration storage

`WidgetConfigStore` keeps per-widget config as JSON in `SharedPreferences` — trip, station board,
favorites and alerts — and migrates the old newline-joined format in place on first read. That
format had no schema version and broke on any label containing a newline.

## Notifications

One periodic worker, `TminusNotificationWorker`, evaluates commutes, last/first train alerts and
station accessibility watches every 15 minutes. When a notification is due sooner than the next tick,
`PreciseNotificationScheduler` schedules a one-off run at the exact moment.

Three channels (`TminusNotificationChannels`), because the alert types are unrelated and Android only
lets users tune them per channel.

Delivery is deduplicated by a key written into `SharedPreferences`; those keys carry a timestamp and
are pruned after three days. `POST_NOTIFICATIONS` is checked inline before `notify()` so a dropped
notification is not recorded as delivered.

Accessibility alerts use the API's `filter[stop]`. They previously fetched every alert on a route and
matched the station by substring, which both missed alerts and fired on unrelated ones.

## UI

Jetpack Compose with Material 3. Material You dynamic colour on Android 12+, with an opt-out in
Settings; otherwise a hand-tuned navy palette.

* `TminusEdgeToEdge` derives system bar icon colour from the **app's** theme rather than system night
  mode, so forcing dark mode on a light device does not hide the status bar.
* `StopPicker` is the single stop-selection component. The commute editor, both alert editors and
  both widget configuration activities all use it; they previously each carried a copy, which is how
  the route-scoped pickers ended up offering stops the route does not serve.
* Screens that need the catalogue use `rememberGlobalData()`.

## Performance

Verified on a Pixel 9a emulator (API 36) with `dumpsys gfxinfo`:

| Measurement | Before | After |
| --- | --- | --- |
| Stop list scroll — janky frames | 60.7% | 2.8% |
| Stop list scroll — p50 / p90 | 61 / 109 ms | 17 / 19 ms |
| Search typing — p90 / p99 | 150 / 250 ms | 32 / 32 ms |

Rules of thumb for changes here:

* Never parse the stop catalogue on the main thread.
* Never filter or sort the full stop list inside a `remember` keyed on a text field.
* Never make a network call from a widget tick — go through `ScheduleRepository`.

### Baseline profile

The `:baselineprofile` module is wired up but **the profile itself has not been generated**: doing so
needs a rootable system image, and only Play Store images (which cannot be rooted) were available on
the machine where this was set up. To generate it:

```bash
./gradlew :baselineprofile:generateBaselineProfile
```

This uses the `pixel6Api34` managed device declared in `baselineprofile/build.gradle.kts` (an AOSP
image). The generated profile lands in `app/src/main/baselineProfiles/` and should be committed.

## Testing

`./gradlew test` runs the JVM unit tests. They cover the parts that are pure and easy to get wrong:
search ranking, schedule pairing, station-board assembly, stop-family resolution and service-day
arithmetic.

Logic that needs testing is extracted to top-level `internal` functions rather than tested through a
copy — see `buildDepartures` and `earliestConnectingPair`.

`./gradlew :app:lintDebug` must pass; CI runs it with `abortOnError`.
