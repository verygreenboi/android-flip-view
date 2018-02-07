package net.gregbeaty.flipview;

import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.View;

abstract class FlipSmoothScroller extends LinearSmoothScroller {
    private RecyclerView mRecyclerView;

    FlipSmoothScroller(RecyclerView recyclerView) {
        super(recyclerView.getContext());

        mRecyclerView = recyclerView;
    }

    void stopInternal() {
        if (isRunning()) {
            mRecyclerView.stopScroll();
        }
    }

    @Override
    public int calculateDxToMakeVisible(View view, int snapPreference) {
        final FlipLayoutManager layoutManager = (FlipLayoutManager) getLayoutManager();
        if (layoutManager == null || !layoutManager.canScrollHorizontally()) {
            return 0;
        }

        return calculateDeltaToMakeVisible(layoutManager, view);
    }

    @Override
    public int calculateDyToMakeVisible(View view, int snapPreference) {
        final FlipLayoutManager layoutManager = (FlipLayoutManager) getLayoutManager();
        if (layoutManager == null || !layoutManager.canScrollVertically()) {
            return 0;
        }

        return calculateDeltaToMakeVisible(layoutManager, view);
    }

    private int calculateDeltaToMakeVisible(FlipLayoutManager layoutManager, View view) {
        int scrollDistance = layoutManager.getScrollDistance();
        int distanceForPage = layoutManager.getPosition(view) * FlipLayoutManager.DISTANCE_PER_POSITION;
        return scrollDistance - distanceForPage;
    }

    @Override
    protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
        return 200f / displayMetrics.densityDpi;
    }

    @Override
    protected int calculateTimeForScrolling(int dx) {
        return super.calculateTimeForScrolling(dx);
    }
}
