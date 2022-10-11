/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.omnirom.alarmclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.omnirom.deskclock.DeskClock;
import org.omnirom.deskclock.R;
import org.omnirom.deskclock.Utils;

import java.util.Date;
import java.util.Locale;

public class BinaryClockAppWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "BinaryClockAppWidgetProvider";

    // there is no other way to use ACTION_TIME_TICK then this
    public static class BinaryClockUpdateService extends Service {
        private final BroadcastReceiver mClockChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DigitalAppWidgetService.LOGGING) {
                    Log.i(TAG, "BinaryClockUpdateService:onReceive: " + action);
                }
                updateAllClocks(context);
            }
        };

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_TIME_TICK);
            registerReceiver(mClockChangedReceiver, intentFilter);
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "BinaryClockUpdateService:onCreate");
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterReceiver(mClockChangedReceiver);
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "BinaryClockUpdateService:onDestroy");
            }
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onEnabled");
        }
        context.startService(new Intent(context, BinaryClockUpdateService.class));
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onDisabled");
        }
        context.stopService(new Intent(context, BinaryClockUpdateService.class));
    }


    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int id : appWidgetIds) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onDeleted: " + id);
            }
            BinaryClockAppWidgetConfigure.clearPrefs(context, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)
                || Intent.ACTION_SCREEN_ON.equals(action)) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onReceive: " + action);
            }
            updateAllClocks(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        for (int appWidgetId : appWidgetIds) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onUpdate " + appWidgetId);
            }
            updateClock(context, appWidgetManager, appWidgetId);
        }
        context.startService(new Intent(context, BinaryClockUpdateService.class));
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        int i = 0;
        for (int oldWidgetId : oldWidgetIds) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onRestored " + oldWidgetId + " " + newWidgetIds[i]);
            }
            BinaryClockAppWidgetConfigure.remapPrefs(context, oldWidgetId, newWidgetIds[i]);
            i++;
        }
    }

    public static void updateAllClocks(Context context) {
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateClocks at = " + new Date());
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetManager != null) {
            ComponentName componentName = new ComponentName(context, BinaryClockAppWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            for (int appWidgetId : appWidgetIds) {
                updateClock(context, appWidgetManager, appWidgetId);
            }
        }
    }

    public static void updateAfterConfigure(Context context, int appWidgetId) {
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateAfterConfigure");
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        updateClock(context, appWidgetManager, appWidgetId);
    }

    private static void updateClock(
            Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        int clockColor = WidgetUtils.getClockColor(context, BinaryClockAppWidgetConfigure.KEY_CLOCK_COLOR, appWidgetId);
        boolean showAlarm = WidgetUtils.isShowingAlarm(context, BinaryClockAppWidgetConfigure.KEY_SHOW_ALARM, appWidgetId, true);
        boolean showDate = WidgetUtils.isShowingDate(context, BinaryClockAppWidgetConfigure.KEY_SHOW_DATE, appWidgetId, true);
        Typeface clockFont = WidgetUtils.getClockFont(context, BinaryClockAppWidgetConfigure.KEY_CLOCK_FONT, appWidgetId);
        boolean clockShadow = WidgetUtils.isClockShadow(context, BinaryClockAppWidgetConfigure.KEY_CLOCK_SHADOW, appWidgetId);

        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateClock " + appWidgetId);
        }
        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.binary_clock_appwidget);

        // Launch clock when clicking on the time in the widget only if not a lock screen widget
        Bundle newOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);
        if (newOptions != null &&
                newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
                        != AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) {
            widget.setOnClickPendingIntent(R.id.the_clock_image,
                    PendingIntent.getActivity(context, 0, new Intent(context, DeskClock.class), 0));
        }

        Bitmap dateBitmap = null;
        if (showAlarm || showDate) {
            float fontSize = context.getResources().getDimension(R.dimen.binary_clock_label_font_size);
            CharSequence dateFormat = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                    context.getString(R.string.abbrev_wday_month_day_no_year));
            Typeface dateFont = Typeface.create("sans-serif-light", Typeface.NORMAL);
            dateBitmap = WidgetUtils.createDataAlarmBitmap(context, dateFont, fontSize, clockColor,
                    false, 0.15f, showDate, showAlarm, dateFormat);
        }

        Bitmap binaryClock = WidgetUtils.createBinaryClockBitmap(context, clockColor, clockShadow, dateBitmap);
        if (clockShadow) {
            binaryClock = WidgetUtils.shadow(context.getResources(), binaryClock);
        }
        widget.setImageViewBitmap(R.id.the_clock_image, binaryClock);

        appWidgetManager.updateAppWidget(appWidgetId, widget);
    }
}
