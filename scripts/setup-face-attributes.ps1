$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskCache=Join-Path $taskRoot '.local/face-attributes'
New-Item -ItemType Directory -Path $taskCache -Force | Out-Null
$taskZip=Join-Path $taskCache 'face-attrib-v065-w8a8.zip'
$taskModel=Join-Path $taskCache 'face_attributes.tflite'
$taskHash='8A320B1C03C741EDFAB1391C590A763494C659FC42706E4E09C4C211F4211913'
if(-not(Test-Path -LiteralPath $taskZip)){
    Invoke-WebRequest -Uri 'https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/face_attrib_net/releases/v0.65.0/face_attrib_net-tflite-w8a8.zip' -OutFile $taskZip -TimeoutSec 90
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive=[IO.Compression.ZipFile]::OpenRead($taskZip)
try {
    $taskEntry=$taskArchive.GetEntry('face_attrib_net-tflite-w8a8/face_attrib_net.tflite')
    if($null -eq $taskEntry){throw 'El paquete no contiene el modelo esperado.'}
    [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry,$taskModel,$true)
} finally {$taskArchive.Dispose()}
if((Get-FileHash -LiteralPath $taskModel -Algorithm SHA256).Hash -ne $taskHash){throw 'El modelo no coincide con la versión revisada. No se instalará.'}
$taskLicense=Join-Path $taskCache 'LICENSE-Qualcomm.txt'
Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/qualcomm/ai-hub-models/v0.65.0/LICENSE' -OutFile $taskLicense -TimeoutSec 30
$taskLicenseDirectory=Join-Path $taskRoot 'app/src/main/assets/licenses'
New-Item -ItemType Directory -Path $taskLicenseDirectory -Force | Out-Null
Copy-Item -LiteralPath $taskModel -Destination (Join-Path $taskRoot 'app/src/main/assets/face_attributes.tflite')
Copy-Item -LiteralPath $taskLicense -Destination (Join-Path $taskLicenseDirectory 'face_attributes-BSD-3-Clause.txt')
Write-Output 'FaceAttribNet v0.65.0 preparado y verificado; no utiliza una API externa.'
