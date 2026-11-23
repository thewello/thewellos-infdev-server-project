package net.thewellodev.api330.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api330.Block330;
import net.thewellodev.api330.Player330;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player330 player;
    private boolean cancelled;

    public BlockBreakEvent(Block330 block, Player330 player) {
        super(block);
        this.player = player;
    }

    public Player330 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
