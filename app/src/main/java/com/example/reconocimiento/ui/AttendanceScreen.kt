package com.example.reconocimiento.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.reconocimiento.domain.Attendance

private val lima = ZoneId.of("America/Lima")
private fun hour(value: String) = runCatching {
    Instant.parse(value).atZone(lima).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(value)

@Composable
fun AttendanceScreen(state: AppState, facial: () -> Unit, fingerprint: () -> Unit, configure: () -> Unit, detail: (Attendance)->Unit = {},selectDate: (String)->Unit = {},report: ()->Unit = {}) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(1000) } }
    val date = now.atZone(lima)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("lista-asistencia"), contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Asistencia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Marcar asistencia", style = MaterialTheme.typography.titleMedium, color = MutedBlue)
                    Text(date.format(DateTimeFormatter.ofPattern("HH:mm:ss")), fontSize = 46.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE"))).replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.bodyMedium, color = MutedBlue)
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            AppIcon(Symbol.Location, color = DeepBlue)
                            Text(if (state.site.latitude == null) "Zona sin configurar" else state.site.name,
                                style = MaterialTheme.typography.labelLarge, color = DeepBlue)
                        }
                        if (state.site.latitude != null) Text("Radio máximo ${state.site.radius.toInt()} m", style = MaterialTheme.typography.bodySmall, color = MutedBlue)
                        if (state.site.latitude == null) TextButton(configure, enabled = !state.loading) { Text("Configurar") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MethodAction("Facial", Symbol.Camera, facial, !state.loading, false,
                    Modifier.weight(1f).testTag("marcar-facial"))
                MethodAction("Huella", Symbol.Fingerprint, fingerprint, !state.loading, true,
                    Modifier.weight(1f).testTag("marcar-huella"))
            }
        }
        item {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Asistencias", Modifier.weight(1f))
                SecondaryAction("Reporte",report,Modifier.testTag("abrir-reporte"),!state.loading,icon=Symbol.Download)
            }
        }
        item {Column(verticalArrangement=Arrangement.spacedBy(6.dp)){AttendanceDateFilter(state.attendanceDate,!state.loading,selectDate)}}
        item {Text("${state.attendance.size} ${if(state.attendance.size==1)"marcación" else "marcaciones"}",style=MaterialTheme.typography.labelMedium,color=MutedBlue)}
        if(state.attendanceError!=null)item {Text(state.attendanceError,color=MaterialTheme.colorScheme.error)}
        else if (state.attendance.isEmpty()) item {
            Surface(color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Text(if(state.loading) "Consultando asistencias…" else "No hay asistencias para esta fecha.", Modifier.fillMaxWidth().padding(20.dp), color = MutedBlue, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(state.attendance, key = { it.dni }) { item ->
            Surface(onClick={ detail(item) }, modifier=Modifier.testTag("asistencia-${item.dni}"),color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(if (item.method == "FACIAL") Symbol.Camera else Symbol.Fingerprint)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text("DNI ${item.dni}", style = MaterialTheme.typography.bodySmall, color = MutedBlue)
                    }
                    Text(hour(item.instant), fontWeight = FontWeight.SemiBold, color = DeepBlue)
                    AppIcon(Symbol.ChevronRight,color=MutedBlue)
                }
            }
        }
    }
}

@Composable
fun AttendanceDetail(a: Attendance, close: ()->Unit) {
    val date=runCatching { Instant.parse(a.instant).atZone(lima) }.getOrNull()
    var tab by remember(a.id,a.instant) { mutableIntStateOf(0) }
    val tabs=if(a.method=="FACIAL")listOf("Resumen","Fotos","Ubicación") else listOf("Resumen","Ubicación")
    EditorDialog("Detalle de asistencia",close,true,actions={PrimaryAction("Cerrar",close)}) {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(a.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("DNI ${a.dni}",color=MutedBlue)
        }
        PrimaryTabRow(tab,containerColor=MaterialTheme.colorScheme.surface) {
            tabs.forEachIndexed {index,title->Tab(tab==index,{tab=index},text={Text(title,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)},modifier=Modifier.testTag("detalle-tab-$title"))}
        }
        when(tabs[tab]) {
            "Resumen" -> {
                DetailValue("Día",date?.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy",Locale.forLanguageTag("es-PE"))) ?: a.instant)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)){DetailValue("Hora",date?.format(DateTimeFormatter.ofPattern("HH:mm:ss"))?.plus(" (Lima)") ?: a.instant)}
                    Column(Modifier.weight(1f)){DetailValue("Método",if(a.method=="FACIAL") "Facial" else "Huella del teléfono")}
                }
                if(a.method=="FACIAL") {
                    Surface(color=Color(0xFFF0F5FC),shape=RoundedCornerShape(8.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text("Similitud",style=MaterialTheme.typography.labelMedium,color=MutedBlue)
                                Text(a.similarity?.let {String.format(Locale.US,"%.2f%%",it*100)} ?: "—",style=MaterialTheme.typography.headlineSmall,color=DeepBlue,fontWeight=FontWeight.Bold)
                                TextButton({tab=1},contentPadding=PaddingValues(0.dp),modifier=Modifier.testTag("ver-comparacion")){Text("Ver comparación")}
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text("Mínimo requerido",style=MaterialTheme.typography.labelMedium,color=MutedBlue)
                                Text(a.threshold?.let {String.format(Locale.US,"%.0f%%",it*100)} ?: "—",style=MaterialTheme.typography.headlineSmall,color=DeepBlue,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                    Text("Puntaje del modelo; no representa una probabilidad de identidad.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
                } else Text("Cuenta vinculada, confirmada con la biometría del teléfono.",style=MaterialTheme.typography.bodyMedium,color=MutedBlue)
            }
            "Fotos" -> FaceComparison(a)
            "Ubicación" -> {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)){DetailValue("Distancia a la sede",String.format(Locale.US,"%.1f m",a.distance))}
                    Column(Modifier.weight(1f)){DetailValue("Precisión GPS",String.format(Locale.US,"±%.1f m",a.accuracy))}
                }
                if(a.ip.isNotBlank())DetailValue("IP de conexión",a.ip)
                Text("La zona y la red se validaron al guardar esta marcación.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
            }
        }
    }
}

@Composable
private fun DetailValue(label: String,value: String) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MutedBlue)
        Text(value,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium)
    }
}

@Composable
private fun MethodAction(label: String, icon: Symbol, action: () -> Unit, enabled: Boolean, secondary: Boolean, modifier: Modifier) {
    Button(action, modifier.height(108.dp), enabled = enabled, shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (secondary) DeepBlue else MaterialTheme.colorScheme.primary,
            disabledContainerColor = Color(0xFFD9E7F5), disabledContentColor = MutedBlue)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(icon, Modifier.size(32.dp), color = if (enabled) Color.White else MutedBlue)
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
