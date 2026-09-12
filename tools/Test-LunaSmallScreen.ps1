param(
    [ValidateSet(26,35,36,37)][int]$Api = 37,
    [string]$Serial = 'emulator-5554',
    [string]$Sdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
$deviceApi = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($deviceApi -ne "$Api") { throw "Requested API $Api but $Serial runs API $deviceApi." }
$artifacts = Join-Path (Split-Path $PSScriptRoot -Parent) "artifacts/e2e/api$Api/small-screen"
New-Item -ItemType Directory -Path $artifacts -Force | Out-Null
$originalSize = (& $adb -s $Serial shell wm size) -join "`n"
$originalDensity = (& $adb -s $Serial shell wm density) -join "`n"
$restoreSize = if ($originalSize -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$restoreDensity = if ($originalDensity -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
try {
    # 640x1136 px at density 320 is a 320x568 dp phone viewport.
    & $adb -s $Serial shell wm size 640x1136
    if ($LASTEXITCODE -ne 0) { throw 'Could not resize the emulator.' }
    & $adb -s $Serial shell wm density 320
    if ($LASTEXITCODE -ne 0) { throw 'Could not set the emulator density.' }
    Start-Sleep -Seconds 3
    & $adb -s $Serial shell wm size | Set-Content -LiteralPath (Join-Path $artifacts 'viewport.txt')
    & $adb -s $Serial shell wm density | Add-Content -LiteralPath (Join-Path $artifacts 'viewport.txt')
    foreach ($testClass in @('LunaVisualTest', 'LunaOverlayTest')) {
        $result = & $adb -s $Serial shell am instrument -w -r -e class "dev.lordierclaw.lunaappalert.$testClass" dev.lordierclaw.lunaappalert.test/androidx.test.runner.AndroidJUnitRunner 2>&1
        $result | Tee-Object -FilePath (Join-Path $artifacts "$testClass.txt")
        & $adb -s $Serial pull /sdcard/Android/data/dev.lordierclaw.lunaappalert/files/visual/. $artifacts 2>$null
        if ($result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' -or -not ($result -match 'OK \(')) {
            throw "Small-screen test failed: $testClass"
        }
    }
} finally {
    & $adb -s $Serial shell wm size $restoreSize
    & $adb -s $Serial shell wm density $restoreDensity
}
Write-Output "320x568 dp, large text, landscape, keyboard and real overlay passed on API $Api."
