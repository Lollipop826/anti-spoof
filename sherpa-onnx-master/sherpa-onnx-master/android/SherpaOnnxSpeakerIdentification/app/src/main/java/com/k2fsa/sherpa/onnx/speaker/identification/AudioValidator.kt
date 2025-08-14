package com.k2fsa.sherpa.onnx.speaker.identification

// [file name]: AudioValidator.kt

import android.util.Log

object AudioValidator {
    const val MIN_SAMPLES = 4000
    const val TAG = "AudioValidator"

    fun validate(samples: FloatArray): Boolean {
        if (samples.isEmpty()) {
            Log.w(TAG, "Rejected: Empty audio")
            return false
        }
        if (samples.size < MIN_SAMPLES) {
            Log.w(TAG, "Rejected: Short audio (${samples.size} < $MIN_SAMPLES)")
            return false
        }
        return true
    }

    fun padIfNeeded(samples: FloatArray): FloatArray {
        return if (samples.size >= MIN_SAMPLES) {
            samples
        } else {
            Log.w(TAG, "Padding audio from ${samples.size} to $MIN_SAMPLES")
            FloatArray(MIN_SAMPLES).apply {
                samples.copyInto(this, 0, 0, samples.size)
            }
        }
    }
}