package net.thewellodev.server.event;

import net.thewellodev.api.entity.Player;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api.event.Event;

/**
 * Builds 320 and 321: an explosion happened in a player's game. If cancelled, the server does not accept the blocks
 * it destroys (they come back), but the game of the player still shows the explosion.
 */
public class SharedExplosionEvent extends Event implements Cancellable {
    private final Player source;
    private final float power;
    private final double x, y, z;
    private boolean cancelled;

    public SharedExplosionEvent(Player source, float power, double x, double y, double z) {
        this.source = source;
        this.power = power;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Player getSource() {
        return source;
    }

    public float getPower() {
        return power;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
