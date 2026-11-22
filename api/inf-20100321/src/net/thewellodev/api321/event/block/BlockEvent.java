package net.thewellodev.api321.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api321.Block321;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block321 block;

    protected BlockEvent(Block321 block) {
        this.block = block;
    }

    public Block321 getBlock() {
        return block;
    }
}
