package com.localllm.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Monochrome Black & White palette (no heavy colors) ----
val DarkBackground = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF141414)
val DarkSurfaceVariant = Color(0xFF1F1F1F)
val DarkBorderColor = Color(0xFF2A2A2A)

val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFAFAFA)
val LightSurfaceVariant = Color(0xFFF0F0F0)
val LightBorderColor = Color(0xFFE2E2E2)

val TextPrimaryDark = Color(0xFFEDEDED)
val TextSecondaryDark = Color(0xFF9A9A9A)
val TextPrimaryLight = Color(0xFF111111)
val TextSecondaryLight = Color(0xFF6B6B6B)

// Primary is pure monochrome: white on dark, near-black on light.
val MonochromePrimaryDark = Color(0xFFFFFFFF)
val MonochromePrimaryLight = Color(0xFF111111)

// Reserved for destructive actions only.
val ErrorColor = Color(0xFFB3261E)
val ErrorColorDark = Color(0xFFCF6679)

// Neutral status tones (kept monochrome per design language).
val SuccessColor = Color(0xFF3A3A3A)
val WarningColor = Color(0xFF6B6B6B)

// Subtle neutral tone for AI "thinking" bubbles (no heavy color).
val ThinkingBubbleColor = Color(0xFF2A2A2A)
