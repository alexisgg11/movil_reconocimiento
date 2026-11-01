package com.example.reconocimiento

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.example.reconocimiento.data.GalleryFaceEnrollment
import com.example.reconocimiento.data.PhotoImporter
import com.example.reconocimiento.domain.AppFailure
import android.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GalleryEnrollmentTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun galleryRejectsAFeaturelessImageAndRequiresVisualReview()=runBlocking {
        val image=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.GRAY)
        try {
            try {GalleryFaceEnrollment(context).prepare(image,false);fail("Falta revisión visual")}catch(_: AppFailure){}
            try {GalleryFaceEnrollment(context).prepare(image,true);fail("No hay rostro")}catch(e: AppFailure){assertEquals("SIN_ROSTRO",e.code)}
        } finally {image.recycle()}
    }
    @Test fun galleryDecodesExifOrientationBeforeTheCrop()=runBlocking {
        val path=File(context.cacheDir,"gallery-exif-test.jpg")
        val original=Bitmap.createBitmap(640,320,Bitmap.Config.ARGB_8888)
        try {
            path.outputStream().use {original.compress(Bitmap.CompressFormat.JPEG,85,it)}
            ExifInterface(path).apply {setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
            val decoded=PhotoImporter(context).load(Uri.fromFile(path))
            try {assertEquals(320,decoded.width);assertEquals(640,decoded.height)}finally {decoded.recycle()}
        } finally {original.recycle();path.delete()}
    }
    @Test fun galleryDetectsAllFacesRatherThanChoosingOneSilently()=runBlocking {
        val file=File(context.getExternalFilesDir(null),"model-fixtures/img1.jpg")
        assertTrue("Falta la imagen de prueba fijada en scripts/test-face-model.ps1",file.isFile)
        val source=BitmapFactory.decodeFile(file.path)
        val size=512
        val square=Bitmap.createScaledBitmap(source,size,size,true)
        val group=Bitmap.createBitmap(size*2,size,Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(group).apply {drawBitmap(square,0f,0f,null);drawBitmap(square,size.toFloat(),0f,null)}
        try {
            try {GalleryFaceEnrollment(context).prepare(group,true);fail("No debe elegir entre dos caras")}catch(e: AppFailure){assertEquals("VARIOS_ROSTROS",e.code)}
        } finally {group.recycle();if(square!==source)square.recycle();source.recycle()}
    }
    @Test fun frontalPhotoProducesOneRealEmbeddingAndSeparateProfileAndAlignedPhotos()=runBlocking {
        val source=BitmapFactory.decodeFile(File(context.getExternalFilesDir(null),"model-fixtures/img1.jpg").path)
        val side=minOf(source.width,source.height)
        val cut=Bitmap.createBitmap(source,0,0,side,side)
        val bitmap=Bitmap.createScaledBitmap(cut,512,512,true)
        try {
            val capture=GalleryFaceEnrollment(context).prepare(bitmap,true)
            assertEquals("GALERIA",capture.source);assertTrue(capture.visuallyReviewed)
            assertEquals(1,capture.vectors.size);assertEquals(512,capture.vectors.single().size)
            assertEquals(1.0,capture.vectors.single().sumOf {it.toDouble()*it},.0001)
            for((photo,expected) in listOf(capture.photo to 160,capture.profilePhoto to 512)) {
                val bytes=Base64.decode(photo,Base64.DEFAULT)
                val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                try {assertEquals(expected,image.width);assertEquals(expected,image.height)}finally {image.recycle()}
            }
        } finally {if(bitmap!==cut)bitmap.recycle();if(cut!==source)cut.recycle();source.recycle()}
    }
    @Test fun tiltedFaceIsRejectedBeforeAFeatureVectorIsAccepted()=runBlocking {
        val bitmap=BitmapFactory.decodeFile(File(context.getExternalFilesDir(null),"model-fixtures/img11.jpg").path)
        try {
            try {GalleryFaceEnrollment(context).prepare(bitmap,true);fail("La cabeza está inclinada")}catch(e: AppFailure){assertEquals("FOTO_NO_FRONTAL",e.code)}
        } finally {bitmap.recycle()}
    }
    @Test fun galleryRejectsEyeglassesSunglassesAndMasksBeforeEnrolling()=runBlocking {
        for(name in listOf("eyeglasses","sunglasses_tinted","mask")) {
            val bitmap=BitmapFactory.decodeFile(File(context.getExternalFilesDir(null),"attribute-fixtures/$name.jpg").path)
            try {
                try {GalleryFaceEnrollment(context).prepare(bitmap,true);fail("No debe registrar $name")}catch(e: AppFailure){assertEquals("ROSTRO_CUBIERTO",e.code)}
            } finally {bitmap.recycle()}
        }
    }
}
