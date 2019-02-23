package com.github.quarck.calnotify.prefs

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import com.github.quarck.calnotify.NotificationSwipeBehavior
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.findOrThrow
import java.text.DateFormat
import java.util.*

//class ButtonPreference(parent: AppCompatActivity, id: Int, f: () -> Unit) {
//    init {
//        parent.findOrThrow<View>(id).setOnClickListener({ f() })
//    }
//}

//class ButtonPreferenceTwoLine(parent: AppCompatActivity, id1: Int, id2: Int, f: () -> Unit) {
//    init {
//        parent.findOrThrow<View>(id1).setOnClickListener({ f() })
//        parent.findOrThrow<View>(id2).setOnClickListener({ f() })
//    }
//}

//class ButtonPreferenceMultipleIDs(parent: AppCompatActivity, ids: IntArray, f: () -> Unit) {
//    init {
//        for (id in ids) {
//            parent.findOrThrow<View>(id).setOnClickListener({ f() })
//        }
//    }
//}

//class SwitchPreference(
//        parent: AppCompatActivity,
//        id: Int,
//        initialValue: Boolean,
//        onChange: (Boolean) -> Unit
//) {
//    val switch: Switch = parent.findOrThrow(id)
//    init {
//        switch.isChecked = initialValue
//
//        switch.setOnClickListener({
//            val value = switch.isChecked
//            onChange(value)
//        })
//    }
//}


//class SwitchPreferenceWithLayout(
//        parent: AppCompatActivity,
//        id: Int,
//        initialValue: Boolean,
//        onChange: (Boolean) -> Unit,
//        updateLayout: (Boolean) -> Unit
//) {
//    val switch: Switch = parent.findOrThrow(id)
//    init {
//        switch.isChecked = initialValue
//
//        updateLayout(initialValue)
//
//        switch.setOnClickListener({
//            val value = switch.isChecked
//            onChange(value)
//            updateLayout(value)
//        })
//    }
//}


class PrefsSwitch(
        val context: Context,
        val inflater: LayoutInflater,
        val root: LinearLayout,
        textMain: String,
        textSecondary: String
) {
    val switch: Switch
    val text: TextView
    var onChangeFn: ((Boolean) -> Unit)? = null
    var dependingLayout: LinearLayout? = null

    init {
        val child = inflater.inflate(R.layout.pref_switch_with_text, null)
        switch = child.findOrThrow<Switch>(R.id.pref_switch_generic)
        text = child.findOrThrow<TextView>(R.id.pref_switch_generic_small_text)

        switch.text = textMain

        if (textSecondary.isNotEmpty())
            text.text = textSecondary
        else
            text.visibility = View.GONE

        switch.setOnClickListener({
            val value = switch.isChecked
            val fn = onChangeFn
            if (fn != null)
                fn(value)
            dependingLayout?.visibility = if (value) View.VISIBLE else View.GONE
        })

        root.addView(child)
    }

    fun initial(value: Boolean) {
        switch.isChecked = value
        dependingLayout?.visibility = if (value) View.VISIBLE else View.GONE
    }

    fun onChange(fn: (Boolean) -> Unit) {
        onChangeFn = fn
    }

    fun depending(fn: PrefsRoot.() -> Unit): PrefsRoot {
        val depLayout = inflater.inflate(R.layout.pref_empty_linear_layout, null)
        if (depLayout is LinearLayout) {
            dependingLayout = depLayout
            depLayout.visibility = if (switch.isChecked) View.VISIBLE else View.GONE
            root.addView(depLayout)
        }
        else {
            throw Exception("Internal error")
        }

        return PrefsRoot(context, inflater, depLayout, fn)
    }
}

