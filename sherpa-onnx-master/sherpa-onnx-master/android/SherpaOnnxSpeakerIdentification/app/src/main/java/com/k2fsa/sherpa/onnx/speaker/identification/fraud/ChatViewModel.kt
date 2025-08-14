package com.k2fsa.sherpa.onnx.speaker.identification.fraud

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2fsa.sherpa.onnx.speaker.identification.BuildConfig
import com.k2fsa.sherpa.onnx.speaker.identification.fraud.data.ChatCompletionRequest
import com.k2fsa.sherpa.onnx.speaker.identification.fraud.data.ChatCompletionResponse
import com.k2fsa.sherpa.onnx.speaker.identification.fraud.data.Message
import com.k2fsa.sherpa.onnx.speaker.identification.fraud.data.ResponseFormat
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ChatViewModel : ViewModel() {
    private val _apiResponse = MutableLiveData<String>()
    val apiResponse: LiveData<String> = _apiResponse

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val siliconFlowApiService: SiliconFlowApiService

    init {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.siliconflow.cn/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()

        siliconFlowApiService = retrofit.create(SiliconFlowApiService::class.java)
    }

    fun getChatCompletion(userQuery: String) {
        val authToken = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}"

        _isLoading.value = true
        _apiResponse.value = "正在判断中..."

        val fullPrompt = """
            请判断以下对话内容是否具有诈骗意图。在判断时，请您特别关注以下几点：
            1. 是否冒充公检法、银行、运营商、快递、电商平台、熟人等官方或可信机构/个人。
            2. 是否以紧急、恐吓、利诱（如中奖、高收益投资、退款、补贴）等方式，诱导受害者。
            3. 最终目的是否是要求转账、汇款到陌生账户、提供银行卡号/密码/验证码、点击不明链接、下载可疑APP。
            若满足以上任意一条或多条，且可能导致用户财产损失或信息泄露，则应判断为诈骗。

            请直接给出您的判断（是诈骗 / 不是诈骗），并简要说明理由，注意：请使用纯文本格式返回。
            对话内容：
            $userQuery
        """.trimIndent()

        val messages = listOf(Message(role = "user", content = fullPrompt))

        val payload = ChatCompletionRequest(
            model = "THUDM/GLM-4-32B-0414",
            messages = messages,
            stream = false,
            max_tokens = 512,
            // enable_thinking = false,
            // thinking_budget = 4096,
            min_p = 0.05,
            stop = emptyList(),
            temperature = 0.7,
            top_p = 0.7,
            top_k = 50,
            frequency_penalty = 0.5,
            n = 1,
            response_format = ResponseFormat(type = "text"),
        )

        viewModelScope.launch {
            siliconFlowApiService.createChatCompletion(authToken, payload).enqueue(object : Callback<ChatCompletionResponse> {
                override fun onResponse(call: Call<ChatCompletionResponse>, response: Response<ChatCompletionResponse>) {
                    _isLoading.value = false
                    if (response.isSuccessful) {
                        val resultText = response.body()?.choices?.firstOrNull()?.message?.content ?: "未获取到有效结果。"
                        _apiResponse.value = resultText
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("ChatViewModel", "API Error: ${response.code()} - $errorBody")
                        _apiResponse.value = "请求失败: ${response.code()}"
                    }
                }

                override fun onFailure(call: Call<ChatCompletionResponse>, t: Throwable) {
                    _isLoading.value = false
                    Log.e("ChatViewModel", "Network Error: ${t.message}", t)
                    _apiResponse.value = "网络错误: ${t.message}"
                }
            })
        }
    }
}