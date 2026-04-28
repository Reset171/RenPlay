package ru.reset.renplay.utils.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Inflater

object RpaExtractor {

    data class ExtractResult(
        val archives: Int,
        val files: Int,
        val skipped: Int,
        val failed: Int,
        val errors: List<String>
    )

    /**
     * Extract all `.rpa` archives found inside [gameDir] (recursively).
     * Files are written next to the archives, preserving relative paths from the
     * archive index. Existing files are skipped unless [overwrite] is true.
     *
     * Archives are processed in parallel; reads within a single archive are sequential
     * (single shared RandomAccessFile per archive).
     *
     * @param onProgress (completedArchives, totalArchives, archiveName) — called from
     *                   worker threads, callers must marshal to UI thread.
     */
    suspend fun extractAll(
        gameDir: File,
        overwrite: Boolean = false,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ): ExtractResult = coroutineScope {
        val archives = withContext(Dispatchers.IO) { findRpaFiles(gameDir) }
        if (archives.isEmpty()) {
            return@coroutineScope ExtractResult(0, 0, 0, 0, emptyList())
        }

        val total = archives.size
        val completed = AtomicInteger(0)
        val filesExtracted = AtomicInteger(0)
        val filesSkipped = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val errors = java.util.Collections.synchronizedList(mutableListOf<String>())

        val parallelism = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val gate = Semaphore(parallelism)

        archives.map { archive ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    try {
                        val stats = extractArchive(archive, archive.parentFile ?: gameDir, overwrite)
                        filesExtracted.addAndGet(stats.extracted)
                        filesSkipped.addAndGet(stats.skipped)
                    } catch (e: Exception) {
                        failed.incrementAndGet()
                        errors.add("${archive.name}: ${e.message ?: e.javaClass.simpleName}")
                    }
                    val done = completed.incrementAndGet()
                    onProgress?.invoke(done, total, archive.name)
                }
            }
        }.awaitAll()

        ExtractResult(
            archives = total - failed.get(),
            files = filesExtracted.get(),
            skipped = filesSkipped.get(),
            failed = failed.get(),
            errors = errors.toList()
        )
    }

    private data class ArchiveStats(val extracted: Int, val skipped: Int)

    @Suppress("UNCHECKED_CAST")
    private fun extractArchive(rpaFile: File, outputDir: File, overwrite: Boolean): ArchiveStats {
        var extracted = 0
        var skipped = 0

        RandomAccessFile(rpaFile, "r").use { raf ->
            val (offset, key, version) = readRpaHeader(raf)
                ?: throw IllegalStateException("Unsupported or invalid RPA archive")

            raf.seek(offset)
            val compressedSize = (raf.length() - offset).toInt()
            val compressedData = ByteArray(compressedSize)
            raf.readFully(compressedData)

            val finalData = inflateAll(compressedData)
            val index = MiniPickle(finalData).load() as? Map<String, List<Any>>
                ?: throw IllegalStateException("Failed to parse archive index")

            val readBuffer = ByteArray(64 * 1024)

            for ((fileName, blocks) in index) {
                val targetFile = File(outputDir, fileName.replace("\\", "/"))
                if (!overwrite && targetFile.exists()) {
                    skipped++
                    continue
                }
                targetFile.parentFile?.mkdirs()

                BufferedOutputStream(FileOutputStream(targetFile), 64 * 1024).use { out ->
                    for (blockInfo in blocks) {
                        val blockList = blockInfo as? List<Any> ?: continue
                        val bOffset: Long
                        val bLength: Long
                        var prefix: ByteArray = ByteArray(0)

                        if (version == 3) {
                            bOffset = (blockList[0] as Number).toLong() xor key
                            bLength = (blockList[1] as Number).toLong() xor key
                            if (blockList.size > 2) {
                                prefix = blockList[2] as? ByteArray ?: ByteArray(0)
                            }
                        } else {
                            bOffset = (blockList[0] as Number).toLong()
                            bLength = (blockList[1] as Number).toLong()
                        }

                        if (prefix.isNotEmpty()) out.write(prefix)

                        raf.seek(bOffset)
                        var remaining = bLength
                        while (remaining > 0L) {
                            val chunk = if (remaining < readBuffer.size) remaining.toInt() else readBuffer.size
                            raf.readFully(readBuffer, 0, chunk)
                            out.write(readBuffer, 0, chunk)
                            remaining -= chunk
                        }
                    }
                }
                extracted++
            }
        }

        return ArchiveStats(extracted, skipped)
    }

    private data class RpaHeader(val offset: Long, val key: Long, val version: Int)

    private fun readRpaHeader(raf: RandomAccessFile): RpaHeader? {
        raf.seek(0)
        val headerBuilder = StringBuilder()
        while (true) {
            val b = raf.read()
            if (b == -1 || b == '\n'.code || b == '\r'.code) break
            headerBuilder.append(b.toChar())
        }
        val header = headerBuilder.toString().trim()

        return when {
            header.startsWith("RPA-3.0 ") || header.startsWith("RPA-3.2 ") -> {
                val parts = header.substring(8).trim().split(' ').filter { it.isNotEmpty() }
                if (parts.isEmpty()) return null
                val offset = parts[0].toLong(16)
                var key = 0L
                for (i in 1 until parts.size) key = key xor parts[i].toLong(16)
                RpaHeader(offset, key, 3)
            }
            header.startsWith("RPA-2.0 ") -> {
                val parts = header.substring(8).trim().split(' ').filter { it.isNotEmpty() }
                if (parts.isEmpty()) return null
                RpaHeader(parts[0].toLong(16), 0L, 2)
            }
            else -> null
        }
    }

    private fun inflateAll(compressedData: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(compressedData)
            var out = ByteArray(compressedData.size * 4)
            var total = 0
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                if (total + count > out.size) {
                    out = out.copyOf(out.size * 2)
                }
                System.arraycopy(buffer, 0, out, total, count)
                total += count
            }
            return out.copyOfRange(0, total)
        } finally {
            inflater.end()
        }
    }

    private fun findRpaFiles(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val result = ArrayList<File>(8)
        dir.walkTopDown().forEach { f ->
            if (f.isFile && f.extension == "rpa") result.add(f)
        }
        // Bigger archives first → balance parallel workers.
        result.sortByDescending { it.length() }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    fun extractSingleFileBytes(rpaFile: File, targetFileName: String): ByteArray? {
        try {
            RandomAccessFile(rpaFile, "r").use { raf ->
                val headerBuilder = StringBuilder()
                while (true) {
                    val b = raf.read()
                    if (b == -1 || b == '\n'.code || b == '\r'.code) break
                    headerBuilder.append(b.toChar())
                }
                val header = headerBuilder.toString().trim()

                var offset = 0L
                var key = 0L
                var version = 0

                if (header.startsWith("RPA-3.0 ") || header.startsWith("RPA-3.2 ")) {
                    version = 3
                    val parts = header.substring(8).trim().split(' ').filter { it.isNotEmpty() }
                    if (parts.isNotEmpty()) {
                        offset = parts[0].toLong(16)
                        for (i in 1 until parts.size) {
                            key = key xor parts[i].toLong(16)
                        }
                    } else return null
                } else if (header.startsWith("RPA-2.0 ")) {
                    version = 2
                    val parts = header.substring(8).trim().split(' ').filter { it.isNotEmpty() }
                    if (parts.isNotEmpty()) {
                        offset = parts[0].toLong(16)
                    } else return null
                } else return null

                raf.seek(offset)
                val compressedData = ByteArray((raf.length() - offset).toInt())
                raf.readFully(compressedData)

                val inflater = Inflater()
                inflater.setInput(compressedData)

                var decompressedData = ByteArray(compressedData.size * 4)
                var totalDecompressed = 0
                val buffer = ByteArray(8192)

                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0) break
                    if (totalDecompressed + count > decompressedData.size) {
                        decompressedData = decompressedData.copyOf(decompressedData.size * 2)
                    }
                    System.arraycopy(buffer, 0, decompressedData, totalDecompressed, count)
                    totalDecompressed += count
                }
                inflater.end()

                val finalData = decompressedData.copyOfRange(0, totalDecompressed)
                val index = MiniPickle(finalData).load() as? Map<String, List<Any>> ?: return null

                val blocks = index[targetFileName] ?: return null

                var totalFileSize = 0
                for (blockInfo in blocks) {
                    val blockList = blockInfo as? List<Any> ?: continue
                    val bLength = if (version == 3) {
                        (blockList[1] as Number).toLong() xor key
                    } else {
                        (blockList[1] as Number).toLong()
                    }
                    var prefixLength = 0
                    if (version == 3 && blockList.size > 2) {
                        prefixLength = (blockList[2] as? ByteArray)?.size ?: 0
                    }
                    totalFileSize += bLength.toInt() + prefixLength
                }

                val resultBytes = ByteArray(totalFileSize)
                var currentPos = 0

                for (blockInfo in blocks) {
                    val blockList = blockInfo as? List<Any> ?: continue
                    val bOffset: Long
                    val bLength: Long
                    var prefix = ByteArray(0)

                    if (version == 3) {
                        bOffset = (blockList[0] as Number).toLong() xor key
                        bLength = (blockList[1] as Number).toLong() xor key
                        if (blockList.size > 2) {
                            prefix = blockList[2] as? ByteArray ?: ByteArray(0)
                        }
                    } else {
                        bOffset = (blockList[0] as Number).toLong()
                        bLength = (blockList[1] as Number).toLong()
                    }

                    if (prefix.isNotEmpty()) {
                        System.arraycopy(prefix, 0, resultBytes, currentPos, prefix.size)
                        currentPos += prefix.size
                    }

                    raf.seek(bOffset)
                    val fileData = ByteArray(bLength.toInt())
                    raf.readFully(fileData)
                    System.arraycopy(fileData, 0, resultBytes, currentPos, fileData.size)
                    currentPos += fileData.size
                }

                return resultBytes
            }
        } catch (e: Exception) {
            return null
        }
    }
}

