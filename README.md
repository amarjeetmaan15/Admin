# Bustime Admin App

This repository is the new, separate Android admin application for Bustime.

## Project identity
- Display name: intentionally not set yet; owner will provide it.
- Android application ID / package name: intentionally not set yet; owner will provide it.
- Repository: private app source; do not publish this app to the Play Store.

## Implementation scope
Build the separate owner-only Android app described in the supplied *Bustime MDM Admin App — Complete Spec*.

- Kotlin and Android Views created in code.
- Supabase Auth email/password sign-in; no legacy short-password screen.
- Supabase Edge Function is the only privileged write path.
- Local Room cache for places and routes, populated by revision-based public data sync.
- Route list/editor, stop list/editor, recycle bin, and review center.
- English, Hindi, and Punjabi for all visible text.
- GitHub Actions builds the APK; no local Android Studio requirement.

## Backend constraints
Use the existing Supabase project and existing Edge Functions from the supplied specification. Do not create or alter database tables as part of the initial Android app work. Never put a service-role key, owner password, or signing credentials in app source.

## Owner decisions still pending
The owner will provide the final app display name and Android application ID/package name. Do not invent either value.

## Working rules
- Make only changes needed for this app.
- Do not delete or rename existing files without asking.
- Keep the public Bustime app and this private admin app separate.
- Verify every GitHub Actions build and report failures clearly.
