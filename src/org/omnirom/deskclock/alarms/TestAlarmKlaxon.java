/*
 *  Copyright (C) 2015-2016 The OmniROM Project
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

package org.omnirom.deskclock.alarms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;

import org.omnirom.deskclock.LogUtils;
import org.omnirom.deskclock.SettingsActivity;
import org.omnirom.deskclock.provider.Alarm;
import org.omnirom.deskclock.provider.AlarmInstance;
import org.omnirom.deskclock.Utils;

/**
 * for testing alarm tones
 */
public class TestAlarmKlaxon {

    private static AudioManager sAudioManager = null;
    private static MediaPlayer sMediaPlayer = null;
    private static boolean sTestStarted = false;
    private static int sSavedVolume;
    private static int sMaxVolume;
    private static List<Uri> mSongs = new ArrayList<Uri>();
    private static Uri mCurrentTone;
    private static int sCurrentIndex;
    private static boolean sRandomPlayback;
    private static ErrorHandler sErrorHandler;
    private static boolean sError;
    private static boolean sRandomMusicMode;
    private static boolean sLocalMediaMode;
    private static boolean sStreamMediaMode;
    private static Context sContext;

    public interface ErrorHandler {
        public void onError(String msg);

        public void onInfo(String msg);

        public void startProgress();

        public void stopProgress();

        public void onTrackChanged(final Uri track);
    }

