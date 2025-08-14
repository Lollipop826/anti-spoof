package com.k2fsa.sherpa.onnx.speaker.identification

sealed class NavRoutes(val route: String) {
    object AntiSpoof : NavRoutes("antispoof")
    object Home : NavRoutes("home")
    object Register : NavRoutes("register")
    object View : NavRoutes("view")
    object Transcript : NavRoutes("transcript")
    object Fraud : NavRoutes("fraud")
}