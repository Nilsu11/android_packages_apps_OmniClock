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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Filter;
import android.widget.Filter.FilterListener;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SearchView.OnQueryTextListener;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.MenuItemCompat.OnActionExpandListener;

import org.omnirom.deskclock.R;
import org.omnirom.deskclock.Utils;
import org.omnirom.deskclock.worldclock.db.DbCities;
import org.omnirom.deskclock.worldclock.db.DbCity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Cities chooser for the world clock
 */
public class CitiesActivity extends AppCompatActivity implements OnCheckedChangeListener,
        View.OnClickListener, View.OnLongClickListener, OnQueryTextListener,
        AddCityDialog.OnCitySelected, FilterListener {

    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_SEARCH_MODE = "search_mode";
    private static final String KEY_LIST_POSITION = "list_position";
    private static final String KEY_CITY_DIALOG = "city_dialog";

    private static final String PREF_SORT = "sort_preference";

    private static final int SORT_BY_NAME = 0;
    private static final int SORT_BY_GMT_OFFSET = 1;

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;

    /**
     * This must be false for production. If true, turns on logging, test code,
     * etc.
     */
    static final boolean DEBUG = false;
    static final String TAG = "CitiesActivity";

    private LayoutInflater mFactory;
    private ListView mCitiesList;
    private CityAdapter mAdapter;
    private ListView mSelectedCitiesList;
    private CityAdapter mSelectedCitiesAdapter;
    private HashMap<String, CityObj> mUserSelectedCities;
    private Calendar mCalendar;
    private AddCityDialog mAddCityDialog;

    private SearchView mSearchView;
    private StringBuffer mQueryTextBuffer = new StringBuffer();
    private boolean mSearchMode;
    private int mPosition = -1;

    private SharedPreferences mPrefs;
    private int mSortType;

    private class SearchActionExpandListener implements OnActionExpandListener {
        private Menu mMenu;

        public SearchActionExpandListener(Menu menu) {
            super();
            mMenu = menu;
        }

        @Override
        public boolean onMenuItemActionExpand(MenuItem item) {
            MenuItem menuItem = mMenu.findItem(org.omnirom.deskclock.R.id.menu_item_add);
            if (menuItem != null) {
                menuItem.setVisible(false);
            }
            return true;
        }

        @Override
        public boolean onMenuItemActionCollapse(MenuItem item) {
            MenuItem menuItem = mMenu.findItem(org.omnirom.deskclock.R.id.menu_item_add);
            if (menuItem != null) {
                menuItem.setVisible(true);
            }
            return true;
        }
    }

    /***
     * Adapter for a list of cities with the respected time zone. The Adapter
     * sorts the list alphabetically and create an indexer.
     ***/
    private class CityAdapter extends BaseAdapter implements Filterable, SectionIndexer {
        private static final int VIEW_TYPE_CITY = 0;
        private static final int VIEW_TYPE_HEADER = 1;

        private static final String DELETED_ENTRY = "C0";

        private List<CityObj> mDisplayedCitiesList;

        private CityObj[] mCities;
        private CityObj[] mSelectedCities;

        private final int mLayoutDirection;

        // A map that caches names of cities in local memory.  The names in this map are
        // preferred over the names of the selected cities stored in SharedPreferences, which could
        // be in a different language.  This map gets reloaded on a locale change, when the new
        // language's city strings are read from the xml file.
        private HashMap<String, String> mCityNameMap = new HashMap<String, String>();

        private String[] mSectionHeaders;
        private Integer[] mSectionPositions;

        private CityNameComparator mSortByNameComparator = new CityNameComparator();
        private CityGmtOffsetComparator mSortByTimeComparator = new CityGmtOffsetComparator();

        private final LayoutInflater mInflater;
        private boolean mIs24HoursMode; // AM/PM or 24 hours mode

        private final String mPattern12;
        private final String mPattern24;

        private final int mNormalCityFgColor;
        private final int mUserDefinedCityFgColor;

        private CityObj mSelected;
        private boolean mShowOnlySelectedCities;

        private Filter mFilter = new Filter() {

            @Override
            protected synchronized FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                String modifiedQuery = constraint.toString().trim().toUpperCase();

                ArrayList<CityObj> filteredList = new ArrayList<CityObj>();
                ArrayList<String> sectionHeaders = new ArrayList<String>();
                ArrayList<Integer> sectionPositions = new ArrayList<Integer>();

                // Update the list first when user using search filter
                final Collection<CityObj> selectedCities = mUserSelectedCities.values();
                mSelectedCities = selectedCities.toArray(new CityObj[selectedCities.size()]);

                long currentTime = System.currentTimeMillis();
                String val = null;
                int offset = -100000; //some value that cannot be a real offset
                for (CityObj city : mCities) {

                    // If the city is a deleted entry, ignore it.
                    if (city.mCityId.equals(DELETED_ENTRY)) {
                        continue;
                    }

                    // If the search query is empty, add section headers.
                    if (TextUtils.isEmpty(modifiedQuery)) {
                        // If the list is sorted by name, and the city has an index
                        // different than the previous city's index, update the section header.
                        if (mSortType == SORT_BY_NAME
                                && !city.mCityIndex.equals(val)) {
                            val = city.mCityIndex.toUpperCase();
                            sectionHeaders.add(val);
                            sectionPositions.add(filteredList.size());
                            city.isHeader = true;
                        } else {
                            city.isHeader = false;
                        }

                        // If the list is sorted by time, and the gmt offset is different than
                        // the previous city's gmt offset, insert a section header.
                        if (mSortType == SORT_BY_GMT_OFFSET) {
                            TimeZone timezone = TimeZone.getTimeZone(city.mTimeZone);
                            int newOffset = timezone.getOffset(currentTime);
                            if (offset != newOffset) {
                                offset = newOffset;
                                String offsetString = Utils.getGMTHourOffset(timezone, false, false);
                                sectionHeaders.add(offsetString);
                                sectionPositions.add(filteredList.size());
                                city.isHeader = true;
                            } else {
                                city.isHeader = false;
                            }
                        }

                        filteredList.add(city);
                    } else {
                        // If the city name begins with the non-empty query, add it into the list.
                        String cityName = city.mCityName.trim().toUpperCase();
                        if (city.mCityId != null && cityName.startsWith(modifiedQuery)) {
                            city.isHeader = false;
                            filteredList.add(city);
                        }
                    }
                }

                mSectionHeaders = sectionHeaders.toArray(new String[sectionHeaders.size()]);
                mSectionPositions = sectionPositions.toArray(new Integer[sectionPositions.size()]);

                results.values = filteredList;
                results.count = filteredList.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                mDisplayedCitiesList = (ArrayList<CityObj>) results.values;
                if (mPosition >= 0) {
                    mCitiesList.setSelectionFromTop(mPosition, 0);
                    mPosition = -1;
                }
                notifyDataSetChanged();
            }
        };

        public CityAdapter(
                Context context, LayoutInflater factory, boolean showOnlySelectedCities) {
            super();
            mShowOnlySelectedCities = showOnlySelectedCities;
            mCalendar = Calendar.getInstance();
            mCalendar.setTimeInMillis(System.currentTimeMillis());
            mLayoutDirection = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault());
            mInflater = factory;

            Resources res = context.getResources();

            mNormalCityFgColor = Utils.getColorAttr(context, android.R.attr.textColorPrimary);
            mUserDefinedCityFgColor = res.getColor(org.omnirom.deskclock.R.color.primary);

            mPattern24 = DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm");

            // There's an RTL layout bug that causes jank when fast-scrolling through
            // the list in 12-hour mode in an RTL locale. We can work around this by
            // ensuring the strings are the same length by using "hh" instead of "h".
            String pattern12 = DateFormat.getBestDateTimePattern(Locale.getDefault(), "hma");
            if (mLayoutDirection == View.LAYOUT_DIRECTION_RTL) {
                pattern12 = pattern12.replaceAll("h", "hh");
            }
            mPattern12 = pattern12;

            loadCitiesDatabase(context, null);
        }

        protected void loadCitiesDatabase(Context context, final CityObj selected) {
            // Re-organize the selected cities into an array.
            Collection<CityObj> selectedCities = mUserSelectedCities.values();
            mSelectedCities = selectedCities.toArray(new CityObj[selectedCities.size()]);

            if (mShowOnlySelectedCities) {
                mCities = mSelectedCities;
            } else {
                // Load the cities from xml.
                mCities = Utils.loadCitiesFromXml(context);
            }

            // Reload the city name map with the recently parsed city names of the currently
            // selected language for use with selected cities.
            mCityNameMap.clear();
            for (CityObj city : mCities) {
                mCityNameMap.put(city.mCityId, city.mCityName);
            }

            // Override the selected city names in the shared preferences with the
            // city names in the updated city name map, which will always reflect the
            // current language.
            for (CityObj city : mSelectedCities) {
                String newCityName = mCityNameMap.get(city.mCityId);
                if (newCityName != null) {
                    city.mCityName = newCityName;
                }
            }

            refreshSelectedCities(selected);
            set24HoursMode(context);
        }

        public void refreshSelectedCities(final CityObj selected) {
            Collection<CityObj> selectedCities = mUserSelectedCities.values();
            mSelectedCities = selectedCities.toArray(new CityObj[selectedCities.size()]);
            if (mShowOnlySelectedCities) {
                mCities = mSelectedCities;
            }
            sortCities(selected);
        }

        private void sortCities(final CityObj selected) {
            Arrays.sort(mCities, mSortType == SORT_BY_NAME ? mSortByNameComparator
                    : mSortByTimeComparator);
            if (mSelectedCities != null) {
                Arrays.sort(mSelectedCities, mSortType == SORT_BY_NAME ? mSortByNameComparator
                        : mSortByTimeComparator);
            }
            mPrefs.edit().putInt(PREF_SORT, mSortType).commit();
            mSelected = selected;
            mFilter.filter(mQueryTextBuffer.toString(), CitiesActivity.this);
        }

        @Override
        public int getCount() {
            return mDisplayedCitiesList != null ? mDisplayedCitiesList.size() : 0;
        }

        @Override
        public Object getItem(int p) {
            if (mDisplayedCitiesList != null && p >= 0 && p < mDisplayedCitiesList.size()) {
                return mDisplayedCitiesList.get(p);
            }
            return null;
        }

        @Override
        public long getItemId(int p) {
            return p;
        }

        protected int getPosition(CityObj o) {
            int count = mDisplayedCitiesList.size();
            for (int i = 0; i < count; i++) {
                CityObj c = (CityObj) mDisplayedCitiesList.get(i);
                if (c.mCityId != null && o.mCityId.compareTo(c.mCityId) == 0) {
                    return i;
                }
            }
            return -1;
        }

        protected CityObj getItem(String name, String tz) {
            int count = mCities.length;
            for (int i = 0; i < count; i++) {
                CityObj c = (CityObj) mCities[i];
                if (c.mCityId != null) {
                    CharSequence tz1 = getTimeCharSequence(tz);
                    CharSequence tz2 = getTimeCharSequence(c.mTimeZone);
                    if (name.compareToIgnoreCase(c.mCityName) == 0 && tz1.equals(tz2)) {
                        return c;
                    }
                }
            }
            return null;
        }

        @Override
        public boolean isEnabled(int p) {
            return mDisplayedCitiesList != null && mDisplayedCitiesList.get(p).mCityId != null;
        }

        @Override
        public synchronized View getView(int position, View view, ViewGroup parent) {
            if (mDisplayedCitiesList == null || position < 0
                    || position >= mDisplayedCitiesList.size()) {
                return null;
            }
            CityObj c = mDisplayedCitiesList.get(position);
            // Header view: A CityObj with nothing but the "selected cities" label
            if (c.mCityId == null) {
                if (view == null) {
                    view = mInflater.inflate(org.omnirom.deskclock.R.layout.city_list_header, parent, false);
                }
            } else { // City view
                // Make sure to recycle a City view only
                if (view == null) {
                    view = mInflater.inflate(org.omnirom.deskclock.R.layout.city_list_item, parent, false);
                    final CityViewHolder holder = new CityViewHolder();
                    holder.index = (TextView) view.findViewById(org.omnirom.deskclock.R.id.index);
                    holder.name = (TextView) view.findViewById(org.omnirom.deskclock.R.id.city_name);
                    holder.time = (TextView) view.findViewById(org.omnirom.deskclock.R.id.city_time);
                    holder.selected = (CheckBox) view.findViewById(org.omnirom.deskclock.R.id.city_onoff);
                    view.setTag(holder);
                }
                view.setOnClickListener(CitiesActivity.this);
                view.setOnLongClickListener(CitiesActivity.this);
                CityViewHolder holder = (CityViewHolder) view.getTag();

                holder.selected.setTag(c);
                holder.selected.setChecked(mUserSelectedCities.containsKey(c.mCityId));
                holder.selected.setOnCheckedChangeListener(CitiesActivity.this);
                holder.name.setText(c.mCityName, TextView.BufferType.SPANNABLE);
                holder.name.setTextColor(
                        c.mUserDefined ? mUserDefinedCityFgColor : mNormalCityFgColor);
                holder.time.setText(getTimeCharSequence(c.mTimeZone));
                if (c.isHeader) {
                    holder.index.setVisibility(View.VISIBLE);
                    if (mSortType == SORT_BY_NAME) {
                        holder.index.setText(c.mCityIndex);
                        holder.index.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                    } else { // SORT_BY_GMT_OFFSET
                        holder.index.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        holder.index.setText(Utils.getGMTHourOffset(
                                TimeZone.getTimeZone(c.mTimeZone), true, false));
                    }
                } else {
                    // If not a header, use the invisible index for left padding
                    holder.index.setVisibility(View.INVISIBLE);
                }
                // skip checkbox and other animations
                view.jumpDrawablesToCurrentState();
            }
            return view;
        }

        private CharSequence getTimeCharSequence(String timeZone) {
            mCalendar.setTimeZone(TimeZone.getTimeZone(timeZone));
            return DateFormat.format(mIs24HoursMode ? mPattern24 : mPattern12, mCalendar);
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return (mDisplayedCitiesList.get(position).mCityId != null)
                    ? VIEW_TYPE_CITY : VIEW_TYPE_HEADER;
        }

        private class CityViewHolder {
            TextView index;
            TextView name;
            TextView time;
            CheckBox selected;
        }

        public void set24HoursMode(Context c) {
            mIs24HoursMode = DateFormat.is24HourFormat(c);
            notifyDataSetChanged();
        }

        @Override
        public int getPositionForSection(int section) {
            return !isEmpty(mSectionPositions) ? mSectionPositions[section] : 0;
        }


        @Override
        public int getSectionForPosition(int p) {
            final Integer[] positions = mSectionPositions;
            if (!isEmpty(positions)) {
                for (int i = 0; i < positions.length - 1; i++) {
                    if (p >= positions[i]
                            && p < positions[i + 1]) {
                        return i;
                    }
                }
                if (p >= positions[positions.length - 1]) {
                    return positions.length - 1;
                }
            }
            return 0;
        }

        @Override
        public Object[] getSections() {
            return mSectionHeaders;
        }

        @Override
        public Filter getFilter() {
            return mFilter;
        }

        private boolean isEmpty(Object[] array) {
            return array == null || array.length == 0;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Utils.getThemeResourceId(this));

        super.onCreate(savedInstanceState);
        mFactory = LayoutInflater.from(this);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSortType = mPrefs.getInt(PREF_SORT, SORT_BY_NAME);
        if (savedInstanceState != null) {
            mQueryTextBuffer.append(savedInstanceState.getString(KEY_SEARCH_QUERY));
            mSearchMode = savedInstanceState.getBoolean(KEY_SEARCH_MODE);
            mPosition = savedInstanceState.getInt(KEY_LIST_POSITION);
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        updateLayout();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString(KEY_SEARCH_QUERY, mQueryTextBuffer.toString());
        bundle.putBoolean(KEY_SEARCH_MODE, mSearchMode);
        bundle.putInt(KEY_LIST_POSITION, mCitiesList.getFirstVisiblePosition());
        if (mAddCityDialog != null) {
            bundle.putBoolean(KEY_CITY_DIALOG, true);
            mAddCityDialog.onSaveInstanceState(bundle);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.getBoolean(KEY_CITY_DIALOG, false)) {
            showAddCityDialog(savedInstanceState);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void updateLayout() {
        setContentView(org.omnirom.deskclock.R.layout.cities_activity);
        mCitiesList = (ListView) findViewById(org.omnirom.deskclock.R.id.cities_list);
        mCitiesList.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);

        mSelectedCitiesList = (ListView) findViewById(org.omnirom.deskclock.R.id.selected_cities_list);
        mSelectedCitiesList.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);

        setFastScroll(TextUtils.isEmpty(mQueryTextBuffer.toString().trim()));

        mUserSelectedCities = Cities.readCitiesFromSharedPrefs(
                PreferenceManager.getDefaultSharedPreferences(this));
        mAdapter = new CityAdapter(this, mFactory, false);
        mCitiesList.setAdapter(mAdapter);

        mSelectedCitiesAdapter = new CityAdapter(this, mFactory, true);
        mSelectedCitiesList.setAdapter(mSelectedCitiesAdapter);
    }

    private void setFastScroll(boolean enabled) {
        if (mCitiesList != null) {
            mCitiesList.setFastScrollAlwaysVisible(enabled);
            mCitiesList.setFastScrollEnabled(enabled);
        }
        if (mSelectedCitiesList != null) {
            mSelectedCitiesList.setFastScrollAlwaysVisible(enabled);
            mSelectedCitiesList.setFastScrollEnabled(enabled);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mAdapter.set24HoursMode(this);
        }
        if (mSelectedCitiesAdapter != null) {
            mSelectedCitiesAdapter.set24HoursMode(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mAddCityDialog != null) {
            mAddCityDialog.dismiss();
            mAddCityDialog = null;
        }
        Cities.saveCitiesToSharedPrefs(PreferenceManager.getDefaultSharedPreferences(this),
                mUserSelectedCities);
        Intent i = new Intent(Cities.WORLDCLOCK_UPDATE_INTENT);
        sendBroadcast(i);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else if (R.id.menu_item_add == item.getItemId()) {
            showAddCityDialog();
            return true;
        } else if (R.id.menu_item_sort == item.getItemId()) {
            toggleSort();
            setFastScroll(TextUtils.isEmpty(mQueryTextBuffer.toString().trim()));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void toggleSort() {
        if (mSortType == SORT_BY_NAME) {
            mSortType = SORT_BY_GMT_OFFSET;
        } else {
            mSortType = SORT_BY_NAME;
        }
        if (mAdapter != null) {
            mAdapter.sortCities(null);
        }
        if (mSelectedCitiesAdapter != null) {
            mSelectedCitiesAdapter.sortCities(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(org.omnirom.deskclock.R.menu.cities_menu, menu);

        MenuItem search = menu.findItem(org.omnirom.deskclock.R.id.menu_item_search);
        if (search != null) {
            MenuItemCompat.setOnActionExpandListener(search, new SearchActionExpandListener(menu));
        }

        MenuItem searchMenu = menu.findItem(org.omnirom.deskclock.R.id.menu_item_search);
        mSearchView = (SearchView) MenuItemCompat.getActionView(searchMenu);
        mSearchView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        mSearchView.setOnSearchClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                mSearchMode = true;
            }
        });
        mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {

            @Override
            public boolean onClose() {
                mSearchMode = false;
                return false;
            }
        });
        if (mSearchView != null) {
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setQuery(mQueryTextBuffer.toString(), false);
            if (mSearchMode) {
                mSearchView.requestFocus();
                mSearchView.setIconified(false);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem sortMenuItem = menu.findItem(org.omnirom.deskclock.R.id.menu_item_sort);
        if (mSortType == SORT_BY_NAME) {
            sortMenuItem.setTitle(getString(org.omnirom.deskclock.R.string.menu_item_sort_by_gmt_offset));
        } else {
            sortMenuItem.setTitle(getString(org.omnirom.deskclock.R.string.menu_item_sort_by_name));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCheckedChanged(CompoundButton b, boolean checked) {
        CityObj c = (CityObj) b.getTag();
        if (checked) {
            mUserSelectedCities.put(c.mCityId, c);
        } else {
            mUserSelectedCities.remove(c.mCityId);
        }
    }

    @Override
    public void onClick(View v) {
        CompoundButton b = (CompoundButton) v.findViewById(org.omnirom.deskclock.R.id.city_onoff);
        boolean checked = b.isChecked();
        onCheckedChanged(b, checked);
        b.setChecked(!checked);
        mAdapter.refreshSelectedCities(null);
        mSelectedCitiesAdapter.refreshSelectedCities(null);
    }

    @Override
    public boolean onLongClick(View v) {
        CompoundButton b = (CompoundButton) v.findViewById(org.omnirom.deskclock.R.id.city_onoff);
        final CityObj c = (CityObj) b.getTag();
        if (c != null && c.mUserDefined) {
            deleteCity(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean onQueryTextChange(String queryText) {
        mQueryTextBuffer.setLength(0);
        mQueryTextBuffer.append(queryText);
        setFastScroll(TextUtils.isEmpty(mQueryTextBuffer.toString().trim()));
        mAdapter.getFilter().filter(queryText, this);
        mSelectedCitiesAdapter.getFilter().filter(queryText, this);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String arg0) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager.isActive()) {
            inputMethodManager.hideSoftInputFromWindow(CitiesActivity.this.getCurrentFocus()
                    .getWindowToken(), 0);
        }
        return false;
    }

    @Override
    public void onFilterComplete(int count) {
        if (mAdapter.mSelected != null) {
            mCitiesList.setSelection(mAdapter.getPosition(mAdapter.mSelected));
            mAdapter.mSelected = null;
        }
    }

    private void showAddCityDialog(Bundle savedInstance) {
        mAddCityDialog = new AddCityDialog(this, mFactory, this);
        if (savedInstance != null) {
            mAddCityDialog.onRestoreInstanceState(savedInstance);
        }
        mAddCityDialog.show();
    }

    private void showAddCityDialog() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } else {
            showAddCityDialog(null);
        }
   }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showAddCityDialog(null);
                }
            }
            return;
        }
    }

    @Override
    public void onCitySelected(String name, String tz) {
        // If city name and timezone exists, then don't add it
        CityObj city = mAdapter.getItem(name, tz);
        if (city != null) {
            // The city already exists
            Toast.makeText(this, org.omnirom.deskclock.R.string.cities_add_already_exists,
                    Toast.LENGTH_SHORT).show();
            mCitiesList.setSelection(mAdapter.getPosition(city));
            return;
        }

        DbCity dbCity = new DbCity();
        dbCity.name = name;
        dbCity.tz = tz;
        long id = DbCities.addCity(this, dbCity);
        if (id < 0) {
            // Something went wrong
            Toast.makeText(this, org.omnirom.deskclock.R.string.cities_add_city_failed,
                    Toast.LENGTH_SHORT).show();
        } else {
            CityObj o = new CityObj(name, tz, "UD" + id, name.substring(0, 1));
            o.mUserDefined = true;
            mAdapter.loadCitiesDatabase(this, o);
            mSelectedCitiesAdapter.loadCitiesDatabase(this, o);
            mUserSelectedCities.put(o.mCityId, o);
            mAdapter.refreshSelectedCities(o);
            mSelectedCitiesAdapter.refreshSelectedCities(o);
            mCitiesList.invalidate();
            mCitiesList.setSelectionAfterHeaderView();
        }

        mAddCityDialog = null;
    }

    @Override
    public void onCancelCitySelection() {
        mAddCityDialog = null;
    }

    private void deleteCity(final CityObj c) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(org.omnirom.deskclock.R.string.cities_delete_city_title);
        builder.setMessage(getString(org.omnirom.deskclock.R.string.cities_delete_city_msg, c.mCityName));
        builder.setPositiveButton(getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int id = Integer.parseInt(c.mCityId.substring(2));
                        if (DbCities.deleteCity(CitiesActivity.this, id) > 0) {
                            // Remove from the list and from the selection
                            mUserSelectedCities.remove(c.mCityId);
                            mAdapter.loadCitiesDatabase(CitiesActivity.this, null);
                            mSelectedCitiesAdapter.loadCitiesDatabase(CitiesActivity.this, null);
                            mCitiesList.invalidate();
                        } else {
                            // Something went wrong
                            Toast.makeText(CitiesActivity.this, org.omnirom.deskclock.R.string.cities_delete_city_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        builder.setNegativeButton(getString(android.R.string.cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
