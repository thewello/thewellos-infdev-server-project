package net.thewellodev.api420.event.player;

import net.thewellodev.api420.Player420;
import net.thewellodev.api.event.Cancellable;

/**
 * Called when a player sends a chat message (not commands).
 * In the format, %1$s is the display name and %2$s is the message.
 */
public class PlayerChatEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private String message;
    private String format = "<%1$s> %2$s";

    public PlayerChatEvent(Player420 player, String message) {
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
