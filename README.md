# Reborn

**Pause. Choose. Begin again.**

Reborn is a 100% offline, zero-network Android app blocker built with Kotlin, Jetpack Compose (Material 3), Hilt, Room, and DataStore.

## Privacy Architecture
- **No `INTERNET` permission** in `AndroidManifest.xml`.
- **No analytics, no ads, no accounts.**
- Targeted `<queries>` manifest declarations (`ACTION_MAIN` + `CATEGORY_LAUNCHER`, `CATEGORY_HOME`, `ACTION_DIAL`, `BROWSABLE`) instead of broad `QUERY_ALL_PACKAGES`.

## Project Structure
- `app/src/main/java/com/harsha/reborn/core/data`: Room database (`RebornDatabase`), DataStore preferences, `InstalledAppsRepository`, `SelectedAppsRepository`, `BlockingRulesRepository`, `FrictionRepository`, `StrictModeRepository`, `InsightsRepository`, `BackupRepository`
- `app/src/main/java/com/harsha/reborn/core/engine`: `RuleEvaluator`, `RulesCacheManager`, `UsageStatsTracker`, `InsightsCalculator`, `QuickBlockToggleController`, `ProtectedPackagesProvider`, `PermissionHealthManager`, `OemBatteryHelper`, `RebornAccessibilityService`, `RebornGuardService`, `BootReceiver`, `GuardWatchdogWorker`, `StrictSystemScreenDetector`, `RebornDeviceAdminReceiver`, `BatteryStatusHelper`, `StrictModeCrypto`, `NetworkWifiHelper`, `OfflineLocationHelper`, `feed/FeedSelectorConfig`, `feed/InAppFeedDetector`, `browser/BrowserKeywordDetector`, `overlay/GrayscaleDimOverlayManager`
- `app/src/main/java/com/harsha/reborn/feature/onboarding`: `OnboardingScreen`, `PermissionHealthScreen`, `PrivacyDisclosureScreen`
- `app/src/main/java/com/harsha/reborn/feature/blocking`: `HomeScreen`, `RulesScreen`, `RuleEditorDialogs`, `AppPickerScreen`, `BlockActivity`
- `app/src/main/java/com/harsha/reborn/feature/friction`: `FrictionScreen`, `FrictionViewModel`, `BreathingPauseOverlay`
- `app/src/main/java/com/harsha/reborn/feature/strict`: `StrictModeScreen`, `StrictModeSections`, `StrictModeViewModel`, `EmergencyEscapeDialog`
- `app/src/main/java/com/harsha/reborn/feature/smart`: `SmartRulesSection`, `SmartRuleDialogs`
- `app/src/main/java/com/harsha/reborn/feature/insights`: `InsightsScreen`, `InsightsCharts`, `HomeInsightsCard`, `InsightsViewModel`
- `app/src/main/java/com/harsha/reborn/feature/widget`: `QuickBlockWidgetProvider`, `RebornQuickBlockTileService`
- `app/src/main/java/com/harsha/reborn/feature/about`: `AboutScreen`, `AboutViewModel`
- `app/src/main/java/com/harsha/reborn/ui/theme`: Phoenix Dark Navy, AMOLED Black, and Warm Light themes, Material You dynamic color, custom bundled `Outfit` + `Plus Jakarta Sans` typography
- `app/src/main/res/values-hi`: Full Hindi (`hi`) string localization alongside English (`values/strings.xml`)
- `logo.svg`: Standalone vector Phoenix logo

## Setup
1. Open the project in Android Studio (AGP 9.1+, JDK 17+, `minSdk = 26`, `targetSdk = 35`).
2. Sync and run `gradle :app:assembleDebug` or `gradle :app:testDebugUnitTest`.

## Manual Testing Checklist

