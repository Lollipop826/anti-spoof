package com.k2fsa.sherpa.onnx.speaker.identification

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Security

object NavBarItems {
    // Note: The string titles are now handled directly in MainActivity
    // using stringResource for localization. This list is now only
    // used to provide the icons.
    val BarItems = listOf(
        BarItem(title = "Home", image = Icons.Filled.Home, route = "home"),
        BarItem(title = "Register", image = Icons.Filled.PersonAdd, route = "register"),
        BarItem(title = "View", image = Icons.Filled.AccountCircle, route = "view"),
        BarItem(title = "Fraud Check", image = Icons.Outlined.Security, route = "fraud"),
        BarItem(title = "Transcript", image = Icons.Filled.Description, route = "transcript"),
    )
}