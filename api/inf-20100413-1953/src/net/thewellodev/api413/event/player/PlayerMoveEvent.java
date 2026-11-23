package net.thewellodev.api413.event.player;

import net.thewellodev.api413.Player413;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api413.Location413;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location413)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location413 from;
    private Location413 to;

    public PlayerMoveEvent(Player413 player, Location413 from, Location413 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location413 getFrom() {
        return from;
    }

    public Location413 getTo() {
        return to;
    }

    public void setTo(Location413 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
