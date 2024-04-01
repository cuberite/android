package org.cuberite.android.ui.formatter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.cuberite.android.ui.theme.log

enum class LogFormat(val identifier: String) {
    LOG("log: "),
    INFO("info: "),
    WARNING("warning: "),
    ERROR("error: "),
}

@Composable
fun List<String>.formatLog(): List<AnnotatedString> {
    val logColor = MaterialTheme.colorScheme.outline
    val infoColor = MaterialTheme.colorScheme.tertiary
    val warningColor = MaterialTheme.colorScheme.error
    val errorColor = MaterialTheme.colorScheme.errorContainer
    val typography = MaterialTheme.typography
    return remember(this) {
        mapNotNull { line ->
            if (line.isEmpty()) {
                return@mapNotNull null
            }
            val format = line.logFormat()
            val color = when (format) {
                LogFormat.LOG -> logColor
                LogFormat.INFO -> infoColor
                LogFormat.WARNING -> warningColor
                LogFormat.ERROR -> errorColor
                null -> logColor
            }
            val trimmedLine = line.trim()
                .run {
                    if (format != null) {
                        replaceFirst(format.identifier, "", ignoreCase = true)
                    } else {
                        this
                    }
                }
            buildAnnotatedString {
                if (format == null) {
                    append(trimmedLine)
                } else {
                    withStyle(
                        typography
                            .log(color = color)
                            .toSpanStyle()
                    ) {
                        append(trimmedLine)
                    }
                }
            }
        }
    }
}

fun String.logFormat(): LogFormat? {
    return LogFormat.entries.find { startsWith(it.identifier, ignoreCase = true) }
}
