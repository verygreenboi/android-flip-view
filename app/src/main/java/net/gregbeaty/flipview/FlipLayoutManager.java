package net.gregbeaty.flipview;

import android.graphics.PointF;
import android.support.annotation.Nullable;
import android.support.v7.widget.OrientationHelper;
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
    private long mCurrentId = RecyclerView.NO_ID;
    private int mCurrentPosition = RecyclerView.NO_POSITION;
    private int mScrollDistance;
    private RecyclerView.Adapter mAdapter;
    private OnPositionChangeListener mPositionChangeListener;
    private FlipSmoothScroller mSmoothScroller;

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

        int oldPosition = mCurrentPosition;
        mCurrentPosition = desiredPosition;
        setCurrentId(mCurrentPosition);
        notifyOfPositionChange(oldPosition, mCurrentPosition);

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
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        mAdapter = newAdapter;
        removeAllViews();
    }

    @Override
    public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
        if (mCurrentPosition >= positionStart && mCurrentPosition < positionStart + itemCount && resetPositionByItemId()) {
            return;
        }

        for (int i = positionStart; i < positionStart + itemCount; i++) {
            if (mCurrentPosition == i) {
                setCurrentPosition(mCurrentPosition - 1, false);
                break;
            }
        }
    }

    @Override
    public void onItemsMoved(RecyclerView recyclerView, int from, int to, int itemCount) {
        if (mCurrentPosition == from && resetPositionByItemId()) {
            return;
        }

        for (int i = from; i < from + itemCount; i++) {
            if (mCurrentPosition == i) {
                setCurrentPosition(to + i, false);
                break;
            }
        }
    }

    @Override
    public void onItemsChanged(RecyclerView recyclerView) {
        resetPositionByItemId();
    }

    private boolean resetPositionByItemId() {
        if (mAdapter.hasStableIds() && mCurrentId != RecyclerView.NO_ID) {
            for (int i = 0; i < mAdapter.getItemCount(); i++) {
                if (mAdapter.getItemId(i) == mCurrentId) {
                    setCurrentPosition(i, false);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (state.isPreLayout()) {
            return;
        }

        if (state.getItemCount() == 0) {
            detachAndScrapAttachedViews(recycler);
            setCurrentPosition(RecyclerView.NO_POSITION, false);
            return;
        }

        if (mDecoratedChildWidth == null || mDecoratedChildHeight == null) {
            View scrap = recycler.getViewForPosition(0);
            addView(scrap);
            measureChildWithMargins(scrap, 0, 0);
            mDecoratedChildWidth = getDecoratedMeasuredWidth(scrap);
            mDecoratedChildHeight = getDecoratedMeasuredHeight(scrap);
            detachAndScrapView(scrap, recycler);
        }

        if (mCurrentPosition <= RecyclerView.NO_POSITION) {
            setCurrentPosition(0, false);
        }

        if (mCurrentPosition >= state.getItemCount()) {
            setCurrentPosition(state.getItemCount() - 1, false);
            return;
        }

        detachAndScrapAttachedViews(recycler);
        fill(recycler, state);
    }

    private void fill(RecyclerView.Recycler recycler, RecyclerView.State state) {
        detachAndScrapAttachedViews(recycler);

        boolean layoutOnlyCurrentPosition = !isScrolling() && !requiresSettling();

        if (!layoutOnlyCurrentPosition) {
            addView(mCurrentPosition - 1, recycler, state);
        }

        addView(mCurrentPosition, recycler, state);

        if (!layoutOnlyCurrentPosition) {
            addView(mCurrentPosition + 1, recycler, state);
        }

        recycler.clear();
    }

    private void addView(int position, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (position == RecyclerView.NO_POSITION) {
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

    private void setCurrentPosition(int position, boolean requestLayout) {
        if (position == mCurrentPosition) {
            return;
        }

        int oldPosition = mCurrentPosition;
        mCurrentPosition = position;
        setCurrentId(mCurrentPosition);

        if (mScrollDistance < 0) {
            mScrollDistance = 0;
        } else {
            mScrollDistance = FlipView.DISTANCE_PER_POSITION * position;
        }

        if (requestLayout) {
            removeAllViews();
            requestLayout();
        }

        notifyOfPositionChange(oldPosition, mCurrentPosition);
    }

    private void setCurrentId(int position) {
        if (mAdapter == null || !mAdapter.hasStableIds()) {
            mCurrentId = RecyclerView.NO_ID;
        } else {
            mCurrentId = mAdapter.getItemId(position);
        }
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
        mSmoothScroller = new FlipSmoothScroller(recyclerView) {
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

        mSmoothScroller.setTargetPosition(position);
        startSmoothScroll(mSmoothScroller);
    }

    @Override
    public void scrollToPosition(int position) {
        if (mSmoothScroller != null) {
            mSmoothScroller.stopInternal();
        }

        setCurrentPosition(position, true);
    }

    void setPositionChangeListener(OnPositionChangeListener onPositionChangeListener) {
        mPositionChangeListener = onPositionChangeListener;
    }

    public boolean isScrolling() {
        return getScrollState() != RecyclerView.SCROLL_STATE_IDLE;
    }

    public boolean isInteractiveScroll() {
        return getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING;
    }

    public boolean requiresSettling() {
        return getScrollDistance() % FlipView.DISTANCE_PER_POSITION != 0;
    }

    int getScrollState() {
        return mScrollState;
    }

    private void notifyOfPositionChange(int oldPosition, int newPosition) {
        if (oldPosition != newPosition && mPositionChangeListener != null) {
            mPositionChangeListener.onPositionChange(this, newPosition);
        }
    }

    public int getOrientation() {
        return mOrientation;
    }
}
