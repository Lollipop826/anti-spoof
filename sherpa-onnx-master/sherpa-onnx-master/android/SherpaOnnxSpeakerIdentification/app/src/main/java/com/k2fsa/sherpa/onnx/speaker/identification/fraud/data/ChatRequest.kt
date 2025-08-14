package com.k2fsa.sherpa.onnx.speaker.identification.fraud.data

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean,
    @SerializedName("max_tokens") val max_tokens: Int,
    // @SerializedName("enable_thinking") val enable_thinking: Boolean,
    // @SerializedName("thinking_budget") val thinking_budget: Int,
    @SerializedName("min_p") val min_p: Double,
    val stop: List<String>,
    val temperature: Double,
    @SerializedName("top_p") val top_p: Double,
    @SerializedName("top_k") val top_k: Int,
    @SerializedName("frequency_penalty") val frequency_penalty: Double,
    val n: Int,
    @SerializedName("response_format") val response_format: ResponseFormat
)

data class Message(val role: String, val content: String)
data class ResponseFormat(val type: String)