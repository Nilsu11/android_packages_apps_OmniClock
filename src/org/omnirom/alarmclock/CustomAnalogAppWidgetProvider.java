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
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import org.omnirom.deskclock.DeskClock;
import org.omnirom.deskclock.R;

import java.util.Date;

public class CustomAnalogAppWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "AnalogAppWidgetProvider";

    // there is no other way to use ACTION_TIME_TICK then this
    public static class AnalogClockUpdateService extends Service {
        private final BroadcastReceiver mClockChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DigitalAppWidgetService.LOGGING) {
                    Log.i(TAG, "AnalogClockUpdateService:onReceive: " + action);
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
                Log.i(TAG, "AnalogClockUpdateService:onCreate");
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterReceiver(mClockChangedReceiver);
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "AnalogClockUpdateService:onDestroy");
            }
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onEnabled");
        }
        context.startService(new Intent(context, AnalogClockUpdateService.class));
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onDisabled");
        }
        context.stopService(new Intent(context, AnalogClockUpdateService.class));
    }


    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int id : appWidgetIds) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onDeleted: " + id);
            }
            CustomAnalogAppWidgetConfigure.clearPrefs(context, id);
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
        context.startService(new Intent(context, AnalogClockUpdateService.class));
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        int i = 0;
        for (int oldWidgetId : oldWidgetIds) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onRestored " + oldWidgetId + " " + newWidgetIds[i]);
            }
            CustomAnalogAppWidgetConfigure.remapPrefs(context, oldWidgetId, newWidgetIds[i]);
            i++;
        }
    }

    public static void updateAllClocks(Context context) {
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateClocks at = " + new Date());
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetManager != null) {
            ComponentName componentName = new ComponentName(context, CustomAnalogAppWidgetProvider.class);
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
        boolean showAlarm = WidgetUtils.isShowingAlarm(context, CustomAnalogAppWidgetConfigure.KEY_SHOW_ALARM, appWidgetId, true);
        boolean showDate = WidgetUtils.isShowingDate(context, CustomAnalogAppWidgetConfigure.KEY_SHOW_DATE, appWidgetId, true);
        boolean showNumbers = WidgetUtils.isShowingNumbers(context, appWidgetId, false);
        boolean showTicks = WidgetUtils.isShowingTicks(context, appWidgetId, false);
        boolean show24Hours = WidgetUtils.isShowing24hours(context, appWidgetId, false);
        int bgColor = WidgetUtils.getAnalogBgColor(context, appWidgetId);
        int borderColor = WidgetUtils.getAnalogBorderColor(context, appWidgetId);
        int hourColor = WidgetUtils.getAnalogHourColor(context, appWidgetId);
        int minuteColor = WidgetUtils.getAnalogMinuteColor(context, appWidgetId);
        int textColor = WidgetUtils.getAnalogTextColor(context, appWidgetId);
        int accentColor = WidgetUtils.getAnalogAccentColor(context, appWidgetId);

        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateClock " + appWidgetId);
        }
        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.custom_analog_appwidget);

        // Launch clock when clicking on the time in the widget only if not a lock screen widget
        Bundle newOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);
        if (newOptions != null &&
                newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
                        != AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) {
            widget.setOnClickPendingIntent(R.id.the_clock_image,
                    PendingIntent.getActivity(context, 0, new Intent(context, DeskClock.class), 0));
        }

        Bitmap analogClock = WidgetUtils.createAnalogClockBitmap(context, showAlarm, showDate, showNumbers, showTicks,
                show24Hours, bgColor, borderColor, hourColor, minuteColor, textColor, accentColor);
        widget.setImageViewBitmap(R.id.the_clock_image, analogClock);

        appWidgetManager.updateAppWidget(appWidgetId, widget);
    }
}
