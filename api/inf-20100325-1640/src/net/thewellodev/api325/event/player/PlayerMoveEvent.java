package net.thewellodev.api325.event.player;

import net.thewellodev.api325.Player325;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api325.Location325;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location325)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location325 from;
    private Location325 to;

    public PlayerMoveEvent(Player325 player, Location325 from, Location325 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location325 getFrom() {
        return from;
    }

    public Location325 getTo() {
        return to;
    }

    public void setTo(Location325 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
