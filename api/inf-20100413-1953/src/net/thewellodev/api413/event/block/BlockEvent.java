package net.thewellodev.api413.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api413.Block413;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block413 block;

    protected BlockEvent(Block413 block) {
        this.block = block;
    }

    public Block413 getBlock() {
        return block;
    }
}