class PrefsItem(
        val context: Context,
        val inflater: LayoutInflater,
        val root: LinearLayout,
        textMain: String,
        textSecondary: String,
        var onClick: PrefsItem.() -> Unit,
        shouldUseValue: Boolean = false,
        initialValue: String? = null
) {
    val main: TextView
    val secondary: TextView
    val valueField: TextView?

    init {
        val child = inflater.inflate(
                if (!shouldUseValue)
                    R.layout.pref_item
                else
                    R.layout.pref_item_with_value,
                null)
        main = child.findOrThrow<TextView>(R.id.pref_item_generic_text)
        secondary = child.findOrThrow<TextView>(R.id.pref_item_generic_text_summary)

        valueField = if (shouldUseValue) child.findOrThrow(R.id.pref_item_generic_value) else null
        if (initialValue != null)
            valueField?.text = initialValue

        main.text = textMain

        if (textSecondary.isNotEmpty())
            secondary.text = textSecondary
        else
            secondary.visibility = View.GONE

        main.setOnClickListener{ onClick() }
        valueField?.setOnClickListener { onClick() }

        root.addView(child)
    }

    fun setValue(str: String){
        valueField?.setText(str)
    }
}


class PrefsNotificationBehavior(
        val context: Context,
        inflater: LayoutInflater,
        root: LinearLayout,
        titleText: String?,
        dismissTitle: String?,
        snoozeTitle: String?,
        disallowTitle: String?,
        initialValue: NotificationSwipeBehavior,
        val onChange: (NotificationSwipeBehavior) -> Unit
) {
    val radioDismiss: RadioButton
    val radioSnooze: RadioButton
    val radioDisallow: RadioButton

    val title: TextView

    init {
        val child = inflater.inflate(R.layout.pref_notification_behavior, null)

        radioDismiss = child.findOrThrow<RadioButton>(R.id.pref_notification_swipe_dismiss)
        radioSnooze = child.findOrThrow<RadioButton>(R.id.pref_notification_swipe_snoozes)
        radioDisallow = child.findOrThrow<RadioButton>(R.id.pref_notification_swipe_disallowed)
        title = child.findOrThrow<TextView>(R.id.pref_item_notification_swipe_behavior_title)

        dismissTitle?.apply { radioDismiss.text = this }
        snoozeTitle?.apply { radioSnooze.text = this }
        disallowTitle?.apply { radioDisallow.text = this }
        titleText?.apply { title.text = this }

        radioDismiss.setOnClickListener({ onRadioClick() })
        radioSnooze.setOnClickListener({ onRadioClick() })
        radioDisallow.setOnClickListener({ onRadioClick() })

        when (initialValue) {
            NotificationSwipeBehavior.DismissEvent ->
                radioDismiss.isChecked = true

            NotificationSwipeBehavior.SnoozeEvent ->
                radioSnooze.isChecked = true

            NotificationSwipeBehavior.SwipeDisallowed ->
                radioDisallow.isChecked = true
        }

        root.addView(child)
    }

    fun onRadioClick() {
        if (radioDisallow.isChecked)
            onChange(NotificationSwipeBehavior.SwipeDisallowed)
        else if (radioSnooze.isChecked)
            onChange(NotificationSwipeBehavior.SnoozeEvent)
        else if (radioDismiss.isChecked)
            onChange(NotificationSwipeBehavior.DismissEvent)
    }
}


class PrefsHeader(val inflater: LayoutInflater, val root: LinearLayout, text: String) {
    init {
        val child = inflater.inflate(R.layout.pref_header, null)
        child.findOrThrow<TextView>(R.id.pref_header_generic_text).text = text
        root.addView(child)
    }
}

class RedNotice(val inflater: LayoutInflater, val root: LinearLayout, text: String) {
    init {
        val child = inflater.inflate(R.layout.red_notice, null)
        child.findOrThrow<TextView>(R.id.red_notice_text).text = text
        root.addView(child)
    }
}

class PrefsSmallprint(val inflater: LayoutInflater, val root: LinearLayout, text: String) {
    init {
        val child = inflater.inflate(R.layout.pref_smallprint, null)
        child.findOrThrow<TextView>(R.id.pref_smallprint_text).text = text
        root.addView(child)
    }
}

