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
function Set-NotificationAccess([bool]$Allow) {
    if ($Api -ge 33) {
        $operation = if ($Allow) { 'grant' } else { 'revoke' }
        & $adb -s $Serial shell pm $operation $package android.permission.POST_NOTIFICATIONS
    } else {
        # Android 8's notification manager keeps a separate enabled/importance
        # state. Use its real master switch; changing only AppOps is not equivalent.
        & $adb -s $Serial shell am start -W -a android.settings.APP_NOTIFICATION_SETTINGS --es android.provider.extra.APP_PACKAGE $package
        if ($LASTEXITCODE -ne 0) { throw 'Could not open notification Settings.' }
        $mode = if ($Allow) { 'restored' } else { 'denied' }
        $verified = $false
        for ($attempt = 0; $attempt -lt 5; $attempt++) {
            $remoteFile = '/sdcard/luna-notifications-' + [Guid]::NewGuid().ToString('N') + '.xml'
            $dump = & $adb -s $Serial shell uiautomator dump $remoteFile
            if ($LASTEXITCODE -ne 0 -or ($dump -join ' ') -notmatch 'dumped to') { throw 'Notification Settings hierarchy unavailable.' }
            $hierarchyText = (& $adb -s $Serial shell cat $remoteFile) -join "`n"
            & $adb -s $Serial shell rm $remoteFile
            [xml]$hierarchy = $hierarchyText
            $toggle = $hierarchy.SelectSingleNode('//node[@resource-id="com.android.settings:id/switch_bar"]')
            if ($null -eq $toggle) { throw 'Android 8 notification master switch is missing.' }
            if (($toggle.checked -eq 'true') -eq $Allow) {
                $hierarchyText | Set-Content -LiteralPath (Join-Path $artifacts "notifications-$mode-system-ui.xml")
                $verified = $true
                break
            }
            if ($toggle.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { throw 'Notification switch touch bounds missing.' }
            $tapX = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
            $tapY = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
            & $adb -s $Serial shell input tap $tapX $tapY
            Start-Sleep -Seconds 1
        }
        if (-not $verified) { throw 'Notification master switch did not reach the requested state.' }
        & $adb -s $Serial shell screencap -p /sdcard/luna-notifications.png
        & $adb -s $Serial pull /sdcard/luna-notifications.png (Join-Path $artifacts "notifications-$mode-system-ui.png") | Out-Null
        & $adb -s $Serial shell input keyevent 4
    }
    if ($LASTEXITCODE -ne 0) { throw 'Could not change notification access.' }
}
function Invoke-NotificationTest([string]$Mode) {
    & $adb -s $Serial shell appops get $package POST_NOTIFICATION | Set-Content -LiteralPath (Join-Path $artifacts "notifications-$Mode-access.txt")
    $result = & $adb -s $Serial shell am instrument -w -r -e class dev.lordierclaw.lunaappalert.LunaNotificationTest -e notifications $Mode dev.lordierclaw.lunaappalert.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $result | Tee-Object -FilePath (Join-Path $artifacts "notifications-$Mode.txt")
    if ($result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' -or -not ($result -match 'OK \(')) { throw "Notification test failed: $Mode" }
}
try {
    Set-NotificationAccess $false
    Invoke-NotificationTest 'denied'
} finally { Set-NotificationAccess $true }
Invoke-NotificationTest 'restored'
New-Item -ItemType Directory -Path (Join-Path $artifacts 'screenshots') -Force | Out-Null
& $adb -s $Serial pull /sdcard/Android/data/dev.lordierclaw.lunaappalert/files/e2e/. (Join-Path $artifacts 'screenshots')
Write-Output "Real notification denial and restoration passed on API $Api."
