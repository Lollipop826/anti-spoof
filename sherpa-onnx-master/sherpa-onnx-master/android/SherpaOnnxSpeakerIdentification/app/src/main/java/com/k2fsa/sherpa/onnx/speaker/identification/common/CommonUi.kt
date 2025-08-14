package com.k2fsa.sherpa.onnx.speaker.identification.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k2fsa.sherpa.onnx.speaker.identification.R
import com.k2fsa.sherpa.onnx.speaker.identification.TranscriptManager
import com.k2fsa.sherpa.onnx.speaker.identification.ui.theme.ActiveCallBlue
import com.k2fsa.sherpa.onnx.speaker.identification.ui.theme.UnknownSpeakerBlue

@Composable
fun TranscriptItem(
    transcript: TranscriptManager.Transcript,
    onDetectClick: (() -> Unit)? = null
) {
    val unknownSpeakerName = stringResource(id = R.string.speaker_unknown)
    val isKnown = transcript.speaker != unknownSpeakerName
    val sideBarColor = if (isKnown) ActiveCallBlue else UnknownSpeakerBlue
    val icon = if (isKnown) Icons.Default.CheckCircle else Icons.Default.HelpOutline

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(sideBarColor)
            )
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Status",
                            tint = sideBarColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = transcript.speaker,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = transcript.timestamp.substringAfter(" "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = transcript.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (onDetectClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AssistChip(onClick = onDetectClick, label = { Text(stringResource(id = R.string.detect_scam_intent)) })
                }
            }
        }
    }
}