### Phase 0 & Phase 1 (Foundation)
- [ ] **Splash & Launcher Icon**: Verify Phoenix adaptive icon (and Android 13+ monochrome icon) and splash transition into Onboarding.
- [ ] **Onboarding & Permission Health**: Grant Accessibility, Usage Access, Overlay, Notifications, and Battery Optimization Exemption; confirm live `ON_RESUME` updates and Home warning banner behavior.
- [ ] **App Picker**: Search, filter by category, multi-select apps, verify persistence across restart, and confirm Phone/Dialer & Home Launcher stay `Protected`.

### Phase 2 (Core Blocking Engine & Rules)
- [ ] **Quick Block**: Pick apps + duration (e.g., 15m), tap **Start Quick Block**, verify `Reborn Guard Active` persistent notification appears, open a blocked app, verify `BlockActivity` shield appears with **"Go Home"**, and tap **Go Home** to reach the launcher cleanly without a loop.
- [ ] **Schedules**: Enable **Work**, **Study**, or **Sleep** (or create an overnight schedule e.g. 22:30–07:00), assign apps, verify blocking inside the scheduled window and normal access outside.
- [ ] **Daily Time Limits**: Create a group limit (e.g. Social Group = 5 min) or single-app limit, verify live progress bar from `UsageStatsManager`, and confirm blocking triggers once daily minutes reach the limit.
- [ ] **Per-App Launch Limits**: Set a 2-open/day limit on a test app, open & return Home twice (allowed), then open a 3rd time to verify `Launch Limit Reached` triggers.
- [ ] **Allowlist Mode**: Enable Allowlist Mode, allow only 1–2 apps, verify non-allowed apps are blocked while allowed apps, Phone/Dialer, Settings, and Launcher remain accessible.
- [ ] **Reboot Persistence**: With a rule active, reboot the device and verify `BootReceiver` restarts `RebornGuardService`.

### Phase 3 (Friction System)
- [ ] **Breathing Pause Screen**: Set pause countdown (e.g., 10s) in **Mindful Friction**; open a blocked app and confirm the breathing circle animates with a countdown while **"Go Home"** stays immediately available and the unlock button remains gated until 0s.
- [ ] **Intent-Based Unlock & Auto Re-block**: Wait out the pause countdown, tap **Need this app? Request Unlock**, enter a reason (< 4 chars rejected), pick **5m / 10m / 15m**, confirm the app opens, check the **Intent Unlock Journal** entry, and wait for expiry to verify `BlockActivity` automatically re-blocks the app if still open.
- [ ] **Daily Unlock Pass Cap**: Set Daily Cap to 1 or 2 passes, use all passes, and verify the next blocked attempt states the daily cap is reached and hides/blocks further unlock passes.
- [ ] **Delayed Disable (10-min Wait)**: With Delayed Rule Disable enabled, tap **Stop Quick Block** or toggle off an active Schedule/Limit/Allowlist; verify the rule stays active, the **Delayed Rule Disables** countdown banner appears with a working **Cancel** button, and the rule disables automatically once the timer reaches `00:00`.

### Phase 4 (Strict Mode)
- [ ] **Duration-Locked Mode**: Activate Strict Mode for 15m (or 1h) with no early unlock options; confirm **Rules**, **Friction**, and **App Picker** show the Strict Mode lock banner and refuse disabling active rules or removing apps until the timer completes.
- [ ] **PIN / Charger / Trusted-Contact Unlock**: Activate Strict Mode with PIN + Charger + Trusted-Contact Code; confirm early unlock fails while unplugged or with a wrong PIN/code, and succeeds once plugged in with valid PIN and friend code.
- [ ] **System Tamper & Uninstall Guard**: While Strict Mode is active, open Android **Settings -> Accessibility -> Reborn**, **App Info (Force Stop / Uninstall)**, or try uninstalling Reborn from the launcher; verify `BlockActivity` (`Strict Mode Protected`) blocks access while Wi-Fi / Display settings remain usable.
- [ ] **Device Admin Uninstall Protection**: Enable **Device Admin Guard** on the Strict Mode screen; confirm Android prevents uninstalling Reborn until Device Admin is turned off after Strict Mode ends.
- [ ] **Emergency Escape Hatch**: Tap **Start Emergency Escape**, verify the cool-down timer cannot be skipped, type `"I CHOOSE TO END STRICT MODE EARLY"` once the timer reaches `00:00`, and verify Strict Mode ends and logs `EMERGENCY_ESCAPE` in **Strict Mode Log**.

