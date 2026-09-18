package com.example.engine

import com.example.data.model.GgufMetadata
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GgufParser {
    private const val GGUF_MAGIC = 0x46554747 // 'G' 'G' 'U' 'F' in little endian

    // GGUF Value Types
    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    fun parse(file: File): GgufMetadata {
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException("File does not exist or is unreadable: ${file.absolutePath}")
        }

        val fileSize = file.length()
        val raf = RandomAccessFile(file, "r")
        try {
            val headerBuffer = ByteArray(24)
            raf.readFully(headerBuffer)
            val bb = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)

            val magic = bb.int
            if (magic != GGUF_MAGIC) {
                // Return fallback metadata if not GGUF or partially downloaded
                return GgufMetadata(
                    magic = "INVALID",
                    modelName = file.nameWithoutExtension,
                    fileSizeBytes = fileSize
                )
            }

            val version = bb.int
            val tensorCount = bb.long
            val kvCount = bb.long

            val metadataMap = mutableMapOf<String, Any>()
            var modelName = file.nameWithoutExtension
            var architecture = "llama"
            var contextLength = 2048
            var embeddingLength = 0
            var blockCount = 0
            var feedForwardLength = 0
            var tokenizerModel = "llama"
            var quantVersion = 2

            // Read up to 250 KV pairs to avoid excessive parsing time on huge files
            val limit = minOf(kvCount, 250L)
            for (i in 0 until limit) {
                if (raf.filePointer >= fileSize - 16) break
                val key = readString(raf) ?: break
                val valueType = readUInt32(raf)
                val value = readValue(raf, valueType)

                if (value != null) {
                    metadataMap[key] = value

                    when {
                        key == "general.name" -> modelName = value.toString()
                        key == "general.architecture" -> architecture = value.toString().lowercase()
                        key.endsWith(".context_length") -> {
                            contextLength = (value as? Number)?.toInt() ?: contextLength
                        }
                        key.endsWith(".embedding_length") -> {
                            embeddingLength = (value as? Number)?.toInt() ?: embeddingLength
                        }
                        key.endsWith(".block_count") -> {
                            blockCount = (value as? Number)?.toInt() ?: blockCount
                        }
                        key.endsWith(".feed_forward_length") -> {
                            feedForwardLength = (value as? Number)?.toInt() ?: feedForwardLength
                        }
                        key == "tokenizer.ggml.model" -> tokenizerModel = value.toString()
                        key == "general.quantization_version" -> {
                            quantVersion = (value as? Number)?.toInt() ?: quantVersion
                        }
                    }
                }
            }

            return GgufMetadata(
                magic = "GGUF",
                version = version,
                tensorCount = tensorCount,
                kvCount = kvCount,
                modelName = modelName,
                architecture = architecture,
                contextLength = contextLength,
                embeddingLength = embeddingLength,
                blockCount = blockCount,
                feedForwardLength = feedForwardLength,
                tokenizerModel = tokenizerModel,
                quantizationVersion = quantVersion,
                fileSizeBytes = fileSize,
                keyValues = metadataMap
            )
        } catch (e: Exception) {
            return GgufMetadata(
                magic = "GGUF",
                modelName = file.nameWithoutExtension,
                fileSizeBytes = fileSize,
                keyValues = mapOf("parse_error" to (e.message ?: "Unknown error"))
            )
        } finally {
            try { raf.close() } catch (_: Exception) {}
        }
    }

    private fun readString(raf: RandomAccessFile): String? {
        val lenBytes = ByteArray(8)
        if (raf.read(lenBytes) != 8) return null
        val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).long
        if (len < 0 || len > 1024 * 1024) return null // Sanity check

        val strBytes = ByteArray(len.toInt())
        if (raf.read(strBytes) != len.toInt()) return null
        return String(strBytes, Charsets.UTF_8)
    }

    private fun readUInt32(raf: RandomAccessFile): Int {
        val buf = ByteArray(4)
        raf.readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readValue(raf: RandomAccessFile, type: Int): Any? {
        return when (type) {
            TYPE_UINT8, TYPE_INT8 -> raf.readByte().toInt()
            TYPE_UINT16, TYPE_INT16 -> {
                val buf = ByteArray(2)
                raf.readFully(buf)
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            }
            TYPE_UINT32, TYPE_INT32 -> {
                val buf = ByteArray(4)
                raf.readFully(buf)
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
            }
            TYPE_FLOAT32 -> {
                val buf = ByteArray(4)
                raf.readFully(buf)
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).float
            }
            TYPE_BOOL -> raf.readByte() != 0.toByte()
            TYPE_STRING -> readString(raf)
            TYPE_UINT64, TYPE_INT64 -> {
                val buf = ByteArray(8)
                raf.readFully(buf)
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).long
            }
            TYPE_FLOAT64 -> {
                val buf = ByteArray(8)
                raf.readFully(buf)
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).double
            }
            TYPE_ARRAY -> {
                val itemType = readUInt32(raf)
                val lenBytes = ByteArray(8)
                raf.readFully(lenBytes)
                val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).long
                // Skip reading massive arrays (e.g. 100k tokenizer tokens)
                if (len > 30) {
                    skipArray(raf, itemType, len)
                    "[Array of $len items]"
                } else {
                    val list = mutableListOf<Any>()
                    for (i in 0 until len) {
                        val itm = readValue(raf, itemType) ?: break
                        list.add(itm)
                    }
                    list
                }
            }
            else -> null
        }
    }

    private fun skipArray(raf: RandomAccessFile, itemType: Int, length: Long) {
        val elementSize = when (itemType) {
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> 1
            TYPE_UINT16, TYPE_INT16 -> 2
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> 4
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> 8
            else -> -1
        }
        if (elementSize > 0) {
            raf.seek(raf.filePointer + elementSize * length)
        } else {
            // Variable size elements (like strings)
            for (i in 0 until minOf(length, 1000L)) {
                readString(raf) ?: break
            }
        }
    }
}
