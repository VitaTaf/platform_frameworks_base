/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.IBinder;
import android.transition.Transition;
import android.transition.Transition.EpicenterCallback;
import android.transition.Transition.TransitionListener;
import android.transition.Transition.TransitionListenerAdapter;
import android.transition.TransitionInflater;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import java.lang.ref.WeakReference;

/**
 * <p>A popup window that can be used to display an arbitrary view. The popup
 * window is a floating container that appears on top of the current
 * activity.</p>
 *
 * @see android.widget.AutoCompleteTextView
 * @see android.widget.Spinner
 */
public class PopupWindow {
    /**
     * Mode for {@link #setInputMethodMode(int)}: the requirements for the
     * input method should be based on the focusability of the popup.  That is
     * if it is focusable than it needs to work with the input method, else
     * it doesn't.
     */
    public static final int INPUT_METHOD_FROM_FOCUSABLE = 0;

    /**
     * Mode for {@link #setInputMethodMode(int)}: this popup always needs to
     * work with an input method, regardless of whether it is focusable.  This
     * means that it will always be displayed so that the user can also operate
     * the input method while it is shown.
     */
    public static final int INPUT_METHOD_NEEDED = 1;

    /**
     * Mode for {@link #setInputMethodMode(int)}: this popup never needs to
     * work with an input method, regardless of whether it is focusable.  This
     * means that it will always be displayed to use as much space on the
     * screen as needed, regardless of whether this covers the input method.
     */
    public static final int INPUT_METHOD_NOT_NEEDED = 2;

    private static final int DEFAULT_ANCHORED_GRAVITY = Gravity.TOP | Gravity.START;

    /**
     * Default animation style indicating that separate animations should be
     * used for top/bottom anchoring states.
     */
    private static final int ANIMATION_STYLE_DEFAULT = -1;

    private final int[] mDrawingLocation = new int[2];
    private final int[] mScreenLocation = new int[2];
    private final Rect mTempRect = new Rect();
    private final Rect mAnchorBounds = new Rect();

    private Context mContext;
    private WindowManager mWindowManager;

    private boolean mIsShowing;
    private boolean mIsTransitioningToDismiss;
    private boolean mIsDropdown;

    /** View that handles event dispatch and content transitions. */
    private PopupDecorView mDecorView;

    /** The contents of the popup. */
    private View mContentView;

    private boolean mFocusable;
    private int mInputMethodMode = INPUT_METHOD_FROM_FOCUSABLE;
    private int mSoftInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
    private boolean mTouchable = true;
    private boolean mOutsideTouchable = false;
    private boolean mClippingEnabled = true;
    private int mSplitTouchEnabled = -1;
    private boolean mLayoutInScreen;
    private boolean mClipToScreen;
    private boolean mAllowScrollingAnchorParent = true;
    private boolean mLayoutInsetDecor = false;
    private boolean mNotTouchModal;
    private boolean mAttachedInDecor = true;
    private boolean mAttachedInDecorSet = false;

    private OnTouchListener mTouchInterceptor;

    private int mWidthMode;
    private int mWidth = LayoutParams.WRAP_CONTENT;
    private int mLastWidth;
    private int mHeightMode;
    private int mHeight = LayoutParams.WRAP_CONTENT;
    private int mLastHeight;

    private int mPopupWidth;
    private int mPopupHeight;

    private float mElevation;

    private Drawable mBackground;
    private Drawable mAboveAnchorBackgroundDrawable;
    private Drawable mBelowAnchorBackgroundDrawable;

    private Transition mEnterTransition;
    private Transition mExitTransition;

    private boolean mAboveAnchor;
    private int mWindowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;

    private OnDismissListener mOnDismissListener;
    private boolean mIgnoreCheekPress = false;

    private int mAnimationStyle = ANIMATION_STYLE_DEFAULT;

    private static final int[] ABOVE_ANCHOR_STATE_SET = new int[] {
        com.android.internal.R.attr.state_above_anchor
    };

    private WeakReference<View> mAnchor;

    private final EpicenterCallback mEpicenterCallback = new EpicenterCallback() {
        @Override
        public Rect onGetEpicenter(Transition transition) {
            final View anchor = mAnchor != null ? mAnchor.get() : null;
            final View decor = mDecorView;
            if (anchor == null || decor == null) {
                return null;
            }

            final Rect anchorBounds = mAnchorBounds;
            final int[] anchorLocation = anchor.getLocationOnScreen();
            final int[] popupLocation = mDecorView.getLocationOnScreen();

            // Compute the position of the anchor relative to the popup.
            anchorBounds.set(0, 0, anchor.getWidth(), anchor.getHeight());
            anchorBounds.offset(anchorLocation[0] - popupLocation[0],
                    anchorLocation[1] - popupLocation[1]);

            return anchorBounds;
        }
    };

    private final OnScrollChangedListener mOnScrollChangedListener = new OnScrollChangedListener() {
        @Override
        public void onScrollChanged() {
            final View anchor = mAnchor != null ? mAnchor.get() : null;
            if (anchor != null && mDecorView != null) {
                final WindowManager.LayoutParams p = (WindowManager.LayoutParams)
                        mDecorView.getLayoutParams();

                updateAboveAnchor(findDropDownPosition(anchor, p, mAnchorXoff, mAnchorYoff,
                        mAnchoredGravity));
                update(p.x, p.y, -1, -1, true);
            }
        }
    };

    private int mAnchorXoff;
    private int mAnchorYoff;
    private int mAnchoredGravity;
    private boolean mOverlapAnchor;

    private boolean mPopupViewInitialLayoutDirectionInherited;

    /**
     * <p>Create a new empty, non focusable popup window of dimension (0,0).</p>
     *
     * <p>The popup does provide a background.</p>
     */
    public PopupWindow(Context context) {
        this(context, null);
    }

