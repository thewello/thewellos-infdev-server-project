package net.thewellodev.api608.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api608.Block608;
import net.thewellodev.api608.Player608;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player608 player;
    private boolean cancelled;

    public BlockBreakEvent(Block608 block, Player608 player) {
        super(block);
        this.player = player;
    }

    public Player608 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
