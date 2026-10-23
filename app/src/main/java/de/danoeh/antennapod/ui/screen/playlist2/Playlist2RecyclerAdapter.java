package de.danoeh.antennapod.ui.screen.playlist2;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter;
import de.danoeh.antennapod.ui.swipeactions.SwipeActions;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder;

/**
 * List adapter for the second playlist.
 */
public class Playlist2RecyclerAdapter extends EpisodeItemListAdapter {
    private static final String TAG = "Playlist2RecyclerAdapter";

    private final SwipeActions swipeActions;

    public Playlist2RecyclerAdapter(FragmentActivity mainActivity, SwipeActions swipeActions) {
        super(mainActivity);
        this.swipeActions = swipeActions;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void afterBindViewHolder(EpisodeItemViewHolder holder, int pos) {
        holder.dragHandle.setVisibility(View.VISIBLE);
        holder.dragHandle.setOnTouchListener((v1, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                Log.d(TAG, "startDrag()");
                swipeActions.startDrag(holder);
            }
            return false;
        });
        holder.coverHolder.setOnTouchListener((v1, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                boolean isLtr = holder.itemView.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;
                float factor = isLtr ? 1 : -1;
                if (factor * event.getX() < factor * 0.5 * v1.getWidth()) {
                    Log.d(TAG, "startDrag()");
                    swipeActions.startDrag(holder);
                } else {
                    Log.d(TAG, "Ignoring drag in right half of the image");
                }
            }
            return false;
        });
        if (inActionMode()) {
            holder.dragHandle.setOnTouchListener(null);
            holder.coverHolder.setOnTouchListener(null);
        }

        holder.isInQueue.setVisibility(View.GONE);
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.playlist2_context, menu);
        super.onCreateContextMenu(menu, v, menuInfo);

        if (!inActionMode()) {
            menu.findItem(R.id.multi_select).setVisible(true);
            if (getItem(0).getId() == getLongPressedItem().getId()) {
                menu.findItem(R.id.move_to_top_item).setVisible(false);
            }
            if (getItem(getItemCount() - 1).getId() == getLongPressedItem().getId()) {
                menu.findItem(R.id.move_to_bottom_item).setVisible(false);
            }
        } else {
            menu.findItem(R.id.move_to_top_item).setVisible(false);
            menu.findItem(R.id.move_to_bottom_item).setVisible(false);
        }
    }
}
