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
 * limitations under the License.
 */

package org.omnirom.deskclock;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextClock;

import androidx.appcompat.app.AppCompatActivity;

import org.omnirom.deskclock.Utils.ScreensaverMoveSaverRunnable;

public class ScreensaverActivity extends AppCompatActivity {
    static final boolean DEBUG = false;
    static final String TAG = "DeskClock/ScreensaverActivity";

    // This value must match android:defaultValue of
    // android:key="screensaver_clock_style" in dream_settings.xml
    static final String DEFAULT_CLOCK_STYLE = "digital";

    private View mContentView, mSaverView;
    private AnalogClock mAnalogClock;
    private TextClock mDigitalClock;
    private View mDateAlarmLine;

    private final Handler mHandler = new Handler();
    private final ScreensaverMoveSaverRunnable mMoveSaverRunnable;
    private String mDateFormat;
    private String mDateFormatForAccessibility;
    private String mClockStyle;
    private boolean mPluggedIn = true;
    private final int mFlags = (WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) {
                Log.v(TAG, "ScreensaverActivity onReceive, action: " + intent.getAction());
            }

            boolean changed = intent.getAction().equals(Intent.ACTION_TIME_CHANGED)
                    || intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED);
            if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
                mPluggedIn = true;
                setWakeLock();
            } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
                mPluggedIn = false;
                setWakeLock();
            } else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                finish();
            }

            if (changed) {
                Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mContentView);
                Utils.refreshAlarm(ScreensaverActivity.this, mContentView);
                Utils.setMidnightUpdater(mHandler, mMidnightUpdater);
            }

            if (intent.getAction().equals(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)) {
                Utils.refreshAlarm(ScreensaverActivity.this, mContentView);
            }
        }
    };

    // Thread that runs every midnight and refreshes the date.
    private final Runnable mMidnightUpdater = new Runnable() {
        Context mContext;
        @Override
        public void run() {
            Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mContentView);
            Utils.setMidnightUpdater(mHandler, mMidnightUpdater);
        }
    };

    public ScreensaverActivity() {
        if (DEBUG) Log.d(TAG, "Screensaver allocated");
        mMoveSaverRunnable = new ScreensaverMoveSaverRunnable(mHandler);
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        Utils.registerReceiver(mIntentReceiver, filter, RECEIVER_EXPORTED, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent chargingIntent =
                registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = chargingIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        mPluggedIn = plugged == BatteryManager.BATTERY_PLUGGED_AC
                || plugged == BatteryManager.BATTERY_PLUGGED_USB
                || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;

        mDateFormat = getString(R.string.abbrev_wday_month_day_no_year);
        mDateFormatForAccessibility = getString(R.string.full_wday_month_day_no_year);

        setWakeLock();
        layoutClockSaver();
        mHandler.post(mMoveSaverRunnable);

        Utils.setMidnightUpdater(mHandler, mMidnightUpdater);
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacks(mMoveSaverRunnable);
        Utils.cancelMidnightUpdater(mHandler, mMidnightUpdater);
        finish();
        super.onPause();
    }

    @Override
    public void onStop() {
        unregisterReceiver(mIntentReceiver);
        super.onStop();
   }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (DEBUG) Log.d(TAG, "Screensaver configuration changed");
        super.onConfigurationChanged(newConfig);
        mHandler.removeCallbacks(mMoveSaverRunnable);
        layoutClockSaver();
        mHandler.postDelayed(mMoveSaverRunnable, Screensaver.ORIENTATION_CHANGE_DELAY_MS);
    }

    @Override
    public void onUserInteraction() {
        // We want the screen saver to exit upon user interaction.
        finish();
    }

    private void setWakeLock() {
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        if (mPluggedIn)
            winParams.flags |= mFlags;
        else
            winParams.flags &= (~mFlags);
        win.setAttributes(winParams);
    }

    private void setClockStyle() {
        Utils.setClockStyle(this, mDigitalClock, mAnalogClock);
        mSaverView = findViewById(R.id.main_clock);
        mClockStyle = (mSaverView == mDigitalClock ?
                Utils.CLOCK_TYPE_DIGITAL : Utils.CLOCK_TYPE_ANALOG);
        Utils.dimClockView(true, mSaverView);
        if (Utils.isClockStyleAnalog(this)) {
            mDateAlarmLine.setVisibility(View.GONE);
        } else {
            mDateAlarmLine.setVisibility(View.VISIBLE);
        }
    }

    private void layoutClockSaver() {
        setContentView(R.layout.desk_clock_saver);
        mDigitalClock = (TextClock) findViewById(R.id.digital_clock);
        mAnalogClock = (AnalogClock) findViewById(R.id.analog_clock);
        mAnalogClock.setShowSeconds(false);
        mDateAlarmLine = findViewById(R.id.date_alarm_line);

        boolean showDateInside = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(SettingsActivity.KEY_ANALOG_SHOW_DATE, false);
        mAnalogClock.setShowDate(showDateInside);
        mAnalogClock.setShowAlarm(showDateInside);
        mDateAlarmLine.setVisibility(showDateInside && Utils.isClockStyleAnalog(this)? View.GONE : View.VISIBLE);
        mAnalogClock.setShowNumbers(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(SettingsActivity.KEY_ANALOG_SHOW_NUMBERS, false));
        mAnalogClock.setShowTicks(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(SettingsActivity.KEY_ANALOG_SHOW_TICKS, false));

        setClockStyle();
        Utils.setTimeFormat(mDigitalClock, (int) (mDigitalClock.getTextSize() / 3), 0);

        mContentView = (View) mSaverView.getParent();
        mContentView.forceLayout();
        mSaverView.forceLayout();
        mSaverView.setAlpha(0);

        mMoveSaverRunnable.registerViews(mContentView, mSaverView);

        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        Utils.updateDate(mDateFormat, mDateFormatForAccessibility,mContentView);
        Utils.refreshAlarm(ScreensaverActivity.this, mContentView);
    }

}
