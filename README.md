# Private Bustime Admin App

This repository contains the separate Android owner app for managing Bustime routes, stops, submissions and deleted records.

## Owner-controlled identity
- App display name: deliberately blank until the owner supplies it.
- Android application ID/package: deliberately not selected. Supply it as the GitHub Actions input `application_id` or repository variable `APP_APPLICATION_ID`.
- Keep this repository private. Never publish the APK to Google Play.

## Implemented
- Kotlin Android Views, built programmatically (no XML layouts).
- Supabase Auth email/password login; validates the signed-in UID against the configured owner UID.
- Token refresh and owner-only Bearer authorization for every privileged request.
- Supabase public publishable key only in the client; service-role key is never included.
- Room local cache for routes, places and revision cursor.
- First-install snapshot copied from the public Bustime repository (dataVersion 74, snapshotRevision 14; 49 places, 55 routes), followed by paged delta sync.
- Route list, correction/edit, add, copy, return (reversed intermediate stops and blank timings), delete.
- Stop list, add/edit, usage check before delete.
- Recycle Bin restore and permanent delete.
- Review Center for all four submission tables; feedback is read-only and only pending/reviewed.
- English, Hindi and Punjabi language selector and localized primary navigation.
- GitHub Actions APK workflow, 7-day artifact retention and private GitHub Release.

## Supabase
Project: `mdryxbvmqsphbfkvqtnf`
URL: `https://mdryxbvmqsphbfkvqtnf.supabase.co`

Existing functions used:
- `mdm-publish` — privileged actions.
- `bustime-data-sync` — public status and change feed.

The `mdm-publish` Edge Function was updated to accept only a valid Supabase Auth Bearer token belonging to the owner. The legacy password body path is no longer accepted. The app does not call `mdm-password`.

The revision counter was reconciled to the maximum change-log revision (92). No database tables or RLS policies were altered.

## Build
GitHub Actions builds on pushes after `APP_APPLICATION_ID` is configured, or manually via Actions → Build private admin APK → Run workflow, entering the owner's chosen application ID.

The display name and application ID are intentionally not invented in this repository. A stable private signing keystore must also be configured before producing long-term upgradeable owner APKs. See [configuration checklist](docs/CONFIGURATION.md).

## Scope
Ads, app-content settings, analytics and the public app's update screens remain in Web Admin / public Bustime as described in the supplied rebuild specification. This repository does not modify those projects.
