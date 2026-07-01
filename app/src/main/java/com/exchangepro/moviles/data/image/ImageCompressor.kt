package com.exchangepro.moviles.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class CompressedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val contentType: String = "image/jpeg"
)

object ImageCompressor {
    private const val MAX_DIMENSION = 1280
    private const val MAX_BYTES = 500 * 1024
    private const val MIN_QUALITY = 40

    /**
     * Lee una imagen elegida por el usuario, corrige orientacion y reduce dimensiones/
     * calidad hasta producir un JPEG apto para el limite documental de Firestore.
     */
    suspend fun compress(context: Context, uri: Uri): CompressedImage = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        var bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val originalWidth = info.size.width
            val originalHeight = info.size.height
            val largestDimension = max(originalWidth, originalHeight)
            if (largestDimension > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toDouble() / largestDimension.toDouble()
                decoder.setTargetSize(
                    (originalWidth * scale).roundToInt().coerceAtLeast(1),
                    (originalHeight * scale).roundToInt().coerceAtLeast(1)
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

        bitmap = bitmap.withWhiteBackground()
        var quality = 82
        var encoded = bitmap.toJpeg(quality)

        while (encoded.size > MAX_BYTES && quality > MIN_QUALITY) {
            quality -= 7
            encoded = bitmap.toJpeg(quality)
        }

        while (encoded.size > MAX_BYTES && max(bitmap.width, bitmap.height) > 640) {
            val nextWidth = (bitmap.width * 0.82).roundToInt().coerceAtLeast(1)
            val nextHeight = (bitmap.height * 0.82).roundToInt().coerceAtLeast(1)
            bitmap = Bitmap.createScaledBitmap(bitmap, nextWidth, nextHeight, true)
            encoded = bitmap.toJpeg(quality)
        }

        require(encoded.size <= MAX_BYTES) {
            "La imagen no pudo reducirse por debajo de 500 KB."
        }

        CompressedImage(
            bytes = encoded,
            width = bitmap.width,
            height = bitmap.height
        )
    }

    private fun Bitmap.withWhiteBackground(): Bitmap {
        if (!hasAlpha()) return this
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).apply {
                drawColor(Color.WHITE)
                drawBitmap(this@withWhiteBackground, 0f, 0f, null)
            }
        }
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().use { stream ->
            check(compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                "No se pudo comprimir la imagen."
            }
            stream.toByteArray()
        }
}
