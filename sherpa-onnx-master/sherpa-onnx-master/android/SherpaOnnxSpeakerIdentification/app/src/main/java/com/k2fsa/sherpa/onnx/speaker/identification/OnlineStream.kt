package com.k2fsa.sherpa.onnx

import android.util.Log

class OnlineStream(var ptr: Long = 0) {
    fun acceptWaveform(samples: FloatArray, sampleRate: Int) =
        acceptWaveform(ptr, samples, sampleRate)

    fun inputFinished() = inputFinished(ptr)

    protected fun finalize() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    fun release() = finalize()

    fun use(block: (OnlineStream) -> Unit) {
        try {
            block(this)
        } finally {
            release()
        }
    }

    fun safeAcceptWaveform(samples: FloatArray, sampleRate: Int) {
        if (samples.isEmpty()) {
            Log.e("OnlineStream", "Attempted to accept empty waveform")
            return
        }
        acceptWaveform(samples, sampleRate)
    }

    private external fun acceptWaveform(ptr: Long, samples: FloatArray, sampleRate: Int)
    private external fun inputFinished(ptr: Long)
    private external fun delete(ptr: Long)


    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
