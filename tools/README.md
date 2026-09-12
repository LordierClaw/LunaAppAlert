# Android E2E tooling

Run these PowerShell commands from the project directory. `ANDROID_HOME` should point to the SDK configured in Android Studio. Install **Android SDK Command-line Tools (latest)** through SDK Manager if it is missing.

Android 16/API 36 is the delivery priority. Use `Setup-LunaEmulators.ps1 -Apis 36`, then pass `-Api 36` to the start/test scripts. The same tooling also supports API 26, 35 and 37 for the already completed broader verification.

```powershell
./tools/Setup-LunaEmulators.ps1
./tools/Start-LunaEmulator.ps1 -Api 37
./tools/Test-LunaE2E.ps1 -Api 37
./tools/Test-LunaE2E.ps1 -Api 37 -SkipBuild -KeepData -TestClass dev.lordierclaw.lunaappalert.LunaConfigUITest
./tools/Test-LunaE2E.ps1 -Api 37 -SkipBuild -KeepData -TestClass dev.lordierclaw.lunaappalert.LunaRuleCrudTest
./tools/Test-LunaE2E.ps1 -Api 37 -SkipBuild -KeepData -TestClass dev.lordierclaw.lunaappalert.data.RepositoryTest
./tools/Test-LunaE2E.ps1 -Api 37 -SkipBuild -KeepData -TestClass dev.lordierclaw.lunaappalert.LunaVisualTest
./tools/Test-LunaRecovery.ps1 -Api 37
./tools/Test-LunaLifecycle.ps1 -Api 37
./tools/Test-LunaNotifications.ps1 -Api 37
./tools/Test-LunaE2E.ps1 -Api 37 -SkipBuild -KeepData -TestClass dev.lordierclaw.lunaappalert.LunaOverlayTest
./tools/Test-LunaE2E.ps1 -Api 37 -SkipBuild -KeepData -TestClass dev.lordierclaw.lunaappalert.UsageObserverTest
./tools/Test-LunaSmallScreen.ps1 -Api 37
```

The main journey clears App Alert's test configuration and installs the two ordinary fixture applications. It uses the real UI to grant system access and create groups/apps/rules. It then switches to external apps, verifies launch precedence and same-package activity transitions, waits for an actual one-minute threshold and one-minute repeat, and checks notification/overlay delivery, Continue, Exit App, permission revocation, and saved configuration. Allow several minutes for each main run.

The configuration test requires the main journey's data. It exercises group/app switches, move and Undo, duplicate validation, and group deletion while retaining apps and their own rules. It restores the baseline configuration for recovery testing. The repository test uses isolated in-memory data and separate settings files.

The rule CRUD test renames a group, edits a custom message, deletes and undoes a rule through the UI, and restores the original configuration. The lifecycle script locks/unlocks the emulator and uninstalls/reinstalls only fixture A, checking retained configuration and actual restored alerts.

The notification script revokes/restores the actual runtime permission on API 33+ with the host ADB commands recommended by Android. On API 26 it operates the master notification switch in Android Settings, since an AppOp change alone does not update the notification manager's enabled state. The change happens outside instrumentation because permission revocation can terminate the target process. Each invocation checks the real permission state, notification delivery, unchanged app-rule precedence, and overlay delivery when notifications are denied.

After every debug check on an AVD is complete, run `./tools/Build-SignedApk.ps1` once and `./tools/Test-LunaRelease.ps1 -Api 37` to exercise the exact signed distributable. Reuse that same APK on the other API levels. This check uninstalls App Alert's debug installation because its certificate differs. It signs only the separate instrumentation APK with the release certificate; the release APK itself stays unchanged and is not debuggable. Reports are saved under `artifacts/release-e2e/`.

To verify layout directly on the installed release without replacing it with debug, run the following after the release journey, then repeat for the other API levels:

```powershell
adb -s emulator-5554 shell am instrument -w -r -e class dev.lordierclaw.lunaappalert.LunaVisualTest dev.lordierclaw.lunaappalert.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class dev.lordierclaw.lunaappalert.LunaOverlayTest dev.lordierclaw.lunaappalert.test/androidx.test.runner.AndroidJUnitRunner
./tools/Test-LunaSmallScreen.ps1 -Api 37
```

The recovery script performs host-side force-stop, a real process kill using the debug app's own UID, and reboot. It records foreground-service state, changed process IDs, and an actual external-app overlay before running instrumentation, since instrumentation itself restarts its target process. Force-stop remains stopped until the app is reopened; ordinary process death and reboot exercise automatic service recovery.

`UsageObserverTest` locks the actual emulator for 20 seconds and constructs a new observer from Android's event source. It verifies that the reconstructed daily total excludes the locked interval, including Android 8 where historical screen-off events are unavailable. The small-screen script temporarily resizes the emulator to 320x568 dp, repeats visual/keyboard and real overlay tests at 200% text, and restores the original viewport in `finally`.

Only one emulator should run at a time. After finishing an API, stop it and start the next:

```powershell
adb -s emulator-5554 emu kill
./tools/Start-LunaEmulator.ps1 -Api 35
# Repeat the test commands with -Api 35, then with -Api 26.
```

AVDs are named `Luna_E2E_API26`, `Luna_E2E_API35`, and `Luna_E2E_API37`. Emulators run without a visible window, using WHPX and software rendering. Logs, screenshots, UI hierarchy XML, and instrumentation reports are written to ignored `artifacts/e2e/api<level>/` directories. Test definitions alone are not proof of execution; consult the generated instrumentation reports for actual results.
