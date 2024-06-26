/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package org.omnirom.deskclock.preference;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import org.omnirom.deskclock.widget.ColorPickerDialog;

public class ColorPickerPreference extends Preference implements DialogInterface.OnDismissListener {

    private static String TAG = "ColorPickerPreference";
    public static final int DEFAULT_COLOR = 0xFFFFFFFF; //White

    private ImageView mLightColorView;
    private Resources mResources;
    private int mColorValue;
    private Dialog mDialog;

    /**
     * @param context
     * @param attrs
     */
    public ColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mColorValue = DEFAULT_COLOR;
        init();
    }

    public ColorPickerPreference(Context context, int color) {
        super(context, null);
        mColorValue = color;
        init();
    }

    private void init() {
        setLayoutResource(org.omnirom.deskclock.R.layout.preference_color_picker);
        mResources = getContext().getResources();
    }

    public void setColor(int color) {
        mColorValue = color;
        updatePreferenceViews();
    }

    public int getColor() {
        return mColorValue;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        mLightColorView = (ImageView) view.findViewById(org.omnirom.deskclock.R.id.light_color);

        updatePreferenceViews();
    }

    private void updatePreferenceViews() {
        final int width = (int) mResources.getDimension(org.omnirom.deskclock.R.dimen.color_preference_width);
        final int height = (int) mResources.getDimension(org.omnirom.deskclock.R.dimen.color_preference_height);

        if (mLightColorView != null) {
            mLightColorView.setEnabled(true);
            mLightColorView.setImageDrawable(createRectShape(width, height, mColorValue));
        }
    }

    @Override
    protected void onClick() {
        if (mDialog != null && mDialog.isShowing()) return;
        mDialog = getDialog();
        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    public Dialog getDialog() {
        final ColorPickerDialog d = new ColorPickerDialog(getContext(), mColorValue, true);

        d.setButton(AlertDialog.BUTTON_POSITIVE, mResources.getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mColorValue =  d.getColor();
                updatePreferenceViews();
                callChangeListener(this);
            }
        });
        d.setButton(AlertDialog.BUTTON_NEGATIVE, mResources.getString(android.R.string.cancel),
                (DialogInterface.OnClickListener) null);

        return d;
    }

    private static ShapeDrawable createRectShape(int width, int height, int color) {
        ShapeDrawable shape = new ShapeDrawable(new RectShape());
        shape.setIntrinsicHeight(height);
        shape.setIntrinsicWidth(width);
        shape.getPaint().setColor(color);
        return shape;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mDialog = null;
    }
}
