package de.danoeh.antennapod.event;

import androidx.annotation.Nullable;

import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;

public class Playlist2Event {

    public enum Action {
        ADDED, SET_PLAYLIST, REMOVED, CLEARED, MOVED
    }

    public final Action action;
    public final FeedItem item;
    public final int position;
    public final List<FeedItem> items;

    private Playlist2Event(Action action,
                           @Nullable FeedItem item,
                           @Nullable List<FeedItem> items,
                           int position) {
        this.action = action;
        this.item = item;
        this.items = items;
        this.position = position;
    }

    public static Playlist2Event added(FeedItem item, int position) {
        return new Playlist2Event(Action.ADDED, item, null, position);
    }

    public static Playlist2Event setPlaylist(List<FeedItem> playlist) {
        return new Playlist2Event(Action.SET_PLAYLIST, null, playlist, -1);
    }

    public static Playlist2Event removed(FeedItem item) {
        return new Playlist2Event(Action.REMOVED, item, null, -1);
    }

    public static Playlist2Event cleared() {
        return new Playlist2Event(Action.CLEARED, null, null, -1);
    }

    public static Playlist2Event moved(FeedItem item, int newPosition) {
        return new Playlist2Event(Action.MOVED, item, null, newPosition);
    }
}
