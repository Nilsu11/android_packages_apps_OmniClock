/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.omnirom.deskclock;

import android.content.Context;
import android.text.format.DateFormat;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import org.omnirom.deskclock.alarms.TimePickerDialogFragment;
import org.omnirom.deskclock.provider.Alarm;
import org.omnirom.deskclock.provider.AlarmInstance;

import java.util.Calendar;
import java.util.Locale;

/**
 * Static utility methods for Alarms.
 */
public class AlarmUtils {
    public static final String FRAG_TAG_TIME_PICKER = "time_dialog";

    public static String getFormattedTime(Context context, Calendar time) {
        String skeleton = DateFormat.is24HourFormat(context) ? "EHm" : "Ehma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return (String) DateFormat.format(pattern, time);
    }

    public static String getAlarmText(Context context, AlarmInstance instance) {
        String alarmTimeStr = getFormattedTime(context, instance.getAlarmTime());
        return !instance.mLabel.isEmpty() ? alarmTimeStr + " - " + instance.mLabel
                : alarmTimeStr;
    }

    public static String getAlarmTitle(Context context, AlarmInstance instance) {
        String preAlarmLabel = context.getString(R.string.prealarm_default_label);
        String preAlarmPrefix = instance.mAlarmState == AlarmInstance.PRE_ALARM_STATE ? (preAlarmLabel + ": ") : "";
        String defaultLabel = instance.mAlarmState == AlarmInstance.PRE_ALARM_STATE ? preAlarmLabel : context.getString(R.string.default_label);
        return instance.mLabel.isEmpty() ? defaultLabel : (preAlarmPrefix + instance.mLabel);
    }

    /**
     * Show the time picker dialog. This is called from AlarmClockFragment to set alarm.
     * @param fragment The calling fragment (which is also a onTimeSetListener),
     *                 we use it as the target fragment of the TimePickerFragment, so later the
     *                 latter can retrieve it and set it as its onTimeSetListener when the fragment
     *                 is recreated.
     * @param alarm The clicked alarm, it can be null if user was clicking the fab instead.
     */
    public static void showTimeEditDialog(Fragment fragment, final Alarm alarm) {
        final int hour, minute;
        if (alarm == null) {
            final Calendar c = Calendar.getInstance();
            hour = c.get(Calendar.HOUR_OF_DAY);
            minute = c.get(Calendar.MINUTE);
        } else {
            hour = alarm.hour;
            minute = alarm.minutes;
        }
        TimePickerDialogFragment.show(fragment, hour, minute);
    }

    /**
     * format "Alarm set for 2 days 7 hours and 53 minutes from
     * now"
     */
    private static String formatToast(Context context, long timeInMillis) {
        long delta = timeInMillis - System.currentTimeMillis();
        long hours = delta / (1000 * 60 * 60);
        long minutes = delta / (1000 * 60) % 60;
        long days = hours / 24;
        hours = hours % 24;

        String daySeq = (days == 0) ? "" :
                (days == 1) ? context.getString(R.string.day) :
                        context.getString(R.string.days, Long.toString(days));

        String minSeq = (minutes == 0) ? "" :
                (minutes == 1) ? context.getString(R.string.minute) :
                        context.getString(R.string.minutes, Long.toString(minutes));

        String hourSeq = (hours == 0) ? "" :
                (hours == 1) ? context.getString(R.string.hour) :
                        context.getString(R.string.hours, Long.toString(hours));

        boolean dispDays = days > 0;
        boolean dispHour = hours > 0;
        boolean dispMinute = minutes > 0;

        int index = (dispDays ? 1 : 0) |
                (dispHour ? 2 : 0) |
                (dispMinute ? 4 : 0);

        String[] formats = context.getResources().getStringArray(R.array.alarm_set);
        return String.format(formats[index], daySeq, hourSeq, minSeq);
    }

    public static void popAlarmSetToast(Context context, long timeInMillis) {
        String toastText = formatToast(context, timeInMillis);
        Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_LONG);
        ToastMaster.setToast(toast);
        toast.show();
    }

    public static void popNoDefaultAlarmSoundToast(Context context) {
        Toast toast = Toast.makeText(context, R.string.no_alarm_sound_hint, Toast.LENGTH_LONG);
        ToastMaster.setToast(toast);
        toast.show();
    }
}
