package net.thewellodev.api327.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api327.Block327;
import net.thewellodev.api327.Player327;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player327 player;
    private boolean cancelled;

    public BlockBreakEvent(Block327 block, Player327 player) {
        super(block);
        this.player = player;
    }

    public Player327 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
