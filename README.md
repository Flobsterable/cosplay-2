# Cosplay

KMP UI/UX prototype for browsing cosplay festivals from [cosplay2.ru](https://cosplay2.ru/).

## What it does

- Loads the festival list from `POST https://cosplay2.ru/api/events/filter_list`
- Opens a detail screen for a selected festival
- Enriches detail data by parsing `application/ld+json` event blocks from the main page of `cosplay2.ru`
- Works as a Compose Multiplatform prototype for Android and Desktop

## Project structure

- `composeApp` - app shell, Android/Desktop targets and composition root
- `core:model` - shared models and DTOs
- `core:network` - shared Ktor client setup
- `core:platform` - platform integrations like image loading, back handling and app updates
- `data:festival` - remote API and repository for `cosplay2.ru`
- `data:update` - GitHub Releases API and update repository
- `feature:festival` - festival list and detail UI
- `feature:update` - update banner UI

## Notes

- The workspace started empty, so the project scaffold was created from scratch.
- A Gradle wrapper is not included in this workspace snapshot.

## Releases

- Production releases are created from Git tags like `v1.0.0`.
- The release workflow lives in [.github/workflows/release.yml](/Users/aleksandrgrigorev/AndroidStudioProjects/Cosplay/.github/workflows/release.yml).
- `APP_VERSION_NAME` and `APP_VERSION_CODE` are stored in [gradle.properties](/Users/aleksandrgrigorev/AndroidStudioProjects/Cosplay/gradle.properties). The Git tag must match `v<APP_VERSION_NAME>`.
- The workflow expects these GitHub repository secrets:
  - `ANDROID_KEYSTORE_BASE64`
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- The workflow builds a signed Android release APK, publishes it to GitHub Releases and uploads a SHA-256 checksum next to the APK.

## Updates

- The app checks the latest GitHub Release from `Flobsterable/cosplay-2`.
- On Android, tapping the update banner downloads the APK and opens the system installer.
- On Desktop, tapping the update banner opens the release asset or release page in the browser.
