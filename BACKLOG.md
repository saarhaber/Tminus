# tMinus — Engineering Backlog

Findings from a full hands-on pass over the app running on a Pixel 9a emulator (API 36) plus a
read of every source file in `:app` and `:network-json`. Ordered by importance: P0 items are
correctness/privacy/performance defects that affect every user; P4 items are polish.

Each item records **what**, **why it matters**, and **evidence** where the defect was observed
directly rather than inferred.

Status legend: `[ ]` open · `[x]` done

---

## P0 — Critical

### [x] 1. Remove `AgentDebugLog` — debug telemetry ships in the release APK
`app/.../android/widget/AgentDebugLog.kt` POSTs a JSON payload (app-widget ids, prefs keys, commit
results) to a hardcoded HTTP endpoint (`http://10.0.2.2:7603`, `http://127.0.0.1:7603`) on every
widget configuration save, every widget update, and every config-activity launch. It is left-over
agent instrumentation with a hardcoded session id and ingest path.

**Why:** unexpected outbound network traffic from a privacy-sensitive transit app; cleartext HTTP;
a thread pool and network work on paths that should be cheap. It is compiled into release builds.

**Evidence:** file exists and is called from `WidgetPreferences`, `MBTATripWidget`,
`MBTAStationBoardWidget`, `WidgetUpdateWorker`, and both config activities.

### [x] 2. Widget stays on "Tap to set up" after being configured
`provideGlance` reads the widget config *before* calling `provideContent`. Glance's
`GlanceAppWidget.update()` recomposes the already-registered content lambda; it does not re-run the
code that ran before `provideContent`. So when the user configures a widget while its Glance session
is alive, the saved config is never read and the widget stays on the configure prompt until the
session is torn down (minutes later, or on next app launch).

**Why:** this is the first thing every new user does. It also explains the accumulated workarounds
in the codebase: `pending_config_widget_id` fallbacks, 12× retry loops, `AgentDebugLog` probes.

**Evidence:** placed the station-board widget on the emulator, configured it with Park Street,
watched it stay on "Tap to set up" for 90+ seconds (`station_board_config_49` was present in
`shared_prefs/widget_config.xml` the whole time). It rendered correctly only after the app process
was restarted, which forced a new Glance session.

**Fix:** load config + data *inside* `provideContent` so recomposition re-reads it.

### [x] 3. 1.75 MB of JSON parsed on the main thread during `Application.onCreate`
`GlobalDataStore.warmFromDisk()` is called synchronously from `TminusApplication.onCreate` and does
`File.readText()` + `Json.decodeFromString` of the whole MBTA stop/route catalog.

**Why:** it is on the critical path of every cold start, including the launch of a widget config
activity and every WorkManager wake-up that starts the process.

**Evidence:** `/data/data/com.saarlabs.tminus/files/global_data_cache.json` is **1,749,441 bytes**.

### [x] 4. Stop pickers drop 60%+ of frames
Every stop picker builds its list with `getParentStopsForSelection()` (filter + sort over ~9 000
stops) inside a `remember` keyed on the search query, i.e. on the main thread on every keystroke.

**Evidence** (`dumpsys gfxinfo`, Pixel 9a emulator):
* scrolling the list — 37/61 janky frames (**60.7%**), p50 **61 ms**, p90 **109 ms**, p99 150 ms,
  34 "Slow UI thread" frames
* typing "Boston" — 8/12 janky frames (**66.7%**), p90 **150 ms**, p99 **250 ms**

### [x] 5. Unbounded 60-second alarm chain refetches the network forever
`LiveUpdateManager.ensureRunningIfNeeded` starts a self-chaining exact alarm with `durationMs = 0`
(no deadline) whenever *any* widget is placed. Each tick calls `updateAll()` on both widget classes,
and each widget update performs a **fresh network fetch** of the stop's schedules — there is no
schedule cache.

**Why:** battery drain and MBTA API rate-limit exhaustion, 24/7, whether or not the screen is on.
A countdown only needs a network fetch when the cached departure list is exhausted; the per-minute
tick should recompute "minutes until" locally.

---

## P1 — High

### [x] 6. Raw ISO timestamps shown to users
The commute "Sample trip" dialog renders `Departure: 2026-08-17T07:48`, ignoring the 12/24-hour
setting the app asks users to configure.
**Evidence:** screenshot of the preview dialog on the emulator.

### [x] 7. Validation error never clears
Saving a commute with an empty name shows "Add a name for this commute."; typing a name does not
clear it. The error also renders far from the field it refers to.
**Evidence:** observed on the emulator — error still visible with "Morning inbound" in the field.