    /**
     * <p>Create a new empty, non focusable popup window of dimension (0,0).</p>
     *
     * <p>The popup does provide a background.</p>
     */
    public PopupWindow(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.popupWindowStyle);
    }

    /**
     * <p>Create a new empty, non focusable popup window of dimension (0,0).</p>
     *
     * <p>The popup does provide a background.</p>
     */
    public PopupWindow(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    /**
     * <p>Create a new, empty, non focusable popup window of dimension (0,0).</p>
     *
     * <p>The popup does not provide a background.</p>
     */
    public PopupWindow(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.PopupWindow, defStyleAttr, defStyleRes);
        final Drawable bg = a.getDrawable(R.styleable.PopupWindow_popupBackground);
        mElevation = a.getDimension(R.styleable.PopupWindow_popupElevation, 0);
        mOverlapAnchor = a.getBoolean(R.styleable.PopupWindow_overlapAnchor, false);

        // Preserve default behavior from Gingerbread. If the animation is
        // undefined or explicitly specifies the Gingerbread animation style,
        // use a sentinel value.
        if (a.hasValueOrEmpty(R.styleable.PopupWindow_popupAnimationStyle)) {
            final int animStyle = a.getResourceId(R.styleable.PopupWindow_popupAnimationStyle, 0);
            if (animStyle == R.style.Animation_PopupWindow) {
                mAnimationStyle = ANIMATION_STYLE_DEFAULT;
            } else {
                mAnimationStyle = animStyle;
            }
        } else {
            mAnimationStyle = ANIMATION_STYLE_DEFAULT;
        }

        final Transition enterTransition = getTransition(a.getResourceId(
                R.styleable.PopupWindow_popupEnterTransition, 0));
        final Transition exitTransition;
        if (a.hasValueOrEmpty(R.styleable.PopupWindow_popupExitTransition)) {
            exitTransition = getTransition(a.getResourceId(
                    R.styleable.PopupWindow_popupExitTransition, 0));
        } else {
            exitTransition = enterTransition == null ? null : enterTransition.clone();
        }

        a.recycle();

        setEnterTransition(enterTransition);
        setExitTransition(exitTransition);
        setBackgroundDrawable(bg);
    }

    /**
     * <p>Create a new empty, non focusable popup window of dimension (0,0).</p>
     *
     * <p>The popup does not provide any background. This should be handled
     * by the content view.</p>
     */
    public PopupWindow() {
        this(null, 0, 0);
    }

    /**
     * <p>Create a new non focusable popup window which can display the
     * <tt>contentView</tt>. The dimension of the window are (0,0).</p>
     *
     * <p>The popup does not provide any background. This should be handled
     * by the content view.</p>
     *
     * @param contentView the popup's content
     */
    public PopupWindow(View contentView) {
        this(contentView, 0, 0);
    }

    /**
     * <p>Create a new empty, non focusable popup window. The dimension of the
     * window must be passed to this constructor.</p>
     *
     * <p>The popup does not provide any background. This should be handled
     * by the content view.</p>
     *
     * @param width the popup's width
     * @param height the popup's height
     */
    public PopupWindow(int width, int height) {
        this(null, width, height);
    }

    /**
     * <p>Create a new non focusable popup window which can display the
     * <tt>contentView</tt>. The dimension of the window must be passed to
     * this constructor.</p>
     *
     * <p>The popup does not provide any background. This should be handled
     * by the content view.</p>
     *
     * @param contentView the popup's content
     * @param width the popup's width
     * @param height the popup's height
     */
    public PopupWindow(View contentView, int width, int height) {
        this(contentView, width, height, false);
    }

    /**
     * <p>Create a new popup window which can display the <tt>contentView</tt>.
     * The dimension of the window must be passed to this constructor.</p>
     *
     * <p>The popup does not provide any background. This should be handled
     * by the content view.</p>
     *
     * @param contentView the popup's content
     * @param width the popup's width
     * @param height the popup's height
     * @param focusable true if the popup can be focused, false otherwise
     */
    public PopupWindow(View contentView, int width, int height, boolean focusable) {
        if (contentView != null) {
            mContext = contentView.getContext();
            mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        }

        setContentView(contentView);
        setWidth(width);
        setHeight(height);
        setFocusable(focusable);
    }

    public void setEnterTransition(Transition enterTransition) {
        mEnterTransition = enterTransition;

        if (mEnterTransition != null) {
            mEnterTransition.setEpicenterCallback(mEpicenterCallback);
        }
    }

    public void setExitTransition(Transition exitTransition) {
        mExitTransition = exitTransition;

        if (mExitTransition != null) {
            mExitTransition.setEpicenterCallback(mEpicenterCallback);
        }
    }

    private Transition getTransition(int resId) {
        if (resId != 0 && resId != R.transition.no_transition) {
            final TransitionInflater inflater = TransitionInflater.from(mContext);
            final Transition transition = inflater.inflateTransition(resId);
            if (transition != null) {
                final boolean isEmpty = transition instanceof TransitionSet
                        && ((TransitionSet) transition).getTransitionCount() == 0;
                if (!isEmpty) {
                    return transition;
                }
            }
        }
        return null;
    }

    /**
     * Return the drawable used as the popup window's background.
     *
     * @return the background drawable or {@code null} if not set
     * @see #setBackgroundDrawable(Drawable)
     * @attr ref android.R.styleable#PopupWindow_popupBackground
     */
    public Drawable getBackground() {
        return mBackground;
    }

    /**
     * Specifies the background drawable for this popup window. The background
     * can be set to {@code null}.
     *
     * @param background the popup's background
     * @see #getBackground()
     * @attr ref android.R.styleable#PopupWindow_popupBackground
     */
    public void setBackgroundDrawable(Drawable background) {
        mBackground = background;

        // If this is a StateListDrawable, try to find and store the drawable to be
        // used when the drop-down is placed above its anchor view, and the one to be
        // used when the drop-down is placed below its anchor view. We extract
        // the drawables ourselves to work around a problem with using refreshDrawableState
        // that it will take into account the padding of all drawables specified in a
        // StateListDrawable, thus adding superfluous padding to drop-down views.
        //
        // We assume a StateListDrawable will have a drawable for ABOVE_ANCHOR_STATE_SET and
        // at least one other drawable, intended for the 'below-anchor state'.
        if (mBackground instanceof StateListDrawable) {
            StateListDrawable stateList = (StateListDrawable) mBackground;

            // Find the above-anchor view - this one's easy, it should be labeled as such.
            int aboveAnchorStateIndex = stateList.getStateDrawableIndex(ABOVE_ANCHOR_STATE_SET);

            // Now, for the below-anchor view, look for any other drawable specified in the
            // StateListDrawable which is not for the above-anchor state and use that.
            int count = stateList.getStateCount();
            int belowAnchorStateIndex = -1;
            for (int i = 0; i < count; i++) {
                if (i != aboveAnchorStateIndex) {
                    belowAnchorStateIndex = i;
                    break;
                }
            }

            // Store the drawables we found, if we found them. Otherwise, set them both
            // to null so that we'll just use refreshDrawableState.
            if (aboveAnchorStateIndex != -1 && belowAnchorStateIndex != -1) {
                mAboveAnchorBackgroundDrawable = stateList.getStateDrawable(aboveAnchorStateIndex);
                mBelowAnchorBackgroundDrawable = stateList.getStateDrawable(belowAnchorStateIndex);
            } else {
                mBelowAnchorBackgroundDrawable = null;
                mAboveAnchorBackgroundDrawable = null;
            }
        }
    }

    /**
     * @return the elevation for this popup window in pixels
     * @see #setElevation(float)
     * @attr ref android.R.styleable#PopupWindow_popupElevation
     */
    public float getElevation() {
        return mElevation;
    }

    /**
     * Specifies the elevation for this popup window.
     *
     * @param elevation the popup's elevation in pixels
     * @see #getElevation()
     * @attr ref android.R.styleable#PopupWindow_popupElevation
     */
    public void setElevation(float elevation) {
        mElevation = elevation;
    }

    /**
     * <p>Return the animation style to use the popup appears and disappears</p>
     *
     * @return the animation style to use the popup appears and disappears
     */
    public int getAnimationStyle() {
        return mAnimationStyle;
    }

    /**
     * Set the flag on popup to ignore cheek press events; by default this flag
     * is set to false
     * which means the popup will not ignore cheek press dispatch events.
     *
     * <p>If the popup is showing, calling this method will take effect only
     * the next time the popup is shown or through a manual call to one of
     * the {@link #update()} methods.</p>
     *
     * @see #update()
     */
    public void setIgnoreCheekPress() {
        mIgnoreCheekPress = true;
    }


    /**
     * <p>Change the animation style resource for this popup.</p>
     *
     * <p>If the popup is showing, calling this method will take effect only
     * the next time the popup is shown or through a manual call to one of
     * the {@link #update()} methods.</p>
     *
     * @param animationStyle animation style to use when the popup appears
     *      and disappears.  Set to -1 for the default animation, 0 for no
     *      animation, or a resource identifier for an explicit animation.
     *
     * @see #update()
     */
    public void setAnimationStyle(int animationStyle) {
        mAnimationStyle = animationStyle;
    }

    /**
     * <p>Return the view used as the content of the popup window.</p>
     *
     * @return a {@link android.view.View} representing the popup's content
     *
     * @see #setContentView(android.view.View)
     */
    public View getContentView() {
        return mContentView;
    }

    /**
     * <p>Change the popup's content. The content is represented by an instance
     * of {@link android.view.View}.</p>
     *
     * <p>This method has no effect if called when the popup is showing.</p>
     *
     * @param contentView the new content for the popup
     *
     * @see #getContentView()
     * @see #isShowing()
     */
    public void setContentView(View contentView) {
        if (isShowing()) {
            return;
        }

        mContentView = contentView;

        if (mContext == null && mContentView != null) {
            mContext = mContentView.getContext();
        }

        if (mWindowManager == null && mContentView != null) {
            mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        }

        // Setting the default for attachedInDecor based on SDK version here
        // instead of in the constructor since we might not have the context
        // object in the constructor. We only want to set default here if the
        // app hasn't already set the attachedInDecor.
        if (mContext != null && !mAttachedInDecorSet) {
            // Attach popup window in decor frame of parent window by default for
            // {@link Build.VERSION_CODES.LOLLIPOP_MR1} or greater. Keep current
            // behavior of not attaching to decor frame for older SDKs.
            setAttachedInDecor(mContext.getApplicationInfo().targetSdkVersion
                    >= Build.VERSION_CODES.LOLLIPOP_MR1);
        }

    }

    /**
     * Set a callback for all touch events being dispatched to the popup
     * window.
     */
    public void setTouchInterceptor(OnTouchListener l) {
        mTouchInterceptor = l;
    }

    /**
     * <p>Indicate whether the popup window can grab the focus.</p>
     *
     * @return true if the popup is focusable, false otherwise
     *
     * @see #setFocusable(boolean)
     */
    public boolean isFocusable() {
        return mFocusable;
    }

    /**
     * <p>Changes the focusability of the popup window. When focusable, the
     * window will grab the focus from the current focused widget if the popup
     * contains a focusable {@link android.view.View}.  By default a popup
     * window is not focusable.</p>
     *
     * <p>If the popup is showing, calling this method will take effect only
     * the next time the popup is shown or through a manual call to one of
     * the {@link #update()} methods.</p>
     *
     * @param focusable true if the popup should grab focus, false otherwise.
     *
     * @see #isFocusable()
     * @see #isShowing()
     * @see #update()
     */
    public void setFocusable(boolean focusable) {
        mFocusable = focusable;
    }

    /**
     * Return the current value in {@link #setInputMethodMode(int)}.
     *
     * @see #setInputMethodMode(int)
     */
    public int getInputMethodMode() {
        return mInputMethodMode;

    }

    /**
     * Control how the popup operates with an input method: one of
     * {@link #INPUT_METHOD_FROM_FOCUSABLE}, {@link #INPUT_METHOD_NEEDED},
     * or {@link #INPUT_METHOD_NOT_NEEDED}.
     *
     * <p>If the popup is showing, calling this method will take effect only
     * the next time the popup is shown or through a manual call to one of
     * the {@link #update()} methods.</p>
     *
     * @see #getInputMethodMode()
     * @see #update()
     */
    public void setInputMethodMode(int mode) {
        mInputMethodMode = mode;
    }

    /**
     * Sets the operating mode for the soft input area.
     *
     * @param mode The desired mode, see
     *        {@link android.view.WindowManager.LayoutParams#softInputMode}
     *        for the full list
     *
     * @see android.view.WindowManager.LayoutParams#softInputMode
     * @see #getSoftInputMode()
     */
    public void setSoftInputMode(int mode) {
        mSoftInputMode = mode;
    }

    /**
     * Returns the current value in {@link #setSoftInputMode(int)}.
     *
     * @see #setSoftInputMode(int)
     * @see android.view.WindowManager.LayoutParams#softInputMode
     */
    public int getSoftInputMode() {
        return mSoftInputMode;
    }

    /**
     * <p>Indicates whether the popup window receives touch events.</p>
     *
     * @return true if the popup is touchable, false otherwise
     *
     * @see #setTouchable(boolean)
     */
    public boolean isTouchable() {
        return mTouchable;
    }

    /**
     * <p>Changes the touchability of the popup window. When touchable, the
     * window will receive touch events, otherwise touch events will go to the
     * window below it. By default the window is touchable.</p>
     *
     * <p>If the popup is showing, calling this method will take effect only
     * the next time the popup is shown or through a manual call to one of
     * the {@link #update()} methods.</p>
     *
     * @param touchable true if the popup should receive touch events, false otherwise
     *
     * @see #isTouchable()
     * @see #isShowing()
     * @see #update()
     */
    public void setTouchable(boolean touchable) {
        mTouchable = touchable;
    }

    /**
     * <p>Indicates whether the popup window will be informed of touch events
     * outside of its window.</p>
     *
     * @return true if the popup is outside touchable, false otherwise
     *
     * @see #setOutsideTouchable(boolean)
     */
    public boolean isOutsideTouchable() {
        return mOutsideTouchable;
    }

    /**
     * <p>Controls whether the pop-up will be informed of touch events outside
     * of its window.  This only makes sense for pop-ups that are touchable
     * but not focusable, which means touches outside of the window will
     * be delivered to the window behind.  The default is false.</p>
     *
     * <p>If the popup is showing, calling this method will take effect only
     * the next time the popup is shown or through a manual call to one of
     * the {@link #update()} methods.</p>
     *
     * @param touchable true if the popup should receive outside
     * touch events, false otherwise
     *
     * @see #isOutsideTouchable()
     * @see #isShowing()
     * @see #update()
     */
    public void setOutsideTouchable(boolean touchable) {
        mOutsideTouchable = touchable;
    }

    /**
     * <p>Indicates whether clipping of the popup window is enabled.</p>
     *
     * @return true if the clipping is enabled, false otherwise
     *
     * @see #setClippingEnabled(boolean)
     */
    public boolean isClippingEnabled() {
        return mClippingEnabled;
    }

    /**
     * <p>Allows the popup window to extend beyond the bounds of the screen. By default the
     * window is clipped to the screen boundaries. Setting this to false will allow windows to be
     * accurately positioned.</p>
     *
     * <p>If the popup is showing, calling this method will take effect only
     * the next time the popup is shown or through a manual call to one of
     * the {@link #update()} methods.</p>
     *
     * @param enabled false if the window should be allowed to extend outside of the screen
     * @see #isShowing()
     * @see #isClippingEnabled()
     * @see #update()
     */
    public void setClippingEnabled(boolean enabled) {
        mClippingEnabled = enabled;
    }

    /**
     * Clip this popup window to the screen, but not to the containing window.
     *
     * @param enabled True to clip to the screen.
     * @hide
     */
    public void setClipToScreenEnabled(boolean enabled) {
        mClipToScreen = enabled;
        setClippingEnabled(!enabled);
    }

    /**
     * Allow PopupWindow to scroll the anchor's parent to provide more room
     * for the popup. Enabled by default.
     *
     * @param enabled True to scroll the anchor's parent when more room is desired by the popup.
     */
    void setAllowScrollingAnchorParent(boolean enabled) {
        mAllowScrollingAnchorParent = enabled;
    }

    /**
     * <p>Indicates whether the popup window supports splitting touches.</p>
     *
     * @return true if the touch splitting is enabled, false otherwise
     *
     * @see #setSplitTouchEnabled(boolean)
     */
    public boolean isSplitTouchEnabled() {
        if (mSplitTouchEnabled < 0 && mContext != null) {
            return mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.HONEYCOMB;
        }
        return mSplitTouchEnabled == 1;
    }

    /**
     * <p>Allows the popup window to split touches across other windows that also
     * support split touch.  When this flag is false, the first pointer
     * that goes down determines the window to which all subsequent touches
     * go until all pointers go up.  When this flag is true, each pointer
     * (not necessarily the first) that goes down determines the window
     * to which all subsequent touches of that pointer will go until that
     * pointer goes up thereby enabling touches with multiple pointers
     * to be split across multiple windows.</p>
     *
     * @param enabled true if the split touches should be enabled, false otherwise
     * @see #isSplitTouchEnabled()
     */
    public void setSplitTouchEnabled(boolean enabled) {
        mSplitTouchEnabled = enabled ? 1 : 0;
    }

    /**
     * <p>Indicates whether the popup window will be forced into using absolute screen coordinates
     * for positioning.</p>
     *
     * @return true if the window will always be positioned in screen coordinates.
     * @hide
     */
    public boolean isLayoutInScreenEnabled() {
        return mLayoutInScreen;
    }

    /**
     * <p>Allows the popup window to force the flag
     * {@link WindowManager.LayoutParams#FLAG_LAYOUT_IN_SCREEN}, overriding default behavior.
     * This will cause the popup to be positioned in absolute screen coordinates.</p>
     *
     * @param enabled true if the popup should always be positioned in screen coordinates
     * @hide
     */
    public void setLayoutInScreenEnabled(boolean enabled) {
        mLayoutInScreen = enabled;
    }

    /**
     * <p>Indicates whether the popup window will be attached in the decor frame of its parent
     * window.
     *
     * @return true if the window will be attached to the decor frame of its parent window.
     *
     * @see #setAttachedInDecor(boolean)
     * @see WindowManager.LayoutParams#FLAG_LAYOUT_ATTACHED_IN_DECOR
     */
    public boolean isAttachedInDecor() {
        return mAttachedInDecor;
    }

    /**
     * <p>This will attach the popup window to the decor frame of the parent window to avoid
     * overlaping with screen decorations like the navigation bar. Overrides the default behavior of
     * the flag {@link WindowManager.LayoutParams#FLAG_LAYOUT_ATTACHED_IN_DECOR}.
     *
     * <p>By default the flag is set on SDK version {@link Build.VERSION_CODES#LOLLIPOP_MR1} or
     * greater and cleared on lesser SDK versions.
     *
     * @param enabled true if the popup should be attached to the decor frame of its parent window.
     *
     * @see WindowManager.LayoutParams#FLAG_LAYOUT_ATTACHED_IN_DECOR
     */
    public void setAttachedInDecor(boolean enabled) {
        mAttachedInDecor = enabled;
        mAttachedInDecorSet = true;
    }

    /**
     * Allows the popup window to force the flag
     * {@link WindowManager.LayoutParams#FLAG_LAYOUT_INSET_DECOR}, overriding default behavior.
     * This will cause the popup to inset its content to account for system windows overlaying
     * the screen, such as the status bar.
     *
     * <p>This will often be combined with {@link #setLayoutInScreenEnabled(boolean)}.
     *
     * @param enabled true if the popup's views should inset content to account for system windows,
     *                the way that decor views behave for full-screen windows.
     * @hide
     */
    public void setLayoutInsetDecor(boolean enabled) {
        mLayoutInsetDecor = enabled;
    }

    /**
     * Set the layout type for this window. Should be one of the TYPE constants defined in
     * {@link WindowManager.LayoutParams}.
     *
     * @param layoutType Layout type for this window.
     * @hide
     */
    public void setWindowLayoutType(int layoutType) {
        mWindowLayoutType = layoutType;
    }

    /**
     * @return The layout type for this window.
     * @hide
     */
    public int getWindowLayoutType() {
        return mWindowLayoutType;
    }

    /**
     * Set whether this window is touch modal or if outside touches will be sent to
     * other windows behind it.
     * @hide
     */
    public void setTouchModal(boolean touchModal) {
        mNotTouchModal = !touchModal;
    }

    /**
     * <p>Change the width and height measure specs that are given to the
     * window manager by the popup.  By default these are 0, meaning that
     * the current width or height is requested as an explicit size from
     * the window manager.  You can supply
     * {@link ViewGroup.LayoutParams#WRAP_CONTENT} or
     * {@link ViewGroup.LayoutParams#MATCH_PARENT} to have that measure
     * spec supplied instead, replacing the absolute width and height that
     * has been set in the popup.</p>
     *
     * <p>If the popup is showing, calling this method will take effect only
     * the next time the popup is shown.</p>
     *
     * @param widthSpec an explicit width measure spec mode, either
     * {@link ViewGroup.LayoutParams#WRAP_CONTENT},
     * {@link ViewGroup.LayoutParams#MATCH_PARENT}, or 0 to use the absolute
     * width.
     * @param heightSpec an explicit height measure spec mode, either
     * {@link ViewGroup.LayoutParams#WRAP_CONTENT},
     * {@link ViewGroup.LayoutParams#MATCH_PARENT}, or 0 to use the absolute
     * height.
     *
     * @deprecated Use {@link #setWidth(int)} and {@link #setHeight(int)}.
     */
    @Deprecated
    public void setWindowLayoutMode(int widthSpec, int heightSpec) {
        mWidthMode = widthSpec;
        mHeightMode = heightSpec;
    }

    /**
     * Returns the popup's height MeasureSpec.
     *
     * @return the height MeasureSpec of the popup
     * @see #setHeight(int)
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * Sets the popup's height MeasureSpec.
     * <p>
     * If the popup is showing, calling this method will take effect the next
     * time the popup is shown.
     *
     * @param height the height MeasureSpec of the popup
     * @see #getHeight()
     * @see #isShowing()
     */
    public void setHeight(int height) {
        mHeight = height;
    }

    /**
     * Returns the popup's width MeasureSpec.
     *
     * @return the width MeasureSpec of the popup
     * @see #setWidth(int)
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * Sets the popup's width MeasureSpec.
     * <p>
     * If the popup is showing, calling this method will take effect the next
     * time the popup is shown.
     *
     * @param width the width MeasureSpec of the popup
     * @see #getWidth()
     * @see #isShowing()
     */
    public void setWidth(int width) {
        mWidth = width;
    }

    /**
     * Sets whether the popup window should overlap its anchor view when
     * displayed as a drop-down.
     * <p>
     * If the popup is showing, calling this method will take effect only
     * the next time the popup is shown.
     *
     * @param overlapAnchor Whether the popup should overlap its anchor.
     *
     * @see #getOverlapAnchor()
     * @see #isShowing()
     */
    public void setOverlapAnchor(boolean overlapAnchor) {
        mOverlapAnchor = overlapAnchor;
    }

    /**
     * Returns whether the popup window should overlap its anchor view when
     * displayed as a drop-down.
     *
     * @return Whether the popup should overlap its anchor.
     *
     * @see #setOverlapAnchor(boolean)
     */
    public boolean getOverlapAnchor() {
        return mOverlapAnchor;
    }

    /**
     * <p>Indicate whether this popup window is showing on screen.</p>
     *
     * @return true if the popup is showing, false otherwise
     */
    public boolean isShowing() {
        return mIsShowing;
    }

    /**
     * <p>
     * Display the content view in a popup window at the specified location. If the popup window
     * cannot fit on screen, it will be clipped. See {@link android.view.WindowManager.LayoutParams}
     * for more information on how gravity and the x and y parameters are related. Specifying
     * a gravity of {@link android.view.Gravity#NO_GRAVITY} is similar to specifying
     * <code>Gravity.LEFT | Gravity.TOP</code>.
     * </p>
     *
     * @param parent a parent view to get the {@link android.view.View#getWindowToken()} token from
     * @param gravity the gravity which controls the placement of the popup window
     * @param x the popup's x location offset
     * @param y the popup's y location offset
     */
    public void showAtLocation(View parent, int gravity, int x, int y) {
        showAtLocation(parent.getWindowToken(), gravity, x, y);
    }

    /**
     * Display the content view in a popup window at the specified location.
     *
     * @param token Window token to use for creating the new window
     * @param gravity the gravity which controls the placement of the popup window
     * @param x the popup's x location offset
     * @param y the popup's y location offset
     *
     * @hide Internal use only. Applications should use
     *       {@link #showAtLocation(View, int, int, int)} instead.
     */
    public void showAtLocation(IBinder token, int gravity, int x, int y) {
        if (isShowing() || mContentView == null) {
            return;
        }

        TransitionManager.endTransitions(mDecorView);

        unregisterForScrollChanged();

        mIsShowing = true;
        mIsDropdown = false;

        final WindowManager.LayoutParams p = createPopupLayoutParams(token);
        preparePopup(p);

        // Only override the default if some gravity was specified.
        if (gravity != Gravity.NO_GRAVITY) {
            p.gravity = gravity;
        }

        p.x = x;
        p.y = y;

        invokePopup(p);
    }

    /**
     * Display the content view in a popup window anchored to the bottom-left
     * corner of the anchor view. If there is not enough room on screen to show
     * the popup in its entirety, this method tries to find a parent scroll
     * view to scroll. If no parent scroll view can be scrolled, the
     * bottom-left corner of the popup is pinned at the top left corner of the
     * anchor view.
     *
     * @param anchor the view on which to pin the popup window
     *
     * @see #dismiss()
     */
    public void showAsDropDown(View anchor) {
        showAsDropDown(anchor, 0, 0);
    }

    /**
     * Display the content view in a popup window anchored to the bottom-left
     * corner of the anchor view offset by the specified x and y coordinates.
     * If there is not enough room on screen to show the popup in its entirety,
     * this method tries to find a parent scroll view to scroll. If no parent
     * scroll view can be scrolled, the bottom-left corner of the popup is
     * pinned at the top left corner of the anchor view.
     * <p>
     * If the view later scrolls to move <code>anchor</code> to a different
     * location, the popup will be moved correspondingly.
     *
     * @param anchor the view on which to pin the popup window
     * @param xoff A horizontal offset from the anchor in pixels
     * @param yoff A vertical offset from the anchor in pixels
     *
     * @see #dismiss()
     */
    public void showAsDropDown(View anchor, int xoff, int yoff) {
        showAsDropDown(anchor, xoff, yoff, DEFAULT_ANCHORED_GRAVITY);
    }

    /**
     * Displays the content view in a popup window anchored to the corner of
     * another view. The window is positioned according to the specified
     * gravity and offset by the specified x and y coordinates.
     * <p>
     * If there is not enough room on screen to show the popup in its entirety,
     * this method tries to find a parent scroll view to scroll. If no parent
     * view can be scrolled, the specified vertical gravity will be ignored and
     * the popup will anchor itself such that it is visible.
     * <p>
     * If the view later scrolls to move <code>anchor</code> to a different
     * location, the popup will be moved correspondingly.
     *
     * @param anchor the view on which to pin the popup window
     * @param xoff A horizontal offset from the anchor in pixels
     * @param yoff A vertical offset from the anchor in pixels
     * @param gravity Alignment of the popup relative to the anchor
     *
     * @see #dismiss()
     */
    public void showAsDropDown(View anchor, int xoff, int yoff, int gravity) {
        if (isShowing() || mContentView == null) {
            return;
        }

        TransitionManager.endTransitions(mDecorView);

        registerForScrollChanged(anchor, xoff, yoff, gravity);

        mIsShowing = true;
        mIsDropdown = true;

        final WindowManager.LayoutParams p = createPopupLayoutParams(anchor.getWindowToken());
        preparePopup(p);

        final boolean aboveAnchor = findDropDownPosition(anchor, p, xoff, yoff, gravity);
        updateAboveAnchor(aboveAnchor);

        invokePopup(p);
    }

    private void updateAboveAnchor(boolean aboveAnchor) {
        if (aboveAnchor != mAboveAnchor) {
            mAboveAnchor = aboveAnchor;

            if (mBackground != null) {
                // If the background drawable provided was a StateListDrawable with above-anchor
                // and below-anchor states, use those. Otherwise rely on refreshDrawableState to
                // do the job.
                if (mAboveAnchorBackgroundDrawable != null) {
                    if (mAboveAnchor) {
                        mDecorView.setBackground(mAboveAnchorBackgroundDrawable);
                    } else {
                        mDecorView.setBackground(mBelowAnchorBackgroundDrawable);
                    }
                } else {
                    mDecorView.refreshDrawableState();
                }
            }
        }
    }

    /**
     * Indicates whether the popup is showing above (the y coordinate of the popup's bottom
     * is less than the y coordinate of the anchor) or below the anchor view (the y coordinate
     * of the popup is greater than y coordinate of the anchor's bottom).
     *
     * The value returned
     * by this method is meaningful only after {@link #showAsDropDown(android.view.View)}
     * or {@link #showAsDropDown(android.view.View, int, int)} was invoked.
     *
     * @return True if this popup is showing above the anchor view, false otherwise.
     */
    public boolean isAboveAnchor() {
        return mAboveAnchor;
    }

    /**
     * Prepare the popup by embedding it into a new ViewGroup if the background
     * drawable is not null. If embedding is required, the layout parameters'
     * height is modified to take into account the background's padding.
     *
     * @param p the layout parameters of the popup's content view
     */
    private void preparePopup(WindowManager.LayoutParams p) {
        if (mContentView == null || mContext == null || mWindowManager == null) {
            throw new IllegalStateException("You must specify a valid content view by "
                    + "calling setContentView() before attempting to show the popup.");
        }

        // The old decor view may be transitioning out. Make sure it finishes
        // and cleans up before we try to create another one.
        if (mDecorView != null) {
            mDecorView.cancelTransitions();
        }

        // When a background is available, we embed the content view within
        // another view that owns the background drawable.
        final View backgroundView;
        if (mBackground != null) {
            backgroundView = createBackgroundView(mContentView);
            backgroundView.setBackground(mBackground);
        } else {
            backgroundView = mContentView;
        }

        mDecorView = createDecorView(backgroundView);

        // The background owner should be elevated so that it casts a shadow.
        backgroundView.setElevation(mElevation);

        // We may wrap that in another view, so we'll need to manually specify
        // the surface insets.
        final int surfaceInset = (int) Math.ceil(backgroundView.getZ() * 2);
        p.surfaceInsets.set(surfaceInset, surfaceInset, surfaceInset, surfaceInset);
        p.hasManualSurfaceInsets = true;

        mPopupViewInitialLayoutDirectionInherited =
                (mContentView.getRawLayoutDirection() == View.LAYOUT_DIRECTION_INHERIT);
        mPopupWidth = p.width;
        mPopupHeight = p.height;
    }

    /**
     * Wraps a content view in a PopupViewContainer.
     *
     * @param contentView the content view to wrap
     * @return a PopupViewContainer that wraps the content view
     */
    private PopupBackgroundView createBackgroundView(View contentView) {
        final ViewGroup.LayoutParams layoutParams = mContentView.getLayoutParams();
        final int height;
        if (layoutParams != null && layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            height = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        final PopupBackgroundView backgroundView = new PopupBackgroundView(mContext);
        final PopupBackgroundView.LayoutParams listParams = new PopupBackgroundView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        backgroundView.addView(contentView, listParams);

        return backgroundView;
    }

    /**
     * Wraps a content view in a FrameLayout.
     *
     * @param contentView the content view to wrap
     * @return a FrameLayout that wraps the content view
     */
    private PopupDecorView createDecorView(View contentView) {
        final ViewGroup.LayoutParams layoutParams = mContentView.getLayoutParams();
        final int height;
        if (layoutParams != null && layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            height = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        final PopupDecorView decorView = new PopupDecorView(mContext);
        decorView.addView(contentView, ViewGroup.LayoutParams.MATCH_PARENT, height);
        decorView.setClipChildren(false);
        decorView.setClipToPadding(false);

        return decorView;
    }

    /**
     * <p>Invoke the popup window by adding the content view to the window
     * manager.</p>
     *
     * <p>The content view must be non-null when this method is invoked.</p>
     *
     * @param p the layout parameters of the popup's content view
     */
    private void invokePopup(WindowManager.LayoutParams p) {
        if (mContext != null) {
            p.packageName = mContext.getPackageName();
        }

        final PopupDecorView decorView = mDecorView;
        decorView.setFitsSystemWindows(mLayoutInsetDecor);
        decorView.requestEnterTransition(mEnterTransition);

        setLayoutDirectionFromAnchor();

        mWindowManager.addView(decorView, p);
    }

    private void setLayoutDirectionFromAnchor() {
        if (mAnchor != null) {
            View anchor = mAnchor.get();
            if (anchor != null && mPopupViewInitialLayoutDirectionInherited) {
                mDecorView.setLayoutDirection(anchor.getLayoutDirection());
            }
        }
    }

    /**
     * <p>Generate the layout parameters for the popup window.</p>
     *
     * @param token the window token used to bind the popup's window
     *
     * @return the layout parameters to pass to the window manager
     */
    private WindowManager.LayoutParams createPopupLayoutParams(IBinder token) {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        // These gravity settings put the view at the top left corner of the
        // screen. The view is then positioned to the appropriate location by
        // setting the x and y offsets to match the anchor's bottom-left
        // corner.
        p.gravity = Gravity.START | Gravity.TOP;
        p.flags = computeFlags(p.flags);
        p.type = mWindowLayoutType;
        p.token = token;
        p.softInputMode = mSoftInputMode;
        p.windowAnimations = computeAnimationResource();

        if (mBackground != null) {
            p.format = mBackground.getOpacity();
        } else {
            p.format = PixelFormat.TRANSLUCENT;
        }

        if (mHeightMode < 0) {
            p.height = mLastHeight = mHeightMode;
        } else {
            p.height = mLastHeight = mHeight;
        }

        if (mWidthMode < 0) {
            p.width = mLastWidth = mWidthMode;
        } else {
            p.width = mLastWidth = mWidth;
        }

        // Used for debugging.
        p.setTitle("PopupWindow:" + Integer.toHexString(hashCode()));

        return p;
    }

    private int computeFlags(int curFlags) {
        curFlags &= ~(
                WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
        if(mIgnoreCheekPress) {
            curFlags |= WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;
        }
        if (!mFocusable) {
            curFlags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            if (mInputMethodMode == INPUT_METHOD_NEEDED) {
                curFlags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }
        } else if (mInputMethodMode == INPUT_METHOD_NOT_NEEDED) {
            curFlags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        if (!mTouchable) {
            curFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        if (mOutsideTouchable) {
            curFlags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        }
        if (!mClippingEnabled) {
            curFlags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        }
        if (isSplitTouchEnabled()) {
            curFlags |= WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
        }
        if (mLayoutInScreen) {
            curFlags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        }
        if (mLayoutInsetDecor) {
            curFlags |= WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        }
        if (mNotTouchModal) {
            curFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }
        if (mAttachedInDecor) {
          curFlags |= WindowManager.LayoutParams.FLAG_LAYOUT_ATTACHED_IN_DECOR;
        }
        return curFlags;
    }

    private int computeAnimationResource() {
        if (mAnimationStyle == ANIMATION_STYLE_DEFAULT) {
            if (mIsDropdown) {
                return mAboveAnchor
                        ? com.android.internal.R.style.Animation_DropDownUp
                        : com.android.internal.R.style.Animation_DropDownDown;
            }
            return 0;
        }
        return mAnimationStyle;
    }

    /**
     * Positions the popup window on screen. When the popup window is too tall
     * to fit under the anchor, a parent scroll view is seeked and scrolled up
     * to reclaim space. If scrolling is not possible or not enough, the popup
     * window gets moved on top of the anchor.
     * <p>
     * The height must have been set on the layout parameters prior to calling
     * this method.
     *
     * @param anchor the view on which the popup window must be anchored
     * @param p the layout parameters used to display the drop down
     * @param xoff horizontal offset used to adjust for background padding
     * @param yoff vertical offset used to adjust for background padding
     * @param gravity horizontal gravity specifying popup alignment
     * @return true if the popup is translated upwards to fit on screen
     */
    private boolean findDropDownPosition(View anchor, WindowManager.LayoutParams p, int xoff,
            int yoff, int gravity) {
        final int anchorHeight = anchor.getHeight();
        final int anchorWidth = anchor.getWidth();
        if (mOverlapAnchor) {
            yoff -= anchorHeight;
        }

        anchor.getLocationInWindow(mDrawingLocation);
        p.x = mDrawingLocation[0] + xoff;
        p.y = mDrawingLocation[1] + anchorHeight + yoff;

        final int hgrav = Gravity.getAbsoluteGravity(gravity, anchor.getLayoutDirection())
                & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (hgrav == Gravity.RIGHT) {
            // Flip the location to align the right sides of the popup and
            // anchor instead of left.
            p.x -= mPopupWidth - anchorWidth;
        }

        boolean onTop = false;

        p.gravity = Gravity.LEFT | Gravity.TOP;

        anchor.getLocationOnScreen(mScreenLocation);
        final Rect displayFrame = new Rect();
        anchor.getWindowVisibleDisplayFrame(displayFrame);

        final int screenY = mScreenLocation[1] + anchorHeight + yoff;
        final View root = anchor.getRootView();
        if (screenY + mPopupHeight > displayFrame.bottom
                || p.x + mPopupWidth - root.getWidth() > 0) {
            // If the drop down disappears at the bottom of the screen, we try
            // to scroll a parent scrollview or move the drop down back up on
            // top of the edit box.
            if (mAllowScrollingAnchorParent) {
                final int scrollX = anchor.getScrollX();
                final int scrollY = anchor.getScrollY();
                final Rect r = new Rect(scrollX, scrollY, scrollX + mPopupWidth + xoff,
                        scrollY + mPopupHeight + anchorHeight + yoff);
                anchor.requestRectangleOnScreen(r, true);
            }

            // Now we re-evaluate the space available, and decide from that
            // whether the pop-up will go above or below the anchor.
            anchor.getLocationInWindow(mDrawingLocation);
            p.x = mDrawingLocation[0] + xoff;
            p.y = mDrawingLocation[1] + anchorHeight + yoff;

            // Preserve the gravity adjustment.
            if (hgrav == Gravity.RIGHT) {
                p.x -= mPopupWidth - anchorWidth;
            }

            // Determine whether there is more space above or below the anchor.
            anchor.getLocationOnScreen(mScreenLocation);
            onTop = (displayFrame.bottom - mScreenLocation[1] - anchorHeight - yoff) <
                    (mScreenLocation[1] - yoff - displayFrame.top);
            if (onTop) {
                p.gravity = Gravity.LEFT | Gravity.BOTTOM;
                p.y = root.getHeight() - mDrawingLocation[1] + yoff;
            } else {
                p.y = mDrawingLocation[1] + anchorHeight + yoff;
            }
        }

        if (mClipToScreen) {
            final int displayFrameWidth = displayFrame.right - displayFrame.left;
            final int right = p.x + p.width;
            if (right > displayFrameWidth) {
                p.x -= right - displayFrameWidth;
            }

            if (p.x < displayFrame.left) {
                p.x = displayFrame.left;
                p.width = Math.min(p.width, displayFrameWidth);
            }

            if (onTop) {
                final int popupTop = mScreenLocation[1] + yoff - mPopupHeight;
                if (popupTop < 0) {
                    p.y += popupTop;
                }
            } else {
                p.y = Math.max(p.y, displayFrame.top);
            }
        }

        p.gravity |= Gravity.DISPLAY_CLIP_VERTICAL;

        return onTop;
    }

    /**
     * Returns the maximum height that is available for the popup to be
     * completely shown. It is recommended that this height be the maximum for
     * the popup's height, otherwise it is possible that the popup will be
     * clipped.
     *
     * @param anchor The view on which the popup window must be anchored.
     * @return The maximum available height for the popup to be completely
     *         shown.
     */
    public int getMaxAvailableHeight(View anchor) {
        return getMaxAvailableHeight(anchor, 0);
    }

    /**
     * Returns the maximum height that is available for the popup to be
     * completely shown. It is recommended that this height be the maximum for
     * the popup's height, otherwise it is possible that the popup will be
     * clipped.
     *
     * @param anchor The view on which the popup window must be anchored.
     * @param yOffset y offset from the view's bottom edge
     * @return The maximum available height for the popup to be completely
     *         shown.
     */
    public int getMaxAvailableHeight(View anchor, int yOffset) {
        return getMaxAvailableHeight(anchor, yOffset, false);
    }

    /**
     * Returns the maximum height that is available for the popup to be
     * completely shown, optionally ignoring any bottom decorations such as
     * the input method. It is recommended that this height be the maximum for
     * the popup's height, otherwise it is possible that the popup will be
     * clipped.
     *
     * @param anchor The view on which the popup window must be anchored.
     * @param yOffset y offset from the view's bottom edge
     * @param ignoreBottomDecorations if true, the height returned will be
     *        all the way to the bottom of the display, ignoring any
     *        bottom decorations
     * @return The maximum available height for the popup to be completely
     *         shown.
     *
     * @hide Pending API council approval.
     */
    public int getMaxAvailableHeight(View anchor, int yOffset, boolean ignoreBottomDecorations) {
        final Rect displayFrame = new Rect();
        anchor.getWindowVisibleDisplayFrame(displayFrame);

        final int[] anchorPos = mDrawingLocation;
        anchor.getLocationOnScreen(anchorPos);

        int bottomEdge = displayFrame.bottom;
        if (ignoreBottomDecorations) {
            Resources res = anchor.getContext().getResources();
            bottomEdge = res.getDisplayMetrics().heightPixels;
        }
        final int distanceToBottom = bottomEdge - (anchorPos[1] + anchor.getHeight()) - yOffset;
        final int distanceToTop = anchorPos[1] - displayFrame.top + yOffset;

        // anchorPos[1] is distance from anchor to top of screen
        int returnedHeight = Math.max(distanceToBottom, distanceToTop);
        if (mBackground != null) {
            mBackground.getPadding(mTempRect);
            returnedHeight -= mTempRect.top + mTempRect.bottom;
        }

        return returnedHeight;
    }

    /**
     * Disposes of the popup window. This method can be invoked only after
     * {@link #showAsDropDown(android.view.View)} has been executed. Failing
     * that, calling this method will have no effect.
     *
     * @see #showAsDropDown(android.view.View)
     */
    public void dismiss() {
        if (!isShowing() || mIsTransitioningToDismiss) {
            return;
        }

        final PopupDecorView decorView = mDecorView;
        final View contentView = mContentView;

        final ViewGroup contentHolder;
        final ViewParent contentParent = contentView.getParent();
        if (contentParent instanceof ViewGroup) {
            contentHolder = ((ViewGroup) contentParent);
        } else {
            contentHolder = null;
        }

        // Ensure any ongoing or pending transitions are canceled.
        decorView.cancelTransitions();

        unregisterForScrollChanged();

        mIsShowing = false;
        mIsTransitioningToDismiss = true;

        if (mExitTransition != null && decorView.isLaidOut()) {
            decorView.startExitTransition(mExitTransition, new TransitionListenerAdapter() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    dismissImmediate(decorView, contentHolder, contentView);
                }
            });
        } else {
            dismissImmediate(decorView, contentHolder, contentView);
        }

        if (mOnDismissListener != null) {
            mOnDismissListener.onDismiss();
        }
    }

    /**
     * Removes the popup from the window manager and tears down the supporting
     * view hierarchy, if necessary.
     */
    private void dismissImmediate(View decorView, ViewGroup contentHolder, View contentView) {
        // If this method gets called and the decor view doesn't have a parent,
        // then it was either never added or was already removed. That should
        // never happen, but it's worth checking to avoid potential crashes.
        if (decorView.getParent() != null) {
            mWindowManager.removeViewImmediate(decorView);
        }

        if (contentHolder != null) {
            contentHolder.removeView(contentView);
        }

        // This needs to stay until after all transitions have ended since we
        // need the reference to cancel transitions in preparePopup().
        mDecorView = null;
        mIsTransitioningToDismiss = false;
    }

    /**
     * Sets the listener to be called when the window is dismissed.
     *
     * @param onDismissListener The listener.
     */
    public void setOnDismissListener(OnDismissListener onDismissListener) {
        mOnDismissListener = onDismissListener;
    }

    /**
     * Updates the state of the popup window, if it is currently being displayed,
     * from the currently set state.
     * <p>
     * This includes:
     * <ul>
     *     <li>{@link #setClippingEnabled(boolean)}</li>
     *     <li>{@link #setFocusable(boolean)}</li>
     *     <li>{@link #setIgnoreCheekPress()}</li>
     *     <li>{@link #setInputMethodMode(int)}</li>
     *     <li>{@link #setTouchable(boolean)}</li>
     *     <li>{@link #setAnimationStyle(int)}</li>
     * </ul>
     */
    public void update() {
        if (!isShowing() || mContentView == null) {
            return;
        }

        final WindowManager.LayoutParams p =
                (WindowManager.LayoutParams) mDecorView.getLayoutParams();

        boolean update = false;

        final int newAnim = computeAnimationResource();
        if (newAnim != p.windowAnimations) {
            p.windowAnimations = newAnim;
            update = true;
        }

        final int newFlags = computeFlags(p.flags);
        if (newFlags != p.flags) {
            p.flags = newFlags;
            update = true;
        }

        if (update) {
            setLayoutDirectionFromAnchor();
            mWindowManager.updateViewLayout(mDecorView, p);
        }
    }

    /**
     * Updates the dimension of the popup window.
     * <p>
     * Calling this function also updates the window with the current popup
     * state as described for {@link #update()}.
     *
     * @param width the new width, must be >= 0 or -1 to ignore
     * @param height the new height, must be >= 0 or -1 to ignore
     */
    public void update(int width, int height) {
        final WindowManager.LayoutParams p =
                (WindowManager.LayoutParams) mDecorView.getLayoutParams();
        update(p.x, p.y, width, height, false);
    }

    /**
     * Updates the position and the dimension of the popup window.
     * <p>
     * Width and height can be set to -1 to update location only. Calling this
     * function also updates the window with the current popup state as
     * described for {@link #update()}.
     *
     * @param x the new x location
     * @param y the new y location
     * @param width the new width, must be >= 0 or -1 to ignore
     * @param height the new height, must be >= 0 or -1 to ignore
     */
    public void update(int x, int y, int width, int height) {
        update(x, y, width, height, false);
    }

    /**
     * Updates the position and the dimension of the popup window.
     * <p>
     * Width and height can be set to -1 to update location only. Calling this
     * function also updates the window with the current popup state as
     * described for {@link #update()}.
     *
     * @param x the new x location
     * @param y the new y location
     * @param width the new width, must be >= 0 or -1 to ignore
     * @param height the new height, must be >= 0 or -1 to ignore
     * @param force {@code true} to reposition the window even if the specified
     *              position already seems to correspond to the LayoutParams,
     *              {@code false} to only reposition if needed
     */
    public void update(int x, int y, int width, int height, boolean force) {
        if (width >= 0) {
            mLastWidth = width;
            setWidth(width);
        }

        if (height >= 0) {
            mLastHeight = height;
            setHeight(height);
        }

        if (!isShowing() || mContentView == null) {
            return;
        }

        final WindowManager.LayoutParams p =
                (WindowManager.LayoutParams) mDecorView.getLayoutParams();

        boolean update = force;

        final int finalWidth = mWidthMode < 0 ? mWidthMode : mLastWidth;
        if (width != -1 && p.width != finalWidth) {
            p.width = mLastWidth = finalWidth;
            update = true;
        }

        final int finalHeight = mHeightMode < 0 ? mHeightMode : mLastHeight;
        if (height != -1 && p.height != finalHeight) {
            p.height = mLastHeight = finalHeight;
            update = true;
        }

        if (p.x != x) {
            p.x = x;
            update = true;
        }

        if (p.y != y) {
            p.y = y;
            update = true;
        }

        final int newAnim = computeAnimationResource();
        if (newAnim != p.windowAnimations) {
            p.windowAnimations = newAnim;
            update = true;
        }

        final int newFlags = computeFlags(p.flags);
        if (newFlags != p.flags) {
            p.flags = newFlags;
            update = true;
        }

        if (update) {
            setLayoutDirectionFromAnchor();
            mWindowManager.updateViewLayout(mDecorView, p);
        }
    }

    /**
     * Updates the position and the dimension of the popup window.
     * <p>
     * Calling this function also updates the window with the current popup
     * state as described for {@link #update()}.
     *
     * @param anchor the popup's anchor view
     * @param width the new width, must be >= 0 or -1 to ignore
     * @param height the new height, must be >= 0 or -1 to ignore
     */
    public void update(View anchor, int width, int height) {
        update(anchor, false, 0, 0, true, width, height);
    }

    /**
     * Updates the position and the dimension of the popup window.
     * <p>
     * Width and height can be set to -1 to update location only. Calling this
     * function also updates the window with the current popup state as
     * described for {@link #update()}.
     * <p>
     * If the view later scrolls to move {@code anchor} to a different
     * location, the popup will be moved correspondingly.
     *
     * @param anchor the popup's anchor view
     * @param xoff x offset from the view's left edge
     * @param yoff y offset from the view's bottom edge
     * @param width the new width, must be >= 0 or -1 to ignore
     * @param height the new height, must be >= 0 or -1 to ignore
     */
    public void update(View anchor, int xoff, int yoff, int width, int height) {
        update(anchor, true, xoff, yoff, true, width, height);
    }

    private void update(View anchor, boolean updateLocation, int xoff, int yoff,
            boolean updateDimension, int width, int height) {

        if (!isShowing() || mContentView == null) {
            return;
        }

        final WeakReference<View> oldAnchor = mAnchor;
        final boolean needsUpdate = updateLocation && (mAnchorXoff != xoff || mAnchorYoff != yoff);
        if (oldAnchor == null || oldAnchor.get() != anchor || (needsUpdate && !mIsDropdown)) {
            registerForScrollChanged(anchor, xoff, yoff, mAnchoredGravity);
        } else if (needsUpdate) {
            // No need to register again if this is a DropDown, showAsDropDown already did.
            mAnchorXoff = xoff;
            mAnchorYoff = yoff;
        }

        if (updateDimension) {
            if (width == -1) {
                width = mPopupWidth;
            } else {
                mPopupWidth = width;
            }
            if (height == -1) {
                height = mPopupHeight;
            } else {
                mPopupHeight = height;
            }
        }

        final WindowManager.LayoutParams p =
                (WindowManager.LayoutParams) mDecorView.getLayoutParams();
        final int x = p.x;
        final int y = p.y;
        if (updateLocation) {
            updateAboveAnchor(findDropDownPosition(anchor, p, xoff, yoff, mAnchoredGravity));
        } else {
            updateAboveAnchor(findDropDownPosition(anchor, p, mAnchorXoff, mAnchorYoff,
                    mAnchoredGravity));
        }

        update(p.x, p.y, width, height, x != p.x || y != p.y);
    }

    /**
     * Listener that is called when this popup window is dismissed.
     */
    public interface OnDismissListener {
        /**
         * Called when this popup window is dismissed.
         */
        public void onDismiss();
    }

    private void unregisterForScrollChanged() {
        final WeakReference<View> anchorRef = mAnchor;
        final View anchor = anchorRef == null ? null : anchorRef.get();
        if (anchor != null) {
            final ViewTreeObserver vto = anchor.getViewTreeObserver();
            vto.removeOnScrollChangedListener(mOnScrollChangedListener);
        }

        mAnchor = null;
    }

    private void registerForScrollChanged(View anchor, int xoff, int yoff, int gravity) {
        unregisterForScrollChanged();

        mAnchor = new WeakReference<>(anchor);

        final ViewTreeObserver vto = anchor.getViewTreeObserver();
        if (vto != null) {
            vto.addOnScrollChangedListener(mOnScrollChangedListener);
        }

        mAnchorXoff = xoff;
        mAnchorYoff = yoff;
        mAnchoredGravity = gravity;
    }

    private class PopupDecorView extends FrameLayout {
        private TransitionListenerAdapter mPendingExitListener;

        public PopupDecorView(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (getKeyDispatcherState() == null) {
                    return super.dispatchKeyEvent(event);
                }

                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    final KeyEvent.DispatcherState state = getKeyDispatcherState();
                    if (state != null) {
                        state.startTracking(event, this);
                    }
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    final KeyEvent.DispatcherState state = getKeyDispatcherState();
                    if (state != null && state.isTracking(event) && !event.isCanceled()) {
                        dismiss();
                        return true;
                    }
                }
                return super.dispatchKeyEvent(event);
            } else {
                return super.dispatchKeyEvent(event);
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (mTouchInterceptor != null && mTouchInterceptor.onTouch(this, ev)) {
                return true;
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int x = (int) event.getX();
            final int y = (int) event.getY();

            if ((event.getAction() == MotionEvent.ACTION_DOWN)
                    && ((x < 0) || (x >= getWidth()) || (y < 0) || (y >= getHeight()))) {
                dismiss();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                dismiss();
                return true;
            } else {
                return super.onTouchEvent(event);
            }
        }

        /**
         * Requests that an enter transition run after the next layout pass.
         */
        public void requestEnterTransition(Transition transition) {
            final ViewTreeObserver observer = getViewTreeObserver();
            if (observer != null && transition != null) {
                final Transition enterTransition = transition.clone();

                // Postpone the enter transition after the first layout pass.
                observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final ViewTreeObserver observer = getViewTreeObserver();
                        if (observer != null) {
                            observer.removeOnGlobalLayoutListener(this);
                        }

                        startEnterTransition(enterTransition);
                    }
                });
            }
        }

        /**
         * Starts the pending enter transition, if one is set.
         */
        private void startEnterTransition(Transition enterTransition) {
            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                enterTransition.addTarget(child);
                child.setVisibility(View.INVISIBLE);
            }

            TransitionManager.beginDelayedTransition(this, enterTransition);

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                child.setVisibility(View.VISIBLE);
            }
        }

        /**
         * Starts an exit transition immediately.
         * <p>
         * <strong>Note:</strong> The transition listener is guaranteed to have
         * its {@code onTransitionEnd} method called even if the transition
         * never starts; however, it may be called with a {@code null} argument.
         */
        public void startExitTransition(Transition transition, final TransitionListener listener) {
            if (transition == null) {
                return;
            }

            // The exit listener MUST be called for cleanup, even if the
            // transition never starts or ends. Stash it for later.
            mPendingExitListener = new TransitionListenerAdapter() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    listener.onTransitionEnd(transition);

                    // The listener was called. Our job here is done.
                    mPendingExitListener = null;
                }
            };

            final Transition exitTransition = transition.clone();
            exitTransition.addListener(mPendingExitListener);

            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                exitTransition.addTarget(child);
            }

            TransitionManager.beginDelayedTransition(this, exitTransition);

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                child.setVisibility(View.INVISIBLE);
            }
        }

        /**
         * Cancels all pending or current transitions.
         */
        public void cancelTransitions() {
            TransitionManager.endTransitions(this);

            if (mPendingExitListener != null) {
                mPendingExitListener.onTransitionEnd(null);
            }
        }
    }

    private class PopupBackgroundView extends FrameLayout {
        public PopupBackgroundView(Context context) {
            super(context);
        }

        @Override
        protected int[] onCreateDrawableState(int extraSpace) {
            if (mAboveAnchor) {
                final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
                View.mergeDrawableStates(drawableState, ABOVE_ANCHOR_STATE_SET);
                return drawableState;
            } else {
                return super.onCreateDrawableState(extraSpace);
            }
        }
    }
}
