package com.k2fsa.sherpa.onnx.speaker.identification.fraud

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2fsa.sherpa.onnx.speaker.identification.R
import com.k2fsa.sherpa.onnx.speaker.identification.ui.components.FrostedCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FraudScreen(modifier: Modifier = Modifier, chatViewModel: ChatViewModel = viewModel()) {
    val initialText = stringResource(id = R.string.fraud_detection_initial_text)
    val apiResponseText by chatViewModel.apiResponse.observeAsState(initialText)
    var userInputText by remember { mutableStateOf("") }
    val isLoading by chatViewModel.isLoading.observeAsState(false)
    val ctx = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEBF2FF), Color.White)
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.fraud_detection_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Divider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            }
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = userInputText,
                        onValueChange = { userInputText = it },
                        label = { Text(stringResource(id = R.string.fraud_detection_label)) },
                        placeholder = { Text(stringResource(id = R.string.fraud_detection_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        minLines = 8
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { chatViewModel.getChatCompletion(userInputText) },
                            enabled = !isLoading && userInputText.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(id = R.string.fraud_detection_button)) }
                        OutlinedButton(
                            onClick = { userInputText = "" },
                            enabled = userInputText.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(id = R.string.clear)) }
                    }
                }
            }
            item {
                if (isLoading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    FrostedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(id = R.string.fraud_detection_result_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val clip = ClipData.newPlainText("fraud_result", apiResponseText)
                                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(clip)
                                Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "copy")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(text = apiResponseText, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}