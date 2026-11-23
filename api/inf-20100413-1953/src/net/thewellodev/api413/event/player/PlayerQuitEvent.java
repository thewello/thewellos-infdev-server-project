package net.thewellodev.api413.event.player;

import net.thewellodev.api413.Player413;

/** Called when a player leaves. The quit message is shown to everybody (null: no message). */
public class PlayerQuitEvent extends PlayerEvent {

    private String quitMessage;

    public PlayerQuitEvent(Player413 player, String quitMessage) {
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
