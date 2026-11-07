package com.skypet.pet.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 把用户选择的图片复制到应用私有目录（filesDir/pets/），并给出读取优先级。
 * FROZEN by CONTRACT §4.4.
 *
 * 读取优先级：用户导入图 > drawable(-nodpi)/pet_whale.png > 矢量（返回 null 由 PetArt 兜底）。
 */
class PetImageStore(private val context: Context) {

    private val prefs: Prefs = Prefs(context)
    private val maxImageBytes = 12L * 1024 * 1024
    private val maxImagePixels = 16_000_000L
    private val targetImagePx = 512

    /** 私有目录：filesDir/pets/ */
    fun petsDir(): File {
        val dir = File(context.filesDir, "pets")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 从 content Uri 导入：字节复制到 filesDir/pets/imported_<ts>.<ext>，
     * 返回新文件名（存入 Prefs.importedImageName）；失败返回 null。
     * 不抠图、不裁剪；显示时按比例缩放（fitCenter）。
     */
    fun importFromUri(uri: Uri): String? {
        val ext = guessExtension(uri)
        val fileName = "imported_${System.currentTimeMillis()}.$ext"
        val dest = File(petsDir(), fileName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8192)
                    var count = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > maxImageBytes) throw IOException("Image exceeds size limit")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return null
            if (dest.length() == 0L || imageBounds(dest) == null) {
                dest.delete()
                return null
            }
            val oldName = prefs.importedImageName
            prefs.importedImageName = fileName
            if (oldName != null && oldName != fileName) File(petsDir(), oldName).delete()
            return fileName
        } catch (_: Exception) {
            dest.delete()
            return null
        }
    }

    /** 清除导入图（删文件 + Prefs 置空）。 */
    fun clearImported() {
        val name = prefs.importedImageName
        if (!name.isNullOrEmpty()) {
            try {
                File(petsDir(), name).takeIf { it.exists() }?.delete()
            } catch (_: Throwable) {
            }
        }
        prefs.importedImageName = null
    }

    /** 当前导入图 File（存在才返回），否则 null。 */
    fun currentImportedFile(): File? {
        val name = prefs.importedImageName ?: return null
        val f = File(petsDir(), name)
        return if (f.exists() && f.length() > 0) f else null
    }

    /**
     * 读取优先级（供 PetArt 使用）：
     * 1) 导入图（Prefs.importedImageName 且文件存在）
     * 2) res/drawable(-nodpi)/pet_whale.png（存在则用）
     * 3) null → 调用方回退矢量 WhaleGirlArt
     */
    fun loadPetBitmap(): Bitmap? {
        // 1) user-imported image
        currentImportedFile()?.let { file ->
            val bmp = try {
                decodeSampled(file)
            } catch (t: Throwable) {
                null
            }
            if (bmp != null) return bmp
        }

        // 2) bundled pet_whale drawable — dynamic lookup avoids a hard compile-time
        //    reference to a resource that may not exist (CONTRACT §4.4 / §5.2).
        val resId = try {
            context.resources.getIdentifier("pet_whale", "drawable", context.packageName)
        } catch (t: Throwable) {
            0
        }
        if (resId != 0) {
            try {
                val drawable = ContextCompat.getDrawable(context, resId)
                if (drawable != null) return drawableToBitmap(drawable)
            } catch (t: Throwable) {
                // fall through to vector
            }
        }

        // 3) no bitmap → caller uses vector WhaleGirlArt
        return null
    }

    private fun imageBounds(file: File): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth
        val height = options.outHeight
        return if (width > 0 && height > 0 && width.toLong() * height <= maxImagePixels) {
            width to height
        } else null
    }

    private fun decodeSampled(file: File): Bitmap? {
        val (width, height) = imageBounds(file) ?: return null
        var sample = 1
        while (maxOf(width / sample, height / sample) > targetImagePx * 2) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
        })
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 256
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    private fun guessExtension(uri: Uri): String {
        val mime = try {
            context.contentResolver.getType(uri)
        } catch (t: Throwable) {
            null
        }
        return when {
            mime == null -> "png"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "png"
        }
    }
}
