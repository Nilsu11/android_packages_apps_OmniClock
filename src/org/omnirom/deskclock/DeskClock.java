/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v13.app.ActivityCompat;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.omnirom.deskclock.alarms.AlarmStateManager;
import org.omnirom.deskclock.provider.Alarm;
import org.omnirom.deskclock.stopwatch.StopwatchFragment;
import org.omnirom.deskclock.stopwatch.StopwatchService;
import org.omnirom.deskclock.stopwatch.Stopwatches;
import org.omnirom.deskclock.timer.TimerFragment;
import org.omnirom.deskclock.timer.TimerObj;
import org.omnirom.deskclock.timer.Timers;
import org.omnirom.deskclock.widget.ActionableToastBar;
import org.omnirom.deskclock.widget.SlidingTabLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TimeZone;

/**
 * DeskClock clock view for desk docks.
 */
public class DeskClock extends Activity implements LabelDialogFragment.TimerLabelDialogHandler,
        LabelDialogFragment.AlarmLabelDialogHandler {
    private static final boolean DEBUG = false;
    private static final String LOG_TAG = "DeskClock";
    private static final String KEY_SELECTED_TAB = "selected_tab";
    private static final String KEY_SPOTIFY_FIRST_START_DONE = "spotify_first_start";
    public static final String COLOR_THEME_UPDATE_INTENT = "color_theme_update";

    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;
    private Handler mHander;
    private ImageView mFab;
    private ImageView mLeftButton;
    private ImageView mRightButton;
    private int mSelectedTab = -1;
    private SlidingTabLayout mSlidingTabs;
    private ActionableToastBar mUndoBar;
    private View mUndoFrame;
    private LinearLayout mFabButtons;

    public static final int ALARM_TAB_INDEX = 0;
    public static final int CLOCK_TAB_INDEX = 1;
    public static final int TIMER_TAB_INDEX = 2;
    public static final int STOPWATCH_TAB_INDEX = 3;

    public static final String SELECT_TAB_INTENT_EXTRA = "deskclock.select.tab";
    private static final int PERMISSIONS_REQUEST_EXTERNAL_STORAGE = 0;

    private final BroadcastReceiver mColorThemeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(DeskClock.COLOR_THEME_UPDATE_INTENT)) {
                Intent restart = new Intent(context, DeskClock.class);
                restart.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(restart);
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (mHander == null) {
            mHander = new Handler();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        if (DEBUG) Log.d(LOG_TAG, "onNewIntent with intent: " + newIntent);

        // update our intent so that we can consult it to determine whether or
        // not the most recent launch was via a dock event
        setIntent(newIntent);

        // Timer receiver may ask to go to the timers fragment if a timer expired.
        int tab = newIntent.getIntExtra(SELECT_TAB_INTENT_EXTRA, -1);
        if (tab != -1) {
            mViewPager.setCurrentItem(tab);
        }
    }

    private static final ViewOutlineProvider OVAL_OUTLINE_PROVIDER = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
    };

    private void initViews() {
        setContentView(R.layout.desk_clock);
        mFabButtons = (LinearLayout) findViewById(R.id.fab_buttons);
        mFab = (ImageView) findViewById(R.id.fab);
        mFab.setOutlineProvider(OVAL_OUTLINE_PROVIDER);

        mLeftButton = (ImageView) findViewById(R.id.left_button);
        mLeftButton.setOutlineProvider(OVAL_OUTLINE_PROVIDER);

        mRightButton = (ImageView) findViewById(R.id.right_button);
        mRightButton.setOutlineProvider(OVAL_OUTLINE_PROVIDER);

        mUndoBar = (ActionableToastBar) findViewById(R.id.undo_bar);
        mUndoFrame = findViewById(R.id.undo_frame);

        if (mTabsAdapter == null) {
            getActionBar().setElevation(0);
            mViewPager = (ViewPager) findViewById(R.id.desk_clock_pager);
            mViewPager.setOffscreenPageLimit(4);
            mTabsAdapter = new TabsAdapter(this, mViewPager);
            createTabs();

            // Assiging the Sliding Tab Layout View
            mSlidingTabs = (SlidingTabLayout) findViewById(R.id.desk_clock_tabs);
            mSlidingTabs.setDeskClock(this);
            mSlidingTabs.setCustomTabView(R.layout.tab_strip_item, R.id.tab_strip_title, R.id.tab_strip_image);

            // Setting the ViewPager For the SlidingTabsLayout
            mSlidingTabs.setViewPager(mViewPager);
            mSlidingTabs.setOnPageChangeListener(mTabsAdapter);
        }

        mFab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getSelectedFragment().onFabClick(view);
            }
        });
        mLeftButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getSelectedFragment().onLeftButtonClick(view);
            }
        });
        mRightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getSelectedFragment().onRightButtonClick(view);
            }
        });
    }

    private DeskClockFragment getSelectedFragment() {
        return (DeskClockFragment) mTabsAdapter.getItem(mSelectedTab);
    }

    private void createTabs() {
        mTabsAdapter.addTab(AlarmClockFragment.class, getResources().getString(R.string.menu_alarm));
        mTabsAdapter.addTab(ClockFragment.class, getResources().getString(R.string.menu_clock));
        mTabsAdapter.addTab(TimerFragment.class, getResources().getString(R.string.menu_timer));
        mTabsAdapter.addTab(StopwatchFragment.class, getResources().getString(R.string.menu_stopwatch));
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(COLOR_THEME_UPDATE_INTENT);
        registerReceiver(mColorThemeReceiver, intentFilter);

        setTheme(Utils.getThemeResourceId(this));

        mSelectedTab = Utils.getDefaultPage(this);
        if (icicle != null) {
            mSelectedTab = icicle.getInt(KEY_SELECTED_TAB, mSelectedTab);
        }

        // Timer receiver may ask the app to go to the timer fragment if a timer expired
        Intent i = getIntent();
        if (i != null) {
            int tab = i.getIntExtra(SELECT_TAB_INTENT_EXTRA, -1);
            if (tab != -1) {
                mSelectedTab = tab;
            }
        }
        initViews();
        mViewPager.setCurrentItem(mSelectedTab);
        setHomeTimeZone();

        // We need to update the system next alarm time on app startup because the
        // user might have clear our data.
        AlarmStateManager.updateNextAlarm(this);
        ExtensionsFactory.init(getAssets());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mColorThemeReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // We only want to show notifications for stopwatch/timer when the app is closed so
        // that we don't have to worry about keeping the notifications in perfect sync with
        // the app.
        Intent stopwatchIntent = new Intent(getApplicationContext(), StopwatchService.class);
        stopwatchIntent.setAction(Stopwatches.KILL_NOTIF);
        startService(stopwatchIntent);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Timers.NOTIF_APP_OPEN, true);
        editor.apply();
        Intent timerIntent = new Intent();
        timerIntent.setAction(Timers.NOTIF_IN_USE_CANCEL);
        sendBroadcast(timerIntent);

        if (Utils.isSpotifyPluginInstalled(this)) {
            boolean firstStartDone = prefs.getBoolean(KEY_SPOTIFY_FIRST_START_DONE, false);
            if (!firstStartDone) {
                prefs.edit().putBoolean(KEY_SPOTIFY_FIRST_START_DONE, true).commit();
                startActivity(Utils.getSpotifyFirstStartIntent(this));
            } else {
                checkStoragePermissions();
            }
        } else {
            checkStoragePermissions();
        }
    }

    @Override
    public void onPause() {
        Intent intent = new Intent(getApplicationContext(), StopwatchService.class);
        intent.setAction(Stopwatches.SHOW_NOTIF);
        startService(intent);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Timers.NOTIF_APP_OPEN, false);
        editor.apply();
        Utils.showInUseNotifications(this);

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, mSelectedTab);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.desk_clock_menu, menu);
        if (Utils.isSpotifyPluginInstalled(this)) {
            menu.findItem(R.id.menu_item_spotify).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_spotify:
                if (Utils.isSpotifyPluginInstalled(this)) {
                    Intent spotifyIntent = Utils.getSpotifySettingsIntent(this);
                    startActivity(spotifyIntent);
                    return true;
                }
            case R.id.menu_item_settings:
                startActivity(new Intent(DeskClock.this, SettingsActivity.class));
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Insert the local time zone as the Home Time Zone if one is not set
     */
    private void setHomeTimeZone() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String homeTimeZone = prefs.getString(SettingsActivity.KEY_HOME_TZ, "");
        if (!homeTimeZone.isEmpty()) {
            return;
        }
        homeTimeZone = TimeZone.getDefault().getID();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(SettingsActivity.KEY_HOME_TZ, homeTimeZone);
        editor.apply();
        Log.v(LOG_TAG, "Setting home time zone to " + homeTimeZone);
    }

    public void registerPageChangedListener(DeskClockFragment frag) {
        if (mTabsAdapter != null) {
            mTabsAdapter.registerPageChangedListener(frag);
        }
    }

    public void unregisterPageChangedListener(DeskClockFragment frag) {
        if (mTabsAdapter != null) {
            mTabsAdapter.unregisterPageChangedListener(frag);
        }
    }

    /**
     * Adapter for wrapping together the ActionBar's tab with the ViewPager
     */
    private class TabsAdapter extends FragmentPagerAdapter
            implements ViewPager.OnPageChangeListener {


        final class TabInfo {
            public final Class<?> mClss;
            public final CharSequence mTitle;

            TabInfo(Class<?> clss, CharSequence title) {
                mClss = clss;
                mTitle = title;
            }
        }

        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();
        private Context mContext;
        private ViewPager mPager;
        // Used for doing callbacks to fragments.
        private HashSet<String> mFragmentTags = new HashSet<String>();

        public TabsAdapter(Activity activity, ViewPager pager) {
            super(activity.getFragmentManager());
            mContext = activity;
            mPager = pager;
            mPager.setAdapter(this);
        }

        @Override
        public Fragment getItem(int position) {
            // Because this public method is called outside many times,
            // check if it exits first before creating a new one.
            final String name = makeFragmentName(R.id.desk_clock_pager, position);
            Fragment fragment = getFragmentManager().findFragmentByTag(name);
            if (fragment == null) {
                TabInfo info = mTabs.get(position);
                fragment = Fragment.instantiate(mContext, info.mClss.getName(), null);
            }
            return fragment;
        }

        /**
         * Copied from:
         * android/frameworks/support/v13/java/android/support/v13/app/FragmentPagerAdapter.java#94
         * Create unique name for the fragment so fragment manager knows it exist.
         */
        private String makeFragmentName(int viewId, int index) {
            return "android:switcher:" + viewId + ":" + index;
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public CharSequence getPageTitle (int position) {
            TabInfo info = mTabs.get(position);
            return info.mTitle;
        }

        public void addTab(Class<?> clss, CharSequence title) {
            TabInfo info = new TabInfo(clss, title);
            mTabs.add(info);
            notifyDataSetChanged();
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            // Do nothing
        }

        @Override
        public void onPageSelected(int position) {
            mSelectedTab = position;

            DeskClockFragment f = (DeskClockFragment) getItem(position);
            f.setFabAppearance();
            f.setLeftRightButtonAppearance();

            notifyPageChanged(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            // Do nothing
        }

        private void notifyPageChanged(int newPage) {
            for (String tag : mFragmentTags) {
                final FragmentManager fm = getFragmentManager();
                DeskClockFragment f = (DeskClockFragment) fm.findFragmentByTag(tag);
                if (f != null) {
                    f.onPageChanged(newPage);
                }
            }
        }

        public void registerPageChangedListener(DeskClockFragment frag) {
            String tag = frag.getTag();
            if (mFragmentTags.contains(tag)) {
                Log.wtf(LOG_TAG, "Trying to add an existing fragment " + tag);
            } else {
                mFragmentTags.add(frag.getTag());
            }
        }

        public void unregisterPageChangedListener(DeskClockFragment frag) {
            mFragmentTags.remove(frag.getTag());
        }
    }

    public static abstract class OnTapListener implements OnTouchListener {
        private float mLastTouchX;
        private float mLastTouchY;
        private long mLastTouchTime;
        private final TextView mMakePressedTextView;
        private final int mPressedColor, mGrayColor;
        private final float MAX_MOVEMENT_ALLOWED = 20;
        private final long MAX_TIME_ALLOWED = 500;

        public OnTapListener(Activity activity, TextView makePressedView) {
            mMakePressedTextView = makePressedView;
            mPressedColor = activity.getResources().getColor(Utils.getPressedColorId());
            mGrayColor = activity.getResources().getColor(Utils.getGrayColorId());
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case (MotionEvent.ACTION_DOWN):
                    mLastTouchTime = Utils.getTimeNow();
                    mLastTouchX = e.getX();
                    mLastTouchY = e.getY();
                    if (mMakePressedTextView != null) {
                        mMakePressedTextView.setTextColor(mPressedColor);
                    }
                    break;
                case (MotionEvent.ACTION_UP):
                    float xDiff = Math.abs(e.getX() - mLastTouchX);
                    float yDiff = Math.abs(e.getY() - mLastTouchY);
                    long timeDiff = (Utils.getTimeNow() - mLastTouchTime);
                    if (xDiff < MAX_MOVEMENT_ALLOWED && yDiff < MAX_MOVEMENT_ALLOWED
                            && timeDiff < MAX_TIME_ALLOWED) {
                        if (mMakePressedTextView != null) {
                            v = mMakePressedTextView;
                        }
                        processClick(v);
                        resetValues();
                        return true;
                    }
                    resetValues();
                    break;
                case (MotionEvent.ACTION_MOVE):
                    xDiff = Math.abs(e.getX() - mLastTouchX);
                    yDiff = Math.abs(e.getY() - mLastTouchY);
                    if (xDiff >= MAX_MOVEMENT_ALLOWED || yDiff >= MAX_MOVEMENT_ALLOWED) {
                        resetValues();
                    }
                    break;
                default:
                    resetValues();
            }
            return false;
        }

        private void resetValues() {
            mLastTouchX = -1 * MAX_MOVEMENT_ALLOWED + 1;
            mLastTouchY = -1 * MAX_MOVEMENT_ALLOWED + 1;
            mLastTouchTime = -1 * MAX_TIME_ALLOWED + 1;
            if (mMakePressedTextView != null) {
                mMakePressedTextView.setTextColor(mGrayColor);
            }
        }

        protected abstract void processClick(View v);
    }

    /**
     * Called by the LabelDialogFormat class after the dialog is finished. *
     */
    @Override
    public void onDialogLabelSet(TimerObj timer, String label, String tag) {
        Fragment frag = getFragmentManager().findFragmentByTag(tag);
        if (frag instanceof TimerFragment) {
            ((TimerFragment) frag).setLabel(timer, label);
        }
    }

    /**
     * Called by the LabelDialogFormat class after the dialog is finished. *
     */
    @Override
    public void onDialogLabelSet(Alarm alarm, String label, String tag) {
        Fragment frag = getFragmentManager().findFragmentByTag(tag);
        if (frag instanceof AlarmClockFragment) {
            ((AlarmClockFragment) frag).setLabel(alarm, label);
        }
    }

    public boolean isClockTab() {
        return mSelectedTab == CLOCK_TAB_INDEX;
    }

    public boolean isAlarmTab() {
        return mSelectedTab == ALARM_TAB_INDEX;
    }

    public boolean isStopwatchTab() {
        return mSelectedTab == STOPWATCH_TAB_INDEX;
    }

    public boolean isTimerTab() {
        return mSelectedTab == TIMER_TAB_INDEX;
    }

    public ImageView getFab() {
        return mFab;
    }

    public ImageView getLeftButton() {
        return mLeftButton;
    }

    public ImageView getRightButton() {
        return mRightButton;
    }

    public ActionableToastBar getUndoBar() {
        return mUndoBar;
    }

    public View getUndoFrame() {
        return mUndoFrame;
    }

    public LinearLayout getFabButtons() {
        return mFabButtons;
    }

    public Drawable getPageImage(int i) {
        if (i == 0) {
            return getResources().getDrawable(R.drawable.ic_notify_alarm);
        }
        if (i == 1) {
            return getResources().getDrawable(R.drawable.ic_fab_earth);
        }
        if (i == 2) {
            return getResources().getDrawable(R.drawable.ic_notify_timer);
        }
        if (i == 3) {
            return getResources().getDrawable(R.drawable.ic_notify_stopwatch);
        }
        return getResources().getDrawable(R.drawable.ic_notify_alarm);
    }

    private void checkStoragePermissions() {
        boolean needRequest = false;
        String[] permissions = {
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
        };
        final ArrayList<String> permissionList = new ArrayList<String>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
                needRequest = true;
            }
        }

        if (needRequest) {
            int count = permissionList.size();
            if (count > 0) {
                final String[] permissionArray = new String[count];
                for (int i = 0; i < count; i++) {
                    permissionArray[i] = permissionList.get(i);
                }
                mHander.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ActivityCompat.requestPermissions(DeskClock.this, permissionArray, PERMISSIONS_REQUEST_EXTERNAL_STORAGE);
                    }
                }, 1000);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                }
            }
            return;
        }
    }
}
