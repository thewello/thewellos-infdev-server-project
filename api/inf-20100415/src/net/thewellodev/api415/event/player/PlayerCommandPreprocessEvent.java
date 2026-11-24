package net.thewellodev.api415.event.player;

import net.thewellodev.api415.Player415;
import net.thewellodev.api.event.Cancellable;

/** Called when a player types a command (before it runs). The message starts with "/". */
public class PlayerCommandPreprocessEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private String message;

    public PlayerCommandPreprocessEvent(Player415 player, String message) {
        super(player);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
