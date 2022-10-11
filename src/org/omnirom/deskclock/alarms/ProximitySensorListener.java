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

package org.omnirom.deskclock.alarms;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import org.omnirom.deskclock.LogUtils;

public class ProximitySensorListener implements SensorEventListener {

    private static final String TAG = "ProximitySensorListener";

    private boolean mStopped;
    private boolean mIsNear;
    private int mNearCount;
    private final Sensor mProxiSensor;
    private SensorManager mSensorManager;
    private Runnable mWaveAction;

    public ProximitySensorListener(Runnable waveAction, SensorManager sensorManager) {
        mWaveAction = waveAction;
        mProxiSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    public void onAccuracyChanged(Sensor sensor, int acc) {
    }

    public void reset() {
        mIsNear = false;
        mStopped = false;
        mNearCount = 0;
    }

    public void onSensorChanged(SensorEvent event) {
        if (mStopped) {
            return;
        }

        mIsNear = event.values[0] < mProxiSensor.getMaximumRange();
        LogUtils.v(TAG, "Range: " + event.values[0]);

        if (mIsNear) {
            LogUtils.v(TAG, "Sensor covered");
            mNearCount++;
            return;
        }

        // Sensor might have been covered when alarm goes off
        if (!mIsNear && mNearCount > 1) {
            LogUtils.v(TAG, "Wave triggered");
            mStopped = true;
            mWaveAction.run();
        }
    }
}