class MiniPickle(private val data: ByteArray) {
    private var pos = 0
    private val stack = mutableListOf<Any?>()
    private val memo = mutableMapOf<Int, Any?>()
    private val marks = mutableListOf<Int>()

    private fun readByte(): Int = data[pos++].toInt() and 0xFF
    
    private fun readBytes(n: Int): ByteArray { 
        val r = data.copyOfRange(pos, pos + n)
        pos += n
        return r 
    }
    
    private fun readInt32(): Int {
        val b = readBytes(4)
        return (b[0].toInt() and 0xFF) or 
               ((b[1].toInt() and 0xFF) shl 8) or 
               ((b[2].toInt() and 0xFF) shl 16) or 
               ((b[3].toInt() and 0xFF) shl 24)
    }

    private fun readUInt16(): Int {
        val b = readBytes(2)
        return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)
    }

    private fun readLong8(): Long {
        val b = readBytes(8)
        var res = 0L
        for (i in 0 until 8) {
            res = res or ((b[i].toLong() and 0xFFL) shl (i * 8))
        }
        return res
    }

    private fun readLongLE(n: Int): Long {
        if (n == 0) return 0L
        var res = 0L
        for (i in 0 until n) {
            val b = readByte().toLong()
            res = res or (b shl (i * 8))
        }
        val lastByte = data[pos - 1].toInt()
        if (lastByte and 0x80 != 0) {
            for (i in n until 8) {
                res = res or (0xFFL shl (i * 8))
            }
        }
        return res
    }
    
    private fun readLine(): String {
        val start = pos
        while (pos < data.size && data[pos] != '\n'.code.toByte()) pos++
        val r = String(data, start, pos - start, Charsets.US_ASCII)
        pos++
        if (r.endsWith("\r")) return r.substring(0, r.length - 1)
        return r
    }

    private fun unquote(s: String): String {
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length - 1).replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return s
    }

    @Suppress("UNCHECKED_CAST")
    fun load(): Any? {
        while (pos < data.size) {
            val opcode = readByte()
            when (opcode) {
                0x80 -> pos += 1
                0x95 -> pos += 8
                0x28 -> marks.add(stack.size)
                0x2E -> return stack.removeLast()
                0x4E -> stack.add(null)
                0x88 -> stack.add(true)
                0x89 -> stack.add(false)
                0x49 -> {
                    val s = readLine()
                    stack.add(if (s == "01") true else if (s == "00") false else s.toLong())
                }
                0x4A -> stack.add(readInt32())
                0x4B -> stack.add(readByte())
                0x4D -> stack.add(readUInt16())
                0x8A -> stack.add(readLongLE(readByte()))
                0x8B -> stack.add(readLongLE(readInt32()))
                0x4C -> {
                    var s = readLine()
                    if (s.endsWith("L")) s = s.substring(0, s.length - 1)
                    stack.add(s.toLong())
                }
                0x54 -> stack.add(String(readBytes(readInt32()), Charsets.UTF_8))
                0x55 -> stack.add(String(readBytes(readByte()), Charsets.UTF_8))
                0x58 -> stack.add(String(readBytes(readInt32()), Charsets.UTF_8))
                0x8C -> stack.add(String(readBytes(readByte()), Charsets.UTF_8))
                0x8D -> stack.add(String(readBytes(readLong8().toInt()), Charsets.UTF_8))
                0x42 -> stack.add(readBytes(readInt32()))
                0x43 -> stack.add(readBytes(readByte()))
                0x53, 0x56 -> {
                    val s = readLine()
                    stack.add(unquote(s))
                }
                0x5D -> stack.add(mutableListOf<Any?>())
                0x6C -> {
                    val mark = marks.removeLast()
                    val list = stack.subList(mark, stack.size).toMutableList()
                    while(stack.size > mark) stack.removeLast()
                    stack.add(list)
                }
                0x7D -> stack.add(mutableMapOf<String, Any?>())
                0x64 -> {
                    val mark = marks.removeLast()
                    val dict = mutableMapOf<String, Any?>()
                    for (i in mark until stack.size step 2) {
                        dict[stack[i] as String] = stack[i+1]
                    }
                    while(stack.size > mark) stack.removeLast()
                    stack.add(dict)
                }
                0x61 -> {
                    val v = stack.removeLast()
                    (stack.last() as MutableList<Any?>).add(v)
                }
                0x65 -> {
                    val mark = marks.removeLast()
                    val l = stack[mark - 1] as MutableList<Any?>
                    l.addAll(stack.subList(mark, stack.size))
                    while (stack.size > mark) stack.removeLast()
                }
                0x73 -> {
                    val v = stack.removeLast()
                    val k = stack.removeLast() as String
                    (stack.last() as MutableMap<String, Any?>)[k] = v
                }
                0x75 -> {
                    val mark = marks.removeLast()
                    val m = stack[mark - 1] as MutableMap<String, Any?>
                    for (i in mark until stack.size step 2) {
                        m[stack[i] as String] = stack[i + 1]
                    }
                    while (stack.size > mark) stack.removeLast()
                }
                0x74 -> {
                    val mark = marks.removeLast()
                    val tuple = stack.subList(mark, stack.size).toList()
                    while (stack.size > mark) stack.removeLast()
                    stack.add(tuple)
                }
                0x85 -> { val v = stack.removeLast(); stack.add(listOf(v)) }
                0x86 -> { val v2 = stack.removeLast(); val v1 = stack.removeLast(); stack.add(listOf(v1, v2)) }
                0x87 -> { val v3 = stack.removeLast(); val v2 = stack.removeLast(); val v1 = stack.removeLast(); stack.add(listOf(v1, v2, v3)) }
                0x71 -> memo[readByte()] = stack.last()
                0x72 -> memo[readInt32()] = stack.last()
                0x68 -> stack.add(memo[readByte()])
                0x6A -> stack.add(memo[readInt32()])
                0x94 -> { memo[memo.size] = stack.last() }
                0x31 -> stack.removeLast()
                else -> throw IllegalStateException("Unknown pickle opcode: 0x${opcode.toString(16).padStart(2, '0').uppercase()} at position ${pos - 1}")
            }
        }
        return null
    }
}
