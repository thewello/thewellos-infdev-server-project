package net.thewellodev.api330.event.player;

import net.thewellodev.api330.Player330;

/** Called when a player dies. The death message is shown to everybody (null: no message). */
public class PlayerDeathEvent extends PlayerEvent {

    private final Player330 killer;
    private String deathMessage;

    public PlayerDeathEvent(Player330 player, Player330 killer, String deathMessage) {
        super(player);
        this.killer = killer;
        this.deathMessage = deathMessage;
    }

    /** The player who killed this player a moment ago, or null. */
    public Player330 getKiller() {
        return killer;
    }

    public String getDeathMessage() {
        return deathMessage;
    }

    public void setDeathMessage(String deathMessage) {
        this.deathMessage = deathMessage;
    }
}
