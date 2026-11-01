package com.example.reconocimiento.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.reconocimiento.domain.PhotoCrop

@Composable
fun PhotoCropDialog(source: Bitmap,onComplete: (Bitmap)->Unit,onCancel: ()->Unit) {
    var rotation by remember(source) {mutableIntStateOf(0)}
    var crop by remember(source,rotation) {mutableStateOf(PhotoCrop())}
    var reviewed by remember(source) {mutableStateOf(false)}
    var viewport by remember {mutableFloatStateOf(1f)}
    val bitmap=remember(source,rotation) {
        if(rotation==0)source else Bitmap.createBitmap(source,0,0,source.width,source.height,
            Matrix().apply {postRotate(rotation.toFloat())},true)
    }
    val image=remember(bitmap) {bitmap.asImageBitmap()}
    val enoughResolution=crop.window(bitmap.width,bitmap.height).side>=256
    DisposableEffect(source){onDispose {source.recycle()}}
    DisposableEffect(bitmap){onDispose {if(bitmap!==source)bitmap.recycle()}}
    EditorDialog("Recortar y validar rostro",onCancel,true,actions={
        SecondaryAction("Cancelar",onCancel)
        PrimaryAction("Validar foto",{
            val w=crop.window(bitmap.width,bitmap.height)
            val cut=Bitmap.createBitmap(bitmap,w.left,w.top,w.side,w.side)
            val result=Bitmap.createScaledBitmap(cut,512,512,true)
            val owned=if(result===bitmap)result.copy(Bitmap.Config.ARGB_8888,false) else result
            if(cut!==bitmap&&cut!==owned)cut.recycle()
            onComplete(owned)
        },enabled=reviewed&&enoughResolution,modifier=Modifier.testTag("validar-foto"))
    }) {
        Text("Centra la cabeza completa. Arrastra la foto y usa el zoom para ajustar el recorte.",color=MutedBlue)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).testTag("recorte-foto")
            .onSizeChanged {viewport=it.width.toFloat()}
            .pointerInput(bitmap){detectTransformGestures {_,pan,zoom,_->
                crop=crop.moved(pan.x,pan.y,viewport,bitmap.width,bitmap.height)
                    .let {it.copy(zoom=(it.zoom*zoom).coerceIn(1f,4f)).bounded(bitmap.width,bitmap.height)}
            }}) {
            val w=crop.window(bitmap.width,bitmap.height)
            drawImage(image,IntOffset(w.left,w.top),IntSize(w.side,w.side),IntOffset.Zero,IntSize(size.width.toInt(),size.height.toInt()))
            for(i in 1..2){
                val f=i/3f
                drawLine(Color.White.copy(alpha=.4f),Offset(size.width*f,0f),Offset(size.width*f,size.height),1.dp.toPx())
                drawLine(Color.White.copy(alpha=.4f),Offset(0f,size.height*f),Offset(size.width,size.height*f),1.dp.toPx())
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Zoom",color=MutedBlue)
            Slider(crop.zoom,{crop=crop.copy(zoom=it).bounded(bitmap.width,bitmap.height)},valueRange=1f..4f,modifier=Modifier.weight(1f).testTag("zoom-foto"))
            TextButton({rotation=(rotation+90)%360}){Text("Girar")}
            TextButton({crop=PhotoCrop()}){Text("Restablecer")}
        }
        if(!enoughResolution)Text("El recorte necesita al menos 256 × 256 píxeles. Reduce el zoom o elige una foto de mayor resolución.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Surface(color=Color(0xFFF0F5FC),shape=MaterialTheme.shapes.small) {
            Row(Modifier.fillMaxWidth().clickable {reviewed=!reviewed}.padding(8.dp).testTag("revision-foto"),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(reviewed,{reviewed=it})
                Text("He comprobado que no hay manos ni otros objetos sobre el rostro.",style=MaterialTheme.typography.bodyMedium)
            }
        }
        Text("La app comprobará rostro frontal, calidad, lentes y mascarilla. Revisa visualmente otros objetos. Importar una foto no verifica presencia.",style=MaterialTheme.typography.bodySmall,color=MutedBlue)
    }
}
