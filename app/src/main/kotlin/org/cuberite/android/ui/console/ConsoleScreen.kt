package org.cuberite.android.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.cuberite.android.R
import org.cuberite.android.ui.formatter.formatLog

@Composable
fun ConsoleScreen(
    logs: String,
    command: String,
    onCommandChange: (String) -> Unit,
    onSendCommand: () -> Unit,
) {
    val logList = logs.lines().formatLog()
    val state = rememberLazyListState()
    LaunchedEffect(logs) {
        state.animateScrollToItem(logList.size)
    }
    Column(
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = state,
        ) {
            items(items = logList) {
                Text(text = it)
            }
        }
        TextField(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
            value = command,
            onValueChange = onCommandChange,
            keyboardActions = KeyboardActions(onDone = { onSendCommand() }),
            placeholder = {
                Text(text = stringResource(R.string.inputLine_hint))
            },
            trailingIcon = {
                IconButton(onClick = onSendCommand) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.do_execute_line)
                    )
                }
            },
        )
    }
}