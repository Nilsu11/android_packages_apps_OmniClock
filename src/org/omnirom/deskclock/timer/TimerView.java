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

package org.omnirom.deskclock.timer;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;


public class TimerView extends LinearLayout {

    private TextView mHoursOnes, mMinutesOnes;
    private TextView mHoursTens, mMinutesTens;
    private TextView mSeconds;

    @SuppressWarnings("unused")
    public TimerView(Context context) {
        this(context, null);
    }

    public TimerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHoursTens = (TextView) findViewById(org.omnirom.deskclock.R.id.hours_tens);
        mHoursOnes = (TextView) findViewById(org.omnirom.deskclock.R.id.hours_ones);

        mMinutesTens = (TextView) findViewById(org.omnirom.deskclock.R.id.minutes_tens);
        mMinutesOnes = (TextView) findViewById(org.omnirom.deskclock.R.id.minutes_ones);

        mSeconds = (TextView) findViewById(org.omnirom.deskclock.R.id.seconds);
    }


    public void setTime(int hoursTensDigit, int hoursOnesDigit, int minutesTensDigit,
                        int minutesOnesDigit, int seconds) {

        if (mHoursTens != null) {
            if (hoursTensDigit == -1) {
                mHoursTens.setText("-");
            } else {
                mHoursTens.setText(String.format("%d", hoursTensDigit));
            }
        }

        if (mHoursOnes != null) {
            if (hoursOnesDigit == -1) {
                mHoursOnes.setText("-");
            } else {
                mHoursOnes.setText(String.format("%d", hoursOnesDigit));
            }
        }

        if (mMinutesTens != null) {
            if (minutesTensDigit == -1) {
                mMinutesTens.setText("-");
            } else {
                mMinutesTens.setText(String.format("%d", minutesTensDigit));
            }
        }
        if (mMinutesOnes != null) {
            if (minutesOnesDigit == -1) {
                mMinutesOnes.setText("-");
            } else {
                mMinutesOnes.setText(String.format("%d", minutesOnesDigit));
            }
        }

        if (mSeconds != null) {
            mSeconds.setText(String.format("%02d", seconds));
        }
    }
}
