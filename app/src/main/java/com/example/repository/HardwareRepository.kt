package com.example.repository

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.data.model.CompatibilityLevel
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ModelVariant

class HardwareRepository(private val context: Context) {

    fun getDeviceHardwareInfo(): DeviceHardwareInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamGb = memInfo.totalMem / (1024f * 1024f * 1024f)
        val availRamGb = memInfo.availMem / (1024f * 1024f * 1024f)
        val cores = Runtime.getRuntime().availableProcessors()

        val stat = StatFs(context.filesDir.absolutePath)
        val freeStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024f * 1024f * 1024f)
        val totalStorageGb = (stat.blockCountLong * stat.blockSizeLong) / (1024f * 1024f * 1024f)

        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

        return DeviceHardwareInfo(
            totalRamGb = (totalRamGb * 10).toInt() / 10f,
            availableRamGb = (availRamGb * 10).toInt() / 10f,
            cpuCores = cores,
            freeStorageGb = (freeStorageGb * 10).toInt() / 10f,
            totalStorageGb = (totalStorageGb * 10).toInt() / 10f,
            deviceName = deviceName
        )
    }

    fun evaluateCompatibility(
        variantRamRequiredGb: Float,
        hardwareInfo: DeviceHardwareInfo = getDeviceHardwareInfo()
    ): CompatibilityLevel {
        val avail = hardwareInfo.availableRamGb
        val total = hardwareInfo.totalRamGb

        return when {
            avail >= variantRamRequiredGb * 1.35f -> CompatibilityLevel.EXCELLENT
            avail >= variantRamRequiredGb * 1.0f -> CompatibilityLevel.GOOD
            total >= variantRamRequiredGb * 1.15f -> CompatibilityLevel.USABLE
            total >= variantRamRequiredGb * 0.85f -> CompatibilityLevel.SLOW
            else -> CompatibilityLevel.MEMORY_RISK
        }
    }

    /**
     * Auto Select: Automatically recommends the best suited model and quantization variant
     * based on user's current hardware.
     */
    fun findRecommendedModelAndVariant(
        catalog: List<ModelCatalogItem>,
        hardwareInfo: DeviceHardwareInfo = getDeviceHardwareInfo()
    ): Pair<ModelCatalogItem, ModelVariant>? {
        if (catalog.isEmpty()) return null

        // Look for variants with EXCELLENT or GOOD compatibility that fit in storage
        var bestMatch: Pair<ModelCatalogItem, ModelVariant>? = null
        var bestScore = -1f

        for (item in catalog) {
            for (variant in item.variants) {
                val compatibility = evaluateCompatibility(variant.ramRequiredGb, hardwareInfo)
                val variantSizeGb = variant.fileSizeBytes / (1024f * 1024f * 1024f)

                // Must fit in free storage with at least 1GB headroom
                if (variantSizeGb + 1.0f > hardwareInfo.freeStorageGb) continue

                val score = when (compatibility) {
                    CompatibilityLevel.EXCELLENT -> 100f - kotlin.math.abs(variant.ramRequiredGb - (hardwareInfo.availableRamGb * 0.7f))
                    CompatibilityLevel.GOOD -> 80f - kotlin.math.abs(variant.ramRequiredGb - hardwareInfo.availableRamGb)
                    CompatibilityLevel.USABLE -> 50f
                    CompatibilityLevel.SLOW -> 20f
                    CompatibilityLevel.MEMORY_RISK -> 0f
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = Pair(item, variant)
                }
            }
        }

        // Fallback to first model's balanced variant if no match
        return bestMatch ?: catalog.firstOrNull()?.let { item ->
            val v = item.variants.firstOrNull { it.name.contains("Q4") } ?: item.variants.first()
            Pair(item, v)
        }
    }
}
