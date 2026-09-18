package com.example.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.model.DownloadedModel
import com.example.engine.GgufParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val NOTIFICATION_CHANNEL_ID = "vipo_model_downloads"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var INSTANCE: ModelDownloadManager? = null

        fun getInstance(context: Context): ModelDownloadManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ModelDownloadManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadTask>> = _tasks.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    init {
        createNotificationChannel()
        recoverIncompleteDownloads()
    }

    private fun getModelsDirectory(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of GGUF model downloads"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun recoverIncompleteDownloads() {
        scope.launch {
            val modelsDir = getModelsDirectory()
            val partFiles = modelsDir.listFiles { _, name -> name.endsWith(".part") } ?: return@launch

            for (part in partFiles) {
                val targetName = part.name.removeSuffix(".part")
                val id = targetName
                val existingBytes = part.length()
                if (existingBytes > 0) {
                    val task = DownloadTask(
                        id = id,
                        modelName = targetName.removeSuffix(".gguf").replace("-", " "),
                        variantName = "Recovered",
                        url = "",
                        destinationFile = File(modelsDir, targetName).absolutePath,
                        partFile = part.absolutePath,
                        bytesDownloaded = existingBytes,
                        totalBytes = existingBytes * 2, // approximation until resumed
                        progressPercent = 50,
                        status = DownloadStatus.PAUSED
                    )
                    _tasks.update { it + (id to task) }
                }
            }
        }
    }

    fun startDownload(
        modelId: String,
        modelName: String,
        variantName: String,
        url: String,
        expectedBytes: Long
    ) {
        val sanitizedName = "${modelId}_${variantName}.gguf".replace("/", "_").replace(" ", "_")
        val modelsDir = getModelsDirectory()
        val destFile = File(modelsDir, sanitizedName)
        val partFile = File(modelsDir, "$sanitizedName.part")

        if (destFile.exists()) {
            Log.i(TAG, "File already downloaded: ${destFile.absolutePath}")
            return
        }

        val task = DownloadTask(
            id = modelId,
            modelName = modelName,
            variantName = variantName,
            url = url,
            destinationFile = destFile.absolutePath,
            partFile = partFile.absolutePath,
            bytesDownloaded = if (partFile.exists()) partFile.length() else 0L,
            totalBytes = expectedBytes,
            progressPercent = if (expectedBytes > 0 && partFile.exists()) {
                ((partFile.length().toDouble() / expectedBytes) * 100).toInt()
            } else 0,
            status = DownloadStatus.DOWNLOADING
        )

        _tasks.update { it + (modelId to task) }
        executeDownload(task)
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        _tasks.update { current ->
            val task = current[id] ?: return@update current
            current + (id to task.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L, speedFormatted = "Paused"))
        }
        updateNotification("Download paused", "")
    }

    fun resumeDownload(id: String) {
        val task = _tasks.value[id] ?: return
        if (task.status == DownloadStatus.DOWNLOADING) return
        _tasks.update { it + (id to task.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null)) }
        executeDownload(task)
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val task = _tasks.value[id]
        if (task != null) {
            val part = File(task.partFile)
            if (part.exists()) part.delete()
        }
        _tasks.update { it - id }
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    fun retryDownload(id: String) {
        val task = _tasks.value[id] ?: return
        _tasks.update { it + (id to task.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null)) }
        executeDownload(task)
    }

    private fun executeDownload(task: DownloadTask) {
        activeJobs[task.id]?.cancel()

        val job = scope.launch {
            val partFile = File(task.partFile)
            val destFile = File(task.destinationFile)
            var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

            try {
                val requestBuilder = Request.Builder().url(task.url)
                if (downloadedBytes > 0) {
                    requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 206) {
                    throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val responseLength = body.contentLength()
                val totalBytes = if (response.code == 206) {
                    downloadedBytes + responseLength
                } else if (responseLength > 0) {
                    downloadedBytes = 0L
                    responseLength
                } else {
                    task.totalBytes
                }

                val raf = RandomAccessFile(partFile, "rw")
                if (response.code == 206) {
                    raf.seek(downloadedBytes)
                } else {
                    raf.setLength(0)
                }

                val inputStream = body.byteStream()
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var lastUpdate = SystemClock.elapsedRealtime()
                var bytesSinceLastUpdate = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    raf.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUpdate >= 400) {
                        val durationSec = (now - lastUpdate) / 1000.0
                        val speedBytes = (bytesSinceLastUpdate / durationSec).toLong()
                        val percent = if (totalBytes > 0) ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else 0

                        val updatedTask = task.copy(
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalBytes,
                            progressPercent = percent,
                            speedBytesPerSec = speedBytes,
                            speedFormatted = DownloadTask.formatBytes(speedBytes) + "/s",
                            status = DownloadStatus.DOWNLOADING
                        )
                        _tasks.update { it + (task.id to updatedTask) }

                        updateNotification(
                            title = "Downloading ${task.modelName}",
                            content = "$percent% • ${DownloadTask.formatBytes(downloadedBytes)} / ${DownloadTask.formatBytes(totalBytes)} (${updatedTask.speedFormatted})",
                            progress = percent
                        )

                        lastUpdate = now
                        bytesSinceLastUpdate = 0L
                    }
                }

                raf.close()
                inputStream.close()

                // Finalize download: rename .part to destFile
                if (destFile.exists()) destFile.delete()
                partFile.renameTo(destFile)

                val completedTask = task.copy(
                    bytesDownloaded = destFile.length(),
                    totalBytes = destFile.length(),
                    progressPercent = 100,
                    speedBytesPerSec = 0L,
                    speedFormatted = "Complete",
                    status = DownloadStatus.COMPLETED
                )
                _tasks.update { it + (task.id to completedTask) }

                updateNotification(
                    title = "Download Complete",
                    content = "${task.modelName} is ready for offline chat",
                    progress = 100
                )
                Log.i(TAG, "Successfully downloaded: ${destFile.absolutePath}")

            } catch (e: CancellationException) {
                Log.i(TAG, "Download cancelled or paused: ${task.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Download error for ${task.id}: ${e.message}", e)
                val failedTask = task.copy(
                    bytesDownloaded = downloadedBytes,
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Connection error. Tap to retry."
                )
                _tasks.update { it + (task.id to failedTask) }
                updateNotification("Download failed", e.message ?: "Network error")
            } finally {
                activeJobs.remove(task.id)
            }
        }
        activeJobs[task.id] = job
    }

    suspend fun importGgufFromUri(uri: Uri, originalFileName: String?): DownloadedModel? = withContext(Dispatchers.IO) {
        try {
            val modelsDir = getModelsDirectory()
            val safeName = (originalFileName ?: "imported_model_${System.currentTimeMillis()}.gguf")
                .replace(" ", "_")
            val targetFile = File(modelsDir, safeName)

            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()

            val metadata = GgufParser.parse(targetFile)
            val displayName = metadata.modelName.ifBlank { targetFile.nameWithoutExtension }

            return@withContext DownloadedModel(
                id = targetFile.name,
                displayName = displayName,
                fileName = targetFile.name,
                filePath = targetFile.absolutePath,
                fileSizeBytes = targetFile.length(),
                formattedSize = DownloadTask.formatBytes(targetFile.length()),
                modelId = null,
                variantName = "Imported",
                architecture = metadata.architecture,
                isImported = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import GGUF file from URI: ${e.message}", e)
            return@withContext null
        }
    }

    fun downloadFromCustomUrl(url: String, customName: String) {
        val fileName = if (url.contains("/")) {
            url.substringAfterLast("/").substringBefore("?")
        } else {
            "custom_model_${System.currentTimeMillis()}.gguf"
        }.let { if (!it.endsWith(".gguf")) "$it.gguf" else it }

        val id = "custom_${System.currentTimeMillis()}"
        startDownload(
            modelId = id,
            modelName = customName.ifBlank { fileName.removeSuffix(".gguf") },
            variantName = "Custom URL",
            url = url,
            expectedBytes = 0L
        )
    }

    private fun updateNotification(title: String, content: String, progress: Int = -1) {
        if (notificationManager == null) return
        try {
            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(progress in 0..99)

            if (progress in 0..100) {
                builder.setProgress(100, progress, false)
            }
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted on Android 13+
        }
    }
}
