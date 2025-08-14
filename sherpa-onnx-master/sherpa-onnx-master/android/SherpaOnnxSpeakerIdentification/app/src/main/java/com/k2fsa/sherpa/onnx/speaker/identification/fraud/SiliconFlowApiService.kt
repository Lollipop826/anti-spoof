package com.k2fsa.sherpa.onnx.speaker.identification.fraud

import com.k2fsa.sherpa.onnx.speaker.identification.fraud.data.ChatCompletionRequest
import com.k2fsa.sherpa.onnx.speaker.identification.fraud.data.ChatCompletionResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface SiliconFlowApiService {
    @POST("v1/chat/completions")
    fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): Call<ChatCompletionResponse>
}