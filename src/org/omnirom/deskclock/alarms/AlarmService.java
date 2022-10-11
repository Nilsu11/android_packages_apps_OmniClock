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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.IBinder;

import org.omnirom.deskclock.AlarmAlertWakeLock;
import org.omnirom.deskclock.AlarmUtils;
import org.omnirom.deskclock.LogUtils;
import org.omnirom.deskclock.Utils;
import org.omnirom.deskclock.provider.AlarmInstance;

/**
 * This service is in charge of starting/stoping the alarm. It will bring up and manage the
 * {@link AlarmKlaxon}.
 */
public class AlarmService extends Service {
    private AlarmInstance mCurrentAlarm = null;

    private void startAlarm(AlarmInstance instance) {
        LogUtils.v("AlarmService.start with instance: " + instance.mId);
        if (mCurrentAlarm != null) {
            LogUtils.v("AlarmService.start set missed state to current alarm: " + mCurrentAlarm.mId);
            AlarmStateManager.setMissedState(this, mCurrentAlarm);
            stopCurrentAlarm();
        }

        AlarmAlertWakeLock.acquireCpuWakeLock(this);
        mCurrentAlarm = instance;

        AlarmKlaxon.start(this, mCurrentAlarm);
        sendBroadcast(new Intent(AlarmConstants.ALARM_ALERT_ACTION));
    }

    private void stopCurrentAlarm() {
        if (mCurrentAlarm == null) {
            LogUtils.v("There is no current alarm to stop");
            return;
        }

        LogUtils.v("AlarmService.stop with instance: " + mCurrentAlarm.mId);
        AlarmKlaxon.stop(this);
        sendBroadcast(new Intent(AlarmConstants.ALARM_DONE_ACTION));
        mCurrentAlarm = null;
        AlarmAlertWakeLock.releaseCpuLock();
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.v("AlarmService.onStartCommand() with intent: " + intent.toString());

        long instanceId = AlarmInstance.getId(intent.getData());
        if (AlarmConstants.START_ALARM_ACTION.equals(intent.getAction())) {
            ContentResolver cr = this.getContentResolver();
            AlarmInstance instance = AlarmInstance.getInstance(cr, instanceId);
            if (instance == null) {
                LogUtils.e("No instance found to start alarm: " + instanceId);
                if (mCurrentAlarm != null) {
                    // Only release lock if we are not firing alarm
                    AlarmAlertWakeLock.releaseCpuLock();
                }
                return Service.START_NOT_STICKY;
            } else if (mCurrentAlarm != null && mCurrentAlarm.mId == instanceId) {
                LogUtils.e("Alarm already started for instance: " + instanceId);
                return Service.START_NOT_STICKY;
            }
            // must create an extra notification for wear cause the
            // service foreground notification is not shown on wear
            if (Utils.showWearNotification(this)) {
                AlarmNotifications.showWearAlarmNotification(this, instance);
            }
            // MUST be different then the other notifications
            // else if killing service is after state change it
            // will take down our state notification e.g. snoozed
            startForeground(instance.hashCode() + 1, showAlarmNotification(this, instance));
            startAlarm(instance);
        } else if(AlarmConstants.STOP_ALARM_ACTION.equals(intent.getAction())) {
            if (mCurrentAlarm != null && mCurrentAlarm.mId != instanceId) {
                LogUtils.e("Can't stop alarm for instance: " + instanceId +
                        " because current alarm is: " + mCurrentAlarm.mId);
                return Service.START_NOT_STICKY;
            }
            boolean fromDismiss = intent.getBooleanExtra(AlarmConstants.DATA_ALARM_EXTRA_DISMISSED, false);
            stopCurrentAlarm();
            // service no longer needed - else we are in transition from pre alarm to main alarm
            // and must never stop service
            if (fromDismiss) {
                stopSelf();
            }
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        LogUtils.v("AlarmService.onDestroy() called");
        super.onDestroy();
        if (mCurrentAlarm != null) {
            stopCurrentAlarm();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification showAlarmNotification(Context context, AlarmInstance instance) {
        if (instance.mAlarmState == AlarmInstance.PRE_ALARM_STATE) {
            LogUtils.v("Displaying pre-alarm notification for alarm instance: " + instance.mId);
        } else {
            LogUtils.v("Displaying alarm notification for alarm instance: " + instance.mId);
        }

        // Close dialogs and window shade, so this will display
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        String alarmName = null;
        if (instance.mAlarmState == AlarmInstance.PRE_ALARM_STATE) {
            alarmName = instance.getPreAlarmRingtoneName();
        } else {
            alarmName = instance.getRingtoneName();
        }
        Resources resources = context.getResources();
        Notification.Builder notification = new Notification.Builder(context)
                .setContentTitle(AlarmUtils.getAlarmTitle(context, instance) + " " + alarmName)
                .setContentText(AlarmUtils.getFormattedTime(context, instance.getAlarmTime()))
                .setSmallIcon(org.omnirom.deskclock.R.drawable.ic_notify_alarm)
                .setAutoCancel(false)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .setColor(resources.getColor(org.omnirom.deskclock.R.color.primary));

        notification.setGroup("GROUP");
        notification.setGroupSummary(true);

        if (Utils.isNotificationVibrate(context)) {
            notification.setVibrate(new long[] {0, 100, 50, 100} );
        }
        // Setup Snooze Action
        if (AlarmStateManager.canSnooze(context)) {
            Intent snoozeIntent = AlarmStateManager.createStateChangeIntent(context,
                    AlarmStateManager.ALARM_SNOOZE_TAG, instance, AlarmInstance.SNOOZE_STATE);
            PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(context, instance.hashCode(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            notification.addAction(org.omnirom.deskclock.R.drawable.ic_notify_snooze,
                    resources.getString(org.omnirom.deskclock.R.string.alarm_alert_snooze_text), snoozePendingIntent);
        }

        // Setup Dismiss Action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(context,
                instance.hashCode(), dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notification.addAction(org.omnirom.deskclock.R.drawable.ic_notify_alarm_off,
                resources.getString(org.omnirom.deskclock.R.string.alarm_alert_dismiss_text),
                dismissPendingIntent);

        // Setup Content Action
        Intent contentIntent = AlarmInstance.createIntent(context, AlarmActivity.class,
                instance.mId);
        notification.setContentIntent(PendingIntent.getActivity(context,
                instance.hashCode(), contentIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // Setup fullscreen intent
        Intent fullScreenIntent = AlarmInstance.createIntent(context, AlarmActivity.class,
                instance.mId);
        // set action, so we can be different then content pending intent
        fullScreenIntent.setAction("fullscreen_activity");
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        notification.setFullScreenIntent(PendingIntent.getActivity(context,
                instance.hashCode(), fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT), true);

        return notification.build();
    }
}
