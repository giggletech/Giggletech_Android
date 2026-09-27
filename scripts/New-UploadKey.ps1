param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$properties = Join-Path $project 'keystore.properties'
if (Test-Path -LiteralPath $properties) { throw 'keystore.properties already exists. Reuse the existing upload key.' }
if (-not $JavaHome) { $JavaHome = 'C:\Program Files\Android\Android Studio\jbr' }
$keytool = Join-Path $JavaHome 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'Set JAVA_HOME to a JDK 17 or 21 installation.' }
$keyDirectory = Join-Path $env:USERPROFILE '.giggletech\android-signing'
New-Item -ItemType Directory -Force -Path $keyDirectory | Out-Null
$keystore = Join-Path $keyDirectory 'upload.jks'
if (Test-Path -LiteralPath $keystore) { throw 'An upload key already exists. Restore its keystore.properties instead of replacing it.' }
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$password = [Convert]::ToBase64String($bytes)
$env:GIGGLETECH_KEY_PASSWORD = $password
try {
    & $keytool -genkeypair -keystore $keystore -storetype JKS -alias upload -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=GiggleTech Android Upload' -storepass:env GIGGLETECH_KEY_PASSWORD -keypass:env GIGGLETECH_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Upload key generation failed.' }
    $escapedPath = $keystore.Replace('\', '/').Replace(':', '\:')
    $content = "storeFile=$escapedPath`nstorePassword=$password`nkeyAlias=upload`nkeyPassword=$password`n"
    [System.IO.File]::WriteAllText($properties, $content)
    [System.IO.File]::WriteAllText((Join-Path $keyDirectory 'keystore.properties'), $content)
    & $keytool -exportcert -rfc -keystore $keystore -alias upload -storepass:env GIGGLETECH_KEY_PASSWORD -file (Join-Path $keyDirectory 'upload_certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
    Write-Output "Upload key created in $keyDirectory. Back up this directory securely. Passwords were not printed."
} finally { Remove-Item Env:\GIGGLETECH_KEY_PASSWORD -ErrorAction SilentlyContinue; $password = $null }
