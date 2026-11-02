# Reconocimiento y asistencia

Aplicación Android para registrar personas y controlar su asistencia mediante reconocimiento facial o biometría del teléfono. La marcación exige estar dentro de la sede configurada y se permite una asistencia por persona al día, con hora de Lima.

**Alcance de Huella:** Android autoriza la cuenta vinculada al dispositivo. La aplicación no extrae ni guarda las huellas del sensor interno y no identifica a varios trabajadores por su dedo en un teléfono compartido. Esa modalidad requiere un lector externo compatible.

**Guía:** [Instalación](#instalación-en-windows) · [Conexión](#conexión-del-teléfono) · [Uso](#uso) · [Recorrido de una petición](#recorrido-de-una-petición) · [Estructura](#estructura-del-proyecto)

## Capturas

<table>
  <tr>
    <td align="center"><strong>Registro de persona</strong><br><img src="docs/capturas/registro.png" width="240" alt="Formulario de biometría de una persona de prueba"></td>
    <td align="center"><strong>Marcación de asistencia</strong><br><img src="docs/capturas/asistencia.png" width="240" alt="Reloj, sede y botones Facial y Huella"></td>
  </tr>
  <tr>
    <td align="center"><strong>Historial y reporte</strong><br><img src="docs/capturas/historial.png" width="240" alt="Historial con nombres y DNI ocultos"></td>
    <td align="center"><strong>Configuración</strong><br><img src="docs/capturas/configuracion.png" width="240" alt="Radio de asistencia y similitud facial mínima"></td>
  </tr>
</table>

Las capturas se anonimizaron para ocultar nombres, DNI y coordenadas precisas. La ficha «Prueba» utiliza datos ficticios.

## Funciones

- **Personas:** registro, búsqueda por DNI o nombre, edición, baja y advertencias de biometría pendiente.
- **Rostro:** registro guiado, actualización e importación de fotos con recorte y validaciones.
- **Asistencia:** reconocimiento, control de zona/red y bloqueo de marcaciones duplicadas.
- **Historial:** consulta por calendario y detalle con fecha, hora, método, fotos y ubicación.
- **Reporte:** consulta por período y descarga de Excel `.xlsx`.
- **Configuración:** mapa con pin ajustable, círculo del radio, red permitida y mínimo facial.

## Tecnologías

| Tecnología | Función |
| --- | --- |
| Kotlin, Compose y Material 3 | Interfaz Android. |
| CameraX y ML Kit | Cámara, detección de rostro, postura y ojos. |
| FaceNet 512 y LiteRT | Descripción numérica del rostro para su comparación. |
| FaceAttribNet | Comprobación de lentes y mascarilla. |
| BiometricPrompt y Android Keystore | Autorización biométrica y firma de retos del dispositivo. |
| Play services Location | Ubicación y precisión GPS. |
| Leaflet y OpenStreetMap | Mapa, pin y radio de la sede. |
| Coroutines, StateFlow y ViewModel | Operaciones asíncronas y estado de la pantalla. |
| HttpURLConnection y `org.json` | Peticiones HTTP y conversión JSON en Android. |
| Java 25 y Spring Boot | Servidor de la aplicación, reglas e inyección de dependencias. |
| Tomcat, Spring MVC y Jackson | Recepción HTTP, rutas de la API y conversión JSON ↔ objetos Java. |
| Spring JDBC y driver PostgreSQL | Ejecución de SQL parametrizado mediante `JdbcTemplate`. |
| PostgreSQL | Personas, plantillas faciales, vínculos, asistencias y ensayos. |
| Apache POI | Generación del Excel. |

Las versiones están fijadas en [libs.versions.toml](gradle/libs.versions.toml) y [pom.xml](backend/pom.xml). La procedencia y las condiciones de los modelos se detallan en [THIRD_PARTY.md](THIRD_PARTY.md). No se utiliza Python ni una API externa de reconocimiento.

**Gradle** construye el APK y **Maven** construye la API; ambos preparan dependencias y pruebas. Android Studio es el entorno desde el que se edita y ejecuta el proyecto. Estas herramientas de construcción no intervienen en cada marcación.

## Instalación en Windows

### 1. Requisitos

La PC ejecuta Android Studio, la API y PostgreSQL; el APK se instala en un teléfono o emulador. Instalar solo el APK no incluye el servidor ni la base de datos.

| Requisito | Preparación |
| --- | --- |
| Git y Android Studio | Git permite descargar el repositorio. Android Studio debe admitir las versiones de Gradle y del plugin fijadas en el proyecto. |
| Android SDK 37 y Platform Tools | Se instalan desde **Android Studio → SDK Manager**. Para emular se añade una imagen x86_64 con Google APIs/Google Play. |
| JDK 25 | Se instala un JDK completo, no solo un entorno de ejecución Java. |
| Maven 3.9.16 | Se descarga y descomprime; su carpeta contiene `bin`, `boot` y `conf`. |
| PostgreSQL 18 | Se instala el servidor local, se conserva la contraseña de `postgres` y se identifica su servicio de Windows. pgAdmin es opcional para consultar los datos. |

Gradle se descarga mediante el wrapper incluido. La app admite Android 7.0 o superior; los binarios incluidos soportan ARM64 y emuladores x86_64. La cámara, el GPS y la biometría reales se comprueban en un teléfono compatible.

Los comandos siguientes se ejecutan en **PowerShell**, desde la carpeta raíz del repositorio después del clonado:

```powershell
git clone https://github.com/alexisgg11/movil_reconocimiento.git
cd movil_reconocimiento
```

Si PowerShell bloquea los scripts, se habilitan únicamente en la terminal actual antes de continuar:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

### 2. Rutas y modelos

Se sustituyen las rutas de ejemplo por las carpetas instaladas en la PC:

```powershell
$env:RECONOCIMIENTO_JAVA_HOME = 'C:\Herramientas\jdk-25'
$env:JAVA_HOME = $env:RECONOCIMIENTO_JAVA_HOME
$env:RECONOCIMIENTO_MAVEN_HOME = 'C:\Herramientas\apache-maven-3.9.16'
$env:RECONOCIMIENTO_PG_BIN = 'C:\Program Files\PostgreSQL\18\bin'
$env:ANDROID_HOME = 'C:\Android\Sdk'
```

`$env:` configura solo esa terminal. Para que **Run** desde Android Studio encuentre las mismas rutas, se añaden esas cinco variables en **Windows → Variables de entorno → Variables de usuario** y se vuelve a abrir Android Studio. `JAVA_HOME` apunta al mismo JDK 25. Las rutas no deben copiarse literalmente si las herramientas están instaladas en otra ubicación.

Después se preparan los modelos:

```powershell
.\scripts\setup-models.ps1
```

El script descarga las versiones fijadas, verifica SHA-256 y deja `facenet_512.tflite` y `face_attributes.tflite` en `app/src/main/assets/`. Los pesos no se almacenan en Git. La primera preparación y sincronización requieren internet.

### 3. Base de datos

Se crea el archivo local de configuración si todavía no existe:

```powershell
if (-not (Test-Path -LiteralPath '.\backend\.env')) {
    Copy-Item -LiteralPath '.\backend\.env.example' -Destination '.\backend\.env'
}
```

En `backend/.env` se completan los datos de PostgreSQL:

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/reconocimiento_app
DB_USER=postgres
DB_PASSWORD=CONFIGURAR_PASSWORD
PORT=8082
PHOTO_DIRECTORY=storage/fotos
POSTGRES_SERVICE=postgresql-x64-18
```

`CONFIGURAR_PASSWORD` se sustituye por la contraseña local. El servicio se ajusta si tiene otro nombre. `PORT` permanece en `8082`; las fotos actuales se guardan en PostgreSQL, aunque se conserva la variable `PHOTO_DIRECTORY` del entorno.

### 4. Servidor y app

```powershell
.\scripts\start-local-services.ps1
Invoke-RestMethod 'http://127.0.0.1:8082/api/health'
```

El arranque comprueba PostgreSQL, crea la base si falta, compila la API cuando corresponde y prepara el esquema. El usuario de desarrollo necesita permiso para crear la base o esta debe existir previamente. Si el servicio PostgreSQL está detenido y Windows impide iniciarlo, se inicia desde **Servicios**. Los logs quedan en `.local/api.log`.

La respuesta de salud debe contener `disponible: true` y `aplicacion: reconocimiento`: ese endpoint también consulta PostgreSQL.

Para instalar la app:

1. En Android Studio se elige **Open** y se abre la raíz `movil_reconocimiento`, que contiene `settings.gradle.kts`; no se abre solo `app/` o `backend/`.
2. Se espera la sincronización de Gradle y se completan las descargas/licencias del SDK. El proyecto solicita Java 25 para Gradle; no se cambia a un JDK antiguo para resolver un error de rutas.
3. Se selecciona un emulador o teléfono conectado según la sección siguiente y la configuración **app**. Se pulsa **Run**.
4. Se aceptan la instalación y los permisos solicitados de cámara, ubicación precisa y red local cuando correspondan.
5. La app debe abrir Personas, Asistencia y Configuración. En una instalación nueva todavía no hay personas ni sede; se configura la sede y se registra una ficha según **Uso**.

Tras esta preparación inicial, **Run** vuelve a comprobar los servicios antes de compilar debug. La API y PostgreSQL permanecen en la PC mientras se utiliza la app.

### Problemas de instalación habituales

| Síntoma | Comprobación |
| --- | --- |
| No encuentra Java, Maven o PostgreSQL | Revisar las variables anteriores y que apunten a carpetas existentes; volver a abrir Android Studio. |
| Solicita completar `backend/.env` o falla la conexión SQL | Sustituir `CONFIGURAR_PASSWORD`, comprobar el servicio, usuario, puerto y base de `DB_URL`. |
| El puerto 8082 pertenece a otra API | Identificar el proceso que lo usa; el script no detiene servicios ajenos. |
| No encuentra modelos faciales | Ejecutar `setup-models.ps1` antes de compilar e instalar de nuevo la app. |
| La app no conecta en el teléfono | Confirmar salud de la API, autorización ADB y túnel `reverse`; si se reconecta el teléfono, repetir el túnel o Run. |
| No aparece huella en el emulador o teléfono | La modalidad requiere biometría fuerte configurada en Android y una persona vinculada. El emulador no certifica el sensor real. |

## Conexión del teléfono

| Entorno | Dirección de la app | Conexión |
| --- | --- | --- |
| Emulador | `http://10.0.2.2:8082/api` | Acceso a la PC anfitriona. |
| Teléfono USB | `http://127.0.0.1:8082/api` | Túnel ADB en el puerto 8082. |
| Teléfono por Wi-Fi | `http://127.0.0.1:8082/api` | ADB inalámbrico con el mismo túnel. |

Para USB se habilita Depuración USB y se acepta la autorización de la PC. En POCO/Xiaomi puede ser necesario permitir también la instalación por USB.

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices
& $adb -s 'SERIAL_DEL_TELEFONO' reverse tcp:8082 tcp:8082
```

El serial se obtiene de `adb devices`. El script de arranque prepara el túnel de los teléfonos conectados. Para Wi-Fi se realiza la vinculación desde **Android Studio → Pair Devices Using Wi-Fi** y **Opciones de desarrollador → Depuración inalámbrica** en el teléfono. PC y teléfono deben conservar la conexión; la PC y la API permanecen encendidas.

Con el túnel, la API normalmente observa `127.0.0.1`, incluso por Wi-Fi. **Usar conexión actual** permite configurar el filtro con esa IP efectiva. La app actual no incluye cambio de URL del servidor desde la interfaz.

## Uso

1. En **Configuración**, se selecciona la sede, se ajustan el pin y el radio y se guardan los cambios.
2. En **Personas → Nuevo**, se completan DNI y nombre. **Siguiente** valida el DNI y abre Biometría.
3. El rostro se añade con cámara o **Importar**. El botón **!** muestra las indicaciones. El registro y la actualización usan tres pasos automáticos; la asistencia solicita una sola posición frontal.
4. **Registrar huella** prepara el vínculo con la biometría ya configurada en Android. Solo se admite una persona vinculada por teléfono. Los cambios se confirman con **Guardar**.
5. En **Asistencia**, se elige Facial o Huella. La API valida identidad, zona, red opcional y duplicados antes de guardar.
6. Las flechas o el calendario permiten consultar otros días. Al pulsar una asistencia se abre su detalle; **Reporte → Excel** permite guardar el archivo.

Una baja retira el rostro y el vínculo del dispositivo, pero conserva el historial.

## Validaciones importantes

- El servidor exige `distancia a la sede + precisión GPS <= radio`. La incertidumbre del GPS no amplía el límite.
- La similitud facial mínima comienza en `0.80` y se rechazan coincidencias ambiguas. Ese puntaje no representa una probabilidad de identidad ni una precisión garantizada.
- La cámara controla postura, ojos, estabilidad, luz, nitidez y accesorios. Una foto importada sirve para registrar un rostro, no para acreditar presencia.
- Los ensayos de **Configuración → Probar reconocimiento facial** no generan asistencia ni entrenan automáticamente el modelo.
- La huella del teléfono autoriza una firma; la base conserva su clave pública vinculada al DNI, no una plantilla dactilar.

## Estructura del proyecto

```text
app/src/main/java/com/example/reconocimiento/
  domain/        Modelos, contratos y casos de uso
  data/          HTTP, IA, fotos, GPS y biometría
  ui/            Pantallas, diálogos y ViewModel
  MainActivity.kt  Permisos y composición de dependencias
backend/src/main/java/com/example/reconocimiento/
  api/           Endpoints y exportación Excel
  application/   Operaciones y transacciones
  domain/        Reglas y contratos de persistencia
  data/          Consultas JDBC
scripts/         Arranque, modelos y verificaciones
docs/capturas/   Imágenes anonimizadas de la aplicación
```

Android y el backend son ejecutables separados dentro del mismo repositorio. El APK contiene la app; el proceso Java de la PC expone la API y se conecta a PostgreSQL. La inferencia facial se realiza en Android y la comparación de identidad en el servidor.

`reconocimiento_app` contiene los datos de uso. `reconocimiento_verificacion` es una base aislada y desechable para pruebas. Las tablas se crean desde [schema.sql](backend/src/main/resources/db/schema.sql).

## Recorrido de una petición

El cliente es la aplicación Android. La API es la entrada HTTP del backend; PostgreSQL conserva los datos. Las llamadas entre funciones dentro de Android ocurren antes de enviar la petición.

```text
Android: pantalla → coordinación → repositorio móvil → HTTP/JSON
PC:      Tomcat → Spring MVC/Jackson → controller → servicio → repositorio JDBC
Datos:   driver PostgreSQL → PostgreSQL
Vuelta:  resultado SQL → objeto Java → JSON → objeto Kotlin → StateFlow → pantalla
```

### Ejemplo completo: marcar asistencia facial

1. **Se pulsa Facial.** [MainActivity.kt](app/src/main/java/com/example/reconocimiento/MainActivity.kt) recibe la acción de Compose y ejecuta una coroutine. `AppViewModel.work()` administra carga, errores y mensajes; el caso de uso [AttendanceActions.markFacial()](app/src/main/java/com/example/reconocimiento/domain/AttendanceActions.kt) coordina la marcación.
2. **Se comprueba la ubicación antes de abrir la cámara.** [DeviceLocation.current()](app/src/main/java/com/example/reconocimiento/data/DeviceLocation.kt) obtiene posición, precisión y antigüedad mediante Play services Location. El repositorio solicita `POST /api/retos` con propósito `FACIAL`. La API comprueba sede, radio y red, y guarda en `reconocimiento.reto` una solicitud temporal con identificador y vencimiento.
3. **Android captura y prepara el rostro.** CameraX entrega imágenes; ML Kit comprueba rostro y postura, y FaceAttribNet controla accesorios. [FaceEngine.embedding()](app/src/main/java/com/example/reconocimiento/data/FaceEngine.kt) ejecuta FaceNet con LiteRT y produce vectores de 512 valores. El modo de asistencia pide una posición frontal y reúne varias muestras coherentes.
4. **Se prepara una solicitud reciente.** Tras la captura, `markFacial()` obtiene otra ubicación y un nuevo reto para que la comparación utilice una posición reciente. [HttpAttendanceRepository.mark()](app/src/main/java/com/example/reconocimiento/data/HttpAttendanceRepository.kt) prepara el identificador del reto, modelo, vector promedio y foto, y envía `POST /api/asistencias`.
5. **Los datos viajan por HTTP.** `JSONObject` y `JSONArray` construyen el JSON; `HttpURLConnection` envía sus bytes UTF-8 en `Dispatchers.IO`, fuera del hilo principal. El emulador accede a la PC por `10.0.2.2`; el teléfono usa el túnel ADB indicado en **Conexión**.
6. **El backend recibe la petición.** Tomcat escucha en el puerto 8082. Spring MVC dirige la ruta al método `mark()` de [ApiController.java](backend/src/main/java/com/example/reconocimiento/api/ApiController.java), y Jackson convierte el JSON en el objeto Java `Proof`. El controlador obtiene también la IP de la conexión y delega en `AttendanceService.mark()`.
7. **El servicio decide si la marcación es válida.** [AttendanceService.java](backend/src/main/java/com/example/reconocimiento/application/AttendanceService.java) comprueba reto no utilizado/vigente, conexión, configuración, zona y modelo. [Rules.java](backend/src/main/java/com/example/reconocimiento/domain/Rules.java) compara el vector con las plantillas activas y aplica umbral y rechazo de ambigüedad. Se confirma que la persona esté activa y que todavía no tenga asistencia en esa fecha de Lima.
8. **Se consulta y guarda en PostgreSQL.** El servicio usa el contrato `AttendanceStore`, implementado por [JdbcAttendanceStore.java](backend/src/main/java/com/example/reconocimiento/data/JdbcAttendanceStore.java). `JdbcTemplate` ejecuta SQL parametrizado a través del driver PostgreSQL: consulta `reconocimiento.persona` y `reconocimiento.rostro`, inserta en `reconocimiento.asistencia` y marca el reto como usado dentro de la transacción. La marcación conserva identidad, instante, método, ubicación, similitud y fotos de evidencia. `UNIQUE(dni, fecha)` protege además contra duplicados concurrentes.
9. **La respuesta vuelve a Android.** El repositorio devuelve el registro con su ID; al completarse la transacción, el controlador responde con el objeto `Attendance` convertido a JSON por Jackson. `HttpAttendanceRepository` lee el JSON y lo transforma en un modelo Kotlin. La app vuelve a consultar el historial y abre el detalle de la marcación.
10. **Compose muestra el resultado confirmado.** [AppViewModel.kt](app/src/main/java/com/example/reconocimiento/ui/AppViewModel.kt) publica el nuevo `StateFlow`; la pantalla lo observa mediante `collectAsStateWithLifecycle()`. Un rechazo devuelve `codigo` y `mensaje`; Android lo convierte en `AppFailure` y muestra el motivo.

### Qué cambia al usar Huella

El recorrido HTTP → API → PostgreSQL es el mismo. Cambia la prueba de identidad:

1. `markFingerprint()` envía `POST /api/retos` con propósito `HUELLA`, ubicación y clave pública. El servidor identifica el vínculo registrado del teléfono.
2. [DeviceBiometrics.sign()](app/src/main/java/com/example/reconocimiento/data/DeviceBiometrics.kt) abre BiometricPrompt. Si Android autoriza, Keystore firma el mensaje del reto con la clave privada del dispositivo.
3. `POST /api/asistencias` envía reto y firma. El backend verifica la firma contra la clave pública vinculada y aplica las mismas reglas de zona, persona activa y asistencia diaria.

La huella no viaja al servidor. En esta modalidad se comprueba la autorización del dispositivo vinculado; la respuesta no incluye similitud facial.

### Registro, edición, consulta y reporte

Estas operaciones reutilizan repositorio móvil → controller → servicio → JDBC → PostgreSQL:

| Acción | Petición principal | Resultado |
| --- | --- | --- |
| Registrar persona | `POST /api/personas` | `AppViewModel.savePerson()` envía datos, fotos y plantillas preparadas; el servicio valida y devuelve la ficha guardada. |
| Editar persona | `PUT /api/personas/{dni}` | Se comprueba la versión de la ficha antes de actualizar; un conflicto evita sobrescribir cambios ajenos. |
| Dar de baja | `DELETE /api/personas/{dni}` | Se desactiva la persona y se retiran rostro/vínculo, conservando el historial. |
| Consultar un día | `GET /api/asistencias?fecha=AAAA-MM-DD` | La base devuelve las marcaciones de esa fecha. |
| Guardar sede | `PUT /api/configuracion` | Se persisten pin, radio, red y umbral; el mapa Leaflet permite preparar esos valores en Android. |
| Descargar Excel | `GET /api/asistencias/reporte.xlsx` | Apache POI genera el libro; Android lo guarda mediante el selector de documentos. |

La app vuelve a consultar configuración, personas y asistencias cada cinco segundos mientras está visible, y después de las operaciones correspondientes. Por eso los cambios de la base aparecen tras una nueva lectura de la API; no existe una conexión directa Android–PostgreSQL ni una notificación instantánea de la base.

## Comprobaciones

```powershell
.\scripts\maven.ps1 -MavenArguments @('test','package')
.\scripts\test-postgres.ps1
.\scripts\audit-storage.ps1
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug -PSTART_LOCAL_SERVICES=false
```

La última validación funcional pasó 42 pruebas de servidor, 33 unitarias Android y 36 instrumentadas. Las pruebas instrumentadas y sus imágenes se preparan únicamente en el emulador mediante `test-face-model.ps1` y `test-face-attributes.ps1`; el APK de pruebas no se instala en el teléfono de uso. Para las pruebas de cámara se utilizan cámaras emuladas independientes.

La auditoría de almacenamiento es de solo lectura. `backend/.env`, datos reales, informes locales y compilados están excluidos de Git. La entrega utiliza HTTP local en debug; no incluye autenticación administrativa por roles, despliegue público ni prueba de vida certificada.
