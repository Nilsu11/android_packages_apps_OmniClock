package org.omnirom.deskclock;

import android.annotation.TargetApi;
import android.content.Intent;
import android.service.quicksettings.TileService;

import static android.provider.AlarmClock.ACTION_SET_ALARM;

@TargetApi(24)
public class AlarmTileService extends TileService {
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        Intent createAlarm = new Intent(this,HandleApiCalls.class);
        createAlarm.setAction(ACTION_SET_ALARM);
        startActivity(createAlarm);
    }
}
