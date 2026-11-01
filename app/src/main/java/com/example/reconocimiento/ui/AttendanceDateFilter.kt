package com.example.reconocimiento.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDateFilter(date: String,enabled: Boolean,select: (String)->Unit) {
    val today=LocalDate.parse(attendanceToday())
    val selected=LocalDate.parse(date)
    var calendar by remember {mutableStateOf(false)}
    Surface(color=androidx.compose.ui.graphics.Color(0xFFEAF1FA),shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton({select(selected.minusDays(1).toString())},enabled=enabled&&selected>LocalDate.of(1900,1,1),modifier=Modifier.testTag("asistencias-anterior")) {
                AppIcon(Symbol.ChevronLeft,description="Día anterior")
            }
            TextButton({calendar=true},Modifier.weight(1f).testTag("asistencias-calendario"),enabled=enabled) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(selected==today)"Hoy" else selected.format(DateTimeFormatter.ofPattern("EEEE",Locale.forLanguageTag("es-PE"))).replaceFirstChar {it.titlecase()},fontWeight=FontWeight.SemiBold)
                    Text(selected.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("es-PE"))),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("fecha-asistencias"))
                }
            }
            IconButton({select(selected.plusDays(1).toString())},enabled=enabled&&selected<today,modifier=Modifier.testTag("asistencias-siguiente")) {
                AppIcon(Symbol.ChevronRight,description="Día siguiente")
            }
        }
    }
    if(calendar)AttendanceDayPicker(date,"Fecha de asistencia",select,{calendar=false})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDayPicker(date: String,title: String,select: (String)->Unit,close: ()->Unit,confirmLabel: String="Ver asistencias") {
    val today=LocalDate.parse(attendanceToday())
    val selected=LocalDate.parse(date)
        val context=LocalContext.current
        val currentConfiguration=LocalConfiguration.current
        val spanishConfiguration=remember(currentConfiguration){Configuration(currentConfiguration).apply {setLocale(Locale.forLanguageTag("es-PE"))}}
        val spanishContext=remember(context,spanishConfiguration){context.createConfigurationContext(spanishConfiguration)}
        CompositionLocalProvider(LocalConfiguration provides spanishConfiguration,LocalContext provides spanishContext) {
        val state=rememberDatePickerState(initialSelectedDateMillis=selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange=1900..today.year,selectableDates=object: SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long)=Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()<=today
                override fun isSelectableYear(year: Int)=year<=today.year
            })
        DatePickerDialog(onDismissRequest={close()},confirmButton={
            TextButton({state.selectedDateMillis?.let {select(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())};close()},enabled=state.selectedDateMillis!=null){Text(confirmLabel)}
        },dismissButton={TextButton({close()}){Text("Cancelar")}}) {
            DatePicker(state,title={Text(title,Modifier.padding(start=24.dp,top=16.dp))},
                headline={
                    val chosen=state.selectedDateMillis?.let {Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()}
                    Text(chosen?.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("es-PE")))?.lowercase(Locale.forLanguageTag("es-PE")) ?: "Selecciona una fecha",
                        Modifier.padding(start=24.dp,end=8.dp,bottom=12.dp).testTag("fecha-calendario"),style=MaterialTheme.typography.headlineLarge)
                },showModeToggle=true)
        }
        }

}
