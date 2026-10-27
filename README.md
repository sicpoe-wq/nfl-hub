# NFL Gridiron

Android app (Kotlin / Jetpack Compose) for NFL rosters, stats, injuries, schedules, scores, standings, leaders and news, using ESPN's public data. All 32 teams available.

Two build flavors (same package id):
- `personal` (`./gradlew assemblePersonalDebug`): opens to the Philadelphia Eagles by default.
- `public` (`./gradlew assemblePublicDebug`): on first launch asks "Choose your team homepage — What team do you follow?" with live search, The League (NFL) first, then all 32 teams.

The startup page (any team or NFL (The League)) can be changed any time in Settings → Default Team.

Download the latest APK from [Releases](https://github.com/sicpoe-wq/nfl-hub/releases/latest).

Package id: `com.tim.nflhub` (unchanged from the earlier "NFL Hub" builds, so new versions install as updates).
