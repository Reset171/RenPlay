package ru.reset.renplay.utils

import android.util.Log
import java.io.File

data class ProjectMetaData(
    val name: String?,
    val version: String?,
    val iconRelPath: String?,
    val backgroundRelPath: String?,
    val scriptVersion: String?
)

object RenpyProjectParser {
    fun parse(projectPath: String): ProjectMetaData {
        val gameDir = File(projectPath, "game")
        
        var name: String? = null
        var version: String? = null
        var iconRelPath: String? = null
        var bgRelPath: String? = null
        var scriptVersion: String? = null

        val svFile = File(gameDir, "script_version.txt")
        if (svFile.exists()) {
            try {
                val content = svFile.readText()
                val scanner = RpyScanner(content)
                if (scanner.findNext("(")) {
                    val inner = scanner.readUntil(')')
                    val parts = inner.split(',').map { it.trim() }
                    if (parts.size >= 3) {
                        scriptVersion = "${parts[0]}.${parts[1]}.${parts[2]}"
                    }
                }
            } catch (e: Exception) {
                Log.e("RenplayParser", "Failed to parse script_version.txt", e)
            }
        }

        var bgAlias: String? = null

        fun processFile(file: File) {
            if (!file.exists()) return
            try {
                val content = file.readText()
                val scanner = RpyScanner(content)
                
                var i = 0
                while (i < content.length) {
                    val defineIdx = content.indexOf("define ", i)
                    val imageIdx = content.indexOf("image ", i)
                    
                    if (defineIdx == -1 && imageIdx == -1) break
                    
                    val nextIdx = if (defineIdx != -1 && imageIdx != -1) minOf(defineIdx, imageIdx)
                                  else if (defineIdx != -1) defineIdx
                                  else imageIdx

                    if (nextIdx == defineIdx) {
                        scanner.pos = defineIdx + 7
                        val id = scanner.readIdentifier()
                        if (id == "config") {
                            if (scanner.findNext(".")) {
                                val prop = scanner.readIdentifier()
                                if (scanner.findNext("=")) {
                                    if (prop == "name" && name == null) name = scanner.readStringLiteral()
                                    else if (prop == "version" && version == null) version = scanner.readStringLiteral()
                                    else if (prop == "window_icon" && iconRelPath == null) iconRelPath = scanner.readStringLiteral()
                                }
                            }
                        } else if (id == "gui") {
                            if (scanner.findNext(".")) {
                                val prop = scanner.readIdentifier()
                                if (scanner.findNext("=")) {
                                    if (prop == "main_menu_background" && bgRelPath == null) {
                                        scanner.skipWhitespace()
                                        if (content.startsWith("Movie", scanner.pos)) {
                                            scanner.pos += 5
                                            scanner.skipWhitespace()
                                            if (scanner.findNext("(")) {
                                                val args = scanner.readUntil(')')
                                                val playIdx = args.indexOf("play")
                                                if (playIdx != -1) {
                                                    val eqIdx = args.indexOf('=', playIdx)
                                                    if (eqIdx != -1) {
                                                        var q = eqIdx + 1
                                                        while (q < args.length && args[q].isWhitespace()) q++
                                                        if (q < args.length && (args[q] == '"' || args[q] == '\'')) {
                                                            val quote = args[q]
                                                            val endQ = args.indexOf(quote, q + 1)
                                                            if (endQ != -1) {
                                                                bgRelPath = args.substring(q + 1, endQ)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            val strVal = scanner.readStringLiteral()
                                            if (strVal != null) {
                                                if (strVal.contains(".")) {
                                                    bgRelPath = strVal
                                                } else {
                                                    bgAlias = strVal
                                                }
                                            } else {
                                                val aliasIdent = scanner.readIdentifier()
                                                if (aliasIdent != null) {
                                                    bgAlias = aliasIdent
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        i = if (scanner.pos > defineIdx) scanner.pos else defineIdx + 7
                    } else {
                        scanner.pos = imageIdx + 6
                        val aliasName = scanner.readIdentifier()
                        if (aliasName != null && aliasName == bgAlias && bgRelPath == null) {
                            val eqIdx = content.indexOf('=', scanner.pos)
                            val colonIdx = content.indexOf(':', scanner.pos)
                            val nlIdx = content.indexOf('\n', scanner.pos).let { if (it == -1) content.length else it }
                            
                            var startVal = -1
                            if (eqIdx != -1 && eqIdx < nlIdx && colonIdx != -1 && colonIdx < nlIdx) {
                                startVal = minOf(eqIdx, colonIdx)
                            } else if (eqIdx != -1 && eqIdx < nlIdx) {
                                startVal = eqIdx
                            } else if (colonIdx != -1 && colonIdx < nlIdx) {
                                startVal = colonIdx
                            }
                            
                            if (startVal != -1) {
                                scanner.pos = startVal + 1
                                val strVal = scanner.readStringLiteral()
                                if (strVal != null) {
                                    bgRelPath = strVal
                                } else {
                                    scanner.skipWhitespace()
                                    val strValNext = scanner.readStringLiteral()
                                    if (strValNext != null) bgRelPath = strValNext
                                }
                            }
                        }
                        i = if (scanner.pos > imageIdx) scanner.pos else imageIdx + 6
                    }
                }
            } catch (e: Exception) {
                Log.e("RenplayParser", "Failed to process ${file.name}", e)
            }
        }

        processFile(File(gameDir, "options.rpy"))
        processFile(File(gameDir, "gui.rpy"))
        processFile(File(gameDir, "screens.rpy"))

        return ProjectMetaData(name, version, iconRelPath, bgRelPath, scriptVersion)
    }
}