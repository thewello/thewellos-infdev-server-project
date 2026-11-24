package net.thewellodev.api415.event.player;

import net.thewellodev.api415.Player415;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api415.Location415;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location415)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location415 from;
    private Location415 to;

    public PlayerMoveEvent(Player415 player, Location415 from, Location415 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location415 getFrom() {
        return from;
    }

    public Location415 getTo() {
        return to;
    }

    public void setTo(Location415 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
