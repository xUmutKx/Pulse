# Compila LockArt.apk (modulo LSPosed + app de ajustes en Compose/Material 3) con Gradle y lo deja en build\LockArt.apk.
# Requiere keystore.properties con la clave de firma (ver app/build.gradle.kts); sin él se firma con la clave de depuración.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Push-Location $root
try {
    & "$root\gradlew.bat" --console=plain assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Fallo Gradle (codigo $LASTEXITCODE)" }
} finally {
    Pop-Location
}

$out = Join-Path $root 'build'
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item -Force "$root\app\build\outputs\apk\release\app-release.apk" "$out\LockArt.apk"
Write-Host "APK listo: $out\LockArt.apk"
