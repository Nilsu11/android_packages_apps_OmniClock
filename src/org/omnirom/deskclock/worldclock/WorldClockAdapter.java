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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.cardview.widget.CardView;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextClock;
import android.widget.TextView;

import org.omnirom.deskclock.AnalogClock;
import org.omnirom.deskclock.SettingsActivity;
import org.omnirom.deskclock.Utils;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

public class WorldClockAdapter extends BaseAdapter {
    protected Object [] mCitiesList;
    private final LayoutInflater mInflater;
    private final Context mContext;
    private String mClockStyle;
    private final Collator mCollator = Collator.getInstance();
    protected HashMap<String, CityObj> mCitiesDb = new HashMap<String, CityObj>();
    protected HashMap<String, CityObj> mCitiesMap = new HashMap<String, CityObj>();
    protected int mClocksPerRow;

    public WorldClockAdapter(Context context) {
        super();
        mContext = context;
        loadData(context);
        loadCitiesDb(context);
        mInflater = LayoutInflater.from(context);
        mClocksPerRow = context.getResources().getInteger(org.omnirom.deskclock.R.integer.world_clocks_per_row);
    }

    public void reloadData(Context context) {
        loadData(context);
        notifyDataSetChanged();
    }

    public void loadData(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mClockStyle = prefs.getString(SettingsActivity.KEY_CLOCK_STYLE,
                mContext.getResources().getString(org.omnirom.deskclock.R.string.default_clock_style));
        mCitiesMap = Cities.readCitiesFromSharedPrefs(prefs);
        mCitiesList = mCitiesMap.values().toArray();
        sortList();
        mCitiesList = addHomeCity();
    }

    public void loadCitiesDb(Context context) {
        mCitiesDb.clear();
        // Read the cities DB so that the names and timezones will be taken from the DB
        // and not from the selected list so that change of locale or changes in the DB will
        // be reflected.
        CityObj[] cities = Utils.loadCitiesFromXml(context);
        if (cities != null) {
            for (int i = 0; i < cities.length; i ++) {
                mCitiesDb.put(cities[i].mCityId, cities [i]);
            }
        }
    }

