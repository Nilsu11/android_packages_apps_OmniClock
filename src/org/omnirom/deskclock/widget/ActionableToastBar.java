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
 * limitations under the License
 */

package org.omnirom.deskclock.widget;
import org.omnirom.deskclock.R;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A custom {@link View} that exposes an action to the user.
 * <p>
 * This is a copy of packages/apps/UnifiedEmail/src/com/android/mail/ui/ActionableToastBar.java
import org.omnirom.deskclock.R;
 * with minor modifications.
 */
public class ActionableToastBar extends LinearLayout {
    private boolean mHidden = false;
    private Animator mShowAnimation;
    private Animator mHideAnimation;
    private final int mBottomMarginSizeInConversation;

    /** Icon for the description. */
    private ImageView mActionDescriptionIcon;
    /** The clickable view */
    private View mActionButton;
    /** Icon for the action button. */
    private ImageView mActionIcon;
    /** The view that contains the description. */
    private TextView mActionDescriptionView;
    /** The view that contains the text for the action button. */
    private TextView mActionText;
    //private ToastBarOperation mOperation;

    public ActionableToastBar(Context context) {
        this(context, null);
    }

    public ActionableToastBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionableToastBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mBottomMarginSizeInConversation = context.getResources().getDimensionPixelSize(
                org.omnirom.deskclock.R.dimen.toast_bar_bottom_margin_in_conversation);
        LayoutInflater.from(context).inflate(org.omnirom.deskclock.R.layout.actionable_toast_row, this, true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mActionDescriptionIcon = (ImageView) findViewById(org.omnirom.deskclock.R.id.description_icon);
        mActionDescriptionView = (TextView) findViewById(org.omnirom.deskclock.R.id.description_text);
        mActionButton = findViewById(org.omnirom.deskclock.R.id.action_button);
        mActionIcon = (ImageView) findViewById(org.omnirom.deskclock.R.id.action_icon);
        mActionIcon.setColorFilter(getResources().getColor(org.omnirom.deskclock.R.color.undo_color));
        mActionText = (TextView) findViewById(org.omnirom.deskclock.R.id.action_text);
        mActionText.setTextColor(getResources().getColor(org.omnirom.deskclock.R.color.undo_color));
    }

    /**
     * Tells the view that it will be appearing in the conversation pane
     * and should adjust its layout parameters accordingly.
     * @param isInConversationMode true if the view will be shown in the conversation view
     */
    public void setConversationMode(boolean isInConversationMode) {
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        params.bottomMargin = isInConversationMode ? mBottomMarginSizeInConversation : 0;
        setLayoutParams(params);
    }

    /**
     * Displays the toast bar and makes it visible. Allows the setting of
     * parameters to customize the display.
     * @param listener performs some action when the action button is clicked
     * @param descriptionIconResourceId resource ID for the description icon or
     * 0 if no icon should be shown
     * @param descriptionText a description text to show in the toast bar
     * @param showActionIcon if true, the action button icon should be shown
     * @param actionTextResource resource ID for the text to show in the action button
     * @param replaceVisibleToast if true, this toast should replace any currently visible toast.
     * Otherwise, skip showing this toast.
     */
    public void show(final ActionClickedListener listener, int descriptionIconResourceId,
            CharSequence descriptionText, boolean showActionIcon, int actionTextResource,
            boolean replaceVisibleToast) {

        if (!mHidden && !replaceVisibleToast) {
            return;
        }

        mActionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View widget) {
                if (listener != null) {
                    listener.onActionClicked();
                }
                hide(true);
            }
        });

        // Set description icon.
        if (descriptionIconResourceId == 0) {
            mActionDescriptionIcon.setVisibility(GONE);
        } else {
            mActionDescriptionIcon.setVisibility(VISIBLE);
            mActionDescriptionIcon.setImageResource(descriptionIconResourceId);
        }

        mActionDescriptionView.setText(descriptionText);
        mActionIcon.setVisibility(showActionIcon ? VISIBLE : GONE);
        mActionText.setText(actionTextResource);

        mHidden = false;
        getShowAnimation().start();
    }

    /**
     * Hides the view and resets the state.
     */
    public void hide(boolean animate) {
        // Prevent multiple call to hide.
        // Also prevent hiding if show animation is going on.
        if (!mHidden && !getShowAnimation().isRunning()) {
            mHidden = true;
            if (getVisibility() == View.VISIBLE) {
                mActionDescriptionView.setText("");
                mActionButton.setOnClickListener(null);
                // Hide view once it's clicked.
                if (animate) {
                    getHideAnimation().start();
                } else {
                    setAlpha(0);
                    setVisibility(View.GONE);
                }
            }
        }
    }

    private Animator getShowAnimation() {
        if (mShowAnimation == null) {
            mShowAnimation = AnimatorInflater.loadAnimator(getContext(), org.omnirom.deskclock.R.anim.abc_fade_in);
            mShowAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    // There is a tiny change that and hide animation could have finished right
                    // before the show animation finished.  In that case, the hide will mark the
                    // view as GONE.  We need to make sure the last one wins.
                    setVisibility(View.VISIBLE);
                }
            });
            mShowAnimation.setTarget(this);
        }
        return mShowAnimation;
    }

    private Animator getHideAnimation() {
        if (mHideAnimation == null) {
            mHideAnimation = AnimatorInflater.loadAnimator(getContext(), org.omnirom.deskclock.R.anim.abc_fade_out);
            mHideAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.GONE);
                }
            });
            mHideAnimation.setTarget(this);
        }
        return mHideAnimation;
    }

    public boolean isEventInToastBar(MotionEvent event) {
        if (!isShown()) {
            return false;
        }
        int[] xy = new int[2];
        float x = event.getX();
        float y = event.getY();
        getLocationOnScreen(xy);
        return (x > xy[0] && x < (xy[0] + getWidth()) && y > xy[1] && y < xy[1] + getHeight());
    }

    /**
     * Classes that wish to perform some action when the action button is clicked
     * should implement this interface.
     */
    public interface ActionClickedListener {
        public void onActionClicked();
    }
}
