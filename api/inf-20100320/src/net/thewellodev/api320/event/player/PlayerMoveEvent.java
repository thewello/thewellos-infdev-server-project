package net.thewellodev.api320.event.player;

import net.thewellodev.api320.Player320;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api320.Location320;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location320)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location320 from;
    private Location320 to;

    public PlayerMoveEvent(Player320 player, Location320 from, Location320 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location320 getFrom() {
        return from;
    }

    public Location320 getTo() {
        return to;
    }

    public void setTo(Location320 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
