package com.k2fsa.sherpa.onnx.speaker.identification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

object TranscriptManager {
    data class Transcript(
        val speaker: String,
        val text: String,
        val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(
            Date()
        )
    )

    private val _transcripts = MutableStateFlow<List<Transcript>>(emptyList())
    val transcripts = _transcripts.asStateFlow()

    fun addTranscript(speaker: String, text: String) {
        _transcripts.update { currentList ->
            currentList + Transcript(speaker, text)
        }
    }

    fun getAllTranscripts(): List<Transcript> {
        return _transcripts.value
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    ////


}