### [x] 8. Last/first-train stop picker ignores the selected route
With "Orange Line" selected, the stop picker lists all ~9 000 stops in the system, starting with
`1 First Ave`. Picking a stop the route does not serve silently produces an alert that never fires.
**Evidence:** screenshot of the picker opened from the Orange Line alert editor.

### [x] 9. Last-train windows cannot cross midnight
`TminusNotificationWorker.minutesToHHmm` clamps hours to `0..23`, and the editor caps the window at
23:59. The MBTA's actual last trains depart after midnight, which is precisely what this feature is
for. MBTA V3 accepts `filter[max_time]` values past 24:00 (e.g. `26:30`).

### [x] 10. Widgets go blank after ~23:00 for late-night service
`MbtaV3Client.fetchScheduleForStops` hardcodes `maxTime = "23:59"`, so after-midnight departures are
never returned and the widget shows "No departures" during the hours riders care most.

### [x] 11. Status bar icons unreadable when app theme ≠ system theme
`enableEdgeToEdge()` is called with no arguments, so the system bar icon appearance follows the
*system* theme. Forcing dark mode in-app on a light-themed device leaves dark icons on a dark bar.
**Evidence:** screenshot — the clock is barely visible after switching to Dark in Settings.

### [x] 12. Settings screen has a large dead gap at the top
`SettingsContent` nests its own `Scaffold` inside the app's `Scaffold` and applies `innerPadding` on
top of the padding the caller already applied.
**Evidence:** screenshot — ~120 px of empty space above the "Settings" title.

### [x] 13. Notification dedup keys grow without bound
`TminusNotificationWorker` writes a `leave_<id>_<trip>_<epochMs>` boolean into SharedPreferences for
every notification it fires and never removes them. The file grows forever and is loaded into memory
on every worker run.

### [x] 14. `mergeIncludedStops(schedDoc, allStops.toMutableMap())` is a no-op that allocates
Inside `fetchReachableDestinationStops`'s pagination loop, the merge target is a throwaway copy of a
~9 000-entry map. The merged stops are discarded, and a full map copy is allocated per route pattern.

### [x] 15. Raw exception text still reaches the UI
`describeMbtaRequestFailure`'s `else` branch returns `e.message ?: e.toString()`, so socket/TLS/JSON
exception strings surface in the widget config screens — the thing commit `f3347ab` set out to fix.

### [x] 16. Stop search has no relevance ranking
Searching "Park" returns `10 Oak Park Dr`, `116 Park Ave`, `1344 Hyde Park Ave`… before `Park Street`.
Plain `contains` + alphabetical sort, with no preference for prefix matches, whole-word matches, or
rail stations over bus stops.
**Evidence:** screenshot of the "Park" query.

### [x] 17. `repeat(8) { … return@repeat }` never breaks the loop
`return@repeat` returns from the current iteration, not the loop, so both widgets always burn the
full 2 s of `delay(250)` before rendering the configure prompt.

### [x] 18. Accessibility alerts use a substring heuristic instead of the API's stop filter
`runAccessibilityNotifications` fetches every alert on a route and matches the station by testing
whether the alert header contains any token of the station name with length ≥ 4. Tokens like
"Street", "Square", "Center", "Station" match unrelated alerts. MBTA V3 supports
`filter[stop]` on `/alerts`, which is exact.

### [x] 19. No tests in the `:app` module
`:network-json` has one test file; `:app` has no `test/` or `androidTest/` source set at all. None of
the trip-planning, schedule-parsing, or time-window logic is covered.

### [x] 20. Widget config is stored as a newline-joined string
`WidgetPreferences` serialises config as `listOf(...).joinToString("\n")` and parses by splitting on
`\n`, with no schema version. A stop label containing a newline corrupts the config, and the format
cannot be extended.

---

## P2 — Medium

### [x] 21. No baseline profile
Startup and first-scroll run fully interpreted/JIT. A baseline profile is the highest-leverage
startup win after item 3.

### [x] 22. `targetSdk` 35 while `compileSdk` is 36
Ships without opting into the current platform's behaviour changes.

### [x] 23. Global mutable singletons instead of dependency injection
`TminusApplication.instance`, `lateinit var widgetTripUseCase`, `GlobalDataStore.client` are
process-wide mutable statics initialised in `onCreate`. Widgets and workers race against
initialisation, which is why `awaitClientReady()` polls with `delay(50)` for 3–15 seconds.

