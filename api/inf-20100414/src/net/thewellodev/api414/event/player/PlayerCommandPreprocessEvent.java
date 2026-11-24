package net.thewellodev.api414.event.player;

import net.thewellodev.api414.Player414;
import net.thewellodev.api.event.Cancellable;

/** Called when a player types a command (before it runs). The message starts with "/". */
public class PlayerCommandPreprocessEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private String message;

    public PlayerCommandPreprocessEvent(Player414 player, String message) {
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
