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
        filter { it.isNotBlank() }.map { line ->
            val format = line.logFormat
            val color = when (format) {
                LogFormat.LOG -> logColor
                LogFormat.INFO -> infoColor
                LogFormat.WARNING -> warningColor
                LogFormat.ERROR -> errorColor
                null -> logColor
            }
            val trimmedLine = line
                .trim()
                .replaceFirst(
                    oldValue = format?.identifier ?: "",
                    newValue = "",
                    ignoreCase = true
                )
            buildAnnotatedString {
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

private val String.logFormat: LogFormat?
    get() = LogFormat.entries.find { startsWith(it.identifier, ignoreCase = true) }
