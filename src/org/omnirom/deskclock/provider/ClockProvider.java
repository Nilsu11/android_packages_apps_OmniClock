/*
 * Copyright (C) 2013 The Android Open Source Project
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

package org.omnirom.deskclock.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import org.omnirom.deskclock.LogUtils;

import java.util.HashMap;

public class ClockProvider extends ContentProvider {
    private ClockDatabaseHelper mOpenHelper;
    private static final String LOGTAG = ClockProvider.class.getSimpleName();

    private static final int ALARMS = 1;
    private static final int ALARMS_ID = 2;
    private static final int INSTANCES = 3;
    private static final int INSTANCES_ID = 4;
    private static final int CITIES = 5;
    private static final int CITIES_ID = 6;

    private static final UriMatcher sURLMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURLMatcher.addURI(ClockContract.AUTHORITY, "alarms", ALARMS);
        sURLMatcher.addURI(ClockContract.AUTHORITY, "alarms/#", ALARMS_ID);
        sURLMatcher.addURI(ClockContract.AUTHORITY, "instances", INSTANCES);
        sURLMatcher.addURI(ClockContract.AUTHORITY, "instances/#", INSTANCES_ID);
        sURLMatcher.addURI(ClockContract.AUTHORITY, "cities", CITIES);
        sURLMatcher.addURI(ClockContract.AUTHORITY, "cities/*", CITIES_ID);
    }


    private final static HashMap<String, String> sDictProjectionMap;

    static {
        sDictProjectionMap = new HashMap<String, String>();
        sDictProjectionMap.put(ClockContract.AlarmsColumns._ID, ClockContract.AlarmsColumns._ID);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.HOUR, ClockContract.AlarmsColumns.HOUR);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.MINUTES, ClockContract.AlarmsColumns.MINUTES);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.DAYS_OF_WEEK, ClockContract.AlarmsColumns.DAYS_OF_WEEK);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.ENABLED, ClockContract.AlarmsColumns.ENABLED);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.VIBRATE, ClockContract.AlarmsColumns.VIBRATE);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.LABEL, ClockContract.AlarmsColumns.LABEL);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.RINGTONE, ClockContract.AlarmsColumns.RINGTONE);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.DELETE_AFTER_USE, ClockContract.AlarmsColumns.DELETE_AFTER_USE);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.INCREASING_VOLUME, ClockContract.AlarmsColumns.INCREASING_VOLUME);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.PRE_ALARM, ClockContract.AlarmsColumns.PRE_ALARM);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.ALARM_VOLUME, ClockContract.AlarmsColumns.ALARM_VOLUME);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.PRE_ALARM_VOLUME, ClockContract.AlarmsColumns.PRE_ALARM_VOLUME);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.PRE_ALARM_TIME, ClockContract.AlarmsColumns.PRE_ALARM_TIME);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.PRE_ALARM_RINGTONE, ClockContract.AlarmsColumns.PRE_ALARM_RINGTONE);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.RANDOM_MODE, ClockContract.AlarmsColumns.RANDOM_MODE);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.RINGTONE_NAME, ClockContract.AlarmsColumns.RINGTONE_NAME);
        sDictProjectionMap.put(ClockContract.AlarmsColumns.PRE_ALARM_RINGTONE_NAME, ClockContract.AlarmsColumns.PRE_ALARM_RINGTONE_NAME);

        sDictProjectionMap.put(ClockContract.InstancesColumns.YEAR, ClockContract.InstancesColumns.YEAR);
        sDictProjectionMap.put(ClockContract.InstancesColumns.HOUR, ClockContract.InstancesColumns.HOUR);
        sDictProjectionMap.put(ClockContract.InstancesColumns.MINUTES, ClockContract.InstancesColumns.MINUTES);
        sDictProjectionMap.put(ClockContract.InstancesColumns.DAY, ClockContract.InstancesColumns.DAY);
        sDictProjectionMap.put(ClockContract.InstancesColumns.MONTH, ClockContract.InstancesColumns.MONTH);
        sDictProjectionMap.put(ClockContract.InstancesColumns.ALARM_ID, ClockContract.InstancesColumns.ALARM_ID);
        sDictProjectionMap.put(ClockContract.InstancesColumns.ALARM_STATE, ClockContract.InstancesColumns.ALARM_STATE);
    }

    public ClockProvider() {
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new ClockDatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projectionIn, String selection, String[] selectionArgs,
            String sort) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
        qb.setProjectionMap(sDictProjectionMap);

        // Generate the body of the query
        int match = sURLMatcher.match(uri);
        switch (match) {
            case ALARMS:
                qb.setTables(ClockDatabaseHelper.ALARMS_TABLE_NAME);
                break;
            case ALARMS_ID:
                qb.setTables(ClockDatabaseHelper.ALARMS_TABLE_NAME);
                qb.appendWhere(ClockContract.AlarmsColumns._ID + "=");
                qb.appendWhere(uri.getLastPathSegment());
                break;
            case INSTANCES:
                qb.setTables(ClockDatabaseHelper.INSTANCES_TABLE_NAME);
                break;
            case INSTANCES_ID:
                qb.setTables(ClockDatabaseHelper.INSTANCES_TABLE_NAME);
                qb.appendWhere(ClockContract.InstancesColumns._ID + "=");
                qb.appendWhere(uri.getLastPathSegment());
                break;
            case CITIES:
                qb.setTables(ClockDatabaseHelper.CITIES_TABLE_NAME);
                break;
            case CITIES_ID:
                qb.setTables(ClockDatabaseHelper.CITIES_TABLE_NAME);
                qb.appendWhere(ClockContract.CitiesColumns.CITY_ID + "=");
                qb.appendWhere(uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor ret = qb.query(db, projectionIn, selection, selectionArgs,
                              null, null, sort);

        if (ret == null) {
            LogUtils.e(LOGTAG, "Alarms.query: failed");
        } else {
            ret.setNotificationUri(getContext().getContentResolver(), uri);
        }

        return ret;
    }

    @Override
    public String getType(Uri uri) {
        int match = sURLMatcher.match(uri);
        switch (match) {
            case ALARMS:
                return "vnd.android.cursor.dir/alarms";
            case ALARMS_ID:
                return "vnd.android.cursor.item/alarms";
            case INSTANCES:
                return "vnd.android.cursor.dir/instances";
            case INSTANCES_ID:
                return "vnd.android.cursor.item/instances";
            case CITIES:
                return "vnd.android.cursor.dir/cities";
            case CITIES_ID:
                return "vnd.android.cursor.item/cities";
            default:
                throw new IllegalArgumentException("Unknown URL");
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        int count;
        String alarmId;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        switch (sURLMatcher.match(uri)) {
            case ALARMS_ID:
                alarmId = uri.getLastPathSegment();
                count = db.update(ClockDatabaseHelper.ALARMS_TABLE_NAME, values,
                        ClockContract.AlarmsColumns._ID + "=" + alarmId,
                        null);
                break;
            case INSTANCES_ID:
                alarmId = uri.getLastPathSegment();
                count = db.update(ClockDatabaseHelper.INSTANCES_TABLE_NAME, values,
                        ClockContract.InstancesColumns._ID + "=" + alarmId,
                        null);
                break;
            case CITIES_ID:
                alarmId = uri.getLastPathSegment();
                count = db.update(ClockDatabaseHelper.CITIES_TABLE_NAME, values,
                        ClockContract.CitiesColumns.CITY_ID + "=" + alarmId,
                        null);
                break;
            default: {
                throw new UnsupportedOperationException(
                        "Cannot update URL: " + uri);
            }
        }
        LogUtils.v(LOGTAG, "notifyChange() id: " + alarmId + " url " + uri);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        long rowId;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        switch (sURLMatcher.match(uri)) {
            case ALARMS:
                rowId = mOpenHelper.fixAlarmInsert(initialValues);
                break;
            case INSTANCES:
                rowId = db.insert(ClockDatabaseHelper.INSTANCES_TABLE_NAME, null, initialValues);
                break;
            case CITIES:
                rowId = db.insert(ClockDatabaseHelper.CITIES_TABLE_NAME, null, initialValues);
                break;
            default:
                throw new IllegalArgumentException("Cannot insert from URL: " + uri);
        }

        Uri uriResult = ContentUris.withAppendedId(ClockContract.AlarmsColumns.CONTENT_URI, rowId);
        getContext().getContentResolver().notifyChange(uriResult, null);
        return uriResult;
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        int count;
        String primaryKey;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        switch (sURLMatcher.match(uri)) {
            case ALARMS:
                count = db.delete(ClockDatabaseHelper.ALARMS_TABLE_NAME, where, whereArgs);
                break;
            case ALARMS_ID:
                primaryKey = uri.getLastPathSegment();
                if (TextUtils.isEmpty(where)) {
                    where = ClockContract.AlarmsColumns._ID + "=" + primaryKey;
                } else {
                    where = ClockContract.AlarmsColumns._ID + "=" + primaryKey +
                            " AND (" + where + ")";
                }
                count = db.delete(ClockDatabaseHelper.ALARMS_TABLE_NAME, where, whereArgs);
                break;
            case INSTANCES:
                count = db.delete(ClockDatabaseHelper.INSTANCES_TABLE_NAME, where, whereArgs);
                break;
            case INSTANCES_ID:
                primaryKey = uri.getLastPathSegment();
                if (TextUtils.isEmpty(where)) {
                    where = ClockContract.InstancesColumns._ID + "=" + primaryKey;
                } else {
                    where = ClockContract.InstancesColumns._ID + "=" + primaryKey +
                            " AND (" + where + ")";
                }
                count = db.delete(ClockDatabaseHelper.INSTANCES_TABLE_NAME, where, whereArgs);
                break;
            case CITIES:
                count = db.delete(ClockDatabaseHelper.CITIES_TABLE_NAME, where, whereArgs);
                break;
            case CITIES_ID:
                primaryKey = uri.getLastPathSegment();
                if (TextUtils.isEmpty(where)) {
                    where = ClockContract.CitiesColumns.CITY_ID + "=" + primaryKey;
                } else {
                    where = ClockContract.CitiesColumns.CITY_ID +"=" + primaryKey +
                            " AND (" + where + ")";
                }
                count = db.delete(ClockDatabaseHelper.CITIES_TABLE_NAME, where, whereArgs);
                break;
            default:
                throw new IllegalArgumentException("Cannot delete from URL: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
