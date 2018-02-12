package net.gregbeaty.flipview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class FlipView extends RecyclerView {
    public static final int HORIZONTAL = OrientationHelper.HORIZONTAL;
    public static final int VERTICAL = OrientationHelper.VERTICAL;
    public static final int DISTANCE_PER_POSITION = 180;

    private static final int MAX_SHADOW_ALPHA = 180;
    private static final int MAX_SHADE_ALPHA = 130;
    private static final int MAX_SHINE_ALPHA = 100;

    private final Rect mTopClippingRect = new Rect();
    private final Rect mBottomClippingRect = new Rect();
    private final Rect mRightClippingRect = new Rect();
    private final Rect mLeftClippingRect = new Rect();

    private Camera mCamera = new Camera();
    private Matrix mMatrix = new Matrix();

    private final Paint mShadowPaint = new Paint();
    private final Paint mShadePaint = new Paint();
    private final Paint mShinePaint = new Paint();

    private int mOrientation;

    private List<OnPositionChangeListener> mPositionChangeListeners;
    private Adapter mAdapter;
    private AdapterDataObserver mDataObserver;

    public FlipView(Context context) {
        this(context, null);
    }

    public FlipView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlipView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        FlipLayoutManager layoutManager = new FlipLayoutManager();
        layoutManager.setPositionChangeListener(new net.gregbeaty.flipview.OnPositionChangeListener() {
            @Override
            public void onPositionChange(FlipLayoutManager flipLayoutManager, int position) {
                if (mPositionChangeListeners == null) {
                    return;
                }

                for (OnPositionChangeListener listener : mPositionChangeListeners) {
                    listener.onPositionChange(FlipView.this, position);
                }
            }
        });

        super.setLayoutManager(layoutManager);

        setItemAnimator(new DefaultItemAnimator());

        mDataObserver = new AdapterDataObserver() {
            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);

                for (int i = positionStart; i < positionStart + itemCount; i++) {
                    if (getCurrentPosition() == i) {
                        scrollToPosition(positionStart - 1);
                        return;
                    }
                }

                requestLayout();
            }
        };
    }

    public void setOrientation(int orientation) {
        if (mOrientation == orientation) {
            return;
        }

        mOrientation = orientation;
        getLayoutManagerInternal().setOrientation(orientation);

        requestLayout();
        invalidate();
    }

    @Override
    public int getOverScrollMode() {
        return OVER_SCROLL_NEVER;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mTopClippingRect.top = 0;
        mTopClippingRect.left = 0;
        mTopClippingRect.right = getWidth();
        mTopClippingRect.bottom = getHeight() / 2;

        mBottomClippingRect.top = getHeight() / 2;
        mBottomClippingRect.left = 0;
        mBottomClippingRect.right = getWidth();
        mBottomClippingRect.bottom = getHeight();

        mLeftClippingRect.top = 0;
        mLeftClippingRect.left = 0;
        mLeftClippingRect.right = getWidth() / 2;
        mLeftClippingRect.bottom = getHeight();

        mRightClippingRect.top = 0;
        mRightClippingRect.left = getWidth() / 2;
        mRightClippingRect.right = getWidth();
        mRightClippingRect.bottom = getHeight();

        super.onLayout(changed, l, t, r, b);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (getLayoutManager() == null) {
            return super.onTouchEvent(e);
        }

        return getScrollState() != RecyclerView.SCROLL_STATE_SETTLING && super.onTouchEvent(e);
    }

    /**
     * @deprecated This view does not support customized layout managers.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public void setLayoutManager(LayoutManager layout) {
        throw new UnsupportedOperationException("This view does not support customized layout managers.");
    }

    private FlipLayoutManager getLayoutManagerInternal() {
        return ((FlipLayoutManager) super.getLayoutManager());
    }

    @Override
    public void setAdapter(Adapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterAdapterDataObserver(mDataObserver);
        }

        mAdapter = adapter;
        mAdapter.registerAdapterDataObserver(mDataObserver);

        super.setAdapter(adapter);
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        return false;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int viewCount = getChildCount();
        if (viewCount == 0) {
            return;
        }

        final boolean isVerticalScrolling = getLayoutManagerInternal().getOrientation() == FlipView.VERTICAL;
        final int angle = getLayoutManagerInternal().getAngle();
        final int currentPosition = getLayoutManagerInternal().getCurrentPosition();

        if (currentPosition == RecyclerView.NO_POSITION) {
            return;
        }

        View previousView = null;
        View currentView = null;
        View nextView = null;

        for (int i = 0; i < viewCount; i++) {
            View view = getChildAt(i);
            int position = getChildAdapterPosition(view);
            if (position == currentPosition - 1 && currentPosition - 1 >= 0) {
                previousView = view;
                continue;
            }

            if (position == currentPosition) {
                currentView = view;
                continue;
            }

            if (position == currentPosition + 1) {
                nextView = view;
            }
        }

        if (currentView == null) {
            return;
        }

        if (!getLayoutManagerInternal().isScrolling() && !getLayoutManagerInternal().requiresSettling()) {
            drawChild(canvas, currentView, 0);
            return;
        }

        //draw previous half
        canvas.save();
        canvas.clipRect(isVerticalScrolling ? mTopClippingRect : mLeftClippingRect);
        final View previousHalf = angle >= 90 ? previousView : currentView;
        if (previousHalf != null) {
            drawChild(canvas, previousHalf, 0);
        }

        if (angle > 90) {
            final int alpha = (int) (((angle - 90) / 90f) * MAX_SHADOW_ALPHA);
            mShadowPaint.setAlpha(alpha);
            canvas.drawPaint(mShadowPaint);
        }

        canvas.restore();

        //draw next half
        canvas.save();
        canvas.clipRect(isVerticalScrolling ? mBottomClippingRect : mRightClippingRect);
        final View nextHalf = angle >= 90 ? currentView : nextView;

        if (nextHalf != null) {
            drawChild(canvas, nextHalf, 0);
        }

        if (angle < 90) {
            final int alpha = (int) ((Math.abs(angle - 90) / 90f) * MAX_SHADOW_ALPHA);
            mShadowPaint.setAlpha(alpha);
            canvas.drawPaint(mShadowPaint);
        }

        canvas.restore();

        //draw flipping half
        canvas.save();
        mCamera.save();

        if (angle > 90) {
            canvas.clipRect(isVerticalScrolling ? mTopClippingRect : mLeftClippingRect);
            if (isVerticalScrolling) {
                mCamera.rotateX(angle - 180);
            } else {
                mCamera.rotateY(180 - angle);
            }
        } else {
            canvas.clipRect(isVerticalScrolling ? mBottomClippingRect : mRightClippingRect);
            if (isVerticalScrolling) {
                mCamera.rotateX(angle);
            } else {
                mCamera.rotateY(-angle);
            }
        }

        mCamera.getMatrix(mMatrix);

        mMatrix.preScale(0.25f, 0.25f);
        mMatrix.postScale(4.0f, 4.0f);
        mMatrix.preTranslate(-getWidth() / 2, -getHeight() / 2);
        mMatrix.postTranslate(getWidth() / 2, getHeight() / 2);

        canvas.concat(mMatrix);

        drawChild(canvas, currentView, 0);

        if (angle < 90) {
            final int alpha = (int) ((angle / 90f) * MAX_SHINE_ALPHA);
            mShinePaint.setAlpha(alpha);
            canvas.drawRect(isVerticalScrolling ? mBottomClippingRect : mRightClippingRect, mShinePaint);
        } else {
            final int alpha = (int) ((Math.abs(angle - 180) / 90f) * MAX_SHADE_ALPHA);
            mShadePaint.setAlpha(alpha);
            canvas.drawRect(isVerticalScrolling ? mTopClippingRect : mLeftClippingRect, mShadePaint);
        }

        mCamera.restore();
        canvas.restore();
    }

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);

        if (state != RecyclerView.SCROLL_STATE_IDLE) {
            return;
        }

        if (!getLayoutManagerInternal().requiresSettling()) {
            return;
        }

        smoothScrollToPosition(getLayoutManagerInternal().getCurrentPosition());
    }

    public void addOnPositionChangeListener(OnPositionChangeListener listener) {
        if (mPositionChangeListeners == null) {
            mPositionChangeListeners = new ArrayList<>();
        }

        mPositionChangeListeners.add(listener);
    }

    public void removeOnPositionChangeListener(OnPositionChangeListener listener) {
        if (mPositionChangeListeners == null) {
            return;
        }

        mPositionChangeListeners.remove(listener);
    }

    public void clearOnPositionChangeListeners() {
        if (mPositionChangeListeners == null) {
            return;
        }

        mPositionChangeListeners.clear();
    }

    public int getItemCount() {
        return getLayoutManagerInternal().getItemCount();
    }

    public int getCurrentPosition() {
        return getLayoutManagerInternal().getCurrentPosition();
    }

    public int getScrollDistance() {
        return getLayoutManagerInternal().getScrollDistance();
    }

    public int getAngle() {
        return getLayoutManagerInternal().getAngle();
    }

    public boolean isScrolling() {
        return getLayoutManagerInternal().isScrolling();
    }

    public interface OnPositionChangeListener {
        void onPositionChange(FlipView flipView, int position);
    }
}
