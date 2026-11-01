package com.example.reconocimiento

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.reconocimiento.ui.PhotoCropDialog
import com.example.reconocimiento.ui.theme.ReconocimientoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PhotoCropUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun cropSupportsZoomAndRotationAndWaitsForVisualReview() {
        val source=Bitmap.createBitmap(640,960,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(110,150,200))}
        var result: Bitmap?=null
        compose.setContent {ReconocimientoTheme {PhotoCropDialog(source,{result=it},{})}}
        compose.onNodeWithTag("validar-foto").assertIsNotEnabled()
        compose.onNodeWithText("Girar").performScrollTo().performClick()
        compose.onNodeWithTag("recorte-foto").performScrollTo().performTouchInput {swipeLeft()}
        compose.onNodeWithTag("revision-foto").performScrollTo().performClick()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"ui-recorte-galeria.png").outputStream().use {compose.onNode(isDialog()).captureToImage().let {img->
            img.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }}
        compose.onNodeWithTag("validar-foto").assertIsEnabled().performClick()
        compose.runOnIdle {assertEquals(512,result!!.width);assertEquals(512,result!!.height);assertFalse(result!!.isRecycled);result!!.recycle()}
    }
}
