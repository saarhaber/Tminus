# Changelog

All notable changes to tMinus are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

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

### Changed

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
