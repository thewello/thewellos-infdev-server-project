package net.thewellodev.api327.event.world;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api.event.Event;
import net.thewellodev.api327.Location327;
import net.thewellodev.api327.Player327;

/**
 * Called when a mob that an arrow made appeared in a player's game. If cancelled, the mob is removed at once.
 */
public class MobSpawnEvent extends Event implements Cancellable {
    public static final int ZOMBIE = 0;
    public static final int SKELETON = 1;
    public static final int CREEPER = 2;
    public static final int SPIDER = 3;
    public static final int PIG = 4;
    public static final int SHEEP = 5;
    /** A primed TNT (a block of TNT that was lit): cancelling the event removes it, so the TNT never explodes. */
    public static final int PRIMED_TNT = 6;
    /** An arrow in flight (of a player or of a skeleton): cancelling the event removes it. */
    public static final int ARROW = 7;

    private final Player327 owner;
    private final int kind;
    private final Location327 location;
    private boolean cancelled;

    public MobSpawnEvent(Player327 owner, int kind, Location327 location) {
        this.owner = owner;
        this.kind = kind;
        this.location = location;
    }

    /** The player whose arrow made the mob. */
    public Player327 getOwner() {
        return owner;
    }

    /** {@link #ZOMBIE}, {@link #SKELETON}, {@link #CREEPER}, {@link #SPIDER}, {@link #PIG}, {@link #SHEEP}, {@link #PRIMED_TNT} or {@link #ARROW}. */
    public int getKind() {
        return kind;
    }

    public Location327 getLocation() {
        return location;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
