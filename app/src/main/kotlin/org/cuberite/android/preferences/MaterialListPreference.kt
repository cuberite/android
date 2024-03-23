package org.cuberite.android.preferences

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialListPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {
    override fun onClick() {
        MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setCancelable(true)
                .setSingleChoiceItems(
                        entries,
                        findIndexOfValue(value)
                ) { dialog: DialogInterface, index: Int ->
                    if (callChangeListener(entryValues[index].toString())) {
                        setValueIndex(index)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(
                        negativeButtonText
                ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
    }
}
