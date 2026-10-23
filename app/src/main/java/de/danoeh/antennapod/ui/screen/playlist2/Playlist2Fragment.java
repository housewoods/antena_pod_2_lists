package de.danoeh.antennapod.ui.screen.playlist2;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.Playlist2Event;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.event.playback.SpeedChangedEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.ConfirmationDialog;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.EmptyViewHandler;
import de.danoeh.antennapod.ui.common.LiftOnScrollListener;
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListRecyclerView;
import de.danoeh.antennapod.ui.episodeslist.EpisodeMultiSelectActionHandler;
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler;
import de.danoeh.antennapod.ui.screen.SearchFragment;
import de.danoeh.antennapod.ui.view.FloatingSelectMenu;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.List;

public class Playlist2Fragment extends Fragment implements MaterialToolbar.OnMenuItemClickListener,
        EpisodeItemListAdapter.OnSelectModeListener {
    public static final String TAG = "Playlist2Fragment";
    private static final String KEY_UP_ARROW = "up_arrow";
    private static final String SCROLL_POSITION_KEY = "scroll_position";
    private static final String SCROLL_OFFSET_KEY = "scroll_offset";

    private TextView infoBar;
    private EpisodeItemListRecyclerView recyclerView;
    private Playlist2RecyclerAdapter recyclerAdapter;
    private EmptyViewHandler emptyView;
    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private boolean displayUpArrow;

    private List<FeedItem> playlist;

    private Disposable disposable;

    private FloatingSelectMenu floatingSelectMenu;
    private ProgressBar progressBar;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getActivity().getSharedPreferences(TAG, Context.MODE_PRIVATE);
    }

    @Override
    public void onStart() {
        super.onStart();
        loadItems();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        Pair<Integer, Integer> scrollPosition = recyclerView.getScrollPosition();
        prefs.edit().putInt(SCROLL_POSITION_KEY, scrollPosition.first)
                .putInt(SCROLL_OFFSET_KEY, scrollPosition.second).apply();
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(Playlist2Event event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        if (playlist == null) {
            return;
        } else if (recyclerAdapter == null) {
            loadItems();
            return;
        }
        int position;
        switch (event.action) {
            case ADDED:
                loadItems();
                break;
            case SET_PLAYLIST:
                playlist = event.items;
                recyclerAdapter.updateItems(event.items);
                break;
            case REMOVED:
                position = FeedItemEvent.indexOfItemWithId(playlist, event.item.getId());
                if (position >= 0) {
                    playlist.remove(position);
                    recyclerAdapter.notifyItemRemoved(position);
                }
                break;
            case CLEARED:
                playlist.clear();
                recyclerAdapter.updateItems(playlist);
                break;
            case MOVED:
                position = FeedItemEvent.indexOfItemWithId(playlist, event.item.getId());
                if (position >= 0) {
                    playlist.add(event.position, playlist.remove(position));
                    recyclerAdapter.notifyItemMoved(position, event.position);
                }
                break;
            default:
                return;
        }
        refreshInfoBar();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        if (playlist == null) {
            return;
        } else if (recyclerAdapter == null) {
            loadItems();
            return;
        }
        for (int i = 0, size = event.items.size(); i < size; i++) {
            FeedItem item = event.items.get(i);
            int pos = FeedItemEvent.indexOfItemWithId(playlist, item.getId());
            if (pos >= 0) {
                playlist.remove(pos);
                playlist.add(pos, item);
                recyclerAdapter.notifyItemChangedCompat(pos);
                refreshInfoBar();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (recyclerAdapter != null) {
            for (int i = 0; i < recyclerAdapter.getItemCount(); i++) {
                EpisodeItemViewHolder holder = (EpisodeItemViewHolder)
                        recyclerView.findViewHolderForAdapterPosition(i);
                if (holder != null && holder.isPlayingItem()) {
                    holder.notifyPlaybackPositionUpdated(event);
                    break;
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void updateSpeed(SpeedChangedEvent event) {
        refreshInfoBar();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (recyclerAdapter != null) {
            recyclerAdapter.endSelectMode();
        }
        recyclerAdapter = null;
        if (toolbar != null) {
            toolbar.setOnMenuItemClickListener(null);
            toolbar.setOnLongClickListener(null);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.refresh_item) {
            FeedUpdateManager.getInstance().runOnceOrAsk(requireContext());
            return true;
        } else if (itemId == R.id.clear_playlist2) {
            ConfirmationDialog conDialog = new ConfirmationDialog(getActivity(),
                    R.string.clear_playlist2_label,
                    R.string.clear_playlist2_confirmation_msg) {

                @Override
                public void onConfirmButtonPressed(DialogInterface dialog) {
                    dialog.dismiss();
                    DBWriter.clearPlaylist2();
                }
            };
            conDialog.createNewDialog().show();
            return true;
        } else if (itemId == R.id.action_search) {
            ((MainActivity) getActivity()).loadChildFragment(
                    SearchFragment.newInstance((String) null));
            return true;
        }
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Log.d(TAG, "onContextItemSelected() called with: " + "item = [" + item + "]");
        if (!isVisible() || recyclerAdapter == null) {
            return false;
        }
        FeedItem selectedItem = recyclerAdapter.getLongPressedItem();
        if (selectedItem == null) {
            Log.i(TAG, "Selected item was null, ignoring selection");
            return super.onContextItemSelected(item);
        }

        int position = FeedItemEvent.indexOfItemWithId(playlist, selectedItem.getId());
        if (position < 0) {
            Log.i(TAG, "Selected item no longer exist, ignoring selection");
            return super.onContextItemSelected(item);
        }
        if (recyclerAdapter.onContextItemSelected(item)) {
            return true;
        }

        final int itemId = item.getItemId();
        if (!recyclerAdapter.inActionMode()) {
            if (itemId == R.id.move_to_top_item) {
                playlist.add(0, playlist.remove(position));
                recyclerAdapter.notifyItemMoved(position, 0);
                DBWriter.movePlaylist2ItemsToTop(Collections.singletonList(selectedItem));
                return true;
            } else if (itemId == R.id.move_to_bottom_item) {
                playlist.add(playlist.remove(position));
                recyclerAdapter.notifyItemMoved(position, playlist.size() - 1);
                DBWriter.movePlaylist2ItemsToBottom(Collections.singletonList(selectedItem));
                return true;
            }
        }
        return FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedItem);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.queue_fragment, container, false);
        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this);
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, displayUpArrow);
        toolbar.inflateMenu(R.menu.playlist2);
        progressBar = root.findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        infoBar = root.findViewById(R.id.info_bar);
        boolean largePadding = displayUpArrow;
        int paddingHorizontal = (int) (getResources().getDisplayMetrics().density * (largePadding ? 60 : 16));
        infoBar.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);

        recyclerView = root.findViewById(R.id.recyclerView);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        registerForContextMenu(recyclerView);
        recyclerView.addOnScrollListener(new LiftOnScrollListener(root.findViewById(R.id.appbar)));

        Playlist2SwipeActions swipeActions = new Playlist2SwipeActions();
        swipeActions.attachTo(recyclerView);

        recyclerAdapter = new Playlist2RecyclerAdapter((MainActivity) getActivity(), swipeActions) {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                super.onCreateContextMenu(menu, v, menuInfo);
                de.danoeh.antennapod.ui.MenuItemUtils.setOnClickListeners(menu,
                        Playlist2Fragment.this::onContextItemSelected);
            }

            @Override
            protected void onSelectedItemsUpdated() {
                super.onSelectedItemsUpdated();
                Menu menu = floatingSelectMenu.getMenu();
                List<FeedItem> selectedItems = getSelectedItems();
                FeedItemMenuHandler.onPrepareMenu(floatingSelectMenu.getMenu(), getSelectedItems(),
                        R.id.add_to_playlist2_item, R.id.remove_inbox_item);

                menu.findItem(R.id.move_to_top_item).setVisible(!selectedItems.isEmpty());
                menu.findItem(R.id.move_to_bottom_item).setVisible(!selectedItems.isEmpty());

                floatingSelectMenu.updateItemVisibility();
            }
        };
        recyclerAdapter.setOnSelectModeListener(this);
        recyclerView.setAdapter(recyclerAdapter);

        swipeRefreshLayout = root.findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance));
        swipeRefreshLayout.setOnRefreshListener(() -> FeedUpdateManager.getInstance().runOnceOrAsk(requireContext()));

        emptyView = new EmptyViewHandler(getContext());
        emptyView.attachToRecyclerView(recyclerView);
        emptyView.setIcon(R.drawable.ic_playlist_play);
        emptyView.setTitle(R.string.no_items_header_label);
        emptyView.setMessage(R.string.no_items_label);
        emptyView.updateAdapter(recyclerAdapter);

        floatingSelectMenu = root.findViewById(R.id.floatingSelectMenu);
        floatingSelectMenu.inflate(R.menu.episodes_apply_action_speeddial);
        floatingSelectMenu.setOnMenuItemClickListener(menuItem -> {
            if (recyclerAdapter.getSelectedCount() == 0) {
                EventBus.getDefault().post(new MessageEvent(getString(R.string.no_items_selected_message)));
                return false;
            }
            new EpisodeMultiSelectActionHandler(getActivity(), menuItem.getItemId())
                    .handleAction(recyclerAdapter.getSelectedItems());
            recyclerAdapter.endSelectMode();
            return true;
        });
        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    private void refreshInfoBar() {
        long timeLeft = 0;
        for (FeedItem item : playlist) {
            float playbackSpeed = 1;
            if (UserPreferences.timeRespectsSpeed()) {
                playbackSpeed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(item.getMedia());
            }
            if (item.getMedia() != null) {
                long itemTimeLeft = item.getMedia().getDuration() - item.getMedia().getPosition();
                timeLeft += (long) (itemTimeLeft / playbackSpeed);
            }
        }
        String episodes = getResources().getQuantityString(R.plurals.num_episodes, playlist.size(), playlist.size());
        String time = Converter.getDurationStringLocalized(getResources(), timeLeft, false);
        infoBar.setText(getString(R.string.queue_time_left_label, episodes, time));

        if (recyclerAdapter.inActionMode()) {
            infoBar.setVisibility(View.INVISIBLE);
        } else {
            infoBar.setVisibility(View.VISIBLE);
        }
    }

    private void loadItems() {
        Log.d(TAG, "loadItems()");
        if (disposable != null) {
            disposable.dispose();
        }
        if (playlist == null) {
            emptyView.hide();
        }
        disposable = Observable.fromCallable(DBReader::getPlaylist2)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    final boolean restoreScrollPosition = playlist == null || playlist.isEmpty();
                    playlist = items;
                    progressBar.setVisibility(View.GONE);
                    recyclerAdapter.setDummyViews(0);
                    recyclerAdapter.updateItems(playlist);
                    if (restoreScrollPosition) {
                        Pair<Integer, Integer> scrollPosition = new Pair<>(
                                prefs.getInt(SCROLL_POSITION_KEY, 0), prefs.getInt(SCROLL_OFFSET_KEY, 0));
                        recyclerView.restoreScrollPosition(scrollPosition);
                    }
                    refreshInfoBar();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    @Override
    public void onStartSelectMode() {
        floatingSelectMenu.setVisibility(View.VISIBLE);
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                (int) getResources().getDimension(R.dimen.floating_select_menu_height));
        refreshInfoBar();
    }

    @Override
    public void onEndSelectMode() {
        floatingSelectMenu.setVisibility(View.GONE);
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(), 0);
        infoBar.setVisibility(View.VISIBLE);
        refreshInfoBar();
    }

    private class Playlist2SwipeActions extends de.danoeh.antennapod.ui.swipeactions.SwipeActions {

        int dragFrom = -1;
        int dragTo = -1;

        public Playlist2SwipeActions() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, Playlist2Fragment.this, TAG);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int fromPosition = viewHolder.getBindingAdapterPosition();
            int toPosition = target.getBindingAdapterPosition();

            if (dragFrom == -1) {
                dragFrom = fromPosition;
            }
            dragTo = toPosition;

            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            Log.d(TAG, "move(" + from + ", " + to + ") in memory");
            if (playlist == null || from >= playlist.size() || to >= playlist.size() || from < 0 || to < 0) {
                return false;
            }
            playlist.add(to, playlist.remove(from));
            recyclerAdapter.notifyItemMoved(from, to);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            if (disposable != null) {
                disposable.dispose();
            }
            super.onSwiped(viewHolder, direction);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                reallyMoved(dragFrom, dragTo);
            }

            dragFrom = dragTo = -1;
        }

        private void reallyMoved(int from, int to) {
            Log.d(TAG, "Write to database move(" + from + ", " + to + ")");
            DBWriter.movePlaylist2Item(from, to);
        }
    }
}
