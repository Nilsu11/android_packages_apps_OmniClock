/*
 *  Copyright (C) 2016 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.deskclock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.omnirom.deskclock.alarms.AlarmConstants;
import org.omnirom.deskclock.alarms.AlarmService;
import org.omnirom.deskclock.alarms.AlarmStateManager;
import org.omnirom.deskclock.provider.AlarmInstance;
import org.omnirom.deskclock.provider.ClockContract;

public class AlarmPluginFactory {

    public static Intent getAlarmServiceIntent(Context context, AlarmInstance instance, boolean preAlarm) {
        if (Utils.isSpotifyAlarm(instance, preAlarm) && Utils.isSpotifyPluginInstalled(context)) {
            String packageName = Utils.getSpotifyPluginPackageName(context);
            if (packageName != null) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(packageName,
                        "com.maxwen.deskclock.alarms.SpotifyAlarmService"));
                intent.setData(instance.getUri(instance.mId));

                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                String speed = prefs.getString(SettingsActivity.KEY_VOLUME_INCREASE_SPEED, "5");
                intent.putExtra(AlarmConstants.DATA_ALARM_VOLUME_INCREASE_SPEED, speed);
                String stream = prefs.getString(SettingsActivity.KEY_AUDIO_STREAM, "1");
                intent.putExtra(AlarmConstants.DATA_ALARM_AUDIO_STREAM, stream);
                intent.putExtra(AlarmConstants.DATA_ALARM_NOTIF_WEARABLE, Utils.showWearNotification(context));
                intent.putExtra(AlarmConstants.DATA_ALARM_CAN_SNOOZE, AlarmStateManager.canSnooze(context));
                intent.putExtra(AlarmConstants.DATA_ALARM_NOTIF_VIBRATE, Utils.isNotificationVibrate(context));
                intent.putExtra(AlarmConstants.DATA_OMNICLOCK_AUTHORITY, ClockContract.AUTHORITY);
                return intent;
            }
        }
        return AlarmInstance.createIntent(context, AlarmService.class, instance.mId);
    }
}
