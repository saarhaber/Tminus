# Changelog

All notable changes to tMinus are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- The "time to leave" alert now counts down live. The minute count moved out of the title, which
  was baked at post time and wrong a minute later, into a chronometer in the notification header.
  That also removes the reason `setWhen` had to be left unset: there is one countdown now instead
  of two drifting apart.
- "Time to leave" alerts carry `Snooze 5 min` and `Mute today`. `Snooze 5 min` appears only when the
  train is more than five minutes away, so it is never offered when it could not come back in time;
  muting applies to the rest of the current *service* day, so muting at 00:30 mutes the commute
  already in progress.

### Fixed

- `Snooze 5 min` did nothing on frequent routes. The alert fires close to departure, a five-minute
  snooze would have landed after the train had gone, and the snooze was then dropped without
  dismissing the notification — so the button looked broken. It is no longer offered in that case,
  and if the train becomes imminent between the alert arriving and the tap, the alert is at least
  dismissed instead of sitting there unchanged.

- Widgets stayed on "Tap to set up" after being configured. Glance's `update()` only recomposes
  already-registered content, so configuration read before `provideContent` was never refreshed for
  the life of the session. Widgets now render as soon as the configuration activity closes.
- Release builds could not be produced at all: the launcher icon foreground was JPEG data with a
  `.png` extension, which `mergeReleaseResources` rejects.
- Widgets showed "no departures" late at night. Schedule queries now use MBTA service-day hours
  (24:00–26:59) and an explicit service date instead of stopping at 23:59.
- "Last train" alert windows can now cross midnight, which is when the last train usually runs.
- The commute preview showed raw ISO timestamps (`2026-08-17T07:48`) instead of formatted times.
- The "add a name" validation error stayed on screen after a name was entered, and appeared far from
  the field it referred to.
- The alert editors' stop picker offered every stop in the system regardless of the selected route,
  so an alert could be attached to a stop the route never serves.
- Status bar icons were unreadable when the in-app theme differed from the system theme.
- Settings had a band of dead space at the top caused by nested scaffolds applying insets twice.
- Elevator and escalator alerts were matched by substring against station names, which both missed
  outages and fired on unrelated ones. They now use the API's stop filter.
- Notification delivery markers accumulated in `SharedPreferences` forever.
- Live widget updates did not resume after a reboot.
- The favorite stops and service alerts widgets showed a bare loading spinner in the widget picker:
  both declared the loading layout as their `previewLayout`. They now preview their real content —
  station names, headsigns and countdowns for favorites, effect and alert text for alerts.
- The station board's picker preview had drifted from the widget: no line-coloured rail, square row
  corners, and a schedule line reading "in 3 min" where the widget renders "3 min".
- Track numbers were missing everywhere except the station board. The commuter rail trip widget —
  the only mode MBTA publishes a track for — never drew one at all; the favorite stops widget
  dropped the track it had already fetched; and the subway trip layout hid it below 5x4 cells.
  The trip widget now shows both ends' tracks (once, when they are the same), and the boards append
  the track to the departure's clock time.
- Configuring a widget could leave it on "Tap to set up" while a different widget picked up the
  configuration. When a launcher starts the configuration activity without `EXTRA_APPWIDGET_ID`,
  the id recorded by the waiting widget is now only trusted if it still names a placed, unconfigured
  widget; otherwise the single unconfigured widget of that provider is used. The recorded id is also
  no longer consumed on read, which had made a rotation mid-setup close the activity with nothing
  saved.
- Home's station cards truncated the destination to "Wor…" when the route name was long, because
  the route chip and the destination shared one line. The chip now sits above the destination.
- The last/first train notification printed a raw ISO timestamp (`2026-08-17T23:45`) for the
  departure instead of a formatted clock time in the user's chosen 12/24-hour style.
- Accessibility alerts notified again on every worker run. Delivery markers expired on the number
  at the end of their key, which is an epoch for trip alerts but an MBTA alert id for these — read
  as a timestamp, six digits is January 1970, so the marker was always already expired. Markers now
  record when they were delivered, and service alerts keep theirs for 30 days rather than 3.
