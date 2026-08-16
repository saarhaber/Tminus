# Security policy

## Reporting a vulnerability

Please report security issues privately through
[GitHub's private vulnerability reporting](https://github.com/saarhaber/Tminus/security/advisories/new)
rather than opening a public issue.

Include what you found, how to reproduce it, and what an attacker could do with it. You can expect an
acknowledgement within a week.

## Scope

tMinus is a client for the public MBTA V3 API. It has no backend and no accounts. The things most
worth reporting are:

- anything that sends user data off the device to somewhere other than `api-v3.mbta.com`
- exposure of the user's MBTA API key
- exported components that can be driven by another app to do something unintended

## What the app stores

| Data | Where | Backed up? |
| --- | --- | --- |
| MBTA API key (optional) | `SharedPreferences` | No — excluded in `backup_rules.xml` |
| Saved commutes, alerts, watches | `SharedPreferences` | Yes |
| Favourite stations | `SharedPreferences` | Yes |
| Stop/route catalogue cache | app files dir | No |
| Timetable cache | app cache dir | No |

The app makes network requests only to `https://api-v3.mbta.com`. There is no analytics, telemetry,
or crash reporting.

## Supported versions

Fixes land on `main` and ship in the next release. There are no long-lived release branches.
