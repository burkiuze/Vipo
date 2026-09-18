package com.example.data.model

data class DeviceHardwareInfo(
    val totalRamGb: Float,
    val availableRamGb: Float,
    val cpuCores: Int,
    val freeStorageGb: Float,
    val totalStorageGb: Float,
    val deviceName: String
)

enum class CompatibilityLevel(val label: String, val description: String) {
    EXCELLENT("Excellent", "Runs smoothly with maximum headroom"),
    GOOD("Good", "Recommended for your phone"),
    USABLE("Usable", "May have minor slowdown with long contexts"),
    SLOW("Slow", "High CPU/RAM demand; sluggish generation"),
    MEMORY_RISK("Memory Risk", "Exceeds available RAM; high risk of OOM")
}
