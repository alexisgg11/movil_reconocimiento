package com.example.reconocimiento.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reconocimiento.domain.Attendance
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AttendanceReportDialog(selectedDate: String,busy: Boolean,
                           load: suspend (String?,String?)->List<Attendance>,
                           download: (String?,String?)->Unit,close: ()->Unit) {
    var all by remember {mutableStateOf(true)}
    var from by remember {mutableStateOf(LocalDate.parse(selectedDate).withDayOfMonth(1).toString())}
    var to by remember {mutableStateOf(selectedDate)}
    var picking by remember {mutableStateOf<String?>(null)}
    var rows by remember {mutableStateOf<List<Attendance>?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var refresh by remember {mutableIntStateOf(0)}
    val start=if(all)null else from
    val end=if(all)null else to
    val invalid=!all&&from>to
    LaunchedEffect(start,end,refresh) {
        rows=null;error=null
        if(!invalid)try {rows=load(start,end)}catch(e: CancellationException){throw e}
        catch(e: Exception){error=e.message ?: "No se pudo cargar el reporte."}
    }
    EditorDialog("Reporte de asistencias",close,!busy,actions={
        SecondaryAction("Cerrar",close,enabled=!busy)
        PrimaryAction("Excel",{download(start,end)},Modifier.testTag("descargar-excel"),
            enabled=!busy&&!invalid&&rows!=null,icon=Symbol.Download)
    }) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(all,{all=true},label={Text("Todo el historial")},enabled=!busy)
            FilterChip(!all,{all=false},label={Text("Por período")},enabled=!busy)
        }
        if(!all)Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            ReportDate("Desde",from,{picking="desde"},!busy,Modifier.weight(1f))
            ReportDate("Hasta",to,{picking="hasta"},!busy,Modifier.weight(1f))
        }
        if(invalid)Text("La fecha inicial debe ser anterior o igual a la final.",color=MaterialTheme.colorScheme.error)
        else if(error!=null){
            Text(error!!,color=MaterialTheme.colorScheme.error)
            SecondaryAction("Reintentar",{refresh++},enabled=!busy)
        } else if(rows==null)LinearProgressIndicator(Modifier.fillMaxWidth())
        else {
            val data=rows.orEmpty()
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${data.size} ${if(data.size==1)"marcación" else "marcaciones"}",Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                TextButton({refresh++},enabled=!busy){Text("Actualizar")}
            }
            Text("El Excel incluye fecha, hora, persona, método, similitud y ubicación.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
            if(data.isEmpty())Text("No hay marcaciones en este período.",color=MutedBlue)
            data.take(100).forEach {a->
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(a.name,fontWeight=FontWeight.SemiBold)
                    Text("DNI ${a.dni}",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
                    Text(runCatching {Instant.parse(a.instant).atZone(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm"))}.getOrDefault(a.instant)+" · "+if(a.method=="FACIAL")"Facial" else "Huella",style=MaterialTheme.typography.bodyMedium,color=DeepBlue)
                    HorizontalDivider(Modifier.padding(top=8.dp))
                }
            }
            if(data.size>100)Text("Vista previa: primeras 100 marcaciones. El Excel incluye las ${data.size}.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
        }
    }
    picking?.let {kind->
        AttendanceDayPicker(if(kind=="desde")from else to,"${if(kind=="desde")"Inicio" else "Fin"} del período",{
            if(kind=="desde")from=it else to=it
            picking=null
        },{picking=null},confirmLabel="Seleccionar")
    }
}

@Composable
private fun ReportDate(label: String,value: String,click: ()->Unit,enabled: Boolean,modifier: Modifier) {
    Surface(onClick=click,enabled=enabled,modifier=modifier,shape=RoundedCornerShape(8.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(label,style=MaterialTheme.typography.labelMedium,color=MutedBlue)
            Text(LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),style=MaterialTheme.typography.bodyMedium)
        }
    }
}
