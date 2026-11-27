package net.thewellodev.api607.event.block;

import net.thewellodev.api.event.Event;
import net.thewellodev.api607.Block607;

/** Base class of the events about one block. */
public abstract class BlockEvent extends Event {

    private final Block607 block;

    protected BlockEvent(Block607 block) {
        this.block = block;
    }

    public Block607 getBlock() {
        return block;
    }
}
