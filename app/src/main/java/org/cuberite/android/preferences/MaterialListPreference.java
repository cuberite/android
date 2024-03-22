package org.cuberite.android.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MaterialListPreference extends ListPreference {
    public MaterialListPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        final Context context = getContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(getTitle())
                .setCancelable(true)
                .setSingleChoiceItems(
                        getEntries(),
                        findIndexOfValue(getValue()),
                        (dialog, index) -> {
                            if (callChangeListener(getEntryValues()[index].toString())) {
                                setValueIndex(index);
                            }
                            dialog.dismiss();
                        })
                .setNegativeButton(
                        getNegativeButtonText(),
                        (dialog, button) -> dialog.dismiss())
                .show();
    }
}
