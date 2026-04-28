package ru.reset.renplay.utils.decompiler

import java.io.File
import java.util.zip.Inflater

/**
 * Reads .rpyc files (RENPY RPC2 archive format or legacy zlib-compressed format)
 * and extracts the raw pickle data containing the AST.
 */
object RpycReader {

    class RpycException(message: String) : Exception(message)

    /**
     * Reads the raw pickle bytes from a .rpyc file.
     * Supports both RENPY RPC2 (v2) format and legacy (v1) format.
     */
    fun readAstData(file: File): ByteArray {
        val rawContents = file.readBytes()
        val contents = extractSlotData(rawContents)
        return decompressZlib(contents)
    }

    private fun extractSlotData(rawContents: ByteArray): ByteArray {
        // Check for RENPY RPC2 header
        if (rawContents.size >= 10 && String(rawContents, 0, 10, Charsets.US_ASCII) == "RENPY RPC2") {
            return extractRpc2Slot(rawContents)
        }
        // Legacy format: entire file is zlib-compressed
        return rawContents
    }

    private fun extractRpc2Slot(data: ByteArray): ByteArray {
        var position = 10
        val chunks = mutableMapOf<Int, ByteArray>()

        while (position + 12 <= data.size) {
            val slot = readInt32LE(data, position)
            val start = readInt32LE(data, position + 4)
            val length = readInt32LE(data, position + 8)

            if (slot == 0) break

            position += 12

            if (start + length <= data.size) {
                chunks[slot] = data.copyOfRange(start, start + length)
            }
        }

        return chunks[1] ?: throw RpycException(
            "Unable to find slot 1 in RENPY RPC2 file. Header structure may be modified."
        )
    }

    private fun decompressZlib(data: ByteArray): ByteArray {
        try {
            val inflater = Inflater()
            inflater.setInput(data)

            var result = ByteArray(data.size * 4)
            var totalOut = 0
            val buffer = ByteArray(8192)

            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                if (totalOut + count > result.size) {
                    result = result.copyOf(result.size * 2)
                }
                System.arraycopy(buffer, 0, result, totalOut, count)
                totalOut += count
            }
            inflater.end()

            return result.copyOfRange(0, totalOut)
        } catch (e: Exception) {
            throw RpycException("Failed to decompress zlib data: ${e.message}")
        }
    }

    private fun readInt32LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
