package net.thewellodev.api413.event.world;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api.event.Event;
import net.thewellodev.api413.Location413;
import net.thewellodev.api413.Player413;

/**
 * Called when an explosion (a creeper, TNT) happened in a player's game. If cancelled, the server does not accept the
 * blocks the explosion destroys, so they come back; the game of that player still shows the explosion.
 */
public class ExplosionEvent extends Event implements Cancellable {
    private final Player413 source;
    private final Location413 location;
    private final float power;
    private boolean cancelled;

    public ExplosionEvent(Player413 source, Location413 location, float power) {
        this.source = source;
        this.location = location;
        this.power = power;
    }

    /** The player in whose game the explosion happened. */
    public Player413 getSource() {
        return source;
    }

    public Location413 getLocation() {
        return location;
    }

    /** The power of the explosion (a creeper has 3, TNT 4). */
    public float getPower() {
        return power;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
