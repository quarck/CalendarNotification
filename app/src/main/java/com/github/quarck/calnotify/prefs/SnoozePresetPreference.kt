package com.github.quarck.calnotify.prefs

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.find

class SnoozePresetPreference(ctx: Context, attrs: AttributeSet): DialogPreference(ctx, attrs)
{
    internal var snoozePresetValue: String? = null

    internal var edit: EditText? = null

    internal lateinit var context: Context

    init
    {
        context = ctx

        dialogLayoutResource = R.layout.dialog_snooze_presets;

        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)

        dialogIcon = null
    }

    override fun onBindDialogView(view: View)
    {
        super.onBindDialogView(view)

        edit = view.find<EditText>(R.id.edit_text_snooze_presets)
        if (snoozePresetValue != null)
        if (snoozePresetValue != null)
            edit?.setText(snoozePresetValue!!)
    }

    override fun onDialogClosed(positiveResult: Boolean)
    {
        // When the user selects "OK", persist the new value
        if (positiveResult)
        {
            val value = edit?.text?.toString()

            if (value != null)
            {
                val presets = Settings.parseSnoozePresets(value)
                if (presets != null)
                {
                    if (presets.size == 0)
                    {
                        snoozePresetValue = Settings.DEFAULT_SNOOZE_PRESET
                    }
                    else
                    {
                        snoozePresetValue =
                            value
                                .split(',')
                                .map { it.trim() }
                                .filter { !it.isEmpty() }
                                .joinToString(", ")
                    }

                    persistString(snoozePresetValue)

                    if (presets.size > Consts.MAX_SUPPORTED_PRESETS)
                    {
                        showMessage(R.string.error_too_many_presets)
                    }
                }
                else
                {
                    showMessage(R.string.error_cannot_parse_preset)
                }
            }
        }
    }

    private fun showMessage(id: Int)
    {
        val builder = AlertDialog.Builder(context)
        builder
            .setMessage(context.getString(id))
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { x,y -> }

        builder.create().show()
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?)
    {
        if (restorePersistedValue)
        {
            snoozePresetValue = this.getPersistedString(Settings.DEFAULT_SNOOZE_PRESET)
        }
        else if (defaultValue != null && defaultValue is String)
        {
            snoozePresetValue = defaultValue
            persistString(snoozePresetValue)
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any
    {
        return a.getString(index)
    }
}