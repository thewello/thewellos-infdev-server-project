package net.thewellodev.api321.event.player;

import net.thewellodev.api321.Player321;

/** Called when a player has joined the world. The join message is shown to everybody (null: no message). */
public class PlayerJoinEvent extends PlayerEvent {

    private String joinMessage;

    public PlayerJoinEvent(Player321 player, String joinMessage) {
        super(player);
        this.joinMessage = joinMessage;
    }

    public String getJoinMessage() {
        return joinMessage;
    }

    public void setJoinMessage(String joinMessage) {
        this.joinMessage = joinMessage;
    }
}
