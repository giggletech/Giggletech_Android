param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) { $JavaHome = 'C:\Program Files\Android\Android Studio\jbr' }
$env:JAVA_HOME = $JavaHome
if (-not (Test-Path -LiteralPath (Join-Path $project 'keystore.properties'))) { throw 'Run scripts/New-UploadKey.ps1 once, or restore your existing upload signing configuration.' }
Push-Location $project
try {
    & .\gradlew.bat testDebugUnitTest lintRelease assembleRelease bundleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release verification/build failed.' }
    New-Item -ItemType Directory -Force dist | Out-Null
    Copy-Item -LiteralPath app\build\outputs\bundle\release\app-release.aab -Destination dist\GiggleTech-1.0.2.aab
    Copy-Item -LiteralPath app\build\outputs\apk\release\app-release.apk -Destination dist\GiggleTech-1.0.2.apk
    Copy-Item -LiteralPath app\build\outputs\mapping\release\mapping.txt -Destination dist\mapping.txt
    Get-FileHash dist\GiggleTech-1.0.2.aab,dist\GiggleTech-1.0.2.apk -Algorithm SHA256 | Format-List | Out-File dist\SHA256.txt
    Write-Output 'Signed release bundle and APK are in dist/.'
} finally { Pop-Location }
