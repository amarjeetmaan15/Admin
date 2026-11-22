# Owner configuration checklist

1. Choose the Android launcher display name and provide it separately; the app label is intentionally blank for now.
2. Choose the Android application ID/package name. Do not use the example package from the specification unless you choose it. Set the repository variable `APP_APPLICATION_ID` or pass the same value to the manual workflow input `application_id`.
3. Add the owner's Supabase Auth email/password account if it is not already active. Its user ID must be the owner UID configured in `Config.kt`.
4. Configure these GitHub Actions secrets for a stable owner signature: `TESTING_KEYSTORE_BASE64` (base64-encoded JKS), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Do not commit the keystore or passwords. When these secrets exist, the workflow decodes the keystore into the runner temporary directory and signs the debug APK with it; without them, Android uses its ephemeral default debug key, suitable only for first-time testing.
5. Run Actions → Build private admin APK → Run workflow with the chosen application ID. Download the `Private-Admin-APK` artifact or the private GitHub Release.

## Backend security changes already made

- `mdm-publish` now rejects requests without a Bearer token and rejects any user other than the owner UID.
- The new Android app signs in with Supabase Auth and refreshes expiring tokens.
- The app uses only the public publishable key. The service-role key remains inside Edge Functions.
- The old `mdm-password` function and `mdm_secret` row were not deleted; the new app does not call them. Remove/retire legacy public-app MDM access separately after the public app no longer contains its old admin entry point.

## Known boundaries of this first implementation

- Version 1 includes route/place management, Recycle Bin and Review Center. Ads, app-content settings and analytics remain in Web Admin as the supplied document recommends.
- UI strings have a three-language helper and the main navigation is translated; some secondary form/error text still needs complete translation coverage.
- Full-page preview navigation and direct conversion of a route submission into a prefilled route editor need a follow-up implementation pass.
- A signed, upgradeable private APK cannot be finalized until the owner supplies the application ID and signing keystore.
