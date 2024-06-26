/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.omnirom.deskclock.alarms;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.omnirom.deskclock.AlarmUtils;
import org.omnirom.deskclock.AnimatorUtils;
import org.omnirom.deskclock.LogUtils;
import org.omnirom.deskclock.R;
import org.omnirom.deskclock.SettingsActivity;
import org.omnirom.deskclock.Utils;
import org.omnirom.deskclock.provider.AlarmInstance;

public class AlarmActivity extends AppCompatActivity implements View.OnClickListener, View.OnTouchListener {

    private static final String LOGTAG = AlarmActivity.class.getSimpleName();

    private static final Interpolator PULSE_INTERPOLATOR =
            new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
    private static final Interpolator REVEAL_INTERPOLATOR =
            new PathInterpolator(0.0f, 0.0f, 0.2f, 1.0f);

    private static final int PULSE_DURATION_MILLIS = 1000;
    private static final int ALARM_BOUNCE_DURATION_MILLIS = 500;
    private static final int ALERT_SOURCE_DURATION_MILLIS = 250;
    private static final int ALERT_REVEAL_DURATION_MILLIS = 500;
    private static final int ALERT_FADE_DURATION_MILLIS = 500;
    private static final int ALERT_DISMISS_DELAY_MILLIS = 2000;

    private static final float BUTTON_SCALE_DEFAULT = 0.7f;
    private static final int BUTTON_DRAWABLE_ALPHA_DEFAULT = 165;

    private final Handler mHandler = new Handler();
    private SensorManager mSensorManager;
    private String mFlipAction;
    private String mShakeAction;
    private String mWaveAction;
    private FlipSensorListener mFlipListener;
    private ShakeSensorListener mShakeListener;
    private ProximitySensorListener mProxiListener;
    private boolean mPreAlarmMode;
    private long mInstanceId;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            LogUtils.v(LOGTAG, "Received broadcast: " + action);

