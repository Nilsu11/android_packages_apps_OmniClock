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

import android.animation.AnimatorSet;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.cardview.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import org.omnirom.deskclock.worldclock.CitiesActivity;
import org.omnirom.deskclock.worldclock.WorldClockAdapter;

import java.util.Date;

/**
 * Fragment that shows  the clock (analog or digital), the next alarm info and the world clock.
 */
public class ClockFragment extends DeskClockFragment {

    private final static String TAG = "ClockFragment";

    private TextClock mDigitalClock;
    private AnalogClock mAnalogClock;
    private View mClockFrame;
    private WorldClockAdapter mAdapter;
    private ListView mList;
    private String mDateFormat;
    private String mDateFormatForAccessibility;
    private View mDateAndAlarm;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean changed = action.equals(Intent.ACTION_TIME_CHANGED)
                    || action.equals(Intent.ACTION_TIMEZONE_CHANGED)
                    || action.equals(Intent.ACTION_LOCALE_CHANGED);
            if (changed) {
                Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mClockFrame);
                if (mAdapter != null) {
                    // *CHANGED may modify the need for showing the Home City
                    if (mAdapter.hasHomeCity() != mAdapter.needHomeCity()) {
                        mAdapter.reloadData(context);
                    } else {
                        mAdapter.notifyDataSetChanged();
                    }
                    // Locale change: update digital clock format and
                    // reload the cities list with new localized names
                    if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
                        if (mDigitalClock != null) {
                            Utils.setTimeFormat(mDigitalClock, (int) (mDigitalClock.getTextSize() / 3),
                                    (int) (mDigitalClock.getTextSize() / 3));
                        }
                        mAdapter.loadCitiesDb(context);
                        mAdapter.notifyDataSetChanged();
                    }
                }
                Utils.setQuarterHourUpdater(mHandler, mQuarterHourUpdater);
            }
            if (changed || action.equals(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)) {
                Utils.refreshAlarm(getActivity(), mClockFrame);
            }
        }
    };

    private final Handler mHandler = new Handler();

    // Thread that runs on every quarter-hour and refreshes the date.
    private final Runnable mQuarterHourUpdater = new Runnable() {
        @Override
        public void run() {
            // Update the main and world clock dates
            Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mClockFrame);
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            Utils.setQuarterHourUpdater(mHandler, mQuarterHourUpdater);
        }
    };

    public ClockFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle icicle) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.clock_fragment, container, false);
        mList = (ListView) v.findViewById(R.id.cities);
        mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startActivity(new Intent(getActivity(), CitiesActivity.class));
            }
        });
        mList.setDivider(null);
        mList.setDividerHeight(0);

        mClockFrame = v.findViewById(R.id.main_clock);
        mClockFrame.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(getActivity(), ScreensaverActivity.class));
                return true;
            }
        });

        CardView clockFrameCard = (CardView) v.findViewById(R.id.main_clock_frame_card);
        clockFrameCard.setCardBackgroundColor(Utils.getViewBackgroundColor(getActivity()));

        final TextView dateDisplay = (TextView) mClockFrame.findViewById(R.id.date);
        if (dateDisplay != null) {
            dateDisplay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // start calendar
                    Utils.startCalendarWithDate(getActivity(), new Date());
                }
            });
        }
        final TextView alarmDisplay = (TextView) mClockFrame.findViewById(R.id.nextAlarm);
        if (alarmDisplay != null) {
            alarmDisplay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // switch to alarms
                    Utils.openAlarmsTab(getActivity());
                }
            });
        }
        mDigitalClock = (TextClock)mClockFrame.findViewById(R.id.digital_clock);
        mAnalogClock = (AnalogClock) mClockFrame.findViewById(R.id.analog_clock);
        mAnalogClock.setShowSeconds(true);
        mAnalogClock.setShowDate(false);
        mAnalogClock.setShowAlarm(false);
        mAnalogClock.setShowNumbers(false);
        mAnalogClock.setShowTicks(false);
        mDateAndAlarm = mClockFrame.findViewById(R.id.date_and_alarm);
        Utils.setTimeFormat(mDigitalClock, (int) (mDigitalClock.getTextSize() / 3),
                (int) (mDigitalClock.getTextSize() / 3));
        View footerView = inflater.inflate(R.layout.blank_footer_view, mList, false);
        mList.addFooterView(footerView, null, false);
        mAdapter = new WorldClockAdapter(getActivity());
        mList.setAdapter(mAdapter);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();

        final DeskClock activity = (DeskClock) getActivity();
        setFabAppearance();
        setLeftRightButtonAppearance();

        mDateFormat = getString(R.string.abbrev_wday_month_day_no_year);
        mDateFormatForAccessibility = getString(R.string.full_wday_month_day_no_year);

        Utils.setQuarterHourUpdater(mHandler, mQuarterHourUpdater);
        // Besides monitoring when quarter-hour changes, monitor other actions that
        // effect clock time
        IntentFilter filter = new IntentFilter();
        filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        Utils.registerReceiver(mIntentReceiver, filter, activity.RECEIVER_EXPORTED, activity);

        // Resume can invoked after changing the cities list or a change in locale
        if (mAdapter != null) {
            mAdapter.loadCitiesDb(activity);
            mAdapter.reloadData(activity);
        }
        // Resume can invoked after changing the clock style.
        Utils.setClockStyle(activity, mDigitalClock, mAnalogClock);

        if (mAdapter.getCount() == 0) {
            mList.setVisibility(View.GONE);
        } else {
            mList.setVisibility(View.VISIBLE);
        }
        mAdapter.notifyDataSetChanged();

        Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mClockFrame);
        Utils.refreshAlarm(activity, mClockFrame);

        boolean showDateInside = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(SettingsActivity.KEY_ANALOG_SHOW_DATE, false);
        mAnalogClock.setShowDate(showDateInside);
        mAnalogClock.setShowAlarm(showDateInside);
        mDateAndAlarm.setVisibility(showDateInside && Utils.isClockStyleAnalog(getActivity())? View.GONE : View.VISIBLE);
        mAnalogClock.setShowNumbers(PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(SettingsActivity.KEY_ANALOG_SHOW_NUMBERS, false));
        mAnalogClock.setShowTicks(PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(SettingsActivity.KEY_ANALOG_SHOW_TICKS, false));
    }

    @Override
    public void onPause() {
        super.onPause();
        Utils.cancelQuarterHourUpdater(mHandler, mQuarterHourUpdater);
        Activity activity = getActivity();
        activity.unregisterReceiver(mIntentReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onFabClick(View view) {
        final Activity activity = getActivity();
        startActivity(new Intent(activity, CitiesActivity.class));
    }

    @Override
    public void setFabAppearance() {
        final DeskClock activity = (DeskClock) getActivity();
        if (mFab == null || !activity.isClockTab()) {
            return;
        }
        mFab.setImageResource(R.drawable.ic_fab_earth);
        mFab.setContentDescription(getString(R.string.button_cities));

        final AnimatorSet animatorSet = getFabButtonTransition(true);
        if (animatorSet != null) {
            animatorSet.start();
        }
    }

    @Override
    public void setLeftRightButtonAppearance() {
        final DeskClock activity = (DeskClock) getActivity();
        if (mLeftButton == null || mRightButton == null ||
                !activity.isClockTab()) {
            return;
        }

        boolean leftVisible = false;
        boolean rightVisible = false;
        final AnimatorSet animatorSet = getButtonTransition(leftVisible, rightVisible);
        if (animatorSet != null) {
            animatorSet.start();
        }
    }
}
