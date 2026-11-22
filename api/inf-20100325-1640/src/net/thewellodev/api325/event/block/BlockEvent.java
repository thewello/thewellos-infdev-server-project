package net.thewellodev.api325.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api325.Block325;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block325 block;

    protected BlockEvent(Block325 block) {
        this.block = block;
    }

    public Block325 getBlock() {
        return block;
    }
}
