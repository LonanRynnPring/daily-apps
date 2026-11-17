# daily-apps

Each day, create ONE new small Android app in `apps/YYYY-MM-DD-short-name/`, then commit and push to `main`. GitHub Actions builds the APK and publishes it as a release named after the folder.

## Rules for each daily app
- Read `REQUESTS.md` first. It is the owner's steering file. If "Specific ideas" has an unticked `- [ ]` line (ignore any marked "example"), build the first one and tick it as `- [x] (YYYY-MM-DD) ...`. Otherwise pick your own idea, following any "Themes" listed. The owner's requests take priority over the other idea rules below, but never over the technical rules.
- Check `apps/` and `IDEAS.md` first. Never repeat an idea. Append a one-line entry to `IDEAS.md` for the new app.
- Copy the structure of `apps/2026-11-17-tap-counter/` exactly (settings.gradle, build.gradle, app/build.gradle, AndroidManifest.xml). Do not add a Gradle wrapper; CI uses Gradle 8.9 and AGP 8.5.2, JDK 17.
- Plain Java, `android.app.Activity`, views built in code. No AndroidX, no third-party dependencies, no network or special permissions unless the app really needs them. This keeps builds reliable.
- Keep it small but genuinely useful or fun: one clear purpose, works offline, saves state with SharedPreferences if needed.
- Use a unique `applicationId` and `namespace`: `com.dailyapps.<shortname>`.
- Only touch the new app's folder, `IDEAS.md`, `REQUESTS.md` (only to tick off an idea) and (if fixing a build) the workflow. Never edit older apps unless fixing a failed build.
- One commit per day, message: `Add <app name>`.
