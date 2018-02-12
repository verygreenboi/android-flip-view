package net.gregbeaty.flipview;

import android.graphics.PointF;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

// TODO: Force all access through FlipView. Make this protected internal.
class FlipLayoutManager extends RecyclerView.LayoutManager {
    private final float INTERACTIVE_SCROLL_SPEED = 0.5f;
    private Integer mDecoratedChildWidth;
    private Integer mDecoratedChildHeight;
    private int mScrollState = RecyclerView.SCROLL_STATE_IDLE;
    private int mPositionBeforeScroll = RecyclerView.NO_POSITION;
    private int mOrientation = FlipView.VERTICAL;
    private int mScrollVector;
    private int mCurrentPosition = RecyclerView.NO_POSITION;
    private int mScrollDistance;
    private int mPreviousPosition;
    private OnPositionChangeListener mPositionChangeListener;

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return false;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    public boolean canScrollHorizontally() {
        return mOrientation == FlipView.HORIZONTAL;
    }

    @Override
    public boolean canScrollVertically() {
        return mOrientation == FlipView.VERTICAL;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        return scrollBy(dy, recycler, state);
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        return scrollBy(dx, recycler, state);
    }

    private int scrollBy(int delta, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (mDecoratedChildWidth == null || mDecoratedChildHeight == null) {
            return 0;
        }

        if (getChildCount() == 0) {
            return 0;
        }

        int modifiedDelta = delta;
        if (isInteractiveScroll()) {
            modifiedDelta = (int) (modifiedDelta > 0
                    ? Math.max(modifiedDelta * INTERACTIVE_SCROLL_SPEED, 1)
                    : Math.min(modifiedDelta * INTERACTIVE_SCROLL_SPEED, -1));
        }

        int desiredDistance = mScrollDistance + modifiedDelta;

        int desiredPosition = findPositionByScrollDistance(desiredDistance);
        if (desiredPosition < 0 || desiredPosition >= state.getItemCount()) {
            return 0;
        }

        if (mPositionBeforeScroll == RecyclerView.NO_POSITION) {
            mPositionBeforeScroll = getCurrentPosition();
        }

        if (mScrollVector == 0 && modifiedDelta != 0) {
            mScrollVector = modifiedDelta > 0 ? 1 : -1;
        }

        final int maxOverScrollDistance = 70;
        int minDistance = 0;
        int maxDistance = ((getItemCount() - 1) * FlipView.DISTANCE_PER_POSITION);

        if (desiredDistance < minDistance - maxOverScrollDistance || desiredDistance > maxDistance + maxOverScrollDistance) {
            return 0;
        }

        if (isInteractiveScroll()) {
            minDistance = (mPositionBeforeScroll - 1) * FlipView.DISTANCE_PER_POSITION;
            if (mScrollVector > 0) {
                minDistance = mPositionBeforeScroll * FlipView.DISTANCE_PER_POSITION;
            }

            maxDistance = (mPositionBeforeScroll + 1) * FlipView.DISTANCE_PER_POSITION;
            if (mScrollVector < 0) {
                maxDistance = mPositionBeforeScroll * FlipView.DISTANCE_PER_POSITION;
            }

            if (desiredDistance < minDistance || desiredDistance > maxDistance) {
                return 0;
            }
        }

        mScrollDistance = desiredDistance;
        mCurrentPosition = desiredPosition;

        fill(recycler, state);
        return modifiedDelta;
    }

    @Override
    public void onMeasure(final RecyclerView.Recycler recycler, final RecyclerView.State state, final int widthSpec, final int heightSpec) {
        mDecoratedChildWidth = null;
        mDecoratedChildHeight = null;

        super.onMeasure(recycler, state, widthSpec, heightSpec);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (state.isPreLayout()) {
            return;
        }

        if (state.getItemCount() == 0) {
            mCurrentPosition = RecyclerView.NO_POSITION;
            mScrollDistance = 0;
            removeAndRecycleAllViews(recycler);
        } else {
            if (mDecoratedChildWidth == null || mDecoratedChildHeight == null) {
                View scrap = recycler.getViewForPosition(0);
                addView(scrap);
                measureChildWithMargins(scrap, 0, 0);
                mDecoratedChildWidth = getDecoratedMeasuredWidth(scrap);
                mDecoratedChildHeight = getDecoratedMeasuredHeight(scrap);
                detachAndScrapView(scrap, recycler);
            }

            detachAndScrapAttachedViews(recycler);

            if (mCurrentPosition <= RecyclerView.NO_POSITION) {
                mCurrentPosition = 0;
                mScrollDistance = 0;
            } else if (mCurrentPosition >= state.getItemCount()) {
                mCurrentPosition = state.getItemCount() - 1;
                mScrollDistance = FlipView.DISTANCE_PER_POSITION * mCurrentPosition;
            }
        }

        fill(recycler, state);
    }

