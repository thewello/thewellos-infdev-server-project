package net.thewellodev.api415.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api415.Block415;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block415 block;

    protected BlockEvent(Block415 block) {
        this.block = block;
    }

    public Block415 getBlock() {
        return block;
    }
}
