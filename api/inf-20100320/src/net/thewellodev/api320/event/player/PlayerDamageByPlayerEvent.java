package net.thewellodev.api320.event.player;

import net.thewellodev.api320.Player320;
import net.thewellodev.api.event.Cancellable;

/** Called when a player is about to take damage from another player (the damage can be changed). */
public class PlayerDamageByPlayerEvent extends PlayerEvent implements Cancellable {

    private final Player320 damager;
    private int damage;
    private boolean cancelled;

    public PlayerDamageByPlayerEvent(Player320 victim, Player320 damager, int damage) {
        super(victim);
        this.damager = damager;
        this.damage = damage;
    }

    public Player320 getDamager() {
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
