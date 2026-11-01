param([string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb='C:\Android\Sdk\platform-tools\adb.exe'
$fixtures=Join-Path $projectRoot '.local/model-fixtures'
New-Item -ItemType Directory -Path $fixtures -Force | Out-Null
foreach($number in 1,2,3,11){
    $file=Join-Path $fixtures "img$number.jpg"
    if(-not (Test-Path -LiteralPath $file)){
        Invoke-WebRequest -Uri "https://raw.githubusercontent.com/serengil/deepface/65697d2ea8b8f8272494b4ba79b5730403e0b5b6/tests/unit/dataset/img$number.jpg" -OutFile $file
    }
}
& $adb -s $Serial shell mkdir -p /sdcard/Android/data/com.example.reconocimiento/files/model-fixtures
if($LASTEXITCODE -ne 0){throw 'No se pudo preparar el dispositivo.'}
foreach($number in 1,2,3,11){
    & $adb -s $Serial push (Join-Path $fixtures "img$number.jpg") /sdcard/Android/data/com.example.reconocimiento/files/model-fixtures/
    if($LASTEXITCODE -ne 0){throw 'No se pudo cargar una imagen de prueba.'}
}
$result=& $adb -s $Serial shell am instrument -w -e class com.example.reconocimiento.FaceModelVerificationTest com.example.reconocimiento.test/androidx.test.runner.AndroidJUnitRunner
$result | Write-Output
if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(\d+ tests?\)'){throw 'Falló la verificación del modelo. Revisa el resultado de instrumentación.'}
& $adb -s $Serial pull /sdcard/Android/data/com.example.reconocimiento/files/face-verification.json (Join-Path $projectRoot '.local/face-verification.json')
