package org.cuberite.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

fun Typography.log(color: Color = Color.Unspecified) = bodyMedium
    .copy(color = color, fontFamily = FontFamily.Monospace)

val Typography = Typography()