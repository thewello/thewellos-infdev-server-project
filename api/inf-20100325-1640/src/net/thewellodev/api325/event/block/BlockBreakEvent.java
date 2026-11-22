package net.thewellodev.api325.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api325.Block325;
import net.thewellodev.api325.Player325;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player325 player;
    private boolean cancelled;

    public BlockBreakEvent(Block325 block, Player325 player) {
        super(block);
        this.player = player;
    }

    public Player325 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
