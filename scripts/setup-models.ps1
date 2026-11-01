$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskAssets = Join-Path $taskRoot 'app/src/main/assets'
$taskCache = Join-Path $taskRoot '.local/models'
$taskFaceHash = 'F0DEDE2FDF5FD10248AAD1D85393D2D40E3494464440BD8FCCB2C957BAA430B7'
$taskAttributesHash = '8A320B1C03C741EDFAB1391C590A763494C659FC42706E4E09C4C211F4211913'
New-Item -ItemType Directory -Path $taskAssets -Force | Out-Null
New-Item -ItemType Directory -Path $taskCache -Force | Out-Null

function Test-Model([string]$Path, [string]$ExpectedHash) {
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $ExpectedHash) {
        throw "El archivo $Path no corresponde al modelo fijado. No se reemplazara automaticamente."
    }
    return $true
}

$taskFace = Join-Path $taskAssets 'facenet_512.tflite'
if (-not (Test-Model $taskFace $taskFaceHash)) {
    $taskDownload = Join-Path $taskCache 'facenet_512.download'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/shubham0204/OnDevice-Face-Recognition-Android/e8d51c97aff6edd2c3d10acc6850617b3385d499/app/src/main/assets/facenet_512.tflite' -OutFile $taskDownload -TimeoutSec 180
    if ((Get-FileHash -LiteralPath $taskDownload -Algorithm SHA256).Hash -ne $taskFaceHash) {
        throw 'La descarga de FaceNet no coincide con su SHA-256. No se instalara.'
    }
    Move-Item -LiteralPath $taskDownload -Destination $taskFace
}

$taskAttributes = Join-Path $taskAssets 'face_attributes.tflite'
if (-not (Test-Model $taskAttributes $taskAttributesHash)) {
    & (Join-Path $PSScriptRoot 'setup-face-attributes.ps1')
    if (-not (Test-Model $taskAttributes $taskAttributesHash)) {
        throw 'No se pudo preparar FaceAttribNet.'
    }
}
Write-Output 'FaceNet y FaceAttribNet preparados y verificados. Procedencia y licencias: THIRD_PARTY.md.'
