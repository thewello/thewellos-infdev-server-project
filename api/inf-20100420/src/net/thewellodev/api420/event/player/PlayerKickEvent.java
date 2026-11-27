package net.thewellodev.api420.event.player;

import net.thewellodev.api420.Player420;
import net.thewellodev.api.event.Cancellable;

/** Called when a player is kicked. If cancelled, the player stays. */
public class PlayerKickEvent extends PlayerEvent implements Cancellable {

    private String reason;
    private String leaveMessage;
    private boolean cancelled;

    public PlayerKickEvent(Player420 player, String reason, String leaveMessage) {
        super(player);
        this.reason = reason;
        this.leaveMessage = leaveMessage;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getLeaveMessage() {
        return leaveMessage;
    }

    public void setLeaveMessage(String leaveMessage) {
        this.leaveMessage = leaveMessage;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
