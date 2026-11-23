package net.thewellodev.api327.event.player;

import net.thewellodev.api327.Player327;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api327.Location327;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location327)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location327 from;
    private Location327 to;

    public PlayerMoveEvent(Player327 player, Location327 from, Location327 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location327 getFrom() {
        return from;
    }

    public Location327 getTo() {
        return to;
    }

    public void setTo(Location327 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
