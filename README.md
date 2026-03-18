# Cosplay

KMP UI/UX prototype for browsing cosplay festivals from [cosplay2.ru](https://cosplay2.ru/).

## What it does

- Loads the festival list from `POST https://cosplay2.ru/api/events/filter_list`
- Opens a detail screen for a selected festival
- Enriches detail data by parsing `application/ld+json` event blocks from the main page of `cosplay2.ru`
- Works as a Compose Multiplatform prototype for Android and Desktop

## Project structure

- `composeApp/src/commonMain` - shared UI, models, repository, parsing
- `composeApp/src/androidMain` - Android entry point and OkHttp client
- `composeApp/src/desktopMain` - Desktop entry point and CIO client

## Notes

- The workspace started empty, so the project scaffold was created from scratch.
- A Gradle wrapper is not included in this workspace snapshot.
