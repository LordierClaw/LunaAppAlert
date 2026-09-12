param(
    [ValidateSet(26,35,37)][int]$Api = 37,
    [int]$Port = 5554,
    [string]$Sdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
$emulator = Join-Path $Sdk 'emulator/emulator.exe'
$name = "Luna_E2E_API$Api"
$serial = "emulator-$Port"
$existingDevices = & $adb devices
if ($existingDevices -match "$serial\s+device") {
    $connectedApi = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
    if ($connectedApi -ne "$Api") { throw "$serial still runs API $connectedApi. Wait for it to stop before starting API $Api." }
    Write-Output "$serial is already connected."
    exit 0
}
if ($existingDevices -match 'emulator-\d+\s+(device|offline)') {
    throw 'An emulator is already running. Stop it before starting another E2E AVD.'
}
if (-not ((& $emulator -list-avds) -contains $name)) {
    throw "Missing $name. Run tools/Setup-LunaEmulators.ps1 first."
}
$artifactDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) "artifacts/e2e/api$Api"
New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
$arguments = @('-avd', $name, '-port', $Port, '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'swiftshader_indirect', '-memory', '2048', '-cores', '2', '-no-metrics')
$process = Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $artifactDirectory 'emulator.log') -RedirectStandardError (Join-Path $artifactDirectory 'emulator-errors.log')
$deadline = [DateTime]::UtcNow.AddMinutes(5)
while ([DateTime]::UtcNow -lt $deadline) {
    if ($process.HasExited) { throw "Emulator exited: $(Get-Content (Join-Path $artifactDirectory 'emulator-errors.log') -Tail 15)" }
    # An older guest can boot while its reused ADB transport is stuck. Bound the
    # individual probe as well as the overall deadline, and reconnect that transport.
    $probeOutput = Join-Path $artifactDirectory 'boot-probe.txt'
    $probe = Start-Process -FilePath $adb -ArgumentList @('-s', $serial, 'shell', 'getprop', 'sys.boot_completed') -WindowStyle Hidden -PassThru -RedirectStandardOutput $probeOutput -RedirectStandardError (Join-Path $artifactDirectory 'boot-probe-errors.txt')
    $booted = ''
    if ($probe.WaitForExit(5000)) {
        $probeText = Get-Content -LiteralPath $probeOutput -Raw
        if ($null -ne $probeText) { $booted = $probeText.Trim() }
    } else {
        # Stop only this timed-out client, leaving the emulator and app state intact.
        Stop-Process -Id $probe.Id -Force -ErrorAction SilentlyContinue
        $reconnect = Start-Process -FilePath $adb -ArgumentList @('-s', $serial, 'reconnect') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $artifactDirectory 'adb-reconnect.txt') -RedirectStandardError (Join-Path $artifactDirectory 'adb-reconnect-errors.txt')
        if (-not $reconnect.WaitForExit(5000)) { Stop-Process -Id $reconnect.Id -Force -ErrorAction SilentlyContinue }
    }
    if ($booted -eq '1') {
        & $adb -s $serial shell settings put global window_animation_scale 0
        & $adb -s $serial shell settings put global transition_animation_scale 0
        & $adb -s $serial shell settings put global animator_duration_scale 0
        & $adb -s $serial shell settings put system screen_off_timeout 1800000
        & $adb -s $serial shell input keyevent 82
        Write-Output "$serial booted on API $Api; logs in $artifactDirectory"
        exit 0
    }
    Start-Sleep -Seconds 3
}
throw "Emulator did not boot within five minutes. Inspect $artifactDirectory."
