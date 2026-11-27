package net.thewellodev.api607.event.player;

import net.thewellodev.api607.Player607;
import net.thewellodev.api.event.Cancellable;

/** Called when a player is about to take damage from another player (the damage can be changed). */
public class PlayerDamageByPlayerEvent extends PlayerEvent implements Cancellable {

    private final Player607 damager;
    private int damage;
    private boolean cancelled;

    public PlayerDamageByPlayerEvent(Player607 victim, Player607 damager, int damage) {
        super(victim);
        this.damager = damager;
        this.damage = damage;
    }

    public Player607 getDamager() {
        return damager;
    }

    public int getDamage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
