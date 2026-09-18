package com.example.download

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val id: String, // typically modelId or url hash
    val modelName: String,
    val variantName: String,
    val url: String,
    val destinationFile: String,
    val partFile: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Int = 0,
    val speedBytesPerSec: Long = 0L,
    val speedFormatted: String = "0 KB/s",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val errorMessage: String? = null
) {
    val downloadedFormatted: String
        get() = formatBytes(bytesDownloaded)

    val totalFormatted: String
        get() = formatBytes(totalBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                kb >= 1.0 -> String.format("%.0f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}
