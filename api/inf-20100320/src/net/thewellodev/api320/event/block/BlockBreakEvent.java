package net.thewellodev.api320.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api320.Block320;
import net.thewellodev.api320.Player320;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player320 player;
    private boolean cancelled;

    public BlockBreakEvent(Block320 block, Player320 player) {
        super(block);
        this.player = player;
    }

    public Player320 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
