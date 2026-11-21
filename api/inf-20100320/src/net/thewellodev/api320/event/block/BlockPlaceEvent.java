package net.thewellodev.api320.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api320.Block320;
import net.thewellodev.api320.Player320;

/**
 * Called when a player places a block. {@link #getBlock()} is the position (it still holds the old block);
 * {@link #getNewTypeId()} is the new block. If cancelled, the old block stays.
 */
public class BlockPlaceEvent extends BlockEvent implements Cancellable {

    private final Player320 player;
    private final int newTypeId;
    private boolean cancelled;

    public BlockPlaceEvent(Block320 block, Player320 player, int newTypeId) {
        super(block);
        this.player = player;
        this.newTypeId = newTypeId;
    }

    public Player320 getPlayer() {
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
