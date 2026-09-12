param(
    [ValidateSet(26,35,37)][int]$Api = 37,
    [string]$Serial = 'emulator-5554',
    [string]$Sdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
if ((& $adb -s $Serial shell getprop sys.boot_completed).Trim() -ne '1') { throw "$Serial is not booted." }
$deviceApi = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($deviceApi -ne "$Api") { throw "Requested API $Api but $Serial runs API $deviceApi." }
$projectRoot = Split-Path $PSScriptRoot -Parent
$artifacts = Join-Path $projectRoot "artifacts/e2e/api$Api"
$fixtureApk = Join-Path $projectRoot 'test-target/build/outputs/apk/alpha/debug/test-target-alpha-debug.apk'
if (-not (Test-Path -LiteralPath $fixtureApk)) { throw 'Build the alpha test fixture before running lifecycle verification.' }
New-Item -ItemType Directory -Path $artifacts -Force | Out-Null

function Invoke-LifecycleTest([string]$Mode) {
    $result = & $adb -s $Serial shell am instrument -w -r -e class dev.lordierclaw.lunaappalert.LunaLifecycleTest -e lifecycle $Mode dev.lordierclaw.lunaappalert.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $result | Tee-Object -FilePath (Join-Path $artifacts "lifecycle-$Mode.txt")
    if ($result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' -or -not ($result -match 'OK \(')) {
        & $adb -s $Serial logcat -d -v threadtime | Set-Content -LiteralPath (Join-Path $artifacts "lifecycle-$Mode-logcat.txt")
        throw "Lifecycle test failed: $Mode"
    }
}

Invoke-LifecycleTest 'lock'
try {
    $uninstallResult = & $adb -s $Serial uninstall dev.lordierclaw.fixture.alpha
    if ($LASTEXITCODE -ne 0 -or $uninstallResult -notmatch 'Success') { throw 'Could not uninstall fixture A.' }
    Invoke-LifecycleTest 'removed'
} finally {
    & $adb -s $Serial install -r $fixtureApk
    if ($LASTEXITCODE -ne 0) { throw 'Could not reinstall fixture A.' }
}
Invoke-LifecycleTest 'reinstalled'
New-Item -ItemType Directory -Path (Join-Path $artifacts 'screenshots') -Force | Out-Null
& $adb -s $Serial pull /sdcard/Android/data/dev.lordierclaw.lunaappalert/files/e2e/. (Join-Path $artifacts 'screenshots')
Write-Output "Screen lock, target uninstall and target reinstall passed on API $Api. Evidence: $artifacts"
