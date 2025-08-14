package com.k2fsa.sherpa.onnx.speaker.identification.fraud.data

data class ChatCompletionResponse(val choices: List<Choice>)
data class Choice(val message: ResponseMessage)
data class ResponseMessage(val content: String)