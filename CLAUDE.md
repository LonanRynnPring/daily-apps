# daily-apps

Each day, create ONE new small Android app in `apps/YYYY-MM-DD-short-name/`, then commit and push to `main`. GitHub Actions builds the APK and publishes it as a release named after the folder.

## Rules for each daily app
- Read `REQUESTS.md` first. It is the owner's steering file. If "Specific ideas" has an unticked `- [ ]` line (ignore any marked "example"), build the first one and tick it as `- [x] (YYYY-MM-DD) ...`. Otherwise pick your own idea, following any "Themes" listed. The owner's requests take priority over the other idea rules below, but never over the technical rules.
- Check `apps/` and `IDEAS.md` first. Never repeat an idea. Append a one-line entry to `IDEAS.md` for the new app.
- Copy the structure of `apps/2026-11-17-tap-counter/` exactly (settings.gradle, build.gradle, app/build.gradle, AndroidManifest.xml). Do not add a Gradle wrapper; CI uses Gradle 8.9 and AGP 8.5.2, JDK 17.
- Plain Java, `android.app.Activity`, views built in code. No AndroidX, no third-party dependencies, no network or special permissions unless the app really needs them. This keeps builds reliable.
- Never build something a phone already ships with. Banned: stopwatch, timer, alarm, clock, calculator, torch/flashlight, notes, calendar, to-do list, compass, unit converter, voice recorder, tap/lap counters, QR scanner, basic dice or coin flip, basic random picker. Pick ideas with a real twist that a stock phone does not do, such as games, creative toys, generators, puzzles, simulations, visualisers, or niche tools for a specific hobby or situation.
- Make it fun and substantial, not a toy: aim for something you would actually want to open again tomorrow. It should have real depth, for example multiple screens or game levels, scoring and high scores, custom drawing with Canvas/animation, sensors (accelerometer etc.), sound, vibration haptics, or procedural generation. Polish counts: good colours, clear layout, sensible touch targets, state saved with SharedPreferences or SQLite where useful. Multiple Java classes and files are fine.
- Stay within the technical constraints so the build passes: complex is fine, fragile is not. Do not use dependencies, only the Android SDK. If a feature risks failing the build (obscure APIs, deprecated calls), pick a simpler route.
- Works offline unless the idea truly needs a network.
- Use a unique `applicationId` and `namespace`: `com.dailyapps.<shortname>`.
- Only touch the new app's folder, `IDEAS.md`, `REQUESTS.md` (only to tick off an idea) and (if fixing a build) the workflow. Never edit older apps unless fixing a failed build.
- One commit per day, message: `Add <app name>`.
