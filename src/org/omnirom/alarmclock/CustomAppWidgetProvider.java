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
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.omnirom.deskclock.DeskClock;
import org.omnirom.deskclock.R;
import org.omnirom.deskclock.Utils;
import org.omnirom.deskclock.worldclock.Cities;
import org.omnirom.deskclock.worldclock.CitiesActivity;

import java.util.Date;
import java.util.Locale;

public class CustomAppWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "CustomAppWidgetProvider";

    // there is no other way to use ACTION_TIME_TICK then this
    public static class ClockUpdateService extends Service {
        private final BroadcastReceiver mClockChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DigitalAppWidgetService.LOGGING) {
                    Log.i(TAG, "ClockUpdateService:onReceive: " + action);
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
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterReceiver(mClockChangedReceiver);
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "ClockUpdateService:onDestroy");
            }
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onEnabled");
        }
        context.startService(new Intent(context, ClockUpdateService.class));
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onDisabled");
        }
        context.stopService(new Intent(context, ClockUpdateService.class));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int id : appWidgetIds) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onDeleted: " + id);
            }
            CustomAppWidgetConfigure.clearPrefs(context, id);
        }
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        int i = 0;
        for (int oldWidgetId : oldWidgetIds) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onRestored " + oldWidgetId + " " + newWidgetIds[i]);
            }
            CustomAppWidgetConfigure.remapPrefs(context, oldWidgetId, newWidgetIds[i]);
            i++;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)
                || AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)
                || Intent.ACTION_SCREEN_ON.equals(action)) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onReceive: " + action);
            }
            updateAllClocks(context);
        } else if (Cities.WORLDCLOCK_UPDATE_INTENT.equals(action)) {
            // Refresh the world cities list
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            if (appWidgetManager != null) {
                ComponentName componentName = new ComponentName(context, CustomAppWidgetProvider.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
                for (int appWidgetId : appWidgetIds) {
                    if (WidgetUtils.isShowingWorldClock(context, appWidgetId)) {
                        appWidgetManager.
                                notifyAppWidgetViewDataChanged(appWidgetId,
                                        R.id.digital_appwidget_listview);
                    }
                }
            }
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
        context.startService(new Intent(context, ClockUpdateService.class));
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onAppWidgetOptionsChanged");
        }
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        updateClock(context, widgetManager, appWidgetId);
    }

    public static void updateAfterConfigure(Context context, int appWidgetId) {
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateAfterConfigure");
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        updateClock(context, appWidgetManager, appWidgetId);
    }

    public static void updateAllClocks(Context context) {
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateClocks at = " + new Date());
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetManager != null) {
            ComponentName componentName = new ComponentName(context, CustomAppWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            for (int appWidgetId : appWidgetIds) {
                updateClock(context, appWidgetManager, appWidgetId);
            }
        }
    }

    private static void updateClock(
            Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        boolean showAlarm = WidgetUtils.isShowingAlarm(context, CustomAppWidgetConfigure.KEY_SHOW_ALARM, appWidgetId, true);
        boolean showDate = WidgetUtils.isShowingDate(context, CustomAppWidgetConfigure.KEY_SHOW_DATE, appWidgetId, true);
        Typeface clockFont = WidgetUtils.getClockFont(context, CustomAppWidgetConfigure.KEY_CLOCK_FONT, appWidgetId);
        int clockColor = WidgetUtils.getClockColor(context, CustomAppWidgetConfigure.KEY_CLOCK_COLOR, appWidgetId);
        boolean clockShadow = WidgetUtils.isClockShadow(context, CustomAppWidgetConfigure.KEY_CLOCK_SHADOW, appWidgetId);
        boolean showWorldClocks = WidgetUtils.isShowingWorldClock(context, appWidgetId);

        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateClock " + appWidgetId);
        }
        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.custom_appwidget);

        widget.setViewVisibility(R.id.the_date_image, showDate ? View.VISIBLE : View.GONE);

        // Launch clock when clicking on the time in the widget only if not a lock screen widget
        Bundle newOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);
        if (newOptions != null &&
                newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
                        != AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) {
            widget.setOnClickPendingIntent(R.id.the_clock_image,
                    PendingIntent.getActivity(context, 0, new Intent(context, DeskClock.class), 0));
            widget.setOnClickPendingIntent(R.id.the_date_image,
                    PendingIntent.getActivity(context, 0, Utils.getCalendarIntent(new Date()), 0));
        }
        float fontSize = context.getResources().getDimension(R.dimen.widget_custom_font_size);
        CharSequence timeFormat = DateFormat.is24HourFormat(context) ?
                Utils.getRaw24ModeFormat(false) :
                Utils.getRaw12ModeFormat(false);

        Bitmap textBitmap = WidgetUtils.createTimeBitmap(timeFormat.toString(),
                clockFont, fontSize, clockColor, false, -1,
                !DateFormat.is24HourFormat(context));
        if (clockShadow) {
            textBitmap = WidgetUtils.shadow(context.getResources(), textBitmap);
        }
        widget.setImageViewBitmap(R.id.the_clock_image, textBitmap);

        if (showAlarm || showDate) {
            updateDate(context, widget, clockColor, clockShadow, showDate, showAlarm, showAlarm);
        }

        if (showWorldClocks) {
            // Set up R.id.digital_appwidget_listview to use a remote views adapter
            // That remote views adapter connects to a RemoteViewsService through intent.
            final Intent intent = new Intent(context, DigitalAppWidgetService.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            widget.setRemoteAdapter(R.id.digital_appwidget_listview, intent);

            // Set up the click on any world clock to start the Cities Activity
            //TODO: Should this be in the options guard above?
            widget.setPendingIntentTemplate(R.id.digital_appwidget_listview,
                    PendingIntent.
                            getActivity(context, 0, new Intent(context, CitiesActivity.class), 0));

            // Refresh the widget
            appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetId, R.id.digital_appwidget_listview);
        }
        appWidgetManager.updateAppWidget(appWidgetId, widget);
    }

    private static void updateDate(Context context, RemoteViews widget, int clockColor,
                                   boolean clockShadow, boolean showDate, boolean showAlarm, boolean hasAlarm) {
        if (showDate || showAlarm) {
            float fontSize = context.getResources().getDimension(R.dimen.custom_widget_label_font_size);
            Typeface dateFont = Typeface.create("sans-serif-light", Typeface.NORMAL);
            CharSequence dateFormat = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                    context.getString((showAlarm && hasAlarm) ? R.string.abbrev_wday_month_day_no_year :
                            R.string.full_wday_month_day_no_year));
            Bitmap dateBitmap = WidgetUtils.createDataAlarmBitmap(context, dateFont, fontSize, clockColor,
                    false, 0.15f, showDate, showAlarm, dateFormat);
            if (dateBitmap != null) {
                if (clockShadow) {
                    dateBitmap = WidgetUtils.shadow(context.getResources(), dateBitmap);
                }
                widget.setViewVisibility(R.id.the_date_image, View.VISIBLE);
                widget.setImageViewBitmap(R.id.the_date_image, dateBitmap);
            } else {
                widget.setViewVisibility(R.id.the_date_image, View.GONE);
            }
        }
    }
}
