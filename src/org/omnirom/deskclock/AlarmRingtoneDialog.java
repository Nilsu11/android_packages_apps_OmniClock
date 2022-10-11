/*
 *  Copyright (C) 2014 The OmniROM Project
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.LinearLayout;

import org.omnirom.deskclock.alarms.AlarmConstants;
import org.omnirom.deskclock.provider.Alarm;

public class AlarmRingtoneDialog extends DialogFragment implements
        DialogInterface.OnClickListener,
        SeekBar.OnSeekBarChangeListener,
        StorageChooserDialog.ChosenStorageListener {

    private static final int REQUEST_CODE_SPOTIFY = 1;
    private static final int REQUEST_CODE_BROWSE = 2;

    private static final int ALARM_TYPE_BROWSE = 0;
    private static final int ALARM_TYPE_FOLDER = 1;
    private static final int ALARM_TYPE_RANDOM = 2;
    private static final int ALARM_TYPE_SPOTIFY = 3;

    private static final String KEY_MEDIA_TYPE = "mediaType";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_INCREASING_VOLUME = "increasingVolume";
    private static final String KEY_RANDOM_MODE = "randomMode";
    private static final String KEY_RINGTONE = "ringtone";
    private static final String KEY_PREALARM = "preAlarm";
    private static final String KEY_ALARM = "alarm";
    private static final String KEY_TAG = "tag";
    private static final String KEY_PRE_ALARM_TIME = "preAlarmTime";
    private static final String KEY_RINGTONE_NAME = "ringtoneName";
    private static final String KEY_RINGTONE_TYPE = "ringtoneType";

    private static final int PERMISSIONS_REQUEST_EXTERNAL_STORAGE = 0;

    private String mTag;
    private Alarm mAlarm;
    private boolean mPreAlarm;

    private TextView ringtone;
    private TextView mRingtoneLabel;
    private Spinner mMediaTypeSelect;
    private int mCurrentMediaType;
    private List<Uri> mAlarms;
    private List<Uri> mRingtones;
    private Uri mRingtone;
    private int mVolume = -1;
    private boolean mIncreasingVolumeValue;
    private boolean mRandomModeValue;
    private CheckBox mEnabledCheckbox;
    private SeekBar mMaxVolumeSeekBar;
    private CheckBox mIncreasingVolume;
    private TextView mMinVolumeText;
    private TextView mMaxVolumeText;
    private CheckBox mRandomMode;
    private Button mOkButton;
    private Button mTestButton;
    private boolean mSpinnerInit;
    private Spinner mPreAlarmTimeSelect;
    private int mPreAlarmTime;
    private AudioManager mAudioManager;
    private LinearLayout mMaxVolumeContainer;
    private Runnable mRunAfter;
    private String mRingtoneName;
    private int mRingtoneImageId;
    private View mRingtoneView;
    private int mRingtoneType;

    public interface AlarmRingtoneDialogListener {
        void onFinishOk(Alarm alarm, boolean preAlarm);
    }

    public static AlarmRingtoneDialog newInstance(Alarm alarm, boolean preAlarm, String tag) {
        AlarmRingtoneDialog fragment = new AlarmRingtoneDialog();
        Bundle args = new Bundle();
        args.putParcelable(KEY_ALARM, alarm);
        args.putString(KEY_TAG, tag);
        args.putBoolean(KEY_PREALARM, preAlarm);
        fragment.setArguments(args);
        return fragment;
    }

    public AlarmRingtoneDialog() {
        super();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        mAlarm = bundle.getParcelable(KEY_ALARM);
        mTag = bundle.getString(KEY_TAG);
        mPreAlarm = bundle.getBoolean(KEY_PREALARM);

        if (savedInstanceState != null) {
            mCurrentMediaType = savedInstanceState.getInt(KEY_MEDIA_TYPE);
            mVolume = savedInstanceState.getInt(KEY_VOLUME);
            mIncreasingVolumeValue = savedInstanceState.getBoolean(KEY_INCREASING_VOLUME);
            mRandomModeValue = savedInstanceState.getBoolean(KEY_RANDOM_MODE);
            mRingtone = savedInstanceState.getParcelable(KEY_RINGTONE);
            mPreAlarmTime = savedInstanceState.getInt(KEY_PRE_ALARM_TIME);
            mRingtoneName = savedInstanceState.getString(KEY_RINGTONE_NAME);
            mRingtoneType  = savedInstanceState.getInt(KEY_RINGTONE_TYPE);
        } else {
            if (mPreAlarm) {
                mRingtone = mAlarm.preAlarmAlert;
                mVolume = mAlarm.preAlarmVolume;
                mPreAlarmTime = mAlarm.preAlarmTime;
                mRingtoneName = mAlarm.getPreAlarmRingtoneName();
                mRingtoneType = mAlarm.getPreAlarmRingtoneType();
            } else {
                mRingtone = mAlarm.alert;
                mVolume = mAlarm.alarmVolume;
                mRingtoneName = mAlarm.getRingtoneName();
                mRingtoneType = mAlarm.getRingtoneType();
            }
            mIncreasingVolumeValue = mAlarm.getIncreasingVolume(mPreAlarm);
            mRandomModeValue = mAlarm.getRandomMode(mPreAlarm);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
                Utils.getDialogThemeResourceId(getActivity()))
                .setTitle(mPreAlarm ? R.string.prealarm_title : R.string.alarm_title)
                .setPositiveButton(android.R.string.ok, this)
                .setNeutralButton(R.string.alarm_test_button, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setView(createDialogView());

        return builder.create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(KEY_MEDIA_TYPE, mCurrentMediaType);
        outState.putInt(KEY_VOLUME, mVolume);
        outState.putBoolean(KEY_INCREASING_VOLUME, mIncreasingVolumeValue);
        outState.putBoolean(KEY_RANDOM_MODE, mRandomModeValue);
        outState.putParcelable(KEY_RINGTONE, mRingtone);
        outState.putInt(KEY_PRE_ALARM_TIME, mPreAlarmTime);
        outState.putString(KEY_RINGTONE_NAME, mRingtoneName);
        outState.putInt(KEY_RINGTONE_TYPE, mRingtoneType);
    }

    @Override
    public void onStart() {
        super.onStart();

        AlertDialog d = (AlertDialog) getDialog();
        if (d != null) {
            Button testButton = (Button) d.getButton(Dialog.BUTTON_NEUTRAL);
            testButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // save into temp for testing
                            Alarm testAlarm = new Alarm();
                            saveChanges(testAlarm);
                            showAlarmTestDialog(testAlarm, mPreAlarm);
                        }
                    });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        closeAlarmTestDialog();
        closeStoragePicker();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            saveChanges(mAlarm);
            Fragment frag = getFragmentManager().findFragmentByTag(mTag);
            if (frag instanceof AlarmClockFragment) {
                ((AlarmClockFragment) frag).onFinishOk(mAlarm, mPreAlarm);
            }
        }
    }

    private View createDialogView() {
        final Activity activity = getActivity();
        final LayoutInflater inflater = (LayoutInflater) activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater
                .inflate(R.layout.dialog_alarm_ringtone, null);
        mAudioManager = (AudioManager) getActivity().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        final int maxVol = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        mMaxVolumeContainer = (LinearLayout) view.findViewById(R.id.alarm_volume_container);
        mMinVolumeText = (TextView) view.findViewById(R.id.alarm_volume_min);
        // must not be 0 if enabled
        mMinVolumeText.setText(String.valueOf(1));
        mMaxVolumeText = (TextView) view.findViewById(R.id.alarm_volume_max);
        mMaxVolumeText.setText(String.valueOf(maxVol));

        mRandomMode = (CheckBox) view.findViewById(R.id.random_mode_enable);
        mRandomMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRandomModeValue = mRandomMode.isChecked();
            }
        });
        mIncreasingVolume = (CheckBox) view.findViewById(R.id.increasing_volume_onoff);
        mIncreasingVolume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIncreasingVolumeValue = mIncreasingVolume.isChecked();
            }
        });
        mEnabledCheckbox = (CheckBox) view.findViewById(R.id.alarm_volume_enable);
        mMaxVolumeSeekBar = (SeekBar) view.findViewById(R.id.alarm_volume);
        mMaxVolumeSeekBar.setMax(maxVol - 1);
        mEnabledCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean value = mEnabledCheckbox.isChecked();
                mMaxVolumeContainer.setVisibility(value ? View.GONE : View.VISIBLE);
                if (value) {
                    mVolume = -1;
                } else {
                    // wemm enabling set default value to current alarm volume
                    mMaxVolumeSeekBar.setProgress(calcMusicVolumeFromCurrentAlarm());
                    mVolume = mMaxVolumeSeekBar.getProgress() + 1;
                }
            }
        });
        mMaxVolumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                if (fromUser) {
                    mVolume = progress + 1;
                }
            }
        });
        mMediaTypeSelect = (Spinner) view.findViewById(R.id.alarm_type_select);

        List<String> alarmTypes = new ArrayList<String>();
        alarmTypes.addAll(Arrays.asList(getResources().getStringArray(R.array.alarm_type_entries)));
        boolean addSpotify = false;
        if (Utils.isSpotifyPluginInstalled(getActivity())) {
            addSpotify = true;
        } else if (Utils.isSpotifyAlarm(mAlarm, mPreAlarm)) {
            addSpotify = true;
        }
        if (addSpotify) {
            final String spotifyMenu = getResources().getString(R.string.menu_item_spotify);
            alarmTypes.add(spotifyMenu);
        }
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter(getActivity(),
                R.layout.spinner_item, alarmTypes);
        mMediaTypeSelect.setAdapter(adapter);

        mMediaTypeSelect.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!mSpinnerInit) {
                    mSpinnerInit = true;
                    return;
                }
                mCurrentMediaType = position;
                if (mCurrentMediaType == ALARM_TYPE_RANDOM) {
                    setRandomSong();
                    mRingtoneView.setVisibility(View.INVISIBLE);
                } else {
                    mRingtoneView.setVisibility(View.VISIBLE);
                }
                updateRandomModeVisibility();
                updateButtons(mRingtone != null && !mRingtone.equals(Alarm.NO_RINGTONE_URI));
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        ringtone = (TextView) view.findViewById(R.id.choose_ringtone);
        ringtone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectRingtone(mCurrentMediaType);
            }
        });

        mRingtoneView = view.findViewById(R.id.choose_ringtone_view);
        mRingtoneLabel = (TextView) view.findViewById(R.id.choose_ringtone_title);

        if (mPreAlarm) {
            LinearLayout preAlarmSection = (LinearLayout) view.findViewById(R.id.pre_alarm_time_section);
            preAlarmSection.setVisibility(View.VISIBLE);

            mPreAlarmTimeSelect = (Spinner) view.findViewById(R.id.pre_alarm_time_select);
            adapter = ArrayAdapter.createFromResource(
                    getActivity(), R.array.pre_alarm_times_entries,
                    R.layout.spinner_item);
            mPreAlarmTimeSelect.setAdapter(adapter);

            mPreAlarmTimeSelect.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String[] preAlarmTimes = getResources().getStringArray(R.array.pre_alarm_times_values);
                    mPreAlarmTime = Integer.parseInt(preAlarmTimes[position]);
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });
            mPreAlarmTimeSelect.setSelection(getPreAlarmTimePosition());
        }

        cacheAlarmTones();
        cacheRingtones();
        initView();
        return view;
    }

    private void checkStoragePermissions(Runnable runAfter) {
        boolean needRequest = false;
        String[] permissions = {
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
        };
        ArrayList<String> permissionList = new ArrayList<String>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
                needRequest = true;
            }
        }

        if (needRequest) {
            mRunAfter = runAfter;
            int count = permissionList.size();
            if (count > 0) {
                String[] permissionArray = new String[count];
                for (int i = 0; i < count; i++) {
                    permissionArray[i] = permissionList.get(i);
                }
                FragmentCompat.requestPermissions(this, permissionArray, PERMISSIONS_REQUEST_EXTERNAL_STORAGE);
            }
        } else {
            runAfter.run();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mRunAfter != null) {
                        mRunAfter.run();
                    }
                }
                mRunAfter = null;
            }
            return;
        }
    }

    private void saveMediaUri(Intent intent) {
        Uri uri = intent.getData();
        mRingtone = uri;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_SPOTIFY: {
                    String uri = data.getStringExtra(AlarmConstants.DATA_ALARM_EXTRA_URI);
                    String name = data.getStringExtra(AlarmConstants.DATA_ALARM_EXTRA_NAME);
                    if (uri != null) {
                        mRingtone = Uri.parse(uri);
                        mRingtoneName = name;
                        mRingtoneType = -1;
                        updateRingtoneName();

                        // shuffle is depending on spotify uri type
                        updateRandomModeVisibility();
                    }
                    break;
                }
                case REQUEST_CODE_BROWSE: {
                    String uri = data.getStringExtra(AlarmConstants.DATA_ALARM_EXTRA_URI);
                    String name = data.getStringExtra(AlarmConstants.DATA_ALARM_EXTRA_NAME);
                    Integer type = data.getIntExtra(AlarmConstants.DATA_ALARM_EXTRA_TYPE, -1);
                    if (uri != null) {
                        mRingtone = Uri.parse(uri);
                        mRingtoneName = name;
                        mRingtoneType = type;
                        updateRingtoneName();
                        updateRandomModeVisibility();
                    }
                    break;
                }
                default:
                    LogUtils.w("Unhandled request code in onActivityResult: "
                            + requestCode);
            }
        }
    }

    private String getRingToneTitle(Uri uri) {
        Ringtone ringTone = RingtoneManager.getRingtone(getActivity(), uri);
        if (ringTone != null) {
            return ringTone.getTitle(getActivity());
        }
        return getResources().getString(R.string.fallback_ringtone);
    }

    private void cacheAlarmTones() {
        mAlarms = new ArrayList<Uri>();

        Cursor alarmsCursor = null;
        try {
            RingtoneManager ringtoneMgr = new RingtoneManager(getActivity()
                    .getApplicationContext());
            ringtoneMgr.setType(RingtoneManager.TYPE_ALARM);

            alarmsCursor = ringtoneMgr.getCursor();
            int alarmsCount = alarmsCursor.getCount();
            if (alarmsCount == 0 && !alarmsCursor.moveToFirst()) {
                return;
            }
            while (!alarmsCursor.isAfterLast() && alarmsCursor.moveToNext()) {
                int currentPosition = alarmsCursor.getPosition();
                mAlarms.add(ringtoneMgr.getRingtoneUri(currentPosition));
            }
        } finally {
            if (alarmsCursor != null) {
                alarmsCursor.close();
            }
        }
    }

    private void cacheRingtones() {
        mRingtones = new ArrayList<Uri>();

        Cursor alarmsCursor = null;
        try {
            RingtoneManager ringtoneMgr = new RingtoneManager(getActivity()
                    .getApplicationContext());
            ringtoneMgr.setType(RingtoneManager.TYPE_RINGTONE);

            alarmsCursor = ringtoneMgr.getCursor();
            int alarmsCount = alarmsCursor.getCount();
            if (alarmsCount == 0 && !alarmsCursor.moveToFirst()) {
                return;
            }

            while (!alarmsCursor.isAfterLast() && alarmsCursor.moveToNext()) {
                int currentPosition = alarmsCursor.getPosition();
                mRingtones.add(ringtoneMgr.getRingtoneUri(currentPosition));
            }
        } finally {
            if (alarmsCursor != null) {
                alarmsCursor.close();
            }
        }
    }

    private void setRingtoneName() {
        Uri ringtoneUri = mRingtone;
        boolean spotifyAlarm = false;
        boolean randomMusicAlarm = false;
        boolean localMediaAlarm = false;
        boolean localFolderAlarm = false;
        mCurrentMediaType = ALARM_TYPE_BROWSE;

        if (ringtoneUri != null) {
            if (!Alarm.NO_RINGTONE_URI.equals(ringtoneUri)) {
                if (Utils.isSpotifyAlarm(mAlarm, mPreAlarm)) {
                    spotifyAlarm = true;
                } else if (Utils.isRandomAlarm(mAlarm, mPreAlarm)) {
                    randomMusicAlarm = true;
                } else if (Utils.isLocalFolderAlarm(mAlarm, mPreAlarm)) {
                    localFolderAlarm = true;
                } else if (Utils.isLocalMediaAlarm(mAlarm, mPreAlarm)) {
                    localMediaAlarm = true;
                } else {
                    if (mAlarms.contains(ringtoneUri)) {
                        mCurrentMediaType = ALARM_TYPE_BROWSE;
                    } else if (mRingtones.contains(ringtoneUri)) {
                        mCurrentMediaType = ALARM_TYPE_BROWSE;
                    }
                }
            }
        }

        if (spotifyAlarm) {
            mCurrentMediaType = ALARM_TYPE_SPOTIFY;
        } else if (randomMusicAlarm) {
            mCurrentMediaType = ALARM_TYPE_RANDOM;
        } else if (localMediaAlarm) {
            mCurrentMediaType = ALARM_TYPE_BROWSE;
        } else if (localFolderAlarm) {
            mCurrentMediaType = ALARM_TYPE_FOLDER;
        }
        updateRingtoneName();
    }

    private void updateRingtoneName() {
        Uri ringtoneUri = mRingtone;
        String ringtoneTitle = "";
        mRingtoneImageId = R.drawable.ic_bell;
        mRingtoneView.setVisibility(View.VISIBLE);
        if (Alarm.NO_RINGTONE_URI.equals(ringtoneUri)) {
            // should never happen!
            ringtoneTitle = getResources().getString(R.string.silent_alarm_summary);
        } else {
            if (mCurrentMediaType == ALARM_TYPE_BROWSE) {
                boolean unknownAlarm = false;
                if (mRingtoneName == null) {
                    if (Utils.isStorageUri(ringtoneUri.toString())){
                        ringtoneTitle = ringtoneUri.getLastPathSegment();
                    } else {
                        ringtoneTitle = getRingToneTitle(ringtoneUri);
                        if (!Utils.isAlarmUriValid(getActivity(), ringtoneUri)) {
                            ringtoneTitle = getResources().getString(R.string.alarm_uri_unkown);
                            unknownAlarm = true;
                        }
                    }
                } else {
                    ringtoneTitle = mRingtoneName;
                    if (!Utils.isValidAlarm(getActivity(), ringtoneUri, mRingtoneType)) {
                        ringtoneTitle = ringtoneTitle + getResources().getString(R.string.alarm_uri_unkown);
                        unknownAlarm = true;
                    }
                }
                if (mAlarms.contains(ringtoneUri) || unknownAlarm) {
                    mRingtoneImageId = R.drawable.ic_alarm;
                } else if (mRingtones.contains(ringtoneUri)) {
                    mRingtoneImageId = R.drawable.ic_bell;
                } else {
                    mRingtoneImageId = Utils.resolveLocalUriImage(ringtoneUri.toString());
                }
            } else if (mCurrentMediaType == ALARM_TYPE_SPOTIFY) {
                ringtoneTitle = mRingtoneName != null ? mRingtoneName : mRingtone.toString();
                mRingtoneImageId = Utils.resolveSpotifyUriImage(mRingtone.toString());
            } else if (mCurrentMediaType == ALARM_TYPE_RANDOM) {
                ringtoneTitle = getResources().getString(R.string.randomMusicType);
                mRingtoneImageId = R.drawable.ic_track;
                mRingtoneView.setVisibility(View.INVISIBLE);
            } else if (mCurrentMediaType == ALARM_TYPE_FOLDER) {
                ringtoneTitle = ringtoneUri.getLastPathSegment();
                mRingtoneImageId = R.drawable.ic_folder;
            }
        }

        ringtone.setText(ringtoneTitle);
        ringtone.setContentDescription(getResources().getString(
                R.string.ringtone_description)
                + " " + ringtone);
        ringtone.setCompoundDrawablesWithIntrinsicBounds(getActivity().getDrawable(mRingtoneImageId), null, null, null);

        updateButtons(mRingtone != null && !mRingtone.equals(Alarm.NO_RINGTONE_URI));
    }

    private void initView() {
        setRingtoneName();

        if (mVolume == -1) {
            mEnabledCheckbox.setChecked(true);
            mMaxVolumeContainer.setVisibility(View.GONE);

        } else {
            mEnabledCheckbox.setChecked(false);
            mMaxVolumeSeekBar.setProgress(mVolume - 1);
            mMaxVolumeContainer.setVisibility(View.VISIBLE);
        }

        mIncreasingVolume.setChecked(mIncreasingVolumeValue);
        updateRandomModeVisibility();
        mMediaTypeSelect.setSelection(mCurrentMediaType);
        updateButtons(mRingtone != null && !mRingtone.equals(Alarm.NO_RINGTONE_URI));
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (progress == 0) {
            // dont allow value 0
            seekBar.setProgress(1);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private void showAlarmTestDialog(Alarm alarm, boolean preAlarm) {
        if (mCurrentMediaType == ALARM_TYPE_SPOTIFY && Utils.isSpotifyAlarm(alarm, preAlarm)) {
            if (Utils.isSpotifyPluginInstalled(getActivity())) {
                // ignore pre alarm here - spotify activities assume it stored in there
                alarm.alert = mRingtone;
                alarm.setRingtoneName(mRingtoneName, mRingtoneType);
                alarm.setRandomMode(false, mRandomModeValue);
                alarm.alarmVolume = mVolume;
                startActivity(Utils.getSpotifyTestPlayIntent(getActivity().getApplicationContext(), alarm));
            } else {
                showSpotifyErrorDialog();
            }
        } else {
            closeAlarmTestDialog();

            final AlarmTestDialog fragment = AlarmTestDialog.newInstance(alarm, preAlarm,
                    ringtone.getText().toString(), mRingtoneImageId);
            fragment.show(getFragmentManager(), "alarm_test");
        }
    }

    private void closeAlarmTestDialog() {
        final Fragment prev = getFragmentManager().findFragmentByTag("alarm_test");
        if (prev != null) {
            ((DialogFragment) prev).dismiss();
        }
    }

    private void saveChanges(Alarm alarm) {
        if (mPreAlarm) {
            alarm.preAlarmAlert = mRingtone;
            alarm.preAlarmVolume = mVolume;
            alarm.preAlarmTime = mPreAlarmTime;
            alarm.setPreAlarmRingtoneName(mRingtoneName, mRingtoneType);
        } else {
            alarm.alert = mRingtone;
            alarm.alarmVolume = mVolume;
            alarm.setRingtoneName(mRingtoneName, mRingtoneType);
        }
        alarm.setIncreasingVolume(mPreAlarm, mIncreasingVolumeValue);
        alarm.setRandomMode(mPreAlarm, mRandomModeValue);
    }

    private void selectRingtone(int mediaType) {
        if (mediaType == ALARM_TYPE_SPOTIFY) {
            if (Utils.isSpotifyPluginInstalled(getActivity())) {
                Alarm testAlarm = new Alarm();
                saveChanges(testAlarm);
                // ignore pre alarm here - spotify activities assume it stored in there
                testAlarm.alert = mRingtone;
                startActivityForResult(Utils.getSpotifyBrowseIntent(getActivity(),
                        testAlarm), REQUEST_CODE_SPOTIFY);
            } else {
                showSpotifyErrorDialog();
            }
        } else if (mediaType == ALARM_TYPE_BROWSE) {
            launchBrowseActivity();
        } else if (mediaType == ALARM_TYPE_FOLDER) {
            launchStoragePicker();
        }
    }

    private void launchBrowseActivity() {
        checkStoragePermissions(new Runnable() {
            @Override
            public void run() {
                launchBroseActivityWithPerms();
            }
        });
    }

    private void launchBroseActivityWithPerms() {
        Alarm testAlarm = new Alarm();
        saveChanges(testAlarm);
        // ignore pre alarm here - activities assume it stored in there
        testAlarm.alert = mRingtone;
        startActivityForResult(Utils.getLocalBrowseIntent(getActivity(),
                testAlarm), REQUEST_CODE_BROWSE);
    }

    private void updateOkButtonState(boolean value) {
        if (mOkButton == null) {
            AlertDialog dialog = (AlertDialog) getDialog();
            if (dialog != null) {
                mOkButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            }
        }

        if (mOkButton != null) {
            mOkButton.setEnabled(value);
        }
    }

    private void updateTestButtonState(boolean value) {
        if (mTestButton == null) {
            AlertDialog dialog = (AlertDialog) getDialog();
            if (dialog != null) {
                mTestButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            }
        }

        if (mTestButton != null) {
            mTestButton.setEnabled(value);
        }
    }

    private void updateButtons(boolean value) {
        updateOkButtonState(value);
        updateTestButtonState(value);
    }

    private int getPreAlarmTimePosition() {
        String[] preAlarmTimes = getResources().getStringArray(R.array.pre_alarm_times_values);
        for (int i = 0; i < preAlarmTimes.length; i++) {
            String preAlarmTime = preAlarmTimes[i];
            if (Integer.parseInt(preAlarmTime) == mPreAlarmTime) {
                return i;
            }
        }
        return 0;
    }

    private int calcMusicVolumeFromCurrentAlarm() {
        int maxMusicVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int alarmVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        int maxAlarmVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);

        if (alarmVolume == 0) {
            return 0;
        }
        return (int) (((float) alarmVolume / (float) maxAlarmVolume) * (float) maxMusicVolume);
    }

    private void showSpotifyErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
                Utils.getDialogThemeResourceId(getActivity()));
        builder.setTitle(android.R.string.dialog_alert_title);
        builder.setMessage(R.string.no_spotify_message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private boolean enableRandomMode() {
        if (mCurrentMediaType == ALARM_TYPE_SPOTIFY) {
            if (mRingtone.toString().contains(":playlist:")) {
                return true;
            } else if (mRingtone.toString().contains(":album:")) {
                return true;
            } else if (mRingtone.toString().contains(":artist:")) {
                return true;
            }
        }
        if (mCurrentMediaType == ALARM_TYPE_BROWSE ||
                mCurrentMediaType == ALARM_TYPE_FOLDER) {
            return Utils.isLocalPlaylistType(mRingtone.toString());
        }
        return false;
    }

    private void updateRandomModeVisibility() {
        if (enableRandomMode()) {
            mRandomMode.setVisibility(View.VISIBLE);
        } else {
            mRandomMode.setVisibility(View.INVISIBLE);
            mRandomModeValue = false;
        }
        mRandomMode.setChecked(mRandomModeValue);
    }

    private void setRandomSong() {
        mRingtone = Uri.parse(Utils.getRandomUriString());
        mRingtoneName = "random";
        String ringtoneTitle = getResources().getString(R.string.randomMusicType);
        mRingtoneImageId = R.drawable.ic_track;
        ringtone.setText(ringtoneTitle);
        ringtone.setCompoundDrawablesWithIntrinsicBounds(getActivity().getDrawable(mRingtoneImageId), null, null, null);
        mRingtoneView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onChooseDirOk(Uri chosenDir) {
        mRingtone = chosenDir;
        mRingtoneName = chosenDir.getLastPathSegment();
        mRingtoneType = BrowseActivity.QUERY_TYPE_FOLDER;
        updateRingtoneName();
        updateRandomModeVisibility();
    }

    @Override
    public void onChooseDirCancel() {
    }

    private void launchStoragePicker() {
        closeStoragePicker();

        final StorageChooserDialog fragment = StorageChooserDialog.newInstance(this);
        fragment.show(getFragmentManager(), "choose_dialog");
    }

    private void closeStoragePicker() {
        final Fragment prev = getFragmentManager().findFragmentByTag("choose_dialog");
        if (prev != null) {
            ((DialogFragment) prev).dismiss();
        }
    }

}

