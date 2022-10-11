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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.AbsoluteSizeSpan;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import org.omnirom.deskclock.alarms.AlarmConstants;
import org.omnirom.deskclock.provider.Alarm;
import org.omnirom.deskclock.provider.AlarmInstance;
import org.omnirom.deskclock.stopwatch.Stopwatches;
import org.omnirom.deskclock.timer.Timers;
import org.omnirom.deskclock.worldclock.CityObj;
import org.omnirom.deskclock.worldclock.db.DbCities;
import org.omnirom.deskclock.worldclock.db.DbCity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;


public class Utils {
    private final static String PARAM_LANGUAGE_CODE = "hl";

    /**
     * Help URL query parameter key for the app version.
     */
    private final static String PARAM_VERSION = "version";

    /**
     * Cached version code to prevent repeated calls to the package manager.
     */
    private static String sCachedVersionCode = null;

    /**
     * Types that may be used for clock displays.
     **/
    public static final String CLOCK_TYPE_DIGITAL = "digital";
    public static final String CLOCK_TYPE_ANALOG = "analog";

    /**
     * The background colors of the app, it changes thru out the day to mimic the sky.
     **/
    public static final String[] BACKGROUND_SPECTRUM = {"#212121", "#27232e", "#2d253a",
            "#332847", "#382a53", "#3e2c5f", "#442e6c", "#393a7a", "#2e4687", "#235395", "#185fa2",
            "#0d6baf", "#0277bd", "#0d6cb1", "#1861a6", "#23569b", "#2d4a8f", "#383f84", "#433478",
            "#3d3169", "#382e5b", "#322b4d", "#2c273e", "#272430"};

    public static final int[] BACKGROUND_IMAGE = {R.drawable.night, R.drawable.night, R.drawable.night,
            R.drawable.night, R.drawable.night, R.drawable.night, R.drawable.night, R.drawable.sunrise,
            R.drawable.sunrise, R.drawable.sunrise, R.drawable.sunrise, R.drawable.day, R.drawable.day,
            R.drawable.day, R.drawable.day, R.drawable.day, R.drawable.day, R.drawable.sunset, R.drawable.sunset,
            R.drawable.sunset, R.drawable.sunset, R.drawable.night, R.drawable.night, R.drawable.night};

    public static final int[] BACKGROUND_IMAGE_WEAR = {R.drawable.night_wear, R.drawable.night_wear, R.drawable.night_wear,
            R.drawable.night_wear, R.drawable.night_wear, R.drawable.night_wear, R.drawable.night_wear, R.drawable.sunrise_wear,
            R.drawable.sunrise_wear, R.drawable.sunrise_wear, R.drawable.sunrise_wear, R.drawable.day_wear, R.drawable.day_wear,
            R.drawable.day_wear, R.drawable.day_wear, R.drawable.day_wear, R.drawable.day_wear, R.drawable.sunset_wear, R.drawable.sunset_wear,
            R.drawable.sunset_wear, R.drawable.sunset_wear, R.drawable.night_wear, R.drawable.night_wear, R.drawable.night_wear};

    public static final String M3U_HEADER = "#EXTM3U";
    public static final String M3U_ENTRY = "#EXTINF:";
    private static final String STREAM_FILE_TAG = "STREAM_M3U";

    private static final int HTTP_READ_TIMEOUT = 30000;
    private static final int HTTP_CONNECTION_TIMEOUT = 30000;

    /**
     * Returns whether the SDK is Nougat or later
     */
    public static boolean isNougatOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static boolean isLollipopMR1OrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    public static boolean isMOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static void prepareHelpMenuItem(Context context, MenuItem helpMenuItem) {
        String helpUrlString = context.getResources().getString(R.string.desk_clock_help_url);
        if (TextUtils.isEmpty(helpUrlString)) {
            // The help url string is empty or null, so set the help menu item to be invisible.
            helpMenuItem.setVisible(false);
            return;
        }
        // The help url string exists, so first add in some extra query parameters.  87
        final Uri fullUri = uriWithAddedParameters(context, Uri.parse(helpUrlString));

        // Then, create an intent that will be fired when the user
        // selects this help menu item.
        Intent intent = new Intent(Intent.ACTION_VIEW, fullUri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        // Set the intent to the help menu item, show the help menu item in the overflow
        // menu, and make it visible.
        helpMenuItem.setIntent(intent);
        helpMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        helpMenuItem.setVisible(true);
    }

    /**
     * Adds two query parameters into the Uri, namely the language code and the version code
     * of the application's package as gotten via the context.
     *
     * @return the uri with added query parameters
     */
    private static Uri uriWithAddedParameters(Context context, Uri baseUri) {
        Uri.Builder builder = baseUri.buildUpon();

        // Add in the preferred language
        builder.appendQueryParameter(PARAM_LANGUAGE_CODE, Locale.getDefault().toString());

        // Add in the package version code
        if (sCachedVersionCode == null) {
            // There is no cached version code, so try to get it from the package manager.
            try {
                // cache the version code
                PackageInfo info = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), 0);
                sCachedVersionCode = Integer.toString(info.versionCode);

                // append the version code to the uri
                builder.appendQueryParameter(PARAM_VERSION, sCachedVersionCode);
            } catch (NameNotFoundException e) {
                // Cannot find the package name, so don't add in the version parameter
                // This shouldn't happen.
                LogUtils.wtf("Invalid package name for context " + e);
            }
        } else {
            builder.appendQueryParameter(PARAM_VERSION, sCachedVersionCode);
        }

