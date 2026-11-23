package net.thewellodev.api413.event.block;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api413.Block413;
import net.thewellodev.api413.Player413;

/** Called when a player breaks a block. If cancelled, the block stays (the player sees it come back). */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player413 player;
    private boolean cancelled;

    public BlockBreakEvent(Block413 block, Player413 player) {
        super(block);
        this.player = player;
    }

    public Player413 getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
