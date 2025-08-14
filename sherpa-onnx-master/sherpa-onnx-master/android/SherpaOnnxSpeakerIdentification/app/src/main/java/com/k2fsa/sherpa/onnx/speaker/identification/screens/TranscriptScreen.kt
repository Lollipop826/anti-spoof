package com.k2fsa.sherpa.onnx.speaker.identification.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.k2fsa.sherpa.onnx.speaker.identification.R
import com.k2fsa.sherpa.onnx.speaker.identification.TranscriptManager
import com.k2fsa.sherpa.onnx.speaker.identification.TranscriptManager.Transcript
import com.k2fsa.sherpa.onnx.speaker.identification.common.TranscriptItem
import com.k2fsa.sherpa.onnx.speaker.identification.fraud.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(navController: NavController, chatViewModel: ChatViewModel) {
    val transcripts by TranscriptManager.transcripts.collectAsState()
    val (showDialog, setShowDialog) = remember { mutableStateOf(false) }

    val apiResponse by chatViewModel.apiResponse.observeAsState()
    val isLoading by chatViewModel.isLoading.observeAsState(false)

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { setShowDialog(false) },
            title = { Text(stringResource(id = R.string.fraud_dialog_title)) },
            text = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    if (isLoading == true) {
                        CircularProgressIndicator()
                    } else {
                        Text(apiResponse ?: stringResource(id = R.string.fraud_dialog_loading))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { setShowDialog(false) }) {
                    Text(stringResource(id = R.string.fraud_dialog_close))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.transcript_history_title)) },
                actions = {
                    IconButton(onClick = { TranscriptManager.clearTranscripts() }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(id = R.string.clear_all)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (transcripts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(id = R.string.no_transcripts_available))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
//                items(
//                    items = transcripts,
//                    key = { transcript -> transcript.timestamp }
//                ) { transcript ->
//                    TranscriptItem(
//                        transcript = transcript,
//                        onDetectClick = {
//                            chatViewModel.getChatCompletion(transcript.text)
//                            setShowDialog(true)
//                        }
//                    )
                // 按说话人分组显示
                val grouped = transcripts.groupBy { it.speaker }

                grouped.forEach { (speaker, transcripts) ->
                    // 合并同一说话人的文本
                    val mergedText = transcripts.joinToString("\n") {
                        "${it.timestamp}: ${it.text}"
                    }

                    item(key = speaker) {
                        TranscriptItem(
                            transcript = Transcript(
                                speaker = speaker,
                                text = mergedText,
                                timestamp = transcripts.first().timestamp
                            ),
                            onDetectClick = {
                                chatViewModel.getChatCompletion(mergedText)
                                setShowDialog(true)
                            }
                        )
                    }
                }


            }
        }
    }
}