package net.thewellodev.api414.event.player;

import net.thewellodev.api414.Player414;
import net.thewellodev.api.event.Cancellable;

/** Called when a player is about to take damage from another player (the damage can be changed). */
public class PlayerDamageByPlayerEvent extends PlayerEvent implements Cancellable {

    private final Player414 damager;
    private int damage;
    private boolean cancelled;

    public PlayerDamageByPlayerEvent(Player414 victim, Player414 damager, int damage) {
        super(victim);
        this.damager = damager;
        this.damage = damage;
    }

    public Player414 getDamager() {
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
