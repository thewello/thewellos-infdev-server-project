package net.thewellodev.api414.event.player;

import net.thewellodev.api414.Player414;

/** Called when a player dies. The death message is shown to everybody (null: no message). */
public class PlayerDeathEvent extends PlayerEvent {

    private final Player414 killer;
    private String deathMessage;

    public PlayerDeathEvent(Player414 player, Player414 killer, String deathMessage) {
        super(player);
        this.killer = killer;
        this.deathMessage = deathMessage;
    }

    /** The player who killed this player a moment ago, or null. */
    public Player414 getKiller() {
        return killer;
    }

    public String getDeathMessage() {
        return deathMessage;
    }

    public void setDeathMessage(String deathMessage) {
        this.deathMessage = deathMessage;
    }
}
