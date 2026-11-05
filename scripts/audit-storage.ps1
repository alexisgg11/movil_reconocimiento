param([string]$ApiBase='http://127.0.0.1:8082/api',[SecureString]$AdminToken)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$auditConfig=@{}
foreach($line in Get-Content -LiteralPath (Join-Path $taskRoot 'backend/.env')){
    if($line -match '^\s*([A-Z_]+)\s*=(.*)$'){$auditConfig[$Matches[1]]=$Matches[2].Trim().Trim('"').Trim("'")}
}
$databaseUri=[Uri]$auditConfig.DB_URL.Substring(5)
$databaseName=$databaseUri.AbsolutePath.Trim('/')
$pgBin=if($env:RECONOCIMIENTO_PG_BIN){$env:RECONOCIMIENTO_PG_BIN}else{'C:\Program Files\PostgreSQL\18\bin'}
$previousPassword=$env:PGPASSWORD
$query=@'
BEGIN READ ONLY;
SELECT json_build_object(
 'personas',COALESCE((SELECT json_agg(json_build_object('dni',p.dni,'nombre',p.nombre,'foto',p.foto,'origenRostro',p.origen_rostro,
  'rostro',EXISTS(SELECT 1 FROM reconocimiento.rostro r WHERE r.dni=p.dni),
  'huella',EXISTS(SELECT 1 FROM reconocimiento.dispositivo d WHERE d.dni=p.dni))) FROM reconocimiento.persona p WHERE p.activo),'[]'::json),
 'configuracion',(SELECT row_to_json(c) FROM reconocimiento.configuracion c WHERE id=1),
 'asistencias',COALESCE((SELECT json_agg(json_build_object('id',id,'dni',dni,'nombre',nombre,'fecha',fecha,'instante',instante,
  'metodo',metodo,'distancia',distancia,'precision',precision_m,'ip',ip,'similitud',similitud,'umbral',umbral,
  'foto',foto,'fotoRegistro',foto_registro,'tipoFotoRegistro',tipo_foto_registro)) FROM reconocimiento.asistencia),'[]'::json),
 'vectores',COALESCE((SELECT json_agg(vector) FROM reconocimiento.rostro),'[]'::json),
 'ensayosFaciales',(SELECT COUNT(*) FROM reconocimiento.ensayo_facial),
 'controles',json_build_object(
  'duplicados',(SELECT COUNT(*) FROM (SELECT dni,fecha FROM reconocimiento.asistencia GROUP BY dni,fecha HAVING COUNT(*)>1) x),
  'fechasIncorrectas',(SELECT COUNT(*) FROM reconocimiento.asistencia WHERE fecha<>(instante AT TIME ZONE 'America/Lima')::date),
  'rostrosInactivos',(SELECT COUNT(*) FROM reconocimiento.rostro r JOIN reconocimiento.persona p ON p.dni=r.dni WHERE NOT p.activo),
  'huellasInactivas',(SELECT COUNT(*) FROM reconocimiento.dispositivo d JOIN reconocimiento.persona p ON p.dni=d.dni WHERE NOT p.activo),
  'modelosIncorrectos',(SELECT COUNT(*) FROM reconocimiento.rostro WHERE modelo<>'facenet512-fp16-v1'),
  'origenesIncorrectos',(SELECT COUNT(*) FROM reconocimiento.persona p WHERE p.activo AND
    (p.origen_rostro NOT IN ('CAMARA','GALERIA') OR (p.origen_rostro='GALERIA' AND
     (p.foto='' OR p.foto_rostro='' OR (SELECT COUNT(*) FROM reconocimiento.rostro r WHERE r.dni=p.dni)<>1)))),
  'evidenciaIncoherente',(SELECT COUNT(*) FROM reconocimiento.asistencia WHERE (metodo='FACIAL' AND (similitud IS NULL OR similitud<0.8 OR similitud>1 OR similitud<umbral)) OR (metodo='HUELLA' AND similitud IS NOT NULL)),
  'ensayosIncoherentes',(SELECT COUNT(*) FROM reconocimiento.ensayo_facial WHERE similitud NOT BETWEEN -1 AND 1 OR umbral NOT BETWEEN 0.8 AND 0.99 OR modelo<>'facenet512-fp16-v1'
    OR resultado NOT IN ('COINCIDENCIA_CORRECTA','RECHAZO_CORRECTO','FALSO_RECHAZO','FALSA_ACEPTACION','IDENTIDAD_INCORRECTA')
    OR (resultado='COINCIDENCIA_CORRECTA' AND (dni_esperado IS NULL OR dni_reconocido IS NULL OR dni_esperado<>dni_reconocido))
    OR (resultado='RECHAZO_CORRECTO' AND (dni_esperado IS NOT NULL OR dni_reconocido IS NOT NULL))
    OR (resultado='FALSO_RECHAZO' AND (dni_esperado IS NULL OR dni_reconocido IS NOT NULL))
    OR (resultado='FALSA_ACEPTACION' AND (dni_esperado IS NOT NULL OR dni_reconocido IS NULL))
    OR (resultado='IDENTIDAD_INCORRECTA' AND (dni_esperado IS NULL OR dni_reconocido IS NULL OR dni_esperado=dni_reconocido)))
  )
);
COMMIT;
'@
try {
    $env:PGPASSWORD=$auditConfig.DB_PASSWORD
    $snapshotText=& (Join-Path $pgBin 'psql.exe') -X -w -h $databaseUri.DnsSafeHost -p $databaseUri.Port -U $auditConfig.DB_USER -d $databaseName -v ON_ERROR_STOP=1 -Atq -c $query
    if($LASTEXITCODE -ne 0){throw 'No se pudo ejecutar la auditoría de lectura en PostgreSQL.'}
    $snapshot=($snapshotText -join "`n") | ConvertFrom-Json
} finally {$env:PGPASSWORD=$previousPassword}
$days=@($snapshot.asistencias | Group-Object fecha | Sort-Object Name)
$peopleMatch=$null
$siteMatch=$null
$attendanceMatch=$null
if ($null -ne $AdminToken) {
    $headers=@{Authorization='Bearer '+[System.Net.NetworkCredential]::new('', $AdminToken).Password}
    $apiPeople=Invoke-RestMethod "$ApiBase/personas" -Headers $headers
    $apiPeople=@($apiPeople | ForEach-Object {$_})
    $apiSite=Invoke-RestMethod "$ApiBase/configuracion" -Headers $headers
    $peopleMatch=$apiPeople.Count -eq @($snapshot.personas).Count
    $peopleByDni=@{};foreach($person in $apiPeople){$peopleByDni[$person.dni]=$person}
    foreach($person in $snapshot.personas){
        $remote=$peopleByDni[$person.dni]
        if($null -eq $remote){$peopleMatch=$false;continue}
        foreach($field in @('dni','nombre','foto','rostro','huella','origenRostro')){if($person.$field -cne $remote.$field){$peopleMatch=$false}}
    }
    $siteMatch=$true
    foreach($field in @('nombre','latitud','longitud','radio','red','umbral','revision')){if($snapshot.configuracion.$field -ne $apiSite.$field){$siteMatch=$false}}
    $attendanceMatch=$true
    foreach($day in $days){
        $remoteRows=Invoke-RestMethod "$ApiBase/asistencias?fecha=$($day.Name)" -Headers $headers
        $remoteRows=@($remoteRows | ForEach-Object {$_})
        if($remoteRows.Count -ne $day.Count){$attendanceMatch=$false}
        $remoteById=@{};foreach($row in $remoteRows){$remoteById[[string]$row.id]=$row}
        foreach($row in $day.Group){
            $remote=$remoteById[[string]$row.id]
            if($null -eq $remote){$attendanceMatch=$false;continue}
            foreach($field in @('id','dni','nombre','fecha','metodo','distancia','precision','ip','similitud','umbral','foto','fotoRegistro','tipoFotoRegistro')){
                if($row.$field -cne $remote.$field){$attendanceMatch=$false}
            }
            if(([DateTimeOffset]$row.instante).UtcDateTime.Ticks -ne ([DateTimeOffset]$remote.instante).UtcDateTime.Ticks){$attendanceMatch=$false}
        }
    }
}
$invalidVectors=0
foreach($raw in $snapshot.vectores){
    try {
        $values=@($raw | ConvertFrom-Json);$norm=0.0
        if($values.Count -ne 512){$invalidVectors++;continue}
        foreach($value in $values){$number=[double]$value;if([double]::IsNaN($number)-or[double]::IsInfinity($number)){throw 'Vector no finito'};$norm+=$number*$number}
        if([Math]::Abs($norm-1)>0.0001){$invalidVectors++}
    } catch {$invalidVectors++}
}
$invalidControls=@($snapshot.controles.PSObject.Properties | Where-Object {[long]$_.Value -ne 0}).Count
$report=[ordered]@{
    verificadoUtc=[DateTime]::UtcNow.ToString('o');soloLectura=$true
    personasActivas=@($snapshot.personas).Count;asistencias=@($snapshot.asistencias).Count
    fechasConsultadas=@($days | ForEach-Object {$_.Name});plantillasFaciales=@($snapshot.vectores).Count;ensayosFaciales=$snapshot.ensayosFaciales
    comparacionApi=($null -ne $AdminToken)
    personasApiCoinciden=$peopleMatch;configuracionApiCoincide=$siteMatch;historialApiCoincide=$attendanceMatch
    plantillasInvalidas=$invalidVectors;controles=$snapshot.controles
    correcto=((($null -eq $AdminToken) -or ($peopleMatch -and $siteMatch -and $attendanceMatch)) -and $invalidVectors -eq 0 -and $invalidControls -eq 0)
}
$json=$report | ConvertTo-Json -Depth 5
$destination=Join-Path $taskRoot '.local/storage-audit.json'
New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
$json | Set-Content -LiteralPath $destination -Encoding UTF8
$json | Write-Output
if(-not $report.correcto){throw 'La auditoría encontró una diferencia. Revisa .local/storage-audit.json.'}
