param([string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
if($Serial -notmatch '^emulator-\d+$'){throw 'Este ensayo utiliza únicamente el emulador para conservar intacto el teléfono de uso.'}
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskFixtures=Join-Path $taskRoot '.local/face-attributes/fixtures'
New-Item -ItemType Directory -Path $taskFixtures -Force | Out-Null
$taskRevision='40853b04006af50706441001147f7bbf4965eceb'
$taskHashes=@{
    'eyeglasses'='B360DE8D82646C07BF65F9853F84A6EABBE58D897BDFEA9D9514129E36D10011'
    'eyeglasses_man'='CD0AD5F6DC40F9FA2238887683429E19D26B216881C3F77212EAAEF3A7671738'
    'sunglasses'='3A4DF3F35CC046498FFCA8A29AE8C7A41E72A9236CC1C5EFA44AF18856855DF8'
    'sunglasses_man'='6BE0DE739712C53344563842CE3FEB59E2C3BA34F9F35359B07B29C421337950'
    'sunglasses_tinted'='7E1CEE220E4947FCF82DEC86DBE5B7B6665D51C9C10D99607995D5C7502D2B74'
    'mask'='25C9D9C78B3FF148391B74FE11CCCBD87C42DF3570143314A7BE635107E5A1AE'
    'mask_man'='9D3412FA4D4685C547682CB1298671FB4412112AA542980E8AEC3271D5E58E5F'
    'closed_eyes'='197C30F111E02372F8B01592F87DFA7D1DA51DFDFABC13AFD06D03E84EA7F568'
}
$taskAdb='C:\Android\Sdk\platform-tools\adb.exe'
& $taskAdb -s $Serial shell mkdir -p /sdcard/Android/data/com.example.reconocimiento/files/attribute-fixtures
if($LASTEXITCODE -ne 0){throw 'Abre el emulador e instala antes ambos APK de depuración.'}
foreach($taskName in $taskHashes.Keys){
    $taskFile=Join-Path $taskFixtures "$taskName.jpg"
    if(-not(Test-Path -LiteralPath $taskFile)){
        Invoke-WebRequest -Uri "https://raw.githubusercontent.com/yakhyo/face-attribute/$taskRevision/assets/test_images/$taskName.jpg" -OutFile $taskFile -TimeoutSec 60
    }
    if((Get-FileHash -LiteralPath $taskFile -Algorithm SHA256).Hash -ne $taskHashes[$taskName]){throw "La referencia $taskName no coincide con la revisión fijada."}
    & $taskAdb -s $Serial push $taskFile /sdcard/Android/data/com.example.reconocimiento/files/attribute-fixtures/ | Out-Null
    if($LASTEXITCODE -ne 0){throw 'No se pudo cargar una referencia de prueba.'}
}
$taskResults=& $taskAdb -s $Serial shell am instrument -w -e class com.example.reconocimiento.FaceAttributesDeviceTest com.example.reconocimiento.test/androidx.test.runner.AndroidJUnitRunner
$taskResults | Write-Output
if($LASTEXITCODE -ne 0 -or ($taskResults -join "`n") -notmatch 'OK \(\d+ tests?\)'){throw 'Falló la comprobación de accesorios.'}
& $taskAdb -s $Serial pull /sdcard/Android/data/com.example.reconocimiento/files/face-attributes-results.json (Join-Path $taskRoot '.local/face-attributes/results.json')
if($LASTEXITCODE -ne 0){throw 'No se pudo recuperar el informe.'}
