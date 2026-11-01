package com.example.reconocimiento.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.reconocimiento.domain.Position
import com.example.reconocimiento.domain.Site
import com.example.reconocimiento.domain.NetworkInfo
import com.example.reconocimiento.domain.ZoneCheck
import java.util.Locale

@Composable
fun ConfigurationScreen(site: Site, loading: Boolean, save: (Site) -> Unit,
                        locate: ((Position) -> Unit) -> Unit, sensorSettings: () -> Unit,
                        dirtyChanged: (Boolean) -> Unit,networkInfo: ((NetworkInfo)->Unit)->Unit = {},
                        zoneCheck: ZoneCheck?=null,checkLocation: ()->Unit={},calibrate: ()->Unit={}) {
    var name by rememberSaveable { mutableStateOf(site.name) }
    var latitude by rememberSaveable { mutableStateOf(site.latitude) }
    var longitude by rememberSaveable { mutableStateOf(site.longitude) }
    var radius by rememberSaveable { mutableStateOf(site.radius.toInt().toString()) }
    var network by rememberSaveable { mutableStateOf(site.network) }
    var threshold by rememberSaveable { mutableStateOf((site.threshold * 100).toInt().toFloat()) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var originalVersion by rememberSaveable { mutableStateOf(site.version) }
    var externalChange by remember { mutableStateOf(false) }
    var restrictNetwork by rememberSaveable { mutableStateOf(site.network.isNotBlank()) }
    var connection by remember { mutableStateOf<NetworkInfo?>(null) }
    var manual by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dirty) { dirtyChanged(dirty) }

    fun load() {
        name = site.name; latitude = site.latitude; longitude = site.longitude
        radius = site.radius.toInt().toString(); network = site.network
        restrictNetwork=site.network.isNotBlank();connection=null
        threshold = (site.threshold * 100).toInt().toFloat()
        originalVersion = site.version; dirty = false; externalChange = false
    }
    LaunchedEffect(site) {
        val matches = name.trim() == site.name && latitude == site.latitude && longitude == site.longitude &&
            radius.toDoubleOrNull() == site.radius && (if(restrictNetwork) network.trim() else "") == site.network && threshold.toInt() == (site.threshold * 100).toInt()
        if (!dirty || matches) load() else externalChange = originalVersion != site.version
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Configuración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (externalChange) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hay cambios guardados en otro lugar.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton({ load() }) { Text("Cargar") }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle("Zona de asistencia")
                    OutlinedTextField(name, { name = it.take(100); dirty = true }, label = { Text("Nombre de la sede *") },
                        singleLine = true, enabled = !loading, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
                    PrimaryAction("Usar mi ubicación", {
                        locate { point -> latitude = point.latitude; longitude = point.longitude; dirty = true }
                    }, Modifier.fillMaxWidth(), !loading, secondary = true, icon = Symbol.Location)
                    Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(8.dp)).testTag("mapa-zona")) {
                        ZoneMap(latitude, longitude, radius.toDoubleOrNull()?.coerceIn(1.0, 10000.0) ?: 100.0,
                            { lat, lon -> if (!loading) { latitude = lat; longitude = lon; dirty = true } }, Modifier.fillMaxSize())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Punto de la sede", style = MaterialTheme.typography.labelMedium, color = MutedBlue)
                            Text(if (latitude == null || longitude == null) "Sin seleccionar" else
                                String.format(Locale.US, "%.6f, %.6f", latitude, longitude), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton({ manual = true }, enabled = !loading) { Text("Editar") }
                    }
                    OutlinedTextField(radius, { radius = it.filter(Char::isDigit).take(5); dirty = true },
                        label = { Text("Radio máximo *") }, suffix = { Text("m") }, singleLine = true, enabled = !loading,
                        shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("radio"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(50, 100, 200).forEach { meters ->
                            FilterChip(radius == meters.toString(), { radius = meters.toString(); dirty = true },
                                label = { Text("$meters m") }, enabled = !loading)
                        }
                    }
                    SecondaryAction("Comprobar mi ubicación",checkLocation,Modifier.fillMaxWidth().testTag("comprobar-zona"),!loading&&!dirty&&site.latitude!=null,icon=Symbol.Location)
                    if(dirty)Text("Guarda la sede para comprobar la ubicación.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
                    if(!dirty)zoneCheck?.let {result->
                        var details by remember(result){mutableStateOf(false)}
                        Surface(color=if(result.allowed)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(8.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(if(result.allowed) "Ubicación permitida" else "Marcación bloqueada",color=if(result.allowed)DeepBlue else MaterialTheme.colorScheme.error)
                                Text(result.explanation(),style=MaterialTheme.typography.bodyMedium)
                                TextButton({details=!details},contentPadding=PaddingValues(0.dp),modifier=Modifier.testTag("detalles-zona")){Text(if(details)"Ocultar detalles" else "Detalles")}
                                if(details){
                                    Text("Distancia: ${result.distance?.let {String.format(Locale.US,"%.1f m",it)} ?: "—"} · Precisión: ±${result.accuracy.toInt()} m",style=MaterialTheme.typography.bodySmall)
                                    Text("Radio: ${result.radius.toInt()} m · Red: ${if(result.networkAllowed) "permitida" else "no permitida"}",style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Biometría")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Similitud facial mínima", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${threshold.toInt()}%", color = DeepBlue, fontWeight = FontWeight.Bold)
                    }
                    Slider(threshold, { threshold = it; dirty = true }, valueRange = 80f..99f, steps = 18, enabled = !loading,
                        onValueChangeFinished = { threshold = threshold.toInt().toFloat() })
                    Text("Puntaje del modelo. Aumentarlo exige mayor similitud y puede rechazar más capturas válidas.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
                    SecondaryAction("Probar reconocimiento facial",calibrate,Modifier.fillMaxWidth(),!loading&&!dirty,icon=Symbol.Camera)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(Symbol.Fingerprint)
                        Column(Modifier.weight(1f)) {
                            Text("Huella digital", fontWeight = FontWeight.Medium)
                            Text("Verificación fuerte por marcación", style = MaterialTheme.typography.bodySmall, color = MutedBlue)
                        }
                        TextButton(sensorSettings, enabled = !loading) { Text("Configurar") }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Red permitida", Modifier.weight(1f))
                        Switch(restrictNetwork,{restrictNetwork=it;dirty=true},enabled=!loading,modifier=Modifier.testTag("limitar-red"))
                    }
                    if (restrictNetwork) {
                        OutlinedTextField(network, { network = it.take(50); dirty = true;connection=null }, label = { Text("IP o red autorizada") },
                            placeholder = { Text("192.168.1.0/24") }, singleLine = true, enabled = !loading,
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().testTag("red-autorizada"))
                        Text("Wi-Fi: puedes indicar una IP o toda la red, por ejemplo 192.168.1.0/24.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
                        SecondaryAction("Usar conexión actual",{networkInfo {connection=it;network=it.serverIp;dirty=true}},Modifier.fillMaxWidth(),!loading,icon=Symbol.Network)
                        connection?.let { info ->
                            Text("IP de esta conexión: ${info.serverIp}. Guarda para aplicar.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
                        }
                    } else Text("Sin restricción de red. La ubicación sigue siendo obligatoria.", style = MaterialTheme.typography.bodySmall, color = MutedBlue)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PrimaryAction("Guardar configuración", {
                    val r = radius.toDoubleOrNull()
                    error = when {
                        name.isBlank() -> "Escribe el nombre de la sede."
                        latitude == null || longitude == null -> "Selecciona la ubicación de la sede en el mapa."
                        r == null || r !in 1.0..10000.0 -> "El radio debe estar entre 1 y 10 000 m."
                        restrictNetwork&&network.isBlank() -> "Escribe una IP o usa la conexión actual."
                        else -> null
                    }
                    if (error == null) save(Site(name.trim(), latitude, longitude, r!!, if(restrictNetwork)network.trim() else "", threshold.toInt() / 100.0, site.revision, originalVersion))
                }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), !loading)
            }
        }
    }
    if (manual) {
        var lat by remember { mutableStateOf(latitude?.toString().orEmpty()) }
        var lon by remember { mutableStateOf(longitude?.toString().orEmpty()) }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { manual = false }, title = { Text("Ubicación de la sede") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(lat, { lat = it }, label = { Text("Latitud") }, singleLine = true,
                        modifier = Modifier.testTag("manual-lat"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(lon, { lon = it }, label = { Text("Longitud") }, singleLine = true,
                        modifier = Modifier.testTag("manual-lon"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (invalid) Text("Escribe coordenadas válidas.", color = MaterialTheme.colorScheme.error)
                }
            }, confirmButton = {
                TextButton({
                    val a = lat.replace(',', '.').toDoubleOrNull(); val b = lon.replace(',', '.').toDoubleOrNull()
                    invalid = a == null || b == null || !a.isFinite() || !b.isFinite() || a !in -90.0..90.0 || b !in -180.0..180.0
                    if (!invalid) { latitude = a; longitude = b; dirty = true; manual = false }
                }) { Text("Aplicar") }
            }, dismissButton = { TextButton({ manual = false }) { Text("Cancelar") } })
    }
}
