package net.thewellodev.api608.event.player;

import net.thewellodev.api608.Player608;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api608.Location608;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location608)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location608 from;
    private Location608 to;

    public PlayerMoveEvent(Player608 player, Location608 from, Location608 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location608 getFrom() {
        return from;
    }

    public Location608 getTo() {
        return to;
    }

    public void setTo(Location608 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