### [x] 24. No ViewModels — UI state lost on rotation and process death
Editors keep all state in `remember { mutableStateOf(...) }` inside composables. Rotating the device
mid-edit discards the form.

### [x] 25. Synchronous `commit()` on the main thread
Settings save (`MainActivity`, `SettingsActivity`) and `WidgetPreferences` pending-id helpers use
`commit()`, which blocks on `fsync`.

### [x] 26. `stopIdsForScheduleFilter` scans every stop on every call
An O(n) pass over ~9 000 stops per invocation, called for each widget render and each commute
evaluation. Should use a precomputed parent→children index.

### [x] 27. `findNextTrip` pairs schedules in O(n·m)
`fromSchedules.flatMap { toSchedules.filter { … } }` over two lists that routinely hold hundreds of
entries. A group-by-`tripId` join is linear.

### [x] 28. No Material You dynamic colour
Android 12+ users get a fixed palette. `dynamicLightColorScheme` / `dynamicDarkColorScheme` should be
offered (opt-out in Settings).

### [x] 29. Accessibility gaps
Home feature cards expose no button role or merged content description (TalkBack reads three
disconnected nodes); several icon-only controls and list rows are under the 48 dp target; the
station-board widget rows have no content description.
**Evidence:** `mobile_list_elements_on_screen` shows the cards as bare `android.view.View` nodes.

### [x] 30. Empty states are unstyled and repeat themselves
`EmptyState` renders left-aligned body text at the top of an otherwise blank screen and shows both
"Tap + to add your route…" and "Tap the + button to add one."

### [x] 31. Commute / alert list rows are visually flat and uninformative
Plain `surfaceVariant` blocks with no route colour, no enable/disable toggle, and no indication of
the next matching departure — inconsistent with the colourful Home cards.

### [x] 32. Home shows no transit information
The landing screen of a transit app is three navigation cards and a tip box. It should surface the
next departures for saved commutes and favourite stations.

### [x] 33. Live updates do not resume after reboot
`LiveUpdateManager`'s chain is only re-armed from `Application.onCreate` or a widget broadcast; there
is no `RECEIVE_BOOT_COMPLETED` receiver, so after a reboot widgets fall back to the 15-minute worker
until the user opens the app.

### [x] 34. `allowBackup="true"` with no backup rules
The MBTA API key in `tminus_settings.xml` is included in cloud backup. Needs
`android:dataExtractionRules` / `android:fullBackupContent` excluding the settings prefs.

### [x] 35. API key rendered in plain text
The Settings field shows the key in the clear with no masking and no reveal toggle.

### [x] 36. CI does not run lint or tests
`.github/workflows/build-apk.yml` only assembles a debug APK. No `lint`, no unit tests, no format
check, and no dependency caching hygiene.

---

## P3 — Repo & project hygiene

### [x] 37. Missing open-source project files
No `CHANGELOG.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, issue templates, or PR template.

### [x] 38. No architecture documentation
Nothing describes module layout, data flow, or the widget lifecycle — the least obvious part of the
codebase and the source of most of its bugs.

### [x] 39. No `CLAUDE.md` / contributor build notes for agent-assisted work
Conventions (explicit API mode, 4-space Kotlin, string resources) are implicit.

### [x] 40. Deprecated `ClickableText`
`SettingsContent.DocLink` uses `androidx.compose.foundation.text.ClickableText`, deprecated in
favour of `LinkAnnotation` + `Text`.

### [x] 41. Notification plumbing details
The channel is recreated on every `notify()`; there is one channel for three unrelated alert types;
`NotificationManagerCompat.notify` is called without checking `POST_NOTIFICATIONS`.

### [x] 42. Notification permission requested with no rationale
The system dialog appears with no in-app explanation of what the alerts are for.
**Evidence:** the prompt appeared over the launcher, detached from any app context.

---

## P4 — Polish

### [x] 43. Widget line-filter chips are colourless
The station-board config shows "Red Line" / "Green Line B" as default chips instead of using each
route's colour.

### [x] 44. Segmented-button selection colour is off-brand
`secondaryContainer` renders as saturated green against a navy/blue palette.

### [x] 45. Exact-alarm denial is invisible
If the user revokes `SCHEDULE_EXACT_ALARM`, live updates stop silently with no way to re-enable them
from the app.

### [x] 46. "Use these stops" is a low-affordance text button in the app bar
The primary action of the stop-pair picker is a small top-right text button while the screen below
it is empty.
