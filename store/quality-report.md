# Pre-build quality report — 1.0.0

## Fixed before release build
- Removed the undefined `createNewAccount()` call by routing initialization through the real internal account-creation flow.
- Removed the global app-context holder and moved URL launching to the current Composable context.
- Reworked periodic work to run only while the Activity lifecycle is RESUMED.
- Added serialized mailbox operations with a coroutine Mutex to avoid overlapping refresh/create/delete actions.
- Added automatic token refresh for message listing, opening, marking read, and deletion.
- Added automatic session recovery after a persistent 401.
- Added message pagination up to a bounded number of pages.
- Added bounded domain pagination and a small delay between pages.
- Added persisted auto-refresh preference.
- Added safer cleanup for corrupted encrypted local session data.
- Configured the release keystore explicitly as PKCS12.
- Changed CI so a release artifact is never silently treated as publishable when signing secrets are missing.
- Explicitly disabled cleartext network traffic in the Android manifest.
- Moved versionCode/versionName to Gradle properties to make future releases controlled and repeatable.
- Added update documentation to preserve package name and signing key.

## Deliberate product behavior
- Mailbox lifetime remains 60 minutes.
- No login, Google Sign-In, ads, analytics SDK, subscriptions, or IAP are included in 1.0.0.
- HTTPS is used for the Mail.tm API.
- Mail.tm attribution remains visible in the app.

## Still requires a real-device/CI check
- Gradle build and packaging
- Signed release APK verification
- Installation/launch on a physical Android device
- Create mailbox / receive message / open / mark read / delete flows against the live Mail.tm service
- Screenshot capture from the final APK
