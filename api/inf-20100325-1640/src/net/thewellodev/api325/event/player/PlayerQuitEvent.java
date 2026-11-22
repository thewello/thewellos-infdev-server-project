package net.thewellodev.api325.event.player;

import net.thewellodev.api325.Player325;

/** Called when a player leaves. The quit message is shown to everybody (null: no message). */
public class PlayerQuitEvent extends PlayerEvent {

    private String quitMessage;

    public PlayerQuitEvent(Player325 player, String quitMessage) {
        super(player);
        this.quitMessage = quitMessage;
    }

    public String getQuitMessage() {
        return quitMessage;
    }

    public void setQuitMessage(String quitMessage) {
        this.quitMessage = quitMessage;
    }
}
