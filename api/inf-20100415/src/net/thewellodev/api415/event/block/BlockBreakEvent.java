package net.thewellodev.api415.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api415.Block415;
import net.thewellodev.api415.Player415;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player415 player;
    private boolean cancelled;

    public BlockBreakEvent(Block415 block, Player415 player) {
        super(block);
        this.player = player;
    }

    public Player415 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
