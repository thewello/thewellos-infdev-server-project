package net.thewellodev.api608.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api608.Block608;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block608 block;

    protected BlockEvent(Block608 block) {
        this.block = block;
    }

    public Block608 getBlock() {
        return block;
    }
}
