package ru.reset.renplay.domain

import android.content.Context
import android.net.Uri
import android.os.Build
import ru.reset.renplay.domain.models.EnginePlugin
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class EngineManager(private val context: Context) {
    val enginesDir = File(context.filesDir, "engines").apply { mkdirs() }

    fun getInstalledEngines(): List<EnginePlugin> {
        val list = mutableListOf<EnginePlugin>()
        enginesDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val txt = File(dir, "renplay-plugin.txt")
                if (txt.exists()) {
                    val ver = txt.readText().trim().removePrefix("renpy=")
                    list.add(EnginePlugin(ver, dir.absolutePath, File(dir, "plugin.zip").absolutePath))
                }
            }
        }
        return list
    }

    fun getEngine(version: String?): EnginePlugin? {
        val engines = getInstalledEngines()
        if (engines.isEmpty()) return null

        if (version != null) {
            val exactMatch = engines.find { it.version == version }
            if (exactMatch != null) return exactMatch

            val parts = version.split(".")
            if (parts.size >= 2) {
                val prefix = "${parts[0]}.${parts[1]}."
                val minorMatch = engines.filter { it.version.startsWith(prefix) }.maxByOrNull { it.version }
                if (minorMatch != null) return minorMatch

                val majorPrefix = "${parts[0]}."
                val majorMatch = engines.filter { it.version.startsWith(majorPrefix) }.maxByOrNull { it.version }
                if (majorMatch != null) return majorMatch
            }
        }
        return engines.maxByOrNull { it.version }
    }

    fun installEngine(uri: Uri): Boolean {
        try {
            val tempFile = File(context.cacheDir, "temp_engine.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }

            var version = ""
            ZipFile(tempFile).use { zip ->
                val entry = zip.getEntry("renplay-plugin.txt") ?: return false
                version = zip.getInputStream(entry).bufferedReader().readText().trim().removePrefix("renpy=")
            }

            if (version.isEmpty()) return false

            val targetDir = File(enginesDir, version)
            targetDir.mkdirs()

            val zipTarget = File(targetDir, "plugin.zip")
            val libDir = File(targetDir, "lib").apply { mkdirs() }
            val abi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" || it == "x86_64" } ?: "armeabi-v7a"

            ZipFile(tempFile).use { zip ->
                java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(FileOutputStream(zipTarget))).use { zout ->
                    zip.entries().asSequence().forEach { entry ->
                        when {
                            entry.name == "renplay-plugin.txt" -> {
                                val out = File(targetDir, "renplay-plugin.txt")
                                zip.getInputStream(entry).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                            }
                            entry.name == "assets/private.mp3" -> {
                                val out = File(targetDir, "private.mp3")
                                zip.getInputStream(entry).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                            }
                            entry.name.startsWith("jniLibs/") -> {
                                if (entry.name.startsWith("jniLibs/$abi/") && entry.name.endsWith(".so")) {
                                    val soName = File(entry.name).name
                                    val out = File(libDir, soName)
                                    zip.getInputStream(entry).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                                }
                            }
                            else -> {
                                val newEntry = java.util.zip.ZipEntry(entry.name)
                                zout.putNextEntry(newEntry)
                                if (!entry.isDirectory) {
                                    zip.getInputStream(entry).use { input -> input.copyTo(zout) }
                                }
                                zout.closeEntry()
                            }
                        }
                    }
                }
            }
            tempFile.delete()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun deleteEngine(version: String) {
        File(enginesDir, version).deleteRecursively()
    }
}