    /***
     * Adds the home city as the first item of the adapter if the feature is on and the device time
     * zone is different from the home time zone that was set by the user.
     * return the list of cities.
     */
    private Object[] addHomeCity() {
        if (needHomeCity()) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String homeTZ = sharedPref.getString(SettingsActivity.KEY_HOME_TZ, "");
            String homeName = mContext.getResources().getString(org.omnirom.deskclock.R.string.home_label);
            CityObj c = new CityObj(homeName, homeTZ, null, homeName.substring(0, 1));
            Object[] temp = new Object[mCitiesList.length + 1];
            temp[0] = c;
            for (int i = 0; i < mCitiesList.length; i++) {
                temp[i + 1] = mCitiesList[i];
            }
            return temp;
        } else {
            return mCitiesList;
        }
    }

    public void updateHomeLabel(Context context) {
        // Update the "home" label if the home time zone clock is shown
        if (needHomeCity() && mCitiesList.length > 0) {
            ((CityObj) mCitiesList[0]).mCityName =
                    context.getResources().getString(org.omnirom.deskclock.R.string.home_label);
        }
    }

    public boolean needHomeCity() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (sharedPref.getBoolean(SettingsActivity.KEY_AUTO_HOME_CLOCK, false)) {
            String homeTZ = sharedPref.getString(
                    SettingsActivity.KEY_HOME_TZ, TimeZone.getDefault().getID());
            final Date now = new Date();
            return TimeZone.getTimeZone(homeTZ).getOffset(now.getTime())
                    != TimeZone.getDefault().getOffset(now.getTime());
        } else {
            return false;
        }
    }

    public boolean hasHomeCity() {
        return (mCitiesList != null) && mCitiesList.length > 0
                && ((CityObj) mCitiesList[0]).mCityId == null;
    }

    private void sortList() {
        final Date now = new Date();

        // Sort by the Offset from GMT taking DST into account
        // and if the same sort by City Name
        Arrays.sort(mCitiesList, new Comparator<Object>() {
            private int safeCityNameCompare(CityObj city1, CityObj city2) {
                if (city1.mCityName == null && city2.mCityName == null) {
                    return 0;
                } else if (city1.mCityName == null) {
                    return -1;
                } else if (city2.mCityName == null) {
                    return 1;
                } else {
                    return mCollator.compare(city1.mCityName, city2.mCityName);
                }
            }

            @Override
            public int compare(Object object1, Object object2) {
                CityObj city1 = (CityObj) object1;
                CityObj city2 = (CityObj) object2;
                if (city1.mTimeZone == null && city2.mTimeZone == null) {
                    return safeCityNameCompare(city1, city2);
                } else if (city1.mTimeZone == null) {
                    return -1;
                } else if (city2.mTimeZone == null) {
                    return 1;
                }

                int gmOffset1 = TimeZone.getTimeZone(city1.mTimeZone).getOffset(now.getTime());
                int gmOffset2 = TimeZone.getTimeZone(city2.mTimeZone).getOffset(now.getTime());
                if (gmOffset1 == gmOffset2) {
                    return safeCityNameCompare(city1, city2);
                } else {
                    return gmOffset1 - gmOffset2;
                }
            }
        });
    }

    @Override
    public int getCount() {
        if (mClocksPerRow == 1) {
            // In the special case where we have only 1 clock per view.
            return mCitiesList.length;
        }

        // Otherwise, each item in the list holds 1 or 2 clocks
        return (mCitiesList.length  + 1)/2;
    }

    @Override
    public Object getItem(int p) {
        return null;
    }

    @Override
    public long getItemId(int p) {
        return p;
    }

    @Override
    public boolean isEnabled(int p) {
        return true;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        // Index in cities list
        int index = position * mClocksPerRow;
        if (index < 0 || index >= mCitiesList.length) {
            return null;
        }

        if (view == null) {
            view = mInflater.inflate(org.omnirom.deskclock.R.layout.world_clock_list_item, parent, false);
        }
        updateView(view, (CityObj)mCitiesList[index]);
        return view;
    }

    private void updateView(View clock, CityObj cityObj) {
        View nameLayout= clock.findViewById(org.omnirom.deskclock.R.id.city_name_layout);
        TextView name = (TextView)(nameLayout.findViewById(org.omnirom.deskclock.R.id.city_name));
        TextView dayOfWeek = (TextView)(nameLayout.findViewById(org.omnirom.deskclock.R.id.city_day));
        TextClock dclock = (TextClock)(clock.findViewById(org.omnirom.deskclock.R.id.digital_clock));
        AnalogClock aclock = (AnalogClock)(clock.findViewById(org.omnirom.deskclock.R.id.analog_clock));
        aclock.setWorldClock(true);

        CardView worldCLockCard = (CardView) clock.findViewById(org.omnirom.deskclock.R.id.world_clock_card);
        worldCLockCard.setCardBackgroundColor(Utils.getViewBackgroundColor(mContext));

        if (mClockStyle.equals("analog")) {
            dclock.setVisibility(View.GONE);
            aclock.setVisibility(View.VISIBLE);
            aclock.setTimeZone(cityObj.mTimeZone);
        } else {
            dclock.setVisibility(View.VISIBLE);
            aclock.setVisibility(View.GONE);
            dclock.setTimeZone(cityObj.mTimeZone);
            Utils.setTimeFormat(dclock, (int)(dclock.getTextSize() / 3), 0);
        }
        CityObj cityInDb = mCitiesDb.get(cityObj.mCityId);
        // Home city or city not in DB , use data from the save selected cities list
        name.setText(Utils.getCityName(cityObj, cityInDb));

        final Calendar now = Calendar.getInstance();
        now.setTimeZone(TimeZone.getDefault());
        int myDayOfWeek = now.get(Calendar.DAY_OF_WEEK);
        // Get timezone from cities DB if available
        String cityTZ = (cityInDb != null) ? cityInDb.mTimeZone : cityObj.mTimeZone;
        now.setTimeZone(TimeZone.getTimeZone(cityTZ));
        int cityDayOfWeek = now.get(Calendar.DAY_OF_WEEK);
        String secondLine = Utils.getGMTHourOffset(TimeZone.getTimeZone(cityTZ), true, true);
        if (myDayOfWeek != cityDayOfWeek) {
            String fmt = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                    mContext.getResources().getString(org.omnirom.deskclock.R.string.abbrev_wday_month_day_no_year));
            SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone(cityTZ));
            secondLine += " - " + sdf.format(now.getTime());
        }
        dayOfWeek.setText(secondLine);
    }
}
