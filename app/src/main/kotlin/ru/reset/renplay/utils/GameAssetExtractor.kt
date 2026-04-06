package ru.reset.renplay.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.reset.renplay.utils.archive.RpaExtractor
import java.io.File

object GameAssetExtractor {
    private val mutex = Mutex()

    private val cacheSize = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    data class GameAssets(val iconPath: String?, val backgroundPath: String?)

    suspend fun getGameAssets(context: Context, projectPath: String): GameAssets {
        return mutex.withLock {
            val iconCacheDir = File(context.cacheDir, "project_icons").apply { mkdirs() }
            val bgCacheDir = File(context.cacheDir, "project_backgrounds").apply { mkdirs() }
            
            val hash = projectPath.hashCode().toString()
            val cachedIcon = File(iconCacheDir, "${hash}_icon.png")
            val cachedBg = File(bgCacheDir, "${hash}_bg.jpg")
            
            var finalIcon: String? = if (cachedIcon.exists()) cachedIcon.absolutePath else null
            var finalBg: String? = if (cachedBg.exists()) cachedBg.absolutePath else null
            
            if (finalIcon != null && finalBg != null) return GameAssets(finalIcon, finalBg)
            
            val gameDir = File(projectPath, "game")
            val meta = RenpyProjectParser.parse(projectPath)
            
            val iconTargets = mutableListOf<String>()
            if (meta.iconRelPath != null) iconTargets.add(meta.iconRelPath)
            iconTargets.addAll(listOf(
                "gui/window_icon.png",
                "gui/icon.png",
                "icon.png",
                "android-icon.png",
                "gui/window_icon.webp",
                "icon.webp"
            ))

            val bgTargets = mutableListOf<String>()
            if (meta.backgroundRelPath != null) bgTargets.add(meta.backgroundRelPath)
            bgTargets.addAll(listOf(
                "gui/main_menu.webm",
                "gui/main_menu.mp4",
                "gui/main_menu.png",
                "gui/main_menu.jpg",
                "gui/main_menu.webp",
                "gui/main_menu_background.png",
                "gui/main_menu_bg.png",
                "images/main_menu.png",
                "images/main_menu.webp",
                "images/bg/main_menu.jpg"
            ))

            if (finalIcon == null) {
                for (target in iconTargets) {
                    val f = File(gameDir, target)
                    if (f.exists()) {
                        finalIcon = f.absolutePath
                        break
                    }
                }
            }

            if (finalBg == null) {
                for (target in bgTargets) {
                    val f = File(gameDir, target)
                    if (f.exists()) {
                        if (target.endsWith(".webm") || target.endsWith(".mp4") || target.endsWith(".mkv")) {
                            try {
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(f.absolutePath)
                                val bmp = retriever.getFrameAtTime(0)
                                retriever.release()
                                if (bmp != null) {
                                    cachedBg.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                                    finalBg = cachedBg.absolutePath
                                }
                            } catch (e: Exception) {
                                Log.e("GameAssetExtractor", "Failed to extract frame from video file", e)
                            }
                        } else {
                            finalBg = f.absolutePath
                        }
                        break
                    }
                }
            }
            
            if (finalIcon == null || finalBg == null) {
                val rpaFiles = gameDir.listFiles { _, n -> n.endsWith(".rpa", true) } ?: emptyArray()
                for (rpa in rpaFiles) {
                    if (finalIcon == null) {
                        for (target in iconTargets) {
                            val bytes = RpaExtractor.extractSingleFileBytes(rpa, target)
                            if (bytes != null) {
                                try {
                                    cachedIcon.writeBytes(bytes)
                                    finalIcon = cachedIcon.absolutePath
                                    break
                                } catch (e: Exception) {}
                            }
                        }
                    }
                    if (finalBg == null) {
                        for (target in bgTargets) {
                            val bytes = RpaExtractor.extractSingleFileBytes(rpa, target)
                            if (bytes != null) {
                                if (target.endsWith(".webm") || target.endsWith(".mp4") || target.endsWith(".mkv")) {
                                    val tempVid = File(bgCacheDir, "${hash}_temp_vid${target.substring(target.lastIndexOf("."))}")
                                    try {
                                        tempVid.writeBytes(bytes)
                                        val retriever = MediaMetadataRetriever()
                                        retriever.setDataSource(tempVid.absolutePath)
                                        val bmp = retriever.getFrameAtTime(0)
                                        retriever.release()
                                        if (bmp != null) {
                                            cachedBg.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                                            finalBg = cachedBg.absolutePath
                                        }
                                    } catch (e: Exception) {
                                        Log.e("GameAssetExtractor", "Failed to extract frame from RPA video", e)
                                    } finally {
                                        tempVid.delete()
                                    }
                                } else {
                                    try {
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bmp != null) {
                                            cachedBg.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                                            finalBg = cachedBg.absolutePath
                                        }
                                    } catch (e: Exception) {}
                                }
                                if (finalBg != null) break
                            }
                        }
                    }
                    if (finalIcon != null && finalBg != null) break
                }
            }
            
            GameAssets(finalIcon, finalBg)
        }
    }

    suspend fun loadBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${path}_${reqWidth}x${reqHeight}"
        bitmapCache.get(cacheKey)?.let { return@withContext it }

        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeFile(path, options)
            if (bitmap != null) {
                bitmapCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            Log.e("GameAssetExtractor", "Failed to load bitmap", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}