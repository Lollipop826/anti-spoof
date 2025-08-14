package com.k2fsa.sherpa.onnx.speaker.identification


import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerRecognition
import com.k2fsa.sherpa.onnx.SpeakerRecognition.initExtractor
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.getVadModelConfig
import com.k2fsa.sherpa.onnx.speaker.identification.AntiSpoof

object SherpaOnnx {
    lateinit var speakerRecognition: SpeakerRecognition
    lateinit var callStateManager: CallStateManager
    lateinit var asrRecognizer: OfflineRecognizer
    lateinit var vad: Vad

    fun initModels(context: Context) {
        val assets = context.assets
        val app = context.applicationContext
        callStateManager = CallStateManager(context)

        speakerRecognition = SpeakerRecognition.apply {
            initExtractor(assets)
        }

        val modelDir = "sherpa-onnx-paraformer-zh-small-2024-03-09"

        val asrModelConfig = OfflineModelConfig().apply {
            paraformer = OfflineParaformerModelConfig(
                model = "$modelDir/model.int8.onnx"
            )
            tokens = "$modelDir/tokens.txt"
        }

        val asrConfig = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000),
            modelConfig = asrModelConfig
        )

        asrRecognizer = OfflineRecognizer(assets, asrConfig)


//         初始化VAD

        val  vadConfig = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.5F,
                minSilenceDuration = 0.25F,
                minSpeechDuration = 0.25F,
                windowSize = 1024,
            ),
            sampleRate = 16000,
            numThreads = 2,
            provider = "cpu",
        )

//        val vadConfig = VadModelConfig(
//            sileroVadModelConfig = SileroVadModelConfig(
//                model = "silero_vad.onnx",
//                threshold = 0.3F,  // 降低阈值，提高语音检测灵敏度
//                minSilenceDuration = 0.05F,  // 缩短静音持续时间要求
//                minSpeechDuration = 0.1F,    // 缩短最小语音持续时间
//                windowSize = 256,            // 使用更小的窗口
//                maxSpeechDuration = 5.0F     // 限制最大语音段长度
//            ),
//            sampleRate = 16000,
//            numThreads = 4,  // 增加线程数提高处理速度
//            provider = "cpu",
//        )

        vad = Vad(assets, vadConfig)

        // 初始化鉴伪模型（使用共享的 ORT 库，模型在 assets 下）
        AntiSpoof.init(context)

    }
}