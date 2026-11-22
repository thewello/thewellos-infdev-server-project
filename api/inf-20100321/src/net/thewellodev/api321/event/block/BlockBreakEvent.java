package net.thewellodev.api321.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api321.Block321;
import net.thewellodev.api321.Player321;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player321 player;
    private boolean cancelled;

    public BlockBreakEvent(Block321 block, Player321 player) {
        super(block);
        this.player = player;
    }

    public Player321 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
