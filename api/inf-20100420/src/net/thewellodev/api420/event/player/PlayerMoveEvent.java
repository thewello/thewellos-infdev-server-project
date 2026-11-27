package net.thewellodev.api420.event.player;

import net.thewellodev.api420.Player420;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api420.Location420;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location420)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location420 from;
    private Location420 to;

    public PlayerMoveEvent(Player420 player, Location420 from, Location420 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location420 getFrom() {
        return from;
    }

    public Location420 getTo() {
        return to;
    }

    public void setTo(Location420 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
