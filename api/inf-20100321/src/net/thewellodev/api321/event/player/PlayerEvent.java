package net.thewellodev.api321.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api321.Player321;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player321 player;

    protected PlayerEvent(Player321 player) {
        this.player = player;
    }

    public Player321 getPlayer() {
        return player;
    }
}
