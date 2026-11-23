package net.thewellodev.api327.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api327.Block327;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block327 block;

    protected BlockEvent(Block327 block) {
        this.block = block;
    }

    public Block327 getBlock() {
        return block;
    }
}
