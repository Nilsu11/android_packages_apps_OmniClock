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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class PlaylistDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener {

    private EditText mLabelBox;
    private PlaylistDialogHandler mHandler;

    public static PlaylistDialogFragment newInstance(PlaylistDialogHandler handler) {
        final PlaylistDialogFragment frag = new PlaylistDialogFragment();
        frag.setHandler(handler);
        return frag;
    }

    private void setHandler(PlaylistDialogHandler handler) {
        mHandler = handler;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
        .setTitle(R.string.playlist_file_name)
        .setPositiveButton(android.R.string.ok, this)
        .setNegativeButton(android.R.string.cancel, null)
        .setView(createDialogView());

        Dialog d = builder.create();
        d.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        return d;
    }

    private View createDialogView() {
        final LayoutInflater inflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater.inflate(R.layout.label_dialog, null);
        mLabelBox = (EditText) view.findViewById(R.id.labelBox);
        return view;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            String label = mLabelBox.getText().toString();
            mHandler.onPlaylistDialogSet(label);
        }
    }

    interface PlaylistDialogHandler {
        void onPlaylistDialogSet(String label);
    }
}
