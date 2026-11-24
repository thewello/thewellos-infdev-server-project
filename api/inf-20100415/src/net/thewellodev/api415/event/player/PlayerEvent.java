package net.thewellodev.api415.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api415.Player415;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player415 player;

    protected PlayerEvent(Player415 player) {
        this.player = player;
    }

    public Player415 getPlayer() {
        return player;
    }
}
