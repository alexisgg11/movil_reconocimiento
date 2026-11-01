package com.example.reconocimiento.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.reconocimiento.data.PhotoInspection
import com.example.reconocimiento.data.PhotoInspector
import com.example.reconocimiento.domain.Attendance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

@Composable
fun FaceComparison(a: Attendance) {
    if(a.photo.isBlank()||a.referencePhoto.isBlank()) {
        if(a.photo.isNotBlank())Photo(a.photo,a.name,Modifier.fillMaxWidth().height(180.dp))
        Text("Esta marcación no conserva ambas fotos. La comparación estará disponible en las nuevas marcaciones.",color=MutedBlue)
        return
    }
    var error by remember(a.id){mutableStateOf(false)}
    val pair by produceState<Pair<PhotoInspection,PhotoInspection>?>(null,a.photo,a.referencePhoto) {
        var first: PhotoInspection?=null;var second: PhotoInspection?=null
        try {
            val inspector=PhotoInspector()
            withContext(Dispatchers.Default){first=inspector.inspect(a.photo);second=inspector.inspect(a.referencePhoto)}
            value=first!! to second!!
            awaitCancellation()
        } catch(e: CancellationException){throw e}
        catch(_: Exception){error=true}
        finally {first?.close();second?.close()}
    }
    val photos=pair
    if(photos==null) {
        if(error)Text("No se pudieron analizar estas fotos. Intenta abrir la comparación otra vez.",color=MaterialTheme.colorScheme.error)
        else {LinearProgressIndicator(Modifier.fillMaxWidth());Text("Analizando las capturas…",color=MutedBlue)}
        return
    }
    var overlay by remember {mutableStateOf(false)}
    var opacity by remember {mutableFloatStateOf(.5f)}
    var markers by remember {mutableStateOf(true)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        InspectionPhoto(photos.first,"Marcación",markers,Modifier.weight(1f))
        InspectionPhoto(photos.second,when(a.referenceKind){"ROSTRO"->"Registro facial";"GALERIA"->"Foto importada";else->"Foto de ficha"},markers,Modifier.weight(1f))
    }
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text("Mostrar puntos de referencia",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Switch(markers,{markers=it})
    }
    val firstAligned=photos.first.aligned;val secondAligned=photos.second.aligned
    if(firstAligned!=null&&secondAligned!=null) {
        FilterChip(overlay,{overlay=!overlay},label={Text("Superponer fotos")},modifier=Modifier.testTag("superponer-fotos"))
        if(overlay) {
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEDF1F6)),contentAlignment=Alignment.Center) {
                Image(secondAligned.asImageBitmap(),"Registro alineado",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                Image(firstAligned.asImageBitmap(),"Marcación alineada",Modifier.fillMaxSize(),alpha=opacity,contentScale=ContentScale.Fit)
            }
            Slider(opacity,{opacity=it})
            Text("Fotos alineadas por los ojos. Registro ← desliza → Marcación",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
        }
    }
    val light=abs(photos.first.light-photos.second.light)
    CaptureDifference("Iluminación",String.format(Locale.US,"Diferencia %.0f/255",light),light>25)
    val yaw=photos.first.yaw?.let {x->photos.second.yaw?.let {abs(x-it)}}
    val tilt=photos.first.tilt?.let {x->photos.second.tilt?.let {abs(x-it)}}
    CaptureDifference("Giro de cabeza",yaw?.let {String.format(Locale.US,"Diferencia %.1f°",it)} ?: "No medible",yaw?.let {it>12})
    CaptureDifference("Inclinación",tilt?.let {String.format(Locale.US,"Diferencia %.1f°",it)} ?: "No medible",tilt?.let {it>10})
    Text("Mediciones orientativas de la captura. No indican cuánto aportó cada rasgo al puntaje facial.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
    if(a.referenceKind=="FICHA")Text("Este registro antiguo conserva la foto de ficha, que puede ser distinta de la usada para registrar el rostro.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
}

@Composable
private fun InspectionPhoto(photo: PhotoInspection,label: String,markers: Boolean,modifier: Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MutedBlue)
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEDF1F6))) {
            Image(photo.bitmap.asImageBitmap(),label,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            if(markers)Canvas(Modifier.fillMaxSize()) {
                val scale=minOf(size.width/photo.bitmap.width,size.height/photo.bitmap.height)
                val w=photo.bitmap.width*scale;val h=photo.bitmap.height*scale
                photo.points.forEach {(x,y)->
                    val p=Offset((size.width-w)/2+x*w,(size.height-h)/2+y*h)
                    drawCircle(Color.White,4.dp.toPx(),p);drawCircle(DeepBlue,2.5.dp.toPx(),p)
                }
            }
        }
    }
}

@Composable
private fun CaptureDifference(label: String,value: String,different: Boolean?) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        AppIcon(if(different==false)Symbol.Check else Symbol.Warning,color=if(different==true)Color(0xFF996000) else MutedBlue)
        Column(Modifier.weight(1f)) {
            Text(label,style=MaterialTheme.typography.bodyMedium)
            Text(value,style=MaterialTheme.typography.bodySmall,color=MutedBlue)
        }
        Text(when(different){true->"Revisar";false->"Similar";null->"Sin dato"},style=MaterialTheme.typography.labelMedium,color=if(different==true)Color(0xFF996000) else MutedBlue)
    }
}
