package net.thewellodev.api330.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api330.Block330;
import net.thewellodev.api330.Player330;

/**
 * Called when a player places a block. {@link #getBlock()} is the position (it still holds the old block);
 * {@link #getNewTypeId()} is the new block. If cancelled, the old block stays.
 */
public class BlockPlaceEvent extends BlockEvent implements Cancellable {

    private final Player330 player;
    private final int newTypeId;
    private boolean cancelled;

    public BlockPlaceEvent(Block330 block, Player330 player, int newTypeId) {
        super(block);
        this.player = player;
        this.newTypeId = newTypeId;
    }

    public Player330 getPlayer() {
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

    /** The block that is placed (the same as {@link #getBlock()}; it still has its old type while the event runs). */
    public Block330 getBlockPlaced() {
        return getBlock();
    }

    /** The type of the block that the new block replaces (water, grass and so on; 0 for air). */
    public int getReplacedTypeId() {
        return getBlock().getTypeId();
    }
}
