# Android application

- Модуль владеет MainActivity, manifest, launcher resources, подписью и APK.
- Общий UI и логика остаются в composeApp/core/data/feature.
- Сохраняй applicationId, FileProvider authority, разрешения и параметры release.
- AGP 9 использует встроенный Kotlin; не применяй kotlin-multiplatform или kotlin-android здесь.
- Проверки: :androidApp:assembleDebug, :androidApp:lintDebug и общие Desktop tests.
