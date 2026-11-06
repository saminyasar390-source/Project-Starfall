# Week 8 – Getting Started with Android Studio

| Topic | Where in the project |
|---|---|
| Project architecture | `app/src/main/java` (code), `res` (images, layouts, themes), `manifests` |
| Gradle basics | `build.gradle.kts` files; `viewBinding = true` in `app/build.gradle.kts` |
| AndroidManifest | `AndroidManifest.xml` – 3 activities, launcher `MainMenuActivity`, landscape |
| Activity lifecycle | `Log.d("Lifecycle", ...)` in `MainMenuActivity`, `GameActivity`, `SettingsActivity`; `GameActivity.onPause/onResume` pause and restart the game loop |
| XML layout | `res/layout/activity_settings.xml` |
| View binding | `SettingsActivity` uses `ActivitySettingsBinding` |
| Click listeners / event handling | `SettingsActivity` – `setOnClickListener`, `setOnCheckedChangeListener`; `MenuView.onTouchEvent` for the menu artwork |
| Explicit Intent | `MainMenuActivity` – Play opens `GameActivity`, Settings opens `SettingsActivity` |
| Implicit Intent | `SettingsActivity` – "View project on GitHub" (`ACTION_VIEW`), "Share the game" (`ACTION_SEND`) |

## Lifecycle log (Logcat filter: `Lifecycle`)
Add the lines you captured here for: open app, press Play, press Back, press Home.
