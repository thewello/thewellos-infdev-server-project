package net.thewellodev.api414.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api414.Block414;
import net.thewellodev.api414.Player414;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player414 player;
    private boolean cancelled;

    public BlockBreakEvent(Block414 block, Player414 player) {
        super(block);
        this.player = player;
    }

    public Player414 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