            switch (action) {
                case AlarmConstants.ALARM_DONE_ACTION:
                    // DONE MUST never be missed else all the listeners will not be 
                    // removed correctly
                    finish();
                    break;
                case AlarmConstants.ALARM_MEDIA_ACTION:
                    //String metaData = intent.getStringExtra(AlarmConstants.DATA_ALARM_EXTRA_NAME);
                    //updateMediaInfo(metaData);
                    break;
                default:
                    LogUtils.v(LOGTAG, "Unknown broadcast: " + action);
                    break;
            }
        }
    };

    private AlarmInstance mAlarmInstance;
    private boolean mAlarmHandled;
    private String mVolumeBehavior;
    private int mCurrentHourColor;
    private boolean mBackgroundImage = true;
    private ViewGroup mContainerView;

    private ViewGroup mContentView;
    private ImageButton mAlarmButton;
    private ImageButton mSnoozeButton;
    private ImageButton mDismissButton;
    private TextView mHintView;
    private TextView mTitleViewSub;

    private ValueAnimator mAlarmAnimator;
    private ValueAnimator mSnoozeAnimator;
    private ValueAnimator mDismissAnimator;
    private ValueAnimator mPulseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInstanceId = AlarmInstance.getId(getIntent().getData());
        mAlarmInstance = AlarmInstance.getInstance(this.getContentResolver(), mInstanceId);
        if (mAlarmInstance != null) {
            LogUtils.v(LOGTAG, "Displaying alarm for instance: " + mAlarmInstance);
        } else {
            // The alarm got deleted before the activity got created, so just finish()
            LogUtils.e(LOGTAG, "Error displaying alarm for intent:" + getIntent());
            finish();
            return;
        }

        mPreAlarmMode = mAlarmInstance.mAlarmState == AlarmInstance.PRE_ALARM_STATE;
        LogUtils.v(LOGTAG, "Pre-alarm mode: " + mPreAlarmMode);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mFlipListener = new FlipSensorListener(new Runnable(){
            @Override
            public void run() {
                if (!mAlarmHandled) {
                    LogUtils.v(LOGTAG, "mFlipListener: " + mAlarmInstance);
                    handleAction(mFlipAction);
                }
            }
        });

        mShakeListener = new ShakeSensorListener(new Runnable(){
            @Override
            public void run() {
                if (!mAlarmHandled) {
                    LogUtils.v(LOGTAG, "mShakeAction: " + mAlarmInstance);
                    handleAction(mShakeAction);
                }
            }
        });

        mProxiListener = new ProximitySensorListener(new Runnable(){
            @Override
            public void run() {
                if (!mAlarmHandled) {
                    LogUtils.v(LOGTAG, "mWaveAction: " + mAlarmInstance);
                    handleAction(mWaveAction);
                }
            }
        }, mSensorManager);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Get the volume/camera button behavior setting
        mVolumeBehavior = prefs.getString(SettingsActivity.KEY_VOLUME_BEHAVIOR,
                        SettingsActivity.DEFAULT_ALARM_ACTION);

        mFlipAction = prefs.getString(SettingsActivity.KEY_FLIP_ACTION,
                SettingsActivity.DEFAULT_ALARM_ACTION);

        mShakeAction = prefs.getString(SettingsActivity.KEY_SHAKE_ACTION,
                SettingsActivity.DEFAULT_ALARM_ACTION);

        mWaveAction = prefs.getString(SettingsActivity.KEY_WAVE_ACTION,
                SettingsActivity.DEFAULT_ALARM_ACTION);

        LogUtils.v(LOGTAG, "mVolumeBehavior: " + mVolumeBehavior + " mFlipAction " + mFlipAction  +
                " mShakeAction " + mShakeAction + " mWaveAction " + mWaveAction);

        final boolean keepScreenOn = prefs.getBoolean(SettingsActivity.KEY_KEEP_SCREEN_ON, true);
        final boolean makeScreenDark = prefs.getBoolean(SettingsActivity.KEY_MAKE_SCREEN_DARK, false);

        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);

        if (keepScreenOn){
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (makeScreenDark){
            win.getAttributes().screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
        }

        // In order to allow tablets to freely rotate and phones to stick
        // with "nosensor" (use default device orientation) we have to have
        // the manifest start with an orientation of unspecified" and only limit
        // to "nosensor" for phones. Otherwise we get behavior like in b/8728671
        // where tablets start off in their default orientation and then are
        // able to freely rotate.
        if (!getResources().getBoolean(org.omnirom.deskclock.R.bool.config_rotateAlarmAlert)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }

        setContentView(org.omnirom.deskclock.R.layout.alarm_activity);

        mContainerView = (ViewGroup) findViewById(android.R.id.content);

        mContentView = (ViewGroup) mContainerView.findViewById(org.omnirom.deskclock.R.id.content);
        mAlarmButton = (ImageButton) mContentView.findViewById(org.omnirom.deskclock.R.id.alarm);
        mSnoozeButton = (ImageButton) mContentView.findViewById(org.omnirom.deskclock.R.id.snooze);
        mDismissButton = (ImageButton) mContentView.findViewById(org.omnirom.deskclock.R.id.dismiss);
        mHintView = (TextView) mContentView.findViewById(org.omnirom.deskclock.R.id.hint);

        final TextView titleView = (TextView) mContentView.findViewById(org.omnirom.deskclock.R.id.title);
        mTitleViewSub = (TextView) mContentView.findViewById(org.omnirom.deskclock.R.id.title_sub);
        String alarmName = null;
        if (mPreAlarmMode) {
            alarmName = mAlarmInstance.getPreAlarmRingtoneName();
        } else {
            alarmName = mAlarmInstance.getRingtoneName();
        }
        updateMediaInfo(alarmName);

        final TextClock digitalClock = (TextClock) mContentView.findViewById(org.omnirom.deskclock.R.id.digital_clock);
        final View pulseView = mContentView.findViewById(org.omnirom.deskclock.R.id.pulse);

        titleView.setText(AlarmUtils.getAlarmTitle(this, mAlarmInstance));
        Utils.setTimeFormat(digitalClock, (int) (digitalClock.getTextSize() / 3), 0);

        mCurrentHourColor = Utils.getCurrentHourColor();
        if (!Utils.showBackgroundImage(this)) {
            mContainerView.setBackgroundColor(mCurrentHourColor);
        } else {
            ImageView background = (ImageView) findViewById(org.omnirom.deskclock.R.id.background);
            background.setImageResource(Utils.getCurrentHourImage());
        }

        mAlarmButton.setOnTouchListener(this);
        if (!AlarmStateManager.canSnooze(this)) {
            mSnoozeButton.setVisibility(View.GONE);
        } else {
            mSnoozeButton.setOnClickListener(this);
        }
        mDismissButton.setOnClickListener(this);

        mAlarmAnimator = AnimatorUtils.getScaleAnimator(mAlarmButton, 1.0f, 0.0f);
        mSnoozeAnimator = getButtonAnimator(mSnoozeButton, getResources().getColor(R.color.snooze_circle_bg));
        mDismissAnimator = getButtonAnimator(mDismissButton, getResources().getColor(R.color.dismiss_circle_bg));
        mPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(pulseView,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.0f, 1.0f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.0f, 1.0f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.0f));
        mPulseAnimator.setDuration(PULSE_DURATION_MILLIS);
        mPulseAnimator.setInterpolator(PULSE_INTERPOLATOR);
        mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mPulseAnimator.start();

        // Set the animators to their initial values.
        setAnimatedFractions(0.0f /* snoozeFraction */, 0.0f /* dismissFraction */);

        // Register to get the alarm done/snooze/dismiss intent.
        final IntentFilter filter = new IntentFilter(AlarmConstants.ALARM_DONE_ACTION);
        filter.addAction(AlarmConstants.ALARM_MEDIA_ACTION);

        Utils.registerReceiver(mReceiver, filter, RECEIVER_NOT_EXPORTED, this);

        if (getResources().getBoolean(org.omnirom.deskclock.R.bool.config_disableSensorOnWirelessCharging)) {
            Intent chargingIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int plugged = chargingIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            boolean wirelessPluggedIn = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;

            if (!wirelessPluggedIn) {
                attachListeners();
            } else {
                LogUtils.v(LOGTAG, "detected wireless charging - disabled sensors");
            }
        } else {
            attachListeners();
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onDestroy() {
        LogUtils.v(LOGTAG, "onDestroy");
        super.onDestroy();

        // If the alarm instance is null the receiver was never registered and calling
        // unregisterReceiver will throw an exception.
        if (mAlarmInstance != null) {
            detachListeners();
            unregisterReceiver(mReceiver);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        // Do this in dispatch to intercept a few of the system keys.
        LogUtils.v(LOGTAG, "dispatchKeyEvent: " + keyEvent);

        switch (keyEvent.getKeyCode()) {
            // Volume keys and camera keys dismiss the alarm.
            //case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            //case KeyEvent.KEYCODE_VOLUME_MUTE:
            //case KeyEvent.KEYCODE_CAMERA:
            //case KeyEvent.KEYCODE_FOCUS:
                if (!mAlarmHandled && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    LogUtils.v(LOGTAG, "keyAction: " + mAlarmInstance);
                    if (handleAction(mVolumeBehavior)) {
                        return true;
                    }
                }
            default:
                return super.dispatchKeyEvent(keyEvent);
        }
    }

    @Override
    public void onBackPressed() {
        // Don't allow back to dismiss.
    }

    private void attachListeners() {
        if (!mFlipAction.equals(SettingsActivity.ALARM_NO_ACTION)) {
            LogUtils.v(LOGTAG, "register mFlipListener");
            mFlipListener.reset();
            mSensorManager.registerListener(mFlipListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (!mShakeAction.equals(SettingsActivity.ALARM_NO_ACTION)) {
            LogUtils.v(LOGTAG, "register mShakeListener");
            mShakeListener.reset();
            mSensorManager.registerListener(mShakeListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (!mWaveAction.equals(SettingsActivity.ALARM_NO_ACTION)) {
            LogUtils.v(LOGTAG, "register mProxiListener");
            mProxiListener.reset();
            mSensorManager.registerListener(mProxiListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void detachListeners() {
        if (!mFlipAction.equals(SettingsActivity.ALARM_NO_ACTION)) {
            LogUtils.v(LOGTAG, "unregister mFlipListener");
            mSensorManager.unregisterListener(mFlipListener);
        }
        if (!mShakeAction.equals(SettingsActivity.ALARM_NO_ACTION)) {
            LogUtils.v(LOGTAG, "unregister mShakeListener");
            mSensorManager.unregisterListener(mShakeListener);
        }
        if (!mWaveAction.equals(SettingsActivity.ALARM_NO_ACTION)) {
            LogUtils.v(LOGTAG, "unregister mProxiListener");
            mSensorManager.unregisterListener(mProxiListener);
        }
    }

    @Override
    public void onClick(View view) {
        if (mAlarmHandled) {
            LogUtils.v(LOGTAG, "onClick ignored: " + view);
            return;
        }
        LogUtils.v(LOGTAG, "onClick: " + view);

        final int alarmLeft = mAlarmButton.getLeft() + mAlarmButton.getPaddingLeft();
        final int alarmRight = mAlarmButton.getRight() - mAlarmButton.getPaddingRight();
        final float translationX = Math.max(view.getLeft() - alarmRight, 0)
                + Math.min(view.getRight() - alarmLeft, 0);
        getAlarmBounceAnimator(translationX, translationX < 0.0f ?
                org.omnirom.deskclock.R.string.description_direction_left : org.omnirom.deskclock.R.string.description_direction_right).start();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (mAlarmHandled) {
            LogUtils.v(LOGTAG, "onTouch ignored: " + motionEvent);
            return false;
        }

        final int[] contentLocation = {0, 0};
        mContentView.getLocationOnScreen(contentLocation);

        final float x = motionEvent.getRawX() - contentLocation[0];
        final float y = motionEvent.getRawY() - contentLocation[1];

        final int alarmLeft = mAlarmButton.getLeft() + mAlarmButton.getPaddingLeft();
        final int alarmRight = mAlarmButton.getRight() - mAlarmButton.getPaddingRight();

        final float snoozeFraction, dismissFraction;
        if (mContentView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            snoozeFraction = getFraction(alarmRight, mSnoozeButton.getLeft(), x);
            dismissFraction = getFraction(alarmLeft, mDismissButton.getRight(), x);
        } else {
            snoozeFraction = getFraction(alarmLeft, mSnoozeButton.getRight(), x);
            dismissFraction = getFraction(alarmRight, mDismissButton.getLeft(), x);
        }
        setAnimatedFractions(snoozeFraction, dismissFraction);

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                LogUtils.v(LOGTAG, "onTouch started: " + motionEvent);

                // Stop the pulse, allowing the last pulse to finish.
                mPulseAnimator.setRepeatCount(0);
                break;
            case MotionEvent.ACTION_UP:
                LogUtils.v(LOGTAG, "onTouch ended: " + motionEvent);

                if (snoozeFraction == 1.0f) {
                    snooze();
                } else if (dismissFraction == 1.0f) {
                    dismiss();
                } else {
                    if (snoozeFraction > 0.0f || dismissFraction > 0.0f) {
                        // Animate back to the initial state.
                        AnimatorUtils.reverse(mAlarmAnimator, mSnoozeAnimator, mDismissAnimator);
                    } else if (mAlarmButton.getTop() <= y && y <= mAlarmButton.getBottom()) {
                        // User touched the alarm button, hint the dismiss action.
                        mDismissButton.performClick();
                    }

                    // Restart the pulse.
                    mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                    if (!mPulseAnimator.isStarted()) {
                        mPulseAnimator.start();
                    }
                }
                break;
            default:
                break;
        }

        return true;
    }

    private void snooze() {
        if (!AlarmStateManager.canSnooze(this)) {
            return;
        }
        mAlarmHandled = true;
        LogUtils.v(LOGTAG, "Snoozed: " + mAlarmInstance);

        try {
            final int revealColor = getResources().getColor(org.omnirom.deskclock.R.color.snooze_circle_bg);
            setAnimatedFractions(1.0f /* snoozeFraction */, 0.0f /* dismissFraction */);
            getAlertAnimator(mSnoozeButton, org.omnirom.deskclock.R.string.alarm_alert_snoozed_text,
                    AlarmStateManager.getSnoozedMinutes(this), revealColor, mCurrentHourColor).start();
        } catch (Exception e) {
        }
        AlarmStateManager.setSnoozeState(this, mAlarmInstance, false /* showToast */);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, ALERT_DISMISS_DELAY_MILLIS);
    }

    private void dismiss() {
        mAlarmHandled = true;
        LogUtils.v(LOGTAG, "Dismissed: " + mAlarmInstance);

        try {
            setAnimatedFractions(0.0f /* snoozeFraction */, 1.0f /* dismissFraction */);
            final int revealColor = getResources().getColor(org.omnirom.deskclock.R.color.dismiss_circle_bg);
            getAlertAnimator(mDismissButton, org.omnirom.deskclock.R.string.alarm_alert_off_text, null /* infoText */,
                    revealColor, mCurrentHourColor).start();
        AlarmStateManager.setDismissState(this, mAlarmInstance);
        } catch (Exception e) {
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, ALERT_DISMISS_DELAY_MILLIS);
    }

    private void setAnimatedFractions(float snoozeFraction, float dismissFraction) {
        if (Utils.isLollipopMR1OrLater()) {
            final float alarmFraction = Math.max(snoozeFraction, dismissFraction);
            mAlarmAnimator.setCurrentFraction(alarmFraction);
            mSnoozeAnimator.setCurrentFraction(snoozeFraction);
            mDismissAnimator.setCurrentFraction(dismissFraction);
        }
    }

    private float getFraction(float x0, float x1, float x) {
        return Math.max(Math.min((x - x0) / (x1 - x0), 1.0f), 0.0f);
    }

    private ValueAnimator getButtonAnimator(ImageButton button, int tintColor) {
        return ObjectAnimator.ofPropertyValuesHolder(button,
                PropertyValuesHolder.ofFloat(View.SCALE_X, BUTTON_SCALE_DEFAULT, 1.0f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, BUTTON_SCALE_DEFAULT, 1.0f),
                PropertyValuesHolder.ofInt(AnimatorUtils.BACKGROUND_ALPHA, 0, 255),
                /*PropertyValuesHolder.ofInt(AnimatorUtils.DRAWABLE_ALPHA,
                        BUTTON_DRAWABLE_ALPHA_DEFAULT, 255),*/
                PropertyValuesHolder.ofObject(AnimatorUtils.DRAWABLE_TINT,
                        AnimatorUtils.ARGB_EVALUATOR, tintColor, Color.WHITE));
    }

    private ValueAnimator getAlarmBounceAnimator(float translationX, final int hintResId) {
        final ValueAnimator bounceAnimator = ObjectAnimator.ofFloat(mAlarmButton,
                View.TRANSLATION_X, mAlarmButton.getTranslationX(), translationX, 0.0f);
        bounceAnimator.setInterpolator(AnimatorUtils.DECELERATE_ACCELERATE_INTERPOLATOR);
        bounceAnimator.setDuration(ALARM_BOUNCE_DURATION_MILLIS);
        bounceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                mHintView.setText(hintResId);
                if (mHintView.getVisibility() != View.VISIBLE) {
                    mHintView.setVisibility(View.VISIBLE);
                    ObjectAnimator.ofFloat(mHintView, View.ALPHA, 0.0f, 1.0f).start();
                }
            }
        });
        return bounceAnimator;
    }

    private Animator getAlertAnimator(final View source, final int titleResId,
            final String infoText, final int revealColor, final int backgroundColor) {
        final ViewGroupOverlay overlay = mContainerView.getOverlay();

        // Create a transient view for performing the reveal animation.
        final View revealView = new View(this);
        revealView.setRight(mContainerView.getWidth());
        revealView.setBottom(mContainerView.getHeight());
        revealView.setBackgroundColor(revealColor);
        overlay.add(revealView);

        // Add the source to the containerView's overlay so that the animation can occur under the
        // status bar, the source view will be automatically positioned in the overlay so that
        // it maintains the same relative position on screen.
        overlay.add(source);

        final int centerX = Math.round((source.getLeft() + source.getRight()) / 2.0f);
        final int centerY = Math.round((source.getTop() + source.getBottom()) / 2.0f);
        final float startRadius = Math.max(source.getWidth(), source.getHeight()) / 2.0f;

        final int xMax = Math.max(centerX, mContainerView.getWidth() - centerX);
        final int yMax = Math.max(centerY, mContainerView.getHeight() - centerY);
        final float endRadius = (float) Math.sqrt(Math.pow(xMax, 2.0) + Math.pow(yMax, 2.0));

        final ValueAnimator sourceAnimator = ObjectAnimator.ofFloat(source, View.ALPHA, 0.0f);
        sourceAnimator.setDuration(ALERT_SOURCE_DURATION_MILLIS);
        sourceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                overlay.remove(source);
            }
        });

        final Animator revealAnimator = ViewAnimationUtils.createCircularReveal(
                revealView, centerX, centerY, startRadius, endRadius);
        revealAnimator.setDuration(ALERT_REVEAL_DURATION_MILLIS);
        revealAnimator.setInterpolator(REVEAL_INTERPOLATOR);
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                mContentView.setVisibility(View.GONE);
                mContainerView.setBackgroundColor(backgroundColor);
            }
        });

        final ValueAnimator fadeAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f);
        fadeAnimator.setDuration(ALERT_FADE_DURATION_MILLIS);
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                overlay.remove(revealView);
            }
        });

        final AnimatorSet alertAnimator = new AnimatorSet();
        alertAnimator.play(revealAnimator).with(sourceAnimator).before(fadeAnimator);
        alertAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                }, ALERT_DISMISS_DELAY_MILLIS);
            }
        });

        return alertAnimator;
    }

    private boolean handleAction(String action) {
        LogUtils.v(LOGTAG, "handleAction: " + action);
        switch (action) {
            case SettingsActivity.ALARM_SNOOZE:
                snooze();
                return true;
            case SettingsActivity.ALARM_DISMISS:
                dismiss();
                return true;
            case SettingsActivity.ALARM_NO_ACTION:
                break;
            default:
                break;
        }
        return false;
    }

    private void updateMediaInfo(String metaData) {
        if (!TextUtils.isEmpty(metaData)) {
            if (mTitleViewSub != null) {
                mTitleViewSub.setVisibility(View.VISIBLE);
                mTitleViewSub.setText(metaData);
            }
        }
    }
}
