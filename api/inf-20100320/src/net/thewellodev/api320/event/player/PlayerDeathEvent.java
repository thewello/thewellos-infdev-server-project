package net.thewellodev.api320.event.player;

import net.thewellodev.api320.Player320;

/** Called when a player dies. The death message is shown to everybody (null: no message). */
public class PlayerDeathEvent extends PlayerEvent {

    private final Player320 killer;
    private String deathMessage;

    public PlayerDeathEvent(Player320 player, Player320 killer, String deathMessage) {
        super(player);
        this.killer = killer;
        this.deathMessage = deathMessage;
    }

    /** The player who killed this player a moment ago, or null. */
    public Player320 getKiller() {
        return killer;
    }

    public String getDeathMessage() {
        return deathMessage;
    }

    public void setDeathMessage(String deathMessage) {
        this.deathMessage = deathMessage;
    }
}
