package com.k2fsa.sherpa.onnx.speaker.identification.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.Canvas
import com.k2fsa.sherpa.onnx.speaker.identification.AudioValidator
import com.k2fsa.sherpa.onnx.speaker.identification.CallStateManager
import com.k2fsa.sherpa.onnx.speaker.identification.R
import com.k2fsa.sherpa.onnx.speaker.identification.SherpaOnnx
import com.k2fsa.sherpa.onnx.speaker.identification.TAG
import com.k2fsa.sherpa.onnx.speaker.identification.TranscriptManager
import com.k2fsa.sherpa.onnx.speaker.identification.common.TranscriptItem
import com.k2fsa.sherpa.onnx.speaker.identification.ui.theme.ActiveCallBlue
import com.k2fsa.sherpa.onnx.speaker.identification.ui.theme.UnknownSpeakerBlue
import com.k2fsa.sherpa.onnx.speaker.identification.ui.theme.DangerRed
import com.k2fsa.sherpa.onnx.speaker.identification.ui.theme.SafeGreen
import com.k2fsa.sherpa.onnx.speaker.identification.AntiSpoofRealtime
import com.k2fsa.sherpa.onnx.speaker.identification.AntiSpoofThreshold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

import com.k2fsa.sherpa.onnx.speaker.identification.ui.components.FrostedCard
import com.k2fsa.sherpa.onnx.speaker.identification.ui.components.ActionIconButton
import com.k2fsa.sherpa.onnx.speaker.identification.ui.components.MetricCard
import com.k2fsa.sherpa.onnx.speaker.identification.screens.components.DetectionRadar
private var audioRecord: AudioRecord? = null
private val samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
private const val sampleRateInHz = 16000
private var audioSamplesAccumulator: MutableList<FloatArray>? = null



//private var segmentIdCounter = 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(callStateManager: CallStateManager) {
    val context = LocalContext.current
    val isCallActive by callStateManager.isCallActive.observeAsState(false)
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // 实时鉴伪
    val antiSpoofRt = remember { AntiSpoofRealtime(scope, sampleRate = sampleRateInHz, windowSamples = 64000, hopMs = 1000) }
    var lastRtScore by remember { mutableStateOf<Float?>(null) }
    var detectionActive by remember { mutableStateOf(false) }

    var recordingAvailableToSave by remember { mutableStateOf(false) }
    var threshold by remember { mutableStateOf(0.65f) }
    val transcripts by TranscriptManager.transcripts.collectAsState()
    var volumeLevel by remember { mutableStateOf(0f) }

    var currentSpeaker by remember { mutableStateOf<String?>(null) }
    var currentTranscript by remember { mutableStateOf("") }
    var lastProcessedSpeaker by remember { mutableStateOf<String?>(null) }
    var textBuffer by remember { mutableStateOf("") }

    /////
    var speakerSegments by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var lastSpeaker by remember { mutableStateOf<String?>(null) }

//    var lastSegmentId by remember { mutableStateOf(-1) }
//
    val unknownSpeaker = stringResource(id = R.string.speaker_unknown)
    val errorText = stringResource(id = R.string.error)



