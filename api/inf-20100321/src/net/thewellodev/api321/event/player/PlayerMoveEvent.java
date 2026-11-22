package net.thewellodev.api321.event.player;

import net.thewellodev.api321.Player321;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api321.Location321;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location321)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location321 from;
    private Location321 to;

    public PlayerMoveEvent(Player321 player, Location321 from, Location321 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location321 getFrom() {
        return from;
    }

    public Location321 getTo() {
        return to;
    }

    public void setTo(Location321 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
