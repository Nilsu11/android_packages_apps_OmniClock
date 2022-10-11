package org.omnirom.deskclock.alarms;

/**
 * Created by maxl on 9/1/16.
 */
public class AlarmConstants {
    // A public action send by AlarmService when the alarm has started.
    public static final String ALARM_ALERT_ACTION = "org.omnirom.deskclock.ALARM_ALERT";

    // A public action sent by AlarmService when the alarm has stopped for any reason.
    public static final String ALARM_DONE_ACTION = "org.omnirom.deskclock.ALARM_DONE";

    // contains metadata for currently played media - if available
    public static final String ALARM_MEDIA_ACTION ="org.omnirom.deskclock.ALARM_MEDIA";

    // Private action used to start an alarm with this service.
    public static final String START_ALARM_ACTION = "START_ALARM";

    // Private action used to stop an alarm with this service.
    public static final String STOP_ALARM_ACTION = "STOP_ALARM";

    public static final String DATA_ALARM_VOLUME_INCREASE_SPEED = "alarm_volume_increase_speed";
    public static final String DATA_ALARM_AUDIO_STREAM = "alarm_audio_stream";
    public static final String DATA_ALARM_CAN_SNOOZE = "can_snooze";
    public static final String DATA_ALARM_NOTIF_WEARABLE = "notif_wearable";
    public static final String DATA_ALARM_NOTIF_VIBRATE = "notif_vibrate";

    public static final String DATA_ALARM_EXTRA = "alarm_data";
    public static final String DATA_ALARM_EXTRA_URI = "alarm_uri";
    public static final String DATA_ALARM_EXTRA_NAME = "alarm_name";
    public static final String DATA_ALARM_EXTRA_RANDOM = "alarm_random";
    public static final String DATA_ALARM_EXTRA_VOLUME = "alarm_volume";
    public static final String DATA_ALARM_EXTRA_DISMISSED = "alarm_dismissed";
    public static final String DATA_COLOR_THEME_LIGHT = "color_theme_light";
    public static final String DATA_OMNICLOCK_PARENT = "omniclock_parent";
    public static final String DATA_OMNICLOCK_AUTHORITY = "omniclock_alarm_authority";
    public static final String DATA_ALARM_EXTRA_TYPE = "alarm_type";
    public static final String DATA_BROWSE_EXTRA_FALLBACK = "alarm_type_fallback";
    public static final String DATA_COLOR_THEME_ID = "color_theme_id";
}
