package com.k2fsa.sherpa.onnx.speaker.identification.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.k2fsa.sherpa.onnx.SpeakerRecognition
import com.k2fsa.sherpa.onnx.speaker.identification.AudioValidator
import com.k2fsa.sherpa.onnx.speaker.identification.R
import com.k2fsa.sherpa.onnx.speaker.identification.TAG
import kotlin.concurrent.thread

private var audioRecord: AudioRecord? = null
private var sampleList: MutableList<FloatArray>? = null
private var embeddingList: MutableList<FloatArray>? = null
private const val sampleRateInHz = 16000
private const val MAX_REGISTRATION_SAMPLES = 16000 * 30 // 30 seconds limit

@SuppressLint("UnrememberedMutableState")
@Preview
@Composable
fun RegisterScreen() {
    val activity = LocalContext.current as Activity
    val context = LocalContext.current

    var firstTime by remember { mutableStateOf(true) }
    if (firstTime) {
        firstTime = false
        embeddingList = null
    }

    var numberAudio by remember { mutableStateOf(0) }
    var isStarted by remember { mutableStateOf(false) }
    var speakerName by remember { mutableStateOf("") }
    val onSpeakerNameChange = { newName: String -> speakerName = newName }

    val onRecordingButtonClick: () -> Unit = Button@{
        isStarted = !isStarted
        if (isStarted) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Recording is not allowed")
                isStarted = false
                return@Button
            }
            val audioSource = MediaRecorder.AudioSource.MIC
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
            audioRecord = AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, numBytes * 2)
            sampleList = mutableListOf()
            thread(true) {
                audioRecord?.startRecording()
                val buffer = ShortArray((0.1 * sampleRateInHz).toInt())
                while (isStarted) {
                    val ret = audioRecord?.read(buffer, 0, buffer.size)
                    if (ret != null && ret > 0) {
                        val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                        sampleList?.add(samples)
                    }
                }
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        } else {
            val currentSamples = sampleList?.toList() ?: emptyList()
            if (currentSamples.isEmpty()) return@Button

            val totalSamples = currentSamples.sumOf { it.size }

            when {
                totalSamples < AudioValidator.MIN_SAMPLES -> {
                    Toast.makeText(context, context.getString(R.string.audio_too_short_detailed, totalSamples, AudioValidator.MIN_SAMPLES), Toast.LENGTH_SHORT).show()
                }
                totalSamples > MAX_REGISTRATION_SAMPLES -> {
                    Toast.makeText(context, "Audio is too long for registration (max 30 seconds)", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    try {
                        val stream = SpeakerRecognition.extractor.createStream()
                        currentSamples.forEach { chunk ->
                            stream.acceptWaveform(chunk, sampleRateInHz)
                        }
                        stream.inputFinished()

                        if (SpeakerRecognition.extractor.isReady(stream)) {
                            val embedding = SpeakerRecognition.extractor.compute(stream)
                            if (embeddingList == null) embeddingList = mutableListOf()
                            embeddingList?.add(embedding)
                            numberAudio = embeddingList?.size ?: 0
                            sampleList?.clear()
                        } else {
                            Toast.makeText(context, context.getString(R.string.audio_too_short_for_processing), Toast.LENGTH_SHORT).show()
                        }
                        stream.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Feature extraction error: ${e.message}")
                        Toast.makeText(context, context.getString(R.string.processing_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val onAddButtonClick: () -> Unit = {
        val trimmedName = speakerName.trim()
        when {
            trimmedName.isBlank() ->
                Toast.makeText(context, context.getString(R.string.input_speaker_name_toast), Toast.LENGTH_SHORT).show()
            SpeakerRecognition.manager.contains(trimmedName) ->
                Toast.makeText(context, context.getString(R.string.speaker_name_exists, trimmedName), Toast.LENGTH_SHORT).show()
            else -> {
                val embeddingsArray = embeddingList!!.toTypedArray()
                val memoryOk = SpeakerRecognition.manager.add(trimmedName, embeddingsArray)
                val databaseOk = SpeakerRecognition.addSpeakerToDatabase(trimmedName, embeddingsArray)

                if (memoryOk && databaseOk) {
                    Toast.makeText(context, context.getString(R.string.add_speaker_success, trimmedName), Toast.LENGTH_SHORT).show()
                    embeddingList = null
                    sampleList = null
                    speakerName = ""
                    numberAudio = 0
                } else {
                    if (memoryOk) SpeakerRecognition.manager.remove(trimmedName)
                    Toast.makeText(context, context.getString(R.string.add_speaker_failed, trimmedName), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = speakerName,
            onValueChange = onSpeakerNameChange,
            label = { Text(stringResource(id = R.string.input_speaker_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            stringResource(id = R.string.num_recordings, numberAudio),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = onRecordingButtonClick,
                modifier = Modifier.height(48.dp)
            ) {
                Text(text = stringResource(if (isStarted) R.string.stop else R.string.start), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.width(24.dp))
            OutlinedButton(
                enabled = numberAudio > 0,
                onClick = onAddButtonClick,
                modifier = Modifier.height(48.dp)
            ) {
                Text(text = stringResource(id = R.string.add), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}