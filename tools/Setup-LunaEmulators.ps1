param(
    [ValidateSet(26,35,37)][int[]]$Apis = @(37,35,26),
    [string]$Sdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$androidCli = Join-Path $Sdk 'cmdline-tools/latest/bin/android.exe'
$sdkmanager = Join-Path $Sdk 'cmdline-tools/latest/bin/sdkmanager.bat'
$avdmanager = Join-Path $Sdk 'cmdline-tools/latest/bin/avdmanager.bat'
if (-not (Test-Path -LiteralPath $avdmanager)) {
    throw 'Install Android SDK Command-line Tools (latest) using Android Studio SDK Manager first.'
}
foreach ($api in $Apis) {
    $platform = if ($api -eq 37) { 'android-37.0' } else { "android-$api" }
    $image = "system-images;$platform;google_apis;x86_64"
    $imageDirectory = Join-Path $Sdk ($image.Replace(';','/'))
    if (-not (Test-Path -LiteralPath (Join-Path $imageDirectory 'package.xml'))) {
        if (Test-Path -LiteralPath $androidCli) {
            & $androidCli --sdk $Sdk sdk install $image.Replace(';','/')
        } else {
            & $sdkmanager "--sdk_root=$Sdk" $image
        }
        # Android CLI 1.0 may return exit code 1 after a successful extraction.
        # The installed package manifest plus disk image are the reliable completion checks.
        if (-not (Test-Path -LiteralPath (Join-Path $imageDirectory 'package.xml')) -or
            -not (Test-Path -LiteralPath (Join-Path $imageDirectory 'system.img'))) {
            throw "Image installation failed: $image"
        }
    }
    $name = "Luna_E2E_API$api"
    if (-not ((& (Join-Path $Sdk 'emulator/emulator.exe') -list-avds) -contains $name)) {
        'no' | & $avdmanager create avd --name $name --package $image --device pixel_2
        if ($LASTEXITCODE -ne 0) { throw "AVD creation failed: $name" }
    }
    Write-Output "Ready: $name ($image)"
}
