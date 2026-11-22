# daily-apps

Each day, create ONE new small Android app in `apps/YYYY-MM-DD-short-name/`, then commit and push to `main`. GitHub Actions builds the APK and publishes it as a release named after the folder.

## Rules for each daily app
- Read `REQUESTS.md` first. It is the owner's steering file. If "Specific ideas" has an unticked `- [ ]` line (ignore any marked "example"), build the first one and tick it as `- [x] (YYYY-MM-DD) ...`. Otherwise pick your own idea, following any "Themes" listed. The owner's requests take priority over the other idea rules below, but never over the technical rules.
- Check `apps/` and `IDEAS.md` first. Never repeat an idea. Append a one-line entry to `IDEAS.md` for the new app.
- Copy the structure of `apps/2026-11-17-tap-counter/` exactly (settings.gradle, build.gradle, app/build.gradle, AndroidManifest.xml). Do not add a Gradle wrapper; CI uses Gradle 8.9 and AGP 8.5.2, JDK 17.
- Plain Java, `android.app.Activity`, views built in code. No AndroidX, no third-party dependencies, no network or special permissions unless the app really needs them. This keeps builds reliable.
- Never build something a phone already ships with. Banned: stopwatch, timer, alarm, clock, calculator, torch/flashlight, notes, calendar, to-do list, compass, unit converter, voice recorder, tap/lap counters, QR scanner, basic dice or coin flip, basic random picker. Pick ideas with a real twist that a stock phone does not do, such as games, creative toys, generators, puzzles, simulations, visualisers, or niche tools for a specific hobby or situation.
- Make it fun and substantial, not a toy: aim for something you would actually want to open again tomorrow. It should have real depth, for example multiple screens or game levels, scoring and high scores, custom drawing with Canvas/animation, sensors (accelerometer etc.), sound, vibration haptics, or procedural generation. Polish counts: good colours, clear layout, sensible touch targets, state saved with SharedPreferences or SQLite where useful. Multiple Java classes and files are fine.
- Stay within the technical constraints so the build passes: do not use dependencies, only the Android SDK. You cannot compile locally (no Android SDK in this sandbox), so write careful, conservative code, then verify with the build loop below. Because builds are verified and fixed automatically, you may be ambitious.
- Works offline unless the idea truly needs a network.
- Use a unique `applicationId` and `namespace`: `com.dailyapps.<shortname>`.
- Only touch the new app's folder, `IDEAS.md`, `REQUESTS.md` (only to tick off an idea) and (if fixing a build) the workflow. Never edit older apps unless fixing a failed build.
- One commit per day for the app, message: `Add <app name>`. Build fixes get their own commits, message: `Fix <app name> build`.

## Build loop (required)
You cannot compile locally, but GitHub Actions can. After pushing the app:
1. Get the pushed commit: `SHA=$(git rev-parse HEAD)`.
2. Run `bash scripts/wait-for-build.sh "$SHA"` with a tool timeout of about 10 minutes (600000 ms). It polls GitHub until the build finishes.
3. Exit 0 means the build passed and the release is published: you are done.
4. Exit 1 means the build failed and the script has printed the compiler errors. Fix the code in the new app's folder only, commit `Fix <app name> build`, push with `git -c push.negotiate=true push origin main`, and go back to step 1 with the new SHA. A `push negotiation failed` warning is harmless; check the result line.
5. Exit 2 means it timed out: run the script once more with the same SHA. If it times out again, say so in your summary.
6. Make at most 3 fix attempts. If it still fails, simplify or remove the risky feature so the app builds, rather than leaving it broken. If it still cannot be made to build, say plainly in your final summary that it failed and why.
Failed-build logs are saved by the workflow on the `build-logs` branch; the script reads them for you. Do not commit to that branch.
