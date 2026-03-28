package com.localllm.app.ui.screens.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InputBar(
    isGenerating: Boolean,
    isModelLoaded: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    enabled = isModelLoaded,
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    text = if (isModelLoaded) "Message LocalLLM..."
                                    else "Load a model to start...",
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.6f),
                                        fontSize = 16.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Send/Stop button
            AnimatedContent(
                targetState = isGenerating,
                label = "sendButton"
            ) { generating ->
                if (generating) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSend(text.trim())
                                text = ""
                            }
                        },
                        enabled = text.isNotBlank() && isModelLoaded,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (text.isNotBlank() && isModelLoaded)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Send",
                            tint = if (text.isNotBlank() && isModelLoaded)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
