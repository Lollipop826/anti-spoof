package com.k2fsa.sherpa.onnx.speaker.identification

import android.content.Context
import java.io.File

object AntiSpoof {
    private const val ASSET_NAME = "LA_model_quantized.onnx"
    private var inited = false

    fun init(context: Context): Boolean {
        val modelPath = ensureModelCopied(context, ASSET_NAME)
        inited = AntiSpoofJNI.init(modelPath)
        return inited
    }

    fun run(pcm: FloatArray, sampleRate: Int = 16000): Float {
        if (!inited) throw IllegalStateException("AntiSpoof not initialized")
        // Mirror MyApplication7: normalize if data appears PCM (>100 abs), then pad to 64600
        val maxAbs = pcm.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        val avgAbs = if (pcm.isNotEmpty()) pcm.sumOf { kotlin.math.abs(it.toDouble()) }.toFloat() / pcm.size else 0f
        val isPCM = maxAbs > 100f || avgAbs > 10f
        val normalized = if (isPCM) FloatArray(pcm.size) { pcm[it] / 32768f } else pcm.copyOf()
        // JNI 侧期望 64000
        val target = 64000
        val prepared = if (normalized.size == target) normalized else if (normalized.size > target) normalized.copyOf(target) else {
            val out = FloatArray(target)
            System.arraycopy(normalized, 0, out, 0, normalized.size)
            var filled = normalized.size
            while (filled < target) {
                val toCopy = minOf(normalized.size, target - filled)
                System.arraycopy(normalized, 0, out, filled, toCopy)
                filled += toCopy
            }
            out
        }
        return AntiSpoofJNI.run(prepared, sampleRate)
    }

    private fun ensureModelCopied(context: Context, assetName: String): String {
        val dst = File(context.filesDir, assetName)
        if (!dst.exists() || dst.length() == 0L) {
            context.assets.open(assetName).use { input ->
                dst.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return dst.absolutePath
    }
}

