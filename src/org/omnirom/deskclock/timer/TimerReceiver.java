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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.omnirom.deskclock.DeskClock;
import org.omnirom.deskclock.TimerRingService;
import org.omnirom.deskclock.Utils;

import java.util.ArrayList;
import java.util.Iterator;

public class TimerReceiver extends BroadcastReceiver {
    private static final String TAG = "TimerReceiver";

    // Make this a large number to avoid the alarm ID's which seem to be 1, 2, ...
    // Must also be different than StopwatchService.NOTIFICATION_ID
    private static final int IN_USE_NOTIFICATION_ID = Integer.MAX_VALUE - 2;
    private static final int RESET_ALL_TIMERS_BROADCAST_ID = Integer.MAX_VALUE - 3;

    ArrayList<TimerObj> mTimers;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (Timers.LOGGING) {
            Log.v(TAG, "Received intent " + intent.toString());
        }
        String actionType = intent.getAction();
        // This action does not need the timers data
        if (Timers.NOTIF_IN_USE_CANCEL.equals(actionType)) {
            cancelInUseNotification(context);
            return;
        }

        // Get the updated timers data.
        if (mTimers == null) {
            mTimers = new ArrayList<TimerObj>();
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        TimerObj.getTimersFromSharedPrefs(prefs, mTimers);

        // These actions do not provide a timer ID, but do use the timers data
        if (Timers.NOTIF_IN_USE_SHOW.equals(actionType)) {
            showInUseNotification(context);
            return;
        } else if (Timers.NOTIF_TIMES_UP_SHOW.equals(actionType)) {
            showTimesUpNotification(context);
            return;
        }

        // Remaining actions provide a timer Id
        if (!intent.hasExtra(Timers.TIMER_INTENT_EXTRA)) {
            // No data to work with, do nothing
            Log.e(TAG, "got intent without Timer data");
            return;
        }

        // Get the timer out of the Intent
        int timerId = intent.getIntExtra(Timers.TIMER_INTENT_EXTRA, -1);
        if (timerId == -1) {
            Log.d(TAG, "OnReceive:intent without Timer data for " + actionType);
        }

        TimerObj t = Timers.findTimer(mTimers, timerId);

        if (Timers.TIMES_UP.equals(actionType)) {
            // Find the timer (if it doesn't exists, it was probably deleted).
            if (t == null) {
                Log.d(TAG, " timer not found in list - do nothing");
                return;
            }

            t.mState = TimerObj.STATE_TIMESUP;
            t.writeToSharedPref(prefs);
            // Play ringtone by using TimerRingService service with a default alarm.
            Log.d(TAG, "playing ringtone");
            Intent si = new Intent();
            si.setClass(context, TimerRingService.class);
            context.startService(si);

            // Update the in-use notification
            if (getNextRunningTimer(mTimers, false, Utils.getTimeNow()) == null) {
                // Found no running timers.
                cancelInUseNotification(context);
            } else {
                showInUseNotification(context);
            }

            cancelTimesUpNotification(context, t);
            showTimesUpNotification(context, t);
        } else if (Timers.TIMER_RESET.equals(actionType)
                || Timers.DELETE_TIMER.equals(actionType)
                || Timers.TIMER_DONE.equals(actionType)) {
            // Stop Ringtone if all timers are not in times-up status
            stopRingtoneIfNoTimesup(context);
        } else if (Timers.NOTIF_TIMES_UP_STOP.equals(actionType)) {
            // Find the timer (if it doesn't exists, it was probably deleted).
            if (t == null) {
                Log.d(TAG, "timer to stop not found in list - do nothing");
                return;
            } else if (t.mState != TimerObj.STATE_TIMESUP) {
                Log.d(TAG, "action to stop but timer not in times-up state - do nothing");
                return;
            }

            // Update timer state
            t.mState = t.getDeleteAfterUse() ? TimerObj.STATE_DELETED : TimerObj.STATE_RESTART;
            t.mTimeLeft = t.mOriginalLength = t.mSetupLength;
            t.writeToSharedPref(prefs);

            // Flag to tell DeskClock to re-sync with the database
            prefs.edit().putBoolean(Timers.FROM_NOTIFICATION, true).apply();

            cancelTimesUpNotification(context, t);

            // Done with timer - delete from data base
            if (t.getDeleteAfterUse()) {
                t.deleteFromSharedPref(prefs);
            }

            // Stop Ringtone if no timers are in times-up status
            stopRingtoneIfNoTimesup(context);
        } else if (Timers.NOTIF_TIMES_UP_PLUS_ONE.equals(actionType)) {
            // Find the timer (if it doesn't exists, it was probably deleted).
            if (t == null) {
                Log.d(TAG, "timer to +1m not found in list - do nothing");
                return;
            } else if (t.mState != TimerObj.STATE_TIMESUP) {
                Log.d(TAG, "action to +1m but timer not in times up state - do nothing");
                return;
            }

            // Restarting the timer with 1 minute left.
            t.mState = TimerObj.STATE_RUNNING;
            t.mStartTime = Utils.getTimeNow();
            t.mTimeLeft = t.mOriginalLength = TimerObj.MINUTE_IN_MILLIS;
            t.writeToSharedPref(prefs);

            // Flag to tell DeskClock to re-sync with the database
            prefs.edit().putBoolean(Timers.FROM_NOTIFICATION, true).apply();

            cancelTimesUpNotification(context, t);

            // If the app is not open, refresh the in-use notification
            if (!prefs.getBoolean(Timers.NOTIF_APP_OPEN, false)) {
                showInUseNotification(context);
            }

            // Stop Ringtone if no timers are in times-up status
            stopRingtoneIfNoTimesup(context);
        } else if (Timers.TIMER_UPDATE.equals(actionType)) {
            // Find the timer (if it doesn't exists, it was probably deleted).
            if (t == null) {
                Log.d(TAG, " timer to update not found in list - do nothing");
                return;
            }

            // Refresh buzzing notification
            if (t.mState == TimerObj.STATE_TIMESUP) {
                // Must cancel the previous notification to get all updates displayed correctly
                cancelTimesUpNotification(context, t);
                showTimesUpNotification(context, t);
            }
        } else if (Timers.NOTIF_DELETE_TIMER.equals(actionType)) {
            if (t == null) {
                Log.d(TAG, " timer to update not found in list - do nothing");
                return;
            }
            t.mState = TimerObj.STATE_DELETED;
            t.deleteFromSharedPref(prefs);
            clearInUseNotification(context);
        } else if (Timers.NOTIF_TOGGLE_STATE.equals(actionType)) {
            if (t == null) {
                Log.d(TAG, " timer to update not found in list - do nothing");
                return;
            }
            if (t.mState == TimerObj.STATE_RUNNING) {
                t.mState = TimerObj.STATE_STOPPED;
                t.updateTimeLeft(true);
            } else {
                t.mState = TimerObj.STATE_RUNNING;
                t.mStartTime = Utils.getTimeNow() - (t.mOriginalLength - t.mTimeLeft);
            }

            t.writeToSharedPref(prefs);
            updateUseNotification(context, t);
        } else if (Timers.NOTIF_RESET_TIMER.equals(actionType)) {
            if (t == null) {
                Log.d(TAG, " timer to update not found in list - do nothing");
                return;
            }
            t.mState = TimerObj.STATE_RESTART;
            t.mTimeLeft = t.mOriginalLength = t.mSetupLength;
            t.writeToSharedPref(prefs);
            clearInUseNotification(context);
        } else if (Timers.NOTIF_RESET_ALL_TIMER.equals(actionType)) {
            resetAllTimers(context, mTimers);
            clearInUseNotification(context);
        }
        // Update the next "Times up" alarm
        updateNextTimesup(context);
    }

