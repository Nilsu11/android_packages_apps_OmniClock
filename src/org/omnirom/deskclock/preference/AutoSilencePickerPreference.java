/*
 * Based on: http://www.lukehorvat.com/blog/android-numberpickerdialogpreference/
 * Thanks to the original author!
 */

package org.omnirom.deskclock.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import org.omnirom.deskclock.R;

public class AutoSilencePickerPreference extends NumberPickerPreference
{
    private CheckBox mNeverCheckbox;
    public AutoSilencePickerPreference(Context context) {
        this(context, null);
    }

    public AutoSilencePickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setMinValue(DEFAULT_MIN_VALUE);
        setMaxValue(DEFAULT_MAX_VALUE);
    }

    @Override
    public View createDialogView(View view) {
        view = LayoutInflater.from(mContext).inflate(R.layout.auto_silence_picker, null);
        view = super.createDialogView(view);

        mNeverCheckbox = (CheckBox) view.findViewById(R.id.never_checkbox);
        mNeverCheckbox.setChecked(getValue() == -1);
        mNumberPicker.setEnabled(getValue() != -1);
        mNeverCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mNumberPicker.setEnabled(!isChecked);
            }
        });
        return view;
    }

    @Override
    public void setMinValue(int minValue) {
        mMinValue = minValue;
    }

    @Override
    public void setValue(int value) {
        if (value != mValue) {
            mValue = value;
            persistInt(value);
            notifyChanged();
        }
    }

    @Override
    protected int getSelectedValue() {
        if (mNeverCheckbox.isChecked()) {
            return -1;
        }
        return mNumberPicker.getValue();
    }
}