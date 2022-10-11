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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;

import java.io.IOException;

/**
 * Play the timer's ringtone. Will continue playing the same alarm until service is stopped.
 */
public class TimerRingService extends Service implements AudioManager.OnAudioFocusChangeListener {
    private static final long[] sVibratePattern = new long[] { 500, 500 };
    private static final int INCREASING_VOLUME = 1001;
    private static final int INCREASING_VOLUME_START = 1;
    private static final int INCREASING_VOLUME_DELTA = 1;

    private static boolean sPlaying = false;
    private MediaPlayer mMediaPlayer;
    private static AudioManager sAudioManager;
    private static boolean sIncreasingVolume;
    private static int sCurrentVolume = INCREASING_VOLUME_START;
    private static int sSavedVolume;
    private static int sMaxVolume;
    private static long sVolumeIncreaseSpeed;

    private static Handler sHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case INCREASING_VOLUME:
                    if (sPlaying) {
                        sCurrentVolume += INCREASING_VOLUME_DELTA;
                        if (sCurrentVolume <= sMaxVolume) {
                            LogUtils.v("Increasing alarm volume to " + sCurrentVolume);
                            sAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, sCurrentVolume, 0);
                            sHandler.sendEmptyMessageDelayed(INCREASING_VOLUME,
                                    sVolumeIncreaseSpeed);
                        }
                    }
                    break;
            }
        }
    };
    @Override
    public void onCreate() {
        sAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        AlarmAlertWakeLock.acquireScreenCpuWakeLock(this);
    }

    @Override
    public void onDestroy() {
        stop();
        // Stop listening for incoming calls.
        AlarmAlertWakeLock.releaseCpuLock();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // No intent, tell the system not to restart us.
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        play();

        return START_STICKY;
    }

    // Volume suggested by media team for in-call alarms.
    private static final float IN_CALL_VOLUME = 0.125f;

    private void play() {

        if (sPlaying) {
            return;
        }

        LogUtils.v("TimerRingService.play()");

        // TODO: Reuse mMediaPlayer instead of creating a new one and/or use
        // RingtoneManager.
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                LogUtils.e("Error occurred while playing audio.");
                mp.stop();
                mp.release();
                mMediaPlayer = null;
                return true;
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        sMaxVolume = sSavedVolume = sAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        sIncreasingVolume = prefs.getBoolean(SettingsActivity.KEY_TIMER_ALARM_INCREASE_VOLUME, false);
        sVolumeIncreaseSpeed = getVolumeChangeDelay(this);
        LogUtils.v("Volume increase interval " + sVolumeIncreaseSpeed);

        try {
            final boolean vibrate = prefs.getBoolean(SettingsActivity.KEY_TIMER_ALARM_VIBRATE, false);
            if (vibrate) {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(sVibratePattern, 0);
            }
            setTimerAlarm();
            startAlarm(mMediaPlayer);
        } catch (Exception ex) {
            LogUtils.v("Using the fallback ringtone");
            // The alert may be on the sd card which could be busy right
            // now. Use the fallback ringtone.
            try {
                // Must reset the media player to clear the error state.
                mMediaPlayer.reset();
                setDataSourceFromResource(getResources(), mMediaPlayer,
                        org.omnirom.deskclock.R.raw.fallbackring);
                startAlarm(mMediaPlayer);
            } catch (Exception ex2) {
                // At this point we just don't play anything.
                LogUtils.e("Failed to play fallback ringtone", ex2);
            }
        }

        sPlaying = true;
    }

    // Do the common stuff when starting the alarm.
    private void startAlarm(MediaPlayer player)
            throws java.io.IOException, IllegalArgumentException,
                   IllegalStateException {
        // do not play alarms if stream volume is 0
        // (typically because ringer mode is silent).
        if (sAudioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
            if (sIncreasingVolume) {
                sCurrentVolume = INCREASING_VOLUME_START;
                sAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                        sCurrentVolume, 0);
                LogUtils.v("Starting alarm volume " + sCurrentVolume
                        + " max volume " + sMaxVolume);
                if (sCurrentVolume < sMaxVolume) {
                    sHandler.sendEmptyMessageDelayed(INCREASING_VOLUME,
                            sVolumeIncreaseSpeed);
                }
            } else {
                LogUtils.v("Alarm volume " + sCurrentVolume);
            }
            player.setAudioStreamType(AudioManager.STREAM_ALARM);
            player.setLooping(true);
            player.prepare();
            sAudioManager.requestAudioFocus(
                    this, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            player.start();
        }
    }

    private void setDataSourceFromResource(Resources resources,
            MediaPlayer player, int res) throws java.io.IOException {
        AssetFileDescriptor afd = resources.openRawResourceFd(res);
        if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
        }
    }

    /**
     * Stops timer audio
     */
    public void stop() {
        LogUtils.v("TimerRingService.stop()");
        if (sPlaying) {
            sPlaying = false;

            // Stop audio playing
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();

                sAudioManager.abandonAudioFocus(this);
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            if (sIncreasingVolume) {
                sHandler.removeMessages(INCREASING_VOLUME);
                // reset to default from before
                sAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                        sSavedVolume, 0);
            }
            ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE))
                    .cancel();
        }
    }


    @Override
    public void onAudioFocusChange(int focusChange) {
        // Do nothing
    }

    private boolean setTimerAlarm() throws IOException  {
        Uri alarmNoise = null;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean customAlarmNoise = prefs.getBoolean(SettingsActivity.KEY_TIMER_ALARM_CUSTOM, false);
        if (customAlarmNoise) {
            alarmNoise = RingtoneManager.getActualDefaultRingtoneUri(this,
                    RingtoneManager.TYPE_ALARM);
            String alarmNoiseStr = prefs.getString(SettingsActivity.KEY_TIMER_ALARM, null);
            if (alarmNoiseStr != null) {
                if (alarmNoiseStr.length() == 0) {
                    // its the none alarm string
                    return false;
                }
                alarmNoise = Uri.parse(alarmNoiseStr);
            }
        }
        if (alarmNoise == null) {
            setDataSourceFromResource(this, mMediaPlayer, R.raw.timer_expire);
        } else {
            mMediaPlayer.setDataSource(this, alarmNoise);
        }
        return true;
    }

    private static void setDataSourceFromResource(Context context,
                                                  MediaPlayer player, int res) throws IOException {
        AssetFileDescriptor afd = context.getResources().openRawResourceFd(res);
        if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
        }
    }

    private static long getVolumeChangeDelay(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String speed = prefs.getString(SettingsActivity.KEY_TIMER_ALARM_INCREASE_VOLUME_SPEED, "5");
        int speedInt = Integer.decode(speed).intValue();
        return speedInt * 1000;
    }
}
