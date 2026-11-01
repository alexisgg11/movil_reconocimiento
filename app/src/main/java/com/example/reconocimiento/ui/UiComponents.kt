package com.example.reconocimiento.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val DeepBlue = Color(0xFF215789)
val MutedBlue = Color(0xFF59758E)

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                  enabled: Boolean = true, secondary: Boolean = false, icon: Symbol? = null) {
    val background = if (secondary) DeepBlue else MaterialTheme.colorScheme.primary
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background,
            disabledContainerColor = Color(0xFFD9E7F5), disabledContentColor = MutedBlue),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        icon?.let {
            AppIcon(it, color = if (enabled) Color.White else MutedBlue)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                    enabled: Boolean = true, icon: Symbol? = null) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepBlue),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        icon?.let { AppIcon(it, color = DeepBlue); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Photo(photo: String, name: String, modifier: Modifier = Modifier) {
    val bitmap = remember(photo) {
        runCatching {
            if (photo.isBlank()) null else {
                val bytes = Base64.decode(photo, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
    }
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFE8F0F9)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, "Foto de $name", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (name.isBlank()) AppIcon(Symbol.Person, Modifier.size(30.dp), color = MutedBlue)
        else Text(name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
            style = MaterialTheme.typography.titleLarge, color = DeepBlue, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IdentitySummary(photo: String, name: String, dni: String, changePhoto: (() -> Unit)? = null) {
    Surface(Modifier.fillMaxWidth().testTag("ficha-en-vivo"), color = Color(0xFFF0F5FC), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Photo(photo, name, Modifier.size(72.dp, 82.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name.ifBlank { "Nombre completo" }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(Symbol.IdCard, Modifier.size(17.dp), color = MutedBlue)
                    Text("DNI ${dni.ifBlank { "--------" }}", style = MaterialTheme.typography.bodyMedium, color = MutedBlue)
                }
                changePhoto?.let {
                    TextButton(it, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 32.dp)) {
                        Text(if (photo.isBlank()) "Añadir foto" else "Cambiar foto", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun EditorDialog(title: String, onClose: () -> Unit, enabled: Boolean,
                 actions: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (enabled) onClose() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        IconButton(onClose, enabled = enabled) { AppIcon(Symbol.Close, description = "Cerrar ficha") }
                    }
                    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = actions)
                }
            }
        }
    }
}
