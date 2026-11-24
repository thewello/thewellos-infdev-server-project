package net.thewellodev.api414.event.world;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api.event.Event;
import net.thewellodev.api414.Location414;
import net.thewellodev.api414.Player414;

/**
 * Called when an explosion (a creeper, TNT) happened in a player's game. If cancelled, the server does not accept the
 * blocks the explosion destroys, so they come back; the game of that player still shows the explosion.
 */
public class ExplosionEvent extends Event implements Cancellable {
    private final Player414 source;
    private final Location414 location;
    private final float power;
    private boolean cancelled;

    public ExplosionEvent(Player414 source, Location414 location, float power) {
        this.source = source;
        this.location = location;
        this.power = power;
    }

    /** The player in whose game the explosion happened. */
    public Player414 getSource() {
        return source;
    }

    public Location414 getLocation() {
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
