/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.omnirom.deskclock.AlarmClockFragment;
import org.omnirom.deskclock.AlarmUtils;
import org.omnirom.deskclock.DeskClock;
import org.omnirom.deskclock.LogUtils;
import org.omnirom.deskclock.NotificationChannelManager;
import org.omnirom.deskclock.NotificationChannelManager.Channel;
import org.omnirom.deskclock.Utils;
import org.omnirom.deskclock.provider.Alarm;
import org.omnirom.deskclock.provider.AlarmInstance;

import java.util.Calendar;

public final class AlarmNotifications {

    public static void registerNextAlarmWithAlarmManager(Context context, AlarmInstance instance)  {
        // Sets a surrogate alarm with alarm manager that provides the AlarmClockInfo for the
        // alarm that is going to fire next. The operation is constructed such that it is ignored
        // by AlarmStateManager.

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        int flags = instance == null ? PendingIntent.FLAG_NO_CREATE : 0;
        flags = flags | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent operation = PendingIntent.getBroadcast(context, 0 /* requestCode */,
                AlarmStateManager.createIndicatorIntent(context), flags);

        if (instance != null) {
            long alarmTime = instance.getAlarmTime().getTimeInMillis();

            // Create an intent that can be used to show or edit details of the next alarm.
            PendingIntent viewIntent = PendingIntent.getActivity(context, instance.hashCode(),
                    createViewAlarmIntent(context, instance), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager.AlarmClockInfo info =
                    new AlarmManager.AlarmClockInfo(alarmTime, viewIntent);
            alarmManager.setAlarmClock(info, operation);
        } else if (operation != null) {
            alarmManager.cancel(operation);
        }
    }

    /*public static void showLowPriorityNotification(Context context, AlarmInstance instance) {
        LogUtils.v("Displaying low priority notification for alarm instance: " + instance.mId);
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        Resources resources = context.getResources();
        Notification.Builder notification = new Notification.Builder(context)
                .setContentTitle(resources.getString(R.string.alarm_alert_predismiss_title))
                .setContentText(AlarmUtils.getAlarmText(context, instance))
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setAutoCancel(false)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setLocalOnly(true);

        // Setup up hide notification
        Intent hideIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DELETE_TAG, instance,
                AlarmInstance.HIDE_NOTIFICATION_STATE);
        notification.setDeleteIntent(PendingIntent.getBroadcast(context, instance.hashCode(),
                hideIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // Setup up dismiss action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        notification.addAction(R.drawable.ic_alarm_off_white_24dp,
                resources.getString(R.string.alarm_alert_dismiss_now_text),
                PendingIntent.getBroadcast(context, instance.hashCode(),
                        dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // Setup content action if instance is owned by alarm
        Intent viewAlarmIntent = createViewAlarmIntent(context, instance);
        notification.setContentIntent(PendingIntent.getActivity(context, instance.hashCode(),
                viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        nm.cancel(instance.hashCode());
        nm.notify(instance.hashCode(), notification.build());
    }*/

    public static void showHighPriorityNotification(Context context, AlarmInstance instance) {
        LogUtils.v("Displaying high priority notification for alarm instance: " + instance.mId);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        Resources resources = context.getResources();
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                Channel.DEFAULT_NOTIFICATION)
                .setContentTitle(resources.getString(org.omnirom.deskclock.R.string.alarm_alert_predismiss_title))
                .setContentText(AlarmUtils.getAlarmText(context, instance))
                .setSmallIcon(org.omnirom.deskclock.R.drawable.ic_notify_alarm)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(context.getResources().getColor(org.omnirom.deskclock.R.color.primary));

        if (!Utils.showWearNotification(context)) {
            notification.setLocalOnly(true);
        }
        // Setup up dismiss action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        notification.addAction(org.omnirom.deskclock.R.drawable.ic_notify_alarm_off,
                resources.getString(org.omnirom.deskclock.R.string.alarm_alert_dismiss_now_text),
                PendingIntent.getBroadcast(context, instance.hashCode(),
                        dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // Setup content action if instance is owned by alarm
        Intent viewAlarmIntent = createViewAlarmIntent(context, instance);
        notification.setContentIntent(PendingIntent.getActivity(context, instance.hashCode(),
                viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        NotificationChannelManager.applyChannel(notification, context, Channel.DEFAULT_NOTIFICATION);
        nm.cancel(instance.hashCode());
        nm.notify(instance.hashCode(), notification.build());
    }

    public static void showSnoozeNotification(Context context, AlarmInstance instance, Calendar snoozeEndTime) {
        LogUtils.v("Displaying snoozed notification for alarm instance: " + instance.mId);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        Resources resources = context.getResources();
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                Channel.DEFAULT_NOTIFICATION)
                .setContentTitle(instance.getLabelOrDefault(context))
                .setContentText(resources.getString(org.omnirom.deskclock.R.string.alarm_alert_snooze_until,
                        AlarmUtils.getFormattedTime(context, snoozeEndTime)))
                .setSmallIcon(org.omnirom.deskclock.R.drawable.ic_notify_alarm)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(context.getResources().getColor(org.omnirom.deskclock.R.color.primary));

        if (!Utils.showWearNotification(context)) {
            notification.setLocalOnly(true);
        }
        // Setup up dismiss action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        notification.addAction(org.omnirom.deskclock.R.drawable.ic_notify_alarm_off,
                resources.getString(org.omnirom.deskclock.R.string.alarm_alert_dismiss_text),
                PendingIntent.getBroadcast(context, instance.hashCode(),
                        dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // Setup content action if instance is owned by alarm
        Intent viewAlarmIntent = createViewAlarmIntent(context, instance);
        notification.setContentIntent(PendingIntent.getActivity(context, instance.hashCode(),
                viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        NotificationChannelManager.applyChannel(notification, context, Channel.DEFAULT_NOTIFICATION);
        nm.cancel(instance.hashCode());
        nm.notify(instance.hashCode(), notification.build());
    }

    public static void showMissedNotification(Context context, AlarmInstance instance) {
        LogUtils.v("Displaying missed notification for alarm instance: " + instance.mId);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        String label = instance.mLabel;
        String alarmTime = AlarmUtils.getFormattedTime(context, instance.getAlarmTime());
        String contextText = instance.mLabel.isEmpty() ? alarmTime :
                context.getString(org.omnirom.deskclock.R.string.alarm_missed_text, alarmTime, label);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                Channel.DEFAULT_NOTIFICATION)
                .setContentTitle(context.getString(org.omnirom.deskclock.R.string.alarm_missed_title))
                .setContentText(contextText)
                .setAutoCancel(false)
                .setSmallIcon(org.omnirom.deskclock.R.drawable.ic_notify_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .setColor(context.getResources().getColor(org.omnirom.deskclock.R.color.primary));

        // Setup dismiss intent
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        notification.setDeleteIntent(PendingIntent.getBroadcast(context, instance.hashCode(),
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // Setup content intent
        Intent showAndDismiss = AlarmInstance.createIntent(context, AlarmStateManager.class,
                instance.mId);
        showAndDismiss.setAction(AlarmStateManager.SHOW_AND_DISMISS_ALARM_ACTION);
        notification.setContentIntent(PendingIntent.getBroadcast(context, instance.hashCode(),
                showAndDismiss, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        NotificationChannelManager.applyChannel(notification, context, Channel.DEFAULT_NOTIFICATION);
        nm.cancel(instance.hashCode());
        nm.notify(instance.hashCode(), notification.build());
    }

    private static NotificationCompat.Builder getAlarmNotification(Context context, AlarmInstance instance) {
        if (instance.mAlarmState == AlarmInstance.PRE_ALARM_STATE) {
            LogUtils.v("Displaying pre-alarm notification for alarm instance: " + instance.mId);
        } else {
            LogUtils.v("Displaying alarm notification for alarm instance: " + instance.mId);
        }

        // Close dialogs and window shade, so this will display
        //context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        Resources resources = context.getResources();
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                Channel.EVENT_EXPIRED)
                .setContentTitle(AlarmUtils.getAlarmTitle(context, instance))
                .setContentText(AlarmUtils.getFormattedTime(context, instance.getAlarmTime()))
                .setSmallIcon(org.omnirom.deskclock.R.drawable.ic_notify_alarm)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(resources.getColor(org.omnirom.deskclock.R.color.primary));

        // Setup Snooze Action
        if (AlarmStateManager.canSnooze(context)) {
            Intent snoozeIntent = AlarmStateManager.createStateChangeIntent(context,
                    AlarmStateManager.ALARM_SNOOZE_TAG, instance, AlarmInstance.SNOOZE_STATE);
            PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(context, instance.hashCode(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            notification.addAction(org.omnirom.deskclock.R.drawable.ic_notify_snooze,
                    resources.getString(org.omnirom.deskclock.R.string.alarm_alert_snooze_text), snoozePendingIntent);
        }

        // Setup Dismiss Action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(context,
                instance.hashCode(), dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notification.addAction(org.omnirom.deskclock.R.drawable.ic_notify_alarm_off,
                resources.getString(org.omnirom.deskclock.R.string.alarm_alert_dismiss_text),
                dismissPendingIntent);

        // Setup Content Action
        Intent contentIntent = AlarmInstance.createIntent(context, AlarmActivity.class,
                instance.mId);
        notification.setContentIntent(PendingIntent.getActivity(context,
                instance.hashCode(), contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // Setup fullscreen intent
        Intent fullScreenIntent = AlarmInstance.createIntent(context, AlarmActivity.class,
                instance.mId);
        // set action, so we can be different then content pending intent
        fullScreenIntent.setAction("fullscreen_activity");
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        notification.setFullScreenIntent(PendingIntent.getActivity(context,
                instance.hashCode(), fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE), true);
        NotificationChannelManager.applyChannel(notification, context, Channel.EVENT_EXPIRED);
        return notification;
    }
    @Deprecated
    public static void showAlarmNotification(Context context, AlarmInstance instance) {
        NotificationCompat.Builder notification = getAlarmNotification(context, instance);
        if (Utils.isNotificationVibrate(context)) {
            notification.setVibrate(new long[] {0, 100, 50, 100} );
        }
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        notification.setLocalOnly(true);
        notification.setGroup("GROUP");
        notification.setGroupSummary(true);
        nm.cancel(instance.hashCode());
        nm.notify(instance.hashCode(), notification.build());
    }

    public static void showWearAlarmNotification(Context context, AlarmInstance instance) {
        NotificationCompat.Builder notification = getAlarmNotification(context, instance);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        Bitmap b = Utils.getCurrentHourWearImage(context);
        if (b != null) {
            NotificationCompat.WearableExtender wearableExtender =
                    new NotificationCompat.WearableExtender()
                            .setHintHideIcon(true)
                            .setBackground(b);
            notification.extend(wearableExtender);
        }
        if (Utils.isNotificationVibrate(context)) {
            notification.setVibrate(new long[]{0, 100, 50, 100});
        }

        // see http://stackoverflow.com/questions/24631932/android-wear-notification-is-not-displayed-if-flag-no-clear-is-used/24916387#24916387
        notification.setGroup("GROUP");
        notification.setGroupSummary(false);
        nm.cancel(instance.hashCode());
        nm.notify(instance.hashCode(), notification.build());
    }

    public static void clearNotification(Context context, AlarmInstance instance) {
        LogUtils.v("Clearing notifications for alarm instance: " + instance.mId);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.cancel(instance.hashCode());
    }

    private static Intent createViewAlarmIntent(Context context, AlarmInstance instance) {
        long alarmId = instance.mAlarmId == null ? Alarm.INVALID_ID : instance.mAlarmId;
        Intent viewAlarmIntent = Alarm.createIntent(context, DeskClock.class, alarmId);
        viewAlarmIntent.putExtra(DeskClock.SELECT_TAB_INTENT_EXTRA, DeskClock.ALARM_TAB_INDEX);
        viewAlarmIntent.putExtra(AlarmClockFragment.SCROLL_TO_ALARM_INTENT_EXTRA, alarmId);
        viewAlarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return viewAlarmIntent;
    }

    public static void showPreAlarmDismissNotification(Context context, AlarmInstance instance) {
        LogUtils.v("Displaying pre-alarm dismiss notification for alarm instance: " + instance.mId);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        Resources resources = context.getResources();
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                Channel.DEFAULT_NOTIFICATION)
                .setContentTitle(resources.getString(org.omnirom.deskclock.R.string.alarm_alert_prealarm_dismiss_title))
                .setContentText(AlarmUtils.getAlarmText(context, instance))
                .setSmallIcon(org.omnirom.deskclock.R.drawable.ic_notify_alarm)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .setColor(resources.getColor(org.omnirom.deskclock.R.color.primary));

        // Setup up dismiss action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(context,
                instance.hashCode(), dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notification.addAction(org.omnirom.deskclock.R.drawable.ic_notify_alarm_off,
                resources.getString(org.omnirom.deskclock.R.string.alarm_alert_dismiss_text),
                dismissPendingIntent);

        // Setup content action if instance is owned by alarm
        long alarmId = instance.mAlarmId == null ? Alarm.INVALID_ID : instance.mAlarmId;
        Intent viewAlarmIntent = Alarm.createIntent(context, DeskClock.class, alarmId);
        viewAlarmIntent.putExtra(DeskClock.SELECT_TAB_INTENT_EXTRA, DeskClock.ALARM_TAB_INDEX);
        viewAlarmIntent.putExtra(AlarmClockFragment.SCROLL_TO_ALARM_INTENT_EXTRA, alarmId);
        viewAlarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notification.setContentIntent(PendingIntent.getActivity(context, instance.hashCode(),
                viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        NotificationChannelManager.applyChannel(notification, context, Channel.DEFAULT_NOTIFICATION);
        nm.cancel(instance.hashCode());
        nm.notify(instance.hashCode(), notification.build());
    }
}