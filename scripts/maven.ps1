param([string[]]$MavenArguments = @('test', 'package'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
$maven = Join-Path $env:USERPROFILE '.maven\maven-3.9.16'
if ($env:RECONOCIMIENTO_JAVA_HOME) { $jdk = $env:RECONOCIMIENTO_JAVA_HOME }
if ($env:RECONOCIMIENTO_MAVEN_HOME) { $maven = $env:RECONOCIMIENTO_MAVEN_HOME }
Push-Location (Join-Path $projectRoot 'backend')
try {
    & (Join-Path $jdk 'bin\java.exe') "-Dmaven.home=$maven" "-Dclassworlds.conf=$maven\bin\m2.conf" "-Dmaven.multiModuleProjectDirectory=$projectRoot\backend" '-cp' "$maven\boot\plexus-classworlds-2.11.0.jar" 'org.codehaus.plexus.classworlds.launcher.Launcher' @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Maven termino con codigo $LASTEXITCODE." }
} finally { Pop-Location }
