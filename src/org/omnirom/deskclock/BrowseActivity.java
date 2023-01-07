/*
 *  Copyright (C) 2017 The OmniROM Project
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

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import androidx.legacy.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import org.omnirom.deskclock.alarms.AlarmConstants;
import org.omnirom.deskclock.provider.Alarm;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BrowseActivity extends Activity implements SearchView.OnQueryTextListener,
        StorageChooserDialog.ChosenStorageListener, PlaylistDialogFragment.PlaylistDialogHandler {

    public static final int QUERY_TYPE_ALARM = 0;
    public static final int QUERY_TYPE_RINGTONE = 1;
    public static final int QUERY_TYPE_RECENT = 2;
    public static final int QUERY_TYPE_ARTIST = 3;
    public static final int QUERY_TYPE_ALBUM = 4;
    public static final int QUERY_TYPE_TRACK = 5;
    public static final int QUERY_TYPE_FOLDER = 6;
    public static final int QUERY_TYPE_PLAYLIST = 7;
    public static final int QUERY_TYPE_STREAM = 8;
    public static final int QUERY_TYPE_UNKNOWN = -1;

    private static final String PREF_RECENT_URI = "local_recent_uri";
    private static final int RECENT_SIZE = 10;
    private static final int PERMISSIONS_REQUEST_EXTERNAL_STORAGE = 0;

    private ListView mQueryList;
    private List<QueryItem> mQueryResultList = new ArrayList<QueryItem>();
    private ArrayAdapter<QueryItem> mAdapter;
    private String mSelectedUri;
    private Alarm mAlarm;
    private SearchView mSearchView;
    private int mQueryType;
    private String mCurrentQueryText = "";
    private ProgressBar mProgress;
    private View mFooterView;
    private TextView mQueryTypeText;
    private List<QueryItem> mQueryResultListCopy = new ArrayList<QueryItem>();
    private View mPlaylistTab;
    private View mArtistTab;
    private View mAlbumTab;
    private View mTrackTab;
    private View mRecentTab;
    private View mStreamTab;
    private View mFolderTab;
    private View mAlarmsTab;
    private boolean mPreAlarm;
    private List<QueryItem> mAlarms;
    private List<QueryItem> mRingtones;
    private TextView mChooseFolder;
    private boolean mHasStoragePerms;
    private View mPasteUrl;
    private EditText mPasteUrlText;
    private boolean mLimitedMode;
    private int mPrimaryColor;
    private int mPrimaryColorDisabled;

    private class QueryItem implements Comparable<QueryItem> {
        String mName;
        String mUri;
        String mSubText;
        int mQueryType;
        int mIconId;

        @Override
        public int compareTo(QueryItem o) {
            return mName.compareTo(o.mName);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (getIntent().hasExtra(AlarmConstants.DATA_COLOR_THEME_ID)) {
            int themeId = getIntent().getIntExtra(AlarmConstants.DATA_COLOR_THEME_ID, 0);
            setTheme(Utils.getThemeResourceId(this, themeId));
        } else if (getIntent().hasExtra(AlarmConstants.DATA_COLOR_THEME_LIGHT)) {
            boolean lightTheme = getIntent().getBooleanExtra(AlarmConstants.DATA_COLOR_THEME_LIGHT, true);
            setTheme(Utils.getThemeResourceId(this, lightTheme ? 0 : 1));
        } else {
            setTheme(Utils.getThemeResourceId(this));
        }

        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mPrimaryColor = getResources().getColor(android.R.color.white);
        mPrimaryColorDisabled = Utils.setColorAlpha(mPrimaryColor,
                getResources().getInteger(org.omnirom.deskclock.R.integer.disabled_text_alpha));

        if (getIntent().hasExtra(AlarmConstants.DATA_ALARM_EXTRA)) {
            mAlarm = getIntent().getParcelableExtra(AlarmConstants.DATA_ALARM_EXTRA);
            if (mAlarm != null) {
                mSelectedUri = mAlarm.alert.toString();
                mPreAlarm = mAlarm.preAlarm;
            }
        }
        if (mAlarm == null) {
            mAlarm = new Alarm();
            mPreAlarm = false;
        }
        if (getIntent().hasExtra(AlarmConstants.DATA_ALARM_EXTRA_URI)) {
            mSelectedUri = getIntent().getStringExtra(AlarmConstants.DATA_ALARM_EXTRA_URI);
            if (mSelectedUri != null) {
                mAlarm.alert = Uri.parse(mSelectedUri);
            }
        }
        if (getIntent().hasExtra(AlarmConstants.DATA_BROWSE_EXTRA_FALLBACK)) {
            mLimitedMode = getIntent().getBooleanExtra(AlarmConstants.DATA_BROWSE_EXTRA_FALLBACK, false);
        }

        setContentView(R.layout.browse_activity);
        mSearchView = (SearchView) findViewById(R.id.query_pattern);
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setSubmitButtonEnabled(false);
        mSearchView.setVisibility(View.INVISIBLE);
        mQueryTypeText = (TextView) findViewById(R.id.query_type_string);
        mQueryList = (ListView) findViewById(R.id.query_result);
        mFooterView = getLayoutInflater().inflate(R.layout.browse_footer_item, mQueryList, false);
        mProgress = (ProgressBar) mFooterView.findViewById(R.id.query_progressbar);
        mPasteUrl = findViewById(R.id.query_paste_url);
        mPasteUrlText = (EditText) findViewById(R.id.query_paste_url_field);
        ImageView pasteUrlButton = (ImageView) findViewById(R.id.query_paste_url_add);
        pasteUrlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!TextUtils.isEmpty(mPasteUrlText.getText().toString())) {
                    showPlaylistDialog();
                }
            }
        });
        mQueryList.addFooterView(mFooterView, null, false);
        mQueryType = QUERY_TYPE_RECENT;
        mQueryTypeText.setText(R.string.local_query_recent);
        mChooseFolder = (TextView) findViewById(R.id.query_folder_button);
        mChooseFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchStoragePicker();
            }
        });

        mAdapter = createListAdapter();
        mQueryList.setAdapter(mAdapter);

        mAlarmsTab = findViewById(R.id.tab_alarms);
        ((ImageView) mAlarmsTab.findViewById(R.id.tab_strip_image)).setImageResource(R.drawable.ic_alarm_white);
        ((TextView) mAlarmsTab.findViewById(R.id.tab_strip_title)).setText(R.string.local_query_alarm);
        mAlarmsTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchView.setVisibility(View.INVISIBLE);
                mChooseFolder.setVisibility(View.GONE);
                mPasteUrl.setVisibility(View.GONE);
                mQueryType = QUERY_TYPE_ALARM;
                mQueryTypeText.setText(R.string.local_query_alarm_ringtones);
                clearList();
                updateTabs();
                doQuery(mCurrentQueryText, 0);
            }
        });

        mRecentTab = findViewById(R.id.tab_recent);
        ((ImageView) mRecentTab.findViewById(R.id.tab_strip_image)).setImageResource(R.drawable.ic_star_white);
        ((TextView) mRecentTab.findViewById(R.id.tab_strip_title)).setText(R.string.local_query_recent);
        mRecentTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchView.setVisibility(View.INVISIBLE);
                mChooseFolder.setVisibility(View.GONE);
                mPasteUrl.setVisibility(View.GONE);
                mQueryType = QUERY_TYPE_RECENT;
                mQueryTypeText.setText(R.string.local_query_recent);
                clearList();
                updateTabs();
                doQuery(mCurrentQueryText, 0);
            }
        });

        mArtistTab = findViewById(R.id.tab_artist);
        ((ImageView) mArtistTab.findViewById(R.id.tab_strip_image)).setImageResource(R.drawable.ic_artist_white);
        ((TextView) mArtistTab.findViewById(R.id.tab_strip_title)).setText(R.string.local_query_artist);
        mArtistTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchView.setVisibility(View.VISIBLE);
                mChooseFolder.setVisibility(View.GONE);
                mPasteUrl.setVisibility(View.GONE);
                mQueryType = QUERY_TYPE_ARTIST;
                mQueryTypeText.setText(R.string.local_query_artist);
                clearList();
                updateTabs();
                doQuery(mCurrentQueryText, 0);
            }
        });

        mAlbumTab = findViewById(R.id.tab_album);
        ((ImageView) mAlbumTab.findViewById(R.id.tab_strip_image)).setImageResource(R.drawable.ic_album_white);
        ((TextView) mAlbumTab.findViewById(R.id.tab_strip_title)).setText(R.string.local_query_album);
        mAlbumTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchView.setVisibility(View.VISIBLE);
                mChooseFolder.setVisibility(View.GONE);
                mPasteUrl.setVisibility(View.GONE);
                mQueryType = QUERY_TYPE_ALBUM;
                mQueryTypeText.setText(R.string.local_query_album);
                clearList();
                updateTabs();
                doQuery(mCurrentQueryText, 0);
            }
        });

        mTrackTab = findViewById(R.id.tab_track);
        ((ImageView) mTrackTab.findViewById(R.id.tab_strip_image)).setImageResource(R.drawable.ic_track_white);
        ((TextView) mTrackTab.findViewById(R.id.tab_strip_title)).setText(R.string.local_query_track);
        mTrackTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchView.setVisibility(View.VISIBLE);
                mChooseFolder.setVisibility(View.GONE);
                mPasteUrl.setVisibility(View.GONE);
                mQueryType = QUERY_TYPE_TRACK;
                mQueryTypeText.setText(R.string.local_query_track);
                clearList();
                updateTabs();
                doQuery(mCurrentQueryText, 0);
            }
        });

        mFolderTab = findViewById(R.id.tab_folder);
        ((ImageView) mFolderTab.findViewById(R.id.tab_strip_image)).setImageResource(R.drawable.ic_folder_white);
        ((TextView) mFolderTab.findViewById(R.id.tab_strip_title)).setText(R.string.local_query_folder);
        mFolderTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchView.setVisibility(View.INVISIBLE);
                mChooseFolder.setVisibility(View.VISIBLE);
                mPasteUrl.setVisibility(View.GONE);
                mQueryType = QUERY_TYPE_FOLDER;
                mQueryTypeText.setText(R.string.local_query_folder);
                clearList();
                updateTabs();
                doQuery(mCurrentQueryText, 0);
            }
        });

        mPlaylistTab = findViewById(R.id.tab_playlist);
        ((ImageView) mPlaylistTab.findViewById(R.id.tab_strip_image)).setImageResource(R.drawable.ic_playlist_white);
        ((TextView) mPlaylistTab.findViewById(R.id.tab_strip_title)).setText(R.string.local_query_playlist);
        mPlaylistTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchView.setVisibility(View.VISIBLE);
                mChooseFolder.setVisibility(View.GONE);
                mPasteUrl.setVisibility(View.GONE);
                mQueryType = QUERY_TYPE_PLAYLIST;
                mQueryTypeText.setText(R.string.local_query_playlist);
                clearList();
                updateTabs();
                doQuery(mCurrentQueryText, 0);
            }
        });

        mStreamTab = findViewById(R.id.tab_stream);
        ((ImageView) mStreamTab.findViewById(R.id.tab_strip_image)).setImageResource(R.drawable.ic_earth_white);
        ((TextView) mStreamTab.findViewById(R.id.tab_strip_title)).setText(R.string.local_query_stream);
        mStreamTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchView.setVisibility(View.GONE);
                mChooseFolder.setVisibility(View.GONE);
                mPasteUrl.setVisibility(View.VISIBLE);
                mQueryType = QUERY_TYPE_STREAM;
                mQueryTypeText.setText(R.string.local_query_stream);
                clearList();
                updateTabs();
                doQuery(mCurrentQueryText, 0);
            }
        });

        if (mLimitedMode) {
            mStreamTab.setVisibility(View.GONE);
            mPlaylistTab.setVisibility(View.GONE);
        }
        mFolderTab.setVisibility(View.GONE);

        updateTabs();

        mQueryList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                QueryItem queryItem = mAdapter.getItem(position);
                if (queryItem.mQueryType != QUERY_TYPE_UNKNOWN) {
                    mSelectedUri = queryItem.mUri;
                    addToRecents(mSelectedUri);
                    Intent intent = new Intent();
                    intent.putExtra(AlarmConstants.DATA_ALARM_EXTRA_URI, mSelectedUri);
                    intent.putExtra(AlarmConstants.DATA_ALARM_EXTRA_NAME, queryItem.mName);
                    intent.putExtra(AlarmConstants.DATA_ALARM_EXTRA_TYPE, queryItem.mQueryType);
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                }
            }
        });

        mQueryList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                QueryItem queryItem = mAdapter.getItem(position);
                // TODO export as spotify open URL
                return true;
            }
        });

        cacheAlarmTones();
        cacheRingtones();

        View queryArea = findViewById(R.id.query_area);
        queryArea.setVisibility(View.VISIBLE);

        checkStoragePermissions();
    }

    @Override
    public void onPause() {
        super.onPause();
        closeAlarmTestDialog();
        closeStoragePicker();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private ArrayAdapter<QueryItem> createListAdapter() {
        return new ArrayAdapter<QueryItem>(this,
                R.layout.browse_item, mQueryResultList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View item = null;
                if (convertView == null) {
                    final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    item = inflater.inflate(R.layout.browse_item, null);
                } else {
                    item = convertView;
                }
                TextView title = (TextView) item.findViewById(R.id.item_text);
                TextView subTitle = (TextView) item.findViewById(R.id.item_subtext);
                ImageView icon = (ImageView) item.findViewById(R.id.item_icon);
                ImageView playIcon = (ImageView) item.findViewById(R.id.item_play);
                ImageView deleteIcon = (ImageView) item.findViewById(R.id.item_delete);

                final QueryItem queryItem = mQueryResultList.get(position);
                title.setText(queryItem.mName);
                if (!TextUtils.isEmpty(queryItem.mSubText)) {
                    subTitle.setVisibility(View.VISIBLE);
                    subTitle.setText(queryItem.mSubText);
                } else {
                    subTitle.setVisibility(View.GONE);
                }
                int iconId = queryItem.mIconId;
                icon.setImageResource(iconId);

                playIcon.setVisibility(queryItem.mQueryType != QUERY_TYPE_UNKNOWN ? View.VISIBLE : View.GONE);
                playIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        playUri(queryItem.mUri, queryItem.mName, queryItem.mIconId);
                    }
                });
                deleteIcon.setVisibility((queryItem.mQueryType == QUERY_TYPE_STREAM && mQueryType == QUERY_TYPE_STREAM) ? View.VISIBLE : View.GONE);
                deleteIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String fileUri = queryItem.mUri;
                        File f = new File(Uri.parse(fileUri).getPath());
                        if (f.exists()) {
                            f.delete();
                            clearList();
                            loadStreams();
                        }
                    }
                });
                return item;
            }
        };
    }

    private void clearList() {
        mQueryResultList.clear();
        mAdapter.notifyDataSetChanged();
        stopProgress();
    }

    private void startProgress() {
        mProgress.setVisibility(View.VISIBLE);
    }

    private void stopProgress() {
        mProgress.setVisibility(View.GONE);
    }

    private static Uri getUriFromCursor(Cursor cursor) {
        return ContentUris.withAppendedId(Uri.parse(cursor.getString(RingtoneManager.URI_COLUMN_INDEX)), cursor
                .getLong(RingtoneManager.ID_COLUMN_INDEX));
    }

    private void searchAlarmsAndRingtones(String query, final int startIndex) {
        startProgress();
        mQueryResultList.addAll(mAlarms);
        mQueryResultList.addAll(mRingtones);
        Collections.sort(mQueryResultList);
        mAdapter.notifyDataSetChanged();
        mQueryList.setSelection(0);
        stopProgress();
    }

    private void searchAlbums(final String query, final int startIndex) {
        if (!mHasStoragePerms) {
            return;
        }
        startProgress();
        final List<QueryItem> queryResultList = new ArrayList<QueryItem>();
        final int startQueryType = mQueryType;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... params) {
                String[] projection = {
                        MediaStore.Audio.Albums._ID,
                        MediaStore.Audio.Albums.ALBUM,
                        MediaStore.Audio.Albums.ARTIST
                };

                String selection = null;
                if (!TextUtils.isEmpty(query)) {
                    selection = MediaStore.Audio.Albums.ALBUM + " LIKE '%" + query + "%' ";
                }
                Cursor c = getContentResolver().query(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        MediaStore.Audio.Albums.ALBUM);

                while (c.moveToNext()) {
                    Uri album = Uri.withAppendedPath(
                            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                            String.valueOf(c.getLong(0)));
                    String title = c.getString(1);
                    String artist = c.getString(2);
                    if (!TextUtils.isEmpty(title)) {
                        QueryItem item = new QueryItem();
                        item.mName = title;
                        item.mUri = album.toString();
                        item.mSubText = artist;
                        item.mQueryType = QUERY_TYPE_ALBUM;
                        item.mIconId = R.drawable.ic_album;
                        queryResultList.add(item);
                    }
                }
                c.close();
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                if (mQueryType == startQueryType) {
                    mQueryResultList.clear();
                    mQueryResultList.addAll(queryResultList);
                    mAdapter.notifyDataSetChanged();
                    mQueryList.setSelection(0);
                }
                stopProgress();
            }
        }.execute();
    }

    private void searchArtists(final String query, final int startIndex) {
        if (!mHasStoragePerms) {
            return;
        }
        startProgress();
        final List<QueryItem> queryResultList = new ArrayList<QueryItem>();
        final int startQueryType = mQueryType;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... params) {
                String[] projection = {
                        MediaStore.Audio.Artists._ID,
                        MediaStore.Audio.Artists.ARTIST
                };
                String selection = null;
                if (!TextUtils.isEmpty(query)) {
                    selection = MediaStore.Audio.Artists.ARTIST + " LIKE '%" + query + "%' ";
                }
                Cursor c = getContentResolver().query(
                        MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        MediaStore.Audio.Artists.ARTIST);

                while (c.moveToNext()) {
                    Uri artist = Uri.withAppendedPath(
                            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                            String.valueOf(c.getLong(0)));
                    String artistName = c.getString(1);
                    if (!TextUtils.isEmpty(artistName) &&
                            !artistName.equals(MediaStore.UNKNOWN_STRING)) {
                        QueryItem item = new QueryItem();
                        item.mName = artistName;
                        item.mUri = artist.toString();
                        item.mQueryType = QUERY_TYPE_ARTIST;
                        item.mIconId = R.drawable.ic_artist;
                        queryResultList.add(item);
                    }
                }
                c.close();
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                if (mQueryType == startQueryType) {
                    mQueryResultList.clear();
                    mQueryResultList.addAll(queryResultList);
                    mAdapter.notifyDataSetChanged();
                    mQueryList.setSelection(0);
                }
                stopProgress();
            }
        }.execute();
    }

    private void searchTracks(final String query, final int startIndex) {
        if (!mHasStoragePerms) {
            return;
        }
        startProgress();
        final List<QueryItem> queryResultList = new ArrayList<QueryItem>();
        final int startQueryType = mQueryType;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... params) {
                String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

                String[] projection = {
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM
                };

                if (!TextUtils.isEmpty(query)) {
                    selection = selection + " AND " + MediaStore.Audio.Media.TITLE + " LIKE '%" + query + "%' ";
                }
                Cursor c = getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        MediaStore.Audio.Media.TITLE);

                while (c.moveToNext()) {
                    Uri track = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            String.valueOf(c.getLong(0)));
                    String trackTitle = c.getString(1);
                    String artistName = c.getString(2);
                    String albumName = c.getString(3);

                    if (!TextUtils.isEmpty(trackTitle)) {
                        QueryItem item = new QueryItem();
                        item.mName = trackTitle;
                        if (!artistName.equals(MediaStore.UNKNOWN_STRING)) {
                            item.mSubText = artistName + " - " + albumName;
                        } else {
                            item.mSubText = albumName;
                        }
                        item.mUri = track.toString();
                        item.mQueryType = QUERY_TYPE_TRACK;
                        item.mIconId = R.drawable.ic_track;
                        queryResultList.add(item);
                    }
                }
                c.close();
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                if (mQueryType == startQueryType) {
                    mQueryResultList.clear();
                    mQueryResultList.addAll(queryResultList);
                    mAdapter.notifyDataSetChanged();
                    mQueryList.setSelection(0);
                }
                stopProgress();
            }
        }.execute();
    }

    private void searchPlaylists(final String query, final int startIndex) {
        if (!mHasStoragePerms) {
            return;
        }
        startProgress();
        final List<QueryItem> queryResultList = new ArrayList<QueryItem>();
        final int startQueryType = mQueryType;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... params) {
                String selection = null;
                if (!TextUtils.isEmpty(query)) {
                    selection = MediaStore.Audio.Playlists.NAME + " LIKE '%" + query + "%' ";
                }

                String[] projection = {
                        MediaStore.Audio.Playlists._ID,
                        MediaStore.Audio.Playlists.NAME
                };

                String[] projectionMembers = {
                        MediaStore.Audio.Playlists.Members.AUDIO_ID,
                        MediaStore.Audio.Playlists.Members.ARTIST,
                        MediaStore.Audio.Playlists.Members.TITLE,
                        MediaStore.Audio.Playlists.Members._ID
                };

                Cursor c = getContentResolver().query(
                        MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        MediaStore.Audio.Playlists.NAME);

                while (c.moveToNext()) {
                    Uri track = Uri.withAppendedPath(
                            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                            String.valueOf(c.getLong(0)));
                    String playlistTitle = c.getString(1);
                    if (!TextUtils.isEmpty(playlistTitle)) {
                        long id = c.getLong(0);
                        Cursor c1 = getContentResolver().query(
                                MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                                projectionMembers,
                                MediaStore.Audio.Media.IS_MUSIC + " != 0 ",
                                null,
                                null);
                        // only show playlists that have valid entries
                        if (c1.getCount() != 0) {
                            QueryItem item = new QueryItem();
                            item.mName = playlistTitle;
                            item.mUri = track.toString();
                            item.mQueryType = QUERY_TYPE_PLAYLIST;
                            item.mIconId = R.drawable.ic_playlist;
                            queryResultList.add(item);
                        }
                        c1.close();
                    }
                }
                c.close();
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                if (mQueryType == startQueryType) {
                    mQueryResultList.clear();
                    mQueryResultList.addAll(queryResultList);
                    mAdapter.notifyDataSetChanged();
                    mQueryList.setSelection(0);
                }
                stopProgress();
            }
        }.execute();
    }

    private void loadStreams() {
        if (!mHasStoragePerms) {
            return;
        }
        startProgress();
        final List<QueryItem> queryResultList = new ArrayList<QueryItem>();
        final int startQueryType = mQueryType;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... params) {
                File playlistDir = Utils.getStreamM3UDirectory(BrowseActivity.this);
                if (playlistDir.exists()) {
                    for (File file : playlistDir.listFiles()) {
                        if (file.isFile()) {
                            String fileUri = Uri.fromFile(file).toString();
                            if (Utils.isStreamM3UFile(fileUri)) {
                                QueryItem item = new QueryItem();
                                String name = Utils.getStreamM3UName(fileUri);
                                item.mName = name == null ? file.getName() : name;
                                item.mSubText = getStreamURLSub(fileUri);
                                item.mUri = fileUri;
                                item.mQueryType = QUERY_TYPE_STREAM;
                                item.mIconId = R.drawable.ic_earth;
                                queryResultList.add(item);
                            }
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                if (mQueryType == startQueryType) {
                    mQueryResultList.clear();
                    mQueryResultList.addAll(queryResultList);
                    mAdapter.notifyDataSetChanged();
                    mQueryList.setSelection(0);
                }
                stopProgress();
            }
        }.execute();
    }

    private boolean resolveAlbum(String album, QueryItem item) {
        if (!mHasStoragePerms) {
            return false;
        }
        String albumId = Uri.parse(album).getLastPathSegment();
        String selection = MediaStore.Audio.Media.ALBUM_ID + " = " + Integer.valueOf(albumId).intValue();

        String[] projection = {
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST
        };

        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null);

        try {
            while (c.moveToNext()) {
                String title = c.getString(1);
                String artist = c.getString(2);
                item.mName = title;
                item.mSubText = artist;
                item.mQueryType = QUERY_TYPE_ALBUM;
                item.mIconId = R.drawable.ic_album;
                return true;
            }
        } finally {
            c.close();
        }
        return false;
    }

    private void resolveRecents() {
        startProgress();
        final int startQueryType = mQueryType;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... params) {
                List<QueryItem> safeIter = new ArrayList<QueryItem>();
                safeIter.addAll(mQueryResultListCopy);

                for (QueryItem item : safeIter) {
                    if (!resolveUri(item.mUri, item)) {
                        mQueryResultListCopy.remove(item);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                if (mQueryType == startQueryType) {
                    mQueryResultList.clear();
                    mQueryResultList.addAll(mQueryResultListCopy);
                    saveRecents(mQueryResultList);
                    mAdapter.notifyDataSetChanged();
                }
                stopProgress();
            }
        }.execute();
    }

    private boolean resolveArtist(String artist, QueryItem item) {
        if (!mHasStoragePerms) {
            return false;
        }
        String artistId = Uri.parse(artist).getLastPathSegment();
        String selection = MediaStore.Audio.Media.ARTIST_ID + " = " + Integer.valueOf(artistId).intValue();

        String[] projection = {
                MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST
        };

        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null);

        try {
            while (c.moveToNext()) {
                String artistName = c.getString(1);
                item.mName = artistName;
                item.mQueryType = QUERY_TYPE_ARTIST;
                item.mIconId = R.drawable.ic_artist;
                return true;
            }
        } finally {
            c.close();
        }
        return false;
    }

    private boolean resolveTrack(String track, QueryItem item) {
        if (!mHasStoragePerms) {
            return false;
        }
        String trackId = Uri.parse(track).getLastPathSegment();
        String selection = MediaStore.Audio.Media._ID + " = " + Integer.valueOf(trackId).intValue();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM
        };

        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null);

        try {
            while (c.moveToNext()) {
                String title = c.getString(1);
                String artistName = c.getString(2);
                String albumName = c.getString(3);

                item.mName = title;
                item.mSubText = artistName + " - " + albumName;
                item.mQueryType = QUERY_TYPE_TRACK;
                item.mIconId = R.drawable.ic_track;
                return true;
            }
        } finally {
            c.close();
        }
        return false;
    }

    private boolean resolvePlaylist(String playlist, QueryItem item) {
        if (!mHasStoragePerms) {
            return false;
        }
        String playlistId = Uri.parse(playlist).getLastPathSegment();
        String selection = MediaStore.Audio.Playlists._ID + " = " + Integer.valueOf(playlistId).intValue();

        String[] projection = {
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME
        };

        Cursor c = getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null);

        try {
            while (c.moveToNext()) {
                String title = c.getString(1);

                item.mName = title;
                item.mQueryType = QUERY_TYPE_PLAYLIST;
                item.mIconId = R.drawable.ic_playlist;
                return true;
            }
        } finally {
            c.close();
        }
        return false;
    }

    private boolean resolveFolder(String folder, QueryItem item) {
        String path = Uri.parse(folder).getPath();
        File f = new File(path);
        if (f.isDirectory() && f.exists()) {
            item.mName = Uri.parse(folder).getLastPathSegment();
            item.mQueryType = QUERY_TYPE_FOLDER;
            item.mIconId = R.drawable.ic_folder;
            return true;
        }
        return false;
    }

    private boolean resolveFile(String folder, QueryItem item) {
        String path = Uri.parse(folder).getPath();
        File f = new File(path);
        if (f.isFile() && f.exists()) {
            item.mName = Uri.parse(folder).getLastPathSegment();
            item.mQueryType = QUERY_TYPE_FOLDER;
            item.mIconId = R.drawable.ic_playlist;
            return true;
        }
        return false;
    }

    private boolean resolveStream(String uri, QueryItem item) {
        String path = Uri.parse(uri).getPath();
        File f = new File(path);
        if (f.isFile() && f.exists()) {
            String name = Utils.getStreamM3UName(uri);
            item.mName = name == null ? f.getName() : name;
            item.mSubText = getStreamURLSub(uri);
            item.mQueryType = QUERY_TYPE_STREAM;
            item.mIconId = R.drawable.ic_earth;
            return true;
        }
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mCurrentQueryText = newText;
        clearList();
        doQuery(newText, 0);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    private void doQuery(String query, int startIndex) {
        switch (mQueryType) {
            case QUERY_TYPE_ALARM:
            case QUERY_TYPE_RINGTONE:
                clearList();
                searchAlarmsAndRingtones(query, startIndex);
                break;
            case QUERY_TYPE_ALBUM:
                clearList();
                searchAlbums(query, startIndex);
                break;
            case QUERY_TYPE_ARTIST:
                clearList();
                searchArtists(query, startIndex);
                break;
            case QUERY_TYPE_TRACK:
                clearList();
                searchTracks(query, startIndex);
                break;
            case QUERY_TYPE_FOLDER:
                clearList();
                break;
            case QUERY_TYPE_PLAYLIST:
                clearList();
                searchPlaylists(query, startIndex);
                break;
            case QUERY_TYPE_STREAM:
                clearList();
                loadStreams();
                break;
            case QUERY_TYPE_RECENT:
                clearList();
                loadRecents();
                break;
        }
    }

    private void resolveUri(final String uri, View view) {
        final TextView title = (TextView) view.findViewById(R.id.item_text);
        final TextView subTitle = (TextView) view.findViewById(R.id.item_subtext);
        final ImageView icon = (ImageView) view.findViewById(R.id.item_icon);
        final ImageView playIcon = (ImageView) view.findViewById(R.id.item_play);

        boolean unknownTone = true;
        int iconId = -1;
        String alarmTitle = isAlarm(uri);
        if (alarmTitle != null) {
            iconId = R.drawable.ic_alarm;
            icon.setImageResource(iconId);
            title.setText(alarmTitle);
            subTitle.setVisibility(View.GONE);
            playIcon.setVisibility(View.VISIBLE);
            unknownTone = false;
        }
        if (unknownTone) {
            String ringtoneTitle = isRingtone(uri);
            if (ringtoneTitle != null) {
                iconId = R.drawable.ic_bell;
                icon.setImageResource(iconId);
                title.setText(ringtoneTitle);
                subTitle.setVisibility(View.GONE);
                playIcon.setVisibility(View.VISIBLE);
                unknownTone = false;
            }
        }
        if (unknownTone && Utils.isLocalAlbumUri(uri)) {
            QueryItem queryItem = new QueryItem();
            queryItem.mQueryType = QUERY_TYPE_ALBUM;
            queryItem.mUri = uri;
            if (resolveAlbum(uri, queryItem)) {
                iconId = queryItem.mIconId;
                icon.setImageResource(iconId);
                title.setText(queryItem.mName);
                subTitle.setText(queryItem.mSubText);
                playIcon.setVisibility(View.VISIBLE);
                unknownTone = false;
            }
        }
        if (unknownTone && Utils.isLocalArtistUri(uri)) {
            QueryItem queryItem = new QueryItem();
            queryItem.mQueryType = QUERY_TYPE_ARTIST;
            queryItem.mUri = uri;
            if (resolveArtist(uri, queryItem)) {
                iconId = queryItem.mIconId;
                icon.setImageResource(iconId);
                title.setText(queryItem.mName);
                subTitle.setVisibility(View.GONE);
                playIcon.setVisibility(View.VISIBLE);
                unknownTone = false;
            }
        }
        if (unknownTone && Utils.isLocalTrackUri(uri)) {
            QueryItem queryItem = new QueryItem();
            queryItem.mQueryType = QUERY_TYPE_TRACK;
            queryItem.mUri = uri;
            if (resolveTrack(uri, queryItem)) {
                iconId = queryItem.mIconId;
                icon.setImageResource(iconId);
                title.setText(queryItem.mName);
                subTitle.setText(queryItem.mSubText);
                playIcon.setVisibility(View.VISIBLE);
                unknownTone = false;
            }
        }
        if (unknownTone && Utils.isStreamM3UFile(uri)) {
            QueryItem queryItem = new QueryItem();
            queryItem.mQueryType = QUERY_TYPE_STREAM;
            queryItem.mUri = uri;
            if (resolveStream(uri, queryItem)) {
                iconId = queryItem.mIconId;
                icon.setImageResource(iconId);
                title.setText(queryItem.mName);
                subTitle.setText(queryItem.mSubText);
                playIcon.setVisibility(View.VISIBLE);
                unknownTone = false;
            }
        }
        if (unknownTone && Utils.isStorageUri(uri)) {
            QueryItem queryItem = new QueryItem();
            queryItem.mQueryType = QUERY_TYPE_FOLDER;
            queryItem.mUri = uri;
            boolean resolved = false;
            if (Utils.isStorageFileUri(uri)) {
                resolved = resolveFile(uri, queryItem);
            } else {
                resolved = resolveFolder(uri, queryItem);
            }
            if (resolved) {
                iconId = queryItem.mIconId;
                icon.setImageResource(iconId);
                title.setText(queryItem.mName);
                subTitle.setVisibility(View.GONE);
                playIcon.setVisibility(View.VISIBLE);
                unknownTone = false;
            }
        }
        if (unknownTone && Utils.isLocalPlaylistUri(uri)) {
            QueryItem queryItem = new QueryItem();
            queryItem.mQueryType = QUERY_TYPE_PLAYLIST;
            queryItem.mUri = uri;
            if (resolvePlaylist(uri, queryItem)) {
                iconId = queryItem.mIconId;
                icon.setImageResource(iconId);
                title.setText(queryItem.mName);
                subTitle.setVisibility(View.GONE);
                playIcon.setVisibility(View.VISIBLE);
                unknownTone = false;
            }
        }

        final int iconIdFinal = iconId;
        playIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playUri(uri, title.getText().toString(), iconIdFinal);
            }
        });
    }

    private boolean resolveUri(final String uri, final QueryItem queryItem) {
        queryItem.mUri = uri;

        boolean unknownTone = true;
        String alarmTitle = isAlarm(uri);
        if (alarmTitle != null) {
            queryItem.mName = alarmTitle;
            queryItem.mIconId = R.drawable.ic_alarm;
            queryItem.mQueryType = QUERY_TYPE_ALARM;
            unknownTone = false;
        }
        if (unknownTone) {
            String ringtoneTitle = isRingtone(uri);
            if (ringtoneTitle != null) {
                queryItem.mName = ringtoneTitle;
                queryItem.mIconId = R.drawable.ic_bell;
                queryItem.mQueryType = QUERY_TYPE_RINGTONE;
                unknownTone = false;
            }
        }
        if (unknownTone && Utils.isLocalAlbumUri(uri)) {
            if (resolveAlbum(uri, queryItem)) {
                unknownTone = false;
            }
        }
        if (unknownTone && Utils.isLocalArtistUri(uri)) {
            if (resolveArtist(uri, queryItem)) {
                unknownTone = false;
            }
        }
        if (unknownTone && Utils.isLocalTrackUri(uri)) {
            if (resolveTrack(uri, queryItem)) {
                unknownTone = false;
            }
        }
        if (!mLimitedMode && unknownTone && Utils.isStreamM3UFile(uri)) {
            if (resolveStream(uri, queryItem)) {
                unknownTone = false;
            }
        }
        if (!mLimitedMode && unknownTone && Utils.isStorageUri(uri)) {
            if (Utils.isStorageFileUri(uri)) {
                if (resolveFile(uri, queryItem)) {
                    unknownTone = false;
                }
            } else {
                if (resolveFolder(uri, queryItem)) {
                    unknownTone = false;
                }
            }
        }
        if (!mLimitedMode && unknownTone && Utils.isLocalPlaylistUri(uri)) {
            if (resolvePlaylist(uri, queryItem)) {
                unknownTone = false;
            }
        }
        if (unknownTone || TextUtils.isEmpty(queryItem.mName)) {
            queryItem.mName = getResources().getString(R.string.alarm_uri_unkown);
            queryItem.mQueryType = QUERY_TYPE_UNKNOWN;
            return false;
        }
        return true;
    }

    private void playUri(String uri, String name, int iconId) {
        if (mAlarm != null) {
            closeAlarmTestDialog();
            mAlarm.alert = Uri.parse(uri);
            final AlarmTestDialog fragment = AlarmTestDialog.newInstance(mAlarm, mPreAlarm,
                    name, iconId);
            fragment.show(getFragmentManager(), "alarm_test");
        }
    }


    private void closeAlarmTestDialog() {
        final Fragment prev = getFragmentManager().findFragmentByTag("alarm_test");
        if (prev != null) {
            ((DialogFragment) prev).dismiss();
        }
    }

    private void addToRecents(String uri) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String saved = sharedPref.getString(PREF_RECENT_URI, null);
        List<String> recentList = new ArrayList<String>();

        if (saved != null) {
            String[] savedParts = saved.split("\\|\\|");
            recentList.addAll(Arrays.asList(savedParts));
            int idx = recentList.indexOf(uri);
            if (idx != -1) {
                // always order in front if selected again
                recentList.remove(idx);
            }
        }
        recentList.add(0, uri);
        if (recentList.size() > RECENT_SIZE) {
            recentList = recentList.subList(0, RECENT_SIZE);
        }
        String recentListString = TextUtils.join("||", recentList);
        sharedPref.edit().putString(PREF_RECENT_URI, recentListString).commit();
    }

    private void saveRecents(List<QueryItem> recentList) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String saved = sharedPref.getString(PREF_RECENT_URI, null);

        List<String> recentStringList = new ArrayList<String>();
        for (QueryItem item : recentList) {
            recentStringList.add(item.mUri);
        }
        String recentListString = TextUtils.join("||", recentStringList);
        if (!saved.equals(recentListString)) {
            sharedPref.edit().putString(PREF_RECENT_URI, recentListString).commit();
        }
    }

    private void loadRecents() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String saved = sharedPref.getString(PREF_RECENT_URI, null);
        if (saved != null) {
            startProgress();
            String[] savedParts = saved.split("\\|\\|");
            mQueryResultListCopy.clear();
            for (String uri : savedParts) {
                QueryItem queryItem = new QueryItem();
                queryItem.mQueryType = QUERY_TYPE_UNKNOWN;
                queryItem.mUri = uri;
                mQueryResultListCopy.add(queryItem);
            }
            resolveRecents();
        }
    }

    private void updateTabs() {
        switch (mQueryType) {
            case QUERY_TYPE_ALARM:
                setTabImageEnabled(mRecentTab, false);
                setTabImageEnabled(mArtistTab, false);
                setTabImageEnabled(mAlbumTab, false);
                setTabImageEnabled(mTrackTab, false);
                setTabImageEnabled(mPlaylistTab, false);
                setTabImageEnabled(mStreamTab, false);
                setTabImageEnabled(mFolderTab, false);
                setTabImageEnabled(mAlarmsTab, true);
                break;
            case QUERY_TYPE_RECENT:
                setTabImageEnabled(mRecentTab, true);
                setTabImageEnabled(mArtistTab, false);
                setTabImageEnabled(mAlbumTab, false);
                setTabImageEnabled(mTrackTab, false);
                setTabImageEnabled(mPlaylistTab, false);
                setTabImageEnabled(mStreamTab, false);
                setTabImageEnabled(mFolderTab, false);
                setTabImageEnabled(mAlarmsTab, false);
                break;
            case QUERY_TYPE_ARTIST:
                setTabImageEnabled(mRecentTab, false);
                setTabImageEnabled(mArtistTab, true);
                setTabImageEnabled(mAlbumTab, false);
                setTabImageEnabled(mTrackTab, false);
                setTabImageEnabled(mPlaylistTab, false);
                setTabImageEnabled(mStreamTab, false);
                setTabImageEnabled(mFolderTab, false);
                setTabImageEnabled(mAlarmsTab, false);
                break;
            case QUERY_TYPE_ALBUM:
                setTabImageEnabled(mRecentTab, false);
                setTabImageEnabled(mArtistTab, false);
                setTabImageEnabled(mAlbumTab, true);
                setTabImageEnabled(mTrackTab, false);
                setTabImageEnabled(mPlaylistTab, false);
                setTabImageEnabled(mStreamTab, false);
                setTabImageEnabled(mFolderTab, false);
                setTabImageEnabled(mAlarmsTab, false);
                break;
            case QUERY_TYPE_TRACK:
                setTabImageEnabled(mRecentTab, false);
                setTabImageEnabled(mArtistTab, false);
                setTabImageEnabled(mAlbumTab, false);
                setTabImageEnabled(mTrackTab, true);
                setTabImageEnabled(mPlaylistTab, false);
                setTabImageEnabled(mStreamTab, false);
                setTabImageEnabled(mFolderTab, false);
                setTabImageEnabled(mAlarmsTab, false);
                break;
            case QUERY_TYPE_FOLDER:
                setTabImageEnabled(mRecentTab, false);
                setTabImageEnabled(mArtistTab, false);
                setTabImageEnabled(mAlbumTab, false);
                setTabImageEnabled(mTrackTab, false);
                setTabImageEnabled(mPlaylistTab, false);
                setTabImageEnabled(mStreamTab, false);
                setTabImageEnabled(mFolderTab, true);
                setTabImageEnabled(mAlarmsTab, false);
                break;
            case QUERY_TYPE_PLAYLIST:
                setTabImageEnabled(mRecentTab, false);
                setTabImageEnabled(mArtistTab, false);
                setTabImageEnabled(mAlbumTab, false);
                setTabImageEnabled(mTrackTab, false);
                setTabImageEnabled(mPlaylistTab, true);
                setTabImageEnabled(mStreamTab, false);
                setTabImageEnabled(mFolderTab, false);
                setTabImageEnabled(mAlarmsTab, false);
                break;
            case QUERY_TYPE_STREAM:
                setTabImageEnabled(mRecentTab, false);
                setTabImageEnabled(mArtistTab, false);
                setTabImageEnabled(mAlbumTab, false);
                setTabImageEnabled(mTrackTab, false);
                setTabImageEnabled(mPlaylistTab, false);
                setTabImageEnabled(mStreamTab, true);
                setTabImageEnabled(mFolderTab, false);
                setTabImageEnabled(mAlarmsTab, false);
                break;
        }
    }

    private void cacheAlarmTones() {
        mAlarms = new ArrayList<QueryItem>();

        Cursor alarmsCursor = null;
        try {
            RingtoneManager ringtoneMgr = new RingtoneManager(this
                    .getApplicationContext());
            ringtoneMgr.setType(RingtoneManager.TYPE_ALARM);

            alarmsCursor = ringtoneMgr.getCursor();
            int alarmsCount = alarmsCursor.getCount();
            if (alarmsCount == 0 && !alarmsCursor.moveToFirst()) {
                return;
            }

            while (!alarmsCursor.isAfterLast() && alarmsCursor.moveToNext()) {
                QueryItem queryItem = new QueryItem();
                queryItem.mName = alarmsCursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                queryItem.mUri = getUriFromCursor(alarmsCursor).toString();
                queryItem.mIconId = R.drawable.ic_alarm;
                mAlarms.add(queryItem);
            }
        } finally {
            if (alarmsCursor != null) {
                alarmsCursor.close();
            }
        }
    }

    private void cacheRingtones() {
        mRingtones = new ArrayList<QueryItem>();

        Cursor alarmsCursor = null;
        try {
            RingtoneManager ringtoneMgr = new RingtoneManager(this
                    .getApplicationContext());
            ringtoneMgr.setType(RingtoneManager.TYPE_RINGTONE);

            alarmsCursor = ringtoneMgr.getCursor();
            int alarmsCount = alarmsCursor.getCount();
            if (alarmsCount == 0 && !alarmsCursor.moveToFirst()) {
                return;
            }

            while (!alarmsCursor.isAfterLast() && alarmsCursor.moveToNext()) {
                QueryItem queryItem = new QueryItem();
                queryItem.mName = alarmsCursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                queryItem.mUri = getUriFromCursor(alarmsCursor).toString();
                queryItem.mIconId = R.drawable.ic_bell;
                mRingtones.add(queryItem);
            }
        } finally {
            if (alarmsCursor != null) {
                alarmsCursor.close();
            }
        }
    }

    private String isAlarm(String uri) {
        for (QueryItem i : mAlarms) {
            if (i.mUri.equals(uri)) {
                return i.mName;
            }
        }
        return null;
    }

    private String isRingtone(String uri) {
        for (QueryItem i : mRingtones) {
            if (i.mUri.equals(uri)) {
                return i.mName;
            }
        }
        return null;
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

    @Override
    public void onChooseDirOk(Uri chosenDir) {
        mQueryResultList.clear();
        QueryItem item = new QueryItem();
        item.mUri = chosenDir.toString();
        resolveFolder(chosenDir.toString(), item);
        mQueryResultList.add(item);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onChooseDirCancel() {
    }

    private void checkStoragePermissions() {
        boolean needRequest = false;
        String[] permissions = {
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
        };
        ArrayList<String> permissionList = new ArrayList<String>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
                needRequest = true;
            }
        }

        if (needRequest) {
            int count = permissionList.size();
            if (count > 0) {
                String[] permissionArray = new String[count];
                for (int i = 0; i < count; i++) {
                    permissionArray[i] = permissionList.get(i);
                }
                ActivityCompat.requestPermissions(this, permissionArray, PERMISSIONS_REQUEST_EXTERNAL_STORAGE);
            }
        } else {
            mHasStoragePerms = true;
            doInit();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mHasStoragePerms = true;
                }
                doInit();
            }
            return;
        }
    }

    private void doInit() {
        doQuery(mCurrentQueryText, 0);
    }

    private void resolvePasteContents(final String pasteString, final String name) {
        startProgress();
        final List<String> pasteStringWrapper = new ArrayList<>();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... params) {
                String content = Utils.downloadUrlMemoryAsString(pasteString);
                if (content != null) {
                    List<String> playlistUrils = null;
                    if (pasteString.endsWith("pls")) {
                        playlistUrils = Utils.parsePLSPlaylistFromMemory(content);
                    }
                    if (pasteString.endsWith("m3u")) {
                        playlistUrils = Utils.parseM3UPlaylistFromMemory(content);
                    }
                    if (playlistUrils != null && playlistUrils.size() != 0) {
                        pasteStringWrapper.add(playlistUrils.get(0));
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                stopProgress();
                String urlString = pasteString;
                if (pasteStringWrapper.size() != 0) {
                    urlString = pasteStringWrapper.get(0);
                }
                doResolvePaste(urlString, name);
            }
        }.execute();
    }
    private void resolvePaste(String pasteString, String name) {
        if (!TextUtils.isEmpty(pasteString) && !TextUtils.isEmpty(name)) {
            if (pasteString.endsWith("pls") || pasteString.endsWith("m3u")) {
                // looks like a playlist pasted try to get it and extract url
                resolvePasteContents(pasteString, name);
            } else {
                doResolvePaste(pasteString, name);
            }
        }
    }

    private void doResolvePaste(String pasteString, String name) {
        if (!TextUtils.isEmpty(pasteString)) {
            // create on the fly m3u and add that
            File playlistDir = Utils.getStreamM3UDirectory(this);
            if (!playlistDir.exists()) {
                playlistDir.mkdir();
            }
            File m3uFile = Utils.writeStreamM3UFile(playlistDir, name, pasteString);
            if (m3uFile != null) {
                clearList();
                loadStreams();
            }
        }
    }

    @Override
    public void onPlaylistDialogSet(String label) {
        if (!TextUtils.isEmpty(mPasteUrlText.getText().toString())) {
            resolvePaste(mPasteUrlText.getText().toString(), label);
            mPasteUrlText.setText("");
        }
    }

    private void showPlaylistDialog() {
        closePlaylistDialog();
        final PlaylistDialogFragment newFragment = PlaylistDialogFragment.newInstance(this);
        newFragment.show(getFragmentManager(), "playlist_dialog");
    }

    private void closePlaylistDialog() {
        final Fragment prev = getFragmentManager().findFragmentByTag("playlist_dialog");
        if (prev != null) {
            ((DialogFragment) prev).dismiss();
        }
    }

    private String getStreamURLSub(String m3UFileUri) {
        List<Uri> files = Utils.parseM3UPlaylist(m3UFileUri);
        if (files.size() != 0) {
            return files.get(0).toString();
        }
        return null;
    }

    private void setTabImageEnabled(View tab, boolean enabled) {
        if (enabled) {
            ((ImageView) tab.findViewById(R.id.tab_strip_image)).setColorFilter(null);
            ((TextView) tab.findViewById(R.id.tab_strip_title)).setTextColor(mPrimaryColor);
        } else {
            ((ImageView) tab.findViewById(R.id.tab_strip_image)).setColorFilter(mPrimaryColorDisabled, PorterDuff.Mode.SRC_IN);
            ((TextView) tab.findViewById(R.id.tab_strip_title)).setTextColor(mPrimaryColorDisabled);
        }
    }
}

