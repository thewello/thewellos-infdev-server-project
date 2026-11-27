package net.thewellodev.api607.event.player;

import net.thewellodev.api607.Player607;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api607.Location607;

/**
 * Called when a player moves (about 20 times a second while moving). If cancelled, the player is put back at
 * {@link #getFrom()}. {@link #setTo(Location607)} moves the player somewhere else.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private final Location607 from;
    private Location607 to;

    public PlayerMoveEvent(Player607 player, Location607 from, Location607 to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location607 getFrom() {
        return from;
    }

    public Location607 getTo() {
        return to;
    }

    public void setTo(Location607 to) {
        this.to = to;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
