package net.thewellodev.api320.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api320.Block320;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block320 block;

    protected BlockEvent(Block320 block) {
        this.block = block;
    }

    public Block320 getBlock() {
        return block;
    }
}