    private static BroadcastReceiver sNetworkListener = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (sTestStarted && sStreamMediaMode) {
                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                    if (!isNetworkConnectivity(context)) {
                        if (sMediaPlayer != null) {
                            sMediaPlayer.stop();
                            sMediaPlayer.reset();
                        }
                        sError = true;
                        sErrorHandler.onError("No network");
                    }
                }
            }
        }
    };

    private static int getAudioStream(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String stream = prefs.getString(SettingsActivity.KEY_AUDIO_STREAM, "1");
        int streamInt = Integer.decode(stream).intValue();
        return streamInt == 0 ? AudioManager.STREAM_MUSIC : AudioManager.STREAM_ALARM;
    }

    public static void test(final Context context, Alarm instance, boolean preAlarm, ErrorHandler errorHandler) {
        sError = false;
        sErrorHandler = errorHandler;

        sContext = context;
        sAudioManager = (AudioManager) sContext
                .getSystemService(Context.AUDIO_SERVICE);

        // save current value
        sSavedVolume = sAudioManager.getStreamVolume(getAudioStream(context));
        if (preAlarm) {
            sMaxVolume = calcNormalizedVolume(context, instance.preAlarmVolume);
        } else {
            sMaxVolume = calcNormalizedVolume(context, instance.alarmVolume);
        }
        sRandomPlayback = instance.getRandomMode(preAlarm);
        if (sMaxVolume == -1) {
            // calc from current alarm volume
            sMaxVolume = calcNormalizedVolumeFromCurrentAlarm(context);
        }

        sAudioManager.setStreamVolume(getAudioStream(context), sMaxVolume, 0);

        Uri alarmNoise = null;
        sRandomMusicMode = false;
        sLocalMediaMode = false;
        sStreamMediaMode = false;
        sCurrentIndex = 0;

        if (preAlarm) {
            alarmNoise = instance.preAlarmAlert;
        } else {
            alarmNoise = instance.alert;
        }
        if (alarmNoise != null) {
            if (Utils.isRandomUri(alarmNoise.toString())) {
                sRandomMusicMode = true;
                sRandomPlayback = true;
                mSongs = Utils.getRandomMusicFiles(context, 50);
                if (mSongs.size() != 0) {
                    alarmNoise = mSongs.get(0);
                    sErrorHandler.onTrackChanged(alarmNoise);
                } else {
                    sError = true;
                    sErrorHandler.onError("No music files");
                    return;
                }
            } else if (Utils.isLocalPlaylistType(alarmNoise.toString())) {
                // can fail if no external storage permissions
                try {
                    sLocalMediaMode = true;
                    mSongs.clear();
                    if (Utils.isLocalAlbumUri(alarmNoise.toString())) {
                        collectAlbumSongs(context, alarmNoise);
                    }
                    if (Utils.isLocalArtistUri(alarmNoise.toString())) {
                        collectArtistSongs(context, alarmNoise);
                    }
                    if (Utils.isStorageUri(alarmNoise.toString())) {
                        if (Utils.isStreamM3UFile(alarmNoise.toString())) {
                            if (isNetworkConnectivity(context)) {
                                sStreamMediaMode = true;
                                collectM3UFiles(alarmNoise);
                            } else {
                                sError = true;
                                sErrorHandler.onError("No network");
                                return;
                            }
                        } else {
                            collectFiles(context, alarmNoise);
                        }
                    }
                    if (Utils.isLocalPlaylistUri(alarmNoise.toString())) {
                        collectPlaylistSongs(context, alarmNoise);
                    }
                    if (mSongs.size() != 0) {
                        sErrorHandler.onInfo("Scanned files: " + mSongs.size());
                        alarmNoise = mSongs.get(0);
                        sErrorHandler.onTrackChanged(alarmNoise);
                    } else {
                        sError = true;
                        sErrorHandler.onError("Empty folder");
                        return;
                    }
                } catch (Exception ex) {
                    sError = true;
                    sErrorHandler.onError("Error accessing folder");
                    return;
                }
            }
        }

        if (alarmNoise == null || AlarmInstance.NO_RINGTONE_URI.equals(alarmNoise)) {
            sError = true;
            sErrorHandler.onError("Error playing alarm sound");
            return;
        }

        if (alarmNoise != null) {
            sTestStarted = true;
            playTestAlarm(context, instance, alarmNoise);

            if (sStreamMediaMode) {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                context.registerReceiver(sNetworkListener, intentFilter);
            }
        }
    }

    public static void stopTest(Alarm instance) {
        if (!sTestStarted) {
            return;
        }

        // reset to default from before
        sAudioManager.setStreamVolume(getAudioStream(sContext),
                sSavedVolume, 0);

        if (sMediaPlayer != null) {
            sMediaPlayer.stop();
            sMediaPlayer.reset();
            sMediaPlayer.release();
            sMediaPlayer = null;
            sAudioManager.abandonAudioFocus(null);
            sAudioManager = null;
        }
        try {
            sContext.unregisterReceiver(sNetworkListener);
        } catch (Exception e) {
        }
        sTestStarted = false;
    }

    private static void playTestAlarm(final Context context, final Alarm instance, final Uri alarmNoise) {
        if (sMediaPlayer != null) {
            sMediaPlayer.reset();
        }
        sMediaPlayer = new MediaPlayer();
        sMediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                sError = true;
                sErrorHandler.onError("Error playing alarm sound");
                return true;
            }
        });
        if (sLocalMediaMode || sRandomMusicMode) {
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    nextSong(context, instance);
                }
            });
        }

        try {
            mCurrentTone = alarmNoise;
            sMediaPlayer.setDataSource(context, alarmNoise);
            LogUtils.v("Using audio stream " + (getAudioStream(context) == AudioManager.STREAM_MUSIC ? "Music" : "Alarm"));

            sMediaPlayer.setAudioStreamType(getAudioStream(context));
            if (!sRandomMusicMode && !sLocalMediaMode) {
                sMediaPlayer.setLooping(true);
            }
            sAudioManager.requestAudioFocus(null, getAudioStream(context),
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            sMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer player) {
                    sMediaPlayer.start();
                }
            });
            sMediaPlayer.prepareAsync();
        } catch (Exception ex) {
            sError = true;
            sErrorHandler.onError("Error playing alarm sound");
        }
    }

    /**
     * if we use the current alarm volume to play on the music stream
     * we must scale the alarm volume inside the music volume range
     */
    private static int calcNormalizedVolumeFromCurrentAlarm(Context context) {
        int alarmVolume = sAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        if (alarmVolume == 0) {
            return 0;
        }
        final int audioStream = getAudioStream(context);
        if (audioStream == AudioManager.STREAM_MUSIC) {
            int maxMusicVolume = sAudioManager.getStreamMaxVolume(audioStream);
            int maxAlarmVolume = sAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            return (int) (((float) alarmVolume / (float) maxAlarmVolume) * (float) maxMusicVolume);
        }
        return alarmVolume;
    }

    /**
     * volume is stored based on music volume steps
     * so if we use the alarm stream to play we must scale the
     * volume inside the alarm volume range
     */
    private static int calcNormalizedVolume(Context context, int alarmVolume) {
        if (alarmVolume == -1) {
            // use system alarm volume calculated later
            return alarmVolume;
        }
        final int audioStream = getAudioStream(context);
        if (audioStream == AudioManager.STREAM_ALARM) {
            int maxMusicVolume = sAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int maxAlarmVolume = sAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            return (int) (((float) alarmVolume / (float) maxMusicVolume) * (float) maxAlarmVolume) + 1;
        }
        return alarmVolume;
    }

    private static void collectFiles(Context context, Uri folderUri) {
        mSongs.clear();
        sErrorHandler.startProgress();
        File folder = new File(folderUri.getPath());
        if (folder.exists() && folder.isDirectory()) {
            for (final File fileEntry : folder.listFiles()) {
                if (!fileEntry.isDirectory()) {
                    if (Utils.isValidAudioFile(fileEntry.getName())) {
                        mSongs.add(Uri.fromFile(fileEntry));
                    }
                } else {
                    collectSub(context, fileEntry);
                }
            }
            if (sRandomPlayback) {
                Collections.shuffle(mSongs);
            } else {
                Collections.sort(mSongs);
            }
        }
        sErrorHandler.stopProgress();
    }

    private static void collectAlbumSongs(Context context, Uri albumUri) {
        mSongs.clear();
        sErrorHandler.startProgress();
        mSongs = Utils.getAlbumSongs(context, albumUri);
        if (sRandomPlayback) {
            Collections.shuffle(mSongs);
        }
        sErrorHandler.stopProgress();
    }

    private static void collectArtistSongs(Context context, Uri artistUri) {
        mSongs.clear();
        sErrorHandler.startProgress();
        mSongs = Utils.getArtistSongs(context, artistUri);
        if (sRandomPlayback) {
            Collections.shuffle(mSongs);
        }
        sErrorHandler.stopProgress();
    }

    private static void collectPlaylistSongs(Context context, Uri playlistUri) {
        mSongs.clear();
        sErrorHandler.startProgress();
        mSongs = Utils.getPlaylistSongs(context, playlistUri);
        if (sRandomPlayback) {
            Collections.shuffle(mSongs);
        }
        sErrorHandler.stopProgress();
    }

    private static void collectSub(Context context, File folder) {
        if (folder.exists() && folder.isDirectory()) {
            for (final File fileEntry : folder.listFiles()) {
                if (!fileEntry.isDirectory()) {
                    if (Utils.isValidAudioFile(fileEntry.getName())) {
                        mSongs.add(Uri.fromFile(fileEntry));
                    }
                } else {
                    collectSub(context, fileEntry);
                }
            }
        }
    }

    private static void nextSong(Context context, Alarm instance) {
        if (sError) {
            return;
        }
        if (mSongs.size() == 0) {
            // some thing bad happend to our play list
            sError = true;
            sErrorHandler.onError("Empty folder");
            return;
        }
        sCurrentIndex++;
        // restart if on end
        if (sCurrentIndex >= mSongs.size()) {
            if (sRandomPlayback) {
                Collections.shuffle(mSongs);
            }
            sCurrentIndex = 0;
        }
        Uri song = mSongs.get(sCurrentIndex);
        if (sLocalMediaMode || sRandomMusicMode) {
            sErrorHandler.onTrackChanged(song);
        }
        playTestAlarm(context, instance, song);
    }

    private static void collectM3UFiles(Uri m3UFileUri) {
        List<Uri> files = Utils.parseM3UPlaylist(m3UFileUri.toString());
        mSongs.clear();
        sErrorHandler.startProgress();

        for (final Uri fileEntry : files) {
            if (fileEntry.getScheme() == "file") {
                File f = new File(fileEntry.getPath());
                if (f.exists()) {
                    if (Utils.isValidAudioFile(f.getName())) {
                        mSongs.add(fileEntry);
                    }
                }
            } else {
                mSongs.add(fileEntry);
            }
        }

        if (sRandomPlayback) {
            Collections.shuffle(mSongs);
        } else {
            Collections.sort(mSongs);
        }

        sErrorHandler.stopProgress();
    }

    private static boolean isNetworkConnectivity(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnected()) {
            return true;
        } else {
            return false;
        }
    }
}
