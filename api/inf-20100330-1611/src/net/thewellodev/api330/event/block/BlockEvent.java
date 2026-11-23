package net.thewellodev.api330.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api330.Block330;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block330 block;

    protected BlockEvent(Block330 block) {
        this.block = block;
    }

    public Block330 getBlock() {
        return block;
    }
}