class PrefsSeparator(val inflater: LayoutInflater, val root: LinearLayout) {
    init {
        val child = inflater.inflate(R.layout.pref_separator, null)
        root.addView(child)
    }
}

class PrefsRoot(val context: Context, val inflater: LayoutInflater, val root: LinearLayout, val fn: PrefsRoot.() -> Unit) {

    init {
        this.fn()
    }

    fun switch(textMain: String, textSecondary: String, fn: PrefsSwitch.() -> Unit): PrefsSwitch  {
        val obj = PrefsSwitch(context, inflater, root, textMain, textSecondary)
        obj.fn()
        return obj
    }

    fun switch(textMainId: Int, textSecondaryId: Int, initFn: PrefsSwitch.() -> Unit): PrefsSwitch  {
        return switch(context.resources.getString(textMainId),
                context.resources.getString(textSecondaryId),
                initFn)
    }

    fun switch(textMain: String, initFn: PrefsSwitch.() -> Unit): PrefsSwitch  {
        val obj = PrefsSwitch(context, inflater, root, textMain, "")
        obj.initFn()
        return obj
    }

    fun switch(textMainId: Int, fn: PrefsSwitch.() -> Unit): PrefsSwitch  {
        return switch(context.resources.getString(textMainId), fn)
    }

    fun notificationBehavior(
            initialValue: NotificationSwipeBehavior,
            onChange: (NotificationSwipeBehavior) -> Unit
    ): PrefsNotificationBehavior {
        return PrefsNotificationBehavior(context, inflater, root, null, null, null, null, initialValue, onChange)
    }

    fun groupNotificationBehavior(
            initialValue: NotificationSwipeBehavior,
            onChange: (NotificationSwipeBehavior) -> Unit
    ): PrefsNotificationBehavior {
        return PrefsNotificationBehavior(context, inflater, root,
                context.resources.getString(R.string.notificaiton_group_swipe_prefix),
                context.resources.getString(R.string.notificaiton_group_swipe_dismisses_the_event),
                context.resources.getString(R.string.notificaiton_group_swipe_snoozes_the_event),
                context.resources.getString(R.string.notificaiton_group_swipe_disallowed),
                initialValue, onChange)
    }

    private fun item(textMain: String, textSecondary: String, onClick: PrefsItem.() -> Unit): PrefsItem  {
        return PrefsItem(context, inflater, root, textMain, textSecondary, onClick)
    }

    fun item(textMainId: Int, textSecondaryId: Int, onClick: PrefsItem.() -> Unit): PrefsItem  {
        return item(context.resources.getString(textMainId),
                context.resources.getString(textSecondaryId),
                onClick)
    }

    private fun item(textMain: String, onClick: PrefsItem.() -> Unit): PrefsItem  {
        return PrefsItem(context, inflater, root, textMain, "", onClick)
    }

    fun item(textMainId: Int, onClick: PrefsItem.() -> Unit): PrefsItem  {
        return item(context.resources.getString(textMainId), "", onClick)
    }

    private fun itemWithValue(textMain: String, textSecondary: String, initial: String, onClick: PrefsItem.() -> Unit): PrefsItem  {
        return PrefsItem(context, inflater, root, textMain, textSecondary, onClick, true, initial)
    }

    fun itemWithValue(textMainId: Int, textSecondaryId: Int, initial: String, onClick: PrefsItem.() -> Unit): PrefsItem  {
        return itemWithValue(context.resources.getString(textMainId),
                context.resources.getString(textSecondaryId),
                initial,
                onClick)
    }

    private fun itemWithValue(textMain: String, reserved: Boolean, initial: String, onClick: PrefsItem.() -> Unit): PrefsItem  {
        return PrefsItem(context, inflater, root, textMain, "", onClick, true, initial)
    }

    fun itemWithValue(textMainId: Int, initial: String, onClick: PrefsItem.() -> Unit): PrefsItem  {
        return PrefsItem(context, inflater, root, context.resources.getString(textMainId), "", onClick, true, initial)
    }

