package ru.reset.renplay.utils.decompiler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coordinates the decompilation of .rpyc files in a game directory.
 * Recursively searches for .rpyc/.rpymc files, decompiles them to .rpy/.rpym.
 *
 * Decompilation runs in parallel across all available CPU cores.
 */
object DecompilerManager {

    data class DecompileResult(
        val success: Int,
        val skipped: Int,
        val failed: Int,
        val errors: List<String>
    )

    /**
     * Decompile all .rpyc/.rpymc files in [gameDir] (recursively) in parallel.
     *
     * @param gameDir the root game directory to scan
     * @param overwrite if true, overwrite existing .rpy files
     * @param onProgress callback for progress: (completed files, total files, last file name).
     *                   Invoked from worker threads — callers must marshal to UI thread.
     * @return summary of results
     */
    suspend fun decompileAll(
        gameDir: File,
        overwrite: Boolean = false,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ): DecompileResult = coroutineScope {
        val files = withContext(Dispatchers.IO) { findRpycFiles(gameDir) }
        if (files.isEmpty()) {
            return@coroutineScope DecompileResult(0, 0, 0, listOf("No .rpyc files found"))
        }

        val total = files.size
        val completed = AtomicInteger(0)
        val success = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val errors = java.util.Collections.synchronizedList(mutableListOf<String>())

        // CPU-bound work: cap parallelism at number of cores to avoid GC/IO thrash.
        val parallelism = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val gate = Semaphore(parallelism)

        files.map { file ->
            async(Dispatchers.Default) {
                gate.withPermit {
                    try {
                        when (decompileSingle(file, overwrite)) {
                            SingleResult.OK -> success.incrementAndGet()
                            SingleResult.SKIPPED -> skipped.incrementAndGet()
                            SingleResult.FAILED -> {
                                failed.incrementAndGet()
                                errors.add("${file.name}: unknown error")
                            }
                        }
                    } catch (e: Exception) {
                        failed.incrementAndGet()
                        errors.add("${file.name}: ${e.message ?: e.javaClass.simpleName}")
                    }
                    val done = completed.incrementAndGet()
                    onProgress?.invoke(done, total, file.name)
                }
            }
        }.awaitAll()

        DecompileResult(success.get(), skipped.get(), failed.get(), errors.toList())
    }

    private enum class SingleResult { OK, SKIPPED, FAILED }

    private fun decompileSingle(inputFile: File, overwrite: Boolean): SingleResult {
        val ext = when (inputFile.extension) {
            "rpyc" -> "rpy"
            "rpymc" -> "rpym"
            else -> "rpy"
        }
        val outputFile = File(inputFile.parentFile, inputFile.nameWithoutExtension + ".$ext")

        if (!overwrite && outputFile.exists()) {
            return SingleResult.SKIPPED
        }

        // 1. Read and decompress rpyc
        val pickleData = RpycReader.readAstData(inputFile)

        // 2. Deserialize pickle -> AST
        val ast = RenpyPickle.loadAst(pickleData)

        // 3. Decompile AST -> rpy text (buffered to minimize syscalls)
        BufferedWriter(
            OutputStreamWriter(FileOutputStream(outputFile), Charsets.UTF_8),
            64 * 1024
        ).use { writer ->
            val decompiler = RenpyDecompiler(writer)
            decompiler.decompile(ast)
        }

        return SingleResult.OK
    }

    private fun findRpycFiles(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val result = ArrayList<File>(256)
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ext = file.extension
                if (ext == "rpyc" || ext == "rpymc") result.add(file)
            }
        }

        // Sort by size descending so the biggest files start first under parallel
        // execution — keeps all workers busy and minimizes overall wall time.
        result.sortByDescending { it.length() }
        return result
    }
}
