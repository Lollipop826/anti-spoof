package com.k2fsa.sherpa.onnx.speaker.identification.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2fsa.sherpa.onnx.SpeakerRecognition
import com.k2fsa.sherpa.onnx.speaker.identification.R

@Stable
class SelectableSpeaker(val name: String) {
    var isSelected by mutableStateOf(false)
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun ViewScreen() {
    val allSpeakerNames = SpeakerRecognition.manager.allSpeakerNames()
    val selectableSpeakers = remember {
        allSpeakerNames.map { SelectableSpeaker(it) }.toMutableStateList()
    }

    val selectedCount = selectableSpeakers.count { it.isSelected }

    Scaffold(
        floatingActionButton = {
            if (selectedCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val toRemove = selectableSpeakers.filter { it.isSelected }
                        toRemove.forEach { speaker ->
                            val memoryOk = SpeakerRecognition.manager.remove(speaker.name)
                            val databaseOk = SpeakerRecognition.removeSpeakerFromDatabase(speaker.name)
                            // ... (error handling)
                        }
                        selectableSpeakers.removeAll(toRemove)
                    },
                    icon = { Icon(Icons.Filled.Delete, contentDescription = stringResource(id = R.string.delete)) },
                    text = { Text(stringResource(id = R.string.delete_selected_count, selectedCount)) }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        if (selectableSpeakers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(id = R.string.no_speakers_registered))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = selectableSpeakers,
                    key = { it.name }
                ) { speaker ->
                    SpeakerRow(
                        speaker = speaker,
                        onSelect = { speaker.isSelected = !speaker.isSelected }
                    )
                }
            }
        }
    }
}

@Composable
fun SpeakerRow(
    speaker: SelectableSpeaker,
    onSelect: () -> Unit
) {
    val backgroundColor = if (speaker.isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = speaker.name.firstOrNull()?.uppercase() ?: "",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = speaker.name,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            if (speaker.isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}