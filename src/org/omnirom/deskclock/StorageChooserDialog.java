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

package org.omnirom.deskclock;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StorageChooserDialog extends DialogFragment
    implements DialogInterface.OnClickListener {

    private File mSDCardDirectory;
    private File mCurrentSelection;
    private List<File> mSubDirs;
    private ArrayAdapter<File> mListAdapter;
    private ListView mListView;
    private String mTag;
    private int mTextColor;
    private int mTextColorDisabled;
    private ChosenStorageListener mListener;

    public interface ChosenStorageListener {
        public void onChooseDirOk(Uri chosenDir);
        public void onChooseDirCancel();
    }

    public static StorageChooserDialog newInstance(ChosenStorageListener listener) {
        StorageChooserDialog fragment = new StorageChooserDialog();
        fragment.setChoosenListener(listener);
        return fragment;
    }

    public StorageChooserDialog() {
        mSDCardDirectory = Environment.getExternalStorageDirectory();
    }

    public static File getDefaultStartDirectory() {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File startFolder;
        if (musicDir.exists() && musicDir.isDirectory()) {
            startFolder = musicDir;
        } else {
            File externalStorage = Environment.getExternalStorageDirectory();
            if (externalStorage.exists() && externalStorage.isDirectory()) {
                startFolder = externalStorage;
            } else {
                startFolder = new File("/"); // root
            }
        }
        return startFolder;
    }

    public void setChoosenListener(ChosenStorageListener listener) {
        mListener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.folder_dialog_title)
                .setPositiveButton("", this)
                .setNegativeButton(android.R.string.cancel, this)
                .setView(createDialogView());

        return builder.create();
    }

    private static File tryGetCanonicalFile(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            e.printStackTrace();
            return file;
        }
    }

    private List<File> getDirectories(File dir) {
        List<File> dirs = new ArrayList<File>();
        File dirFile = tryGetCanonicalFile(dir);

        try {
            if (!dirFile.exists() || !dirFile.isDirectory()) {
                return dirs;
            }

            if (dirFile.equals(mSDCardDirectory.getParentFile())) {
                dirs.add(0, mSDCardDirectory);
            }
            for (File file : dirFile.listFiles()) {
                if (!file.getName().startsWith(".") && !file.getAbsolutePath().equals("/storage/self")) {
                    dirs.add(file);
                }
            }
        } catch (Exception e) {
        }

        Collections.sort(dirs, new Comparator<File>() {
            public int compare(File o1, File o2) {
                if (o1.isDirectory() && o2.isFile()) {
                    return -1;
                }
                if (o2.isDirectory() && o1.isFile()) {
                    return 1;
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });
        if (!dir.equals(new File("/storage"))) {
            dirs.add(0, new File(".."));
        }
        return dirs;
    }

    private void updateDirectory() {
        mSubDirs.clear();
        mSubDirs.addAll(getDirectories(mCurrentSelection));
        mListAdapter.notifyDataSetChanged();
        boolean enableOk = !mCurrentSelection.equals(new File("/storage")) && !mCurrentSelection.equals(mSDCardDirectory.getParentFile());
        ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setText(getResources().getString(android.R.string.ok));
        ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(enableOk ? View.VISIBLE : View.GONE);
    }

    private ArrayAdapter<File> createListAdapter(List<File> items) {
        return new ArrayAdapter<File>(getActivity(),
                R.layout.folder_item, R.id.folder_name, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View item = null;
                if (convertView == null){
                    final LayoutInflater inflater = (LayoutInflater) getActivity()
                            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    item = inflater.inflate(R.layout.folder_item, null);
                } else {
                    item = convertView;
                }
                TextView tv = (TextView) item.findViewById(R.id.folder_name);
                File f = mSubDirs.get(position);
                tv.setText(f.getName());
                if (f.isFile()) {
                    tv.setTextColor(mTextColorDisabled);
                } else {
                    tv.setTextColor(mTextColor);
                }
                return item;
            }
        };
    }

    private View createDialogView() {
        final LayoutInflater inflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater
                .inflate(R.layout.folder_dialog, null);

        mTextColor = Utils.getColorAttr(getActivity(), android.R.attr.textColorPrimary);
        boolean light = Utils.isLightTheme(getActivity());
        if (light) {
            mTextColorDisabled = getActivity().getResources().getColor(R.color.folder_dialog_file_color);
        } else {
            mTextColorDisabled = getActivity().getResources().getColor(R.color.folder_dialog_file_color_dark);
        }

        mCurrentSelection = getDefaultStartDirectory();
        mSubDirs = getDirectories(mCurrentSelection);

        mListAdapter = createListAdapter(mSubDirs);
        mListView = (ListView) view.findViewById(R.id.folders);
        mListView.setAdapter(mListAdapter);

        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                File f = mListAdapter.getItem(position);
                if (f.getName().equals("..")) {
                    mCurrentSelection = mCurrentSelection.getParentFile();
                } else {
                    // Navigate into the sub-directory
                    mCurrentSelection = mListAdapter.getItem(position);
                }
                updateDirectory();
            }
        });

        return view;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            if (mCurrentSelection.isDirectory()){
                Uri uri = Uri.fromFile(mCurrentSelection);
                mListener.onChooseDirOk(uri);
            }
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            mListener.onChooseDirCancel();
        }
    }
}
