package net.thewellodev.api415.event.world;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api.event.Event;
import net.thewellodev.api415.Location415;
import net.thewellodev.api415.Player415;

/**
 * Called when an explosion (a creeper, TNT) happened in a player's game. If cancelled, the server does not accept the
 * blocks the explosion destroys, so they come back; the game of that player still shows the explosion.
 */
public class ExplosionEvent extends Event implements Cancellable {
    private final Player415 source;
    private final Location415 location;
    private final float power;
    private boolean cancelled;

    public ExplosionEvent(Player415 source, Location415 location, float power) {
        this.source = source;
        this.location = location;
        this.power = power;
    }

    /** The player in whose game the explosion happened. */
    public Player415 getSource() {
        return source;
    }

    public Location415 getLocation() {
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
