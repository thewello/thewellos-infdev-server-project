package net.thewellodev.api330.event.player;

import net.thewellodev.api330.Player330;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api330.Location330;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location330)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location330 from;
    private Location330 to;

    public PlayerMoveEvent(Player330 player, Location330 from, Location330 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location330 getFrom() {
        return from;
    }

    public Location330 getTo() {
        return to;
    }

    public void setTo(Location330 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
