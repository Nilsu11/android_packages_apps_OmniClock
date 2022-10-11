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

package org.omnirom.deskclock.timer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.omnirom.deskclock.CircleTimerView;
import org.omnirom.deskclock.Utils;


public class TimerListItem extends LinearLayout {

    CountingTimerView mTimerText;
    CircleTimerView mCircleView;
    ImageView mResetAddButton;

    long mTimerLength;

    public TimerListItem(Context context) {
        this(context, null);
    }

//    public void TimerListItem newInstance(Context context) {
//        final LayoutInflater layoutInflater =
//                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//        layoutInflater.inflate(R.layout.timer_list_item, this);
//    }

    public TimerListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTimerText = (CountingTimerView) findViewById(org.omnirom.deskclock.R.id.timer_time_text);
        mCircleView = (CircleTimerView) findViewById(org.omnirom.deskclock.R.id.timer_time);
        mCircleView.setBackgroundResource(Utils.getCircleViewBackgroundResourceId(getContext()));        mResetAddButton = (ImageView) findViewById(org.omnirom.deskclock.R.id.reset_add);
        mCircleView.setTimerMode(true);
    }

    public void set(long timerLength, long timeLeft, boolean drawRed) {
        if (mCircleView == null) {
            mCircleView = (CircleTimerView) findViewById(org.omnirom.deskclock.R.id.timer_time);
            mCircleView.setTimerMode(true);
        }
        mTimerLength = timerLength;
        mCircleView.setIntervalTime(mTimerLength);
        mCircleView.setPassedTime(timerLength - timeLeft, drawRed);
        invalidate();
    }

    public void start() {
        mResetAddButton.setImageResource(Utils.isLightTheme(getContext()) ? org.omnirom.deskclock.R.drawable.ic_plusone_black : org.omnirom.deskclock.R.drawable.ic_plusone);
        mResetAddButton.setContentDescription(getResources().getString(org.omnirom.deskclock.R.string.timer_plus_one));
        mCircleView.startIntervalAnimation();
        mTimerText.showTime(true);
        mCircleView.setVisibility(VISIBLE);
    }

    public void pause() {
        mResetAddButton.setImageResource(Utils.isLightTheme(getContext()) ? org.omnirom.deskclock.R.drawable.ic_reset_black : org.omnirom.deskclock.R.drawable.ic_reset);
        mResetAddButton.setContentDescription(getResources().getString(org.omnirom.deskclock.R.string.timer_reset));
        mCircleView.pauseIntervalAnimation();
        mTimerText.showTime(true);
        mCircleView.setVisibility(VISIBLE);
    }

    public void stop() {
        mCircleView.stopIntervalAnimation();
        mTimerText.showTime(true);
        mCircleView.setVisibility(VISIBLE);
    }

    public void timesUp() {
        mCircleView.abortIntervalAnimation();
    }

    public void done() {
        mCircleView.stopIntervalAnimation();
        mCircleView.setVisibility(VISIBLE);
        mCircleView.invalidate();
    }

    public void setLength(long timerLength) {
        mTimerLength = timerLength;
        mCircleView.setIntervalTime(mTimerLength);
        mCircleView.invalidate();
    }

    public void setTextBlink(boolean blink) {
        mTimerText.showTime(!blink);
    }

    public void setCircleBlink(boolean blink) {
        mCircleView.setVisibility(blink ? INVISIBLE : VISIBLE);
    }

    public void setResetAddButton(boolean isRunning, OnClickListener listener) {
        if (mResetAddButton == null) {
            mResetAddButton = (ImageView) findViewById(org.omnirom.deskclock.R.id.reset_add);
        }

        mResetAddButton.setImageResource(isRunning ?
                (Utils.isLightTheme(getContext()) ? org.omnirom.deskclock.R.drawable.ic_plusone_black : org.omnirom.deskclock.R.drawable.ic_plusone) :
                ((Utils.isLightTheme(getContext()) ? org.omnirom.deskclock.R.drawable.ic_reset_black : org.omnirom.deskclock.R.drawable.ic_reset)));
        mResetAddButton.setContentDescription(getResources().getString(
                isRunning ? org.omnirom.deskclock.R.string.timer_plus_one : org.omnirom.deskclock.R.string.timer_reset));
        mResetAddButton.setOnClickListener(listener);
    }

    public void setTime(long time, boolean forceUpdate) {
        if (mTimerText == null) {
            mTimerText = (CountingTimerView) findViewById(org.omnirom.deskclock.R.id.timer_time_text);
        }
        mTimerText.setTime(time, false, forceUpdate);
    }

    // Used by animator to animate the size of a timer
    @SuppressWarnings("unused")
    public void setAnimatedHeight(int height) {
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams != null) {
            layoutParams.height = height;
            requestLayout();
        }
    }

    public void registerVirtualButtonAction(final Runnable runnable) {
        mTimerText.registerVirtualButtonAction(runnable);
    }
}
