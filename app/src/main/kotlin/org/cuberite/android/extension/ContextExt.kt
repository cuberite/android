package org.cuberite.android.extension

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

val Context.isSDAvailable: Boolean
    get() = getExternalFilesDirs(null).size > 1

val Context.isExternalStorageGranted: Boolean
    get() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
