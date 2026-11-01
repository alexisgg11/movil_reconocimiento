param([switch]$SkipAdb)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$localRoot = Join-Path $projectRoot '.local'
$backendRoot = Join-Path $projectRoot 'backend'
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
if ($env:RECONOCIMIENTO_PG_BIN) { $pgBin = $env:RECONOCIMIENTO_PG_BIN }
if ($env:RECONOCIMIENTO_JAVA_HOME) { $jdk = $env:RECONOCIMIENTO_JAVA_HOME }
$java = Join-Path $jdk 'bin\java.exe'
New-Item -ItemType Directory -Path $localRoot -Force | Out-Null
$shortTemp = Join-Path $localRoot 'tmp'
New-Item -ItemType Directory -Path $shortTemp -Force | Out-Null
$previousTemp = $env:TEMP; $previousTmp = $env:TMP
$env:TEMP = $shortTemp; $env:TMP = $shortTemp
function Hash-Text([string]$value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($value)))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
}
function Source-Hash {
    $entries = @(Get-Item (Join-Path $backendRoot 'pom.xml')) + @(Get-ChildItem (Join-Path $backendRoot 'src') -Recurse -File | Sort-Object FullName)
    $hashes = foreach ($entry in $entries) {
        $sha = [Security.Cryptography.SHA256]::Create()
        $stream = [IO.File]::OpenRead($entry.FullName)
        try { $entry.FullName.Substring($backendRoot.Length) + ':' + [BitConverter]::ToString($sha.ComputeHash($stream)) } finally { $stream.Dispose(); $sha.Dispose() }
    }
    return Hash-Text ($hashes -join "`n")
}
function Health {
    try { return Invoke-RestMethod -Uri 'http://127.0.0.1:8082/api/health' -TimeoutSec 2 } catch { return $null }
}
function Hidden-Process([string]$exe, [string]$arguments, [string]$directory) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $exe; $info.Arguments = $arguments; $info.WorkingDirectory = $directory
    $info.UseShellExecute = $true; $info.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    return [Diagnostics.Process]::Start($info)
}
function Psql([string]$database, [string]$sql) {
    $result = & (Join-Path $pgBin 'psql.exe') '-w' '-h' $dbHost '-p' "$port" '-U' $config.DB_USER '-d' $database '-v' 'ON_ERROR_STOP=1' '-At' '-c' $sql
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo conectar a PostgreSQL. Revisa DB_URL, DB_USER y DB_PASSWORD en backend/.env.' }
    return $result
}
$mutex = New-Object Threading.Mutex($false, ('Local\Reconocimiento_' + (Hash-Text $projectRoot).Substring(0, 24)))
$locked = $false
$previousPgPassword = $env:PGPASSWORD
try {
    try { $locked = $mutex.WaitOne(180000) } catch [Threading.AbandonedMutexException] { $locked = $true }
    if (-not $locked) { throw 'Otro arranque de Reconocimiento sigue en curso. Vuelve a ejecutar Run.' }
    if (-not (Test-Path $java) -or -not (Test-Path (Join-Path $pgBin 'psql.exe'))) { throw 'Instala Java 25 y PostgreSQL 18, o configura RECONOCIMIENTO_JAVA_HOME y RECONOCIMIENTO_PG_BIN.' }
    $envFile = Join-Path $backendRoot '.env'
    if (-not (Test-Path $envFile)) {
        Copy-Item -LiteralPath (Join-Path $backendRoot '.env.example') -Destination $envFile
    }
    $config = @{}
    foreach ($line in Get-Content $envFile -Encoding UTF8) {
        if ($line -match '^\s*([A-Z_]+)\s*=(.*)$') { $config[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'") }
    }
    foreach ($key in @('DB_URL', 'DB_USER', 'DB_PASSWORD', 'PORT', 'PHOTO_DIRECTORY')) {
        if (-not $config[$key] -or $config[$key] -like 'CONFIGURAR_*') { throw "Completa $key en backend/.env con la configuracion local de PostgreSQL." }
    }
    if ($config.DB_URL -notmatch '^jdbc:postgresql://') { throw 'DB_URL debe usar jdbc:postgresql://host:puerto/base.' }
    $dbUri = [Uri]($config.DB_URL.Substring(5))
    $dbHost = $dbUri.DnsSafeHost
    $port = if ($dbUri.Port -gt 0) { $dbUri.Port } else { 5432 }
    $database = $dbUri.AbsolutePath.Trim('/')
    if (-not $dbHost -or $database -notmatch '^[a-zA-Z_][a-zA-Z0-9_]{0,62}$' -or $dbUri.Query -or $dbUri.Fragment -or $dbUri.UserInfo) { throw 'DB_URL debe indicar un host y una sola base, sin parametros ni credenciales.' }
    if ($config.PORT -ne '8082') { throw 'El arranque local de Android requiere PORT=8082 en backend/.env.' }
    $env:PGPASSWORD = $config.DB_PASSWORD
    & (Join-Path $pgBin 'pg_isready.exe') '-h' $dbHost '-p' "$port" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        if ($dbHost -notin @('localhost', '127.0.0.1', '::1')) { throw 'El servidor PostgreSQL configurado no esta disponible.' }
        $serviceName = if ($config.POSTGRES_SERVICE) { $config.POSTGRES_SERVICE } else { 'postgresql-x64-18' }
        try { Start-Service -Name $serviceName -ErrorAction Stop } catch { throw "No se pudo iniciar el servicio $serviceName. El servicio PostgreSQL de Windows debe estar instalado y permitir su inicio." }
        $available = $false
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            & (Join-Path $pgBin 'pg_isready.exe') '-h' $dbHost '-p' "$port" | Out-Null
            if ($LASTEXITCODE -eq 0) { $available = $true; break }
            Start-Sleep -Milliseconds 500
        }
        if (-not $available) { throw 'PostgreSQL no responde en el host y puerto de backend/.env.' }
    }
    if ((Psql 'postgres' "SELECT 1 FROM pg_database WHERE datname='$database'") -ne '1') { Psql 'postgres' "CREATE DATABASE `"$database`"" | Out-Null }
    $instanceFile = Join-Path $localRoot 'instance.txt'
    if (-not (Test-Path $instanceFile)) { [IO.File]::WriteAllText($instanceFile, [Guid]::NewGuid().ToString()) }
    $instance = [IO.File]::ReadAllText($instanceFile).Trim()
    $sourceHash = Source-Hash
    $runtimeHash = Hash-Text ($sourceHash + ':' + (Hash-Text ([IO.File]::ReadAllText($envFile))))
    $stateFile = Join-Path $localRoot 'api-state.json'
    $state = if (Test-Path $stateFile) { Get-Content $stateFile -Raw | ConvertFrom-Json } else { $null }
    $health = Health
    if ($health -and ($health.instancia -ne $instance -or $health.aplicacion -ne 'reconocimiento')) { throw 'El puerto 8082 pertenece a otra API. No se detuvo ningun servicio ajeno.' }
    $ownedProcess = $null
    if ($state) {
        $candidate = Get-Process -Id $state.pid -ErrorAction SilentlyContinue
        if ($candidate -and $candidate.StartTime.ToUniversalTime().Ticks.ToString() -eq $state.startTicks -and $candidate.Path -ieq $java -and $state.instance -eq $instance) { $ownedProcess = $candidate }
    }
    if (-not ($health -and $health.disponible -eq $true -and $health.version -eq $runtimeHash -and $state.hash -eq $runtimeHash)) {
        if ($health -and -not $ownedProcess) { throw 'La API necesita actualizarse pero no se pudo verificar su proceso. No se detuvo ningun servicio.' }
        if ($ownedProcess) { $ownedProcess.Kill(); $ownedProcess.WaitForExit(15000) | Out-Null }
        $jar = Join-Path $backendRoot 'target\reconocimiento-api-1.0.0.jar'
        $builtFile = Join-Path $localRoot 'backend-build.txt'
        if (-not (Test-Path $jar) -or -not (Test-Path $builtFile) -or [IO.File]::ReadAllText($builtFile).Trim() -ne $sourceHash) {
            Write-Output 'Compilando y verificando la API de Reconocimiento...'
            & (Join-Path $PSScriptRoot 'maven.ps1') -MavenArguments @('test', 'package')
            [IO.File]::WriteAllText($builtFile, $sourceHash)
        }
        $runtimeJar = Join-Path $localRoot 'reconocimiento-api.jar'
        Copy-Item -LiteralPath $jar -Destination $runtimeJar -Force
        $log = (Join-Path $localRoot 'api.log').Replace('\', '/')
        $arguments = "-Dapp.instance=$instance -Dapp.build=$runtimeHash -Dlogging.file.name=`"$log`" -jar `"$runtimeJar`""
        $previousConfig = @{}
        try {
            foreach ($key in $config.Keys) {
                $previousConfig[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
                [Environment]::SetEnvironmentVariable($key, $config[$key], 'Process')
            }
            $apiProcess = Hidden-Process $java $arguments $backendRoot
        } finally {
            foreach ($key in $previousConfig.Keys) {
                if($null -eq $previousConfig[$key]) { Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue }
                else { [Environment]::SetEnvironmentVariable($key, $previousConfig[$key], 'Process') }
            }
        }
        $state = @{ pid=$apiProcess.Id; startTicks=$apiProcess.StartTime.ToUniversalTime().Ticks.ToString(); instance=$instance; hash=$runtimeHash }
        [IO.File]::WriteAllText($stateFile, ($state | ConvertTo-Json))
        $ready = $false
        for ($attempt = 0; $attempt -lt 80; $attempt++) {
            $apiProcess.Refresh()
            if ($apiProcess.HasExited) { throw 'La API se detuvo. Revisa .local/api.log.' }
            $health = Health
            if ($health -and $health.disponible -eq $true -and $health.instancia -eq $instance -and $health.version -eq $runtimeHash) { $ready = $true; break }
            Start-Sleep -Milliseconds 750
        }
        if (-not $ready) { throw 'La API no confirmo su conexion con PostgreSQL. Revisa .local/api.log.' }
    }
    if (-not $SkipAdb) {
        $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
        $sdkProperties = Join-Path $projectRoot 'local.properties'
        if (Test-Path $sdkProperties) {
            $sdkLine = Get-Content $sdkProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
            if ($sdkLine) { $sdkCandidates += $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
        }
        $sdkCandidates += 'C:\Android\Sdk'
        $adb = $sdkCandidates | Where-Object { $_ } | ForEach-Object { Join-Path $_ 'platform-tools\adb.exe' } |
            Where-Object { Test-Path $_ } | Select-Object -First 1
        if ($adb) {
            $adbServer = Hidden-Process $adb 'start-server' $projectRoot
            if ($adbServer.WaitForExit(15000)) {
                $devices = & $adb 'devices'
                foreach ($line in $devices) {
                    if ($line -match '^(\S+)\s+device$') {
                        $serial = $Matches[1]
                        if ($serial -notlike 'emulator-*') { & $adb '-s' $serial 'reverse' 'tcp:8082' 'tcp:8082' | Out-Null }
                    }
                }
            }
        }
    }
    Write-Output "Reconocimiento listo: PostgreSQL en ${dbHost}:$port, base $database y API en 8082."
} finally {
    $env:PGPASSWORD = $previousPgPassword
    $env:TEMP = $previousTemp; $env:TMP = $previousTmp
    if ($locked) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
