param(
    [ValidateSet(26,35,37)][int]$Api = 37,
    [string]$Serial = 'emulator-5554',
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.android/lunaappalert-signing')
)
$ErrorActionPreference = 'Stop'
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
if ((& $adb -s $Serial shell getprop sys.boot_completed).Trim() -ne '1') { throw "$Serial is not booted." }
$deviceApi = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($deviceApi -ne "$Api") { throw "Requested API $Api but $Serial runs API $deviceApi." }
$projectRoot = Split-Path $PSScriptRoot -Parent
$release = Join-Path $projectRoot 'artifacts/release/LunaAppAlert-1.0.apk'
$testApk = Join-Path $projectRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
$artifacts = Join-Path $projectRoot "artifacts/release-e2e/api$Api"
New-Item -ItemType Directory -Path $artifacts -Force | Out-Null
$signedTest = Join-Path $artifacts 'release-compatible-tests.apk'
$buildTools = Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat') } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not (Test-Path -LiteralPath $release) -or -not (Test-Path -LiteralPath $testApk)) { throw 'Build and sign release plus AndroidTest APK first.' }
# Instrumentation must share the target certificate. This signs only the test APK;
# the exact distributable release APK is installed unchanged and keeps debuggable=false.
try {
    $credential = Import-Clixml -LiteralPath (Join-Path $SigningDirectory 'credential.xml')
    $env:LUNA_SIGNING_PASSWORD = $credential.GetNetworkCredential().Password
    & (Join-Path $buildTools.FullName 'apksigner.bat') sign --ks (Join-Path $SigningDirectory 'release.jks') --ks-key-alias lunaappalert --ks-pass env:LUNA_SIGNING_PASSWORD --key-pass env:LUNA_SIGNING_PASSWORD --out $signedTest $testApk
    if ($LASTEXITCODE -ne 0) { throw 'Could not sign the instrumentation APK.' }
} finally { Remove-Item Env:\LUNA_SIGNING_PASSWORD -ErrorAction SilentlyContinue }

# Run last on this AVD: the debug/release certificate change requires uninstalling
# App Alert's previous test installation and therefore removes that test configuration.
& $adb -s $Serial uninstall dev.lordierclaw.lunaappalert.test
& $adb -s $Serial uninstall dev.lordierclaw.lunaappalert
foreach ($apk in @($release, $signedTest,
    (Join-Path $projectRoot 'test-target/build/outputs/apk/alpha/debug/test-target-alpha-debug.apk'),
    (Join-Path $projectRoot 'test-target/build/outputs/apk/beta/debug/test-target-beta-debug.apk'))) {
    & $adb -s $Serial install -r -t $apk
    if ($LASTEXITCODE -ne 0) { throw "Could not install $apk" }
}
& $adb -s $Serial shell dumpsys package dev.lordierclaw.lunaappalert | Set-Content -LiteralPath (Join-Path $artifacts 'installed-release-package.txt')
& $adb -s $Serial logcat -c
$result = & $adb -s $Serial shell am instrument -w -r -e class dev.lordierclaw.lunaappalert.LunaE2ETest dev.lordierclaw.lunaappalert.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$result | Tee-Object -FilePath (Join-Path $artifacts 'instrumentation.txt')
& $adb -s $Serial logcat -d -v threadtime | Set-Content -LiteralPath (Join-Path $artifacts 'logcat.txt')
& $adb -s $Serial pull /sdcard/Android/data/dev.lordierclaw.lunaappalert/files/e2e (Join-Path $artifacts 'screenshots')
if ($result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' -or -not ($result -match 'OK \(')) { throw "Signed release E2E failed. See $artifacts." }
Get-FileHash -LiteralPath $release -Algorithm SHA256 | Format-List | Out-String | Set-Content -LiteralPath (Join-Path $artifacts 'tested-apk-sha256.txt')
Write-Output "The unchanged signed release APK passed the real timed E2E journey on API $Api."
