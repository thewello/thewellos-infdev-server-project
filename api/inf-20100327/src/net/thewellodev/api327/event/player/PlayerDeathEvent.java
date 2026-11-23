package net.thewellodev.api327.event.player;

import net.thewellodev.api327.Player327;

/** Called when a player dies. The death message is shown to everybody (null: no message). */
public class PlayerDeathEvent extends PlayerEvent {

    private final Player327 killer;
    private String deathMessage;

    public PlayerDeathEvent(Player327 player, Player327 killer, String deathMessage) {
        super(player);
        this.killer = killer;
        this.deathMessage = deathMessage;
    }

    /** The player who killed this player a moment ago, or null. */
    public Player327 getKiller() {
        return killer;
    }

    public String getDeathMessage() {
        return deathMessage;
    }

    public void setDeathMessage(String deathMessage) {
        this.deathMessage = deathMessage;
    }
}
