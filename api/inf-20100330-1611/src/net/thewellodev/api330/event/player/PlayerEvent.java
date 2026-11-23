package net.thewellodev.api330.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api330.Player330;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player330 player;

    protected PlayerEvent(Player330 player) {
        this.player = player;
    }

    public Player330 getPlayer() {
        return player;
    }
}
