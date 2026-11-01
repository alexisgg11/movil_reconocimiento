$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskConfig = @{}
foreach ($taskLine in Get-Content -LiteralPath (Join-Path $taskRoot 'backend/.env')) {
    if ($taskLine -match '^\s*([A-Z_]+)\s*=(.*)$') { $taskConfig[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'") }
}
$taskPgBin = if ($env:RECONOCIMIENTO_PG_BIN) { $env:RECONOCIMIENTO_PG_BIN } else { 'C:\Program Files\PostgreSQL\18\bin' }
$taskPreviousPassword = $env:PGPASSWORD
$taskPreviousCredentials = @{}
try {
    foreach($key in @('DB_USER','DB_PASSWORD')){
        $taskPreviousCredentials[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
        [Environment]::SetEnvironmentVariable($key,$taskConfig[$key],'Process')
    }
    $env:PGPASSWORD = $taskConfig.DB_PASSWORD
    $taskExists = & (Join-Path $taskPgBin 'psql.exe') -w -h localhost -p 5432 -U $taskConfig.DB_USER -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname='reconocimiento_verificacion'"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar PostgreSQL.' }
    if ($taskExists -ne '1') {
        & (Join-Path $taskPgBin 'psql.exe') -w -h localhost -p 5432 -U $taskConfig.DB_USER -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE reconocimiento_verificacion'
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base aislada de verificacion.' }
    }
    & (Join-Path $PSScriptRoot 'maven.ps1') -MavenArguments @('-B','-ntp','-Dreconocimiento.test.postgres=true','test')
} finally {
    $env:PGPASSWORD = $taskPreviousPassword
    foreach($key in $taskPreviousCredentials.Keys){
        if($null -eq $taskPreviousCredentials[$key]){Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue}
        else {[Environment]::SetEnvironmentVariable($key,$taskPreviousCredentials[$key],'Process')}
    }
}
