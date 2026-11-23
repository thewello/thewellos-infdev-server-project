package net.thewellodev.api413.event.player;

import net.thewellodev.api413.Player413;

/** Called when a player dies. The death message is shown to everybody (null: no message). */
public class PlayerDeathEvent extends PlayerEvent {

    private final Player413 killer;
    private String deathMessage;

    public PlayerDeathEvent(Player413 player, Player413 killer, String deathMessage) {
        super(player);
        this.killer = killer;
        this.deathMessage = deathMessage;
    }

    /** The player who killed this player a moment ago, or null. */
    public Player413 getKiller() {
        return killer;
    }

    public String getDeathMessage() {
        return deathMessage;
    }

    public void setDeathMessage(String deathMessage) {
        this.deathMessage = deathMessage;
    }
}
