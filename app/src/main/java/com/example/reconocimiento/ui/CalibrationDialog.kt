package com.example.reconocimiento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reconocimiento.domain.*
import java.util.Locale

@Composable
fun CalibrationDialog(people: List<Person>,loading: Boolean,summary: TrialSummary,result: FaceTestResult?,
                      test: (String?)->Unit,close: ()->Unit) {
    val registered=people.filter {it.face}
    var known by remember {mutableStateOf(true)}
    var selected by remember {mutableStateOf(registered.firstOrNull()?.dni)}
    var expanded by remember {mutableStateOf(false)}
    var guide by remember {mutableStateOf(false)}
    val expected=if(known) selected else null
    val selectedPerson=registered.find {it.dni==selected}
    EditorDialog("Prueba facial",close,!loading,actions={
        SecondaryAction("Cerrar",close,enabled=!loading)
        PrimaryAction("Probar rostro",{test(expected)},enabled=!loading&&registered.isNotEmpty()&&(!known||selectedPerson!=null),icon=Symbol.Camera)
    }) {
        Text("Elige quién estará frente a la cámara. Esta prueba no marca asistencia.",style=MaterialTheme.typography.bodyMedium,color=MutedBlue)
        TextButton({guide=!guide},modifier=Modifier.testTag("guia-pruebas")) {Text(if(guide) "Ocultar guía" else "Cómo hacer las pruebas")}
        if(guide)Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(8.dp)) {
            Text("1. Registra primero todos los rostros que vas a evaluar.\n\n2. En Registrada, selecciona la ficha correcta y haz 10 intentos nuevos por persona. Cambia suavemente la luz y la distancia.\n\n3. En No registrada, prueba también a personas que no estén en el sistema.\n\n4. Mantén los mismos registros y umbral durante la ronda. Revisa los errores del resumen.",Modifier.padding(14.dp),style=MaterialTheme.typography.bodyMedium,color=DeepBlue)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(known,{known=true},label={Text("Registrada")},enabled=!loading)
            FilterChip(!known,{known=false},label={Text("No registrada")},enabled=!loading)
        }
        if(known)Box {
            OutlinedButton({expanded=true},Modifier.fillMaxWidth(),enabled=!loading&&registered.isNotEmpty(),shape=RoundedCornerShape(8.dp)) {
                Text(selectedPerson?.let {"${it.name}\nDNI ${it.dni}"} ?: "Primero registra un rostro",Modifier.weight(1f))
                AppIcon(Symbol.ChevronDown)
            }
            DropdownMenu(expanded,{expanded=false}) {
                registered.forEach {person->DropdownMenuItem(text={Text("${person.name} · ${person.dni}")},onClick={selected=person.dni;expanded=false})}
            }
        } else Text("Usa el rostro de alguien que no figure en Personas.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
        if(registered.isEmpty())Text("Registra al menos un rostro antes de probar.",color=MaterialTheme.colorScheme.error)
        result?.takeIf {it.expectedDni==expected}?.let {r->
            val correct=r.outcome=="COINCIDENCIA_CORRECTA"||r.outcome=="RECHAZO_CORRECTO"
            Surface(color=if(correct)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(8.dp),modifier=Modifier.testTag("resultado-prueba")) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(when(r.outcome){
                        "COINCIDENCIA_CORRECTA" -> "Persona reconocida correctamente"
                        "RECHAZO_CORRECTO" -> "Persona no registrada: rechazada correctamente"
                        "FALSO_RECHAZO" -> "Persona registrada no reconocida"
                        "FALSA_ACEPTACION" -> "Se aceptó a una persona no registrada"
                        else -> "Se reconoció a otra persona"
                    },fontWeight=FontWeight.SemiBold,color=if(correct)DeepBlue else MaterialTheme.colorScheme.error)
                    if(r.outcome=="COINCIDENCIA_CORRECTA") {
                        Text("Similitud con la persona elegida: ${String.format(Locale.US,"%.2f%%",(r.expectedScore ?: r.score)*100)}",style=MaterialTheme.typography.bodyMedium)
                        Text("Mínimo requerido: ${String.format(Locale.US,"%.0f%%",r.threshold*100)}",style=MaterialTheme.typography.bodySmall)
                    } else if(r.matchedDni==null)Text("Persona no reconocida. No se encontró una coincidencia válida con los rostros registrados.",style=MaterialTheme.typography.bodyMedium)
                    if(r.outcome=="IDENTIDAD_INCORRECTA")Text("DNI reconocido: ${r.matchedDni}",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider()
        SectionTitle("Resumen de pruebas")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)){Text("Realizadas",style=MaterialTheme.typography.labelMedium,color=MutedBlue);Text("${summary.total}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
            Column(Modifier.weight(1f)){Text("Correctas",style=MaterialTheme.typography.labelMedium,color=MutedBlue);Text("${summary.correct}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=DeepBlue)}
        }
        Text("Falsos rechazos: ${summary.falseReject}\nFalsas aceptaciones: ${summary.falseAccept}\nPersonas confundidas: ${summary.wrongIdentity}",style=MaterialTheme.typography.bodyMedium)
        Text("Resumen del control facial, umbral y rostros actuales. Los ensayos anteriores se conservan por separado.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
    }
}