//    val processAudioSegment: (FloatArray) -> Unit = Unit@{ samples ->
//        if (samples.isEmpty()) return@Unit
//
//        scope.launch {
//            val speakerResult = withContext(Dispatchers.IO) {
//                try {
//                    val stream = SherpaOnnx.speakerRecognition.extractor.createStream()
//                    stream.acceptWaveform(samples, sampleRateInHz)
//                    stream.inputFinished()
//                    if (SherpaOnnx.speakerRecognition.extractor.isReady(stream)) {
//                        val embedding = SherpaOnnx.speakerRecognition.extractor.compute(stream)
//                        SherpaOnnx.speakerRecognition.manager.search(embedding, threshold)
//                            ?: unknownSpeaker
//                    } else unknownSpeaker
//                } catch (e: Exception) {
//                    errorText
//                }
//            }
//
//            val asrResult = withContext(Dispatchers.IO) {
//                try {
//                    val stream = SherpaOnnx.asrRecognizer.createStream()
//                    stream.acceptWaveform(samples, sampleRateInHz)
//                    SherpaOnnx.asrRecognizer.decode(stream)
//                    SherpaOnnx.asrRecognizer.getResult(stream).text
//                } catch (e: Exception) {
//                    ""
//                }
//            }
//
//            if (asrResult.isBlank()) return@launch
//
////            if (speakerResult == lastProcessedSpeaker && speakerResult != unknownSpeaker) {
////                textBuffer += " $asrResult"
////            } else {
////                if (textBuffer.isNotBlank() && lastProcessedSpeaker != null) {
////                    TranscriptManager.addTranscript(lastProcessedSpeaker!!, textBuffer)
////                }
////                textBuffer = ""
////                lastProcessedSpeaker = null
////
////                lastProcessedSpeaker = speakerResult
////                textBuffer = asrResult
////            }
//
//            if (speakerResult != lastProcessedSpeaker) {
//                // 说话人切换，提交上一段文本
//                if (textBuffer.isNotBlank() && lastProcessedSpeaker != null) {
//                    TranscriptManager.addTranscript(lastProcessedSpeaker!!, textBuffer)
//                }
//
//                // 重置为新说话人
//                textBuffer = asrResult
//                lastProcessedSpeaker = speakerResult
//            } else {
//                // 同一说话人，追加文本
//                textBuffer += " $asrResult"
//            }
//
//            currentSpeaker = speakerResult
//            currentTranscript = textBuffer
//
//
//        }
//    }

    /////
    val processAudioSegment: (FloatArray) -> Unit = Unit@{ samples ->
        if (samples.isEmpty()) return@Unit

        scope.launch {
            val speakerResult = withContext(Dispatchers.IO) {
                try {
                    val stream = SherpaOnnx.speakerRecognition.extractor.createStream()
                    stream.acceptWaveform(samples, sampleRateInHz)
                    stream.inputFinished()
                    if (SherpaOnnx.speakerRecognition.extractor.isReady(stream)) {
                        val embedding = SherpaOnnx.speakerRecognition.extractor.compute(stream)
                        SherpaOnnx.speakerRecognition.manager.search(embedding, threshold)
                            ?: unknownSpeaker
                    } else unknownSpeaker
                } catch (e: Exception) {
                    errorText
                }
            }

            val asrResult = withContext(Dispatchers.IO) {
                try {
                    val stream = SherpaOnnx.asrRecognizer.createStream()
                    stream.acceptWaveform(samples, sampleRateInHz)
                    SherpaOnnx.asrRecognizer.decode(stream)
                    SherpaOnnx.asrRecognizer.getResult(stream).text
                } catch (e: Exception) {
                    ""
                }
            }

            if (asrResult.isBlank()) return@launch

            // 处理说话人合并逻辑
            if (speakerResult == unknownSpeaker) {
                // 未知说话人 - 直接添加为独立条目
                TranscriptManager.addTranscript(speakerResult, asrResult)
            } else {
                // 已知说话人 - 合并到现有条目
                val currentText = speakerSegments[speakerResult] ?: ""
                val updatedText = if (currentText.isNotEmpty()) "$currentText $asrResult" else asrResult

                // 更新状态
                speakerSegments = speakerSegments + (speakerResult to updatedText)

                // 当说话人切换时，提交上一段文本
                if (lastSpeaker != null && lastSpeaker != speakerResult) {
                    lastSpeaker?.let { prevSpeaker ->
                        speakerSegments[prevSpeaker]?.let { text ->
                            if (text.isNotBlank()) {
                                TranscriptManager.addTranscript(prevSpeaker, text)
                            }
                        }
                    }
                    // 重置上一说话人的内容
                    speakerSegments = (speakerSegments - lastSpeaker) as Map<String, String>
                }

                lastSpeaker = speakerResult
            }

            // 更新当前显示的说话人和文本
            currentSpeaker = speakerResult
            currentTranscript = if (speakerResult == unknownSpeaker) {
                asrResult
            } else {
                speakerSegments[speakerResult] ?: ""
            }

            // 一旦识别出任意一段有效语音，就允许保存
            recordingAvailableToSave = true
        }
    }



    Log.i(TAG,"threshold$threshold")
    Log.i(TAG,"Callactive...recording$isCallActive")

    // 通话生命周期：音频采集 + 处理一次性启动/停止
    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            // 一接通电话就自动开始实时鉴伪（无需手动点击）
            detectionActive = true

            // 重置状态
            speakerSegments = emptyMap()
            lastSpeaker = null
            recordingAvailableToSave = false
            textBuffer = ""
            lastProcessedSpeaker = null
            currentSpeaker = null
            currentTranscript = ""

            startRecording(activity, samplesChannel)
            startProcessing(scope, antiSpoofRt, processAudioSegment) { v ->
                // 将音量映射到 0..1
                volumeLevel = v.coerceIn(0f, 1f)
            }
        } else {
            // 挂断时停止实时鉴伪
            detectionActive = false

            stopRecording()
            // 提交所有剩余的说话人内容
            speakerSegments.forEach { (speaker, text) ->
                if (text.isNotBlank()) {
                    TranscriptManager.addTranscript(speaker, text)
                }
            }
        }
    }

    // 实时鉴伪启停：不再重复启动处理协程，只控制打分循环
    LaunchedEffect(isCallActive, detectionActive) {
        if (isCallActive && detectionActive) {
            antiSpoofRt.reset()
            antiSpoofRt.start()
            lastRtScore = null
            scope.launch {
                while (isCallActive && detectionActive) {
                    kotlinx.coroutines.delay(1000)
                    val s = antiSpoofRt.lastScore
                    lastRtScore = if (s >= -10f) s else null
                }
            }
        } else {
            antiSpoofRt.stop()
            lastRtScore = null
        }
    }


    var showClearConfirm by remember { mutableStateOf(false) }




    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            ControlPanel(
                isRecordingActive = isCallActive,
                isSaveEnabled = recordingAvailableToSave,
                onSaveClick = {
                    val accumulatedAudio = audioSamplesAccumulator?.toList()
                    if (accumulatedAudio != null && accumulatedAudio.any { it.isNotEmpty() }) {
                        scope.launch(Dispatchers.IO) {
                            val totalSize = accumulatedAudio.sumOf { it.size }
                            if (totalSize > 0) {
                                val combinedData = FloatArray(totalSize)
                                var offset = 0
                                for (chunk in accumulatedAudio) {
                                    System.arraycopy(chunk, 0, combinedData, offset, chunk.size)
                                    offset += chunk.size
                                }
                                saveAudioToFile(context, combinedData, sampleRateInHz)
                            }
                        }
                    }
                    recordingAvailableToSave = false
                },
                onClearClick = {
                    showClearConfirm = true
                }
            )
        }
    ) { paddingValues ->

        // 顶部视觉：渐变 + 星空点缀 + 前景内容
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = if (isCallActive)
                            listOf(Color(0xFFEBF2FF), Color.White)
                        else
                            listOf(Color(0xFFF5F9FF), Color.White)
                    )
                )
        ) {
            // 星空：位于背景层，低密度星点，轻微闪烁
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val topRegion = height * 0.55f  // 仅在上半区域画星点，避免干扰列表
                val rand = java.util.Random(42)
                val starCount = 90
                for (i in 0 until starCount) {
                    val x = rand.nextFloat() * width
                    val y = rand.nextFloat() * topRegion
                    val baseAlpha = 0.05f + 0.12f * rand.nextFloat()
                    val r = 0.6f + 1.4f * rand.nextFloat()
                    // 使用 y 做相位差，避免同时闪烁
                    val tw = 0.5f + 0.5f * kotlin.math.abs(kotlin.math.sin((y + x) / 180f + (System.currentTimeMillis() % 4000L) / 4000f * 6.28318f))
                    drawCircle(color = Color.White.copy(alpha = baseAlpha * tw), radius = r)
                }
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // 中心动态雷达：水平居中靠上
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        DetectionRadar(
                            active = isCallActive && detectionActive,
                            score = lastRtScore,
                            volume = volumeLevel,
                            modifier = Modifier.size(260.dp),
                            onToggle = { if (isCallActive) detectionActive = !detectionActive }
                        )
                    }
                }
                item {
                    // 关键指标区：阈值 + 实时状态
                    FrostedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = "相似度阈值: ${"%.2f".format(threshold)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = threshold,
                                onValueChange = { threshold = it },
                                valueRange = 0.5f..0.95f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                item {
                    AnimatedVisibility(
                        visible = isCallActive,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300))
                    ) {
                        CurrentSpeakerCard(
                            speaker = currentSpeaker,
                            transcript = currentTranscript,
                            unknownSpeakerName = unknownSpeaker
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                item {
                    if (detectionActive && lastRtScore != null) {
                        AntiSpoofFloatingCard(
                            score = lastRtScore!!,
                            threshold = AntiSpoofThreshold.DEFAULT_THRESHOLD,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(id = R.string.transcript_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
                items(
                    items = transcripts,
                    key = { it.timestamp }
                ) { transcript ->
                    TranscriptItem(transcript = transcript, onDetectClick = null)
                }
            }

            // 已改为内联展示，避免覆盖转录列表

            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    confirmButton = {
                        TextButton(onClick = {
                            TranscriptManager.clearTranscripts()
                            audioSamplesAccumulator?.clear()
                            recordingAvailableToSave = false
                            showClearConfirm = false
                        }) { Text("清空") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
                    },
                    title = { Text("清空记录与缓存？") },
                    text = { Text("此操作将清除通话记录与已缓存的录音片段，无法恢复。") }
                )
            }
        }
    }

    }


/* CallStatusHeader component removed per new design */
@Composable
fun CurrentSpeakerCard(speaker: String?, transcript: String, unknownSpeakerName: String) {
    val isKnown = speaker != null && speaker != unknownSpeakerName
    val speakerName = if (speaker != null) speaker else stringResource(id = R.string.listening)
    val speakerColor = if (isKnown) ActiveCallBlue else UnknownSpeakerBlue

    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(speakerColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.current_speaker_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = speakerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = speakerColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Transcript",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = transcript.ifEmpty { stringResource(id = R.string.no_speech_detected) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ControlPanel(
    isRecordingActive: Boolean,
    isSaveEnabled: Boolean,
    onSaveClick: () -> Unit,
    onClearClick: () -> Unit
) {
    // 底部“毛玻璃”栏，圆角+投影
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 主行动：保存
            Button(
                onClick = onSaveClick,
                enabled = isSaveEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(id = R.string.save_recording))
            }

            // 次行动：清空
            OutlinedButton(
                onClick = onClearClick,
                enabled = true,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.clear))
            }
        }
    }
}

