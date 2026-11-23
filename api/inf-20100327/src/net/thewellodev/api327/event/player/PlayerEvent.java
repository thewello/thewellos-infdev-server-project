package net.thewellodev.api327.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api327.Player327;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player327 player;

    protected PlayerEvent(Player327 player) {
        this.player = player;
    }

    public Player327 getPlayer() {
        return player;
    }
}
