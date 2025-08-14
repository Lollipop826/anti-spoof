package com.k2fsa.sherpa.onnx.speaker.identification.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.k2fsa.sherpa.onnx.speaker.identification.AntiSpoofRealtime
import com.k2fsa.sherpa.onnx.speaker.identification.AntiSpoofThreshold
import com.k2fsa.sherpa.onnx.speaker.identification.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AntiSpoofScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detectionActive by remember { mutableStateOf(false) }
    var lastScore by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val sampleRate = 16000
    val antiSpoofRt = remember { AntiSpoofRealtime(scope, sampleRate = sampleRate, windowSamples = 64000, hopMs = 1000) }

    var audioRecord by remember { mutableStateOf<AudioRecord?>(null) }

    fun stopAll() {
        try {
            detectionActive = false
            antiSpoofRt.stop()
            audioRecord?.let { ar ->
                if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) ar.stop()
                ar.release()
            }
        } catch (_: Throwable) {}
        audioRecord = null
    }

    // 刷新分数显示
    LaunchedEffect(detectionActive) {
        if (detectionActive) {
            lastScore = null
            while (detectionActive) {
                kotlinx.coroutines.delay(1000)
                val s = antiSpoofRt.lastScore
                lastScore = if (s >= -10f) s else null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("鉴伪测试（实时）", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Card(elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("说明：点击开始实时检测，将持续采集语音并每秒打分。分数越低越像伪造。")
                Spacer(Modifier.height(8.dp))
                Text("阈值：${AntiSpoofThreshold.DEFAULT_THRESHOLD}")
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (detectionActive) return@Button

                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                != PackageManager.PERMISSION_GRANTED) {
                                Toast.makeText(context, "缺少录音权限", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            error = null
                            lastScore = null
                            detectionActive = true
                            antiSpoofRt.reset()
                            antiSpoofRt.start()

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val channelConfig = AudioFormat.CHANNEL_IN_MONO
                                    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                                    val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                                    val bufSize = (minBuf * 2).coerceAtLeast(4096)

                                    val ar = AudioRecord(
                                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                                        sampleRate,
                                        channelConfig,
                                        audioFormat,
                                        bufSize
                                    )
                                    audioRecord = ar
                                    val buf = ShortArray(bufSize / 2)
                                    ar.startRecording()
                                    while (detectionActive) {
                                        val n = ar.read(buf, 0, buf.size)
                                        if (n > 0) {
                                            val samples = FloatArray(n) { buf[it] / 32768f }
                                            antiSpoofRt.accept(samples)
                                        } else if (n < 0) {
                                            throw RuntimeException("AudioRecord.read=$n")
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "AntiSpoof realtime error", t)
                                    withContext(Dispatchers.Main) {
                                        error = t.message ?: "未知错误"
                                        detectionActive = false
                                    }
                                    stopAll()
                                }
                            }
                        },
                        enabled = !detectionActive
                    ) { Text("开始检测") }

                    Spacer(Modifier.width(12.dp))

                    OutlinedButton(
                        onClick = { stopAll() },
                        enabled = detectionActive
                    ) { Text("结束检测") }
                }

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                when {
                    error != null -> Text("错误：${error}", color = MaterialTheme.colorScheme.error)
                    lastScore != null -> {
                        val s = lastScore!!
                        val isSpoof = s <= AntiSpoofThreshold.DEFAULT_THRESHOLD
                        Text("分数：${"%.4f".format(s)}")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isSpoof) "判断：疑似伪造" else "判断：真人语音",
                            color = if (isSpoof) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    detectionActive -> Text("检测中…")
                    else -> Text("未开始")
                }
            }
        }
    }

    // 退出页面时清理
    DisposableEffect(Unit) {
        onDispose { stopAll() }
    }
}