@Composable
fun AntiSpoofFloatingCard(
    score: Float,
    threshold: Float,
    modifier: Modifier = Modifier
) {
    val isSpoof = score <= threshold
    val bg = if (isSpoof) DangerRed else SafeGreen
    val label = if (isSpoof) "疑似伪造" else "真人语音"


    Surface(
        modifier = modifier,
        shape = CircleShape,
        tonalElevation = 6.dp,
        color = bg.copy(alpha = 0.95f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = if (isSpoof) Icons.Default.CallEnd else Icons.Default.Call,
                contentDescription = label,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label + "  分数:" + "%.3f".format(score),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


private fun startProcessing(scope: CoroutineScope, antiSpoofRealtime: AntiSpoofRealtime, onAudioSegment: (FloatArray) -> Unit, onVolume: ((Float) -> Unit)? = null) {
    scope.launch(Dispatchers.Default) {
        val audioSegmentFlow = samplesChannel.receiveAsFlow()
        SherpaOnnx.vad.reset()

        val vadAndAsrScope = CoroutineScope(Dispatchers.Default)

        audioSegmentFlow.collect { samples ->
            if (samples.isEmpty()) return@collect
            // 将原始音频片段送入实时鉴伪
            antiSpoofRealtime.accept(samples)

            // 估算音量（RMS）并回调
            val rms = kotlin.math.sqrt(samples.map { it * it }.average()).toFloat()
            onVolume?.invoke(rms)

            SherpaOnnx.vad.acceptWaveform(samples)
            while (!SherpaOnnx.vad.empty()) {
                val segment = SherpaOnnx.vad.front()
                vadAndAsrScope.launch {
                    onAudioSegment(segment.samples)
                }
                SherpaOnnx.vad.pop()
            }
        }
    }
}


//private fun startProcessing(scope: CoroutineScope, onAudioSegment: (FloatArray, Int) -> Unit) {
//    scope.launch(Dispatchers.Default) {
//        val audioSegmentFlow = samplesChannel.receiveAsFlow()
//        SherpaOnnx.vad.reset()
//
//        audioSegmentFlow.collect { samples ->
//            if (samples.isEmpty()) return@collect
//
//            SherpaOnnx.vad.acceptWaveform(samples)
//
//            val currentSegmentId = segmentIdCounter++
//
//            while (!SherpaOnnx.vad.empty()) {
//                val segment = SherpaOnnx.vad.front()
//
//                if (AudioValidator.validate(segment.samples)) {
//                    onAudioSegment(segment.samples, currentSegmentId)
//                } else {
//                    Log.w(TAG, "Skipping invalid audio segment")
//                }
//
//                SherpaOnnx.vad.pop()
//            }
//        }
//    }
//}

private fun startRecording(activity: Activity, channel: Channel<FloatArray>) {
    if (ActivityCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    audioSamplesAccumulator = mutableListOf()

    val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)

    audioRecord = AudioRecord(
        audioSource, sampleRateInHz, channelConfig, audioFormat, minBufferSize * 2
    )

    CoroutineScope(Dispatchers.IO).launch {
        Log.i(TAG, "Start recording")
        val bufferSize = (0.1 * sampleRateInHz).toInt()
        val buffer = ShortArray(bufferSize)
        audioRecord?.startRecording()
        try {
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val ret = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (ret > 0) {
                    val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                    channel.send(samples)
                    audioSamplesAccumulator?.add(samples)
                } else {
                    Log.w(TAG, "AudioRecord.read returned $ret")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording error: ${e.message}")
        } finally {
            channel.trySend(FloatArray(0))
        }
    }
}

private fun stopRecording() {
    Log.i(TAG, "Stop recording")
    try {
        if(audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord?.stop()
        }
        audioRecord?.release()
    } catch (e: Exception) {
        Log.e(TAG, "Error stopping recording: ${e.message}")
    } finally {
        audioRecord = null
    }
}

private fun saveAudioToFile(context: Context, audioData: FloatArray, sampleRate: Int) {
    if (audioData.isEmpty()) {
        Log.w(TAG, "Attempted to save empty audio recording.")
        return
    }

    val bitsPerSample: Short = 16
    val fileName = "CallRecording_${System.currentTimeMillis()}.wav"

    val storageDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
    } else {
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        File(musicDir, "Recordings").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    if (storageDir == null) {
        Log.e(TAG, "Storage directory is null. Cannot save file.")
        return
    }

    val file = File(storageDir, fileName)

    try {
        val fileOutputStream = FileOutputStream(file)
        val dataOutputStream = DataOutputStream(BufferedOutputStream(fileOutputStream))

        val shortData = ShortArray(audioData.size) { (audioData[it] * 32767.0f).toInt().toShort() }
        val byteData = ByteArray(shortData.size * 2)
        ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shortData)

        val channels = 1
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        writeWavHeader(
            dataOutputStream,
            byteData.size.toLong(),
            sampleRate.toLong(),
            channels,
            byteRate,
            bitsPerSample
        )
        dataOutputStream.write(byteData)
        dataOutputStream.close()

        Log.i(TAG, "Recording saved to: ${file.absolutePath}")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "录音已保存: ${file.name}", Toast.LENGTH_LONG).show()
        }
    } catch (e: IOException) {
        Log.e(TAG, "保存录音文件时出错: ${e.message}")
    }
}

@Throws(IOException::class)
private fun writeWavHeader(
    out: DataOutputStream,
    totalAudioLen: Long,
    longSampleRate: Long,
    channels: Int,
    byteRate: Long,
    bitsPerSample: Short
) {
    val totalDataLen = totalAudioLen + 36
    val header = ByteArray(44)
    header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    header[4] = (totalDataLen and 0xff).toByte()
    header[5] = (totalDataLen shr 8 and 0xff).toByte()
    header[6] = (totalDataLen shr 16 and 0xff).toByte()
    header[7] = (totalDataLen shr 24 and 0xff).toByte()
    header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
    header[20] = 1; header[21] = 0
    header[22] = channels.toByte(); header[23] = 0
    val sampleRateBytes =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(longSampleRate.toInt()).array()
    header[24] = sampleRateBytes[0]; header[25] = sampleRateBytes[1]; header[26] = sampleRateBytes[2]
    header[27] = sampleRateBytes[3]
    val byteRateBytes =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate.toInt()).array()
    header[28] = byteRateBytes[0]; header[29] = byteRateBytes[1]; header[30] = byteRateBytes[2]
    header[31] = byteRateBytes[3]
    header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0
    header[34] = bitsPerSample.toByte(); header[35] = 0
    header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    val audioLenBytes =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalAudioLen.toInt()).array()
    header[40] = audioLenBytes[0]; header[41] = audioLenBytes[1]; header[42] = audioLenBytes[2]
    header[43] = audioLenBytes[3]
    out.write(header, 0, 44)
}