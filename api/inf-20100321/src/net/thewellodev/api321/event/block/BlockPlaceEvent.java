package net.thewellodev.api321.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api321.Block321;
import net.thewellodev.api321.Player321;

/**
 * Called when a player places a block. {@link #getBlock()} is the position (it still holds the old block);
 * {@link #getNewTypeId()} is the new block. If cancelled, the old block stays.
 */
public class BlockPlaceEvent extends BlockEvent implements Cancellable {

    private final Player321 player;
    private final int newTypeId;
    private boolean cancelled;

    public BlockPlaceEvent(Block321 block, Player321 player, int newTypeId) {
        super(block);
        this.player = player;
        this.newTypeId = newTypeId;
    }

    public Player321 getPlayer() {
        return player;
    }

    public int getNewTypeId() {
        return newTypeId;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
