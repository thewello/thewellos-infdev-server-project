package net.thewellodev.api413.event.player;

import net.thewellodev.api413.Player413;

/**
 * Called while a player logs in (before the world is sent).
 * Use {@link #disallow(String)} to refuse the login.
 */
public class PlayerLoginEvent extends PlayerEvent {

    private boolean allowed = true;
    private String kickMessage = "";
    private final String address;

    public PlayerLoginEvent(Player413 player, String address) {
        super(player);
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public void disallow(String message) {
        allowed = false;
        kickMessage = message;
    }

    public void allow() {
        allowed = true;
        kickMessage = "";
    }
}
