package net.thewellodev.api420.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api420.Block420;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block420 block;

    protected BlockEvent(Block420 block) {
        this.block = block;
    }

    public Block420 getBlock() {
        return block;
    }
}