    private void fill(RecyclerView.Recycler recycler, RecyclerView.State state) {
        boolean layoutOnlyCurrentPosition = !isScrolling() && !requiresSettling();

        if (!layoutOnlyCurrentPosition) {
            addView(mCurrentPosition - 1, recycler, state);
        }

        addView(mCurrentPosition, recycler, state);

        if (!layoutOnlyCurrentPosition) {
            addView(mCurrentPosition + 1, recycler, state);
        }

        int previousPosition = mPreviousPosition;
        mPreviousPosition = mCurrentPosition;

        if (previousPosition != mCurrentPosition && mPositionChangeListener != null) {
            mPositionChangeListener.onPositionChange(this, mCurrentPosition);
        }

        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            int position = getPosition(child);

            if (position == mCurrentPosition - 1 || position == mCurrentPosition || position == mCurrentPosition + 1) {
                continue;
            }

            removeAndRecycleView(child, recycler);
        }
    }

    private void addView(int position, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (position <= RecyclerView.NO_POSITION) {
            return;
        }

        if (position >= state.getItemCount()) {
            return;
        }

        View view = recycler.getViewForPosition(position);
        addView(view);
        measureChildWithMargins(view, 0, 0);
        layoutDecorated(view, 0, 0, mDecoratedChildWidth, mDecoratedChildHeight);
    }

    public int getAngle() {
        return getAngle(mScrollDistance);
    }

    private int getAngle(int distance) {
        float currentDistance = distance % FlipView.DISTANCE_PER_POSITION;

        if (currentDistance < 0) {
            currentDistance += FlipView.DISTANCE_PER_POSITION;
        }

        return Math.round((currentDistance / FlipView.DISTANCE_PER_POSITION) * FlipView.DISTANCE_PER_POSITION);
    }

    private int findPositionByScrollDistance(float distance) {
        return Math.round(distance / FlipView.DISTANCE_PER_POSITION);
    }

    public int getCurrentPosition() {
        if (getItemCount() == 0) {
            return RecyclerView.NO_POSITION;
        }

        return mCurrentPosition;
    }

    public int getScrollDistance() {
        return mScrollDistance;
    }

    @Override
    public void onScrollStateChanged(int state) {
        if (mScrollState != RecyclerView.SCROLL_STATE_IDLE && state == RecyclerView.SCROLL_STATE_IDLE) {
            requestLayout();
        }

        mScrollState = state;

        if (!isScrolling()) {
            mScrollVector = 0;
            mPositionBeforeScroll = RecyclerView.NO_POSITION;
        }
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, final RecyclerView.State state, final int position) {
        FlipSmoothScroller smoothScroller = new FlipSmoothScroller(recyclerView) {
            @Nullable
            @Override
            public PointF computeScrollVectorForPosition(int targetPosition) {
                if (position < 0) {
                    throw new IllegalArgumentException("position can't be less then 0. position is : " + position);
                }
                if (position >= state.getItemCount()) {
                    throw new IllegalArgumentException("position can't be great then adapter items count. position is : " + position);
                }

                if (getChildCount() == 0) {
                    return null;
                }

                final int firstChildPos = getPosition(getChildAt(0));
                final int direction = targetPosition < firstChildPos ? -1 : 1;
                if (mOrientation == FlipView.HORIZONTAL) {
                    return new PointF(direction, 0);
                } else {
                    return new PointF(0, direction);
                }
            }
        };

        smoothScroller.setTargetPosition(position);
        startSmoothScroll(smoothScroller);
    }

    @Override
    public void scrollToPosition(int position) {
        if (position < RecyclerView.NO_POSITION || position > getItemCount() - 1) {
            throw new UnsupportedOperationException("Position " + position + " is not valid.");
        }

        mCurrentPosition = position;
        mScrollDistance = position == RecyclerView.NO_POSITION
                ? 0
                : FlipView.DISTANCE_PER_POSITION * position;

        requestLayout();
    }

    void setPositionChangeListener(OnPositionChangeListener onPositionChangeListener) {
        mPositionChangeListener = onPositionChangeListener;
    }

    public boolean isScrolling() {
        return mScrollState != RecyclerView.SCROLL_STATE_IDLE;
    }

    public boolean isInteractiveScroll() {
        return mScrollState == RecyclerView.SCROLL_STATE_DRAGGING;
    }

    public boolean requiresSettling() {
        return getScrollDistance() % FlipView.DISTANCE_PER_POSITION != 0;
    }

    public int getOrientation() {
        return mOrientation;
    }
}
