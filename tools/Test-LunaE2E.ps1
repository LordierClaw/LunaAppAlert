param(
    [ValidateSet(26,35,36,37)][int]$Api = 37,
    [string]$Serial = 'emulator-5554',
    [string]$TestClass = 'dev.lordierclaw.lunaappalert.LunaE2ETest',
    [switch]$SkipBuild,
    [switch]$KeepData,
    [string]$Sdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$project = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
$artifacts = Join-Path $project "artifacts/e2e/api$Api"
$reportName = if ($TestClass -eq 'dev.lordierclaw.lunaappalert.LunaE2ETest') { 'instrumentation' } else { 'instrumentation-' + ($TestClass.Split('.')[-1]) }
New-Item -ItemType Directory -Path $artifacts -Force | Out-Null
Push-Location $project
try {
    if (-not $SkipBuild) {
        & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :test-target:assembleAlphaDebug :test-target:assembleBetaDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'E2E APK build failed.' }
    }
    if ((& $adb -s $Serial shell getprop sys.boot_completed).Trim() -ne '1') { throw "$Serial is not booted." }
    $deviceApi = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
    if ($deviceApi -ne "$Api") { throw "Requested API $Api but $Serial runs API $deviceApi." }
    $apks = @(
        'app/build/outputs/apk/debug/app-debug.apk',
        'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
        'test-target/build/outputs/apk/alpha/debug/test-target-alpha-debug.apk',
        'test-target/build/outputs/apk/beta/debug/test-target-beta-debug.apk'
    )
    foreach ($apk in $apks) {
        & $adb -s $Serial install -r -t (Join-Path $project $apk)
        if ($LASTEXITCODE -ne 0) { throw "Install failed: $apk" }
    }
    if (-not $KeepData) {
        & $adb -s $Serial shell pm clear dev.lordierclaw.lunaappalert
        & $adb -s $Serial shell appops set dev.lordierclaw.lunaappalert GET_USAGE_STATS default
        & $adb -s $Serial shell appops set dev.lordierclaw.lunaappalert SYSTEM_ALERT_WINDOW default
        & $adb -s $Serial shell appops set dev.lordierclaw.lunaappalert POST_NOTIFICATION default
    }
    & $adb -s $Serial logcat -c
    $result = & $adb -s $Serial shell am instrument -w -r -e class $TestClass dev.lordierclaw.lunaappalert.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $result | Tee-Object -FilePath (Join-Path $artifacts "$reportName.txt")
    & $adb -s $Serial logcat -d -v threadtime | Set-Content -LiteralPath (Join-Path $artifacts "$reportName-logcat.txt")
    New-Item -ItemType Directory -Path (Join-Path $artifacts 'screenshots') -Force | Out-Null
    & $adb -s $Serial pull /sdcard/Android/data/dev.lordierclaw.lunaappalert/files/e2e/. (Join-Path $artifacts 'screenshots') 2>$null
    if ($TestClass.EndsWith('.LunaVisualTest') -or $TestClass.EndsWith('.LunaOverlayTest')) {
        New-Item -ItemType Directory -Path (Join-Path $artifacts 'visual') -Force | Out-Null
        & $adb -s $Serial pull /sdcard/Android/data/dev.lordierclaw.lunaappalert/files/visual/. (Join-Path $artifacts 'visual') 2>$null
    }
    if ($result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' -or -not ($result -match 'OK \(')) {
        throw "E2E failed. See $artifacts/$reportName.txt."
    }
    Write-Output "E2E passed on API $Api. Evidence: $artifacts"
} finally {
    Pop-Location
}
