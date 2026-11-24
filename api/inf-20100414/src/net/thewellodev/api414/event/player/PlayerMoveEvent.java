package net.thewellodev.api414.event.player;

import net.thewellodev.api414.Player414;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api414.Location414;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location414)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location414 from;
    private Location414 to;

    public PlayerMoveEvent(Player414 player, Location414 from, Location414 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location414 getFrom() {
        return from;
    }

    public Location414 getTo() {
        return to;
    }

    public void setTo(Location414 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
