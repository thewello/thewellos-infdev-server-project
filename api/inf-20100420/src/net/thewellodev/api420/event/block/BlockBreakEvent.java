package net.thewellodev.api420.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api420.Block420;
import net.thewellodev.api420.Player420;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player420 player;
    private boolean cancelled;

    public BlockBreakEvent(Block420 block, Player420 player) {
        super(block);
        this.player = player;
    }

    public Player420 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