    @Suppress("DEPRECATION")
    private fun formatTime(time: Pair<Int, Int>): String {
        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        val date = Date(0, 0, 0, time.component1(), time.component2())
        return timeFormatter.format(date)
    }

    private fun timeOfDay(
            textMain: String,
            textSecondary: String,
            initialValue: Pair<Int, Int>,
            onChange: PrefsItem.(v: Pair<Int, Int>)->Unit): PrefsItem {

        return PrefsItem(
                context,
                inflater,
                root,
                textMain,
                textSecondary,
                {
                    TimeOfDayPreference(
                            context,
                            inflater,
                            initialValue,
                            {
                                setValue(formatTime(it))
                                onChange(it)
                            }
                    ).create().show()
                },
                true,
                formatTime(initialValue))
    }

    private fun timeOfDay(
            textMain: String,
            initialValue: Pair<Int, Int>,
            onChange: PrefsItem.(v: Pair<Int, Int>)->Unit): PrefsItem {

        return timeOfDay(
                textMain,
                "",
                initialValue,
                onChange
        )
    }

    fun timeOfDay(
            textMainId: Int,
            textSecondaryId: Int,
            initialValue: Pair<Int, Int>,
            onChange: PrefsItem.(v: Pair<Int, Int>)->Unit): PrefsItem {

        return timeOfDay(
                context.resources.getString(textMainId),
                context.resources.getString(textSecondaryId),
                initialValue,
                onChange
        )
    }

    fun timeOfDay(
            textMainId: Int,
            initialValue: Pair<Int, Int>,
            onChange: PrefsItem.(v: Pair<Int, Int>)->Unit): PrefsItem {

        return timeOfDay(
                context.resources.getString(textMainId),
                "",
                initialValue,
                onChange
        )
    }

    fun<T> list(
            textMainId: Int,
            textSecondaryId: Int,
            arrayNames: Array<String>,
            arrayValues: Array<T>,
            currentValue: T,
            showValue: Boolean,
            onNewValue: (valueNmae: String, value: T) -> Unit
            ): PrefsItem {

        if (arrayNames.size != arrayValues.size)
            throw Exception("Names and values arrays are not aligned")

        val currentIndex = arrayValues.indexOf(currentValue)

        return PrefsItem(
                context = context,
                inflater = inflater,
                root = root,
                textMain = context.resources.getString(textMainId),
                textSecondary = context.resources.getString(textSecondaryId),
                onClick = {
                    ListPreference<T>(
                            context,
                            textMainId,
                            arrayNames,
                            arrayValues,
                            {
                                name, value ->

                                if (showValue)
                                    this@PrefsItem.setValue(name)

                                onNewValue(name, value)
                            }
                    ).create()
                },
                shouldUseValue = showValue,
                initialValue = if (showValue && currentIndex >= 0 && currentIndex < arrayNames.size)
                    arrayNames[currentIndex] else null)
    }

    fun header(text: String): PrefsHeader {
        return PrefsHeader(inflater, root, text)
    }

    fun header(textId: Int): PrefsHeader {
        return header(context.resources.getString(textId))
    }

    fun red_notice(text: String ): RedNotice {
        return RedNotice(inflater, root, text)
    }

    fun red_notice(textId: Int): RedNotice {
        return red_notice(context.resources.getString(textId))
    }

    fun smallprint(text: String): PrefsSmallprint {
        return PrefsSmallprint(inflater, root, text)
    }

    fun smallprint(textId: Int): PrefsSmallprint {
        return smallprint(context.resources.getString(textId))
    }

    fun separator(): PrefsSeparator {
        return PrefsSeparator(inflater, root)
    }
}

fun preferences(activity: AppCompatActivity, initFunc: PrefsRoot.() -> Unit): PrefsRoot {
    activity.setContentView(R.layout.activity_pref_root)
    return PrefsRoot(
            activity,
            activity.layoutInflater,
            activity.findOrThrow<LinearLayout>(R.id.notification_pref_root),
            initFunc
    )
}