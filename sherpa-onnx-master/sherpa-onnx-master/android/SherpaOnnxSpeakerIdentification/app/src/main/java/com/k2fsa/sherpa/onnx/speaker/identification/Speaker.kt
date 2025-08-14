package com.k2fsa.sherpa.onnx

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.speaker.identification.SpeakerDatabaseHelper

class SpeakerEmbeddingExtractor(
    assetManager: AssetManager? = null,
    config: SpeakerEmbeddingExtractorConfig,
) {
    private var ptr: Long

    init {
        ptr = if (assetManager != null) {
            newFromAsset(assetManager, config)
        } else {
            newFromFile(config)
        }
    }

    fun safeCompute(stream: OnlineStream): FloatArray? {
        return try {
            if (isReady(stream)) compute(stream) else null
        } catch (e: Exception) {
            Log.e("Extractor", "Compute failed: ${e.message}")
            null
        }
    }


    protected fun finalize() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    fun release() = finalize()

    fun createStream(): OnlineStream {
        val p = createStream(ptr)
        return OnlineStream(p)
    }

    fun isReady(stream: OnlineStream) = isReady(ptr, stream.ptr)
    fun compute(stream: OnlineStream) = compute(ptr, stream.ptr)
    fun dim() = dim(ptr)

    private external fun newFromAsset(
        assetManager: AssetManager,
        config: SpeakerEmbeddingExtractorConfig,
    ): Long

    private external fun newFromFile(
        config: SpeakerEmbeddingExtractorConfig,
    ): Long

    private external fun delete(ptr: Long)

    private external fun createStream(ptr: Long): Long

    private external fun isReady(ptr: Long, streamPtr: Long): Boolean

    private external fun compute(ptr: Long, streamPtr: Long): FloatArray

    private external fun dim(ptr: Long): Int

    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}

class SpeakerEmbeddingManager(val dim: Int) {
    private var ptr: Long

    init {
        ptr = create(dim)
    }

    protected fun finalize() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    fun release() = finalize()
    fun add(name: String, embedding: FloatArray) = add(ptr, name, embedding)
    fun add(name: String, embedding: Array<FloatArray>) = addList(ptr, name, embedding)
    fun remove(name: String) = remove(ptr, name)
    fun search(embedding: FloatArray, threshold: Float) = search(ptr, embedding, threshold)
    fun verify(name: String, embedding: FloatArray, threshold: Float) =
        verify(ptr, name, embedding, threshold)

    fun contains(name: String) = contains(ptr, name)
    fun numSpeakers() = numSpeakers(ptr)

    fun allSpeakerNames() = allSpeakerNames(ptr)

    private external fun create(dim: Int): Long
    private external fun delete(ptr: Long): Unit
    private external fun add(ptr: Long, name: String, embedding: FloatArray): Boolean
    private external fun addList(ptr: Long, name: String, embedding: Array<FloatArray>): Boolean
    private external fun remove(ptr: Long, name: String): Boolean
    private external fun search(ptr: Long, embedding: FloatArray, threshold: Float): String
    private external fun verify(
        ptr: Long,
        name: String,
        embedding: FloatArray,
        threshold: Float
    ): Boolean

    private external fun contains(ptr: Long, name: String): Boolean
    private external fun numSpeakers(ptr: Long): Int

    private external fun allSpeakerNames(ptr: Long): Array<String>

    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}

// Please download the model file from
// https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models
// and put it inside the assets directory.
//
// Please don't put it in a subdirectory of assets
//private val modelName = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
private val modelName = "3dspeaker_speech_eres2net_large_sv_zh-cn_3dspeaker_16k.onnx"
//private val modelName = "3dspeaker_speech_eres2net_sv_zh-cn_16k-common.onnx"

object SpeakerRecognition {
    var _extractor: SpeakerEmbeddingExtractor? = null
    var _manager: SpeakerEmbeddingManager? = null
    private lateinit var databaseHelper: SpeakerDatabaseHelper
    fun initDatabase(context: Context) {
        databaseHelper = SpeakerDatabaseHelper(context)
    }
    val extractor: SpeakerEmbeddingExtractor
        get() {
            return _extractor!!
        }

    val manager: SpeakerEmbeddingManager
        get() {
            return _manager!!
        }



    fun initExtractor(assetManager: AssetManager? = null) {
        synchronized(this) {
            if (_extractor != null) {
                return
            }
            Log.i("sherpa-onnx", "Initializing speaker embedding extractor")

            _extractor = SpeakerEmbeddingExtractor(
                assetManager = assetManager,
                config = SpeakerEmbeddingExtractorConfig(
                    model = modelName,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                )
            )

            _manager = SpeakerEmbeddingManager(dim = _extractor!!.dim())
        }
    }

    fun computeEmbedding(audioData: FloatArray): FloatArray? {
        return try {
            val stream = extractor.createStream()
            stream.acceptWaveform(audioData, 16000)
            stream.inputFinished()
            extractor.safeCompute(stream)
        } catch (e: Exception) {
            Log.e("SpeakerRecognition", "Embedding error: ${e.message}")
            null
        }
    }
    fun addSpeakerToDatabase(name: String, embeddings: Array<FloatArray>): Boolean {
        return databaseHelper.addSpeaker(name, embeddings)
    }

    fun removeSpeakerFromDatabase(name: String): Boolean {
        return databaseHelper.removeSpeaker(name)
    }

    fun loadSpeakersFromDatabase() {
        val speakers = databaseHelper.getAllSpeakers()
        for ((name, embeddings) in speakers) {
            manager.add(name, embeddings)
        }
    }

}
