package net.thewellodev.api414.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api414.Block414;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block414 block;

    protected BlockEvent(Block414 block) {
        this.block = block;
    }

    public Block414 getBlock() {
        return block;
    }
}
