package com.k2fsa.sherpa.onnx.speaker.identification

object AntiSpoofJNI {
    init {
        try {
            System.loadLibrary("antispoof")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    external fun init(modelPath: String): Boolean
    external fun run(pcm: FloatArray, sampleRate: Int): Float
}

