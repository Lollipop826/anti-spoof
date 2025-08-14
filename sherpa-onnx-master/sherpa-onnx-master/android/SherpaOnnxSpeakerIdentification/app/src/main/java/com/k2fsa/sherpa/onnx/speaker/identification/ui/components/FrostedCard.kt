package com.k2fsa.sherpa.onnx.speaker.identification.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A reusable frosted-glass like card used to create premium look surfaces.
 * It does not apply real blur to keep dependencies light; instead it uses
 * translucent surface color + subtle border + elevation.
 */
@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderAlpha: Float = 0.06f,
    contentPadding: Int = 16,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        tonalElevation = 6.dp,
        // subtle shadow to float above background
        shadowElevation = 6.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = borderAlpha)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Column(modifier = Modifier.padding(contentPadding.dp)) {
            content()
        }
    }
}

