param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.android/lunaappalert-signing'),
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$signingPath = [IO.Path]::GetFullPath($SigningDirectory)
if ($signingPath.StartsWith([IO.Path]::GetFullPath($projectRoot), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The signing directory must be outside this repository.'
}
$keyFile = Join-Path $signingPath 'release.jks'
$credentialFile = Join-Path $signingPath 'credential.xml'
$keytool = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr/bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { $keytool = (Get-Command keytool).Source }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat') } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Android build-tools are missing.' }
New-Item -ItemType Directory -Force -Path $signingPath | Out-Null
if (-not (Test-Path -LiteralPath $keyFile)) {
    if (Test-Path -LiteralPath $credentialFile) { throw 'Credential exists without a key; restore the existing keystore before continuing.' }
    $randomBytes = New-Object byte[] 48
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $randomGenerator.GetBytes($randomBytes) } finally { $randomGenerator.Dispose() }
    $secret = [Convert]::ToBase64String($randomBytes)
    $credential = [Management.Automation.PSCredential]::new('lunaappalert', (ConvertTo-SecureString $secret -AsPlainText -Force))
    # DPAPI binds this credential to this Windows account on this computer.
    $credential | Export-Clixml -LiteralPath $credentialFile
    $env:LUNA_SIGNING_PASSWORD = $secret
    try {
        & $keytool -genkeypair -keystore $keyFile -storetype JKS -alias lunaappalert -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Nguyen Nam Hai, OU=App Alert, O=LordierClaw' -storepass:env LUNA_SIGNING_PASSWORD -keypass:env LUNA_SIGNING_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Key creation failed.' }
    } finally { Remove-Item Env:\LUNA_SIGNING_PASSWORD -ErrorAction SilentlyContinue; $secret = $null }
}
if (-not (Test-Path -LiteralPath $credentialFile)) { throw 'Signing credential is missing; restore it before signing.' }
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & .\gradlew.bat :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    }
    $artifacts = Join-Path $projectRoot 'artifacts/release'
    New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
    $unsigned = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release-unsigned.apk'
    $aligned = Join-Path $artifacts 'app-aligned.apk'
    $output = Join-Path $artifacts 'LunaAppAlert-1.0.apk'
    & (Join-Path $buildTools.FullName 'zipalign.exe') -f -p 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
    $credential = Import-Clixml -LiteralPath $credentialFile
    $env:LUNA_SIGNING_PASSWORD = $credential.GetNetworkCredential().Password
    & (Join-Path $buildTools.FullName 'apksigner.bat') sign --ks $keyFile --ks-key-alias lunaappalert --ks-pass env:LUNA_SIGNING_PASSWORD --key-pass env:LUNA_SIGNING_PASSWORD --out $output $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose $output
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $hash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  LunaAppAlert-1.0.apk" | Set-Content -LiteralPath (Join-Path $artifacts 'SHA256SUMS.txt') -Encoding utf8
    Write-Output "Signed APK: $output"
    Write-Output "Signing key (outside Git): $keyFile"
} finally {
    Remove-Item Env:\LUNA_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
