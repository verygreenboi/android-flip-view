package net.gregbeaty.flipview.sample;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import net.gregbeaty.flipview.FlipView;

public class MainActivity extends AppCompatActivity {
    private TextView mPosition;
    private TextView mTotalItems;
    private TextView mDistanceText;
    private TextView mAngleText;
    private FlipView mView;
    private SampleAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mPosition = (TextView) findViewById(R.id.main_position);
        mTotalItems = (TextView) findViewById(R.id.main_total_items);
        mDistanceText = (TextView) findViewById(R.id.main_flip_distance);
        mAngleText = (TextView) findViewById(R.id.main_flip_angle);

        mAdapter = new SampleAdapter();

        for (int i = 0; i < 10; i++) {
            mAdapter.addItem();
        }

        mView = (FlipView) findViewById(R.id.main_flip_view);
        mView.setOrientation(FlipView.VERTICAL);

        mView.addOnPositionChangeListener(new FlipView.OnPositionChangeListener() {
            @Override
            public void onPositionChange(FlipView flipView, int position) {
                refreshDetails();
            }
        });

        mView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                refreshDetails();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    return;
                }

                refreshDetails();
            }
        });

        mView.setAdapter(mAdapter);
    }

    @SuppressLint("RestrictedApi")
    private void refreshDetails() {
        mPosition.setText(String.format("Position: %s", mView.getCurrentPosition()));
        mTotalItems.setText(String.format("Total items: %s", mView.getItemCount()));
        mDistanceText.setText(String.format("Distance: %s", mView.getScrollDistance()));
        mAngleText.setText(String.format("Angle: %s", mView.getAngle()));

        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        MenuItem deleteMenuItem = menu.findItem(R.id.delete_item);
        deleteMenuItem.setVisible(mView.getCurrentPosition() != RecyclerView.NO_POSITION);

        MenuItem scrollToBeginningItem = menu.findItem(R.id.scroll_to_beginning);
        scrollToBeginningItem.setVisible(mView.getCurrentPosition() > 0);

        MenuItem scrollToEndItem = menu.findItem(R.id.scroll_to_end);
        scrollToEndItem.setVisible(mView.getCurrentPosition() < mView.getItemCount() - 1);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add_item:
                mAdapter.addItem();
                return true;
            case R.id.delete_item:
                mAdapter.removeItem(mView.getCurrentPosition());
                return true;
            case R.id.scroll_to_beginning:
                mView.smoothScrollToPosition(0);
                return true;
            case R.id.scroll_to_end:
                mView.smoothScrollToPosition(mView.getItemCount() - 1);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}