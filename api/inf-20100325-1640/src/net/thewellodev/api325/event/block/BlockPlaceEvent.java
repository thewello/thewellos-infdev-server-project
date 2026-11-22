package net.thewellodev.api325.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api325.Block325;
import net.thewellodev.api325.Player325;

/**
 * Called when a player places a block. {@link #getBlock()} is the position (it still holds the old block);
 * {@link #getNewTypeId()} is the new block. If cancelled, the old block stays.
 */
public class BlockPlaceEvent extends BlockEvent implements Cancellable {

    private final Player325 player;
    private final int newTypeId;
    private boolean cancelled;

    public BlockPlaceEvent(Block325 block, Player325 player, int newTypeId) {
        super(block);
        this.player = player;
        this.newTypeId = newTypeId;
    }

    public Player325 getPlayer() {
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