        // Build the full uri and return it
        return builder.build();
    }

    public static long getTimeNow() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * Calculate the amount by which the radius of a CircleTimerView should be offset by the any
     * of the extra painted objects.
     */
    public static float calculateRadiusOffset(
            float strokeSize, float markerStrokeSize) {
        return Math.max(strokeSize, markerStrokeSize);
    }

    /**
     * Uses {@link Utils#calculateRadiusOffset(float, float)} after fetching the values
     * from the resources just as {@link CircleTimerView#init(android.content.Context)} does.
     */
    public static float calculateRadiusOffset(Resources resources) {
        if (resources != null) {
            float strokeSize = resources.getDimension(R.dimen.circletimer_circle_size);
            float markerStrokeSize = resources.getDimension(R.dimen.circletimer_marker_size);
            return calculateRadiusOffset(strokeSize, markerStrokeSize);
        } else {
            return 0f;
        }
    }

    /**
     * The pressed color used throughout the app. If this method is changed, it will not have
     * any effect on the button press states, and those must be changed separately.
     **/
    public static int getPressedColorId() {
        return R.color.primary;
    }

    /**
     * The un-pressed color used throughout the app. If this method is changed, it will not have
     * any effect on the button press states, and those must be changed separately.
     **/
    public static int getGrayColorId() {
        return R.color.clock_gray;
    }

    /**
     * Clears the persistent data of stopwatch (start time, state, laps, etc...).
     */
    public static void clearSwSharedPref(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(Stopwatches.PREF_START_TIME);
        editor.remove(Stopwatches.PREF_ACCUM_TIME);
        editor.remove(Stopwatches.PREF_STATE);
        int lapNum = prefs.getInt(Stopwatches.PREF_LAP_NUM, Stopwatches.STOPWATCH_RESET);
        for (int i = 0; i < lapNum; i++) {
            String key = Stopwatches.PREF_LAP_TIME + Integer.toString(i);
            editor.remove(key);
        }
        editor.remove(Stopwatches.PREF_LAP_NUM);
        editor.apply();
    }

    /**
     * Broadcast a message to show the in-use timers in the notifications
     */
    public static void showInUseNotifications(Context context) {
        Intent timerIntent = new Intent();
        timerIntent.setAction(Timers.NOTIF_IN_USE_SHOW);
        context.sendBroadcast(timerIntent);
    }

    /**
     * Broadcast a message to show the in-use timers in the notifications
     */
    public static void showTimesUpNotifications(Context context) {
        Intent timerIntent = new Intent();
        timerIntent.setAction(Timers.NOTIF_TIMES_UP_SHOW);
        context.sendBroadcast(timerIntent);
    }

    /**
     * Runnable for use with screensaver and dream, to move the clock every minute.
     * registerViews() must be called prior to posting.
     */
    public static class ScreensaverMoveSaverRunnable implements Runnable {
        static final long MOVE_DELAY = 60000; // DeskClock.SCREEN_SAVER_MOVE_DELAY;
        static final long SLIDE_TIME = 10000;
        static final long FADE_TIME = 3000;

        static final boolean SLIDE = false;

        private View mContentView, mSaverView;
        private final Handler mHandler;

        private static TimeInterpolator mSlowStartWithBrakes;


        public ScreensaverMoveSaverRunnable(Handler handler) {
            mHandler = handler;
            mSlowStartWithBrakes = new TimeInterpolator() {
                @Override
                public float getInterpolation(float x) {
                    return (float) (Math.cos((Math.pow(x, 3) + 1) * Math.PI) / 2.0f) + 0.5f;
                }
            };
        }

        public void registerViews(View contentView, View saverView) {
            mContentView = contentView;
            mSaverView = saverView;
        }

        @Override
        public void run() {
            long delay = MOVE_DELAY;
            if (mContentView == null || mSaverView == null) {
                mHandler.removeCallbacks(this);
                mHandler.postDelayed(this, delay);
                return;
            }

            final float xrange = mContentView.getWidth() - mSaverView.getWidth();
            final float yrange = mContentView.getHeight() - mSaverView.getHeight();

            if (xrange == 0 && yrange == 0) {
                delay = 500; // back in a split second
            } else {
                final int nextx = (int) (Math.random() * xrange);
                final int nexty = (int) (Math.random() * yrange);

                if (mSaverView.getAlpha() == 0f) {
                    // jump right there
                    mSaverView.setX(nextx);
                    mSaverView.setY(nexty);
                    ObjectAnimator.ofFloat(mSaverView, "alpha", 0f, 1f)
                            .setDuration(FADE_TIME)
                            .start();
                } else {
                    AnimatorSet s = new AnimatorSet();
                    Animator xMove = ObjectAnimator.ofFloat(mSaverView,
                            "x", mSaverView.getX(), nextx);
                    Animator yMove = ObjectAnimator.ofFloat(mSaverView,
                            "y", mSaverView.getY(), nexty);

                    Animator xShrink = ObjectAnimator.ofFloat(mSaverView, "scaleX", 1f, 0.85f);
                    Animator xGrow = ObjectAnimator.ofFloat(mSaverView, "scaleX", 0.85f, 1f);

                    Animator yShrink = ObjectAnimator.ofFloat(mSaverView, "scaleY", 1f, 0.85f);
                    Animator yGrow = ObjectAnimator.ofFloat(mSaverView, "scaleY", 0.85f, 1f);
                    AnimatorSet shrink = new AnimatorSet();
                    shrink.play(xShrink).with(yShrink);
                    AnimatorSet grow = new AnimatorSet();
                    grow.play(xGrow).with(yGrow);

                    Animator fadeout = ObjectAnimator.ofFloat(mSaverView, "alpha", 1f, 0f);
                    Animator fadein = ObjectAnimator.ofFloat(mSaverView, "alpha", 0f, 1f);


                    if (SLIDE) {
                        s.play(xMove).with(yMove);
                        s.setDuration(SLIDE_TIME);

                        s.play(shrink.setDuration(SLIDE_TIME / 2));
                        s.play(grow.setDuration(SLIDE_TIME / 2)).after(shrink);
                        s.setInterpolator(mSlowStartWithBrakes);
                    } else {
                        AccelerateInterpolator accel = new AccelerateInterpolator();
                        DecelerateInterpolator decel = new DecelerateInterpolator();

                        shrink.setDuration(FADE_TIME).setInterpolator(accel);
                        fadeout.setDuration(FADE_TIME).setInterpolator(accel);
                        grow.setDuration(FADE_TIME).setInterpolator(decel);
                        fadein.setDuration(FADE_TIME).setInterpolator(decel);
                        s.play(shrink);
                        s.play(fadeout);
                        s.play(xMove.setDuration(0)).after(FADE_TIME);
                        s.play(yMove.setDuration(0)).after(FADE_TIME);
                        s.play(fadein).after(FADE_TIME);
                        s.play(grow).after(FADE_TIME);
                    }
                    s.start();
                }

                long now = System.currentTimeMillis();
                long adjust = (now % 60000);
                delay = delay
                        + (MOVE_DELAY - adjust) // minute aligned
                        - (SLIDE ? 0 : FADE_TIME) // start moving before the fade
                ;
            }

            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, delay);
        }
    }

    /**
     * Setup to find out when the quarter-hour changes (e.g. Kathmandu is GMT+5:45)
     **/
    public static long getAlarmOnQuarterHour() {
        Calendar nextQuarter = Calendar.getInstance();
        //  Set 1 second to ensure quarter-hour threshold passed.
        nextQuarter.set(Calendar.SECOND, 1);
        nextQuarter.set(Calendar.MILLISECOND, 0);
        int minute = nextQuarter.get(Calendar.MINUTE);
        nextQuarter.add(Calendar.MINUTE, 15 - (minute % 15));
        long alarmOnQuarterHour = nextQuarter.getTimeInMillis();
        long now = System.currentTimeMillis();
        long delta = alarmOnQuarterHour - now;
        if (0 >= delta || delta > 901000) {
            // Something went wrong in the calculation, schedule something that is
            // about 15 minutes. Next time , it will align with the 15 minutes border.
            alarmOnQuarterHour = now + 901000;
        }
        return alarmOnQuarterHour;
    }

    // Setup a thread that starts at midnight plus one second. The extra second is added to ensure
    // the date has changed.
    public static void setMidnightUpdater(Handler handler, Runnable runnable) {
        String timezone = TimeZone.getDefault().getID();
        if (handler == null || runnable == null || timezone == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Time time = new Time(timezone);
        time.set(now);
        long runInMillis = ((24 - time.hour) * 3600 - time.minute * 60 - time.second + 1) * 1000;
        handler.removeCallbacks(runnable);
        handler.postDelayed(runnable, runInMillis);
    }

    // Stop the midnight update thread
    public static void cancelMidnightUpdater(Handler handler, Runnable runnable) {
        if (handler == null || runnable == null) {
            return;
        }
        handler.removeCallbacks(runnable);
    }

    // Setup a thread that starts at the quarter-hour plus one second. The extra second is added to
    // ensure dates have changed.
    public static void setQuarterHourUpdater(Handler handler, Runnable runnable) {
        String timezone = TimeZone.getDefault().getID();
        if (handler == null || runnable == null || timezone == null) {
            return;
        }
        long runInMillis = getAlarmOnQuarterHour() - System.currentTimeMillis();
        // Ensure the delay is at least one second.
        if (runInMillis < 1000) {
            runInMillis = 1000;
        }
        handler.removeCallbacks(runnable);
        handler.postDelayed(runnable, runInMillis);
    }

    // Stop the quarter-hour update thread
    public static void cancelQuarterHourUpdater(Handler handler, Runnable runnable) {
        if (handler == null || runnable == null) {
            return;
        }
        handler.removeCallbacks(runnable);
    }

    /**
     * For screensavers to set whether the digital or analog clock should be displayed.
     * Returns the view to be displayed.
     */
    public static View setClockStyle(Context context, View digitalClock, View analogClock) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String defaultClockStyle = context.getResources().getString(R.string.default_clock_style);
        String style = sharedPref.getString(SettingsActivity.KEY_CLOCK_STYLE, defaultClockStyle);
        View returnView;
        if (style.equals(CLOCK_TYPE_ANALOG)) {
            digitalClock.setVisibility(View.GONE);
            analogClock.setVisibility(View.VISIBLE);
            returnView = analogClock;
        } else {
            digitalClock.setVisibility(View.VISIBLE);
            analogClock.setVisibility(View.GONE);
            returnView = digitalClock;
        }

        return returnView;
    }

    public static boolean isClockStyleAnalog(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String defaultClockStyle = context.getResources().getString(R.string.default_clock_style);
        String style = sharedPref.getString(SettingsActivity.KEY_CLOCK_STYLE, defaultClockStyle);
        if (style.equals(CLOCK_TYPE_ANALOG)) {
            return true;
        }

        return false;
    }

    /**
     * For screensavers to dim the lights if necessary.
     */
    public static void dimClockView(boolean dim, View clockView) {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setColorFilter(new PorterDuffColorFilter(
                (dim ? 0x40FFFFFF : 0xC0FFFFFF),
                PorterDuff.Mode.MULTIPLY));
        clockView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
    }

    /**
     * @return The next alarm from {@link AlarmManager}
     */
    public static String getNextAlarm(Context context) {
        String timeString = null;
        final AlarmManager.AlarmClockInfo info = ((AlarmManager) context.getSystemService(
                Context.ALARM_SERVICE)).getNextAlarmClock();
        if (info != null) {
            final long triggerTime = info.getTriggerTime();
            final Calendar alarmTime = Calendar.getInstance();
            alarmTime.setTimeInMillis(triggerTime);
            timeString = AlarmUtils.getFormattedTime(context, alarmTime);
        }
        return timeString;
    }

    /**
     * @return The next alarm from {@link AlarmManager}
     */
    public static long getNextAlarmInMillis(Context context) {
        final AlarmManager.AlarmClockInfo info = ((AlarmManager) context.getSystemService(
                Context.ALARM_SERVICE)).getNextAlarmClock();
        if (info != null) {
            return info.getTriggerTime();
        }
        return -1;
    }

    /**
     * Clock views can call this to refresh their alarm to the next upcoming value.
     **/
    public static void refreshAlarm(Context context, View clock) {
        final String nextAlarm = getNextAlarm(context);
        TextView nextAlarmView;
        nextAlarmView = (TextView) clock.findViewById(R.id.nextAlarm);
        if (nextAlarmView != null) {
            if (!TextUtils.isEmpty(nextAlarm)) {
                nextAlarmView.setText(
                        context.getString(R.string.control_set_alarm_with_existing, nextAlarm));
                nextAlarmView.setContentDescription(context.getResources().getString(
                        R.string.next_alarm_description, nextAlarm));
                nextAlarmView.setVisibility(View.VISIBLE);
            } else {
                nextAlarmView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Clock views can call this to refresh their date.
     **/
    public static void updateDate(String dateFormat, String dateFormatForAccessibility, View clock) {

        Date now = new Date();
        TextView dateDisplay;
        dateDisplay = (TextView) clock.findViewById(R.id.date);
        if (dateDisplay != null) {
            final Locale l = Locale.getDefault();
            String fmt = DateFormat.getBestDateTimePattern(l, dateFormat);
            SimpleDateFormat sdf = new SimpleDateFormat(fmt, l);
            dateDisplay.setText(sdf.format(now));
            dateDisplay.setVisibility(View.VISIBLE);
            fmt = DateFormat.getBestDateTimePattern(l, dateFormatForAccessibility);
            sdf = new SimpleDateFormat(fmt, l);
            dateDisplay.setContentDescription(sdf.format(now));
        }
    }

    /***
     * Formats the time in the TextClock according to the Locale with a special
     * formatting treatment for the am/pm label.
     *
     * @param clock        - TextClock to format
     * @param amPmFontSize - size of the am/pm label since it is usually smaller
     *                     than the clock time size.
     */
    public static void setTimeFormat(TextClock clock, int amPmFontSize, int secondsSize) {
        if (clock != null) {
            // Get the best format for 12 hours mode according to the locale
            clock.setFormat12Hour(get12ModeFormat(amPmFontSize, secondsSize));
            // Get the best format for 24 hours mode according to the locale
            clock.setFormat24Hour(get24ModeFormat(secondsSize));
        }
    }

    /***
     * @param amPmFontSize - size of am/pm label (label removed is size is 0).
     * @return format string for 12 hours mode time
     */
    public static CharSequence get12ModeFormat(int amPmFontSize, int secondsSize) {
        String skeleton = secondsSize != 0 ? "hmsa" : "hma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        // Remove the am/pm
        if (amPmFontSize <= 0) {
            pattern = pattern.replaceAll("a", "").trim();
        }
        // Replace spaces with "Hair Space"
        pattern = pattern.replaceAll(" ", "\u200A");

        // Build a spannable so that the seconds and am/pm will be formatted
        Spannable sp = new SpannableString(pattern);
        int amPmPos = pattern.indexOf('a');
        int secPos = pattern.indexOf('s');
        if (secPos == -1 && amPmPos == -1) {
            return pattern;
        }
        if (secPos != -1) {
            sp.setSpan(new AbsoluteSizeSpan(secondsSize), secPos - 1, secPos + 2,
                    Spannable.SPAN_POINT_MARK);
        }
        if (amPmPos != -1) {
            sp.setSpan(new AbsoluteSizeSpan(amPmFontSize), amPmPos, amPmPos + 1,
                    Spannable.SPAN_POINT_MARK);
        }
        return sp;
    }

    public static CharSequence getRaw12ModeFormat(boolean withSeconds) {
        String skeleton = withSeconds ? "hmsa" : "hma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return pattern;
    }

    public static CharSequence get24ModeFormat(int secondsSize) {
        String skeleton = secondsSize != 0 ? "Hms" : "Hm";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        // Replace spaces with "Hair Space"
        pattern = pattern.replaceAll(" ", "\u200A");

        // Build a spannable so that the seconds will be formatted
        int secPos = pattern.indexOf('s');
        if (secPos == -1) {
            return pattern;
        }
        Spannable sp = new SpannableString(pattern);
        sp.setSpan(new AbsoluteSizeSpan(secondsSize), secPos - 1, secPos + 2,
                Spannable.SPAN_POINT_MARK);
        return sp;
    }

    public static CharSequence getRaw24ModeFormat(boolean withSeconds) {
        String skeleton = withSeconds ? "Hms" : "Hm";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return pattern;
    }

    public static CityObj[] loadCitiesFromXml(Context c) {
        final Collator collator = Collator.getInstance();
        Resources r = c.getResources();

        // Get list of cities defined by the app (App-defined has the prefix C)
        // Read strings array of name,timezone, id
        // make sure the list are the same length
        String[] cityNames = r.getStringArray(R.array.cities_names);
        String[] timezones = r.getStringArray(R.array.cities_tz);
        String[] ids = r.getStringArray(R.array.cities_id);

        int minLength = cityNames.length;
        if (cityNames.length != timezones.length || ids.length != cityNames.length) {
            minLength = Math.min(cityNames.length, Math.min(timezones.length, ids.length));
            LogUtils.e("City lists sizes are not the same, fallback to default");
            Configuration conf = c.getResources().getConfiguration();
            Locale currentLocale = conf.locale;
            conf.locale = Locale.US;
            c.getResources().updateConfiguration(conf, null);
            cityNames = r.getStringArray(R.array.cities_names);
            minLength = cityNames.length;
            conf.locale = currentLocale;
            c.getResources().updateConfiguration(conf, null);
        }
        // Get the list of user-defined cities (User-defined has the prefix UD)
        final List<DbCity> dbcities = DbCities.getCities(c.getContentResolver());
        final CityObj[] cities = new CityObj[minLength + dbcities.size()];
        int i = 0;
        for (; i < minLength; i++) {
            // Default to using the first character of the city name as the index unless one is
            // specified. The indicator for a specified index is the addition of character(s)
            // before the "=" separator.
            final String parseString = cityNames[i];
            final int separatorIndex = parseString.indexOf("=");
            final String index;
            final String cityName;
            if (separatorIndex == 0) {
                // Default to using second character (the first character after the = separator)
                // as the index.
                index = parseString.substring(1, 2);
                cityName = parseString.substring(1, parseString.length());
            } else {
                index = parseString.substring(0, separatorIndex);
                cityName = parseString.substring(separatorIndex + 1, parseString.length());
            }
            cities[i] = new CityObj(cityName, timezones[i], ids[i], index);
        }

        for (int j = 0; j < dbcities.size(); j++) {
            DbCity dbCity = dbcities.get(j);
            CityObj city = new CityObj(dbCity.name, dbCity.tz, "UD" + dbCity.id, dbCity.name.substring(0, 1));
            city.mUserDefined = true;
            cities[i] = city;
            i++;
        }
        return cities;
    }

    /**
     * Returns string denoting the timezone hour offset (e.g. GMT-8:00)
     */
    public static String getGMTHourOffset(TimeZone timezone, boolean showMinutes, boolean shortTZ) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortTZ ? "GMT " : "GMT  ");
        int gmtOffset = timezone.getRawOffset();
        if (gmtOffset < 0) {
            sb.append('-');
        } else {
            sb.append('+');
        }
        sb.append(Math.abs(gmtOffset) / DateUtils.HOUR_IN_MILLIS); // Hour

        if (showMinutes) {
            final int min = (Math.abs(gmtOffset) / (int) DateUtils.MINUTE_IN_MILLIS) % 60;
            sb.append(':');
            if (min < 10) {
                sb.append('0');
            }
            sb.append(min);
        }

        return sb.toString();
    }

    public static String getCityName(CityObj city, CityObj dbCity) {
        return (city.mCityId == null || dbCity == null) ? city.mCityName : dbCity.mCityName;
    }

    public static int getCurrentHourColor() {
        final int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return Color.parseColor(BACKGROUND_SPECTRUM[hourOfDay]);
    }

    public static boolean isValidAudioFile(String baseName) {
        // check if audio
        int idx = baseName.lastIndexOf(".");
        if (idx != -1) {
            String ext = baseName.substring(idx + 1);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null && (mime.contains("audio") || mime.contains("ogg"))) {
                return true;
            }
        }
        return false;
    }

    public static void startCalendarWithDate(final Context context, final Date date) {
        context.startActivity(getCalendarIntent(date));
    }

    public static Intent getCalendarIntent(final Date date) {
        if (date != null) {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            builder.appendPath(Long.toString(date.getTime()));
            Intent intent = new Intent(Intent.ACTION_VIEW, builder.build());
            return intent;
        } else {
            Intent calIntent = new Intent(Intent.ACTION_MAIN);
            calIntent.addCategory(Intent.CATEGORY_APP_CALENDAR);
            return calIntent;
        }
    }

    public static void openAlarmsTab(final Context context) {
        context.startActivity(getAlarmTabIntent(context));
    }

    public static Intent getAlarmTabIntent(final Context context) {
        return new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
    }

    public static boolean showWearNotification(final Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPref.getBoolean(SettingsActivity.KEY_WEAR_NOTIFICATIONS, true);
    }

    public static boolean isNotificationVibrate(final Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPref.getBoolean(SettingsActivity.KEY_VIBRATE_NOTIFICATION, true);
    }

    private static boolean isNewSpotifyPluginInstalled(final Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo("com.maxwen.deskclock.spotify2", 0);
            return info != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean isOldSpotifyPluginInstalled(final Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo("com.maxwen.deskclock.spotify", 0);
            if (info != null) {
                // we need at least version 1.3.0 = 19
                if (info.versionCode >= 19) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
        return false;
    }

    public static boolean isSpotifyPluginInstalled(final Context context) {
        if (!isNewSpotifyPluginInstalled(context)) {
            return isOldSpotifyPluginInstalled(context);
        }
        return true;
    }

    public static boolean isSpotifyAlarm(AlarmInstance instance, boolean preAlarm) {
        if (preAlarm) {
            return isSpotifyUri(instance.mPreAlarmRingtone.toString());
        }
        return isSpotifyUri(instance.mRingtone.toString());
    }

    public static boolean isSpotifyAlarm(Alarm alarm, boolean preAlarm) {
        if (preAlarm) {
            return isSpotifyUri(alarm.preAlarmAlert.toString());
        }
        return isSpotifyUri(alarm.alert.toString());
    }

    public static boolean isSpotifyUri(String uri) {
        return uri.startsWith("spotify:");
    }

    public static Intent getSpotifyTestPlayIntent(final Context context, Alarm alarm) {
        Intent spotifyIntent = new Intent();
        ComponentName cn = getSpotifyComponentName(context, "TestPlayActivity");
        spotifyIntent.setComponent(cn);
        spotifyIntent.putExtra(AlarmConstants.DATA_COLOR_THEME_LIGHT, isLightTheme(context));
        spotifyIntent.putExtra(AlarmConstants.DATA_ALARM_EXTRA_URI, alarm.alert.toString());
        spotifyIntent.putExtra(AlarmConstants.DATA_ALARM_EXTRA_NAME, alarm.getRingtoneName());
        spotifyIntent.putExtra(AlarmConstants.DATA_ALARM_EXTRA_RANDOM, alarm.getRandomMode(false));
        spotifyIntent.putExtra(AlarmConstants.DATA_ALARM_EXTRA_VOLUME, alarm.alarmVolume);
        spotifyIntent.putExtra(AlarmConstants.DATA_COLOR_THEME_ID, getThemeId(context));
        return spotifyIntent;
    }

    public static Intent getSpotifyFirstStartIntent(final Context context) {
        Intent spotifyIntent = new Intent();
        ComponentName cn = getSpotifyComponentName(context, "FirstStartActivity");
        spotifyIntent.setComponent(cn);
        spotifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return spotifyIntent;
    }

    public static int getHighNotificationOffset(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String offset = prefs.getString(SettingsActivity.KEY_PRE_ALARM_NOTIFICATION_TIME, "-30");
        return Integer.decode(offset).intValue();
    }

    public static Intent getSpotifyBrowseIntent(final Context context, Alarm alarm) {
        Intent spotifyIntent = new Intent();
        ComponentName cn = getSpotifyComponentName(context, "BrowseActivity");
        spotifyIntent.setComponent(cn);
        spotifyIntent.putExtra(AlarmConstants.DATA_ALARM_EXTRA_URI, alarm.alert.toString());
        spotifyIntent.putExtra(AlarmConstants.DATA_COLOR_THEME_LIGHT, isLightTheme(context));
        spotifyIntent.putExtra(AlarmConstants.DATA_COLOR_THEME_ID, getThemeId(context));        return spotifyIntent;
    }

    public static Intent getSpotifySettingsIntent(final Context context) {
        Intent spotifyIntent = new Intent();
        ComponentName cn = getSpotifyComponentName(context, "SpotifyActivity");
        spotifyIntent.setComponent(cn);
        spotifyIntent.putExtra(AlarmConstants.DATA_COLOR_THEME_LIGHT, isLightTheme(context));
        spotifyIntent.putExtra(AlarmConstants.DATA_OMNICLOCK_PARENT, true);
        spotifyIntent.putExtra(AlarmConstants.DATA_COLOR_THEME_ID, getThemeId(context));
        return spotifyIntent;
    }

    public static Intent getLocalBrowseIntent(final Context context, Alarm alarm) {
        Intent browseIntent = new Intent(context, BrowseActivity.class);
        browseIntent.putExtra(AlarmConstants.DATA_ALARM_EXTRA, alarm);
        browseIntent.putExtra(AlarmConstants.DATA_COLOR_THEME_LIGHT, isLightTheme(context));
        browseIntent.putExtra(AlarmConstants.DATA_COLOR_THEME_ID, getThemeId(context));        return browseIntent;
    }

    public static boolean isLightTheme(final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String color = prefs.getString(SettingsActivity.KEY_COLOR_THEME, "0");
        return color.equals("0");
    }

    public static int getThemeId(final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String color = prefs.getString(SettingsActivity.KEY_COLOR_THEME, "0");
        return Integer.valueOf(color);
    }

    public static int getThemeResourceId(final Context context) {
        switch (getThemeId(context)) {
            case 0:
                return R.style.DeskClock;
            case 1:
                return R.style.DeskClockDark;
            case 2:
                return R.style.DeskClockBlack;
        }
        return R.style.DeskClock;
    }

    public static int getThemeResourceId(final Context context, int themeId) {
        switch (themeId) {
            case 0:
                return R.style.DeskClock;
            case 1:
                return R.style.DeskClockDark;
            case 2:
                return R.style.DeskClockBlack;
        }
        return R.style.DeskClock;
    }

    public static int getDialogThemeResourceId(final Context context) {
        switch (getThemeId(context)) {
            case 0:
                return R.style.DialogTheme;
            case 1:
                return R.style.DialogThemeDark;
            case 2:
                return R.style.DialogThemeBlack;
        }
        return R.style.DialogTheme;
    }

    public static int getViewBackgroundColor(final Context context) {
        switch (getThemeId(context)) {
            case 0:
                return context.getResources().getColor(R.color.view_background);
            case 1:
                return context.getResources().getColor(R.color.view_background_dark);
            case 2:
                return context.getResources().getColor(R.color.view_background_black);
        }
        return context.getResources().getColor(R.color.view_background);
    }

    public static int getViewBackgroundColor(final Context context, int themeId) {
        switch (themeId) {
            case 0:
                return context.getResources().getColor(R.color.view_background);
            case 1:
                return context.getResources().getColor(R.color.view_background_dark);
            case 2:
                return context.getResources().getColor(R.color.view_background_black);
        }
        return context.getResources().getColor(R.color.view_background);
    }

    public static int getCircleViewBackgroundResourceId(final Context context) {
        switch (getThemeId(context)) {
            case 0:
                return R.drawable.bg_circle_view;
            case 1:
                return R.drawable.bg_circle_view_dark;
            case 2:
                return R.drawable.bg_circle_view_black;
        }
        return R.drawable.bg_circle_view;
    }

    public static List<Uri> getRandomMusicFiles(Context context, int size) {
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID
        };

        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "RANDOM() LIMIT " + size);

        List<Uri> mediaFiles = new ArrayList<>(size);
        while (c.moveToNext()) {
            Uri mediaFile = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    String.valueOf(c.getLong(0)));
            mediaFiles.add(mediaFile);
        }
        c.close();
        return mediaFiles;
    }

    public static List<Uri> getAlbumSongs(Context context, Uri album) {
        String albumId = album.getLastPathSegment();
        String selection = MediaStore.Audio.Media.ALBUM_ID + " = " + Integer.valueOf(albumId).intValue();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE
        };

        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.TITLE);

        List<Uri> albumFiles = new ArrayList<>();
        while (c.moveToNext()) {
            Uri mediaFile = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    String.valueOf(c.getLong(0)));
            albumFiles.add(mediaFile);
        }
        c.close();
        return albumFiles;
    }

    public static boolean checkAlbumExists(Context context, Uri album) {
        String albumId = album.getLastPathSegment();
        String selection = MediaStore.Audio.Media.ALBUM_ID + " = " + Integer.valueOf(albumId).intValue();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE
        };

        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.TITLE);

        return c.getCount() != 0;
    }

    public static List<Uri> getArtistSongs(Context context, Uri artist) {
        String artistId = artist.getLastPathSegment();
        String selection = MediaStore.Audio.Media.ARTIST_ID + " = " + Integer.valueOf(artistId).intValue();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE
        };

        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.TITLE);

        List<Uri> albumFiles = new ArrayList<>();
        while (c.moveToNext()) {
            Uri mediaFile = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    String.valueOf(c.getLong(0)));
            albumFiles.add(mediaFile);
        }
        c.close();
        return albumFiles;
    }

    public static boolean checkArtistExists(Context context, Uri artist) {
        String artistId = artist.getLastPathSegment();
        String selection = MediaStore.Audio.Media.ARTIST_ID + " = " + Integer.valueOf(artistId).intValue();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE
        };

        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.TITLE);
        return c.getCount() != 0;
    }

    public static List<Uri> getPlaylistSongs(Context context, Uri playlist) {
        String playlistId = playlist.getLastPathSegment();
        String selection = MediaStore.Audio.Playlists._ID + " = " + Integer.valueOf(playlistId).intValue();

        String[] projection = {
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME
        };
        String[] projectionMembers = {
                MediaStore.Audio.Playlists.Members.AUDIO_ID,
                MediaStore.Audio.Playlists.Members.ARTIST,
                MediaStore.Audio.Playlists.Members.TITLE,
                MediaStore.Audio.Playlists.Members._ID
        };
        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null);

        List<Uri> playlistFiles = new ArrayList<>();

        while (c.moveToNext()) {
            long id = c.getLong(0);
            Cursor c1 = context.getContentResolver().query(
                    MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                    projectionMembers,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0 ",
                    null,
                    null);
            while (c1.moveToNext()) {
                Uri mediaFile = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(c1.getLong(0)));
                playlistFiles.add(mediaFile);
            }
            c1.close();
        }
        c.close();
        return playlistFiles;
    }

    public static boolean checkPlaylistExists(Context context, Uri playlist) {
        String playlistId = playlist.getLastPathSegment();
        String selection = MediaStore.Audio.Playlists._ID + " = " + Integer.valueOf(playlistId).intValue();

        String[] projection = {
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME
        };

        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                MediaStore.Audio.Playlists.NAME);
        return c.getCount() != 0;
    }

    public static String resolveTrack(Context context, Uri track) {
        String trackId = track.getLastPathSegment();
        String selection = MediaStore.Audio.Media._ID + " = " + Integer.valueOf(trackId).intValue();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE
        };

        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null);

        String title = null;
        while (c.moveToNext()) {
            title = c.getString(1);
            break;
        }
        c.close();
        return title;
    }

    public static String getRandomUriString() {
        return "random:/";
    }

    public static boolean isRandomUri(String uri) {
        return uri.startsWith(getRandomUriString());
    }

    public static boolean isRandomAlarm(AlarmInstance instance, boolean preAlarm) {
        if (preAlarm) {
            return isRandomUri(instance.mPreAlarmRingtone.toString());
        }
        return isRandomUri(instance.mRingtone.toString());
    }

    public static boolean isRandomAlarm(Alarm alarm, boolean preAlarm) {
        if (preAlarm) {
            return isRandomUri(alarm.preAlarmAlert.toString());
        }
        return isRandomUri(alarm.alert.toString());
    }

    public static int resolveSpotifyUriImage(final String uri) {
        if (uri.contains(":playlist:")) {
            return R.drawable.ic_playlist;
        } else if (uri.contains(":track:")) {
            return R.drawable.ic_track;
        } else if (uri.contains(":album:")) {
            return R.drawable.ic_album;
        } else if (uri.contains(":artist:")) {
            return R.drawable.ic_artist;
        }
        return R.drawable.ic_alarm;
    }

    public static int resolveLocalUriImage(final String uri) {
        if (isLocalAlbumUri(uri)) {
            return R.drawable.ic_album;
        } else if (isLocalArtistUri(uri)) {
            return R.drawable.ic_artist;
        } else if (isLocalTrackUri(uri)) {
            return R.drawable.ic_track;
        } else if (isStreamM3UFile(uri)) {
            return R.drawable.ic_earth;
        } else if (isStorageUri(uri)) {
            if (isStorageFileUri(uri)) {
                return R.drawable.ic_playlist;
            } else {
                return R.drawable.ic_folder;
            }
        } else if (isLocalPlaylistUri(uri)) {
            return R.drawable.ic_playlist;
        }
        return R.drawable.ic_alarm;
    }

    public static boolean isLocalAlbumUri(String uri) {
        return uri.contains("/audio/albums/");
    }

    public static boolean isLocalArtistUri(String uri) {
        return uri.contains("/audio/artists/");
    }

    public static boolean isLocalTrackUri(String uri) {
        return uri.contains("/audio/media/");
    }

    public static boolean isLocalPlaylistUri(String uri) {
        return uri.contains("/audio/playlists/");
    }

    private static boolean isLocalMediaUri(String uri) {
        return isLocalAlbumUri(uri) || isLocalArtistUri(uri) || isLocalTrackUri(uri)
                || isStorageUri(uri) || isLocalPlaylistUri(uri);
    }

    public static boolean isLocalMediaAlarm(Alarm alarm, boolean preAlarm) {
        if (preAlarm) {
            return isLocalMediaUri(alarm.preAlarmAlert.toString());
        }
        return isLocalMediaUri(alarm.alert.toString());
    }

    public static boolean isLocalFolderAlarm(Alarm alarm, boolean preAlarm) {
        if (preAlarm) {
            return isStorageUri(alarm.preAlarmAlert.toString()) && !isStreamM3UFile(alarm.preAlarmAlert.toString());
        }
        return isStorageUri(alarm.alert.toString()) && !isStreamM3UFile(alarm.alert.toString());
    }

    public static boolean isLocalPlaylistType(final String uri) {
        if (isLocalAlbumUri(uri)) {
            return true;
        } else if (isLocalArtistUri(uri)) {
            return true;
        } else if (isStorageUri(uri)) {
            return true;
        } else if (isLocalPlaylistUri(uri)) {
            return true;
        }
        return false;
    }

    public static boolean isStorageUri(String uri) {
        return uri.startsWith("file:/");
    }

    public static boolean isStorageFileUri(String uri) {
        if (isStorageUri(uri)) {
            String path = Uri.parse(uri).getPath();
            File f = new File(path);
            if (f.isFile() && f.exists()) {
                return true;
            }
        }
        return false;
    }

    public static Uri getDefaultAlarmUri(Context context) {
        Uri alert = RingtoneManager.getActualDefaultRingtoneUri(context,
                RingtoneManager.TYPE_ALARM);
        return alert;
    }

    public static boolean showBackgroundImage(final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(SettingsActivity.KEY_SHOW_BACKGROUND_IMAGE, true);
    }

    public static int getCurrentHourImage() {
        final int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return BACKGROUND_IMAGE[hourOfDay];
    }

    public static Bitmap getCurrentHourWearImage(Context context) {
        final int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int resourceId = BACKGROUND_IMAGE_WEAR[hourOfDay];
        Drawable d = context.getResources().getDrawable(resourceId);
        if (d instanceof BitmapDrawable) {
            return ((BitmapDrawable) d).getBitmap();
        }
        return null;
    }

    public static boolean isAlarmUriValid(Context context, Uri uri) {
        final RingtoneManager rm = new RingtoneManager(context);
        rm.setType(RingtoneManager.TYPE_ALL);
        return rm.getRingtonePosition(uri) != -1;
    }


    public static String getMediaTitle(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver()
                    .query(uri, null, null, null, null);
            int nameIndex = cursor
                    .getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
            cursor.moveToFirst();
            return cursor.getString(nameIndex);
        } catch (Exception e) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static boolean isValidAlarm(Context context, Uri ringtoneUri, int type) {
        if (type == BrowseActivity.QUERY_TYPE_ALARM || type == BrowseActivity.QUERY_TYPE_RINGTONE) {
            if (!Utils.isAlarmUriValid(context, ringtoneUri)) {
                return false;
            }
        } else if (type == BrowseActivity.QUERY_TYPE_ALBUM) {
            if (!Utils.checkAlbumExists(context, ringtoneUri)) {
                return false;
            }
        } else if (type == BrowseActivity.QUERY_TYPE_ARTIST) {
            if (!Utils.checkArtistExists(context, ringtoneUri)) {
                return false;
            }
        } else if (type == BrowseActivity.QUERY_TYPE_TRACK) {
            String title = getMediaTitle(context, ringtoneUri);
            if (title == null) {
                return false;
            }
        } else if (type == BrowseActivity.QUERY_TYPE_PLAYLIST) {
            if (!Utils.checkPlaylistExists(context, ringtoneUri)) {
                return false;
            }
        } else if (type == BrowseActivity.QUERY_TYPE_STREAM) {
            if (!Utils.isStreamM3UFile(ringtoneUri.toString())) {
                return false;
            }
        }
        return true;
    }

    public static String getSpotifyPluginPackageName(Context context) {
        String packageName = isNewSpotifyPluginInstalled(context) ?
                "com.maxwen.deskclock.spotify2" :
                (isOldSpotifyPluginInstalled(context) ?
                        "com.maxwen.deskclock.spotify" : null);
        return packageName;
    }

    public static ComponentName getSpotifyComponentName(Context context, String activity) {
        String packageName = getSpotifyPluginPackageName(context);
        if (packageName == null) {
            return null;
        }
        return new ComponentName(packageName, packageName + "." + activity);
    }

    public static int getDefaultPage(final Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.valueOf(sharedPref.getString(SettingsActivity.KEY_DEFAULT_PAGE,
                String.valueOf(DeskClock.CLOCK_TAB_INDEX)));
    }

    public static File getStreamM3UDirectory(Context context) {
        return new File(context.getExternalFilesDir(null), "streams");
    }

    public static List<Uri> parseM3UPlaylist(String uri) {
        List<Uri> files = new ArrayList<>();

        if (isStorageUri(uri)) {
            String path = Uri.parse(uri).getPath();
            File f = new File(path);

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                reader.mark(1024);
                String line = reader.readLine();
                if (line != null) {
                    boolean extM3U = false;
                    if (line.equalsIgnoreCase(M3U_HEADER)) {
                        extM3U = true;
                    } else {
                        reader.reset();
                    }
                    while ((line = reader.readLine()) != null) {
                        if (TextUtils.isEmpty(line)) {
                            continue;
                        }
                        String entryUriString = line;
                        if (extM3U && line.startsWith(M3U_ENTRY)) {
                            entryUriString = reader.readLine();
                        }
                        Uri entryUri = Uri.parse(entryUriString);
                        if (entryUri.getScheme() == null) {
                            File file = new File(entryUriString);
                            if (!file.isAbsolute()) {
                                file = new File(f.getParent(), file.getPath());
                            }
                            entryUri = Uri.fromFile(file);
                        }
                        files.add(entryUri);

                    }
                }
            } catch (Exception e) {
            }
        }
        return files;
    }

    public static List<String> parseM3UPlaylistFromMemory(String m3uContent) {
        List<String> files = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new StringReader(m3uContent));
            reader.mark(1024);
            String line = reader.readLine();
            if (line != null) {
                boolean extM3U = false;
                if (line.equalsIgnoreCase(M3U_HEADER)) {
                    extM3U = true;
                } else {
                    reader.reset();
                }
                while ((line = reader.readLine()) != null) {
                    if (TextUtils.isEmpty(line)) {
                        continue;
                    }
                    String entryUriString = line;
                    if (extM3U && line.startsWith(M3U_ENTRY)) {
                        entryUriString = reader.readLine();
                    }
                    files.add(entryUriString);
                }
            }
        } catch (Exception e) {
        }
        return files;
    }

    public static List<String> parsePLSPlaylistFromMemory(String plsContent) {
        List<String> files = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new StringReader(plsContent));
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (TextUtils.isEmpty(line)) {
                    continue;
                }
                if (line.startsWith("File")) {
                    int idx = line.indexOf("=");
                    if (idx != -1) {
                        String entryUriString = line.substring(idx + 1);
                        files.add(entryUriString);
                    }
                }
            }
        } catch (Exception e) {
        }
        return files;
    }

    public static boolean isStreamM3UFile(String uri) {
        if (isStorageUri(uri)) {
            String path = Uri.parse(uri).getPath();
            File f = new File(path);
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(STREAM_FILE_TAG)) {
                        return true;

                    }
                }
            } catch (Exception e) {
            }
        }
        return false;
    }

    public static String getStreamM3UName(String uri) {
        if (isStreamM3UFile(uri)) {
            String path = Uri.parse(uri).getPath();
            File f = new File(path);
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(STREAM_FILE_TAG)) {
                        return line.substring((M3U_ENTRY + STREAM_FILE_TAG + ":").length());
                    }
                }
            } catch (Exception e) {
            }
        }
        return null;
    }

    public static File writeStreamM3UFile(File dir, String label, String url) {
        try {
            File f = File.createTempFile("stream", null, dir);
            BufferedWriter bw = new BufferedWriter(new FileWriter(f));
            bw.write(M3U_HEADER);
            bw.newLine();
            bw.write(M3U_ENTRY + STREAM_FILE_TAG + ":" + label);
            bw.newLine();
            bw.write(url);
            bw.close();
            return f;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static HttpURLConnection setupHttpRequest(String urlStr) {
        URL url;
        HttpURLConnection urlConnection = null;
        try {
            url = new URL(urlStr);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT);
            urlConnection.setReadTimeout(HTTP_READ_TIMEOUT);
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoInput(true);
            urlConnection.connect();
            int code = urlConnection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                LogUtils.e("response:" + code);
                return null;
            }
            return urlConnection;
        } catch (Exception e) {
            LogUtils.e("Failed to connect to server", e);
            return null;
        }
    }

    public static String downloadUrlMemoryAsString(String url) {
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = setupHttpRequest(url);
            if (urlConnection == null) {
                return null;
            }

            InputStream is = urlConnection.getInputStream();
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            int byteInt;

            while ((byteInt = is.read()) >= 0) {
                byteArray.write(byteInt);
            }

            byte[] bytes = byteArray.toByteArray();
            if (bytes == null) {
                return null;
            }
            String responseBody = new String(bytes, StandardCharsets.UTF_8);

            return responseBody;
        } catch (Exception e) {
            // Download failed for any number of reasons, timeouts, connection
            // drops, etc. Just log it in debugging mode.
            LogUtils.e("", e);
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    public static int getColorAttr(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    public static int setColorAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int getTimeoutValue(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        if (pref.contains(SettingsActivity.KEY_AUTO_SILENCE_OLD)) {
            String timeoutSetting = pref.getString(SettingsActivity.KEY_AUTO_SILENCE_OLD, "10");
            int timeoutMinutes = Integer.parseInt(timeoutSetting);
            pref.edit().putInt(SettingsActivity.KEY_ALARM_SILENCE_AFTER, timeoutMinutes).commit();
            pref.edit().remove(SettingsActivity.KEY_AUTO_SILENCE_OLD).commit();
        }
        return pref.getInt(SettingsActivity.KEY_ALARM_SILENCE_AFTER, AlarmInstance.DEFAULT_ALARM_TIMEOUT_SETTING);
    }

    public static int getSnoozeTimeoutValue(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        if (pref.contains(SettingsActivity.KEY_ALARM_SNOOZE_OLD)) {
            String timeoutSetting = pref.getString(SettingsActivity.KEY_ALARM_SNOOZE_OLD, "10");
            int timeoutMinutes = Integer.parseInt(timeoutSetting);
            pref.edit().putInt(SettingsActivity.KEY_ALARM_SNOOZE_MINUTES, timeoutMinutes).commit();
            pref.edit().remove(SettingsActivity.KEY_ALARM_SNOOZE_OLD).commit();
        }
        return pref.getInt(SettingsActivity.KEY_ALARM_SNOOZE_MINUTES, AlarmInstance.DEFAULT_ALARM_TIMEOUT_SETTING);
    }
}



