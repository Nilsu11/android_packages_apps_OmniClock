/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.PorterDuff;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.omnirom.deskclock.alarms.AlarmStateManager;
import org.omnirom.deskclock.provider.Alarm;
import org.omnirom.deskclock.provider.AlarmInstance;
import org.omnirom.deskclock.provider.DaysOfWeek;
import org.omnirom.deskclock.widget.ActionableToastBar;
import org.omnirom.deskclock.widget.ExpandAnimation;
import org.omnirom.deskclock.widget.TextTime;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * AlarmClock application.
 */
public class AlarmClockFragment extends DeskClockFragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        TimePickerDialog.OnTimeSetListener,
        View.OnTouchListener,
        AlarmRingtoneDialog.AlarmRingtoneDialogListener {
    private static final float EXPAND_DECELERATION = 1f;
    private static final float COLLAPSE_DECELERATION = 0.7f;

    private static final int ANIMATION_DURATION = 300;
    private static final int EXPAND_DURATION = 300;
    private static final int COLLAPSE_DURATION = 150;

    private static final float ROTATE_180_DEGREE = 180f;
    private static final float ROTATE_0_DEGREE = 0f;

    private static final String KEY_EXPANDED_ID = "expandedId";
    private static final String KEY_DELETED_ALARM = "deletedAlarm";
    private static final String KEY_UNDO_SHOWING = "undoShowing";
    private static final String KEY_SELECTED_ALARM = "selectedAlarm";
    private static final String PREF_KEY_ALARM_HINT_SHOWN = "alarmHintShown";
    private static final String PREF_KEY_NO_ALARM_SOUND_HINT_SHOWN = "noAlarmSoundHintShown";

    private static final DeskClockExtensions sDeskClockExtensions = ExtensionsFactory
            .getDeskClockExtensions();

    // This extra is used when receiving an intent to create an alarm, but no alarm details
    // have been passed in, so the alarm page should start the process of creating a new alarm.
    public static final String ALARM_CREATE_NEW_INTENT_EXTRA = "deskclock.create.new";

    // This extra is used when receiving an intent to scroll to specific alarm. If alarm
    // can not be found, and toast message will pop up that the alarm has be deleted.
    public static final String SCROLL_TO_ALARM_INTENT_EXTRA = "deskclock.scroll.to.alarm";

    private static final int PERMISSIONS_REQUEST_EXTERNAL_STORAGE = 0;

    private FrameLayout mMainLayout;
    private ListView mAlarmsList;
    private AlarmItemAdapter mAdapter;

    private Alarm mSelectedAlarm;
    private long mScrollToAlarmId = Alarm.INVALID_ID;
    private long mExpandedId = Alarm.INVALID_ID;

    private Loader mCursorLoader = null;

    // Saved states for undo
    private Alarm mDeletedAlarm;
    private Alarm mAddedAlarm;
    private boolean mUndoShowing;
    private boolean mCloneAlarm;

    private Transition mAddRemoveTransition;
    private Transition mRepeatTransition;

    private List<Uri> mAlarms;
    private List<Uri> mRingtones;
    private Runnable mRunAfter;

    private int mTextColorWhite;
    private int mTextColorDisabled;

    public AlarmClockFragment() {
        // Basic provider required by Fragment.java
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mCursorLoader = getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedState) {
        // Inflate the layout for this fragment
        final View v = inflater.inflate(R.layout.alarm_clock, container, false);

        cacheAlarmTones();
        cacheRingtones();

        if (savedState != null) {
            mExpandedId = savedState.getLong(KEY_EXPANDED_ID);
            mDeletedAlarm = savedState.getParcelable(KEY_DELETED_ALARM);
            mUndoShowing = savedState.getBoolean(KEY_UNDO_SHOWING);
            mSelectedAlarm = savedState.getParcelable(KEY_SELECTED_ALARM);
        }

        mAddRemoveTransition = new AutoTransition();
        mAddRemoveTransition.setDuration(ANIMATION_DURATION);

        mRepeatTransition = new AutoTransition();
        mRepeatTransition.setDuration(ANIMATION_DURATION / 2);
        mRepeatTransition.setInterpolator(new AccelerateDecelerateInterpolator());

        mMainLayout = (FrameLayout) v.findViewById(R.id.main);
        mAlarmsList = (ListView) v.findViewById(R.id.alarms_list);
        mAlarmsList.setDivider(null);
        mAlarmsList.setDividerHeight(0);

        mAdapter = new AlarmItemAdapter(getActivity(), mAlarmsList);
        mAdapter.registerDataSetObserver(new DataSetObserver() {

            private int prevAdapterCount = -1;

            @Override
            public void onChanged() {

                final int count = mAdapter.getCount();
                if (mDeletedAlarm != null && prevAdapterCount > count) {
                    showUndoBar();
                }

                // Cache this adapter's count for when the adapter changes.
                prevAdapterCount = count;
                super.onChanged();
            }
        });

        mAlarmsList.setAdapter(mAdapter);
        View footerView = inflater.inflate(R.layout.blank_footer_view, mAlarmsList, false);
        mAlarmsList.addFooterView(footerView, null, false);
        View headerView = inflater.inflate(R.layout.empty_header_view, mAlarmsList, false);
        mAlarmsList.addHeaderView(headerView, null, false);

        mTextColorWhite = getResources().getColor(android.R.color.white);
        mTextColorDisabled = Utils.setColorAlpha(mTextColorWhite,
                getResources().getInteger(R.integer.disabled_text_alpha));

        if (mUndoShowing) {
            showUndoBar();
        }
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();

        setFabAppearance();
        setLeftRightButtonAppearance();

        if (mAdapter != null) {
            mAdapter.updateDayOrder();
            mAdapter.notifyDataSetChanged();
        }
        // Check if another app asked us to create a blank new alarm.
        final Intent intent = getActivity().getIntent();
        if (intent.hasExtra(ALARM_CREATE_NEW_INTENT_EXTRA)) {
            if (intent.getBooleanExtra(ALARM_CREATE_NEW_INTENT_EXTRA, false)) {
                // An external app asked us to create a blank alarm.
                startCreatingAlarm();
            }

            // Remove the CREATE_NEW extra now that we've processed it.
            intent.removeExtra(ALARM_CREATE_NEW_INTENT_EXTRA);
        } else if (intent.hasExtra(SCROLL_TO_ALARM_INTENT_EXTRA)) {
            long alarmId = intent.getLongExtra(SCROLL_TO_ALARM_INTENT_EXTRA, Alarm.INVALID_ID);
            if (alarmId != Alarm.INVALID_ID) {
                mScrollToAlarmId = alarmId;
                if (mCursorLoader != null && mCursorLoader.isStarted()) {
                    // We need to force a reload here to make sure we have the latest view
                    // of the data to scroll to.
                    mCursorLoader.forceLoad();
                }
            }

            // Remove the SCROLL_TO_ALARM extra now that we've processed it.
            intent.removeExtra(SCROLL_TO_ALARM_INTENT_EXTRA);
        }
    }

    private void hideUndoBar(boolean animate) {
        mUndoBar.hide(animate);
        mUndoFrame.setVisibility(View.GONE);
        mDeletedAlarm = null;
        mUndoShowing = false;
    }

    private void showUndoBar() {
        final Alarm deletedAlarm = mDeletedAlarm;
        mUndoFrame.setVisibility(View.VISIBLE);
        mUndoFrame.setOnTouchListener(this);
        mUndoBar.show(new ActionableToastBar.ActionClickedListener() {
            @Override
            public void onActionClicked() {
                mAddedAlarm = deletedAlarm;
                hideUndoBar(true);
                asyncAddAlarm(deletedAlarm, false);
            }
        }, 0, getResources().getString(R.string.alarm_deleted), false, R.string.alarm_undo, true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAdapter != null) {
            outState.putLong(KEY_EXPANDED_ID, mExpandedId);
            outState.putParcelable(KEY_DELETED_ALARM, mDeletedAlarm);
            outState.putBoolean(KEY_UNDO_SHOWING, mUndoShowing);
            outState.putParcelable(KEY_SELECTED_ALARM, mSelectedAlarm);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ToastMaster.cancelToast();
    }

    @Override
    public void onPause() {
        super.onPause();
        // When the user places the app in the background by pressing "home",
        // dismiss the toast bar. However, since there is no way to determine if
        // home was pressed, just dismiss any existing toast bar when restarting
        // the app.
        if (mUndoShowing) {
            hideUndoBar(false);
        }
    }

    // Callback used by TimePickerDialog
    @Override
    public void onTimeSet(RadialPickerLayout timePicker, int hourOfDay, int minute, int seconds) {
        if (mCloneAlarm && mSelectedAlarm != null) {
            cloneAlarm(mSelectedAlarm, hourOfDay, minute);
            mSelectedAlarm = null;
        } else {
            if (mSelectedAlarm == null) {
                // If mSelectedAlarm is null then we're creating a new alarm.
                Alarm a = new Alarm();
                a.hour = hourOfDay;
                a.minutes = minute;
                a.enabled = true;
                a.alert = getDefaultAlarmUri();
                a.setRingtoneName(getRingToneTitle(a.alert), BrowseActivity.QUERY_TYPE_ALARM);
                a.deleteAfterUse = false;
                mAddedAlarm = a;
                asyncAddAlarm(a, true);
            } else {
                // only change time but nothing else
                if (mSelectedAlarm.hour != hourOfDay || mSelectedAlarm.minutes != minute) {
                    mSelectedAlarm.hour = hourOfDay;
                    mSelectedAlarm.minutes = minute;
                    asyncUpdateAlarm(mSelectedAlarm, true);
                    mSelectedAlarm = null;
                }
            }
        }
    }

    private void showLabelDialog(final Alarm alarm) {
        closeLabelDialog();

        // Create and show the dialog.
        final LabelDialogFragment newFragment =
                LabelDialogFragment.newInstance(alarm, alarm.label, getTag());
        newFragment.show(getFragmentManager(), "label_dialog");
    }

    private void closeLabelDialog() {
        final Fragment prev = getFragmentManager().findFragmentByTag("label_dialog");
        if (prev != null) {
            ((DialogFragment) prev).dismiss();
        }
    }

    public void setLabel(Alarm alarm, String label) {
        alarm.label = label;
        asyncUpdateAlarm(alarm, false);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return Alarm.getAlarmsCursorLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, final Cursor data) {
        mAdapter.swapCursor(data);
        if (mScrollToAlarmId != Alarm.INVALID_ID) {
            scrollToAlarm(mScrollToAlarmId);
            mScrollToAlarmId = Alarm.INVALID_ID;
        }
    }

    /**
     * Scroll to alarm with given alarm id.
     *
     * @param alarmId The alarm id to scroll to.
     */
    private void scrollToAlarm(long alarmId) {
        int alarmPosition = mAdapter.getPositionOfAlarm(alarmId);
        if (alarmPosition >= 0) {
            mAlarmsList.smoothScrollToPositionFromTop(alarmPosition, 0);
        } else {
            // Trying to display a deleted alarm should only happen from a missed notification for
            // an alarm that has been marked deleted after use.
            Context context = getActivity().getApplicationContext();
            Toast toast = Toast.makeText(context, R.string.missed_alarm_has_been_deleted,
                    Toast.LENGTH_LONG);
            ToastMaster.setToast(toast);
            toast.show();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mAdapter.swapCursor(null);
    }

    public class AlarmItemAdapter extends CursorAdapter {
        private final Context mContext;
        private final LayoutInflater mFactory;
        private final String[] mLongWeekDayStrings;
        private final ListView mList;

        private final boolean mHasVibrator;

        // This determines the order in which it is shown and processed in the UI.
        private final int[] DAY_ORDER = new int[7];

        public class ItemHolder {

            // views for optimization
            CardView alarmItemCard;
            TextTime clock;
            TextView tomorrowLabel;
            TextView daysOfWeek;
            ImageButton delete;
            View expandArea;
            View summary;
            TextView clickableLabel;
            CheckBox repeat;
            LinearLayout repeatDays;
            TextView[] dayButtons = new TextView[7];
            CheckBox vibrate;
            TextView ringtone;
            ImageButton arrow;
            CheckBox preAlarm;
            CheckBox alarmtone;
            TextView prealarmRingtone;
            ImageButton clone;
            ImageView alarmInidicator;
            View alarmContainer;
            // Other states
            Alarm alarm;
        }

        public AlarmItemAdapter(Context context, ListView list) {
            super(context, null, 0);
            mContext = context;
            mFactory = LayoutInflater.from(context);
            mList = list;

            DateFormatSymbols dfs = new DateFormatSymbols();
            mLongWeekDayStrings = dfs.getWeekdays();

            updateDayOrder();

            mHasVibrator = ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE))
                    .hasVibrator();
        }

        public void updateDayOrder() {
            int firstDayOfWeek = Calendar.getInstance(Locale.getDefault()).getFirstDayOfWeek();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            final String customFirstDayStr = prefs.getString(SettingsActivity.KEY_WEEK_START, "0");
            final int customFirstDay = Integer.valueOf(customFirstDayStr);
            if (customFirstDay != 0) {
                // 0 means locale default
                firstDayOfWeek = customFirstDay;
            }
            int j = 0;
            for (int i = firstDayOfWeek; i <= DAY_ORDER.length; i++, j++) {
                DAY_ORDER[j] = i;
            }
            for (int i = Calendar.SUNDAY; i < firstDayOfWeek; i++, j++) {
                DAY_ORDER[j] = i;
            }
        }

        private String[] getShortWeekDayStrings() {
            List<String> weekDayList = new ArrayList<String>();
            for (int i = 0; i < mLongWeekDayStrings.length; i++) {
                String s = mLongWeekDayStrings[i];
                if (s.length() > 2) {
                    s = s.substring(0, 2);
                }
                weekDayList.add(s);
            }
            return weekDayList.toArray(new String[weekDayList.size()]);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final View view = mFactory.inflate(R.layout.alarm_time, parent, false);
            setNewHolder(view);
            return view;
        }

        /**
         * In addition to changing the data set for the alarm list, swapCursor is now also
         * responsible for preparing the transition for any added/removed items.
         */
        @Override
        public synchronized Cursor swapCursor(Cursor cursor) {
            // maxwen TODO this causes graphic glitches
            /*if (mAddedAlarm != null || mDeletedAlarm != null) {
                TransitionManager.beginDelayedTransition(mAlarmsList, mAddRemoveTransition);
            }*/

            final Cursor c = super.swapCursor(cursor);

            mAddedAlarm = null;
            mDeletedAlarm = null;

            return c;
        }

        private void setNewHolder(View view) {
            // standard view holder optimization
            final ItemHolder holder = new ItemHolder();
            holder.alarmItemCard = (CardView) view.findViewById(R.id.alarm_item_card);
            holder.alarmItemCard.setCardBackgroundColor(Utils.getViewBackgroundColor(mContext));
            holder.tomorrowLabel = (TextView) view.findViewById(R.id.tomorrowLabel);
            holder.clock = (TextTime) view.findViewById(R.id.digital_clock);
            holder.daysOfWeek = (TextView) view.findViewById(R.id.daysOfWeek);
            holder.delete = (ImageButton) view.findViewById(R.id.delete);
            holder.summary = view.findViewById(R.id.summary);
            holder.expandArea = view.findViewById(R.id.expand_area);
            holder.arrow = (ImageButton) view.findViewById(R.id.arrow);
            holder.repeat = (CheckBox) view.findViewById(R.id.repeat_onoff);
            holder.clickableLabel = (TextView) view.findViewById(R.id.edit_label);
            holder.repeatDays = (LinearLayout) view.findViewById(R.id.repeat_days);
            holder.vibrate = (CheckBox) view.findViewById(R.id.vibrate_onoff);
            holder.ringtone = (TextView) view.findViewById(R.id.choose_ringtone);
            holder.preAlarm = (CheckBox) view.findViewById(R.id.pre_alarm);
            holder.alarmtone = (CheckBox) view.findViewById(R.id.alarm_select);
            holder.prealarmRingtone = (TextView) view.findViewById(R.id.prealarm_choose_ringtone);
            holder.clone = (ImageButton) view.findViewById(R.id.clone);
            holder.alarmInidicator = (ImageView) view.findViewById(R.id.alarm_inidicator);
            holder.alarmContainer = view.findViewById(R.id.alarm_container);
            view.setTag(holder);
        }

        @Override
        public void bindView(final View view, Context context, final Cursor cursor) {
            final Alarm alarm = new Alarm(cursor);
            Object tag = view.getTag();
            if (tag == null) {
                // The view was converted but somehow lost its tag.
                setNewHolder(view);
            }
            final ItemHolder itemHolder = (ItemHolder) tag;
            itemHolder.alarm = alarm;

            setEnabledState(itemHolder, alarm.enabled);

            itemHolder.clock.setFormat((int) (itemHolder.clock.getTextSize() / 3), 0);
            itemHolder.clock.setTime(alarm.hour, alarm.minutes);
            itemHolder.clock.setClickable(true);
            itemHolder.clock.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    mSelectedAlarm = itemHolder.alarm;
                    mCloneAlarm = false;
                    AlarmUtils.showTimeEditDialog(AlarmClockFragment.this, alarm);
                    return true;
                }
            });
            itemHolder.clock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alarm.enabled = !alarm.enabled;
                    setEnabledState(itemHolder, alarm.enabled);
                    asyncUpdateAlarm(alarm, alarm.enabled);
                }
            });
            itemHolder.alarmInidicator.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alarm.enabled = !alarm.enabled;
                    setEnabledState(itemHolder, alarm.enabled);
                    asyncUpdateAlarm(alarm, alarm.enabled);
                }
            });

            boolean expanded = isAlarmExpanded(alarm);

            itemHolder.expandArea.setVisibility(expanded ? View.VISIBLE : View.GONE);
            itemHolder.arrow.setRotation(expanded ? ROTATE_180_DEGREE : 0);

            itemHolder.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDeletedAlarm = alarm;
                    asyncDeleteAlarm(alarm);
                }
            });

            itemHolder.clone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSelectedAlarm = alarm;
                    startCloningAlarm();
                }
            });

            itemHolder.clickableLabel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showLabelDialog(alarm);
                }
            });

            if (!TextUtils.isEmpty(alarm.label)) {
                itemHolder.clickableLabel.setText(alarm.label);
            } else {
                itemHolder.clickableLabel.setText("");
            }

            if (expanded) {
                expandAlarm(itemHolder, false);
            }

            updateAlarmLabel(itemHolder);

            itemHolder.arrow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (isAlarmExpanded(itemHolder.alarm)) {
                        collapseAlarm(itemHolder, true);
                    } else {
                        expandAlarm(itemHolder, true);
                    }
                }
            });
        }

        private boolean isTomorrow(Alarm alarm) {
            final Calendar now = Calendar.getInstance();
            final int alarmHour = alarm.hour;
            final int currHour = now.get(Calendar.HOUR_OF_DAY);
            return alarmHour < currHour ||
                    (alarmHour == currHour && alarm.minutes <= now.get(Calendar.MINUTE));
        }

        private void bindExpandArea(final ItemHolder itemHolder, final Alarm alarm) {
            // Views in here are not bound until the item is expanded.

            int ringtoneImageId = R.drawable.ic_alarm;
            if (itemHolder.alarm.daysOfWeek.isRepeating()) {
                itemHolder.repeat.setChecked(true);
                itemHolder.repeatDays.setVisibility(View.VISIBLE);
            } else {
                itemHolder.repeat.setChecked(false);
                itemHolder.repeatDays.setVisibility(View.GONE);
            }

            itemHolder.repeat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Animate the resulting layout changes.
                    TransitionManager.beginDelayedTransition(mList, mRepeatTransition);

                    final boolean checked = ((CheckBox) view).isChecked();
                    if (checked) {
                        // Show days
                        itemHolder.repeatDays.setVisibility(View.VISIBLE);

                        // Set all days
                        alarm.daysOfWeek.setDaysOfWeek(true, DAY_ORDER);

                        updateDaysOfWeekButtons(itemHolder, alarm.daysOfWeek);
                    } else {
                        // Hide days
                        itemHolder.repeatDays.setVisibility(View.GONE);

                        // Remove all repeat days
                        alarm.daysOfWeek.clearAllDays();
                    }

                    asyncUpdateAlarm(alarm, false);
                    updateAlarmLabel(itemHolder);
                }
            });

            buildRepeatButtons(itemHolder);
            updateDaysOfWeekButtons(itemHolder, alarm.daysOfWeek);
            for (int i = 0; i < 7; i++) {
                final int buttonIndex = i;

                itemHolder.dayButtons[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final boolean isActivated =
                                itemHolder.dayButtons[buttonIndex].isActivated();
                        alarm.daysOfWeek.setDaysOfWeek(!isActivated, DAY_ORDER[buttonIndex]);
                        if (!isActivated) {
                            turnOnDayOfWeek(itemHolder, buttonIndex);
                        } else {
                            turnOffDayOfWeek(itemHolder, buttonIndex);

                            // See if this was the last day, if so, un-check the repeat box.
                            if (!alarm.daysOfWeek.isRepeating()) {
                                // Animate the resulting layout changes.
                                TransitionManager.beginDelayedTransition(mList, mRepeatTransition);

                                itemHolder.repeat.setChecked(false);
                                itemHolder.repeatDays.setVisibility(View.GONE);
                            }
                        }
                        asyncUpdateAlarm(alarm, false);
                        updateAlarmLabel(itemHolder);
                    }
                });
            }

            if (!mHasVibrator) {
                itemHolder.vibrate.setVisibility(View.GONE);
            } else {
                itemHolder.vibrate.setVisibility(View.VISIBLE);
                itemHolder.vibrate.setChecked(alarm.vibrate);
            }

            itemHolder.vibrate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final boolean checked = ((CheckBox) v).isChecked();
                    alarm.vibrate = checked;
                    asyncUpdateAlarm(alarm, false);
                }
            });

            String ringtone = "";

            boolean spotifyAlarm = false;
            boolean randomMusicAlarm = false;
            if (alarm.alert != null) {
                if (!Alarm.NO_RINGTONE_URI.equals(alarm.alert)) {
                    if (Utils.isSpotifyUri(alarm.alert.toString())) {
                        spotifyAlarm = true;
                    } else if (Utils.isRandomUri(alarm.alert.toString())) {
                        randomMusicAlarm = true;
                    }
                }

                if (spotifyAlarm) {
                    ringtone = alarm.getRingtoneName() != null ? alarm.getRingtoneName() : alarm.alert.toString();
                    ringtoneImageId = Utils.resolveSpotifyUriImage(alarm.alert.toString());
                } else if (randomMusicAlarm) {
                    ringtone = getResources().getString(R.string.randomMusicType);
                    ringtoneImageId = R.drawable.ic_track;
                } else {
                    boolean unknownAlarm = false;
                    if (Alarm.NO_RINGTONE_URI.equals(alarm.alert)) {
                        ringtone = "";
                    } else if (alarm.getRingtoneName() == null) {
                        if (Utils.isStorageUri(alarm.alert.toString())) {
                            ringtone = alarm.alert.getLastPathSegment();
                        } else {
                            ringtone = getRingToneTitle(alarm.alert);
                            if (ringtone == null) {
                                ringtone = getResources().getString(R.string.alarm_uri_unkown);
                                unknownAlarm = true;
                            }
                        }
                    } else {
                        ringtone = alarm.getRingtoneName();
                        if (!Utils.isValidAlarm(getActivity(), alarm.alert, alarm.getRingtoneType())) {
                            ringtone = ringtone + getResources().getString(R.string.alarm_uri_unkown);
                            unknownAlarm = true;
                        }
                    }
                    if (mAlarms.contains(alarm.alert) || unknownAlarm) {
                        ringtoneImageId = R.drawable.ic_alarm;
                    } else if (mRingtones.contains(alarm.alert)) {
                        ringtoneImageId = R.drawable.ic_bell;
                    } else {
                        ringtoneImageId = Utils.resolveLocalUriImage(alarm.alert.toString());
                    }
                }
            }

            itemHolder.ringtone.setText(ringtone);
            itemHolder.ringtone.setContentDescription(
                    mContext.getResources().getString(R.string.ringtone_description) + " "
                            + ringtone);

            itemHolder.ringtone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showAlarmRingtoneDialog(alarm, false);
                }
            });
            itemHolder.ringtone.setCompoundDrawablesWithIntrinsicBounds(mContext.getDrawable(ringtoneImageId), null, null, null);

            itemHolder.preAlarm.setChecked(alarm.preAlarm);
            if (alarm.preAlarm) {
                itemHolder.preAlarm.setText("");
            } else {
                itemHolder.preAlarm.setText(R.string.prealarm_title);
            }
            itemHolder.preAlarm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final boolean checked = ((CheckBox) v).isChecked();

                    if (!checked) {
                        itemHolder.prealarmRingtone.setVisibility(View.GONE);
                        alarm.disablePreAlarm();
                        itemHolder.preAlarm.setText(R.string.prealarm_title);
                    } else {
                        itemHolder.prealarmRingtone.setVisibility(View.VISIBLE);
                        Uri defaultAlarm = getDefaultAlarmUri();
                        alarm.preAlarm = true;
                        alarm.preAlarmAlert=defaultAlarm;
                        alarm.setPreAlarmRingtoneName(getRingToneTitle(defaultAlarm), BrowseActivity.QUERY_TYPE_ALARM);
                        itemHolder.preAlarm.setText("");
                    }
                    itemHolder.prealarmRingtone.requestLayout();
                    asyncUpdateAlarm(alarm, false);
                }
            });
            itemHolder.prealarmRingtone.setVisibility(alarm.preAlarm ? View.VISIBLE : View.GONE);
            itemHolder.alarmtone.setChecked(!alarm.isSilentAlarm());
            if (!alarm.isSilentAlarm()) {
                itemHolder.alarmtone.setText("");
            } else {
                itemHolder.alarmtone.setText(R.string.alarm_title);
            }
            itemHolder.alarmtone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final boolean checked = ((CheckBox) view).isChecked();

                    if (!checked) {
                        itemHolder.ringtone.setVisibility(View.GONE);
                        alarm.setSilentAlarm();
                        itemHolder.alarmtone.setText(R.string.alarm_title);
                    } else {
                        itemHolder.ringtone.setVisibility(View.VISIBLE);
                        Uri defaultAlarm = getDefaultAlarmUri();
                        alarm.alert=defaultAlarm;
                        alarm.setRingtoneName(getRingToneTitle(defaultAlarm), BrowseActivity.QUERY_TYPE_ALARM);
                        itemHolder.alarmtone.setText("");
                    }
                    itemHolder.ringtone.requestLayout();
                    asyncUpdateAlarm(alarm, false);
                }
            });
            itemHolder.ringtone.setVisibility(itemHolder.alarmtone.isChecked() ? View.VISIBLE : View.GONE);
            ringtone = "";

            spotifyAlarm = false;
            randomMusicAlarm = false;
            ringtoneImageId = R.drawable.ic_alarm;

            if (alarm.preAlarmAlert != null) {
                if (!Alarm.NO_RINGTONE_URI.equals(alarm.preAlarmAlert)) {
                    if (Utils.isSpotifyUri(alarm.preAlarmAlert.toString())) {
                        spotifyAlarm = true;
                    } else if (Utils.isRandomUri(alarm.preAlarmAlert.toString())) {
                        randomMusicAlarm = true;
                    }
                }

                if (spotifyAlarm) {
                    ringtone = alarm.getPreAlarmRingtoneName() != null ? alarm.getPreAlarmRingtoneName() : alarm.preAlarmAlert.toString();
                    ringtoneImageId = Utils.resolveSpotifyUriImage(alarm.preAlarmAlert.toString());
                } else if (randomMusicAlarm) {
                    ringtone = getResources().getString(R.string.randomMusicType);
                    ringtoneImageId = R.drawable.ic_track;
                } else {
                    boolean unknownAlarm = false;
                    if (Alarm.NO_RINGTONE_URI.equals(alarm.preAlarmAlert)) {
                        ringtone = "";
                    } else if (alarm.getPreAlarmRingtoneName() == null) {
                        if (Utils.isStorageUri(alarm.preAlarmAlert.toString())) {
                            ringtone = alarm.preAlarmAlert.getLastPathSegment();
                        } else {
                            ringtone = getRingToneTitle(alarm.preAlarmAlert);
                            if (ringtone == null) {
                                ringtone = getResources().getString(R.string.alarm_uri_unkown);
                                unknownAlarm = true;
                            }
                        }
                    } else {
                        ringtone = alarm.getPreAlarmRingtoneName();
                        if (!Utils.isValidAlarm(getActivity(), alarm.preAlarmAlert, alarm.getPreAlarmRingtoneType())) {
                            ringtone = ringtone + getResources().getString(R.string.alarm_uri_unkown);
                            unknownAlarm = true;
                        }
                    }
                    if (mAlarms.contains(alarm.preAlarmAlert) || unknownAlarm) {
                        ringtoneImageId = R.drawable.ic_alarm;
                    } else if (mRingtones.contains(alarm.preAlarmAlert)) {
                        ringtoneImageId = R.drawable.ic_bell;
                    } else {
                        ringtoneImageId = Utils.resolveLocalUriImage(alarm.preAlarmAlert.toString());
                    }
                }
            }

            itemHolder.prealarmRingtone.setText(ringtone);
            itemHolder.prealarmRingtone.setContentDescription(
                    mContext.getResources().getString(R.string.ringtone_description) + " "
                            + ringtone);

            itemHolder.prealarmRingtone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showAlarmRingtoneDialog(alarm, true);
                }
            });
            itemHolder.prealarmRingtone.setCompoundDrawablesWithIntrinsicBounds(mContext.getDrawable(ringtoneImageId), null, null, null);
        }

        private void setEnabledState(ItemHolder holder, boolean enabled) {
            if (enabled) {
                holder.clock.setTextColor(getResources().getColor(R.color.primary));
                holder.alarmInidicator.setColorFilter(getResources().getColor(R.color.primary), PorterDuff.Mode.SRC_IN);
                holder.alarmInidicator.setImageResource(R.drawable.ic_alarm);
            } else {
                if (Utils.isLightTheme(mContext)) {
                    holder.clock.setTextColor(getResources().getColor(R.color.black_30p));
                    holder.alarmInidicator.setColorFilter(getResources().getColor(R.color.black_30p), PorterDuff.Mode.SRC_IN);
                } else {
                    holder.clock.setTextColor(getResources().getColor(R.color.white_30p));
                    holder.alarmInidicator.setColorFilter(getResources().getColor(R.color.white_30p), PorterDuff.Mode.SRC_IN);
                }
                holder.alarmInidicator.setImageResource(R.drawable.ic_alarm_off);
            }
        }

        private void updateDaysOfWeekButtons(ItemHolder holder, DaysOfWeek daysOfWeek) {
            HashSet<Integer> setDays = daysOfWeek.getSetDays();
            for (int i = 0; i < 7; i++) {
                if (setDays.contains(DAY_ORDER[i])) {
                    turnOnDayOfWeek(holder, i);
                } else {
                    turnOffDayOfWeek(holder, i);
                }
            }
        }

        private void buildRepeatButtons(ItemHolder holder) {
            // Build button for each day.
            holder.repeatDays.removeAllViews();
            String[] shortWeekDayStrings = getShortWeekDayStrings();
            for (int i = 0; i < 7; i++) {
                final TextView dayButton = (TextView) mFactory.inflate(
                        R.layout.day_button, holder.repeatDays, false /* attachToRoot */);
                dayButton.setText(shortWeekDayStrings[DAY_ORDER[i]]);
                dayButton.setContentDescription(mLongWeekDayStrings[DAY_ORDER[i]]);
                holder.repeatDays.addView(dayButton);
                holder.dayButtons[i] = dayButton;
            }
        }

        private View getTopParent(View v) {
            while (v != null && v.getId() != R.id.alarm_item) {
                v = (View) v.getParent();
            }
            return v;
        }

        private void turnOffDayOfWeek(ItemHolder holder, int dayIndex) {
            final TextView dayButton = holder.dayButtons[dayIndex];
            dayButton.setActivated(false);
            if (Utils.isLightTheme(mContext)) {
                dayButton.setTextColor(getResources().getColor(R.color.black_30p));
            } else {
                dayButton.setTextColor(getResources().getColor(R.color.white_30p));
            }
        }

        private void turnOnDayOfWeek(ItemHolder holder, int dayIndex) {
            final TextView dayButton = holder.dayButtons[dayIndex];
            dayButton.setActivated(true);
            dayButton.setTextColor(getResources().getColor(R.color.primary));
        }

        /**
         * Expands the alarm for editing.
         *
         * @param itemHolder The item holder instance.
         */
        private void expandAlarm(final ItemHolder itemHolder, boolean animate) {
            if (mExpandedId != Alarm.INVALID_ID
                    && mExpandedId != itemHolder.alarm.id) {
                View v = getViewById(mExpandedId);
                if (v != null) {
                    ItemHolder holder = (ItemHolder) v.getTag();
                    if (holder != null && holder.alarm.id != itemHolder.alarm.id) {
                        // Only allow one alarm to expand at a time.
                        collapseAlarm((ItemHolder) v.getTag(), animate);
                    }
                }
            }

            bindExpandArea(itemHolder, itemHolder.alarm);

            mExpandedId = itemHolder.alarm.id;

            if (animate) {
                ExpandAnimation expandAni = new ExpandAnimation(itemHolder.expandArea, EXPAND_DURATION);
                expandAni.setAnimationListener(new AnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        itemHolder.alarmContainer.requestLayout();
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationStart(Animation animation) {
                    }
                });
                itemHolder.expandArea.startAnimation(expandAni);
                itemHolder.summary.setEnabled(true);
                Animator rotateAnimator = ObjectAnimator.ofFloat(itemHolder.arrow, View.ROTATION, ROTATE_0_DEGREE, ROTATE_180_DEGREE);
                rotateAnimator.setDuration(EXPAND_DURATION);
                rotateAnimator.start();
            } else {
                itemHolder.expandArea.setVisibility(View.VISIBLE);
                itemHolder.arrow.setRotation(ROTATE_180_DEGREE);
                itemHolder.summary.setEnabled(true);
                itemHolder.alarmContainer.requestLayout();
            }
        }

        private boolean isAlarmExpanded(Alarm alarm) {
            return mExpandedId == alarm.id;
        }

        private void collapseAlarm(final ItemHolder itemHolder, boolean animate) {
            mExpandedId = Alarm.INVALID_ID;

            if (animate) {
                ExpandAnimation expandAni = new ExpandAnimation(itemHolder.expandArea, COLLAPSE_DURATION);
                expandAni.setAnimationListener(new AnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        itemHolder.alarmContainer.requestLayout();
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationStart(Animation animation) {
                    }
                });
                itemHolder.expandArea.startAnimation(expandAni);

                itemHolder.summary.setEnabled(false);

                Animator rotateAnimator = ObjectAnimator.ofFloat(itemHolder.arrow, View.ROTATION, ROTATE_180_DEGREE, ROTATE_0_DEGREE);
                rotateAnimator.setDuration(COLLAPSE_DURATION);
                rotateAnimator.start();
            } else {
                itemHolder.expandArea.setVisibility(View.GONE);
                itemHolder.arrow.setRotation(ROTATE_0_DEGREE);
                itemHolder.summary.setEnabled(false);
                itemHolder.alarmContainer.requestLayout();
            }
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        private View getViewById(long id) {
            for (int i = 0; i < mList.getCount(); i++) {
                View v = mList.getChildAt(i);
                if (v != null) {
                    ItemHolder h = (ItemHolder) (v.getTag());
                    if (h != null && h.alarm.id == id) {
                        return v;
                    }
                }
            }
            return null;
        }

        private int getPositionOfAlarm(long alarmId) {
            int alarmPosition = -1;
            for (int i = 0; i < getCount(); i++) {
                long id = getItemId(i);
                if (id == alarmId) {
                    alarmPosition = i;
                    break;
                }
            }
            return alarmPosition;
        }

        private void updateAlarmLabel(ItemHolder itemHolder) {
            if (itemHolder.alarm.daysOfWeek.isRepeating()) {
                itemHolder.tomorrowLabel.setVisibility(View.GONE);
            } else {
                itemHolder.tomorrowLabel.setVisibility(View.VISIBLE);
                final Resources resources = getResources();
                final String labelText = isTomorrow(itemHolder.alarm) ?
                        resources.getString(R.string.alarm_tomorrow) :
                        resources.getString(R.string.alarm_today);
                itemHolder.tomorrowLabel.setText(labelText);
            }

            // Set the repeat text or leave it blank if it does not repeat.
            final String daysOfWeekStr =
                    itemHolder.alarm.daysOfWeek.toString(AlarmClockFragment.this.getActivity(), false, DAY_ORDER);
            if (daysOfWeekStr != null && daysOfWeekStr.length() != 0) {
                itemHolder.daysOfWeek.setText(daysOfWeekStr);
                itemHolder.daysOfWeek.setContentDescription(itemHolder.alarm.daysOfWeek.toAccessibilityString(
                        AlarmClockFragment.this.getActivity(), DAY_ORDER));
                itemHolder.daysOfWeek.setVisibility(View.VISIBLE);
            } else {
                itemHolder.daysOfWeek.setVisibility(View.GONE);
            }
        }
    }

    private void startCreatingAlarm() {
        // Set the "selected" alarm as null, and we'll create the new one when the timepicker
        // comes back.
        mSelectedAlarm = null;
        mCloneAlarm = false;
        AlarmUtils.showTimeEditDialog(this, null);
    }

    private void startCloningAlarm() {
        mCloneAlarm = true;
        AlarmUtils.showTimeEditDialog(this, null);
    }

    private static AlarmInstance setupAlarmInstance(Context context, Alarm alarm) {
        ContentResolver cr = context.getContentResolver();
        AlarmInstance newInstance = alarm.createInstanceAfter(Calendar.getInstance());
        newInstance = AlarmInstance.addInstance(cr, newInstance);
        // Register instance to state manager
        AlarmStateManager.registerInstance(context, newInstance, true);
        return newInstance;
    }

    private void asyncDeleteAlarm(final Alarm alarm) {
        final Context context = getActivity().getApplicationContext();
        final AsyncTask<Void, Void, Void> deleteTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... parameters) {
                // Activity may be closed at this point , make sure data is still valid
                if (context != null && alarm != null) {
                    ContentResolver cr = context.getContentResolver();
                    AlarmStateManager.deleteAllInstances(context, alarm.id);
                    Alarm.deleteAlarm(cr, alarm.id);
                    sDeskClockExtensions.deleteAlarm(context, alarm.id);
                }
                return null;
            }
        };
        mUndoShowing = true;
        deleteTask.execute();
    }

    private void asyncAddAlarm(final Alarm alarm, final boolean expand) {
        final Context context = getActivity().getApplicationContext();
        final AsyncTask<Void, Void, AlarmInstance> updateTask =
                new AsyncTask<Void, Void, AlarmInstance>() {
                    @Override
                    protected AlarmInstance doInBackground(Void... parameters) {
                        if (context != null && alarm != null) {
                            ContentResolver cr = context.getContentResolver();

                            // Add alarm to db
                            Alarm newAlarm = Alarm.addAlarm(cr, alarm);
                            mScrollToAlarmId = newAlarm.id;
                            if (expand) {
                                mExpandedId = newAlarm.id;
                            }

                            // Create and add instance to db
                            if (newAlarm.enabled) {
                                sDeskClockExtensions.addAlarm(context, newAlarm);
                                return setupAlarmInstance(context, newAlarm);
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(AlarmInstance instance) {
                        if (instance != null) {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                            boolean noAlarmSoundHintShown = prefs.getBoolean(PREF_KEY_NO_ALARM_SOUND_HINT_SHOWN, false);
                            boolean hintShown = prefs.getBoolean(PREF_KEY_ALARM_HINT_SHOWN, false);

                            if (!instance.mRingtone.equals(Alarm.NO_RINGTONE_URI)) {
                                Uri defaultAlarm = Utils.getDefaultAlarmUri(getActivity());
                                if (defaultAlarm == null) {
                                    if (!noAlarmSoundHintShown) {
                                        // show hint that this alarm has been created with alarm tone choosen
                                        AlarmUtils.popNoDefaultAlarmSoundToast(context);
                                        prefs.edit().putBoolean(PREF_KEY_NO_ALARM_SOUND_HINT_SHOWN, true).commit();
                                        return;
                                    }
                                }
                            }
                            if (!hintShown) {
                                AlarmUtils.popFirstAlarmCreatedToast(context);
                                prefs.edit().putBoolean(PREF_KEY_ALARM_HINT_SHOWN, true).commit();
                            } else {
                                AlarmUtils.popAlarmSetToast(context, instance.getAlarmTime().getTimeInMillis());
                            }
                        }
                    }
                };
        updateTask.execute();
    }

    private void asyncUpdateAlarm(final Alarm alarm, final boolean popToast) {
        final Context context = getActivity().getApplicationContext();
        final AsyncTask<Void, Void, AlarmInstance> updateTask =
                new AsyncTask<Void, Void, AlarmInstance>() {
                    @Override
                    protected AlarmInstance doInBackground(Void... parameters) {
                        ContentResolver cr = context.getContentResolver();

                        // Dismiss all old instances
                        AlarmStateManager.deleteAllInstances(context, alarm.id);

                        // Update alarm
                        Alarm.updateAlarm(cr, alarm);
                        if (alarm.enabled) {
                            return setupAlarmInstance(context, alarm);
                        }

                        return null;
                    }

                    @Override
                    protected void onPostExecute(AlarmInstance instance) {
                        if (popToast && instance != null) {
                            AlarmUtils.popAlarmSetToast(context, instance.getAlarmTime().getTimeInMillis());
                        }
                    }
                };
        updateTask.execute();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mUndoShowing) {
            hideUndoBar(true);
            return true;
        }
        return false;
    }

    @Override
    public void onFabClick(View view) {
        if (mUndoShowing) {
            hideUndoBar(true);
        } else {
            startCreatingAlarm();
        }
    }

    @Override
    public void setFabAppearance() {
        final DeskClock activity = (DeskClock) getActivity();
        if (mFab == null || !activity.isAlarmTab()) {
            return;
        }
        mFab.setVisibility(View.VISIBLE);
        mFab.setImageResource(R.drawable.ic_fab_plus);
        mFab.setContentDescription(getString(R.string.button_alarms));
    }

    @Override
    public void setLeftRightButtonAppearance() {
        final DeskClock activity = (DeskClock) getActivity();
        if (mLeftButton == null || mRightButton == null ||
                !activity.isAlarmTab()) {
            return;
        }
        mLeftButton.setVisibility(View.GONE);
        mRightButton.setVisibility(View.GONE);
    }

    private void showAlarmRingtoneDialog(final Alarm alarm, final boolean preAlarm) {
        checkStoragePermissions(new Runnable() {
            @Override
            public void run() {
                showAlarmRingtoneDialogWithPerms(alarm, preAlarm);
            }
        });
    }

    private void showAlarmRingtoneDialogWithPerms(Alarm alarm, boolean preAlarm) {
        closeAlarmRingtoneDialog();

        AlarmRingtoneDialog fragment = AlarmRingtoneDialog.newInstance(alarm, preAlarm, getTag());
        fragment.show(getFragmentManager(), "alarm_ringtone_edit");
    }

    private void closeAlarmRingtoneDialog() {
        final Fragment prev = getFragmentManager().findFragmentByTag("alarm_ringtone_edit");
        if (prev != null) {
            ((DialogFragment) prev).dismiss();
        }
    }

    public void onFinishOk(Alarm alarm, boolean preAlarm) {
        asyncUpdateAlarm(alarm, false);
    }

    private void cloneAlarm(Alarm baseAlarm, int hourOfDay, int minute) {
        Alarm a = new Alarm(baseAlarm);
        a.hour = hourOfDay;
        a.minutes = minute;
        mAddedAlarm = a;
        asyncAddAlarm(a, false);
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

    private String getRingToneTitle(Uri uri) {
        Ringtone ringTone = RingtoneManager.getRingtone(getActivity(), uri);
        if (ringTone != null) {
            return ringTone.getTitle(getActivity());
        }
        return null;
    }

    private Uri getDefaultAlarmUri() {
        if (mAlarms == null || mAlarms.size() == 0) {
            cacheAlarmTones();
        }
        Uri defaultAlarm = Utils.getDefaultAlarmUri(getActivity());
        if (defaultAlarm == null || !mAlarms.contains(defaultAlarm)) {
            // choose the first one from the list
            if (mAlarms.size() != 0) {
                defaultAlarm = mAlarms.get(0);
            }
        }
        return defaultAlarm;
    }
}
