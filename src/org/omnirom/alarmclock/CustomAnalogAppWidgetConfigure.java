/*
 *  Copyright (C) 2016 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.omnirom.alarmclock;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.View;

import org.omnirom.deskclock.R;
import org.omnirom.deskclock.Utils;
import org.omnirom.deskclock.preference.ColorPickerPreference;

public class CustomAnalogAppWidgetConfigure extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {

    public static final String KEY_SHOW_ALARM = "show_alarm";
    public static final String KEY_SHOW_DATE = "show_date";
    public static final String KEY_SHOW_NUMBERS = "show_numbers";
    public static final String KEY_SHOW_TICKS = "show_ticks";
    public static final String KEY_24H_MODE = "show_24_hour";
    public static final String KEY_BG_COLOR = "bg_color";
    public static final String KEY_BORDER_COLOR = "border_color";
    public static final String KEY_TEXT_COLOR = "text_color";
    public static final String KEY_HOUR_COLOR = "hour_color";
    public static final String KEY_MINUTE_COLOR = "minute_color";
    public static final String KEY_ACCENT_COLOR = "accent_color";

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(Utils.getThemeResourceId(this));
        getListView().setBackgroundColor(Utils.getViewBackgroundColor(this));

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If this activity was started with an intent without an app widget ID,
        // finish with an error.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        addPreferencesFromResource(R.xml.custom_analog_appwidget_configure);
        initPreference(KEY_SHOW_ALARM, true);
        initPreference(KEY_SHOW_DATE, true);
        initPreference(KEY_SHOW_NUMBERS, false);
        initPreference(KEY_SHOW_TICKS, false);
        initPreference(KEY_24H_MODE, false);

        initColorPreference(KEY_BG_COLOR, getResources().getColor(R.color.analog_clock_bg_color));
        initColorPreference(KEY_ACCENT_COLOR, getResources().getColor(R.color.accent));
        initColorPreference(KEY_BORDER_COLOR, getResources().getColor(R.color.primary));
        initColorPreference(KEY_TEXT_COLOR, getResources().getColor(R.color.analog_clock_text_color));
        initColorPreference(KEY_HOUR_COLOR, getResources().getColor(R.color.analog_clock_hour_hand_color));
        initColorPreference(KEY_MINUTE_COLOR, getResources().getColor(R.color.analog_clock_minute_hand_color));
    }

    public void handleOkClick(View v) {
        CustomAnalogAppWidgetProvider.updateAfterConfigure(this, mAppWidgetId);
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    private void initPreference(String key, boolean defaultValue) {
        CheckBoxPreference b = (CheckBoxPreference) findPreference(key);
        b.setKey(key + "_" + String.valueOf(mAppWidgetId));
        b.setDefaultValue(defaultValue);
        b.setChecked(defaultValue);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(b.getKey(), defaultValue).commit();
    }

    private void initColorPreference(String key, int defaultValue) {
        ColorPickerPreference c = (ColorPickerPreference) findPreference(key);
        c.setKey(key + "_" + String.valueOf(mAppWidgetId));
        c.setColor(defaultValue);
        String hexColor = String.format("#%08X", defaultValue);
        c.setSummary(hexColor);
        c.setOnPreferenceChangeListener(this);
    }

    public static void clearPrefs(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().remove(KEY_SHOW_ALARM + "_" + id).commit();
        prefs.edit().remove(KEY_SHOW_DATE + "_" + id).commit();
        prefs.edit().remove(KEY_SHOW_NUMBERS + "_" + id).commit();
        prefs.edit().remove(KEY_SHOW_TICKS + "_" + id).commit();
        prefs.edit().remove(KEY_24H_MODE + "_" + id).commit();
        prefs.edit().remove(KEY_BG_COLOR + "_" + id).commit();
        prefs.edit().remove(KEY_BORDER_COLOR + "_" + id).commit();
        prefs.edit().remove(KEY_TEXT_COLOR + "_" + id).commit();
        prefs.edit().remove(KEY_HOUR_COLOR + "_" + id).commit();
        prefs.edit().remove(KEY_MINUTE_COLOR + "_" + id).commit();
        prefs.edit().remove(KEY_ACCENT_COLOR + "_" + id).commit();
    }

    public static void remapPrefs(Context context, int oldId, int newId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean oldValue = prefs.getBoolean(KEY_SHOW_ALARM + "_" + oldId, false);
        prefs.edit().putBoolean(KEY_SHOW_ALARM + "_" + newId, oldValue).commit();
        oldValue = prefs.getBoolean(KEY_SHOW_DATE + "_" + oldId, false);
        prefs.edit().putBoolean(KEY_SHOW_DATE + "_" + newId, oldValue).commit();
        oldValue = prefs.getBoolean(KEY_SHOW_NUMBERS + "_" + oldId, false);
        prefs.edit().putBoolean(KEY_SHOW_NUMBERS + "_" + newId, oldValue).commit();
        oldValue = prefs.getBoolean(KEY_SHOW_TICKS + "_" + oldId, false);
        prefs.edit().putBoolean(KEY_SHOW_TICKS + "_" + newId, oldValue).commit();
        oldValue = prefs.getBoolean(KEY_24H_MODE + "_" + oldId, false);
        prefs.edit().putBoolean(KEY_24H_MODE + "_" + newId, oldValue).commit();
        int oldInt = prefs.getInt(KEY_BG_COLOR + "_" + oldId, context.getResources().getColor(R.color.analog_clock_bg_color));
        prefs.edit().putInt(KEY_BG_COLOR + "_" + newId, oldInt).commit();
        oldInt = prefs.getInt(KEY_BORDER_COLOR + "_" + oldId, context.getResources().getColor(R.color.primary));
        prefs.edit().putInt(KEY_BORDER_COLOR + "_" + newId, oldInt).commit();
        oldInt = prefs.getInt(KEY_TEXT_COLOR + "_" + oldId, context.getResources().getColor(R.color.analog_clock_text_color));
        prefs.edit().putInt(KEY_TEXT_COLOR + "_" + newId, oldInt).commit();
        oldInt = prefs.getInt(KEY_HOUR_COLOR + "_" + oldId, context.getResources().getColor(R.color.analog_clock_hour_hand_color));
        prefs.edit().putInt(KEY_HOUR_COLOR + "_" + newId, oldInt).commit();
        oldInt = prefs.getInt(KEY_MINUTE_COLOR + "_" + oldId, context.getResources().getColor(R.color.analog_clock_minute_hand_color));
        prefs.edit().putInt(KEY_MINUTE_COLOR + "_" + newId, oldInt).commit();
        oldInt = prefs.getInt(KEY_ACCENT_COLOR + "_" + oldId, context.getResources().getColor(R.color.accent));
        prefs.edit().putInt(KEY_ACCENT_COLOR + "_" + newId, oldInt).commit();

        prefs.edit().remove(KEY_SHOW_ALARM + "_" + oldId).commit();
        prefs.edit().remove(KEY_SHOW_DATE + "_" + oldId).commit();
        prefs.edit().remove(KEY_SHOW_NUMBERS + "_" + oldId).commit();
        prefs.edit().remove(KEY_SHOW_TICKS + "_" + oldId).commit();
        prefs.edit().remove(KEY_24H_MODE + "_" + oldId).commit();
        prefs.edit().remove(KEY_BG_COLOR + "_" + oldId).commit();
        prefs.edit().remove(KEY_BORDER_COLOR + "_" + oldId).commit();
        prefs.edit().remove(KEY_TEXT_COLOR + "_" + oldId).commit();
        prefs.edit().remove(KEY_HOUR_COLOR + "_" + oldId).commit();
        prefs.edit().remove(KEY_MINUTE_COLOR + "_" + oldId).commit();
        prefs.edit().remove(KEY_ACCENT_COLOR + "_" + oldId).commit();
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference instanceof ColorPickerPreference) {
            ColorPickerPreference c = (ColorPickerPreference) preference;
            String hexColor = String.format("#%08X", c.getColor());
            preference.setSummary(hexColor);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putInt(c.getKey(), c.getColor()).commit();

            return true;
        }
        return false;
    }
}
