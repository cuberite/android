package org.cuberite.android.ui.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.cuberite.android.R
import org.cuberite.android.ui.formatter.formatLog

@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel) {

    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val logList = logs.formatLog()
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(logList) {
        // To reduce jitter on log update
        delay(50)
        state.animateScrollToItem(logList.size)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = (56 + 16).dp,
                start = 12.dp,
                end = 12.dp
            ),
        ) {
            items(items = logList) {
                Text(text = it)
            }
        }

        TextField(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
            value = viewModel.command,
            onValueChange = viewModel::updateCommand,
            keyboardActions = KeyboardActions(onDone = { viewModel.invokeCommand() }),
            placeholder = {
                Text(text = stringResource(R.string.inputLine_hint))
            },
            trailingIcon = {
                IconButton(onClick = viewModel::invokeCommand) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.do_execute_line)
                    )
                }
            },
        )

        AnimatedVisibility(
            modifier = Modifier
                .padding(bottom = (24 + 56).dp)
                .align(Alignment.BottomCenter),
            visible = state.canScrollForward,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            FilledTonalIconButton(
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(logList.lastIndex)
                    }
                }
            ) {
                Icon(imageVector = Icons.Rounded.ArrowDownward, contentDescription = null)
            }
        }
    }
}