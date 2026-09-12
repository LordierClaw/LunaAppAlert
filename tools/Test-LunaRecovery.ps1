param(
    [ValidateSet(26,35,36,37)][int]$Api = 37,
    [string]$Serial = 'emulator-5554',
    [string]$Sdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
if ((& $adb -s $Serial shell getprop sys.boot_completed).Trim() -ne '1') { throw "$Serial is not booted." }
$deviceApi = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($deviceApi -ne "$Api") { throw "Requested API $Api but $Serial runs API $deviceApi." }
$artifacts = Join-Path (Split-Path $PSScriptRoot -Parent) "artifacts/e2e/api$Api"
New-Item -ItemType Directory -Path $artifacts -Force | Out-Null
$package = 'dev.lordierclaw.lunaappalert'

function Read-FreshHierarchy {
    $remoteFile = '/sdcard/luna-' + [Guid]::NewGuid().ToString('N') + '.xml'
    $dumpResult = & $adb -s $Serial shell uiautomator dump $remoteFile
    if ($LASTEXITCODE -ne 0 -or ($dumpResult -join ' ') -notmatch 'dumped to') { throw 'Could not obtain a fresh UI hierarchy.' }
    $hierarchyText = (& $adb -s $Serial shell cat $remoteFile) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $hierarchyText -notmatch '<hierarchy') { throw 'Fresh UI hierarchy is invalid.' }
    & $adb -s $Serial shell rm $remoteFile
    return $hierarchyText
}

function Invoke-RecoveryTest([string]$Mode) {
    $result = & $adb -s $Serial shell am instrument -w -r -e class dev.lordierclaw.lunaappalert.LunaRecoveryTest -e recovery $Mode dev.lordierclaw.lunaappalert.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $result | Tee-Object -FilePath (Join-Path $artifacts "recovery-$Mode.txt")
    if ($result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' -or -not ($result -match 'OK \(')) {
        & $adb -s $Serial logcat -d -v threadtime | Set-Content -LiteralPath (Join-Path $artifacts "recovery-$Mode-logcat.txt")
        throw "Recovery test failed: $Mode"
    }
}

function Wait-MonitorService([string]$Stage) {
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        $service = (& $adb -s $Serial shell dumpsys activity services $package) -join "`n"
        if ($service -match 'MonitoringService' -and $service -match 'isForeground=true') {
            $service | Set-Content -LiteralPath (Join-Path $artifacts "recovery-$Stage-service.txt")
            Write-Output "Foreground service verified externally: $Stage"
            return
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    $service | Set-Content -LiteralPath (Join-Path $artifacts "recovery-$Stage-service.txt")
    throw "No foreground MonitoringService after $Stage"
}

function Assert-ExternalOverlay([string]$Stage) {
    & $adb -s $Serial shell input keyevent 3
    Start-Sleep -Seconds 2
    & $adb -s $Serial shell am start -W -n dev.lordierclaw.fixture.beta/dev.lordierclaw.fixture.MainActivity
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    $found = $false
    do {
        $hierarchyText = Read-FreshHierarchy
        if ($hierarchyText -match 'E2E nhóm mở ứng dụng') { $found = $true; break }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    $hierarchyText | Set-Content -LiteralPath (Join-Path $artifacts "recovery-$Stage-overlay.xml")
    & $adb -s $Serial shell screencap -p /sdcard/luna-recovery.png
    & $adb -s $Serial pull /sdcard/luna-recovery.png (Join-Path $artifacts "recovery-$Stage-overlay.png") | Out-Null
    if (-not $found) { throw "No actual group launch overlay after $Stage" }
    [xml]$hierarchy = $hierarchyText
    $continue = $hierarchy.SelectSingleNode('//node[@content-desc="overlay_continue"]')
    if ($null -eq $continue -or $continue.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { throw 'Overlay Continue bounds missing' }
    $tapX = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $tapY = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    & $adb -s $Serial shell input tap $tapX $tapY
    Write-Output "Actual external launch overlay verified: $Stage"
}

# Force-stop is deliberately a stopped state; only a user opening the app resumes it.
& $adb -s $Serial shell am force-stop $package
$stopped = (& $adb -s $Serial shell dumpsys activity services $package) -join "`n"
$stopped | Set-Content -LiteralPath (Join-Path $artifacts 'recovery-force-stop-service.txt')
if ($stopped -match 'isForeground=true') { throw 'Foreground service survived force-stop unexpectedly.' }
& $adb -s $Serial shell input keyevent 3
& $adb -s $Serial shell am start -W -n dev.lordierclaw.fixture.beta/dev.lordierclaw.fixture.MainActivity
Start-Sleep -Seconds 4
$forceStopHierarchy = Read-FreshHierarchy
$forceStopHierarchy | Set-Content -LiteralPath (Join-Path $artifacts 'recovery-force-stop-external.xml')
if ($forceStopHierarchy -match 'overlay_alert|E2E nhóm mở ứng dụng') { throw 'A force-stopped monitor showed an external alert.' }
$afterExternalLaunch = (& $adb -s $Serial shell dumpsys activity services $package) -join "`n"
if ($afterExternalLaunch -match 'isForeground=true') { throw 'Launching a target resumed the force-stopped monitor.' }
Invoke-RecoveryTest 'force_stop'

# Verify sticky process recovery outside instrumentation, which itself restarts the target.
& $adb -s $Serial shell am start -W -n "$package/.MainActivity"
Wait-MonitorService 'before-process-death'
& $adb -s $Serial shell input keyevent 3
$oldProcess = (& $adb -s $Serial shell pidof $package).Trim()
if ($oldProcess -notmatch '^\d+$') { throw "Unexpected target process identifier: $oldProcess" }
& $adb -s $Serial shell run-as $package kill -9 $oldProcess
if ($LASTEXITCODE -ne 0) { throw 'Could not terminate the debug app process using its own UID.' }
Start-Sleep -Seconds 2
Wait-MonitorService 'process-death'
$newProcess = (& $adb -s $Serial shell pidof $package).Trim()
if ($oldProcess -eq $newProcess) { throw 'Process-death verification did not observe a new process.' }
"Previous PID: $oldProcess`nRestarted PID: $newProcess" | Set-Content -LiteralPath (Join-Path $artifacts 'recovery-process-death-pids.txt')
Assert-ExternalOverlay 'process-death'

& $adb -s $Serial reboot
$deadline = [DateTime]::UtcNow.AddMinutes(5)
do {
    Start-Sleep -Seconds 3
    $booted = (& $adb -s $Serial shell getprop sys.boot_completed 2>$null)
} while ($booted -ne '1' -and [DateTime]::UtcNow -lt $deadline)
if ($booted -ne '1') { throw 'Device did not reboot within five minutes.' }
& $adb -s $Serial shell input keyevent 82
& $adb -s $Serial shell settings put system screen_off_timeout 1800000
Wait-MonitorService 'reboot'
Assert-ExternalOverlay 'reboot'
Invoke-RecoveryTest 'host_verified'
New-Item -ItemType Directory -Path (Join-Path $artifacts 'screenshots') -Force | Out-Null
& $adb -s $Serial pull /sdcard/Android/data/dev.lordierclaw.lunaappalert/files/e2e/. (Join-Path $artifacts 'screenshots') 2>$null
Write-Output "Force-stop, process-death, and reboot recovery passed on API $Api. Evidence: $artifacts"
