package org.cuberite.android.ui.control

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ControlScreen(viewModel: ControlViewModel) {

    val state: ControlAction by viewModel.state.collectAsStateWithLifecycle()

    val isRunning: Boolean by remember {
        derivedStateOf {
            state is ControlAction.Stop
        }
    }

    val runningTransition = updateTransition(targetState = isRunning, label = null)

    val cornerRadius: Dp by runningTransition.animateDp(
        label = "Corner",
        transitionSpec = {
            spring(stiffness = Spring.StiffnessLow)
        }
    ) {
        if (it) 32.dp else 74.dp
    }

    val scale: Float by runningTransition.animateFloat(label = "Scale") {
        if (!it) 0.9F else 1F
    }

    val stateTransition = updateTransition(targetState = state, label = null)

    val containerColor: Color by stateTransition.animateColor(label = "Container") { it.containerColor }

    val contentColor: Color by stateTransition.animateColor(label = "Content") { it.contentColor }

    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarState) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(cornerRadius)
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
//                    enter = slideInVertically(initialOffsetY = { it / 2 }),
//                    exit = slideOutVertically(targetOffsetY = { it / 2 }),
                    visible = isRunning
                ) {
                    Text(
                        modifier = Modifier.padding(
                            top = 16.dp,
                            bottom = 8.dp,
                            end = 8.dp,
                            start = 8.dp
                        ),
                        text = (state as? ControlAction.Stop)?.ipAddress ?: "",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(240.dp, 148.dp)
                        .graphicsLayer {
                            clip = true
                            shape = RoundedCornerShape(cornerRadius)
                        }
                        .drawBehind {
                            drawRect(containerColor)
                        }
                        .clickable {
                            viewModel.onActionClick(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(state.stringRes),
                        color = contentColor,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}