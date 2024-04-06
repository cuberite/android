package org.cuberite.android.extension

import kotlinx.coroutines.flow.SharingStarted

private const val DEFAULT_TIMEOUT = 5_000L

val SharingStarted.Companion.Default: SharingStarted
    get() = WhileSubscribed(DEFAULT_TIMEOUT)