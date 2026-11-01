# Modelo facial y dependencias

## FaceAttribNet — accesorios faciales

- Autor del modelo: Qualcomm Technologies, Inc. y/o sus subsidiarias.
- Distribución oficial: [Facial-Attribute-Detection](https://huggingface.co/qualcomm/Facial-Attribute-Detection), versión de exportación `v0.65.0`, TFLite `w8a8`, universal.
- [Paquete fijado](https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/face_attrib_net/releases/v0.65.0/face_attrib_net-tflite-w8a8.zip).
- Archivo en el APK: `app/src/main/assets/face_attributes.tflite`, 11 535 824 bytes.
- SHA-256: `8a320b1c03c741edfab1391c590a763494c659fc42706e4e09c4c211f4211913`.
- Entrada UINT8 NHWC `[1,128,128,3]`; escala `0.003920967690646648`, cero 0. Salida UINT8 `[1,5]`; escala `0.00390625`, cero 0. Se consultan los parámetros del tensor en ejecución.
- Orden de salida: ojo izquierdo abierto, ojo derecho abierto, lentes, mascarilla, lentes de sol. Son cinco cabezas binarias independientes. El control nuevo utiliza las tres clases de accesorios; ML Kit conserva el control de ojos abiertos y postura.
- Licencia del modelo BSD-3-Clause declarada por el [publicador](https://aihub.qualcomm.com/mobile/models/face_attrib_net). El aviso íntegro está en `app/src/main/assets/licenses/face_attributes-BSD-3-Clause.txt`, copiado del [LICENSE de v0.65.0](https://github.com/qualcomm/ai-hub-models/blob/v0.65.0/LICENSE).
- Las fotos de ensayo adicionales proceden de `yakhyo/face-attribute`, revisión `40853b04006af50706441001147f7bbf4965eceb`. Su README atribuye esas fotos a Pexels. Se fijan SHA-256 en `scripts/test-face-attributes.ps1`; quedan únicamente en `.local` y en archivos de prueba del emulador. No se incluyen las fotos en el APK ni en la base real.
- Esta integración utiliza el modelo oficial de atributos y el detector existente de ML Kit; no incorpora detectores SCRFD ni otros modelos de UniFace.

## FaceNet 512

- Archivo: `app/src/main/assets/facenet_512.tflite`
- Tamaño: 47 058 600 bytes.
- SHA-256: `f0dede2fdf5fd10248aad1d85393d2d40e3494464440bd8fccb2c957baa430b7`.
- Revisión de origen: `e8d51c97aff6edd2c3d10acc6850617b3385d499` de shubham0204/OnDevice-Face-Recognition-Android.
- Descarga: https://raw.githubusercontent.com/shubham0204/OnDevice-Face-Recognition-Android/e8d51c97aff6edd2c3d10acc6850617b3385d499/app/src/main/assets/facenet_512.tflite
- La revisión documenta conversión desde `DeepFace.build_model("Facenet512")` a TFLite con optimización FP16.
- Entrada: float32 `[1,160,160,3]` RGB con estandarización por imagen; salida: float32 `[1,512]`.
- Identificador interno: `facenet512-fp16-v1`. Cambiar modelo o preprocesamiento exige volver a registrar los rostros y calibrar el umbral.

El código del repositorio de referencia está publicado bajo Apache-2.0. La licencia de un repositorio no implica automáticamente derechos sobre los pesos y los datos de entrenamiento. El modelo se incorpora para esta práctica académica; deben revisarse por separado las condiciones de los pesos y del conjunto de entrenamiento antes de redistribución o uso comercial.

Referencias:

- https://github.com/shubham0204/OnDevice-Face-Recognition-Android/tree/e8d51c97aff6edd2c3d10acc6850617b3385d499
- https://github.com/serengil/deepface
- https://openaccess.thecvf.com/content_cvpr_2015/html/Schroff_FaceNet_A_Unified_2015_CVPR_paper.html
- https://developers.google.com/edge/litert/android
- https://developers.google.com/ml-kit/vision/face-detection/android

## Interfaz

El mapa incorpora Leaflet **1.9.4** (JavaScript y CSS locales), licencia BSD-2-Clause incluida en `app/src/main/assets/map/LEAFLET-LICENSE.txt`. Fuentes: https://leafletjs.com/ y https://unpkg.com/leaflet@1.9.4/dist/. Se utiliza WebViewAssetLoader para servir el contenido local en un origen HTTPS, sin acceso a archivos del dispositivo. La cartografía procede de https://tile.openstreetmap.org/ con atribución a OpenStreetMap y un User-Agent que identifica Reconocimiento. No se realiza descarga masiva ni precarga de mapas.

Política del servicio de mapas: https://operations.osmfoundation.org/policies/tiles/.

## Imágenes de verificación

El script opcional `scripts/test-face-model.ps1` descarga `img1.jpg`, `img2.jpg`, `img3.jpg` e `img11.jpg` del conjunto de pruebas de DeepFace, revisión `65697d2ea8b8f8272494b4ba79b5730403e0b5b6`. Su prueba de referencia identifica los pares 1/2, 1/11 y 2/11 como la misma persona, y 1/3 como personas distintas: https://github.com/serengil/deepface/blob/65697d2ea8b8f8272494b4ba79b5730403e0b5b6/tests/unit/test_verify.py . Las imágenes no se incluyen en el APK ni en la base de datos; quedan en `.local` y en el almacenamiento de prueba del emulador. No se presume que la licencia del código otorgue derechos adicionales sobre estas fotografías.

Tema e iconos adaptados del proyecto Personas del usuario, conforme a su indicación de conservar el diseño. CameraX, AndroidX Biometric y LiteRT se integran como dependencias; ML Kit y Play services Location se utilizan conforme a los términos de sus proveedores.
