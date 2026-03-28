package com.localllm.app.domain

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

data class HardwareProfile(
    val totalRamMB: Long,
    val availableRamMB: Long,
    val cpuCores: Int,
    val cpuArchitecture: String,
    val is64Bit: Boolean,
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
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return HardwareProfile(
            totalRamMB = memInfo.totalMem / (1024 * 1024),
            availableRamMB = memInfo.availMem / (1024 * 1024),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    fun analyzeModelCompatibility(modelSizeMB: Long): AutoConfig {
        val profile = getProfile()
        val usable = profile.totalRamMB * 0.6
        val ratio = usable / modelSizeMB.toDouble()

        val compat = when {
            ratio >= 2.0 -> ModelCompatibility.SMOOTH
            ratio >= 1.3 -> ModelCompatibility.MODERATE
            ratio >= 0.9 -> ModelCompatibility.BARELY
            else -> ModelCompatibility.NOT_SUPPORTED
        }

        val threads = when {
            profile.cpuCores >= 8 -> 6
            profile.cpuCores >= 6 -> 4
            profile.cpuCores >= 4 -> 3
            else -> 2
        }

        val remaining = usable - modelSizeMB
        val ctx = when {
            remaining > 2000 -> 4096
            remaining > 1000 -> 2048
            remaining > 500 -> 1024
            else -> 512
        }

        return when (compat) {
            ModelCompatibility.SMOOTH -> AutoConfig(ctx, 40, 0.95f, 0.7f, 1.1f, threads, 2048, compat, null)
            ModelCompatibility.MODERATE -> AutoConfig(ctx, 30, 0.9f, 0.7f, 1.1f, threads, 1024, compat,
                "Model will run but may be slow on ${profile.totalRamMB}MB RAM.")
            ModelCompatibility.BARELY -> AutoConfig(ctx, 20, 0.85f, 0.8f, 1.1f, threads, 512, compat,
                "⚠️ Model will barely fit. Expect slow responses.")
            ModelCompatibility.NOT_SUPPORTED -> AutoConfig(512, 10, 0.8f, 0.9f, 1.1f, threads, 256, compat,
                "❌ Not enough RAM (${profile.totalRamMB}MB). Choose a smaller model.")
        }
    }
}
