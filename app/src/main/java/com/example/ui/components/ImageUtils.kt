package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

const val PRESET_EMERALD_PACK = "preset:emerald"
const val PRESET_TWILIGHT_PACK = "preset:twilight"

@Composable
fun rememberPackImageModel(imageUrl: String): Any {
    return remember(imageUrl) {
        when {
            imageUrl == PRESET_EMERALD_PACK -> R.drawable.img_default_pack_1794860089473
            imageUrl == PRESET_TWILIGHT_PACK -> R.drawable.img_hero_banner_1794860088941
            imageUrl.startsWith("data:image/") -> {
                val base64Part = imageUrl.substringAfter("base64,", "")
                if (base64Part.isNotEmpty()) {
                    try {
                        Base64.decode(base64Part, Base64.DEFAULT)
                    } catch (_: Exception) {
                        R.drawable.img_default_pack_1794860089473
                    }
                } else {
                    R.drawable.img_default_pack_1794860089473
                }
            }
            imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> imageUrl
            else -> R.drawable.img_default_pack_1794860089473
        }
    }
}

suspend fun compressImageUriToDataUrl(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val maxDim = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 720) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return@withContext null

        val scale = if (max(decoded.width, decoded.height) > 640) {
            640f / max(decoded.width, decoded.height).toFloat()
        } else {
            1f
        }
        val finalBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }

        val outputStream = ByteArrayOutputStream()
        var quality = 78
        do {
            outputStream.reset()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            quality -= 12
        } while (outputStream.size() > 140_000 && quality >= 30)

        val encoded = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        if (finalBitmap !== decoded) finalBitmap.recycle()
        decoded.recycle()
        "data:image/jpeg;base64,$encoded"
    } catch (_: Exception) {
        null
    }
}