### Phase 5 (Smarter Rules)
- [ ] **Conditional Rules**: Create a rule combining Wi-Fi (or specific SSID), Charger state, Time of day, and optional Area Zone (`ACCESS_COARSE_LOCATION`); confirm the target app is blocked when all enabled conditions match and allowed when one stops matching.
- [ ] **In-App Short Feeds (Shorts / Reels / TikTok)**: Enable **In-App Short Feeds** under **Rules -> Smart**; open YouTube Home / Search (allowed), tap **Shorts** (blocked with option to return safely to Home or App). Repeat with Instagram Feed vs. **Reels**, and test editing **Selector Config JSON** (confirm invalid JSON shows error & fails open).
- [ ] **Browser Website & Keyword Filter**: Add a domain (e.g., `reddit.com`) and a keyword (e.g., `breaking news`); open Chrome/Firefox/Brave, visit a safe site (allowed), then navigate to `reddit.com` or search for `breaking news` to confirm `BlockActivity` intercepts it.
- [ ] **Grayscale-Style Dim Overlay**: Create a Daily Time Limit with **Grayscale Dim** (or enable **Grayscale Dim After Daily Limit** in **Rules -> Smart**); hit the limit on a test app and confirm a cool desaturated dim overlay sits over that app (passes touches through) and disappears as soon as you return to Home.

### Phase 6 (Insights)
- [ ] **Home Insights Preview Card**: Confirm the **Today's Insights** card on Home shows today's screen time, blocked attempts, unlock passes, and the Phoenix **"d reborn"** badge, and tapping **Weekly Report & Insights** opens the Insights screen.
- [ ] **Dashboard & Top Apps Today**: With Usage Access granted, verify today's total screen time, blocked count, unlock count, and **Top Apps Today** bars match actual device usage (excluding Reborn & Launcher). Revoke Usage Access temporarily to confirm the graceful notice banner appears without crashing.
- [ ] **Weekly Report Canvas Charts**: Tap individual days under **Weekly Screen Time** and **Blocks vs. Unlock Passes** charts to verify day selection updates the pill/summary and displays resisted urges vs. unlock passes alongside recent unlock intent notes.
- [ ] **Gentle Phoenix Streak**: Enable a rule or resist a blocked app, verify **"1 day reborn"** activates gently with zero gems, leaderboards, or social elements.

### Phase 7 (Polish)
- [ ] **Dark / AMOLED / Light & Material You**: Open **About -> Appearance**, cycle through **Dark Navy**, **AMOLED Black** (true `#000000` canvas), **Warm Light**, and **System**, and toggle **Material You Dynamic Color** (Android 12+).
- [ ] **SAF Local JSON Backup & Restore**: Tap **Export Backup JSON** to save a `.json` file locally via the system file picker, clear/modify rules, then tap **Restore from JSON** and confirm rules, apps, and preferences return cleanly (and confirm restore is blocked if Strict Mode is locked).
- [ ] **Home Screen Widget**: Add the **Quick Block** widget to your Android launcher, tap **Start** to activate a 30m Quick Block for selected apps, and tap **Stop** to end it (or verify **Locked** while Strict Mode is active).
- [ ] **Quick Settings Tile**: Edit Android Quick Settings, add the **Quick Block** tile, tap to toggle Quick Block ON/OFF, and confirm live active/inactive state syncs with the app.
- [ ] **Accessibility, Large Text & Hindi (`hi`)**: Enable TalkBack, raise Android Font Size to **Largest**, and switch system language to **हिन्दी (Hindi)** to confirm labels wrap cleanly without clipping and touch targets stay comfortable.

