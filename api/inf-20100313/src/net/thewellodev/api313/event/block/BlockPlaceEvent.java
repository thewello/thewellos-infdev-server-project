package net.thewellodev.api313.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api313.Block313;
import net.thewellodev.api313.Player313;

/**
 * Called when a player places a block. {@link #getBlock()} is the position (it still holds the old block);
 * {@link #getNewTypeId()} is the new block. If cancelled, the old block stays.
 */
public class BlockPlaceEvent extends BlockEvent implements Cancellable {

    private final Player313 player;
    private final int newTypeId;
    private boolean cancelled;

    public BlockPlaceEvent(Block313 block, Player313 player, int newTypeId) {
        super(block);
        this.player = player;
        this.newTypeId = newTypeId;
    }

    public Player313 getPlayer() {
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
    public Block313 getBlockPlaced() {
        return getBlock();
    }

    /** The type of the block that the new block replaces (water, grass and so on; 0 for air). */
    public int getReplacedTypeId() {
        return getBlock().getTypeId();
    }
}
