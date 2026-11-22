# Implementation plan

The app is being rebuilt as a separate Android project in this repository. The final display name and application ID are deliberately left undecided until the owner supplies them.

## Phase 1 — project foundation
- Android/Kotlin project and GitHub Actions debug APK build.
- Programmatic Android Views; no XML screen layouts.
- Supabase Auth email/password session handling and token refresh.
- Centralized API client; anon/publishable key only in the client.
- Shared English/Hindi/Punjabi text helper.
- Room entities and revision cursor.

## Phase 2 — data and publishing
- Read sync status from `bustime-data-sync?action=status`.
- Fetch changes from `bustime-data-sync?action=changes&since=N&offset=M` in pages of 1000.
- Apply route/place upserts and deletes in revision order.
- Route/place lists and search backed by Room.
- Publish through `mdm-publish` with an owner Supabase Auth Bearer token.
- Never send the legacy password field and never call `mdm-password`.

## Phase 3 — owner screens
1. Sign-in.
2. Home.
3. Route list and full-page route details/editor/preview.
4. Add and correct bus stops.
5. Recycle Bin restore and permanent delete.
6. Review Center with four submission categories and server-approved editable fields.

## Phase 4 — verification
- Build with GitHub Actions.
- Verify sign-in, wrong-password rejection, token refresh, route/stop CRUD, validation messages, recycle actions, review actions, and public-app sync.
- Verify all visible text in English, Hindi, and Punjabi.

## Existing backend discovered
Supabase project `mdryxbvmqsphbfkvqtnf` is active. Existing Edge Functions found:
- `mdm-publish` (active, version 17)
- `bustime-data-sync` (active, version 3)
- `mdm-password` (active; legacy function, not used by the new app)
- `mdm-history` (active)

The current `mdm-publish` implementation accepts both an owner Bearer token and the legacy password fallback. The new app will use only the owner Bearer-token path. As specified by the owner, the server-side password fallback change is a separate backend task and is not changed by this Android app initialization.

## Important pre-build configuration
- Owner must supply the final app display name and Android application ID.
- Owner must supply the Supabase public anon/publishable key for the client configuration.
- GitHub Actions signing secrets and the private app keystore must be configured before a signed owner-device APK can be produced.

No Supabase tables, RLS policies, or Edge Functions were changed during initialization.
