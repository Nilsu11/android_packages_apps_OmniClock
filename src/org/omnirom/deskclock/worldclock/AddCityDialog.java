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

package org.omnirom.deskclock.worldclock;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimationDrawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import org.omnirom.deskclock.Utils;
import org.omnirom.deskclock.worldclock.CityAndTimeZoneLocator.OnCityAndTimeZoneLocatedCallback;
import org.omnirom.deskclock.worldclock.CityAndTimeZoneLocator.TZ;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class AddCityDialog implements OnClickListener,
        OnItemSelectedListener, TextWatcher, LocationListener {

    private static final int HOURS_1 = 60 * 60000;

    private static final String STATE_CITY_NAME = "city_name";
    private static final String STATE_CITY_TIMEZONE = "city_tz";

    private static final float LOCATION_ACCURACY_THRESHOLD_METERS = 50000;
    private static final long LOCATION_REQUEST_TIMEOUT = 1L * 60L * 1000L; // request for at most 1 minutes
    private static final long OUTDATED_LOCATION_THRESHOLD_MILLIS = 10L * 60L * 1000L; // 10 minutes

    public interface OnCitySelected {
        public void onCitySelected(String city, String tz);
        public void onCancelCitySelection();
    }

    private final AsyncTask<Void, Void, Void> mTzLoadTask = new AsyncTask<Void, Void, Void>() {
        private CityTimeZone[] mZones;

        @Override
        protected Void doInBackground(Void... params) {
            List<CityTimeZone> zones = loadTimeZones();
            Collections.sort(zones);

            mZones = zones.toArray(new CityTimeZone[zones.size()]);
            mDefaultTimeZonePos = zones.indexOf(mDefaultTimeZone);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (!isCancelled()) {
                int id = mSavedTimeZonePos != -1 ? mSavedTimeZonePos : mDefaultTimeZonePos;
                setTimeZoneData(mZones, id, true);
                mLoadingTz = false;
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkLocationAvailability();
        }
    };
    private boolean mReceiverRegistered;

    private final Runnable mLocationTimeout = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(mContext, org.omnirom.deskclock.R.string.cities_add_gps_not_available,
                    Toast.LENGTH_SHORT).show();
            mLocationRequesting = false;
            mCityName.setEnabled(true);
            mCityName.setText("");
            mTimeZones.setEnabled(true);
            stopGpsAnimation();
            mGps.setImageResource(org.omnirom.deskclock.R.drawable.ic_gps);
            checkSelectionStatus();
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                mLocationMgr.removeUpdates(AddCityDialog.this);
            }
        }
    };

    private static class CityTimeZone implements Comparable<CityTimeZone> {
        String mId;
        int mSign;
        int mHours;
        int mMinutes;
        String mLabel;
        boolean mHasDst;

        @Override
        public String toString() {
            if (mId == null) {
                // Loading
                return mLabel;
            }
            return String.format("GMT%s%02d:%02d - %s",
                    (mSign == -1 ? "-" : "+"), mHours, mMinutes, mLabel);
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof CityTimeZone) {
                return compareTo((CityTimeZone) other) == 0;
            }
            return false;
        }

        @Override
        public int compareTo(CityTimeZone other) {
            long offset = getOffset();
            long otherOffset = other.getOffset();
            if (offset != otherOffset) {
                return offset < otherOffset ? -1 : 1;
            }
            if (mHasDst != other.mHasDst) {
                return mHasDst ? 1 : -1;
            }
            return mLabel.compareTo(other.mLabel);
        }

        private long getOffset() {
            return mSign * (mHours * HOURS_1 + mMinutes * 60000);
        }
    }

    private final Context mContext;
    private final Handler mHandler;
    private final OnCitySelected mListener;
    private final AlertDialog mDialog;
    private final EditText mCityName;
    private final ImageButton mGps;
    private final Spinner mTimeZones;
    private Button mButton;

    private LocationManager mLocationMgr;
    private ConnectivityManager mConnectivityMgr;
    private CityAndTimeZoneLocator mLocator;
    private boolean mLocationRequesting;
    private boolean mLoadingTz;

    private int mDefaultTimeZonePos;
    private int mSavedTimeZonePos;
    private CityTimeZone mDefaultTimeZone;


    private static final Criteria sLocationCriteria;
    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }

    public AddCityDialog(Context context, LayoutInflater inflater, OnCitySelected listener) {
        mContext = context;
        mHandler = new Handler();
        mListener = listener;
        mDefaultTimeZonePos = 0;
        mDefaultTimeZone = null;
        mSavedTimeZonePos = -1;
        mLocationMgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mConnectivityMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mLocationRequesting = false;
        mLoadingTz = true;

        // Initialize dialog
        View dlgView = inflater.inflate(org.omnirom.deskclock.R.layout.city_add, null);
        mCityName = (EditText) dlgView.findViewById(org.omnirom.deskclock.R.id.add_city_name);
        mCityName.addTextChangedListener(this);

        mTimeZones = (Spinner) dlgView.findViewById(org.omnirom.deskclock.R.id.add_city_tz);
        CityTimeZone loading = new CityTimeZone();
        loading.mId = null;
        loading.mLabel = context.getString(org.omnirom.deskclock.R.string.cities_add_loading);
        setTimeZoneData(new CityTimeZone[]{ loading }, 0, false);
        mTimeZones.setEnabled(false);
        mTimeZones.setOnItemSelectedListener(this);

        mGps = (ImageButton)dlgView.findViewById(org.omnirom.deskclock.R.id.add_city_gps);
        mGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    public void run() {
                        if (!mLocationRequesting) {
                            requestLocation();
                        } else {
                            cancelRequestLocation();
                        }
                    }
                });
            }
        });
        mGps.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(mContext, org.omnirom.deskclock.R.string.cities_add_city_gps_cd, Toast.LENGTH_SHORT).show();
                mGps.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return true;
            }
        });
        checkLocationAvailability();

        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                Utils.getDialogThemeResourceId(context));
        builder.setTitle(org.omnirom.deskclock.R.string.cities_add_city_title);
        builder.setView(dlgView);
        builder.setPositiveButton(context.getString(android.R.string.ok), this);
        builder.setNegativeButton(context.getString(android.R.string.cancel), null);
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (mReceiverRegistered) {
                    mContext.unregisterReceiver(mReceiver);
                    mReceiverRegistered = false;
                }
                cancelRequestLocation();
                if (mListener != null) {
                    mListener.onCancelCitySelection();
                }
            }
        });
        builder.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (mReceiverRegistered) {
                    mContext.unregisterReceiver(mReceiver);
                    mReceiverRegistered = false;
                }
                cancelRequestLocation();
                if (mListener != null) {
                    mListener.onCancelCitySelection();
                }
            }
        });
        mDialog = builder.create();

        // Register broadcast listeners
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(mReceiver, filter);
        mReceiverRegistered = true;
    }

    private void setTimeZoneData(CityTimeZone[] data, int selected, boolean enabled) {
        ArrayAdapter<CityTimeZone> adapter = new ArrayAdapter<CityTimeZone>(mContext,
                org.omnirom.deskclock.R.layout.spinner_item, data);
        mTimeZones.setAdapter(adapter);
        mTimeZones.setSelection(selected);
        mTimeZones.setEnabled(enabled);
        if (mButton != null) {
            checkSelectionStatus();
        }
    }

    private List<CityTimeZone> loadTimeZones() {
        ArrayList<CityTimeZone> timeZones = new ArrayList<CityTimeZone>();
        Resources res = mContext.getResources();
        final long date = Calendar.getInstance().getTimeInMillis();
        mDefaultTimeZone = buildCityTimeZone(TimeZone.getDefault().getID(), date);
        String[] ids = res.getStringArray(org.omnirom.deskclock.R.array.cities_tz);
        for (String id : ids) {
            CityTimeZone zone = buildCityTimeZone(id, date);
            if (!timeZones.contains(zone)) {
                timeZones.add(zone);
            }
        }
        return timeZones;
    }

    private CityTimeZone buildCityTimeZone(String id, long date) {
        return buildCityTimeZone(TimeZone.getTimeZone(id), date);
    }

    private CityTimeZone buildCityTimeZone(final TimeZone tz, long date) {
        final int offset = tz.getOffset(date);
        final int p = Math.abs(offset);
        final boolean inDst = tz.inDaylightTime(new Date(date));

        CityTimeZone timeZone = new CityTimeZone();
        timeZone.mId = tz.getID();
        timeZone.mLabel = tz.getDisplayName(inDst, TimeZone.LONG);
        timeZone.mSign = offset < 0 ? -1 : 1;
        timeZone.mHours = p / (HOURS_1);
        timeZone.mMinutes = (p / 60000) % 60;
        timeZone.mHasDst = tz.useDaylightTime();

        return timeZone;
    }

    private CityTimeZone toCityTimeZone(TZ info) {
        final long date = Calendar.getInstance().getTimeInMillis();
        final String id;

        if (Arrays.binarySearch(TimeZone.getAvailableIDs(), info.name) < 0) {
            int seconds = info.offset < 0 ? -info.offset : info.offset;
            int hours = seconds / 3600;
            int minutes = (seconds - (hours * 3600)) / 60;
            id = String.format("GMT%s%02d%02d", info.offset < 0 ? "-" : "+", hours, minutes);
        } else {
            id = info.name;
        }

        return buildCityTimeZone(id, date);
    }

    private void checkSelectionStatus() {
        String name = mCityName.getText().toString().toLowerCase();
        String tz = null;
        if (mTimeZones.getSelectedItem() != null) {
            tz = mTimeZones.getSelectedItem().toString();
        }
        boolean enabled =
                !mLoadingTz &&
                mCityName.isEnabled() && !TextUtils.isEmpty(name) &&
                mTimeZones.isEnabled() && !TextUtils.isEmpty(tz);
        mButton.setEnabled(enabled);
    }

    private void checkLocationAvailability() {
        boolean gpsEnabled = mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = mLocationMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        mGps.setEnabled(gpsEnabled || networkEnabled);
    }

    private void requestLocation() {
        Looper looper = mContext.getMainLooper();

        mHandler.postDelayed(mLocationTimeout, LOCATION_REQUEST_TIMEOUT);
        mLocationRequesting = true;
        mCityName.setEnabled(false);
        mCityName.setText(org.omnirom.deskclock.R.string.cities_add_searching);
        mTimeZones.setEnabled(false);
        mGps.setImageResource(org.omnirom.deskclock.R.drawable.ic_gps_anim);
        try {
            ((AnimationDrawable)mGps.getDrawable()).start();
        } catch (Exception ex) {
            // Ignore
        }

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            Location location = mLocationMgr.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (location != null && location.getAccuracy() > LOCATION_ACCURACY_THRESHOLD_METERS) {
                location = null;
            }

            // If lastKnownLocation is not present (because none of the apps in the
            // device has requested the current location to the system yet) or outdated,
            // then try to get the current location use the provider that best matches the criteria.
            boolean needsUpdate = location == null;
            if (location != null) {
                long delta = System.currentTimeMillis() - location.getTime();
                needsUpdate = delta > OUTDATED_LOCATION_THRESHOLD_MILLIS;
            }
            if (needsUpdate) {
                String locationProvider = mLocationMgr.getBestProvider(sLocationCriteria, true);
                if (locationProvider != null) {
                    LocationProvider lp = mLocationMgr.getProvider(locationProvider);
                    if (lp != null) {
                        mLocationMgr.requestSingleUpdate(locationProvider, this, looper);
                    }
                }
            } else {
                onLocationChanged(location);
            }
        }
    }

    private void cancelRequestLocation() {
        mHandler.removeCallbacks(mLocationTimeout);
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationMgr.removeUpdates(this);
        }
        mLocationRequesting = false;
        mCityName.setText("");
        mCityName.setEnabled(true);
        mTimeZones.setEnabled(true);
        stopGpsAnimation();
        mGps.setImageResource(org.omnirom.deskclock.R.drawable.ic_gps);
    }

    private void stopGpsAnimation() {
        Drawable gpsDrawable = mGps.getDrawable();
        if (gpsDrawable instanceof AnimationDrawable) {
            ((AnimationDrawable) gpsDrawable).stop();
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        String name = mCityName.getText().toString();
        CityTimeZone ctz = null;
        if (mTimeZones.getSelectedItem() != null) {
            ctz = (CityTimeZone)mTimeZones.getSelectedItem();
        }
        if (ctz != null && mListener != null) {
            mListener.onCitySelected(name, ctz.mId);
        }
    }

    /* package */ void show() {
        mDialog.show();
        mTzLoadTask.execute();
        mButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        mButton.setEnabled(false);
    }

    /* package */ void dismiss() {
        mDialog.dismiss();
        if (mTzLoadTask.getStatus() == AsyncTask.Status.RUNNING) {
            mTzLoadTask.cancel(true);
        }
        if (mLocator != null) {
            mLocator.cancel();
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        String name = mCityName.getText().toString();
        int tz = -1;
        if (mTimeZones.getSelectedItem() != null) {
            tz = mTimeZones.getSelectedItemPosition();
        }

        outState.putString(STATE_CITY_NAME, name);
        outState.putInt(STATE_CITY_TIMEZONE, tz);
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        String name = savedInstanceState.getString(STATE_CITY_NAME);
        if (name != null) {
            mCityName.setText(name);
        }
        mSavedTimeZonePos = savedInstanceState.getInt(STATE_CITY_TIMEZONE, -1);
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        checkSelectionStatus();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        checkSelectionStatus();
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void afterTextChanged(Editable s) {
        if (mButton != null) {
            checkSelectionStatus();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!mLocationRequesting) return;
        mLocationRequesting = false;
        mHandler.removeCallbacks(mLocationTimeout);
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationMgr.removeUpdates(this);
        }
        mLocator = new CityAndTimeZoneLocator(
                mContext, location, mConnectivityMgr, new OnCityAndTimeZoneLocatedCallback() {
            @Override
            @SuppressWarnings("unchecked")
            public void onCityAndTimeZoneLocated(String city, TZ timezone) {
                CityTimeZone ctz = toCityTimeZone(timezone);
                int pos = ((ArrayAdapter<CityTimeZone>)mTimeZones.getAdapter()).getPosition(ctz);
                if (pos == -1) {
                    // This mean you are in the middle of the ocean and Android doesn't have
                    // a timezone definition for you.
                    pos = mDefaultTimeZonePos;
                }

                // Update the views with the new information
                updateViews(city, pos);
            }

            @Override
            public void onNoCityAndTimeZoneLocateResults() {
                //No results
                Toast.makeText(mContext, org.omnirom.deskclock.R.string.cities_add_gps_no_results,
                        Toast.LENGTH_SHORT).show();
                updateViews("", -1);
            }

            @Override
            public void onCityAndTimeZoneLocateError() {
                // Not available
                Toast.makeText(mContext, org.omnirom.deskclock.R.string.cities_add_gps_not_available,
                        Toast.LENGTH_SHORT).show();
                updateViews("", -1);
            }

            private void updateViews(String city, int tz) {
                mCityName.setText(city);
                mCityName.setEnabled(true);
                if (tz != -1) {
                    mTimeZones.setSelection(tz);
                }
                mTimeZones.setEnabled(true);
                stopGpsAnimation();
                mGps.setImageResource(org.omnirom.deskclock.R.drawable.ic_gps);
                checkSelectionStatus();
            }
        });
        mLocator.resolve();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
        checkLocationAvailability();
    }

    @Override
    public void onProviderDisabled(String provider) {
        checkLocationAvailability();
    }
}
