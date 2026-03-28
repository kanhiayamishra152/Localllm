package com.localllm.app.domain

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.io.RandomAccessFile

data class HardwareProfile(
    val totalRamMB: Long,
    val availableRamMB: Long,
    val cpuCores: Int,
    val cpuFreqMHz: Long,
    val cpuArchitecture: String,
    val is64Bit: Boolean,
    val hasNeon: Boolean,
    val androidVersion: Int,
    val deviceModel: String
)

enum class ModelCompatibility(val label: String, val emoji: String) {
    SMOOTH("Runs Smoothly", "🟢"),
    MODERATE("Runs Moderately", "🟡"),
    BARELY("Barely Runs", "🟠"),
    NOT_SUPPORTED("Not Supported", "🔴")
}

data class AutoConfig(
    val contextWindow: Int,
    val topK: Int,
    val topP: Float,
    val temperature: Float,
    val repeatPenalty: Float,
    val threads: Int,
    val maxTokens: Int,
    val compatibility: ModelCompatibility,
    val warningMessage: String?
)

class HardwareProfiler(private val context: Context) {

    fun getProfile(): HardwareProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamMB = memInfo.totalMem / (1024 * 1024)
        val availableRamMB = memInfo.availMem / (1024 * 1024)

        return HardwareProfile(
            totalRamMB = totalRamMB,
            availableRamMB = availableRamMB,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuFreqMHz = getMaxCpuFrequency(),
            cpuArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            hasNeon = checkNeonSupport(),
            androidVersion = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    fun analyzeModelCompatibility(modelSizeMB: Long): AutoConfig {
        val profile = getProfile()
        val availableForModel = profile.availableRamMB * 0.7 // 70% of available
        val totalUsable = profile.totalRamMB * 0.6 // 60% of total is realistic

        val ratio = totalUsable / modelSizeMB.toDouble()

        val compatibility = when {
            ratio >= 2.0 -> ModelCompatibility.SMOOTH
            ratio >= 1.3 -> ModelCompatibility.MODERATE
            ratio >= 0.9 -> ModelCompatibility.BARELY
            else -> ModelCompatibility.NOT_SUPPORTED
        }

        // Auto-configure based on hardware and compatibility
        val optimalThreads = calculateOptimalThreads(profile)
        val contextWindow = calculateContextWindow(profile, modelSizeMB, compatibility)
        val maxTokens = calculateMaxTokens(compatibility)

        val (topK, topP, temperature) = when (compatibility) {
            ModelCompatibility.SMOOTH -> Triple(40, 0.95f, 0.7f)
            ModelCompatibility.MODERATE -> Triple(30, 0.9f, 0.7f)
            ModelCompatibility.BARELY -> Triple(20, 0.85f, 0.8f)
            ModelCompatibility.NOT_SUPPORTED -> Triple(10, 0.8f, 0.9f)
        }

        val warning = when (compatibility) {
            ModelCompatibility.SMOOTH -> null
            ModelCompatibility.MODERATE ->
                "This model will run but may be slow. ${profile.totalRamMB}MB RAM available."
            ModelCompatibility.BARELY ->
                "⚠️ This model will barely fit in memory. Expect very slow responses and possible crashes. Consider a smaller model."
            ModelCompatibility.NOT_SUPPORTED ->
                "❌ This model requires more RAM than your device has (${profile.totalRamMB}MB). It will likely crash. Please choose a smaller model."
        }

        return AutoConfig(
            contextWindow = contextWindow,
            topK = topK,
            topP = topP,
            temperature = temperature,
            repeatPenalty = 1.1f,
            threads = optimalThreads,
            maxTokens = maxTokens,
            compatibility = compatibility,
            warningMessage = warning
        )
    }

    private fun calculateOptimalThreads(profile: HardwareProfile): Int {
        // Use physical cores minus 1 for UI responsiveness
        val cores = profile.cpuCores
        return when {
            cores >= 8 -> 6
            cores >= 6 -> 4
            cores >= 4 -> 3
            else -> 2
        }
    }

    private fun calculateContextWindow(
        profile: HardwareProfile,
        modelSizeMB: Long,
        compatibility: ModelCompatibility
    ): Int {
        val availableAfterModel = (profile.totalRamMB * 0.6) - modelSizeMB
        return when {
            compatibility == ModelCompatibility.NOT_SUPPORTED -> 512
            availableAfterModel > 2000 -> 4096
            availableAfterModel > 1000 -> 2048
            availableAfterModel > 500 -> 1024
            else -> 512
        }
    }

    private fun calculateMaxTokens(compatibility: ModelCompatibility): Int {
        return when (compatibility) {
            ModelCompatibility.SMOOTH -> 2048
            ModelCompatibility.MODERATE -> 1024
            ModelCompatibility.BARELY -> 512
            ModelCompatibility.NOT_SUPPORTED -> 256
        }
    }

    private fun getMaxCpuFrequency(): Long {
        return try {
            val file = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (file.exists()) {
                file.readText().trim().toLong() / 1000 // Convert kHz to MHz
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun checkNeonSupport(): Boolean {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            cpuInfo.contains("neon", ignoreCase = true) ||
                    cpuInfo.contains("asimd", ignoreCase = true) ||
                    Build.SUPPORTED_ABIS.contains("arm64-v8a")
        } catch (e: Exception) {
            Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        }
    }
}