    private void stopRingtoneIfNoTimesup(final Context context) {
        if (Timers.findExpiredTimer(mTimers) == null) {
            // Stop ringtone
            Log.d(TAG, "stopping ringtone");
            Intent si = new Intent();
            si.setClass(context, TimerRingService.class);
            context.stopService(si);
        }
    }

    // Scan all timers and find the one that will expire next.
    // Tell AlarmManager to send a "Time's up" message to this receiver when this timer expires.
    // If no timer exists, clear "time's up" message.
    private void updateNextTimesup(Context context) {
        TimerObj t = getNextRunningTimer(mTimers, false, Utils.getTimeNow());
        long nextTimesup = (t == null) ? -1 : t.getTimesupTime();
        int timerId = (t == null) ? -1 : t.mTimerId;

        Intent intent = new Intent();
        intent.setAction(Timers.TIMES_UP);
        intent.setClass(context, TimerReceiver.class);
        if (!mTimers.isEmpty()) {
            intent.putExtra(Timers.TIMER_INTENT_EXTRA, timerId);
        }
        AlarmManager mngr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = PendingIntent.getBroadcast(context,
                0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        if (t != null) {
            if (Utils.isMOrLater()) {
                // Ensure the alarm fires even if the device is dozing.
                mngr.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextTimesup, p);
            } else {
                mngr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextTimesup, p);
            }
            if (Timers.LOGGING) {
                Log.d(TAG, "Setting times up to " + nextTimesup);
            }
        } else {
            mngr.cancel(p);
            if (Timers.LOGGING) {
                Log.v(TAG, "no next times up");
            }
        }
    }

    private void showInUseNotification(final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean appOpen = prefs.getBoolean(Timers.NOTIF_APP_OPEN, false);
        ArrayList<TimerObj> timersInUse = Timers.timersInUse(mTimers);
        int numTimersInUse = timersInUse.size();

        if (appOpen || numTimersInUse == 0) {
            return;
        }

        String title, contentText;
        Long nextBroadcastTime = null;
        long now = Utils.getTimeNow();
        if (timersInUse.size() == 1) {
            TimerObj timer = timersInUse.get(0);
            boolean timerIsTicking = timer.isTicking();
            String label = timer.getLabelOrDefault(context);
            title = timerIsTicking ? label : context.getString(org.omnirom.deskclock.R.string.timer_stopped);
            long timeLeft = timerIsTicking ? timer.getTimesupTime() - now : timer.mTimeLeft;
            contentText = buildTimeRemaining(context, timeLeft);
            if (timerIsTicking && timeLeft > TimerObj.MINUTE_IN_MILLIS) {
                nextBroadcastTime = getUpdateBroadcastTime(now);
            }
            showCollapsedNotificationWithNext(context, timer, title, contentText, nextBroadcastTime);
        } else {
            TimerObj timer = getNextRunningTimer(timersInUse, false, now);
            if (timer == null) {
                // No running timers.
                title = String.format(
                        context.getString(org.omnirom.deskclock.R.string.timers_stopped), numTimersInUse);
                contentText = context.getString(org.omnirom.deskclock.R.string.all_timers_stopped_notif);
            } else {
                // We have at least one timer running and other timers stopped.
                title = String.format(
                        context.getString(org.omnirom.deskclock.R.string.timers_in_use), numTimersInUse);
                long completionTime = timer.getTimesupTime();
                long timeLeft = completionTime - now;
                contentText = String.format(context.getString(org.omnirom.deskclock.R.string.next_timer_notif),
                        buildTimeRemaining(context, timeLeft));
                if (timeLeft <= TimerObj.MINUTE_IN_MILLIS) {
                    TimerObj timerWithUpdate = getNextRunningTimer(timersInUse, true, now);
                    if (timerWithUpdate != null) {
                        completionTime = timerWithUpdate.getTimesupTime();
                        timeLeft = completionTime - now;
                        nextBroadcastTime = getUpdateBroadcastTime(now);
                    }
                } else {
                    nextBroadcastTime = getUpdateBroadcastTime(now);
                }
            }
            showCollapsedNotificationWithNext(context, null, title, contentText, nextBroadcastTime);
        }
    }

    private void updateUseNotification(final Context context, TimerObj timer) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean appOpen = prefs.getBoolean(Timers.NOTIF_APP_OPEN, false);

        if (appOpen || timer == null) {
            return;
        }

        String title, contentText;
        Long nextBroadcastTime = null;
        long now = Utils.getTimeNow();

        boolean timerIsTicking = timer.isTicking();
        String label = timer.getLabelOrDefault(context);
        title = timerIsTicking ? label : context.getString(org.omnirom.deskclock.R.string.timer_stopped);
        long timeLeft = timerIsTicking ? timer.getTimesupTime() - now : timer.mTimeLeft;
        contentText = buildTimeRemaining(context, timeLeft);
        if (timerIsTicking && timeLeft > TimerObj.MINUTE_IN_MILLIS) {
            nextBroadcastTime = getUpdateBroadcastTime(now);
        }
        showCollapsedNotificationWithNext(context, timer, title, contentText, nextBroadcastTime);
    }

    private void clearInUseNotification(final Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.cancel(IN_USE_NOTIFICATION_ID);
    }

    // notification will be updated every minute
    private long getUpdateBroadcastTime(long now) {
        return now + TimerObj.MINUTE_IN_MILLIS;
    }

    private void showCollapsedNotificationWithNext(
            final Context context, TimerObj timer, String title, String text, Long nextBroadcastTime) {
        Intent activityIntent = new Intent(context, DeskClock.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra(DeskClock.SELECT_TAB_INTENT_EXTRA, DeskClock.TIMER_TAB_INDEX);
        PendingIntent pendingActivityIntent = PendingIntent.getActivity(context, 0, activityIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        if (Utils.isNougatOrLater()) {
            showCollapsedNotificationNew(context, timer, title, text, Notification.PRIORITY_HIGH,
                    pendingActivityIntent, IN_USE_NOTIFICATION_ID, false);
        } else {
            showCollapsedNotificationOld(context, timer, title, text, Notification.PRIORITY_HIGH,
                    pendingActivityIntent, IN_USE_NOTIFICATION_ID, false);
        }

        if (nextBroadcastTime == null) {
            return;
        }
        Intent nextBroadcast = new Intent();
        nextBroadcast.setAction(Timers.NOTIF_IN_USE_SHOW);
        PendingIntent pendingNextBroadcast =
                PendingIntent.getBroadcast(context, 0, nextBroadcast, 0);
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Utils.isMOrLater()) {
            // Ensure the alarm fires even if the device is dozing.
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextBroadcastTime, pendingNextBroadcast);
        } else {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextBroadcastTime, pendingNextBroadcast);
        }
    }

    private static void showCollapsedNotificationOld(final Context context, TimerObj timer, String title, String text,
                                                     int priority, PendingIntent pendingIntent, int notificationId, boolean showTicker) {

        Notification.Builder builder = new Notification.Builder(context)
                .setAutoCancel(false)
                .setContentTitle(title)
                .setContentText(text)
                .setDeleteIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(priority)
                .setShowWhen(false)
                .setSmallIcon(org.omnirom.deskclock.R.drawable.ic_notify_timer)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .setColor(context.getResources().getColor(org.omnirom.deskclock.R.color.primary));

        if (showTicker) {
            builder.setTicker(text);
        }

        Notification notification = null;

        // only for a single timer show notifiction buttons
        if (timer != null) {
            // Set up remoteviews for the notification.
            RemoteViews remoteViewsCollapsed = new RemoteViews(context.getPackageName(),
                    org.omnirom.deskclock.R.layout.custom_notif_collapsed);
            remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.collapsed_chronometer, View.GONE);
            remoteViewsCollapsed.setTextViewText(org.omnirom.deskclock.R.id.collapsed_title, title);
            remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.collapsed_title, View.VISIBLE);
            remoteViewsCollapsed.setTextViewText(org.omnirom.deskclock.R.id.collapsed_text, text);
            remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.collapsed_text, View.VISIBLE);
            remoteViewsCollapsed.
                    setImageViewResource(org.omnirom.deskclock.R.id.notification_icon, org.omnirom.deskclock.R.drawable.ic_notify_timer);

            RemoteViews remoteViewsExpanded = new RemoteViews(context.getPackageName(),
                    org.omnirom.deskclock.R.layout.custom_notif_expanded);
            remoteViewsExpanded.setViewVisibility(org.omnirom.deskclock.R.id.expanded_chronometer, View.GONE);
            remoteViewsExpanded.setTextViewText(org.omnirom.deskclock.R.id.expanded_title, title);
            remoteViewsExpanded.setViewVisibility(org.omnirom.deskclock.R.id.expanded_title, View.VISIBLE);
            remoteViewsExpanded.setTextViewText(org.omnirom.deskclock.R.id.expanded_text, text);
            remoteViewsExpanded.setViewVisibility(org.omnirom.deskclock.R.id.expanded_text, View.VISIBLE);
            remoteViewsExpanded.
                    setImageViewResource(org.omnirom.deskclock.R.id.notification_icon, org.omnirom.deskclock.R.drawable.ic_notify_timer);

            // delete notification button will delete timer
            PendingIntent deleteNotificationIntent = PendingIntent.getBroadcast(context, timer.mTimerId,
                    new Intent(Timers.NOTIF_DELETE_TIMER)
                            .putExtra(Timers.TIMER_INTENT_EXTRA, timer.mTimerId),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            // reset timer action
            PendingIntent resetTimerIntent = PendingIntent.getBroadcast(context, timer.mTimerId,
                    new Intent(Timers.NOTIF_RESET_TIMER)
                            .putExtra(Timers.TIMER_INTENT_EXTRA, timer.mTimerId),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            remoteViewsExpanded.setTextViewText(
                    org.omnirom.deskclock.R.id.left_button, context.getResources().getString(org.omnirom.deskclock.R.string.timer_reset).toUpperCase());
            remoteViewsExpanded.setOnClickPendingIntent(org.omnirom.deskclock.R.id.left_button,
                    resetTimerIntent);
            remoteViewsExpanded.
                    setTextViewCompoundDrawablesRelative(org.omnirom.deskclock.R.id.left_button,
                            org.omnirom.deskclock.R.drawable.ic_notify_reset_black, 0, 0, 0);

            if (timer.mState == TimerObj.STATE_RUNNING || timer.mState == TimerObj.STATE_STOPPED) {
                PendingIntent toggleTimerIntent = PendingIntent.getBroadcast(context, timer.mTimerId,
                        new Intent(Timers.NOTIF_TOGGLE_STATE)
                                .putExtra(Timers.TIMER_INTENT_EXTRA, timer.mTimerId),
                        PendingIntent.FLAG_UPDATE_CURRENT);
                remoteViewsExpanded.setTextViewText(
                        org.omnirom.deskclock.R.id.right_button, context.getResources().getString(timer.mState == TimerObj.STATE_RUNNING ?
                                org.omnirom.deskclock.R.string.sw_pause_button :
                                org.omnirom.deskclock.R.string.sw_start_button).toUpperCase());
                remoteViewsExpanded.setOnClickPendingIntent(org.omnirom.deskclock.R.id.right_button,
                        toggleTimerIntent);
                remoteViewsExpanded.
                        setTextViewCompoundDrawablesRelative(org.omnirom.deskclock.R.id.right_button,
                                timer.mState == TimerObj.STATE_RUNNING ?
                                        org.omnirom.deskclock.R.drawable.ic_notify_pause_black :
                                        org.omnirom.deskclock.R.drawable.ic_notify_start_black, 0, 0, 0);
                if (timer.mState == TimerObj.STATE_STOPPED) {
                    // Show stopped string.
                    remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.notif_text, View.GONE);
                    remoteViewsExpanded.
                            setTextViewText(org.omnirom.deskclock.R.id.notif_text, context.getResources().getString(org.omnirom.deskclock.R.string.swn_stopped));
                    remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.notif_text, View.VISIBLE);
                } else {
                    remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.notif_text, View.GONE);
                    remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.notif_text, View.GONE);
                }
            }
            builder.setContent(remoteViewsCollapsed);
            builder.setOngoing(timer.mState == TimerObj.STATE_RUNNING);
            builder.setDeleteIntent(deleteNotificationIntent);

            notification = builder.build();
            notification.bigContentView = remoteViewsExpanded;
        } else {
            // show single button to reset all timers
            PendingIntent resetAllTimerIntent = PendingIntent.getBroadcast(context, RESET_ALL_TIMERS_BROADCAST_ID,
                    new Intent(Timers.NOTIF_RESET_ALL_TIMER)
                            .putExtra(Timers.TIMER_INTENT_EXTRA, RESET_ALL_TIMERS_BROADCAST_ID),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(org.omnirom.deskclock.R.drawable.ic_notify_reset_black,
                    context.getResources().getString(org.omnirom.deskclock.R.string.timer_reset),
                    resetAllTimerIntent);
            notification = builder.build();
        }

        notification.contentIntent = pendingIntent;
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(notificationId, notification);
    }

    private static void showCollapsedNotificationNew(final Context context, TimerObj timer, String title, String text,
                                                     int priority, PendingIntent pendingIntent, int notificationId, boolean showTicker) {
        if (Utils.isNougatOrLater()) {
            Notification.Builder builder = new Notification.Builder(context)
                    .setAutoCancel(false)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setDeleteIntent(pendingIntent)
                    .setOngoing(true)
                    .setPriority(priority)
                    .setShowWhen(false)
                    .setSmallIcon(org.omnirom.deskclock.R.drawable.ic_notify_timer)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setStyle(new Notification.DecoratedCustomViewStyle())
                    .setLocalOnly(true)
                    .setColor(context.getResources().getColor(org.omnirom.deskclock.R.color.primary));

            if (showTicker) {
                builder.setTicker(text);
            }

            Notification notification = null;

            // only for a single timer show notifiction buttons
            if (timer != null) {
                long now = Utils.getTimeNow();
                long baseTime = now + (timer.isTicking() ? timer.getTimesupTime() - now : timer.mTimeLeft);

                // Set up remoteviews for the notification.
                RemoteViews remoteViewsCollapsed = new RemoteViews(context.getPackageName(),
                        org.omnirom.deskclock.R.layout.custom_notif_nougat);
                remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.notif_chronometer, View.VISIBLE);
                remoteViewsCollapsed.setChronometer(
                        org.omnirom.deskclock.R.id.notif_chronometer, baseTime, null, timer.isTicking());
                remoteViewsCollapsed.setChronometerCountDown(org.omnirom.deskclock.R.id.notif_chronometer, true);

                RemoteViews remoteViewsExpanded = new RemoteViews(context.getPackageName(),
                        org.omnirom.deskclock.R.layout.custom_notif_nougat);
                remoteViewsExpanded.setViewVisibility(org.omnirom.deskclock.R.id.notif_chronometer, View.VISIBLE);
                remoteViewsExpanded.setChronometer(
                        org.omnirom.deskclock.R.id.notif_chronometer, baseTime, null, timer.isTicking());
                remoteViewsExpanded.setChronometerCountDown(org.omnirom.deskclock.R.id.notif_chronometer, true);

                // delete notification button will delete timer
                PendingIntent deleteNotificationIntent = PendingIntent.getBroadcast(context, timer.mTimerId,
                        new Intent(Timers.NOTIF_DELETE_TIMER)
                                .putExtra(Timers.TIMER_INTENT_EXTRA, timer.mTimerId),
                        PendingIntent.FLAG_UPDATE_CURRENT);

                // reset timer action
                PendingIntent resetTimerIntent = PendingIntent.getBroadcast(context, timer.mTimerId,
                        new Intent(Timers.NOTIF_RESET_TIMER)
                                .putExtra(Timers.TIMER_INTENT_EXTRA, timer.mTimerId),
                        PendingIntent.FLAG_UPDATE_CURRENT);

                builder.addAction(org.omnirom.deskclock.R.drawable.ic_notify_reset_black,
                        context.getResources().getString(org.omnirom.deskclock.R.string.timer_reset),
                        resetTimerIntent);

                if (timer.mState == TimerObj.STATE_RUNNING || timer.mState == TimerObj.STATE_STOPPED) {
                    PendingIntent toggleTimerIntent = PendingIntent.getBroadcast(context, timer.mTimerId,
                            new Intent(Timers.NOTIF_TOGGLE_STATE)
                                    .putExtra(Timers.TIMER_INTENT_EXTRA, timer.mTimerId),
                            PendingIntent.FLAG_UPDATE_CURRENT);

                    builder.addAction(timer.mState == TimerObj.STATE_RUNNING ?
                                    org.omnirom.deskclock.R.drawable.ic_notify_pause_black :
                                    org.omnirom.deskclock.R.drawable.ic_notify_start_black,
                            context.getResources().getString(timer.mState == TimerObj.STATE_RUNNING ?
                                    org.omnirom.deskclock.R.string.sw_pause_button :
                                    org.omnirom.deskclock.R.string.sw_start_button),
                            toggleTimerIntent);

                    if (timer.mState == TimerObj.STATE_STOPPED) {
                        // Show stopped string.
                        remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.notif_text, View.GONE);
                        remoteViewsExpanded.
                                setTextViewText(org.omnirom.deskclock.R.id.notif_text, context.getResources().getString(org.omnirom.deskclock.R.string.swn_stopped));
                        remoteViewsExpanded.setViewVisibility(org.omnirom.deskclock.R.id.notif_text, View.VISIBLE);
                    } else {
                        remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.notif_text, View.GONE);
                        remoteViewsCollapsed.setViewVisibility(org.omnirom.deskclock.R.id.notif_text, View.GONE);
                    }
                }
                builder.setCustomContentView(remoteViewsCollapsed);
                builder.setCustomBigContentView(remoteViewsExpanded);
                builder.setOngoing(timer.mState == TimerObj.STATE_RUNNING);
                builder.setDeleteIntent(deleteNotificationIntent);

                notification = builder.build();
            } else {
                // show single button to reset all timers
                PendingIntent resetAllTimerIntent = PendingIntent.getBroadcast(context, RESET_ALL_TIMERS_BROADCAST_ID,
                        new Intent(Timers.NOTIF_RESET_ALL_TIMER)
                                .putExtra(Timers.TIMER_INTENT_EXTRA, RESET_ALL_TIMERS_BROADCAST_ID),
                        PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(org.omnirom.deskclock.R.drawable.ic_notify_reset_black,
                        context.getResources().getString(org.omnirom.deskclock.R.string.timer_reset),
                        resetAllTimerIntent);
                notification = builder.build();
            }

            notification.contentIntent = pendingIntent;
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            notificationManager.notify(notificationId, notification);
        }
    }

    private String buildTimeRemaining(Context context, long timeLeft) {
        if (timeLeft < 0) {
            // We should never be here...
            Log.v(TAG, "Will not show notification for timer already expired.");
            return null;
        }

        long hundreds, seconds, minutes, hours;
        seconds = timeLeft / 1000;
        minutes = seconds / 60;
        seconds = seconds - minutes * 60;
        hours = minutes / 60;
        minutes = minutes - hours * 60;
        if (hours > 99) {
            hours = 0;
        }

        String hourSeq = (hours == 0) ? "" :
                ((hours == 1) ? context.getString(org.omnirom.deskclock.R.string.hour) :
                        context.getString(org.omnirom.deskclock.R.string.hours, Long.toString(hours)));
        String minSeq = (minutes == 0) ? "" :
                ((minutes == 1) ? context.getString(org.omnirom.deskclock.R.string.minute) :
                        context.getString(org.omnirom.deskclock.R.string.minutes, Long.toString(minutes)));

        boolean dispHour = hours > 0;
        boolean dispMinute = minutes > 0;
        int index = (dispHour ? 1 : 0) | (dispMinute ? 2 : 0);
        String[] formats = context.getResources().getStringArray(org.omnirom.deskclock.R.array.timer_notifications);
        return String.format(formats[index], hourSeq, minSeq);
    }

    private TimerObj getNextRunningTimer(
            ArrayList<TimerObj> timers, boolean requireNextUpdate, long now) {
        long nextTimesup = Long.MAX_VALUE;
        boolean nextTimerFound = false;
        Iterator<TimerObj> i = timers.iterator();
        TimerObj t = null;
        while (i.hasNext()) {
            TimerObj tmp = i.next();
            if (tmp.mState == TimerObj.STATE_RUNNING) {
                long timesupTime = tmp.getTimesupTime();
                long timeLeft = timesupTime - now;
                if (timesupTime < nextTimesup && (!requireNextUpdate || timeLeft > 60)) {
                    nextTimesup = timesupTime;
                    nextTimerFound = true;
                    t = tmp;
                }
            }
        }
        if (nextTimerFound) {
            return t;
        } else {
            return null;
        }
    }

    private void cancelInUseNotification(final Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(IN_USE_NOTIFICATION_ID);
    }

    private void showTimesUpNotification(final Context context) {
        for (TimerObj timerObj : Timers.timersInTimesUp(mTimers)) {
            showTimesUpNotification(context, timerObj);
        }
    }

    private void showTimesUpNotification(final Context context, TimerObj timerObj) {
        // Content Intent. When clicked will show the timer full screen
        PendingIntent contentIntent = PendingIntent.getActivity(context, timerObj.mTimerId,
                new Intent(context, TimerAlertFullScreen.class).putExtra(
                        Timers.TIMER_INTENT_EXTRA, timerObj.mTimerId),
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Setup fullscreen intent - either heads up or fullscreen
        PendingIntent fullScreenIntent = PendingIntent.getActivity(context, timerObj.mTimerId,
                new Intent(context, TimerAlertFullScreen.class)
                        .putExtra(Timers.TIMER_INTENT_EXTRA, timerObj.mTimerId)
                        .setAction("fullscreen_activity")
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Add one minute action button
        PendingIntent addOneMinuteAction = PendingIntent.getBroadcast(context, timerObj.mTimerId,
                new Intent(Timers.NOTIF_TIMES_UP_PLUS_ONE)
                        .putExtra(Timers.TIMER_INTENT_EXTRA, timerObj.mTimerId),
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Add stop/done action button
        PendingIntent stopIntent = PendingIntent.getBroadcast(context, timerObj.mTimerId,
                new Intent(Timers.NOTIF_TIMES_UP_STOP)
                        .putExtra(Timers.TIMER_INTENT_EXTRA, timerObj.mTimerId),
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Notification creation
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentIntent(contentIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .addAction(org.omnirom.deskclock.R.drawable.ic_menu_add,
                        context.getResources().getString(org.omnirom.deskclock.R.string.timer_plus_1_min),
                        addOneMinuteAction)
                .addAction(
                        org.omnirom.deskclock.R.drawable.ic_notify_stop,
                        context.getResources().getString(org.omnirom.deskclock.R.string.timer_stop),
                        stopIntent)
                .setContentTitle(timerObj.getLabelOrDefault(context))
                .setContentText(context.getResources().getString(org.omnirom.deskclock.R.string.timer_times_up))
                .setSmallIcon(org.omnirom.deskclock.R.drawable.ic_notify_timer)
                .setAutoCancel(false)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(context.getResources().getColor(org.omnirom.deskclock.R.color.primary));

        if (!Utils.showWearNotification(context)) {
            builder.setLocalOnly(true);
        }
        if (Utils.isNotificationVibrate(context)) {
            builder.setVibrate(new long[] {0, 100, 50, 100} );
        }
        Notification notification = builder.build();

        // Send the notification using the timer's id to identify the
        // correct notification
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                timerObj.mTimerId, notification);
        if (Timers.LOGGING) {
            Log.v(TAG, "Setting times-up notification for "
                    + timerObj.getLabelOrDefault(context) + " #" + timerObj.mTimerId);
        }
    }

    private void cancelTimesUpNotification(final Context context) {
        for (TimerObj timerObj : Timers.timersInTimesUp(mTimers)) {
            cancelTimesUpNotification(context, timerObj);
        }
    }

    private void cancelTimesUpNotification(final Context context, TimerObj timerObj) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(timerObj.mTimerId);
        if (Timers.LOGGING) {
            Log.v(TAG, "Canceling times-up notification for "
                    + timerObj.getLabelOrDefault(context) + " #" + timerObj.mTimerId);
        }
    }

    private void closeNotificationShade(final Context context) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(intent);
    }

    private void resetAllTimers(final Context context, ArrayList<TimerObj> timers) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Iterator<TimerObj> i = timers.iterator();
        while (i.hasNext()) {
            TimerObj t = i.next();
            t.mState = TimerObj.STATE_RESTART;
            t.mTimeLeft = t.mOriginalLength = t.mSetupLength;
            t.writeToSharedPref(prefs);
        }
    }
}
