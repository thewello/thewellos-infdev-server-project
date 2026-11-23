package net.thewellodev.api413.event.player;

import net.thewellodev.api413.Player413;
import net.thewellodev.api.event.Cancellable;

/**
 * Called when a player hits another player (before damage is applied). If cancelled, the hit is ignored. The
 * distance is measured from the players' server-side positions.
 */
public class PlayerAttackEvent extends PlayerEvent implements Cancellable {

    private final Player413 target;
    private final double distanceSq;
    private final int damage;
    private final int heldItem;
    private boolean cancelled;

    public PlayerAttackEvent(Player413 attacker, Player413 target, double distanceSq, int damage, int heldItem) {
        super(attacker);
        this.target = target;
        this.distanceSq = distanceSq;
        this.damage = damage;
        this.heldItem = heldItem;
    }

    /** The attacked player. */
    public Player413 getTarget() {
        return target;
    }

    /** Squared distance between attacker and target, in blocks. */
    public double getDistanceSq() {
        return distanceSq;
    }

    /** Damage reported by the client (the server clamps it to 1-10). */
    public int getDamage() {
        return damage;
    }

    /** Id of the held item as the client shows it, or -1 if empty. */
    public int getHeldItem() {
        return heldItem;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
