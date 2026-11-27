package net.thewellodev.api607.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api607.Block607;
import net.thewellodev.api607.Player607;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player607 player;
    private boolean cancelled;

    public BlockBreakEvent(Block607 block, Player607 player) {
        super(block);
        this.player = player;
    }

    public Player607 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
