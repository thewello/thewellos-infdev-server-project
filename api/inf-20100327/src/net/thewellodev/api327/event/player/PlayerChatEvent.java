package net.thewellodev.api327.event.player;

import net.thewellodev.api327.Player327;
import net.thewellodev.api.event.Cancellable;

/**
 * Called when a player sends a chat message (not commands).
 * In the format, %1$s is the display name and %2$s is the message.
 */
public class PlayerChatEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private String message;
    private String format = "<%1$s> %2$s";

    public PlayerChatEvent(Player327 player, String message) {
        super(player);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
