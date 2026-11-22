package net.thewellodev.server.event;

import net.thewellodev.api.entity.Player;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api.event.Event;

/** Builds 316, 320 and 321: a mob that an arrow made appeared in a player's game. If cancelled, the mob is removed. */
public class SharedMobSpawnEvent extends Event implements Cancellable {
    private final Player owner;
    private final int kind;
    private final double x, y, z;
    private boolean cancelled;

    public SharedMobSpawnEvent(Player owner, int kind, double x, double y, double z) {
        this.owner = owner;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Player getOwner() {
        return owner;
    }

    public int getKind() {
        return kind;
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