- Elevator and escalator alerts never notified at all. The V3 alerts endpoint applies its own
  `filter[activity]` default of `BOARD,EXIT,RIDE` when the parameter is absent, and lift and
  escalator outages are filed under `USING_WHEELCHAIR` and `USING_ESCALATOR` — so the station watch
  fetched alerts for the right route and stops and the closures were filtered out before the app
  ever saw them. The accessibility query now names the activities it needs, restating the defaults
  it replaces so ordinary stop closures keep arriving.
- A notification dropped for want of notification permission was still recorded as delivered, so the
  event stayed silent for good once permission was granted. Delivery is now recorded only after the
  notification reaches the shade.
- Each periodic run queued another exact wakeup for the same departure — four for one arrival in
  testing — and every one of them woke the process and re-ran the worker, MBTA fetches included,
  only for the delivery marker to throw the result away. Wakeups are now keyed per event.
- "Time to leave" showed two countdowns that disagreed: the title's minutes were computed when the
  worker ran, while Android rendered the notification's timestamp as its own live relative time, so
  "Departs in 6 min" sat beside a header reading "in 5m". The departure remains in the body as a
  clock time, which cannot drift out of step.
- Notifications repeated the stop picker's disambiguation suffix, so a commute alert read "South
  Station (Transit hub) → Back Bay (Transit hub) · 10:02 AM" and was cut off after the arrow — the
  destination, the one thing the line is for, never survived. Notifications now use the plain stop
  name, as the trip widget already did; a label the user typed themselves is still respected.

### Changed

- Notifications are drawn by the platform instead of a custom `RemoteViews`. The old layout painted
  the whole notification in the route colour and hard-coded the text colours, which ignored dark
  mode, Material You and font settings, and clipped long alert text to one line. The line colour now
  tints the notification the way Android expects, long text expands, and each alert carries the
  commute or watch name as its sub-text and its track where there is one. Station alerts lead with
  what has actually failed — "Elevator out at Chinatown" rather than "Station accessibility alert",
  which only repeated what the icon already said — and keep the MBTA's own sentence, with the unit
  number and the platforms it serves, as the body. "Time to leave" leads with the countdown and retires itself ten minutes after the
  train has gone.
- The next trip widget (subway, bus and ferry) was relaid out. "min" now sits on the countdown's
  baseline instead of on the body's next line, where it drifted away from its own digits as the
  widget grew; the departure and arrival times moved into a footer band that lines up with the stop
  names; and the widget picks between three layouts by size — countdown stacked above the stops,
  countdown beside them when the widget is wide, and a single row when it is one cell tall. Type is
  fitted to the space that is actually left, so the countdown is no longer clipped by the footer.
- Stop search ranks results: exact, then prefix, then word-start, then substring, with rail stations
  ahead of bus stops. Searching "Park" now returns Park Street first.
- Home leads with the next departures from starred stations.
- Notifications are split into three channels, and the permission request is preceded by an
  explanation of what the alerts are for.
- Material You dynamic colour on Android 12+, with an opt-out in Settings.
- The API key is masked, with a reveal toggle, and is excluded from cloud backup.
- `targetSdk` raised to 36.

### Removed

- Debug instrumentation that POSTed widget configuration data to a hardcoded HTTP endpoint on every
  save and update, in release builds as well as debug.

### Performance

- The ~1.7 MB stop catalogue is no longer parsed on the main thread during application startup.
- Stop pickers filter and sort off the main thread. Measured on a Pixel 9a emulator: janky frames
  while scrolling fell from 60.7% to 2.8%, p90 frame time from 109 ms to 19 ms; while typing, p99
  fell from 250 ms to 32 ms.
- Widget ticks recompute countdowns from cached timetables instead of issuing one API request per
  widget per minute, and back off to 15-minute intervals while the screen is off.

### Added

- Unit tests for search ranking, trip pairing, station-board assembly, stop-family resolution and
  service-day handling.
- CI runs unit tests, Android Lint, and a release build.
- `docs/ARCHITECTURE.md` and `CLAUDE.md`.
